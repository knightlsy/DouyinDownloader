package com.zz.douyin.hook;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 动态照片（Motion Photo）合成器。
 *
 * ## 为什么需要它
 * 抖音的实况图 = 一张静态 JPEG + 一段 MP4。只下载图片地址，相册里看到的就是死图 ——
 * 动态效果整个丢掉。要让小米相册 / Google 相册认出来，必须把两段拼成一个文件，
 * 并在 JPEG 的 XMP 里写明视频从哪里开始。
 *
 * ## 文件结构
 * ```
 * JPEG 主图（XMP 里带 GCamera:MicroVideo* 标记 + MiCamera:XMPMeta）
 * + 末尾原样追加的 MP4 字节
 * ```
 * `MicroVideoOffset` = 从文件末尾往回数到 MP4 起点的字节数，相册靠它定位。
 *
 * ## 兼容性
 * 同时写 Google（GCamera）和小米（MiCamera）两个命名空间 ——
 * 只写一个的话，另一家的相册会把动态部分当成垃圾数据忽略。
 *
 * 参考 Android Motion Photo Format 1.0。
 */
final class MotionPhotoMuxer {

    private static final String TAG = "DouxianrenMotionPhoto";

    private MotionPhotoMuxer() {
    }

    /**
     * 把静图与视频合成一张动态照片。
     *
     * @param jpegBytes 静图 JPEG 完整字节
     * @param mp4Bytes  动态部分 MP4 完整字节
     * @return 合成后的字节，直接以 .jpg 落盘，相册会识别出动态效果
     */
    static byte[] mux(byte[] jpegBytes, byte[] mp4Bytes) {
        // 入口校验。不校验的后果不是报错，是产出一个打不开的文件 ——
        // 调用方拿到损坏图还当成功了，用户相册里躺一张裂图。
        // 这里抛 IllegalArgumentException（RuntimeException），调用方 catch 后走静态图兜底。
        if (jpegBytes == null || jpegBytes.length < 4
                || (jpegBytes[0] & 0xFF) != 0xFF || (jpegBytes[1] & 0xFF) != 0xD8) {
            throw new IllegalArgumentException("主图不是 JPEG（缺少 FFD8 SOI）");
        }
        if (!looksLikeMp4(mp4Bytes)) {
            throw new IllegalArgumentException("动态部分不是 MP4（缺少 ftyp 盒）");
        }
        byte[] jpegWithXmp = injectXmp(jpegBytes, mp4Bytes.length);
        ByteArrayOutputStream out =
                new ByteArrayOutputStream(jpegWithXmp.length + mp4Bytes.length);
        out.write(jpegWithXmp, 0, jpegWithXmp.length);
        out.write(mp4Bytes, 0, mp4Bytes.length);
        return out.toByteArray();
    }

    /**
     * 在 JPEG 里插入 XMP 段（APP1）。
     *
     * 先摘掉已有的 XMP APP1（抖音的图有些自带，留着会和新的冲突），
     * 再把带 MicroVideo 标记的新段插在 SOI 之后。
     *
     * JPEG 段结构：FFD8(SOI) + [FFE1 长度 负载]... + FFDA(SOS) + 压缩数据 + FFD9(EOI)
     */
    private static byte[] injectXmp(byte[] jpeg, long mp4Size) {
        byte[] xmpPacket = buildXmp(mp4Size);
        byte[] xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000"
                .getBytes(StandardCharsets.US_ASCII);

        // 长度字段含自身 2 字节
        int segmentLen = 2 + xmpHeader.length + xmpPacket.length;
        byte[] app1 = new byte[2 + segmentLen];
        app1[0] = (byte) 0xFF;
        app1[1] = (byte) 0xE1;
        app1[2] = (byte) ((segmentLen >> 8) & 0xFF);
        app1[3] = (byte) (segmentLen & 0xFF);
        System.arraycopy(xmpHeader, 0, app1, 4, xmpHeader.length);
        System.arraycopy(xmpPacket, 0, app1, 4 + xmpHeader.length, xmpPacket.length);

        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + app1.length);
        out.write(jpeg, 0, 2); // SOI 原样保留

        int index = 2;
        while (index + 1 < jpeg.length) {
            if ((jpeg[index] & 0xFF) != 0xFF) {
                // 结构不合预期：余下字节必须原样补进输出。
                // 原来这里只 break，注释写着「余下原样写」但实际一个字节都没写 ——
                // 输出被截断，图打不开。
                out.write(jpeg, index, jpeg.length - index);
                return finishXmp(out, app1);
            }
            int marker = jpeg[index + 1] & 0xFF;
            if (marker == 0xDA) {
                // SOS 之后是压缩数据，不含段长度，整段照抄
                out.write(jpeg, index, jpeg.length - index);
                return finishXmp(out, app1);
            }
            if (marker == 0xD9) {
                out.write(jpeg, index, 2);
                index += 2;
                continue;
            }
            if ((marker >= 0xD0 && marker <= 0xD8) || marker == 0x01) {
                // 独立 marker，没有长度字段
                out.write(jpeg, index, 2);
                index += 2;
                continue;
            }
            if (index + 3 >= jpeg.length) {
                // 剩余不足一个段的头部，同样是原样补出去
                out.write(jpeg, index, jpeg.length - index);
                return finishXmp(out, app1);
            }
            int segLen = ((jpeg[index + 2] & 0xFF) << 8) | (jpeg[index + 3] & 0xFF);
            if (segLen < 2 || index + 2 + segLen > jpeg.length) {
                out.write(jpeg, index, jpeg.length - index);
                return finishXmp(out, app1);
            }
            boolean isXmp = marker == 0xE1
                    && index + 9 < jpeg.length
                    && jpeg[index + 4] == 'h'
                    && jpeg[index + 5] == 't'
                    && jpeg[index + 6] == 't'
                    && jpeg[index + 7] == 'p'
                    && jpeg[index + 8] == ':'
                    && jpeg[index + 9] == '/';
            if (!isXmp) {
                out.write(jpeg, index, 2 + segLen);
            } else {
                Log.d(TAG, "移除原 XMP 段 " + segLen + "B");
            }
            index += 2 + segLen;
        }
        return finishXmp(out, app1);
    }

    /**
     * 粗判 MP4：第 4..7 字节是 'ftyp'。
     *
     * 只靠「文件大于 1KB」是不够的 —— CDN 对抖音 UA 返回的 HTML 错误页
     * 通常远超 1KB，被拼进去之后 XMP offset 指向的是网页而不是 ftyp，
     * 相册只会显示静态图，而且不会有任何报错。
     */
    static boolean looksLikeMp4(byte[] data) {
        return data != null
                && data.length >= 12
                && data[4] == 'f'
                && data[5] == 't'
                && data[6] == 'y'
                && data[7] == 'p';
    }

    /** 把新 XMP 段插到 SOI 之后、其余所有段之前。 */
    private static byte[] finishXmp(ByteArrayOutputStream out, byte[] app1) {
        byte[] head = out.toByteArray();
        byte[] result = new byte[head.length + app1.length];
        System.arraycopy(head, 0, result, 0, 2);                 // SOI
        System.arraycopy(app1, 0, result, 2, app1.length);       // 新 XMP
        System.arraycopy(head, 2, result, 2 + app1.length, head.length - 2);
        return result;
    }

    /**
     * 生成 XMP 包。
     *
     * MicroVideoOffset 用「视频字节数」而不是绝对偏移 —— 相册是从文件末尾往回数的。
     * MicroVideoPresentationTimestampUs 给一个合理值即可，影响的是首帧对齐。
     */
    private static byte[] buildXmp(long mp4Size) {
        String xmp = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.4-c0 85.0\">\n"
                + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "  <rdf:Description rdf:about=\"\"\n"
                + "    xmlns:GCamera=\"http://ns.google.com/photos/1.0/camera/\"\n"
                + "    xmlns:MiCamera=\"http://ns.xiaomi.com/photos/1.0/camera/\"\n"
                + "   GCamera:MicroVideo=\"1\"\n"
                + "   GCamera:MicroVideoVersion=\"1\"\n"
                + "   GCamera:MicroVideoOffset=\"" + mp4Size + "\"\n"
                + "   GCamera:MicroVideoPresentationTimestampUs=\"1500000\"\n"
                + "   MiCamera:XMPMeta=\"1\"/>\n"
                + " </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>";
        return xmp.getBytes(StandardCharsets.UTF_8);
    }
}

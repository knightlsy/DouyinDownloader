package com.knightlsy.douyin.data

import android.util.Log
import java.io.ByteArrayOutputStream

/**
 * 小米/Google 动态照片(Motion Photo)合成器。
 *
 * 格式(小米相册可识别, 兼容 Google MicroVideo V1 与 MiCamera 命名空间):
 * 1. JPEG 主图(XMP 里写入 GCamera:MicroVideo* 标记 + MiCamera:XMPMeta)
 * 2. 末尾原样追加 MP4 视频字节
 * 3. MicroVideoOffset = 从文件末尾往回数到 MP4 起点的字节数
 *
 * 参考: Android Motion Photo Format 1.0 (developer.android.com)
 * 与 AppleLIVP_to_XiaomiMotionPhoto 的实测实现。
 */
object MotionPhotoMuxer {

    private const val TAG = "MotionPhotoMuxer"

    /**
     * 把 JPEG 字节与 MP4 字节合成一张动态照片。
     * @param jpegBytes 静图 JPEG 完整字节
     * @param mp4Bytes 动态部分 MP4 完整字节
     * @return 合成后的动态照片 JPEG 字节(直接以 .jpg 落盘, 相册会识别出动态效果)
     */
    fun mux(jpegBytes: ByteArray, mp4Bytes: ByteArray): ByteArray {
        // 1. 在 JPEG 的 XMP 段写入 MicroVideo 标记(小米/Google 相册靠它定位视频)
        val jpegWithXmp = injectXmp(jpegBytes, mp4Bytes.size.toLong())
        // 2. 追加 MP4
        val out = ByteArrayOutputStream(jpegWithXmp.size + mp4Bytes.size)
        out.write(jpegWithXmp)
        out.write(mp4Bytes)
        return out.toByteArray()
    }

    /**
     * 在 JPEG 里插入/替换 XMP 包(APP1 段)。
     * 简化实现: 先移除已有 XMP APP1 段, 再在 SOI 之后插入带 GCamera/MiCamera 标记的新 XMP APP1 段。
     * JPEG 结构: FFD8 (SOI) [APP1...] ...FFD9
     */
    private fun injectXmp(jpeg: ByteArray, mp4Size: Long): ByteArray {
        val xmpPacket = buildXmp(mp4Size)
        // XMP APP1: FFE1 + 2字节长度 + "http://ns.adobe.com/xap/1.0/\0" + xmpPacket
        val xmpHeader = "http://ns.adobe.com/xap/1.0/\u0000".toByteArray(Charsets.US_ASCII)
        val segmentLen = 2 + xmpHeader.size + xmpPacket.size // 长度字段含自身2字节
        val app1 = ByteArray(2 + segmentLen)
        app1[0] = 0xFF.toByte(); app1[1] = 0xE1.toByte()
        app1[2] = ((segmentLen shr 8) and 0xFF).toByte(); app1[3] = (segmentLen and 0xFF).toByte()
        xmpHeader.copyInto(app1, 4)
        xmpPacket.copyInto(app1, 4 + xmpHeader.size)

        // 跳过 SOI, 移除已有的 XMP APP1 段(FFE1 + "http"头), 其余原样保留
        val out = ByteArrayOutputStream(jpeg.size + app1.size)
        out.write(jpeg, 0, 2) // SOI
        var i = 2
        while (i + 1 < jpeg.size) {
            if (jpeg[i].toInt() and 0xFF != 0xFF) break // 非法结构, 原样写余下
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xDA) { // SOS — 之后是压缩数据, 原样写到底
                out.write(jpeg, i, jpeg.size - i)
                return finishXmp(out, app1)
            }
            if (marker == 0xD9) { // EOI
                out.write(jpeg, i, 2)
                i += 2
                continue
            }
            if (marker in 0xD0..0xD8 || marker == 0x01) { // 独立 marker, 无长度字段
                out.write(jpeg, i, 2); i += 2; continue
            }
            if (i + 3 >= jpeg.size) break
            val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            if (segLen < 2 || i + 2 + segLen > jpeg.size) { // 长度异常, 原样写余下
                out.write(jpeg, i, jpeg.size - i)
                return finishXmp(out, app1)
            }
            val isXmp = marker == 0xE1 && i + 10 < jpeg.size &&
                jpeg[i + 4] == 'h'.code.toByte() && jpeg[i + 5] == 't'.code.toByte() &&
                jpeg[i + 6] == 't'.code.toByte() && jpeg[i + 7] == 'p'.code.toByte() &&
                jpeg[i + 8] == ':'.code.toByte() && jpeg[i + 9] == '/'.code.toByte()
            if (!isXmp) out.write(jpeg, i, 2 + segLen)
            else Log.d(TAG, "移除原XMP段 ${segLen}B")
            i += 2 + segLen
        }
        return finishXmp(out, app1)
    }

    /** 新 XMP APP1 段插在 SOI 后(所有段之前), 输出最终字节 */
    private fun finishXmp(out: ByteArrayOutputStream, app1: ByteArray): ByteArray {
        val head = out.toByteArray()
        val result = ByteArray(head.size + app1.size)
        // head[0..1] 是 SOI, 其余是原段; 把 app1 插在 SOI 之后
        head.copyInto(result, 0, 0, 2)
        app1.copyInto(result, 2)
        head.copyInto(result, 2 + app1.size, 2)
        return result
    }

    /** 生成含 GCamera:MicroVideo* 与 MiCamera:XMPMeta 的 XMP 包 */
    private fun buildXmp(mp4Size: Long): ByteArray {
        val xmp = """<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>
<x:xmpmeta xmlns:x="adobe:ns:meta/" x:xmptk="Adobe XMP Core 5.4-c0 85.0">
 <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about=""
    xmlns:GCamera="http://ns.google.com/photos/1.0/camera/"
    xmlns:MiCamera="http://ns.xiaomi.com/photos/1.0/camera/"
   GCamera:MicroVideo="1"
   GCamera:MicroVideoVersion="1"
   GCamera:MicroVideoOffset="$mp4Size"
   GCamera:MicroVideoPresentationTimestampUs="1500000"
   MiCamera:XMPMeta="1"/>
 </rdf:RDF>
</x:xmpmeta>
<?xpacket end="w"?>"""
        return xmp.toByteArray(Charsets.UTF_8)
    }
}

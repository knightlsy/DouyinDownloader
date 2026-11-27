package com.zz.douyin;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 共享下载历史的落盘层。
 *
 * ## 为什么需要它
 * 模块在抖音进程里下载，App 在另一个进程里要看到这些记录。
 * 两边签名不同，走不了 signature 级权限；Android 11+ 又读不到对方的应用目录。
 * 所以由模块自己的进程（com.zz.douyin）落盘，再通过 {@link SharedProvider} 暴露给 App。
 *
 * ## 抖音进程怎么把数据送进来
 * 抖音进程写不了本应用的文件（沙盒），所以走显式广播 → {@link HistoryReceiver} 落盘。
 *
 * ## 格式
 * JSONL：一行一条，只追加。写坏一行不影响其它行。
 * 字段定义见仓库根的 SHARED-DATA.md。
 */
public final class SharedHistoryStore {

    private static final String TAG = "DouyinShared";
    private static final String FILE_NAME = "shared_history.jsonl";
    /** 保留的最大行数。历史是参考信息，不需要无限增长。 */
    private static final int MAX_LINES = 2000;

    /**
     * 单条记录上限。广播 extra 能塞到几百 KB，不设限的话第三方应用循环发大包
     * 就能把本应用私有目录写到用户清不掉（只有卸载或清数据能回收）。
     * 正常记录十几个字段加起来不到 2KB，16KB 留了充足余量。
     */
    private static final int MAX_RECORD_BYTES = 16 * 1024;

    private SharedHistoryStore() {
    }

    private static File file(Context context) {
        File dir = new File(context.getFilesDir(), "shared");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "无法创建共享目录: " + dir);
        }
        return new File(dir, FILE_NAME);
    }

    /**
     * 追加一条记录。
     *
     * 写入前先做最基础的字段校验：没有 id 或 contentId 的记录写进去也没法去重。
     * 失败只记日志 —— 下载本身已经成功了，历史丢失不该让用户看到报错。
     */
    static synchronized void append(Context context, String jsonLine) {
        if (jsonLine == null || jsonLine.trim().isEmpty()) {
            return;
        }
        if (jsonLine.getBytes(StandardCharsets.UTF_8).length > MAX_RECORD_BYTES) {
            Log.w(TAG, "历史记录超过 " + MAX_RECORD_BYTES + " 字节，丢弃");
            return;
        }

        String line;
        try {
            JSONObject object = new JSONObject(jsonLine);
            if (object.optString("id").isEmpty() || object.optString("contentId").isEmpty()) {
                Log.w(TAG, "历史记录缺少 id/contentId，丢弃");
                return;
            }
            // 落盘用重新序列化的结果，不是原始串。
            // org.json 解析字符串字面量里的裸控制字符是宽容的 —— 原始串里塞一个
            // 真实的换行，就能让一条广播在 JSONL 里变成多行，后续那些行完全绕过
            // 上面的字段校验，直接成为独立记录。重新序列化会把换行转义成 \n。
            line = object.toString();
        } catch (Exception error) {
            Log.w(TAG, "历史记录不是合法 JSON，丢弃", error);
            return;
        }

        File target = file(context);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(target, true), StandardCharsets.UTF_8)) {
            writer.write(line);
            writer.write('\n');
            writer.flush();
        } catch (Exception error) {
            Log.w(TAG, "写入共享历史失败", error);
            return;
        }
        trimIfNeeded(context, target);
    }

    /**
     * 删除指定 id 的记录（历史页删除模块记录时的 JSONL 删除通道）。
     *
     * 整体重写：先写临时文件再 rename，与 trimIfNeeded 的原子写法一致，
     * 避免中途失败留下半个文件。解析不出 id 的行原样保留。
     */
    public static synchronized void removeAll(Context context, Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            File target = file(context);
            List<String> all = readAllLines(target);
            List<String> kept = new ArrayList<>(all.size());
            boolean removed = false;
            for (String line : all) {
                String id = parseId(line);
                if (id != null && ids.contains(id)) {
                    removed = true;
                } else {
                    kept.add(line);
                }
            }
            if (removed) {
                rewrite(target, kept);
            }
        } catch (Exception error) {
            Log.w(TAG, "删除共享历史失败", error);
        }
    }

    /**
     * 清空全部记录（历史页「清空全部」时对 JSONL 的删除通道）。
     * 直接删文件：单次系统调用本身就是原子的，比重写空文件更干净。
     */
    public static synchronized void clear(Context context) {
        File target = file(context);
        if (target.isFile() && !target.delete()) {
            Log.w(TAG, "清空共享历史失败: " + target);
        }
    }

    private static String parseId(String line) {
        try {
            return new JSONObject(line).optString("id");
        } catch (Exception error) {
            return null;
        }
    }

    /** 整体重写：先写临时文件再 rename，避免中途失败留下半个文件。 */
    private static void rewrite(File target, List<String> lines) throws Exception {
        File temp = new File(target.getParentFile(), FILE_NAME + ".tmp");
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(temp, false), StandardCharsets.UTF_8)) {
            for (String line : lines) {
                writer.write(line);
                writer.write('\n');
            }
        }
        if (!temp.renameTo(target)) {
            Log.w(TAG, "历史重写后 rename 失败，保留原文件");
        }
    }

    /** 超过上限时砍掉最早的一半，避免无限增长又不用每次都重写整个文件。 */
    private static void trimIfNeeded(Context context, File target) {
        try {
            List<String> all = readAllLines(target);
            if (all.size() <= MAX_LINES) {
                return;
            }
            List<String> kept = new ArrayList<>(all.subList(all.size() - MAX_LINES / 2, all.size()));
            // 整体重写：先写临时文件再 rename，避免中途失败留下半个文件
            File temp = new File(target.getParentFile(), FILE_NAME + ".tmp");
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    new FileOutputStream(temp, false), StandardCharsets.UTF_8)) {
                for (String line : kept) {
                    writer.write(line);
                    writer.write('\n');
                }
            }
            if (!temp.renameTo(target)) {
                Log.w(TAG, "历史裁剪后 rename 失败，保留原文件");
            }
        } catch (Exception error) {
            Log.w(TAG, "历史裁剪失败", error);
        }
    }

    /**
     * 读回所有行。解析失败的行跳过，不影响其余。
     *
     * 必须是 public：调用方在 com.knightlsy.douyin.data（Kotlin），
     * 与这个类不在同一个包，包私有访问不到。合并之前两边是两个 App，
     * 现在是同一个 APK 的两个包。
     */
    public static synchronized List<String> read(Context context) {
        return readAllLines(file(context));
    }

    private static List<String> readAllLines(File target) {
        List<String> lines = new ArrayList<>();
        if (!target.isFile()) {
            return lines;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(target), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "读取共享历史失败", error);
        }
        return lines;
    }
}

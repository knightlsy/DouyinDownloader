package com.zz.douyin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 抖音进程 → 本应用进程的历史投递通道。
 *
 * ## 为什么用广播
 * 模块的 hook 代码跑在抖音进程里，本应用的文件属于 com.zz.douyin，跨包写不了（Android 沙盒）。
 * 显式广播（setPackage 指向本包）+ 静态注册的 Receiver 是唯一不依赖签名的通路。
 *
 * ## 安全
 * Receiver 设成 exported，但只接受 action = {@link #ACTION} 且包名匹配的请求。
 * 广播里只有下载记录（标题/作者/文件名），不含任何凭据；伪造它最多污染历史列表，
 * 影响面可控。真正的数据出口 {@link SharedProvider} 另有双层校验。
 */
public final class HistoryReceiver extends BroadcastReceiver {

    static final String ACTION = "com.knightlsy.douyin.SHARED_HISTORY";
    static final String EXTRA_JSON = "json";

    /**
     * 单线程写入队列。
     *
     * 原来每条广播都 new Thread —— 被刷时线程数没有上限，且 onReceive 一 return
     * 系统就认为接收完成，进程随时可能被回收，work 还没落盘就丢了（静默丢数据，
     * 连日志都没有）。单线程池顺带保证写入顺序。
     */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        String json = intent.getStringExtra(EXTRA_JSON);
        if (json == null || json.trim().isEmpty()) {
            return;
        }
        // goAsync 把进程的存活窗口延长到 finish() 为止，写盘期间不会被系统回收；
        // 写盘本身放到单线程池，不阻塞广播主线程。
        final PendingResult pending = goAsync();
        final Context appContext = context.getApplicationContext();
        try {
            WRITER.execute(() -> {
                try {
                    SharedHistoryStore.append(appContext, json);
                    Log.i("DouyinShared", "收到模块下载记录");
                } catch (Throwable error) {
                    Log.w("DouyinShared", "投递历史失败", error);
                } finally {
                    pending.finish();
                }
            });
        } catch (Throwable error) {
            // 队列满/已关闭：必须 finish，否则系统一直认为这个广播没处理完
            pending.finish();
            Log.w("DouyinShared", "无法排入写入队列", error);
        }
    }
}

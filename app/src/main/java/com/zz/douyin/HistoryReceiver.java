package com.zz.douyin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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
 * Receiver 必须 exported：hook 从抖音进程发的是跨应用显式广播。也不能挂
 * signature 级 permission：广播从抖音进程发出，抖音与本应用签名不同，带不上
 * 本应用的签名权限，加了等于把合法流量一起拒掉。因此发送方校验放在代码里做
 * （{@link #isTrustedSender}）：只放行本应用自己与抖音宿主包名
 * （com.ss.android.ugc.* 及其渠道变体）。广播里只有下载记录（标题/作者/
 * 文件名），不含任何凭据；伪造它最多污染历史列表，影响面可控。
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
        if (!isTrustedSender(context, intent)) {
            Log.w("DouyinShared", "拒绝来自未知发送方的 SHARED_HISTORY 广播");
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

    /**
     * 发送方校验：只接受本应用自己与抖音宿主（com.ss.android.ugc.* 及其渠道
     * 变体）发出的广播。hook 跑在抖音进程里，发送方就是宿主的包名，所以不能
     * 写死成某一个具体包名。
     *
     * 实现分层，可靠性不同：
     * - API 34+：{@link #getSentFromPackage()}。只有发送方在 BroadcastOptions
     *   里 setShareIdentityEnabled(true) 才会返回非 null；返回 null 表示发送方
     *   没共享身份，此时无法判定，落到下面的弱校验。
     * - 弱校验（兜底）：Intent#getPackage() 只是发送方写进 Intent 的目标包名，
     *   可以被伪造，仅能挡住不带 setPackage 的扫描式广播，聊胜于无。
     *
     * 不能改用 signature permission（原因见类注释）：hook 从抖音进程发广播，
     * 跨进程跨应用、签名不同，带不上本应用的签名权限。
     */
    private boolean isTrustedSender(Context context, Intent intent) {
        String sender = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            sender = getSentFromPackage();
        }
        if (sender != null) {
            return context.getPackageName().equals(sender)
                    || sender.startsWith("com.ss.android.ugc.");
        }
        return context.getPackageName().equals(intent.getPackage());
    }
}

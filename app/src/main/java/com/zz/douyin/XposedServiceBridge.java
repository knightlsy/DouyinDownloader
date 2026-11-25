package com.zz.douyin;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/**
 * App 进程与 LSPosed 服务之间的桥。
 *
 * ## 为什么必须有它
 * hook 侧的 `getRemotePreferences(name)` 读的**不是**本 APK 的 shared_prefs XML，
 * 而是 LSPosed 守护进程按「模块包名 + userId」管理的那一份。
 * App 侧要写同一份数据，唯一途径是 `XposedService.getRemotePreferences(name)`。
 *
 * 如果直接用 `context.getSharedPreferences("content_filter", MODE_PRIVATE)`，
 * 写进去的是本 APK 私有目录下的 XML —— 和守护进程那份是两个存储，互不相通。
 * 表现就是：开关能拨、状态也存下来了，但 hook 侧永远读不到，
 * 模块行为完全不受设置页控制，而且没有任何报错。
 *
 * 这套做法照搬自抖仙人的 ModuleApplication（它原本就是模块 + 界面同包，
 * 一直是这么走的），不是我推测的。
 *
 * ## 未连接时
 * LSPosed 没启用本模块、或者管理器没起来时，service 为 null。
 * 此时 {@link #prefs()} 返回 null，调用方必须禁用 UI 而不是静默写本地 ——
 * 写本地等于制造一份永远不会被读到的数据，用户会以为设置生效了。
 */
public final class XposedServiceBridge implements XposedServiceHelper.OnServiceListener {

    private static final String TAG = "XposedBridge";

    private static volatile XposedService service;
    private static final Set<Runnable> LISTENERS = new CopyOnWriteArraySet<>();

    private XposedServiceBridge() {
    }

    /** 在 Application.onCreate 里调用一次。 */
    public static void attach(Context context) {
        XposedServiceHelper.registerListener(new XposedServiceBridge());
    }

    /**
     * 模块的远程偏好。
     *
     * @return 服务未连接时返回 null —— 调用方必须处理，不能当成空 prefs 用
     */
    public static SharedPreferences prefs() {
        XposedService current = service;
        if (current == null) {
            return null;
        }
        try {
            return current.getRemotePreferences(FilterPreferences.NAME);
        } catch (Throwable error) {
            Log.w(TAG, "读取远程偏好失败", error);
            return null;
        }
    }

    public static boolean isConnected() {
        return service != null;
    }

    /** 直接给设置页显示的一行状态。 */
    public static String describe() {
        XposedService current = service;
        if (current == null) {
            return "未连接 LSPosed";
        }
        try {
            return current.getFrameworkName() + " · API " + current.getApiVersion();
        } catch (Throwable error) {
            return "已连接";
        }
    }

    /** 服务连接/断开时回调，设置页用它来刷新开关可用性。 */
    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    private static void notifyChanged() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (Throwable error) {
                Log.w(TAG, "监听器异常", error);
            }
        }
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        Log.i(TAG, "LSPosed 服务已连接");
        notifyChanged();
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
        }
        Log.i(TAG, "LSPosed 服务已断开");
        notifyChanged();
    }
}

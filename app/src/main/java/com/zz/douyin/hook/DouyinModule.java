package com.zz.douyin.hook;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

public final class DouyinModule extends XposedModule {
    static final String TAG = "DouyinImmersive";
    static final String TARGET_PACKAGE = "com.ss.android.ugc.aweme";
    /** 系统界面：焦点通知白名单与签名检查在这里 */
    static final String SYSTEMUI_PACKAGE = "com.android.systemui";
    /** xms 授权服务：OS3 焦点授权会话在这里 */
    static final String XMSF_PACKAGE = "com.xiaomi.xmsf";
    private static final Set<ClassLoader> INSTALLED_LOADERS =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "module 1.7.7 loaded in " + param.getProcessName()
                + ", framework=" + getFrameworkName()
                + ", api=" + getApiVersion());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        /*
         * 三个作用域，各走各的：
         *  抖音      —— 原有的全部增强 hook
         *  SystemUI  —— 超级岛白名单解锁（只在用户开了开关时）
         *  XMSF      —— 焦点授权绕过（同上）
         *
         * 后两个会进系统进程。开关关着就一个 hook 都不装 —— 不给系统留任何
         * 多余的回调，也不给以后埋"明明关了还在跑"的隐患。
         */
        String packageName = param.getPackageName();
        if (SYSTEMUI_PACKAGE.equals(packageName)) {
            onSystemUiReady(param);
            return;
        }
        if (XMSF_PACKAGE.equals(packageName)) {
            onXmsfReady(param);
            return;
        }
        if (!TARGET_PACKAGE.equals(packageName) || !param.isFirstPackage()) {
            return;
        }
        synchronized (INSTALLED_LOADERS) {
            if (!INSTALLED_LOADERS.add(param.getClassLoader())) {
                log(Log.DEBUG, TAG, "hooks already installed for this class loader");
                return;
            }
        }

        /*
         * 用户开关。关着就一个 hook 都不装 —— 比「装上了但内部各自判断不干活」干净：
         * 后者会在抖音进程里挂一堆空壳监听，白白吃回调。
         * 注意改这个开关要重启抖音才生效（hook 是进程启动时装的）。
         */
        SharedPreferences modulePrefs = getRemotePreferences(
                com.zz.douyin.FilterPreferences.NAME);
        if (!com.zz.douyin.FilterPreferences.readModuleEnabled(modulePrefs)) {
            log(Log.INFO, TAG, "模块开关关闭，跳过全部 hook");
            return;
        }

        installSubsystem("activity lifecycle", this::hookActivityLifecycle);
        installSubsystem("activity touch", this::hookActivityTouch);
        installSubsystem("feed double tap guard",
                () -> DoubleTapGuard.installFeedInterceptor(this, param.getClassLoader()));
        installSubsystem("native double tap guard",
                () -> DoubleTapGuard.installPlatformListeners(this));
        installSubsystem("hidden control touch",
                () -> ImmersiveTouchGuard.install(this, param.getClassLoader()));
        installSubsystem("instrumentation lifecycle", this::hookInstrumentationLifecycle);
        installSubsystem(
                "Douyin activity lifecycle",
                () -> hookDouyinActivityLifecycle(param.getClassLoader())
        );
        installSubsystem(
                "feed content tracker",
                () -> {
                    SharedPreferences preferences = getRemotePreferences(
                            com.zz.douyin.FilterPreferences.NAME
                    );
                    ImmersiveUi.configurePreferences(preferences);
                    FeedContentTracker.install(
                            this,
                            param.getClassLoader(),
                            preferences
                    );
                }
        );
        installSubsystem(
                "player tracker",
                () -> PlayerHooks.install(this, param.getClassLoader())
        );
        installSubsystem(
                "video quality guard",
                () -> {
                    SharedPreferences preferences = getRemotePreferences(
                            com.zz.douyin.FilterPreferences.NAME
                    );
                    QualityHooks.install(this, param.getClassLoader(), preferences);
                }
        );
        log(Log.INFO, TAG, "hook installation finished for " + param.getPackageName());
    }

    /**
     * SystemUI 进程：只做超级岛白名单解锁，别的什么都不碰。
     *
     * 这个进程里跑错一行代码的代价是整个状态栏、通知、锁屏 ——
     * 所以不做任何列表遍历、不注册监听、不缓存引用，装完就退。
     */
    private void onSystemUiReady(PackageReadyParam param) {
        SharedPreferences prefs = getRemotePreferences(
                com.zz.douyin.FilterPreferences.NAME);
        if (!com.zz.douyin.FilterPreferences.readFocusUnlock(prefs)) {
            log(Log.INFO, TAG, "focus unlock off, skip systemui");
            return;
        }
        if (!param.isFirstPackage()) {
            return;
        }
        try {
            FocusUnlockHook.installSystemUi(this, param.getClassLoader());
        } catch (Throwable error) {
            // 绝对不能把异常抛给 SystemUI
            log(Log.ERROR, TAG, "focus unlock systemui failed", error);
        }
    }

    /** XMSF 进程：只做焦点授权绕过，同样装完就退。 */
    private void onXmsfReady(PackageReadyParam param) {
        SharedPreferences prefs = getRemotePreferences(
                com.zz.douyin.FilterPreferences.NAME);
        if (!com.zz.douyin.FilterPreferences.readFocusUnlock(prefs)) {
            log(Log.INFO, TAG, "focus unlock off, skip xmsf");
            return;
        }
        if (!param.isFirstPackage()) {
            return;
        }
        try {
            FocusUnlockHook.installXmsf(this, param.getClassLoader());
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "focus unlock xmsf failed", error);
        }
    }

    private void installSubsystem(String name, HookInstaller installer) {
        try {
            installer.install();
            log(Log.INFO, TAG, "hook subsystem installed: " + name);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "hook subsystem failed: " + name, error);
        }
    }

    @FunctionalInterface
    private interface HookInstaller {
        void install() throws Throwable;
    }

    private void hookActivityTouch() throws NoSuchMethodException {
        Method dispatchTouchEvent = Activity.class.getDeclaredMethod(
                "dispatchTouchEvent", MotionEvent.class
        );
        hook(dispatchTouchEvent)
                .setId("douyin-immersive-activity-touch")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = (Activity) chain.getThisObject();
                    MotionEvent event = (MotionEvent) chain.getArgs().get(0);
                    ImmersiveUi.beforeActivityTouch(activity, event);
                    Object result = chain.proceed();
                    ImmersiveUi.onActivityTouch(activity, event);
                    return result;
                });
    }

    private void hookActivityLifecycle() throws NoSuchMethodException {
        Method onResume = Activity.class.getDeclaredMethod("onResume");
        hook(onResume)
                .setId("douyin-immersive-activity-resume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ImmersiveUi.onActivityResumed((Activity) chain.getThisObject());
                    return result;
                });

        Method onPause = Activity.class.getDeclaredMethod("onPause");
        hook(onPause)
                .setId("douyin-immersive-activity-pause")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ImmersiveUi.onActivityPaused((Activity) chain.getThisObject());
                    return chain.proceed();
                });
    }

    private void hookInstrumentationLifecycle() throws NoSuchMethodException {
        Method callResume = Instrumentation.class.getDeclaredMethod(
                "callActivityOnResume", Activity.class
        );
        hook(callResume)
                .setId("douyin-immersive-instrumentation-resume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ImmersiveUi.onActivityResumed((Activity) chain.getArgs().get(0));
                    return result;
                });

        Method callPause = Instrumentation.class.getDeclaredMethod(
                "callActivityOnPause", Activity.class
        );
        hook(callPause)
                .setId("douyin-immersive-instrumentation-pause")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ImmersiveUi.onActivityPaused((Activity) chain.getArgs().get(0));
                    return chain.proceed();
                });
    }

    private void hookDouyinActivityLifecycle(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> splashActivity = Class.forName(
                "com.ss.android.ugc.aweme.splash.SplashActivity", false, loader
        );

        Method onCreate = splashActivity.getDeclaredMethod(
                "onCreate", android.os.Bundle.class
        );
        hook(onCreate)
                .setId("douyin-immersive-splash-create")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ImmersiveUi.onActivityResumed((Activity) chain.getThisObject());
                    return result;
                });

        Method onResume = splashActivity.getDeclaredMethod("onResume");
        hook(onResume)
                .setId("douyin-immersive-splash-resume")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    ImmersiveUi.onActivityResumed((Activity) chain.getThisObject());
                    return result;
                });

        Method onStop = splashActivity.getDeclaredMethod("onStop");
        hook(onStop)
                .setId("douyin-immersive-splash-stop")
                .setExceptionMode(ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    ImmersiveUi.onActivityPaused((Activity) chain.getThisObject());
                    return chain.proceed();
                });
    }
}

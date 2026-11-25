package com.zz.douyin.hook;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * HyperOS 焦点通知 / 超级岛白名单解锁。
 *
 * <h2>为什么需要这个</h2>
 * HyperOS 对焦点通知有应用白名单。普通应用即使按官方规范把 {@code miui.focus.param}
 * 塞进通知 extras，系统也不放行 —— 见 {@code MiuiIslandProbe} 那几轮实测：
 * 通知能进活跃列表、JSON 结构也逐字段对齐了社区库，但岛就是不出现。
 *
 * <h2>OS2 与 OS3 的差别</h2>
 * <ul>
 *   <li><b>OS2</b>：SystemUI 主进程 {@code NotificationSettingsManager} 的
 *       {@code canShowFocus} / {@code canCustomFocus} 决定白名单，强制返回 true 即可。</li>
 *   <li><b>OS3</b>：白名单类搬进了 SystemUI 插件（{@code MIUISystemUIPlugin}），
 *       而且多了两道检查 —— 插件内的 {@code SignatureChecker.checkSignatures}
 *       （非白名单签名不放行），以及 {@code com.xiaomi.xmsf} 进程里
 *       {@code AuthSession} 的焦点授权会话（授权失败不上岛）。</li>
 * </ul>
 *
 * <h2>这个类会碰系统进程，风险等级和改抖音完全不同</h2>
 * SystemUI 里一个 hook 崩了，影响的是整个状态栏、通知、锁屏 —— 不是"抖音打不开"。
 * 所以这里的规矩比别处严：
 * <ul>
 *   <li><b>默认关闭</b>：{@code FilterPreferences.KEY_FOCUS_UNLOCK} 默认 false，
 *       用户有意识打开才装 hook；</li>
 *   <li>每一步独立 try/catch，失败只记日志，绝不外抛；</li>
 *   <li>只用最低限度的 hook 点，不碰热路径；</li>
 *   <li>主线程不做事，全部在 onPackageReady 阶段一次性装完。</li>
 * </ul>
 *
 * <h2>实现参考</h2>
 * Hook 目标与 {@code Yizhou147/focus-unlock-module}（GPL-3.0）一致，该类本身是
 * 对 Hail（雹）/ HyperIsland / HyperCeiler 同款功能的 libxposed 化重写。
 * 本文件按本项目既有风格（{@link DouyinModule} 的 hook 链式 API）重新实现。
 *
 * <p><b>改这里之前请先读 {@code MiuiIslandProbe} 的注释</b>，那边记录了 5 轮实测
 * 各自的结论，避免重复踩坑。
 */
final class FocusUnlockHook {

    /** 白名单类名随 HyperOS 版本迁移：旧版 miui.* （插件内），新版 com.miui.*（主 APK）。 */
    private static final String[] WHITELIST_CLASSES = {
            "miui.systemui.notification.NotificationSettingsManager",
            "com.miui.systemui.notification.NotificationSettingsManager",
    };

    /** 仅 OS3 焦点通知插件存在。 */
    private static final String SIGNATURE_CHECKER_CLASS =
            "miui.systemui.notification.focus.SignatureChecker";

    /** 插件加载工厂：hook 它的 createPluginContext 才能拿到插件 ClassLoader。 */
    private static final String PLUGIN_FACTORY_CLASS =
            "com.android.systemui.shared.plugins.PluginInstance$PluginFactory";

    /** XMSF 进程里的焦点授权会话。 */
    private static final String AUTH_SESSION_CLASS = "com.xiaomi.xms.auth.AuthSession";

    /** 已处理过的 ClassLoader，避免插件重建时重复挂载。 */
    private static final Set<ClassLoader> HOOKED_LOADERS = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<ClassLoader, Boolean>())
    );

    /**
     * canShowFocus 在 SystemUI 里是高频调用（每条通知都查一遍白名单）。
     * 拦到的每一次都写日志会拖慢通知流程、把 logcat 刷满，所以只记第一次命中，
     * 用来确认 hook 真的在生效，之后安静放行。
     */
    private static final AtomicBoolean WHITELIST_HIT = new AtomicBoolean(false);

    private FocusUnlockHook() {
    }

    // ------------------------------------------------------------------
    // SystemUI：白名单 + 签名检查
    // ------------------------------------------------------------------

    /**
     * SystemUI 进程入口。
     *
     * 两条线都要走：主 APK 上直接找（OS2 / 或 OS3 类仍在主 APK），
     * 以及挂插件加载器（OS3 的类在插件里，得等插件加载完才有 ClassLoader）。
     */
    static void installSystemUi(DouyinModule host, ClassLoader loader) {
        int hooked = hookWhitelist(host, loader);
        hookPluginFactory(host, loader);
        host.log(Log.INFO, DouyinModule.TAG,
                "focus unlock (systemui) installed, direct hooks=" + hooked);
    }

    /**
     * 挂 {@code PluginInstance$PluginFactory.createPluginContext}，
     * 在它返回后取出插件 ClassLoader，再去插件里 hook 同一组类。
     *
     * 这一步必须 proceed 拿真实结果 —— 插件上下文是 SystemUI 自己要用的，
     * 换成假对象会把系统界面搞坏。
     */
    private static void hookPluginFactory(DouyinModule host, ClassLoader loader) {
        try {
            Class<?> factory = Class.forName(PLUGIN_FACTORY_CLASS, false, loader);
            Method[] methods = factory.getDeclaredMethods();
            int count = 0;
            for (Method method : methods) {
                if (!"createPluginContext".equals(method.getName())) {
                    continue;
                }
                host.hook(method)
                        .setId("focus-unlock-plugin-factory")
                        .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            Object result = chain.proceed();
                            try {
                                // createPluginContext 返回插件 Context，它的 classLoader
                                // 就是加载 MIUISystemUIPlugin 的那个
                                ClassLoader pluginLoader = result instanceof android.content.Context
                                        ? ((android.content.Context) result).getClassLoader()
                                        : null;
                                if (pluginLoader != null) {
                                    hookWhitelist(host, pluginLoader);
                                }
                            } catch (Throwable error) {
                                host.log(Log.WARN, DouyinModule.TAG,
                                        "plugin class loader hook failed", error);
                            }
                            return result;
                        });
                count++;
            }
            if (count == 0) {
                host.log(Log.INFO, DouyinModule.TAG,
                        "createPluginContext not found in " + PLUGIN_FACTORY_CLASS);
            } else {
                host.log(Log.INFO, DouyinModule.TAG,
                        "waiting for focus plugin class loader (" + count + " entry)");
            }
        } catch (ClassNotFoundException absent) {
            // 老系统没有这个类，正常
            host.log(Log.DEBUG, DouyinModule.TAG, "plugin factory absent");
        } catch (Throwable error) {
            host.log(Log.WARN, DouyinModule.TAG, "failed to hook plugin factory", error);
        }
    }

    /**
     * 在给定的 ClassLoader 里找白名单方法和签名检查，全部强制返回 true。
     *
     * @return 这次新增的 hook 数；同一个 ClassLoader 重复调用返回 0
     */
    private static int hookWhitelist(DouyinModule host, ClassLoader loader) {
        if (loader == null || HOOKED_LOADERS.contains(loader)) {
            return 0;
        }
        int hooked = 0;

        // 白名单：不限定参数签名，只要求返回 boolean。
        // 不同 OS 版本参数表不一样（canShowFocus(Context, String) 等），
        // 按名字 + 返回类型匹配比硬编码签名稳。
        for (String className : WHITELIST_CLASSES) {
            try {
                Class<?> type = Class.forName(className, false, loader);
                for (Method method : type.getDeclaredMethods()) {
                    String name = method.getName();
                    if (!"canShowFocus".equals(name) && !"canCustomFocus".equals(name)) {
                        continue;
                    }
                    // 编译期生成的桥接/合成方法同名但语义不同，hook 它没意义
                    if (method.isSynthetic() || method.isBridge()) {
                        continue;
                    }
                    if (method.getReturnType() != boolean.class) {
                        continue;
                    }
                    String signature = name + "(" + joinParams(method) + ")";
                    host.hook(method)
                            .setId("focus-unlock-whitelist-" + signature)
                            .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                if (WHITELIST_HIT.compareAndSet(false, true)) {
                                    host.log(Log.INFO, DouyinModule.TAG,
                                            "whitelist hook active (first hit): " + signature);
                                }
                                return Boolean.TRUE;
                            });
                    host.log(Log.INFO, DouyinModule.TAG,
                            "hooked " + signature + " in " + className);
                    hooked++;
                }
            } catch (ClassNotFoundException absent) {
                // 该 ClassLoader 下没有这个类：可能在插件里，或新旧系统二选一
            } catch (Throwable error) {
                host.log(Log.WARN, DouyinModule.TAG,
                        "whitelist hook failed for " + className, error);
            }
        }

        // 签名检查（仅 OS3 插件）。不绕过这道，白名单过了也会被签名拦下。
        try {
            Class<?> checker = Class.forName(SIGNATURE_CHECKER_CLASS, false, loader);
            for (Method method : checker.getDeclaredMethods()) {
                if (!"checkSignatures".equals(method.getName())) {
                    continue;
                }
                if (method.getReturnType() != boolean.class) {
                    continue;
                }
                host.hook(method)
                        .setId("focus-unlock-signature")
                        .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            host.log(Log.DEBUG, DouyinModule.TAG, "checkSignatures overridden");
                            return Boolean.TRUE;
                        });
                host.log(Log.INFO, DouyinModule.TAG, "hooked SignatureChecker.checkSignatures");
                hooked++;
            }
        } catch (ClassNotFoundException absent) {
            // 只有新版焦点通知插件才有，正常
        } catch (Throwable error) {
            host.log(Log.WARN, DouyinModule.TAG, "signature checker hook failed", error);
        }

        if (hooked > 0) {
            HOOKED_LOADERS.add(loader);
        }
        return hooked;
    }

    // ------------------------------------------------------------------
    // XMSF：焦点授权会话
    // ------------------------------------------------------------------

    /**
     * XMSF 进程入口。
     *
     * OS3 上白名单和签名都过了还不行 —— 还要 xms 授权服务点头。
     * {@code AuthSession.b(Throwable)} 是授权失败回调，这里把错误对象里的
     * 错误码（字段 {@code a}）改成 0，再调成功回调 {@code h()}，等于告诉
     * 系统"授权通过了"。
     */
    static void installXmsf(DouyinModule host, ClassLoader loader) {
        try {
            Class<?> authSession = Class.forName(AUTH_SESSION_CLASS, false, loader);
            Method target = null;
            for (Method method : authSession.getDeclaredMethods()) {
                // b(error)：单参数、名字叫 b —— 混淆后的名字，只能按形状找
                if ("b".equals(method.getName()) && method.getParameterCount() == 1) {
                    target = method;
                    break;
                }
            }
            if (target == null) {
                host.log(Log.INFO, DouyinModule.TAG,
                        "method b(error) not found in " + AUTH_SESSION_CLASS);
                return;
            }
            // b() 的返回类型不确定（可能是 void，也可能返回 boolean）。
            // 对 void 方法必须返回 null，返回非 null 会让 libxposed 类型不匹配，
            // 异常被 PROTECTIVE 吞掉后功能静默失效 —— 所以先记住类型再决定。
            final Class<?> authReturnType = target.getReturnType();
            host.hook(target)
                    .setId("focus-unlock-auth")
                    .setExceptionMode(DouyinModule.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        List<Object> args = chain.getArgs();
                        if (args == null || args.isEmpty()) {
                            return chain.proceed();
                        }
                        Object error = args.get(0);
                        if (error == null) {
                            return chain.proceed();
                        }
                        try {
                            int original = readIntField(error, "a");
                            host.log(Log.INFO, DouyinModule.TAG,
                                    "auth error intercepted, original code=" + original
                                            + ", forcing 0");
                            writeField(error, "a", 0);
                            // h() 找不到或调用失败会抛，由下面的 catch 兜住走原逻辑。
                            // 这里不能把"调用失败"当成"成功但返回 null" —— 那会
                            // 错误地跳掉原始的失败回调。
                            Object success = callNoArg(chain.getThisObject(), "h");
                            host.log(Log.INFO, DouyinModule.TAG, "auth bypassed");
                            if (authReturnType == void.class) {
                                return null;
                            }
                            return success != null ? success : Boolean.TRUE;
                        } catch (Throwable failed) {
                            // 绕过失败就老老实实走原逻辑，别把 XMSF 搞崩
                            host.log(Log.WARN, DouyinModule.TAG,
                                    "auth bypass failed, fall back", failed);
                            return chain.proceed();
                        }
                    });
            host.log(Log.INFO, DouyinModule.TAG, "hooked AuthSession.b(error)");
        } catch (ClassNotFoundException absent) {
            host.log(Log.INFO, DouyinModule.TAG, "AuthSession absent");
        } catch (Throwable error) {
            host.log(Log.WARN, DouyinModule.TAG, "xmsf hook failed", error);
        }
    }

    // ------------------------------------------------------------------
    // 反射小工具（字段/方法名都被混淆了，只能沿继承链找）
    // ------------------------------------------------------------------

    private static int readIntField(Object instance, String name) {
        Object value = findField(instance, name);
        return value instanceof Integer ? (Integer) value : 0;
    }

    private static void writeField(Object instance, String name, Object value)
            throws IllegalAccessException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(instance, value);
                return;
            } catch (NoSuchFieldException absent) {
                type = type.getSuperclass();
            }
        }
    }

    private static Object findField(Object instance, String name) {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException absent) {
                type = type.getSuperclass();
            } catch (IllegalAccessException denied) {
                return null;
            }
        }
        return null;
    }

    /**
     * 调用无参方法。
     *
     * 找不到方法、或者调用本身抛异常，都向上抛 —— 让调用方走「fall back 到原逻辑」。
     * 不能返回 null 冒充成功：调用方拿 null 会以为"回调返回空"而跳掉原始流程。
     */
    private static Object callNoArg(Object instance, String name) throws Throwable {
        if (instance == null) {
            throw new NoSuchMethodException(name + " (null receiver)");
        }
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method.invoke(instance);
            } catch (NoSuchMethodException absent) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static String joinParams(Method method) {
        StringBuilder builder = new StringBuilder();
        Class<?>[] types = method.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(types[i].getSimpleName());
        }
        return builder.toString();
    }
}

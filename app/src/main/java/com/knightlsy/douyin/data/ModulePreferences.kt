package com.knightlsy.douyin.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * 增强模块（抖仙人 / LSPosed）的偏好与状态探测。
 *
 * ## 为什么不强制
 * 本应用的核心功能是粘贴链接下载，完全不需要模块。模块只是多一个数据来源：
 * 在抖音里刷到喜欢的作品，模块直接下载，记录同步到这里。
 * 所以联动默认**关闭**，用户主动打开才生效 —— 不装模块的人不该被这个功能打扰。
 *
 * ## 状态怎么判断
 * - [ModuleState.NOT_INSTALLED]：包管理器里查不到 com.zz.douyin
 * - [ModuleState.INSTALLED]：装了，但读不到数据
 * - [ModuleState.ACTIVE]：能读到模块暴露的数据接口
 *
 * **ACTIVE 不等于「在抖音里已经生效」**。Provider 跑在模块 App 自己的进程里，
 * 只要 App 装着、进程起得来，query 就能返回游标 —— 跟 LSPosed 里有没有启用、
 * 作用域有没有勾抖音无关。那两件事只有真的下载一次才知道。
 * 界面上因此不写「已连接」，只写「已就绪」。
 *
 * ## 超时
 * ContentResolver.query 是跨进程 binder 调用，**没有内建超时**。
 * 对端活着但卡住时，它既不抛异常也不返回 —— 调用方永远等下去。
 * 所以这里用线程池 + Future.get(超时) 主动放弃等待。
 * 注意 binder 调用不响应中断，被放弃的线程仍占着，这是已知代价：
 * 池子固定 2 个线程，最坏情况两个都卡住，之后的探测一律超时降级为
 * INSTALLED —— 对可选功能来说，退化成「读不到」比拖住调用方要好。
 */
enum class ModuleState { NOT_INSTALLED, INSTALLED, ACTIVE }

object ModulePreferences {

    private const val TAG = "ModulePreferences"

    /** 模块包名。改这里要同步改 SharedProvider 的白名单。 */
    const val MODULE_PACKAGE = "com.zz.douyin"

    /**
     * 模块 Provider 的 authority。
     *
     * **固定值**，不跟包名拼接：模块 manifest 里写死 com.zz.douyin.shared，
     * applicationIdSuffix=".debug" 只改包名不改 authority。
     * 按包名拼会在 debug 变体下打到不存在的 authority，整条链路静默断掉。
     */
    const val HISTORY_URI = "content://com.zz.douyin.shared/history"

    /** 模块的下载页。走 GitHub Releases —— 用户自己装到 LSPosed 里。 */
    const val MODULE_URL = "https://github.com/knightlsy/douyim/releases/latest"

    /** 模块的所有可能包名：release 变体 + debug 变体（applicationIdSuffix=".debug"）。 */
    private val MODULE_PACKAGES = listOf(MODULE_PACKAGE, "$MODULE_PACKAGE.debug")

    private const val PREFS = "module_prefs"
    private const val KEY_ENABLED = "enhanced_enabled"

    /** 跨进程探测超时。正常 query 毫秒级，3 秒已非常宽松。 */
    private const val PROBE_TIMEOUT_MS = 3_000L

    /** 见类注释「超时」：固定 2 线程，卡死时的影响是有界的。 */
    private val PROBE = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "module-probe").apply { isDaemon = true }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 联动是否开启。默认 false —— 可选功能，不默认替用户做主。
     */
    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 模块 App 是否已安装（不代表 LSPosed 里启用了）。
     *
     * 两个变体都查：正式包和 debug 包（applicationIdSuffix=".debug"）。
     * 注意 Manifest 里必须声明 <queries>，否则 Android 11+ 一律查不到 ——
     * 那种情况下这里会误报「未安装」，而模块其实装得好好的。
     */
    fun isInstalled(context: Context): Boolean = installedPackage(context) != null

    /** 返回实际装着的那个包名，没装返回 null。 */
    fun installedPackage(context: Context): String? {
        for (name in MODULE_PACKAGES) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        name, PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(name, 0)
                }
                return name
            } catch (error: PackageManager.NameNotFoundException) {
                // 继续试下一个
            }
        }
        return null
    }

    /**
     * 探测模块的数据接口是否可读。
     *
     * 真的去 query 一次 —— 只有能拿到游标才算 ACTIVE。带超时，理由见类注释。
     */
    fun state(context: Context): ModuleState {
        if (!isInstalled(context)) return ModuleState.NOT_INSTALLED
        val readable = try {
            PROBE.submit<Boolean> {
                try {
                    context.contentResolver
                        .query(Uri.parse(HISTORY_URI), null, null, null, null)
                        ?.use { true } ?: false
                } catch (error: Exception) {
                    // 打完整信息：这一步失败的原因有好几种，而界面上只有
                    // 「读不到数据」四个字，不给日志根本分不清是哪一种 ——
                    // authority 不存在（模块版本旧）/ 权限被拒 / 模块进程挂了。
                    Log.w(TAG, "query 失败 uri=$HISTORY_URI", error)
                    false
                }
            }.get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            Log.w(TAG, "模块探测超时或提交失败", error)
            false
        }
        return if (readable) ModuleState.ACTIVE else ModuleState.INSTALLED
    }

    /**
     * 状态的中文描述，直接用在设置页。
     *
     * ACTIVE 写「已就绪」而不是「已连接」：它只证明数据接口能读，
     * 不证明 LSPosed 启用了、抖音重启过了 —— 那一步只有真下载一次才知道。
     */
    fun describe(state: ModuleState): String = when (state) {
        ModuleState.ACTIVE -> "已就绪"
        ModuleState.INSTALLED -> "已安装 · 读不到数据"
        ModuleState.NOT_INSTALLED -> "未安装"
    }
}

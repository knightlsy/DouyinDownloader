package com.knightlsy.douyin.data

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * 增强模块（抖仙人 / LSPosed）的偏好与状态探测。
 *
 * ## 为什么不强制
 * 本应用的核心功能是粘贴链接下载，完全不需要模块。模块只是多一个数据来源：
 * 在抖音里刷到喜欢的作品，模块直接下载，记录同步到这里。
 * 所以联动默认**关闭**，用户主动打开才生效 —— 不装模块的人不该被这个功能打扰。
 *
 * ## 状态怎么判断
 * - `NOT_INSTALLED`：包管理器里查不到 com.zz.douyin
 * - `INSTALLED`：装了，但读取不到数据（LSPosed 没启用 / 作用域没勾抖音 / 抖音没重启）
 * - `ACTIVE`：Provider 能返回游标，说明整条链路通了
 */
enum class ModuleState { NOT_INSTALLED, INSTALLED, ACTIVE }

object ModulePreferences {

    /** 模块包名。改这里要同步改 SharedProvider 的白名单。 */
    const val MODULE_PACKAGE = "com.zz.douyin"

    /** 模块的所有可能包名：release 变体 + debug 变体（applicationIdSuffix=".debug"）。 */
    private val MODULE_PACKAGES = listOf(MODULE_PACKAGE, "$MODULE_PACKAGE.debug")

    /** 模块的下载页。走 GitHub Releases —— 用户自己装到 LSPosed 里。 */
    const val MODULE_URL = "https://github.com/knightlsy/douyim/releases/latest"

    private const val PREFS = "module_prefs"
    private const val KEY_ENABLED = "enhanced_enabled"

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
     * 探测完整链路是否可用。
     *
     * 真的去查一次 Provider —— 只有能拿到游标才算 ACTIVE。
     * 装了模块但没在 LSPosed 里启用时，Provider 仍然存在（它是模块 App 自己声明的），
     * 所以「能查到包」不等于「能用」，必须实际读一次。
     */
    fun state(context: Context): ModuleState {
        val pkg = installedPackage(context) ?: return ModuleState.NOT_INSTALLED
        val uri = Uri.parse("content://$pkg.shared/history")
        val cursor = try {
            context.contentResolver.query(uri, null, null, null, null)
        } catch (error: Exception) {
            null
        }
        cursor?.use { return ModuleState.ACTIVE }
        return ModuleState.INSTALLED
    }

    /** 状态的中文描述，直接用在设置页。 */
    fun describe(state: ModuleState): String = when (state) {
        ModuleState.ACTIVE -> "已连接"
        ModuleState.INSTALLED -> "已安装 · 未激活"
        ModuleState.NOT_INSTALLED -> "未安装"
    }
}

package com.knightlsy.douyin.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 主题模式：跟随系统 / 强制浅色 / 强制深色。
 *
 * 原实现只存一个 Boolean（dark_mode），没有「跟随系统」这个状态，
 * 因此无法适配系统深色模式切换。改为存模式枚举：
 *  - SYSTEM：跟随系统（默认）
 *  - LIGHT / DARK：用户手动指定
 *
 * 兼容旧值：老版本存过 dark_mode=true/false，迁移为 DARK/LIGHT。
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val modeKey = "theme_mode"
    private val legacyKey = "dark_mode"

    fun mode(): ThemeMode {
        val raw = prefs.getString(modeKey, null)
        if (raw != null) {
            return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
        }
        // 旧版本（v1.6.6 及以前）只存一个 Boolean dark_mode，且它没有「跟随系统」这个概念：
        // 那个值只是「上次渲染时是不是深色」，不是用户的明确选择（默认就是浅色）。
        // 因此迁移时一律转为 SYSTEM（跟随系统），而不是把它当成用户锁定了浅色/深色 ——
        // 否则升级后系统切深色，App 会一直卡在浅色，看起来就是「不跟随系统」。
        if (prefs.contains(legacyKey)) {
            setMode(ThemeMode.SYSTEM)
            prefs.edit().remove(legacyKey).apply()
            return ThemeMode.SYSTEM
        }
        return ThemeMode.SYSTEM
    }

    fun setMode(mode: ThemeMode) {
        prefs.edit().putString(modeKey, mode.name).apply()
    }

    /** 旧接口保留（其他调用点若仍引用不会编译失败）。 */
    fun isDarkMode(): Boolean = mode() == ThemeMode.DARK

    fun setDarkMode(dark: Boolean) = setMode(if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
}

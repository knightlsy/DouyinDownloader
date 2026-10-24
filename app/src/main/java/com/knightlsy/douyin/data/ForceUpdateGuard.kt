package com.knightlsy.douyin.data

import android.content.Context
import android.util.Log

/**
 * 强制更新的「逃生口」。
 *
 * 强制更新有一个绕不过去的结构性风险：一旦更新链路本身坏了
 * （服务端异常、所有镜像被劫持、DownloadManager 在这台机器上不工作），
 * 用户就被永久锁在更新界面里 —— 旧版进不去，新版装不上。
 *
 * 这里给一条受限的退路：连续失败 3 次后，放行 24 小时。
 * 放行期间照常提醒用户还有新版本，只是不再挡住整个应用。
 * 24 小时后重新开始强制。
 *
 * 为什么不直接给「跳过」按钮：
 *   有按钮就等于可选更新，用户第一次看到就会点，强制更新形同虚设。
 *   计数式放行只在「真的装不上」时才会触发。
 */
object ForceUpdateGuard {

    private const val TAG = "ForceUpdateGuard"
    private const val PREFS = "force_update_guard"

    private const val KEY_FAILS = "consecutive_failures"
    private const val KEY_PASS_UNTIL = "pass_until"
    private const val KEY_PASS_VERSION = "pass_version"

    /** 连续失败多少次后放行。 */
    private const val FAIL_THRESHOLD = 3

    /** 放行时长。 */
    private const val PASS_DURATION_MS = 24L * 60 * 60 * 1000

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * 记录一次失败。达到阈值时开启放行窗口。
     * @return 是否刚刚触发了放行（调用方可以据此提示用户）
     */
    fun recordFailure(context: Context, version: String): Boolean {
        val p = prefs(context)
        val fails = p.getInt(KEY_FAILS, 0) + 1
        p.edit().putInt(KEY_FAILS, fails).apply()
        Log.w(TAG, "update failure #$fails (version=$version)")

        if (fails >= FAIL_THRESHOLD) {
            val until = System.currentTimeMillis() + PASS_DURATION_MS
            p.edit()
                .putLong(KEY_PASS_UNTIL, until)
                .putString(KEY_PASS_VERSION, version)
                .apply()
            Log.w(TAG, "entering 24h pass window until $until")
            return true
        }
        return false
    }

    /** 更新成功时清零，下次强制更新重新从 0 开始计数。 */
    fun recordSuccess(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAILS)
            .remove(KEY_PASS_UNTIL)
            .remove(KEY_PASS_VERSION)
            .apply()
    }

    /** 当前是否处于放行窗口内。 */
    fun isPassed(context: Context, version: String): Boolean {
        val p = prefs(context)
        val until = p.getLong(KEY_PASS_UNTIL, 0L)
        if (until <= 0L) return false
        // 版本变了就作废：新版本可能已经修好了更新链路
        val passVersion = p.getString(KEY_PASS_VERSION, "") ?: ""
        if (passVersion.isNotEmpty() && passVersion != version) return false
        if (System.currentTimeMillis() >= until) {
            // 窗口过期，顺手清掉计数，重新开始强制
            p.edit().remove(KEY_PASS_UNTIL).remove(KEY_FAILS).apply()
            return false
        }
        return true
    }

    /** 放行窗口还剩多少分钟。 */
    fun remainingMinutes(context: Context): Long {
        val until = prefs(context).getLong(KEY_PASS_UNTIL, 0L)
        if (until <= 0L) return 0L
        val left = until - System.currentTimeMillis()
        return if (left <= 0L) 0L else left / 60000L
    }
}

package com.knightlsy.douyin.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用内日志仓库：供「设置 → 日志」页面查看/复制/清空。
 *
 * 与 ParseDiag 的分工：
 *  - ParseDiag 只保留最近一次解析会话的 12 条环节信息，失败时自动带进报错文本；
 *  - LogStore 是跨会话的完整流水（内存环形，最多 [MAX_LINES] 条），
 *    由用户主动打开日志页查看，用于排查偶发失败。
 *
 * 开关持久化在 SharedPreferences，默认开启 —— 偶发问题只有先记下来，
 * 事后才有得查；开关只是给在意隐私/性能的用户一个关掉它的选择。
 */
object LogStore {

    private const val PREFS = "log_store_prefs"
    private const val KEY_ENABLED = "log_enabled"
    private const val MAX_LINES = 500

    private val lines = ArrayDeque<String>()
    private var prefs: SharedPreferences? = null

    @Volatile
    var enabled: Boolean = true
        private set

    /** 在 Application.onCreate 里调用一次，之后 log() 才真正落日志。 */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        if (value) {
            log("log", "日志开关已开启")
        } else {
            // 关闭时记最后一条再停写：写之前 enabled 已经是 false，
            // 所以这里手动追加，保证用户能看到「关闭」这个动作本身。
            append("log", "日志开关已关闭")
        }
    }

    fun isEnabled(): Boolean = enabled

    /** 写一条日志。开关关闭时直接丢弃。 */
    fun log(tag: String, msg: String) {
        if (!enabled) return
        append(tag, msg)
    }

    private fun append(tag: String, msg: String) {
        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val line = "$ts [$tag] $msg"
        synchronized(lines) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) lines.removeFirst()
        }
    }

    /** 导出全部日志（时间正序，换行分隔）；为空返回空字符串。 */
    fun dump(): String = synchronized(lines) { lines.joinToString("\n") }

    /** 清空全部日志。 */
    fun clear() {
        synchronized(lines) { lines.clear() }
    }

    fun lineCount(): Int = synchronized(lines) { lines.size }
}

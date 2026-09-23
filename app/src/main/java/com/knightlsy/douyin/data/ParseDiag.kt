package com.knightlsy.douyin.data

/**
 * 解析诊断单例：记录最近一次解析各环节的耗时/结果，失败时把关键信息带给 UI。
 * 轻量实现（线程安全用 synchronized），仅用于排查，不影响正常流程。
 */
object ParseDiag {

    private val lines = ArrayDeque<String>()
    /** 会话开始时间戳，用于判断诊断信息是否属于本次解析 */
    @Volatile var sessionStart: Long = 0
        private set

    /** 开始一轮新的解析会话（清空旧记录） */
    @Synchronized
    fun startSession() {
        sessionStart = System.currentTimeMillis()
        lines.clear()
        log("开始解析")
    }

    @Synchronized
    fun log(msg: String) {
        val t = ((System.currentTimeMillis() - sessionStart) / 100) / 10.0
        lines.addLast("${t}s $msg")
        while (lines.size > 12) lines.removeFirst()
    }

    /** 取全部诊断文本（换行分隔），空会话返回 null */
    @Synchronized
    fun dump(): String? = if (sessionStart == 0L || lines.isEmpty()) null else lines.joinToString("\n")
}

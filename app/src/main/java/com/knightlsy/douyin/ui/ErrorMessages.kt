package com.knightlsy.douyin.ui

/**
 * 把技术异常翻译成人话。
 *
 * 之前是 `error = "解析失败: ${e.message}"`，直接把异常原文甩给用户 ——
 * 视频被作者删掉、设成私密、或者链接过期时，用户看到的是
 * 「HTTP响应状态异常」「HTTP 403」这种，既不知道发生了什么，也不知道要不要重试。
 *
 * 这里只翻译能确定的几种（403/404/410 = 内容侧没了；超时/DNS = 网络侧），
 * 其余统一收敛成一句「稍后重试」，不再暴露原始异常文本 ——
 * 原文仍然通过 ParseDiag 进诊断（复制到剪贴板），排查能力不受影响。
 *
 * 为什么放在这里（顶层、独立文件）：
 * - 纯字符串映射，不依赖任何类状态，却困在 MainViewModel（God ViewModel）里，
 *   单测它得先构造整个 ViewModel。搬成顶层 internal fun 后可直接被单测覆盖。
 * - 放在 ui 包：它的产出就是给用户看的文案，属于 UI 层职责。
 */
internal fun humanizeParseError(raw: String?): String {
    if (raw.isNullOrBlank()) return "解析失败，请稍后重试"
    val m = raw.lowercase()
    return when {
        // 抖音对已删除/私密内容常见的几种返回
        m.contains("403") || m.contains("404") || m.contains("410") ->
            "视频已被作者删除，或已设为私密"
        m.contains("http") && (m.contains("状态") || m.contains("status") || m.contains("code")) ->
            "视频已被作者删除，或已设为私密"
        m.contains("timeout") || m.contains("timed out") || m.contains("超时") ->
            "请求超时，请检查网络后重试"
        m.contains("unable to resolve host") || m.contains("unknownhost") || m.contains("dns") ->
            "网络不可用，请检查网络连接"
        m.contains("connect") || m.contains("socket") || m.contains("network") ->
            "网络连接失败，请检查网络后重试"
        else -> "解析失败，请稍后重试"
    }
}

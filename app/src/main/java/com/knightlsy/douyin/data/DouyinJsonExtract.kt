package com.knightlsy.douyin.data

/**
 * 从 HTML / JSON 文本里按括号配对切出完整对象或数组。
 *
 * 纯字符串处理，不依赖网络、不依赖 Gson —— 这是最容易也最该写单测的一层。
 * 抖音改版时最先崩的就是这里，所以从 VideoRepository 里单独提出来。
 */
internal object DouyinJsonExtract {

    internal fun extractRouterData(html: String): String? {
        val marker = "window._ROUTER_DATA = "
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        return extractJsonObject(html, idx + marker.length)
    }

    internal fun extractItemList(html: String): String? {
        val idx = html.indexOf("\"item_list\"")
        if (idx < 0) return null
        var start = idx
        while (start < html.length && html[start] != '[') start++
        return extractJsonArray(html, start)
    }

    internal fun extractJsonObject(json: String, start: Int): String? {
        if (start >= json.length || json[start] != '{') return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until json.length) {
            val c = json[i]
            if (esc) { esc = false; continue }
            if (c == '\\' && inStr) { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (c == '{') depth++ else if (c == '}') { depth--; if (depth == 0) return json.substring(start, i + 1) }
        }
        return null
    }

    internal fun extractJsonArray(json: String, start: Int): String? {
        if (start >= json.length || json[start] != '[') return null
        var depth = 0; var inStr = false; var esc = false
        for (i in start until json.length) {
            val c = json[i]
            if (esc) { esc = false; continue }
            if (c == '\\' && inStr) { esc = true; continue }
            if (c == '"') { inStr = !inStr; continue }
            if (inStr) continue
            if (c == '[') depth++ else if (c == ']') { depth--; if (depth == 0) return json.substring(start, i + 1) }
        }
        return null
    }
}

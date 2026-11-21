package com.knightlsy.douyin.data

import android.util.Log
import java.util.regex.Pattern

/**
 * 把抖音各种来源的 JSON 解析成 [ContentInfo]。
 *
 * 两条路：正则直取 item_list（快），失败则 Gson 整树递归找（稳）。
 * RouterData 实测可达 47KB 且正则可能不命中，所以树解析是必需的兜底，不是冗余。
 */
internal object DouyinParser {
    /** 沿用 VideoRepo 这个 tag，保持日志过滤习惯不变。 */
    private const val TAG = "VideoRepo"

    private val gson by lazy { com.google.gson.Gson() }
    private val itemListPattern by lazy { Pattern.compile("\"item_list\"\\s*:\\s*\\[") }

    internal fun parseRouterData(json: String, contentId: String): ContentInfo? {
        val m = itemListPattern.matcher(json)
        if (m.find()) {
            val arr = extractJsonArray(json, m.end() - 1)
            val items = arr?.let {
                try { gson.fromJson(it, Array<ItemObj>::class.java) } catch (_: Exception) { null }
            }
            if (!items.isNullOrEmpty()) {
                // 优先取 aweme_id 等于目标 contentId 的 item（推荐流会混入其他视频）
                val target = items.firstOrNull { it.aweme_id == contentId }
                val firstValid = items.firstOrNull { obj ->
                    (!obj.video?.play_addr?.url_list.isNullOrEmpty()) ||
                        (!obj.images.isNullOrEmpty() && obj.images!!.any { it.url_list.isNotEmpty() })
                }
                val chosen = target ?: firstValid ?: items[0]
                Log.d(TAG, "Router item: chosen=${chosen.aweme_id} target=${target != null} total=${items.size}")
                val info = chosen.toContentInfo()
                if (info != null) return info
                // 继续落树兜底
                ParseDiag.log("正则分支item无效(${chosen.aweme_id == contentId}), 走树解析")
            }
        }
        // 正则没命中 item_list（结构变化/嵌套转义），退化为 gson 整体解析后递归找数据块。
        // 2026-09 实测 App 端 routerData 可达 47KB 且正则不命中，此兜底能直接定位 item_list /
        // aweme_detail / videoInfoRes 等任意嵌套层级里的数据。
        return parseJsonTree(json, contentId)
    }

    /** 深度优先: 找 aweme_id 匹配 contentId 的 item；找不到再退回第一个有效 item */
    internal fun findItemInTree(el: com.google.gson.JsonElement?, contentId: String): com.google.gson.JsonObject? {
        if (el == null || !el.isJsonObject) {
            if (el != null && el.isJsonArray) {
                for (e in el.asJsonArray) findItemInTree(e, contentId)?.let { return it }
            }
            return null
        }
        val obj = el.asJsonObject
        val id = obj.get("aweme_id")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        if (id.isNotEmpty() && (obj.has("video") || obj.has("images"))) {
            // 第一优先: 精确匹配目标视频/图集（推荐流里会有大量其他视频的 item）
            if (id == contentId) return obj
        }
        // 命中 item_list/aweme_detail 容器时优先进入
        for (key in listOf("item_list", "aweme_detail", "aweme_details", "videoInfoRes", "items")) {
            obj.get(key)?.let { child -> findItemInTree(child, contentId)?.let { return it } }
        }
        // 无命中则遍历所有子节点
        for ((_, v) in obj.entrySet()) findItemInTree(v, contentId)?.let { return it }
        return null
    }

    /**
     * 两轮查找: 先精确匹配 contentId，失败再放宽到第一个带 url 的 item。
     * 防止推荐流 item（url 被风控剥空）误匹配导致 toContentInfo 返回 null。
     */
    internal fun parseJsonTree(json: String, contentId: String): ContentInfo? = try {
        val root = gson.fromJson(json, com.google.gson.JsonElement::class.java)
        // 第一轮: 精确 id
        findItemInTree(root, contentId)?.let { obj ->
            val item = gson.fromJson(obj, ItemObj::class.java)
            item.toContentInfo()?.let {
                Log.d(TAG, "Tree item (exact): id=${item.aweme_id}")
                return it
            }
            // url 被剥空的精确节点: 记录诊断
            ParseDiag.log("目标item存在但无url(风控剥空?)")
            null
        }
            // 第二轮: 任一有效 item
            ?: run {
                findAnyValidItem(root)?.let { obj ->
                    val item = gson.fromJson(obj, ItemObj::class.java)
                    Log.d(TAG, "Tree item (fallback): id=${item.aweme_id}")
                    item.toContentInfo()
                } ?: run { ParseDiag.log("树解析: 无任何有效url的item"); null }
            }
    } catch (e: Exception) {
        ParseDiag.log("树解析异常: ${e.message?.take(50)}")
        null
    }

    /** 找第一个能转出有效内容的 item（url_list 非空） */
    internal fun findAnyValidItem(el: com.google.gson.JsonElement?): com.google.gson.JsonObject? {
        if (el == null) return null
        if (el.isJsonArray) {
            for (e in el.asJsonArray) findAnyValidItem(e)?.let { return it }
            return null
        }
        if (!el.isJsonObject) return null
        val obj = el.asJsonObject
        val id = obj.get("aweme_id")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
        if (id.isNotEmpty()) {
            val hasUrlVideo = (obj.getAsJsonObject("video")?.getAsJsonObject("play_addr")
                ?.getAsJsonArray("url_list")?.size() ?: 0) > 0
            val hasUrlImages = obj.getAsJsonArray("images")?.any { img ->
                ((img as? com.google.gson.JsonObject)?.getAsJsonArray("url_list")?.size() ?: 0) > 0
            } == true
            if (hasUrlVideo || hasUrlImages) return obj
        }
        for (key in listOf("item_list", "aweme_detail", "aweme_details", "videoInfoRes", "items")) {
            obj.get(key)?.let { child -> findAnyValidItem(child)?.let { return it } }
        }
        for ((_, v) in obj.entrySet()) findAnyValidItem(v)?.let { return it }
        return null
    }

    internal fun parseItemList(json: String, contentId: String): ContentInfo? {
        try {
            val arr = gson.fromJson(json, Array<ItemObj>::class.java)
            if (arr.isNotEmpty()) {
                // 优先目标 id，其次第一个带有效 url 的 item
                val target = arr.firstOrNull { it.aweme_id == contentId }
                val firstValid = arr.firstOrNull { obj ->
                    (!obj.video?.play_addr?.url_list.isNullOrEmpty()) ||
                        (!obj.images.isNullOrEmpty() && obj.images!!.any { it.url_list.isNotEmpty() })
                }
                val chosen = target ?: firstValid ?: arr[0]
                Log.d(TAG, "Array item: chosen=${chosen.aweme_id} target=${target != null} total=${arr.size}")
                chosen.toContentInfo()?.let { return it }
            }
        } catch (_: Exception) {}
        try {
            val obj = gson.fromJson(json, ItemObj::class.java)
            if (obj.aweme_id.isNotEmpty()) return obj.toContentInfo()
        } catch (_: Exception) {}
        try {
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            // WebView 通道新增来源：iteminfo 的 {item_list:[...]}、slidesinfo 可能的 {aweme_details:[...]}
            for (key in listOf("item_list", "aweme_details", "aweme_list")) {
                val arrEl = root.getAsJsonArray(key) ?: continue
                if (arrEl.size() > 0) {
                    val obj = gson.fromJson(arrEl[0], ItemObj::class.java)
                    if (obj.aweme_id.isNotEmpty()) {
                        Log.d(TAG, "Wrapped item ($key): id=${obj.aweme_id}")
                        return obj.toContentInfo()
                    }
                }
            }
            val detail = root.getAsJsonObject("aweme_detail") ?: return parseJsonTree(json, contentId)
            val obj = gson.fromJson(detail, ItemObj::class.java)
            return obj.toContentInfo()
        } catch (_: Exception) {}
        return parseJsonTree(json, contentId)
    }
}

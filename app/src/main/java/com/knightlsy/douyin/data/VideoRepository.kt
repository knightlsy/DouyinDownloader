package com.knightlsy.douyin.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class VideoRepository {
    companion object {
        private const val TAG = "VideoRepo"
        private const val MAX_RETRY = 3
        private const val RETRY_DELAY = 1000L
        private const val BUFFER_SIZE = 65536
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .build()

    private val baseHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Xiaomi 14 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
        "Accept-Encoding" to "identity",
        "Connection" to "keep-alive",
        "Sec-Fetch-Dest" to "document",
        "Sec-Fetch-Mode" to "navigate",
        "Sec-Fetch-Site" to "none",
        "Upgrade-Insecure-Requests" to "1"
    )

    // ttwid 是抖音分享页必需的 Cookie，缺失时服务端返回空的 item_list（伪装成视频不存在）
    @Volatile private var cachedTtwid: String? = null
    private val ttwidMutex = kotlinx.coroutines.sync.Mutex()

    private val ttwidClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchTtwid(forceRefresh: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!forceRefresh) cachedTtwid?.let { return@withContext it }
        // Mutex 防止并发场景下重复注册 ttwid
        ttwidMutex.withLock {
            if (!forceRefresh) cachedTtwid?.let { return@withContext it }
            try {
            val body = """
                {"region":"cn","aid":1768,"needFid":false,"service":"www.ixigua.com",
                 "migrate_info":{"ticket":"","source":"node"},"cbUrlProtocol":"https","union":true}
            """.trimIndent().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://ttwid.bytedance.com/ttwid/union/register/")
                .post(body)
                .header("User-Agent", baseHeaders["User-Agent"] ?: "Mozilla/5.0")
                .build()
            ttwidClient.newCall(request).execute().use { resp ->
                val ck = resp.headers("Set-Cookie")
                    .firstOrNull { it.startsWith("ttwid=") }
                    ?.substringBefore(";")
                    ?.takeIf { it.length > "ttwid=".length }
                if (ck != null) {
                    cachedTtwid = ck
                    Log.d(TAG, "ttwid registered: ${ck.take(24)}...")
                } else {
                    Log.w(TAG, "ttwid register: no Set-Cookie, code=${resp.code}")
                }
                ck
            }
        } catch (e: Exception) {
            Log.w(TAG, "ttwid fetch failed", e)
            null
        }
        }
    }

    private val videoIdPatterns = listOf(
        Pattern.compile("/video/(\\d+)"),
        Pattern.compile("/note/(\\d+)"),
        Pattern.compile("/slides/(\\d+)"),
        Pattern.compile("modal_id=(\\d+)"),
        Pattern.compile("aweme_id=(\\d+)")
    )

    suspend fun extractVideoId(url: String): String? = withContext(Dispatchers.IO) {
        try {
            var processedUrl = url.trim()
            if (processedUrl.contains("v.douyin.com") || processedUrl.contains("vm.tiktok.com")) {
                processedUrl = resolveShortUrl(processedUrl)
            }
            for (pattern in videoIdPatterns) {
                val matcher = pattern.matcher(processedUrl)
                if (matcher.find()) {
                    val id = matcher.group(1)
                    Log.d(TAG, "Extracted ID: $id")
                    return@withContext id
                }
            }
            Log.d(TAG, "No ID found in: $processedUrl")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Extract video ID failed", e)
            null
        }
    }

    suspend fun resolveShortUrl(shortUrl: String): String = withContext(Dispatchers.IO) {
        try {
            // client 已开 followRedirects，一次 GET 后 request.url 即为最终地址
            val request = Request.Builder().url(shortUrl.trim())
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()
            client.newCall(request).execute().use { response ->
                response.request.url.toString()
            }
        } catch (_: Exception) {
            shortUrl
        }
    }

    suspend fun getContentInfo(context: Context, contentId: String): ContentInfo? = withContext(Dispatchers.IO) {
        ParseDiag.startSession()
        // 其它路由（slides 等）解析不了时由末尾的 WebView 解析通道兜底
        val tryUrls = listOf(
            "https://m.douyin.com/share/video/$contentId/",
            "https://m.douyin.com/share/note/$contentId/"
        )

        // 最多两轮：第一轮用现有 ttwid，全部失败可能是 ttwid 过期，强制刷新后重试
        repeat(2) { attempt ->
            val ttwid = fetchTtwid(forceRefresh = attempt > 0)
            ParseDiag.log("HTML轮${attempt + 1} ttwid=${if (ttwid != null) "ok" else "无"}")
            for (sharePageUrl in tryUrls) {
                try {
                    Log.d(TAG, "Fetching: $sharePageUrl (attempt $attempt)")
                    val request = Request.Builder().url(sharePageUrl)
                        .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                        .apply {
                            ttwid?.let {
                                addHeader("Cookie", it)
                                addHeader("Referer", "https://www.douyin.com/")
                            }
                        }
                        .build()
                    val response = client.newCall(request).execute()
                    val html = response.use { it.body?.string() }

                    if (html.isNullOrEmpty() || html.length < 10000) {
                        Log.d(TAG, "HTML too short, skip")
                        ParseDiag.log("HTML过短(${html?.length ?: 0}B)")
                        continue
                    }

                    val routerData = extractRouterData(html)
                    if (routerData != null) {
                        // 诊断增强: 记录 routerData 内的关键数据块位置，判断是挖空页还是解析问题
                        val hasItemList = routerData.contains("\"item_list\"")
                        val hasVideoInfo = routerData.contains("videoInfoRes")
                        val hasDetail = routerData.contains("aweme_detail")
                        val hasEmpty = routerData.contains("\"item_list\":[]") || routerData.contains("\"item_list\": []")
                        ParseDiag.log("ROUTER_DATA ${routerData.length}B item_list=$hasItemList videoInfoRes=$hasVideoInfo detail=$hasDetail 空数组=$hasEmpty")
                        val result = parseRouterData(routerData, contentId)
                        if (result != null) {
                            Log.d(TAG, "SUCCESS from $sharePageUrl")
                            return@withContext result
                        }
                        ParseDiag.log("parseRouterData失败(gson/字段不匹配?)")
                    }

                    val itemData = extractItemList(html)
                    if (itemData != null) {
                        ParseDiag.log("item_list ${itemData.length}B")
                        val result = parseItemList(itemData, contentId)
                        if (result != null) {
                            Log.d(TAG, "SUCCESS (itemList) from $sharePageUrl")
                            return@withContext result
                        }
                    }

                    // 数据被挖空的典型特征：Cookie 失效/缺失，直接换下一轮刷新的 ttwid
                    if (html.contains("SYSTEM_ITEM_NOT_EXIST")) {
                        Log.w(TAG, "Empty item_list (SYSTEM_ITEM_NOT_EXIST) - ttwid invalid")
                        ParseDiag.log("item_list被挖空(NOT_EXIST)")
                        break
                    }

                    Log.d(TAG, "No parseable data in this page")
                    ParseDiag.log("HTML无可解析数据(${html.length}B)")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed: $sharePageUrl - ${e.message}")
                    ParseDiag.log("HTML异常: ${e.message?.take(40)}")
                }
            }
        }

        Log.w(TAG, "ALL URLS FAILED for $contentId")
        // HTML 静态解析两轮都失败（接口变更/数据被挖空），兜底走 WebView 解析通道
        ParseDiag.log("转WebView通道")
        try {
            Log.d(TAG, "Falling back to WebViewParser for $contentId")
            val json = WebViewParser(context).parse(contentId)
            if (json != null) {
                ParseDiag.log("WebView返回${json.length}B")
                parseRouterData(json, contentId)?.let {
                    Log.d(TAG, "SUCCESS from WebView (routerData)")
                    return@withContext it
                }
                parseItemList(json, contentId)?.let {
                    Log.d(TAG, "SUCCESS from WebView (itemList/detail)")
                    return@withContext it
                }
                Log.w(TAG, "WebView returned JSON but nothing parseable")
                ParseDiag.log("WebView JSON无法解析: ${json.take(60)}")
            } else {
                Log.w(TAG, "WebView parse returned null")
                ParseDiag.log("WebView返回空(超时/未命中接口)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebView fallback failed: ${e.message}")
            ParseDiag.log("WebView异常: ${e.message?.take(40)}")
        }

        Log.w(TAG, "getContentInfo ALL FAILED for $contentId")
        null
    }

    private fun extractRouterData(html: String): String? {
        val marker = "window._ROUTER_DATA = "
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        return extractJsonObject(html, idx + marker.length)
    }

    private fun extractItemList(html: String): String? {
        val idx = html.indexOf("\"item_list\"")
        if (idx < 0) return null
        var start = idx
        while (start < html.length && html[start] != '[') start++
        return extractJsonArray(html, start)
    }

    private fun extractJsonObject(json: String, start: Int): String? {
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

    private fun extractJsonArray(json: String, start: Int): String? {
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

    private val gson by lazy { com.google.gson.Gson() }
    private val itemListPattern by lazy { Pattern.compile("\"item_list\"\\s*:\\s*\\[") }

    private fun parseRouterData(json: String, contentId: String): ContentInfo? {
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
    private fun findItemInTree(el: com.google.gson.JsonElement?, contentId: String): com.google.gson.JsonObject? {
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
    private fun parseJsonTree(json: String, contentId: String): ContentInfo? = try {
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
    private fun findAnyValidItem(el: com.google.gson.JsonElement?): com.google.gson.JsonObject? {
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

    private fun parseItemList(json: String, contentId: String): ContentInfo? {
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

    data class ItemObj(
        val aweme_id: String = "", val desc: String = "", val aweme_type: Int = 0,
        val author: AuthorObj? = null, val video: VideoObj? = null,
        val images: List<ImageObj>? = null, val create_time: Long = 0,
        val statistics: StatisticsObj? = null
    )
    data class AuthorObj(val nickname: String = "")
    data class VideoObj(val play_addr: UrlObj? = null, val download_addr: UrlObj? = null, val cover: UrlObj? = null, val duration: Long = 0, val width: Int = 0, val height: Int = 0, val bit_rate: List<BitRateObj>? = null)
    data class UrlObj(val url_list: List<String> = emptyList())
    data class ImageObj(val url_list: List<String> = emptyList())
    data class BitRateObj(val gear_name: String = "", val bit_rate: Int = 0, val play_addr: UrlObj? = null)
    data class StatisticsObj(val digg_count: Long = 0, val comment_count: Long = 0, val share_count: Long = 0)

    private fun ItemObj.toContentInfo(): ContentInfo? {
        val authorName = this.author?.nickname ?: "未知"
        val title = this.desc.ifEmpty { "未命名" }

        val videoUrl = this.video?.play_addr?.url_list?.firstOrNull()
            ?: this.video?.download_addr?.url_list?.firstOrNull()
        val cover = this.video?.cover?.url_list?.firstOrNull() ?: ""
        val duration = this.video?.duration ?: 0

        if (!this.images.isNullOrEmpty()) {
            val imageUrls = this.images.mapNotNull { it.url_list.firstOrNull() }.filter { it.isNotEmpty() }
            if (imageUrls.isNotEmpty()) {
                Log.d(TAG, "ImageCollection: ${imageUrls.size} images")
                return ContentInfo.ImageCollection(this.aweme_id, title, authorName, imageUrls.first(), imageUrls, this.create_time)
            }
        }

        if (!videoUrl.isNullOrEmpty()) {
            Log.d(TAG, "Video: ${videoUrl.take(80)}")
            val qualities = mutableListOf<ContentInfo.QualityOption>()
            this.video?.bit_rate?.forEach { br ->
                val url = br.play_addr?.url_list?.firstOrNull()
                if (!url.isNullOrEmpty()) {
                    val label = when {
                        br.gear_name.isNotEmpty() -> br.gear_name
                        br.bit_rate >= 2000 -> "${br.bit_rate / 1000}k"
                        else -> "${br.bit_rate}"
                    }
                    qualities.add(ContentInfo.QualityOption(label, cleanUrl(url), br.bit_rate))
                }
            }
            if (qualities.isEmpty() && !videoUrl.isNullOrEmpty()) {
                qualities.add(ContentInfo.QualityOption("默认", cleanUrl(videoUrl)))
            }
            return ContentInfo.Video(this.aweme_id, title, authorName, cover, cleanUrl(videoUrl), duration, this.create_time,
                diggCount = this.statistics?.digg_count ?: 0,
                commentCount = this.statistics?.comment_count ?: 0,
                shareCount = this.statistics?.share_count ?: 0,
                width = this.video?.width ?: 0,
                height = this.video?.height ?: 0,
                qualities = qualities
            )
        }

        return null
    }

    private fun cleanUrl(url: String): String = url
        .replace("playwm", "play").replace("play=1", "play=0")
        .replace(Regex("ratio=\\d+"), "ratio=1080p")

    suspend fun downloadContent(
        context: Context, contentInfo: ContentInfo,
        onProgress: (Float) -> Unit, onFileSaved: (String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            when (contentInfo) {
                is ContentInfo.Video -> {
                    val r = downloadVideoWithRetry(context, contentInfo.videoUrl, "Douyin_Video_${contentInfo.id}", onProgress)
                    if (r != null) { onFileSaved(r); DownloadResult(true, listOf(r)) } else DownloadResult(false, error = "下载视频失败")
                }
                is ContentInfo.ImageCollection -> {
                    val r = downloadImages(context, contentInfo.imageUrls, "Douyin_${contentInfo.id}", onProgress, onFileSaved)
                    DownloadResult(r.isNotEmpty(), r, if (r.isEmpty()) "下载图片失败" else null)
                }
            }
        } catch (e: Exception) { DownloadResult(false, error = e.message) }
    }

    private suspend fun downloadVideoWithRetry(context: Context, url: String, fileName: String, onProgress: (Float) -> Unit): String? {
        var lastError: Exception? = null
        val ttwid = fetchTtwid()
        repeat(MAX_RETRY) { attempt ->
            try {
                val request = Request.Builder().url(url).apply {
                    baseHeaders.forEach { (k, v) -> addHeader(k, v) }
                    ttwid?.let { addHeader("Cookie", it) }
                }.build()
                val result = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    saveVideoFile(context, body.byteStream(), body.contentLength(), fileName, onProgress)
                }
                if (result != null) return result
            } catch (e: Exception) {
                lastError = e
                if (attempt < MAX_RETRY - 1) delay(RETRY_DELAY * (attempt + 1))
            }
        }
        throw lastError ?: Exception("Download failed")
    }

    private fun saveVideoFile(context: Context, inputStream: InputStream, contentLength: Long, fileName: String, onProgress: (Float) -> Unit): String? {
        val fullFileName = "${fileName}_${System.currentTimeMillis()}.mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fullFileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Douyin")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
            try {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                    FileOutputStream(pfd.fileDescriptor).use { writeWithProgress(inputStream, it, contentLength, onProgress) }
                }
                cv.clear(); cv.put(MediaStore.Video.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, cv, null, null)
                return uri.toString()
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Douyin").also { it.mkdirs() }
            val file = File(dir, fullFileName)
            FileOutputStream(file).use { writeWithProgress(inputStream, it, contentLength, onProgress) }
            return file.absolutePath
        }
    }

    private fun writeWithProgress(inputStream: InputStream, outputStream: OutputStream, totalSize: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        var totalRead = 0L; var lastUpdate = 0f; var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead); totalRead += bytesRead
            if (totalSize > 0) { val p = totalRead.toFloat() / totalSize.toFloat(); if (p - lastUpdate >= 0.01f || totalRead == totalSize) { onProgress(p); lastUpdate = p } }
        }
        outputStream.flush(); onProgress(1f)
    }

    private suspend fun downloadImages(context: Context, imageUrls: List<String>, baseName: String, onProgress: (Float) -> Unit, onFileSaved: (String) -> Unit): List<String> = coroutineScope {
        val results = mutableListOf<String>()
        // ttwid 下载前统一取一次，避免每张图重复请求
        val ttwid = fetchTtwid()
        imageUrls.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                try {
                    val r = downloadSingleImage(context, url, "${baseName}_${index + 1}", ttwid)
                    r?.let { synchronized(results) { results.add(it) }; onFileSaved(it) }
                    onProgress((index + 1).toFloat() / imageUrls.size); r
                } catch (_: Exception) { null }
            }
        }.awaitAll(); results.toList()
    }

    private suspend fun downloadSingleImage(context: Context, url: String, fileName: String, ttwid: String?): String? {
        var lastError: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val request = Request.Builder().url(url).apply {
                    baseHeaders.forEach { (k, v) -> addHeader(k, v) }
                    ttwid?.let { addHeader("Cookie", it) }
                }.build()
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    val ext = when { response.header("Content-Type")?.contains("png") == true -> "png"; response.header("Content-Type")?.contains("webp") == true -> "webp"; else -> "jpg" }
                    saveImageFile(context, body.byteStream(), fileName, ext)
                }
            } catch (e: Exception) { lastError = e; if (attempt < MAX_RETRY - 1) delay(RETRY_DELAY) }
        }
        throw lastError ?: Exception("Image download failed")
    }

    private fun saveImageFile(context: Context, inputStream: InputStream, fileName: String, extension: String): String? {
        val fullFileName = "${fileName}_${System.currentTimeMillis()}.$extension"
        val mimeType = when (extension) { "png" -> "image/png"; "webp" -> "image/webp"; else -> "image/jpeg" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fullFileName); put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Douyin"); put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
            try {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { pfd -> FileOutputStream(pfd.fileDescriptor).use { inputStream.copyTo(it, BUFFER_SIZE) } }
                cv.clear(); cv.put(MediaStore.Images.Media.IS_PENDING, 0); context.contentResolver.update(uri, cv, null, null)
                return uri.toString()
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Douyin").also { it.mkdirs() }
            val file = File(dir, fullFileName); FileOutputStream(file).use { inputStream.copyTo(it, BUFFER_SIZE) }; return file.absolutePath
        }
    }
}

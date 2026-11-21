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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
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
        // 256KB：64KB 时一次 write 要走 4 遍系统调用，视频下载会白占 CPU 时间片；
        // 256KB 把 write() 次数压到 1/4，对 UFS/eMMC 更友好。
        private const val BUFFER_SIZE = 262_144
    }

    /**
     * 图集并发上限。
     *
     * 原实现是 `imageUrls.mapIndexed { async(Dispatchers.IO) { … } }.awaitAll()` ——
     * async 不限并发，20 张图会同时开 20 条连接。三个后果：
     * 抖音 CDN 限流、每张 `body.bytes()` 全量驻留导致内存峰值、单张超时拖慢整体。
     * 但完全串行又慢。4 路是均衡点：带宽能吃满，连接数和内存也压得住。
     */
    private val imageSemaphore = Semaphore(4)

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
        "Referer" to "https://www.douyin.com/",
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

    /**
     * 从分享文案中抽出干净的短链地址。
     *
     * 抖音分享文案形如「4.35 复制打开抖音，看看【xxx的作品】… https://v.douyin.com/xxxxxx/ 09/09 :2pm …」，
     * 链接周围常粘着中文文案与时间戳。若把整段文本当 URL 请求，短链解跳必然失败。
     * 所以先用正则切出链接边界（以空白、中文字符或结尾为界），只保留链接本体。
     *
     * 匹配不到时返回 null，由调用方回退到 trim() 后的原文。
     */
    private fun extractShortLink(text: String): String? {
        val m = Pattern.compile(
            "https?://(?:v\\.douyin\\.com|www\\.douyin\\.com|www\\.iesdouyin\\.com|vm\\.tiktok\\.com)[^\\s\\u4e00-\\u9fff]*"
        ).matcher(text)
        return if (m.find()) m.group() else null
    }

    suspend fun extractVideoId(url: String): String? = withContext(Dispatchers.IO) {
        try {
            var processedUrl = url.trim()
            if (processedUrl.contains("v.douyin.com") || processedUrl.contains("vm.tiktok.com")) {
                // 优先用抽出的干净短链，抽不到才退回原文（兼容旧行为）
                extractShortLink(processedUrl)?.let {
                    Log.d(TAG, "Short link extracted from share text")
                    processedUrl = it
                }
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

    /**
     * 拉取 slidesinfo 接口（App 端数据源），返回原始 JSON 文本。
     *
     * 这是实况图与高分辨率图片的唯一来源 —— 分享页接口不返回
     * `images[i].video` 与 `live_photo_type`，主图也只给 1440x1920。
     *
     * 已知坑（都踩过）：
     * - `aweme_ids` 必须是 **[数字]** —— 加引号（`["123"]`）服务端会返回空
     *   （HTTP 200 但 `aweme_details` 为 null），实测 41898B vs 260B
     * - 用 `aweme_id` 单数同样返回 null
     * - 下载图片时不带 `Accept: image/jpeg` 会拿到 webp，导致 Motion Photo 合成被跳过
     */
    private fun fetchSlidesInfo(contentId: String, ttwid: String?): String? {
        return try {
            val idsParam = java.net.URLEncoder.encode("[$contentId]", "UTF-8")
            val url = "https://www.douyin.com/web/api/v2/aweme/slidesinfo/" +
                "?aweme_ids=$idsParam&aid=6383&device_platform=webapp" +
                "&version_code=190500&version_name=19.5.0"
            val request = Request.Builder().url(url)
                .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .apply {
                    ttwid?.let {
                        addHeader("Cookie", it)
                        addHeader("Referer", "https://www.douyin.com/")
                    }
                }
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string()
                if (resp.code != 200 || body.isNullOrEmpty()) {
                    Log.w(TAG, "slidesinfo HTTP ${resp.code} len=${body?.length ?: 0}")
                    null
                } else if (!body.contains("aweme_details")) {
                    Log.w(TAG, "slidesinfo no aweme_details: ${body.take(80)}")
                    null
                } else {
                    body
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "slidesinfo failed: ${e.message}")
            ParseDiag.log("slidesinfo 异常: ${e.message?.take(40)}")
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

        // 首选：slidesinfo 接口（App 端数据源）
        //
        // 相比分享页接口，它多给两样东西，直接决定实况图能否下载与画质高低：
        //   1. images[i].video            → 实况图的动态视频（分享页完全没有）
        //   2. images[i].live_photo_type  → 是否实况(1=是)
        //   3. 主图 2160x2880（分享页只有 1440x1920，小 50%）
        // 失败则继续走下面的分享页链路，行为与之前完全一致。
        run {
            val ttwid = fetchTtwid()
            ParseDiag.log("slidesinfo ttwid=${if (ttwid != null) "ok" else "无"}")
            fetchSlidesInfo(contentId, ttwid)?.let { json ->
                ParseDiag.log("slidesinfo 返回 ${json.length}B")
                DouyinParser.parseItemList(json, contentId)?.let {
                    Log.d(TAG, "SUCCESS from slidesinfo")
                    return@withContext it
                }
                ParseDiag.log("slidesinfo 解析失败")
            }
        }

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

                    val routerData = DouyinJsonExtract.extractRouterData(html)
                    if (routerData != null) {
                        // 诊断增强: 记录 routerData 内的关键数据块位置，判断是挖空页还是解析问题
                        val hasItemList = routerData.contains("\"item_list\"")
                        val hasVideoInfo = routerData.contains("videoInfoRes")
                        val hasDetail = routerData.contains("aweme_detail")
                        val hasEmpty = routerData.contains("\"item_list\":[]") || routerData.contains("\"item_list\": []")
                        ParseDiag.log("ROUTER_DATA ${routerData.length}B item_list=$hasItemList videoInfoRes=$hasVideoInfo detail=$hasDetail 空数组=$hasEmpty")
                        val result = DouyinParser.parseRouterData(routerData, contentId)
                        if (result != null) {
                            Log.d(TAG, "SUCCESS from $sharePageUrl")
                            return@withContext result
                        }
                        ParseDiag.log("parseRouterData失败(gson/字段不匹配?)")
                    }

                    val itemData = DouyinJsonExtract.extractItemList(html)
                    if (itemData != null) {
                        ParseDiag.log("item_list ${itemData.length}B")
                        val result = DouyinParser.parseItemList(itemData, contentId)
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
                DouyinParser.parseRouterData(json, contentId)?.let {
                    Log.d(TAG, "SUCCESS from WebView (routerData)")
                    return@withContext it
                }
                DouyinParser.parseItemList(json, contentId)?.let {
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



    suspend fun downloadContent(
        context: Context, contentInfo: ContentInfo,
        onProgress: (Float) -> Unit, onFileSaved: (String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        try {
            when (contentInfo) {
                is ContentInfo.Video -> {
                    // 依次尝试候选清晰度（1080p → 720p → 540p），首个成功的即采用。
                    // 某个档位不可用时自动降到下一档，不再直接报「下载失败」。
                    val candidates = contentInfo.candidateUrls.ifEmpty { listOf(contentInfo.videoUrl) }
                    var saved: String? = null
                    var lastErr: String? = null
                    for ((i, u) in candidates.withIndex()) {
                        if (u.isBlank()) continue
                        try {
                            val r = downloadVideoWithRetry(context, u, "Douyin_Video_${contentInfo.id}", onProgress)
                            if (r != null) { saved = r; break }
                        } catch (e: Exception) { lastErr = e.message }
                        if (i < candidates.size - 1) {
                            Log.w(TAG, "清晰度降级: ${u.take(60)} 失败，尝试下一档")
                            LogStore.log("dl", "清晰度降级: 第${i + 1}档失败(${lastErr?.take(40) ?: "无错误信息"})，尝试下一档")
                        }
                    }
                    if (saved != null) { onFileSaved(saved); DownloadResult(true, listOf(saved)) }
                    else DownloadResult(false, error = lastErr ?: "下载视频失败")
                }
                is ContentInfo.ImageCollection -> {
                    val r = downloadImages(context, contentInfo.imageUrls, "Douyin_${contentInfo.id}", contentInfo.animatedUrls, onProgress, onFileSaved)
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
                    // 关键：抖音 CDN 按 Accept 决定返回格式 —— URL 后缀写着 .jpeg，
                    // 不带这个头时服务端实际回 webp，会让 Motion Photo 合成被跳过（实况变静图）。
                    addHeader("Accept", "image/jpeg,image/*;q=0.9,*/*;q=0.8")
                }.build()
                val result = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = java.io.IOException("HTTP ${response.code}")
                        return@use null
                    }
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
        var totalRead = 0L
        var lastPct = -1
        var lastEmitMs = 0L
        var bytesRead: Int
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalRead += bytesRead
            if (totalSize > 0) {
                val pct = (totalRead * 100 / totalSize).toInt().coerceAtMost(100)
                val now = System.currentTimeMillis()
                // 双闸：进度至少 +1% 且距上次回调 ≥120ms，才上报一次。
                // 原来只按 1% 节流：一个 5MB 短视频 1 秒下完会连发 100 次进度，
                // 每次都触发 Compose 状态更新 —— 下载时界面发飘就是这么来的。
                if (pct != lastPct && (now - lastEmitMs >= 120 || totalRead >= totalSize)) {
                    onProgress(pct / 100f)
                    lastPct = pct
                    lastEmitMs = now
                }
            }
        }
        outputStream.flush(); onProgress(1f)
    }

    /**
     * 批量下载图集。animatedUrls 与 imageUrls 一一对应：非 null 表示该图是实况图(Live Photo)，
     * 同时下载其动态视频版本（MP4，存 Movies/Douyin），小米等支持动态照片的相册可辨识。
     */
    private suspend fun downloadImages(
        context: Context, imageUrls: List<String>, baseName: String,
        animatedUrls: List<String?> = List(imageUrls.size) { null },
        onProgress: (Float) -> Unit, onFileSaved: (String) -> Unit
    ): List<String> = coroutineScope {
        val results = mutableListOf<String>()
        // ttwid 下载前统一取一次，避免每张图重复请求
        val ttwid = fetchTtwid()
        imageUrls.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                imageSemaphore.withPermit {
                    try {
                        val liveUrl = animatedUrls.getOrNull(index)
                        val r = downloadSingleImage(context, url, "${baseName}_${index + 1}", ttwid, liveUrl)
                        r?.let { synchronized(results) { results.add(it) }; onFileSaved(it) }
                        onProgress((index + 1).toFloat() / imageUrls.size); r
                    } catch (_: Exception) { null }
                }
            }
        }.awaitAll(); results.toList()
    }

    /**
     * 下载单张图。liveUrl 非空时该图是实况图(Live Photo):
     * 追加下载动态视频 MP4, 与静图合成为小米/Google 相册可直接识别的 Motion Photo JPEG。
     * 合成失败则退回只存静图, 不影响整体下载。
     */
    private suspend fun downloadSingleImage(
        context: Context, url: String, fileName: String, ttwid: String?, liveUrl: String? = null
    ): String? {
        var lastError: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val request = Request.Builder().url(url).apply {
                    baseHeaders.forEach { (k, v) -> addHeader(k, v) }
                    ttwid?.let { addHeader("Cookie", it) }
                    // 关键：抖音 CDN 按 Accept 决定返回格式 —— URL 后缀写着 .jpeg，
                    // 不带这个头时服务端实际回 webp，会让 Motion Photo 合成被跳过（实况变静图）。
                    addHeader("Accept", "image/jpeg,image/*;q=0.9,*/*;q=0.8")
                }.build()
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    // 扩展名判定兼顾 Content-Type 与 URL 后缀: 实况图合成必须拿到真正的 jpg 主图,
                    // 若这里判成 webp, 合成分支会被跳过而导致"实况变静图"。
                    val ctype = response.header("Content-Type")?.lowercase() ?: ""
                    val path = url.substringBefore('?').lowercase()
                    val ext = when {
                        ctype.contains("png") || path.endsWith(".png") -> "png"
                        ctype.contains("webp") || path.endsWith(".webp") -> "webp"
                        else -> "jpg"
                    }
                    // 静图字节留一份用于实况合成
                    val imageBytes = body.bytes()
                    if (liveUrl != null && ext == "jpg") {
                        try {
                            val liveReq = Request.Builder().url(liveUrl).apply {
                                baseHeaders.forEach { (k, v) -> addHeader(k, v) }
                                ttwid?.let { addHeader("Cookie", it) }
                            }.build()
                            val mp4Bytes = client.newCall(liveReq).execute().use { resp ->
                                if (!resp.isSuccessful) null else resp.body?.bytes()
                            }
                            if (mp4Bytes != null && mp4Bytes.size > 1024) {
                                val motionBytes = MotionPhotoMuxer.mux(imageBytes, mp4Bytes)
                                Log.d(TAG, "Motion Photo 合成: img=${imageBytes.size}B mp4=${mp4Bytes.size}B")
                                return saveImageBytes(context, motionBytes, "${fileName}_live", "jpg")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Motion Photo 合成失败, 退回静图: ${e.message}")
                        }
                    }
                    saveImageBytes(context, imageBytes, fileName, ext)
                }
            } catch (e: Exception) { lastError = e; if (attempt < MAX_RETRY - 1) delay(RETRY_DELAY) }
        }
        throw lastError ?: Exception("Image download failed")
    }

    /** 字节版落盘: 复用 saveImageFile 的 MediaStore 逻辑 */
    private fun saveImageBytes(context: Context, bytes: ByteArray, fileName: String, extension: String): String? {
        return saveImageFile(context, bytes.inputStream(), fileName, extension)
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

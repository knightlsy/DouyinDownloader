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
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
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
        var currentUrl = shortUrl
        var redirectCount = 0
        while (redirectCount < 10) {
            try {
                val request = Request.Builder().url(currentUrl)
                    .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .method("GET", null)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code in 300..399) {
                        currentUrl = response.header("Location") ?: currentUrl
                        Log.d(TAG, "Redirect $redirectCount -> $currentUrl")
                        redirectCount++
                    } else {
                        return@withContext response.request.url.toString()
                    }
                }
            } catch (_: Exception) {
                return@withContext shortUrl
            }
        }
        currentUrl
    }

    suspend fun getContentInfo(contentId: String): ContentInfo? = withContext(Dispatchers.IO) {
        val tryUrls = listOf(
            "https://m.douyin.com/share/video/$contentId/",
            "https://m.douyin.com/share/note/$contentId/",
            "https://m.douyin.com/share/slides/$contentId/",
            "https://www.iesdouyin.com/share/video/$contentId/",
            "https://www.iesdouyin.com/share/note/$contentId/",
            "https://www.iesdouyin.com/share/slides/$contentId/"
        )

        for (sharePageUrl in tryUrls) {
            try {
                Log.d(TAG, "Fetching: $sharePageUrl")
                val request = Request.Builder().url(sharePageUrl)
                    .apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }
                    .build()
                val response = client.newCall(request).execute()
                val html = response.use { it.body?.string() }

                if (html.isNullOrEmpty() || html.length < 10000) {
                    Log.d(TAG, "HTML too short, skip")
                    continue
                }

                val routerData = extractRouterData(html)
                if (routerData != null) {
                    val result = parseRouterData(routerData)
                    if (result != null) {
                        Log.d(TAG, "SUCCESS from $sharePageUrl")
                        return@withContext result
                    }
                }

                val itemData = extractItemList(html)
                if (itemData != null) {
                    val result = parseItemList(itemData)
                    if (result != null) {
                        Log.d(TAG, "SUCCESS (itemList) from $sharePageUrl")
                        return@withContext result
                    }
                }

                Log.d(TAG, "No parseable data in this page")
            } catch (e: Exception) {
                Log.w(TAG, "Failed: $sharePageUrl - ${e.message}")
            }
        }

        Log.w(TAG, "ALL URLS FAILED for $contentId")
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

    private fun parseRouterData(json: String): ContentInfo? {
        val m = itemListPattern.matcher(json)
        if (!m.find()) return null
        val arr = extractJsonArray(json, m.end() - 1) ?: return null
        val items = try { gson.fromJson(arr, Array<ItemObj>::class.java) } catch (_: Exception) { return null }
        if (items.isEmpty()) return null
        Log.d(TAG, "Router item: id=${items[0].aweme_id}, images=${items[0].images?.size}, video=${items[0].video != null}")
        return items[0].toContentInfo()
    }

    private fun parseItemList(json: String): ContentInfo? {
        try {
            val arr = gson.fromJson(json, Array<ItemObj>::class.java)
            if (arr.isNotEmpty()) {
                Log.d(TAG, "Array item: id=${arr[0].aweme_id}")
                return arr[0].toContentInfo()
            }
        } catch (_: Exception) {}
        try {
            val obj = gson.fromJson(json, ItemObj::class.java)
            if (obj.aweme_id.isNotEmpty()) return obj.toContentInfo()
        } catch (_: Exception) {}
        try {
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val detail = root.getAsJsonObject("aweme_detail") ?: return null
            val obj = gson.fromJson(detail, ItemObj::class.java)
            return obj.toContentInfo()
        } catch (_: Exception) {}
        return null
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
        repeat(MAX_RETRY) { attempt ->
            try {
                val request = Request.Builder().url(url).apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }.build()
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
        imageUrls.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                try {
                    val r = downloadSingleImage(context, url, "${baseName}_${index + 1}")
                    r?.let { synchronized(results) { results.add(it) }; onFileSaved(it) }
                    onProgress((index + 1).toFloat() / imageUrls.size); r
                } catch (_: Exception) { null }
            }
        }.awaitAll(); results.toList()
    }

    private fun downloadSingleImage(context: Context, url: String, fileName: String): String? {
        var lastError: Exception? = null
        repeat(MAX_RETRY) { attempt ->
            try {
                val request = Request.Builder().url(url).apply { baseHeaders.forEach { (k, v) -> addHeader(k, v) } }.build()
                return client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body ?: return null
                    val ext = when { response.header("Content-Type")?.contains("png") == true -> "png"; response.header("Content-Type")?.contains("webp") == true -> "webp"; else -> "jpg" }
                    saveImageFile(context, body.byteStream(), fileName, ext)
                }
            } catch (e: Exception) { lastError = e; if (attempt < MAX_RETRY - 1) Thread.sleep(RETRY_DELAY) }
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

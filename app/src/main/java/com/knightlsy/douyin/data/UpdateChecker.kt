package com.knightlsy.douyin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class UpdateChecker(private val context: Context) {
    companion object {
        private const val TAG = "UpdateChecker"
        // 第一优先级：自有服务器
        private const val API_URL = "https://douyinapi.knightlsy.cn"
        // 备选：GitHub 最新 Release
        private const val GITHUB_LATEST_API =
            "https://api.github.com/repos/knightlsy/DouyinDownloader/releases/latest"
        // GitHub 下载加速镜像，按优先级排序；GitHub 直连作为最后兜底
        private val MIRRORS = listOf(
            "https://ghproxy.net/",
            "https://gh.jesd.top/",
            "https://gh.horsey.top/",
            "https://gh.chenx264.top/"
        )
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 测速客户端：超时要短。并发探测时最慢的那条决定总耗时，
    // 5 秒会让"全部镜像都不通"的场景卡 5 秒，收到 4 秒。
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String = "",
        @SerializedName("html_url") val htmlUrl: String = "",
        val assets: List<GithubAsset> = emptyList()
    )

    private data class GithubAsset(
        val name: String = "",
        @SerializedName("browser_download_url") val browserDownloadUrl: String = ""
    )

    data class ServerApiResponse(
        @SerializedName("has_update") val hasUpdate: Boolean = false,
        val version: String = "",
        @SerializedName("download_url") val downloadUrl: String = "",
        @SerializedName("release_notes") val releaseNotes: String = ""
    )

    /**
     * 检查更新：自有服务器优先，失败或无更新时回退 GitHub 最新 Release。
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        // 1) 自有服务器
        val fromServer = checkServer(currentVersion)
        if (fromServer != null) {
            Log.d(TAG, "Update source: server")
            return@withContext fromServer
        }

        // 2) GitHub 备选
        val fromGithub = checkGithub(currentVersion)
        if (fromGithub != null) {
            Log.d(TAG, "Update source: github")
            return@withContext fromGithub
        }

        UpdateResult(hasUpdate = false)
    }

    /** 服务器检查：返回 null 表示不可用或无更新（此时才考虑 GitHub） */
    private suspend fun checkServer(currentVersion: String): UpdateResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_URL/api/check-update")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Server check failed: ${response.code}")
                    return@withContext null
                }

                val json = response.body?.string() ?: return@withContext null
                val apiResponse = gson.fromJson(json, ServerApiResponse::class.java)
                    ?: return@withContext null

                if (apiResponse.hasUpdate && apiResponse.version != currentVersion) {
                    // 服务器返回 GitHub 地址时同样套用 CDN 加速探测
                    val url = apiResponse.downloadUrl
                    // 不再在这里探测：留给点击「立即更新」时并发测速，选最快的
            val finalUrl = url
                    UpdateResult(
                        hasUpdate = true,
                        version = apiResponse.version,
                        downloadUrl = finalUrl,
                        releaseNotes = apiResponse.releaseNotes
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server check error", e)
            null
        }
    }

    /** GitHub 备选检查：返回 null 表示不可用或无更新 */
    private suspend fun checkGithub(currentVersion: String): UpdateResult? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub latest API failed: ${response.code}")
                    return@withContext null
                }

                val json = response.body?.string() ?: return@withContext null
                val release = gson.fromJson(json, GithubRelease::class.java)
                    ?: return@withContext null

                val latestVersion = release.tagName.removePrefix("v")
                // 过滤 pre-release（如 1.4.0-beta）并做数值比较，避免字符串比较误判
                if (latestVersion.isEmpty() || latestVersion.contains('-') ||
                    compareVersions(latestVersion, currentVersion) <= 0) {
                    return@withContext null
                }

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext null

                val downloadUrl = apkAsset.browserDownloadUrl
                Log.d(TAG, "Update v$latestVersion, download via: $downloadUrl")

                UpdateResult(
                    hasUpdate = true,
                    version = latestVersion,
                    downloadUrl = downloadUrl,
                    releaseNotes = release.htmlUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update error", e)
            null
        }
    }

    /**
     * 下载端点：名字 + 地址 + 实测延迟。给界面显示"从哪条线路下"用。
     */
    data class DownloadEndpoint(
        val name: String,
        val url: String,
        val latencyMs: Long,
    )

    /**
     * 并发测速，挑最快的可用地址。
     *
     * 为什么不再用「串行探测第一个可用的」：
     *   旧实现是 for 循环逐个 HEAD，第一个返回 2xx/3xx 的就直接用。
     *   问题有两个 ——
     *     1. 第一个「可用」不等于「最快」，公共镜像经常是能连但只有几十 KB/s
     *     2. 串行探测在全部镜像都不通时，要等 N × 超时，用户盯着转圈
     *   现在改成并发 Range 探测，全部同时发，谁先回来用谁。
     *
     * 为什么用 `Range: bytes=0-0` 而不是 HEAD：
     *   自建 Worker 现在两种都支持，但公共镜像（ghproxy 系列）很多不支持 HEAD，
     *   或者 HEAD 返回 200 但实际下载被拦。Range 只拉 1 字节，代价可忽略，
     *   而且能同时验证「能连上」和「能取到数据」两件事。
     *
     * 全部失败时回退到原始地址 —— 不阻断用户，让浏览器自己去报错。
     */
    suspend fun resolveFastest(downloadUrl: String): DownloadEndpoint = coroutineScope {
        val ghUrl = extractGithubUrl(downloadUrl)
        val candidates = buildCandidates(ghUrl, downloadUrl)

        Log.d(TAG, "Probing ${candidates.size} endpoints concurrently")

        val results = candidates.map { (name, url) ->
            async(Dispatchers.IO) {
                val ms = probeLatency(url)
                if (ms != null) DownloadEndpoint(name, url, ms) else null
            }
        }.awaitAll().filterNotNull()

        results.forEach { Log.d(TAG, "  ${it.name}: ${it.latencyMs}ms") }

        results.minByOrNull { it.latencyMs }
            ?: DownloadEndpoint("原始地址", downloadUrl, -1L).also {
                Log.w(TAG, "All endpoints failed, falling back to original URL")
            }
    }

    /** 从代理地址里还原出 GitHub 直链；不是代理地址就原样返回。 */
    private fun extractGithubUrl(url: String): String {
        if (!url.contains("download-apk?url=")) return url
        return try {
            val raw = url.substringAfter("download-apk?url=").substringBefore("&")
            java.net.URLDecoder.decode(raw, "UTF-8")
        } catch (e: Exception) {
            url
        }
    }

    /** 构造候选：GitHub 直连 + 自建代理 + 公共镜像。 */
    private fun buildCandidates(ghUrl: String, originalUrl: String): List<Pair<String, String>> {
        if (!ghUrl.contains("github.com")) {
            // 不是 GitHub 的地址（理论上不会发生），只测它自己
            return listOf("原始地址" to originalUrl)
        }
        val list = mutableListOf<Pair<String, String>>()
        list.add("GitHub 直连" to ghUrl)
        // 自建 Worker 代理：域名是自己控的，国内一般比 GitHub 直连稳
        val proxy = "https://douyinapi.knightlsy.cn/api/download-apk?url=" +
            java.net.URLEncoder.encode(ghUrl, "UTF-8")
        list.add("自建代理" to proxy)
        // 公共加速镜像
        MIRRORS.forEach { mirror -> list.add(mirror to (mirror + ghUrl)) }
        return list
    }

    /**
     * 测一次延迟（毫秒）。失败返回 null。
     * 只拉 1 字节，耗时基本等于 TLS 握手 + 首字节，这正是「打开下载页有多快」的近似。
     */
    private fun probeLatency(url: String): Long? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()
            val t0 = System.nanoTime()
            probeClient.newCall(req).execute().use { resp ->
                val ms = (System.nanoTime() - t0) / 1_000_000
                when {
                    resp.code == 206 || resp.code in 200..299 || resp.code in 300..399 -> ms
                    else -> {
                        Log.d(TAG, "probe fail ${resp.code}: ${url.take(60)}")
                        null
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "probe error: ${e.message?.take(40)} | ${url.take(60)}")
            null
        }
    }

    /**
     * 语义化版本数值比较：逐段比较主/次/修订号，缺段按 0 处理。
     * 返回负数/0/正数 表示 latest 对 current 的相对大小。
     */
    private fun compareVersions(latest: String, current: String): Int {
        val l = latest.split('.').map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.filter { ch -> ch.isDigit() }.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val li = l.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (li != ci) return li - ci
        }
        return 0
    }

    fun startDownload(downloadUrl: String) {
        if (downloadUrl.isBlank()) {
            Log.w(TAG, "Download URL is empty")
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Start download failed", e)
        }
    }
}

data class UpdateResult(
    val hasUpdate: Boolean,
    val version: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

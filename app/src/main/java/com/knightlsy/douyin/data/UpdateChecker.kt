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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class UpdateChecker(private val context: Context) {
    companion object {
        private const val TAG = "UpdateChecker"
        // 第一优先级：自有服务器
        private const val API_URL = "https://douyinapi.knightlsy.cn"
        // 备选：GitHub 最新 Release
        private const val GITHUB_LATEST_API =
            "https://api.github.com/repos/knightlsy/DouyinDownloader/releases/latest"
        /** 整次检查的总超时（毫秒）。超过就当作「没有更新」，不阻塞界面。 */
        private const val CHECK_TIMEOUT_MS = 12_000L
        /** 测速总预算（毫秒）。全部线路都没应答时，到点回退原始地址。 */
        private const val PROBE_BUDGET_MS = 5_000L
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
        // callTimeout 才是真兜底：connect/read 超时管不住 DNS 解析卡住，
        // 也管不住服务端一点一点滴数据的场景。没有它，一条线路就能把
        // 整个测速挂死，而测速发生在强制更新弹窗里 —— 挂死 = 用户被锁住。
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private data class GithubRelease(
        @SerializedName("tag_name") val tagName: String = "",
        @SerializedName("html_url") val htmlUrl: String = "",
        val body: String = "",
        val assets: List<GithubAsset> = emptyList()
    )

    private data class GithubAsset(
        val name: String = "",
        @SerializedName("browser_download_url") val browserDownloadUrl: String = "",
        /** 形如 "sha256:abc..."，GitHub 从 2024 年起提供 */
        val digest: String = "",
        val size: Long = 0L
    )

    data class ServerApiResponse(
        @SerializedName("has_update") val hasUpdate: Boolean = false,
        val version: String = "",
        @SerializedName("download_url") val downloadUrl: String = "",
        @SerializedName("release_notes") val releaseNotes: String = "",
        val sha256: String = "",
        val size: Long = 0L
    )

    /**
     * 一次检查的三种结果。
     *
     * 必须区分「确认没有新版本」和「服务器不可达」：
     *   旧实现两者都返回 null，于是「服务器说没更新」时还会回退去问 GitHub。
     *   国内环境问 GitHub 基本必然超时，等于每次冷启动都白挂一个几十秒的请求。
     */
    private sealed class CheckOutcome {
        data class Found(val result: UpdateResult) : CheckOutcome()
        /** 服务器明确答复：没有新版本。这是权威答案，不再回退 GitHub。 */
        object Nothing : CheckOutcome()
        /** 服务器不可达 / 返回错误，可以回退 GitHub。 */
        object Unreachable : CheckOutcome()
    }

    /**
     * 检查更新：自有服务器优先，不可达时才回退 GitHub 最新 Release。
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        // 总超时兜底：服务器段 10+15 秒，GitHub 段再来 10+15 秒，最坏 50 秒。
        // 强制更新弹窗里点「重新检查」时，用户看到的就是按钮文字卡住不动。
        val outcome = withTimeoutOrNull(CHECK_TIMEOUT_MS) {
            when (val server = checkServer(currentVersion)) {
                is CheckOutcome.Found -> {
                    Log.d(TAG, "Update source: server")
                    server.result
                }
                // 服务器说没更新 = 权威答案，直接结束，不再问 GitHub。
                CheckOutcome.Nothing -> {
                    Log.d(TAG, "Server says up to date")
                    null
                }
                CheckOutcome.Unreachable -> {
                    Log.d(TAG, "Server unreachable, fallback to GitHub")
                    checkGithub(currentVersion)?.also { Log.d(TAG, "Update source: github") }
                }
            }
        }
        outcome ?: UpdateResult(hasUpdate = false)
    }

    /** 服务器检查。返回三态，见 CheckOutcome。 */
    private suspend fun checkServer(currentVersion: String): CheckOutcome = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_URL/api/check-update")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Server check failed: ${response.code}")
                    return@withContext CheckOutcome.Unreachable
                }

                val json = response.body?.string() ?: return@withContext CheckOutcome.Unreachable
                val apiResponse = gson.fromJson(json, ServerApiResponse::class.java)
                    ?: return@withContext CheckOutcome.Unreachable

                // 数值比较，不用 !=：
                // 服务器若因缓存/回滚返回了更旧的版本号，!="" 会把它当成
                // 「有新版本」，用户点下去反而装回旧版。
                val hasNewer = apiResponse.hasUpdate &&
                    compareVersions(apiResponse.version, currentVersion) > 0

                if (!hasNewer) return@withContext CheckOutcome.Nothing

                CheckOutcome.Found(
                    UpdateResult(
                        hasUpdate = true,
                        version = apiResponse.version,
                        // 不在检查阶段探测线路：留给点击「立即更新」时并发测速
                        downloadUrl = apiResponse.downloadUrl,
                        releaseNotes = apiResponse.releaseNotes,
                        sha256 = apiResponse.sha256,
                        size = apiResponse.size
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Server check error: ${e.message?.take(60)}")
            CheckOutcome.Unreachable
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
                    // 原来这里塞的是 htmlUrl（一串网址），界面上「更新内容」
                    // 显示出来就是一条链接。改用 Release 正文。
                    releaseNotes = release.body.take(1200),
                    // GitHub asset 的 digest 形如 "sha256:abc..."，去掉前缀
                    sha256 = apkAsset.digest.removePrefix("sha256:").takeIf { it.length == 64 } ?: "",
                    size = apkAsset.size
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

        if (candidates.size == 1) {
            val (name, url) = candidates.first()
            return@coroutineScope DownloadEndpoint(name, url, probeLatency(url) ?: -1L)
        }

        // 先到先得：谁先测出延迟，谁就是当前最快的，立刻用它，不等其余线路。
        //
        // 上一版用 awaitAll()，语义是「等全部完成再挑最小」——
        // 总耗时 = 最慢那条的超时（4~5 秒）。即使大多数时候最快线路
        // 200ms 就回来了，用户仍要干等满 5 秒。
        // 而这段代码跑在强制更新弹窗里，等待时间直接等于「被锁住的时长」。
        val channel = Channel<DownloadEndpoint>(Channel.UNLIMITED)
        val jobs = candidates.map { (name, url) ->
            launch(Dispatchers.IO) {
                probeLatency(url)?.let { ms -> channel.send(DownloadEndpoint(name, url, ms)) }
            }
        }

        val fastest = withTimeoutOrNull(PROBE_BUDGET_MS) { channel.receive() }
        jobs.forEach { it.cancel() }
        channel.close()

        fastest ?: DownloadEndpoint("原始地址", downloadUrl, -1L).also {
            Log.w(TAG, "No endpoint answered within ${PROBE_BUDGET_MS}ms, using original URL")
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

}

data class UpdateResult(
    val hasUpdate: Boolean = false,
    val version: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    /** APK 的 SHA256（十六进制，小写，无前缀）。为空表示服务端没提供，此时不校验。 */
    val sha256: String = "",
    /** APK 字节数，仅用于进度显示的初值。 */
    val size: Long = 0L
)

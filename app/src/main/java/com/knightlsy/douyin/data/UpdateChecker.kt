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
        /**
         * GitHub 下载加速镜像，按实测速度排序。
         *
         * 2026-11 实测（同一 APK，拉 1MB）：
         *   gh-proxy.com    1.34 MB/s   ← 比 GitHub 直连快一倍
         *   GitHub 直连     0.69 MB/s
         *   自建代理        0.66 MB/s
         *   ghproxy.net     0.17 MB/s
         *   gh.jesd.top / gh.horsey.top / gh.chenx264.top  已全部失效，移除
         *
         * 这份列表会腐烂（公共镜像死亡率很高），所以测速逻辑不能依赖"顺序"，
         * 必须靠实测速度选路；这里只是候选集。
         */
        private val MIRRORS = listOf(
            "https://gh-proxy.com/",
            "https://ghproxy.net/",
        )

        /**
         * 镜像优先阈值：镜像速度只要达到最快直连的这个百分比，就选镜像。
         *
         * 为什么不是"无条件优先镜像"：实测里镜像和直连互有胜负，无条件优先可能挑到慢的。
         * 为什么不是"只选最快的"：国内移动网络下直连 GitHub 容易中途断流，
         * 同一测速窗口内快一点不代表下载 10MB 的全程都稳。留 20% 余量做这个权衡。
         */
        private const val MIRROR_PREFER_RATIO = 80L

        /**
         * 拿到第一条测速结果后再多等这么久，收集其余线路。
         *
         * 只靠"先到先得"会选中响应最快的线路 —— 但响应快不等于下载快。
         * 用户要下的是 10MB，值得多等这一小会儿拿真实速度做对比。
         */
        private const val PROBE_SECOND_WAVE_MS = 1_200L
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
     * 下载端点：名字 + 地址 + 实测速度。给界面显示"从哪条线路下"用。
     */
    data class DownloadEndpoint(
        val name: String,
        val url: String,
        /** 实测下载速度（KB/s）。-1 表示没测出任何线路，退回了原始地址。 */
        val speedKbps: Long,
        /** 自建代理和公共镜像算镜像；GitHub 直连不算。用于"速度接近时镜像优先"。 */
        val isMirror: Boolean,
    )

    /** 候选线路。 */
    private data class Candidate(val name: String, val url: String, val isMirror: Boolean)

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
    suspend fun resolveFastest(
        downloadUrl: String,
        exclude: Set<String> = emptySet(),
    ): DownloadEndpoint = coroutineScope {
        val ghUrl = extractGithubUrl(downloadUrl)
        val all = buildCandidates(ghUrl, downloadUrl)

        // 排除本次会话里已经失败过的线路。
        //
        // 不加这个的话会死循环：某个镜像持续返回篡改包 → 校验失败 → 重试
        // → 它测速依然最快 → 又选中它 → 再失败。用户会看到「校验失败，
        // 请重试」重试无数次都装不上。
        val candidates = all.filterNot { it.url in exclude }

        Log.d(TAG, "Probing ${candidates.size}/${all.size} endpoints (excluded ${exclude.size})")

        if (candidates.isEmpty()) {
            Log.w(TAG, "All endpoints excluded, falling back to original URL")
            return@coroutineScope DownloadEndpoint("原始地址", downloadUrl, -1L, false)
        }

        if (candidates.size == 1) {
            val one = candidates.first()
            return@coroutineScope DownloadEndpoint(
                one.name, one.url, probeSpeed(one.url) ?: -1L, one.isMirror
            )
        }

        // 并发测真实速度（不再是首字节延迟）。
        val channel = Channel<DownloadEndpoint>(Channel.UNLIMITED)
        val jobs = candidates.map { cand ->
            launch(Dispatchers.IO) {
                probeSpeed(cand.url)?.let { kbps ->
                    channel.send(DownloadEndpoint(cand.name, cand.url, kbps, cand.isMirror))
                }
            }
        }

        val results = mutableListOf<DownloadEndpoint>()
        val first = withTimeoutOrNull(PROBE_BUDGET_MS) { channel.receive() }
        if (first != null) {
            results.add(first)
            // 再给其余线路一个翻盘窗口：响应最快的未必下载最快
            withTimeoutOrNull(PROBE_SECOND_WAVE_MS) {
                while (results.size < candidates.size) {
                    results.add(channel.receive())
                }
            }
        }
        jobs.forEach { it.cancel() }
        channel.close()

        if (results.isEmpty()) {
            Log.w(TAG, "No endpoint answered within ${PROBE_BUDGET_MS}ms, using original URL")
            return@coroutineScope DownloadEndpoint("原始地址", downloadUrl, -1L, false)
        }

        // 选路：镜像优先，但只在速度不吃亏的前提下。
        val bestMirror = results.filter { it.isMirror }.maxByOrNull { it.speedKbps }
        val bestDirect = results.filterNot { it.isMirror }.maxByOrNull { it.speedKbps }
        val chosen = when {
            bestMirror == null -> bestDirect ?: results.maxByOrNull { it.speedKbps }!!
            bestDirect == null -> bestMirror
            // 镜像只要达到最快直连的 MIRROR_PREFER_RATIO% 就用镜像
            bestMirror.speedKbps * 100L >= bestDirect.speedKbps * MIRROR_PREFER_RATIO -> bestMirror
            else -> bestDirect
        }
        Log.d(
            TAG,
            "Chosen: ${chosen.name} ${chosen.speedKbps}KB/s " +
                "(mirror=${bestMirror?.speedKbps ?: -1}, direct=${bestDirect?.speedKbps ?: -1}, " +
                "tested=${results.size})"
        )
        chosen
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
    private fun buildCandidates(ghUrl: String, originalUrl: String): List<Candidate> {
        if (!ghUrl.contains("github.com")) {
            // 不是 GitHub 的地址（理论上不会发生），只测它自己
            return listOf(Candidate("原始地址", originalUrl, false))
        }
        val list = mutableListOf<Candidate>()
        list.add(Candidate("GitHub 直连", ghUrl, false))
        // 自建 Worker 代理：域名自己控，国内一般比 GitHub 直连稳
        val proxy = "https://douyinapi.knightlsy.cn/api/download-apk?url=" +
            java.net.URLEncoder.encode(ghUrl, "UTF-8")
        list.add(Candidate("自建代理", proxy, true))
        // 公共加速镜像（名字取域名，界面上好认是哪条）
        MIRRORS.forEach { mirror ->
            val shortName = mirror.trimEnd('/').substringAfter("//")
            list.add(Candidate(shortName, mirror + ghUrl, true))
        }
        return list
    }

    /**
     * 测一小段真实传输速度（KB/s）。失败返回 null。
     *
     * 旧版只拉 1 字节测首字节延迟 —— 那测的是「TLS 握手 + 首包」，不是下载速度。
     * 实测能说明问题：gh-proxy.com 首字节 0.59s / 实际 1.34MB/s，
     * ghproxy.net 首字节 1.41s / 实际 0.17MB/s。
     * 只要出现「握手快、限速狠」的镜像，按首字节选就会挑错 —— 而用户下的是 10MB。
     *
     * 拉 128KB 做样本：够区分快慢，单条线路只多花约 0.1MB 流量。
     */
    private fun probeSpeed(url: String): Long? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-131071")
                .build()
            val t0 = System.nanoTime()
            probeClient.newCall(req).execute().use { resp ->
                val ms = (System.nanoTime() - t0) / 1_000_000
                if (resp.code != 206 && resp.code !in 200..299 && resp.code !in 300..399) {
                    Log.d(TAG, "probe fail ${resp.code}: ${url.take(60)}")
                    return null
                }
                // 只读最多 128KB。有些镜像不认 Range（回 200 + 整个文件），
                // 这时候 body.bytes() 会把 10MB 全读进内存，既费流量又拖慢测速 ——
                // 所以这里手动截断，读够样本就断开。
                val bytes = resp.body?.byteStream()?.use { ins ->
                    val buf = ByteArray(131_072)
                    var total = 0
                    while (total < buf.size) {
                        val n = ins.read(buf, total, buf.size - total)
                        if (n < 0) break
                        total += n
                    }
                    total.toLong()
                } ?: 0L
                if (bytes <= 0L || ms <= 0L) return null
                (bytes / 1024L) * 1000L / ms
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

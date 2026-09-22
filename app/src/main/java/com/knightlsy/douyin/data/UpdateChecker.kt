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

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
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
                    val finalUrl = if (url.contains("github.com")) pickDownloadUrl(url) else url
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

                val downloadUrl = pickDownloadUrl(apkAsset.browserDownloadUrl)
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
     * 按优先级探测下载地址：CDN 加速镜像优先，GitHub 直连兜底。
     * 2xx/3xx 均视为可用；全部镜像失败返回直连。
     */
    private fun pickDownloadUrl(directUrl: String): String {
        for (mirror in MIRRORS) {
            val candidate = mirror + directUrl
            try {
                val request = Request.Builder().url(candidate).head().build()
                probeClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 300..399) {
                        Log.d(TAG, "Mirror OK: $mirror (${resp.code})")
                        return candidate
                    }
                    Log.d(TAG, "Mirror unavailable: $mirror code=${resp.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Mirror unreachable: $mirror - ${e.message}")
            }
        }
        return directUrl
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

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
        private const val GITHUB_LATEST_API =
            "https://api.github.com/repos/knightlsy/DouyinDownloader/releases/latest"
        // GitHub 下载加速镜像，按优先级排序；空串表示直连 GitHub（首选）
        private val MIRRORS = listOf(
            "",
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

    /**
     * 检查更新：直接查询 GitHub 最新 Release。
     * 1. 取最新 tag（如 v1.3.9）与当前版本比对
     * 2. 找到 .apk 资产
     * 3. 按优先级探测镜像（直连 -> ghproxy.net -> ...），生成第一个可用的下载链接
     */
    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_API)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub latest API failed: ${response.code}")
                    return@withContext UpdateResult(hasUpdate = false)
                }

                val json = response.body?.string()
                    ?: return@withContext UpdateResult(hasUpdate = false)
                val release = gson.fromJson(json, GithubRelease::class.java)
                    ?: return@withContext UpdateResult(hasUpdate = false)

                val latestVersion = release.tagName.removePrefix("v")
                if (latestVersion.isEmpty() || latestVersion == currentVersion) {
                    return@withContext UpdateResult(hasUpdate = false)
                }

                val apkAsset = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    ?: return@withContext UpdateResult(hasUpdate = false)

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
            UpdateResult(hasUpdate = false)
        }
    }

    /**
     * 按优先级探测镜像，返回第一个可用的完整下载地址。
     * 直连 GitHub 返回 302 跳 CDN 属正常，2xx/3xx 均视为可用；全部失败兜底直连。
     */
    private fun pickDownloadUrl(directUrl: String): String {
        for (mirror in MIRRORS) {
            val candidate = if (mirror.isEmpty()) directUrl else mirror + directUrl
            val label = mirror.ifEmpty { "direct" }
            try {
                val request = Request.Builder().url(candidate).head().build()
                probeClient.newCall(request).execute().use { resp ->
                    if (resp.isSuccessful || resp.code in 300..399) {
                        Log.d(TAG, "Mirror OK: $label (${resp.code})")
                        return candidate
                    }
                    Log.d(TAG, "Mirror unavailable: $label code=${resp.code}")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Mirror unreachable: $label - ${e.message}")
            }
        }
        return directUrl
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

data class UpdateApiResponse(
    @SerializedName("has_update") val hasUpdate: Boolean = false,
    val version: String = "",
    @SerializedName("download_url") val downloadUrl: String = "",
    @SerializedName("release_notes") val releaseNotes: String = ""
)

data class UpdateResult(
    val hasUpdate: Boolean,
    val version: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

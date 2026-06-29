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
        private const val API_URL = "https://douyinapi.knightlsy.cn"
        private const val TAG = "UpdateChecker"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun checkForUpdate(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_URL/api/check-update")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Check update failed: ${response.code}")
                    return@withContext UpdateResult(hasUpdate = false)
                }

                val json = response.body?.string() ?: return@withContext UpdateResult(hasUpdate = false)
                val apiResponse = gson.fromJson(json, UpdateApiResponse::class.java)
                    ?: return@withContext UpdateResult(hasUpdate = false)

                if (apiResponse.hasUpdate && apiResponse.version != currentVersion) {
                    UpdateResult(
                        hasUpdate = true,
                        version = apiResponse.version,
                        downloadUrl = apiResponse.downloadUrl,
                        releaseNotes = apiResponse.releaseNotes
                    )
                } else {
                    UpdateResult(hasUpdate = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check update error", e)
            UpdateResult(hasUpdate = false)
        }
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

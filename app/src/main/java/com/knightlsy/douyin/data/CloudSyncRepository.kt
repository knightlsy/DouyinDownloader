package com.knightlsy.douyin.data

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudSyncRepository(private val context: Context) {
    companion object {
        private const val API_URL = "https://douyinapi.knightlsy.cn"

        private fun getApiKey(): String {
            val encoded = "NWU2NTI5MDEzOGFjNDM2NmVjZmQxMTQxMTdkN2E3Y2NmNjIxNjdhNGViYTg5ZDdmZjEyNWE3ZDRmOGJiYw=="
            val decoded = Base64.decode(encoded, Base64.DEFAULT).toString(Charsets.UTF_8)
            return decoded.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
        }
    }

    private val tag = "CloudSync"
    private val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun getDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    fun getDeviceModel(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}"
    }

    suspend fun saveRecord(record: ParseRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(record).toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$API_URL/api/records")
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.d(tag, "Record saved: ${record.originalUrl}")
                    true
                } else {
                    Log.w(tag, "Save failed: ${response.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Save error", e)
            false
        }
    }

    suspend fun getRecords(
        deviceId: String? = null,
        page: Int = 1,
        limit: Int = 50
    ): RecordListResponse? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$API_URL/api/records?page=$page&limit=$limit")
            deviceId?.let { urlBuilder.append("&device_id=${URLEncoder.encode(it, "UTF-8")}") }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    gson.fromJson(json, RecordListResponse::class.java)
                } else {
                    Log.w(tag, "Get records failed: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Get records error", e)
            null
        }
    }

    suspend fun deleteRecord(id: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_URL/api/records/$id")
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Delete error", e)
            false
        }
    }

    suspend fun clearDeviceRecords(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(mapOf("device_id" to deviceId)).toRequestBody(jsonType)
            val request = Request.Builder()
                .url("$API_URL/api/records/clear")
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .delete(body)
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(tag, "Clear error", e)
            false
        }
    }

    suspend fun getStats(deviceId: String? = null): StatsResponse? = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$API_URL/api/records/stats")
            deviceId?.let { urlBuilder.append("?device_id=$it") }

            val request = Request.Builder()
                .url(urlBuilder.toString())
                .addHeader("Authorization", "Bearer ${getApiKey()}")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val json = response.body?.string()
                    gson.fromJson(json, StatsResponse::class.java)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Stats error", e)
            null
        }
    }
}

data class ParseRecord(
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_model") val deviceModel: String = "",
    @SerializedName("original_url") val originalUrl: String,
    @SerializedName("content_id") val contentId: String = "",
    @SerializedName("content_type") val contentType: String = "VIDEO",
    val title: String = "",
    val author: String = "",
    @SerializedName("cover_url") val coverUrl: String = ""
)

data class RecordItem(
    val id: Long,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_model") val deviceModel: String = "",
    @SerializedName("original_url") val originalUrl: String,
    @SerializedName("content_id") val contentId: String,
    @SerializedName("content_type") val contentType: String,
    val title: String,
    val author: String,
    @SerializedName("cover_url") val coverUrl: String,
    @SerializedName("parsed_at") val parsedAt: Long,
    @SerializedName("created_at") val createdAt: Long
)

data class Pagination(
    val page: Int,
    val limit: Int,
    val total: Int,
    val pages: Int
)

data class RecordListResponse(
    val records: List<RecordItem>,
    val pagination: Pagination
)

data class StatsData(
    @SerializedName("total_records") val totalRecords: Int,
    @SerializedName("total_devices") val totalDevices: Int,
    @SerializedName("unique_content") val uniqueContent: Int,
    @SerializedName("video_count") val videoCount: Int,
    @SerializedName("image_count") val imageCount: Int
)

data class StatsResponse(
    val stats: StatsData
)

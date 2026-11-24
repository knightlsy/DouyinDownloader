package com.knightlsy.douyin.data

import android.content.Context
import android.net.Uri
import android.util.Log

/**
 * 读取 LSPosed 模块（抖仙人 / com.zz.douyin）下载过的作品记录。
 *
 * ## 为什么是 ContentProvider
 * 两个 App 签名不同，走不了 signature 级权限；Android 11+ 又读不到对方的应用目录。
 * 模块那边用 `SharedProvider` 暴露只读游标，这里用 ContentResolver 拉。
 *
 * ## 失败即空
 * 模块没装、没激活、被卸载、Provider 拒绝 —— 全都不该影响 App 自己。
 * 所有异常都吞成空列表，只记日志。
 */
class SharedHistoryClient(private val context: Context) {

    fun read(): List<DownloadHistoryItem> {
        val cursor = try {
            context.contentResolver.query(URI, null, null, null, null)
        } catch (error: Exception) {
            // 模块没装时有些 ROM 会抛 IllegalArgumentException 而不是返回 null
            Log.d(TAG, "模块历史不可用：${error.javaClass.simpleName}")
            return emptyList()
        } ?: return emptyList()

        return cursor.use { c ->
            val result = ArrayList<DownloadHistoryItem>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                runCatching { readRow(c) }
                    .onSuccess { if (it != null) result.add(it) }
                    .onFailure { Log.d(TAG, "跳过一行无法解析的模块记录") }
            }
            result
        }
    }

    private fun readRow(cursor: android.database.Cursor): DownloadHistoryItem? {
        val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
        val contentId = cursor.getString(cursor.getColumnIndexOrThrow("contentId"))
        if (id.isNullOrEmpty() || contentId.isNullOrEmpty()) return null

        val filesRaw = cursor.getString(cursor.getColumnIndexOrThrow("downloadedFiles")).orEmpty()
        return DownloadHistoryItem(
            id = id,
            contentId = contentId,
            title = cursor.getString(cursor.getColumnIndexOrThrow("title")).orEmpty(),
            author = cursor.getString(cursor.getColumnIndexOrThrow("author")).orEmpty(),
            coverUrl = cursor.getString(cursor.getColumnIndexOrThrow("coverUrl")).orEmpty(),
            contentType = runCatching {
                ContentType.valueOf(
                    cursor.getString(cursor.getColumnIndexOrThrow("contentType")).orEmpty()
                )
            }.getOrDefault(ContentType.VIDEO),
            originalUrl = cursor.getString(cursor.getColumnIndexOrThrow("originalUrl")).orEmpty(),
            downloadTime = cursor.getLong(cursor.getColumnIndexOrThrow("downloadTime")),
            downloadedFiles = if (filesRaw.isEmpty()) emptyList() else filesRaw.split("\n"),
            // 标记来源，历史列表里要显示「模块」角标
            source = cursor.getString(cursor.getColumnIndexOrThrow("source"))
                ?: SOURCE_XPOSED,
        )
    }

    companion object {
        private const val TAG = "SharedHistory"

        /** 模块 Provider 的只读历史入口。 */
        private val URI: Uri = Uri.parse("content://com.zz.douyin.shared/history")

        /** 记录来源：App 自己解析下载。 */
        const val SOURCE_APP = "app"

        /** 记录来源：LSPosed 模块在抖音里下载。 */
        const val SOURCE_XPOSED = "xposed"
    }
}

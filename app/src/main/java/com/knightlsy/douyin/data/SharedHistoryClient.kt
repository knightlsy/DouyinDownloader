package com.knightlsy.douyin.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors

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
        // 跨进程 query 没有内建超时：对端活着但卡住时既不抛异常也不返回，
        // 调用方会一直等下去（历史页停在旧数据、没有任何报错线索）。
        // 用线程池 + Future.get 主动放弃，超时就当作没有模块记录。
        return try {
            PROBE.submit<List<DownloadHistoryItem>> { readBlocking() }
                .get(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            Log.d(TAG, "读取模块历史失败：${error.javaClass.simpleName}")
            emptyList()
        }
    }

    /** 阻塞版实现，只在 [PROBE] 线程上跑。 */
    private fun readBlocking(): List<DownloadHistoryItem> {
        // authority 用固定值，不跟探测到的包名拼接。
        // 模块 AndroidManifest 里 provider 的 authority 写死是 com.zz.douyin.shared，
        // applicationIdSuffix=".debug" 只改包名、不改 authority ——
        // 之前按包名拼成 com.zz.douyin.debug.shared，query 打到一个不存在的
        // authority 上，debug 变体下整条联动链路静默断掉。
        val uri = Uri.parse(HISTORY_URI)

        val cursor = try {
            context.contentResolver.query(uri, null, null, null, null)
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

        /**
         * 模块 Provider 的只读历史入口。
         *
         * 注意这里是**固定** authority：模块 manifest 里写死 com.zz.douyin.shared，
         * 不随 applicationIdSuffix 变化。改模块 side 的 authority 时才同步改这里。
         */
        const val HISTORY_URI = "content://com.zz.douyin.shared/history"

        private const val PROBE_TIMEOUT_MS = 3_000L

        /**
         * 固定 2 线程：binder 调用不响应中断，被放弃的任务仍占着线程。
         * 卡死时影响有界，退化成「读不到」而不是拖住历史页。
         */
        private val PROBE = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "shared-history-read").apply { isDaemon = true }
        }

        /** 记录来源：App 自己解析下载。 */
        const val SOURCE_APP = "app"

        /** 记录来源：LSPosed 模块在抖音里下载。 */
        const val SOURCE_XPOSED = "xposed"
    }
}

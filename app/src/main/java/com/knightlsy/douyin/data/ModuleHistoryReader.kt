package com.knightlsy.douyin.data

import android.content.Context
import android.util.Log
import com.zz.douyin.SharedHistoryStore
import org.json.JSONObject

/**
 * 读取 Xposed 模块（hook 跑在抖音进程里）写下的下载记录。
 *
 * ## 数据怎么来的
 * `hook → 显式广播 → HistoryReceiver → SharedHistoryStore 落盘 shared_history.jsonl`
 *
 * 之前这里是 `SharedHistoryClient`，走 `content://com.zz.douyin.shared/history`
 * 跨应用查 ContentProvider —— 那套是「模块是独立 App」时代的方案。
 * 合并成一个 APK 之后那个 Provider 根本不存在，query 必然失败。
 * 更糟的是失败被吞成空列表、界面上看不出任何异常：
 * 记录明明写进磁盘了，历史页却一条都不显示，而且永远不报错。
 *
 * 现在直接读本应用自己的文件。同一个 APK，不需要任何跨进程通道。
 *
 * ## 字段契约
 * 与 `VideoDownloader.publishHistory()` 写入的 JSON 一致：
 * id / contentId / title / author / coverUrl / contentType /
 * originalUrl / downloadTime / downloadedFiles / source
 */
class ModuleHistoryReader(private val context: Context) {

    fun read(): List<DownloadHistoryItem> {
        // SharedHistoryStore.read 已失败即空，这里不再套 try
        return SharedHistoryStore.read(context).mapNotNull(::parse)
    }

    /** 单行解析。任何字段异常只丢这一行，不影响其余记录。 */
    private fun parse(line: String): DownloadHistoryItem? = runCatching {
        val json = JSONObject(line)
        val id = json.optString("id")
        val contentId = json.optString("contentId")
        // 与 SharedHistoryStore 的写入校验对齐：没有这两个字段的记录没法去重
        if (id.isEmpty() || contentId.isEmpty()) {
            return@runCatching null
        }

        // downloadedFiles 是 JSON 数组
        // （旧跨 App 方案里是 Provider 用换行拼成一个字符串，别照那个写）
        val files = json.optJSONArray("downloadedFiles")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val name = array.optString(index)
                    if (name.isNotEmpty()) add(name)
                }
            }
        } ?: emptyList()

        DownloadHistoryItem(
            id = id,
            contentId = contentId,
            title = json.optString("title"),
            author = json.optString("author"),
            coverUrl = json.optString("coverUrl"),
            contentType = runCatching {
                ContentType.valueOf(json.optString("contentType"))
            }.getOrDefault(ContentType.VIDEO),
            originalUrl = json.optString("originalUrl"),
            downloadTime = json.optLong("downloadTime"),
            downloadedFiles = files,
            // 决定历史页那个「模块」角标，以及是否隐藏「重新解析」
            source = json.optString("source").ifEmpty { SOURCE_XPOSED },
        )
    }.onFailure {
        Log.d(TAG, "跳过一行无法解析的模块记录")
    }.getOrNull()

    companion object {
        private const val TAG = "ModuleHistory"

        /** 记录来源：本应用自己解析下载。 */
        const val SOURCE_APP = "app"

        /** 记录来源：Xposed 模块在抖音里下载。 */
        const val SOURCE_XPOSED = "xposed"
    }
}

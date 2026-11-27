package com.knightlsy.douyin.data

import android.content.Context
import com.zz.douyin.SharedHistoryStore

class HistoryRepository(context: Context) {
    // 只用于把删除指令转给 JSONL 侧；applicationContext 避免持有 Activity 引用
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(context).historyDao()

    /**
     * 模块（hook）写下的记录。
     *
     * 合并之前这里走的是 ContentResolver 查另一个 App 的 Provider；
     * 现在模块就在本 APK 里，hook 把记录广播过来落成 JSONL，
     * 直接读自己文件即可 —— 不再有任何跨进程/跨应用查询。
     */
    private val moduleHistory = ModuleHistoryReader(context)

    /**
     * 本应用的历史 + 模块在抖音里下载的历史，按 (contentId, contentType) 去重、时间倒序。
     *
     * 模块记录不进 Room：它是 hook 侧写的 JSONL，随时可能被裁剪（上限 2000 行），
     * 当作「外部数据源合并进来」比「复制一份进数据库」更容易保持一致。
     */
    fun getAll(): List<DownloadHistoryItem> {
        val local = dao.getAll().map { it.toDownloadHistoryItem() }

        // 不再有开关门控。原来那道 if (!ModulePreferences.isEnabled(...)) return local
        // 依赖的 enhanced_enabled 已经没有任何 UI 会写（设置页改成了 content_filter），
        // 永远停在默认 false —— 模块记录就算落盘了也永远不显示。
        // 现在模块内置、是否生效由 hook 侧自己的开关控制，读侧无条件合并即可。
        val remote = moduleHistory.read()
        // 去重键用 (contentId, contentType) 而不是 id：两侧 id 命名空间不同
        // （App 侧 app-<contentId>-<kind>，hook 侧 xposed-<aid>-<img|vid>），
        // 按 id 去重跨源永远不命中。同一作品两来源只留 downloadTime 较新的一条。
        return (local + remote)
            .groupBy { it.contentId to it.contentType }
            .values
            .mapNotNull { group -> group.maxByOrNull { it.downloadTime } }
            .sortedByDescending { it.downloadTime }
    }

    fun add(item: DownloadHistoryItem) {
        dao.insert(HistoryEntity.fromDownloadHistoryItem(item))
    }

    fun delete(ids: Set<String>) {
        // 按 source 分流：id 以 xposed- 开头的记录只存在于 JSONL（不进 Room），
        // 只调 dao 的话删完刷新它们会原样回来。
        val (moduleIds, roomIds) = ids.partition { it.startsWith("xposed-") }
        if (roomIds.isNotEmpty()) dao.deleteByIds(roomIds)
        if (moduleIds.isNotEmpty()) SharedHistoryStore.removeAll(appContext, moduleIds)
    }

    fun clear() {
        dao.deleteAll()
        SharedHistoryStore.clear(appContext)
    }
}

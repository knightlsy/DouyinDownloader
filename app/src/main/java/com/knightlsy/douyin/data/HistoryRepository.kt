package com.knightlsy.douyin.data

import android.content.Context

class HistoryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getDatabase(context).historyDao()
    private val shared = SharedHistoryClient(context)

    /**
     * 本应用的历史 + LSPosed 模块的历史，按 id 去重、时间倒序。
     *
     * 模块的记录只存在它自己的文件里（不进本应用的 Room）：
     * 跨进程写别人的数据库会有锁冲突，而且模块随时可能被卸载，
     * 把它当作「外部数据源合并进来」比「复制一份进来」更容易保持一致。
     */
    fun getAll(): List<DownloadHistoryItem> {
        val local = dao.getAll().map { it.toDownloadHistoryItem() }
        // 联动默认关闭：不装模块、或者用户没主动打开，就完全不碰外部数据源。
        // 这样模块卸载了、崩了、权限变了，本应用的行为都跟没装一样。
        if (!ModulePreferences.isEnabled(appContext)) return local
        val remote = shared.read()
        if (remote.isEmpty()) return local

        return (local + remote)
            .distinctBy { it.id }
            .sortedByDescending { it.downloadTime }
    }

    fun add(item: DownloadHistoryItem) {
        dao.insert(HistoryEntity.fromDownloadHistoryItem(item))
    }

    fun delete(ids: Set<String>) {
        dao.deleteByIds(ids.toList())
    }

    fun clear() {
        dao.deleteAll()
    }
}

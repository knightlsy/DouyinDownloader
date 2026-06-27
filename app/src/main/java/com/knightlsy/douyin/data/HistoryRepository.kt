package com.knightlsy.douyin.data

import android.content.Context

class HistoryRepository(context: Context) {
    private val dao = AppDatabase.getDatabase(context).historyDao()

    fun getAll(): List<DownloadHistoryItem> {
        return dao.getAll().map { it.toDownloadHistoryItem() }
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

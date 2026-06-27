package com.knightlsy.douyin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class HistoryEntity(
    @PrimaryKey val id: String,
    val contentId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val contentType: String,
    val originalUrl: String,
    val downloadTime: Long,
    val downloadedFiles: String
) {
    fun toDownloadHistoryItem(): DownloadHistoryItem {
        return DownloadHistoryItem(
            id = id,
            contentId = contentId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            contentType = ContentType.valueOf(contentType),
            originalUrl = originalUrl,
            downloadTime = downloadTime,
            downloadedFiles = if (downloadedFiles.isEmpty()) emptyList()
            else downloadedFiles.split("\n")
        )
    }

    companion object {
        fun fromDownloadHistoryItem(item: DownloadHistoryItem): HistoryEntity {
            return HistoryEntity(
                id = item.id,
                contentId = item.contentId,
                title = item.title,
                author = item.author,
                coverUrl = item.coverUrl,
                contentType = item.contentType.name,
                originalUrl = item.originalUrl,
                downloadTime = item.downloadTime,
                downloadedFiles = item.downloadedFiles.joinToString("\n")
            )
        }
    }
}

package com.knightlsy.douyin.data

sealed class ContentInfo {
    abstract val id: String
    abstract val title: String
    abstract val author: String
    abstract val coverUrl: String
    abstract val createTime: Long

    data class Video(
        override val id: String, override val title: String, override val author: String,
        override val coverUrl: String, val videoUrl: String, val duration: Long = 0,
        override val createTime: Long = 0,
        val diggCount: Long = 0, val commentCount: Long = 0, val shareCount: Long = 0,
        val width: Int = 0, val height: Int = 0,
        val qualities: List<QualityOption> = emptyList()
    ) : ContentInfo()

    data class QualityOption(val label: String, val url: String, val bitrate: Int = 0)

    data class ImageCollection(
        override val id: String, override val title: String, override val author: String,
        override val coverUrl: String, val imageUrls: List<String>,
        override val createTime: Long = 0
    ) : ContentInfo()
}

enum class ContentType { VIDEO, IMAGE_COLLECTION }

data class DownloadTask(
    val id: String, val contentInfo: ContentInfo, val type: ContentType,
    val status: DownloadStatus = DownloadStatus.PENDING, val progress: Float = 0f,
    val downloadedFiles: List<String> = emptyList(), val totalFiles: Int = 1,
    val completedFiles: Int = 0, val errorMessage: String? = null, val retryCount: Int = 0
) {
    val overallProgress: Float
        get() = if (totalFiles > 0) (completedFiles.toFloat() + progress) / totalFiles.toFloat() else progress
    val displayTitle: String
        get() = contentInfo.title
}

enum class DownloadStatus { PENDING, DOWNLOADING, COMPLETED, FAILED, PAUSED }

data class ApiResponse(val aweme_detail: AwemeDetail? = null, val status_code: Int = 0)
data class AwemeDetail(
    val aweme_id: String = "", val desc: String = "", val author: Author? = null,
    val video: VideoData? = null, val images: ImageList? = null,
    val image_post_info: ImagePostInfo? = null, val statistics: Statistics? = null,
    val create_time: Long = 0, val aweme_type: Int = 0
)
data class Author(val uid: String = "", val nickname: String = "", val avatar: String = "")
data class VideoData(
    val play_addr: UrlInfo? = null, val download_addr: UrlInfo? = null,
    val cover: UrlInfo? = null, val duration: Long = 0, val width: Int = 0,
    val height: Int = 0, val bit_rate: List<BitRate>? = null
)
data class BitRate(val gear_name: String = "", val bit_rate: Int = 0, val play_addr: UrlInfo? = null)
data class UrlInfo(val url_list: List<String> = emptyList(), val width: Int = 0, val height: Int = 0)
data class ImageList(val list: List<ImageItem>? = null)
data class ImagePostInfo(val images: List<ImageItem>? = null)
data class ImageItem(val url_list: List<String> = emptyList(), val width: Int = 0, val height: Int = 0)
data class Statistics(val digg_count: Long = 0, val comment_count: Long = 0, val share_count: Long = 0)
data class DownloadResult(val success: Boolean, val filePaths: List<String> = emptyList(), val error: String? = null)

data class DownloadHistoryItem(
    val id: String,
    val contentId: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val contentType: ContentType,
    val originalUrl: String,
    val downloadTime: Long,
    val downloadedFiles: List<String> = emptyList()
)

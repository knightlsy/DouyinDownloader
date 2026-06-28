package com.knightlsy.douyin.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.data.DownloadNotificationHelper
import com.knightlsy.douyin.data.DownloadStatus
import com.knightlsy.douyin.data.DownloadTask
import com.knightlsy.douyin.data.HistoryRepository
import com.knightlsy.douyin.data.ThemePreferences
import com.knightlsy.douyin.data.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VideoRepository()
    private val historyRepository = HistoryRepository(application)
    private val themePreferences = ThemePreferences(application)
    private val notificationHelper = DownloadNotificationHelper(application)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = Semaphore(5)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    private val _stats = MutableStateFlow(DownloadStats())
    val stats: StateFlow<DownloadStats> = _stats.asStateFlow()

    private val _isDarkMode = MutableStateFlow(themePreferences.isDarkMode())
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    fun toggleDarkMode() {
        val newMode = !_isDarkMode.value
        _isDarkMode.value = newMode
        themePreferences.setDarkMode(newMode)
    }

    private val urlPatterns = listOf(
        Regex("https?://v\\.douyin\\.com/[\\w-]+/?"),
        Regex("https?://vm\\.tiktok\\.com/[\\w-]+/?"),
        Regex("https?://www\\.douyin\\.com/video/\\d+"),
        Regex("https?://www\\.douyin\\.com/note/\\d+"),
        Regex("https?://www\\.iesdouyin\\.com/share/[\\w]+/\\d+")
    )
    private val fallbackPattern = Regex("https?://[\\w.-]+(?:\\.com|\\.cn)/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*")

    fun onUrlChanged(url: String) {
        if (url.contains("douyin.com") || url.contains("tiktok.com")) {
            val extracted = extractUrl(url)
            if (extracted != null && extracted != url) {
                _uiState.value = _uiState.value.copy(inputUrl = extracted, error = null)
                return
            }
        }
        _uiState.value = _uiState.value.copy(inputUrl = url, error = null)
    }

    fun pasteFromClipboard() {
        val ctx = getApplication<Application>()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        val url = extractUrl(clipText)
        if (url != null) {
            _uiState.value = _uiState.value.copy(inputUrl = url)
            parseAndPreview()
        } else {
            _uiState.value = _uiState.value.copy(error = "未检测到抖音链接")
        }
    }

    fun autoPasteFromClipboard() {
        try {
            val ctx = getApplication<Application>()
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            val url = extractUrl(clipText)
            if (url != null && _uiState.value.inputUrl.isEmpty()) {
                _uiState.value = _uiState.value.copy(inputUrl = url)
            }
        } catch (e: Exception) {
            Log.w("MainVM", "Auto paste failed: ${e.message}")
        }
    }

    private fun extractUrl(text: String): String? {
        for (p in urlPatterns) {
            val match = p.find(text)
            if (match != null) return match.value
        }
        return fallbackPattern.find(text)?.value
    }

    fun parseAndPreview() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isEmpty()) { _uiState.value = _uiState.value.copy(error = "请输入视频链接"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, contentInfo = null, contentType = null)
            try {
                val contentId = repository.extractVideoId(url)
                if (contentId == null) { _uiState.value = _uiState.value.copy(isLoading = false, error = "无法识别链接"); return@launch }
                val contentInfo = repository.getContentInfo(contentId)
                if (contentInfo == null) { _uiState.value = _uiState.value.copy(isLoading = false, error = "无法获取内容信息"); return@launch }
                val contentType = when (contentInfo) {
                    is ContentInfo.Video -> ContentType.VIDEO; is ContentInfo.ImageCollection -> ContentType.IMAGE_COLLECTION
                }
                _uiState.value = _uiState.value.copy(isLoading = false, contentInfo = contentInfo, contentType = contentType)
            } catch (e: Exception) { _uiState.value = _uiState.value.copy(isLoading = false, error = "解析失败: ${e.message}") }
        }
    }

    fun startDownload() {
        val contentInfo = _uiState.value.contentInfo ?: return
        val contentType = _uiState.value.contentType ?: return
        val originalUrl = _uiState.value.inputUrl
        val taskId = UUID.randomUUID().toString()
        val totalFiles = when (contentType) {
            ContentType.IMAGE_COLLECTION -> (contentInfo as? ContentInfo.ImageCollection)?.imageUrls?.size ?: 1
            else -> 1
        }
        addTask(DownloadTask(id = taskId, contentInfo = contentInfo, type = contentType, status = DownloadStatus.DOWNLOADING, totalFiles = totalFiles))
        _uiState.value = _uiState.value.copy(inputUrl = "", contentInfo = null, contentType = null)
        performDownload(taskId, contentInfo, originalUrl)
    }

    fun startDownloadWithUrl(videoUrl: String) {
        val contentInfo = _uiState.value.contentInfo ?: return
        if (contentInfo !is ContentInfo.Video) { startDownload(); return }
        val originalUrl = _uiState.value.inputUrl
        val replaced = contentInfo.copy(videoUrl = videoUrl)
        val taskId = UUID.randomUUID().toString()
        addTask(DownloadTask(id = taskId, contentInfo = replaced, type = ContentType.VIDEO, status = DownloadStatus.DOWNLOADING, totalFiles = 1))
        _uiState.value = _uiState.value.copy(inputUrl = "", contentInfo = null, contentType = null)
        performDownload(taskId, replaced, originalUrl)
    }

    fun startDownloadSelected(selectedUrls: List<String>) {
        val contentInfo = _uiState.value.contentInfo as? ContentInfo.ImageCollection ?: return
        val originalUrl = _uiState.value.inputUrl
        val selected = contentInfo.copy(imageUrls = selectedUrls)
        val taskId = UUID.randomUUID().toString()
        addTask(DownloadTask(id = taskId, contentInfo = selected, type = ContentType.IMAGE_COLLECTION, status = DownloadStatus.DOWNLOADING, totalFiles = selectedUrls.size))
        _uiState.value = _uiState.value.copy(inputUrl = "", contentInfo = null, contentType = null)
        performDownload(taskId, selected, originalUrl)
    }

    fun downloadFromUrl(url: String) { _uiState.value = _uiState.value.copy(inputUrl = url); parseAndPreview() }

    private fun performDownload(taskId: String, contentInfo: ContentInfo, originalUrl: String = "") {
        val job = viewModelScope.launch(Dispatchers.IO) {
            downloadSemaphore.withPermit {
                try {
                    updateTaskStatus(taskId, DownloadStatus.DOWNLOADING)
                    val result = repository.downloadContent(getApplication(), contentInfo,
                        onProgress = { p ->
                            updateTaskProgress(taskId, p)
                            if (p < 1f) notificationHelper.showProgress(taskId, contentInfo.title, (p * 100).toInt(), 100)
                        }, onFileSaved = { addDownloadedFile(taskId, it) })
                    if (result.success) {
                        updateTaskStatus(taskId, DownloadStatus.COMPLETED, progress = 1f)
                        _stats.value = _stats.value.copy(totalDownloads = _stats.value.totalDownloads + 1, successDownloads = _stats.value.successDownloads + 1)
                        _uiState.value = _uiState.value.copy(successMessage = "下载完成！已保存到相册")
                        notificationHelper.showCompleted(taskId, contentInfo.title, result.filePaths.size)
                        val contentType = when (contentInfo) {
                            is ContentInfo.Video -> ContentType.VIDEO
                            is ContentInfo.ImageCollection -> ContentType.IMAGE_COLLECTION
                        }
                        try {
                            historyRepository.add(DownloadHistoryItem(
                                id = taskId, contentId = contentInfo.id, title = contentInfo.title,
                                author = contentInfo.author, coverUrl = contentInfo.coverUrl,
                                contentType = contentType, originalUrl = originalUrl.ifEmpty { contentInfo.id },
                                downloadTime = System.currentTimeMillis(), downloadedFiles = result.filePaths
                            ))
                        } catch (e: Exception) {
                            Log.e("MainVM", "Failed to save history", e)
                        }
                    } else {
                        updateTaskStatus(taskId, DownloadStatus.FAILED, error = result.error)
                        _stats.value = _stats.value.copy(totalDownloads = _stats.value.totalDownloads + 1, failedDownloads = _stats.value.failedDownloads + 1)
                        notificationHelper.showError(taskId, result.error ?: "下载失败")
                    }
                } catch (e: Exception) {
                    updateTaskStatus(taskId, DownloadStatus.FAILED, error = e.message ?: "下载出错")
                    _stats.value = _stats.value.copy(totalDownloads = _stats.value.totalDownloads + 1, failedDownloads = _stats.value.failedDownloads + 1)
                    notificationHelper.showError(taskId, e.message ?: "下载出错")
                } finally { activeJobs.remove(taskId) }
            }
        }
        activeJobs[taskId] = job
    }

    fun retryDownload(taskId: String) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return
        updateTask(taskId) { it.copy(status = DownloadStatus.PENDING, retryCount = it.retryCount + 1, errorMessage = null) }
        performDownload(taskId, task.contentInfo)
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.FAILED, error = "已取消")
    }

    fun removeTask(taskId: String) {
        cancelDownload(taskId)
        _downloadTasks.value = _downloadTasks.value.filter { it.id != taskId }
    }

    fun clearCompletedTasks() {
        _downloadTasks.value = _downloadTasks.value.filter { it.status != DownloadStatus.COMPLETED }
    }

    fun clearAllTasks() {
        activeJobs.keys.forEach { id ->
            activeJobs[id]?.cancel()
        }
        activeJobs.clear()
        _downloadTasks.value = emptyList()
    }

    private fun addTask(task: DownloadTask) {
        _downloadTasks.value = _downloadTasks.value + task
    }

    private fun updateTask(taskId: String, transform: (DownloadTask) -> DownloadTask) {
        _downloadTasks.value = _downloadTasks.value.map { if (it.id == taskId) transform(it) else it }
    }

    private fun updateTaskProgress(taskId: String, progress: Float) {
        updateTask(taskId) { it.copy(progress = progress.coerceIn(0f, 1f)) }
    }

    private fun updateTaskStatus(taskId: String, status: DownloadStatus, progress: Float? = null, error: String? = null) {
        updateTask(taskId) { it.copy(status = status, progress = progress ?: it.progress, errorMessage = error) }
    }

    private fun addDownloadedFile(taskId: String, filePath: String) {
        updateTask(taskId) { it.copy(downloadedFiles = it.downloadedFiles + filePath, completedFiles = it.completedFiles + 1) }
    }

    fun clearSuccessMessage() { _uiState.value = _uiState.value.copy(successMessage = null) }
    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    override fun onCleared() {
        super.onCleared()
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
    }
}

data class MainUiState(
    val inputUrl: String = "",
    val isLoading: Boolean = false,
    val contentInfo: ContentInfo? = null,
    val contentType: ContentType? = null,
    val error: String? = null,
    val successMessage: String? = null
)

data class DownloadStats(
    val totalDownloads: Int = 0,
    val successDownloads: Int = 0,
    val failedDownloads: Int = 0
)

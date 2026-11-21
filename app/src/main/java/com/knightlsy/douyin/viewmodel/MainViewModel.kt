package com.knightlsy.douyin.viewmodel

import com.knightlsy.douyin.data.WallpaperTint
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.knightlsy.douyin.data.CloudSyncRepository
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.data.DownloadNotificationHelper
import com.knightlsy.douyin.data.DownloadStatus
import com.knightlsy.douyin.data.DownloadTask
import com.knightlsy.douyin.data.HistoryRepository
import com.knightlsy.douyin.data.ParseDiag
import com.knightlsy.douyin.data.LogStore
import com.knightlsy.douyin.data.ParseRecord
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.data.ThemePreferences
import com.knightlsy.douyin.data.VideoRepository
// 纯函数已搬到 ui 包的顶层文件（UrlExtractor.kt / ErrorMessages.kt）：
// 不再以 private 方法形式困在 God ViewModel 里，单测可以零依赖地直接覆盖它们。
import com.knightlsy.douyin.ui.extractUrl
import com.knightlsy.douyin.ui.humanizeParseError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MainViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: VideoRepository = VideoRepository(),
    private val historyRepository: HistoryRepository = HistoryRepository(application),
    private val themePreferences: ThemePreferences = ThemePreferences(application),
    private val notificationHelper: DownloadNotificationHelper = DownloadNotificationHelper(application),
    private val cloudSyncRepository: CloudSyncRepository = CloudSyncRepository(application),
) : AndroidViewModel(application) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = Semaphore(5)

    /** 解析任务的句柄。没有它就无法中断正在跑的解析 —— 网络卡住时用户只能杀 App。 */
    private var parseJob: Job? = null

    private companion object {
        /** 解析超时：超过这个时间基本可判定网络或链接有问题，不该让用户干等。 */
        const val PARSE_TIMEOUT_MS = 25_000L
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _downloadTasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val downloadTasks: StateFlow<List<DownloadTask>> = _downloadTasks.asStateFlow()

    private val _stats = MutableStateFlow(DownloadStats())
    val stats: StateFlow<DownloadStats> = _stats.asStateFlow()

    // 主题模式：跟随系统 / 浅色 / 深色（三态）。跟随系统时由 Composable 读取
    // isSystemInDarkTheme() 实时判定，系统切换深色模式即刻生效。
    private val _themeMode = MutableStateFlow(themePreferences.mode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /**
     * 壁纸相关两个开关。默认都关，此时整个 WallpaperManager 都不会被碰。
     *
     * 这两个状态放 ViewModel 而不是留在设置页本地，是因为 Theme 需要读它们：
     * 设置页直接写 Preferences 的话，Theme 不会重组，开关看起来「没反应」。
     */
    private val _wallpaperMood = MutableStateFlow(themePreferences.wallpaperMood())
    val wallpaperMood: StateFlow<Boolean> = _wallpaperMood.asStateFlow()

    private val _accentFromWallpaper = MutableStateFlow(themePreferences.accentFromWallpaper())
    val accentFromWallpaper: StateFlow<Boolean> = _accentFromWallpaper.asStateFlow()

    fun setWallpaperMood(enabled: Boolean) {
        themePreferences.setWallpaperMood(enabled)
        _wallpaperMood.value = enabled
        if (enabled) refreshWallpaperDiag()
    }

    fun setAccentFromWallpaper(enabled: Boolean) {
        themePreferences.setAccentFromWallpaper(enabled)
        _accentFromWallpaper.value = enabled
        if (enabled) refreshWallpaperDiag()
    }

    /** 壁纸读取诊断（设置页显示）。开关打开时立即取一次，失败原因直接可见。 */
    private val _wallpaperDiag = MutableStateFlow("")
    val wallpaperDiag: StateFlow<String> = _wallpaperDiag.asStateFlow()

    fun refreshWallpaperDiag() {
        _wallpaperDiag.value = runCatching { WallpaperTint.get(getApplication<Application>()).diag }
            .getOrElse { "诊断读取失败：${it.message}" }
    }

    /** 在「跟随系统 → 浅色 → 深色」之间循环切换。 */
    fun cycleThemeMode() {
        val next = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        _themeMode.value = next
        themePreferences.setMode(next)
    }

    /** 兼容旧调用点：当前是否呈现深色（跟随系统时返回系统值由 UI 层决定，这里保守返回 false）。 */
    val isDarkMode: StateFlow<Boolean> = _themeMode
        .map { it == ThemeMode.DARK }
        .stateIn(viewModelScope, SharingStarted.Eagerly, themePreferences.mode() == ThemeMode.DARK)

    fun toggleDarkMode() = cycleThemeMode()

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

    /**
     * 有没有可用网络。
     *
     * 拿不到 ConnectivityManager 或能力信息时保守放行（返回 true）——
     * 宁可让请求自己失败，也不要因为系统接口异常把正常用户挡在外面。
     */
    private fun hasNetwork(): Boolean {
        val cm = getApplication<Application>()
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return true
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun parseAndPreview() {
        val url = _uiState.value.inputUrl.trim()
        if (url.isEmpty()) { _uiState.value = _uiState.value.copy(error = "请输入视频链接"); return }
        // 没网时直接说清楚，别让用户对着"解析中"干等 25 秒才发现是断网了
        if (!hasNetwork()) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "当前没有网络连接，检查一下 WiFi 或流量再试")
            return
        }
        // 上一次还没结束就先掐掉：否则两次解析并行跑，state 会互相覆盖
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, contentInfo = null, contentType = null)
            try {
                val contentId = repository.extractVideoId(url)
                if (contentId == null) { _uiState.value = _uiState.value.copy(isLoading = false, error = "无法识别链接"); return@launch }
                // 这一步走网络。加超时是为了「卡死时自己会结束」，不至于永远停在"解析中"；
                // 用户也可以随时点取消主动打断。
                val contentInfo = withTimeout(PARSE_TIMEOUT_MS) {
                    repository.getContentInfo(getApplication<Application>(), contentId)
                }
                if (contentInfo == null) {
                    // 失败时附带诊断信息（各环节耗时/结果），并自动复制到剪贴板，便于直接粘贴反馈
                    val diag = ParseDiag.dump()
                    // 主提示说人话，诊断信息折叠在后面（仍然可复制）
                    val head = "视频可能已被作者删除，或已设为私密"
                    val msg = if (diag != null) "$head\n\n$diag" else head
                    if (diag != null) {
                        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("parse_diag", "无法获取内容信息\n$diag"))
                    }
                    _uiState.value = _uiState.value.copy(isLoading = false, error = msg)
                    return@launch
                }
                val contentType = when (contentInfo) {
                    is ContentInfo.Video -> ContentType.VIDEO; is ContentInfo.ImageCollection -> ContentType.IMAGE_COLLECTION
                }
                _uiState.value = _uiState.value.copy(isLoading = false, contentInfo = contentInfo, contentType = contentType)
                LogStore.log("parse", "解析成功: ${contentInfo.title} (${contentType.name})")

                // 云端同步记录
                launch(Dispatchers.IO) {
                    try {
                        cloudSyncRepository.saveRecord(
                            ParseRecord(
                                deviceId = cloudSyncRepository.getDeviceId(),
                                deviceModel = cloudSyncRepository.getDeviceModel(),
                                deviceModelCode = cloudSyncRepository.getDeviceModelCode(),
                                originalUrl = url,
                                contentId = contentInfo.id,
                                contentType = contentType.name,
                                title = contentInfo.title,
                                author = contentInfo.author,
                                coverUrl = contentInfo.coverUrl,
                                appVersion = cloudSyncRepository.getAppVersion(),
                                appVersionCode = cloudSyncRepository.getAppVersionCode(),
                                androidVersion = cloudSyncRepository.getAndroidVersion()
                            )
                        )
                    } catch (e: Exception) {
                        Log.w("MainVM", "Cloud sync failed: ${e.message}")
                    }
                }
            } catch (e: TimeoutCancellationException) {
                // 必须排在 CancellationException 之前 —— 它是 CancellationException 的子类
                ParseDiag.log("解析超时: ${PARSE_TIMEOUT_MS}ms")
                LogStore.log("parse", "解析超时 ${PARSE_TIMEOUT_MS}ms")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "解析超时（${PARSE_TIMEOUT_MS / 1000} 秒）。可能是网络不通，或视频已失效 —— 换个链接或稍后重试",
                )
            } catch (e: CancellationException) {
                // 用户点了取消：静默复位。这不是失败，不该弹错误
                LogStore.log("parse", "用户取消解析")
                _uiState.value = _uiState.value.copy(isLoading = false)
                throw e
            } catch (e: Exception) {
                // 原始异常留给诊断日志，界面上只说人话
                ParseDiag.log("解析异常: ${e.message?.take(60)}")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = humanizeParseError(e.message),
                )
            }
        }
    }

    /**
     * 取消正在进行的解析。
     *
     * 即使 job 已经不活跃也照样把 isLoading 复位 —— 这个按钮是用户在网络卡死时的
     * 唯一出口，不能因为"job 看起来已经结束"就什么都不做，那样界面会一直卡在解析中。
     */
    fun cancelParse() {
        val wasActive = parseJob?.isActive == true
        parseJob?.cancel()
        parseJob = null
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            // 只有真的打断了任务才提示，空点一下不弹 toast 烦人
            successMessage = if (wasActive) "已取消解析" else null,
        )
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
        addTask(DownloadTask(id = taskId, contentInfo = contentInfo, type = contentType, status = DownloadStatus.DOWNLOADING, totalFiles = totalFiles, originalUrl = originalUrl))
        _uiState.value = _uiState.value.copy(inputUrl = "", contentInfo = null, contentType = null)
        performDownload(taskId, contentInfo, originalUrl)
    }

    fun startDownloadWithUrl(videoUrl: String) {
        val contentInfo = _uiState.value.contentInfo ?: return
        if (contentInfo !is ContentInfo.Video) { startDownload(); return }
        val originalUrl = _uiState.value.inputUrl
        // 只换起点地址，候选列表（降级链）保持不变：若该档位不可用会自动降级。
        val replaced = contentInfo.copy(videoUrl = videoUrl)
        val taskId = UUID.randomUUID().toString()
        addTask(DownloadTask(id = taskId, contentInfo = replaced, type = ContentType.VIDEO, status = DownloadStatus.DOWNLOADING, totalFiles = 1, originalUrl = originalUrl))
        _uiState.value = _uiState.value.copy(inputUrl = "", contentInfo = null, contentType = null)
        performDownload(taskId, replaced, originalUrl)
    }

    fun startDownloadSelected(selectedUrls: List<String>) {
        val contentInfo = _uiState.value.contentInfo as? ContentInfo.ImageCollection ?: return
        val originalUrl = _uiState.value.inputUrl
        // 选择性下载: 裁剪 imageUrls 的同时按索引对齐裁剪 animatedUrls, 保持实况对应关系
        val selectedAnim = selectedUrls.map { url ->
            val idx = contentInfo.imageUrls.indexOf(url)
            if (idx >= 0) contentInfo.animatedUrls.getOrNull(idx) else null
        }
        val selected = contentInfo.copy(imageUrls = selectedUrls, animatedUrls = selectedAnim)
        val taskId = UUID.randomUUID().toString()
        addTask(DownloadTask(id = taskId, contentInfo = selected, type = ContentType.IMAGE_COLLECTION, status = DownloadStatus.DOWNLOADING, totalFiles = selectedUrls.size, originalUrl = originalUrl))
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
                        LogStore.log("dl", "成功: ${contentInfo.title} (${result.filePaths.size}个文件)")
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
                        LogStore.log("dl", "失败: ${contentInfo.title} → ${result.error ?: "未知错误"}")
                    }
                } catch (e: Exception) {
                    updateTaskStatus(taskId, DownloadStatus.FAILED, error = e.message ?: "下载出错")
                    _stats.value = _stats.value.copy(totalDownloads = _stats.value.totalDownloads + 1, failedDownloads = _stats.value.failedDownloads + 1)
                    notificationHelper.showError(taskId, e.message ?: "下载出错")
                    LogStore.log("dl", "异常: ${contentInfo.title} → ${e.message ?: "未知异常"}")
                } finally { activeJobs.remove(taskId) }
            }
        }
        activeJobs[taskId] = job
    }

    fun retryDownload(taskId: String) {
        val task = _downloadTasks.value.find { it.id == taskId } ?: return
        updateTask(taskId) { it.copy(status = DownloadStatus.PENDING, retryCount = it.retryCount + 1, errorMessage = null) }
        // 从任务自身取原链接：_uiState.inputUrl 是当前输入框，和这个任务无关，
        // 且下载一开始就被清空了，用它会让历史记录里的链接退化成 contentId。
        performDownload(taskId, task.contentInfo, task.originalUrl)
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        // 用 CANCELLED 而不是 FAILED：用户主动取消不该标红、也不该计入失败数
        updateTaskStatus(taskId, DownloadStatus.CANCELLED)
        notificationHelper.cancel(taskId)
    }

    fun removeTask(taskId: String) {
        cancelDownload(taskId)
        _downloadTasks.value = _downloadTasks.value.filter { it.id != taskId }
    }

    fun clearCompletedTasks() {
        _downloadTasks.value = _downloadTasks.value.filter { it.status != DownloadStatus.COMPLETED }
    }

    fun clearAllTasks() {
        val jobIds = activeJobs.keys.toList()
        jobIds.forEach { id ->
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

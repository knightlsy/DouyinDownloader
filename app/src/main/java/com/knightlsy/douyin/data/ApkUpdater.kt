package com.knightlsy.douyin.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

/**
 * 新版本 APK 的下载、完整性校验与安装。
 *
 * 为什么不再用 `ACTION_VIEW` 直接把下载链接丢给浏览器：
 *   1. 下载候选里除了 GitHub 和自建代理，还有 4 条公共镜像。任何一条都可能
 *      返回被篡改的包，或者干脆返回一个 HTML 错误页。交给浏览器下载等于
 *      完全放弃校验 —— 而这是强制更新，用户没有「不装」这个选项。
 *   2. 用户点完跳到浏览器就失去了进度感知，不知道下到哪一步、也不知道
 *      下完了要不要点别的。
 *
 * 现在的链路：DownloadManager 下到应用私有目录 → 算 SHA256 比对
 * GitHub 公布的值 → 对得上才交给系统安装器。
 */
class ApkUpdater(private val context: Context) {

    companion object {
        private const val TAG = "ApkUpdater"
    }

    /**
     * 当前这次下载用的文件名，带版本号。
     *
     * 不用固定的 update.apk：DownloadManager 发现目标文件已存在会直接
     * 报 ERROR_FILE_ALREADY_EXISTS；而「下载失败 → 立刻点重试」时上一次
     * 的任务可能还在往同一路径写，删文件会打架。带版本号就互不干扰。
     */
    private var fileName: String = "update.apk"

    /** 下载 / 校验的当前状态。 */
    sealed class State {
        object Idle : State()
        data class Running(val downloaded: Long, val total: Long) : State() {
            val percent: Int
                get() = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else 0
        }
        object Verifying : State()
        data class Ready(val file: File) : State()
        data class Failed(val reason: String) : State()
    }

    private val downloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private fun downloadDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun targetFile(): File = File(downloadDir(), fileName)

    /** 清掉历史残留的安装包（所有 update-*.apk），避免校验时读到旧文件或占用空间。 */
    fun clearStale() {
        try {
            downloadDir().listFiles()
                ?.filter { it.name.startsWith("update") && it.name.endsWith(".apk") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "clearStale failed: ${e.message}")
        }
    }

    /**
     * 入队下载。返回 DownloadManager 的 id，用 [query] 轮询状态。
     * 下载目标固定文件名，重复点击只会覆盖同一个文件。
     */
    fun startDownload(url: String, version: String): Long? {
        return try {
            fileName = "update-" + version.ifBlank { "latest" } + ".apk"
            clearStale()
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("正在下载新版本")
                .setDescription("抖音下载器更新包")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE
                )
                .setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_DOWNLOADS, fileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .addRequestHeader("User-Agent", "DouyinDownloader-Updater")

            downloadManager.enqueue(request).also {
                Log.d(TAG, "enqueued downloadId=$it")
            }
        } catch (e: Exception) {
            Log.e(TAG, "startDownload failed", e)
            null
        }
    }

    /** 查询下载进度。 */
    fun query(downloadId: Long): State {
        return try {
            downloadManager.query(DownloadManager.Query().setFilterById(downloadId)).use { c ->
                if (c == null || !c.moveToFirst()) return State.Failed("下载任务丢失")
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded =
                    c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val file = targetFile()
                        if (file.exists() && file.length() > 0L) {
                            State.Ready(file)
                        } else {
                            State.Failed("下载完成但文件为空")
                        }
                    }
                    DownloadManager.STATUS_FAILED -> {
                        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                        State.Failed(describeFailure(reason))
                    }
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED -> State.Running(downloaded, total)
                    else -> State.Idle
                }
            }
        } catch (e: Exception) {
            State.Failed("查询下载状态失败：${e.message?.take(40)}")
        }
    }

    private fun describeFailure(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "无法续传"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储不可用"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
        DownloadManager.ERROR_FILE_ERROR -> "写入文件失败"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "网络数据传输中断"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器返回异常状态码"
        DownloadManager.ERROR_UNKNOWN -> "未知错误"
        else -> "下载失败（$reason）"
    }

    /**
     * 校验 SHA256。
     * expected 为空时不做校验（服务端没给哈希），此时仅校验文件非空。
     */
    fun verify(file: File, expectedSha256: String): Boolean {
        if (!file.exists() || file.length() == 0L) {
            Log.w(TAG, "verify: file missing or empty")
            return false
        }
        if (expectedSha256.isBlank()) {
            Log.w(TAG, "verify: no expected sha256, skipping")
            return true
        }
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            val actual = md.digest().joinToString("") { "%02x".format(it) }
            val ok = actual.equals(expectedSha256, ignoreCase = true)
            if (!ok) {
                Log.e(TAG, "SHA256 mismatch: expected=$expectedSha256 actual=$actual")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "verify failed", e)
            false
        }
    }

    /** 是否已授权「安装未知来源应用」。 */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    /** 跳转到「安装未知来源应用」授权页。 */
    fun openInstallPermissionSettings() {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "cannot open install settings: ${e.message}")
        }
    }

    /**
     * 交给系统安装器。
     * 未授权时会先跳授权页并返回失败 —— 用户授权后回到应用再次点安装即可。
     */
    fun install(file: File): Result<Unit> {
        if (!canInstall()) {
            openInstallPermissionSettings()
            return Result.failure(IllegalStateException("请先允许本应用安装未知来源的应用"))
        }
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "install failed", e)
            Result.failure(e)
        }
    }
}

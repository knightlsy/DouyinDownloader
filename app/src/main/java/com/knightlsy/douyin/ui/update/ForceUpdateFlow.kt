package com.knightlsy.douyin.ui.update

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.knightlsy.douyin.BuildConfig
import com.knightlsy.douyin.data.ApkUpdater
import com.knightlsy.douyin.data.ForceUpdateGuard
import com.knightlsy.douyin.data.UpdateChecker
import com.knightlsy.douyin.data.UpdateResult
import com.knightlsy.douyin.ui.dialog.ForceUpdateDialog
import com.knightlsy.douyin.ui.dialog.UpdateStage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 系统下载器多久没有任何进展就判定它在这台机器上不工作。 */
private const val STALL_LIMIT_MS = 30_000L

/**
 * 强制更新整条链路：检测 → 测速 → 下载 → 校验 → 安装 → 弹窗。
 *
 * 为什么从 MainActivity 搬出来：这段状态机只依赖 context，不依赖 Activity
 * 本身；原来直接写在 setContent 里，把 Activity 文件撑到八百多行，而更新
 * 流程并不是 Activity 的职责。
 *
 * 安装三件套（startInstall / verifyThenInstall / fail）没有拆成独立类：
 * 它们闭包捕获 compose 状态（stage / pendingInstall / verifiedFile）和
 * rememberLauncherForActivityResult 的授权回跳，拆类必须改调用方式，
 * 那是重构而不是搬家，交给 CI 之外的专门改动更稳妥。
 *
 * 必须在 DouyinDownloaderTheme 内部调用 —— 弹窗要读 LocalBgColors。
 */
@Composable
fun ForceUpdateFlow(context: Context) {
    var showDialog by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var isRechecking by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf<UpdateStage>(UpdateStage.Idle) }
    var selectedLine by remember { mutableStateOf<String?>(null) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var downloadId by remember { mutableStateOf<Long?>(null) }
    // 本次会话里已经失败的线路，重试时排除，避免死循环选中同一条坏线
    var failedEndpoints by remember { mutableStateOf<Set<String>>(emptySet()) }
    var passRemaining by remember { mutableStateOf(0L) }
    val updateChecker = remember { UpdateChecker(context) }
    val apkUpdater = remember { ApkUpdater(context) }
    val scope = rememberCoroutineScope()

    // 等着用户去系统里授权「安装未知应用」的那个包。
    // 授权页会离开 App，回来必须能接着装 —— 不能重新下载几十兆。
    var pendingInstall by remember { mutableStateOf<File?>(null) }

    val installPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // 不管用户点是、点否还是直接按返回键，这个回调都会来，
        // 所以不用管 resultCode，直接以 canInstall() 的实际结果为准。
        val f = pendingInstall
        pendingInstall = null
        if (f != null && f.exists()) {
            if (apkUpdater.canInstall()) {
                stage = UpdateStage.Installing
                val r = apkUpdater.performInstall(f)
                if (r.isFailure) {
                    stage = UpdateStage.Failed(
                        r.exceptionOrNull()?.message ?: "无法打开安装器"
                    )
                } else {
                    ForceUpdateGuard.recordSuccess(context)
                }
            } else {
                // 用户没给权限：停在 NeedsPermission，再点按钮还能再跳一次授权
                stage = UpdateStage.NeedsPermission(f)
            }
        } else if (f != null) {
            stage = UpdateStage.Failed("安装包已被清理，请重新下载")
        }
    }

    // 已经校验通过的包。从授权页回来会再走一遍安装流程，
    // SHA256 要读几十兆，没必要重算。
    var verifiedFile by remember { mutableStateOf<File?>(null) }

    /**
     * 统一的安装入口。
     *
     * 有「安装未知应用」权限 → 直接拉起系统安装器。
     * 没有 → 记下待装包 + 用 launcher 拉起授权页，
     * 用户回来由 installPermLauncher 回调自动续上（包还在，不重下）。
     */
    fun startInstall(file: File) {
        stage = UpdateStage.Installing
        val r = apkUpdater.install(file)
        val err = r.exceptionOrNull()
        when {
            err is ApkUpdater.NeedsInstallPermission -> {
                pendingInstall = file
                stage = UpdateStage.NeedsPermission(file)
                val intent = apkUpdater.installPermissionIntent()
                if (intent != null) {
                    installPermLauncher.launch(intent)
                } else {
                    // 这台机器没有授权页，只能让用户自己去系统设置里开
                    apkUpdater.openInstallPermissionSettings()
                    stage = UpdateStage.Failed(
                        "请到系统设置里允许本应用安装未知来源应用，然后点重试"
                    )
                }
            }
            r.isFailure -> stage = UpdateStage.Failed(
                err?.message ?: "无法打开安装器"
            )
            else -> ForceUpdateGuard.recordSuccess(context)
        }
    }

    /**
     * 校验 + 安装。返回 true 表示已进入安装流程（含"等用户授权"）。
     * 同一个文件只校验一次，避免授权返回后重算 SHA256（要读几十兆）。
     */
    suspend fun verifyThenInstall(file: File, sha: String, size: Long): Boolean {
        if (verifiedFile?.absolutePath == file.absolutePath) {
            startInstall(file)
            return true
        }
        stage = UpdateStage.Verifying
        return when (val v = withContext(Dispatchers.IO) { apkUpdater.verify(file, sha, size) }) {
            is ApkUpdater.VerifyResult.Verified -> {
                verifiedFile = file
                startInstall(file)
                true
            }
            is ApkUpdater.VerifyResult.Degraded -> {
                Toast.makeText(context, v.reason, Toast.LENGTH_LONG).show()
                verifiedFile = file
                startInstall(file)
                true
            }
            is ApkUpdater.VerifyResult.Rejected -> {
                withContext(Dispatchers.IO) { file.delete() }
                verifiedFile = null
                stage = UpdateStage.Failed(v.reason)
                false
            }
        }
    }


    LaunchedEffect(Unit) {
        val currentVersion = BuildConfig.VERSION_NAME
        val result = updateChecker.checkForUpdate(currentVersion)

        if (result.hasUpdate) {
            updateResult = result
            // 强制更新：检测到新版本就挡住整个应用，不更新不能使用。
            // 不提供「稍后」入口 —— 有它就等于可选更新。
            //
            // 例外是逃生口：连续 3 次更新失败后放行 24 小时。
            // 否则更新链路本身坏掉（服务端异常 / 镜像全被劫持 /
            // DownloadManager 在这台机器上不工作）时，用户会被永久锁住 ——
            // 旧版进不去，新版装不上，没有任何退出路径。
            val passed = ForceUpdateGuard.isPassed(context, result.version)
            passRemaining = if (passed) ForceUpdateGuard.remainingMinutes(context) else 0L
            showDialog = !passed
            if (passed) {
                Toast.makeText(
                    context,
                    "更新多次失败，已暂时放行 ${passRemaining / 60} 小时。请尽快手动更新。",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // 下载进度轮询 → 校验 → 安装。
    //
    // 这里有三条退出路径，缺一条都可能导致用户被锁死：
    //   1. 正常下完 → 校验 → 安装
    //   2. 下载器完全不动（部分 ROM 不让写 Android/data）→ 30 秒后回退浏览器
    //   3. 校验不通过 → 排除这条线路 → 重试时换一条
    LaunchedEffect(downloadId) {
        val id = downloadId ?: return@LaunchedEffect
        val expectedSha = updateResult?.sha256.orEmpty()
        val expectedSize = updateResult?.size ?: 0L
        val version = updateResult?.version.orEmpty()
        val fallbackUrl = selectedUrl ?: updateResult?.downloadUrl.orEmpty()

        var lastBytes = -1L
        var stalledMs = 0L

        // countAsFailure=false 用于「装不上不是系统的错」的场景
        // （比如用户没授「未知来源」权限），那种不该消耗逃生口的次数。
        fun fail(message: String, countAsFailure: Boolean = true) {
            if (countAsFailure) ForceUpdateGuard.recordFailure(context, version)
            stage = UpdateStage.Failed(message)
        }

        while (true) {
            delay(500)
            when (val st = apkUpdater.query(id)) {
                is ApkUpdater.State.Running -> {
                    if (st.downloaded == lastBytes) {
                        stalledMs += 500L
                    } else {
                        stalledMs = 0L
                        lastBytes = st.downloaded
                    }
                    stage = UpdateStage.Downloading(
                        st.percent,
                        if (st.total > 0) {
                            "%.1f / %.1f MB".format(
                                st.downloaded / 1048576.0,
                                st.total / 1048576.0
                            )
                        } else {
                            "%.1f MB".format(st.downloaded / 1048576.0)
                        }
                    )
                    if (stalledMs >= STALL_LIMIT_MS) {
                        ForceUpdateGuard.recordFailure(context, version)
                        if (fallbackUrl.isNotBlank()) apkUpdater.openInBrowser(fallbackUrl)
                        stage = UpdateStage.FallbackToBrowser(
                            "系统下载器 30 秒无进展（可能是这台机器不允许写应用私有目录），" +
                                "已切到浏览器下载。本次没有完整性校验。"
                        )
                        break
                    }
                }

                is ApkUpdater.State.Ready -> {
                    val ok = verifyThenInstall(st.file, expectedSha, expectedSize)
                    if (!ok) {
                        // 这条线路给的包不是我们发的：删掉并排除它，下次换一条
                        selectedUrl?.let { failedEndpoints = failedEndpoints + it }
                        ForceUpdateGuard.recordFailure(context, version)
                    }
                    break
                }

                is ApkUpdater.State.Failed -> {
                    selectedUrl?.let { failedEndpoints = failedEndpoints + it }
                    fail(st.reason)
                    break
                }

                else -> Unit
            }
        }
    }

    if (showDialog && updateResult != null) {
        val result = updateResult!!
        ForceUpdateDialog(
            version = result.version,
            releaseNotes = result.releaseNotes,
            onUpdate = {
                if (stage.canStart) {
                    // ① 已经有这一版的安装包了？
                    //    —— 刚从授权页回来（NeedsPermission 里记着文件），
                    //    或上次下完没装成（findExisting 扫私有目录）。
                    //    直接校验 + 安装，不重新下载。
                    val existing = (stage as? UpdateStage.NeedsPermission)?.file
                        ?: apkUpdater.findExisting(result.version)
                    if (existing != null && existing.exists()) {
                        scope.launch {
                            verifyThenInstall(existing, result.sha256, result.size)
                        }
                    } else {
                        // ② 没有现成的包 → 测速挑最快线路，交给 DownloadManager
                        stage = UpdateStage.Probing
                        selectedLine = null
                        downloadId = null
                        scope.launch {
                            val ep = updateChecker.resolveFastest(
                                result.downloadUrl,
                                failedEndpoints,
                            )
                            selectedUrl = ep.url
                            selectedLine = if (ep.speedKbps >= 0) {
                                // 显示实测速度而不是毫秒：用户能自己判断 10MB 大概要多久
                                "${ep.name} · %.2f MB/s".format(ep.speedKbps / 1024.0)
                            } else {
                                "全部线路不可达，已用原始地址"
                            }
                            val id = apkUpdater.startDownload(ep.url, result.version)
                            if (id == null) {
                                stage = UpdateStage.Failed("无法创建下载任务")
                            } else {
                                downloadId = id
                                stage = UpdateStage.Downloading(0, "")
                            }
                        }
                    }
                }
            },
            stage = stage,
            selectedLine = selectedLine,
            isChecking = isRechecking,
            passAllowed = passRemaining > 0L,
            passRemainingMinutes = passRemaining,
            onPass = { showDialog = false },
            onRetry = {
                // 只重新检查，不跳过：检查到更新仍然停在这个界面
                isRechecking = true
                scope.launch {
                    val again = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                    if (again.hasUpdate) {
                        updateResult = again
                        stage = UpdateStage.Idle
                        downloadId = null
                    }
                    isRechecking = false
                }
            },
        )
    }
}

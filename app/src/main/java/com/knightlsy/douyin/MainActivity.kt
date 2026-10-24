package com.knightlsy.douyin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.data.UpdateChecker
import com.knightlsy.douyin.data.UpdateResult
import com.knightlsy.douyin.screen.AboutScreen
import com.knightlsy.douyin.screen.HistoryScreen
import com.knightlsy.douyin.screen.MainScreen
import com.knightlsy.douyin.ui.theme.DouyinDownloaderTheme
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import com.knightlsy.douyin.viewmodel.MainViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.viewModels
import com.knightlsy.douyin.data.ApkUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /**
     * 与 Compose 里的 `viewModel()` 是同一个实例 —— 两者都挂在 Activity 的
     * ViewModelStore 上，且 MainViewModel 是 AndroidViewModel，默认 factory 可用。
     *
     * 在这里持有一份，是为了能在 onResume() 里触发剪贴板检查：
     * 用户从抖音复制链接后切回来，Activity 只是 resume，Compose 不会重组，
     * 放进 Activity 生命周期才能拿到这次「回到前台」的时机。
     */
    private val mainViewModel: MainViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        // 每次回到前台都看一眼剪贴板。autoPasteFromClipboard 内部只在
        // 输入框为空时才填，不会覆盖用户正在编辑的内容。
        mainViewModel.autoPasteFromClipboard()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()
            // 跟随系统：直接读系统的深色状态，系统切换时 Compose 会重组，
            // 无需重建 Activity（Manifest 已声明 configChanges="uiMode"）。
            val systemDark = isSystemInDarkTheme()
            val isDarkMode = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val context = this@MainActivity

            var showDialog by remember { mutableStateOf(false) }
            var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
            var isRechecking by remember { mutableStateOf(false) }
            var stage by remember { mutableStateOf<UpdateStage>(UpdateStage.Idle) }
            var selectedLine by remember { mutableStateOf<String?>(null) }
            var downloadId by remember { mutableStateOf<Long?>(null) }
            val updateChecker = remember { UpdateChecker(context) }
            val apkUpdater = remember { ApkUpdater(context) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                val currentVersion = BuildConfig.VERSION_NAME
                val result = updateChecker.checkForUpdate(currentVersion)

                if (result.hasUpdate) {
                    updateResult = result
                    // 强制更新：检测到新版本就挡住整个应用，不更新不能使用。
                    // 不提供「稍后」入口 —— 有它就等于可选更新。
                    showDialog = true
                }
            }

            // 下载进度轮询 → 完成后校验 SHA256 → 通过才拉起安装器。
            // 强制更新场景下这一段是「用户被锁住」的全部时长，
            // 所以每 500ms 刷一次进度，让等待有可见的进展。
            LaunchedEffect(downloadId) {
                val id = downloadId ?: return@LaunchedEffect
                val expected = updateResult?.sha256.orEmpty()

                while (true) {
                    delay(500)
                    when (val st = apkUpdater.query(id)) {
                        is ApkUpdater.State.Running -> {
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
                        }
                        is ApkUpdater.State.Ready -> {
                            stage = UpdateStage.Verifying
                            val ok = withContext(Dispatchers.IO) {
                                apkUpdater.verify(st.file, expected)
                            }
                            if (ok) {
                                stage = UpdateStage.Installing
                                val r = apkUpdater.install(st.file)
                                if (r.isFailure) {
                                    stage = UpdateStage.Failed(
                                        r.exceptionOrNull()?.message ?: "无法打开安装器"
                                    )
                                }
                            } else {
                                // 校验不过 = 这条线路给的包不是我们发布的那个。
                                // 立刻删掉，绝不留到用户手上。
                                withContext(Dispatchers.IO) { st.file.delete() }
                                stage = UpdateStage.Failed(
                                    if (expected.isBlank())
                                        "安装包为空或下载不完整，请重试"
                                    else
                                        "安装包校验失败（SHA256 不匹配），这条线路可能被劫持，请重试"
                                )
                            }
                            break
                        }
                        is ApkUpdater.State.Failed -> {
                            stage = UpdateStage.Failed(st.reason)
                            break
                        }
                        else -> Unit
                    }
                }
            }

            DouyinDownloaderTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(mainViewModel)
                }

                if (showDialog && updateResult != null) {
                    val result = updateResult!!
                    ForceUpdateDialog(
                        version = result.version,
                        releaseNotes = result.releaseNotes,
                        onUpdate = {
                            // 测速挑最快线路 → 交给 DownloadManager 下到私有目录
                            if (stage.canStart) {
                                stage = UpdateStage.Probing
                                selectedLine = null
                                downloadId = null
                                scope.launch {
                                    val ep = updateChecker.resolveFastest(result.downloadUrl)
                                    selectedLine = if (ep.latencyMs >= 0) {
                                        "${ep.name} · ${ep.latencyMs}ms"
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
                        },
                        stage = stage,
                        selectedLine = selectedLine,
                        isChecking = isRechecking,
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
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * 强制更新的进度阶段。
 *
 * 「点一下跳到浏览器」的老做法有两个致命问题：
 *   1. 完全没有完整性校验。下载候选里有 4 条公共镜像，任何一条返回篡改包
 *      或 HTML 错误页，用户都会装上去 —— 而强制更新下用户没有「不装」的选项。
 *   2. 用户没有进度感知，下到哪、下完要不要再点，全靠猜。
 * 现在改成应用内下载 → SHA256 比对 GitHub 公布的值 → 对得上才交给安装器。
 */
sealed interface UpdateStage {
    /** 还没开始，或上一次已重置。 */
    object Idle : UpdateStage
    /** 正在并发测速挑线路。 */
    object Probing : UpdateStage
    data class Downloading(val percent: Int, val detail: String) : UpdateStage
    /** 正在算 SHA256。 */
    object Verifying : UpdateStage
    /** 校验通过，已拉起系统安装器。 */
    object Installing : UpdateStage
    data class Failed(val message: String) : UpdateStage

    /** 这个阶段能不能再点「立即更新」。 */
    val canStart: Boolean
        get() = this is Idle || this is Failed
}

/**
 * 强制更新弹窗。
 *
 * 为什么不用 AlertDialog：
 *   AlertDialog 无法关掉系统返回键 —— 虽然 onDismissRequest 是空 lambda，
 *   但用户按返回键仍然能把它关掉，然后继续用旧版本。
 *   这里用原始 Dialog + DialogProperties 两个 false，从窗口层面禁掉
 *   「返回键」与「点外部」两条关闭路径。
 *
 * 为什么用不透明全屏遮罩：
 *   半透明的话用户能看到底下的界面，会以为「其实还能用」。
 *   这里铺满不透明底色，视觉上就是「应用停在这儿了」。
 *
 * 为什么留一个「重新检查」：
 *   下载页打不开、当时没网、或 GitHub 临时抽风时，用户不至于彻底卡死。
 *   它不是「跳过」，只是重试检查 —— 检查通过仍然会停在强制更新界面。
 */
@Composable
fun ForceUpdateDialog(
    version: String,
    releaseNotes: String,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    isChecking: Boolean = false,
    stage: UpdateStage = UpdateStage.Idle,
    selectedLine: String? = null,
) {
    val c = LocalBgColors.current

    // 忙碌态：正在测速/下载/校验/安装时按钮不可点，但文案要说明在做什么 ——
    // 强制更新界面里用户没有别的操作可做，只能盯着这里。
    val busy = stage is UpdateStage.Probing ||
        stage is UpdateStage.Downloading ||
        stage is UpdateStage.Verifying ||
        stage is UpdateStage.Installing

    val buttonLabel = when (stage) {
        is UpdateStage.Idle -> "↓  立即更新"
        is UpdateStage.Probing -> "正在测速选择线路…"
        is UpdateStage.Downloading -> "正在下载 ${stage.percent}%"
        is UpdateStage.Verifying -> "正在校验安装包…"
        is UpdateStage.Installing -> "正在打开安装器…"
        is UpdateStage.Failed -> "↻  重试下载"
    }

    Dialog(
        onDismissRequest = { /* 不允许关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.paper)
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.fillMaxWidth()) {

                // 标记
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "必须更新",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        color = c.accent,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.ink,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "当前 v${BuildConfig.VERSION_NAME}  →  最新 v$version",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                    color = c.ink3,
                )

                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                Spacer(Modifier.height(16.dp))

                // 更新内容（限高可滚）
                Text(
                    text = "更新内容".uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.6.sp,
                    color = c.ink3,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = releaseNotes.ifBlank { "新版本已发布，请更新后继续使用。" },
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = c.ink2,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                )

                Spacer(Modifier.height(24.dp))

                // 立即更新
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .background(if (busy) c.hair else c.accent)
                        .then(
                            if (!busy) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onUpdate,
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = c.ink2,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = buttonLabel,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                letterSpacing = 1.0.sp,
                                color = c.ink2,
                            )
                        }
                    } else {
                        Text(
                            text = buttonLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            letterSpacing = 1.4.sp,
                            color = c.accentInk,
                        )
                    }
                }

                // 下载进度：2dp 细线，和整体设计语言一致（不用 Material 的粗进度条）
                if (stage is UpdateStage.Downloading) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(c.hair)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((stage.percent / 100f).coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(c.accent)
                        )
                    }
                    if (stage.detail.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stage.detail,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = c.ink4,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 重新检查（网络失败时的出口，不是跳过）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isChecking && !busy,
                            onClick = onRetry,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isChecking) "正在重新检查…" else "更新失败？重新检查版本",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.6.sp,
                        color = if (isChecking || busy) c.ink4 else c.ink3,
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (selectedLine != null) {
                    Text(
                        text = "线路：$selectedLine",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.4.sp,
                        color = c.ink4,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (stage is UpdateStage.Failed) {
                    Text(
                        text = stage.message,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = c.accent,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                Text(
                    text = if (selectedLine.isNullOrBlank()) {
                        "此版本包含必要修复，不更新将无法继续使用。"
                    } else {
                        "安装包会校验 SHA256，与官方发布不一致会被丢弃。"
                    },
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = c.ink4,
                )
            }
        }
    }
}

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf("main") }
    var pendingReparseUrl by remember { mutableStateOf<String?>(null) }
    val historyViewModel: HistoryViewModel = viewModel()

    val nonMainScreens = listOf("history", "about")
    BackHandler(enabled = currentScreen in nonMainScreens) {
        currentScreen = "main"
    }

    // 历史页点「重新解析」时会把 url 放进 pendingReparseUrl。
    // 原来是在 `when(screen) { "main" -> { if (...) { downloadFromUrl(...) } } }`
    // 的 Composable 函数体里直接调用 —— 等于在 composition 期间触发副作用
    // 并修改状态。AnimatedContent 切换动画期间会反复重组，这类写法可能
    // 导致重复触发或被跳过。放到 LaunchedEffect 里，每次 url 变化只跑一次。
    LaunchedEffect(pendingReparseUrl) {
        pendingReparseUrl?.let { url ->
            mainViewModel.downloadFromUrl(url)
            pendingReparseUrl = null
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            val duration = 300
            if (targetState == "main") {
                (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration)))
                    .togetherWith(slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
            } else {
                (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                    .togetherWith(slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            "main" -> {
                MainScreen(
                    viewModel = mainViewModel,
                    onNavigateToHistory = { currentScreen = "history" },
                    onNavigateToAbout = { currentScreen = "about" }
                )
            }
            "history" -> HistoryScreen(
                viewModel = historyViewModel,
                onBack = { currentScreen = "main" },
                onReparse = { url ->
                    pendingReparseUrl = url
                    currentScreen = "main"
                }
            )
            "about" -> AboutScreen(onBack = { currentScreen = "main" })
        }
    }
}

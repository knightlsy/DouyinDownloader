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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
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
import com.knightlsy.douyin.ui.theme.DouyinCyan
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

class MainActivity : ComponentActivity() {
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
            var isForceMode by remember { mutableStateOf(false) }
            var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
            var isRechecking by remember { mutableStateOf(false) }
            val updateChecker = remember { UpdateChecker(context) }
            val scope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                val currentVersion = BuildConfig.VERSION_NAME
                val result = updateChecker.checkForUpdate(currentVersion)

                if (result.hasUpdate) {
                    updateResult = result
                    // 强制更新：检测到新版本就挡住整个应用，不更新不能使用。
                    // 只保留 skipped_version 的读取作为历史兼容（不再据此降级为可选）。
                    isForceMode = true
                    showDialog = true
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
                    if (isForceMode) {
                        ForceUpdateDialog(
                            version = result.version,
                            releaseNotes = result.releaseNotes,
                            onUpdate = {
                                updateChecker.startDownload(result.downloadUrl)
                            },
                            isChecking = isRechecking,
                            onRetry = {
                                // 只重新检查，不跳过：检查到更新仍然停在这个界面
                                isRechecking = true
                                scope.launch {
                                    val again = updateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                                    if (again.hasUpdate) {
                                        updateResult = again
                                    }
                                    isRechecking = false
                                }
                            },
                        )
                    } else {
                        OptionalUpdateDialog(
                            version = result.version,
                            releaseNotes = result.releaseNotes,
                            onUpdate = {
                                updateChecker.startDownload(result.downloadUrl)
                            },
                            onLater = {
                                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putString("skipped_version", result.version).apply()
                                showDialog = false
                            }
                        )
                    }
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

@Composable
fun OptionalUpdateDialog(
    version: String,
    releaseNotes: String,
    onUpdate: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        icon = null,
        title = {
            Text("发现新版本 v$version", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                releaseNotes.ifEmpty { "新版本已发布，是否立即更新？" },
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onUpdate,
                colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan)
            ) {
                Text("立即升级", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onLater) {
                Text("下次升级")
            }
        }
    )
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
) {
    val c = LocalBgColors.current

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
                        .background(c.accent)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onUpdate,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "↓  立即更新",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        letterSpacing = 1.4.sp,
                        color = c.accentInk,
                    )
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
                            enabled = !isChecking,
                            onClick = onRetry,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isChecking) "正在重新检查…" else "打不开下载页？重新检查",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.6.sp,
                        color = if (isChecking) c.ink4 else c.ink3,
                    )
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "此版本包含必要修复，不更新将无法继续使用。",
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
                if (pendingReparseUrl != null) {
                    mainViewModel.downloadFromUrl(pendingReparseUrl!!)
                    pendingReparseUrl = null
                }
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

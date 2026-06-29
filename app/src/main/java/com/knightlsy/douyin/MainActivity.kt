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
import com.knightlsy.douyin.data.UpdateChecker
import com.knightlsy.douyin.data.UpdateResult
import com.knightlsy.douyin.screen.AboutScreen
import com.knightlsy.douyin.screen.HistoryScreen
import com.knightlsy.douyin.screen.MainScreen
import com.knightlsy.douyin.ui.theme.DouyinCyan
import com.knightlsy.douyin.ui.theme.DouyinDownloaderTheme
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import com.knightlsy.douyin.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val isDarkMode by mainViewModel.isDarkMode.collectAsState()
            val context = this@MainActivity

            var showDialog by remember { mutableStateOf(false) }
            var isForceMode by remember { mutableStateOf(false) }
            var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
            val updateChecker = remember { UpdateChecker(context) }

            LaunchedEffect(Unit) {
                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                val currentVersion = BuildConfig.VERSION_NAME
                val result = updateChecker.checkForUpdate(currentVersion)

                if (result.hasUpdate) {
                    updateResult = result
                    val skippedVersion = prefs.getString("skipped_version", "")

                    if (skippedVersion == result.version) {
                        isForceMode = true
                        showDialog = true
                    } else {
                        isForceMode = false
                        showDialog = true
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
                    if (isForceMode) {
                        ForceUpdateDialog(
                            version = updateResult!!.version,
                            releaseNotes = updateResult!!.releaseNotes,
                            onUpdate = {
                                updateChecker.startDownload(updateResult!!.downloadUrl)
                            }
                        )
                    } else {
                        OptionalUpdateDialog(
                            version = updateResult!!.version,
                            releaseNotes = updateResult!!.releaseNotes,
                            onUpdate = {
                                updateChecker.startDownload(updateResult!!.downloadUrl)
                            },
                            onLater = {
                                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                                prefs.edit().putString("skipped_version", updateResult!!.version).apply()
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

@Composable
fun ForceUpdateDialog(
    version: String,
    releaseNotes: String,
    onUpdate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        icon = null,
        title = {
            Text("请更新到 v$version", fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                releaseNotes.ifEmpty { "新版本已发布，请更新后继续使用。" },
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
        dismissButton = null
    )
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

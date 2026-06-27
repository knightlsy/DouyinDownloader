package com.knightlsy.douyin

import android.Manifest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.screen.AboutScreen
import com.knightlsy.douyin.screen.HistoryScreen
import com.knightlsy.douyin.screen.MainScreen
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

            DouyinDownloaderTheme(darkTheme = isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(mainViewModel)
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

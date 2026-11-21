package com.knightlsy.douyin.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.ui.screen.AboutScreen
import com.knightlsy.douyin.ui.screen.HistoryScreen
import com.knightlsy.douyin.ui.screen.LogScreen
import com.knightlsy.douyin.ui.screen.MainScreen
import com.knightlsy.douyin.ui.screen.SettingsScreen
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import com.knightlsy.douyin.viewmodel.MainViewModel

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf("main") }
    var pendingReparseUrl by remember { mutableStateOf<String?>(null) }
    val historyViewModel: HistoryViewModel = viewModel()
    val themeMode by mainViewModel.themeMode.collectAsState()

    val nonMainScreens = listOf("history", "about", "settings", "logs")
    BackHandler(enabled = currentScreen in nonMainScreens) {
        // logs 的上级是 settings，其余页面上级都是 main
        currentScreen = if (currentScreen == "logs") "settings" else "main"
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
            val duration = 350
            if (targetState == "main") {
                // 返回主界面：主界面从 92% 放大回全屏、从左边滑回原位；
                // 二级页面缩到 92% 并向右滑出屏幕。
                (scaleIn(initialScale = 0.92f, animationSpec = tween(duration)) +
                    slideInHorizontally(tween(duration)) { -it / 3 } + fadeIn(tween(duration)))
                    .togetherWith(
                        scaleOut(targetScale = 0.92f, animationSpec = tween(duration)) +
                            slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration))
                    )
            } else {
                // 进入二级页：二级页面从右边滑入，92% 放大到 100% 铺满；
                // 主界面缩到 92% 并向左退后一层。
                (scaleIn(initialScale = 0.92f, animationSpec = tween(duration)) +
                    slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                    .togetherWith(
                        scaleOut(targetScale = 0.92f, animationSpec = tween(duration)) +
                            slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(tween(duration))
                    )
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            "main" -> {
                MainScreen(
                    viewModel = mainViewModel,
                    onNavigateToHistory = { currentScreen = "history" },
                    onNavigateToSettings = { currentScreen = "settings" }
                )
            }
            "settings" -> SettingsScreen(
                themeMode = themeMode,
                onCycleTheme = { mainViewModel.toggleDarkMode() },
                onNavigateToLogs = { currentScreen = "logs" },
                onNavigateToAbout = { currentScreen = "about" },
                onBack = { currentScreen = "main" }
            )
            "logs" -> LogScreen(onBack = { currentScreen = "settings" })
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

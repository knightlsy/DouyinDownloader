package com.knightlsy.douyin.navigation

import com.knightlsy.douyin.ui.components.bgHazeGlass
import com.knightlsy.douyin.ui.components.rememberBgHaze
import dev.chrisbanes.haze.hazeSource
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.shadow
import com.knightlsy.douyin.ui.components.bgGlassSurface
import com.knightlsy.douyin.ui.theme.BgRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.ui.screen.AboutScreen
import com.knightlsy.douyin.ui.screen.HistoryScreen
import com.knightlsy.douyin.ui.screen.LogScreen
import com.knightlsy.douyin.ui.screen.MainScreen
import com.knightlsy.douyin.ui.screen.SettingsScreen
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import com.knightlsy.douyin.viewmodel.MainViewModel

/**
 * 底部 Tab 的三个一级路由。关于页（about）与日志页（logs）是设置页的
 * 二级页，不进 Tab，但从设置页进去时「设置」保持选中态。
 */
private val MainTabs = listOf(
    "main" to "首页",
    "history" to "历史",
    "settings" to "设置",
)

/**
 * 当前屏幕对应的 Tab 高亮路由：settings 的两个二级页（logs / about）
 * 也归到「设置」上。
 */
private fun tabRouteOf(screen: String): String =
    if (screen == "main" || screen == "history") screen else "settings"

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf("main") }
    var pendingReparseUrl by remember { mutableStateOf<String?>(null) }
    val historyViewModel: HistoryViewModel = viewModel()
    val themeMode by mainViewModel.themeMode.collectAsState()
    val wallpaperMood by mainViewModel.wallpaperMood.collectAsState()
    val accentFromWallpaper by mainViewModel.accentFromWallpaper.collectAsState()

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

    // Tab 栏放在 AnimatedContent 外面：切换页面时内容走原有的
    // 350ms 缩放 + 横滑动效，Tab 栏本身保持静止（motion-spec「页面级：不动」）。
    // 底部 Tab 要「透」出页面内容，所以页面内容必须注册成 Haze 的采样源。
    // 源和效果是兄弟关系：Tab 栏在源外面，否则它采样的就是它自己。
    val haze = rememberBgHaze()
    Box(Modifier.fillMaxSize()) {
      Box(Modifier.fillMaxSize().hazeSource(haze)) {
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
                "main" -> MainScreen(viewModel = mainViewModel)
                "settings" -> SettingsScreen(
                    themeMode = themeMode,
                    onCycleTheme = { mainViewModel.toggleDarkMode() },
                    onNavigateToLogs = { currentScreen = "logs" },
                    onNavigateToAbout = { currentScreen = "about" },
                    wallpaperMood = wallpaperMood,
                    onWallpaperMoodChange = { mainViewModel.setWallpaperMood(it) },
                    accentFromWallpaper = accentFromWallpaper,
                    onAccentFromWallpaperChange = { mainViewModel.setAccentFromWallpaper(it) },
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

        // 底部 Tab 栏（首页 / 历史 / 设置），叠在内容之上、贴屏幕底部。
        BottomTabBar(
            currentRoute = currentScreen,
            onSelect = { route ->
                if (route != currentScreen) currentScreen = route
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 底部 Tab 栏。
 *
 * 结构上是悬浮玻璃条：rgba(255,255,255,.045) 底 + 1dp 边线 + 26dp 圆角，
 * 高 56dp，通过 navigationBarsPadding 避开手势条。选中的表达只有两处：
 * 文字提亮加粗 + 下方 2dp 橙色指示条 —— 不整片染色（motion-spec ③）。
 *
 * 指示条随选中项横向滑动（240ms · cubic-bezier(.34,1.45,.64,1)，
 * 过冲中等），文字 200ms 变色、字重 500→650。宽度按三等分推算，
 * 不依赖每个 Tab 的实测宽度，切换时不会跳变。
 */
@Composable
private fun BottomTabBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    val selectedRoute = tabRouteOf(currentRoute)

    Box(
        modifier
            .navigationBarsPadding()
            .padding(start = BgSpace.gutter, end = BgSpace.gutter, bottom = 12.dp)
            .fillMaxWidth()
            .height(56.dp)
            // 外阴影先于 clip：clip=false 让它画在形状外面，浮起来才有「离开页面」的重量。
            .shadow(14.dp, RoundedCornerShape(BgRadius.r4), clip = false)
            // 真玻璃：采样页面内容，模糊后透出来
            .bgHazeGlass(haze, BgRadius.r4, blurRadius = 22.dp)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabWidth = maxWidth / 3
            val selectedIndex = MainTabs
                .indexOfFirst { it.first == selectedRoute }
                .coerceAtLeast(0)
            // 指示条：26dp 宽、2dp 高，圆角取半高成胶囊。外发光需要 blur，
            // Modifier.blur 是 API 31+ 专属（minSdk 26），此处省略。
            val indicatorX by animateDpAsState(
                targetValue = tabWidth * selectedIndex + (tabWidth - 26.dp) / 2,
                animationSpec = tween(240, easing = CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f)),
                label = "tab_indicator_x",
            )

            Row(Modifier.fillMaxSize()) {
                MainTabs.forEach { (route, label) ->
                    TabItem(
                        label = label,
                        selected = route == selectedRoute,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(route) },
                    )
                }
            }

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = indicatorX)
                    .padding(bottom = 9.dp)
                    .size(26.dp, 2.dp)
                    .clip(CircleShape)
                    .background(c.accent)
            )
        }
    }
}

/**
 * 单个 Tab。整格可点（44dp+ 触达区），无 ripple，靠文字色/字重表达选中。
 */
@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val c = LocalBgColors.current
    val textColor by animateColorAsState(
        targetValue = if (selected) c.ink else c.ink.copy(alpha = 0.46f),
        animationSpec = tween(200),
        label = "tab_text_color",
    )

    Box(
        modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight(650) else FontWeight.Medium,
            letterSpacing = 0.8.sp,
            color = textColor,
        )
    }
}

package com.knightlsy.douyin.navigation

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.knightlsy.douyin.ui.theme.BgRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha

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
    val wallpaperDiag by mainViewModel.wallpaperDiag.collectAsState()
    val wallpaperTick by mainViewModel.wallpaperTick.collectAsState()
    val hasCustomBackdrop by mainViewModel.hasCustomBackdrop.collectAsState()

    val nonMainScreens = listOf("history", "about", "settings", "logs")
    BackHandler(enabled = currentScreen in nonMainScreens) {
        // logs 的上级是 settings，其余页面上级都是 main
        // about 是从设置页进的，上级也应该是设置页 —— 原来直接跳回首页，层级断层
        currentScreen = if (currentScreen == "logs" || currentScreen == "about") {
            "settings"
        } else {
            "main"
        }
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

    /*
     * 布局：Tab 栏固定在顶部，内容区在它下面单独做转场。
     *
     * 上一版把 Tab 搬进 MainScreen 内部，结果它成了 AnimatedContent 的一部分 ——
     * 点 Tab 时整个首页（连 Tab 一起）缩到 92% 滑走，观感是「进入二级菜单」。
     * 用户要的是「卡片在 Tab 下面左右线性切换」，Tab 本身必须静止。
     *
     * 状态栏内边距在这里统一处理，各页面自己的 systemBarsPadding 已移除，
     * 否则会算两次。
     */
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // 标题区。位置和高度在任何页面都不变，只有透明度跟着页面走 ——
        // 这样 Tab 栏永远贴在标题正下方，切页时不会跳。
        HomeHeader(visible = currentScreen == "main")

        TabBar(
            currentRoute = currentScreen,
            onSelect = { route -> if (route != currentScreen) currentScreen = route },
            // 标题卡自身 bottom 有 8dp，这里再加 4dp，两者合计 12dp。
            // 原来只有 8dp，标题和 Tab 粘在一起，分组关系看不出来。
            modifier = Modifier.padding(top = 4.dp),
        )
        Box(Modifier.weight(1f)) {
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
                "main" -> MainScreen(
                    viewModel = mainViewModel,
                    onSelectTab = { route -> if (route != currentScreen) currentScreen = route },
                )
                "settings" -> SettingsScreen(
                    themeMode = themeMode,
                    onCycleTheme = { mainViewModel.toggleDarkMode() },
                    onNavigateToLogs = { currentScreen = "logs" },
                    onNavigateToAbout = { currentScreen = "about" },
                    wallpaperMood = wallpaperMood,
                    onWallpaperMoodChange = { mainViewModel.setWallpaperMood(it) },
                    accentFromWallpaper = accentFromWallpaper,
                    onAccentFromWallpaperChange = { mainViewModel.setAccentFromWallpaper(it) },
                    wallpaperDiag = wallpaperDiag,
                    hasCustomBackdrop = hasCustomBackdrop,
                    onPickBackdrop = { uri -> mainViewModel.applyCustomBackdrop(uri) },
                    onClearBackdrop = { mainViewModel.clearCustomBackdrop() },
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
                "about" -> AboutScreen(onBack = { currentScreen = "settings" })
            }
        }
      }
    }
}


/**
 * 首页标题栏（「抖音下载器 / 粘贴链接即可下载」）。
 *
 * 提到这一层是为了结构稳定：用户要的是固定的「标题 > Tab > 内容」三段。
 * 如果标题留在 MainScreen 内部，它要么被 Tab 顶掉，要么跟着内容一起滑走。
 *
 * 用 alpha 淡出而不是 AnimatedVisibility：后者会把自己从布局里摘掉，
 * 高度瞬间塌陷，Tab 栏就往上跳一截。这里必须始终占住同样高度。
 */
@Composable
private fun HomeHeader(visible: Boolean) {
    val c = LocalBgColors.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 220 else 140),
        label = "home_header_alpha",
    )

    /*
     * 刻意不加背景和边框。
     *
     * 原来的顶栏是 bgHazeGlass(reveal = barReveal)：停在顶部时 reveal = 0，
     * 完全透明，只有列表滚上去才浮现玻璃 —— 标题就是浮在背景上的一行字。
     * 搬到这里之后我给它套了固定底色 + 1dp 描边，凭空多出一个框，
     * 把标题和下面的内容切成两块，整页读起来是断的。
     *
     * 这里恢复成无框。左边 16dp 是原来玻璃条的内边距，保留是为了让标题文字
     * 和下面卡片里的文字左边缘对得上；去掉它就只剩 gutter，会比卡片内容更靠左。
     */
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = BgSpace.gutter + 16.dp,
                end = BgSpace.gutter + 16.dp,
                // 无框之后顶部只留 12dp 会贴着状态栏。原来是 10dp 外边距
                // 加玻璃条内 12dp 内边距，共 22dp —— 这里取 18dp 折中。
                top = 18.dp,
                bottom = 8.dp,
            )
            .alpha(alpha),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            // 系统无衬线 + 粗字重 + 负字距。等宽只适合数字和代码，
            // 中文笔画密度高，等宽加字距会把标题撑散。
            Text(
                text = "抖音下载器",
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = c.ink,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = "粘贴链接即可下载",
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                color = c.ink3,
            )
        }
    }
}

/**
 * 一级导航 Tab 栏（首页 / 历史 / 设置），固定在内容区顶部。
 *
 * 位置是页面顶部而不是底部：底部悬浮时会压住设置页最后一屏文字。
 * 关键性质是**它不参与页面转场** —— 内容在它下面缩放滑动，它自己静止，
 * 这才是「卡片在 Tab 下方切换」的观感。放进 AnimatedContent 里就会变成
 * 「进入二级菜单」那种整页被替换的效果。
 *
 * 结构：半透明卡片 + 1dp 边线 + 26dp 圆角，高 56dp。选中的表达只有两处：
 * 文字提亮加粗 + 下方 2dp 橙色指示条 —— 不整片染色（motion-spec ③）。
 *
 * 指示条随选中项横向滑动（240ms · cubic-bezier(.34,1.45,.64,1)，
 * 过冲中等），文字 200ms 变色、字重 500→650。宽度按三等分推算，
 * 不依赖每个 Tab 的实测宽度，切换时不会跳变。
 */
@Composable
internal fun TabBar(
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    val selectedRoute = tabRouteOf(currentRoute)

    Box(
        modifier
            // 左右留和标题卡、输入框、步骤卡一样的 gutter（20dp）。
            // 少了这一层，Tab 会横跨整屏、边距只剩几像素，和上面下面所有卡片的
            // 左边缘对不齐 —— 一眼就能看出来的栅格破坏。
            .padding(start = BgSpace.gutter, end = BgSpace.gutter)
            .fillMaxWidth()
            .height(56.dp)
            // 不再采 Haze：Tab 栏现在固定在内容动画之外，背后是各页面的标题栏
            // 而不是滚动的列表，采样意义不大；半透明 + 细边就够，也省掉一层模糊开销。
            // 注意去掉 shadow：clip=false 的阴影边缘处最深，浅色底上看着就是一圈灰线
            // （用户说的「小白条」是它，不是 border）。
            .clip(RoundedCornerShape(BgRadius.r4))
            .background(c.glass)
            .border(1.dp, c.glassEdge, RoundedCornerShape(BgRadius.r4))
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

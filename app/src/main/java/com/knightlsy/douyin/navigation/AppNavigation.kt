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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width

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

/**
 * 页面左右顺序，用来决定切换动画方向。
 *
 * 序号变大 = 往右走（新页从右滑入），变小 = 往左走（新页从左滑入）。
 * 一级页按 Tab 从左到右：main(0) → history(1) → settings(2)；
 * logs / about 是设置页的子页，排在同一层（3），只跟 settings 互相切换。
 */
private fun screenOrder(screen: String): Int = when (screen) {
    "main" -> 0
    "history" -> 1
    "settings" -> 2
    "logs" -> 3
    "about" -> 3
    else -> 0
}

@Composable
fun AppNavigation(mainViewModel: MainViewModel) {
    // rememberSaveable 而非 remember：转屏、系统深浅色切换、字号缩放、切主题
    // 都会重建 Activity，用 remember 的话一律被扔回首页，正在填的输入和
    // 二级页位置全丢（用户看到的现象是「页面自己跳回首页」）。
    var currentScreen by rememberSaveable { mutableStateOf("main") }
    var pendingReparseUrl by rememberSaveable { mutableStateOf<String?>(null) }
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
    // 返回层级：logs/about 的上级是 settings，其余回首页。
    // 与下面 BackHandler 的规则保持一致，避免标题栏的返回键和系统返回键去不同地方。
    val goBack: () -> Unit = {
        currentScreen = when (currentScreen) {
            "logs", "about" -> "settings"
            else -> "main"
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // 标题区：所有页面共用，位置和高度恒定，Tab 栏永远贴在它正下方。
        AppHeader(screen = currentScreen, onBack = goBack)

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
                // 方向跟随点击方向：目标页序号比当前页大 = 往右走（新页从右滑入），
                // 比当前页小 = 往左走（新页从左滑入）。
                //
                // 原来是 if (targetState == "main") 两分支 —— 只区分「是不是主界面」，
                // 所以 history ↔ settings 这种同层切换永远走同一侧，从设置点回历史
                // 时画面还是从右边进来，方向是反的。
                val forward = screenOrder(targetState) >= screenOrder(initialState)
                if (forward) {
                    // 前进：新页从右侧整屏滑入并把前一层顶回去（92% → 100%）
                    (scaleIn(initialScale = 0.92f, animationSpec = tween(duration)) +
                        slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                        .togetherWith(
                            scaleOut(targetScale = 0.92f, animationSpec = tween(duration)) +
                                slideOutHorizontally(tween(duration)) { -it / 3 } + fadeOut(tween(duration))
                        )
                } else {
                    // 返回：新页从左侧回位（只走 1/3，配合放大回全屏的观感），
                    // 前一层整屏向右滑出
                    (scaleIn(initialScale = 0.92f, animationSpec = tween(duration)) +
                        slideInHorizontally(tween(duration)) { -it / 3 } + fadeIn(tween(duration)))
                        .togetherWith(
                            scaleOut(targetScale = 0.92f, animationSpec = tween(duration)) +
                                slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration))
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
                )
                "logs" -> LogScreen()
                "history" -> HistoryScreen(
                    viewModel = historyViewModel,
                    onReparse = { url ->
                        pendingReparseUrl = url
                        currentScreen = "main"
                    }
                )
                "about" -> AboutScreen()
            }
        }
      }
    }
}


/**
 * 统一页面标题栏。所有一级/二级页面共用这一份，位置在 Tab 栏上方。
 *
 * ## 为什么提到这一层
 * 原来每个页面各写一套顶栏，风格还不一样（设置/日志是小圆点 + 等宽小字，
 * 历史/关于是返回圆钮 + 大标题），首页则是大标题 + 副标题。
 * 摆在一起就是：首页标题在 Tab 上面，其他页标题在 Tab 下面 ——
 * 同一个 App 里出现两种头部层级。
 *
 * ## 为什么固定「标题 + 副标题」两行
 * 高度必须恒定，否则切页时 Tab 栏会跟着上下跳。首页本来就是两行，
 * 其他页也补一行副标题，五个页面高度一致。
 *
 * ## 无框
 * 不加背景和描边。原来的顶栏是 bgHazeGlass(reveal = barReveal)，
 * 停在顶部时完全透明，只有滚动才浮现玻璃 —— 它本来就是浮在背景上的一行字。
 */
@Composable
private fun AppHeader(screen: String, onBack: () -> Unit) {
    val c = LocalBgColors.current
    // 返回 0 表示没有上一级（首页）
    val hasBack = screen != "main"

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(
                start = BgSpace.gutter + if (hasBack) 2.dp else 16.dp,
                end = BgSpace.gutter + 16.dp,
                // 无框之后顶部留 18dp 才不贴着状态栏（原来是外边距 10 + 玻璃条内 12）
                top = 18.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasBack) {
            // 38dp 触摸区，视觉是个 ← 字符
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "←", fontSize = 17.sp, color = c.ink2)
            }
            Spacer(Modifier.width(4.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = headerTitle(screen),
                fontFamily = FontFamily.SansSerif,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                color = c.ink,
                maxLines = 1,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = headerSubtitle(screen),
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
                color = c.ink3,
                maxLines = 1,
            )
        }
    }
}

private fun headerTitle(screen: String): String = when (screen) {
    "main" -> "抖音下载器"
    "history" -> "下载历史"
    "settings" -> "设置"
    "logs" -> "日志"
    "about" -> "关于"
    else -> ""
}

private fun headerSubtitle(screen: String): String = when (screen) {
    "main" -> "粘贴链接即可下载"
    "history" -> "下载过的视频和图集"
    "settings" -> "外观、背景与增强模块"
    "logs" -> "运行过程中的诊断信息"
    "about" -> "版本与说明"
    else -> ""
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
/**
 * Tab 切换的统一动画参数。
 *
 * 原来指示条用的是 CubicBezierEasing(0.34f, 1.45f, 0.64f, 1f) —— 第二个控制点为
 * 1.45，y 值超过 1，属于**过冲曲线**：指示条会先冲过目标位置再弹回来。
 * 短距离移动时这个回弹看着就是「顿一下、抖一下」；再叠上文字用 200ms
 * 无回弹曲线变色、指示条用 240ms 带回弹，两个机构各走各的节奏，
 * 视觉上就分成两截 —— 也就是反馈的「割裂感」。
 *
 * 现在统一成 Material 3 的 emphasized-decelerate（0.2, 0, 0, 1）：
 * 起步快、收尾缓、全程单调递增不过冲，指示条和文字同一个时长同一条曲线。
 */
private const val TabAnimMs = 260

private val TabAnimEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

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
                animationSpec = tween(TabAnimMs, easing = TabAnimEasing),
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
        // 和指示条用同一条曲线、同一个时长：两者是同一个动作的两个表现，
        // 节奏不一致时眼睛会看到「指示条先滑、文字后变色」的分裂感。
        animationSpec = tween(TabAnimMs, easing = TabAnimEasing),
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
            // 字重固定，不随选中变化。
            //
            // 原来是 selected 用 650、未选中用 Medium(500) —— 字重一变，两个字的
            // 排版宽度就跟着变，切换那一刻文字会「胀一下 / 缩一下」，和指示条
            // 的位移叠在一起就是第三种割裂感。选中状态已经由指示条 + 颜色
            // （ink 与 46% 透明）表达得够清楚了，不需要再动字重。
            fontWeight = FontWeight(600),
            letterSpacing = 0.8.sp,
            color = textColor,
        )
    }
}

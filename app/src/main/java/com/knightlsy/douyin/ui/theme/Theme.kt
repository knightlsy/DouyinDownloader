package com.knightlsy.douyin.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * 主题。
 *
 * 保留原有两个机制：
 *  1. 三态切换（跟随系统 / 浅色 / 深色）由 MainViewModel 的 ThemeMode 驱动。
 *  2. 切主题时不硬切，而是对每个颜色槽位跑 animateColorAsState 补间（420ms）。
 *     硬切会让整个界面在一帧内突变，感知为「生硬」；补间之后像灯光渐暗。
 *
 * 移除动态取色：跟壁纸取色会摧毁粉青这套固定身份，也让「每屏 ≤4 处强调色」
 * 这条纪律无从谈起。
 */

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = SecondaryDark,
    onTertiary = OnSecondaryDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerLow = BackgroundDark,
    surfaceContainerLowest = BackgroundDark,
    surfaceContainerHigh = SurfaceVariantDark,
    surfaceContainerHighest = SurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineDark,
    error = StatusFailed,
    onError = Color(0xFF2A0407),
    errorContainer = Color(0xFF3A0A0E),
    onErrorContainer = Color(0xFFFFD9DC),
    inverseSurface = OnBackgroundDark,
    inverseOnSurface = BackgroundDark,
    inversePrimary = DouyinPinkDeep,
    scrim = Color(0xFF000000),
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = SecondaryLight,
    onTertiary = OnSecondaryLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainer = SurfaceLight,
    surfaceContainerLow = BackgroundLight,
    surfaceContainerLowest = SurfaceLight,
    surfaceContainerHigh = SurfaceVariantLight,
    surfaceContainerHighest = SurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineLight,
    error = StatusFailed,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDADC),
    onErrorContainer = Color(0xFF3F0006),
    inverseSurface = OnBackgroundLight,
    inverseOnSurface = BackgroundLight,
    inversePrimary = DouyinPink,
    scrim = Color(0xFF000000),
)

/** 主题过渡时长（毫秒）。太短看不出过渡，太长显得拖沓。 */
private const val THEME_TRANSITION_MS = 420

/**
 * 在两套配色之间做逐色插值。
 */
@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val spec = tween<Color>(durationMillis = THEME_TRANSITION_MS)

    @Composable
    fun anim(c: Color): Color =
        animateColorAsState(targetValue = c, animationSpec = spec, label = "themeColor").value

    return target.copy(
        primary = anim(target.primary),
        onPrimary = anim(target.onPrimary),
        primaryContainer = anim(target.primaryContainer),
        onPrimaryContainer = anim(target.onPrimaryContainer),
        secondary = anim(target.secondary),
        onSecondary = anim(target.onSecondary),
        secondaryContainer = anim(target.secondaryContainer),
        onSecondaryContainer = anim(target.onSecondaryContainer),
        background = anim(target.background),
        onBackground = anim(target.onBackground),
        surface = anim(target.surface),
        onSurface = anim(target.onSurface),
        surfaceVariant = anim(target.surfaceVariant),
        onSurfaceVariant = anim(target.onSurfaceVariant),
        surfaceContainer = anim(target.surfaceContainer),
        surfaceContainerHigh = anim(target.surfaceContainerHigh),
        surfaceContainerHighest = anim(target.surfaceContainerHighest),
        outline = anim(target.outline),
        outlineVariant = anim(target.outlineVariant),
        error = anim(target.error),
        onError = anim(target.onError),
        errorContainer = anim(target.errorContainer),
        onErrorContainer = anim(target.onErrorContainer),
    )
}

/* ══════════ 设计 token 的运行时快照 ══════════ */

/**
 * Material3 的 colorScheme 没有「细线色」这种自定义槽位。
 * 新写的界面需要三级文字色、细线色时从这里取，自动跟随明暗主题，
 * 不必在每个调用点写 `if (isDark) ... else ...`。
 *
 * 旧代码仍可直接引用 [DouyinPink] 等常量 —— 两条路并存，迁移期不必一次改完。
 */
@Immutable
data class BgColors(
    val ink: Color,       // 主文字
    val ink2: Color,      // 次要文字
    val ink3: Color,      // 三级文字 / 标签
    val ink4: Color,      // 四级文字：禁用态 / 极弱提示
    val paper: Color,     // 页面底色
    val surface: Color,   // 卡片底
    val raise: Color,     // 次级块（输入框、错误带）
    val hair: Color,      // 全部细线
    val accent: Color,    // 粉：主行动 + 失败
    val accentInk: Color, // 压在粉底上的文字色
    val success: Color,   // 青：成功 + 次要标记
)

private val LightTokens = BgColors(
    ink = OnBackgroundLight,
    ink2 = OnSurfaceVariantLight,
    ink3 = Color(0xFF8C8B92),
    ink4 = Color(0xFFB0AFB6),
    paper = BackgroundLight,
    surface = SurfaceLight,
    raise = SurfaceVariantLight,
    hair = OutlineLight,
    accent = DouyinPinkDeep,
    accentInk = Color(0xFFFFFFFF),
    success = DouyinCyanDeep,
)

private val DarkTokens = BgColors(
    ink = OnBackgroundDark,
    ink2 = OnSurfaceVariantDark,
    ink3 = Color(0xFF58575F),
    ink4 = Color(0xFF3D3C43),
    paper = BackgroundDark,
    surface = SurfaceDark,
    raise = SurfaceVariantDark,
    hair = OutlineDark,
    accent = DouyinPink,
    accentInk = Color(0xFF1A0410),
    success = DouyinCyan,
)

val LocalBgColors = staticCompositionLocalOf { LightTokens }

/**
 * 局部抑制 DEPRECATION 的原因：
 *
 * `window.statusBarColor` / `navigationBarColor` 在 API 35 起被废弃，因为系统强制
 * edge-to-edge、状态栏一律透明。但本应用 `targetSdk = 34`，在 API 26–34 上
 * **只有显式设成 Transparent 才不会出现黑条** —— 删掉这两行不是「消除废弃」，
 * 而是「制造黑边」。
 *
 * 等 targetSdk 提到 35 时，这两行连同这个注解一起删掉即可。
 */
@Suppress("DEPRECATION")
@Composable
fun DouyinDownloaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val target = if (darkTheme) DarkColorScheme else LightColorScheme

    // 关键：不在两套方案之间硬切，而是对颜色做补间，消除切换时的生硬感。
    val colorScheme = animateColorScheme(target)
    val tokens = if (darkTheme) DarkTokens else LightTokens

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalBgColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

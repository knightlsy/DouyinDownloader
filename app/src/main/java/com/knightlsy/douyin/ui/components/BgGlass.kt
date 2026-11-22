package com.knightlsy.douyin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 液态玻璃。
 *
 * ## 这里没有用 backdrop blur，是有意的
 *
 * 真正的 `backdrop-filter` 在 Android 上要引入 Haze 这类库（+1~2MB），
 * 且 API 26–30 照样降级成半透明。更要紧的是：**玻璃能不能「透」，
 * 取决于背后有没有东西可透，而不取决于模糊本身。**
 *
 * 所以这里的做法是配套的：
 *  - 背景层（[BgBackdrop]）铺大范围径向柔光斑，本身就是无边界渐变
 *  - 玻璃层半透明浮上去，透过去的就是那层柔光
 *
 * 光斑没有硬边，透过去天然是柔的，视觉上与真模糊等价，
 * 而且全版本一致、零额外开销。
 *
 * ## 三层构成，缺一层就不成立
 * ```
 * 1. 半透明底   alpha 0.04~0.10 —— 高了立刻变灰膜
 * 2. 1dp 亮边   模拟玻璃边缘反光
 * 3. 顶部内高光  光从上方来，玻璃上沿更亮
 * ```
 * 再叠一层外阴影，浮起来。
 */

/** 玻璃卡片。带 padding 的 Column。 */
@Composable
fun BgGlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = BgRadius.r3,
    padding: Dp = 16.dp,
    contentPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalBgColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.glass)
            .drawBehind { drawGlassTopHighlight(c.glassTop, isDark = c.paper.luminance() < 0.5f) }
            .border(1.dp, c.glassEdge, RoundedCornerShape(radius))
            .then(if (contentPadding) Modifier.padding(padding) else Modifier)
    ) {
        content()
    }
}

/** 玻璃容器。不带 padding，给需要自己控制布局的场景。 */
@Composable
fun BgGlassBox(
    modifier: Modifier = Modifier,
    radius: Dp = BgRadius.r3,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBgColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(radius))
            .background(c.glass)
            .drawBehind { drawGlassTopHighlight(c.glassTop, isDark = c.paper.luminance() < 0.5f) }
            .border(1.dp, c.glassEdge, RoundedCornerShape(radius))
    ) {
        content()
    }
}

/**
 * 直接给任意 modifier 加玻璃底。用于不方便套 BgGlassCard 的地方
 * （比如 LazyColumn 的 item 已经自带背景的情况）。
 *
 * 注意顺序：调用方要保证自己的 clip 在此之后，否则圆角会被覆盖。
 */
@Composable
fun Modifier.bgGlassSurface(radius: Dp = BgRadius.r3): Modifier {
    val c = LocalBgColors.current
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(c.glass)
        .drawBehind { drawGlassTopHighlight(c.glassTop, isDark = c.paper.luminance() < 0.5f) }
        .border(1.dp, c.glassEdge, shape)
}

/**
 * 顶部内高光：从上沿往下 22dp 的极短渐变。
 *
 * 不用整块渐变 —— 那会变成「上半亮下半暗」的立体感，像拟物按钮。
 * 真正的玻璃只有边缘那一圈反光。
 */
/**
 * 玻璃上沿的水滴反光。
 *
 * ## 为什么不是「一条白线」
 * 光打在弧面上，反射的是一条**中间最亮、两端渐隐**的细线 ——
 * 就像水珠边缘那道高光。画成通栏等宽的白线，看起来是「贴了条胶带」，
 * 而且深色底上 20% 白对比极强，会突兀地横在滑块上方。
 *
 * ## 为什么深浅两套参数
 * 同一个白色，在 #14110F 上和 #F2EFEB 上的视觉重量完全不同：
 * 深色底会把白色衬得刺眼，浅色底反而需要多一点才能看见边界。
 * 所以深色压到 14%，浅色给到 26%。
 *
 * @param radius 容器圆角，用于让高光跟随形状（这里只取上沿，故不用）
 * @param tint 高光色（通常是主题的 glassTop）
 * @param isDark 当前是否为深色主题
 */
private fun DrawScope.drawGlassTopHighlight(tint: Color, isDark: Boolean) {
    // 深色底白色对比强 → 更淡；浅色底需要多一点才看得见
    val a = if (isDark) 0.14f else 0.26f
    val lineH = 1.5.dp.toPx().coerceAtMost(size.height * 0.5f)
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                tint.copy(alpha = a),
                tint.copy(alpha = a),
                Color.Transparent,
            ),
            startX = 0f,
            endX = size.width,
        ),
        topLeft = Offset.Zero,
        size = Size(size.width, lineH),
    )
}

/**
 * 页面背景：纯色打底 + 两团柔光。
 *
 * 光斑位置固定在左上与右下，与设计稿一致 —— 上方暖橙、下方深琥珀。
 * 它们不是装饰，是玻璃「透」的对象：没有这层，玻璃就是一块灰膜。
 */
@Composable
fun BgBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBgColors.current
    Box(modifier.fillMaxSize().background(c.paper)) {
        val accent = c.accent
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // 左上暖光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.10f),
                    radius = size.minDimension * 0.85f,
                ),
                radius = size.minDimension * 0.85f,
                center = Offset(size.width * 0.18f, size.height * 0.10f),
            )
            // 右下琥珀光
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.92f, size.height * 0.72f),
                    radius = size.minDimension * 0.75f,
                ),
                radius = size.minDimension * 0.75f,
                center = Offset(size.width * 0.92f, size.height * 0.72f),
            )
        }
        content()
    }
}

/* ═══════════════════════════════════════════════════════════
   真 · 液态玻璃（Haze）
   ═══════════════════════════════════════════════════════════

   上面 [BgGlassCard] 那套是「固定色 + 高光」，做不出「透」——
   不管 alpha 调多少，看到的都是预设的颜色，和背后内容无关。

   真正的玻璃必须**采样背后的像素**：把那块区域的渲染结果离屏取出 →
   模糊 → 叠色调 → 画进玻璃形状。Compose 自带的 Modifier.blur() 只能模糊
   自己（拿不到身后），RenderEffect 同理，所以只能靠 Haze。

   用法（源和效果必须是兄弟节点，玻璃不能放在源内部，否则采样到自己）：

       val haze = rememberBgHaze()
       Box {
           Column(Modifier.hazeSource(haze)) { 页面内容 }
           TopBar(Modifier.bgHazeGlass(haze, BgRadius.r4))
       }
   */

/** 全局共享的 Haze 源。源头在 AppNavigation 建一次，各层玻璃共用。 */
val LocalBgHaze = staticCompositionLocalOf<HazeState?> { null }

@Composable
fun rememberBgHaze(): HazeState = remember { HazeState() }

/**
 * 液态玻璃修饰符。
 *
 * @param blurRadius 模糊半径。iOS 顶栏大约 20–30dp；再高内容就糊成一片色块了。
 * @param tint 叠色。玻璃不是纯透明的，有一层极薄的色 —— 这是「玻璃有厚度」的来源。
 * @param backgroundAlpha 底色不透明度。这是「透多少」的主控：
 *        0.0 = 完全透明只看到模糊；0.6+ = 变回半透明色块。iOS 落在 0.25–0.45。
 */
@Composable
fun Modifier.bgHazeGlass(
    haze: HazeState,
    radius: Dp = BgRadius.r3,
    blurRadius: Dp = 24.dp,
    tint: Color? = null,
    backgroundAlpha: Float = 0.26f,
    /**
     * 玻璃的显现程度。0 = 完全隐藏（只剩浮在页面上的内容），1 = 完整玻璃。
     *
     * 这个参数是给「滚动才出现玻璃」用的。原因是物理性的：
     * 页面停在顶部时，顶栏背后根本没有内容可采样 —— Haze 采到的是纯色 paper，
     * 模糊纯色还是纯色，于是整个顶栏就是一块与背景有微小色差的矩形。
     * 那不是玻璃，是色块。iOS 导航栏的做法就是停在顶部时完全透明、滚动后才渐显。
     */
    reveal: Float = 1f,
): Modifier {
    val c = LocalBgColors.current
    val shape = RoundedCornerShape(radius)
    val tintColor = tint ?: c.glassTop
    val r = reveal.coerceIn(0f, 1f)
    return this
        .clip(shape)
        .hazeEffect(haze) {
            alpha = r
            style = HazeStyle(
                // 底色压得很低 —— 高了这个效果立刻退化成一块灰膜。
                // 0.26 是「还能看清文字」和「透出内容」的平衡点。
                backgroundColor = c.paper.copy(alpha = backgroundAlpha),
                // 薄薄一层色，给玻璃厚度。固定 6% 白而不是纯白 ——
                // 玻璃的材质感来自采样到的内容，不是靠盖一层白。
                tints = listOf(HazeTint(tintColor.copy(alpha = 0.06f))),
                blurRadius = blurRadius,
                // 极细颗粒，让模糊不至于像塑料。iOS 毛玻璃有这个
                noiseFactor = 0.018f,
            )
        }
        .drawBehind {
            // 顶部内高光：光从上方来，上沿更亮
            if (r > 0.01f) {
                drawGlassTopHighlight(
                    c.glassTop.copy(alpha = c.glassTop.alpha * r),
                    isDark = c.paper.luminance() < 0.5f,
                )
            }
        }
        .border(1.dp, c.glassEdge.copy(alpha = c.glassEdge.alpha * r), shape)
}

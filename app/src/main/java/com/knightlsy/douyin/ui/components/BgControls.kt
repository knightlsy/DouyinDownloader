package com.knightlsy.douyin.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.theme.BgMotion
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/* ═══════════════════════════════════════════════════════
   细线
   ═══════════════════════════════════════════════════════ */

/** 1dp 细线。整个应用的分隔只用它，不用卡片阴影与浮起。 */
@Composable
fun BgHair(
    modifier: Modifier = Modifier,
    color: Color = LocalBgColors.current.hair,
) {
    Box(modifier.fillMaxWidth().height(BgSpace.hair).background(color))
}

/* ═══════════════════════════════════════════════════════
   细线进度条
   ═══════════════════════════════════════════════════════ */

/**
 * 2dp 细线进度条。
 *
 * 取代旧版的圆环进度：圆环直径通常 48–54dp，在任务卡里要占掉整整一行；
 * 一根细线横着走只占 2dp，同样的信息量，省一半高度，而且一眼能看出还剩多少。
 *
 * 进度用 animateFloatAsState 补间，下载中频繁更新时不会一跳一跳。
 *
 * @param progress 0f..1f
 * @param indeterminate true 时显示一条来回扫动的线（用于解析中这种无进度场景）
 */
@Composable
fun BgProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = LocalBgColors.current.accent,
    trackColor: Color = LocalBgColors.current.hair,
    height: Dp = 2.dp,
) {
    // spring 而不是 tween：下载进度的回调不均匀（一次几十 KB、一次几 MB），
    // 固定时长的线性补间在这种频率下会显成一跳一跳；spring 自带速度延续，
    // 密集更新时平滑接上，稀疏更新时轻微过冲再回落（超出部分被外层 clip 吃掉）
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = BgMotion.progressSpring(),
        label = "bgProgress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(1.dp))
            .background(trackColor)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction = animated)
                .height(height)
                .background(color)
        )
    }
}

/* ═══════════════════════════════════════════════════════
   方角勾选
   ═══════════════════════════════════════════════════════ */

/**
 * 方角勾选框（选中时方→圆）。
 *
 * Material 的 Checkbox 是 18dp 圆角 + 40dp 触摸区。这里视觉 17dp 方角，
 * 但外层用 44dp 的透明触摸区包住 —— 视觉不变，可点面积达标。
 *
 * 动效（规范 ⑤）：圆角 9dp→50%（方形变圆）、背景 透明→强调绿、
 * 勾号 scale(0)→scale(1) 用最强过冲（dampingRatio 0.50）——
 * 面积小，弹得不够明显等于没有动效。
 *
 * @param overlay true 表示叠在图片上（需要半透明白边，否则深色图上会消失）
 */
@Composable
fun BgCheckBox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
    enabled: Boolean = true,
    onToggle: () -> Unit,
) {
    val c = LocalBgColors.current
    // 圆角：规范写「9dp → 50%」。17dp 的框上 9dp 圆角正好被钳到一半尺寸
    // （即 50% 圆），所以选中目标取 9dp、非选中保持现行方角 r1，视觉即「方形变圆」
    val corner by animateDpAsState(
        targetValue = if (checked) BgMotion.CHECK_CORNER_ROUND else BgRadius.r1,
        animationSpec = BgMotion.checkSpring(),
        label = "bgCheckCorner",
    )
    // 勾号 scale(0)→scale(1)，强过冲。remember 捕获初值：首帧已选中的不播「弹出」
    val checkScale = remember { Animatable(if (checked) 1f else 0f) }
    LaunchedEffect(checked) {
        checkScale.animateTo(if (checked) 1f else 0f, BgMotion.checkSpring())
    }
    // 颜色补间走 CHECK_MS，和形变同步起落，避免「形状到了颜色还没到」的生硬切换。
    // 选中底色按色彩语义用强调绿（选中），不再借用主 CTA 的橙 —— 一色一义
    val fillColor by animateColorAsState(
        targetValue = when {
            checked -> c.success
            overlay -> Color.Black.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        animationSpec = tween(BgMotion.CHECK_MS),
        label = "bgCheckFill",
    )
    val edgeColor by animateColorAsState(
        targetValue = when {
            checked -> c.success
            overlay -> Color.White.copy(alpha = 0.85f)
            else -> c.hair
        },
        animationSpec = tween(BgMotion.CHECK_MS),
        label = "bgCheckEdge",
    )
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(44.dp)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggle,
                    )
                } else Modifier
            )
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(17.dp)
                .clip(shape)
                .background(fillColor)
                .border(BorderStroke(1.2.dp, edgeColor), shape),
            contentAlignment = Alignment.Center,
        ) {
            // 勾号常驻组合树（scale 0 即不可见），否则取消选中时没有「缩回去」的过程
            Text(
                text = "✓",
                fontSize = 9.sp,
                lineHeight = 9.sp,
                color = if (overlay) Color.White else c.paper,
                modifier = Modifier.graphicsLayer {
                    // 形变走 graphicsLayer（draw 阶段），不触发 measure/layout
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                },
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════
   分段控件
   ═══════════════════════════════════════════════════════ */

// 旧版泛型 BgSegmented（一格反色的「印刷表格」）已删除：全仓库零调用者，
// 且功能不全（无滑块、无动效）。带拖尾滑块的正式版见 BgSegmented.kt
// —— API 改为 (options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit)。

/* ═══════════════════════════════════════════════════════
   按钮
   ═══════════════════════════════════════════════════════ */

/**
 * 按压缩放的共享实现（规范 ④）。
 *
 * 按下 160ms tween 直压到 0.972（快、无迟滞），松手 spring 回弹带轻微过冲
 * —— 「按下快、松手慢」才有按进软胶的手感。
 *
 * 返回 Animatable 而不是 Float：缩放值留给 graphicsLayer 在 draw 阶段读，
 * 压按全程不触发每帧重组。
 */
@Composable
private fun rememberPressScale(interactionSource: InteractionSource): Animatable<Float, AnimationVector1D> {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            scale.animateTo(BgMotion.BUTTON_PRESS_SCALE, BgMotion.pressInSpec())
        } else {
            scale.animateTo(1f, BgMotion.pressOutSpec())
        }
    }
    return scale
}

/**
 * 内阴影：沿圆角边框向内描一圈半透明黑。
 *
 * 液态玻璃受压时边缘先暗下去，和缩放一起形成「按进软胶」的深度感。
 * 用 drawBehind 而不是 elevation —— elevation 是外投影，玻璃要的是内暗边。
 * [shadowAlpha] 由调用处用 BgMotion.BUTTON_SHADOW_REST/PRESSED 补间。
 */
private fun Modifier.bgInnerShadow(shadowAlpha: Float): Modifier = drawBehind {
    val stroke = BgMotion.BUTTON_SHADOW_STROKE.toPx()
    drawRoundRect(
        color = Color.Black.copy(alpha = shadowAlpha),
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(BgRadius.r2.toPx()),
        style = Stroke(width = stroke),
    )
}

/**
 * 主按钮。橙底反白，方角。
 * 全屏里最多出现一次 —— 橙是「主行动」的信号，出现两次就失效。
 *
 * 液态玻璃动效（规范 ④）：按下 scale .972 + 内阴影加深，松手 spring 回弹；
 * 另有一条 420ms 的斜向高光每次按下从左扫到右（一次性，不循环）。
 */
@Composable
fun BgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    /** 加载中：转圈 + [loadingText]，期间不可点击，底色降为 hair。 */
    loading: Boolean = false,
    loadingText: String = "处理中",
) {
    val c = LocalBgColors.current
    // interactionSource 提出来共享：clickable 消费按下事件，动画层读同一个源
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = rememberPressScale(interactionSource)
    // 内阴影透明度补间：160ms 加深，短动画走组合阶段即可
    val shadowAlpha by animateFloatAsState(
        targetValue = if (pressed) BgMotion.BUTTON_SHADOW_PRESSED else BgMotion.BUTTON_SHADOW_REST,
        animationSpec = BgMotion.pressInSpec(),
        label = "primaryShadow",
    )
    // 高光扫过：每次按下从 0 跑到 1，一次性动画（省电，也不干扰阅读）
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, BgMotion.highlightSweepSpec())
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                // 形变走 graphicsLayer（draw 阶段），不触发 measure/layout
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(BgRadius.r2))
            // 禁用态用「淡化的主色」而不是 hair 灰：
            // 灰块看起来像「这个按钮本来就是灰的」，用户不知道它是主操作；
            // 淡橙则一眼能认出「是主按钮，但现在还不能点」。
            .background(
                when {
                    loading -> c.hair
                    enabled -> c.accent
                    else -> c.accent.copy(alpha = 0.30f)
                }
            )
            // 禁用/加载中不画内阴影：底是 hair 时内阴影会变成一圈深边，看起来像脏边
            .bgInnerShadow(if (enabled && !loading) shadowAlpha else 0f)
            .drawWithContent {
                drawContent()
                // 斜向白渐变带从左到右扫过；读 sweep.value 在 draw 阶段，不触发重组
                val f = sweep.value
                if (f > 0f && f < 1f) {
                    val band = size.width * BgMotion.BUTTON_HIGHLIGHT_WIDTH
                    val x = -band + (size.width + band) * f
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = BgMotion.BUTTON_HIGHLIGHT_ALPHA),
                                Color.Transparent,
                            ),
                            start = Offset(x, 0f),
                            end = Offset(x + band, size.height),
                        ),
                        cornerRadius = CornerRadius(BgRadius.r2.toPx()),
                    )
                }
            }
            .then(
                if (enabled && !loading) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = c.ink3,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = loadingText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = c.ink2,
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                // 禁用态底是 30% 淡橙，白字在上面看不清，改用深色文字
                color = if (enabled) c.accentInk else c.ink2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 次级按钮。只有一圈细线，无边无影。
 *
 * 按压反馈与主按钮共享同一组曲线（规范 ④），但不加高光扫过
 * —— 高光是主 CTA 的专属信号，次级按钮也发光会稀释它。
 */
@Composable
fun BgGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 42.dp,
    color: Color = LocalBgColors.current.ink2,
) {
    val c = LocalBgColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = rememberPressScale(interactionSource)
    val shadowAlpha by animateFloatAsState(
        targetValue = if (pressed) BgMotion.BUTTON_SHADOW_PRESSED else BgMotion.BUTTON_SHADOW_REST,
        animationSpec = BgMotion.pressInSpec(),
        label = "ghostShadow",
    )
    Box(
        modifier
            .height(height)
            .graphicsLayer {
                // 形变走 graphicsLayer（draw 阶段），不触发 measure/layout
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(BgRadius.r2))
            // 次按钮描边压淡，避免和主按钮抢注意力
            .border(BorderStroke(1.dp, c.hair.copy(alpha = 0.6f)), RoundedCornerShape(BgRadius.r2))
            .alpha(if (enabled) 1f else 0.45f)
            .bgInnerShadow(shadowAlpha)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 12.5.sp,
            color = color,
            maxLines = 1,
        )
    }
}

/* ═══════════════════════════════════════════════════════
   警告 / 说明条
   ═══════════════════════════════════════════════════════ */

/**
 * 左侧竖线式的提示条。
 *
 * 取代旧版的 Card + 图标 + 两行文字 —— 那种要占固定高度。
 * 这里缺几项就列几条，高度随内容走。
 *
 * @param critical true 用粉色竖线（真问题），false 用细线色（提示性质）
 */
@Composable
fun BgWarn(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    critical: Boolean = true,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = LocalBgColors.current
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onAction != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAction,
                    )
                } else Modifier
            )
            .padding(vertical = 10.dp)
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(if (actionText != null) 44.dp else if (body.isBlank()) 18.dp else 32.dp)
                .background(if (critical) c.accent else c.hair)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.ink,
            )
            if (body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink2,
                )
            }
            if (actionText != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = actionText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    letterSpacing = 0.6.sp,
                    color = c.accent,
                )
            }
        }
    }
}

/* ═══════════════════════════⚠═══════════════════════════
   等宽标签
   ═══════════════════════════════════════════════════════ */

/**
 * 一切小标签的统一样式入口。调用方负责 .uppercase()。
 */
@Composable
fun BgMonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalBgColors.current.ink3,
    size: Int = 9.5f.toInt(),
    tracking: Float = 1.6f,
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        letterSpacing = tracking.sp,
        color = color,
    )
}

/* ═══════════════════════════════════════════════════════
   分组小标题
   ═══════════════════════════════════════════════════════ */

/** 等宽大写小标题 + 可选后缀。用于设置页与任务列表的分组。 */
@Composable
fun BgSectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    accent: String? = null,
) {
    val c = LocalBgColors.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 中文用小节标题的字重和字号 —— 它是这一段的标题，要能一眼抓到。
        // 之前是 9.5sp 等宽 + 字距 1.6，比正文小还撑散了，读起来像水印。
        // 等宽字体只留给英文标签和数字：中文笔画密度高，等宽字面窄，纯粹是自找难读。
        Text(
            text = text,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            color = c.ink,
        )
        if (accent != null) {
            Spacer(Modifier.width(7.dp))
            Text(
                text = accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                letterSpacing = 1.0.sp,
                color = c.accent.copy(alpha = 0.75f),
            )
        }
    }
}

/* ═══════════════════════════════════════════════════════
   状态点
   ═══════════════════════════════════════════════════════ */

/**
 * 下载状态指示点。
 *
 * 用颜色 + 文字双编码：不能只靠颜色（色弱用户分不清粉与青），
 * 所以调用处必须同时给文字（「下载中」「已完成」「失败」）。
 */
@Composable
fun BgStatusDot(color: Color, size: Dp = 5.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

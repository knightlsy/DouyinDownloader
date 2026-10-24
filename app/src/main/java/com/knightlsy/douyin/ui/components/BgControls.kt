package com.knightlsy.douyin.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320),
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
 * 方角勾选框。
 *
 * Material 的 Checkbox 是 18dp 圆角 + 40dp 触摸区。这里视觉 17dp 方角，
 * 但外层用 44dp 的透明触摸区包住 —— 视觉不变，可点面积达标。
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
    val borderColor = if (overlay) Color.White.copy(alpha = 0.85f) else c.hair
    val fillColor = when {
        checked -> c.accent
        overlay -> Color.Black.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
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
                .clip(RoundedCornerShape(BgRadius.r1))
                .background(fillColor)
                .border(BorderStroke(1.2.dp, if (checked) c.accent else borderColor),
                    RoundedCornerShape(BgRadius.r1)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    fontSize = 9.sp,
                    lineHeight = 9.sp,
                    color = if (overlay) Color.White else c.accentInk,
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
   分段控件
   ═══════════════════════════════════════════════════════ */

/**
 * 分段控件。用于「跟随系统 / 深色 / 浅色」这类少量固定选项。
 * 一格反色，没有胶囊外形 —— 视觉上更接近印刷品的表格。
 */
@Composable
fun <T> BgSegmented(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(BgRadius.r2))
            .border(BorderStroke(1.dp, c.hair), RoundedCornerShape(BgRadius.r2))
    ) {
        options.forEachIndexed { idx, opt ->
            val isOn = opt == selected
            Box(
                Modifier
                    .weight(1f)
                    .background(if (isOn) c.ink else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(opt) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(opt),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 0.3.sp,
                    color = if (isOn) c.paper else c.ink3,
                    maxLines = 1,
                )
            }
            if (idx != options.lastIndex) {
                Box(Modifier.width(1.dp).height(26.dp).background(c.hair))
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
   按钮
   ═══════════════════════════════════════════════════════ */

/**
 * 主按钮。粉底反白，方角。
 * 全屏里最多出现一次 —— 粉是「主行动」的信号，出现两次就失效。
 */
@Composable
fun BgPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 48.dp,
) {
    val c = LocalBgColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(BgRadius.r2))
            .background(if (enabled) c.accent else c.hair)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) c.accentInk else c.ink3,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 次级按钮。只有一圈细线，无边无影。
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
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(BgRadius.r2))
            .border(BorderStroke(1.dp, c.hair), RoundedCornerShape(BgRadius.r2))
            .alpha(if (enabled) 1f else 0.45f)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
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
        Text(
            text = text.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            letterSpacing = 1.6.sp,
            color = c.ink3,
        )
        if (accent != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = accent,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                letterSpacing = 1.0.sp,
                color = c.accent,
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

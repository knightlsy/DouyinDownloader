package com.knightlsy.douyin.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.theme.BgMotion
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 带拖尾滑块的段控件（规范 ①：拖尾型液体）。
 *
 * 三段动画同时跑，形成「前缘冲出、后缘拖拽」的拉丝感：
 *  - 主体 340ms 位移 + 宽度 1→1.22×→1.07×→1 + 圆角 12→17→13→12dp
 *  - 拖尾 60ms 延迟启动、420ms 位移，宽度 1.34×，透明度 0.55→0
 *
 * 形变全部走 graphicsLayer（translationX / scaleX，draw 阶段），
 * 不用 animateContentSize —— 后者每帧触发 measure/layout 会掉帧。
 *
 * 颜色语义：选中 = 绿（[com.knightlsy.douyin.ui.theme.BgColors.success]），
 * 橙只留给主 CTA，一色一义。
 *
 * @param selectedIndex 当前选中下标（越界会被钳回合法区间）
 * @param onSelect 点击某段时回调其下标
 */
@Composable
fun BgSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current

    // 空选项：只留一个占位壳，避免除以 0 的段宽
    if (options.isEmpty()) {
        Box(
            modifier
                .height(BgMotion.TOUCH_HEIGHT_MIN)
                .clip(RoundedCornerShape(BgRadius.r3)),
        )
        return
    }

    val idx = selectedIndex.coerceIn(0, options.lastIndex)
    val n = options.size

    /* ── 动效状态：Animatable 而不是 animate*AsState，
     *    因为位移 / 拉伸 / 圆角 / 拖尾透明度要各自独立中断续接。 ── */
    val bodyX = remember { Animatable(idx.toFloat()) }        // 主体位置（以段宽为单位）
    val stretch = remember { Animatable(1f) }                  // 主体宽度倍数
    val radius = remember { Animatable(BgMotion.SEG_RADIUS_REST.value) } // 圆角 dp
    val trailX = remember { Animatable(idx.toFloat()) }       // 拖尾位置
    val trailStretch = remember { Animatable(1f) }            // 拖尾宽度倍数
    val trailAlpha = remember { Animatable(0f) }              // 拖尾透明度（静止时不可见）
    var started by remember { mutableStateOf(false) }          // 首帧是否已就位
    var movingRight by remember { mutableStateOf(true) }       // 拉伸的锚点方向

    LaunchedEffect(idx) {
        // 首帧直接就位：不播「凭空拉一下」的入场动画
        if (!started) {
            started = true
            return@LaunchedEffect
        }
        movingRight = idx >= bodyX.value
        // 主体：位移 + 拉伸 + 圆角三条动画同时跑（规范 ①）
        launch { bodyX.animateTo(idx.toFloat(), BgMotion.segBodySpring()) }
        launch {
            stretch.snapTo(1f)
            stretch.animateTo(1f, BgMotion.segStretchKeyframes())
        }
        launch {
            radius.snapTo(BgMotion.SEG_RADIUS_REST.value)
            radius.animateTo(BgMotion.SEG_RADIUS_REST.value, BgMotion.segRadiusKeyframes())
        }
        // 拖尾：60ms 延迟启动，比主体慢半拍 —— 两者重叠的瞬间才有液体感
        launch {
            delay(BgMotion.SEG_TRAIL_DELAY_MS)
            launch { trailX.animateTo(idx.toFloat(), BgMotion.segTrailSpring()) }
            launch {
                trailStretch.snapTo(1f)
                trailStretch.animateTo(1f, BgMotion.segTrailStretchKeyframes())
            }
            launch {
                trailAlpha.snapTo(BgMotion.SEG_TRAIL_ALPHA_START)
                trailAlpha.animateTo(0f, BgMotion.segTrailAlphaKeyframes())
            }
        }
    }

    // API 31+ 有 RenderEffect 才能真模糊；26–30 退化成半透明矩形（见 BgMotion 注释）
    val hasBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    BoxWithConstraints(
        modifier
            .height(BgMotion.TOUCH_HEIGHT_MIN)
            .clip(RoundedCornerShape(BgRadius.r3))
            .border(BorderStroke(BgSpace.hair, c.hair), RoundedCornerShape(BgRadius.r3)),
    ) {
        val segW = maxWidth / n
        val segWpx = with(LocalDensity.current) { segW.toPx() }
        val pillH = BgMotion.TOUCH_HEIGHT_MIN - BgMotion.SEG_PILL_INSET * 2

        /* ── 拖尾层（画在主体下面） ── */
        // 拖尾永远朝运动反方向甩：向右移动时以右缘为锚，超宽的部分留在左侧身后
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(segW)
                .height(pillH)
                .then(if (hasBlur) Modifier.blur(BgMotion.SEG_TRAIL_BLUR) else Modifier)
                .graphicsLayer {
                    translationX = trailX.value * segWpx
                    scaleX = trailStretch.value
                    transformOrigin = TransformOrigin(if (movingRight) 1f else 0f, 0.5f)
                }
                .clip(RoundedCornerShape(radius.value.dp))
                .background(
                    c.success.copy(
                        alpha = if (hasBlur) trailAlpha.value
                        else trailAlpha.value * BgMotion.SEG_TRAIL_DEGRADE_ALPHA
                    )
                ),
        )

        /* ── 主体滑块 ── */
        // transformOrigin 钉在运动方向的后缘：向右移动时以左缘为锚，
        // 拉伸全部加在前缘上 —— 才是「前缘冲出」，而不是向两边均匀变胖
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .width(segW)
                .height(pillH)
                .graphicsLayer {
                    translationX = bodyX.value * segWpx
                    scaleX = stretch.value
                    transformOrigin = TransformOrigin(if (movingRight) 0f else 1f, 0.5f)
                }
                .clip(RoundedCornerShape(radius.value.dp))
                .background(c.success),
        )

        /* ── 选项文字（最上层，整格可点） ── */
        Row(Modifier.matchParentSize()) {
            options.forEachIndexed { i, opt ->
                val isOn = i == idx
                // 选中变色 + 字重变化，200ms（规范 ③ 同款节奏）
                val textColor by animateColorAsState(
                    targetValue = if (isOn) c.paper else c.ink3,
                    animationSpec = tween(BgMotion.TEXT_MS),
                    label = "segTextColor",
                )
                val weight by animateFloatAsState(
                    targetValue = if (isOn) BgMotion.TEXT_WEIGHT_ACTIVE else BgMotion.TEXT_WEIGHT_REST,
                    animationSpec = tween(BgMotion.TEXT_MS),
                    label = "segTextWeight",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = opt,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 0.3.sp,
                        color = textColor,
                        fontWeight = FontWeight(weight.roundToInt()),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

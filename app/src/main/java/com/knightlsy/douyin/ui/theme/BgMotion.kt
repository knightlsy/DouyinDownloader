package com.knightlsy.douyin.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 动效 token 层 —— 液态玻璃版。
 *
 * 依据 product/redesign/motion-spec.md：核心不是「动得快」，是**形变连续**。
 * 所有时长 / 曲线 / 形变倍数集中在这里，控件代码不允许出现裸数字，
 * 改手感只改这一处。
 *
 * ## CSS bezier → dampingRatio 映射（只在这里换算）
 *
 * | CSS cubic-bezier 第二参 | Compose dampingRatio | 常量 |
 * |---|---|---|
 * | 1.3（轻微） | 0.85 | [DAMPING_SLIGHT] |
 * | 1.42（中等） | 0.72 | [DAMPING_MEDIUM] |
 * | 1.5（明显） | 0.62 | [DAMPING_PRONOUNCED] |
 * | 1.7（强弹） | 0.50 | [DAMPING_STRONG] |
 *
 * ## 全局约束（规范末节）
 * - 进入慢 / 退出快：[ENTER_MS] / [EXIT_MS]
 * - 位移上限 [MAX_TRANSLATION]：超过就是浮夸的滑动，不是「轻」
 * - 不用无限循环动画：省电，也不干扰阅读
 * - 所有形变只改 scale / translation / radius / alpha / color，
 *   且走 graphicsLayer（draw 阶段），不触发 measure/layout 掉帧
 */
object BgMotion {

    /* ══════════════ 时长（ms） ══════════════ */

    /** 分段滑块：主体位移时长（规范 ①）。 */
    const val SEG_BODY_MS = 340

    /** 分段滑块：拖尾位移时长，比主体长 80ms，收尾比主体慢一拍。 */
    const val SEG_TRAIL_MS = 420

    /** 分段滑块：拖尾启动延迟。主体到位时拖尾还在路上，重叠的瞬间才有液体感。 */
    const val SEG_TRAIL_DELAY_MS = 60L

    /** 选项文字变色 / 变字重（规范 ③ 同款）。 */
    const val TEXT_MS = 200

    /** 开关 knob 滑动（规范 ②）。 */
    const val SWITCH_MS = 280

    /** 开关轨道底色线性过渡。 */
    const val SWITCH_TRACK_MS = 240

    /** 底部 Tab 指示条滑动（规范 ③）。 */
    const val TAB_MS = 240

    /** 按钮按下 / 松手（规范 ④：按下快、松手慢）。 */
    const val BUTTON_PRESS_MS = 160
    const val BUTTON_RELEASE_MS = 160

    /** 主按钮高光扫过。 */
    const val BUTTON_HIGHLIGHT_MS = 420

    /** 勾选框（规范 ⑤）。 */
    const val CHECK_MS = 220

    /** 全局：出现用慢档（260–340 区间取中），用户注意力在「出现」上。 */
    const val ENTER_MS = 300

    /** 全局：消失用快档（150–180 区间取中）。 */
    const val EXIT_MS = 165

    /* ══════════════ 形变倍数 / 尺寸 ══════════════ */

    /** 分段滑块主体峰值拉伸（规范 ①）。 */
    const val SEG_STRETCH = 1.22f

    /** 分段滑块主体回落中继值（原值 → 1.22× → 1.07× → 原值）。 */
    const val SEG_SETTLE = 1.07f

    /** 拖尾峰值拉伸 —— 超宽的拖尾才拉得出丝。 */
    const val SEG_TRAIL_STRETCH = 1.34f

    /** 分段滑块圆角轨迹：12 → 17 → 13 → 12dp（峰值 17 恰好是 34dp 滑块的一半）。 */
    val SEG_RADIUS_REST = 12.dp
    val SEG_RADIUS_PEAK = 17.dp
    val SEG_RADIUS_SETTLE = 13.dp

    /** 滑块上下内缩，让 44dp 触控高度内是 34dp 的胶囊。 */
    val SEG_PILL_INSET = 5.dp

    /** 按钮按下缩放（规范 ④：scale .972）。 */
    const val BUTTON_PRESS_SCALE = 0.972f

    /** 勾选框选中时的圆角：17dp 的框上 9dp 会被钳到一半尺寸，正好是 50% 圆。 */
    val CHECK_CORNER_ROUND = 9.dp

    /** 开关 knob：常态 24×24，按住压扁成 30×24（规范 ②）。 */
    val SWITCH_KNOB_REST = 24.dp
    val SWITCH_KNOB_PRESSED_W = 30.dp
    val SWITCH_KNOB_PRESSED_H = 24.dp

    /** Tab 指示条（规范 ③）：宽 26dp 高 2dp 圆角 1dp，外发光 6dp。 */
    val TAB_INDICATOR_WIDTH = 26.dp
    val TAB_INDICATOR_HEIGHT = 2.dp
    val TAB_INDICATOR_RADIUS = 1.dp
    val TAB_GLOW_BLUR = 6.dp
    const val TAB_GLOW_ALPHA = 0.55f

    /** 全局位移上限：超过就是浮夸的滑动，不是「轻」。 */
    val MAX_TRANSLATION: Dp = 16.dp

    /** 触控目标最小高度（含分段滑块的整格）。 */
    val TOUCH_HEIGHT_MIN = 44.dp

    /* ══════════════ 拖尾参数 ══════════════ */

    /** 拖尾透明度轨迹：0.55 → 0.34 → 0。 */
    const val SEG_TRAIL_ALPHA_START = 0.55f
    const val SEG_TRAIL_ALPHA_MID = 0.34f

    /** 拖尾模糊半径（对应 CSS blur(7px)；仅 API 31+ 生效）。 */
    val SEG_TRAIL_BLUR = 7.dp

    /**
     * API 26–30 没有 blur（RenderEffect 是 31 才有），拖尾退化成半透明矩形。
     * 无模糊时边缘发硬，把透明度整体乘这个系数补偿，避免拖尾抢戏。
     */
    const val SEG_TRAIL_DEGRADE_ALPHA = 0.7f

    /* ══════════════ 按钮内阴影 / 高光 ══════════════ */

    /** 内阴影描边宽度与透明度：按下时边缘变暗更深（规范 ④）。 */
    val BUTTON_SHADOW_STROKE = 2.dp
    const val BUTTON_SHADOW_REST = 0.10f
    const val BUTTON_SHADOW_PRESSED = 0.30f

    /** 高光扫过：斜向白渐变带宽占比与峰值透明度。 */
    const val BUTTON_HIGHLIGHT_WIDTH = 0.4f
    const val BUTTON_HIGHLIGHT_ALPHA = 0.22f

    /* ══════════════ 文字 ══════════════ */

    /** 选中文字字重 500 → 650（规范 ③）。 */
    const val TEXT_WEIGHT_REST = 500f
    const val TEXT_WEIGHT_ACTIVE = 650f

    /* ══════════════ dampingRatio 映射（唯一换算处） ══════════════ */

    /** CSS bezier 第二参 1.3 —— 轻微过冲。 */
    const val DAMPING_SLIGHT = 0.85f

    /** CSS bezier 第二参 1.42 —— 中等过冲。 */
    const val DAMPING_MEDIUM = 0.72f

    /** CSS bezier 第二参 1.5 —— 明显过冲。 */
    const val DAMPING_PRONOUNCED = 0.62f

    /** CSS bezier 第二参 1.7 —— 强弹（面积小的元素才用，如勾选框）。 */
    const val DAMPING_STRONG = 0.50f

    /* ══════════════ spring 工厂 ══════════════ */

    /**
     * 液态曲线的通用 spring 工厂：dampingRatio 的换算只发生在这一处，
     * 控件代码永远传 [DAMPING_*] 常量，不写裸 0.xx。
     */
    fun <T> liquidSpring(
        dampingRatio: Float = DAMPING_SLIGHT,
        stiffness: Float = Spring.StiffnessMediumLow,
        visibilityThreshold: T? = null,
    ): SpringSpec<T> = spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness,
        visibilityThreshold = visibilityThreshold,
    )

    /** 分段滑块主体（cubic-bezier(.32,1.42,.55,1)）：中等过冲。 */
    fun <T> segBodySpring(): SpringSpec<T> = liquidSpring(DAMPING_MEDIUM)

    /** 分段拖尾（cubic-bezier(.3,1.05,.6,1)）：过冲小于 1.3 → 轻微档。 */
    fun <T> segTrailSpring(): SpringSpec<T> = liquidSpring(DAMPING_SLIGHT)

    /** 按压 / 开关（cubic-bezier(.34,1.5,.64,1)）：明显过冲。 */
    fun <T> pressSpring(): SpringSpec<T> = liquidSpring(DAMPING_PRONOUNCED, Spring.StiffnessMedium)

    /** 勾选框（cubic-bezier(.34,1.7,.64,1)）：最强过冲，面积小要弹得明显。 */
    fun <T> checkSpring(): SpringSpec<T> = liquidSpring(DAMPING_STRONG, Spring.StiffnessMedium)

    /** 进度条：轻微过冲即可，超出的部分会被外层 clip 吃掉，只留下「追上去」的手感。 */
    fun <T> progressSpring(): SpringSpec<T> = liquidSpring(DAMPING_SLIGHT)

    /* ══════════════ 按钮：按下 tween（快）/ 松手 spring（慢 + 过冲） ══════════════ */

    /** 规范 ④ 的按压 bezier(.34,1.5,.64,1)：160ms 内压到底，easing 自带越过量程的「软胶」感。 */
    private val PressEasing = CubicBezierEasing(0.34f, 1.5f, 0.64f, 1f)

    /** 按下：固定时长快进（即时→160ms），不要 spring 的起步迟滞。 */
    fun <T> pressInSpec(): TweenSpec<T> = tween(BUTTON_PRESS_MS, easing = PressEasing)

    /** 松手：spring 回弹带轻微过冲 —— 「按下快、松手慢」才有按进软胶的手感。 */
    fun <T> pressOutSpec(): SpringSpec<T> = liquidSpring(DAMPING_PRONOUNCED, Spring.StiffnessMediumLow)

    /** 高光扫过：一次性的 420ms 左→右，缓入缓出，不循环。 */
    fun <T> highlightSweepSpec(): TweenSpec<T> =
        tween(BUTTON_HIGHLIGHT_MS, easing = CubicBezierEasing(0.4f, 0f, 0.6f, 1f))

    /* ══════════════ 分段滑块 keyframes（原值 → 峰值 → 中继 → 原值） ══════════════ */

    /** 主体宽度：1 → 1.22× → 1.07× → 1，峰值在 40% 处（前缘冲出后回落）。 */
    fun segStretchKeyframes(): KeyframesSpec<Float> = keyframes {
        durationMillis = SEG_BODY_MS
        SEG_STRETCH at (SEG_BODY_MS * 0.4f).roundToInt()
        SEG_SETTLE at (SEG_BODY_MS * 0.7f).roundToInt()
    }

    /** 主体圆角：12 → 17 → 13 → 12dp，和宽度同步膨胀再收。 */
    fun segRadiusKeyframes(): KeyframesSpec<Float> = keyframes {
        durationMillis = SEG_BODY_MS
        SEG_RADIUS_PEAK.value at (SEG_BODY_MS * 0.4f).roundToInt()
        SEG_RADIUS_SETTLE.value at (SEG_BODY_MS * 0.7f).roundToInt()
    }

    /** 拖尾宽度：1 → 1.34× → 1，拖尾全程比主体宽，才盖得住后缘。 */
    fun segTrailStretchKeyframes(): KeyframesSpec<Float> = keyframes {
        durationMillis = SEG_TRAIL_MS
        SEG_TRAIL_STRETCH at (SEG_TRAIL_MS * 0.4f).roundToInt()
    }

    /** 拖尾透明度：0.55 → 0.34 → 0，走到终点前先泄掉一半，避免突然消失。 */
    fun segTrailAlphaKeyframes(): KeyframesSpec<Float> = keyframes {
        durationMillis = SEG_TRAIL_MS
        SEG_TRAIL_ALPHA_MID at (SEG_TRAIL_MS * 0.5f).roundToInt()
    }
}

package com.knightlsy.douyin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 配色 token —— 暖调液态玻璃版。
 *
 * ## 温度：warm
 * 中性色带极轻微暖偏（chroma ≤ 0.02），不再用蓝调黑。
 * 原来的 #0A0810 是带紫的黑，玻璃叠上去会发灰；#0C0B0A 接近中性偏暖，
 * 橙色的强调才能透出来。
 *
 * ## 颜色语义（严格执行，一色一义）
 * ```
 * 橙 #FF7A3A  = 主操作。解析 / 下载 / 主 CTA。全应用唯一强调色
 * 绿 #6FD3A8  = 成功 / 选中 / 进行中。不做装饰
 * 红 #F26B5E  = 失败。破坏性操作（清空历史）不用红，改「管理」+ 多选
 * 中性灰      = 次级文字操作（粘贴 / 全选），不抢主操作
 * ```
 * 旧版是「粉同时表示主行动 / 失败 / 下载中」，一色三义。这次拆开了。
 *
 * ## 对比度实测（WCAG AA 要求 4.5:1）
 * | | 深色底 #0C0B0A | 浅色底 #F2EFEB |
 * |---|---|---|
 * | 正文 | 17.2:1 | 15.6:1 |
 * | 次要 | 7.3:1 | 4.8:1 |
 * | 强调 | 7.6:1 | 4.9:1 |
 * | 成功 | 10.8:1 | 4.5:1 |
 * | 失败 | 6.6:1 | 4.6:1 |
 * | 警告 | 11.1:1 | 4.9:1 |
 *
 * 旧版 ink3（#58575F）对深底只有 2.8:1，达不到 AA。这次提到 7.3:1。
 *
 * ## 旧名保留
 * [DouyinPink] / [DouyinCyan] 作为别名保留，迁移期代码不必一次改完。
 * 新代码请用 BgAccent / BgSuccess。
 */

/* ══════════ 品牌色 ══════════ */

/** 主强调色（深色主题用）。暖橙。全应用唯一强调色。 */
val BgAccent = Color(0xFFFF7A3A)

/** 主强调色（浅色主题用）。压暗到 4.9:1，否则暖白底上读不出。 */
val BgAccentDeep = Color(0xFFAC4A1B)

/** 中间调，用于渐变收尾。 */
val BgAccentMid = Color(0xFFE8642A)

/** 柔和版，仅用于渐变内层。 */
val BgAccentSoft = Color(0xFFFFB98F)

/** 成功 / 选中 / 进行中（深色主题用）。薄荷绿。 */
val BgSuccess = Color(0xFF6FD3A8)

/** 成功 / 选中（浅色主题用）。 */
val BgSuccessDeep = Color(0xFF287A60)

/** 深色底。暖中性黑。 */
val BgInk = Color(0xFF0C0B0A)

/* ══════════ 旧名别名（迁移期兼容，新代码勿用）══════════ */

@Deprecated("改用 BgAccent", ReplaceWith("BgAccent"))
val DouyinPink = BgAccent

@Deprecated("改用 BgAccentDeep", ReplaceWith("BgAccentDeep"))
val DouyinPinkDeep = BgAccentDeep

@Deprecated("改用 BgAccentMid", ReplaceWith("BgAccentMid"))
val DouyinPinkMid = BgAccentMid

@Deprecated("改用 BgAccentSoft", ReplaceWith("BgAccentSoft"))
val DouyinPinkSoft = BgAccentSoft

@Deprecated("改用 BgSuccess", ReplaceWith("BgSuccess"))
val DouyinCyan = BgSuccess

@Deprecated("改用 BgSuccessDeep", ReplaceWith("BgSuccessDeep"))
val DouyinCyanDeep = BgSuccessDeep

@Deprecated("改用 BgInk", ReplaceWith("BgInk"))
val DouyinDark = BgInk

/* ══════════ 深色槽位 ══════════ */
val BackgroundDark = Color(0xFF0C0B0A)       // 暖中性黑，chroma 0.008
val SurfaceDark = Color(0xFF141210)          // 卡片，chroma 0.016
val SurfaceVariantDark = Color(0xFF1C1917)   // 次级块（输入框、错误带）
val OnBackgroundDark = Color(0xFFF2EFEC)     // 正文 17.2:1
val OnSurfaceDark = Color(0xFFF2EFEC)
val OnSurfaceVariantDark = Color(0xFFA39C95) // 次要 7.3:1（旧值仅 2.8:1）
val OutlineDark = Color(0xFF2A2724)          // 细线

val PrimaryDark = BgAccent
val OnPrimaryDark = Color(0xFF1A0E06)        // 压在橙上的字，7.3:1
val PrimaryContainerDark = Color(0xFF3D2418)
val OnPrimaryContainerDark = Color(0xFFFFD9C7)

val SecondaryDark = BgSuccess
val OnSecondaryDark = Color(0xFF06251A)
val SecondaryContainerDark = Color(0xFF123B30)
val OnSecondaryContainerDark = Color(0xFFC7F5E4)

/* ══════════ 浅色槽位 ══════════ */
val BackgroundLight = Color(0xFFF2EFEB)      // 暖白
val SurfaceLight = Color(0xFFFAF8F5)
val SurfaceVariantLight = Color(0xFFEBE6E0)
val OnBackgroundLight = Color(0xFF1C1612)    // 正文 15.6:1
val OnSurfaceLight = Color(0xFF1C1612)
val OnSurfaceVariantLight = Color(0xFF6D6864) // 次要 4.8:1
val OutlineLight = Color(0xFFDDD6CF)

val PrimaryLight = BgAccentDeep
val OnPrimaryLight = Color(0xFFFFFFFF)       // 压在深橙上，5.6:1
val PrimaryContainerLight = Color(0xFFFFE0CC)
val OnPrimaryContainerLight = Color(0xFF3D1A00)

val SecondaryLight = BgSuccessDeep
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFCBEEE2)
val OnSecondaryContainerLight = Color(0xFF00291C)

/* ══════════ 顶栏渐变（已弃用，保留占位防旧引用）══════════ */
val AppBarDarkStart = Color(0xFF1B1030)
val AppBarDarkMid = Color(0xFF2A1038)
val AppBarDarkEnd = Color(0xFF3A1030)
val AppBarLightStart = Color(0xFFFFF2F6)
val AppBarLightMid = Color(0xFFFFE9F0)
val AppBarLightTint = Color(0xFFF8EDF8)
val AppBarLightEnd = BackgroundLight

/* ══════════ 状态色 ══════════ */
val StatPink = BgAccent
val StatGreen = BgSuccess
val StatRed = Color(0xFFF26B5E)              // 失败 6.6:1
val StatAmber = Color(0xFFF5B940)            // 进行中 11.1:1（琥珀，与橙区分）

val StatusSuccess = BgSuccess
val StatusFailed = Color(0xFFF26B5E)
val StatusRunning = Color(0xFFF5B940)

/** 浅色主题下的状态色（压暗到 AA）。 */
val StatusSuccessLight = Color(0xFF287A60)
val StatusFailedLight = Color(0xFFC43A2E)
val StatusRunningLight = Color(0xFF8A5F0D)


/* ══════════ 液态玻璃 ══════════
 * 玻璃的三层构成。缺任何一层就不成立：
 *   1. 半透明底  —— alpha 必须低（0.04~0.10），高了立刻变灰膜
 *   2. 1dp 亮边  —— 模拟玻璃边缘的反光
 *   3. 顶部内高光 —— 光从上方来，玻璃上沿更亮
 */
val GlassTintDark = Color(0x14FFFFFF)        // 8% 白：深色主题下的玻璃底
val GlassEdgeDark = Color(0x24FFFFFF)        // 14% 白：玻璃边缘
val GlassTopDark = Color(0x33FFFFFF)         // 20% 白：顶部内高光

val GlassTintLight = Color(0x70FFFFFF)       // 44% 白：浅色主题下玻璃更实（浅底透色难）
/**
 * 浅色主题的玻璃描边用**极淡的暗边**，不是白边。
 * 浅色玻璃叠在米色底上，白描边只会变成一条白线（用户看到的「小白条」就是它）；
 * 而一点点暗边才能把玻璃从背景里分离出来 —— iOS 浅色毛玻璃就是这么做的。
 */
val GlassEdgeLight = Color(0x14000000)       // 8% 黑
val GlassTopLight = Color(0x33FFFFFF)        // 20% 白高光（纯白会在浅色顶栏上糊成一块白板）

/** 页面背景的柔光斑颜色（深色主题）。极低不透明度，只给色温。 */
val GlowWarmDark = Color(0x2EC4652B)         // 18% 暖橙
val GlowAmberDark = Color(0x268A4A1E)        // 15% 深琥珀

/** 页面背景的柔光斑颜色（浅色主题）。 */
val GlowWarmLight = Color(0x40F0C9A8)
val GlowAmberLight = Color(0x33E3CDB6)

/* ══════════ 几何 ══════════
 * r1 / r2 保持原值不动 —— 它们被 60 多处引用，其中 r1 用在缩略图、
 * 序号角标这类小元素上，圆角超过元素一半会变成胶囊。圆角放大留到
 * 界面重排时与布局一起改，单独改颜色不会破坏布局。
 *
 * r3 / r4 是新增的大容器圆角，供后续玻璃卡片使用。
 */
object BgRadius {
    /**
     * 圆角是液态玻璃的硬性前提 —— 1dp/2dp 的直角最多做出「便签本」。
     * 玻璃靠边缘张力表达流体感，锐角会直接破坏这层语义。
     *
     * r1 用在缩略图、序号角标这类小元素上：10dp 在 20dp 见方的元素上
     * 正好是半圆（Compose 会自动收敛），呈现为圆形徽章，不会过圆。
     */
    val r1: Dp = 10.dp   // 小元素：缩略图、角标、标签
    val r2: Dp = 16.dp   // 中元素：按钮、输入框
    val r3: Dp = 20.dp   // 玻璃卡片
    val r4: Dp = 26.dp   // 悬浮条 / 底部导航
}

/** 间距骨架。所有页面只从这里取值，不在调用处写 ad-hoc dp。 */
object BgSpace {
    val gutter: Dp = 20.dp
    val groupGap: Dp = 22.dp
    val rowY: Dp = 13.dp
    val hair: Dp = 1.dp
}

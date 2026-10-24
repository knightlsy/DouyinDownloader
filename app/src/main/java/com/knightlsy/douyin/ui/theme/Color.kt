package com.knightlsy.douyin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A+D 配色体系。
 *
 * 骨架取 A（Cinema）：近黑、克制、内容当主角。
 * 配色取 D（Tide）：深紫黑底 + 粉青双色，但整体压暗一档，避免变成廉价霓虹。
 *
 * 所有色值由 OKLCH 精确转换而来（oklch → linear sRGB → gamma → 8bit），不是肉眼估的：
 *   深色底  oklch(0.14 0.018 295)  → #0A0810
 *   深色粉  oklch(0.68 0.21 12)    → #FE4F75   （抖音原色 #FE2C55 的同族，稍沉）
 *   深色青  oklch(0.80 0.13 195)   → #2AD7D7   （抖音原色 #25F4EE 的同族，降饱和）
 *   浅色粉  oklch(0.55 0.21 12)    → #CE1750   （深色粉在白底上会糊，必须压暗）
 *   浅色青  oklch(0.60 0.13 195)   → #009798
 *
 * 纪律：粉只用于「主行动」（解析 / 下载 / 已选）与「失败」，青只用于「成功」与
 * 「次要标记」。其余全部走中性灰。一屏里粉青加起来不超过 4 处 —— 超过就变成装饰。
 */

/* ══════════ 品牌色（跨主题共用名字，深浅取不同值）══════════ */

/** 亮版粉（深色主题用）。 */
val DouyinPink = Color(0xFFFE4F75)

/** 暗版粉（浅色主题用）。深色粉在白底上对比度不足，压暗一档。 */
val DouyinPinkDeep = Color(0xFFCE1750)

/** 中间调，用于渐变收尾。 */
val DouyinPinkMid = Color(0xFFF03A63)

/** 柔和粉，仅用于渐变内层。 */
val DouyinPinkSoft = Color(0xFFFFA0B6)

/** 亮版青（深色主题用）。 */
val DouyinCyan = Color(0xFF2AD7D7)

/** 暗版青（浅色主题用）。新增 —— 旧代码只有亮版，白底上会糊。 */
val DouyinCyanDeep = Color(0xFF009798)

/** 深色底（保留旧名）。 */
val DouyinDark = Color(0xFF0A0810)

/* ══════════ 深色槽位 ══════════ */
val BackgroundDark = Color(0xFF0A0810)       // oklch(0.14 0.018 295)
val SurfaceDark = Color(0xFF13111B)          // oklch(0.185 0.020 295)
val SurfaceVariantDark = Color(0xFF1C1A26)   // oklch(0.225 0.024 295)
val OnBackgroundDark = Color(0xFFEBEBEE)     // oklch(0.94 0.004 295)
val OnSurfaceDark = Color(0xFFEBEBEE)
val OnSurfaceVariantDark = Color(0xFF86858E) // oklch(0.62 0.015 295)
val OutlineDark = Color(0xFF292732)          // oklch(0.28 0.020 295)

val PrimaryDark = DouyinPink
val OnPrimaryDark = Color(0xFF1A0410)
val PrimaryContainerDark = Color(0xFF3A0D1E)
val OnPrimaryContainerDark = Color(0xFFFFD9E1)

val SecondaryDark = DouyinCyan
val OnSecondaryDark = Color(0xFF001112)
val SecondaryContainerDark = Color(0xFF07383A)
val OnSecondaryContainerDark = Color(0xFFC9FBFA)

/* ══════════ 浅色槽位 ══════════ */
val BackgroundLight = Color(0xFFF7F6FA)      // oklch(0.975 0.006 300)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1EFF5)  // oklch(0.955 0.008 300)
val OnBackgroundLight = Color(0xFF0E0C13)    // oklch(0.16 0.015 295)
val OnSurfaceLight = Color(0xFF0E0C13)
val OnSurfaceVariantLight = Color(0xFF5E5C64) // oklch(0.48 0.012 295)
val OutlineLight = Color(0xFFDFDDE3)          // oklch(0.90 0.008 300)

val PrimaryLight = DouyinPinkDeep
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFDCE4)
val OnPrimaryContainerLight = Color(0xFF3F0013)

val SecondaryLight = DouyinCyanDeep
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFC8F1F0)
val OnSecondaryContainerLight = Color(0xFF00302F)

/* ══════════ 顶栏渐变（新顶栏已不用，保留占位以防旧引用）══════════ */
val AppBarDarkStart = Color(0xFF1B1030)
val AppBarDarkMid = Color(0xFF2A1038)
val AppBarDarkEnd = Color(0xFF3A1030)
val AppBarLightStart = Color(0xFFFFF2F6)
val AppBarLightMid = Color(0xFFFFE9F0)
val AppBarLightTint = Color(0xFFF8EDF8)
val AppBarLightEnd = Color(0xFFF7F6FA)   // 与 BackgroundLight 一致，消除分界线

/* ══════════ 状态色 ══════════ */
val StatPink = DouyinPink
val StatGreen = Color(0xFF3FD9A0)        // oklch(0.78 0.16 165)
val StatRed = Color(0xFFF0555E)          // oklch(0.65 0.19 20)
val StatAmber = Color(0xFFF5B940)        // oklch(0.80 0.14 80)

val StatusSuccess = Color(0xFF3FD9A0)
val StatusFailed = Color(0xFFF0555E)
val StatusRunning = Color(0xFFF5B940)

/* ══════════ 几何 ══════════
 * Material 默认 12–28dp 圆角是「友好」语言。这个应用是工具，统一 2dp 方角表达精确。
 * 唯一例外是圆形指示点与勾选圈。
 */
object BgRadius {
    val r1: Dp = 1.dp
    val r2: Dp = 2.dp
}

/** 间距骨架。所有页面只从这里取值，不在调用处写 ad-hoc dp。 */
object BgSpace {
    val gutter: Dp = 20.dp
    val groupGap: Dp = 22.dp
    val rowY: Dp = 13.dp
    val hair: Dp = 1.dp
}

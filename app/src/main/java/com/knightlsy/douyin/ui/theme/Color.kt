package com.knightlsy.douyin.ui.theme

import androidx.compose.ui.graphics.Color

// ============ 品牌色 ============
val DouyinPink = Color(0xFFFE2C55)
val DouyinPinkDeep = Color(0xFFB01E3F)      // 浅色主题下的可读强调色（对比度足够）
val DouyinPinkMid = Color(0xFFFF4064)
val DouyinPinkSoft = Color(0xFFFF7A94)
val DouyinCyan = Color(0xFF25F4EE)
val DouyinDark = Color(0xFF161823)

// ============ 深色主题 ============
val PrimaryDark = Color(0xFFFFB1C8)
val OnPrimaryDark = Color(0xFF5E1029)
val PrimaryContainerDark = Color(0xFF801541)
val OnPrimaryContainerDark = Color(0xFFFFDDE1)
val SecondaryDark = Color(0xFF80DEEA)
val OnSecondaryDark = Color(0xFF00363D)
val SecondaryContainerDark = Color(0xFF004F5C)
val OnSecondaryContainerDark = Color(0xFFB2EBF2)
val BackgroundDark = Color(0xFF0E0F16)
val OnBackgroundDark = Color(0xFFECEDF2)
val SurfaceDark = Color(0xFF181A24)
val OnSurfaceDark = Color(0xFFECEDF2)
val SurfaceVariantDark = Color(0xFF20232F)
val OnSurfaceVariantDark = Color(0xFF9EA0B0)
val OutlineDark = Color(0xFF2B2F3D)

// ============ 浅色主题 ============
// 层次关键：背景明显偏灰、卡片纯白，两者拉开明度差；
// 卡片靠双层柔和阴影浮起，而不是靠描边。
val PrimaryLight = Color(0xFFE91E63)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFFFDDE1)
val OnPrimaryContainerLight = Color(0xFF3F0013)
val SecondaryLight = Color(0xFF00BCD4)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFB2EBF2)
val OnSecondaryContainerLight = Color(0xFF00363D)
val BackgroundLight = Color(0xFFEEF1F7)      // 内容区底色（与顶栏渐变收尾色一致）
val OnBackgroundLight = Color(0xFF12141B)
val SurfaceLight = Color(0xFFFFFFFF)         // 卡片纯白
val OnSurfaceLight = Color(0xFF12141B)
val SurfaceVariantLight = Color(0xFFF7F8FC)  // 输入框 / 次级块
val OnSurfaceVariantLight = Color(0xFF5A5E6E)
val OutlineLight = Color(0xFFE3E7F0)

// ============ 顶栏渐变 ============
// 深色：深紫底 + 粉/青双色光晕（保留品牌感）
val AppBarDarkStart = Color(0xFF1B1030)
val AppBarDarkMid = Color(0xFF2A1038)
val AppBarDarkEnd = Color(0xFF3A1030)
// 浅色：浅粉 → 浅紫 → 内容区底色，末端与背景同色 → 消除分界线
val AppBarLightStart = Color(0xFFFFF2F6)
val AppBarLightMid = Color(0xFFFFE9F0)
val AppBarLightTint = Color(0xFFF8EDF8)
val AppBarLightEnd = Color(0xFFEEF1F7)

// ============ 状态色（统计磁贴 / 下载状态）============
val StatPink = Color(0xFFFE2C55)
val StatGreen = Color(0xFF34D399)
val StatRed = Color(0xFFF87171)
val StatAmber = Color(0xFFFBBF24)

// 下载状态色（深浅通用，饱和度高、在两种背景上都可读）
val StatusSuccess = Color(0xFF34D399)
val StatusFailed = Color(0xFFF87171)
val StatusRunning = Color(0xFFFBBF24)

package com.knightlsy.douyin.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 排版。
 *
 * 设计稿里大标题用的是 Oswald（窄体大写），但 Android 没有内置窄体字族，
 * 打包 Oswald 又会因为不含中文字形而露出 fallback 接缝 —— 中英混排时
 * 英文窄体 + 中文常规体，行高和重心都对不上。
 *
 * 所以这里用「系统无衬线 + Light 字重 + 负字距 + 调用处大写」去逼近窄体的观感：
 * 大写字母本身就让词形变方，负字距压紧字间，Light 字重削弱笔画厚度，
 * 三者叠加在标题尺寸下能拿到接近的「仪表盘」气质。
 *
 * 等宽用于所有数据：体积、时长、分辨率、百分比、文件序号。
 */

private val Sans = FontFamily.SansSerif
private val Mono = FontFamily.Monospace

val Typography = Typography(

    // ── 页面主标题（调用处配合 .uppercase()）──
    displayLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.2).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.9).sp,
    ),

    headlineLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.7).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.1).sp,
    ),

    // ── 等宽小节标签（调用处配合 .uppercase()）──
    titleLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    ),

    // ── 正文 ──
    bodyLarge = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.1.sp,
    ),

    // ── 按钮 / 标签 ──
    labelLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 1.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.4.sp,
    ),
)

/**
 * 等宽数字：让 0–9 占同样宽度，进度百分比变化时不会左右抖动。
 */
val TabularMono = TextStyle(
    fontFamily = Mono,
    fontWeight = FontWeight.Medium,
    fontFeatureSettings = "tnum",
    fontSize = 11.sp,
)

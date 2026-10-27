package com.knightlsy.douyin.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.data.LogStore
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 设置页（二级）：主题偏好、日志入口、关于入口。
 * 主界面顶栏「设置」进入；日志入口再进三级「日志」页。
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: () -> Unit,
) {
    val c = LocalBgColors.current

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .systemBarsPadding()
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = BgSpace.gutter, end = BgSpace.gutter, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(c.accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "设置",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.8.sp,
                color = c.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "← 返回",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = c.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        BgHair()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BgSpace.gutter)
                .padding(top = 18.dp, bottom = 40.dp)
        ) {
            BgSectionLabel("偏好", accent = null)

            SettingCard(
                title = "主题模式",
                value = when (themeMode) {
                    ThemeMode.SYSTEM -> "跟随系统"
                    ThemeMode.LIGHT -> "浅色"
                    ThemeMode.DARK -> "深色"
                },
                onClick = onCycleTheme,
            )

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("诊断", accent = null)

            SettingCard(
                title = "日志",
                value = if (LogStore.isEnabled()) "${LogStore.lineCount()} 条" else "已关闭",
                onClick = onNavigateToLogs,
            )

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("关于", accent = null)

            SettingCard(
                title = "关于抖音下载器",
                value = "版本 · 检查更新 · 开源信息",
                onClick = onNavigateToAbout,
            )
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val c = LocalBgColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BgRadius.r2))
            .background(c.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            color = c.ink3,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "›",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = c.ink3,
        )
    }
}

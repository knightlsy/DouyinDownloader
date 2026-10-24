package com.knightlsy.douyin.ui.screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import com.knightlsy.douyin.viewmodel.DownloadStats

/**
 * 三格统计条。
 *
 * 旧版是 2×2 的磁贴卡（四个格子各带图标），占掉版面上半屏还能多。
 * 这里压成一行三格 + 上下细线，像仪表盘上的读数：数字大、标签小、没有容器。
 *
 * 颜色纪律：今日用主文字色（中性），成功用青，失败用粉。三格就是三个状态，
 * 不需要再用图标说明 —— 颜色 + 标签已经双编码了。
 */
@Composable
fun StatsBar(stats: DownloadStats, modifier: Modifier = Modifier) {
    val c = LocalBgColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BgSpace.gutter)
    ) {
        // 标签原本写「今日」，但 stats 只在内存里、重启即归零，
        // 叫「本次」才不骗人。
        StatCell("本次", stats.totalDownloads.toString(), c.ink, Modifier.weight(1f))
        StatCell("成功", stats.successDownloads.toString(), c.success, Modifier.weight(1f))
        StatCell("失败", stats.failedDownloads.toString(), c.accent, Modifier.weight(1f))
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    Column(modifier) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            letterSpacing = 1.6.sp,
            color = c.ink3,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.displayMedium,
            color = color,
        )
    }
}

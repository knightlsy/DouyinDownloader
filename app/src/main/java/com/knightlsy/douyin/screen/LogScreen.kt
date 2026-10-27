package com.knightlsy.douyin.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.data.LogStore
import com.knightlsy.douyin.ui.components.BgGhostButton
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 日志页（三级）：日志开关 + 复制日志 + 清空日志 + 完整流水预览。
 */
@Composable
fun LogScreen(onBack: () -> Unit) {
    val c = LocalBgColors.current
    val context = LocalContext.current

    // 用本地状态驱动 UI，每次操作后刷新
    var enabled by remember { mutableStateOf(LogStore.isEnabled()) }
    var lineCount by remember { mutableIntStateOf(LogStore.lineCount()) }

    fun refresh() {
        enabled = LogStore.isEnabled()
        lineCount = LogStore.lineCount()
    }

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
                text = "日志",
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
                .padding(horizontal = BgSpace.gutter)
                .padding(top = 18.dp, bottom = 24.dp)
        ) {
            // 开关行
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.surface)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "日志开关",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 13.sp,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (enabled) "记录中 · $lineCount 条" else "已关闭",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = if (enabled) c.success else c.ink3,
                )
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = { v ->
                        LogStore.setEnabled(v)
                        refresh()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.accentInk,
                        checkedTrackColor = c.accent,
                        uncheckedThumbColor = c.ink3,
                        uncheckedTrackColor = c.raise,
                    ),
                )
            }

            Spacer(Modifier.height(14.dp))

            // 操作按钮
            Row(Modifier.fillMaxWidth()) {
                BgGhostButton(
                    text = "复制日志",
                    onClick = {
                        val text = LogStore.dump()
                        if (text.isBlank()) {
                            Toast.makeText(context, "暂无日志", Toast.LENGTH_SHORT).show()
                        } else {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("app-log", text))
                            Toast.makeText(context, "已复制 $lineCount 条日志", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                BgGhostButton(
                    text = "清空日志",
                    onClick = {
                        LogStore.clear()
                        refresh()
                        Toast.makeText(context, "日志已清空", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    color = c.accent,
                )
            }

            Spacer(Modifier.height(18.dp))

            BgSectionLabel("日志内容", accent = if (lineCount > 0) "$lineCount 条" else null)

            Spacer(Modifier.height(10.dp))

            // 日志预览
            val text = remember(enabled, lineCount) { LogStore.dump() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.raise)
                    .padding(12.dp)
            ) {
                if (text.isBlank()) {
                    Text(
                        text = "暂无日志。开启开关后，解析与下载的关键步骤会记录在这里，\n用于排查偶发失败。",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 18.sp,
                        color = c.ink3,
                    )
                } else {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                        color = c.ink2,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
    }
}

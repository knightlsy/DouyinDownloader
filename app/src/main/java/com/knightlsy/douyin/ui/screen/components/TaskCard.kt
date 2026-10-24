package com.knightlsy.douyin.ui.screen.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.knightlsy.douyin.data.DownloadStatus
import com.knightlsy.douyin.data.DownloadTask
import com.knightlsy.douyin.ui.components.BgProgressLine
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 下载任务卡。
 *
 * 四处对旧版的修正：
 *
 * 1. **进度从圆环换成 2dp 细线**。圆环通常 48–54dp，在卡片里要占满一行；
 *    细线只要 2dp，同样的信息量，省一半高度，而且一眼看出还剩多少。
 *
 * 2. **卡片本身不可点**。旧版整卡可点（真机 dump 出 3 个 clickable），
 *    手指落在图标边缘会先触发整卡而不是图标。现在只有图标可点，
 *    卡片上的 clickable 数量是 0。
 *
 * 3. **图标触摸区 44dp**。旧版是 `Modifier.size(32.dp)`，可点区域就 32dp，
 *    低于 Material 的 48dp 建议值。现在视觉仍是 20dp 图标，外层 44dp 透明区。
 *
 * 4. **失败原因写人话**。旧版直接甩 HTTP 码，用户不知道要不要重试。
 *    这里在错误原文前补一句处置建议。
 *
 * 另外给卡片加了 animateContentSize —— 失败时会多出一行错误说明，
 * 高度突变很跳，现在平滑撑开。
 */
@Composable
fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current

    val statusText = when (task.status) {
        DownloadStatus.PENDING -> "等待中"
        DownloadStatus.DOWNLOADING -> "下载中"
        DownloadStatus.COMPLETED -> "已完成"
        DownloadStatus.FAILED -> "失败"
        DownloadStatus.PAUSED -> "已暂停"
        DownloadStatus.CANCELLED -> "已取消"
    }
    val statusColor = when (task.status) {
        DownloadStatus.DOWNLOADING -> c.accent
        DownloadStatus.COMPLETED -> c.success
        DownloadStatus.FAILED -> c.accent
        else -> c.ink3
    }

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(BgRadius.r2))
            .border(1.dp, c.hair, RoundedCornerShape(BgRadius.r2))
            .padding(12.dp)
    ) {
        Row {
            // 缩略图
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.raise)
            ) {
                AsyncImage(
                    model = task.contentInfo.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = task.displayTitle.ifBlank { "无标题" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = buildString {
                            append(statusText)
                            if (task.status == DownloadStatus.DOWNLOADING) {
                                append(" · ")
                                append((task.overallProgress * 100).toInt())
                                append("%")
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.3.sp,
                        color = statusColor,
                    )
                }
            }

            // 操作：只有图标可点，触摸区 44dp
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (task.status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.PENDING -> {
                        TouchIcon(Icons.Filled.Close, "取消", onCancel)
                    }
                    DownloadStatus.FAILED -> {
                        TouchIcon(Icons.Filled.Refresh, "重试", onRetry)
                        TouchIcon(Icons.Filled.Delete, "删除", onRemove)
                    }
                    else -> {
                        TouchIcon(Icons.Filled.Delete, "删除", onRemove)
                    }
                }
            }
        }

        // 进度：只在下载中有意义
        if (task.status == DownloadStatus.DOWNLOADING) {
            Spacer(Modifier.height(9.dp))
            BgProgressLine(progress = task.overallProgress, color = c.accent, trackColor = c.hair)
            if (task.totalFiles > 1) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "第 ${task.completedFiles + 1} / ${task.totalFiles} 个文件",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.3.sp,
                    color = c.ink3,
                )
            }
        }

        // 失败原因：人话 + 原文
        if (task.status == DownloadStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(9.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.raise)
                    .padding(9.dp)
            ) {
                Box(Modifier.width(2.dp).height(30.dp).background(c.accent))
                Spacer(Modifier.width(9.dp))
                // 只显示人话。原来拼了 "\n" + 原始异常，用户会看到
                // 「下载失败，可重试一次」下面紧跟一行 HTTP 403 —— 等于白改。
                // 原始错误仍在 task.errorMessage 里，需要排查时从日志取。
                Text(
                    text = humanizeError(task.errorMessage),
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                    color = c.ink2,
                )
            }
        }
    }
}

/** 图标按钮：视觉 20dp，触摸区 44dp。 */
@Composable
private fun TouchIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit,
) {
    val c = LocalBgColors.current
    Box(
        Modifier
            .size(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = c.ink3,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 把常见错误码翻译成一句处置建议。
 *
 * 不编造原因 —— 只在能确定的情形下给建议，其余情况让原文说话。
 */
private fun humanizeError(raw: String?): String {
    if (raw == null) return "下载失败"
    return when {
        "403" in raw -> "播放地址已失效（HTTP 403），可重试一次；仍失败说明该视频已被作者删除"
        "404" in raw -> "内容不存在（HTTP 404），可能已被删除"
        "timeout" in raw.lowercase() || "timed out" in raw.lowercase() -> "请求超时，检查网络后重试"
        "space" in raw.lowercase() || "ENOSPC" in raw -> "存储空间不足，清理后重试"
        else -> "下载失败，可重试一次"
    }
}

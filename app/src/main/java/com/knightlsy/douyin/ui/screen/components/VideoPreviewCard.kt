package com.knightlsy.douyin.ui.screen.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.ui.formatCount
import com.knightlsy.douyin.ui.formatDuration
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 视频预览卡。
 *
 * 骨架来自 A（Cinema）：封面占最大视觉权重，信息压在下面，UI 不抢戏。
 *
 * 三处对旧版的修正：
 *
 * 1. **封面用固定的 236dp 高度 + 明确底色**。旧版封面偶发不渲染 —— 深色底
 *    完全消失，只剩底下的按钮。给 Box 一个确定的背景色、AsyncImage 铺满，
 *    即使图挂了也是一块可见的占位，不会整个盒子塌掉。
 *
 * 2. **播放按钮换成真正的 PlayArrow**。旧版用的是 `ic_cloud_download`
 *    （一朵云 + 下箭头），放在封面中央看着像「下载」而不是「播放」。
 *
 * 3. **播放按钮自己的点击区独立**。旧版整张封面不可点、按钮也不是独立可点元素，
 *    结果「点封面没反应」。现在封面本身可点（打开播放器），中央按钮只是视觉提示。
 */
@Composable
fun VideoPreviewCard(
    video: ContentInfo.Video,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current

    Column(modifier.fillMaxWidth().padding(horizontal = BgSpace.gutter)) {
        // ── 封面 ──
        Box(
            Modifier
                .fillMaxWidth()
                .height(236.dp)
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.raise)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onPlay,
                )
        ) {
            AsyncImage(
                model = video.coverUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 左上角：分辨率
            if (video.width > 0 && video.height > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(BgRadius.r1))
                        .background(c.accent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${video.height}p",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        letterSpacing = 0.5.sp,
                        color = c.accentInk,
                    )
                }
            }

            // 左下角：时长
            if (video.duration > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(9.dp)
                        .clip(RoundedCornerShape(BgRadius.r1))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = Color.White,
                    )
                }
            }

            // 中央播放键：视觉提示，点击由外层 Box 接管
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.38f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.88f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(Modifier.height(11.dp))

        // ── 标题 ──
        Text(
            text = video.title.ifBlank { "无标题" },
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink,
            maxLines = 2,
        )

        Spacer(Modifier.height(7.dp))

        // ── 元信息一行 ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MonoMeta("@${video.author.ifBlank { "未知作者" }}")
            if (video.diggCount > 0) MonoMeta("♥ ${formatCount(video.diggCount)}")
            if (video.commentCount > 0) MonoMeta("💬 ${formatCount(video.commentCount)}")
        }

        Spacer(Modifier.height(13.dp))

        // ── 下载 ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDownload,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "↓  下载视频",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    letterSpacing = 1.4.sp,
                    color = c.accentInk,
                )
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .border(1.dp, c.hair, RoundedCornerShape(BgRadius.r2))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onPlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "预览",
                    tint = c.ink2,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun MonoMeta(text: String) {
    val c = LocalBgColors.current
    Text(
        text = text,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.5.sp,
        letterSpacing = 0.2.sp,
        color = c.ink3,
        maxLines = 1,
    )
}

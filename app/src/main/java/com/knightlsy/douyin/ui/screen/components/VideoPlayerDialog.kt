package com.knightlsy.douyin.ui.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.buttons.SeekBackButton
import androidx.media3.ui.compose.material3.buttons.SeekForwardButton
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.material3.indicator.RemainingDurationText
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 全屏播放器。
 *
 * 两个关键修正：
 *
 * 1. **控制层常驻**。
 *    media3 的 `Player` 把控制层显隐交给内部状态，通过 `centerControls` /
 *    `bottomControls` 的第二个参数 `visible` 传下来。默认状态是「隐」——
 *    要点击画面才出现。用户第一次打开只看到一块画面，什么按钮都没有，
 *    会以为播放器坏了。
 *
 *    这里不再透传 `visible`，直接写死 true：控制层常驻。
 *    代价是画面被按钮挡一点点，但「看得见怎么操作」远比「纯净画面」重要。
 *
 *    顺带说明：网上流传的「设 showControls = true」在这个版本里没有这个参数，
 *    真正的开关就是这两个 lambda 的第二参数。
 *
 * 2. **release 只调一次**。
 *    旧版在 `onDispose` 和关闭按钮里各调了一次 `player.release()`。
 *    ExoPlayer 的 release 是幂等的不会崩，但这种「两处都管生命周期」的写法
 *    一旦将来有人加了暂停恢复逻辑就会踩空。现在统一交给 DisposableEffect。
 *
 * 3. **加了「下载此视频」**。
 *    旧版只能看不能下，想下载得关掉回首页再点一次。这里直接用当前播放地址建任务。
 */
@Composable
fun VideoPlayerDialog(
    videoUrl: String,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalBgColors.current
    val context = LocalContext.current

    val exoPlayer = remember(videoUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    // 唯一的释放点
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding()
        ) {
            Player(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                centerControls = { p, _ ->
                    // 注意第二参数被丢弃 —— 控制层常驻
                    PlayerDefaults.CenterControls(
                        player = p,
                        visible = true,
                        modifier = Modifier.fillMaxWidth(),
                        back = { SeekBackButton(p, Modifier.size(44.dp), tint = Color.White) },
                        central = { pp -> PlayPauseButton(pp, Modifier.size(64.dp), tint = Color.White) },
                        forward = { SeekForwardButton(p, Modifier.size(44.dp), tint = Color.White) },
                    )
                },
                bottomControls = { p, _ ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        ProgressSlider(
                            player = p,
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = c.accent,
                                activeTrackColor = c.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            ),
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            PositionText(p, Modifier, color = Color.White.copy(alpha = 0.8f))
                            RemainingDurationText(p, Modifier, color = Color.White.copy(alpha = 0.8f))
                        }
                    }
                },
            )

            // 左上：关闭
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(40.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            // 右上：下载当前视频
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.accent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onDownload(videoUrl) },
                    )
                    .padding(horizontal = 13.dp, vertical = 9.dp)
            ) {
                Text(
                    text = "↓  下载此视频",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    letterSpacing = 1.0.sp,
                    color = c.accentInk,
                )
            }
        }
    }
}

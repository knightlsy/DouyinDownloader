package com.knightlsy.douyin.ui.screen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.material3.Player
import androidx.media3.ui.compose.material3.PlayerDefaults
import androidx.media3.ui.compose.material3.buttons.PlayPauseButton
import androidx.media3.ui.compose.material3.indicator.PositionText
import androidx.media3.ui.compose.material3.indicator.ProgressSlider
import androidx.media3.ui.compose.material3.indicator.RemainingDurationText
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors
import kotlinx.coroutines.delay

/** 与 VideoRepository.baseHeaders 的 UA 保持一致，避免播放/下载两个链路口径不一。 */
private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 14; Xiaomi 14 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

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
        // ── 起播优化 ──
        // DefaultLoadControl 默认要缓冲 2500ms 才开播、卡顿后要 5000ms 才恢复。
        // 对抖音这种 15~60 秒的短视频太保守：网络起来的那半秒其实已经够播了。
        // 这里把起播门槛压到 500ms、卡顿恢复压到 1000ms。
        // 权衡：极弱网下可能刚播就转一下圈，但 1 秒就能恢复，比空屏等 2.5 秒好。
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_000,   // minBufferMs
                15_000,  // maxBufferMs
                500,     // bufferForPlaybackMs —— 起播门槛（默认 2500）
                1_000,   // bufferForPlaybackAfterRebufferMs —— 卡顿恢复（默认 5000）
            )
            .build()

        // ── 取流优化 ──
        // 抖音 CDN 按 Referer 判断来源，不带时偶发限速。下载链路早就补了这个头，
        // 播放链路一直漏着 —— 这正是「同一个视频下载很快、预览却慢」的直接原因。
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(PLAYER_UA)
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.douyin.com/"))
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
            .setLoadControl(loadControl)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
            }
    }

    // 唯一的释放点
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // ── 控制层显隐（必须自己管）──
    // media3 的 Player 只是个布局容器：showControls 默认 false，且它自己不维护状态，
    // 只把值透传给 top/center/bottom 三个 slot（见 1.11.1 源码 PlayerImpl）。
    // 于是只有两种坏结果：
    //   · 写死 true（旧版）→ 三颗按钮永远压在画面正中，全程挡内容
    //   · 直接透传 false   → 永远看不见按钮，用户以为播放器坏了
    // 正确做法是调用方维护「播放中 3 秒自动隐藏 / 暂停立即显示 / 点画面唤出」。
    var isPlaying by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            delay(3_000)
            controlsVisible = false
        } else {
            // 暂停 / 缓冲 / 播完时重新显示 —— 这几个时刻用户往往正要操作
            controlsVisible = true
        }
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
                .pointerInput(Unit) {
                    // 点画面切换控制层显隐。
                    // 用 pointerInput 而不是铺一层 clickable：按钮自己会消费点击事件，
                    // Compose 不会再把事件冒泡到父级，所以点播放键不会顺带把控制层收起；
                    // 点空白处才落到这里触发 toggle。
                    detectTapGestures { controlsVisible = !controlsVisible }
                }
        ) {
            Player(
                player = exoPlayer,
                showControls = controlsVisible,
                modifier = Modifier.fillMaxSize(),
                centerControls = { p, show ->
                    // 只留播放/暂停。
                    // CenterControls 的五个 slot 全都带默认按钮
                    // （PreviousButton / SeekBackButton / PlayPauseButton / SeekForwardButton / NextButton），
                    // 不传就会把默认按钮画出来，所以其余四个必须显式给空 lambda。
                    // 它内部已有 AnimatedVisibility，这里不用再包动画。
                    PlayerDefaults.CenterControls(
                        player = p,
                        visible = show,
                        modifier = Modifier.fillMaxWidth(),
                        backSecondary = {},
                        back = {},
                        central = { pp -> PlayPauseButton(pp, Modifier.size(64.dp), tint = Color.White) },
                        forward = {},
                        forwardSecondary = {},
                    )
                },
                bottomControls = { p, show ->
                    // 底部进度条跟着一起显隐。加淡入淡出，避免"啪"地消失显得生硬。
                    AnimatedVisibility(
                        visible = show,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
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
                    }
                },
            )

            // 左上：关闭（始终显示 —— 它是唯一退出路径，
            // 且不挡画面正中，所以不跟控制层一起淡出）
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

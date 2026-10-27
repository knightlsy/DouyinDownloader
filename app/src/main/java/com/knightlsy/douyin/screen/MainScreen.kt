package com.knightlsy.douyin.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgMonoLabel
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.components.BgWarn
import com.knightlsy.douyin.ui.screen.components.CollectionPreviewCard
import com.knightlsy.douyin.ui.screen.components.ImageViewerDialog
import com.knightlsy.douyin.ui.screen.components.LinkInputCard
import com.knightlsy.douyin.ui.screen.components.StatsBar
import com.knightlsy.douyin.ui.screen.components.TaskCard
import com.knightlsy.douyin.ui.screen.components.VideoPlayerDialog
import com.knightlsy.douyin.ui.screen.components.VideoPreviewCard
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import com.knightlsy.douyin.viewmodel.MainViewModel
import com.knightlsy.douyin.data.DownloadStatus

/**
 * 主页。
 *
 * 与旧版的结构差异：
 *
 *  1. 去掉 Scaffold + 渐变 TopAppBar。顶栏改成「等宽标记 + 三个文字按钮」，
 *     高度从 64dp 降到 46dp，省下来的空间给内容。
 *  2. 统计从 2×2 磁贴卡压成三格参数带（StatsBar），高度减半。
 *  3. 内容区仍然是一个 LazyColumn，但每个区块都拆成了独立文件，
 *     这个文件只负责编排 —— 旧版 1310 行全部塞在这里，改一处要滚半天。
 *  4. 预览出现用 AnimatedVisibility 做淡入 + 上移，不再突然撑开页面。
 *
 * 交互上的修正都落在子组件里（输入框收键盘、播放器控制层常驻、
 * 任务卡触摸区 44dp、去掉整卡可点），这里不重复。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val c = LocalBgColors.current
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // 播放中的视频地址（null = 没在播）
    var playingUrl by remember { mutableStateOf<String?>(null) }
    // 看图：图片列表 + 起始序号（null = 没在看）
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    // 自动填链接改由 MainActivity.onResume() 触发 —— 不能只用 LaunchedEffect(Unit)：
    // 核心路径是「抖音里复制 → 切回本应用」，此时 App 只是从后台恢复，
    // composition 不重建，LaunchedEffect(Unit) 早就跑完了，剪贴板不会被重读。
    // （放在 Activity 里而不是这里，是因为 lifecycle-runtime-compose 只有 2.6.2，
    //   androidx.lifecycle.compose.LocalLifecycleOwner 要 2.8+ 才有。）

    // 成功提示
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearSuccessMessage()
        }
    }
    // 错误提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let { raw ->
            // 错误文本可能是「人话 + 空行 + 诊断信息」。Toast 最多显示几秒，
            // 塞多行技术细节等于没提示。只取第一段；完整诊断在失败时
            // 已经自动复制进剪贴板了。
            val human = raw.substringBefore("\n\n").ifBlank { raw }
            Toast.makeText(context, human, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .systemBarsPadding()
    ) {

        /* ── 顶栏 ── */
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
                text = "抖音下载器",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.8.sp,
                color = c.ink,
            )
            Spacer(Modifier.weight(1f))
            TopAction("历史", onNavigateToHistory)
            TopAction("设置", onNavigateToSettings)
        }

        BgHair()

        /* ── 内容 ── */
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 18.dp, bottom = 40.dp),
        ) {

            // 统计
            item { StatsBar(stats) }

            // 链接输入
            item {
                Spacer(Modifier.height(20.dp))
                LinkInputCard(
                    url = uiState.inputUrl,
                    isLoading = uiState.isLoading,
                    onUrlChange = viewModel::onUrlChanged,
                    onPaste = viewModel::pasteFromClipboard,
                    onParse = viewModel::parseAndPreview,
                )
            }

            // 预览
            item {
                AnimatedVisibility(
                    visible = uiState.contentInfo != null,
                    enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 8 },
                    exit = fadeOut(tween(160)),
                ) {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        when (val info = uiState.contentInfo) {
                            is ContentInfo.Video -> VideoPreviewCard(
                                video = info,
                                onPlay = {
                                    // web 接口给的 videoUrl 有时为空，退回候选列表第一个
                                    val url = info.videoUrl.ifBlank {
                                        info.candidateUrls.firstOrNull().orEmpty()
                                    }
                                    if (url.isNotBlank()) {
                                        playingUrl = url
                                    } else {
                                        // 原来这里静默返回，用户点「播放」毫无反应，
                                        // 以为按钮坏了。明确告知还能直接下载。
                                        Toast.makeText(
                                            context,
                                            "暂无可用播放地址，可直接下载后观看",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                onDownload = { viewModel.startDownload() },
                            )

                            is ContentInfo.ImageCollection -> CollectionPreviewCard(
                                collection = info,
                                onDownloadSelected = { urls ->
                                    viewModel.startDownloadSelected(urls)
                                },
                                onImageClick = { idx -> viewer = info.imageUrls to idx },
                            )

                            else -> Unit
                        }
                    }
                }
            }

            // 任务列表
            if (tasks.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(26.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = BgSpace.gutter, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BgSectionLabel(
                            text = "下载任务",
                            modifier = Modifier.weight(1f),
                            accent = "· ${tasks.size}",
                        )
                        if (tasks.any { it.status == DownloadStatus.COMPLETED }) {
                            BgMonoLabel(
                                text = "清空已完成",
                                color = c.ink3,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { viewModel.clearCompletedTasks() },
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                items(tasks, key = { it.id }) { task ->
                    Column(Modifier.padding(horizontal = BgSpace.gutter, vertical = 4.dp)) {
                        TaskCard(
                            task = task,
                            onCancel = { viewModel.cancelDownload(task.id) },
                            onRetry = { viewModel.retryDownload(task.id) },
                            onRemove = { viewModel.removeTask(task.id) },
                        )
                    }
                }
            }

            // 空态提示
            if (uiState.contentInfo == null && tasks.isEmpty() && !uiState.isLoading) {
                item {
                    Spacer(Modifier.height(30.dp))
                    Column(Modifier.padding(horizontal = BgSpace.gutter)) {
                        BgWarn(
                            title = "怎么用",
                            body = "在抖音里点分享 → 复制链接 → 回到这里会自动填入 → 点解析。\n" +
                                "视频下载到相册；图集可以逐张挑选，实况图会连同动态一起保存。",
                            critical = false,
                        )
                    }
                }
            }
        }
    }

    /* ── 播放器 ── */
    playingUrl?.let { url ->
        VideoPlayerDialog(
            videoUrl = url,
            onDownload = { downloadUrl ->
                // 用正在播放的那个地址直接建任务。
                // 注意不能调 downloadFromUrl() —— 那个是历史页「重新解析」用的，
                // 它会把 inputUrl 覆盖成视频直链再重新解析，而直链不是分享链接，
                // 解析必然失败，用户看到的就是「输入框被换成了一个下不了的链接」。
                playingUrl = null
                viewModel.startDownloadWithUrl(downloadUrl)
            },
            onDismiss = { playingUrl = null },
        )
    }

    /* ── 看图 ── */
    viewer?.let { (urls, idx) ->
        ImageViewerDialog(
            urls = urls,
            initialIndex = idx,
            onDismiss = { viewer = null },
        )
    }
}

/**
 * 顶栏文字按钮。
 *
 * 用文字而不是图标：A 的语言是「窄体大写 + 等宽」，一排放三个图标会和
 * 整体气质打架。文字按钮的可点区通过 padding 撑到 44dp 高。
 */
@Composable
private fun TopAction(text: String, onClick: () -> Unit) {
    val c = LocalBgColors.current
    Box(
        Modifier
            .height(44.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            color = c.ink2,
        )
    }
}

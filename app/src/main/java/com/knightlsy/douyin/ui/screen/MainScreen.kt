package com.knightlsy.douyin.ui.screen

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.data.HistoryRepository
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.ui.components.BgMonoLabel
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.screen.components.CollectionPreviewCard
import com.knightlsy.douyin.ui.screen.components.ImageSelectionSheet
import com.knightlsy.douyin.ui.screen.components.ImageViewerDialog
import com.knightlsy.douyin.ui.screen.components.LinkInputCard
import com.knightlsy.douyin.ui.screen.components.TaskCard
import com.knightlsy.douyin.ui.screen.components.VideoPlayerDialog
import com.knightlsy.douyin.ui.screen.components.VideoPreviewCard
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import com.knightlsy.douyin.viewmodel.DownloadStats
import com.knightlsy.douyin.viewmodel.MainViewModel
import com.knightlsy.douyin.data.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主页。
 *
 * 本轮（底部导航重构）的结构变化：
 *
 *  1. 顶栏去掉「历史」「设置」两个文字按钮 —— 一级入口收进 Tab 栏
 *     （Tab 栏由 AppNavigation 渲染，位置在内容动画之外，转场时保持静止），
 *     顶栏只留身份信息：6dp 圆点 +
 *     应用名 + 副标题，并改成玻璃悬浮条（16dp 圆角 + 白 4.2% 底 + 1dp 白 13% 边）。
 *  2. 信息顺序按「打开 App 是为了粘贴链接」重排：链接解析 → 预览 →
 *     空态/最近解析 → 任务 → 统计（弱化）。统计仍保留三个读数，只是
 *     从主角降为参考：位置移到任务之后，字号降一档、颜色统一 ink3。
 *  3. Tab 栏在顶栏下方、链接解析上方，是页面的一部分；底部不再需要让位。
 *
 * 其余交互修正都在子组件里，这里只负责编排。
 */
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    /**
     * 保留参数位：Tab 栏现在由 AppNavigation 统一渲染（在内容动画外层，转场时静止），
     * 本屏不再需要它。留空实现是为了不让外层调用点编译失败，将来若要
     * 在页面内部放跳转入口可以直接复用。
     */
    onSelectTab: (String) -> Unit = {},
) {
    // 顶栏和 Tab 栏都移到 AppNavigation 了，本屏不再有玻璃，也就不需要采样源。

    val listState = rememberLazyListState()
    val c = LocalBgColors.current
    val context = LocalContext.current


    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val tasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    // 播放中的视频地址（null = 没在播）
    var playingUrl by remember { mutableStateOf<String?>(null) }
    // 看图：图片列表 + 起始序号（null = 没在看）
    var viewer by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    /**
     * 正在选图的图集。null = 没在选。
     *
     * 浮层放在这一层而不是 CollectionPreviewCard 内部：那张卡是 LazyColumn 的
     * item，item 被回收（滚动、数据刷新）时内部的一切都会被销毁 ——
     * 全屏 Dialog 寄生在那里会跟着消失。
     */
    var pickingCollection by remember { mutableStateOf<ContentInfo.ImageCollection?>(null) }

    // 空状态填充：无解析内容时，首次显示引导、有历史则显示最近解析
    var recentHistory by remember { mutableStateOf<List<DownloadHistoryItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        recentHistory = withContext(Dispatchers.IO) {
            runCatching { HistoryRepository(context).getAll() }
                .getOrDefault(emptyList())
                .sortedByDescending { it.downloadTime }
                .take(3)
        }
    }

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

    // 根容器改用 Box：顶栏要从「内容源」里挪出来做玻璃，
    // 玻璃不能是源的子节点，否则采样的就是它自己（会糊成灰块）。
    Box(
        Modifier
            .fillMaxSize()
            // 不铺纸色：Theme 已经铺过底，这里再铺一层会把壁纸层整个盖住
            // （这就是「背景功能开了没效果」的根因 —— 跟壁纸读不到无关）
            // 液态玻璃的「可透内容」：两团无边界柔光。
            // 没有这层，上面的玻璃卡片就是一块灰膜 —— 玻璃能不能透，
            // 取决于背后有没有东西，而不取决于模糊。
            .drawBehind {
                val r1 = size.minDimension * 0.85f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c.accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.08f),
                        radius = r1,
                    ),
                    radius = r1,
                    center = Offset(size.width * 0.18f, size.height * 0.08f),
                )
                val r2 = size.minDimension * 0.75f
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(c.accent.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.72f),
                        radius = r2,
                    ),
                    radius = r2,
                    center = Offset(size.width * 0.92f, size.height * 0.72f),
                )
            }
    ) {

        /* ── 内容 ── */
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            // 顶栏和 Tab 栏都在 AppNavigation 里，占的是布局空间，本屏内容
            // 从它们下面开始即可 —— 只留 12dp 呼吸空间。bottom 24dp 避手势条。
            contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        ) {

            // 链接输入 —— 唯一主角，紧跟顶栏
            item {
                BgSectionLabel(
                    text = "链接解析",
                    modifier = Modifier.padding(horizontal = BgSpace.gutter),
                )
                Spacer(Modifier.height(10.dp))
                LinkInputCard(
                    url = uiState.inputUrl,
                    isLoading = uiState.isLoading,
                    onUrlChange = viewModel::onUrlChanged,
                    onPaste = viewModel::pasteFromClipboard,
                    onParse = viewModel::parseAndPreview,
                    onCancel = viewModel::cancelParse,
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
                                onRequestPick = { pickingCollection = it },
                            )

                            else -> Unit
                        }
                    }
                }
            }

            // 空状态填充：首次引导 / 最近解析
            if (uiState.contentInfo == null && tasks.isEmpty()) {
                item {
                    Spacer(Modifier.height(20.dp))
                    if (recentHistory.isEmpty()) {
                        OnboardingGuideCard()
                    } else {
                        RecentParsesCard(
                            items = recentHistory,
                            onReparse = { item -> viewModel.downloadFromUrl(item.originalUrl) },
                        )
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

            // 统计（弱化）：从页面最上方移到任务之后，三个读数保留，
            // 但字号降一档、颜色统一 ink3 —— 读数仍在，只是不再是开场白。
            item {
                Spacer(Modifier.height(30.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = BgSpace.gutter)
                        .height(1.dp)
                        .background(c.hair)
                )
                Spacer(Modifier.height(16.dp))
                SubduedStats(stats)
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

    // 图集选图浮层。和上面的看图 Dialog 一样放在函数末尾 ——
    // 只有在这一层，列表滚动和数据刷新才不会把它连带销毁。
    pickingCollection?.let { target ->
        androidx.compose.runtime.LaunchedEffect(target.id) {
            android.util.Log.i("ImagePicker", "渲染选图浮层：${target.imageUrls.size} 张")
        }
        ImageSelectionSheet(
            collection = target,
            initialSelection = target.imageUrls.indices.toSet(),
            onDismiss = { pickingCollection = null },
            onConfirm = { urls ->
                pickingCollection = null
                viewModel.startDownloadSelected(urls)
            },
        )
    }

}

/**
 * 弱化版三格统计条。
 *
 * 与 StatsBar.kt 的区别：那个组件按「仪表盘读数」设计（displayMedium 大数字、
 * 成功绿/失败橙双编码），是旧首页的开场白。统计移到任务之后降为参考信息后，
 * 这里改用小一号的等宽数字 + 统一 ink3，避免在页面末尾又抢回注意力。
 * StatsBar.kt 组件本身不动，留给其他调用方。
 */
@Composable
private fun SubduedStats(stats: DownloadStats, modifier: Modifier = Modifier) {
    val c = LocalBgColors.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BgSpace.gutter)
    ) {
        // 标签原本写「今日」，但 stats 只在内存里、重启即归零，
        // 叫「本次」才不骗人。
        SubduedStatCell("本次", stats.totalDownloads.toString(), Modifier.weight(1f))
        SubduedStatCell("成功", stats.successDownloads.toString(), Modifier.weight(1f))
        SubduedStatCell("失败", stats.failedDownloads.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun SubduedStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    Column(modifier) {
        Text(
            text = label.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 8.sp,
            letterSpacing = 1.4.sp,
            color = c.ink3,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            color = c.ink3,
        )
    }
}

/**
 * 首次使用引导卡：无解析历史时填在输入框下方。
 * 三步说明让用户知道核心用法是「抖音复制 → 切回本 App 自动识别」。
 */
@Composable
private fun OnboardingGuideCard() {
    val c = LocalBgColors.current
    Column {
        BgSectionLabel(
            "开始使用",
            modifier = Modifier.padding(horizontal = BgSpace.gutter),
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BgSpace.gutter)
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.glass)
                .border(1.dp, c.glassEdge, RoundedCornerShape(BgRadius.r2))
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            GuideStep("01", "复制抖音分享链接", "在抖音里点分享 → 复制链接")
            Spacer(Modifier.height(12.dp))
            GuideStep("02", "切回本 App", "链接会自动识别，无需手动粘贴")
            Spacer(Modifier.height(12.dp))
            GuideStep("03", "点「解析」下载", "视频、图集、实况图都能保存")
        }
    }
}

@Composable
private fun GuideStep(index: String, title: String, desc: String) {
    val c = LocalBgColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = index,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = c.accent,
            letterSpacing = 0.5.sp,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = c.ink,
            )
            Text(
                text = desc,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp,
                color = c.ink3,
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * 最近解析卡：有历史但当前无解析内容时显示最近 3 条，
 * 点击直接重新解析那条链接。
 */
@Composable
private fun RecentParsesCard(
    items: List<DownloadHistoryItem>,
    onReparse: (DownloadHistoryItem) -> Unit,
) {
    val c = LocalBgColors.current
    val fmt = remember { java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()) }
    Column {
        BgSectionLabel(
            "最近解析",
            accent = "RECENT · 点击重新解析",
            modifier = Modifier.padding(horizontal = BgSpace.gutter),
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BgSpace.gutter)
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.glass)
                .border(1.dp, c.glassEdge, RoundedCornerShape(BgRadius.r2))
        ) {
            items.forEachIndexed { i, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onReparse(item) },
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title.ifBlank { "(无标题)" },
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            color = c.ink,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (item.contentType == ContentType.VIDEO) "视频" else "图集",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.5.sp,
                            color = c.ink3,
                        )
                    }
                    Text(
                        text = fmt.format(java.util.Date(item.downloadTime)),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
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
                if (i < items.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp)
                            .height(1.dp)
                            .background(c.hair)
                    )
                }
            }
        }
    }
}

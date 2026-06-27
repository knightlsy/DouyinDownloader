@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)

package com.knightlsy.douyin.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.knightlsy.douyin.R
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadStatus
import com.knightlsy.douyin.data.DownloadTask
import com.knightlsy.douyin.ui.theme.DouyinCyan
import com.knightlsy.douyin.ui.theme.DouyinPink
import com.knightlsy.douyin.viewmodel.MainViewModel

private val inputUrlPatterns = listOf(
    Regex("https?://v\\.douyin\\.com/[\\w-]+/?"),
    Regex("https?://vm\\.tiktok\\.com/[\\w-]+/?"),
    Regex("https?://www\\.douyin\\.com/(video|note|slides)/\\d+"),
    Regex("https?://www\\.iesdouyin\\.com/share/[\\w]+/\\d+"),
    Regex("modal_id=\\d+"),
    Regex("aweme_id=\\d+")
)

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadTasks by viewModel.downloadTasks.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val context = LocalContext.current

    val activeTaskCount = remember(downloadTasks) { downloadTasks.count { it.status == DownloadStatus.DOWNLOADING } }

    LaunchedEffect(Unit) { viewModel.autoPasteFromClipboard() }
    LaunchedEffect(uiState.successMessage) { uiState.successMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearSuccessMessage() } }
    LaunchedEffect(uiState.error) { uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearError() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.combinedClickable(
                            onDoubleClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://daohang.knightlsy.cn"))
                                context.startActivity(intent)
                            },
                            onClick = {}
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(10.dp), color = Color.White, modifier = Modifier.size(38.dp)) {
                            Icon(painterResource(R.drawable.ic_douyin_download), null, Modifier.padding(5.dp), tint = Color.Unspecified)
                        }
                        Column {
                            Text("抖音下载器", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("无水印 · 图集下载", fontSize = 10.sp, color = Color.White.copy(alpha = 0.75f))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDarkMode) Color(0xFF1C1B1F) else DouyinPink,
                    titleContentColor = Color.White
                ),
                actions = {
                    IconButton(onClick = { viewModel.pasteFromClipboard() }) {
                        Icon(painterResource(R.drawable.ic_paste), "粘贴链接", tint = Color.White)
                    }
                    IconButton(onClick = { viewModel.toggleDarkMode() }) {
                        Icon(
                            if (isDarkMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            "切换主题", tint = Color.White
                        )
                    }
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(painterResource(R.drawable.ic_history), "历史记录", tint = Color.White)
                    }
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(Icons.Outlined.Info, "关于", tint = Color.White)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                StatsRow(
                    totalDownloads = stats.totalDownloads,
                    successDownloads = stats.successDownloads,
                    failedDownloads = stats.failedDownloads,
                    activeTasks = activeTaskCount
                )
            }

            item {
                InputCard(
                    url = uiState.inputUrl,
                    onUrlChange = viewModel::onUrlChanged,
                    onParse = viewModel::parseAndPreview,
                    isLoading = uiState.isLoading
                )
            }

            item {
                AnimatedVisibility(visible = uiState.contentInfo != null, enter = fadeIn() + slideInVertically(), exit = fadeOut()) {
                    uiState.contentInfo?.let { info ->
                        when (uiState.contentType) {
                            ContentType.VIDEO -> VideoPreviewCard(info as ContentInfo.Video, onDownload = { url -> viewModel.startDownloadWithUrl(url) })
                            ContentType.IMAGE_COLLECTION -> ImageCollectionPreviewCard(
                                info as ContentInfo.ImageCollection,
                                onDownloadSelected = { urls -> viewModel.startDownloadSelected(urls) },
                                onDownloadAll = viewModel::startDownload
                            )
                            null -> {}
                        }
                    }
                }
            }

            if (downloadTasks.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("下载任务 (${downloadTasks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { viewModel.clearAllTasks() }) { Text("清空全部", color = Color.Gray, fontSize = 13.sp) }
                    }
                }
                items(downloadTasks.reversed(), key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onRetry = { viewModel.retryDownload(task.id) },
                        onCancel = { viewModel.cancelDownload(task.id) },
                        onRemove = { viewModel.removeTask(task.id) }
                    )
                }
            }

            if (downloadTasks.isEmpty() && uiState.contentInfo == null) {
                item { TipsCard() }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun StatsRow(totalDownloads: Int, successDownloads: Int, failedDownloads: Int, activeTasks: Int) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("总下载", totalDownloads.toString(), DouyinPink, Icons.Outlined.ListAlt)
            StatItem("成功", successDownloads.toString(), Color(0xFF4CAF50), Icons.Outlined.Check)
            StatItem("失败", failedDownloads.toString(), Color(0xFFF44336), Icons.Outlined.Close)
            StatItem("进行中", activeTasks.toString(), Color(0xFFFF9800), Icons.Outlined.Sync)
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = color.copy(alpha = 0.6f))
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
fun TipsCard() {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                Text("使用提示", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            TipItem("1", "打开抖音，点击分享 → 复制链接")
            TipItem("2", "返回本 App，粘贴链接后点击解析")
            TipItem("3", "支持视频、图集无水印下载")
            TipItem("4", "支持从分享文案中自动提取有效链接")
        }
    }
}

@Composable
fun TipItem(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = DouyinPink.copy(alpha = 0.1f),
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DouyinPink)
            }
        }
        Text(text, fontSize = 13.sp, color = Color.DarkGray, lineHeight = 18.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
fun InputCard(url: String, onUrlChange: (String) -> Unit, onParse: () -> Unit, isLoading: Boolean) {
    var pasteCount by remember { mutableStateOf(0) }
    val previousUrl = remember { mutableStateOf(url) }

    LaunchedEffect(url) {
        if (url.isNotEmpty() && url != previousUrl.value && pasteCount > 0) {
            for (p in inputUrlPatterns) {
                val match = p.find(url)
                if (match != null && match.value != url) {
                    onUrlChange(match.value)
                    break
                }
            }
        }
        previousUrl.value = url
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Link, null, tint = DouyinPink, modifier = Modifier.size(20.dp))
                Text("粘贴视频链接", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            OutlinedTextField(
                value = url,
                onValueChange = { onUrlChange(it); pasteCount++ },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("粘贴任意内容，自动提取有效链接", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f), fontSize = 14.sp) },
                trailingIcon = {
                    if (url.isNotEmpty()) {
                        IconButton(onClick = { onUrlChange("") }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Filled.Close, "清除", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DouyinPink,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 15.sp)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { onUrlChange("") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                ) {
                    Icon(Icons.Outlined.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清空", fontSize = 13.sp)
                }
                Button(
                    onClick = onParse,
                    modifier = Modifier.weight(2f),
                    enabled = url.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DouyinPink),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (isLoading) "解析中..." else "解析链接", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun VideoPreviewCard(videoInfo: ContentInfo.Video, onDownload: (String) -> Unit) {
    var showPlayer by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf(videoInfo.qualities.firstOrNull()) }

    if (showPlayer) {
        VideoPlayerDialog(videoUrl = videoInfo.videoUrl, onDismiss = { showPlayer = false })
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF0a0a1a))) {
                if (videoInfo.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(videoInfo.coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    Modifier.align(Alignment.Center)
                        .size(60.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .clickable { showPlayer = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cloud_download),
                        null,
                        Modifier.size(28.dp),
                        tint = Color.White
                    )
                }
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Videocam, null, Modifier.size(12.dp), tint = Color.White)
                        Text("视频", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(8.dp), color = DouyinCyan) {
                    Text("无水印", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (videoInfo.duration > 0) {
                    Surface(Modifier.align(Alignment.BottomEnd).padding(10.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.65f)) {
                        Text(formatDuration(videoInfo.duration), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp)
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(videoInfo.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = Color.Gray)
                    Text("@${videoInfo.author}", fontSize = 13.sp, color = Color.Gray)
                }
                if (videoInfo.diggCount > 0 || videoInfo.commentCount > 0 || videoInfo.shareCount > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        StatChip(Icons.Outlined.ThumbUpOffAlt, formatCount(videoInfo.diggCount))
                        StatChip(Icons.Outlined.ChatBubbleOutline, formatCount(videoInfo.commentCount))
                        StatChip(Icons.Outlined.Share, formatCount(videoInfo.shareCount))
                    }
                }
                if (videoInfo.width > 0 && videoInfo.height > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.HighQuality, null, Modifier.size(14.dp), tint = Color.Gray)
                        Text("${videoInfo.width}x${videoInfo.height}", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                if (videoInfo.qualities.size > 1) {
                    Box {
                        OutlinedButton(
                            onClick = { expanded = true },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Outlined.VideoSettings, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(selectedQuality?.label ?: "选择画质", fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            videoInfo.qualities.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q.label) },
                                    onClick = { selectedQuality = q; expanded = false },
                                    leadingIcon = { if (q == selectedQuality) Icon(Icons.Outlined.Check, null, tint = DouyinPink) }
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onDownload(selectedQuality?.url ?: videoInfo.videoUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(painterResource(R.drawable.ic_cloud_download), null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("下载无水印视频", fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun VideoPlayerDialog(videoUrl: String, onDismiss: () -> Unit) {
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val exoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
        player = exoPlayer
        onDispose {
            exoPlayer.release()
            player = null
        }
    }

    Dialog(
        onDismissRequest = {
            player?.release()
            player = null
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            player?.let { exoPlayer ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = true
                            this.player = exoPlayer
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            IconButton(
                onClick = {
                    player?.release()
                    player = null
                    onDismiss()
                },
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
            ) {
                Icon(Icons.Outlined.Close, "关闭", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun ImageCollectionPreviewCard(
    imageCollection: ContentInfo.ImageCollection,
    onDownloadSelected: (List<String>) -> Unit,
    onDownloadAll: () -> Unit
) {
    val selectedUrls = remember { mutableStateListOf<String>() }
    val pagerState = rememberPagerState(pageCount = { imageCollection.imageUrls.size })
    var showImageViewer by remember { mutableStateOf(false) }
    var viewerIndex by remember { mutableStateOf(0) }

    if (showImageViewer) {
        ImageViewerDialog(
            images = imageCollection.imageUrls,
            initialPage = viewerIndex,
            onDismiss = { showImageViewer = false }
        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(380.dp).background(Color(0xFF0a0a1a))) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { imageCollection.imageUrls[it] }) { page ->
                    Box(
                        Modifier.fillMaxSize()
                            .clickable { viewerIndex = page; showImageViewer = true }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageCollection.imageUrls[page])
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) {
                    val currentUrl = imageCollection.imageUrls[pagerState.currentPage]
                    val isSelected = currentUrl in selectedUrls
                    Surface(
                        modifier = Modifier.size(40.dp).clickable {
                            if (isSelected) selectedUrls.remove(currentUrl) else selectedUrls.add(currentUrl)
                        },
                        shape = CircleShape,
                        color = if (isSelected) DouyinPink else Color.Black.copy(alpha = 0.5f),
                        shadowElevation = 3.dp
                    ) {
                        Icon(
                            if (isSelected) Icons.Filled.Check else Icons.Filled.Add,
                            null, Modifier.padding(10.dp).size(20.dp), tint = Color.White
                        )
                    }
                }
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(12.dp), tint = Color.White)
                        Text("图集", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(6.dp), color = DouyinPink) {
                    Text("已选${selectedUrls.size}张", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    imageCollection.imageUrls.forEachIndexed { index, _ ->
                        Box(
                            Modifier.size(if (index == pagerState.currentPage) 18.dp else 7.dp, 7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.35f))
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(imageCollection.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${selectedUrls.size}/${imageCollection.imageUrls.size} 张图片已选择", fontSize = 13.sp, color = Color.Gray)
                Button(
                    onClick = {
                        if (selectedUrls.size == imageCollection.imageUrls.size) onDownloadAll()
                        else onDownloadSelected(selectedUrls.toList())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = selectedUrls.isNotEmpty()
                ) {
                    Icon(painterResource(R.drawable.ic_cloud_download), null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (selectedUrls.size == imageCollection.imageUrls.size) "下载全部 ${imageCollection.imageUrls.size} 张图片"
                        else "下载选择 ${selectedUrls.size} 张图片",
                        fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ImageViewerDialog(images: List<String>, initialPage: Int, onDismiss: () -> Unit) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { images[it] }) { page ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(images[page])
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.5f)) {
                    Text(
                        "${pagerState.currentPage + 1} / ${images.size}",
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        color = Color.White, fontSize = 13.sp
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(Icons.Outlined.Close, "关闭", tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun DownloadTaskCard(task: DownloadTask, onRetry: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit) {
    val progress by animateFloatAsState(targetValue = task.overallProgress, label = "progress")

    Card(
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    val coverUrl = task.contentInfo.coverUrl
                    if (coverUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(coverUrl)
                                .crossfade(true)
                                .size(104, 104)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Surface(Modifier.align(Alignment.TopStart).padding(3.dp), shape = RoundedCornerShape(3.dp), color = getTypeColor(task.type)) {
                        Icon(getTypeIcon(task.type), null, Modifier.padding(2.dp).size(10.dp), tint = Color.White)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(task.displayTitle, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusIcon(task.status)
                        Text(getStatusText(task), fontSize = 11.sp, color = getStatusColor(task.status))
                    }
                }
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> IconButton(onClick = onCancel, Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Cancel, "取消", Modifier.size(18.dp), tint = Color.Gray)
                    }
                    DownloadStatus.FAILED, DownloadStatus.PAUSED -> {
                        IconButton(onClick = onRetry, Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Refresh, "重试", Modifier.size(18.dp), tint = DouyinPink)
                        }
                        IconButton(onClick = onRemove, Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, "删除", Modifier.size(18.dp), tint = Color.Gray)
                        }
                    }
                    DownloadStatus.COMPLETED -> IconButton(onClick = onRemove, Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.CheckCircle, "完成", Modifier.size(18.dp), tint = Color(0xFF4CAF50))
                    }
                    else -> {}
                }
            }
            if (task.status == DownloadStatus.DOWNLOADING) {
                @Suppress("DEPRECATION")
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = DouyinPink, trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${(progress * 100).toInt()}%", fontSize = 11.sp, color = DouyinPink, fontWeight = FontWeight.Bold)
                    Text("正在下载...", fontSize = 11.sp, color = Color.Gray)
                }
            }
            if (task.status == DownloadStatus.FAILED && task.errorMessage != null) {
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)) {
                    Text(task.errorMessage, Modifier.padding(10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun StatusIcon(status: DownloadStatus) {
    val icon = when (status) {
        DownloadStatus.PENDING -> Icons.Outlined.Schedule
        DownloadStatus.DOWNLOADING -> Icons.Outlined.Autorenew
        DownloadStatus.COMPLETED -> Icons.Outlined.CheckCircle
        DownloadStatus.FAILED -> Icons.Outlined.ErrorOutline
        DownloadStatus.PAUSED -> Icons.Outlined.PauseCircleOutline
    }
    Icon(icon, null, Modifier.size(12.dp), tint = getStatusColor(status))
}

fun getStatusText(task: DownloadTask): String = when (task.status) {
    DownloadStatus.PENDING -> "等待中"
    DownloadStatus.DOWNLOADING -> "下载中 ${(task.overallProgress * 100).toInt()}%"
    DownloadStatus.COMPLETED -> "下载完成"
    DownloadStatus.FAILED -> task.errorMessage ?: "下载失败"
    DownloadStatus.PAUSED -> "已暂停"
}

fun getStatusColor(status: DownloadStatus): Color = when (status) {
    DownloadStatus.PENDING -> Color.Gray
    DownloadStatus.DOWNLOADING -> DouyinPink
    DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
    DownloadStatus.FAILED -> Color(0xFFF44336)
    DownloadStatus.PAUSED -> Color(0xFFFF9800)
}

fun getTypeColor(type: ContentType): Color = when (type) {
    ContentType.VIDEO -> DouyinPink
    ContentType.IMAGE_COLLECTION -> Color(0xFF9C27B0)
}

fun getTypeIcon(type: ContentType) = when (type) {
    ContentType.VIDEO -> Icons.Outlined.Videocam
    ContentType.IMAGE_COLLECTION -> Icons.Outlined.PhotoLibrary
}

fun formatDuration(millis: Long): String {
    val s = millis / 1000
    return String.format("%02d:%02d", s / 60, s % 60)
}

@Composable
fun StatChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = Color.Gray)
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}

fun formatCount(count: Long): String = when {
    count >= 10000 -> String.format("%.1fw", count / 10000.0)
    count >= 1000 -> String.format("%.1fk", count / 1000.0)
    else -> count.toString()
}

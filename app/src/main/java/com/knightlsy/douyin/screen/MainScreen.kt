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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.data.VideoQuality
import com.knightlsy.douyin.ui.theme.AppBarDarkEnd
import com.knightlsy.douyin.ui.theme.AppBarDarkMid
import com.knightlsy.douyin.ui.theme.AppBarDarkStart
import com.knightlsy.douyin.ui.theme.AppBarLightEnd
import com.knightlsy.douyin.ui.theme.AppBarLightMid
import com.knightlsy.douyin.ui.theme.AppBarLightStart
import com.knightlsy.douyin.ui.theme.AppBarLightTint
import com.knightlsy.douyin.ui.theme.DouyinCyan
import com.knightlsy.douyin.ui.theme.DouyinPink
import com.knightlsy.douyin.ui.theme.DouyinPinkSoft
import com.knightlsy.douyin.ui.theme.StatAmber
import com.knightlsy.douyin.ui.theme.StatGreen
import com.knightlsy.douyin.ui.theme.StatPink
import com.knightlsy.douyin.ui.theme.StatRed
import com.knightlsy.douyin.ui.theme.StatusFailed
import com.knightlsy.douyin.ui.theme.StatusRunning
import com.knightlsy.douyin.ui.theme.StatusSuccess
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
    val isDarkMode = isDarkBackground()
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current

    val activeTaskCount by remember(downloadTasks) {
        derivedStateOf { downloadTasks.count { it.status == DownloadStatus.DOWNLOADING } }
    }

    LaunchedEffect(Unit) { viewModel.autoPasteFromClipboard() }
    LaunchedEffect(uiState.successMessage) { uiState.successMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.clearSuccessMessage() } }
    LaunchedEffect(uiState.error) { uiState.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); viewModel.clearError() } }

    Scaffold(
        topBar = {
            // 顶栏：深色用「深紫底 + 粉/青双色光晕」保留品牌感；
            // 浅色用「浅粉 → 浅紫 → 内容区底色」渐变收尾，末端与背景同色，消除分界线。
            // 浅色下刻意不加投影 —— 有投影就会在顶栏底部重新画出一条分界线。
            val appBarBrush = if (isDarkMode) {
                Brush.linearGradient(
                    colors = listOf(AppBarDarkStart, AppBarDarkMid, AppBarDarkEnd)
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(AppBarLightStart, AppBarLightMid, AppBarLightTint, AppBarLightEnd)
                )
            }
            val appBarOnColor = if (isDarkMode) Color.White else Color(0xFF1A1024)
            val appBarSubColor = if (isDarkMode) Color.White.copy(alpha = 0.62f) else Color(0xFF1A1024).copy(alpha = 0.55f)
            Column(modifier = Modifier.background(appBarBrush)) {
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
                            horizontalArrangement = Arrangement.spacedBy(11.dp)
                        ) {
                            // 应用图标：品牌渐变圆角方块（与 launcher 图标同一视觉语言）
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent,
                                modifier = Modifier
                                    .size(38.dp)
                                    .shadow(6.dp, RoundedCornerShape(12.dp))
                            ) {
                                Box(
                                    modifier = Modifier.background(
                                        Brush.linearGradient(listOf(DouyinPink, DouyinPinkSoft))
                                    ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_douyin_download),
                                        null,
                                        Modifier.size(22.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                            Column {
                                Text(
                                    "抖音下载器",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp,
                                    color = appBarOnColor,
                                    letterSpacing = (-0.2).sp
                                )
                                Text(
                                    "无水印 · 图集 · 实况",
                                    fontSize = 10.sp,
                                    color = appBarSubColor
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = appBarOnColor
                    ),
                    actions = {
                        // 三态循环：跟随系统 → 浅色 → 深色。
                        // 图标反映「当前模式」而非「点击后的模式」，用户一眼知道现在处于哪一态。
                        IconButton(onClick = { viewModel.cycleThemeMode() }) {
                            Icon(
                                when (themeMode) {
                                    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
                                    ThemeMode.LIGHT -> Icons.Outlined.LightMode
                                    ThemeMode.DARK -> Icons.Outlined.DarkMode
                                },
                                when (themeMode) {
                                    ThemeMode.SYSTEM -> "跟随系统"
                                    ThemeMode.LIGHT -> "浅色模式"
                                    ThemeMode.DARK -> "深色模式"
                                },
                                tint = appBarOnColor
                            )
                        }
                        IconButton(onClick = onNavigateToHistory) {
                            Icon(painterResource(R.drawable.ic_history), "历史记录", tint = appBarOnColor)
                        }
                        IconButton(onClick = onNavigateToAbout) {
                            Icon(Icons.Outlined.Info, "关于", tint = appBarOnColor)
                        }
                    }
                )
            }
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
                    onPaste = { viewModel.pasteFromClipboard() },
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
    // 2×2 磁贴：每格独立色条 + 彩色图标底 + 大号粗体数字，扫一眼即可读数。
    // 原设计是「单卡里 4 个数字平铺」，图标 16dp 很淡、层级弱。
    val isDark = isDarkBackground()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("总下载", totalDownloads.toString(), StatPink, Icons.Outlined.ListAlt,
                Modifier.weight(1f), isDark)
            StatTile("成功", successDownloads.toString(), StatGreen, Icons.Outlined.Check,
                Modifier.weight(1f), isDark)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("失败", failedDownloads.toString(), StatRed, Icons.Outlined.Close,
                Modifier.weight(1f), isDark)
            StatTile("进行中", activeTasks.toString(), StatAmber, Icons.Outlined.Sync,
                Modifier.weight(1f), isDark)
        }
    }
}

/** 浅色判定：背景亮度低即为深色主题（避免再往上层传 isDarkMode 参数）。 */
@Composable
private fun isDarkBackground(): Boolean {
    val bg = MaterialTheme.colorScheme.background
    return (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
}

/**
 * 卡片投影：浅色下用双层投影（近处收紧 + 远处扩散），卡片才会「浮起来」
 * 而不是像贴上去的纸片。深色下不加投影 —— 深色靠明度差分层，投影反而显脏。
 */
@Composable
private fun Modifier.cardShadow(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    return if (isDarkBackground()) {
        this
    } else {
        this.shadow(
            elevation = 8.dp,
            shape = shape,
            ambientColor = Color(0x0F000000),
            spotColor = Color(0x1A000000)
        )
    }
}

/** 浅色下给卡片描一道极淡的边，进一步区分卡片与背景（深色下不需要）。 */
@Composable
private fun cardBorderOrNull(): BorderStroke? {
    return if (isDarkBackground()) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
}

@Composable
fun StatTile(
    label: String,
    value: String,
    accent: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isDark: Boolean = true
) {
    // 双层阴影：近处收紧 + 远处扩散 —— 浅色下卡片才会「浮起来」而不是像贴上去的纸片
    val tileShadow = if (isDark) {
        Modifier
    } else {
        Modifier.shadow(
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp),
            ambientColor = Color(0x14000000),
            spotColor = Color(0x1A000000)
        )
    }
    Card(
        modifier = modifier.then(tileShadow),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isDark) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(18.dp), tint = accent)
            }
            Column {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, lineHeight = 22.sp)
                Text(label, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun TipsCard() {
    Card(
        modifier = Modifier.cardShadow(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = cardBorderOrNull(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
fun InputCard(url: String, onUrlChange: (String) -> Unit, onParse: () -> Unit, onPaste: () -> Unit, isLoading: Boolean) {
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
        modifier = Modifier.cardShadow(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorderOrNull(),
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
                    onClick = onPaste,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                ) {
                    Icon(painterResource(R.drawable.ic_paste), null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("粘贴", fontSize = 13.sp)
                }
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
            }
            Button(
                onClick = onParse,
                modifier = Modifier.fillMaxWidth(),
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

@Composable
fun VideoPreviewCard(videoInfo: ContentInfo.Video, onDownload: (String) -> Unit) {
    var showPlayer by remember { mutableStateOf(false) }

    if (showPlayer) {
        VideoPlayerDialog(videoUrl = videoInfo.videoUrl, onDismiss = { showPlayer = false })
    }

    Card(
        modifier = Modifier.cardShadow(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorderOrNull(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(236.dp).background(Color(0xFF0a0a1a))) {
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
                // 底部渐变遮罩：把时长徽标和封面底部隔开，避免徽标直接压在画面上
                Box(
                    Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )
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
                    Icon(Icons.Outlined.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("@${videoInfo.author}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (videoInfo.diggCount > 0 || videoInfo.commentCount > 0 || videoInfo.shareCount > 0) {
                    // 数据条：上下细分隔线，让元信息从正文里独立成一个区块
                    Column {
                        HorizontalDivider(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatChip(Icons.Outlined.ThumbUpOffAlt, formatCount(videoInfo.diggCount))
                            StatChip(Icons.Outlined.ChatBubbleOutline, formatCount(videoInfo.commentCount))
                            StatChip(Icons.Outlined.Share, formatCount(videoInfo.shareCount))
                        }
                        if (videoInfo.width > 0 && videoInfo.height > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Outlined.HighQuality, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${videoInfo.width} × ${videoInfo.height}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(
                            Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            thickness = 0.5.dp
                        )
                    }
                } else if (videoInfo.width > 0 && videoInfo.height > 0) {
                    // 没有互动数据时，分辨率单独成行，不丢掉这条信息
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.HighQuality, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${videoInfo.width} × ${videoInfo.height}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 不再提供画质选择：统一取最高可用清晰度（1080p），
                // 下载时自动在 1080p → 720p → 540p 间降级，避免选到不可用档位而失败。
                Surface(shape = RoundedCornerShape(8.dp), color = DouyinCyan.copy(alpha = 0.15f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Outlined.HighQuality, null, Modifier.size(14.dp), tint = DouyinCyan)
                        Text("${VideoQuality.BEST_LABEL} 清晰度 · 无水印", fontSize = 12.sp, color = DouyinCyan)
                    }
                }
                Button(
                    onClick = { onDownload(videoInfo.videoUrl) },
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
    val selectedCount by remember { derivedStateOf { selectedUrls.size } }
    // 展示模式：false = 左右滑动（原样），true = 网格列表。整个会话内记住选择。
    var listMode by remember { mutableStateOf(false) }
    val imageUrls = imageCollection.imageUrls
    val liveUrls = imageCollection.animatedUrls

    if (showImageViewer) {
        // 列表模式下点开单图：整屏大图。left/right 翻页 + 双指缩放，避免只能一张张划。
        FullscreenImageViewer(
            images = imageUrls,
            liveFlags = liveUrls.map { it != null },
            initialIndex = viewerIndex,
            isSelected = { idx: Int -> imageUrls.getOrNull(idx)?.let { u -> u in selectedUrls } == true },
            onToggleSelect = { idx: Int ->
                imageUrls.getOrNull(idx)?.let { u ->
                    if (u in selectedUrls) selectedUrls.remove(u) else selectedUrls.add(u)
                }
            },
            onDismiss = { showImageViewer = false }
        )
    }

    Card(
        modifier = Modifier.cardShadow(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorderOrNull(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(380.dp).background(Color(0xFF0a0a1a))) {
                if (!listMode) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { imageUrls[it] }) { page ->
                    Box(
                        Modifier.fillMaxSize()
                            .clickable { viewerIndex = page; showImageViewer = true }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(imageUrls[page])
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(14.dp)) {
                    val currentUrl = imageUrls[pagerState.currentPage]
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
                } else {
                    // 列表模式：网格缩略图，点开单图看大图，右下角直接勾选
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize().padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        gridItemsIndexed(items = imageUrls, key = { _, url -> url }) { index, url ->
                            val isSelected = url in selectedUrls
                            val hasLive = liveUrls.getOrNull(index) != null
                            Box(
                                Modifier.aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f))
                                    .clickable { viewerIndex = index; showImageViewer = true }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(url).crossfade(true).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // 实况标记
                                if (hasLive) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.Black.copy(alpha = 0.55f)
                                    ) {
                                        Text("实况", Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            color = Color.White, fontSize = 9.sp)
                                    }
                                }
                                // 序号
                                Surface(
                                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.Black.copy(alpha = 0.5f)
                                ) {
                                    Text("${index + 1}", Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = Color.White, fontSize = 9.sp)
                                }
                                // 勾选圈
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                        .size(22.dp).clickable {
                                            if (isSelected) selectedUrls.remove(url) else selectedUrls.add(url)
                                        },
                                    shape = CircleShape,
                                    color = if (isSelected) DouyinPink else Color.Black.copy(alpha = 0.45f)
                                ) {
                                    Icon(
                                        if (isSelected) Icons.Filled.Check else Icons.Filled.Add,
                                        null, Modifier.padding(3.dp), tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
                Surface(Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.55f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.PhotoLibrary, null, Modifier.size(12.dp), tint = Color.White)
                        val liveCount = imageCollection.animatedUrls.count { it != null }
                        Text(if (liveCount > 0) "图集 · ${liveCount}张实况" else "图集", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Surface(Modifier.align(Alignment.TopEnd).padding(10.dp), shape = RoundedCornerShape(6.dp), color = DouyinPink) {
                    Text("已选${selectedCount}张", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                // 全选 / 取消全选：图集多于一张时才显示
                if (imageUrls.size > 1) {
                    val allSelected = selectedCount == imageUrls.size
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 44.dp, end = 10.dp)
                            .clickable {
                                if (allSelected) selectedUrls.clear()
                                else {
                                    selectedUrls.clear()
                                    selectedUrls.addAll(imageUrls)
                                }
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            if (allSelected) "取消全选" else "全选",
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium
                        )
                    }
                }
                // 模式切换：滑动 ⇄ 列表
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .clickable { listMode = !listMode },
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f)
                ) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            if (listMode) Icons.Filled.ViewCarousel else Icons.Filled.GridView,
                            null, Modifier.size(14.dp), tint = Color.White
                        )
                        Text(if (listMode) "滑动模式" else "列表模式", color = Color.White, fontSize = 11.sp)
                    }
                }
                // 页码指示点只在滑动模式下显示（列表模式已有序号）
                if (!listMode) {
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        imageUrls.forEachIndexed { index, _ ->
                            Box(
                                Modifier.size(if (index == pagerState.currentPage) 18.dp else 7.dp, 7.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                } else {
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            "共 ${imageUrls.size} 张 · 点图看大图",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
                        )
                    }
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(imageCollection.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (selectedCount == 0) "未选择（点「全选」可一次选中全部 ${imageCollection.imageUrls.size} 张）"
                    else "${selectedCount}/${imageCollection.imageUrls.size} 张图片已选择",
                    fontSize = 13.sp, color = Color.Gray
                )
                Button(
                    onClick = {
                        // 一张没选 = 下载全部；选了部分 = 只下选中的
                        if (selectedCount == 0 || selectedCount == imageCollection.imageUrls.size) onDownloadAll()
                        else onDownloadSelected(selectedUrls.toList())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    enabled = true
                ) {
                    Icon(painterResource(R.drawable.ic_cloud_download), null, Modifier.size(18.dp), tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            selectedCount == 0 -> "下载全部 ${imageCollection.imageUrls.size} 张图片"
                            selectedCount == imageCollection.imageUrls.size -> "下载全部 ${imageCollection.imageUrls.size} 张图片"
                            else -> "下载选择 ${selectedCount} 张图片"
                        },
                        fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 整屏大图查看器：左右滑动翻页 + 双指缩放/拖动。
 *
 * 与 [ImageViewerDialog] 的区别：列表模式点开单图后用它，能直接左右翻页看完全部，
 * 不必退回列表再点下一张；缩放便于查看细节。
 * 底部显示「N / 总数」并可直接勾选/取消勾选当前图。
 */
@Composable
fun FullscreenImageViewer(
    images: List<String>,
    liveFlags: List<Boolean>,
    initialIndex: Int,
    isSelected: (Int) -> Boolean,
    onToggleSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(initialPage = initialIndex.coerceIn(0, (images.size - 1).coerceAtLeast(0)), pageCount = { images.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(), key = { images[it] }) { page ->
                // 每页独立记住缩放状态，翻页后自动复位
                var scale by remember(page) { mutableStateOf(1f) }
                var offsetX by remember(page) { mutableStateOf(0f) }
                var offsetY by remember(page) { mutableStateOf(0f) }
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(page) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale <= 1f) { offsetX = 0f; offsetY = 0f }
                                else {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(images[page]).crossfade(true).build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale; scaleY = scale
                                translationX = offsetX; translationY = offsetY
                            },
                        contentScale = ContentScale.Fit
                    )
                    if (liveFlags.getOrNull(page) == true) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 14.dp),
                            shape = RoundedCornerShape(6.dp), color = DouyinPink
                        ) {
                            Text("实况", Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // 顶部：关闭 + 页码
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Icon(Icons.Outlined.Close, "关闭", tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
                shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text("${pagerState.currentPage + 1} / ${images.size}",
                    Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    color = Color.White, fontSize = 13.sp)
            }
            // 底部：勾选当前图
            val cur = pagerState.currentPage
            val sel = isSelected(cur)
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                    .clickable { onToggleSelect(cur) },
                shape = RoundedCornerShape(24.dp),
                color = if (sel) DouyinPink else Color.Black.copy(alpha = 0.55f)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(if (sel) Icons.Filled.Check else Icons.Filled.Add, null, Modifier.size(18.dp), tint = Color.White)
                    Text(if (sel) "已选择（点击取消）" else "选择这张", color = Color.White, fontSize = 13.sp)
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
        modifier = Modifier.cardShadow(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorderOrNull(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // 下载中：缩略图外包一圈环形进度（百分比居中）—— 全卡仅此一处进度指示，
                // 不再另加横向进度条，避免同一个百分比显示两遍。
                if (task.status == DownloadStatus.DOWNLOADING) {
                    Box(Modifier.size(54.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            color = DouyinPink,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeWidth = 4.dp
                        )
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "${(progress * 100).toInt()}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = DouyinPink
                            )
                        }
                    }
                } else {
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
                }
                Spacer(Modifier.width(11.dp))
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
                        Icon(Icons.Outlined.CheckCircle, "完成", Modifier.size(18.dp), tint = StatusSuccess)
                    }
                    else -> {}
                }
            }
            // 多文件任务（如图集）才显示文件进度 —— 这是真实可用的数据，不编造速度/体积
            if (task.status == DownloadStatus.DOWNLOADING && task.totalFiles > 1) {
                Text(
                    "第 ${(task.completedFiles + 1).coerceAtMost(task.totalFiles)} / ${task.totalFiles} 个文件",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val tint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = tint)
        Text(text, fontSize = 12.sp, color = tint)
    }
}

fun formatCount(count: Long): String = when {
    count >= 10000 -> String.format("%.1fw", count / 10000.0)
    count >= 1000 -> String.format("%.1fk", count / 1000.0)
    else -> count.toString()
}

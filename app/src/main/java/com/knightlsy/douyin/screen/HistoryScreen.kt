package com.knightlsy.douyin.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.ui.components.BgCheckBox
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgMonoLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 下载历史。
 *
 * 结构沿用旧版（列表 + 长按进选择模式 + 清空确认），视觉全部换掉：
 *
 * - 顶栏从粉色 TopAppBar 换成「细线 + 文字操作」。粉色整条铺满太抢，
 *   而且跟首页的主按钮撞色 —— 粉是「主行动」的信号，不能用在导航上。
 * - 每条记录从 60dp 缩略图 + 卡片阴影，换成 44dp 缩略图 + 底部细线。
 *   一屏能从 4 条看到 7 条。
 * - 选择模式用方角勾选（44dp 触摸区），不再是 Material 的 Checkbox。
 * - 「重新解析」图标触摸区从 36dp 提到 44dp。
 */
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onBack: () -> Unit,
    onReparse: (String) -> Unit,
) {
    val c = LocalBgColors.current
    val context = LocalContext.current

    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val isSelectMode by viewModel.isSelectMode.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadHistory() }

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
                .padding(start = 10.dp, end = BgSpace.gutter, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { if (isSelectMode) viewModel.toggleSelectMode() else onBack() },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isSelectMode) "✕" else "←",
                    fontSize = 17.sp,
                    color = c.ink2,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (isSelectMode) "已选 ${selectedIds.size}" else "下载历史",
                style = MaterialTheme.typography.displayMedium,
                color = c.ink,
                modifier = Modifier.weight(1f),
            )

            if (isSelectMode) {
                BgMonoLabel(
                    text = "全选",
                    color = c.ink2,
                    modifier = Modifier
                        .clip(RoundedCornerShape(BgRadius.r1))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.toggleSelectAll() },
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
                if (selectedIds.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    BgMonoLabel(
                        text = "删除",
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BgRadius.r1))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    val count = selectedIds.size
                                    viewModel.deleteSelected()
                                    Toast.makeText(context, "已删除 $count 条记录", Toast.LENGTH_SHORT).show()
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            } else {
                if (items.isNotEmpty()) {
                    BgMonoLabel(
                        text = "清空",
                        color = c.ink3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(BgRadius.r1))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showClearDialog = true },
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }

        BgHair()

        /* ── 列表 ── */
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "暂无下载历史",
                        style = MaterialTheme.typography.headlineSmall,
                        color = c.ink2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "下载过的视频和图集会出现在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = c.ink3,
                    )
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        isSelected = item.id in selectedIds,
                        isSelectMode = isSelectMode,
                        onClick = {
                            if (isSelectMode) viewModel.toggleSelect(item.id)
                            else viewModel.toggleSelectMode()
                        },
                        onLongClick = {
                            if (!isSelectMode) {
                                viewModel.toggleSelectMode()
                                viewModel.toggleSelect(item.id)
                            } else {
                                viewModel.toggleSelect(item.id)
                            }
                        },
                        onReparse = {
                            onReparse(item.originalUrl)
                            viewModel.toggleSelectMode()
                        },
                    )
                }
            }
        }
    }

    /* ── 清空确认 ── */
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = c.paper,
            shape = RoundedCornerShape(BgRadius.r2),
            title = {
                Text("清空历史", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            },
            text = {
                Text(
                    "确定要清空所有下载历史吗？已下载的文件不会被删除。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) {
                    Text("确定", color = c.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("取消", color = c.ink3)
                }
            },
        )
    }
}

/**
 * 历史记录单条。
 *
 * 点空白处进选择模式（旧版逻辑），长按也能进。选择模式下点任意处切换选中。
 * 「重新解析」只在非选择模式出现，触摸区 44dp。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: DownloadHistoryItem,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReparse: () -> Unit,
) {
    val c = LocalBgColors.current
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    val typeLabel = when (item.contentType) {
        ContentType.VIDEO -> "视频"
        ContentType.IMAGE_COLLECTION -> "图集"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BgSpace.gutter)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 缩略图
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(c.raise)
            ) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(11.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title.ifBlank { "无标题" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "@${item.author.ifBlank { "未知" }} · $typeLabel · ${timeFormat.format(Date(item.downloadTime))}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.2.sp,
                    color = c.ink3,
                    maxLines = 1,
                )
            }

            if (isSelectMode) {
                BgCheckBox(
                    checked = isSelected,
                    onToggle = onClick,
                )
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onReparse,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "重新解析",
                        tint = c.ink3,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
        }
        BgHair()
    }
}

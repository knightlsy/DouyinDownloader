@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.knightlsy.douyin.screen

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.ui.theme.DouyinCyan
import com.knightlsy.douyin.ui.theme.DouyinPink
import com.knightlsy.douyin.viewmodel.HistoryViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(),
    onBack: () -> Unit,
    onReparse: (String) -> Unit
) {
    val items by viewModel.items.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isSelectMode by viewModel.isSelectMode.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.loadHistory() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isSelectMode) "已选择 ${selectedIds.size} 项" else "下载历史",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectMode) viewModel.toggleSelectMode() else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DouyinPink, titleContentColor = Color.White),
                actions = {
                    if (isSelectMode) {
                        IconButton(onClick = { viewModel.toggleSelectAll() }) {
                            Icon(Icons.Filled.SelectAll, "全选", tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (selectedIds.isNotEmpty()) {
                                val count = selectedIds.size
                                viewModel.deleteSelected()
                                Toast.makeText(context, "已删除 $count 条记录", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Delete, "删除", tint = Color.White)
                        }
                        IconButton(onClick = {
                            if (selectedIds.size == 1) {
                                val item = items.find { it.id in selectedIds }
                                if (item != null) {
                                    viewModel.toggleSelectMode()
                                    onReparse(item.originalUrl)
                                }
                            } else if (selectedIds.size > 1) {
                                Toast.makeText(context, "重新解析只支持单条操作", Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, "重新解析", tint = Color.White)
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleSelectMode() }) {
                            Icon(Icons.Filled.Checklist, "选择", tint = Color.White)
                        }
                        if (items.isNotEmpty()) {
                            var showClearDialog by remember { mutableStateOf(false) }
                            IconButton(onClick = { showClearDialog = true }) {
                                Icon(Icons.Filled.DeleteSweep, "清空", tint = Color.White)
                            }
                            if (showClearDialog) {
                                AlertDialog(
                                    onDismissRequest = { showClearDialog = false },
                                    title = { Text("清空历史") },
                                    text = { Text("确定要清空所有下载历史吗？") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            viewModel.clearAll()
                                            showClearDialog = false
                                        }) { Text("确定") }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showClearDialog = false }) { Text("取消") }
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.History, null, Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                    Text("暂无下载历史", color = Color.Gray, fontSize = 16.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).background(Color(0xFFF5F5F5)),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    HistoryItemCard(
                        item = item,
                        isSelected = item.id in selectedIds,
                        isSelectMode = isSelectMode,
                        onClick = {
                            if (isSelectMode) viewModel.toggleSelect(item.id)
                        },
                        onLongClick = {
                            if (!isSelectMode) {
                                viewModel.toggleSelectMode()
                                viewModel.toggleSelect(item.id)
                            }
                        },
                        onReparse = { onReparse(item.originalUrl) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: DownloadHistoryItem,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onReparse: () -> Unit
) {
    val timeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val typeColor = when (item.contentType) {
        ContentType.VIDEO -> DouyinPink
        ContentType.IMAGE_COLLECTION -> Color(0xFF9C27B0)
    }
    val typeLabel = when (item.contentType) {
        ContentType.VIDEO -> "视频"
        ContentType.IMAGE_COLLECTION -> "图集"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) DouyinPink.copy(alpha = 0.08f) else Color.White
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0F0F0))) {
                if (item.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.coverUrl)
                            .crossfade(true)
                            .size(120, 120)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                    shape = RoundedCornerShape(4.dp), color = typeColor
                ) {
                    Text(typeLabel, Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("@${item.author}", fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(timeFormat.format(Date(item.downloadTime)), fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.7f))
            }

            if (isSelectMode) {
                Checkbox(checked = isSelected, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = DouyinPink))
            } else {
                IconButton(onClick = onReparse, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.Refresh, "重新解析", tint = DouyinPink, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.ui.components.BgCheckBox
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 图集下载前的选图浮层。
 *
 * ## 为什么要有它
 * 图集原来是「内嵌在页面卡片里勾选」—— 图多的时候要把整张卡滚到底才能看到
 * 下载按钮，翻页找图也在同一个滚动流里，选到第 20 张时已经不知道前面选了什么。
 *
 * 浮层把「选」和「确认」压到一屏内：底部弹出、网格独立滚动、
 * 选完直接点「下载 N 张」，不需要在长页面里来回滚。
 *
 * ## 交互
 * - 默认全选（图集场景大多是想全要，少点一次）
 * - 顶部显示「已选 3/12」，右侧一键全选/取消
 * - 点遮罩或返回键关闭，等于取消
 * - 确认后把选中的 URL 列表交给调用方，浮层自己不碰下载逻辑
 */
@Composable
fun ImageSelectionSheet(
    collection: ContentInfo.ImageCollection,
    initialSelection: Set<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val c = LocalBgColors.current
    val total = collection.imageUrls.size
    // 以图集 id 为 key：同一张卡复用时不要沿用上一次的勾选
    var selected by remember(collection.id) {
        mutableStateOf(initialSelection.ifEmpty { collection.imageUrls.indices.toSet() })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            // 关掉默认的窄宽度，否则底部面板只有屏幕中间一小块
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .clip(
                        RoundedCornerShape(
                            topStart = BgRadius.r4,
                            topEnd = BgRadius.r4,
                        )
                    )
                    .background(c.paper)
                    // 吃掉面板内部的点击，否则点空白处会穿透到遮罩直接关掉
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = BgSpace.gutter),
            ) {
                // 顶部的拖拽条：纯视觉暗示，告诉用户这是从底部上来的面板
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 4.dp)
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(c.hair)
                )

                // ── 标题行 ──
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "选择要下载的图片",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                            color = c.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "已选 ${selected.size} / $total" +
                                if (collection.animatedUrls.any { it != null }) {
                                    " · 含实况"
                                } else "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (selected.isEmpty()) c.ink3 else c.accent,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(BgRadius.r1))
                            .background(c.raise)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    selected = if (selected.size == total) {
                                        emptySet()
                                    } else {
                                        collection.imageUrls.indices.toSet()
                                    }
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (selected.size == total) "取消全选" else "全选",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 0.4.sp,
                            color = c.ink,
                        )
                    }
                }

                // ── 网格 ──
                // 这里可以用 LazyVerticalGrid：浮层是独立的一屏，不在外层的 LazyColumn 里，
                // 不存在嵌套滚动崩溃的问题（卡片内嵌版之所以手写 chunked(3) 就是怕这个）。
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    items(total) { index ->
                        SelectionCell(
                            url = collection.imageUrls[index],
                            index = index,
                            isLive = collection.animatedUrls.getOrNull(index) != null,
                            isSelected = index in selected,
                            onToggle = {
                                selected = if (index in selected) {
                                    selected - index
                                } else {
                                    selected + index
                                }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── 底部按钮 ──
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(BgRadius.r2))
                            .background(c.raise)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            fontSize = 14.sp,
                            color = c.ink2,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1.6f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(BgRadius.r2))
                            .background(if (selected.isEmpty()) c.raise else c.accent)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = selected.isNotEmpty(),
                                onClick = {
                                    if (selected.isNotEmpty()) {
                                        onConfirm(selected.sorted().map { collection.imageUrls[it] })
                                    }
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (selected.isEmpty()) {
                                "未选择"
                            } else {
                                "下载 ${selected.size} 张"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selected.isEmpty()) c.ink3 else c.accentInk,
                        )
                    }
                }
            }
        }
    }
}

/** 浮层里的单张图。勾选圈叠在右上角，序号在左上角。 */
@Composable
private fun SelectionCell(
    url: String,
    index: Int,
    isLive: Boolean,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    val c = LocalBgColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(BgRadius.r1))
            .background(c.raise)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
    ) {
        AsyncImage(
            model = url,
            contentDescription = "第 ${index + 1} 张",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 未选中时压一层暗色，让选中/未选中的差别在缩略图本身就能读出来
        if (!isSelected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }

        // 选中描边
        if (isSelected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.dp, c.accent, RoundedCornerShape(BgRadius.r1))
            )
        }

        // 序号
        Box(
            Modifier
                .padding(4.dp)
                .align(Alignment.TopStart)
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "${index + 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                color = Color.White,
            )
        }

        // 实况标记
        if (isLive) {
            Box(
                Modifier
                    .padding(4.dp)
                    .align(Alignment.BottomStart)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "实况",
                    fontSize = 7.5.sp,
                    color = Color.White,
                )
            }
        }

        BgCheckBox(
            checked = isSelected,
            overlay = true,
            onToggle = onToggle,
            modifier = Modifier
                .padding(4.dp)
                .align(Alignment.TopEnd)
                .size(20.dp),
        )
    }
}

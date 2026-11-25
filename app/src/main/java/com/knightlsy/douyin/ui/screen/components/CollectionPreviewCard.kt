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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.knightlsy.douyin.data.ContentInfo
import com.knightlsy.douyin.ui.components.BgCheckBox
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 图集预览卡。
 *
 * 沿用旧版的三列网格 + 勾选交互，视觉全部换掉：
 *
 * - 网格 2dp 间隙（旧版 6dp），让缩略图连成一片，像一张联系印样
 * - 勾选圈改成方角（A 的精确语言），叠在图上时带半透明白边
 * - 覆盖角标仍然保留四个：图集类型（左上）、已选数（右上）、全选（右上二行）、
 *   「共 N 张 · 点图看大图」（底部居中）
 * - 下载按钮用青色 —— 跟首页视频卡的粉色主按钮区分开：视频是「一条」，
 *   图集是「一批」，两个不同的心智
 *
 * 网格不用 LazyVerticalGrid：这个卡片本身处在 LazyColumn 里，
 * 嵌套两个滚动容器会崩（无限高度约束）。用 Column + 手动 chunked(3) 分组。
 */
@Composable
fun CollectionPreviewCard(
    collection: ContentInfo.ImageCollection,
    onDownloadSelected: (List<String>) -> Unit,
    onImageClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    val total = collection.imageUrls.size

    // 默认全选：图集场景下用户多半想全要，少点一次
    var selected by remember(collection.id) {
        mutableStateOf(collection.imageUrls.indices.toSet())
    }

    val liveCount = collection.animatedUrls.count { it != null }

    // 下载前弹选图浮层：图集动辄十几二十张，在长页面里翻着勾容易漏选，
    // 勾到后半段也记不住前面选了什么。浮层一屏看完、网格独立滚动。
    var showPicker by remember(collection.id) { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().padding(horizontal = BgSpace.gutter)) {

        // ── 工具栏：类型 + 已选数 + 全选 ──
        // 这几个必须放在网格外面。网格是 3 列，浮层若叠在 Box 的 TopEnd 上，
        // 正好压住第 3 张图的勾选框（GridCell 的 BgCheckBox 也在 TopEnd），
        // 用户点不到；同理，左上角标会压住第 1 张图的序号。
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(BgRadius.r1))
                    .background(c.raise)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (liveCount > 0) "▦ 图集 · ${liveCount}张实况" else "▦ 图集",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.6.sp,
                    color = c.ink3,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "已选 ${selected.size}/${total}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 0.3.sp,
                color = if (selected.isEmpty()) c.ink3 else c.accent,
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(BgRadius.r1))
                    .background(c.raise)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            selected = if (selected.size == total) emptySet() else collection.imageUrls.indices.toSet()
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (selected.size == total) "取消全选" else "全选",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.6.sp,
                    color = c.ink,
                )
            }
        }

        // ── 网格 ──
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.glass)
        ) {
                    // 分组结果按图集 id 缓存：原来 chunked(3) 在一次重组里算两遍（遍历 + lastIndex），
                    // 图集有二十张时就是白白构造两个 List<List<String>>。
                    val rows = remember(collection.id) { collection.imageUrls.chunked(3) }
            Box {
                Column(Modifier.fillMaxWidth().padding(2.dp)) {
                    rows.forEachIndexed { rowIdx, row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEachIndexed { colIdx, url ->
                                val idx = rowIdx * 3 + colIdx
                                GridCell(
                                    url = url,
                                    index = idx,
                                    isLive = collection.animatedUrls.getOrNull(idx) != null,
                                    selected = idx in selected,
                                    modifier = Modifier.weight(1f),
                                    onToggle = {
                                        selected = if (idx in selected) selected - idx else selected + idx
                                    },
                                    onClick = { onImageClick(idx) },
                                )
                            }
                            // 最后一行不足三个时补空位，否则格子会被拉宽
                            repeat(3 - row.size) {
                                Box(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                        if (rowIdx != rows.lastIndex) {
                            Spacer(Modifier.height(2.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── 标题 ──
        Text(
            text = collection.title.ifBlank { "无标题" },
            style = MaterialTheme.typography.bodyLarge,
            color = c.ink,
            maxLines = 2,
        )

        Spacer(Modifier.height(6.dp))

        // ── 选择状态 ──
        Text(
            text = when {
                selected.isEmpty() -> "未选择 · 点上方全选，或逐张点选"
                selected.size == total -> "全部 $total 张已选择"
                else -> "已选 ${selected.size} / $total 张"
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 0.3.sp,
            color = c.ink3,
        )

        Spacer(Modifier.height(12.dp))

        // ── 下载按钮（青色，与视频卡的粉色区分）──
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(BgRadius.r2))
                // 不再依赖「先勾再点」：按钮始终可点，点开浮层在里面选。
                .background(c.success)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showPicker = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (selected.isEmpty()) {
                    "↓  选择并下载图片"
                } else {
                    "↓  下载 ${selected.size} 张图片"
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                letterSpacing = 1.4.sp,
                color = Color(0xFF052A2A),
            )
        }
    }

    // 浮层是 Dialog，独立于布局树，不影响 Column 排版。
    // 初始选择沿用卡片里已勾好的，用户不必重选。
    if (showPicker) {
        ImageSelectionSheet(
            collection = collection,
            initialSelection = selected,
            onDismiss = { showPicker = false },
            onConfirm = { urls ->
                showPicker = false
                onDownloadSelected(urls)
            },
        )
    }
}
/**
 * 网格单元。
 *
 * 点图片本体 = 看大图；点右上角勾选 = 选中/取消。
 * 两个操作分开，避免「想看图结果选中了」。
 */
@Composable
private fun GridCell(
    url: String,
    index: Int,
    isLive: Boolean,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val c = LocalBgColors.current
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(BgRadius.r1))
            .background(c.raise)
            .then(if (selected) Modifier.border(2.dp, c.accent) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
    ) {
        AsyncImage(
            model = url,
            contentDescription = "第 ${index + 1} 张",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // 左上序号
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(3.dp)
                .clip(RoundedCornerShape(BgRadius.r1))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        ) {
            Text(
                text = "${index + 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 7.5.sp,
                color = Color.White.copy(alpha = 0.9f),
            )
        }

        // 左下实况标记
        if (isLive) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(BgRadius.r1))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "实况",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    color = c.success,
                )
            }
        }

        // 右上勾选
        BgCheckBox(
            checked = selected,
            overlay = true,
            onToggle = onToggle,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
}

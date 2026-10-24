package com.knightlsy.douyin.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.BuildConfig
import com.knightlsy.douyin.ui.components.BgGhostButton
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL


private data class UpdateInfo(
    val hasUpdate: Boolean,
    val version: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    /** 最后一次完成打包的日期（yyyy-MM-dd），取自 Release 的发布时间 */
    val packagedAt: String = ""
)

/** 把 GitHub 的 ISO 时间（2026-10-11T12:34:56Z）转成 2026-10-11 */
private fun formatPackagedAt(iso: String): String = try {
    java.time.OffsetDateTime.parse(iso).toLocalDate().toString()
} catch (_: Exception) {
    iso.take(10)
}

private suspend fun checkUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/knightlsy/DouyinDownloader/releases/latest")
        val conn = url.openConnection()
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "DouyinDownloader-Android")
        val json = conn.getInputStream().use { it.bufferedReader().readText() }

        val tagMatch = Regex(""""tag_name"\s*:\s*"(v[^"]+)"""").find(json)
        val version = tagMatch?.groupValues?.get(1)?.replace("v", "") ?: return@withContext UpdateInfo(false)

        val downloadMatch = Regex(""""browser_download_url"\s*:\s*"([^"]*\.apk)"""").find(json)
        val downloadUrl = downloadMatch?.groupValues?.get(1) ?: ""

        val bodyMatch = Regex(""""body"\s*:\s*"((?:[^"\\]|\\.)*)"\s*[,}]""").find(json)
        val notes = bodyMatch?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""

        // 打包时间：Release 的发布时间即最后一次打包成功的时刻
        val publishedMatch = Regex(""""published_at"\s*:\s*"([^"]+)"""").find(json)
        val packagedAt = publishedMatch?.groupValues?.get(1)?.let { formatPackagedAt(it) } ?: ""

        val currentVersion = BuildConfig.VERSION_NAME
        UpdateInfo(
            hasUpdate = version != currentVersion,
            version = version,
            downloadUrl = downloadUrl,
            releaseNotes = notes,
            packagedAt = packagedAt
        )
    } catch (e: Exception) {
        UpdateInfo(false)
    }
}

/**
 * 关于页。
 *
 * 内容一项没删：软件信息、10 项技术架构、8 项功能特性、开源信息、更新检查。
 * 但把旧版的「图标 + 卡片 + 分组标题」全部拆掉，换成
 * 「等宽小节标签 + 细线 + 左边标签右边值」的排版。
 *
 * 旧版每个技术项都配一个 Material 图标（Cloud / Storage / AccountTree…），
 * 10 个图标各有各的语义，读者反而要逐个辨认。现在只用文字对齐，
 * 扫一眼就能读完 —— 这是「关于」页，信息密度比装饰重要。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = LocalBgColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val githubUrl = "https://github.com/knightlsy/DouyinDownloader"

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.paper)
            .systemBarsPadding()
    ) {
        // 顶栏
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
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("←", fontSize = 17.sp, color = c.ink2)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "关于",
                style = MaterialTheme.typography.displayMedium,
                color = c.ink,
            )
        }

        BgHair()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BgSpace.gutter)
        ) {
            Spacer(Modifier.height(22.dp))

            // 应用标记
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "抖音下载器",
                        style = MaterialTheme.typography.headlineMedium,
                        color = c.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "无水印 · 图集下载",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.4.sp,
                        color = c.ink3,
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            BgSectionLabel("软件信息")
            Spacer(Modifier.height(6.dp))
            BgHair()

            KVRow("软件名称", "抖音视频无水印下载器")
            KVRow("作者", "Knightlsy")
            KVRow("版本", "v${BuildConfig.VERSION_NAME}")
            KVRow("开源协议", "MIT License")

            Spacer(Modifier.height(6.dp))
            BgGhostButton(
                text = if (isChecking) "正在检查…" else "检查更新",
                enabled = !isChecking,
                onClick = {
                    scope.launch {
                        isChecking = true
                        val info = checkUpdate()
                        isChecking = false
                        updateInfo = info
                        showUpdateDialog = true
                    }
                },
            )

            Spacer(Modifier.height(26.dp))
            BgSectionLabel("技术架构")
            Spacer(Modifier.height(6.dp))
            BgHair()

            KVRow("开发语言", "Kotlin")
            KVRow("UI 框架", "Jetpack Compose")
            KVRow("设计规范", "Material Design 3")
            KVRow("网络库", "OkHttp3")
            KVRow("JSON 解析", "Gson")
            KVRow("图片加载", "Coil")
            KVRow("视频播放", "Media3 ExoPlayer")
            KVRow("异步处理", "Kotlin Coroutines")
            KVRow("状态管理", "StateFlow")
            KVRow("本地数据库", "Room")

            Spacer(Modifier.height(26.dp))
            BgSectionLabel("功能特性", accent = "· 8 项")
            Spacer(Modifier.height(6.dp))
            BgHair()

            listOf(
                "无水印下载抖音视频",
                "支持图集批量下载",
                "自动解析短链接",
                "多线程并发下载",
                "断点续传与自动重试",
                "实时下载进度通知",
                "下载历史记录管理",
                "深色模式支持",
            ).forEachIndexed { i, t ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = (i + 1).toString().padStart(2, '0'),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.6.sp,
                        color = c.ink3,
                        modifier = Modifier.width(26.dp),
                    )
                    Text(t, fontSize = 13.sp, color = c.ink2)
                }
                if (i != 7) BgHair()
            }

            Spacer(Modifier.height(26.dp))
            BgSectionLabel("开源信息")
            Spacer(Modifier.height(6.dp))
            BgHair()

            KVRow("仓库地址", "github.com/knightlsy/DouyinDownloader")
            Spacer(Modifier.height(4.dp))
            BgGhostButton(
                text = "访问 GitHub 仓库",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)))
                    }
                },
            )

            Spacer(Modifier.height(48.dp))
        }
    }

    /* ── 更新弹窗 ── */
    if (showUpdateDialog) {
        val info = updateInfo
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = c.paper,
            shape = RoundedCornerShape(BgRadius.r2),
            title = {
                Text(
                    text = if (info?.hasUpdate == true) "发现新版本" else "已是最新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    color = c.ink,
                )
            },
            text = {
                Column {
                    Text(
                        text = "当前版本: v${BuildConfig.VERSION_NAME}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        color = c.ink3,
                    )
                    if (info != null && info.version.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "最新版本: v${info.version}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = c.ink3,
                        )
                    }
                    if (info != null && info.packagedAt.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "最后更新: ${info.packagedAt}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            color = c.ink3,
                        )
                    }
                    if (info != null && info.releaseNotes.isNotBlank() && info.hasUpdate) {
                        Spacer(Modifier.height(12.dp))
                        Text("更新内容：", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = c.ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = info.releaseNotes.take(600),
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp,
                            color = c.ink2,
                        )
                    }
                }
            },
            confirmButton = {
                if (info?.hasUpdate == true && info.downloadUrl.isNotBlank()) {
                    TextButton(onClick = {
                        showUpdateDialog = false
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))
                            )
                        }
                    }) {
                        Text("下载更新", color = c.accent)
                    }
                } else {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("确定", color = c.ink2)
                    }
                }
            },
            dismissButton = if (info?.hasUpdate == true) {
                {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后", color = c.ink3)
                    }
                }
            } else null,
        )
    }
}

/** 左边等宽标签、右边值的对齐行。 */
@Composable
private fun KVRow(label: String, value: String) {
    val c = LocalBgColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            letterSpacing = 0.6.sp,
            color = c.ink3,
        )
        Text(
            text = value,
            fontSize = 12.5.sp,
            color = c.ink,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
    BgHair()
}

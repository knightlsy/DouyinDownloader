package com.knightlsy.douyin.screen

import android.content.Intent
import com.knightlsy.douyin.BuildConfig
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.theme.DouyinCyan
import com.knightlsy.douyin.ui.theme.DouyinPink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

private data class UpdateInfo(
    val hasUpdate: Boolean,
    val version: String = "",
    val downloadUrl: String = "",
    val releaseNotes: String = ""
)

private suspend fun checkUpdate(): UpdateInfo = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://api.github.com/repos/knightlsy/DouyinDownloader/releases/latest")
        val conn = url.openConnection()
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "DouyinDownloader-Android")
        val json = conn.getInputStream().bufferedReader().readText()

        val tagMatch = Regex(""""tag_name"\s*:\s*"(v[^"]+)"""").find(json)
        val version = tagMatch?.groupValues?.get(1)?.replace("v", "") ?: return@withContext UpdateInfo(false)

        val downloadMatch = Regex(""""browser_download_url"\s*:\s*"([^"]*\.apk)"""").find(json)
        val downloadUrl = downloadMatch?.groupValues?.get(1) ?: ""

        val bodyMatch = Regex(""""body"\s*:\s*"((?:[^"\\]|\\.)*)"\s*[,}]""").find(json)
        val notes = bodyMatch?.groupValues?.get(1)?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: ""

        val currentVersion = BuildConfig.VERSION_NAME
        UpdateInfo(
            hasUpdate = version != currentVersion,
            version = version,
            downloadUrl = downloadUrl,
            releaseNotes = notes
        )
    } catch (e: Exception) {
        UpdateInfo(false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val githubUrl = "https://github.com/knightlsy/DouyinDownloader"

    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "返回", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DouyinPink, titleContentColor = Color.White)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = DouyinPink
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("DY", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                    Text("抖音下载器", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text("无水印 · 图集下载", fontSize = 13.sp, color = Color.Gray)
                    Text("v${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Person, null, tint = DouyinPink, modifier = Modifier.size(20.dp))
                        Text("软件信息", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    AboutItem(label = "软件名称", value = "抖音视频无水印下载器")
                    AboutItem(label = "软件作者", value = "Knightlsy")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("版本号", fontSize = 13.sp, color = Color.Gray)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    isChecking = true
                                    scope.launch {
                                        updateInfo = checkUpdate()
                                        isChecking = false
                                        showUpdateDialog = true
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                enabled = !isChecking
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = DouyinCyan)
                                } else {
                                    Text("检查更新", fontSize = 12.sp, color = DouyinCyan)
                                }
                            }
                            Text("v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    AboutItem(label = "开源协议", value = "MIT License")
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Code, null, tint = DouyinCyan, modifier = Modifier.size(20.dp))
                        Text("技术架构", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    TechItem("开发语言", "Kotlin", Icons.Outlined.Brush)
                    TechItem("UI框架", "Jetpack Compose", Icons.Outlined.ViewQuilt)
                    TechItem("设计规范", "Material Design 3", Icons.Outlined.Palette)
                    TechItem("网络库", "OkHttp3", Icons.Outlined.Cloud)
                    TechItem("JSON解析", "Gson", Icons.Outlined.DataObject)
                    TechItem("图片加载", "Coil", Icons.Outlined.Image)
                    TechItem("视频播放", "Media3 ExoPlayer", Icons.Outlined.PlayCircle)
                    TechItem("异步处理", "Kotlin Coroutines", Icons.Outlined.Autorenew)
                    TechItem("状态管理", "StateFlow", Icons.Outlined.AccountTree)
                    TechItem("本地数据库", "Room", Icons.Outlined.Storage)
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Star, null, tint = Color(0xFFFF9800), modifier = Modifier.size(20.dp))
                        Text("功能特性", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    FeatureItem("无水印下载抖音视频")
                    FeatureItem("支持图集批量下载")
                    FeatureItem("自动解析短链接")
                    FeatureItem("多线程并发下载")
                    FeatureItem("断点续传与自动重试")
                    FeatureItem("实时下载进度通知")
                    FeatureItem("下载历史记录管理")
                    FeatureItem("深色模式支持")
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Code, null, tint = Color(0xFF333333), modifier = Modifier.size(20.dp))
                        Text("开源信息", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    AboutItem(label = "开源协议", value = "MIT License")
                    AboutItem(label = "仓库地址", value = "github.com/knightlsy/DouyinDownloader")
                    Spacer(Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Outlined.OpenInNew, null, Modifier.size(18.dp), tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("访问 GitHub 仓库", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showUpdateDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            icon = {
                Icon(
                    if (updateInfo!!.hasUpdate) Icons.Outlined.SystemUpdate else Icons.Outlined.CheckCircle,
                    null,
                    tint = if (updateInfo!!.hasUpdate) DouyinCyan else Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    if (updateInfo!!.hasUpdate) "发现新版本" else "已是最新版本",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    if (updateInfo!!.hasUpdate) {
                        Text("当前版本: v${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = Color.Gray)
                        Text("最新版本: v${updateInfo!!.version}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        if (updateInfo!!.releaseNotes.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("更新内容:", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(updateInfo!!.releaseNotes, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    } else {
                        Text("当前已是最新版本 v${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                if (updateInfo!!.hasUpdate) {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(updateInfo!!.downloadUrl))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan)
                    ) {
                        Text("下载更新")
                    }
                } else {
                    Button(
                        onClick = { showUpdateDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = DouyinCyan)
                    ) {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                if (updateInfo!!.hasUpdate) {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后")
                    }
                }
            }
        )
    }
}

@Composable
private fun AboutItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun TechItem(name: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(16.dp), tint = DouyinPink)
            }
        }
        Column {
            Text(name, fontSize = 12.sp, color = Color.Gray)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(6.dp),
            shape = CircleShape,
            color = DouyinCyan
        ) {}
        Text(text, fontSize = 13.sp)
    }
}

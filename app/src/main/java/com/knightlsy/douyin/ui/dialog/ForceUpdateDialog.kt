package com.knightlsy.douyin.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.knightlsy.douyin.BuildConfig
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.LocalBgColors
import java.io.File

/**
 * 强制更新的进度阶段。
 *
 * 「点一下跳到浏览器」的老做法有两个致命问题：
 *   1. 完全没有完整性校验。下载候选里有 4 条公共镜像，任何一条返回篡改包
 *      或 HTML 错误页，用户都会装上去 —— 而强制更新下用户没有「不装」的选项。
 *   2. 用户没有进度感知，下到哪、下完要不要再点，全靠猜。
 * 现在改成应用内下载 → SHA256 比对 GitHub 公布的值 → 对得上才交给安装器。
 */
sealed interface UpdateStage {
    /** 还没开始，或上一次已重置。 */
    object Idle : UpdateStage
    /** 正在并发测速挑线路。 */
    object Probing : UpdateStage
    data class Downloading(val percent: Int, val detail: String) : UpdateStage
    /** 正在算 SHA256。 */
    object Verifying : UpdateStage
    /** 校验通过，已拉起系统安装器。 */
    object Installing : UpdateStage
    /**
     * DownloadManager 在这台机器上不工作，已回退到浏览器下载。
     * 这条路径没有完整性校验，界面必须明说。
     */
    data class FallbackToBrowser(val reason: String) : UpdateStage
    data class Failed(val message: String) : UpdateStage

    /**
     * 包已经下载并校验通过，只差系统没给「安装未知应用」权限。
     *
     * 这个状态必须能再次点按钮 —— 用户从授权页回来后要么自动继续（launcher 回调），
     * 要么手动再点一次，两条路都不能重新下载。
     */
    data class NeedsPermission(val file: File) : UpdateStage

    /** 这个阶段能不能再点「立即更新」。 */
    val canStart: Boolean
        get() = this is Idle || this is Failed || this is FallbackToBrowser || this is NeedsPermission
}

/**
 * 强制更新弹窗。
 *
 * 为什么不用 AlertDialog：
 *   AlertDialog 无法关掉系统返回键 —— 虽然 onDismissRequest 是空 lambda，
 *   但用户按返回键仍然能把它关掉，然后继续用旧版本。
 *   这里用原始 Dialog + DialogProperties 两个 false，从窗口层面禁掉
 *   「返回键」与「点外部」两条关闭路径。
 *
 * 为什么用不透明全屏遮罩：
 *   半透明的话用户能看到底下的界面，会以为「其实还能用」。
 *   这里铺满不透明底色，视觉上就是「应用停在这儿了」。
 *
 * 为什么留一个「重新检查」：
 *   下载页打不开、当时没网、或 GitHub 临时抽风时，用户不至于彻底卡死。
 *   它不是「跳过」，只是重试检查 —— 检查通过仍然会停在强制更新界面。
 */
@Composable
fun ForceUpdateDialog(
    version: String,
    releaseNotes: String,
    onUpdate: () -> Unit,
    onRetry: () -> Unit,
    isChecking: Boolean = false,
    stage: UpdateStage = UpdateStage.Idle,
    selectedLine: String? = null,
    passAllowed: Boolean = false,
    passRemainingMinutes: Long = 0L,
    onPass: () -> Unit = {},
) {
    val c = LocalBgColors.current

    // 忙碌态：正在测速/下载/校验/安装时按钮不可点，但文案要说明在做什么 ——
    // 强制更新界面里用户没有别的操作可做，只能盯着这里。
    val busy = stage is UpdateStage.Probing ||
        stage is UpdateStage.Downloading ||
        stage is UpdateStage.Verifying ||
        stage is UpdateStage.Installing

    val buttonLabel = when (stage) {
        is UpdateStage.Idle -> "↓  立即更新"
        is UpdateStage.Probing -> "正在测速选择线路…"
        is UpdateStage.Downloading -> "正在下载 ${stage.percent}%"
        is UpdateStage.Verifying -> "正在校验安装包…"
        is UpdateStage.Installing -> "正在打开安装器…"
        is UpdateStage.Failed -> "↻  重试下载"
        is UpdateStage.FallbackToBrowser -> "↻  重新尝试应用内下载"
        // 包已下载并校验通过，只差系统授权 —— 点这里直接进安装，不会重新下载
        is UpdateStage.NeedsPermission -> "↓  安装包已就绪 · 点此安装"
    }

    Dialog(
        onDismissRequest = { /* 不允许关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.paper)
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.fillMaxWidth()) {

                // 标记
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(c.accent)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = "必须更新",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.8.sp,
                        color = c.accent,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.ink,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "当前 v${BuildConfig.VERSION_NAME}  →  最新 v$version",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp,
                    color = c.ink3,
                )

                Spacer(Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.hair))
                Spacer(Modifier.height(16.dp))

                // 更新内容（限高可滚）
                Text(
                    text = "更新内容".uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    letterSpacing = 1.6.sp,
                    color = c.ink3,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = releaseNotes.ifBlank { "新版本已发布，请更新后继续使用。" },
                    fontSize = 12.5.sp,
                    lineHeight = 19.sp,
                    color = c.ink2,
                    modifier = Modifier
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                )

                Spacer(Modifier.height(24.dp))

                // 立即更新
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .background(if (busy) c.hair else c.accent)
                        .then(
                            if (!busy) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onUpdate,
                            ) else Modifier
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = c.ink2,
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = buttonLabel,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                letterSpacing = 1.0.sp,
                                color = c.ink2,
                            )
                        }
                    } else {
                        Text(
                            text = buttonLabel,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            letterSpacing = 1.4.sp,
                            color = c.accentInk,
                        )
                    }
                }

                // 下载进度：2dp 细线，和整体设计语言一致（不用 Material 的粗进度条）
                if (stage is UpdateStage.Downloading) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(c.hair)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth((stage.percent / 100f).coerceIn(0f, 1f))
                                .height(2.dp)
                                .background(c.accent)
                        )
                    }
                    if (stage.detail.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stage.detail,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            color = c.ink4,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 重新检查（网络失败时的出口，不是跳过）
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !isChecking && !busy,
                            onClick = onRetry,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isChecking) "正在重新检查…" else "更新失败？重新检查版本",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        letterSpacing = 0.6.sp,
                        color = if (isChecking || busy) c.ink4 else c.ink3,
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (selectedLine != null) {
                    Text(
                        text = "线路：$selectedLine",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.4.sp,
                        color = c.ink4,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (stage is UpdateStage.Failed) {
                    Text(
                        text = stage.message,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = c.accent,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (stage is UpdateStage.FallbackToBrowser) {
                    Text(
                        text = stage.reason,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = c.accent,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                if (stage is UpdateStage.NeedsPermission) {
                    Text(
                        text = "安装包已下载并校验完成。系统还没有允许本应用安装应用 —— " +
                            "点下面的按钮去开启授权，返回后会自动继续安装，不用重新下载。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = c.ink3,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }

                // 逃生口：连续 3 次失败后才会出现。正常情况下这段永远不渲染。
                if (passAllowed) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(BgRadius.r2))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPass,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "更新多次失败，暂时继续使用（剩 ${passRemainingMinutes / 60} 小时）",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            letterSpacing = 0.4.sp,
                            color = c.ink3,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }

                Text(
                    text = if (selectedLine.isNullOrBlank()) {
                        "此版本包含必要修复，不更新将无法继续使用。"
                    } else {
                        "安装包会校验 SHA256，与官方发布不一致会被丢弃。"
                    },
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = c.ink4,
                )
            }
        }
    }
}

package com.knightlsy.douyin.ui.screen.components

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.components.BgGhostButton
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 链接输入卡。
 *
 * 这一版按大厂输入框的通用模式重做了操作区，解决两个硬伤：
 *
 * 1. **粘贴按钮原来只在输入框为空时显示** —— 解析成功后 url 非空，粘贴按钮消失，
 *    用户想换一个视频解析就没有入口了，只能手动全选删除。现在粘贴是常驻的。
 *
 * 2. **没有清空按钮** —— 现在输入框有内容时右侧出现 ✕，点一下清空。
 *    这是微信、Chrome 地址栏、淘宝搜索框的共同做法：有内容就给你一个一键清空的出口。
 *
 * 布局逻辑（对齐大厂习惯）：
 *   · 输入框内的 ✕ 只负责「清空」，不做提交
 *   · 「粘贴」永远在左边，点了直接覆盖现有内容 —— 不用先清空再粘贴
 *   · 「解析」永远在右边，是主行动，粉底
 *   · 解析成功后输入框保留原文（用户能核对自己解析的是哪条），要换就点 ✕ 或直接粘贴
 *
 * 另外两处沿用上一版的修正：
 *   · 输入框两行（抖音分享文案几十字，单行会横向滚走）
 *   · 解析时收起键盘（hideSoftInputFromWindow + clearFocus 双保险）
 */
@Composable
fun LinkInputCard(
    url: String,
    isLoading: Boolean,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onParse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBgColors.current
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val view = LocalView.current

    /** 收起键盘 + 清除焦点，两者缺一不可。 */
    fun dismissKeyboard() {
        focusManager.clearFocus()
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun submit() {
        if (url.isBlank() || isLoading) return
        dismissKeyboard()
        onParse()
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BgSpace.gutter)
    ) {
        /* ── 输入框 ── */
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.surface)
                .border(1.dp, c.hair, RoundedCornerShape(BgRadius.r2))
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (url.isEmpty()) {
                    Text(
                        text = "粘贴抖音分享链接…",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = c.ink3,
                        lineHeight = 18.sp,
                    )
                }
                BasicTextField(
                    value = url,
                    onValueChange = onUrlChange,
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = c.ink,
                        lineHeight = 18.sp,
                    ),
                    // 两行：抖音分享文案长，单行会横向滚走
                    maxLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 清空：只在有内容时出现，触摸区 40dp（视觉 16dp）
            if (url.isNotEmpty() && !isLoading) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onUrlChange("") },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "清空",
                        tint = c.ink3,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(9.dp))

        /* ── 操作行：粘贴（常驻，左） + 解析（主行动，右） ── */
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BgGhostButton(
                text = "粘贴",
                enabled = !isLoading,
                height = 46.dp,
                onClick = {
                    dismissKeyboard()
                    onPaste()
                },
            )

            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .background(if (url.isNotBlank() && !isLoading) c.accent else c.hair)
                    .then(
                        if (url.isNotBlank() && !isLoading) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { submit() },
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.5.dp,
                            color = c.ink3,
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = "解析中",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp,
                            color = c.ink2,
                        )
                    }
                } else {
                    Text(
                        text = "解析",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp,
                        letterSpacing = 1.6.sp,
                        color = if (url.isNotBlank()) c.accentInk else c.ink3,
                    )
                }
            }
        }
    }
}

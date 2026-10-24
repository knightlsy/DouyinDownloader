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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 链接输入卡。
 *
 * 相比旧版改了三处：
 *
 * 1. **输入框允许两行**。抖音分享文案是「长按复制此条消息，打开抖音搜索…」这种
 *    几十字的东西，`singleLine = true` 会横向滚动，用户看不到自己粘对了没。
 *
 * 2. **解析时收键盘**。旧版点了解析键盘还赖着，挡住下面的结果。可靠做法是
 *    `InputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)` 配合
 *    `focusManager.clearFocus()` —— 只做一个都不可靠：
 *      只 clearFocus → 输入框失焦但键盘还在
 *      只 hideSoftInput → 有时输入框又把焦点抢回来
 *    另外 `SoftwareKeyboardController.hide()` 在百度/搜狗输入法下会返回 null，
 *    调了没反应，所以不能用它。
 *
 * 3. **四个按钮压成两个**。旧的「粘贴」「清空」各占半行，「解析」再占一行。
 *    现在输入框右上角放一个「粘贴」，下面只有一颗「解析」。
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
        dismissKeyboard()
        onParse()
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BgSpace.gutter)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(RoundedCornerShape(BgRadius.r2))
                .background(c.surface)
                .border(1.dp, c.hair, RoundedCornerShape(BgRadius.r2))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
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
                if (url.isEmpty()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(BgRadius.r2))
                            .border(1.dp, c.hair, RoundedCornerShape(BgRadius.r2))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onPaste,
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "粘贴",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.5.sp,
                            letterSpacing = 0.8.sp,
                            color = c.ink2,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(9.dp))

        Box(
            Modifier
                .fillMaxWidth()
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
                        modifier = Modifier.width(14.dp).height(14.dp),
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

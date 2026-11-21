package com.knightlsy.douyin.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knightlsy.douyin.BuildConfig
import com.knightlsy.douyin.data.LogStore
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.data.ThemePreferences
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors

/**
 * 设置页：主题（三分段直选）、背景（壁纸氛围 / 强调色跟随壁纸）、日志入口、关于入口。
 *
 * ## 主题：分段控件，不再是循环点按
 * 旧实现点一下 SYSTEM→LIGHT→DARK 循环，用户看不到选项也不知道会切到哪。
 * 现在三个选项横排直接可选（[ThemeSegmented]），本页内不再有循环切换的 onClick。
 *
 * ## 背景：两个壁纸开关
 * 壁纸氛围 / 强调色跟随壁纸，只做持久化落库（写入 [ThemePreferences]），
 * 取色与渲染是下一阶段的事 —— 所以开关状态暂由本页直连 ThemePreferences
 * 读写，等渲染层接入后再上提到 ViewModel。
 *
 * ## 顶栏返回按钮
 * 设置页计划改为底部 Tab 直入，届时顶栏「← 返回」可能不再需要，
 * 但 AppNavigation 正在并行改造，先保留（入口从哪进都不崩）。
 */
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onNavigateToLogs: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onBack: () -> Unit,
    /**
     * 分段控件直选目标主题模式。
     *
     * 现有 AppNavigation 只接了 [onCycleTheme]（循环回调），还没有直选 setter ——
     * 这里给一个兼容默认值：按 [com.knightlsy.douyin.viewmodel.MainViewModel]
     * `cycleThemeMode()` 的固定顺序（SYSTEM → LIGHT → DARK → SYSTEM）把状态
     * 推进到目标模式，至多点两次，保证旧接线下分段控件依然可用。
     * AppNavigation 改造完成后请传真正的 setMode 类回调，届时该默认值与
     * [onCycleTheme] 参数可以一起删掉。
     */
    onSelectTheme: (ThemeMode) -> Unit = { target ->
        var cursor = themeMode
        while (cursor != target) {
            cursor = when (cursor) {
                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                ThemeMode.LIGHT -> ThemeMode.DARK
                ThemeMode.DARK -> ThemeMode.SYSTEM
            }
            onCycleTheme()
        }
    },
) {
    val c = LocalBgColors.current

    // 壁纸两开关：本阶段只落库不渲染，直连 ThemePreferences（见类注释）。
    val context = LocalContext.current
    val prefs = remember { ThemePreferences(context) }
    var wallpaperMood by remember { mutableStateOf(prefs.wallpaperMood()) }
    var accentFromWallpaper by remember { mutableStateOf(prefs.accentFromWallpaper()) }

    fun toggleWallpaperMood(enabled: Boolean) {
        prefs.setWallpaperMood(enabled)
        wallpaperMood = enabled
    }

    fun toggleAccentFromWallpaper(enabled: Boolean) {
        prefs.setAccentFromWallpaper(enabled)
        accentFromWallpaper = enabled
    }

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
                .padding(start = BgSpace.gutter, end = BgSpace.gutter, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(c.accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "设置",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.8.sp,
                color = c.ink,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "← 返回",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = c.ink2,
                modifier = Modifier
                    .clip(RoundedCornerShape(BgRadius.r2))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack,
                    )
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        BgHair()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BgSpace.gutter)
                .padding(top = 18.dp, bottom = 40.dp)
        ) {
            BgSectionLabel("外观", accent = null)

            SettingsCard(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Text(
                    text = "主题",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.W500,
                    color = c.ink,
                )
                Spacer(Modifier.height(11.dp))
                ThemeSegmented(
                    selected = themeMode,
                    onSelect = onSelectTheme,
                )
            }

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("背景", accent = null)

            SettingsCard(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 10.dp),
            ) {
                SettingsSwitchRow(
                    title = "壁纸氛围",
                    description = "背景跟随桌面壁纸，压到很淡，只影响色温",
                    checked = wallpaperMood,
                    onChange = ::toggleWallpaperMood,
                )
                RowDivider()
                SettingsSwitchRow(
                    title = "强调色跟随壁纸",
                    description = "从壁纸取色替代橙色。关闭时固定用橙色 #FF7A3A",
                    checked = accentFromWallpaper,
                    onChange = ::toggleAccentFromWallpaper,
                )
                RowDivider()
                Text(
                    text = "需要 Android 12 及以上。更低版本会自动降级为预先模糊的静态背景。",
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = c.ink4,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("诊断", accent = null)

            SettingsCard(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            ) {
                SettingsNavRow(
                    title = "日志",
                    value = if (LogStore.isEnabled()) "${LogStore.lineCount()} 条" else "已关闭",
                    onClick = onNavigateToLogs,
                )
            }

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("关于", accent = null)

            SettingsCard(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
            ) {
                SettingsNavRow(
                    title = "关于抖音下载器",
                    value = "v${BuildConfig.VERSION_NAME}",
                    onClick = onNavigateToAbout,
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
   卡片容器 / 行组件（本页私有，等视觉组件库稳定后再下沉）
   ═══════════════════════════════════════════════════════ */

/**
 * 设置卡片：暖调玻璃底 + 1dp 细边 + 20dp 圆角（design/settings.html）。
 * 深浅主题都走 [LocalBgColors] token（surface / hair），
 * 对应设计稿的 rgba(255,255,255,.042) 底与 .13 边线。
 */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalBgColors.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .border(1.dp, c.hair, RoundedCornerShape(20.dp))
            .padding(contentPadding),
        content = content,
    )
}

/** 行间细分隔线。 */
@Composable
private fun RowDivider() {
    val c = LocalBgColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.hair.copy(alpha = 0.5f))
    )
}

/** 可跳转的设置行：标题 + 单值 + ›。行高 ≥56dp。 */
@Composable
private fun SettingsNavRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val c = LocalBgColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontFamily = FontFamily.SansSerif,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.W500,
            color = c.ink,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = c.ink3,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "›",
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            color = c.ink3,
        )
    }
}

/** 开关行：标题 + 描述 + 自绘开关。行高 ≥56dp。 */
@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalBgColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.W500,
                color = c.ink,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = description,
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.5.sp,
                lineHeight = 17.sp,
                color = c.ink3,
            )
        }
        Spacer(Modifier.width(14.dp))
        BgSettingSwitch(
            checked = checked,
            onChange = onChange,
        )
    }
}

/* ═══════════════════════════════════════════════════════
   分段控件（主题模式三分选，本页私有实现）
   视觉参照 design/settings.html：
     - 选中：rgba(255,255,255,.13) 底 + 白字 + 字重 650
     - 未选：透明底 + 三级文字色
     - 整行 44dp 触控，内项 ≥38dp 高
   ═══════════════════════════════════════════════════════ */

@Composable
private fun ThemeSegmented(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
) {
    val options = listOf(
        ThemeMode.SYSTEM to "跟随系统",
        ThemeMode.DARK to "深色",
        ThemeMode.LIGHT to "浅色",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, LocalBgColors.current.hair, RoundedCornerShape(14.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (mode, label) ->
            val on = mode == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) Color.White.copy(alpha = 0.13f) else Color.Transparent)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onSelect(mode) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight(if (on) 650 else 600),
                    color = if (on) LocalBgColors.current.ink else LocalBgColors.current.ink3,
                )
            }
        }
    }
}

/* ═══════════════════════════════════════════════════════
   自绘开关（视觉 48x28，热区 44dp）
     - 关：raise 底 + hair 边，knob 跟随主题（深色主题下近白）
     - 开：accent（#FF7A3A）底 + 同色边，knob 压橙底色 accentInk
   不依赖 BgControls 里尚未完工的开关组件。
   ═══════════════════════════════════════════════════════ */

@Composable
private fun BgSettingSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val c = LocalBgColors.current
    val knobStart by animateDpAsState(
        targetValue = if (checked) 23.dp else 3.dp,
        label = "knobStart",
    )
    Box(
        Modifier
            .size(width = 56.dp, height = 44.dp) // 44dp 热区
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onChange(!checked) }
            .semantics { role = Role.Switch },
        contentAlignment = Alignment.Center,
    ) {
        val trackShape = RoundedCornerShape(14.dp)
        Box(
            Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(trackShape)
                .background(if (checked) c.accent else c.raise)
                .border(1.dp, if (checked) c.accent else c.hair, trackShape)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = knobStart)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) c.accentInk else c.ink)
            )
        }
    }
}

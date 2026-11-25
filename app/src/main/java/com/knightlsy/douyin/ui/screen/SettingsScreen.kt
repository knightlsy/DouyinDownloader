package com.knightlsy.douyin.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.knightlsy.douyin.ui.components.BgSegmented
import com.knightlsy.douyin.ui.components.BgHair
import com.knightlsy.douyin.ui.components.BgSectionLabel
import com.knightlsy.douyin.ui.theme.BgRadius
import com.knightlsy.douyin.ui.theme.BgSpace
import com.knightlsy.douyin.ui.theme.LocalBgColors
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.zz.douyin.FilterPreferences
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.alpha
import com.zz.douyin.XposedServiceBridge

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
    /** 壁纸氛围开关的当前值。由 ViewModel 驱动，Theme 读同一个源。 */
    wallpaperMood: Boolean = false,
    onWallpaperMoodChange: (Boolean) -> Unit = {},
    /** 强调色跟随壁纸开关的当前值。 */
    accentFromWallpaper: Boolean = false,
    /** 壁纸读取诊断（AppNavigation 从 ViewModel 收集后传入）。 */
    wallpaperDiag: String = "",
    /** 是否已设置自定义背景图。 */
    hasCustomBackdrop: Boolean = false,
    /** 相册选中一张图后回调（复制 + 应用由 ViewModel 负责）。 */
    onPickBackdrop: (android.net.Uri) -> Unit = {},
    onClearBackdrop: () -> Unit = {},
    onAccentFromWallpaperChange: (Boolean) -> Unit = {},
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

    // 壁纸两开关的状态由 ViewModel 持有 —— Theme 读同一份源。
    // 留在这里会与同名参数（wallpaperMood / accentFromWallpaper）冲突。
    fun toggleWallpaperMood(enabled: Boolean) = onWallpaperMoodChange(enabled)

    fun toggleAccentFromWallpaper(enabled: Boolean) = onAccentFromWallpaperChange(enabled)

    Column(
        Modifier
            .fillMaxSize()
            // 不铺纸色：Theme 已经铺过底，这里再铺一层会把壁纸层整个盖住
            // （这就是「背景功能开了没效果」的根因 —— 跟壁纸读不到无关）
    ) {
        BgHair()

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = BgSpace.gutter)
                .padding(top = 18.dp, bottom = 40.dp)
        ) {
            val context = LocalContext.current

            /*
             * 这些开关写的是「远程偏好」—— 必须经 XposedService 拿，
             * 不能自己 getSharedPreferences。
             *
             * hook 侧的 getRemotePreferences 读的是 LSPosed 守护进程按
             * 「模块包名 + userId」管理的那一份，跟本 APK 的 shared_prefs
             * 目录是两个存储。直接写本地 XML 的话，开关能拨、值也存下来了，
             * 但 hook 永远读不到，而且没有任何报错。
             *
             * service 没连上（LSPosed 没启用本模块 / 管理器没起来）时
             * prefs() 返回 null，UI 上整块置灰 —— 不让用户拨一个空开关。
             */
            var bridgeConnected by remember { mutableStateOf(XposedServiceBridge.isConnected()) }
            var bridgeDesc by remember { mutableStateOf(XposedServiceBridge.describe()) }
            var moduleEnabled by remember { mutableStateOf(true) }
            var immersiveEnabled by remember { mutableStateOf(true) }
            var skipAds by remember { mutableStateOf(FilterPreferences.DEFAULT_SKIP_ADS) }
            var skipImages by remember { mutableStateOf(FilterPreferences.DEFAULT_SKIP_IMAGES) }
            var skipLives by remember { mutableStateOf(FilterPreferences.DEFAULT_SKIP_LIVES) }

            // 从远程偏好重读全部开关。service 绑定是异步的，
            // 首次进入设置页时可能还没连上，所以连接状态变化也要触发重读。
            fun reloadModulePrefs() {
                bridgeConnected = XposedServiceBridge.isConnected()
                bridgeDesc = XposedServiceBridge.describe()
                val prefs = XposedServiceBridge.prefs() ?: return
                moduleEnabled = FilterPreferences.readModuleEnabled(prefs)
                immersiveEnabled = FilterPreferences.readImmersiveEnabled(prefs)
                val values = FilterPreferences.read(prefs)
                skipAds = values.skipAds
                skipImages = values.skipImages
                skipLives = values.skipLives
            }

            LaunchedEffect(Unit) { reloadModulePrefs() }
            DisposableEffect(Unit) {
                val listener = Runnable { reloadModulePrefs() }
                XposedServiceBridge.addListener(listener)
                onDispose { XposedServiceBridge.removeListener(listener) }
            }

            // 写不进去（service 断开）就什么都不做，UI 那边已经置灰了
            fun writeModulePref(key: String, value: Boolean) {
                XposedServiceBridge.prefs()?.edit()?.putBoolean(key, value)?.apply()
            }

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
                    title = "强调色跟随背景图",
                    description = "从背景图取色替代橙色。关闭时固定用橙色 #FF7A3A",
                    checked = accentFromWallpaper,
                    onChange = ::toggleAccentFromWallpaper,
                )
                RowDivider()
                // 相册自选背景图 —— 不走 WallpaperManager，绕开 ROM 限制。
                // 系统壁纸读取在很多国产 ROM 上被直接拒绝，那条路走不通时只剩这条。
                val picker = rememberLauncherForActivityResult(
                    ActivityResultContracts.PickVisualMedia()
                ) { uri -> if (uri != null) onPickBackdrop(uri) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(BgRadius.r2))
                        .clickable {
                            picker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(vertical = 14.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "自定义背景图",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = c.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (hasCustomBackdrop) "已设置 · 点击更换"
                            else "系统壁纸读不到？从相册挑一张，不依赖任何权限",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp,
                            color = c.ink3,
                        )
                    }
                    if (hasCustomBackdrop) {
                        Text(
                            text = "清除",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 12.sp,
                            color = c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(BgRadius.r1))
                                .clickable { onClearBackdrop() }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "选择",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.accent,
                    )
                }
                RowDivider()
            }

            Spacer(Modifier.height(22.dp))
            BgSectionLabel("增强模块", accent = null)
             /*
              * Xposed 功能已经内置在本应用里，不再需要单独装一个模块。
              * 开关写的是 FilterPreferences（content_filter 这个 prefs），
              * hook 侧通过 getRemotePreferences 读同一个文件 —— 名字必须严格一致，
              * 换成别的 prefs 名开关就形同虚设。
              *
              * 改动需要重启抖音才生效：hook 是在目标进程启动时安装的。
              */
             SettingsCard(
                 contentPadding = PaddingValues(horizontal = 18.dp, vertical = 4.dp),
             ) {
                 Column(Modifier.padding(vertical = 14.dp)) {
                     Text(
                         text = "Xposed 增强（已内置）",
                         fontFamily = FontFamily.SansSerif,
                         fontSize = 14.5.sp,
                         fontWeight = FontWeight.W500,
                         color = c.ink,
                     )
                     Spacer(Modifier.height(4.dp))
                     Text(
                         text = "在 LSPosed 里启用本应用、作用域勾选抖音，重启抖音后生效。" +
                             "不启用也完全不影响本应用的下载功能。",
                         fontFamily = FontFamily.SansSerif,
                         fontSize = 11.5.sp,
                         lineHeight = 17.sp,
                         color = c.ink3,
                     )
                     Spacer(Modifier.height(6.dp))
                     // 连接状态必须显式显示：没连上时开关是灰的，
                     // 不解释原因用户只会觉得「设置坏了」
                     Text(
                         text = if (bridgeConnected) {
                             "已连接 · $bridgeDesc"
                         } else {
                             "未连接 LSPosed，设置不可修改"
                         },
                         fontFamily = FontFamily.SansSerif,
                         fontSize = 11.sp,
                         color = if (bridgeConnected) c.accent else c.ink3,
                     )
                 }
                 RowDivider()
                 SettingsSwitchRow(
                     title = "启用模块功能",
                     // 两个方向的实际生效速度不同：
                     // 关 → 开：hook 压根没装（onPackageReady 直接 return），必须重启抖音
                     // 开 → 关：各 hook 内部有 OnSharedPreferenceChangeListener，近似即时
                     description = if (moduleEnabled) {
                         "已启用 · 关闭后立刻停止拦截"
                     } else {
                         "已停用 · 重新打开需要重启抖音"
                     },
                     checked = moduleEnabled,
                     enabled = bridgeConnected,
                     onChange = { enabled ->
                         moduleEnabled = enabled
                         writeModulePref(FilterPreferences.KEY_MODULE_ENABLED, enabled)
                     },
                 )
                 RowDivider()
                 SettingsSwitchRow(
                     title = "沉浸式播放",
                     description = "全屏沉浸、隐藏原生控件",
                     checked = immersiveEnabled,
                     enabled = bridgeConnected,
                     onChange = { enabled ->
                         immersiveEnabled = enabled
                         writeModulePref(FilterPreferences.KEY_IMMERSIVE_ENABLED, enabled)
                     },
                 )
                 RowDivider()
                 SettingsSwitchRow(
                     title = "跳过广告",
                     description = "自动划走信息流广告",
                     checked = skipAds,
                     enabled = bridgeConnected,
                     onChange = { enabled ->
                         skipAds = enabled
                         writeModulePref(FilterPreferences.KEY_SKIP_ADS, enabled)
                     },
                 )
                 RowDivider()
                 // 这两个默认是 true（FilterPreferences.DEFAULT_SKIP_IMAGES/LIVES），
                 // 之前没有入口 —— 用户在抖音里会莫名其妙被自动划走图文和直播，
                 // 却找不到任何开关能关掉。
                 SettingsSwitchRow(
                     title = "跳过图文",
                     description = "自动划走纯图文作品",
                     checked = skipImages,
                     enabled = bridgeConnected,
                     onChange = { enabled ->
                         skipImages = enabled
                         writeModulePref(FilterPreferences.KEY_SKIP_IMAGES, enabled)
                     },
                 )
                 RowDivider()
                 SettingsSwitchRow(
                     title = "跳过直播",
                     description = "自动划走直播推荐",
                     checked = skipLives,
                     enabled = bridgeConnected,
                     onChange = { enabled ->
                         skipLives = enabled
                         writeModulePref(FilterPreferences.KEY_SKIP_LIVES, enabled)
                     },
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
            .background(c.glass)
            .border(1.dp, c.glassEdge, RoundedCornerShape(20.dp))
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
            .padding(vertical = 10.dp)
            // 不可用时整行置灰。不是「点了没反应」，而是看起来就不能点 ——
            // 远程偏好拿不到时拨开关只会写进一份没人读的数据。
            .alpha(if (enabled) 1f else 0.45f),
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
            onChange = { if (enabled) onChange(it) },
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
    // 用 BgSegmented 而不是内联实现：滑块主体走 spring、移动时短暂拉伸、
    // 圆角跟着从 12dp 变 17dp 再收回，后面还跟一层延迟 60ms 的拖尾。
    // 内联版是「选中色块直接跳过去」，一个过渡都没有 —— 那是标签页，不是液态玻璃。
    val modes = remember { listOf(ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.LIGHT) }
    val labels = remember { listOf("跟随系统", "深色", "浅色") }
    BgSegmented(
        options = labels,
        selectedIndex = modes.indexOf(selected).coerceAtLeast(0),
        onSelect = { onSelect(modes[it]) },
        modifier = Modifier.fillMaxWidth(),
    )
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

package com.knightlsy.douyin

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.knightlsy.douyin.data.ThemeMode
import com.knightlsy.douyin.navigation.AppNavigation
import com.knightlsy.douyin.ui.theme.DouyinDownloaderTheme
import com.knightlsy.douyin.ui.update.ForceUpdateFlow
import com.knightlsy.douyin.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    /**
     * 与 Compose 里的 `viewModel()` 是同一个实例 —— 两者都挂在 Activity 的
     * ViewModelStore 上，且 MainViewModel 是 AndroidViewModel，默认 factory 可用。
     *
     * 在这里持有一份，是为了能在 onResume() 里触发剪贴板检查：
     * 用户从抖音复制链接后切回来，Activity 只是 resume，Compose 不会重组，
     * 放进 Activity 生命周期才能拿到这次「回到前台」的时机。
     */
    private val mainViewModel: MainViewModel by viewModels()

    /**
     * 壁纸刷新信号。
     *
     * 用户可能在别处换了壁纸再切回来，此时 Compose 没有任何状态变化、不会重组，
     * Theme 里的壁纸缓存就一直是旧的。每次 onResume 递增这个值，
     * Theme 收到新 key 后重新比对壁纸 ID，变了才真正重算。
     */
    private val wallpaperTick = mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        // 每次回到前台都看一眼剪贴板。autoPasteFromClipboard 内部只在
        // 输入框为空时才填，不会覆盖用户正在编辑的内容。
        mainViewModel.autoPasteFromClipboard()
        // 壁纸可能变了，让 Theme 重算一次（内部比对 ID，没变就直接命中缓存）
        wallpaperTick.intValue++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        setContent {
            val mainViewModel: MainViewModel = viewModel()
            val themeMode by mainViewModel.themeMode.collectAsState()
            // 跟随系统：直接读系统的深色状态，系统切换时 Compose 会重组，
            // 无需重建 Activity（Manifest 已声明 configChanges="uiMode"）。
            val systemDark = isSystemInDarkTheme()
            val isDarkMode = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val context = this@MainActivity

            // 背景只有一个来源：用户手动选的图。有图就画，没有就是纯纸色底。
            val hasCustomBackdrop by mainViewModel.hasCustomBackdrop.collectAsState()
            val accentFromWallpaper by mainViewModel.accentFromWallpaper.collectAsState()
            // 选/清除自定义背景图时由 ViewModel 递增；与 onResume 的 tick 相加，
            // 两者任一变化都会让 Theme 里的 remember 重算。
            val vmWallpaperTick by mainViewModel.wallpaperTick.collectAsState()

            DouyinDownloaderTheme(
                darkTheme = isDarkMode,
                wallpaperMood = hasCustomBackdrop,
                accentFromWallpaper = accentFromWallpaper,
                wallpaperRefreshKey = wallpaperTick.intValue + vmWallpaperTick,
            ) {
                // color 必须透明：Theme 在 content() 之前已经画好了纸色底 + 壁纸层，
                // Surface 再铺一层不透明 background 就会把壁纸整个盖掉。
                // 这是「背景功能无效」的第二层遮挡（第一层是各 Screen 的 background(c.paper)）。
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                ) {
                    AppNavigation(mainViewModel)
                }

                // 强制更新链路只依赖 context，不依赖 Activity，整体在 ui/update。
                // 必须放在 Theme 内部调用 —— 弹窗要读 LocalBgColors。
                ForceUpdateFlow(context)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

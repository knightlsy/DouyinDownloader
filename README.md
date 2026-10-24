# 抖音视频无水印下载器

> 现代化Material Design 3界面 | 适用于小米14Pro / Android 16

## 界面设计系统（v1.7.0）

整套 UI 重写。设计语言是 **A+D 混合**：骨架取「影院感」（内容当主角、UI 退到边缘），
配色取「抖音基因的克制版」（深紫黑底 + 粉青双色，但整体压暗、去渐变滥用）。

### 配色

所有色值由 OKLCH 精确转换而来，不是肉眼估的：

```
深色底   oklch(0.14 0.018 295)  → #0A0810
深色粉   oklch(0.68 0.21 12)    → #FE4F75   抖音原色 #FE2C55 同族，稍沉
深色青   oklch(0.80 0.13 195)   → #2AD7D7   抖音原色 #25F4EE 同族，降饱和
浅色粉   oklch(0.55 0.21 12)    → #CE1750   深色粉在白底会糊，必须压暗
浅色青   oklch(0.60 0.13 195)   → #009798
```

**纪律：一屏里粉青加起来不超过 4 处。** 粉只用于主行动（解析 / 下载 / 已选）与失败，
青只用于成功与次要标记，其余全部走中性灰。超过这个数就从「信号」退化成「装饰」。

### 几何

- 圆角统一 **2dp**（Material 默认 12–28dp 是「友好」语言，这个应用是工具，方角表达精确）
- 分隔只用 **1dp 细线**，不用卡片阴影与浮起
- 去掉 Scaffold + TopAppBar（顶栏从 64dp 降到 46dp），标题自己承担导航

### 排版

大标题用「系统无衬线 + Light 字重 + 负字距 + 大写」逼近窄体观感 —— 设计稿里的 Oswald
是窄体，但 Android 没有内置窄体，打包 Oswald 又会因为不含中文字形而露出 fallback 接缝。
等宽用于所有数据：体积、时长、分辨率、百分比、文件序号。

### 控件库

`ui/components/BgControls.kt`：

| 组件 | 替代 | 差异 |
|---|---|---|
| `BgProgressLine` | 圆环进度 | 2dp 细线，省一半高度 |
| `BgCheckBox` | Material Checkbox | 方角 17dp，触摸区 44dp |
| `BgSegmented` | FilterChip 组 | 一格反色，无胶囊外形 |
| `BgPrimaryButton` | Button | 粉底反白，全屏最多一处 |
| `BgGhostButton` | OutlinedButton | 只有一圈细线 |
| `BgWarn` | 提示 Card | 左侧 2dp 竖线，缺几项列几条 |

### 文件结构

`MainScreen.kt` 从 **1310 行**降到 **317 行**，其余拆到 `ui/screen/components/`：

```
ui/screen/MainScreen.kt              主函数：编排 + 路由
ui/screen/HistoryScreen.kt           历史
ui/screen/AboutScreen.kt             关于
ui/screen/components/StatsBar.kt     三格统计
ui/screen/components/LinkInputCard.kt  链接输入
ui/screen/components/VideoPreviewCard.kt
ui/screen/components/CollectionPreviewCard.kt
ui/screen/components/TaskCard.kt
ui/screen/components/VideoPlayerDialog.kt
ui/screen/components/ImageViewerDialog.kt
ui/Format.kt                         时长 / 计数 / 体积格式化
```

---

## v1.7.0 修掉的老问题

这一版顺带处理了五个长期存在的交互问题。其中两个之前**以为修过**，
但 `git log -S` 显示这个仓库的历史里从来没有相关提交 —— 大概是在别的工作区改的，没提交上来。

### 1. 播放器看不见播放/暂停按钮

**现象**：打开播放器只有一块画面，什么按钮都没有，点哪儿都没反应。

**真实原因**（和网上流传的说法不一样）：media3 的 `Player` composable **没有**
`showControls` 这个参数。控制层显隐是通过 `centerControls` / `bottomControls`
两个 lambda 的**第二个参数 `visible`** 传下来的，默认状态是「隐」（要点画面才出现）。
旧代码把 `visible` 原样透传给了 `PlayerDefaults.CenterControls`，所以控制层跟着内部状态一起隐掉。

**改法**：`centerControls = { p, _ -> ... }`，丢弃第二参数，`visible` 写死 `true`。
代价是画面被按钮挡一点，但「看得见怎么操作」比「纯净画面」重要。

### 2. 解析后键盘不收起

**现象**：点「解析」后输入框失焦，但键盘还挡在下面。

**真实原因**：`SoftwareKeyboardController.hide()` 在百度输入法、搜狗输入法下经常返回
`null` 或调了无效。

**改法**（两个必须一起做，缺一个都不可靠）：

```kotlin
focusManager.clearFocus()
val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
imm?.hideSoftInputFromWindow(view.windowToken, 0)
```

只 `clearFocus()` → 输入框失焦但键盘还在；
只 `hideSoftInput` → 有时输入框又把焦点抢回来。

### 3. 输入框只能单行

抖音分享文案是「长按复制此条消息，打开抖音搜索…」这种几十字的东西。
`singleLine = true` 会横向滚动，用户看不到自己粘对了没。
→ 改 `maxLines = 2` 自动换行。

### 4. 任务卡整卡可点，跟内部按钮打架

真机 dump 出卡片区有 3 个 clickable，但代码里只有单个通栏按钮 —— 手指落在图标边缘
会先触发整卡。→ 卡片本身去掉 `clickable`，只有图标可点。

### 5. 图标触摸区 32dp

`IconButton(Modifier.size(32.dp))` 的可点区域就 32dp，低于 Material 建议的 48dp。
→ 视觉仍是 20dp 图标，外层用 44dp 透明区包住。

### 另外修掉的两个小问题

- **播放器封面图标错了**：旧版播放按钮用的是 `ic_cloud_download`（云+下箭头），
  放在画面中央看着像「下载」不像「播放」。→ 换 `Icons.Filled.PlayArrow`。
- **`player.release()` 调了两次**（`onDispose` + 关闭按钮各一次）。
  ExoPlayer 的 release 幂等不会崩，但两处都管生命周期，将来加暂停恢复逻辑会踩空。
  → 统一交给 `DisposableEffect`。

---

## 签名密钥与覆盖升级

**问题**：v1.6.9 及更早的 Release，每个版本的签名都不一样。

根因是 CI 上没有固定的 keystore，`signingConfigs.release` 不存在时回退到 debug 签名，
而 GitHub Actions 每个 runner 的 `~/.android/debug.keystore` 都是新生成的 ——
所以 v1.6.9 的证书指纹是 `1549a11d…`，v1.7.0 第一次构建出来变成 `1b074e46…`，两者无法互相覆盖。

**修复**（v1.7.0 起）：

1. 生成固定的 `release-key.jks`（RSA 4096，有效期 10950 天，DN 与 BgCamera 一致）
2. base64 后存进三个 GitHub Secrets：
   - `DOUYIN_KEYSTORE_B64`
   - `DOUYIN_STORE_PASSWORD`
   - `DOUYIN_KEY_PASSWORD`
3. `build.yml` 在构建前把 base64 还原成 `release-key.jks`
4. `build.gradle.kts` 一旦检测到 keystore + 两个口令都存在，就用 release 签名

**当前正式签名**（可用于校验下载到的 APK）：

```
DN      : CN=DouyinDownloader, OU=Mobile, O=knightlsy, L=Mianyang, ST=Sichuan, C=CN
SHA-256 : E8:EA:E2:6F:FE:F2:55:85:A8:EA:A9:32:BE:06:5F:90:50:BB:13:23:0A:D3:AF:76:F3:2B:7E:0A:16:B9:A1:C7
有效期  : 2026-10-24 → 2056-10-16
```

**代价**：v1.7.0 是切换签名的分界点，**必须先卸载 v1.6.9 再装**。
从 v1.7.0 往后，所有版本都能直接覆盖升级。

密钥文件不入库（`.gitignore` 里已排除 `*.jks`），本地副本在项目根目录。

---

## 功能特点

### 核心功能
- ✅ 无水印下载抖音视频
- ✅ 支持图集批量下载
- ✅ 自动解析短链接
- ✅ 实时下载进度显示
- ✅ 下载历史记录管理
- ✅ 云端记录同步（多设备共享）

### 性能优化
- ⚡ 多线程并发下载
- ⚡ 断点续传支持
- ⚡ 自动重试机制（最多3次）
- ⚡ 智能连接池管理
- ⚡ 后台下载支持

### 界面特性
- 🎨 Material Design 3 现代化UI
- 🎨 抖音风格配色（粉色+青色）
- 🎨 流畅动画过渡
- 🎨 深色模式支持
- 🎨 响应式布局

## 编译APK

### 方法1：Android Studio（推荐）

1. 下载安装 [Android Studio](https://developer.android.com/studio)
2. 打开 Android Studio
3. 选择 `File -> Open`，选择 `DouyinDownloader` 文件夹
4. 等待Gradle同步完成
5. 点击 `Build -> Build Bundle(s) / APK(s) -> Build APK(s)`
6. APK生成在 `app/build/outputs/apk/debug/app-debug.apk`

### 方法2：命令行编译

```bash
# 进入项目目录
cd DouyinDownloader

# 编译Debug APK
./gradlew assembleDebug

# APK位置
# app/build/outputs/apk/debug/app-debug.apk
```

## 安装到手机

1. 将APK文件传输到手机
2. 在手机上点击APK文件安装
3. 允许安装未知来源应用
4. 完成安装

## 使用方法

### 下载视频
1. 打开抖音App，找到想要下载的视频
2. 点击分享按钮，选择"复制链接"
3. 打开"抖音下载器"App
4. 点击"粘贴链接"按钮或手动粘贴
5. 点击"解析视频"
6. 预览视频信息，点击"下载无水印视频"

### 下载图集
1. 找到想要下载的图文笔记
2. 复制分享链接
3. 粘贴到App中解析
4. 点击"下载全部X张图片"

## 项目结构

``` 
app/src/main/java/com/knightlsy/douyin/
├── MainActivity.kt           入口 + 三屏路由（main / history / about）
├── App.kt                    Application（Coil 图像加载器配置）
├── data/                     数据层
│   ├── Models.kt             ContentInfo / DownloadTask / ContentType
│   ├── VideoRepository.kt    解析 + 下载 + 重试（最大的文件）
│   ├── WebViewParser.kt      WebView 兜底解析通道
│   ├── MotionPhotoMuxer.kt   实况图合成（JPEG + MP4 → Motion Photo）
│   ├── GalleryRepository.kt  相册读写
│   ├── AppDatabase.kt        Room 数据库
│   ├── HistoryDao.kt / HistoryEntity.kt / HistoryRepository.kt
│   ├── ThemePreferences.kt   主题三态持久化
│   ├── UpdateChecker.kt      版本更新检查
│   └── ParseDiag.kt          解析失败诊断（复制到剪贴板）
├── screen/
│   ├── MainScreen.kt         主函数：编排 + 路由（317 行）
│   ├── HistoryScreen.kt      下载历史
│   ├── AboutScreen.kt        关于 + 检查更新
│   └── components/           首页各区块（v1.7.0 从 MainScreen 拆出）
│       ├── StatsBar.kt              三格统计
│       ├── LinkInputCard.kt         链接输入（含键盘收起）
│       ├── VideoPreviewCard.kt      视频预览
│       ├── CollectionPreviewCard.kt 图集预览（3 列网格 + 勾选）
│       ├── TaskCard.kt              下载任务（细线进度）
│       ├── VideoPlayerDialog.kt     播放器（控制层常驻）
│       └── ImageViewerDialog.kt     全屏看图（可翻页）
├── ui/
│   ├── Format.kt             时长 / 计数 / 体积格式化
│   ├── components/
│   │   └── BgControls.kt     BgProgressLine / BgCheckBox / BgSegmented /
│   │                         BgPrimaryButton / BgGhostButton / BgWarn …
│   └── theme/
│       ├── Color.kt          A+D 配色 token（OKLCH 转换）
│       ├── Type.kt           三字族排版
│       └── Theme.kt          双主题 + 逐色过渡（420ms）
└── viewmodel/
    ├── MainViewModel.kt      解析 / 下载 / 主题 / 状态机
    └── HistoryViewModel.kt   历史增删改查
```


## 技术栈

- **语言**: Kotlin
- **UI框架**: Jetpack Compose
- **设计规范**: Material Design 3
- **网络库**: OkHttp3
- **图片加载**: Coil
- **异步处理**: Kotlin Coroutines
- **状态管理**: StateFlow

## 权限说明

| 权限 | 用途 |
|------|------|
| INTERNET | 访问网络下载内容 |
| ACCESS_NETWORK_STATE | 检测网络状态 |
| WRITE_EXTERNAL_STORAGE | 保存文件（Android 9及以下） |
| READ_MEDIA_IMAGES | 读取图片（Android 13+） |
| READ_MEDIA_VIDEO | 读取视频（Android 13+） |
| POST_NOTIFICATIONS | 显示下载通知（Android 13+） |

## 下载位置

- **视频**: `Movies/Douyin/`
- **图片**: `Pictures/Douyin/`

## 常见问题

**Q: 为什么安装时提示"未知来源"？**
A: 需要在设置中允许安装未知来源应用

**Q: 下载速度慢怎么办？**
A: 确保网络连接稳定，App支持断点续传和自动重试

**Q: 如何取消下载？**
A: 在下载任务列表中点击关闭按钮

**Q: 图集可以单独下载某一张吗？**
A: 目前支持批量下载全部，后续版本会添加单张下载

## 更新日志

### v1.7.0
- **界面全部重写**：A+D 混合设计语言（影院感骨架 + 克制的粉青配色），深浅双主题
- **修复：播放器控制层不显示** —— media3 `Player` 没有 `showControls` 参数，
  真正的开关是 `centerControls` / `bottomControls` 的 `visible` 参数
- **修复：解析后键盘不收起** —— `hideSoftInputFromWindow` + `clearFocus` 双保险
- **修复：输入框单行裁切** —— 改两行
- **修复：任务卡点击区域打架** —— 去掉整卡可点，图标触摸区 32dp → 44dp
- **修复：播放器封面用了下载图标** —— 换成 PlayArrow
- **重构：MainScreen 1310 行拆成 10 个文件**
- 播放器新增「下载此视频」，不用关掉回首页


### v1.5.1 (2026-09-24)
- 修复实况图仍下载成静态图的问题：动态视频数据的字段名不匹配，导致动态照片合成从未真正生效；现在实况图下载即为可播放的动态照片
- 图集图片改用更高码率的编码版本保存，同等分辨率下画质明显提升（原为压缩率更高的格式，暗部与细节损失较大）

### v1.5.0 (2026-09-23)
- 实况图下载升级：动态视频不再单独保存，而是直接合成为「动态照片」格式（Motion Photo）——下载一张图，小米/Google 等支持动态照片的相册里点开即播，无需另找视频文件
- 保存的文件名带 _live 后缀，便于区分实况图

### v1.4.9 (2026-09-23)
- 支持实况图（Live Photo）：图集里含实况图时，下载会同时保存动态视频版本（MP4），图集角标会显示「N张实况」
- 选择性下载图片时动态版本同步对应

### v1.4.8 (2026-09-23)
- 修复发布版本解析全部失败的核心问题：打包配置错误导致数据解析组件被裁剪混淆（此前仅调试构建可正常解析）
- 该问题自启用代码压缩的版本起存在，本次为根因级修复

### v1.4.7 (2026-09-23)
- 修复选择逻辑缺陷：此前页面数据解析后只取第一条，若恰为无效条目（如推荐流视频）即整体失败；现优先精确匹配目标内容、其次第一条有效条目，并保留树状解析兜底
- 解析失败诊断新增关键环节提示（树解析异常/无有效条目等）

### v1.4.6 (2026-09-23)
- 修复推荐流干扰导致的解析失败：现按分享链接精确匹配目标视频/图集，不再被页面里的其他视频干扰
- 目标内容被风控剥空时给出更明确的诊断提示

### v1.4.5 (2026-09-23)
- 解析失败时诊断信息自动复制到剪贴板，直接粘贴反馈即可

### v1.4.4 (2026-09-23)
- 修复部分分享链接「无法获取内容信息」：页面数据结构变化导致提取失败，现改为智能解析（在整份数据里自动定位视频/图集内容块），不再依赖固定字段位置
- 解析诊断信息更详细（数据块内容标记），便于持续跟踪

### v1.4.3 (2026-09-23)
- 解析失败时显示各环节诊断详情（HTML/WebView 通道每步的结果），长按可复制反馈，便于快速定位问题

### v1.4.2 (2026-09-23)
- 修复 WebView 解析通道接口匹配不全导致的「无法获取内容信息」：按分享页最新 JS 逆向结果补齐视频/图集实际使用的数据接口（iteminfo/slidesinfo 等）
- 图集（多图）分享链接解析成功率提升
- WebView 拦截请求时透传页面原始请求头，进一步降低被风控拦截的概率

### v1.4.1 (2026-09-23)
- 修复抖音升级风控后解析全部失效的问题（分享页不再返回数据、接口签名被拦截）
- 新增内置 WebView 真实浏览器解析通道：普通解析失败时自动加载分享页并拦截抖音自己请求的数据，成功率大幅提升
- 解析更稳定，无需任何配置

### v1.3.9 (2026-06-30)
- 新增 WebView 解析通道：普通解析失败时自动改用内置 WebView 加载分享页并拦截数据接口，大幅提升解析成功率
- 解析过程更抗抖音接口调整，分享链接失效的概率更低
- 修复部分视频/图集提示"无法获取内容信息"的问题

### v1.4.0 (2026-06-30)
- 更新检测优先级调整：自有服务器优先，CDN 加速镜像次之，GitHub 直连兜底
- 修复更新弹窗逻辑错误（跳过的版本不再误报为强制更新）
- 版本比较改为数值比较，过滤 pre-release
- 签名密码移出仓库（改为环境变量/local.properties 读取）
- 开启 R8 代码与资源压缩，APK 体积减少约 27%
- 依赖升级：Kotlin 2.1.0 / AGP 8.7.3 / Compose BOM 2024.12.01
- 网络安全加固：默认禁明文流量，仅对抖音 CDN 相关域名放行
- 修复图集下载重复获取 ttwid、取消下载通知残留、重试后历史记录原始链接丢失等问题
- 清理死代码与冗余同步块，简化短链解析逻辑
- README 权限表与实际声明对齐

### v1.3.9 (2026-06-30)
- 检查更新改为直接查询 GitHub 最新 Release
- 新版本下载链接自动附加 GitHub CDN 加速前缀（直连失败时自动切换 ghproxy 等镜像）

### v1.3.8 (2026-06-30)
- 修复抖音更新解析接口后无法获取视频/图集信息的问题（解析请求新增 ttwid Cookie，失败时自动刷新重试）
- 下载请求同步携带 Cookie，提升下载稳定性

### v1.3.7 (2026-06-30)
- 修复云端同步关键错误

### v1.3.6 (2026-06-29)
- 修复启动时检查更新失败的问题
- 修复 APK 下载未走 Cloudflare CDN 中转的问题

### v1.3.5 (2026-06-29)
- 修复多项代码问题，提升稳定性
- 优化并发下载安全性
- 修复潜在崩溃风险

### v1.3.4 (2026-06-29)
- 优化更新弹窗：首次可选跳过，再次打开强制更新
- 优化关于页面布局

### v1.3.3 (2026-06-29)
- 新增启动时自动检查更新
- 新增强制更新弹窗
- APK 下载通过 Cloudflare CDN 中转加速

### v1.3.2 (2026-06-29)
- 新增检查更新功能
- 修复一些关键错误
- 提升性能与用户体验

### v1.3.1 (2026-06-29)
- 修复一些关键错误
- 提升性能与用户体验

### v1.2.1 (2026-06-28)
- 关于页面版本号改为动态读取 BuildConfig，不再硬编码

### v1.2.0 (2026-06-28)
- 粘贴按钮移至清空按钮左侧，解析按钮独立一行
- 下载缓冲 16KB → 64KB
- 并发下载 3 → 5
- OkHttp 连接池 8连接 / 15s超时
- Coil 图片缓存 25%内存 + 2%磁盘
- Compose 重组优化 derivedStateOf

### v1.1.0 (2026-06-13)
- 新增图集批量下载支持
- 优化下载速度（多线程并发）
- 增强稳定性（断点续传、自动重试）
- 改进UI交互体验

### v1.0.0 (2026-06-13)
- 初始版本发布
- 支持抖音视频无水印下载
- Material Design 3界面
- 基础下载管理功能

## 注意事项

- 仅供个人学习研究使用
- 请尊重原创作者版权
- 不要用于商业用途
- 下载的视频请勿未经授权传播

## License

MIT License

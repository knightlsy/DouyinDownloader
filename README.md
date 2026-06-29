# 抖音视频无水印下载器

> 现代化Material Design 3界面 | 适用于小米14Pro / Android 16

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
DouyinDownloader/
├── app/src/main/
│   ├── java/com/douyin/downloader/
│   │   ├── MainActivity.kt          # 入口Activity
│   │   ├── data/
│   │   │   ├── Models.kt            # 数据模型
│   │   │   └── VideoRepository.kt   # 下载仓库
│   │   ├── screen/
│   │   │   └── MainScreen.kt        # 主界面UI
│   │   ├── viewmodel/
│   │   │   └── MainViewModel.kt     # 业务逻辑
│   │   └── ui/theme/                # Material 3主题
│   ├── res/                          # 资源文件
│   └── AndroidManifest.xml
├── build.gradle.kts
└── README.md
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
| FOREGROUND_SERVICE | 后台下载支持 |
| WAKE_LOCK | 保持下载时屏幕常亮 |

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

### v1.3.1 (2026-06-29)
- 修复一些关键错误
- 提升性能与用户体验

### v1.3.0 (2026-06-29)
- 新增云端记录同步功能
- 使用 Android 设备识别码区分不同设备
- 解析记录自动同步到云端

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

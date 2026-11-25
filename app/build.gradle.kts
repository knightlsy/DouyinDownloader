import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.knightlsy.douyin"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.knightlsy.douyin"
        minSdk = 26
        targetSdk = 34
        versionCode = 83
        versionName = "1.10.0"

        // 云端同步记录接口。密钥不写进源码（仓库是公开的）：
        // 优先环境变量（CI secrets），其次 local.properties（本地构建，已 gitignore）。
        // 两者都没有 -> SYNC_API_KEY 为空 -> 运行时自动跳过云端同步（本地功能不受影响）。
        val apiProps = Properties()
        val apiPropsFile = rootProject.file("local.properties")
        if (apiPropsFile.exists()) {
            apiPropsFile.inputStream().use { stream -> apiProps.load(stream) }
        }
        val syncApiUrl = System.getenv("DOUYIN_SYNC_API_URL")
            ?: apiProps.getProperty("douyin.syncApiUrl") ?: "https://douyinapi.knightlsy.cn"
        val syncApiKey = System.getenv("DOUYIN_SYNC_API_KEY")
            ?: apiProps.getProperty("douyin.syncApiKey") ?: ""
        buildConfigField("String", "SYNC_API_URL", "\"$syncApiUrl\"")
        buildConfigField("String", "SYNC_API_KEY", "\"$syncApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        // 签名文件与密码都不入库：密钥本地放置，密码从 local.properties 或环境变量读取
        val keystoreFile = file("${rootProject.projectDir}/release-key.jks")
        val localProps = Properties()
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { stream -> localProps.load(stream) }
        }
        val storePass = System.getenv("DOUYIN_STORE_PASSWORD")
            ?: localProps.getProperty("douyin.storePassword") ?: ""
        val keyPass = System.getenv("DOUYIN_KEY_PASSWORD")
            ?: localProps.getProperty("douyin.keyPassword") ?: ""
        if (keystoreFile.exists() && storePass.isNotEmpty() && keyPass.isNotEmpty()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = "douyin-release"
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (signingConfigs.findByName("release") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            applicationIdSuffix = ".release"
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    /*
     * Java 17：合并进来的 Xposed hook 代码用了 instanceof 模式匹配
     * （`if (value instanceof Integer integer)`，Java 16+），
     * 1.8 下直接编译失败。抖仙人那边本来就是 17。
     */
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            // Xposed 声明文件必须在，不能被 shrinkResources 清掉
            merges += "META-INF/xposed/*"
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Xposed 模块能力。api 用 compileOnly：运行时的 XposedModule 由框架注入，
    // 打进 APK 会和宿主里的那份冲突。
    /*
     * libxposed 三个包用本地 jar，不用 Maven 坐标。
     *
     * 原因：AAR 元数据里要求 compileSdk >= 37，而 AGP 8.9 官方只认到 35，
     * SDK 37 平台在官方仓库里也还没发布（sdkmanager 报 Failed to find package
     * 'platforms;android-37'）。升 AGP 到 9.x 会牵动 Compose 和整个构建配置，
     * 为三个几十 KB 的 jar 不值。
     *
     * 三个包的职责：
     *  - api        compileOnly。XposedModule 等契约由框架在运行时注入，
     *               打进 APK 会和宿主里那份重复。
     *  - service    implementation。getRemotePreferences 的实现就在这里
     *               （io.github.libxposed.service.*、XposedProvider）。
     *               上一版删掉它，编译照样过，但模块加载时 NoClassDefFoundError。
     *  - interface  implementation。service 的传递依赖。
     *
     * 注意：service 的 AAR 里带一个 XposedProvider 声明，改成本地 jar 之后
     * 那部分 manifest 不会自动合并，已在 AndroidManifest.xml 里手写补上。
     */
    compileOnly(files("libs/libxposed-api.jar"))
    implementation(files("libs/libxposed-service.jar", "libs/libxposed-interface.jar"))
    // 抖仙人的音频转码（MP3 下载）依赖
    implementation("de.sciss:jump3r:1.0.5")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2026.05.00"))

    /**
     * Haze：真正的 backdrop blur（采样背后的内容 → 模糊 → 画到玻璃上）。
     *
     * 为什么必须引库：Compose 自带的 Modifier.blur() 只能模糊**自己**，
     * 拿不到身后的内容；RenderEffect 同理。要「透」就必须离屏采样背景，
     * 这套 RenderNode + 坐标映射 Haze 已经做对了。
     *
     * 版本锁 1.5.3：它依赖 kotlin-stdlib 2.1.0，本项目 Kotlin 2.2.10 向下兼容。
     * 2.x 依赖 Kotlin 2.4.21，会被 2.2.10 的编译器以 metadata 版本不符拒掉。
     */
    implementation("dev.chrisbanes.haze:haze:1.5.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("io.coil-kt:coil-video:2.5.0")

    // media3 1.11.1 + Compose material3 播放器（Player / PlayerDefaults / MiniController）
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("androidx.media3:media3-ui-compose:1.11.1")
    implementation("androidx.media3:media3-ui-compose-material3:1.11.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // JVM 单元测试（JUnit4）：此前 app 模块没有任何 test 依赖，测试覆盖为零。
    // 只加这一条 —— 解析层（DouyinJsonExtract/DouyinParser/ContentMapper）是
    // 纯 JVM 逻辑，Log 的替身已放在 test 源集里，不需要 Robolectric 这类重依赖。
    testImplementation("junit:junit:4.13.2")
}

/**
 * Kotlin 编译选项。
 *
 * 旧的 `android { kotlinOptions { jvmTarget = "17" } }` 在 Kotlin 2.x 已废弃，
 * 每次编译都会打一条 deprecation。新写法是顶层的 kotlin { compilerOptions { } }。
 */
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

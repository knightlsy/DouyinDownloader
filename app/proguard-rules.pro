# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep data models
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.douyin.downloader.data.** { *; }
-keep class com.knightlsy.douyin.data.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson TypeAdapter
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Coil
-dontwarn coil.**
-keep class coil.** { *; }

# AndroidX
-keep class androidx.** { *; }
-keep interface androidx.** { *; }

# Keep Application
-keep class com.douyin.downloader.** { *; }
-keep class com.knightlsy.douyin.** { *; }

# General
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Xposed 模块（从抖仙人合并进来）──
# 入口类由框架按 java_init.list 里的全限定名加载，名字绝不能变
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
# hook 代码全靠反射读抖音内部类，成员名和结构不能动
-keepclassmembers class com.zz.douyin.hook.** { *; }
-keep class com.zz.douyin.FilterPreferences { *; }
-keep class com.zz.douyin.HistoryReceiver { *; }
-keep class com.zz.douyin.SharedHistoryStore { *; }

@echo off
echo ==========================================
echo   抖音视频无水印下载器 - APK编译脚本
echo ==========================================
echo.

REM 检查Android SDK
if defined ANDROID_HOME (
    echo [信息] 检测到Android SDK: %ANDROID_HOME%
) else (
    echo [警告] 未检测到ANDROID_HOME环境变量
    echo [提示] 请先安装Android Studio或配置Android SDK
    echo.
)

REM 检查Java
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [错误] 未检测到Java JDK
    echo [提示] 请安装JDK 17或更高版本
    echo.
    pause
    exit /b 1
)

echo [1/3] 清理项目...
call gradlew clean

echo [2/3] 编译Debug APK...
call gradlew assembleDebug

if %errorlevel% equ 0 (
    echo [3/3] 编译成功！
    echo.
    echo APK位置:
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo 你可以将APK传输到手机安装使用
) else (
    echo [错误] 编译失败，请检查错误信息
)

echo.
pause

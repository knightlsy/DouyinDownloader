package android.util

/**
 * JVM 单元测试专用的 android.util.Log 替身（no-op）。
 *
 * 为什么需要它：AGP 给 unit test 挂的是 mockable android.jar，其中所有方法
 * 默认抛 "Method d in android.util.Log not mocked"。而被测的 DouyinParser /
 * ContentMapper 在**正常成功路径**上就会调 Log.d 记日志，不替身化的话
 * 每个解析测试都会在第一行日志处崩掉。
 *
 * 为什么放在 test 源集而不是开 `unitTests.isReturnDefaultValues = true`：
 * 这样完全不动 app/build.gradle.kts 的构建配置，替换范围也只影响测试进程；
 * 而且是"显式替身"而不是"静默返回默认值"，出问题时更容易定位。
 *
 * @JvmStatic 是必须的：主源集的字节码是对 Log.d 的 invokestatic 调用，
 * Kotlin object 没有 static 方法就接不上。测试类路径上本类排在
 * mockable jar 之前，JVM 会优先加载这一份。
 */
object Log {
    @JvmStatic fun v(tag: String?, msg: String?): Int = 0
    @JvmStatic fun d(tag: String?, msg: String?): Int = 0
    @JvmStatic fun i(tag: String?, msg: String?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?): Int = 0
    @JvmStatic fun w(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?): Int = 0
    @JvmStatic fun e(tag: String?, msg: String?, tr: Throwable?): Int = 0
    @JvmStatic fun println(priority: Int, tag: String?, msg: String?): Int = 0
    @JvmStatic fun isLoggable(tag: String?, priority: Int): Boolean = false
    @JvmStatic fun getStackTraceString(tr: Throwable?): String = tr?.toString() ?: ""
}

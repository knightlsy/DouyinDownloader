package com.knightlsy.douyin.ui

import java.util.Locale

/**
 * 展示层格式化工具。
 *
 * 从旧的 MainScreen 里抽出来 —— 那些是 private fun，拆文件后就够不着了。
 */

/** 毫秒 → mm:ss。时长 ≤ 0 时返回 00:00，避免显示 "-1:-1"。 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}

/** 点赞/评论数 → 1.7万 / 3.2亿。 */
internal fun formatCount(n: Long): String = when {
    n >= 100_000_000L -> String.format(Locale.US, "%.1f亿", n / 100_000_000.0)
    n >= 10_000L -> String.format(Locale.US, "%.1f万", n / 10_000.0)
    n >= 1_000L -> String.format(Locale.US, "%.1fk", n / 1_000.0)
    else -> n.toString()
}

/** 字节 → 12.4 MB。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 B"
    bytes >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

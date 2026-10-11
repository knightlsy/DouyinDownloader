package com.knightlsy.douyin.data

/**
 * 视频清晰度处理。
 *
 * 背景（2026-10 实测）：
 * 抖音 web 接口对同一条视频的清晰度由播放地址里的 `ratio` 参数决定，
 * 服务端按档位下发，**不是**由前端选择多个 bit_rate 数组（分享页的
 * `video.bit_rate` 恒为 null）。实测同一条 4K 源（2160x3840）的视频：
 *
 * | ratio            | 实际下发 | 大小      |
 * |------------------|----------|-----------|
 * | 480p             | 480p     | 2.9 MB    |
 * | 540p             | 540p     | 1.9 MB    |
 * | 720p             | 720p     | 2.2 MB    |
 * | 1080p 及以上      | 1080p    | 2.9 MB（封顶）|
 *
 * 即：web 接口最高只给到 **1080p**，再高的档位（1440p/2160p/4k/origin）
 * 都返回同一个 1080p 文件。源视频即使是 4K，web 端也拿不到 4K —— 这是
 * 接口侧限制，不是我们没找对参数（已穷举 ratio/域名/媒体类型验证）。
 *
 * `playwm`（带水印）路径不受 ratio 影响，恒返回 720p 带水印版本，
 * 所以必须走 `play`（无水印）路径。
 *
 * 因此策略是：**默认直接取 1080p**，用户无需选择；下载时按
 * 1080p → 720p → 540p 依次降级，避免某个档位不可用时整个下载失败。
 */
object VideoQuality {

    /** 期望的清晰度档位，从高到低。下载时依次尝试，首个可用的即采用。 */
    val LADDER = listOf("1080p", "720p", "540p")

    /** 对外展示的清晰度名称 */
    const val BEST_LABEL = "1080P"

    /** 从抖音播放地址中取 video_id */
    fun extractVideoId(url: String): String? =
        Regex("video_id=([^&]+)").find(url)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }

    /**
     * 规范化播放地址：去掉水印路径、把清晰度设为目标档。
     *
     * - `playwm` → `play`（带水印改无水印）
     * - `ratio=<任意>` → `ratio=<target>`；原地址没有该参数则追加
     *
     * 注意 ratio 的值可能带 `p` 后缀（`720p`），替换时必须整体替换，
     * 否则会拼出 `1080pp` 这种无效值。
     */
    fun rewrite(url: String, target: String): String {
        var u = url.replace("/playwm/", "/play/").replace("/playwm?", "/play?")
        u = if (Regex("[?&]ratio=[^&]*").containsMatchIn(u)) {
            u.replace(Regex("([?&])ratio=[^&]*"), "$1ratio=$target")
        } else {
            u + (if (u.contains("?")) "&" else "?") + "ratio=$target"
        }
        return u
    }

    /**
     * 由一条播放地址生成候选列表（高清在前）。
     *
     * 同一 video_id 在不同档位下服务端会返回不同清晰度的文件，因此直接
     * 按 [LADDER] 生成多条候选；下载时逐个尝试，谁先成功用谁。
     * 无法识别 video_id 时退化为「只规范化这一条」，仍然去掉水印。
     */
    fun candidates(url: String): List<String> {
        if (url.isEmpty()) return emptyList()
        return LADDER.map { rewrite(url, it) }.distinct()
    }

    /**
     * 由 bit_rate 数组（App 私有接口 / 未来接口变化时可能出现）生成候选。
     *
     * 若接口真的给出了多档位，优先用它的地址（带真实宽高，最准），
     * 并把它排在最前面。
     */
    fun candidatesFromBitRates(
        bitRates: List<Pair<String, Pair<Int, Int>>>,  // url to (width, height)
    ): List<String> {
        val sorted = bitRates
            .filter { it.first.isNotEmpty() }
            .sortedByDescending { it.second.first * it.second.second }
            .map { it.first }
        return sorted.distinct()
    }
}

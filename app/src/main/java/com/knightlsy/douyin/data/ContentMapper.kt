package com.knightlsy.douyin.data

import android.util.Log

/**
 * DTO 到 [ContentInfo] 的映射，以及视频/图片地址的挑选逻辑。
 *
 * 这里每个函数都是纯函数：给定输入必得同一输出，不碰网络不碰磁盘。
 * pickBestImageUrl 曾经踩过 webp/jpeg 的坑（见注释），最该有测试。
 */

/** 沿用 VideoRepo 这个 tag，保持 `adb logcat -s VideoRepo` 的过滤习惯不变。 */
private const val TAG = "VideoRepo"

internal fun ItemObj.toContentInfo(): ContentInfo? {
    val authorName = this.author?.nickname ?: "未知"
    val title = this.desc.ifEmpty { "未命名" }

    val videoUrl = this.video?.play_addr?.url_list?.firstOrNull()
        ?: this.video?.download_addr?.url_list?.firstOrNull()
    val cover = this.video?.cover?.url_list?.firstOrNull() ?: ""
    val duration = this.video?.duration ?: 0

    if (!this.images.isNullOrEmpty()) {
        // 每张图挑最优地址。
        //
        // 背景（2026-10 实测，slidesinfo 接口）：
        //   url_list          → 2160x2880，tplv-dy-aweme-images:q75  【无水印】
        //   download_url_list → 2160x2880，tplv-dy-water-v2          【带水印】
        //   分享页接口只给 1440x1920 —— 这就是改走 slidesinfo 的原因。
        //
        // 所以优先 url_list（同分辨率且无水印），只有它为空才退回 download_url_list。
        // jpeg 优先于 webp：同分辨率下码率约 2.3 倍，暗部/细纹理更好；
        // 且 Motion Photo 合成只接受 jpg 主图，webp 会让合成被跳过退化成静图。
        //
        // 一次遍历同时产出 imageUrls 与 animatedUrls：取不到静态图地址的那一项
        // 两个列表都跳过，保证下标严格一一对应（见 [ContentInfo.ImageCollection.animatedUrls]）。
        // 曾经 imageUrls 用 mapNotNull+filter 丢项、animatedUrls 却全量 map，
        // 某张图被风控剥空时两个列表长度不一致，实况视频会错位挂到别的静图上。
        val imageUrls = mutableListOf<String>()
        val animatedUrls = mutableListOf<String?>()
        for (img in this.images) {
            val best = pickBestImageUrl(img.url_list) ?: pickBestImageUrl(img.download_url_list)
            if (best.isNullOrEmpty()) continue // 这张图没有可用地址：静态图和实况视频两边都跳过
            imageUrls.add(best)
            animatedUrls.add(img.video?.let { pickLiveVideoUrl(it) })
        }
        if (imageUrls.isNotEmpty()) {
            // 实况图(Live Photo): images[i].video 存在时该图有动态视频版本。
            //
            // ★ 必须优先 play_addr，它才无水印。2026-10 实测同一条实况（slidessinfo）：
            //   play_addr     -> 337508 B, 720x960, 3.0s/90帧   【无水印】raw 原片
            //   download_addr -> 640306 B, 720x960, 6.0s/180帧  【带水印】
            //                    水印 = 抖音logo + 「抖音号: xxx」+ 搜索图标，固定在左上角
            //                    且 6s 版是 3s 动作重复两遍，每帧都有水印
            // download_addr 的 official play 接口 URL 里明确带 `watermark=1`：
            //   .../aweme/v1/play/?video_id=xxx&ratio=720p&watermark=1&media_type=2
            // 把该参数去掉就回到 337508 B 的无水印版 —— 但既然 play_addr 直接给了
            // 无水印地址，就没有必要自己拼参数（拼参数还依赖 video_id，易随接口变动失效）。
            // download_addr 仅作兜底：play_addr 为空时才用，用前尽量去掉 watermark 参数。
            val liveCount = animatedUrls.count { it != null }
            if (liveCount > 0) Log.d(TAG, "ImageCollection: ${imageUrls.size} images, live=$liveCount (play_addr/无水印优先)")
            return ContentInfo.ImageCollection(this.aweme_id, title, authorName, imageUrls.first(), imageUrls, this.create_time, animatedUrls)
        }
    }

    if (!videoUrl.isNullOrEmpty()) {
        Log.d(TAG, "Video: ${videoUrl.take(80)}")
        // 清晰度由 URL 的 ratio 参数决定，web 接口最高 1080p（实测 1440p/2160p
        // 返回的是同一个 1080p 文件）。这里直接生成候选地址，用户无需选择。
        val best = VideoQuality.rewrite(videoUrl, VideoQuality.LADDER.first())
        val candidates = VideoQuality.candidates(videoUrl).ifEmpty { listOf(best) }
        Log.d(TAG, "清晰度候选: ${candidates.size} 档, 最高 ${VideoQuality.BEST_LABEL}")
        return ContentInfo.Video(this.aweme_id, title, authorName, cover, best, duration, this.create_time,
            diggCount = this.statistics?.digg_count ?: 0,
            commentCount = this.statistics?.comment_count ?: 0,
            shareCount = this.statistics?.share_count ?: 0,
            width = this.video?.width ?: 0,
            height = this.video?.height ?: 0,
            candidateUrls = candidates
        )
    }

    return null
}

/**
 * 挑实况视频(MP4)的下载地址 —— 目标是**无水印**。
 *
 * 2026-10 实测同一条实况（slidesinfo 的 images[i].video）：
 *
 * | 来源           | 大小     | 时长/帧数    | 水印           |
 * |----------------|----------|--------------|----------------|
 * | `play_addr`    | 337508 B | 3.0s / 90f   | 无（原片）      |
 * | `download_addr`| 640306 B | 6.0s / 180f  | 有（每帧左上角） |
 *
 * `download_addr` 那条 `douyin.com/aweme/v1/play/` 地址显式带 `watermark=1`，
 * 返回的 MP4 左上角有「抖音 logo + 抖音号: xxx + 搜索图标」，而且时长翻倍
 * （同一段 3s 素材放两遍）。把 `watermark=1` 参数删掉就回到 337508 B 的无水印原片；
 * 但 `douyinvod.com` 那条路径本身带水印，删参数无效，所以只对 play 接口生效。
 *
 * 因此顺序是：play_addr[0] → download_addr 里能去水印的那条 → download_addr[0] 兜底。
 */
internal fun pickLiveVideoUrl(v: ImageVideoObj): String? {
    v.play_addr?.url_list?.firstOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
    val dl = v.download_addr?.url_list?.filter { it.isNotEmpty() }.orEmpty()
    dl.firstOrNull { it.contains("/aweme/v1/play/") }?.let { return stripWatermarkParam(it) }
    return dl.firstOrNull()
}

// 清晰度与水印处理已统一收敛到 VideoQuality（见 VideoQuality.kt）。
// 原先的 cleanUrl 正则不含 p 后缀，会把 ratio=1440p 改成 ratio=1080pp，故移除。

/**
 * 去掉抖音播放地址里的水印开关参数。
 *
 * `download_addr` 那条 `www.douyin.com/aweme/v1/play/` 地址带 `watermark=1`，
 * 拿到的 MP4 左上角会有「抖音logo + 抖音号: xxx」水印，且时长被拉长到两倍
 * （同段 3s 素材重复两遍）。实测把 `watermark=1` 删掉即回到无水印原片：
 *   watermark=1  -> 640306 B  带水印
 *   （删除）      -> 337508 B  无水印
 *
 * 只在 play_addr 没有地址、退回 download_addr 兜底时才需要调用。
 */
internal fun stripWatermarkParam(url: String): String {
    val stripped = url.replace(Regex("[?&]watermark=1(?=&|$)"), "").replace(Regex("[?&]watermark=0(?=&|$)"), "")
    // 去掉参数后可能出现 "?&" 或末尾多余的 "?"/"&"
    var u = stripped.replace("?&", "?").replace(Regex("[?&]$"), "")
    // 回归修复：watermark 是 query 的第一个参数时，上面的正则连 "?" 一起删掉，
    // 剩下 "&video_id=x" 这种残缺 query。此时若整串已无 "?" 但还有 "&"，
    // 把第一个 "&" 换回 "?" 即可恢复合法 URL。
    // 只在确实删过 watermark 参数（stripped 变短）时才修：没动过的 URL 必须原样返回。
    if (stripped != url && !u.contains('?') && u.contains('&')) {
        u = u.replaceFirst("&", "?")
    }
    return u
}

/**
 * 从同图的多个候选 URL 中挑最合适的。
 *
 * 抖音 images[i].url_list 里的条目是**同一张图的不同编码**, 顺序不固定:
 * 典型为 [webp, webp, jpeg]。webp 走 q75 有损压缩, 码率约为 jpeg 的 43%,
 * 暗部与细纹理损失更明显; 且 jpg 主图才能合成 Motion Photo(实况图)。
 * 因此优先 jpeg/jpg, 其次 png(无损), 都没有才回退第一个可用地址。
 */
internal fun pickBestImageUrl(urls: List<String>): String? {
    if (urls.isEmpty()) return null
    val usable = urls.filter { it.isNotEmpty() }
    if (usable.isEmpty()) return null
    fun pathOf(u: String) = u.substringBefore('?').lowercase()
    return usable.firstOrNull { pathOf(it).endsWith(".jpeg") || pathOf(it).endsWith(".jpg") }
        ?: usable.firstOrNull { pathOf(it).endsWith(".png") }
        ?: usable.firstOrNull()
}

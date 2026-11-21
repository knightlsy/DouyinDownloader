package com.knightlsy.douyin.data

/**
 * 抖音接口的 DTO。
 *
 * 从 VideoRepository 内的嵌套类提出来，改为同 package 顶层类 —— 这样解析层
 * 和映射层可以各自独立文件、独立测试，而所有既有引用（ItemObj 等）无需改 import。
 *
 * internal 而非 public：只在模块内可见，单测可直接访问。
 */

data class ItemObj(
    val aweme_id: String = "", val desc: String = "", val aweme_type: Int = 0,
    val author: AuthorObj? = null, val video: VideoObj? = null,
    val images: List<ImageObj>? = null, val create_time: Long = 0,
    val statistics: StatisticsObj? = null
)
data class AuthorObj(val nickname: String = "")
data class VideoObj(val play_addr: UrlObj? = null, val download_addr: UrlObj? = null, val cover: UrlObj? = null, val duration: Long = 0, val width: Int = 0, val height: Int = 0, val bit_rate: List<BitRateObj>? = null)
data class UrlObj(val url_list: List<String> = emptyList(), val data_size: Long = 0)

/**
 * 实况图(Live Photo)的视频部分。
 *
 * 字段名是单数 `video`（不是 `videos`），取自 slidesinfo 接口的 images[i]。
 * 实测 `play_addr` 是**无水印原片**（3.0s），`download_addr` 带 `watermark=1`
 * 水印且时长翻倍（6.0s）—— 取地址请走 [pickLiveVideoUrl]，不要直接用 download_addr。
 */
data class ImageVideoObj(
    val play_addr: UrlObj? = null,
    val download_addr: UrlObj? = null,
    val duration: Long = 0, val width: Int = 0, val height: Int = 0,
    val bit_rate: List<BitRateObj>? = null
)

/**
 * 图集里的单张图。
 *
 * url_list / download_url_list 是同一张图的两组地址：
 * - `url_list`          → 无水印(tplv-dy-aweme-images / tplv-dy-lqen-new)
 * - `download_url_list` → 带水印(tplv-dy-water-v2 / lqen-new-water)，但分辨率更高
 *
 * slidesinfo 接口会额外给出 `live_photo_type`(1=实况) 与 `video`(实况视频)，
 * 分享页接口这两项都没有 —— 这就是改走 slidesinfo 的原因。
 */
data class ImageObj(
    val url_list: List<String> = emptyList(),
    val download_url_list: List<String> = emptyList(),
    val video: ImageVideoObj? = null,
    val live_photo_type: Int = 0,
    val width: Int = 0, val height: Int = 0
)
data class BitRateObj(val gear_name: String = "", val bit_rate: Int = 0, val play_addr: UrlObj? = null)
data class StatisticsObj(val digg_count: Long = 0, val comment_count: Long = 0, val share_count: Long = 0)

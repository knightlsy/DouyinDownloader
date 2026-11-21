package com.knightlsy.douyin.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ContentMapper] 的测试：图片地址挑选、水印参数清理、DTO → ContentInfo 映射。
 *
 * 重点回归点（都踩过真实的坑，见各函数注释）：
 * - pickBestImageUrl 必须 jpeg > png > 回退第一个。曾经选中 webp 导致
 *   Motion Photo 合成失败退化成静图 —— 所以这里的断言要精确到具体 URL。
 * - stripWatermarkParam 删参数后不能留下 ?& / 末尾 ? 或 &。
 * - ImageObj 的实况视频字段名是单数 video（抖音接口的真实字段名）。
 */
class ContentMapperTest {

    private val gson = Gson()

    // ---------------- pickBestImageUrl ----------------

    @Test
    fun `jpeg优先于webp_即使webp排在前面`() {
        // 抖音 url_list 的典型顺序就是 [webp, webp, jpeg]，顺序不固定
        val urls = listOf(
            "https://p3-sign.douyinpic.com/tos/i/oU1bZDE.webp?tplv=dg=x&x-expires=1731000000",
            "https://p9-sign.douyinpic.com/tos/i/oU1bZDE2.webp?tplv=dg=x",
            "https://p3-sign.douyinpic.com/tos/i/oU1bZDE.jpeg?tplv=dg=x"
        )
        assertEquals(urls[2], pickBestImageUrl(urls))
    }

    @Test
    fun `带query参数的jpeg仍被识别`() {
        // 抖音图床 URL 一定带 query（签名/过期时间），扩展名判断不能被 query 干扰
        val urls = listOf(
            "https://p3-sign.douyinpic.com/tos/i/a1.webp?tplv=dg=item&x-expires=1731000000",
            "https://p3-sign.douyinpic.com/tos/i/a1.jpeg?tplv=dg=item&x-expires=1731000000"
        )
        assertEquals(urls[1], pickBestImageUrl(urls))
    }

    @Test
    fun `无jpeg时png次优`() {
        val urls = listOf(
            "https://p3-sign.douyinpic.com/tos/i/a1.webp?tplv=dg=x",
            "https://p3-sign.douyinpic.com/tos/i/a1.png"
        )
        assertEquals(urls[1], pickBestImageUrl(urls))
    }

    @Test
    fun `全webp时回退第一个`() {
        val urls = listOf(
            "https://p3-sign.douyinpic.com/tos/i/a1.webp?tplv=dg=x",
            "https://p3-sign.douyinpic.com/tos/i/a2.webp"
        )
        assertEquals(urls[0], pickBestImageUrl(urls))
    }

    @Test
    fun `空列表返回null`() {
        assertNull(pickBestImageUrl(emptyList()))
    }

    @Test
    fun `全空字符串返回null`() {
        // 接口偶尔给 ["",""]，必须当成没有可用地址
        assertNull(pickBestImageUrl(listOf("", "")))
    }

    @Test
    fun `大写JPEG后缀也命中`() {
        // 图床签名 URL 的扩展名大小写不稳定，判断必须大小写不敏感
        val urls = listOf(
            "https://p3-sign.douyinpic.com/tos/i/a1.webp",
            "https://p3-sign.douyinpic.com/tos/i/a1.JPEG?s=abc"
        )
        assertEquals(urls[1], pickBestImageUrl(urls))
    }

    @Test
    fun `query里的jpg字样不误判为图片扩展名`() {
        // 路径本身没有扩展名、只是 query 里有 "jpg"，绝不能当成 jpeg 优先项
        val urls = listOf(
            "https://www.douyin.com/aweme/v1/get?fmt=jpg",
            "https://p3-sign.douyinpic.com/tos/i/a1.webp"
        )
        assertEquals(urls[0], pickBestImageUrl(urls)) // 无命中 → 回退第一个
    }

    @Test
    fun `空字符串被过滤后仍能选到jpeg`() {
        val urls = listOf("", "https://p3-sign.douyinpic.com/tos/i/a1.jpeg")
        assertEquals(urls[1], pickBestImageUrl(urls))
    }

    // ---------------- stripWatermarkParam ----------------

    @Test
    fun `末尾的watermark1被删除且不留尾巴`() {
        val url = "https://www.douyin.com/aweme/v1/play/?video_id=lva1&ratio=720p&watermark=1"
        assertEquals(
            "https://www.douyin.com/aweme/v1/play/?video_id=lva1&ratio=720p",
            stripWatermarkParam(url)
        )
    }

    @Test
    fun `中间的watermark0被删除且不留问号与and杂字符`() {
        val url = "https://www.douyin.com/aweme/v1/play/?video_id=lva1&watermark=0&media_type=2"
        assertEquals(
            "https://www.douyin.com/aweme/v1/play/?video_id=lva1&media_type=2",
            stripWatermarkParam(url)
        )
    }

    @Test
    fun `连续两个watermark参数都删干净`() {
        val url = "https://www.douyin.com/aweme/v1/play/?video_id=lva1&watermark=1&watermark=0"
        assertEquals(
            "https://www.douyin.com/aweme/v1/play/?video_id=lva1",
            stripWatermarkParam(url)
        )
    }

    @Test
    fun `只有watermark一个参数时整段删除`() {
        val url = "https://www.douyin.com/aweme/v1/play/?watermark=1"
        assertEquals("https://www.douyin.com/aweme/v1/play/", stripWatermarkParam(url))
    }

    @Test
    fun `watermark是首个query参数时问号不能丢`() {
        // 回归：watermark 排在 query 第一位时，删参数的正则会把 "?" 一起吃掉，
        // 剩下 "&video_id=x" 这种残缺 query —— 首个参数的 "?" 必须补回来
        val url = "https://h/aweme/v1/play/?watermark=1&video_id=x"
        assertEquals("https://h/aweme/v1/play/?video_id=x", stripWatermarkParam(url))
    }

    @Test
    fun `无watermark参数原样返回`() {
        val url = "https://v.douyinvod.com/video/a1.mp4?ratio=720p&line=0"
        assertEquals(url, stripWatermarkParam(url))
    }

    @Test
    fun `watermark值为其他数字时不删除`() {
        // 正则只匹配 0/1，别的值保持原样（宁可不处理也不要改坏 URL）
        val url = "https://www.douyin.com/aweme/v1/play/?video_id=x&watermark=2"
        assertEquals(url, stripWatermarkParam(url))
    }

    // ---------------- pickLiveVideoUrl ----------------

    @Test
    fun `play_addr优先且直接返回原始地址`() {
        // play_addr 是无水印原片，必须最先返回且不做任何改写
        val v = ImageVideoObj(
            play_addr = UrlObj(url_list = listOf("https://v.douyinvod.com/live/a1.mp4")),
            download_addr = UrlObj(url_list = listOf("https://www.douyin.com/aweme/v1/play/?video_id=l1&watermark=1"))
        )
        assertEquals("https://v.douyinvod.com/live/a1.mp4", pickLiveVideoUrl(v))
    }

    @Test
    fun `无play_addr时选play接口地址并去掉水印参数`() {
        val v = ImageVideoObj(
            download_addr = UrlObj(
                url_list = listOf(
                    "https://v.douyinvod.com/wm/a1.mp4", // douyinvod 路径删参数无效
                    "https://www.douyin.com/aweme/v1/play/?video_id=lva1&ratio=720p&watermark=1&media_type=2"
                )
            )
        )
        assertEquals(
            "https://www.douyin.com/aweme/v1/play/?video_id=lva1&ratio=720p&media_type=2",
            pickLiveVideoUrl(v)
        )
    }

    @Test
    fun `兜底取第一个download地址`() {
        // 没有可去水印的 play 接口地址时，退回第一个 download 地址
        val v = ImageVideoObj(
            download_addr = UrlObj(url_list = listOf("https://v.douyinvod.com/wm/a1.mp4", "https://v.douyinvod.com/wm/a2.mp4"))
        )
        assertEquals("https://v.douyinvod.com/wm/a1.mp4", pickLiveVideoUrl(v))
    }

    @Test
    fun `无任何地址返回null`() {
        assertNull(pickLiveVideoUrl(ImageVideoObj()))
    }

    // ---------------- ItemObj.toContentInfo ----------------

    private val videoItemJson = """
        {"aweme_id":"7361000000000000000","desc":"测试视频标题","create_time":1730000000,
         "author":{"nickname":"测试作者"},
         "video":{"duration":15000,"width":1080,"height":1920,
           "play_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=720p&line=0"],"data_size":2890000},
           "download_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=720p&line=0&watermark=1"]},
           "cover":{"url_list":["https://p3-sign.douyinpic.com/cover/c7361000.jpeg"]}},
         "statistics":{"digg_count":1200,"comment_count":34,"share_count":56}}
    """.trimIndent()

    @Test
    fun `视频item映射为ContentInfo_Video且字段逐项对应`() {
        val info = gson.fromJson(videoItemJson, ItemObj::class.java).toContentInfo()
        assertTrue(info is ContentInfo.Video)
        val v = info as ContentInfo.Video
        assertEquals("7361000000000000000", v.id)
        assertEquals("测试视频标题", v.title)
        assertEquals("测试作者", v.author)
        assertEquals("https://p3-sign.douyinpic.com/cover/c7361000.jpeg", v.coverUrl)
        assertEquals(15000L, v.duration)
        assertEquals(1730000000L, v.createTime)
        assertEquals(1200L, v.diggCount)
        assertEquals(34L, v.commentCount)
        assertEquals(56L, v.shareCount)
        assertEquals(1080, v.width)
        assertEquals(1920, v.height)
    }

    @Test
    fun `视频url被规范化为1080p且候选从高到低`() {
        // VideoQuality.rewrite 会把 ratio 换成 1080p（web 接口清晰度由 ratio 决定）
        val v = gson.fromJson(videoItemJson, ItemObj::class.java).toContentInfo() as ContentInfo.Video
        assertEquals("https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=1080p&line=0", v.videoUrl)
        assertEquals(3, v.candidateUrls.size)
        assertEquals(v.videoUrl, v.candidateUrls[0])
        assertTrue(v.candidateUrls[1].contains("ratio=720p"))
        assertTrue(v.candidateUrls[2].contains("ratio=540p"))
    }

    @Test
    fun `desc为空时标题回退未命名_作者缺失回退未知`() {
        val json = """{"aweme_id":"1","desc":"","video":{"play_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=x&ratio=720p"]}}}"""
        val v = gson.fromJson(json, ItemObj::class.java).toContentInfo() as ContentInfo.Video
        assertEquals("未命名", v.title)
        assertEquals("未知", v.author)
    }

    private val liveImageItemJson = """
        {"aweme_id":"7412345678901234567","desc":"实况图集测试","create_time":1730000100,
         "author":{"nickname":"图集作者"},
         "images":[
           {"url_list":["https://p3-sign.douyinpic.com/tos/i/a1.webp?tplv=dg=x","https://p3-sign.douyinpic.com/tos/i/a1.jpeg?tplv=dg=x"],
            "download_url_list":["https://p3-sign.douyinpic.com/watermark/a1.jpeg"],
            "video":{"play_addr":{"url_list":["https://v.douyinvod.com/live/a1.mp4"]},
                     "download_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=lva1&watermark=1"]}},
            "live_photo_type":1,"width":2160,"height":2880},
           {"url_list":["https://p3-sign.douyinpic.com/tos/i/a2.webp"],"live_photo_type":0,"width":2160,"height":2880}
         ]}
    """.trimIndent()

    @Test
    fun `图集item映射为ImageCollection_实况图jpeg优先且video字段单数`() {
        // 两个回归点：images[i].video 是单数字段名；url_list 里 jpeg 必须排在 webp 前被选中
        val info = gson.fromJson(liveImageItemJson, ItemObj::class.java).toContentInfo()
        assertTrue(info is ContentInfo.ImageCollection)
        val img = info as ContentInfo.ImageCollection
        assertEquals("7412345678901234567", img.id)
        assertEquals("实况图集测试", img.title)
        assertEquals("图集作者", img.author)
        assertEquals(1730000100L, img.createTime)
        // 第一张：jpeg 胜出（webp 在前也不行）；第二张：只有 webp 回退第一个
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a1.jpeg?tplv=dg=x", img.imageUrls[0])
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a2.webp", img.imageUrls[1])
        assertEquals(img.imageUrls[0], img.coverUrl)
        // 实况图与静图一一对应：第一张有 play_addr 视频地址，第二张是 null
        assertEquals(2, img.animatedUrls.size)
        assertEquals("https://v.douyinvod.com/live/a1.mp4", img.animatedUrls[0])
        assertNull(img.animatedUrls[1])
    }

    @Test
    fun `图集url_list为空时回退download_url_list`() {
        // 只有带水印地址可用时也不能丢图 —— 回退到 download_url_list
        val json = """
            {"aweme_id":"7412345678901234599","desc":"x",
             "images":[{"url_list":[],"download_url_list":["https://p3-sign.douyinpic.com/watermark/a1.jpeg"]}]}
        """.trimIndent()
        val img = gson.fromJson(json, ItemObj::class.java).toContentInfo() as ContentInfo.ImageCollection
        assertEquals(listOf("https://p3-sign.douyinpic.com/watermark/a1.jpeg"), img.imageUrls)
        assertNull(img.animatedUrls[0])
    }

    @Test
    fun `中间图无地址时imageUrls与animatedUrls仍一一对应`() {
        // 回归：第二张图被风控剥空（url_list 和 download_url_list 都拿不到地址）时，
        // 该图在两个列表里都要跳过。修复前 imageUrls 被过滤成 2 项、animatedUrls
        // 却仍是全量 3 项，第三张的实况视频会错位挂到第二张的静图位置上。
        val json = """
            {"aweme_id":"7412345678901234700","desc":"错位回归",
             "images":[
               {"url_list":["https://p3-sign.douyinpic.com/tos/i/a1.jpeg"]},
               {"url_list":[],"download_url_list":[],
                "video":{"play_addr":{"url_list":["https://v.douyinvod.com/live/a2.mp4"]}}},
               {"url_list":["https://p3-sign.douyinpic.com/tos/i/a3.webp"],
                "video":{"play_addr":{"url_list":["https://v.douyinvod.com/live/a3.mp4"]}}}
             ]}
        """.trimIndent()
        val img = gson.fromJson(json, ItemObj::class.java).toContentInfo() as ContentInfo.ImageCollection
        // 两个列表长度必须相等（剥空的那张图两边都跳过）
        assertEquals(img.imageUrls.size, img.animatedUrls.size)
        assertEquals(2, img.imageUrls.size)
        // 第三张图的实况视频仍挂在第三张（现在下标 1）上，而不是错位串到别的图
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a1.jpeg", img.imageUrls[0])
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a3.webp", img.imageUrls[1])
        assertNull(img.animatedUrls[0])
        assertEquals("https://v.douyinvod.com/live/a3.mp4", img.animatedUrls[1])
    }

    @Test
    fun `无video无images返回null`() {
        // 风控剥空的 item：id 还在但 url 全没了，映射层必须给出 null 让上层走兜底
        assertNull(gson.fromJson("""{"aweme_id":"123","desc":"no url"}""", ItemObj::class.java).toContentInfo())
    }
}

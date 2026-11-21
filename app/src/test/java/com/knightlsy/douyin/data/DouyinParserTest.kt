package com.knightlsy.douyin.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DouyinParser] 的测试：正则直取 item_list 与 Gson 整树兜底两条路径。
 *
 * 为什么两条路都要测：RouterData 实测可达 47KB 且正则可能不命中，
 * 树解析是线上真实依赖的兜底（不是冗余代码）；推荐流会混入其他视频，
 * 必须验证「精确 contentId 优先」不会被别的 item 抢走。
 *
 * 测试数据贴近抖音真实返回结构：aweme_id / desc / author.nickname /
 * video.play_addr.url_list / item_list 容器 / aweme_details（slidesinfo）等
 * 字段名都照抄接口，字段名一旦对不上 Gson 会静默填默认值，断言就会失败。
 */
class DouyinParserTest {

    private val gson = Gson()

    private val targetId = "7361000000000000000"

    /** 单条普通视频的 item，结构与分享页 videoInfoRes.item_list[i] 一致 */
    private val videoItemJson = """
        {"aweme_id":"7361000000000000000","desc":"测试视频标题","create_time":1730000000,
         "author":{"nickname":"测试作者"},
         "video":{"duration":15000,"width":1080,"height":1920,
           "play_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=720p&line=0"],"data_size":2890000},
           "download_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=720p&line=0&watermark=1"]},
           "cover":{"url_list":["https://p3-sign.douyinpic.com/cover/c7361000.jpeg"]}},
         "statistics":{"digg_count":1200,"comment_count":34,"share_count":56}}
    """.trimIndent()

    /** 推荐流里混进来的无 url item（风控剥空），id 与目标不同 */
    private val strippedItemJson = """{"aweme_id":"9998887770000000000","desc":"推荐流的其他视频"}"""

    // ---------------- parseRouterData ----------------

    @Test
    fun `正则命中item_list时直接解析出视频`() {
        val routerJson = """{"loaderData":{"video_(id)/page":{"videoInfoRes":{"item_list":[$videoItemJson]}}},"_location":{"href":"https://www.douyin.com/video/$targetId"}}"""
        val info = DouyinParser.parseRouterData(routerJson, targetId)
        assertTrue(info is ContentInfo.Video)
        val v = info as ContentInfo.Video
        assertEquals(targetId, v.id)
        assertEquals("测试视频标题", v.title)
        assertEquals("测试作者", v.author)
        assertEquals("https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=1080p&line=0", v.videoUrl)
        assertEquals(3, v.candidateUrls.size)
    }

    @Test
    fun `推荐流混入其他视频时目标contentId优先`() {
        // 无 url 的推荐流 item 排在前面，也绝不能抢走目标视频
        val routerJson = """{"loaderData":{"videoInfoRes":{"item_list":[$strippedItemJson,$videoItemJson]}}}"""
        val info = DouyinParser.parseRouterData(routerJson, targetId)
        assertEquals(targetId, (info as ContentInfo.Video).id)
    }

    @Test
    fun `目标id不存在时选第一个带有效url的item`() {
        // contentId 匹配不上（推荐流场景）：跳过无 url 的，选第一个有效的
        val routerJson = """{"loaderData":{"videoInfoRes":{"item_list":[$strippedItemJson,$videoItemJson]}}}"""
        val info = DouyinParser.parseRouterData(routerJson, "0000000000000000000")
        assertEquals(targetId, (info as ContentInfo.Video).id)
    }

    @Test
    fun `正则不命中时树解析兜底`() {
        // RouterData 没有字面 item_list（实测 47KB 大页面的一种形态），
        // 数据在任意嵌套层级的 aweme_detail 里 —— 必须靠树解析找到
        val routerJson = """{"loaderData":{"route:video/page":{"aweme_detail":$videoItemJson}},"props":{"x":1}}"""
        val info = DouyinParser.parseRouterData(routerJson, targetId)
        assertEquals(targetId, (info as ContentInfo.Video).id)
    }

    @Test
    fun `item_list为空数组返回null`() {
        // ttwid 失效时服务端返回挖空页 "item_list":[] —— 必须返回 null 而不是乱猜
        val routerJson = """{"loaderData":{"videoInfoRes":{"item_list":[]}}}"""
        assertNull(DouyinParser.parseRouterData(routerJson, targetId))
    }

    @Test
    fun `坏JSON返回null`() {
        // 截断/非 JSON 内容：两条解析路径都失败，最终给 null
        assertNull(DouyinParser.parseRouterData("""{"loaderData":""", targetId))
    }

    // ---------------- parseJsonTree / findItemInTree ----------------

    @Test
    fun `parseJsonTree在深层嵌套中命中目标`() {
        val json = """{"a":{"b":{"aweme_detail":$videoItemJson}}}"""
        assertEquals(targetId, (DouyinParser.parseJsonTree(json, targetId) as ContentInfo.Video).id)
    }

    @Test
    fun `parseJsonTree对有id但无video和images的节点返回null`() {
        // 风控剥空：aweme_id 还在但 video/images 字段都没了
        val json = """{"data":{"aweme_detail":{"aweme_id":"123","desc":"stripped"}}}"""
        assertNull(DouyinParser.parseJsonTree(json, "123"))
    }

    @Test
    fun `parseJsonTree对非法JSON返回null`() {
        assertNull(DouyinParser.parseJsonTree("{not json", "1"))
    }

    @Test
    fun `findItemInTree多item时精确匹配contentId`() {
        val itemA = """{"aweme_id":"a","video":{"play_addr":{"url_list":["u-a"]}}}"""
        val itemB = """{"aweme_id":"b","video":{"play_addr":{"url_list":["u-b"]}}}"""
        val tree = JsonParser.parseString("""{"item_list":[$itemA,$itemB]}""")
        val obj = DouyinParser.findItemInTree(tree, "b")
        assertEquals("b", obj?.get("aweme_id")?.asString)
    }

    @Test
    fun `findItemInTree无匹配返回null`() {
        val tree = JsonParser.parseString("""{"foo":{"bar":1}}""")
        assertNull(DouyinParser.findItemInTree(tree, "b"))
    }

    // ---------------- findAnyValidItem ----------------

    @Test
    fun `findAnyValidItem跳过无url的item取第一个有效的`() {
        val tree = JsonParser.parseString(
            """{"item_list":[{"aweme_id":"x"},{"aweme_id":"y","video":{"play_addr":{"url_list":["u-y"]}}}]}"""
        )
        assertEquals("y", DouyinParser.findAnyValidItem(tree)?.get("aweme_id")?.asString)
    }

    @Test
    fun `findAnyValidItem对非对象节点返回null`() {
        assertNull(DouyinParser.findAnyValidItem(JsonParser.parseString("\"just a string\"")))
        assertNull(DouyinParser.findAnyValidItem(JsonParser.parseString("42")))
    }

    // ---------------- parseItemList ----------------

    @Test
    fun `纯item数组JSON解析出视频`() {
        // iteminfo 通道给的是裸数组 [item, ...]
        val info = DouyinParser.parseItemList("[$videoItemJson]", targetId)
        val v = info as ContentInfo.Video
        assertEquals(targetId, v.id)
        assertEquals("https://www.douyin.com/aweme/v1/play/?video_id=v7361000&ratio=1080p&line=0", v.videoUrl)
        assertEquals(1200L, v.diggCount)
    }

    @Test
    fun `数组内目标id优先且跳过无url项`() {
        val json = "[$strippedItemJson,$videoItemJson]"
        // contentId 命中 → 选目标
        assertEquals(targetId, (DouyinParser.parseItemList(json, targetId) as ContentInfo.Video).id)
        // contentId 不命中 → 选第一个带 url 的（跳过剥空 item）
        assertEquals(
            targetId,
            (DouyinParser.parseItemList(json, "0000000000000000000") as ContentInfo.Video).id
        )
    }

    @Test
    fun `空数组返回null`() {
        assertNull(DouyinParser.parseItemList("[]", targetId))
    }

    @Test
    fun `包裹结构item_list取第一个item`() {
        // WebView 通道的 iteminfo 来源：{"item_list":[...]} 外面还套着状态码等字段
        val json = """{"status_code":0,"item_list":[$videoItemJson],"extra":true}"""
        assertEquals(targetId, (DouyinParser.parseItemList(json, targetId) as ContentInfo.Video).id)
    }

    /** 生成与 videoItemJson 同结构、指定 id 的视频 item，用于多条目选择逻辑的测试 */
    private fun videoItem(id: String): String = """
        {"aweme_id":"$id","desc":"视频-$id","create_time":1730000000,
         "author":{"nickname":"作者-$id"},
         "video":{"duration":15000,"width":1080,"height":1920,
           "play_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=$id&ratio=720p&line=0"],"data_size":2890000},
           "download_addr":{"url_list":["https://www.douyin.com/aweme/v1/play/?video_id=$id&ratio=720p&line=0&watermark=1"]},
           "cover":{"url_list":["https://p3-sign.douyinpic.com/cover/$id.jpeg"]}},
         "statistics":{"digg_count":100,"comment_count":1,"share_count":2}}
    """.trimIndent()

    @Test
    fun `包裹结构多条item时目标contentId优先`() {
        // 回归：iteminfo 返回多条（推荐流混入），目标不在首位也必须取到目标，而不是第 0 个
        val json = """{"status_code":0,"item_list":[${videoItem("B")},${videoItem("A")}],"extra":true}"""
        assertEquals("A", (DouyinParser.parseItemList(json, "A") as ContentInfo.Video).id)
    }

    @Test
    fun `包裹结构目标不存在时退回第一个有效item`() {
        // contentId 匹配不上（推荐流场景）：不能返回 null，退回第一个带 url 的 item
        val json = """{"item_list":[${videoItem("B")},${videoItem("A")}]}"""
        assertEquals("B", (DouyinParser.parseItemList(json, "X") as ContentInfo.Video).id)
    }

    @Test
    fun `包裹结构目标不存在且首位无url时跳过剥空item`() {
        // 首位是风控剥空的推荐流 item：第二级 fallback 要跳过它选第一个有效的
        val json = """{"item_list":[$strippedItemJson,${videoItem("A")}]}"""
        assertEquals("A", (DouyinParser.parseItemList(json, "X") as ContentInfo.Video).id)
    }

    @Test
    fun `aweme_details多条时目标contentId优先`() {
        // slidesinfo 的 {"aweme_details":[...]} 同样优先匹配 contentId，而不是盲取第 0 个
        val json = """{"aweme_details":[${videoItem("B")},${videoItem("A")}]}"""
        assertEquals("A", (DouyinParser.parseItemList(json, "A") as ContentInfo.Video).id)
    }

    @Test
    fun `slidesinfo风格aweme_details解析出图集`() {
        // slidesinfo 接口的真实形态：{"aweme_details":[{...images...}]}
        val imageItemJson = """
            {"aweme_id":"7412345678901234567","desc":"图集测试","create_time":1730000100,
             "author":{"nickname":"图集作者"},
             "images":[
               {"url_list":["https://p3-sign.douyinpic.com/tos/i/a1.webp?tplv=dg=x","https://p3-sign.douyinpic.com/tos/i/a1.jpeg?tplv=dg=x"],
                "video":{"play_addr":{"url_list":["https://v.douyinvod.com/live/a1.mp4"]}},"live_photo_type":1},
               {"url_list":["https://p3-sign.douyinpic.com/tos/i/a2.webp"],"live_photo_type":0}
             ]}
        """.trimIndent()
        val info = DouyinParser.parseItemList("""{"aweme_details":[$imageItemJson]}""", "7412345678901234567")
        val img = info as ContentInfo.ImageCollection
        assertEquals("7412345678901234567", img.id)
        // jpeg 优先规则在解析链路上同样生效
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a1.jpeg?tplv=dg=x", img.imageUrls[0])
        assertEquals("https://p3-sign.douyinpic.com/tos/i/a2.webp", img.imageUrls[1])
        assertEquals("https://v.douyinvod.com/live/a1.mp4", img.animatedUrls[0])
        assertNull(img.animatedUrls[1])
    }

    @Test
    fun `aweme_detail单对象解析`() {
        val json = """{"aweme_detail":$videoItemJson}"""
        assertEquals(targetId, (DouyinParser.parseItemList(json, targetId) as ContentInfo.Video).id)
    }

    @Test
    fun `有id但无url的裸对象返回null`() {
        // 单对象形态的剥空 item：走完所有分支都不该硬造出一个 ContentInfo
        assertNull(DouyinParser.parseItemList("""{"aweme_id":"123","desc":"x"}""", "123"))
    }

    @Test
    fun `parseItemList坏JSON返回null`() {
        assertNull(DouyinParser.parseItemList("""{"a":""", targetId))
    }
}

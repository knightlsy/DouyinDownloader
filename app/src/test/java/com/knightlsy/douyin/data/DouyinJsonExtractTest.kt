package com.knightlsy.douyin.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [DouyinJsonExtract] 的括号配对切分测试。
 *
 * 为什么逐字符断言返回的字符串：这一层的职责就是把大文本里的某个 JSON 片段
 * **原样**切出来，多切或少切一个字符，下游 Gson 就会解析失败 —— 所以
 * assertEquals 必须精确到整个子串，而不是只判断非空。
 *
 * 抖音改版时最先崩的就是这里（HTML 结构一变标记就找不到），全部用例
 * 不碰网络、纯字符串输入输出。
 */
class DouyinJsonExtractTest {

    // ---------------- extractJsonObject ----------------

    @Test
    fun `字符串内的花括号不参与深度计数`() {
        // {"a":"{x}"} —— 字符串值里的 { 必须被引号状态屏蔽，否则会提前闭合
        val json = """{"a":"{x}","b":"}","c":{"d":1}}"""
        assertEquals(json, DouyinJsonExtract.extractJsonObject(json, 0))
    }

    @Test
    fun `字符串内的方括号不影响对象切分`() {
        // 值里混 [ ] 时，对象配对仍只看 { }
        val json = """{"a":"[y]","b":"["}"""
        assertEquals(json, DouyinJsonExtract.extractJsonObject(json, 0))
    }

    @Test
    fun `转义引号不打断字符串状态`() {
        // {"a":"x\"y}"} —— \" 里的 } 在字符串内部，绝不能让 depth 归零
        val json = """{"a":"x\"y}","b":2}"""
        assertEquals(json, DouyinJsonExtract.extractJsonObject(json, 0))
    }

    @Test
    fun `转义反斜杠后的引号不会提前闭合字符串`() {
        // {"path":"a\\"} —— 值是单个反斜杠，\\ 消耗转义后才是真正的收尾引号
        val json = """{"path":"a\\"}"""
        assertEquals(json, DouyinJsonExtract.extractJsonObject(json, 0))
    }

    @Test
    fun `从中途起点切出内层嵌套对象`() {
        // RouterData 里经常要从外层对象中定位内层块，起点必须精确落在 {
        val json = """prefix {"inner":{"x":1,"y":"}"}} suffix"""
        val start = json.indexOf('{')
        assertEquals("""{"inner":{"x":1,"y":"}"}}""", DouyinJsonExtract.extractJsonObject(json, start))
    }

    @Test
    fun `未闭合对象返回null`() {
        // 挖空页/截断的 HTML：内层 { } 配平了但最外层没有闭合
        assertNull(DouyinJsonExtract.extractJsonObject("""{"a":{"b":1}""", 0))
    }

    @Test
    fun `start处不是花括号返回null`() {
        // 标记后跟的是数字/字符串而不是对象（接口改版的一种表现）
        assertNull(DouyinJsonExtract.extractJsonObject("""[1,2]""", 0))
        assertNull(DouyinJsonExtract.extractJsonObject("""{"a":1}""", 1)) // 起点是引号
    }

    @Test
    fun `start越界返回null`() {
        assertNull(DouyinJsonExtract.extractJsonObject("{}", 2))   // == length
        assertNull(DouyinJsonExtract.extractJsonObject("{}", 10))  // 远超长度
    }

    // ---------------- extractJsonArray ----------------

    @Test
    fun `嵌套数组对象且字符串含方括号时切出完整数组`() {
        // item_list 的真实形态：数组里嵌对象、对象里再嵌数组，值里还可能有 [ ]
        val json = """[{"desc":"[广告]推广"},{"tags":["a]b"]},[1,2]]"""
        assertEquals(json, DouyinJsonExtract.extractJsonArray(json, 0))
    }

    @Test
    fun `字符串内的右方括号不会提前闭合数组`() {
        val json = """["a]b","c[d"]"""
        assertEquals(json, DouyinJsonExtract.extractJsonArray(json, 0))
    }

    @Test
    fun `未闭合数组返回null`() {
        assertNull(DouyinJsonExtract.extractJsonArray("""[[1,2]""", 0))
    }

    @Test
    fun `空数组切出空数组字面量`() {
        // ttwid 失效时抖音会回 "item_list":[] —— 切出来应是 "[]" 而不是 null，
        // 上层靠这个区分「挖空页」和「页面没加载」
        assertEquals("[]", DouyinJsonExtract.extractJsonArray("""[] trailing""", 0))
    }

    @Test
    fun `数组start处不是左方括号返回null`() {
        assertNull(DouyinJsonExtract.extractJsonArray("""{"a":1}""", 0))
    }

    @Test
    fun `数组start越界返回null`() {
        assertNull(DouyinJsonExtract.extractJsonArray("", 0))
        assertNull(DouyinJsonExtract.extractJsonArray("[1]", 5))
    }

    // ---------------- extractRouterData ----------------

    @Test
    fun `从真实HTML片段切出ROUTER_DATA对象`() {
        // 分享页 HTML 的真实形态：script 里赋值，后面还跟着别的全局变量
        val routerJson = """{"loaderData":{"video_(id)/page":{"videoInfoRes":{"item_list":[]}}},"props":{"a":1}}"""
        val html = "<html><body><div id=\"root\"></div><script>window._ROUTER_DATA = $routerJson;" +
            " window.__INITIAL_STATE__={};</script></body></html>"
        assertEquals(routerJson, DouyinJsonExtract.extractRouterData(html))
    }

    @Test
    fun `没有ROUTER_DATA标记返回null`() {
        // WebView 通道偶尔拿到的是降级/风控页，没有路由数据
        val html = "<html><body><script>var x = 1;</script></body></html>"
        assertNull(DouyinJsonExtract.extractRouterData(html))
    }

    @Test
    fun `标记后对象未闭合返回null`() {
        // 截断的响应：标记在但 JSON 不完整
        assertNull(DouyinJsonExtract.extractRouterData("""window._ROUTER_DATA = {"loaderData":{"a":1"""))
    }

    // ---------------- extractItemList ----------------

    @Test
    fun `从JSON文本切出item_list数组`() {
        val html = """{"status_code":0,"item_list":[{"aweme_id":"7361000000000000000"}],"extra":{"x":1}}"""
        assertEquals("""[{"aweme_id":"7361000000000000000"}]""", DouyinJsonExtract.extractItemList(html))
    }

    @Test
    fun `item_list冒号后有空白仍可切出`() {
        // 不同接口的压缩程度不一样，": [" 和 ":[ " 都要兼容
        val html = """{"item_list" :  [ {"aweme_id":"1"} ]}"""
        assertEquals("""[ {"aweme_id":"1"} ]""", DouyinJsonExtract.extractItemList(html))
    }

    @Test
    fun `没有item_list返回null`() {
        assertNull(DouyinJsonExtract.extractItemList("""{"status_code":8,"status_msg":"error"}"""))
    }

    @Test
    fun `空字符串返回null`() {
        assertNull(DouyinJsonExtract.extractItemList(""))
    }
}

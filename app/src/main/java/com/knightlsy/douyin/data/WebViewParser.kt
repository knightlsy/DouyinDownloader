package com.knightlsy.douyin.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * WebView 解析通道：HTML 静态解析拿不到数据时，用 headless WebView 加载分享页，
 * 借真实浏览器环境执行页面 JS，拦截 XHR/fetch 响应里的 aweme detail / item_list JSON。
 *
 * 注意：
 * - WebView 必须在主线程创建与销毁（Context 用 applicationContext，防 Activity 泄漏）
 * - CookieManager 是 WebView 全局单例，与 OkHttpClient 的请求头 Cookie 完全隔离，互不影响
 */
class WebViewParser(private val context: Context) {

    companion object {
        private const val TAG = "WebViewParser"
        /** 整体超时：页面加载 + JS 注入 + 接口返回的总预算 */
        private const val TIMEOUT_MS = 15_000L
        /** 解析出的 JSON 至少要这么长，太短视为空数据页 */
        private const val MIN_JSON_LENGTH = 500

        private val mainHandler = Handler(Looper.getMainLooper())

        /**
         * 命中这些接口的 XHR 响应体就是我们要的解析数据。
         * 2026-09 逆向 m.douyin.com/iesdouyin.com 分享页 JS（6531 chunk）确认页面实际调用：
         * - /web/api/v2/aweme/iteminfo/   视频详情（旧 web API，仍被分享页使用）
         * - /web/api/v2/aweme/slidesinfo/ 图集详情（slides 分享页必走）
         * - /aweme/v1/web/aweme/detail    web 详情接口（部分场景）
         * - /aweme/v1/web/seo/entity/     SEO 数据（兜底）
         * 旧版只匹配 detail/item_list 导致 WebView 跑满 15s 超时空手而归。
         */
        private fun isAwemeJson(url: String): Boolean =
            url.contains("/web/api/v2/aweme/iteminfo/") ||
                url.contains("/web/api/v2/aweme/slidesinfo/") ||
                url.contains("/aweme/v1/web/aweme/detail") ||
                url.contains("/aweme/v1/web/seo/entity/") ||
                url.contains("aweme/v1/web/item_list")
    }

    /** 超时/协程取消后置位，避免迟到的回调再次 resume */
    private val cancelled = AtomicBoolean(false)

    /**
     * 加载分享页并抓取解析 JSON，返回原始 JSON 文本（item_list 数组 / aweme_detail 对象 /
     * _ROUTER_DATA 对象），交给上层 VideoRepository 复用 parseRouterData/parseItemList 解析。
     * 任何失败都返回 null，由上层决定是否报错。
     */
    suspend fun parse(contentId: String): String? = withContext(Dispatchers.Main) {
        cancelled.set(false)
        var webView: WebView? = null
        try {
            try {
                // 整个流程挂在主线程：创建 WebView、等回调、超时兜底都在这里
                suspendCancellableCoroutine { cont ->
                    webView = createAndLoad(contentId) { json ->
                        if (cont.isActive) cont.resume(json)
                    }
                    // 协程被取消时置位，超时定时器就不会再触发无意义的 finish
                    cont.invokeOnCancellation { cancelled.set(true) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "WebView parse failed: ${e.message}")
                null
            }
        } finally {
            // 无论成功/失败/取消都要销毁 WebView，防内存泄漏（此处必在主线程）
            cancelled.set(true)
            webView?.let { destroyQuietly(it) }
        }
    }

    /** 在主线程创建 headless WebView 并加载分享页，结果（JSON 或 null）通过回调回传 */
    @SuppressLint("SetJavaScriptEnabled")
    private fun createAndLoad(contentId: String, onResult: (String?) -> Unit): WebView {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView 必须在主线程创建" }
        val webView = WebView(context.applicationContext)
        // captured 保证结果只回传一次（XHR 命中 / JS 注入命中 / 超时 三路竞争）
        val captured = AtomicBoolean(false)
        fun finish(json: String?) {
            if (captured.compareAndSet(false, true)) onResult(json)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadWithOverviewMode = true
            useWideViewPort = true
            // headless 解析不需要加载图片，省流量也降低被页面脚本干扰的概率
            blockNetworkImage = true
            allowFileAccess = false
            allowContentAccess = false
            // UA 与 VideoRepository 的 baseHeaders 保持一致，提高接口命中率
            userAgentString = "Mozilla/5.0 (Linux; Android 14; Xiaomi 14 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // Cookie 隔离说明：CookieManager 是 WebView 全局单例，与 OkHttpClient（无 CookieJar）互不干扰
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            // shouldInterceptRequest 在 WebView 的 IO 线程回调，这里抓 XHR 响应体
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                if (!isAwemeJson(url)) return null
                try {
                    // 用裸 HttpURLConnection 读响应体，不复用业务 OkHttpClient，避免 Cookie/连接串扰
                    val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    conn.requestMethod = request.method
                    // 透传页面原始请求头（Agw-Js-Conv 等）+ Cookie（ttwid/odin_tt 等风控字段）
                    // 旧版只带 Cookie+UA 重发，丢了页面原生头，风控容易判异常
                    for ((k, v) in request.requestHeaders ?: emptyMap()) {
                        if (k.equals("Cookie", true) || k.equals("User-Agent", true)) continue
                        conn.setRequestProperty(k, v)
                    }
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrEmpty()) conn.setRequestProperty("Cookie", cookie)
                    conn.setRequestProperty("User-Agent", view.settings.userAgentString)
                    if (conn.responseCode in 200..299) {
                        val body = conn.inputStream?.bufferedReader()?.use { it.readText() }
                        Log.d(TAG, "Intercepted ${url.take(120)} len=${body?.length}")
                        if (!body.isNullOrEmpty() && body.length > MIN_JSON_LENGTH) {
                            finish(body)
                        }
                    } else {
                        Log.w(TAG, "Intercept ${url.take(120)} code=${conn.responseCode}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Intercept failed: ${e.message}")
                }
                // 返回 null 让 WebView 自己继续加载，不影响页面流程
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                injectJs(view)
            }

            /** 页面加载完后兜底读一次全局静态数据，弱网下可能比 XHR 更早可用 */
            private fun injectJs(view: WebView) {
                val js = """
                    (function() {
                        try {
                            if (window._ROUTER_DATA) { return JSON.stringify(window._ROUTER_DATA); }
                            var el = document.querySelector('script#__NEXT_DATA__');
                            if (el && el.textContent) { return el.textContent; }
                        } catch (e) {}
                        return '';
                    })();
                """.trimIndent()
                view.evaluateJavascript(js) { value ->
                    // evaluateJavascript 返回的是 JSON 编码后的字符串字面量，先解码再判断
                    val text = decodeJsValue(value)
                    Log.d(TAG, "JS injected data len=${text.length}")
                    if (text.length > MIN_JSON_LENGTH) finish(text)
                }
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                // 分享页会 302 跳转（m.douyin.com → www.iesdouyin.com/share/slides|video），
                // 每次 URL 变化都补一次 JS 注入，避免只注入在跳转前的骨架页上
                if (!url.isNullOrEmpty() && url.startsWith("http")) injectJs(view)
            }
        }

        // 2026-09 实测分享链接会 302 到 www.iesdouyin.com/share/slides|video|note，
        // 直接加载 m.douyin.com 会多一次跳转耗时；但视频/图集路径不同，
        // 保守起见仍从 m.douyin.com 进，让服务端决定落点。
        val shareUrl = "https://m.douyin.com/share/video/$contentId/"
        Log.d(TAG, "WebView load $shareUrl")
        webView.loadUrl(shareUrl)

        // 整体超时：不管走到哪一步，15 秒内必须给结论（finish 只生效一次）
        mainHandler.postDelayed({
            if (!cancelled.get()) {
                Log.w(TAG, "WebView parse timeout after ${TIMEOUT_MS / 1000}s")
                finish(null)
            }
        }, TIMEOUT_MS)

        return webView
    }

    /** 把 evaluateJavascript 的返回值（JSON 字符串字面量）解码成原始文本 */
    private fun decodeJsValue(value: String?): String {
        if (value.isNullOrEmpty() || value == "null") return ""
        return try {
            if (value.startsWith("\"")) {
                org.json.JSONTokener(value).nextValue().toString()
            } else value
        } catch (_: Exception) {
            value
        }
    }

    private fun destroyQuietly(webView: WebView) {
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "WebView destroy failed: ${e.message}")
        }
    }
}

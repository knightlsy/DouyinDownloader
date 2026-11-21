package com.knightlsy.douyin.ui

/**
 * 从一段任意文本里提取出抖音 / TikTok 分享链接。
 *
 * 为什么放在这里（顶层、独立文件）：
 * - 它是纯函数 —— 不依赖任何类状态、不碰 Android 框架，输入相同输出必然相同。
 * - 之前长在 MainViewModel（God ViewModel）里，想单测它就必须构造整个 ViewModel
 *   （连带 Application 和一堆仓库依赖），测试成本远高于函数本身。
 *   搬成顶层 internal fun 后，单测可以直接调用，不需要任何运行环境。
 *
 * 注意：匹配优先级是有讲究的 —— 先按抖音/TikTok 的已知域名精确匹配，
 * 全都失败时才走 fallback 泛 URL 兜底，避免把分享文案里的无关链接误当目标。
 */
internal fun extractUrl(text: String): String? {
    for (p in urlPatterns) {
        val match = p.find(text)
        if (match != null) return match.value
    }
    return fallbackPattern.find(text)?.value
}

// 已知分享域名白名单，按精确程度从高到低排列；private 保证只服务本文件的 extractUrl，
// 不构成对外的稳定 API —— 调整匹配规则不影响任何其他模块。
private val urlPatterns = listOf(
    Regex("https?://v\\.douyin\\.com/[\\w-]+/?"),
    Regex("https?://vm\\.tiktok\\.com/[\\w-]+/?"),
    Regex("https?://www\\.douyin\\.com/video/\\d+"),
    Regex("https?://www\\.douyin\\.com/note/\\d+"),
    Regex("https?://www\\.iesdouyin\\.com/share/[\\w]+/\\d+")
)

// 兜底：任意 .com/.cn 域名的 URL。只在白名单全部未命中时才使用 ——
// 宁可放宽也不漏链接，因为用户粘贴的文案格式无法穷举。
private val fallbackPattern = Regex("https?://[\\w.-]+(?:\\.com|\\.cn)/[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]*")

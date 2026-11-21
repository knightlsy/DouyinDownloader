package com.knightlsy.douyin.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs
import kotlin.math.max

/**
 * 从桌面壁纸提取「氛围底图」与「约束后的强调色」。
 *
 * ## 两条约束（都是踩过坑总结的）
 *
 * **① 强调色不直接用壁纸主色，而是约束在橙色家族内**
 * 如果直接拿壁纸主色当 accent，冷色壁纸会把「解析」「下载」这些主按钮变成蓝紫，
 * 品牌识别彻底丢失。所以只允许色相偏离基准橙 ±25°，明度和饱和度锁定。
 * 效果是：暖壁纸让橙更暖，冷壁纸让橙偏琥珀，但不会失控成别的颜色。
 *
 * **② 壁纸缩到 48×48 再拉伸，不追求真模糊**
 * 原图 4800×3200 直接模糊会 OOM；用 RenderEffect 需要 API 31，而 minSdk 是 26。
 * 缩到 48×48 后拉伸到全屏，视觉上等效于重度模糊，且零额外开销、全版本一致。
 *
 * ## 缓存
 * 壁纸只在 `onResume` 比对壁纸 ID，变了才重算。一次计算约 2-5ms（48×48 像素）。
 */
object WallpaperTint {

    /** 采样边长。48 足够提取色相，又能让拉伸后完全看不出像素格。 */
    private const val SAMPLE = 48

    /** 基准橙 #FF7A3A 的色相（HSL 角度）。 */
    private const val BASE_HUE = 20f

    /** 允许的最大色相偏移。超过这个范围就不是「橙色家族」了。 */
    private const val MAX_SHIFT = 25f

    /** 锁定的饱和度与明度 —— 保证 accent 在深底上的对比度始终达标。 */
    private const val LOCKED_SAT = 0.88f
    private const val LOCKED_LIGHT = 0.61f

    data class Result(
        /** 约束后的强调色。壁纸不可读时为 null，调用方应回退到 [BgAccent]。 */
        val accent: Color?,
        /** 壁纸缩略图，供背景层拉伸铺满。不可读时为 null。 */
        val backdrop: ImageBitmap?,
    )

    @Volatile private var cachedId = Int.MIN_VALUE
    @Volatile private var cached: Result? = null

    /**
     * 取当前壁纸的氛围与强调色。壁纸未变时命中缓存。
     *
     * 任何时候都不抛异常 —— 壁纸读取在部分 ROM 上会失败（权限或 OEM 限制），
     * 失败就返回 null，调用方回退到默认橙色与纯色底，界不能因此崩掉。
     */
    fun get(context: Context): Result {
        val wm = WallpaperManager.getInstance(context)

        val id = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
            } else {
                0
            }
        }.getOrDefault(0)

        val hit = cached
        if (hit != null && id == cachedId) return hit

        val fresh = runCatching { compute(wm) }.getOrDefault(Result(null, null))
        cachedId = id
        cached = fresh
        return fresh
    }

    private fun compute(wm: WallpaperManager): Result {
        val src = wm.drawable ?: return Result(null, null)
        val w = src.intrinsicWidth.coerceAtLeast(1)
        val h = src.intrinsicHeight.coerceAtLeast(1)

        // 等比缩放到 SAMPLE 见方，保持画面构成不变
        val scale = SAMPLE.toFloat() / max(w, h)
        val tw = (w * scale).toInt().coerceAtLeast(1)
        val th = (h * scale).toInt().coerceAtLeast(1)

        val small = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(small)
        src.setBounds(0, 0, tw, th)
        src.draw(canvas)

        return Result(
            accent = constrainAccent(small),
            backdrop = small.asImageBitmap(),
        )
    }

    /**
     * 从缩略图取主色相，压回橙色家族。
     *
     * 取样策略：忽略过暗与过亮的像素（天空、纯黑边），只看中间调，
     * 用饱和度加权平均 —— 一张照片的主体色通常在中间调里。
     */
    private fun constrainAccent(bmp: Bitmap): Color? {
        var hueX = 0.0
        var hueY = 0.0
        var totalWeight = 0.0

        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)

        for (p in px) {
            val r = (p shr 16 and 0xFF) / 255f
            val g = (p shr 8 and 0xFF) / 255f
            val b = (p and 0xFF) / 255f

            val maxC = max(r, max(g, b))
            val minC = minOf(r, g, b)
            val light = (maxC + minC) / 2f
            val delta = maxC - minC

            // 过暗、过亮、接近灰的像素不参与色相统计
            if (light < 0.15f || light > 0.85f || delta < 0.04f) continue

            val sat = if (light > 0.5f) delta / (2f - maxC - minC) else delta / (maxC + minC)
            if (sat < 0.15f) continue

            val hue = when (maxC) {
                r -> 60f * (((g - b) / delta) % 6f)
                g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }.let { if (it < 0) it + 360f else it }

            // 用饱和度当权重：颜色越浓，越能代表这张壁纸的倾向
            val weight = sat.toDouble()
            val rad = Math.toRadians(hue.toDouble())
            hueX += kotlin.math.cos(rad) * weight
            hueY += kotlin.math.sin(rad) * weight
            totalWeight += weight
        }

        if (totalWeight < 1.0) return null   // 壁纸几乎无彩色（纯黑白），没有可提取的倾向

        val avgHue = ((Math.toDegrees(kotlin.math.atan2(hueY, hueX)) + 360) % 360).toFloat()

        // 把壁纸色相朝基准橙靠拢，最多偏 MAX_SHIFT
        var shift = avgHue - BASE_HUE
        if (shift > 180f) shift -= 360f
        if (shift < -180f) shift += 360f
        shift = shift.coerceIn(-MAX_SHIFT, MAX_SHIFT)

        val finalHue = (BASE_HUE + shift + 360f) % 360f

        // 明度/饱和度锁定，只让色相变化
        return hslToColor(finalHue, LOCKED_SAT, LOCKED_LIGHT)
    }

    private fun hslToColor(h: Float, s: Float, l: Float): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((h / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            h < 60f -> Triple(c, x, 0f)
            h < 120f -> Triple(x, c, 0f)
            h < 180f -> Triple(0f, c, x)
            h < 240f -> Triple(0f, x, c)
            h < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }
}

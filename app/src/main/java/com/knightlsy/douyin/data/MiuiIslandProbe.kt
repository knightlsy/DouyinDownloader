package com.knightlsy.douyin.data

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import androidx.core.content.ContextCompat
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.knightlsy.douyin.R
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 小米澎湃 OS 焦点通知 / 超级岛的能力探测。
 *
 * ## 为什么先探测再谈接入
 * 焦点通知有一条**应用白名单**：普通应用即使按官方规范把 `miui.focus.param`
 * 塞进通知 extras，系统也不会放行。白名单判定在 SystemUI 里，不受应用控制。
 *
 * 所以做界面之前先确认三件事，否则改了也白改：
 *  1. 这台设备支持岛吗（系统属性）
 *  2. 焦点通知协议版本是几（1=OS1 / 2=OS2 / 3=OS3，只有 3 才有岛形态）
 *  3. **本应用有焦点通知权限吗**（`canShowFocus`，唯一直接的决定因素）
 *
 * 第 3 条是零风险查询：只是读一个 ContentProvider，不改任何系统状态。
 *
 * 来源：澎湃 OS 开发者平台 开发指南（pId=2131）/ 常见 Q&A（pId=2146）。
 */
object MiuiIslandProbe {

    private const val TAG = "MiuiIsland"
    private const val CHANNEL_ID = "island_probe"
    private const val NOTIFICATION_ID = 0x1501
    /** 纯文字变体：有文字区但不给任何 picInfo */
    private const val NOTIFICATION_ID_TEXT_ONLY = 0x1503
    /** 对照用：不带 focus 参数的普通通知 */
    private const val NOTIFICATION_ID_PLAIN = 0x1502
    /** 图片键，JSON 里引用它，Bundle 里按同名挂实体 */
    private const val PIC_KEY = "miui.focus.pic_icon"

    /**
     * 写一条诊断记录。
     *
     * 同时进两处：logcat（开发时看）和 [LogStore]（用户能在
     * 设置 → 诊断 → 日志 里直接复制导出）。只写 logcat 的话，
     * 用户在应用里看不到，排查只能靠插线。
     */
    private fun record(context: Context?, message: String) {
        Log.i(TAG, message)
        LogStore.log("island", message)
    }

    data class Result(
        /** 系统属性 persist.sys.feature.island */
        val supportsIsland: Boolean,
        /** notification_focus_protocol：0=无 / 1=OS1 / 2=OS2 / 3=OS3（有岛） */
        val protocolVersion: Int,
        /** 本应用是否在焦点通知白名单里 —— 这条决定能不能上岛 */
        val hasFocusPermission: Boolean,
        /** 探测过程有没有异常，非 null 时上面的值不可信 */
        val error: String? = null,
    ) {
        val protocolLabel: String
            get() = when (protocolVersion) {
                1 -> "OS1"
                2 -> "OS2"
                3 -> "OS3（支持岛）"
                else -> "无焦点通知能力"
            }

        /** 给设置页直接显示的一行。 */
        fun describe(): String {
            if (error != null) return "探测失败：$error"
            if (!supportsIsland) return "本机不支持超级岛"
            if (protocolVersion < 3) return "系统支持焦点通知，但非 OS3（无岛形态）"
            return if (hasFocusPermission) {
                "支持岛 · 已获焦点通知权限"
            } else {
                "支持岛 · 未获焦点通知权限（需白名单）"
            }
        }

        /** 设置页那一行右侧的短标签。完整描述在 describe()。 */
        val shortLabel: String
            get() = when {
                error != null -> "探测失败"
                !supportsIsland -> "不支持"
                protocolVersion < 3 -> "无岛形态"
                hasFocusPermission -> "可接入"
                else -> "需白名单"
            }

        /** 能不能真的上岛。 */
        val canShowIsland: Boolean
            get() = error == null && supportsIsland && protocolVersion >= 3 && hasFocusPermission
    }

    fun probe(context: Context): Result {
        var supportsIsland = false
        var protocol = 0
        var hasPermission = false
        var error: String? = null
        try {
            supportsIsland = readBooleanProperty("persist.sys.feature.island")
            protocol = runCatching {
                Settings.System.getInt(
                    context.contentResolver,
                    "notification_focus_protocol",
                    0,
                )
            }.getOrDefault(0)
            hasPermission = queryCanShowFocus(context, context.packageName)
        } catch (throwable: Throwable) {
            error = throwable.javaClass.simpleName
        }
        val result = Result(supportsIsland, protocol, hasPermission, error)
        record(
            context,
            "探测 设备=${Build.MANUFACTURER}/${Build.MODEL} SDK=${Build.VERSION.SDK_INT}"
                    + " | 支持岛=$supportsIsland"
                    + " | 协议版本=$protocol(${result.protocolLabel})"
                    + " | canShowFocus=$hasPermission"
                    + " | 包名=${context.packageName}"
                    + " | 结论=${result.describe()}",
        )
        return result
    }

    /**
     * 查某个包名有没有焦点通知权限。
     *
     * 这是决定「能不能上岛」的唯一直接信号。零风险：只是读 ContentProvider，
     * 不写任何设置，也不 hook 系统。查不到时按「没有」处理（fail-closed）。
     */
    fun queryCanShowFocus(context: Context, packageName: String): Boolean {
        return try {
            val uri = Uri.parse("content://miui.statusbar.notification.public")
            val extras = Bundle().apply { putString("package", packageName) }
            val bundle = context.contentResolver.call(uri, "canShowFocus", null, extras)
            bundle?.getBoolean("canShowFocus", false) ?: false
        } catch (throwable: Throwable) {
            Log.d(TAG, "canShowFocus 查询失败（多半是系统不认这个 provider）", throwable)
            false
        }
    }

    /**
     * 发一条带 `miui.focus.param` 的测试通知。
     *
     * 最小验证用：不改白名单、不 hook 系统，只是试图让系统认这条通知。
     * `timeout = -1`（5 秒后自动消失）—— 焦点通知文档里明确支持这个值，
     * 免得测试通知赖在通知栏。
     *
     * @return false 表示通知权限本身就没给（连普通通知都发不出去）
     */
    fun sendProbeNotification(context: Context, title: String, text: String): Boolean {
        val manager = NotificationManagerCompat.from(context)
        val notificationsEnabled = manager.areNotificationsEnabled()
        record(context, "开始发送测试通知 通知开关=$notificationsEnabled | channel=$CHANNEL_ID")
        if (!notificationsEnabled) {
            record(context, "中止：通知权限未授予，连普通通知都发不出去")
            return false
        }

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("超级岛探测")
                .setDescription("仅用于测试本机是否支持小米焦点通知")
                .build()
        )

        /*
         * 三路对照实验，一次点击同时发三条，用「哪条上了岛」定位问题层：
         *
         *   5377  完整   —— 有图标 + 有文字区
         *   5376  纯文字 —— 有文字区、**完全不给 picInfo**
         *   5378  对照   —— 不带 miui.focus.param，纯普通通知
         *
         * 为什么要这个纯文字变体：前几轮我把候选原因收窄到
         * 「图标解析失败导致整个岛布局被丢弃」。如果纯文字那条能上岛，
         * 就证明协议结构没问题、锅在图标（VectorDrawable 跨进程渲染不可靠）。
         *
         * 图标同时从 VectorDrawable 换成 Bitmap：焦点通知由 SystemUI 进程解析，
         * Icon.createWithResource 指向 .xml 矢量图时跨进程可能拿不到实际像素。
         */
        val icon = buildBitmapIcon(context)

        val fullParams = islandParams(withPic = true, title = title, text = text)
        val textOnlyParams = islandParams(withPic = false, title = title, text = text)

        val results = mutableListOf<String>()

        // ① 完整变体
        results += notifyOne(
            context, manager, NOTIFICATION_ID, title, text, fullParams, icon,
            label = "完整(带Bitmap图标)",
        )
        // ② 纯文字变体
        results += notifyOne(
            context, manager, NOTIFICATION_ID_TEXT_ONLY, "$title（纯文字）", text,
            textOnlyParams, null, label = "纯文字(无picInfo)",
        )
        // ③ 普通对照
        results += runCatching {
            val plain = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("$title（普通）")
                .setContentText("不带 miui.focus.param，用来对照")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFICATION_ID_PLAIN, plain)
            record(context, "已发对照通知 id=$NOTIFICATION_ID_PLAIN（不带 miui.focus.param）")
            "对照=已发"
        }.getOrElse {
            record(context, "对照通知发送失败：${it.javaClass.simpleName} ${it.message}")
            "对照=失败"
        }

        record(context, "本批结果：${results.joinToString(" | ")}")
        return true
    }

    /** 发一条带焦点参数的岛通知，返回「是否进了系统活跃列表」的简短结论。 */
    private fun notifyOne(
        context: Context,
        manager: NotificationManagerCompat,
        id: Int,
        title: String,
        text: String,
        params: String,
        icon: Icon?,
        label: String,
    ): String {
        val extras = Bundle().apply {
            putString("miui.focus.param", params)
            // 只有真的给了图标才挂 pics Bundle；纯文字变体连这个都不给，
            // 这样才能干净地区分「结构问题」和「图标问题」
            icon?.let { putBundle("miui.focus.pics", Bundle().apply { putParcelable(PIC_KEY, it) }) }
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
            .addExtras(extras)
            .build()
        return runCatching {
            manager.notify(id, notification)
            val active = runCatching { manager.activeNotifications?.map { it.id } }.getOrNull()
            val inSystem = active?.contains(id) == true
            record(
                context,
                "[$label] id=$id 已发送 | 在活跃列表=$inSystem | param=${params.replace(Regex("\\s+"), "")}",
            )
            "$label=${if (inSystem) "在" else "不在"}"
        }.getOrElse {
            record(context, "[$label] id=$id 发送异常：${it.javaClass.simpleName} ${it.message}")
            "$label=异常"
        }
    }

    /**
     * 把 App 图标渲染成 Bitmap 再包成 Icon。
     *
     * 不用 Icon.createWithResource(context, R.drawable.ic_cloud_download)：
     * 那是 VectorDrawable，SystemUI 进程解析时拿不到像素的风险较高。
     * 万一 toBitmap 失败，退回一块纯色，至少保证图标存在。
     */
    private fun buildBitmapIcon(context: Context): Icon? {
        return runCatching {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                Icon.createWithBitmap(drawable.toBitmap(96, 96))
            } else {
                solidIcon()
            }
        }.getOrElse {
            record(context, "图标渲染失败，退回纯色：${it.javaClass.simpleName}")
            runCatching { solidIcon() }.getOrNull()
        }
    }

    private fun solidIcon(): Icon {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(Color.parseColor("#FF7A3A"))
        return Icon.createWithBitmap(bitmap)
    }

    /**
     * 生成 param_v2。结构以社区库 hyperisland_kit
     * （github.com/D4vidDf/HyperIsland-ToolKit，已发布在 Maven Central）为准，
     * 它是真机跑通过的实现；官方文档那份示例的字段名对不上当前系统。
     *
     * @param withPic false 时**完全不输出任何 picInfo**，用来做「纯文字岛」对照
     */
    private fun islandParams(withPic: Boolean, title: String, text: String): String {
        // 注意：picInfo 段必须整体出现或整体消失，留半个对象反而是非法结构
        val leftPic = if (withPic) """"picInfo":{"type":1,"pic":"$PIC_KEY"},""" else ""
        val rightPic = if (withPic) ""","picInfo":{"type":1,"pic":"$PIC_KEY"}""" else ""
        // SmallIslandArea 只有 combinePicInfo / picInfo 两个字段，没有 type。
        // 少了 picInfo 就是一个合法的空对象 {}
        val smallPic = if (withPic) """"picInfo":{"type":1,"pic":"$PIC_KEY"}""" else ""
        val tickerPic = if (withPic) """"tickerPic":"$PIC_KEY",""" else ""
        return """
            {"param_v2":{
              "protocol":3,
              "business":"download",
              "updatable":true,
              "ticker":"抖音下载器",
              $tickerPic
              "enableFloat":true,
              "islandFirstFloat":true,
              "timeout":2,
              "param_island":{
                "islandProperty":1,
                "islandPriority":2,
                "islandTimeout":15,
                "needCloseAnimation":true,
                "bigIslandArea":{
                  "imageTextInfoLeft":{
                    "type":1,
                    $leftPic
                    "textInfo":{
                      "title":"$title",
                      "content":"$text",
                      "showHighlightColor":false
                    }
                  }$rightPic
                },
                "smallIslandArea":{
                  $smallPic
                }
              },
              "baseInfo":{
                "type":1,
                "title":"抖音下载器",
                "content":"$text",
                "colorTitle":"#FF7A3A"
              }
            }}
        """.trimIndent().replace(Regex(",\\s*,", RegexOption.IGNORE_CASE), ",")
    }


    /** 反射读 boolean 系统属性。取不到按 false。 */
    private fun readBooleanProperty(key: String): Boolean {
        return try {
            @Suppress("PrivateApi")
            val get = Class.forName("android.os.SystemProperties")
                .getMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
            get.invoke(null, key, false) as? Boolean ?: false
        } catch (throwable: Throwable) {
            // 顺手用 Build 信息兜一下：非小米设备直接 false
            false
        }
    }

    /** 是不是小米/红米设备（只用来决定设置页显不显示这一项）。 */
    fun isXiaomiDevice(): Boolean {
        val brand = Build.BRAND.orEmpty().lowercase()
        val manufacturer = Build.MANUFACTURER.orEmpty().lowercase()
        return brand.contains("xiaomi") || brand.contains("redmi") ||
            brand.contains("poco") || manufacturer.contains("xiaomi")
    }
}

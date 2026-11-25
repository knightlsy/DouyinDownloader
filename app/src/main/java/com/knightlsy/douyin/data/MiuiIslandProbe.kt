package com.knightlsy.douyin.data

import android.content.Context
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
    /** 对照用：不带 focus 参数的普通通知 */
    private const val NOTIFICATION_ID_PLAIN = 0x1502

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
        record(
            context,
            "开始发送测试通知 通知开关=$notificationsEnabled"
                    + " | channel=$CHANNEL_ID",
        )
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
         * param_v2 结构（照抄官方示例模板）。
         *
         * 前两次失败的教训：
         *  · timeout = -1 是「5 秒后消失」，测的时候根本来不及看
         *  · param_island 的 bigIslandArea / smallIslandArea 是**必选且要按模板填**，
         *    之前给的是空 {}，系统没有内容可渲染，直接跳过岛 —— 所以通知在、
         *    岛不在。这个字段不是"可选装饰"，是岛的正文。
         *
         * picInfo.pic 只是**键引用**，真实图标要从 extras 里的
         * "miui.focus.pics" Bundle 取（见下面的 pics），否则图标是空的。
         */
        val params = """
            {"param_v2":{
              "protocol":1,
              "business":"download",
              "enableFloat":true,
              "updatable":true,
              "ticker":"抖音下载器",
              "tickerPic":"miui.focus.pic_icon",
              "timeout":5,
              "param_island":{
                "islandProperty":1,
                "islandTimeout":120,
                "bigIslandArea":{
                  "imageTextInfoLeft":{
                    "type":1,
                    "picInfo":{"type":1,"pic":"miui.focus.pic_icon"},
                    "miui.focus.paramtextInfo":{
                      "frontTitle":"抖音下载器",
                      "title":"超级岛测试",
                      "content":"能看到这条说明岛通了",
                      "useHighLight":false
                    }
                  },
                  "picInfo":{"type":1,"pic":"miui.focus.pic_icon"}
                },
                "smallIslandArea":{
                  "picInfo":{"type":1,"pic":"miui.focus.pic_icon"}
                }
              },
              "baseInfo":{
                "title":"抖音下载器",
                "content":"超级岛联通性测试",
                "colorTitle":"#FF7A3A",
                "type":2
              }
            }}
        """.trimIndent()

        // 岛 JSON 里引用的 miui.focus.pic_xxx，实体图标从这个 Bundle 取
        val pics = Bundle().apply {
            putParcelable(
                "miui.focus.pic_icon",
                Icon.createWithResource(context, R.drawable.ic_cloud_download),
            )
        }
        val notificationExtras = Bundle().apply {
            putString("miui.focus.param", params)
            putBundle("miui.focus.pics", pics)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // 焦点通知要能更新/常驻观察，先不自动取消
            .setAutoCancel(false)
            .setOngoing(false)
            .addExtras(notificationExtras)
            .build()

        /*
         * 先发一条**不带** miui.focus.param 的普通通知做对照。
         *
         * 有了它，一次点击就能区分三种失败：
         *  · 两条都出现 → 通知系统正常，是 focus 协议体没被系统识别
         *  · 只有普通那条 → focus 协议体被判非法，整条被丢弃
         *  · 两条都没有 → 通知权限/渠道层面就没通，跟岛无关
         */
        runCatching {
            val plain = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("$title（普通）")
                .setContentText("不带 miui.focus.param，用来对照")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            manager.notify(NOTIFICATION_ID_PLAIN, plain)
            record(context, "已发对照通知 id=$NOTIFICATION_ID_PLAIN（不带 miui.focus.param）")
        }.onFailure { record(context, "对照通知发送失败：${it.javaClass.simpleName} ${it.message}") }

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            // 发送后立刻回读活跃通知：能看到 id 说明通知真的进了系统，
            // 看不到说明被系统层直接丢弃（协议体非法时就是这样）
            val active = runCatching { manager.activeNotifications?.map { it.id } }
                .getOrNull()
            record(context, "已发焦点通知 id=$NOTIFICATION_ID")
            record(context, "param_v2=${params.replace(Regex("\\s+"), "")}")
            record(
                context,
                "发送后活跃通知=${active?.joinToString(",") ?: "读取失败"}"
                        + " | 包含焦点通知=${active?.contains(NOTIFICATION_ID) == true}"
                        + " | 包含对照通知=${active?.contains(NOTIFICATION_ID_PLAIN) == true}",
            )
            true
        } catch (throwable: Throwable) {
            Log.w(TAG, "发送测试通知失败", throwable)
            false
        }
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

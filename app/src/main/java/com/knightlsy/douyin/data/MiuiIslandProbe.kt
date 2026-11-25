package com.knightlsy.douyin.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        Log.i(
            TAG,
            "probe: island=${result.supportsIsland} protocol=${result.protocolVersion}"
                    + "(${result.protocolLabel}) canShowFocus=${result.hasFocusPermission}"
                    + " → ${result.describe()}",
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
        if (!manager.areNotificationsEnabled()) {
            Log.w(TAG, "通知权限未授予，测试通知发不出去")
            return false
        }

        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("超级岛探测")
                .setDescription("仅用于测试本机是否支持小米焦点通知")
                .build()
        )

        /*
         * param_v2 是文档里的根对象。
         *
         * 上一版的两个坑（导致「通知栏都看不到」）：
         *  1. timeout = -1 按文档是「5 秒后消失」，太快了，测的时候根本来不及看。
         *     现在用 1（分钟），留足观察时间。
         *  2. param_island 只给了 islandProperty，缺 bigIslandArea / smallIslandArea /
         *     shareData。官方示例里这三个是有的 —— 协议体不完整时系统很可能
         *     直接丢弃整条通知，连普通通知形态都不剩。
         */
        val params = """
            {"param_v2":{
              "business":"download",
              "protocol":1,
              "timeout":1,
              "islandFirstFloat":true,
              "param_island":{
                "islandProperty":1,
                "bigIslandArea":{},
                "smallIslandArea":{},
                "shareData":{}
              }
            }}
        """.trimIndent()

        val extras = Bundle().apply {
            putString("miui.focus.param", params)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            // 焦点通知要能更新/常驻观察，先不自动取消
            .setAutoCancel(false)
            .setOngoing(false)
            .addExtras(extras)
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
        }.onFailure { Log.w(TAG, "普通对照通知发送失败", it) }

        return try {
            manager.notify(NOTIFICATION_ID, notification)
            val active = manager.activeNotifications
            Log.i(
                TAG,
                "已发送焦点通知测试 id=$NOTIFICATION_ID"
                        + " param=${params.replace("\n", "")}"
                        + " 当前活跃通知数=${active.size}"
                        + " channel锁=${manager.areNotificationsEnabled()}",
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

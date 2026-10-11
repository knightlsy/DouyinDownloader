package com.knightlsy.douyin.data

import android.os.Build
import java.lang.reflect.Method

/**
 * 机型识别：把 `Build.MODEL` 的机型代码（如 `23127PN0CC`）还原成用户认得的名字（如 `Xiaomi 14`）。
 *
 * 数据来源按可靠性排序：
 *
 * 1. **系统属性 `ro.product.marketname`** —— 小米/红米官方机型名（HyperOS 上即设置里显示的
 *    设备名称）。实测设备可直接取到，最准确，且能覆盖后续新机型，无需维护。
 * 2. **厂商自带属性**（`ro.vivo.market.name` / `ro.config.marketing_name` 等）—— 其它品牌。
 * 3. **内置映射表** —— 兜底（仅用于系统属性取不到的老机型/精简 ROM）。
 * 4. 都拿不到则原样返回 `厂商 + 型号代码`。
 *
 * 注意：反射读取只用于“获取机型显示名”，失败一律回退，不影响任何功能。
 */
object DeviceNaming {

    /**
     * 机型代码 -> 官方机型名。
     *
     * 依据 `khwang9883/MobileModels` 型号汇总库（业界常用的小米/vivo 国行型号对照表）
     * 逐条核对，拿不准的不写 —— 宁可回退到系统属性或原始型号，也不写错。
     */
    private val MODEL_CODE_TO_NAME: Map<String, String> = mapOf(
        // --- 小米 / 红米（国行）---
        "23127PN0CC" to "Xiaomi 14",
        "23127PN0CG" to "Xiaomi 14",
        "23116PN5BC" to "Xiaomi 14 Pro",
        "2311BPN23C" to "Xiaomi 14 Pro Ti Satellite",
        "24117RK2CC" to "REDMI K80",
        "24127RK2CC" to "REDMI K80 Pro",
        "25060RK16C" to "REDMI K80 Ultra",
        "22081212C" to "Redmi K50 Ultra",
        "22041211A" to "Redmi K50",
        "24129RT7CC" to "Redmi Turbo 4",
        "25053RT47C" to "Redmi Turbo 4 Pro",
        "2510DRK44C" to "REDMI K90",
        "25102RKBEC" to "REDMI K90 Pro Max",
        "25079RPDCC" to "REDMI K Pad",
        // --- 三星 ---
        "SM-S9110" to "Samsung Galaxy S23",
        "SM-S911B" to "Samsung Galaxy S23",
        "SM-S9180" to "Samsung Galaxy S23 Ultra",
        // --- vivo ---
        "V2309A" to "vivo X100",
        "V2359A" to "vivo X100s",
        "PA2553" to "vivo Pad5",
        // --- 魅族 ---
        "MEIZU 20" to "MEIZU 20",
        // --- realme ---
        "RMX3366" to "realme GT Master Explorer"
    )

    /** 优先读取的系统属性（顺序即优先级） */
    private val MARKET_NAME_PROPS = listOf(
        "ro.product.marketname",      // 小米/红米：HyperOS 里的设备名称
        "ro.vivo.market.name",        // vivo
        "ro.config.marketing_name",   // 三星等
        "ro.product.oppo_market_name",// OPPO
        "ro.product.market_name",
        "ro.product.vendor.marketname"
    )

    /** 反射读取系统属性（隐藏 API，失败返回 null） */
    private fun getSystemProperty(key: String): String? = try {
        val cls = Class.forName("android.os.SystemProperties")
        val get = cls.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()?.takeIf { it.isNotEmpty() }
    } catch (_: Throwable) {
        null
    }

    /** 由厂商 + 型号代码拼出可读名称 */
    private fun fallback(model: String): String {
        val maker = Build.MANUFACTURER?.trim().orEmpty()
        if (maker.isEmpty()) return model
        // 型号代码里已含厂商名就不重复拼
        return if (model.lowercase().startsWith(maker.lowercase())) model else "$maker $model"
    }

    /**
     * 返回用户可读的机型名。
     *
     * 例：`23127PN0CC` + 小米 -> `Xiaomi 14`；拿不到就回退 `Xiaomi 23127PN0CC`。
     */
    fun displayName(): String {
        val model = Build.MODEL?.trim().orEmpty()
        // 1) 系统属性（最准）
        for (prop in MARKET_NAME_PROPS) {
            getSystemProperty(prop)?.let { return it }
        }
        // 2) 映射表兜底
        MODEL_CODE_TO_NAME[model]?.let { return it }
        // 3) 原始型号
        return if (model.isEmpty()) (Build.MANUFACTURER ?: "未知") else fallback(model)
    }

    /**
     * 原始型号代码，与 [displayName] 一起上报：
     * 显示用可读名，排查时仍可按代码精确区分同一机型的地区版本。
     */
    fun modelCode(): String = Build.MODEL?.trim().orEmpty()
}

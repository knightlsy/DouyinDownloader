# 抖音下载器 · 动效规范

> 依据：液态玻璃（Liquid Glass）质感。核心不是"动得快"，是**形变连续**。

## 页面级：不动

沿用现有 `AppNavigation.kt` 的实现，本次改造不碰：

```
350ms · 缩放 0.92 → 1.0 · 横向滑动 · AnimatedContent
```

## 按钮级：液态玻璃

### ① 分段滑块（拖尾型）

三段动画同时跑，形成"前缘冲出、后缘拖拽"的拉丝感。

| 层 | 时长 | 延迟 | 宽度变化 | 圆角 | 曲线 |
|---|---|---|---|---|---|
| 主体 | 340ms | 0 | 原值 → 1.22× → 1.07× → 原值 | 12→17→13→12dp | `cubic-bezier(.32,1.3,.55,1)` |
| 拖尾 | 420ms | 60ms | 原值 → 1.34× → 原值 | 跟随主体 | `cubic-bezier(.3,1.05,.6,1)` |
| 拖尾透明度 | 420ms | 60ms | 0.55 → 0.34 → 0 | — | 同上 |

拖尾层 `filter: blur(7px)` + `opacity` 渐隐。**关键在 60ms 延迟和 1.34 倍超宽**——主体到位时拖尾还在路上，两者重叠的瞬间才有液体感。

Compose 对应：

```kotlin
object BgMotion {
    const val SegBodyMs = 340
    const val SegTrailMs = 420
    const val SegTrailDelayMs = 60L
    const val SegStretch = 1.22f
    const val SegTrailStretch = 1.34f

    val SegBodySpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )
}
```

### ② 开关 knob

| 阶段 | 时长 | 形变 |
|---|---|---|
| 常态滑动 | 280ms | 24×24 圆，left 2→24dp |
| 按住 | 即时 | 压扁成 30×24，left 相应回缩 |
| 松手 | 280ms | 回圆，带轻微过冲 |

曲线 `cubic-bezier(.34,1.5,.64,1)`。
轨道底色 240ms 线性过渡到强调色。

### ③ 底部 Tab 指示条

```
240ms · cubic-bezier(.34,1.45,.64,1)
宽 26dp · 高 2dp · 圆角 1dp
外发光 drop-shadow(0 0 6px rgba(255,122,58,.55))
```

指示条随选中项横向滑动，同时文字 200ms 变色 + 字重 500→650。
**不整片染色**，只靠指示条和文字提亮表达选中。

### ④ 主按钮

| 状态 | 时长 | 变化 |
|---|---|---|
| 按下 | 即时→160ms | `scale .972`、内阴影 1dp→2dp 6dp 加深 |
| 松手 | 160ms | 回弹（轻微过冲） |
| 高光 | 420ms | 斜向白色渐变从左扫到右 |

曲线 `cubic-bezier(.34,1.5,.64,1)`。按下快、松手慢，形成"按进软胶"的手感。

### ⑤ 勾选框

```
220ms · cubic-bezier(.34,1.7,.64,1)   ← 过冲最大
圆角 9dp → 50%（方形变圆）
背景 透明 → 强调绿
勾号 scale(0) → scale(1)
```

面积小，需要更强的过冲才看得出来。

## 全局约束

**① 进入慢、退出快**
用户注意力在"出现"上。出现用 260–340ms，消失用 150–180ms。

**② 位移不超过 16dp**
超过就是浮夸的滑动，不是"轻"。

**③ 不用无限循环动画**
省电，也不干扰阅读。

**④ 所有形变必须可回退**
动画只改 `scale` / `width` / `radius` / `alpha` / `color`，不改布局约束，避免触发重排导致掉帧。

## Compose 实现注意

```kotlin
// ✅ 用 graphicsLayer 做形变，不触发布局
Modifier.graphicsLayer {
    scaleX = stretch
    scaleY = 1f
}

// ❌ 不要用 animateContentSize 做滑块形变
//    它每帧都会触发 measure/layout
```

`spring()` 的 `dampingRatio` 对应 CSS 的过冲强度：

| CSS cubic-bezier 第二参数 | Compose dampingRatio |
|---|---|
| 1.3（轻微） | 0.85 |
| 1.42（中等） | 0.72 |
| 1.5（明显） | 0.62 |
| 1.7（强弹） | 0.50 |

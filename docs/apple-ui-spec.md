# 睿智校园 · Apple 风格 UI 精确设计规格（主方案：全屏 Apple 仪表盘 bath-full）

- 抽取来源（只读）：`docs/apple-design-preview.html`（1109 行）
- 抽取范围：`<body>` 内 `<h2>主方案 · 全屏 Apple 仪表盘（浅色 / 深色适配）</h2>` 区块
  - 主界面 HTML：第 485–545 行（`.screen.bath.bath-full#bathScreen`）
  - 主界面 CSS：第 209–235、260–450 行（`.bath-full` / `.full-*`，含同名选择器的后置覆盖）
  - 设备弹窗 HTML：第 781–837 行（`#devicePicker`）
  - 设置弹窗 HTML：第 838–873 行（`#accountMenu`）
  - 弹窗 CSS：第 244–260、313–336、341–344、365–401、442–449 行
  - 状态文案数据：第 926–963 行（`bathStates`）
  - 交互脚本：第 893–924 行、第 964–1021 行
- CSS 层叠说明（重要）：本文件的 CSS 有多轮追加、后写覆盖先写。本文档给出的值一律是**最终生效值**（最后一个同名声明的值）。凡本文档标注「最终值」处，即为覆盖链末端的取值。
- 单位：本文档所有 px 均为 CSS px，按 1 CSS px = 1 dp 直接映射到 Android。
- 字体栈：`-apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Helvetica Neue", Arial, sans-serif`；等宽处为 `ui-monospace, SFMono-Regular, Menlo, monospace`。Android 端建议 `sans-serif` / 数字用 `sans-serif-medium` 或 `Roboto` + `android:fontFeatureSettings`。字体全局切换有 `transition:background .25s,color .25s`，Android 端对应 250 ms 主题切换过渡。
- **HTML 内部不一致提示（先看这里）**：规格中所有「HTML 未定义」或「选择器被覆盖」的地方已就地标注，见第 8 章汇总。

---

## 1. 设计 tokens 表

### 1.1 颜色 · 浅色主题（`body[data-theme="light"]`，即 `:root`）

| 用途 | HTML 取值（精确） | 建议 Android 资源名 | 是否已存在 |
|---|---|---|---|
| 页面背景 background | `#f5f5f7` | `@color/apple_bg` | 已存在（`#F5F5F7`） |
| 卡片/表面 card、surface | `#ffffff` | `@color/apple_surface` | 已存在（`#FFFFFFFF`） |
| 正文文字 text | `#1d1d1f` | `@color/apple_text` | 已存在（`#1D1D1F`） |
| 次要文字 secondary | `#86868b` | `@color/apple_secondary` | 已存在（`#86868B`） |
| 分隔线 line（圆环轨道、抓取条、输入框描边） | `#d2d2d7` | `@color/apple_line` | 已存在（`#D2D2D7`） |
| 蓝色 blue（主按钮、链接、进度弧） | `#0071e3` | `@color/apple_blue` | 已存在（`#0071E3`） |
| 蓝色悬停 blue-hover | `#0077ed` | 建议新增 `@color/apple_blue_hover` | **不存在** |
| 绿色 green（成功/空闲/结算） | `#34c759` | `@color/apple_green` | 已存在（`#34C759`） |
| 红色 red（危险/停止/异常） | `#ff3b30` | `@color/apple_red` | 已存在（`#FF3B30`） |
| 紫色 purple（旧版 token，主方案未使用） | `#a13ff6` | 建议新增 `@color/apple_purple` | **不存在** |
| 琥珀色 amber（关阀中） | `#ff9f0a` | 建议新增 `@color/apple_amber` | **不存在** |
| chip 背景 chip-bg | `rgba(52,199,89,.14)` → 铺在 `#f5f5f7` 上 ≈ `#E3F4E7`；铺在 `#ffffff` 上 ≈ `#EBF8ED` | 建议新增 `@color/apple_chip_bg`（**必须用 alpha 版**：`#2434C759`，见下方说明） | **不存在** |
| chip 边框 chip-border | `rgba(52,199,89,.45)` → 不透明近似 `#A2E3AC` | 建议新增 `@color/apple_chip_border`（alpha 版 `#7334C759`） | **不存在** |
| chip 发光 glow（圆环 drop-shadow） | `rgba(52,199,89,.35)` | 建议新增 `@color/apple_glow`（alpha 版 `#5934C759`） | **不存在** |
| 主按钮阴影 primary-shadow | `rgba(0,113,227,.30)` | 建议新增 `@color/apple_primary_shadow`（`#4D0071E3`） | **不存在** |
| 卡片阴影 shadow token（旧值） | `0 8px 30px rgba(0,0,0,.08)` | 见 1.4 阴影表 | — |
| 强阴影 shadow-strong（旧值） | `0 20px 60px rgba(0,0,0,.18)` | 见 1.4 阴影表 | — |
| chip 绿色文字 `.chip.green` | `#248a3d` | 建议新增 `@color/apple_chip_green_text` | **不存在** |
| 输入框（`.picker-input` / `.field input` 深色底） | `#f5f5f7`（浅色下 background = `var(--gray)`） | `@color/apple_bg` | 已存在 |

> **alpha 说明**：HTML 直接用 `rgba()`。Android 建议保留半透明，写法 `#AARRGGBB`（alpha 0.14 → `0x24`，0.45 → `0x73`，0.35 → `0x59`，0.30 → `0x4D`，0.12 → `0x1F`，0.10 → `0x1A`，0.18 → `0x2E`）。上表「不透明近似值」仅供比对，不要用于写盘资源。

### 1.2 颜色 · 深色主题（`body[data-theme="dark"]`）

| 用途 | HTML 取值（精确） | 建议 Android 资源名 | 是否已存在 |
|---|---|---|---|
| 页面背景 background | `#000000` | `@color/apple_bg`（values-night） | 已存在（`#000000`） |
| 卡片/表面 card | `#1c1c1e` | `@color/apple_surface`（values-night） | 已存在（`#1C1C1E`） |
| 正文文字 text | `#f5f5f7` | `@color/apple_text`（values-night） | 已存在（`#F5F5F7`） |
| 次要文字 secondary | `#a1a1a6` | `@color/apple_secondary`（values-night） | 已存在（`#A1A1A6`） |
| 分隔线 line | `#38383a` | `@color/apple_line`（values-night） | 已存在（`#38383A`） |
| 蓝色 blue | `#0a84ff` | `@color/apple_blue`（values-night） | 已存在（`#0A84FF`） |
| 绿色 green | `#30d158` | `@color/apple_green`（values-night） | 已存在（`#30D158`） |
| 红色 red | `#ff453a` | `@color/apple_red`（values-night） | 已存在（`#FF453A`） |
| 琥珀色 amber | `#ff9f0a` | `@color/apple_amber` | **不存在，两套主题同值** |
| chip 背景 chip-bg | `rgba(48,209,88,.12)` → 铺在 `#000000` 上 ≈ `#0A1A0B` | `@color/apple_chip_bg`（values-night，`#1F30D158`） | **不存在** |
| chip 边框 chip-border | `rgba(48,209,88,.45)` | `@color/apple_chip_border`（values-night，`#7330D158`） | **不存在** |
| chip 发光 glow | `rgba(48,209,88,.35)` | `@color/apple_glow`（values-night，`#5930D158`） | **不存在** |
| primary-shadow | `rgba(10,132,255,.30)` | `@color/apple_primary_shadow`（`#4D0A84FF`） | **不存在** |
| 卡片阴影 shadow token | `0 8px 30px rgba(0,0,0,.45)` | 见 1.4 | — |
| 强阴影 shadow-strong | `0 20px 60px rgba(0,0,0,.65)` | 见 1.4 | — |
| chip 绿色文字（深色） | `#5ee07a` | 建议新增 `@color/apple_chip_green_text`（values-night） | **不存在** |
| 深色下的输入框/次级面板（`.field input`、`.btn.secondary`、`.stat`、`.compact-icon`） | `#2c2c2e` | 建议新增 `@color/apple_surface_2` | **不存在** |
| `.picker-icon` 在深色下的底色 | `var(--card)` = `#1c1c1e` | `@color/apple_surface` | 已存在 |
| 深色下蓝色 tint（`.dash-icon` / `.minimal-drop`，主方案未用） | `rgba(10,132,255,.16)` | 建议新增 `@color/apple_blue_tint_dark` | **不存在** |

**深色主题未覆盖、沿用浅色取值的 token（重要）**：`:root` 中的 `--black`、`--white`、`--purple`、`--button-radius`、`--card-radius`、`--input-radius` 在 `body[data-theme="dark"]` 中没有重新声明，因此深色下仍是 `#000000 / #ffffff / #a13ff6 / 999px / 22px / 14px`。

### 1.3 圆角

| 元素 | 选择器 | 最终值 |
|---|---|---|
| 手机外框（仅预览用，Android 不需要） | `.phone` | `46px` |
| 屏幕内框（仅预览用） | `.screen` | `38px` |
| 卡片基准圆角 | `--card-radius` | `22px` |
| 胶囊按钮/胶囊 chip | `--button-radius` | `999px` |
| 输入框基准圆角 | `--input-radius` | `14px` |
| 设备详情行按钮 `.full-device-btn` | 后置覆盖 | `16px`（原声明 `14px` 被覆盖） |
| 小卡 `.full-stat`（协议/水阀） | 后置覆盖 | `16px`（原声明 `14px` 被覆盖） |
| 圆环容器 `.full-gauge` | — | `50%`（正圆） |
| 顶部刷新按钮 `.full-refresh` | 后置覆盖 | `50%`，尺寸 `32px × 32px`（原声明 `40px × 40px` 被覆盖） |
| 右上角设置齿轮按钮 `.full-account-icon` | — | `50%`，尺寸 `36px × 36px` |
| 状态 chip `.full-chip` | — | `999px`，高 `32px` |
| 设备弹窗/设置弹窗 `.sheet-dialog` | — | `28px 28px 0 0`（仅顶部圆角，底部贴边直角） |
| 弹窗基准（`.picker-dialog`） | — | `28px`（被 `.sheet-dialog` 覆盖为顶部圆角） |
| 弹窗关闭按钮 `.picker-close` | — | `50%`，`34px × 34px` |
| 弹窗当前设备块 `.picker-current` | — | `18px` |
| 弹窗列表行 `.picker-row` | — | `18px` |
| 弹窗列表行内图标 `.picker-icon` | — | `13px`，`42px × 42px` |
| 弹窗描边/危险按钮 | `.picker-action*` | `999px`，高 `50px` |
| 弹窗手动 MAC 输入框 `.picker-input` | — | `14px`，高 `48px` |
| 弹窗「连接此设备」按钮 `.picker-connect` | — | `14px`，高 `48px` |
| 弹窗锁定提示条 `.picker-lock` | — | `14px` |
| 抓取条 `.sheet-grabber` | — | `999px`，`38px × 5px` |
| 设置页账号头像块 `.account-hero` | — | `20px` |
| 设置页头像按钮 `.account-avatar` | — | `50%`，`52px × 52px` |
| 设置页刷新按钮 `.account-refresh` | 后置覆盖 | `50%`，`36px × 36px`（原声明 `32px` 被覆盖） |
| 设置页操作行 `.account-action` | — | `16px`，高 `52px` |
| 调试日志块 `.log`（非主方案） | — | `16px` |

### 1.4 阴影 / 发光

| 用途 | HTML 取值（精确） | Android 建议 |
|---|---|---|
| 顶部刷新按钮 `.full-refresh` | `box-shadow:none`（后置覆盖，原为 `0 6px 18px rgba(0,0,0,.06)`） | 无阴影 |
| 右上角设置齿轮 `.full-account-icon` | `0 6px 18px rgba(0,0,0,.06)` | `elevation="2dp"`（近似） |
| 设备详情行 `.full-device-btn` | `0 6px 18px rgba(0,0,0,.04)` | `elevation="2dp"`（近似） |
| 小卡 `.full-stat` | `0 6px 18px rgba(0,0,0,.04)` | `elevation="2dp"`（近似） |
| 圆环容器内阴影 `.full-gauge` | `inset 0 0 22px rgba(128,128,128,.08)` | 无法用 elevation 表达；用一层 `foreground` 径向渐变或 1dp `#14808080` 描边近似 |
| 圆环容器径向底纹 `.full-gauge` | `radial-gradient(circle at 50% 35%, rgba(128,128,128,.10), transparent 64%)` | 自定义 drawable 径向渐变 |
| 圆环进度弧发光 | `drop-shadow(0 0 10px var(--glow))`（浅 `rgba(52,199,89,.35)`）；starting/bathing 为 `rgba(10,132,255,.35)`；stopping `rgba(255,159,10,.35)`；error `rgba(255,69,58,.35)` | Paint `setShadowLayer(10, 0, 0, glowColor)` |
| 主按钮 `.full-btn` | `0 14px 30px var(--primary-shadow)`（浅 `rgba(0,113,227,.30)`，深 `rgba(10,132,255,.30)`） | `elevation="6dp"`（近似） |
| 洗浴中主按钮 `.full-btn` | `0 14px 30px rgba(255,59,48,.25)` | 同上，颜色 `#40FF3B30` |
| 弹窗 `.sheet-dialog` | `0 30px 80px rgba(0,0,0,.28)` | `elevation="16dp"`（近似） |
| 弹窗遮罩 `.picker-dialog::backdrop` | `background:rgba(0,0,0,.35)` + `backdrop-filter:blur(6px)` | scrim `#59000000`；blur 用 `RenderEffect`（API 31+）或省略 |
| 内容切换过渡 | `.full-value{transition:stroke .25s, stroke-dasharray .25s, stroke-dashoffset .25s, opacity .25s}` | 250 ms |
| 主按钮过渡 | `.full-btn{transition:background .25s, opacity .25s, transform .15s}` | 250 ms / 150 ms |

### 1.5 字号 / 字重 / 字间距（主方案 + 两个弹窗全量）

| 元素 | 选择器 | 字号 | 字重 | 字间距 letter-spacing | 行高 | 颜色 |
|---|---|---|---|---|---|---|
| 眉标 WATER CONTROL | `.full-eyebrow` | `11px` | `700` | `.18em` | 默认 | `var(--secondary)` |
| 主标题 洗澡仪表盘 | `.full-appbar h3`（含 `.full-appbar h3` 后置覆盖） | `25px` | 700（继承 h3） | `-.03em` | 默认 | `var(--text)` |
| 右侧大标题（备用） | `.full-title` | `30px` | `700` | `-.03em` | `1.12` | `var(--text)` |
| 状态 chip 文字 | `.full-chip` | `12px` | `700` | 默认 | — | 见第 3 章 |
| 圆环中心大字 idle | `.full-big` | `36px` | `700` | `-.05em` | `1` | `var(--text)` |
| 圆环中心大字 bathing | `.bath-full[data-state="bathing"] .full-big` | `30px` | `700` | `-.05em` | `1` | `var(--text)` |
| 圆环中心大字 starting/stopping/error | `.bath-full[data-state="…"] .full-big` | `28px` | `700` | `-.05em` | `1` | `var(--text)` |
| 圆环中心大字 settled | 无覆盖 | `36px` | `700` | `-.05em` | `1` | `var(--text)` |
| 圆环下方小字 | `.full-gauge-label` | `12px` | `700` | 默认 | — | `var(--green)`，`margin-top:4px` |
| 圆环 meta 小字 | `.full-gauge-meta` | `11px` | 默认(400) | 默认 | — | `var(--secondary)`，`margin-top:4px` |
| 设备名 宿舍 101 | `.full-device` | `19px` | `700` | `-.02em` | 默认 | `var(--text)`，`margin-top:18px` |
| 设备副标题 | `.full-sub` | `12px` | 400 | 默认 | — | `var(--secondary)`，`margin-top:3px` |
| 连接状态行 | `.full-connection` | `12px` | 400 | 默认 | — | `var(--secondary)`，`margin-top:6px` |
| 设备详情行小标签 蓝牙设备 | `.full-device-k` | `10px` | `600` | 默认 | — | `var(--secondary)` |
| 设备详情行值 | `.full-device-v` | `15px`（后置覆盖，原 `14px`） | `700` | 默认 | — | `var(--text)`（继承按钮 color） |
| 设备详情行箭头 › | `.full-device-chev` | `22px` | 默认 | 默认 | `1` | `var(--secondary)` |
| 小卡标签 协议/水阀 | `.full-stat .k` | `10px` | 400 | 默认 | — | `var(--secondary)`，`margin-bottom:3px` |
| 小卡值 | `.full-stat .v` | `15px`（后置覆盖，原 `14px`） | `700` | 默认 | — | `var(--text)` |
| 主按钮文字 | `.full-btn` | `16px` | `700` | 默认 | — | `#ffffff` |
| 账号行 | `.full-account` | `12px` | 400 | 默认 | — | `var(--secondary)`，`margin-top:6px` |
| 弹窗眉标 DEVICE CONTROL / SETTINGS | `.picker-eyebrow` | `11px` | `700` | `.16em` | — | `var(--secondary)` |
| 弹窗标题 选择洗澡设备 / 设置 | `.picker-title` | `24px` | `700` | `-.02em` | — | 继承 `var(--text)` |
| 弹窗关闭 × | `.picker-close` | `16px` | 400 | 默认 | — | `var(--secondary)` |
| 弹窗分组标题 当前设备/附近设备 | `.picker-section-title` | `12px` | `700` | `.04em` | — | `var(--secondary)`，`margin-bottom:8px` |
| 弹窗当前设备名 | `.picker-name` | `15px` | `700` | 默认 | — | 继承 `var(--text)` |
| 弹窗元信息 | `.picker-meta` | `12px` | 400 | 默认 | — | `var(--secondary)` |
| 弹窗徽标 | `.picker-badge` | `11px` | `700` | 默认 | — | `var(--secondary)`；`.green`=`var(--green)`；`.blue`=`var(--blue)` |
| 弹窗按钮（三态） | `.picker-action*` | `15px` | `700` | 默认 | — | 见第 5 章 |
| 弹窗锁定提示 | `.picker-lock` | `12px` | `600` | 默认 | — | `#ff9f0a` |
| 手动 MAC 标题 | `.picker-manual-title` | `14px` | `700` | 默认 | — | `var(--blue)`，`margin-bottom:10px` |
| 手动 MAC 输入框 | `.picker-input` | `14px` | 400 | 默认 | — | `var(--text)` |
| 连接此设备按钮 | `.picker-connect` | `14px` | `700` | 默认 | — | `#ffffff` |
| 设置页头像块标签 账号/余额 | `.account-label` | `12px` | `600` | 默认 | — | `var(--secondary)` |
| 设置页头像块值 | `.account-value` | `15px`（后置覆盖，原 `18px`） | `700` | 默认 | — | `var(--text)`，`white-space:nowrap; overflow:hidden; text-overflow:ellipsis` |
| 设置页刷新按钮 ↻ | `.account-refresh` | `17px` | 400 | 默认 | — | `var(--blue)` |
| 设置页操作行标题 检查更新/诊断日志/重新登录 | `.account-action-title` | `15px` | `600` | 默认 | — | `var(--text)`；`.danger` 为 `var(--red)` |
| 设置页操作行副标题 当前版本 0.1919 | `.account-action-sub` | `11px` | `500` | 默认 | — | `var(--secondary)` |
| 设置页操作行箭头 › | `.account-action .chev` | `18px` | 默认 | 默认 | — | `var(--secondary)` |
| 设置页操作行休息图标（`d` 无字体号，仅占位） | `.account-action-icon` | `16px` | 400 | 默认 | — | `var(--blue)` |
| 设置页**旧版**操作行（无副标题） | `.account-action` | `15px` | `600` | 默认 | — | `var(--text)`，高 `52px`，`border-radius:16px` |
| 密码显示切换 👁/🙈 | `.login-eye`（非主方案参考） | `16px` | — | — | — | `var(--secondary)`；输入框 `padding-right:52px` |

### 1.6 间距 / 布局常量（主方案）

| 项 | 选择器 | 精确值 |
|---|---|---|
| 屏幕内容区内边距 | `.bath-full .screen-body` | `padding:0 20px 18px` |
| 内容区布局 | `.bath-full .screen-body` | `display:flex; flex-direction:column; overflow:hidden`（**不滚动**） |
| 顶栏内边距 | `.full-appbar` | `padding:2px 0 6px`（`display:flex; align-items:center; justify-content:space-between`） |
| 主区栅格 | `.full-hero`（最终值） | `display:grid; grid-template-rows:auto auto auto; row-gap:12px; padding-top:0; padding-bottom:0` |
| 顶部文字区 | `.full-top-zone` | `display:flex; flex-direction:column; align-items:flex-start; padding-top:8px` |
| 圆环区 | `.full-gauge-zone`（最终值） | `display:flex; align-items:flex-start; justify-content:center; padding-top:52px; padding-bottom:0; min-height:0` |
| 设备文字区 | `.full-device-zone` | `display:flex; flex-direction:column; align-items:flex-start; padding-top:2px` |
| 底部区 | `.full-bottom-zone`（最终值） | `display:flex; flex-direction:column; gap:12px; justify-content:flex-start` |
| 圆环尺寸 | `.full-gauge`（最终值） | `208px × 208px`（覆盖链：第 222 行 `176px` → 第 396 行 `194px` → 第 397 行 `208px`，按源码行序最终生效 `208px`） |
| 圆环外边距 | `.full-gauge`（最终值） | `margin:0` |
| 小卡栅格 | `.full-stats` | `display:grid; grid-template-columns:1fr 1fr; gap:10px; margin-top:12px` |
| 账号行图标 | `.full-account-icon` | `align-self:flex-end; transform:translateY(3px)` |
| 状态栏（预览用） | `.statusbar` | 高 `44px`，`padding:0 22px`，`font-size:13px`，`font-weight:600` |

### 1.7 圆环几何常量（照抄即用）

| 参数 | 值 |
|---|---|
| SVG viewBox | `0 0 200 200` |
| 圆心 | `cx=100, cy=100` |
| 半径 | `r=76` |
| 周长（用于 dasharray/dashoffset） | `2πr = 477.5`（HTML 硬编码 `477.5`） |
| 轨道 stroke | `stroke:var(--line)`，`stroke-width:12`（后置覆盖，原 `10`），`fill:none`，`stroke-linecap:round` |
| 进度弧 stroke-width | `15px`（后置覆盖，原 `10px`） |
| SVG 变换 | `.full-gauge svg{transform:rotate(-90deg)}`（12 点方向起弧；起笔点在**顶部**，顺时针） |
| 发光 | `filter:drop-shadow(0 0 10px <glow>)` |
| 起始角度（idle/settled dashoffset=0 时） | 顶部（12 点） |
| 圆环底部水滴轨道元素 | `.full-orbit`（`position:absolute; inset:0; opacity:0`）；`.full-droplet`（`10px × 10px`，`top:-3px; left:50%; margin-left:-5px`，`border-radius:50% 50% 50% 0`，`background:var(--blue)`，`transform:rotate(45deg)`，`box-shadow:0 0 12px var(--primary-shadow)`） |

**dashoffset → 可见弧长换算（供 Android 端按百分比绘制时对齐）**

| dashoffset | 已绘弧长 = 477.5 − offset | 占整圆比例 | 对应角度 |
|---|---|---|---|
| `0` | `477.5` | `100%` | `360°` |
| `95.5` | `382.0` | `80.0%` | `288°` |

---

## 2. 主界面（bath-full）纵向结构

HTML 根节点：`<div class="screen bath bath-full" id="bathScreen" data-state="idle">`，**初始状态 = `idle`**（脚本第 1020 行 `setBathState("idle")` 在页面加载时强制执行）。
整体纵向顺序（自上而下，`display:flex; flex-direction:column`）：

1. 状态栏 `.statusbar`（预览用）
2. 内容区 `.screen-body` → 其下依次为 `.appbar.full-appbar`，然后 `.full-hero`（内部为 3 行 grid：顶部文字区 / 圆环区 / 设备区 + 底部区）

> **HTML 结构事实（照抄依据）**：`.full-hero` 的直接子元素只有 4 个且顺序固定：`1) .full-gauge-zone`、`2) .full-device-zone`、`3) .full-bottom-zone`。`.full-top-zone`、`.full-gauge-zone`、`.full-device-zone`、`.full-bottom-zone` 这几个 class 中，`.full-top-zone` 与 `.full-gauge-zone` 只在 CSS 中定义、HTML 中**只有 `.full-gauge-zone` 实际存在**，`.full-top-zone` 在 HTML 中**不存在**（CSS 有空规则）。因此实际纵向顺序以下表为准。

| # | 区块 | 选择器 | 作用 | 文本（初始 idle） | 字号/字重/颜色 | 间距 | 背景 / 圆角 | 对应真实功能 |
|---|---|---|---|---|---|---|---|---|
| 0 | 状态栏 | `.statusbar` | iOS 风格状态栏占位（真实 App 用系统状态栏） | 左 `9:41`；右 `● ● ●` | `13px` / `600` / `var(--text)`；`.dots` 为 `10px`、`letter-spacing:2px` | 高 `44px`，`padding:0 22px`，左右两端对齐 | 无背景 | 系统状态栏 |
| 1 | 眉标 | `.full-eyebrow` | 页面层级视觉锚点 | `WATER CONTROL` | `11px` / `700` / `var(--secondary)`；`letter-spacing:.18em`（**全大写 + 大字距**） | 属于 `.appbar` 内左侧 `.full-title-block` 第 1 行 | 无 | 纯装饰眉标 |
| 2 | 主标题 | `.full-appbar h3` | 页面名 | `洗澡仪表盘` | `25px` / `700` / `var(--text)`；`letter-spacing:-.03em` | 紧接眉标下一行 | 无 | 页面标题 |
| 3 | 右上角设置齿轮 | `.full-account-icon` | 打开设置页 | 齿轮 SVG（`viewBox="0 0 24 24"`、`width/height=20`、`fill=currentColor`） | 图标 `20×20`；按钮色 `var(--text)` | 与标题块同一 `.appbar` flex 行，两端对齐 | `background:var(--card)`；圆 `50%`；`36px × 36px`；阴影 `0 6px 18px rgba(0,0,0,.06)`；`transform:translateY(3px)`、`align-self:flex-end` | 点击 → `openAccountMenu()` 打开设置 bottom sheet |
| 4 | 右上角刷新按钮（**HTML 中存在于 `.full-account-icon` 之外的独立元素，见第 8 章**） | `.full-refresh` | 刷新数据 | 无文本（字号 `18px` 的 ↻ 字形位） | 图标字号 `18px`；颜色 `var(--secondary)` | 后置覆盖为 `32px × 32px` | `background:transparent`；圆 `50%`；`box-shadow:none` | 刷新按钮；旋转动效见第 4 章（`accountSpin`） |
| 5 | 状态 chip | `.full-chip` | 连接/运行状态 | `<span class="dot"></span>已连接` | chip 文字 `12px`/`700`；初始色 `var(--green)`；dot `7px × 7px` 圆，`background:var(--green)` | `height:32px`，`padding:0 12px`，`gap:6px`；位于 `.full-gauge-zone` 之前的顶部区 | `background:var(--chip-bg)`；`border:1px solid var(--chip-border)`；`border-radius:999px`；`white-space:nowrap` | 连接状态 chip（含左侧圆点） |
| 6 | 圆环仪表盘 | `.full-gauge` | 主视觉 + 数据展示 + 点击刷新 | 中心大字 `¥5.26`；环形下方小字 `余额可用`；meta 行初始为空 | 大字 `36px`/`700`/`-.05em`/`var(--text)`；小字 `12px`/`700`/`var(--green)`；meta `11px`/400/`var(--secondary)` | `194px × 194px`，`margin:0`；容器 `padding-top:52px`（`.full-gauge-zone`）；内部 `svg{transform:rotate(-90deg)}` | `border-radius:50%`；`background:radial-gradient(circle at 50% 35%, rgba(128,128,128,.10), transparent 64%)`；`box-shadow:inset 0 0 22px rgba(128,128,128,.08)`；`cursor:pointer` | **余额圆环**；`onclick="refreshGauge()"`，`title="点击刷新"` |
| 6a | └ 轨道 | `.full-track` | 底环 | — | `stroke:var(--line)` | `fill:none`，`stroke-width:12` | — | — |
| 6b | └ 进度弧 | `.full-value` | 数据弧 | — | `stroke`/`dasharray`/`dashoffset` 按状态变（第 3 章） | `stroke-width:15`，`fill:none`，`stroke-linecap:round` | 发光 `drop-shadow(0 0 10px …)` | — |
| 6c | └ 中心大字 | `.full-big#gaugeMain` | 主数值 | `¥5.26` | 见第 3 章状态表 | 居中（`.full-gauge-center{position:absolute;inset:0;flex column;center}`） | — | 余额 / 计时 / 状态词 |
| 6d | └ 环形下方小字 | `.full-gauge-label#gaugeSub` | 大字语义 | `余额可用` | `12px`/`700`/`var(--green)`，`margin-top:4px` | — | — | 语义标签 |
| 6e | └ meta 小字 | `.full-gauge-meta#gaugeMeta` | 补充数值 | 空（`:empty{display:none}`） | `11px`/400/`var(--secondary)`，`margin-top:4px` | — | — | 洗浴中显示 `余额 ¥4.06` |
| 6f | └ 轨道水滴 | `.full-orbit` / `.full-droplet` | 洗浴中旋转水滴 | 无 | 水滴 `10px × 10px`，色 `var(--blue)` | `top:-3px; left:50%; margin-left:-5px` | `border-radius:50% 50% 50% 0`、`rotate(45deg)`、发光 `0 0 12px var(--primary-shadow)` | 仅 `bathing` 显示（`opacity:1`）并旋转 3.6 s/圈 |
| 7 | 设备名 | `.full-device#deviceName` | 当前设备 | `宿舍 101` | `19px` / `700` / `-.02em` / `var(--text)` | `margin-top:18px`（在 `.full-device-zone` 内） | 无 | 当前设备显示 |
| 8 | 设备副标题 | `.full-sub#deviceSub` | 设备型号 + 设备状态 | `KLCXKJ-Water · 设备空闲` | `12px` / 400 / `var(--secondary)` | `margin-top:3px` | 无 | 设备状态描述 |
| 9 | 连接状态行 | `.full-connection` | 蓝牙连接状态 | `<span class="full-connection-dot"></span>已连接` | `12px` / 400 / `var(--secondary)`；dot `7px × 7px` 圆 | `display:flex; align-items:center; gap:6px; margin-top:6px` | dot 背景按状态（第 3 章） | 连接状态行（含圆点） |
| 10 | **设备详情行（整行可点击按钮）** | `button.full-device-btn` | 打开设备弹窗 | 左：小标签 `蓝牙设备` + 值 `已连接 · -48 dBm`；右：`›` | 标签 `10px`/`600`/`var(--secondary)`；值 `15px`/`700`/`var(--text)`；`›` `22px`/`var(--secondary)`，`line-height:1` | `width:100%`；`min-height:64px`；`padding:0 14px`；`margin-top:0`；`display:flex; align-items:center; justify-content:space-between`；内部 `.full-device-main{flex column; gap:4px; min-width:0}` | `background:var(--card)`；`border:0`；`border-radius:16px`；`box-shadow:0 6px 18px rgba(0,0,0,.04)`；`text-align:left`；`cursor:pointer` | **整行是一个按钮**，`onclick="openDevicePicker()"` → 打开设备弹窗（bottom sheet）；`.full-device-v` 单行省略号截断（`white-space:nowrap; overflow:hidden; text-overflow:ellipsis`） |
| 11 | 两张小卡（协议 / 水阀） | `.full-stats` > `.full-stat` × 2 | 关键参数 | 卡 1：`协议` / `20`；卡 2：`水阀` / `空闲` | 标签 `10px`/400/`var(--secondary)`；值 `15px`/`700`/`var(--text)` | 栅格 `1fr 1fr`，`gap:10px`，`margin-top:12px`；每卡 `min-height:64px`，`padding:0 14px`，`display:flex; flex-direction:column; justify-content:center` | `background:var(--card)`；`border-radius:16px`；`box-shadow:0 6px 18px rgba(0,0,0,.04)` | **协议/水阀小卡**（值随状态变化，见第 3 章） |
| 12 | 底部主按钮 | `button.full-btn#mainAction` | 主操作 | `开始洗澡` | `16px` / `700` / `#ffffff`（文字恒为白色） | `width:100%`；`height:50px`；`margin-top:0`（此前多轮 `margin-top:auto` 被覆盖） | `background:var(--blue)`；`border:0`；`border-radius:999px`；`box-shadow:0 14px 30px var(--primary-shadow)`；`:active{transform:scale(.98)}`；`[disabled]{opacity:.45; pointer-events:none}` | **底部主按钮**：idle→`starting`（1.8 s 后自动 `bathing`）；bathing→`stopping`（1.8 s 后自动 `settled`）；settled→`idle`。详见第 3/4 章 |
| 13 | 账号行 | `.full-account` | 文末账号信息 | HTML 中主方案区块**未渲染**该元素，仅在 CSS 中有定义（详见第 8 章） | `12px`/400/`var(--secondary)`（后置覆盖同时给出 `display:block; width:100%; border:0; background:transparent; padding:0; text-align:left; cursor:default`） | `margin-top:6px` | `background:transparent`，无圆角 | **账号行**（无点击行为，`cursor:default`）；`.full-account-chev` 为 `16px`/`var(--secondary)` 的箭头，若行可点击时使用 |

**底部按钮不需要避让底部栏**：`.full-hero` 是 grid 第 3 行（`1fr` 已被最终 `grid-template-rows:auto auto auto` 覆盖，无自适应撑高），按钮就是内容区最后一个元素，屏幕内容区 `padding-bottom:18px`。

---

## 3. 六种状态的完整视觉映射表

HTML 通过 `.bath-full[data-state="xxx"]` 切换。下表逐条抄录 CSS 选择器内的数值。

### 3.1 圆环进度弧 `.full-value`

基础声明（第 264、306 行）：`transition:stroke .25s, stroke-dasharray .25s, stroke-dashoffset .25s, opacity .25s`；`stroke-width:15px`。

| 状态 | stroke 颜色 | stroke-width | stroke-dasharray | stroke-dashoffset | 动画 | 周期/曲线 | 发光 filter |
|---|---|---|---|---|---|---|---|
| `idle` | `var(--green)`（浅 `#34c759` / 深 `#30d158`） | `15px` | `477.5` | `95.5` | `gaugeBreathe` | `2.8s ease-in-out infinite` | 基础 `drop-shadow(0 0 10px var(--glow))` |
| `starting` | `var(--blue)`（浅 `#0071e3` / 深 `#0a84ff`） | `15px` | `120 357.5` | `0` | `gaugeFlow` | `1.1s linear infinite` | `drop-shadow(0 0 10px rgba(10,132,255,.35))` |
| `bathing` | `var(--blue)` | `15px` | `80 397.5` | `0` | `gaugeFlow` | `1.6s linear infinite` | `drop-shadow(0 0 10px rgba(10,132,255,.35))` |
| `stopping` | `#ff9f0a`（硬编码琥珀） | `15px` | `120 357.5` | `0` | `gaugeFlowReverse` | `1.1s linear infinite` | `drop-shadow(0 0 10px rgba(255,159,10,.35))` |
| `settled` | `var(--green)` | `15px` | `477.5` | `0` | `gaugeBreathe` | `2.4s ease-in-out infinite` | 基础 `drop-shadow(0 0 10px var(--glow))` |
| `error` | `var(--red)`（浅 `#ff3b30` / 深 `#ff453a`） | `15px` | `477.5` | `95.5` | `gaugeAlert` | `1s ease-in-out infinite` | `drop-shadow(0 0 10px rgba(255,69,58,.35))` |

关键帧原始定义（逐字抄录）：

```css
@keyframes gaugeBreathe{0%,100%{opacity:.72}50%{opacity:1}}
@keyframes gaugeFlow{to{stroke-dashoffset:-477.5}}
@keyframes gaugeFlowReverse{to{stroke-dashoffset:477.5}}
@keyframes gaugeOrbit{to{transform:rotate(360deg)}}
@keyframes gaugeAlert{0%,100%{opacity:.55}50%{opacity:1}}
```

> `gaugeFlow` 从 `0` 动画到 `-477.5`：可见的 120/80 长度弧段沿顺时针方向连续前进一整圈后循环，视觉为「流动的光段」。`gaugeFlowReverse` 是反向流动。

外圈 halo 环（`.full-gauge::after`，`inset:-6px`、`border:2px solid transparent`）在静止态即按状态着色，供 `.is-refreshing` 时扩散使用：

| 状态 | `.full-gauge::after` 边框色 |
|---|---|
| `idle` / `settled` | `var(--green)` |
| `starting` / `bathing` | `var(--blue)` |
| `stopping` | `#ff9f0a` |
| `error` | `var(--red)` |

### 3.2 chip、中心大字、环形下方小字、连接圆点、底部按钮

| 状态 | chip 文字 | chip 颜色（文字 / 边框 / 背景） | chip 圆点 | 中心大字内容 | 中心大字字号 | 环形下方小字 | meta 小字 | 连接行圆点颜色 | 底部按钮文字 | 按钮颜色 | 是否禁用 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `idle` | `已连接` | 文字 `var(--green)`；边框 `var(--chip-border)`；背景 `var(--chip-bg)`（**默认 chip 样式，无覆盖**） | `var(--green)`，`7px` | `¥5.26` | `36px` | `余额可用` | 空（隐藏） | `var(--green)` | `开始洗澡` | `var(--blue)`；阴影 `0 14px 30px var(--primary-shadow)` | **否**（`disabled:false`） |
| `starting` | `启动中` | 文字 `var(--blue)`；边框 `var(--blue)`；背景 `transparent` | `var(--blue)` | `启动中` | `28px` | `正在确认设备` | 空（隐藏） | `var(--blue)` | `正在启动…` | `var(--blue)`（未覆盖）；阴影 `0 14px 30px var(--primary-shadow)` | **是**（`disabled:true` → `opacity:.45`，`pointer-events:none`） |
| `bathing` | `供水中` | 文字 `var(--blue)`；边框 `var(--blue)`；背景 `transparent` | `var(--blue)` | `00:12:34` | `30px` | `已用 ¥1.20` | `余额 ¥4.06` | `var(--blue)` | `停止并结算` | `var(--red)`；阴影 `0 14px 30px rgba(255,59,48,.25)` | **否**（`disabled:false`） |
| `stopping` | `关阀中` | 文字 `#ff9f0a`；边框 `#ff9f0a`；背景 `transparent` | `#ff9f0a` | `关阀中` | `28px` | `请稍候` | 空（隐藏） | `#ff9f0a` | `正在关阀…` | `var(--red)` + `opacity:.55` | **是**（`disabled:true`） |
| `settled` | `已完成` | 文字 `var(--green)`；边框 `var(--chip-border)`；背景 `var(--chip-bg)`（默认样式） | `var(--green)` | `¥0.80` | `36px` | `本次消费` | 空（隐藏） | `var(--green)` | `完成` | `var(--blue)`；阴影 `0 14px 30px var(--primary-shadow)` | **否**（`disabled:false`） |
| `error` | `异常` | 文字 `var(--red)`；边框 `var(--red)`；背景 `transparent` | `var(--red)` | `异常` | `28px` | `正在退回预扣` | `请稍候` | `var(--red)` | `处理中…` | `var(--red)` + `opacity:.55` | **是**（`disabled:true`） |

chip 基础几何（所有状态一致）：`display:inline-flex; align-items:center; gap:6px; height:32px; padding:0 12px; border-radius:999px; font-size:12px; font-weight:700; white-space:nowrap`。

- chip 色覆盖的 CSS 原文（bathing 与 starting 共用一条规则）：
  `.bath-full[data-state="starting"] .full-chip, .bath-full[data-state="bathing"] .full-chip{color:var(--blue);border-color:var(--blue);background:transparent}`
- `stopping`：`{color:#ff9f0a;border-color:#ff9f0a;background:transparent}`；圆点 `background:#ff9f0a`
- `error`：`{color:var(--red);border-color:var(--red);background:transparent}`；圆点 `background:var(--red)`
- 连接圆点覆盖：`starting`/`bathing` → `var(--blue)`；`stopping` → `#ff9f0a`；`error` → `var(--red)`；`idle`/`settled` 保持 `.full-connection-dot{background:var(--green)}`。

### 3.3 状态伴随的其他文案字段（`bathStates` 数据，逐字抄录）

| 状态 | `deviceSub`（`.full-sub`） | `deviceBtnValue`（`.full-device-v`） | 小卡 1 标签 / 值 | 小卡 2 标签 / 值 | `action` |
|---|---|---|---|---|---|
| `idle` | `KLCXKJ-Water · 设备空闲` | `已连接 · -48 dBm` | `协议` / `20` | `水阀` / `空闲` | `开始洗澡` |
| `starting` | `正在发送开阀指令…` | `连接中 · 请稍候` | `协议` / `20` | `水阀` / `--` | `正在启动…` |
| `bathing` | `水阀已开，停止后自动结算` | `已连接 · -48 dBm` | `水流` / `正常` | `剩余` / `¥4.06` | `停止并结算` |
| `stopping` | `正在发送关阀指令…` | `已连接 · -48 dBm` | `水流` / `--` | `结算` / `准备中` | `正在关阀…` |
| `settled` | `设备已停止，可以再次开始` | `已连接 · -48 dBm` | `时长` / `12:34` | `余额` / `¥4.46` | `完成` |
| `error` | `未确认开阀，正在回滚` | `已连接 · -48 dBm` | `订单` / `处理中` | `余额` / `--` | `处理中…` |

`deviceName` 在六种状态下**恒为** `宿舍 101`。`statusText`（连接状态行文字）= 该状态 chip 文字（脚本第 969 行 `statusText.textContent = s.chip`）。

### 3.4 未连接（disconnected）的表现

**HTML 内部不一致（重要，务必先读）**：预览稿中**不存在 `data-state="disconnected"`**：既没有 `.bath-full[data-state="disconnected"]` 的 CSS 规则，`bathStates` 对象里也没有 `disconnected` 键（键只有 `idle / starting / bathing / stopping / settled / error`），状态切换按钮也只有 6 个。脚本第 967 行 `var s = bathStates[key] || bathStates.idle;` 意味着**任何未知 key 会回落到 idle 的文案**，但 `screen.dataset.state` 仍会被设成该 key（因此会匹配不到任何状态 CSS，圆环退回基础声明）。

按任务书要求，Android 端「未连接」应实现为（**这是从 HTML 现有 token 推导的规格，不是 HTML 原文**）：

| 元素 | 规格 |
|---|---|
| 圆环轨道 `.full-track` | 保持 `stroke:var(--line)`、`stroke-width:12` |
| 圆环进度弧 `.full-value` | `stroke:#8e8e93`（iOS 系统灰，**HTML 未定义，需新增 `@color/apple_gray`**）；`stroke-width:15`；`stroke-dasharray:477.5`；`stroke-dashoffset:477.5`（即完全不可见/无进度）；`animation:none`；`filter:none`（或 `drop-shadow(0 0 10px rgba(142,142,147,.35))`） |
| chip 文字 | `未连接`；颜色 `var(--secondary)`；`border-color:var(--line)`；`background:transparent`；圆点 `var(--secondary)` |
| 中心大字 | `--`（`36px`/`700`/`var(--text)`） |
| 环形下方小字 | `余额不可用`（`12px`/`700`；颜色建议 `var(--secondary)` 而非 `var(--green)`） |
| 连接行 | 圆点 `var(--secondary)`，文字 `未连接` |
| 底部主按钮 | 文字 `开始洗澡`；`background:var(--blue)`；**禁用**（`disabled:true` → `opacity:.45`） |
| 设备详情行 | 值文案建议 `未连接`，并同样置为该状态的可用性描述；`蓝牙设备` 标签不变 |

**HTML 未定义 `#8e8e93`**：全文没有出现过该值；上表灰色是 UI 常规做法，需 Android 侧自行新增资源。若要求「严格照抄 HTML」，则该状态无法照抄，必须由设计补充。

---

## 4. 圆环刷新交互 与 刷新按钮旋转

### 4.1 点击圆环（`.full-gauge`，`onclick="refreshGauge()"`）

| 阶段 | 触发条件 | 动效 | 精确数值 |
|---|---|---|---|
| 按下 | `:active` | 缩放 | `.full-gauge{transition:transform .12s, filter .2s}`；`.full-gauge:active{transform:scale(.96)}` |
| 刷新中（按状态着色） | 添加 class `is-refreshing` | 整体下压回弹 | `.full-gauge.is-refreshing{animation:gaugePress .5s ease; filter:brightness(1.06)}`；`@keyframes gaugePress{0%{transform:scale(.96)}50%{transform:scale(1.02)}100%{transform:scale(1)}}` |
| 刷新中 | 同上 | halo 扩散 | `.full-gauge::after{content:""; position:absolute; inset:-6px; border-radius:50%; border:2px solid transparent; opacity:0; pointer-events:none; transform:scale(.96)}`；`.full-gauge.is-refreshing::after{animation:gaugeHalo .8s ease-out}`；`@keyframes gaugeHalo{0%{transform:scale(.9);opacity:.9}100%{transform:scale(1.16);opacity:0}}`；halo 边框色按状态（见 3.1 表末） |
| 刷新中 | 同上 | 中心数字弹跳 | `.full-gauge.is-refreshing .full-big{animation:numberPop .5s ease}`；`@keyframes numberPop{50%{transform:scale(1.06)}}` |
| 刷新中 · 圆环发光加速 | `starting` / `bathing` | halo 周期缩短 | `.bath-full[data-state="starting"] .full-gauge.is-refreshing::after, .bath-full[data-state="bathing"] .full-gauge.is-refreshing::after{animation-duration:.55s}` |
| 刷新中 · 圆环发光加速 | `stopping` | halo 周期缩短 | `animation-duration:.65s` |
| 刷新中 · 异常抖动 | `error` | 抖动（**覆盖 gaugePress**） | `.bath-full[data-state="error"] .full-gauge.is-refreshing{animation:gaugeShake .45s ease}`；`@keyframes gaugeShake{25%{transform:translateX(-2px) scale(.97)}75%{transform:translateX(2px) scale(.97)}}` |
| 触觉反馈 | 点击时 | 震动 | JS：`if (navigator.vibrate) navigator.vibrate(8);` → Android `HapticFeedbackConstants` / `Vibrator` 8 ms |
| 结束 | 900 ms 后 | 移除 `is-refreshing` | JS：`setTimeout(function(){ gauge.classList.remove('is-refreshing'); }, 900)`；重入保护：`if (gauge.classList.contains('is-refreshing')) return;` |

### 4.2 刷新按钮旋转 `accountSpin`

| 项 | 值 |
|---|---|
| 关键帧 | `@keyframes accountSpin{to{transform:rotate(360deg)}}` |
| 绑定类 | `.account-refresh.is-spinning{animation:accountSpin .85s linear infinite}` |
| 周期 | `.85s`，`linear`，`infinite` |
| 触发条件 | 点击设置页刷新按钮（`.account-refresh`，`onclick="refreshAccount()"`）；JS 逻辑：若已含 `is-spinning` 则直接返回（重入保护），否则加类，`setTimeout(..., 900)` 后移除 |
| 时长 | 动画本身无限循环，实际运行 900 ms 后被 JS 摘除 |
| 同类动效 2 | `.account-action-icon.is-spinning{animation:accountSpin .85s linear infinite}`（设置页操作行右侧图标，若使用） |
| 顶部刷新按钮 `.full-refresh` 的按下反馈 | `.full-status-refresh{margin-left:2px;color:var(--secondary);font-size:14px;line-height:1;opacity:.8;transition:transform .2s}`；`.full-status-action:active .full-status-refresh{transform:rotate(90deg)}`（**这组选择器在主方案 HTML 中无对应元素，见第 8 章**） |
| 头像编辑反馈 | `.account-avatar.is-editing{transform:scale(1.06); box-shadow:0 0 0 4px var(--chip-bg); transition:transform .15s, box-shadow .2s}`；`.account-avatar:active{transform:scale(.97)}`；JS：加类后 600 ms 移除 |

### 4.3 底部主按钮点击后的自动状态机（脚本第 1005–1019 行）

| 当前状态 | 点击行为 | 延时后自动进入 |
|---|---|---|
| `idle` | `setBathState("starting")` | `1800 ms` → `bathing` |
| `bathing` | `setBathState("stopping")` | `1800 ms` → `settled` |
| `settled` | `setBathState("idle")` | 无 |
| `starting` / `stopping` / `error` | 按钮 `disabled`，点击无效 | — |

---

## 5. 设备弹窗（bottom sheet）规格

根节点：`<dialog id="devicePicker" class="picker-dialog sheet-dialog">`，由 `.full-device-btn` 的点击调用 `openDevicePicker()` → `d.showModal()` 打开；关闭：`closeDevicePicker()` → `d.close()`。

### 5.1 容器与抓取条

| 项 | 选择器 | 精确值 |
|---|---|---|
| 弹窗定位 | `.sheet-dialog` | `position:fixed; left:50%; bottom:0; top:auto; transform:translateX(-50%)` |
| 宽度 | `.sheet-dialog` | `width:min(100%,420px)` |
| 最大高度 | `.sheet-dialog` | `max-height:88vh`，`overflow:auto`，`margin:0` |
| 圆角 | `.sheet-dialog` | `border-radius:28px 28px 0 0`（**仅顶部两角圆角**，底部贴屏） |
| 内边距 | `.sheet-dialog` | `padding:14px 20px 24px` |
| 背景 | `.picker-dialog` | `background:var(--card)`（浅 `#ffffff` / 深 `#1c1c1e`） |
| 边框 | `.picker-dialog` | `border:0` |
| 阴影 | `.picker-dialog` | `box-shadow:0 30px 80px rgba(0,0,0,.28)` |
| 遮罩 | `.picker-dialog::backdrop` | `background:rgba(0,0,0,.35)`；`backdrop-filter:blur(6px)`（Android：scrim 色 `#59000000`，blur 可选） |
| 抓取条 | `.sheet-grabber` | `width:38px; height:5px; border-radius:999px; background:var(--line); margin:0 auto 12px` |

### 5.2 标题行

| 项 | 选择器 | 精确值 |
|---|---|---|
| 行布局 | `.picker-head` | `display:flex; align-items:flex-start; justify-content:space-between; gap:12px` |
| 眉标文字 | `.picker-eyebrow` | `DEVICE CONTROL`；`11px`/`700`/`.16em`/`var(--secondary)` |
| 标题文字 | `.picker-title` | `选择洗澡设备`；`24px`/`700`/`-.02em`；`margin-top:4px`；色 `var(--text)` |
| 关闭按钮 | `.picker-close` | 文字 `×`；`34px × 34px`；`border-radius:50%`；`border:0`；`background:var(--gray)`（浅 `#f5f5f7` / 深 `#000000`）；`color:var(--secondary)`；`font-size:16px`；`cursor:pointer` |

### 5.3 锁定提示条

| 项 | 选择器 | 精确值 |
|---|---|---|
| 容器 | `.picker-lock` | 文字 `当前正在用水或结算，暂不能更换或断开设备`；`background:rgba(255,159,10,.12)`；`color:#ff9f0a`；`border-radius:14px`；`padding:10px 12px`；`font-size:12px`；`font-weight:600`；`margin-top:14px` |
| 默认可见性 | `.picker-lock` | `display:none`（默认隐藏，仅锁定状态下由 JS 置为 `display:block`） |
| 展示条件（JS） | 第 992 行 | `lockHint.style.display = locked ? "block" : "none"` |

### 5.4 当前设备块

| 项 | 选择器 | 精确值 |
|---|---|---|
| 分组容器 | `.picker-section` | `margin-top:18px` |
| 分组标题 | `.picker-section-title` | 文字 `当前设备`；`12px`/`700`/`.04em`/`var(--secondary)`；`margin-bottom:8px` |
| 块容器 | `.picker-current` | `display:flex; align-items:center; gap:12px`；`background:var(--gray)`；`border-radius:18px`；`padding:12px` |
| 左侧图标 | `.picker-icon` | 文字 `水`；`42px × 42px`；`border-radius:13px`；`background:var(--card)`；`color:var(--blue)`；`font-weight:700`；`flex:0 0 auto` |
| 信息列 | `.picker-info` | `display:flex; flex-direction:column; gap:3px; min-width:0; flex:1` |
| 设备名 | `.picker-name` | 文字 `宿舍 101 · KLCXKJ-Water`；`15px`/`700`；单行省略号 |
| 元信息 1 | `.picker-meta` | 文字 `MAC AA:BB:CC:DD:EE:FF`；`12px`/400/`var(--secondary)` |
| 元信息 2 | `.picker-meta` | 文字 `编号 25468 · 协议 20 · -48 dBm`；`12px`/400/`var(--secondary)` |
| 右侧徽标 | `.picker-badge.green` | 文字 `当前`；`11px`/`700`/`var(--green)`；`white-space:nowrap` |

### 5.5 操作按钮三态

| 态 | 选择器 | 精确值 | 主方案中的实例 |
|---|---|---|---|
| 主（实心蓝） | `.picker-action` | `width:100%; height:50px; border-radius:999px; font-size:15px; font-weight:700; margin-top:12px`；`background:var(--blue); color:#fff; border:0` | 设备弹窗中**未使用**（该弹窗用的是 `.picker-action-danger` 与 `.picker-action-outline`）；旧版设备列表曾用 |
| 描边（蓝字） | `.picker-action-outline` | `width:100%; height:50px; border-radius:999px; font-size:15px; font-weight:700; margin-top:12px`；`background:var(--card); color:var(--blue); border:1px solid var(--line)` | 按钮文字 `重新扫描附近设备` |
| 危险（红字） | `.picker-action-danger` | `width:100%; height:50px; border-radius:999px; font-size:15px; font-weight:700; margin-top:12px`；`background:var(--card); color:var(--red); border:1px solid var(--line)` | 按钮文字 `断开连接` |

三个类共享的几何声明原文：`.picker-action,.picker-action-outline,.picker-action-danger{width:100%;height:50px;border-radius:999px;font-size:15px;font-weight:700;margin-top:12px;cursor:pointer}`

### 5.6 附近设备列表行

| 项 | 选择器 | 精确值 |
|---|---|---|
| 列表容器 | `.picker-list` | `display:flex; flex-direction:column; gap:10px; margin-top:18px` |
| 行（按钮） | `.picker-row` | `display:flex; align-items:center; gap:12px; width:100%; border:0; border-radius:18px; background:var(--gray); padding:12px; text-align:left; cursor:pointer; color:var(--text)` |
| 行·选中态 | `.picker-row.selected` | `background:var(--chip-bg); box-shadow:inset 0 0 0 1px var(--chip-border)` |
| 行图标 | `.picker-icon` | `42px × 42px`；`border-radius:13px`；`background:var(--card)`；色 `var(--blue)`（未识别设备用文字 `?`） |
| 行名称 | `.picker-name` | `15px`/`700`；单行省略号；`gap:3px` 列内 |
| 行元信息 | `.picker-meta` | `12px`/400/`var(--secondary)` |
| 行徽标 | `.picker-badge` | `11px`/`700`/`var(--secondary)`；`.blue` → `var(--blue)`；`.green` → `var(--green)` |

主方案 HTML 中的**逐行样例（照抄）**：

| 行 | 图标 | 名称 | 元信息 | 徽标 | 徽标色 |
|---|---|---|---|---|---|
| 1 | `水` | `KLCXKJ-Water-2` | `-62 dBm · 已识别 · 可连接` | `可连接` | `.picker-badge.blue` → `var(--blue)` |
| 2 | `?` | `未命名设备` | `-88 dBm · 未知设备` | `未知` | `.picker-badge`（默认）→ `var(--secondary)` |

> 弹窗内**没有**「重新扫描中…」等文案；重新扫描按钮的文案固定为 `重新扫描附近设备`。
> 参考：预览稿其它区块（归档方案，非主方案）使用的徽标文案还有 `可连接`、`已识别`，行元信息格式 `-62 dBm · 已识别`、`-88 dBm · 未知设备`、`MAC AA:BB:CC:DD:EE:FF`。
> 任务书提到的「已连接 / 信号强」：`已连接` 出现在主界面（chip、设备详情行、`.picker-badge.green` 的 `当前` 替代文案场景）；**`信号强` 在整份 HTML 中不存在**（HTML 内出现的信号描述只有 `-48 dBm`、`-62 dBm`、`-88 dBm`，以及文本 `已识别`、`可连接`、`未知`）。

### 5.7 重新扫描按钮

| 项 | 精确值 |
|---|---|
| 选择器 | `.picker-action-outline.needs-idle` |
| 文字 | `重新扫描附近设备` |
| 几何 | `width:100%; height:50px; border-radius:999px; font-size:15px; font-weight:700; margin-top:12px` |
| 颜色 | `background:var(--card); color:var(--blue); border:1px solid var(--line)` |

### 5.8 手动 MAC 输入区（**默认展开**）

| 项 | 选择器 | 精确值 |
|---|---|---|
| 容器 | `.picker-manual` | `margin-top:14px; border-top:1px solid var(--line); padding-top:12px` |
| 标题 | `.picker-manual-title` | 文字 `手动输入 MAC`；`14px`/`700`/`var(--blue)`；`margin-bottom:10px` |
| 主体 | `.picker-manual-body` | `display:flex; flex-direction:column; gap:10px; margin-top:10px` |
| 输入框 | `.picker-input` | `height:48px`；`border:1px solid var(--line)`；`border-radius:14px`；`background:var(--gray)`；`color:var(--text)`；`padding:0 14px`；`font-size:14px`；`outline:none`；`placeholder="例如 AA:BB:CC:DD:EE:FF"` |
| 连接按钮 | `.picker-connect` | 文字 `连接此设备`；`height:48px`；`border:0`；`border-radius:14px`；`background:var(--blue)`；`color:#fff`；`font-size:14px`；`font-weight:700`；`cursor:pointer` |

> **HTML 内部不一致（重要）**：预览稿用的是 **`<div class="picker-manual" id="pickerManual">`，不是 `<details>`**，所以该区**在主方案中始终可见（默认展开）**。但 CSS 里保留了 `<details>` 版样式：`.picker-manual summary{...}`、`.picker-manual summary::-webkit-details-marker{display:none}`。这些规则在主方案中**不生效**。Android 端按「默认展开、无折叠交互」实现即可。

### 5.9 弹窗锁定规则

| 项 | 规格 |
|---|---|
| 锁定状态集合 | `starting`（启动中，**不是**任务书所列的 4 个之一，但 JS 明确包含）、`bathing`（洗浴中/用水）、`stopping`（关阀中）、`error`（异常） |
| JS 原文（第 983 行） | `var locked = (key === "starting" \|\| key === "bathing" \|\| key === "stopping" \|\| key === "error");` |
| 被禁用的元素 | 所有 `.needs-idle`：`断开连接`、两行附近设备行、`重新扫描附近设备`、手动 MAC 输入框、`连接此设备` |
| 禁用效果 | `element.disabled = true` 且加 class `is-locked`；`.is-locked{opacity:.42; pointer-events:none}` |
| 手动区整体 | `manual.classList.toggle("is-locked", locked)` → `.picker-manual.is-locked{opacity:.42; pointer-events:none}` |
| 锁定提示条 | `locked` 时 `display:block`，否则 `display:none`；文案固定 `当前正在用水或结算，暂不能更换或断开设备` |
| **不锁定**的状态 | `idle`（待机）、`settled`（完成/结算后） |

> 注意：任务书列的锁定状态是「用水、结算、关阀、异常」，而 HTML 实际的锁定集合是「启动中、洗浴中、关阀中、异常」——**`settled`（完成/结算）在 HTML 中不锁定**，`starting`（启动中）反而被锁定。以 HTML 为准。

---

## 6. 设置页（bottom sheet）规格

根节点：`<dialog id="accountMenu" class="picker-dialog sheet-dialog account-dialog">`，由右上角齿轮 `.full-account-icon` 的点击调用 `openAccountMenu()` → `d.showModal()` 打开。容器、抓取条、标题行、遮罩、关闭按钮**与设备弹窗完全一致**（共用 `.sheet-dialog` / `.picker-dialog` / `.picker-head` / `.picker-eyebrow` / `.picker-title` / `.picker-close`），差异如下。

| # | 区块 | 选择器 | 文本 | 尺寸 / 圆角 / 颜色 | 间距 | 行为 |
|---|---|---|---|---|---|---|
| 1 | 抓取条 | `.sheet-grabber` | 无 | `38px × 5px`；`border-radius:999px`；`var(--line)` | `margin:0 auto 12px` | 纯装饰（HTML 中未绑定拖拽） |
| 2 | 眉标 | `.picker-eyebrow` | `SETTINGS` | `11px`/`700`/`.16em`/`var(--secondary)` | — | 装饰 |
| 3 | 标题 | `.picker-title` | `设置` | `24px`/`700`/`-.02em`/`var(--text)` | `margin-top:4px` | — |
| 4 | 关闭按钮 | `.picker-close` | `×` | `34px × 34px`；`50%`；`background:var(--gray)`；`color:var(--secondary)`；`16px` | — | `onclick="closeAccountMenu()"` |
| 5 | 账号块容器 | `.account-hero` | — | `display:flex; align-items:center; gap:14px`；`background:var(--gray)`；`border-radius:20px`；`padding:16px` | `margin-top:16px` | — |
| 5a | 头像（**可点击修改**） | `button.account-avatar` | 人像 SVG（`viewBox="0 0 24 24"`、`24×24`、`fill=currentColor`） | `52px × 52px`；`border-radius:50%`；`background:var(--card)`；`color:var(--blue)`；`border:0; padding:0; font-family:inherit; cursor:pointer; flex:0 0 auto` | `gap:14px` | `onclick="editAvatar()"`，`aria-label="修改头像"`；按下 `.account-avatar:active{transform:scale(.97)}`；编辑态 `.account-avatar.is-editing{transform:scale(1.06); box-shadow:0 0 0 4px var(--chip-bg)}`，600 ms 后自动移除 |
| 5b | 信息列 | `.account-info` | — | `flex:1; min-width:0; display:flex; flex-direction:column; gap:8px` | — | — |
| 5c | 账号行 | `.account-row` | 左 `账号`、右 `138 0000 0000` | 行 `display:flex; align-items:baseline; justify-content:space-between; gap:12px`；标签 `12px`/`600`/`var(--secondary)`；值 `15px`/`700`/`var(--text)`，`white-space:nowrap; overflow:hidden; text-overflow:ellipsis` | 列内 `gap:8px` | — |
| 5d | 余额行 | `.account-row` | 左 `余额`、右 `¥5.26` | 同上 | 同上 | — |
| 5e | 右侧刷新按钮 | `button.account-refresh` | `↻` | `36px × 36px`（**最终值**，初版为 `32px`）；`border-radius:50%`；`border:0`；`background:var(--card)`；`color:var(--blue)`；`font-size:17px`；`flex:0 0 auto; cursor:pointer` | `gap:14px` | `onclick="refreshAccount()"`，`aria-label="刷新账户"`；旋转动效见 4.2 |
| 6 | 操作列表 | `.account-actions` | — | `display:flex; flex-direction:column; gap:10px` | `margin-top:16px` | — |
| 6a | 检查更新行 | `button.account-action` | 标题 `检查更新`；副标题 `当前版本 0.1919`；右侧 `›` | 行 `width:100%; height:52px; border:0; border-radius:16px; background:var(--gray); color:var(--text); padding:0 16px; display:flex; align-items:center; justify-content:space-between; font-size:15px; font-weight:600; cursor:pointer`；标题 `15px`/`600`；副标题 `11px`/`500`/`var(--secondary)`；`›` 为 `18px`/`var(--secondary)` | `gap:10px`；行内文字列 `gap:2px; text-align:left` | `onclick="checkUpdate(this)"`。文案时序：点击后副标题变 `检查中…` → **1200 ms** 后变 `已是最新版本` → 再 **1200 ms** 后恢复 `当前版本 0.1919`；期间置 `data-busy="1"` 防重入 |
| 6b | 诊断日志行 | `button.account-action` | `诊断日志` + 右侧 `›` | 同 6a（无副标题，因此文字在行内垂直居中） | 同 6a | `onclick="closeAccountMenu()"` |
| 6c | 重新登录行（**置底、危险色**） | `button.account-action.danger.needs-idle` | `重新登录` + 右侧 `›` | 同 6a，但 `color:var(--red)`；`.danger` 只改文字色，圆角/背景不变 | 同 6a，为列表最后一项（`flex-direction:column`，自然置底） | `onclick="closeAccountMenu()"`；带 `needs-idle`，被状态锁定（`.is-locked{opacity:.42}`） |

CSS 原文（照抄）：

```css
.account-action{display:flex;align-items:center;justify-content:space-between;width:100%;height:52px;border:0;border-radius:16px;background:var(--gray);color:var(--text);font-size:15px;font-weight:600;padding:0 16px;cursor:pointer}
.account-action .chev{color:var(--secondary);font-size:18px}
.account-action.danger{color:var(--red)}
.account-action-text{display:flex;flex-direction:column;gap:2px;text-align:left}
.account-action-title{font-size:15px;font-weight:600}
.account-action-sub{font-size:11px;color:var(--secondary);font-weight:500}
```

**HTML 内部不一致**：`.account-divider{height:1px;background:var(--line);margin:12px 0}` 在前面定义，随后被 `.account-divider{display:none}` 覆盖，且主方案 HTML 中根本没有该元素 → **Android 端不要实现账号块内的分隔线**。

**未被主方案使用的设置页遗留样式**（HTML 中无对应元素，可作为备用规格）：`.account-label-gap{margin-top:12px}`、`.account-block`（旧版卡片式，`background:var(--gray); border-radius:18px; padding:16px; margin-top:16px`）、`.account-action-right{display:flex;align-items:center;gap:8px}`、`.account-action-icon{color:var(--blue);font-size:16px;display:inline-block}`。

---

## 7. HTML 中的示例文案清单（Android `strings.xml` 用）

### 7.1 主方案主界面 + 两个弹窗（必须实现）

| 文案 | 出处 | 浅/深是否有差异 |
|---|---|---|
| `WATER CONTROL` | 主界面眉标 | 无差异（仅颜色 token 变） |
| `洗澡仪表盘` | 主界面标题 | 无差异 |
| `DEVICE CONTROL` | 设备弹窗眉标 | 无差异 |
| `SETTINGS` | 设置弹窗眉标 | 无差异 |
| `选择洗澡设备` | 设备弹窗标题 | 无差异 |
| `设置` | 设置弹窗标题 | 无差异 |
| `×` | 两个弹窗关闭按钮 | 无差异 |
| `当前正在用水或结算，暂不能更换或断开设备` | 设备弹窗锁定提示 | 无差异 |
| `当前设备` | 设备弹窗分组标题 | 无差异 |
| `附近设备` | 设备弹窗分组标题 | 无差异 |
| `宿舍 101 · KLCXKJ-Water` | 弹窗当前设备名 | 无差异 |
| `MAC AA:BB:CC:DD:EE:FF` | 弹窗当前设备元信息 1 | 无差异 |
| `编号 25468 · 协议 20 · -48 dBm` | 弹窗当前设备元信息 2 | 无差异 |
| `当前` | 弹窗当前设备徽标 | 无差异 |
| `断开连接` | 设备弹窗危险按钮 | 无差异 |
| `KLCXKJ-Water-2` | 附近设备行 1 名称 | 无差异 |
| `-62 dBm · 已识别 · 可连接` | 附近设备行 1 元信息 | 无差异 |
| `可连接` | 附近设备行 1 徽标 | 无差异 |
| `未命名设备` | 附近设备行 2 名称 | 无差异 |
| `-88 dBm · 未知设备` | 附近设备行 2 元信息 | 无差异 |
| `未知` | 附近设备行 2 徽标 | 无差异 |
| `重新扫描附近设备` | 设备弹窗描边按钮 | 无差异 |
| `手动输入 MAC` | 手动区标题 | 无差异 |
| `例如 AA:BB:CC:DD:EE:FF` | 手动 MAC 输入框 placeholder | 无差异 |
| `连接此设备` | 手动区按钮 | 无差异 |
| `宿舍 101` | 主界面设备名（六状态恒同） | 无差异 |
| `蓝牙设备` | 设备详情行小标签 | 无差异 |
| `›` | 设备详情行箭头、设置页操作行箭头 | 无差异 |
| `↻` | 顶部刷新按钮 / 设置页刷新按钮 | 无差异 |
| `账号` / `余额` | 设置页标签 | 无差异 |
| `138 0000 0000` | 设置页账号值（全局示例手机号，登录页默认值同） | 无差异 |
| `¥5.26` | 设置页余额值 / 待机中心大字 | 无差异 |
| `检查更新` | 设置页操作行 | 无差异 |
| `当前版本 0.1919` | 设置页检查更新副标题（**版本号硬编码，Android 端应改为动态版本名**） | 无差异 |
| `检查中…` | 检查更新过程态 | 无差异 |
| `已是最新版本` | 检查更新结果态 | 无差异 |
| `诊断日志` | 设置页操作行 | 无差异 |
| `重新登录` | 设置页危险行 | 无差异 |

### 7.2 六种状态的文案（`bathStates`，逐字抄录；替换维度同时含浅色/深色）

| key | chip / statusText | 中心大字 | 环形下方小字 | meta | deviceSub | deviceBtnValue | 小卡 1 | 小卡 2 | 主按钮 |
|---|---|---|---|---|---|---|---|---|---|
| `idle` | `已连接` | `¥5.26` | `余额可用` | （空） | `KLCXKJ-Water · 设备空闲` | `已连接 · -48 dBm` | `协议` / `20` | `水阀` / `空闲` | `开始洗澡` |
| `starting` | `启动中` | `启动中` | `正在确认设备` | （空） | `正在发送开阀指令…` | `连接中 · 请稍候` | `协议` / `20` | `水阀` / `--` | `正在启动…` |
| `bathing` | `供水中` | `00:12:34` | `已用 ¥1.20` | `余额 ¥4.06` | `水阀已开，停止后自动结算` | `已连接 · -48 dBm` | `水流` / `正常` | `剩余` / `¥4.06` | `停止并结算` |
| `stopping` | `关阀中` | `关阀中` | `请稍候` | （空） | `正在发送关阀指令…` | `已连接 · -48 dBm` | `水流` / `--` | `结算` / `准备中` | `正在关阀…` |
| `settled` | `已完成` | `¥0.80` | `本次消费` | （空） | `设备已停止，可以再次开始` | `已连接 · -48 dBm` | `时长` / `12:34` | `余额` / `¥4.46` | `完成` |
| `error` | `异常` | `异常` | `正在退回预扣` | `请稍候` | `未确认开阀，正在回滚` | `已连接 · -48 dBm` | `订单` / `处理中` | `余额` / `--` | `处理中…` |

小卡标签出现的全部词：`协议`、`水阀`、`水流`、`剩余`、`时长`、`余额`、`订单`、`结算`。小卡值出现的全部词：`20`、`空闲`、`正常`、`¥4.06`、`准备中`、`处理中`、`¥4.46`、`12:34`、`--`。

### 7.3 浅色 / 深色下**文案相同**、但**颜色不同**的项（Android 需做 night 资源，字符串无需两套）

| 文案 | 浅色色值 | 深色色值 |
|---|---|---|
| 状态 chip 绿色文字（`已连接` / `已完成`） | `var(--green)` = `#34c759`（边框 `rgba(52,199,89,.45)`、底 `rgba(52,199,89,.14)`） | `var(--green)` = `#30d158`（边框 `rgba(48,209,88,.45)`、底 `rgba(48,209,88,.12)`） |
| 圆环 idle/settled 弧与环形下方小字 | `#34c759` | `#30d158` |
| 圆环 starting/bathing 弧与 chip | `#0071e3` | `#0a84ff` |
| 圆环 error 弧与 chip | `#ff3b30` | `#ff453a` |
| 圆环 stopping 弧与 chip | `#ff9f0a` | `#ff9f0a`（**深色无覆盖，同值**） |
| 主按钮底色（非红色态） | `#0071e3` | `#0a84ff` |
| 主按钮阴影 | `rgba(0,113,227,.30)` | `rgba(10,132,255,.30)` |
| 弹窗关闭按钮底色 | `var(--gray)` = `#f5f5f7` | `var(--gray)` = `#000000` |
| 弹窗列表行 / 账号块 / 操作行底色 | `var(--gray)` = `#f5f5f7` | `#000000` |
| 弹窗输入框底色 | `var(--gray)` = `#f5f5f7` | `#000000` |

**唯一的字符串层面差异**：无。所有文案在浅色/深色下文本完全一致，仅颜色 token 变化；需要 `values-night` 的只是颜色资源。

### 7.4 预览稿其它区块出现的文案（**非主方案，供参考，勿混入主界面**）

`睿智校园 · Apple 风格 UI 预览`、`基于你提供的 Apple Style Tokens：黑 / 白 / #0071e3 / #f5f5f7，SF Pro 字体栈，大圆角、轻阴影、胶囊按钮。`、`切换深色`、`颜色`、`Primary #000000`、`Secondary #f5f5f7`、`Accent #0071e3`、`Success #34c759`、`Danger #ff3b30`、`Accent 2 #a13ff6`、`主方案 · 全屏 Apple 仪表盘（浅色 / 深色适配）`、`点击状态按钮查看圆盘动效：待机显示余额，洗浴中切换为计时和消费进度，关阀、结算、异常都有独立动画。`、状态按钮 `待机`/`启动中`/`洗浴中`/`关阀中`/`完成`/`异常`、`主方案 · 状态圆盘动效预览`、`9:41`、`● ● ●`、`点击刷新`、`公寓洗澡`、`空闲`、`可以开始`、`设备状态`、`附近设备 · 2 台已发现`、`已折叠，点击展开扫描列表`、`设备空闲，可以开始`、`尾号 0000 · 余额 ¥5.26`、`尾号 0000 · 账户 25468`、`更换设备 ›`、`更换洗澡设备 ›`、`KLCXKJ-Water · 已连接`、`KLCXKJ-Water · 设备空闲`、`余额`、`可用余额`、`附近设备`、`2`、`台`、`仪表盘融合版 · 中部大卡片 + 底部小卡片`、`中部大卡片承载当前设备和状态，底部用小卡片放余额仪表盘和附近设备入口，底部按钮固定。`、`大卡片 + 小卡片`、`主方案 · 中部大卡片仪表盘`、`文字系统`、`大标题 34px / Bold`、`卡片标题 24px / Bold`、`正文强调 17px / Semibold`、`辅助说明 15px / Regular，颜色 #86868b`、`按钮`、`连接设备`、`停止并结算`、`设备列表项`、`宿舍 101 · KLCXKJ-Water`、`-62 dBm · 已识别`、`AA:BB:CC:DD:EE:FF`、`可连接`、`日志（仅调试）`、`[status] 已连接，正在确认设备状态…`、`回帧 cmd=0x23 payload=...`、`归档方案 · 紧凑版 / 极简版 / 中部大卡片仪表盘`、`紧凑版 · 当前设备 + 折叠附近设备`、`极简版 · 底部按钮在用水时切换为停止并结算`、登录页全部文案（`睿智校园`、`校园热水 · 清爽无打扰`、`验证码登录`、`密码登录`、`手机号`、`请输入手机号`、`验证码`、`请输入验证码`、`获取验证码`、`密码`、`请输入密码`、`👁`、`🙈`、`登录后仅访问校园账户和公寓热水服务`、`请输入 11 位手机号`、`请输入验证码`、`请输入密码`、`登录中…`、`Ns 后重发`）。

---

## 8. HTML 内部不一致 / 含糊之处汇总（实现前必读）

1. **`.full-refresh` 在主方案 HTML 中不存在**。CSS 有 3 组 `.full-refresh` 声明（`40px`→`32px`，`background:var(--card)`→`transparent`，阴影 `0 6px 18px rgba(0,0,0,.06)`→`none`），但主界面 HTML 只有 `.full-account-icon`（设置齿轮）。任务书要求的「右上角刷新按钮」在预览稿中**没有实际渲染**。建议 Android 端实现：`32px × 32px`、圆、`background:transparent`、`color:var(--secondary)`、`font-size:18px`、无阴影；或与齿轮合并为一个 40/36 dp 圆形按钮（需设计确认）。
2. **`.full-gauge` 尺寸被覆盖 3 次**：第 222 行 `176px` → 第 396 行 `194px` → 第 397 行 `208px`。按源码行序，**最终生效值 = `208px × 208px`**（第 397 行是最后一条 `.full-gauge` 宽高声明）。容器为 `208dp`，内部 SVG `viewBox="0 0 200 200"` 按 `width:100%;height:100%` 拉伸，弧半径仍为 `76`（viewBox 单位），实际渲染半径 = `76 × 208 / 200 = 79.04 dp`，实际描边 = `12 × 208 / 200 = 12.48 dp`（轨道）/ `15 × 208 / 200 = 15.6 dp`（进度弧）。**Android 端若直接按 viewBox 尺寸建 Canvas，建议统一用 200 单位基准再缩放到 208 dp，以保证描边比例一致。**
3. **`.account-value` 字号被覆盖**：先 `18px`（第 375 行）后 `15px`（第 449 行），最终 `15px`。同理 `.account-refresh` 先 `32px` 后 `36px` → 最终 `36px`。
4. **`.state-switch` / `.state-btn` 被覆盖**：先 `flex` 后 `grid`（`repeat(6,minmax(0,1fr))`，`max-width:720px`）、`height:40px`。这是预览控件，不是产品 UI，Android 不需要。
5. **`disconnected` 状态完全缺失**：无 CSS、无数据、无切换按钮。第 3.4 节给出的是**推导规格**，其中灰色 `#8e8e93` 在 HTML 中从未出现，必须由设计确认或改用现有 `var(--secondary)`。
6. **`信号强` 文案在 HTML 中不存在**；信号只有 dBm 数值描述（`-48 / -62 / -88 dBm`）与 `已识别` / `可连接` / `未知` / `当前` 四个状态词。
7. **弹窗锁定集合与任务书描述不一致**：HTML 实际锁定 `starting / bathing / stopping / error`，**不锁定 `settled`**；任务书写的「结算时锁定」与 HTML 相反。
8. **`.picker-manual` 是 `<div>` 不是 `<details>`**：手动 MAC 区**默认且始终展开**；CSS 里针对 `summary` 的规则不生效。
9. **`.full-account` / `.full-account-chev` / `.full-account-icon`(作为账号行) / `.full-status-action` / `.full-status-refresh` / `.full-top-zone` / `.full-orbit` 之外的 `.full-title`、`.full-gauge-meta` 等**：`.full-account`、`.full-status-action`、`.full-status-refresh`、`.full-top-zone`、`.full-title` 在主方案 HTML 中**没有对应元素**（只有 CSS）。任务书要求的「账号行」在预览稿主界面上**不可见**，需按第 2 章第 13 行的 token 自行实现。
10. **`.account-divider` 最终 `display:none`**，且 HTML 无该元素 → 设置页账号块内不要分隔线。
11. **`.full-hero` 布局被覆盖多轮**：`flex column` → `display:grid; grid-template-rows:auto 1fr auto` → `auto auto auto 1fr` → `auto auto auto auto; row-gap:12px` → 最终 `grid-template-rows:auto auto auto; row-gap:12px`，配合 `.full-gauge-zone{padding-top:52px}`。这是「靠 padding 硬撑垂直留白」的做法；Android 端建议改用 `ConstraintLayout` 的 `layout_constraintVertical_bias` / `Space` 权重，但需保持：标题区在上、圆环距标题约 52 dp、设备区紧随圆环、底部区（设备行 + 两小卡 + 主按钮）靠下。
12. **`.full-btn` 的 `margin-top:auto`** 在基础声明中出现，随后被 `.full-btn{margin-top:0}` 覆盖 → 按钮不做 `auto` 撑开，间距由 `.full-bottom-zone{gap:12px}` 提供。
13. **`#ff9f0a`（琥珀）硬编码在 4 处**（圆环弧、chip 文字/边框、chip 圆点、连接圆点、halo 边框），未走 CSS 变量 → Android 端应新增 `@color/apple_amber` 并在浅深两套中取同值。
14. **停用态视觉**：`.full-btn[disabled]{opacity:.45; pointer-events:none}` 与 `.bath-full[data-state="stopping"] .full-btn, .bath-full[data-state="error"] .full-btn{background:var(--red);opacity:.55}` 同时作用时，最终不透明度由后出现的 `.55` 决定（同一特异性下后者胜出）→ stopping/error 的按钮不透明度 **0.55**；starting 的按钮仅有 `[disabled]` 的 **0.45**。
15. **版本号 `0.1919` 与示例数据硬编码**（`138 0000 0000`、`¥5.26`、`宿舍 101`、`KLCXKJ-Water`、`25468`、`MAC AA:BB:CC:DD:EE:FF`、`-48 dBm`、`00:12:34`、`¥1.20`、`¥4.06`、`¥4.46`、`¥0.80`、`12:34`）：全部为演示常量，Android 端必须走真实数据，仅作文案格式参考（金额两位小数、时长 `HH:MM:SS` / `MM:SS`）。
16. **`transition:background .25s,color .25s` 在 `body` 上**：主题切换 250 ms 过渡；Android 端对应 `android:windowAnimationStyle` 或手动 animator，可选。

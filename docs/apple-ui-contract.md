# 睿智校园 · 第 2 阶段 Apple UI 映射 —— 接口契约（唯一事实来源）

本文件由主协调者在派发并行子任务**之前**写成，用于冻结所有跨文件接口。
任何子任务都**不得**修改本文件，也**不得**偏离本文件定义的 id / 类名 / 方法签名。
如果发现契约确实有问题，在最终回报中明确指出，不要自行改接口。

最终设计基准（视觉唯一依据）：`docs/apple-design-preview.html`
提取出的详细规格：`docs/apple-ui-spec.md`（若已生成则以其补充细节，冲突时以本契约的结构/id 为准）

---

## 0. 全局约束

- 所有改动只在 `D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev`。**绝对不要**碰 `Funnyass_school-sugars-`（无 dev 后缀）原始目录。
- **不要** `git commit`、**不要** `git push`、**不要**执行任何 git 写操作。
- 纯原生化 View 体系（XML + findViewById）。**禁止**引入 Compose、禁止新增第三方依赖、禁止改 `build.gradle.kts` / `settings.gradle.kts` / `gradle.properties` / `gradle-wrapper.properties`。
- 只做"UI 映射"。**禁止**改任何业务逻辑：
  - 不改 BLE 回调（`onScanResult` / `onStateChanged` / `onFrame` / `onWriteDone`）
  - 不改 `CmdServer` 指令语义
  - 不改 `Api` 调用与参数
  - 不新增开阀接口、不新增扣费接口
  - 不改 `BleManager.kt` / `FrameUtils.kt` / `CmdServer.kt` / `net/*` / `data/*` / `store/*` / `util/*`
  - 不改 `LoginActivity.kt` / `activity_login.xml`（第 1 批已验收）
- 保持 Kotlin 编译零错误零警告级别即可，不要引入需要新依赖的 API。
- 配色/圆角一律走 `@color/apple_*` 与 `@dimen`/字面量，浅色深色自动切换依赖 `values/` 与 `values-night/` 同名资源，**不要在代码里硬编码颜色**（圆环绘制除外，圆环颜色必须按 state 从资源取）。

## 1. 颜色资源契约（由主协调者补齐，其他人只使用）

已存在：`apple_bg` `apple_surface` `apple_text` `apple_secondary` `apple_line` `apple_blue` `apple_green` `apple_red` `apple_blue_tint` `apple_green_tint` `apple_red_tint`

主协调者会新增（其他人**直接引用，不要自己定义重复名字**）：

| 资源名 | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `apple_amber` | `#FF9F0A` | `#FF9F0A` | 关阀中 圆环/chip/圆点 |
| `apple_amber_tint` | `#FFF3E0` | `#4A321D` | 关阀中 chip 底色 |
| `apple_gauge_track` | `#E5E5EA` | `#38383A` | 圆环轨道 |
| `apple_scrim` | `#66000000` | `#99000000` | 弹窗遮罩 |
| `apple_sheet_grabber` | `#D2D2D7` | `#48484A` | 弹窗顶部抓手 |
| `apple_neutral_tint` | `#F2F2F7` | `#2C2C2E` | 次级按钮/输入框底 |

## 2. 必须保留的 23 个 id（一个都不能删、不能改 id 名）

```
status
log
device_list
wallet
account_subtitle
account_value
device_title
device_detail
device_mac
device_id_value
connection_value
protocol_value
control_hint
mac_input
advanced_panel
advanced_toggle
connect_btn
scan_btn
start_btn
stop_btn
disconnect_btn
relogin_btn
refresh_btn
```

**归属划分（冻结）**

- 主界面 `activity_bath.xml` 顶层直接持有（`findViewById` 从 Activity 上能找到）：
  `account_subtitle` `account_value` `wallet` `device_title` `device_detail` `connection_value` `control_hint` `advanced_toggle` `status` `refresh_btn` `start_btn` `stop_btn`
- 设备底部弹窗 `bottom_sheet_device_picker.xml`（由 `activity_bath.xml` 用 `<include>` 引入，Activity 的 `findViewById` 必须仍能找到，所以**不要**给 include 加 `android:id` 之外会阻断查找的写法）：
  `device_list` `device_mac` `device_id_value` `protocol_value` `mac_input` `connect_btn` `scan_btn` `disconnect_btn`
- 设置底部弹窗 `bottom_sheet_settings.xml`（同样 `<include>`）：
  `advanced_panel` `log` `relogin_btn`

> 注意：`<include>` 引入的子布局内的 id 会被合并进宿主 Activity 的视图树，
> 因此 `findViewById(R.id.device_list)` 在 `BathActivity` 中依然可用。
> **前提**：`<include>` 标签本身不要设置 `android:id`（设置也不影响，但为安全起见不设）。

`advanced_panel` 的语义调整为「设置页中的诊断区块」，默认 `visibility="gone"`，可由 `advanced_toggle` 或日志入口展开。

## 3. `activity_bath.xml` 顶层结构与「一屏显示」契约

根布局：`FrameLayout`（承载主内容 + 两个弹窗 overlay + 遮罩）

```
FrameLayout (root, background=@color/apple_bg)
├── LinearLayout (vertical, 主内容, match_parent)
│   ├── LinearLayout (vertical, 顶部区, paddingStart/End=20dp, paddingTop=10dp)
│   │   ├── 头部行: [WATER CONTROL 眉标 + 洗澡仪表盘 标题]  ...  [refresh_btn]
│   │   ├── account_subtitle   (13sp, secondary, 单行省略)
│   │   ├── account_value      (必须保留；可 visibility=gone)
│   │   └── status             (chip, 圆角999, 小圆点+文字效果用背景 drawable 实现)
│   ├── BathGaugeView (id=bath_gauge, 1:1 正方形, 居中, layout_weight=1 或固定 208dp)
│   ├── LinearLayout (vertical, 中下区, paddingStart/End=20dp)
│   │   ├── device_title       (19sp bold)   ← 设备名，如「宿舍 101」
│   │   ├── device_detail      (12sp secondary) ← 如「KLCXKJ-Water · 设备空闲」
│   │   ├── connection_value   (12sp, 前置圆点) ← 已连接 / 未连接
│   │   ├── control_hint       (12sp secondary, 连接状态下方的小字提示)
│   │   ├── 蓝牙设备入口按钮 (整行可点，打开设备弹窗)
│   │   │     ├── 左: 小标题「蓝牙设备」+ 值 (文本由 device_detail/连接状态派生，用独立 TextView)
│   │   │     └── 右: 「›」
│   │   └── 两张小卡 (水平 LinearLayout, 各 weight=1)
│   │         ├── 卡1: 标签 + 值  ← 协议
│   │         └── 卡2: 标签 + 值  ← 水阀 / 水流 / 剩余 / 结算
│   └── LinearLayout (vertical, 底部固定, padding 20dp)
│         ├── start_btn  (56dp 高, 圆角28dp, 蓝底白字「开始洗澡」)
│         ├── stop_btn   (56dp 高, 圆角28dp, 红底白字「停止并结算」, 默认 gone)
│         └── advanced_toggle (居中, 蓝字 15sp bold, 文本「更换洗澡设备  ›」)
├── View (id=sheet_scrim, 全屏, background=@color/apple_scrim, visibility=gone)
├── <include layout=@layout/bottom_sheet_device_picker id=picker_sheet>
└── <include layout=@layout/bottom_sheet_settings      id=settings_sheet>
```

「一屏显示」要求：**主内容不允许出现纵向 ScrollView**（否则弹窗 overlay 与滚动冲突）；
用 `layout_weight` 分配，小屏时靠 `adjustResize` + 紧凑间距保证不裁切。
如果确实需要兜底，可在中下区用 `layout_weight="1"` + `minHeight`，但不得让底部按钮滚出屏幕。

新增 id（已落地，与 `activity_bath.xml` 一致）：

- `bath_gauge` → `com.funnyass.test.ui.BathGaugeView`
- `sheet_scrim` → 遮罩 View
- `picker_sheet` / `settings_sheet` → 两个 `<include>` 的 id
- 顶栏按钮：`refresh_btn`（↻ 账户刷新）、`settings_btn`（⚙ 设置）
- 连接状态圆点：`connection_dot`
- 蓝牙设备入口整行按钮：`device_entry_btn`，其值文本 `device_entry_value`
- 两张小卡：`stat1_label` `stat1_value` `stat2_label` `stat2_value`
- 头部眉标/标题无 id

**「一屏显示」实现方式（已落地）**：主内容不用 ScrollView；由
`BathGaugeView(layout_height=0dp, layout_weight=1, minHeight=150dp)` 吸收高度变化，
其余区块 `wrap_content`，底部按钮固定在最下方的 LinearLayout 内。

## 4. `BathGaugeView` 契约（子任务 A 产出）

文件：`app/src/main/java/com/funnyass/test/ui/BathGaugeView.kt`

```kotlin
package com.funnyass.test.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View

class BathGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class GaugeState { DISCONNECTED, IDLE, STARTING, BATHING, STOPPING, SETTLED, ERROR }

    /** 设置圆环状态并启动对应动画；会触发 invalidate()。必须在主线程调用。 */
    fun setGaugeState(state: GaugeState)

    /** 设置中心三行文本。meta 传空串或 null 时该行不绘制。必须在主线程调用。 */
    fun setGaugeText(main: String, label: String, meta: String? = null)

    /** 点击圆环时回调（用于触发余额刷新）。设置 null 取消回调。 */
    fun setOnGaugeClickListener(listener: (() -> Unit)?)

    /** 主动播放一次「刷新」按压 + halo 动效（叠加在当前状态上，不改变 state）。 */
    fun playRefreshAnimation()
}
```

行为要求（对应 `apple-design-preview.html` 的 `.bath-full[data-state=...]`）：

| state | 进度弧颜色 | 进度弧形态 | 动画 |
|---|---|---|---|
| `DISCONNECTED` | `apple_gauge_track` 灰 | 整圈轨道，无进度弧 | 无 |
| `IDLE` | `apple_green` | dasharray 整圈, dashoffset 20%（缺口在顶部） | 呼吸 2.8s ease-in-out（透明度 .72→1） |
| `STARTING` | `apple_blue` | 25% 弧段 (dasharray 120/357.5) | 顺时针流动 1.1s linear |
| `BATHING` | `apple_blue` | 16.7% 弧段 (dasharray 80/397.5) | 顺时针流动 1.6s linear + 轨道外侧水滴绕圈 3.6s/圈 |
| `STOPPING` | `apple_amber` | 25% 弧段 | 逆时针流动 1.1s linear |
| `SETTLED` | `apple_green` | 满圈 | 呼吸 2.4s ease-in-out |
| `ERROR` | `apple_red` | 整圈, dashoffset 20% | 脉冲 1.0s ease-in-out |

绘制细节：

- 圆环用 `Canvas.drawArc` 或 `Path` + `Paint.STROKE`，`strokeCap = ROUND`。
- 轨道 stroke width ≈ 12dp；进度弧 stroke width ≈ 15dp（按 HTML `.full-track{stroke-width:12}` `.full-value{stroke-width:15}`）。
- 进度弧加发光：`Paint.setShadowLayer` 或用 `BlurMaskFilter`，颜色取该 state 主色的 35% 透明。
- 圆环直径取 `min(width, height)`，居中绘制；中心留白区域绘制三行文本。
- 文本：main → 36sp bold（`BATHING` 时 30sp，`STARTING/STOPPING/ERROR` 时 28sp，对齐 HTML）；
  label → 12sp bold，颜色 = 该 state 主色；meta → 11sp，`apple_secondary`。
- 字体颜色用 `apple_text` / 该 state 主色，从 `ContextCompat.getColor` 取，**不允许**在代码里写死十六进制。
- 动画用 `ValueAnimator`（`infinite`）或 `postInvalidateOnAnimation` 的自绘相位累加；`onDetachedFromWindow` 必须取消所有动画防止泄漏。
- `playRefreshAnimation()`：0.5s 缩放 0.96→1.02→1.0 + halo 外扩 0.8s。
- 深色/浅色适配：所有颜色在每次 `onDraw` 时通过 `ContextCompat.getColor` 获取（或监听 `onConfigurationChanged` 重新取），保证主题切换后圆环颜色正确。

同时产出可选的 `res/layout/view_bath_gauge.xml`（若采用子 View 组合实现）。

## 5. 两个底部弹窗契约（子任务 B 产出）

### 5.1 `res/layout/bottom_sheet_device_picker.xml`

根：`LinearLayout`（vertical，`background=@drawable/bg_apple_sheet`，圆角 28dp 顶部）──
但注意它会被 `<include>` 进宿主，所以**根布局不要写 `layout_width/height` 之外的定位属性**，
定位（贴底、最大高度）由本文件根布局的 `layout_gravity="bottom"` 完成。

结构：

```
LinearLayout(vertical, layout_gravity=bottom, background=bg_apple_sheet_rounded_top)
├── View 抓手 (38dp x 5dp, 居中, background=@drawable/bg_apple_grabber)
├── 头部行: [DEVICE CONTROL 眉标 + 「选择洗澡设备」标题] ... [关闭 × 按钮 id=picker_close]
├── TextView id=picker_lock_hint  文本「当前正在用水或结算，暂不能更换或断开设备」默认 gone
├── 「当前设备」区块
│   ├── 区块小标题「当前设备」
│   ├── 当前设备行: 图标「水」 + 名称 + MAC + 编号/协议/信号 + 徽标「当前」
│   │     ├── TextView id=device_mac        （文本形如「MAC AA:BB:CC:DD:EE:FF」）
│   │     ├── TextView id=protocol_value    （协议值，纯文本如「20」）
│   │     └── TextView id=device_id_value   （编号值，纯文本如「25468」）
│   └── disconnect_btn  （danger 描边按钮，红字「断开连接」）
├── 「附近设备」区块
│   ├── 区块小标题「附近设备」
│   ├── ListView id=device_list （高度至少 160dp；adapter 由 Activity 提供）
│   └── scan_btn （描边按钮，蓝字「重新扫描附近设备」）
└── 手动输入区（**默认展开，不用折叠**）
    ├── 小标题「手动输入 MAC」
    ├── EditText id=mac_input  hint「例如 AA:BB:CC:DD:EE:FF」
    └── connect_btn （蓝底白字「连接此设备」）
```

要求：
- 整个弹窗内容需要可滚动（内容较长）：在头部/抓手之外套 `NestedScrollView`（但 `device_list` 自身要能滚动，
  做法：给 `device_list` 固定高度如 180dp 并 `android:nestedScrollingEnabled="true"`，外层用 `NestedScrollView`）。
- `picker_close` 为新增 id，允许。
- 内部区块的图标/徽标用 `TextView` + `bg_apple_*` drawable 实现，不要新增 png。

### 5.2 `res/layout/bottom_sheet_settings.xml`

```
LinearLayout(vertical, layout_gravity=bottom, background=bg_apple_sheet_rounded_top)
├── View 抓手
├── 头部行: [SETTINGS 眉标 + 「设置」标题] ... [关闭 × id=settings_close]
├── 账户卡 (灰底圆角20dp)
│   ├── 头像按钮 id=account_avatar  （圆形，点击可修改头像 → 弹系统图片选择）
│   ├── 账号区: 「账号」+ 手机号文本 id=settings_phone
│   │            「余额」+ 余额文本  id=settings_wallet
│   └── 刷新按钮 id=settings_refresh （↻，点击刷新账户，**刷新时不关闭弹窗**）
├── 操作列表
│   ├── 行 id=settings_check_update : 「检查更新」+ 副标题 id=settings_version「当前版本 0.1919」+ ›
│   ├── 行 id=settings_diagnostics  : 「诊断日志」+ ›
│   │     └── LinearLayout id=advanced_panel (默认 gone) 内含:
│   │           TextView id=log (monospace 11sp, 可滚动)
│   └── 行 id=relogin_btn : 「重新登录」红字 + › （置底）
```

要求：
- `advanced_toggle` 这个 id 必须保留；把它放在诊断日志行的位置（点击展开 `advanced_panel`），
  或保留在设置页底部作为「高级 / 诊断」入口。**由 Activity 决定可见性与文本**，布局里给默认文本「诊断日志  ›」。
  为同时满足两处需求：把 `advanced_toggle` 用在与 `settings_diagnostics` **同一行**（即 `settings_diagnostics` 行右侧或该行本身就是 advanced_toggle）。
  **最终以本契约为准**：`advanced_toggle` 就是「诊断日志」这一行的可点击 TextView（id 为 `advanced_toggle`），
  不再额外造 `settings_diagnostics`。

### 5.3 新增 drawable（子任务 B 产出，命名固定）

- `bg_apple_sheet_rounded_top.xml`：白色/深色 surface 实色 + 顶部圆角 28dp
- `bg_apple_grabber.xml`：圆角 3dp 的 `@color/apple_sheet_grabber` 矩形
- `bg_apple_card.xml`：`@color/apple_surface` + 圆角 22dp（若已有同名则复用）
- `bg_apple_chip_green.xml` / `bg_apple_chip_blue.xml` / `bg_apple_chip_amber.xml` / `bg_apple_chip_red.xml`：
  圆角 999dp + 对应 tint 底色（用于状态 chip 与设备行徽标）
- `bg_apple_danger_outline.xml`：圆角 999dp，白/深底 + 1dp `@color/apple_line` 边框（危险按钮用）
- `bg_apple_secondary_btn.xml`：圆角 999dp + `@color/apple_neutral_tint` 底色

允许按需增补，但**不要**修改已有的 `bg_apple_field.xml` / `bg_apple_pill_blue.xml` / `bg_apple_pill_green.xml` 的**语义**（可微调颜色引用）。

## 6. `BathActivity.kt` 状态映射契约（主协调者负责）

状态优先级（从上到下取第一个成立的），只读现有字段，不新增业务状态：

| 条件 | GaugeState | 中心 main | 中心 label | 中心 meta | chip 文本 | 底部主按钮 |
|---|---|---|---|---|---|---|
| `connected == false` | `DISCONNECTED` | `--` | `未连接` | `""` | `未连接` | `开始洗澡` 禁用 |
| `rollbackInFlight` | `ERROR` | `异常` | `正在退回预扣` | `请稍候` | `异常` | `处理中…` 禁用 |
| `startRequestInFlight` | `STARTING` | `启动中` | `正在确认设备` | `""` | `启动中` | `正在启动…` 禁用 |
| `stopRequestInFlight` | `STOPPING` | `关阀中` | `请稍候` | `""` | `关阀中` | `正在关阀…` 禁用 |
| `settlementInFlight \|\| deviceState == 3` | `STOPPING` | `结算中` | `正在上传消费记录` | `""` | `结算中` | `结算中…` 禁用 |
| `justSettled`（见下） | `SETTLED` | 本次消费金额 | `本次消费` | `""` | `已完成` | `完成` → 点击回到待机 |
| `isOwnActiveSession()` | `BATHING` | 已用时长 `mm:ss` | `已用 ¥x.xx` | `余额 ¥x.xx` | `供水中` | `停止并结算` 启用 |
| 其它（已连接） | `IDLE` | `¥余额` | `余额可用` | `""` | `已连接` | `开始洗澡`（按原 `canStart` 决定 enabled） |

- `justSettled`：在结算成功分支（`0x86/0xFA` 成功处）设 `true`，只在用户点击「完成」时设 `false`。后续 `0x23` 空闲查询不得覆盖完成态。这是**纯展示标志**，不得影响任何原有分支判断。
- 时长/已用/消费金额：从 `startConfirmedAtMs`（在 `markStartConfirmed()` 记录 `SystemClock.elapsedRealtime()`）与
  `lastSettleAmountFen`（在 `onCollectResp` 记录的 `amount`）派生，用 `refreshGauge()` 里 1s 定时器刷新；定时器只在 `BATHING` 时运行。
  这些是**新增展示字段**，不得用于任何扣费/校验判断。
- 底部两张小卡：沿用 HTML 的 `stat1/stat2` 语义随状态切换（协议/水阀/水流/剩余/时长/余额/订单）。
- 底部主按钮点击：`IDLE` → `startBath()`；`BATHING` → `stopBath()`；`SETTLED` → 仅清除 `justSettled` 回到待机；
  其它状态按钮 `isEnabled=false`，点击只 `toast` 提示。
- 圆环点击 → `refreshUser()`（余额刷新），并调用 `playRefreshAnimation()`。
- 弹窗锁定：`operationLocksDevice() || rollbackInFlight` 为真时，设备弹窗内 `device_list`/`scan_btn`/`disconnect_btn`/`mac_input`/`connect_btn`
  全部 `isEnabled=false` 且 `alpha=0.42f`，并显示 `picker_lock_hint`。这与原有 `operationLocksDevice()` 一致，不新增锁。
- 设置页刷新按钮 → `refreshUser()`，**不关闭弹窗**；刷新完成后更新设置页里的手机号/余额文本。
- 设置页「重新登录」→ `requestRelogin()`（原有逻辑）。
- 设置页「检查更新」→ 只在副标题上做 1.2s「检查中…」→「已是最新版本」→ 复原的纯 UI 反馈。
- 头像点击 → 用 `Intent(Intent.ACTION_PICK)` + `startActivityForResult` 选图后仅做本地展示（不改头像上传逻辑，
  若没有上传接口就只 toast「头像修改功能待接入」并保留原有 `Session` 数据不变）。

### 6.1 `scanDevices()` 流程必须保持可用

`scanDevices()` 仍负责 `showDevicePicker()`、权限检查和 `beginScan()`；它只由用户点击蓝牙设备入口、设备页扫描按钮或外部扫描命令触发，启动 `BathActivity` 时不得自动调用。
新实现：`showDevicePicker()` 打开设备弹窗 overlay（`picker_sheet` 可见 + `sheet_scrim` 可见），
**不改变**扫描/连接的后续调用链。用户点击 `device_list` 项仍执行原 `connectDevice(mac)`；
点击 `connect_btn` 仍执行原「读 mac_input → connectDevice」；`disconnect_btn` → 原 `disconnectDev()`；
`scan_btn` → 原 `scanDevices()`（重新扫描）。

### 6.2 弹窗开关（新增，纯 UI）

- `showDevicePicker()` / `hideDevicePicker()`
- `showSettingsSheet()` / `hideSettingsSheet()`
- 遮罩 `sheet_scrim` 点击 → 关闭当前弹窗
- **禁止**在弹窗里调用任何新的业务接口。

## 7. 构建命令（子任务 D / 主协调者）

```powershell
$env:JAVA_HOME='D:\AndroidDev\jdk25\jdk-25.0.4.1+1'
$env:ANDROID_HOME='D:\AndroidDev\sdk'
$env:ANDROID_USER_HOME='D:\AndroidDev\android-home'
$env:GRADLE_USER_HOME='D:\AndroidDev\gradle-home'
$env:GRADLE_OPTS='-Dorg.gradle.java.installations.auto-download=false'
& 'D:\AndroidDev\gradle-9.5.0\gradle-9.5.0\bin\gradle.bat' -p 'D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev' :app:assembleDebug --no-daemon
```

APK：`app\build\outputs\apk\debug\睿智校园-0.1919.apk`

## 8. 子任务边界（防止互相踩）

- 子任务 A 只写：`BathGaugeView.kt`、（可选）`view_bath_gauge.xml`
- 子任务 B 只写：`bottom_sheet_device_picker.xml`、`bottom_sheet_settings.xml`、新增 `drawable/bg_apple_*.xml`
- 主协调者写：`activity_bath.xml`、`BathActivity.kt`、`values/colors.xml`、`values-night/colors.xml`、`drawable/bg_apple_sheet_rounded_top.xml` 等共享资源
- 子任务 D 只读 + 执行构建/安装/截图

**任何子任务都不要修改 `activity_bath.xml` 或 `BathActivity.kt`。**

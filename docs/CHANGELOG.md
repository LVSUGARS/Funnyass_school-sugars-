# 版本变更记录（睿智校园 · 无广告二次开发版）

> 本文档记录每一版的代码改动、原因与验证方式。
> 详细的问题排查过程见 `docs/HANDOFF-*.md`。

---

## 产品前提（很重要，读改动前先看）

这是**官方客户端的无广告二次开发版**。客户端职责只有三件事：

1. **发开闸（`0x21`）/ 关闸（`0x22`）指令**
2. **拉取余额**（`account/wallet`）
3. 扫描/连接洗浴设备

**计费由服务端按实际用水量自动完成**，客户端不参与计费、不存在预扣模型。
因此：消费记录上传失败（服务端返回 `226`）**不影响扣费**，只是少一条对账数据。

---

## 版本历史

### 1.0.2 — 登录页 Logo 与软件图标统一（当前版本）

**改动**：登录页首屏的 Logo 原本是「蓝色圆底 + 白色水滴矢量图」（`bg_apple_login_logo` +
`ic_water_drop`），与应用图标不一致。现改为**直接使用软件图标**
（`@drawable/ic_launcher_ruizhi_no_ads`，与桌面图标同款）。

图标本身是带圆角与底色的位图，因此去掉了原来的圆形背景、20dp 内边距与白色 tint，
尺寸由 72dp 调整为 96dp。

**文档**：README 增加「界面预览」，放入真机截图
（`docs/screenshots/dashboard.jpg`，已裁掉状态栏）。

---

### 1.0.1 — 修复发布资源文件名

v1.0.0 的 Release 资源名因编码问题变成 `-1.0.0.apk`（`gh` 上传时中文丢失）。
本版改以 ASCII 文件名 `RuiZhiXiaoYuan-1.0.1.apk` 上传。功能与 1.0.0 相同。

---

### 1.0.0 — 首个正式版

从 0.1965 升到 1.0.0，是一次收尾整理 + 补齐缺失功能。

**1. 清理界面上的「幽灵数据」**

布局文件里遗留了一批设计稿占位文字，**代码从未给这些控件赋值**，于是界面会一直显示看着像真数据的假值：

| 文件 | 幽灵内容 | 改为 |
|---|---|---|
| `bottom_sheet_device_picker.xml` | `宿舍 101 · KLCXKJ-Water` | `--` |
| `bottom_sheet_device_picker.xml` | `KLCXKJ-Water · 设备空闲` | `未连接` |
| `bottom_sheet_device_picker.xml` | `MAC AA:BB:CC:DD:EE:FF` | `MAC --` |
| `bottom_sheet_device_picker.xml` | `25468`（编号） | `--` |
| `bottom_sheet_device_picker.xml` | `20`（协议） | `--` |
| `bottom_sheet_settings.xml` | `138 0000 0000`（手机号） | `--` |
| `bottom_sheet_settings.xml` | `¥0.00`（余额） | `--` |
| `bottom_sheet_device_picker.xml` | `-48 dBm`（假信号值） | 已在 0.1965 清空 |

**2. 删除调试用的状态切换按钮**

右上角「实时状态」按钮（点击循环预览 未连接/待机/洗浴中/关阀中/已完成/异常）
已随布局与相关代码一并移除，其中包括 `cycleDebugGaugeState()`、`debugGaugeState`、
`resolveGaugeState()` 的 `debugState` 参数，以及 `renderGauge/renderStats` 里的预览分支
（这些分支会显示 `¥1.20`、`12:34` 等假数据）。

**3. 「检查更新」真正落地**

原实现是假的：固定延时后无脑显示「已是最新版本」。现在：

- 新增 `update/UpdateChecker.kt`：请求 GitHub Releases API
  （`repos/LVSUGARS/Funnyass_school-sugars-/releases/latest`），
  解析 `tag_name` 与当前版本做语义化比较（支持 `v1.0.0`、`1.0`、`1.0.0+build` 等形式）
- 发现新版本时版本行变为蓝色可点的「发现新版本 x.y.z，点此更新」，
  再点一次用浏览器打开 APK 直链（Release 里有 `.apk` 资源时）或 Release 页
- 区分「已是最新」「暂未发布版本」「查询过于频繁」「网络不可用」等结果
- 只用标准库 + Gson，未引入新依赖
- 新增 `UpdateCheckerTest`（5 项）覆盖版本归一化与比较

**4. 文档**

`README.md` 按当前功能重写：新增计费说明、项目结构、发布流程（含 tag 命名要求）、
自动重连与使用记录等说明；`docs/CHANGELOG.md` 补全历史。

**验证**：37 项单测通过；真机确认调试按钮消失、版本显示 1.0.0。

> 注意：本版本发布时仓库尚无 Release，因此 App 内首次「检查更新」会提示
> **「暂未发布版本」**。创建 v1.0.0 Release 后即恢复正常。

---

### 0.1965 — 同一台设备出现两个信号值（-90 与 -48）

**问题**：界面同时显示「已连接 · -90 dBm」和「-48 dBm」，看起来自相矛盾。

**根因有三层**：

1. **布局里有一个硬编码的假值**
   `bottom_sheet_device_picker.xml` 的「当前设备」卡片里写着
   `android:text="-48 dBm"`（设计稿占位），而**代码从未设置过它**。
   所以界面上一直挂着一个恒定 -48，与真实读数并排，看起来像两个信号。

2. **信号有两个数据源并存**
   `signalText()` 读扫描缓存 `deviceRssi[mac]`，而 `onRssiChanged()` 又把 GATT
   实时值写进同一个 Map。同一台设备于是出现两个不同读数。

3. **GATT `readRemoteRssi()` 在本机型上绝对值偏低**
   服务发现完成即读、以及 600ms/2s/5s 各读一次，稳定得到 -90/-91/-94；
   而扫描广播是 -48。服务发现 0.7s 内完成本身就说明信号很好，
   说明该机型 GATT 读数不可信（Android 上 GATT RSSI 精度问题较常见）。

**改法**：
- 清空布局里硬编码的 "-48 dBm"（保留节点以免改变既有布局结构）
- 新增 `currentRssi()` 作为**全应用唯一信号来源**：实时读数优先，其次扫描缓存；
  `signalText()` 与 `renderPickerCurrent()` 都走它
- `onRssiChanged()` **不再**写入 `deviceRssi`（那是扫描广播的缓存，不能混用）
- `BleManager` 在连接建立后按 600ms/2s/5s 多读几次 RSSI（单次读数会偏低），
  断开时清除待执行队列

**验证（真机）**：界面所有 dBm 节点统一为同一个值
（`device_entry_value` 与 `picker_current_meta` 都是 `已连接 · -92 dBm`）。

> 注：本项目机型上 GATT 与扫描的**绝对值**仍会不同，界面已统一采用 GATT 值以保证一致；
> 若后续要追求准确，应以扫描广播值为准。

---

### 0.1963 — 打开设备列表就断线 / 当前设备为空 / 房间名重复

**问题：「点开蓝牙设备之后，当前设备显示未连接、上面的当前设备也空了」**

三个叠加的原因，全都在「打开设备列表」这条链路上：

1. **打开列表会触发扫描，而扫描会主动断开连接**（最主要）
   `deviceEntryBtn` 原来绑的是 `scanDevices()` → `beginScan()`，而 `beginScan()` 里有
   `if (connected) { ble.disconnect(); connected = false }`。
   于是自动连接好的设备，用户一打开列表就被断开 → 显示「未连接」。
   **改法**：打开列表只 `showDevicePicker()`；`beginScan()` **不再断开连接**，
   也**不再清空 `deviceState`**（否则「水阀」卡片会变空）。
   真正换设备时 `connectDevice()` 会断旧连新并重查 `0x23`。
   列表里本来就有「重新扫描附近设备」按钮，需要找新设备时显式点它。

2. **`beginScan()` 清空 `device`**（0.1953 只修了一半）
   0.1953 保留了 `selectedMac`，但 `device = null` 仍在，所以「当前设备」区依旧变空。
   **改法**：不再清空 `device`，并新增 `restoreSelectedDeviceFromCache()` 用详情缓存回填；
   `renderPickerCurrent()` 在 `device` 为空时用缓存兜底。

3. **选中设备重复出现在「附近设备」列表**
   **改法**：`visibleDeviceMacs()` 新增可选参数 `selectedMac`，把已选设备从附近列表排除
   （比较时统一转小写——`equals(?, ignoreCase)` 在 selected 为 null 时是空值扩展，
   行为不可靠，实测漏排除）。

**顺带修掉的显示重复**：`devName` 本身可能已含房间名，而代码又拼了一次
（`room + " · " + name`），出现「612房 · 热水表-南铁院-10栋-6层-612房」。
新增纯函数 `joinRoomAndName()` 去重，设备列表项与主界面详情行一并改用。

**验证（真机）**：
- 自动连接后主界面 `已连接 · -90 dBm`
- 打开设备列表 → `picker_current_meta = "已连接 · -90 dBm"`（不再「未连接」）
- `picker_current_name = "热水表-南铁院-10栋-6层-612房 · 热水表"`（有内容、不重复）
- 打开列表后日志无「已断开」

新增测试：`BathDeviceNameTest`（4 项）、`BathDeviceFilterTest` 补 2 项。
单测总数 32，全部通过。

---

### 0.1961 — 水阀状态与实际不符 + 连接后信号未知

**问题 1：别人用水时，按钮已灰掉但「水阀」显示「空闲」**

根因：`renderStats()` 的 IDLE 分支写的是 `stat2ValueTv.text = if (d != null) "空闲" else "--"`
——**只判断"有没有拉到设备详情"，完全没看 `deviceState`**。
别人占用时 `deviceState==1`，而 `resolveGaugeState` 因为没有自己的会话会落到 IDLE 分支，
于是水阀被显示成「空闲」，与实际相反。

修复：改为按 `deviceState` 映射（新纯函数 `valveStateLabel`）：
`0→空闲 / 1→使用中 / 2→不可中断 / 3→有遗留数据 / 5→受控模式 / 其他→"--"`。
新增 `BathValveStateTest`（4 项）锁死映射，防止退化。

**问题 2：连接成功后一直显示「信号未知」**

根因：`signalText()` 只读 `deviceRssi[mac]`，而这个 Map 由**扫描结果**填充。
0.1955 加入的自动重连**不经过扫描**，所以永远没有 RSSI → 一直「信号未知」。

修复：
- `BleManager` 在服务发现完成后主动 `readRemoteRssi()`，缓存到 `liveRssi`
- 新增 `Listener.onRssiChanged(rssi)` 回调（**没有**复用 `onStateChanged`——它的
  `state=3` 带"通知已开启"语义，会误触发连接流程）
- `signalText()` 改为「先查扫描缓存，再退回活动连接实时值」

实测：自动重连后日志 `BLE RSSI=-92 dBm`，界面显示 `已连接 · -92 dBm`。

---

### 0.1959 — 使用记录 / 诊断日志改为全屏页

**需求**：原来两行是设置页内的折叠区（120~140dp），空间太小；改为点击跳转独立页面。

**改动**：
- 新增 `res/layout/page_usage_log.xml`、`res/layout/page_diagnostics.xml`，
  在 `activity_bath.xml` 末尾 `<include>`（放最后 → 天然盖在 sheet 与 scrim 之上）
- 设置页两行改为跳转入口，移除折叠区；返回按钮复用登录页同款
  （LinearLayout + `ImageView` 图标 + 「返回」文字）
- 两个页面各带「清空」（清使用记录 / 清日志）
- 系统返回键：`OnBackPressedCallback`，子页面可见时先关子页面，否则走默认行为

**踩到的坑（重要）**：
1. **返回按钮点不动**——全屏页没有避开系统栏，`usage_back` 中心落在 **y=112**，
   而状态栏高 128，**触摸被系统状态栏吃掉**。修法：`applyWindowInsets()` 里给两个
   子页面加 `bars.top/bottom` 内边距（页面 XML 里基础 `paddingTop=12dp`），
   修后中心 y=320，点击正常。
2. `ClassCastException`——两行在布局里改成了整行可点的 `LinearLayout`，
   但 Kotlin 字段类型仍是 `TextView`，启动即崩。
3. 与项目已有的 `formatDuration` 重名导致 `Conflicting overloads`，
   使用记录的格式化改名 `formatUsageDuration`。

**验证**：真机点「使用记录」→ 全屏页 → 点「返回」→ 回到设置页；
点「诊断日志」→ 整屏日志 → 点「返回」→ 回到设置页。两条链路均通过。

---

### 0.1958 — 全屏页返回按钮位置修复

见 0.1959 的第 1 条坑：给子页面加系统栏内边距，返回按钮不再落到状态栏区域。

---

### 0.1955 — 自动连接上次设备 + 使用记录

**需求**：
1. 记住设备还不够，要**自动连接**——只有手动断开或切换设备才替换
2. 设置页日志上方加**使用记录**，记录每次洗澡的时长与花费

**自动连接**：
- 新增 `autoConnectLastDevice()`，`onCreate` 后延 1.2s 触发
  （避开权限/适配器初始化，让界面先出来）
- 直接 `BleManager.connect(mac)`，**不需要先扫描**（Android 支持连已知地址）
- 前置检查：`operationLocksDevice()` / 已连接 / `ble.isBusy()`（新增方法，`gatt != null`）
  / 蓝牙开关，任一不满足就静默跳过，用户仍可手动扫描
- `beginScan()` 不再清空选择（0.1953 已修），因此扫描不会破坏"记忆的设备"

**使用记录**：
- 新增 `store/UsageLog.kt`：SharedPreferences + Gson，最多保留 50 条，时间倒序
- 每次用水收尾时写入一条（时长已知、金额待补），`loadWallet()` 拿到刷新后的余额后
  用 **`起始余额 - 当前余额`** 回填金额（比本地估算可信）；差值取不到才退回按费率估算，
  都取不到就显示 `--`，**不编造金额**
- 关键点：`markStartConfirmed()` 里重置 `usageEndedAtMs=0`，否则第二次洗澡不会再记录
- UI：设置页「使用记录」一行（位于「诊断日志」上方），展开显示
  `MM-dd HH:mm   时长   金额`，空态为「暂无使用记录」

**验证**：真机重启后日志出现
`恢复上次选中设备` → `自动连接上次设备` → `已连接，正在服务发现`；
设置页展开使用记录显示「暂无使用记录」（尚未产生用水）。

> 注意：使用记录的金额依赖服务端余额差值。若某次上传失败但服务端已扣费
> （见「已知未修复问题 1」），余额仍会刷新，因此金额依然能记录正确。

---

### 0.1954 — 文档整理

无代码改动，仅文档与脚本：

- 新增本文件 `docs/CHANGELOG.md`：版本历史、产品前提、已知未修复问题、项目约定、
  构建方式与调试用命令端口
- `README.md` 版本号 0.1919 → 0.1954 并链接到本文件
- `docs/HANDOFF-settlement-upload-signature.md` / `docs/HANDOFF-gauge-animation.md`
  头部补上最新状态与交叉引用（前者部分结论已被后续证据更正，必须在文档内声明）
- 修正 `scripts/verify-device.ps1` 里硬编码的 APK 路径默认值（`0.1919` 已不存在，
  会让脚本直接失败）→ 改为自动选取构建目录里最新的 APK

> 说明：`docs/apple-ui-contract.md`、`docs/apple-ui-spec.md`、
> `docs/REAL_DEVICE_TEST_PREP.md` 里的 `0.1919` 是**当时真机测试的历史记录**，
> 按既有约定保留，不随版本升级改写。

---

### 0.1953 — 设备选择持久化

**问题**（用户反馈两个现象，实为两个独立 bug）：
1. 重启 App 后选中的洗澡设备丢失
2. 点开「蓝牙设备」列表后，之前选中的设备也消失了

**根因**：
1. `selectedMac` 只存在内存（`BathActivity` 第 117 行），从未持久化
2. `beginScan()` 里有 `selectedMac = null` + `macInput.text.clear()`，
   **每次扫描都会清空选择**；而设备信息拉取会触发列表重绘，让这个问题更容易撞上

**改动**：
- `Session.kt` 新增 `selectedDeviceMac()` / `saveSelectedDeviceMac()`，复用已有
  的 `qzxy_lite` SharedPreferences
- `BathActivity.onCreate` 启动时恢复并回填 `macInput`
- `connectDevice()`（所有选中路径的总入口）选中即保存
- `beginScan()` **不再**清空选择，只刷新列表
- `disconnectDev()`（用户主动断开 = 明确放弃）清除持久化

**验证**：命令端口触发连接后重启应用，日志出现
`恢复上次选中设备 selectedMac=AA:BB:CC:DD:EE:FF`。

---

### 0.1952 — 余额小数位与官方一致

**问题**：余额被统一四舍五入到两位（服务端返回 `1.744`，界面显示 `¥1.74`）。

**改动**：`walletDisplay()` 改为**原样保留服务端返回的精度**。
所有余额显示点（主界面元信息、两个统计卡、「剩余」、设置页）都走这一个函数，改一处即全部生效。

**验证**：服务端 `1.744` → 界面 `¥1.744`。

**未改动**：`money(fen)` 仍两位小数——它只用于「本次消费」这类由**分**换算的金额。

---

### 0.1951 — 关阀后立即刷新余额（修"钱没回来"）

**问题**：上传消费记录失败后，界面余额**停留在用水中的 `0.000`**，
用户以为钱没退，必须切后台或重开应用才看到真实余额。

**根因**：
- `uploadData()` 的**三个失败分支都没有刷新余额**
- `justSettled = true`（进入「已完成」）只在收到 `0x86` 写回回执时设置，
  而 `0x86` 要等上传成功才发 → 上传失败就永远看不到「已完成」

**改动**：新增 `finishSettlementLocally(status)`，让上传失败也走"本次用水已结束"的收尾：
刷新余额、设置 `justSettled`，并按成功路径补发一次 `0x23` 刷新设备状态
（注意 `resolveGaugeState` 里 `deviceState == 3` 优先级高于 `justSettled`）。

**文案更正**：

| 位置 | 原文案 | 现文案 |
|---|---|---|
| 仪表盘错误态 | 结算中断 / 消费数据仍在设备中 / 请点击下方重试 | 记录待同步 / 设备仍存有本次记录 / 重连后可重试 |
| 状态栏 | 已关阀，消费记录上传失败，请重连重试 | 已关阀，已结束本次用水 |

---

### 0.1950 — 移除诊断探针

移除 0.1949 为排查 `226` 加的临时探针（`probeRateSign` / `probeUploadSign`
及 `CmdServer` 的对应动作），恢复干净的单次上传请求。

---

### 0.1940 — 三态波浪动画（启动/供水/关阀）

- `启动中`：活动波浪占比 18% → 78%，只播放一次
- `供水中`：整圈活动波浪持续流动
- `关阀中`：活动波浪占比 78% → 18%，只播放一次

占比动画与波浪流动相位**分离**，避免结束后循环跳回。

---

### 0.1937 ~ 0.1939 — 波浪圆环重新实现

按 Google Material 3 Expressive 的
[CircularWavyProgressIndicator](https://github.com/androidx/androidx/blob/androidx-main/compose/material3/material3/src/commonMain/kotlin/androidx/compose/material3/WavyProgressIndicator.kt)
实现：蓝色**局部活动波浪段** + 灰色平滑轨道，不再整圈波浪；
波峰 7 个，振幅在约 45%~100% 间连续涨落，两端逐渐收平。

**背景**：此前误做成了"整圈波浪"和"横穿画面的正弦线"，均被用户否决。
原设计里 BATHING 的绕圈水滴（`.full-orbit` / `.full-droplet`）**已整体废弃**，不要恢复。

---

### 0.1933 — 修复待机/异常状态动画跳变

**问题**：待机与异常状态下圆环"转一圈快转完了，没转过去就跳回原位"，周期性出现。

**根因**：`arcStartAngle()` 对 IDLE/ERROR 用了 `-90 + 288f * phase`。
`288°` 是**弧长**却被当成**旋转量**；相位每周期要绕一整圈，288° ≠ 360°，
导致周期结束时圆环朝向差 72°，每 2.8s 瞬移一次（逐帧实测 **73.7°**）。

**改动**：按设计规格（`docs/apple-ui-spec.md` 第 256 行起），IDLE/ERROR 的动画
`gaugeBreathe` / `gaugeAlert` **只改 opacity，弧不旋转**，故改为返回常量。

**同时**：把相位换算抽成纯函数 `BathGaugePhase`，新增 `BathGaugePhaseTest`
（5 个用例：循环点连续 / IDLE-ERROR 弧静止 / 流动匀速 / 呼吸连续 / 呼吸幅度），
毫秒级验证，防止退化。

---

### 更早（0.1920 ~ 0.1932）

- **0.1932**：登录页改为三步流程 —— 选择页（唯一有 Logo）→ 密码输入页 / 验证码输入页
- **0.1931**：修正返回按钮位置与左右对称、去掉多余空档
- **0.1929**：修正滚动条占用 16dp 导致内容左偏的问题
- **0.1921 ~ 0.1926**：修复登录页键盘遮挡输入框（edge-to-edge 下需自行处理 `ime()` 内边距）
- **0.1920**：蓝牙过滤改为广播名包含 `water`（大小写不敏感）即显示

---

## 已知未修复的问题

### 1. 消费记录上传失败（服务端 `226 加密校验失败`）

- **影响**：低。计费由服务端自动完成，此失败只影响对账数据
- **已排除**：签名错误（`226` ≠ 签名错误，返回 `170` 才是）、大小写（大小写都是 `226`）、
  用 native `encryptByAES` 加密 `xfData`（返回 `-2`）、官方 APK 逆向（易盾加固）、
  换用 `checkSignature`（反汇编证明它只校验 APK 包签名）
- **唯一可靠解法**：抓一次官方 App 的成功请求，看 `xfData` 的真实格式与全部字段
- **抓包环境已验证可行**：手机连电脑热点（`192.168.137.x` 同子网），
  `mitmdump --listen-host 0.0.0.0 --listen-port 8888` +
  `adb shell settings put global http_proxy 192.168.137.1:8888`。
  ⚠️ 收尾务必 `adb shell settings delete global http_proxy`，
  否则 OPPO 会弹「不安全代理」警告并可能拦截官方 App

### 2. 「重试结算」按钮在真实场景下不会成功

它重新去设备读记录，而**设备侧的 `0x85` 记录读取一次后即被清空**。
正确做法是缓存首次采集的原始 payload 用于重传。等问题 1 定位后再一并处理，
否则只是重发同样的错误数据。

---

## 项目约定（改动时必须遵守）

1. **每次代码改动递增版本号**，并同步四处：
   - `app/build.gradle.kts`（`versionCode` + `versionName`）
   - `BathActivity.APP_VERSION`（约第 1970 行）
   - `res/layout/bottom_sheet_settings.xml`（「当前版本 0.19xx」）
   - `androidTest/BathUiSmokeTest.kt`（版本断言）
2. 构建通过后把 `app/build/outputs/apk/debug/睿智校园-<版本>.apk`
   复制到桌面，删除桌面旧包，并校验 SHA-256 一致
3. 用 `adb install -r -d` 覆盖安装，**不要清理应用数据**（会清掉登录态）
4. 不要把手机号、`loginCode`、`signature`、完整 MAC、消费数据传到公开位置

---

## 构建

```powershell
$env:JAVA_HOME='D:\AndroidDev\jdk25\jdk-25.0.4.1+1'
$env:ANDROID_HOME='D:\AndroidDev\sdk'
$env:ANDROID_USER_HOME='D:\AndroidDev\android-home'
$env:ANDROID_AVD_HOME='D:\AndroidDev\avd'
$env:GRADLE_USER_HOME='D:\AndroidDev\gradle-home'
$env:GRADLE_OPTS='-Dorg.gradle.java.installations.auto-download=false'
& 'D:\AndroidDev\gradle-9.5.0\gradle-9.5.0\bin\gradle.bat' -p '<仓库路径>' `
    :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

当前：22 项 JVM 单测通过。真机为 OPPO PKJ110（adb 序列号 `629ca0e1`）。

---

## 调试用命令端口（debug 包自带）

应用内起 HTTP 服务监听 `127.0.0.1:8080`（`CmdServer.kt`）：

```powershell
adb forward tcp:8080 tcp:8080
# 读日志 / 清日志 / 看状态
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/log'
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/clear'
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/status'
# 动作：connect / scan / start / stop / disconnect / query / collect / upload / fail
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/cmd?action=connect&mac=AA:BB:CC:DD:EE:FF'
```

⚠️ 参数必须走 **query string**，放 POST body 会解析不到 `action`。

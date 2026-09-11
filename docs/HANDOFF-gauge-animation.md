# 转接任务：修复「睿智校园」仪表盘待机/异常状态动画跳变

> ✅ **该问题已修复**（0.1933）。根因与验证见 `docs/CHANGELOG.md` 的 0.1933 条目：
> IDLE/ERROR 把弧长 `288°` 当成旋转量写进相位换算，而相位每周期要绕一整圈，
> 导致每 2.8s 在循环点瞬移 73.7°。
> 修复方式 + 回归测试：`BathGaugePhase.kt` / `BathGaugePhaseTest.kt`。
> 本文档保留原始排查过程与体力教训（第 8 节的"走过的弯路"仍值得一读）。

> 交接时间：2026-09-11
> 交接原因：原 AI 多轮修改后用户反馈问题仍在，用户判断原 AI 效率过低，转交处理。

---

## 1. 项目基本信息

| 项 | 值 |
|---|---|
| 仓库路径 | `D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev` |
| 当前分支 | `ui-apple-minimal`（HEAD = `a3e11d0 docs: add Apple style UI preview`） |
| 包名 | `com.funnyass.miuix`（注意：与仓库原名 `com.funnyass.test` 不同，是独立副本，可与原调试版并存安装） |
| 当前版本 | `versionCode = 1933` / `versionName = "0.1933"` |
| compileSdk / targetSdk / minSdk | 37 / 37 / 24 |

### 构建环境（必须设置，否则构建失败）

```powershell
$env:JAVA_HOME='D:\AndroidDev\jdk25\jdk-25.0.4.1+1'
$env:ANDROID_HOME='D:\AndroidDev\sdk'
$env:ANDROID_USER_HOME='D:\AndroidDev\android-home'
$env:ANDROID_AVD_HOME='D:\AndroidDev\avd'
$env:GRADLE_USER_HOME='D:\AndroidDev\gradle-home'
$env:GRADLE_OPTS='-Dorg.gradle.java.installations.auto-download=false'

& 'D:\AndroidDev\gradle-9.5.0\gradle-9.5.0\bin\gradle.bat' -p 'D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev' :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

APK 输出：`app\build\outputs\apk\debug\睿智校园-0.1933.apk`（文件名由 `androidComponents` 块动态生成）

设备：用户真机 **OPPO PKJ110**（曾在 `adb` 中以序列号 `629ca0e1` 出现，当前未连接），另有模拟器 `emulator-5554`（API 36，1440x3168 @640dpi）。

---

## 2. 问题现象（用户原话，逐字保留）

> 「待机状态和异常状态下，还是不连贯，会跳一下」
> 「他这个问题一直都有，他就是这一圈快转完了，然后没转过去，直接跳到原来位置继续，是周期性的，就是中间少转了一部分」
> 「0.1933」（确认安装的是 1933 版本后问题仍在）

**解读**：待机/异常时圆环上的弧段会旋转，旋转接近一圈时**没有走完**，直接跳回起点继续，周期性重复，中间"少转了一段"。

---

## 3. 核心矛盾：代码逻辑与用户现象不符 ⚠️ 请从这里开始

### 3.1 已修复的原始 bug（有硬证据）

原代码 `BathGaugeView.arcStartAngle()` 中，IDLE/ERROR 的弧起始角为：

```kotlin
GaugeState.IDLE, GaugeState.ERROR -> (1f - GAP_FRACTION) * 360f * phase   // 旧代码
```

`(1f - GAP_FRACTION) * 360f = 288f` 是**弧长**（弧占 288°，缺口 72°），却被当成**旋转量**。而相位 `phase` 每个周期要绕**一整圈（360°）**，288° ≠ 360°，导致每个周期结束时圆环朝向差 72°。

逐帧计算弧中心角的位移（脚本见第 6 节）：

```
帧 167 → 168（循环点）：位移 73.7°   ← 跳变
其余每一帧：            位移 1.71°   ← 完美匀速
```

**73.7° 与用户描述的"少转了一部分"（缺口宽 72°）完全吻合**，且是周期性的（每 2.8s 一次，`DUR_BREATHE_IDLE = 2800`）。

### 3.2 已做的修复

按设计稿 `docs/apple-ui-spec.md` 第 256 行规格，IDLE/ERROR 的动画是 `gaugeBreathe` / `gaugeAlert`，**只改 opacity，弧本身不旋转**（位置由静态 `dasharray:477.5; dashoffset:95.5` 决定）。故已改为：

```kotlin
// app/src/main/java/com/funnyass/test/ui/BathGaugePhase.kt:34-39  ← 当前源码
fun arcStartAngleFor(state: BathGaugeView.GaugeState, phase: Float): Float = when (state) {
    BathGaugeView.GaugeState.IDLE,
    BathGaugeView.GaugeState.ERROR,
    BathGaugeView.GaugeState.DISCONNECTED -> STATIC_ARC_START   // 常量，不含 phase
    else -> -90f + 360f * phase                                 // 流动状态，走满一整圈
}
```

`STATIC_ARC_START = -90f + (1f - GAP_FRACTION) * 360f = 198f`

### 3.3 已验证的证据（三处独立验证均通过）

1. **源码静态检查**：全文搜索确认无残留旧写法（仅注释里提到 288 作为历史说明）
2. **JVM 单元测试**：`app/src/test/java/com/funnyass/test/ui/BathGaugePhaseTest.kt`，5 个用例全部通过（循环点连续 / IDLE-ERROR 弧静止 / 流动匀速 / 呼吸连续 / 呼吸幅度）
3. **真机实测**：在用户手机上切到待机，弧起始角 6 个采样帧恒为 `0`；异常状态恒为 `-1`

### 3.4 矛盾点

**IDLE/ERROR 的弧角度现在是常量，数学上不可能旋转。** 但用户确认装了 0.1933 后现象依旧。

**因此只有两种可能，必须先用一次操作分清：**

- **(A) 用户实际运行的不是这份代码** —— 例如装了旧包、或点开了另一个图标（`com.funnyass.miuix` 与原调试版 `com.funnyass.test` 可并存）
- **(B) 旋转来自另一条尚未找到的路径** —— 原 AI 未能定位

---

## 4. 建议的第一步（不要跳过）

**打开 App → 右上角齿轮 → 看「检查更新」那行的版本号。**

- 若 ≠ `0.1933` → 情况 (A)，问题在安装包，重新安装桌面版即可
- 若 = `0.1933` 且仍跳 → 情况 (B)，执行第 5 节

也可用哈希比对确认手机上的 APK 就是本地构建产物：

```powershell
$adb='D:\AndroidDev\sdk\platform-tools\adb.exe'
$devPath=((& $adb -s <序列号> shell pm path com.funnyass.miuix) -split "`n" | Where-Object { $_ -like '*base.apk*' }) -replace '^package:',''
& $adb -s <序列号> pull $devPath.Trim() "$env:TEMP\dev.apk"
(Get-FileHash "$env:TEMP\dev.apk" -Algorithm SHA256).Hash
(Get-FileHash 'D:\...\app\build\outputs\apk\debug\睿智校园-0.1933.apk' -Algorithm SHA256).Hash
```

---

## 5. 情况 (B) 的排查方向（按可能性排序）

### 5.1 首选怀疑：`gaugeTicker` 每秒重启动画

`BathActivity.kt` 中：

```kotlin
// 第 158-163 行
private val gaugeTicker = object : Runnable {
    override fun run() {
        refreshGauge()
        if (isOwnActiveSession()) opHandler.postDelayed(this, 1000L)
    }
}

// 第 546-549 行
private fun refreshGauge() {
    if (!this::gauge.isInitialized) return
    val state = gaugeState()
    gauge.setGaugeState(state)
    ...
}
```

若 `gaugeState()` 返回值在 IDLE 与其它状态间**每秒抖动一次**，`setGaugeState()` 会每次重置相位，表现为周期性跳变。

`BathGaugeView.setGaugeState()`（第 199-215 行附近）中有早退保护：

```kotlin
fun setGaugeState(state: GaugeState) {
    if (gaugeState == state) return   // ← 保护存在
    ...
}
```

**待验证**：打印 `gaugeState()` 每秒的实际返回值，确认是否抖动。

### 5.2 次选：动画被反复 cancel / restart

`BathGaugeView.kt` 中以下位置都会触发 `startAnimatorForCurrentState()` 或 `cancelStateAnimators()`：

| 行号 | 触发源 |
|---|---|
| 652 | `onAttachedToWindow` |
| 677 | `onVisibilityChanged` |
| 684 | `onWindowVisibilityChanged` |

若这些回调被反复触发，相位会不断重启。**注意**：`startAnimatorForCurrentState()` 内有复用保护（第 578-583 行），但 `DISCONNECTED` 与 `BATHING` 是例外分支，值得细看。

### 5.3 其它已排除项（不要重复查）

- ❌ 帧率不足：实测帧间隔中位 16.6ms、p95 16.6ms、**超过 33ms 的丢帧为 0**
- ❌ 相位推进不均匀：单帧增量稳定 `0.01062`（中位数 = 最大值），线性完美
- ❌ 呼吸透明度在循环点不连续：`breathAlpha` 用 `(1-cos(2π·phase))/2`，phase=0 与 phase→1 均为最小值，数学连续
- ❌ 流动状态（STARTING/BATHING/STOPPING）跳变：用 `360×phase`，每帧步长与循环点步长完全相等

---

## 6. 有用的诊断脚本（原 AI 用过，可复用）

### 6.1 纯计算验证弧中心角连续性

```powershell
function AngleDelta($a,$b){ $d=$b-$a; while($d -gt 180){$d-=360}; while($d -lt -180){$d+=360}; return $d }
$sweep=288.0; $rot=0.0    # IDLE 修复后 rot=0；流动状态 rot=360
$prev=$null; $maxAbs=0; $at=-1; $loopD=0
for($f=0;$f -le 170;$f++){
  $phase=($f % 168)/168.0
  $center=-90 + $rot*$phase + $sweep/2
  $d=if($prev -ne $null){ AngleDelta $prev $center } else { 0 }
  if($f -gt 0 -and [math]::Abs($d) -gt $maxAbs){ $maxAbs=[math]::Abs($d); $at=$f }
  if($f -eq 168){ $loopD=$d }
  $prev=$center
}
"最大单帧位移=$maxAbs° (帧$at)  循环点位移=$loopD°"
```

### 6.2 真机切换状态观察（调试开关）

App 内有隐藏调试开关：`R.id.debug_gauge_toggle`，**每次点击循环切换**：

```
实时状态 → 未连接 → 待机 → 启动中 → 洗浴中 → 关阀中 → 已完成 → 异常 → (回到实时状态)
```

该开关仅 debug 包可用（`isDebuggable()` 检查 `FLAG_DEBUGGABLE`）。点击坐标可用 `uiautomator dump` 取 `debug_gauge_toggle` 的 bounds 求得。

### 6.3 关键坐标（1440x3168 屏幕）

| 元素 | bounds |
|---|---|
| `bath_gauge` | `[256,690 .. 1184,1618]`（232dp 见方，中心 `(720,1154)`） |
| `debug_gauge_toggle` | `[896,233 .. 1184,377]` |

圆形仪表盘半径换算：`ringRadius = (min(w,h)_px - dp(15)) / 2`，控件 232dp 时 `ringRadius ≈ 386px`；实际绘制直径由常量 `GAUGE_DIAMETER_DP = 208f` 决定。

---

## 7. 相关文件清单

| 文件 | 作用 |
|---|---|
| `app/src/main/java/com/funnyass/test/ui/BathGaugeView.kt` | 仪表盘自绘 View（弧、水波、中心文字、全部动画） |
| `app/src/main/java/com/funnyass/test/ui/BathGaugePhase.kt` | **已抽出的纯函数**：`arcStartAngleFor` / `arcAlphaFor` / `arcWidthFractionFor` |
| `app/src/test/java/com/funnyass/test/ui/BathGaugePhaseTest.kt` | **回归测试**（5 用例，毫秒级，无需设备） |
| `app/src/main/java/com/funnyass/test/ui/BathActivity.kt` | 主界面；`gaugeState()` 第 536-543 行，`refreshGauge()` 第 546 行，`gaugeTicker` 第 158 行 |
| `app/src/main/res/layout/activity_bath.xml` | 布局；`bath_gauge` 第 90-95 行（232dp） |
| `docs/apple-ui-spec.md` | **权威设计规格**（第 256 行起是各状态动画定义表） |
| `docs/apple-design-preview.html` | HTML 设计预览稿（动画 CSS 在第 260-300 行） |

### 设计稿规格（`docs/apple-ui-spec.md` 第 256 行起）

| 状态 | stroke | stroke-width | dasharray | 动画 | 周期 |
|---|---|---|---|---|---|
| `idle` | green | 15px | `477.5` + dashoffset `95.5` | `gaugeBreathe`（**仅 opacity**） | 2.8s |
| `starting` | blue | 15px | `120 357.5` | `gaugeFlow`（dashoffset→-477.5） | 1.1s |
| `bathing` | blue | 15px | `80 397.5` | `gaugeFlow` | 1.6s |
| `stopping` | #ff9f0a | 15px | `120 357.5` | `gaugeFlowReverse` | 1.1s |
| `settled` | green | 15px | `477.5` dashoffset `0` | `gaugeBreathe`（仅 opacity） | 2.4s |
| `error` | red | 15px | `477.5` dashoffset `95.5` | `gaugeAlert`（**仅 opacity**） | 1s |

关键：`idle` 与 `error` 的动画**只改 opacity**，弧位置固定（缺口 72° 居中在顶部）。

---

## 8. 原 AI 走过的弯路（**请勿重复**）

1. **依赖像素采样判断连续性** —— `adb exec-out screencap` 单次耗时 200–400ms，采样间隔远大于帧间隔，**两次得出"没问题"的错误结论**。若需高频验证，请用录屏（`adb shell screenrecord`）或直接在 `onDraw` 里采样计数，不要用连续截图。

2. **误判为性能问题** —— 花了多轮排查软件图层 / `setShadowLayer` 模糊导致的掉帧，实测丢帧为 0，是错误方向。

3. **为容纳水滴而缩小仪表盘** —— 曾把 `ringRadius` 减 40dp 让水滴不被裁，被用户否决（「你把仪表盘的体积缩小了？调回去」）。**主视觉不应为小装饰让步。**

4. **误解"波形"需求** —— 用户要的是「**把圆环本身画成波浪状**」（Google 加载动画那种起伏的圈），原 AI 先做成了「横穿画面的正弦线」，被否决（「不是这个波形图啊」）。

5. **水滴特性已整体废弃** —— 用户明确表示「这个水珠我不要了」。原设计中 BATHING 状态的绕圈水滴（`.full-orbit` / `.full-droplet`）**已全部删除**，取而代之的是波浪圆环（`drawWavyRing`）。不要恢复水滴。

6. **反复"编译-装机-截图"循环** —— 用户明确批评「不要重复操作，你一直在这耗时间」。请先做静态推理和可离线验证（单元测试），再上设备。

---

## 9. 当前代码状态（干净可交付）

- 源码已修（见 3.2），纯函数已抽出，回归测试已加
- `:app:testDebugUnitTest` + `:app:assembleDebug` 通过
- 所有临时诊断测试与日志已清除（`androidTest` 目录仅剩原有 4 个测试文件）
- `git diff --check` 干净
- 桌面 APK：`C:\Users\L3356\Desktop\睿智校园-0.1933.apk`
  - SHA-256：`113090881413889D69E0BDF0D823AC0C866130D423BA31DF4332038D1451E888`

### 本轮涉及的工作区改动（未提交）

```
M  app/build.gradle.kts
M  app/src/main/AndroidManifest.xml
M  app/src/main/java/com/funnyass/test/ui/BathActivity.kt
M  app/src/main/java/com/funnyass/test/ui/LoginActivity.kt
M  app/src/main/res/layout/activity_bath.xml
M  app/src/main/res/layout/activity_login.xml
M  app/src/main/res/values*/colors.xml, themes.xml
M  docs/apple-design-preview.html
?? app/src/main/java/com/funnyass/test/ui/BathGaugeView.kt     ← 全新文件
?? app/src/main/java/com/funnyass/test/ui/BathGaugePhase.kt   ← 全新文件
?? app/src/test/java/com/funnyass/test/ui/                    ← 目录
?? app/src/main/res/drawable/bg_apple_*.xml (多个)
?? app/src/main/res/drawable/ic_apple_*.xml (多个)
?? app/src/androidTest/java/com/funnyass/test/*.kt
?? docs/apple-ui-spec.md, docs/apple-ui-contract.md, docs/REAL_DEVICE_TEST_PREP.md
?? scripts/
```

### ⚠️ 需要注意的既有约定

1. **每次代码改动都要递增版本号**（用户明确要求），并同步这四处：
   - `app/build.gradle.kts`（`versionCode` + `versionName`）
   - `app/src/main/java/com/funnyass/test/ui/BathActivity.kt`（`APP_VERSION` 常量，约第 1910 行）
   - `app/src/main/res/layout/bottom_sheet_settings.xml`（「当前版本 0.19xx」文案）
   - `app/src/androidTest/java/com/funnyass/test/BathUiSmokeTest.kt`（版本断言）

2. **发布 APK 到桌面**：每次改完构建，把 `app\build\outputs\apk\debug\睿智校园-<版本>.apk` 复制到桌面，并删除桌面上的旧包、校验 SHA-256 一致。

3. **不要清理用户手机上的应用数据**（会清掉登录态和设置）。原 AI 只清过自己那台模拟器的数据。

---

## 10. 一句话总结给接手者

> 代码层面已证明并修复了「IDLE/ERROR 弧旋转在循环点跳 73.7°」的 bug，且有单元测试与真机实测双重佐证；但用户确认安装 0.1933 后现象仍在。**首要任务是确认用户实际运行的 APK 是否就是这份代码**（版本号 / APK 哈希比对），确认无误后再按第 5 节排查第二条旋转来源。不要再用连续截图做判断依据。

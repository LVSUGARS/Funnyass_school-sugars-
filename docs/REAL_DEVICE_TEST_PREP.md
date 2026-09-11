# 睿智校园 0.1919 真机测试准备与逆向成果清单

更新时间：2026-09-11

本文用于真机测试前的交接和执行记录。它只整理仓库中已经存在的代码、注释和构建产物，不把静态代码分析当成真实设备验证结果。

## 1. 当前构建状态

| 项目 | 当前值 |
|---|---|
| applicationId | `com.funnyass.miuix` |
| namespace | `com.funnyass.test` |
| versionName | `0.1919` |
| minSdk | API 24（Android 7.0） |
| targetSdk | API 37 |
| APK | `app/build/outputs/apk/debug/睿智校园-0.1919.apk` |
| 原生 ABI | 目前只包含 `arm64-v8a` |
| 构建验证 | `testDebugUnitTest`、`assembleDebug`、`connectedDebugAndroidTest` 已通过 |

真机必须优先选择 ARM64 设备。没有 `armeabi-v7a`、`x86` 或 `x86_64` 的原生库，非 ARM64 设备不能作为首轮测试机。

## 2. 已找到的逆向/移植成果

仓库没有独立命名为“逆向”的目录；相关成果已经被整理进可编译代码中。

### 2.1 BLE 连接层

文件：`app/src/main/java/com/funnyass/test/ble/BleManager.kt`

- 服务 UUID：`0000FF00-0000-1000-8000-00805F9B34FB`
- 通知特征：`0000FF01-0000-1000-8000-00805F9B34FB`
- 写入特征：`0000FF02-0000-1000-8000-00805F9B34FB`
- CCCD：`00002902-0000-1000-8000-00805f9b34fb`
- 流程：扫描 → GATT 连接 → 服务发现 → 打开 FF01 通知 → 通过 FF02 写入。
- 写入按 20 字节分片，片间等待 20 ms，并等待每片写入回执。
- 扫描超时为 12 秒。

这些是代码中的协议假设，必须用目标水控器实测服务和特征是否一致。

### 2.2 水控帧协议

文件：`app/src/main/java/com/funnyass/test/ble/FrameUtils.kt`

二进制帧结构：

```text
60 00 dataLen+3 80 cmd 00 data... crc 16
```

线上帧结构：

```text
# + 二进制帧大写 HEX 文本 + LF(0x0A)
```

CRC 计算：`(sum(data) + 0x80 + cmd) % 256`。

当前控制命令在 `BathActivity.kt` 中的映射：

| 命令 | 用途 |
|---|---|
| `0x21` | 下发费率/启动准备数据 |
| `0x22` | 关阀 |
| `0x23` | 查询设备状态 |
| `0x85` / `0xFB` | 采集消费数据 |
| `0x86` / `0xFA` | 写回结算数据 |

### 2.3 原应用 JNI 加密与签名移植

文件：

- `app/src/main/java/com/klcxkj/jni/JniUtils.java`
- `app/src/main/jniLibs/arm64-v8a/libklcxkjencry.so`
- `app/src/main/java/com/funnyass/test/util/Crypto.kt`

已移植的 native 能力：

- `signParams(loginCode, raw)`：第二套请求签名。
- `getSk()`、`getBk()`、`getCk()`、`getDk()`、`getMk()`：原应用密钥素材接口。
- `encryptByAES()` / `decryptByAES()`：设备域 `clData` 加解密。
- 密码登录使用 `MD5(明文).toUpperCase()` 的后 10 位。
- 验证码 secret 使用手机号前三位、后四位和 `klcx` 计算 MD5。

原生库来源和分发权利仍需由项目维护者确认。真机测试只使用本地授权账户，不要把签名输入、密钥返回值或令牌写入公开日志。

### 2.4 HTTP 接口映射

文件：`app/src/main/java/com/funnyass/test/net/Api.kt`

当前调用的业务路径包括：

- `user/verification/code/get`
- `user/registerAndLogin`
- `user/login`
- `user/info`
- `account/wallet`
- `device/info/mac`
- `order/downRate/bluetooth/rateOrder`
- `order/upload/bluetooth/data`
- `order/upload/bluetooth/fail`

接口签名和鉴权实现位于 `ApiClient.kt`、`Crypto.kt`。接口返回成功不等于设备真实执行成功；开阀必须以设备回帧和随后状态查询为准。

### 2.5 历史消费记录逆向结论

我检查了当前源码、所有本地 Git 分支/提交以及仓库内可识别的说明文件，没有找到独立的历史订单列表接口、历史记录页面或已保存的 JADX/Smali 逆向工程目录。

当前代码只覆盖以下“当前交易/恢复”数据：

- `rateOrder` 返回本次交易的 `consumeDate`、订单号和下发设备数据。
- `uploadBluetoothData` 上传本次设备消费数据并返回本次结算结果。
- `failBluetoothOrder` 按 `consumeDate` 回滚未完成的本次订单。
- `BathActivity` 在本地 `bath_safety` 偏好中保存待核对的 `consumeDate` 和 MAC，用于异常恢复，不是历史列表。

因此，当前 APK **不能直接查看历史消费记录**。不能根据接口名称猜一个历史接口，也不能把本地待核对记录当作账户历史。

要继续逆向历史记录，至少需要一个经授权的证据来源：原官方 APK/JADX 输出、官方 App 的历史记录网络请求（已脱敏）、或服务端接口文档。拿到证据后才能确认路径、分页参数、签名字段和数据模型；在此之前不应把“历史记录功能”列入真机放行范围。

### 2.6 本地调试控制

文件：`app/src/main/java/com/funnyass/test/CmdServer.kt`

应用会在 `127.0.0.1:8080` 提供本地命令端口，供 `adb forward` 后进行诊断。它不是远程服务，不应暴露到局域网。首轮真机测试优先使用界面操作；只有需要复现协议状态时才使用命令端口。

## 3. 真机前置条件

- 使用本人有权使用的校园账户和本人明确确认归属的水控器。
- 手机为 ARM64，Android 7.0 或更高版本。
- Android 12+ 授予“附近的设备”权限；Android 11 及以下按系统提示授予定位权限。
- 蓝牙已打开，系统没有限制应用的蓝牙扫描权限。
- 手机可以访问接口域名；当前构建允许明文 HTTP，若实际服务切换到 HTTPS 需重新确认网络配置。
- 首轮测试保持账户余额较低但足以完成一次最小可控测试，并提前记录测试前余额。
- 测试期间保留手机系统时间、设备型号、Android 版本和应用版本记录。
- OPPO Find X8 Ultra 属于 ARM64 设备，满足当前 APK 的 native ABI 条件；ColorOS 上应将本应用的“附近的设备”权限设为允许，并在首轮测试期间关闭电池优化/后台冻结。

## 4. 安装与采集命令

以下命令在 PowerShell 执行。先确认 `adb devices` 只显示目标真机，不要误操作其他设备。

```powershell
$adb = 'D:\AndroidDev\sdk\platform-tools\adb.exe'
$apk = 'D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev\app\build\outputs\apk\debug\睿智校园-0.1919.apk'

& $adb devices
& $adb install -r $apk
& $adb shell am force-stop com.funnyass.miuix
& $adb shell monkey -p com.funnyass.miuix 1
```

采集日志时不要把完整日志上传到公开位置，日志可能包含账户编号、设备 MAC、订单数据和签名输入：

```powershell
$log = 'D:\Documents\ChatGPT\water\real-device-0.1919-log.txt'
& $adb logcat -c
& $adb logcat -v threadtime -s AndroidRuntime com.funnyass.miuix '*:S' | Tee-Object -FilePath $log
```

结束采集用 `Ctrl+C`。如果需要测试本地命令端口：

```powershell
& $adb forward tcp:18080 tcp:8080
Invoke-WebRequest 'http://127.0.0.1:18080/status'
```

不要在没有确认设备归属和测试阶段的情况下调用 `start`、`stop`、`collect` 或 `upload` 命令。

## 5. 分阶段测试矩阵

| 阶段 | 操作 | 通过标准 | 失败时保留证据 |
|---|---|---|---|
| A 启动 | 冷启动应用 | 进入仪表盘；设备选择 sheet 默认隐藏 | 截图、logcat、Android 版本 |
| B 权限 | 点击“蓝牙设备” | 仅此时出现权限/蓝牙提示，允许后显示扫描页 | 权限结果、系统版本 |
| C 扫描 | 扫描 12 秒 | 能看到目标设备名或可识别广播名，RSSI 有值 | 设备名、RSSI、时间 |
| D 识别 | 只点击目标设备 | 页面显示正确房间/设备信息，不能误连邻近设备 | 页面截图、设备 MAC 脱敏值 |
| E 查询 | 连接后等待状态 | 收到状态回帧，显示协议、阀状态和可开始/不可开始原因 | BLE TX/RX 日志 |
| F 最小业务 | 在确认归属后开始一次最短用水 | 只有服务端下费率成功且设备回帧确认后才供水 | 测试前后余额、订单时间 |
| G 结束 | 点击停止并等待结算 | 关阀确认、采集、上传、写回完成，余额和订单一致 | 完整脱敏日志 |
| H 恢复 | 断蓝牙/杀进程/重新打开 | 不重复下单；能提示核对待处理预扣 | 状态截图、日志 |

## 6. 首轮禁止事项

- 不要扫描到多个设备后凭信号强度猜测归属。
- 不要在设备房间信息未确认时点击开始。
- 不要连续点击开始、停止、连接或扫描。
- 不要在结算未完成时切换设备或清除应用数据。
- 不要清理 `bath_safety` 共享偏好；其中可能有待核对预扣记录。
- 不要公开上传手机号、验证码、密码、登录令牌、完整 MAC、账户编号、消费数据或 native 签名日志。

## 7. 已知风险与未验证项

1. 当前自动化测试只覆盖模拟器 UI、布局、状态和协议纯函数，不能证明目标水控器兼容。
2. 真实 BLE 服务 UUID、设备广播名、RSSI 稳定性和 GATT 写入行为尚未在目标真机/水控器上验证。
3. 真实账户余额、下费率、开阀、关阀、结算链路尚未完成现场证据闭环。
4. 原生库只有 `arm64-v8a`，其他 ABI 尚未提供。
5. 官方接口和设备协议可能变更；接口返回 `code=0` 也不能单独证明水阀已执行。
6. JNI 二进制和衍生图标的再分发权利需要单独确认，不能由 MPL-2.0 自动覆盖。

## 8. 放行标准

首轮真机测试只有在以下条件全部满足后，才允许进入最小金额业务测试：

- A、B、C、D、E 阶段通过。
- 设备房间和 MAC 已由现场人员确认。
- 测试账户、测试时间和测试前余额已记录。
- 连接、查询、断开、重新连接均无崩溃。
- 失败时能保留日志，并能通过原设备完成待处理预扣核对。
- 测试人员明确知道停止后必须等待关阀和结算完成，不能直接卸载或清除数据。

在这些条件未满足前，结论只能写为“协议和代码准备完成，真实设备兼容性待验证”。

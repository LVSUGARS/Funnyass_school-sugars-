# 转接任务：修复「睿智校园」关水结算失败（上传消费记录签名被服务端拒绝）

> 📌 **最新状态请看 `docs/CHANGELOG.md`**（版本历史、已知未修复问题、项目约定、构建与调试端口）。
> 本文档保留的是 `226` 上传失败的完整排查过程，其中部分结论已被后续证据更正——见下方更正说明。

> ⚠️ **2026-09-12 重要更正（务必先读）**
>
> 用户澄清了产品前提：**这是官方客户端的无广告二次开发版**，客户端职责只有三件事——
> **发开闸/关闸指令、拉取余额**。**计费由服务端按实际用水量自动完成，不存在客户端预扣**，
> 也**不存在"钱没入账"或漏账**的问题。
>
> 这推翻了本文档多处基于"预扣模式"的推断：
> - ❌ 原第 9 节末尾"上传的消费金额是 0 → 可能服务端拒绝零金额记录"的怀疑，方向错误
> - ❌ 原第 10 节"每洗一次漏一次账"的表述已被用户否认（实测余额 1.808→1.776 即服务端正常扣费）
> - ✅ 真实影响仅仅是：App 拿不到 `0x86` 写回数据，于是无法确认结算，界面卡在错误态
>
> **因此 226 上传失败的优先级大幅下降**。已按此完成一轮 UX 收尾（见第 11 节）。
> 单纯为了"修 226"去抓包/逆向，收益已经很低；除非维护者确实需要对账数据。
>
> 另外更正本文档原第 5 节：那批"8 组签名候选实测"**本身是无效测试**——
> `probeUploadSign` 里 `xfDataUpper` 只改了签名输入，POST 请求体被固定为小写，
> 因此必然返回 170（签名与请求体不一致）。**"大小写已排除"这个结论不成立**。
> 后续用"签名输入与请求体始终一致"的方式重测，得到：小写 226、大写 226、AES 密文 -2。


> 交接时间：2026-09-11
> 交接原因：已定位到失败点并排除大量可能，但**无法仅靠现有信息确定上传接口的签名口径**，原 AI 尝试的两条自主路径（逆向官方 APK、穷举字段组合）均已失败，需要换方法。

---

## 1. 项目与交付基线

| 项 | 值 |
|---|---|
| 仓库 | `D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev` |
| 分支 | `ui-apple-minimal` |
| 包名 | `com.funnyass.miuix` |
| 当前版本 | `versionCode = 1945` / `versionName = "0.1945"` |
| 桌面 APK | `C:\Users\L3356\Desktop\睿智校园-0.1945.apk`（已装到手机，登录态保留） |
| 手机 | OPPO PKJ110，adb 序列号 `629ca0e1`（**未 root**：`su` 不存在，`ro.debuggable=0`） |
| 官方 App | `com.klcxkj.zqxy`，versionName `6.5.28`（同接口版本） |

### 构建环境（必须）

```powershell
$env:JAVA_HOME='D:\AndroidDev\jdk25\jdk-25.0.4.1+1'
$env:ANDROID_HOME='D:\AndroidDev\sdk'
$env:ANDROID_USER_HOME='D:\AndroidDev\android-home'
$env:ANDROID_AVD_HOME='D:\AndroidDev\avd'
$env:GRADLE_USER_HOME='D:\AndroidDev\gradle-home'
$env:GRADLE_OPTS='-Dorg.gradle.java.installations.auto-download=false'
& 'D:\AndroidDev\gradle-9.5.0\gradle-9.5.0\bin\gradle.bat' -p '<仓库路径>' :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

---

## 2. 问题现象（用户原话）

> 「关水结算的时候他跳转不了」
> 「不是，他一直显示关闭阀门，然后加载不出来，不会跳转」
> 「报错了，显示，结算中断，消费数据仍在设备中」
> 「结算失败了」

**实际链路**：设备关阀成功 → 采集消费数据成功 → **上传消费记录到服务端失败** → 拿不到 `0x86` 写回数据 → 永远进不了「已完成」。

---

## 3. 已定位的失败点（有真机日志硬证据）

真机日志（`adb logcat -s QZXY:D`，0.1942 版）：

```
23:44:45.953 设备已确认关阀
23:44:45.954 关阀已确认，发送采集 0x85
23:44:46.275 回帧 cmd=0x85 payload=80260911234442000002B8000000E30000637C...A10DB55D4F
23:44:46.275 采集记录 accountId=<已脱敏> 金额(分)=0
23:44:46.276 上传消费 xfData=<已脱敏：真实消费记录十六进制>
23:44:46.278 upload sign input=loginCode<已脱敏>telephone<已脱敏>xfData<已脱敏>
23:44:46.278 upload signature=6ab76cdb1581486a868c58b430f556dc
23:44:46.385 上传失败: 226 加密校验失败 data=null
```

**结论：设备侧（蓝牙）完全正常，失败 100% 在服务端签名校验。**

对照：**同一会话下 `rateOrder`（下费率）的签名被服务端接受**：

```
23:44:41.797 rate sign input=deviceId227randomNumber<已脱敏>telephone<已脱敏>xfModel0
23:44:41.797 rate signature=a4613faba039743d64ceb837de1a9c45
23:44:41.976 下发费率 0x21（已保存预扣回滚凭据）   ← 服务端接受了
```

所以：**native 签名库本身没问题，`loginCode` 本身有效，失败只出在「上传接口该签哪些字段/怎么签」上。**

---

## 4. 两个接口的签名现状（关键差异）

代码位置：`app/src/main/java/com/funnyass/test/net/Api.kt`

### rateOrder（可用）

```kotlin
val signParams = mapOf(
    "telephone" to user.telephone,
    "deviceId" to device.devID.toString(),
    "xfModel" to xfModel,
    "randomNumber" to rn          // 注意：小写化后的
)
val signature = Crypto.signNative(ApiClient.authCode(user), signParams)
```
body 另含 `macAddress/macType/bigTypeId/smallTypeId/protocolType/randomNumber/xfModel` 等，**但签名只覆盖上述 4 个字段**。

### uploadBluetoothData（恒定被拒）

```kotlin
val signParams = mapOf(
    "loginCode" to ApiClient.authCode(user),
    "telephone" to user.telephone,
    "xfData" to data              // data = xfData.lowercase()
)
val signature = Crypto.signNative(ApiClient.authCode(user), signParams)
```
body：`protocolType/randomNumber/xfData/signature` + `ApiClient.baseParams()`（`projectId/accountId/userId/telephone/telPhone/loginCode/phoneSystem/version`）。

### 签名函数实现

`app/src/main/java/com/funnyass/test/util/Crypto.kt`：

```kotlin
fun signNative(loginCode: String, params: Map<String, String?>): String {
    val sb = StringBuilder()
    params.keys.sorted().forEach { k ->      // key 升序
        sb.append(k)
        params[k]?.let { sb.append(it) }     // 直接拼接 key+value，无分隔符
    }
    return JniUtils.signParams(loginCode, sb.toString())
}
```

`JniUtils.signParams(sessionKey, raw)` → `libklcxkjencry.so` 的
`Java_com_klcxkj_jni_JniUtils_signParams`。

---

## 5. 已做的实测（**请勿重复**）

### 5.1 上传签名候选批量测试 —— 8 种全部被拒

用真机 + 本地命令端口一次性测完（见第 7 节的方法），服务端返回统一为
`code=170 msg=签名错误,请更新版本`：

| 候选 | 字段组合 | 结果 |
|---|---|---|
| xfDataUpper | loginCode+telephone+xfData(大写) | ❌ 170 |
| xfDataLower+rnUpper | +randomNumber(大写) | ❌ 170 |
| xfDataUpper+rnUpper | 两者大写 | ❌ 170 |
| code+tel+xfData+deviceId | 补 deviceId | ❌ 170 |
| code+tel+xfData+accountId | 补 accountId | ❌ 170 |
| code+tel+xfData+upMoney | 补 upMoney | ❌ 170 |
| bodyNoSignature | loginCode+telephone+telPhone+protocolType+randomNumber+xfData+userId+accountId | ❌ 170 |
| md5Sign | 改用 `Crypto.signMd5`（第一套签名） | ❌ 170 |

另外在修复前的版本里，`loginCode+telephone+xfData(小写)` 与 `+randomNumber` 也试过，
分别返回 `226 加密校验失败`。

**结论：字段名、大小写、字段集合这三个方向已被系统性排除。**

### 5.2 官方 APK 逆向 —— 失败（已被加固）

- 官方 APK 193MB，`classes.dex` 33.1MB
- dex 头部合法、file_size 一致，但 **`class_defs_size = 30`**（只有 30 个类）
  → 业务代码不在 Java 层，dex 是易盾加固壳（含 `libAPSE_8.0.0.so`、`libsecuritydevice.so` 等易盾组件）
- jadx 只解出 31 个文件（全是易盾 SDK），字符串搜索 `upload/bluetooth`、`xfData`、`signParams` 命中 0
- **不要在这条路上继续投入**

---

## 6. ⭐ 最有价值的未用线索：native 库里还有没被调用的签名函数

原 AI 在收尾时反查了 `libklcxkjencry.so`（5.29MB，官方与本项目用的是**同一个库**），
发现 Java 侧 `JniUtils.java` **只声明了 7 个方法，但库里导出了 10 个 JNI 函数**：

```
Java_com_klcxkj_jni_JniUtils_signParams          ← 一直在用（rate 可用）
Java_com_klcxkj_jni_JniUtils_checkSignature      ← ⚠️ 未声明、未使用
Java_com_klcxkj_jni_JniUtils_getSk / getBk / getCk / getDk / getMk
Java_com_klcxkj_jni_JniUtils_encryptByAES / decryptByAES
Java_com_encryption_aes_EncryptionUtils_decryptByAES
```

库内相关字符串：

```
&key=sign-kailu-855c74a88b8b494187c99b08b8c9a744
&key=
sign-kailu=
signature_first
```

**推断**：签名算法在 native 层，且**按业务动作（如 `sign-kailu` 开阀）使用不同密钥/口径**。
`checkSignature` 很可能是另一套签名（或校验）入口，也许正是上传接口要用的。

**建议下一步**：反汇编这个 `.so` 读 `signParams` 与 `checkSignature` 的实现，确认：
1. `raw` 的规范化要求（是否要分隔符、是否要求特定拼接顺序）
2. 上传接口是否该用 `checkSignature` 而非 `signParams`
3. `&key=sign-kailu-...` 在什么调用路径下拼接、是否有对应的 `sign-upload` 类密钥

工具：本机有 Python 3.12（`C:\Users\L3356\AppData\Local\Programs\Python\Python312\python.exe`）、
GitHub 可达；`pip install capstone pyelftools` 即可开始（原 AI 正要装时被叫停）。
本机**没有** NDK / llvm-objdump / objdump / radare2 / ghidra。

---

## 7. 项目自带的诊断基础设施（可直接复用）

### 7.1 本地命令端口（不用连设备也能触发上传/签名测试）

`app/src/main/java/com/funnyass/test/CmdServer.kt`：应用内起 HTTP 服务监听 `127.0.0.1:8080`。

```powershell
$adb='D:\AndroidDev\sdk\platform-tools\adb.exe'
& $adb -s 629ca0e1 forward tcp:8080 tcp:8080
# 读日志 / 清日志 / 看状态
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/log'
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/clear'
Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:8080/status'
# 触发上传（用日志里抓到的 xfData/random 即可复现，无需重跑整个洗澡流程）
$data='80260911234442000002B8000000E30000637C0200000002000007100000000000000020C47F0E458E04000000000000000400000000000000A10DB55D4F'
Add-Type -AssemblyName System.Web
Invoke-WebRequest -UseBasicParsing ('http://127.0.0.1:8080/cmd?action=upload&random=f9030000&data=' + [System.Web.HttpUtility]::UrlEncode($data))
```

⚠️ 注意：命令参数走 **query string**（不是 POST body），否则 action 为空。

### 7.2 已有的诊断动作（原 AI 本轮新增）

| action | 作用 |
|---|---|
| `proberate` | 用当前会话跑一次真实 rateOrder，验证 loginCode/会话是否仍有效 |
| `probeupload` | **批量试上传签名候选**，参数 `random=`、`data=`；命中会打印 `probeUpload 命中=<候选名>` |

`probeupload` 的候选表维护在 `BathActivity.probeUploadSign()`，加候选只需改这一处。

### 7.3 已知可复现的测试数据

- 设备 MAC：`<已脱敏>`
- `protocolType=20`，`randomNumber=f9030000`（来自 0x23 应答 C1 字段）
- 账号：`accountId=<已脱敏>`，`loginCode=<已脱敏>`
- `xfData`（0x85 采集回帧完整 payload，124 hex 字符）：
  `80260911234442000002B8000000E30000637C0200000002000007100000000000000020C47F0E458E04000000000000000400000000000000A10DB55D4F`

⚠️ `randomNumber`/`xfData` 是**会话相关**的：重连设备后 `randomNumber` 会变。
用 `/cmd?action=query` 或重新连设备可刷新。

---

## 8. 替代取证方案（原 AI 判断「不能直接抓包」，请复核）

原 AI 的结论与理由：

- 手机**未 root** → 无法安装系统级 CA、无法绕过 SSL Pinning
- Android 7+ 起 App 默认不信任用户安装的 CA → 抓官方 App 的 HTTPS 需要
  **代理 + 用户 CA**，或 root
- 而且那是**官方 App 自己的流量**，无法由原 AI 侧决定走代理

**但以下方案尚未尝试，建议评估：**

1. **`adb shell settings put global http_proxy <PC_IP>:8888`** 强制全机代理 +
   PC 端 mitmproxy/Charles。若官方 App 未做 SSL Pinning，**这条路能直接抓到明文请求**。
   （风险：若做了 Pinning 会看到 `Client SSL handshake failed`，此时放弃即可）
2. **让用户装一次可信任证书**（Android 需在「设置 → 安全 → 加密与凭据 → 安装证书 → CA 证书」；
   ColorOS 可能还要在应用侧再信任一次）
3. 找**未被加固的官方旧版本 APK**（旧版通常未上易盾），jadx 可直接读出上传请求构造代码
4. 读官方 App 的 `/data/data/com.klcxkj.zqxy/` 缓存/日志（**未 root 读不到私有目录**，
   `adb shell run-as` 仅对 debuggable 应用有效；官方 App 非 debuggable）
5. 用 Frida（需 root 或可调试包）hook `signParams`/`checkSignature`，观察官方 App 实际传入的
   `loginCode` 与 `raw`——这是**最直接**的证据

---

## 9. 代码现状（干净、可交付）

- 版本 `0.1945`，四处版本号已同步（`build.gradle.kts`、`BathActivity.APP_VERSION`、
  `bottom_sheet_settings.xml`、`BathUiSmokeTest.kt`）
- `:app:testDebugUnitTest` + `:app:assembleDebug` 通过，`git diff --check` 干净
- 桌面 APK 与构建产物哈希一致
- 本轮改动文件：
  - `app/src/main/java/com/funnyass/test/net/Api.kt`（补上传签名日志；曾短暂加入
    「5 候选轮发」后又撤回，现为干净的单次请求）
  - `app/src/main/java/com/funnyass/test/CmdServer.kt`（新增 `proberate` / `probeupload` 动作）
  - `app/src/main/java/com/funnyass/test/ui/BathActivity.kt`（新增两个诊断方法）

### 用户可见的改进（已生效，勿回退）

- 结算失败不再无限转圈：会显示「结算中断 / 消费数据仍在设备中 / 请点击下方重试」
- 下方按钮变「**重试结算**」（`BathActivity.canRetrySettlement()`，要求 `connected && deviceState == 3`）
- 但**重试仍会因为同一个签名问题失败** —— 这是本任务要解决的

### ⚠️ 需要留意：上传的消费金额是 0

日志里 `采集记录 accountId=<已脱敏> 金额(分)=0`。该次洗浴只持续约 4 秒
（下费率 23:44:41 → 关阀 23:44:45），所以金额为 0 可能是**真实结果**，
但也可能 `onCollectResp` 的金额解析（`FrameUtils.intAt(p, 28)`，BathActivity 第 ~1856 行）有偏差。
**建议先确认这一点**：若服务端拒绝的是「金额为 0 的消费记录」，
那本任务的签名方向可能整个是错的——用一次**时长较长**的洗浴复现即可区分。

---

## 10. 一句话总结给接手者

> 失败点已定位在「上传消费记录被服务端拒绝」（`226`）。**但按用户澄清的产品前提，计费由服务端
> 自动完成，这个失败不影响扣费，优先级已降低。** 已完成的收尾见第 11 节。
> 如果仍要根治 226，唯一可靠路径是抓一次官方 App 的成功请求（网络路径已验证可行，见第 11.3 节）。

---

## 11. 2026-09-12 本轮改动（0.1950 / 0.1951）

### 11.1 定位到的真实 UX 缺陷（已修）

`BathActivity.uploadData()` 的**三个失败分支都没有刷新余额**：
- 响应无效（原第 ~1905 行）
- 服务端返回非成功码（原第 ~1920 行）
- 网络异常（原第 ~1931 行）

后果：上传失败后界面余额**停留在用水中的 `0.000`**，用户以为钱没退，
必须切后台或重开应用才看到真实余额。这正是用户反馈「钱没回来」的原因。

另外 `justSettled = true`（进入「已完成」）**只在收到 `0x86` 写回回执时**设置（第 ~1637 行），
而 `0x86` 要等上传成功才发 → **上传失败就永远看不到「已完成」**。

**修复**：新增 `finishSettlementLocally(status)`，让上传失败也走"本次用水已结束"的收尾——
刷新余额、设置 `justSettled`、并按成功路径那样补发一次 `0x23` 刷新设备状态
（注意 `resolveGaugeState` 里 `deviceState == 3` 的优先级高于 `justSettled`）。

### 11.2 文案更正

| 位置 | 原文案 | 现文案 |
|---|---|---|
| ERROR 态仪表盘 | 「结算中断 / 消费数据仍在设备中 / 请点击下方重试」 | 「记录待同步 / 设备仍存有本次记录 / 重连后可重试」 |
| 上传失败状态栏 | 「已关阀，消费记录上传失败，请重连重试」 | 「已关阀，已结束本次用水」 |

原「消费数据仍在设备中」是**错误描述**——实测设备侧 `0x85` 记录读取一次后即被清空。

### 11.3 环境与网络（下次抓包可直接复用）

- 手机连的是**电脑开的移动热点**：手机 `192.168.137.167` ↔ 电脑 `192.168.137.1`（同子网，已实测可达）
- 电脑本机代理：`mitmdump --listen-host 0.0.0.0 --listen-port 8888`
- 手机设代理：`adb shell settings put global http_proxy 192.168.137.1:8888`
- **收尾必须撤销**：`adb shell settings delete global http_proxy`
- ⚠️ 设了系统代理后 OPPO 会弹「不安全代理」警告并可能拦截官方 App，务必及时撤销
- 证书需装「CA 证书」类型：设置→安全→更多安全设置→凭据存储→从存储设备安装证书→CA 证书→仍然安装→选文件→**指纹/密码验证（必须用户本人操作）**

### 11.4 仍未修复（明确保留）

1. **226 上传失败本身**——根因未定，需要官方 App 的成功请求样本
2. **「重试结算」在真实场景下不会成功**——它重新去设备读记录，而设备记录读一次即被清空。
   正确做法是缓存首次采集的原始 payload 用于重传。等 226 定位后再一并做，否则只是重发同样的错误数据。

### 11.5 明确不要动的部分

`rateOrder` / `0x21` / `0x22` / `0x85` / `0x86` 属于**设备通信链路**，与计费模型无关，
拿掉可能导致无法开阀。除非有真机验证，不要为了"去掉预扣逻辑"而删改这些。

---

## 12. 2026-09-12 余额小数位（0.1952）

**需求**：余额的小数位要与官方客户端一致 = **原样显示服务端返回的精度**，不自行四舍五入。

**改动**：`BathActivity.walletDisplay()` 原来统一 `String.format("%.2f")`
（`1.776 → ¥1.78`），已改为原样返回。所有余额显示点（主界面元信息、两个统计卡、
`renderSettings()` 设置页）都走这一个函数，因此改一处即全部生效。
`loadWallet()` 里本来就保留服务端原值（`"¥$moneyValue"`），未改动。

**实测**：服务端返回 `1.744` → 界面显示 `¥1.744`（此前会显示 `¥1.74`）。

**未改动**：`money(fen)` 仍为两位小数——它只用于「本次消费」这类由"分"换算的金额，
不涉及服务端精度。`walletYuan()` 的数值解析对多位小数正常。



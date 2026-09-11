# 睿智校园

[![License: MPL 2.0](https://img.shields.io/badge/License-MPL%202.0-brightgreen.svg)](https://www.mozilla.org/MPL/2.0/)
![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)
![Status: Testing](https://img.shields.io/badge/status-testing-orange.svg)

> 先向大家道歉：这个项目全部都是 **Vibe Coding**。因为我自己的编程能力实在有限，代码质量和实现方式可能不够成熟，欢迎大家提交 Issue、帮助测试和改进。

“睿智校园”是“趣智校园”的第三方精简版，一个面向校园公寓淋浴场景的精简 Android 客户端，目标是保留登录、余额查询和蓝牙淋浴控制等常用功能，减少与核心使用流程无关的界面干扰。

## 界面预览

<p align="center">
  <img src="docs/screenshots/dashboard.jpg" alt="洗澡仪表盘" width="300">
</p>

<p align="center"><sub>洗澡仪表盘：余额、设备连接状态、协议与水阀状态、开始洗澡</sub></p>

> 截图中的手机号已做模糊处理。

## AI 辅助开发说明

- 分析与构建阶段使用 **GPT-5.6 Sol**。
- 逆向与重写阶段使用 **DeepSeek V4 Pro**。
- 项目的代码、文档和测试流程均包含 AI 辅助生成内容，尚未经过充分的人工代码审计。

## 当前状态

当前版本为 **1.0.0**（完整改动记录见 [`docs/CHANGELOG.md`](docs/CHANGELOG.md)）。项目仍在测试中，可能存在不稳定、设备不兼容、接口变更或异常计费等风险。请先小范围测试，并在确认设备状态后再开始使用。

### 已实现

- 手机号 **验证码或密码** 登录，登录态本地持久化。
- 查询校园账户余额，**小数位与服务端返回精度一致**（不做四舍五入）。
- 扫描并连接附近的 `KLCXKJ-Water` 蓝牙水控设备；**记住上次设备并在启动后自动重连**。
- 开始供水、停止供水；关阀后自动尝试上传消费记录。
- 界面展示设备真实状态（空闲 / 使用中 / 不可中断 / 有遗留数据），避免误操作他人设备。
- **使用记录**：每次用水结束后记录时间、时长与花费（花费取服务端余额差值）。
- **全屏诊断日志**：便于反馈问题时导出排查信息。
- **检查更新**：从 GitHub Releases 自动比对最新版本，发现新版本可一键跳转下载。
- 登录凭据失效检测与手动重新登录。
- Apple / MIUIX 风格精简界面，**无广告模块**。

### 暂未提供

- 充值功能。
- 微信小程序版本。

## 计费说明（重要）

本客户端**不参与计费**，只负责三件事：**发送开闸/关闸指令、读取余额、展示状态**。

扣费由趣智校园**服务端按实际用水量自动完成**，客户端不存在预扣模型。
因此客户端上传消费记录失败（服务端返回加密校验类错误）**不影响实际扣费**，
只会让本次记录少一条对账数据；关阀后余额仍以服务端为准。

## 使用方法

1. 从 [GitHub Releases](https://github.com/LVSUGARS/Funnyass_school-sugars-/releases) 下载并安装最新 APK。
2. 打开“睿智校园”，使用校园账户登录。
3. 按系统提示授予蓝牙或“附近的设备”权限，并确保蓝牙已开启。
4. 进入「蓝牙设备」，点击 **重新扫描附近设备**，在列表中找到目标水控器；
   可结合房间名、编号与信号强度确认，**不要操作不属于自己的设备**。
5. 点选设备完成连接。设备显示 **空闲** 后，点击「开始洗澡」。
6. 使用结束后点击「停止并结算」。设备会先关阀，随后尝试上传消费记录；
   即使记录同步失败，本次用水也已结束，余额会在关阀后刷新。
7. 下次打开 App 会自动重连上次使用的设备；如需更换或放弃，
   在设备列表里选择其他设备（会自动切换）或点「断开连接」。

## 构建

建议使用 Android Studio 打开项目，并安装项目所需的 Android SDK。

Windows：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

macOS / Linux：

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

APK 默认输出到：

```text
app/build/outputs/apk/debug/睿智校园-<版本号>.apk
```

最低支持 Android 7.0（API 24）。当前 APK 仅包含 `arm64-v8a` 原生库，其他 CPU 架构可能无法正常运行。

### 发布新版本

发布前请同步版本号（**四处**，缺一会出现界面与包内版本不一致）：

1. `app/build.gradle.kts` 的 `versionCode`（整数，递增）与 `versionName`
2. `BathActivity.APP_VERSION`
3. `res/layout/bottom_sheet_settings.xml` 的「当前版本」
4. `androidTest/.../BathUiSmokeTest.kt` 的版本断言

然后在 GitHub 打 tag 并上传 APK：

```bash
gh release create v1.0.0 "app/build/outputs/apk/debug/睿智校园-1.0.0.apk" \
  --repo LVSUGARS/Funnyass_school-sugars- --title "v1.0.0" --notes "更新说明"
```

App 内「检查更新」读取的是 `releases/latest` 的 `tag_name`，
因此 **tag 必须写成 `v1.0.0` 这种形式**（可带 `v` 前缀），并且要把 `.apk` 作为 Release 资源上传。

## 项目结构

```text
app/src/main/java/com/funnyass/test/
├── ui/          BathActivity（主界面/仪表盘/设备列表/设置与子页面）、LoginActivity
├── ble/         BleManager（扫描、连接、收发帧、RSSI）
├── net/         Api / ApiClient（接口与签名）
├── store/       Session（登录态）、UsageLog（使用记录）
├── update/      UpdateChecker（GitHub Releases 版本比对）
└── util/        FrameUtils（协议组帧/解析）、Crypto、Logger
```

调试用命令端口（debug 包自动开启，监听 `127.0.0.1:8080`）：

```bash
adb forward tcp:8080 tcp:8080
curl "http://127.0.0.1:8080/log"          # 读日志
curl "http://127.0.0.1:8080/cmd?action=connect&mac=<MAC>"
```

## 隐私与安全

- 请勿在 Issue、日志或提交记录中上传手机号、验证码、密码、登录令牌、账户编号、设备 MAC 或消费订单数据。
- 不要在无法确认设备归属时尝试开阀或关阀。
- 若页面提示登录失效，请重新登录；正在供水时应优先完成关阀。
- 本项目涉及真实账户余额和设备控制，请不要把测试版本当作完全可靠的生产软件。

## 非官方声明

本项目为非官方社区项目，与趣智校园、相关运营方、学校及设备厂商不存在隶属、合作或授权关系。“趣智校园”“KLCXKJ”及其他名称、商标和服务归其各自权利人所有。官方接口和设备协议可能随时发生变化，本项目不保证持续可用。

请仅在法律、学校规定和服务条款允许的范围内，将本项目用于本人有权使用的账户与设备。因使用、修改或分发本项目造成的账户、费用、设备或其他损失，由使用者自行承担。

如果任何权利人或相关方认为本项目中的代码、二进制文件、图片、名称或其他内容存在版权、商标、许可或其他权利问题，请通过本仓库的 GitHub Issue 及时联系。收到通知后，我会第一时间核查，并删除或替换存在问题的相关内容。

## 开源协议

本项目中由项目作者享有著作权并有权许可的原创代码，以 [Mozilla Public License 2.0](LICENSE) 发布。

MPL-2.0 采用文件级 Copyleft：对 MPL 源文件的修改仍需按照 MPL-2.0 提供源码，但允许这些文件与采用其他许可证的独立文件组成更大的作品。它不会自动改变第三方组件、商标、素材、服务接口或二进制文件原有的权利归属。公开分发前，请确认仓库内所有第三方内容均具有兼容的分发授权；无法确认时，应移除或以可合法分发的实现替换。

### 发布前特别注意

当前工程包含下列需要发布者自行确认分发权利的文件：

- `app/src/main/jniLibs/arm64-v8a/libklcxkjencry.so`：用于设备协议的原生二进制库。
- `app/src/main/res/drawable-nodpi/ic_launcher_ruizhi_no_ads.png`：基于原应用图标制作的衍生图标。

在取得明确授权或完成可合法分发的替换实现之前，不建议将上述文件随公开源码或 APK 一并发布。仓库使用 MPL-2.0 并不代表这些第三方内容自动获得 MPL-2.0 授权。

## 参与贡献

欢迎提交 Issue 和 Pull Request，包括但不限于：

- 不同学校、宿舍和水控设备的兼容性反馈。
- 蓝牙稳定性、计费安全和异常恢复改进。
- 登录失效、界面和无障碍体验优化。
- 充值及微信小程序适配。

提交日志时请务必先删除所有个人信息和服务端凭据。

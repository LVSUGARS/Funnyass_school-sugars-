# 睿智校园 · 第 2 阶段 Apple UI 真机验证脚本
#
# 用法：
#   pwsh -File verify-device.ps1
#
# 前置：手机用 USB 连接并已授权 adb 调试（adb devices 能看到设备）。
# 本脚本只做「保留数据安装 → 启动 → 截图 → 抓崩溃日志」，
# 绝不会点击"开始洗澡"，不会触发开阀 / 扣费。
# 不会清除应用数据或登录态；若当前进入登录页，则不会保存可能包含账号信息的截图。
#
# 安全边界：只通过 adb input tap 点击【登录页输入框】和【设置齿轮 / 蓝牙设备入口】这类纯 UI 控件；
# 不输入手机号、不点"获取验证码"、不点任何登录提交按钮。

param(
    [string]$Serial = "",
    # 留空则自动取构建目录里最新的 APK，避免版本升级后这里的硬编码路径失效
    [string]$Apk = "",
    [string]$OutDir = "D:\Documents\DeepSeek\Github\Funnyass_school-sugars-dev\docs\verify-shots"
)

$ErrorActionPreference = 'Stop'
$adb = 'D:\AndroidDev\sdk\platform-tools\adb.exe'
$pkg = 'com.funnyass.miuix'

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
    if ($Serial) { & $adb -s $Serial @Args } else { & $adb @Args }
}

Write-Host '=== 0. 设备检查 ===' -ForegroundColor Cyan
$devices = & $adb devices | Select-String -Pattern '^\S+\s+device$'
if (-not $devices) {
    Write-Host '未检测到已授权设备。请连接手机后重跑。' -ForegroundColor Red
    & $adb devices -l
    exit 2
}
if (-not $Serial) {
    $Serial = ($devices[0] -split '\s+')[0]
}
Write-Host "使用设备: $Serial"
Invoke-Adb logcat -c
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Save-Shot {
    param([string]$Name)
    Invoke-Adb exec-out screencap -p > (Join-Path $OutDir "$Name.png")
    Write-Host "  截图 -> $Name.png"
}

function Get-DarkMode {
    (Invoke-Adb shell cmd uimode night) -join ''
}

function Set-DarkMode {
    param([string]$Mode)  # yes / no
    Invoke-Adb shell cmd uimode night $Mode | Out-Null
    Start-Sleep -Seconds 2
}

Write-Host '=== 1. 安装 APK ===' -ForegroundColor Cyan
if ([string]::IsNullOrWhiteSpace($Apk)) {
    # 自动取构建目录里最新的 APK（按修改时间），避免版本号变化后硬编码失效
    $apkDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'app\build\outputs\apk\debug'
    $latest = Get-ChildItem -LiteralPath $apkDir -Filter '睿智校园-*.apk' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($null -eq $latest) { Write-Host "未在 $apkDir 找到 APK，请先构建或显式指定 -Apk" -ForegroundColor Red; exit 3 }
    $Apk = $latest.FullName
    Write-Host "自动选用最新 APK: $(Split-Path -Leaf $Apk)" -ForegroundColor DarkGray
}
if (-not (Test-Path $Apk)) { Write-Host "APK 不存在: $Apk" -ForegroundColor Red; exit 3 }
Invoke-Adb install -r -d $Apk | Write-Host

Write-Host '=== 2. 保留登录态并启动 ===' -ForegroundColor Cyan
Invoke-Adb shell am start -n "$pkg/com.funnyass.test.MainActivity" | Write-Host
Start-Sleep -Seconds 4
$focusedActivity = (Invoke-Adb shell dumpsys activity activities | Select-String -Pattern 'topResumedActivity|mResumedActivity' | Select-Object -First 1) -join ''
$isLoginPage = $focusedActivity -match 'LoginActivity'

if ($isLoginPage) {
    Write-Host '当前没有可用登录会话，已进入登录页；为避免保存账号信息，本次跳过截图。' -ForegroundColor Yellow
} else {
    Write-Host '=== 3. 浅色 · 当前页面 ===' -ForegroundColor Cyan
    Set-DarkMode 'no'
    Start-Sleep -Seconds 1
    Save-Shot '01-current-light'

    Write-Host '=== 4. 深色 · 当前页面 ===' -ForegroundColor Cyan
    Set-DarkMode 'yes'
    Save-Shot '02-current-dark'
    Set-DarkMode 'no'
}

Write-Host '=== 5. 崩溃日志检查（到目前为此）===' -ForegroundColor Cyan
$crash = Invoke-Adb logcat -d -s AndroidRuntime:E | Out-String
if ($crash -match 'FATAL EXCEPTION') {
    Write-Host '检测到崩溃：' -ForegroundColor Red
    Write-Host $crash
} else {
    Write-Host '  无 FATAL EXCEPTION' -ForegroundColor Green
}

Write-Host '=== 6. 业务日志（QZXY）===' -ForegroundColor Cyan
Invoke-Adb logcat -d -s QZXY | Select-Object -Last 40

Write-Host ''
Write-Host '=== 说明 ===' -ForegroundColor Yellow
Write-Host '洗澡页 / 设备弹窗 / 设置页需要已登录会话，脚本不会清数据或代为登录。'
Write-Host '请在手机上手动登录（脚本不触碰登录提交），然后：'
Write-Host '  a) 截图洗澡页浅色/深色'
Write-Host '  b) 点右上角 ⚙ 打开设置页并截图'
Write-Host '  c) 点"蓝牙设备"卡片打开设备弹窗并截图（会触发扫描，属正常只读行为）'
Write-Host '  d) 再次运行下面的命令检查是否崩溃：'
Write-Host "     & '$adb' -s $Serial logcat -d -s AndroidRuntime:E QZXY"
Write-Host ''
Write-Host "截图目录: $OutDir"

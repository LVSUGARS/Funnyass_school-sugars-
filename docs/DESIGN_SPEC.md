# 睿智校园 · UI 设计规范 v1.0

> 适用范围：`com.funnyass.miuix`  
> 风格：MIUI 清爽风 + Material 3 规范  
> 状态：基于现有 XML 布局审查后的 P0 改造规范

## 0. 设计原则

1. 洗澡优先：首屏核心是设备状态 + 开始 / 停止。
2. 高对比可读：正文对比度 ≥ 4.5:1。
3. 少干扰：调试信息默认隐藏。
4. 单手可达：主操作固定底部，触控 ≥ 48dp，主按钮 56dp。
5. 全量 token 化：颜色、间距、圆角、字体不再散落硬编码。

## 1. 颜色系统

### 1.1 浅色主题

| Token | 色值 | 用途 | 对比度 |
|---|---|---|---|
| colorPrimary | #1F5FD1 | 主按钮背景 | 白字 5.81:1 |
| colorOnPrimary | #FFFFFF | 主按钮文字 | 5.81:1 |
| colorPrimaryContainer | #EAF2FF | 次按钮 / 标签底 | - |
| colorOnPrimaryContainer | #1F5FD1 | 浅底蓝字 | 5.16:1 |
| colorBackground | #F5F6F8 | 页面背景 | - |
| colorSurface | #FFFFFF | 卡片 | - |
| colorOnSurface | #1F2329 | 主文字 | 15.09:1 |
| colorOnSurfaceVariant | #5F6673 | 次级文字 | 5.78:1 |
| colorOnSurfaceTertiary | #667085 | 辅助文字 | 4.60:1 |
| colorOutline | #E7E9EE | 描边 / 分割线 | - |
| colorSuccess | #0A7D43 | 成功 / 使用中 | 对白 5.21:1 |
| colorSuccessContainer | #E9F8F0 | 成功底 | - |
| colorWarning | #B54708 | 警告 / 重试 | 对白 5.43:1 |
| colorWarningContainer | #FFF3E8 | 警告底 | - |
| colorDanger | #B42318 | 错误 / 停止 | 对白 6.57:1 |
| colorDangerContainer | #FEE4E2 | 危险底 | - |

### 1.2 暗色主题

| Token | 色值 | 用途 | 对比度 |
|---|---|---|---|
| colorPrimary | #9CC2FF | 主按钮背景 / 重点文字 | 对容器 6.39:1 |
| colorOnPrimary | #0B1B3A | 主按钮文字 | 9.39:1 |
| colorPrimaryContainer | #1C376C | 次按钮 / 标签底 | - |
| colorOnPrimaryContainer | #9CC2FF | 浅底蓝字 | 6.39:1 |
| colorBackground | #17191D | 页面背景 | - |
| colorSurface | #24272D | 卡片 | - |
| colorOnSurface | #F4F5F7 | 主文字 | 13.72:1 |
| colorOnSurfaceVariant | #A7AFBB | 次级文字 | 6.77:1 |
| colorOnSurfaceTertiary | #7F8895 | 辅助文字 | 4.91:1 |
| colorOutline | #353A43 | 描边 | - |
| colorSuccess | #3DDC97 | 成功 | 对成功底 6.83:1 |
| colorSuccessContainer | #173D2C | 成功底 | - |
| colorWarning | #FDB022 | 警告 | 对警告底 6.46:1 |
| colorWarningContainer | #4A321D | 警告底 | - |
| colorDanger | #FDA29B | 危险 | 对危险底 7.29:1 |
| colorDangerContainer | #4A1D1D | 危险底 | - |

### 1.3 使用规则

- 主按钮：`colorPrimary` + `colorOnPrimary`
- 次按钮：`colorPrimaryContainer` + `colorOnPrimaryContainer`
- 危险：`colorDanger` / `colorDangerContainer`
- 状态：成功 / 警告 / 危险必须有语义色
- 禁用：alpha 0.38，同时保持文字可读
- 不要用颜色作为唯一状态信息，必须配文字

## 2. 字体系统

字族统一 `sans-serif`，日志和 MAC 用 `monospace`。

| 名称 | 字号 | 行高 | 字重 | 用途 |
|---|---|---|---|---|
| Display | 30sp | 38sp | Bold | 页面标题 |
| Title | 24sp | 32sp | Bold | 金额 / 大标题 |
| Headline | 20sp | 28sp | SemiBold | 卡片标题 |
| Subtitle | 17sp | 24sp | SemiBold | 设备名 / 区块标题 |
| BodyLarge | 16sp | 24sp | Medium | 主按钮 |
| Body | 14sp | 22sp | Regular | 正文 |
| Label | 13sp | 18sp | Medium | 按钮 / 标签 |
| Caption | 12sp | 16sp | Regular | 辅助说明 |
| Mono | 12sp | 18sp | Regular | 日志 / MAC |

规则：正文最小 14sp，辅助信息最小 12sp，不再用 11sp 作为可读信息。

## 3. 间距系统

4dp 网格。

| Token | 值 | 用途 |
|---|---|---|
| space1 | 4dp | 微调 |
| space2 | 8dp | 小间距 |
| space3 | 12dp | 按钮 / 标签 |
| space4 | 16dp | 卡片内 / 表单项 |
| space5 | 20dp | 卡片内大间距 |
| space6 | 24dp | 页面边距 |
| space7 | 32dp | 区块之间 |

页面左右 24dp，卡片之间 16dp，卡片内边距 20dp。

## 4. 圆角与描边

| 组件 | 圆角 |
|---|---|
| 输入框 | 12dp |
| 按钮 | 14dp |
| 卡片 | 20dp |
| 状态标签 | 999dp |
| 弹窗 | 28dp |

描边统一 1dp `colorOutline`，卡片默认 elevation 0。

## 5. 触控与无障碍

- 可点击控件最小 48 × 48dp
- 主按钮高 56dp，次按钮高 48dp
- 文本对比度 ≥ 4.5:1
- 支持系统字体放大，不硬裁文字
- 状态变化加 accessibilityLiveRegion
- 图标按钮必须有 contentDescription
- 输入框用 TextInputLayout 常驻 label

## 6. 组件规范

### 6.1 Button

- Primary：高 56dp，背景 colorPrimary，文字 colorOnPrimary，圆角 14dp，全宽
- Secondary：高 48dp，背景 colorPrimaryContainer，文字 colorOnPrimaryContainer
- Danger：高 56dp，背景 colorDanger，用于停止 / 危险操作
- Text：高 48dp，无背景，用于刷新 / 重新登录
- 禁用：alpha 0.38，不要只变浅色

### 6.2 TextField

- `TextInputLayout` + OutlinedBox
- 高 56dp，常驻 label
- 错误态用 colorDanger
- 密码框带显示 / 隐藏
- 正确设置 imeOptions
- 键盘弹出时按钮不被遮挡

### 6.3 Card

- 背景 colorSurface
- 圆角 20dp
- 描边 1dp colorOutline
- elevation 0
- 内边距 20dp

### 6.4 StatusChip

- 高 28-32dp，圆角 999dp，文字 12sp
- 成功 / 警告 / 危险 / 中性四种语义
- 颜色 + 文字同时表达状态

### 6.5 DeviceListItem

结构建议：

```text
[设备图标] 房间名 / 设备名
           KLCXKJ-Water · -62 dBm
           AA:BB:CC:DD:EE:FF
```

- 最小高度 72dp
- 主标题 16sp，副标题 13sp，MAC 12sp monospace
- 点击先选中，再确认连接，避免误触
- 已识别设备置顶，按 RSSI 排序

### 6.6 日志面板

- 只放开发者 / 调试入口
- 12sp monospace
- 支持复制
- 限制最大行数，避免内存膨胀
- 普通用户默认隐藏

## 7. 页面规范

### 7.1 登录页

顺序：品牌标识 → 标题 / 副标题 → 登录卡片 → 手机号 → 验证码 + 获取验证码 → 验证码登录主按钮 → 或使用密码登录 → 密码 → 密码登录次按钮 → 隐私声明。

规则：

- 主按钮最大最醒目
- 获取验证码要有倒计时
- 输入框统一高度、label、错误态
- 键盘不挡按钮

### 7.2 洗澡页

推荐结构：

```text
[顶部 AppBar] 公寓洗澡 + 刷新图标

[账户摘要] 手机尾号 · 账户 ID · 余额（一行）

[设备卡片] 设备名称 / 房间
           连接状态 / 状态标签
           扫描 / 更换设备

[底部固定操作栏]
           [开始洗澡] 或 [停止并结算]
```

规则：

- 主操作固定底部，首屏可见
- 技术参数（协议、编号、MAC）次级或折叠
- 高级面板默认隐藏
- 运行日志移出主页面
- 连接成功后再展示设备详情

### 7.3 状态覆盖

| 状态 | 表现 |
|---|---|
| 加载中 | 按钮文字 + 进度指示 |
| 空状态 | 图标 + 一句话 + 重试 |
| 错误 | 红色错误条 + 重试入口 |
| 成功 | 绿色标签 + 余额刷新 |
| 危险 | 红色 / 橙色 + 二次确认 |

## 8. Android 资源映射

`colors.xml`：

```xml
<color name="miui_primary">#1F5FD1</color>
<color name="miui_on_primary">#FFFFFF</color>
<color name="miui_primary_container">#EAF2FF</color>
<color name="miui_on_primary_container">#1F5FD1</color>
<color name="miui_background">#F5F6F8</color>
<color name="miui_surface">#FFFFFF</color>
<color name="miui_on_surface">#1F2329</color>
<color name="miui_on_surface_variant">#5F6673</color>
<color name="miui_on_surface_tertiary">#667085</color>
<color name="miui_outline">#E7E9EE</color>
<color name="miui_success">#0A7D43</color>
<color name="miui_success_container">#E9F8F0</color>
<color name="miui_warning">#B54708</color>
<color name="miui_warning_container">#FFF3E8</color>
<color name="miui_danger">#B42318</color>
<color name="miui_danger_container">#FEE4E2</color>
```

`dimens.xml`：

```xml
<dimen name="space_1">4dp</dimen>
<dimen name="space_2">8dp</dimen>
<dimen name="space_3">12dp</dimen>
<dimen name="space_4">16dp</dimen>
<dimen name="space_5">20dp</dimen>
<dimen name="space_6">24dp</dimen>
<dimen name="space_7">32dp</dimen>
<dimen name="radius_input">12dp</dimen>
<dimen name="radius_button">14dp</dimen>
<dimen name="radius_card">20dp</dimen>
<dimen name="touch_min">48dp</dimen>
<dimen name="button_primary_height">56dp</dimen>
<dimen name="button_secondary_height">48dp</dimen>
```

## 9. P0 验收清单

- [ ] 所有正文 / 按钮对比度 ≥ 4.5:1
- [ ] 主操作首屏可见并固定底部
- [ ] 高级面板 / 日志默认隐藏
- [ ] 所有可点击控件 ≥ 48dp
- [ ] 品牌名统一
- [ ] 登录页键盘不挡按钮
- [ ] 暗色模式独立 token
- [ ] UI 文案进入 strings.xml
- [ ] 设备列表使用自定义 item
- [ ] 去掉嵌套滚动反模式

---

_本规范 v1.0，后续可直接映射到 colors.xml、dimens.xml、themes.xml、styles.xml 和布局文件。_
# 应用主题外观

<!-- feature-id: app-appearance -->
<!-- feature-title: 应用主题外观 -->
<!-- feature-aliases: themes, 换肤 -->
<!-- feature-status: active -->
<!-- feature-summary: 在设置页选择五套配色，并跨重启应用到全部账本页面和系统栏。 -->
<!-- last-verified: 2026-11-02 -->
<!-- code-basis: HEAD 8d2f173e8547; worktree-digest sha256:1d2ebaf4cfee15e5d383de512046acad66b3261b4b1bac58dd54648e97b305be -->

## 快速上下文

设置 → 换肤提供琥珀暖白、青竹、海盐蓝、雾紫和石墨深色五套配色，点击后立即生效。主题 key 只保存在应用私有 SharedPreferences；默认 amber 沿用旧版配色。换肤页使用跨页面一致的 56dp 居中标题栏。

主题色由 `CaijiPalette` 按当前主题选择，Material 3 色彩方案和 Activity 系统栏随选择更新。日期选择器按主题 key 使用相应的 Android dialog 样式。

## 目标与边界

### 目标

- 允许用户快速切换并持久保存外观配色。
- 在浅色及深色主题中保持文本、收入/支出和操作按钮可读。

### 范围内

- 设置页打开主题选择列表，提供五个固定命名及预览色点。
- 当前选中主题用勾号标识；点击其他主题立即重绘应用并写入 `appearance/theme`。
- 所有使用 `CaijiPalette` 的账本页面、卡片、对话框、收入/支出文字和导航/状态栏随主题切换。
- 金额、排序、历史数据及账单备份内容不随主题变化。

### 范围外

- 用户自定义颜色、动态壁纸取色、跟随系统深浅色和云端同步。
- 将主题 key 写入账本 Room 或加密账本备份。

### 不变量

- 仅接受 `CaijiThemeId` 中的五个稳定 key；偏好缺失或无效时回退到 amber。
- 语义收入、警示和正文颜色在五套主题中分别映射，选择主题不会改变数据值。

## 业务行为

- 设置页“换肤”行显示当前主题名；进入后展示结余卡预览和五项配色。
- 选项被点按后立即更新主题色、状态栏和导航栏，不需要额外确认按钮。
- Activity 启动时从偏好读取主题；系统日期选择器随选中的浅/深色样式和主色更新。
- 换肤期间若剪贴板发现银行通知，待主题页面结束后按账本记账功能现有规则处理。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/ui/theme/CaijiTheme.kt` — `CaijiThemeId`、五套 `CaijiColors`、`CaijiPalette.select` 和 Material 3 light/dark 色彩方案。
- `app/src/main/java/app/caiji/android/MainActivity.kt` — 启动恢复 `appearance/theme`，选择主题后持久化，并通过主题 Composition 更新应用状态栏和导航栏。
- `app/src/main/java/app/caiji/android/ui/SettingsScreen.kt` — 换肤入口；`ui/FeedbackScreens.kt` 的 `ThemePickerScreen` 展示即时预览与选择。
- `app/src/main/res/values/styles.xml` — 与五个主题色匹配的系统日期选择器样式。

## API、事件与任务

### `CaijiPalette.select(id: CaijiThemeId)`

- 应用内调用，将当前 palette state 切换到稳定主题 ID 对应的颜色集合。
- `CaijiRoot` 同步写入 SharedPreferences；Activity 再启动时恢复该值。

### `ThemePickerScreen`

- 消费选中主题、选择回调及返回回调；点选后立即调用主题变更流程，无网络事件或后台任务。

## 数据模型与迁移

- 只在 SharedPreferences `appearance` 文件中保存 string key `theme`，合法值为 `amber`、`bamboo`、`sea`、`mist`、`graphite`。
- 无 Room 字段、索引或迁移；现有账本加密备份格式 2 不包含主题偏好。

## 核心流程

1. `MainActivity.onCreate` 读取主题 key，无效时选 amber。
2. `CaijiTheme` 从 `CaijiPalette` 读取当前颜色构建 Material 3 色彩方案，并在 `SideEffect` 同步系统栏色和明暗图标模式。
3. 用户在换肤页选择一项；palette state 立即变化，主题 key 异步写入 SharedPreferences。
4. 记账日期选择器按当前主题 key 选择系统对话框资源样式。

## 异常与边界条件

- 偏好缺失/不在已知 key 列表 — 使用琥珀暖白，不阻止启动。
- 进程重启 — 由 SharedPreferences 恢复，不依赖账本数据库。
- 深色状态栏 — 同步浅色系统图标，深色背景下保证系统图标可辨。

## 并发与幂等

- 主题 key 是单值偏好，重复保存相同 key 是幂等操作；不与账单 Room 写事务耦合。

## 安全与权限

- 主题偏好仅保存在应用私有存储；无新增权限、网络请求或用户数据外发。

## 配置与依赖

- 使用现有 Compose Material 3 和 Android SharedPreferences；Pager/视觉外观不新增主题库。
- Android `minSdk=26`、`targetSdk=34`；对应 0.3.1/versionCode 8 发布。

## 可观测性与运维

- 无遥测或后台任务；无效 key 静默回到默认主题。
- 换肤偏好独立于账本格式 2 加密备份；卸载应用后会清除偏好。

## 测试与验证

### 已执行

- `CaijiThemeTest.fiveThemesHaveDistinctStableIdsAndChangingSelectionUpdatesPalette` 验证五个 key 对应不同主色，逐项选择即时替换 palette 并恢复默认。
- API34 `FeedbackAndThemeScreenTest.selectingAnyOfFiveThemesAppliesItsPaletteImmediately` 验证主题选择页点按行为；`MainActivityAppearanceTest.selectedThemeIsRestoredWhenActivityIsRecreated` 验证 Activity 重建后恢复偏好；`feedbackListEditorAndThemeTitlesShareTheCenteredHeader` 验证换肤标题与反馈页共用屏幕中心。90 项全量回归及 release 证据记于 Work Item。

### 未执行

- 未在 Android API 26 设备逐主题查看日期选择器；当前只有 API34 模拟器，系统样式兼容仍需实体设备回归。

## 关键决策

### 五套固定配色，本机即时切换

- **决定**：固定五个 key、以 SharedPreferences 持久保存；设置页点击立即应用，默认 amber。
- **原因**：回应用户指定的五套主题，并保持换肤独立于账本数据。
- **取舍**：颜色不能自定义或跨设备同步；深色主题不会自动跟随系统。
- **证据**：用户确认的高保真主题对比稿、`CaijiPalette` 和主题仪器测试。

## 已知问题与后续工作

### 旧系统实体设备样式

- **影响**：Android API 26 实际日期选择器样式未由当前模拟器验证。
- **当前处理**：API34 dark/light 样式渲染测试通过，资源使用原生 Android Dialog theme。
- **后续动作**：安装 0.3.1 后在用户 Android 8.0 设备核对系统对话框和状态栏对比度。

## 变更记录

### 2026-11-02 — 0.3.0 五套主题即时应用

- **状态**：已完成
- **变化**：增加 amber、bamboo、sea、mist、graphite 五套全局配色；偏好独立保存；系统栏及日期选择器跟随颜色模式。
- **原因**：按用户批准的高保真主题选择页增加外观自定义。
- **兼容性**：不修改 ledger Room schema 1 或账本备份格式 2。
- **验证**：79 项 API34、JVM、lint、APK metadata/v2 签名/zipalign/SHA-256 和 code6→7 覆盖升级通过；API26 日期 Dialog 未实跑。


### 2026-11-02 — 0.3.1 换肤顶部栏对齐

- **状态**：已完成
- **变化**：换肤页使用共用 56dp 居中标题栏。
- **原因**：按已确认高保真稿统一设置内功能页顶部样式。
- **兼容性**：主题 ID/SharedPreferences 与账本备份范围不变。
- **验证**：API34 测量换肤、反馈列表和编辑标题中心坐标；90 项仪器、JVM、lint 和 release 构建通过；0.3.0→0.3.1 覆盖升级保留青竹偏好。

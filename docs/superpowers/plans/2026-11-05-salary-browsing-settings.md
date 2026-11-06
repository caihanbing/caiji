# 工资浏览与设置入口实施计划

> **For agentic workers:** 执行此计划的每项任务并验证工资首页、设置导航和 SAF/XLSX 流程。

**Goal:** 发布工资按年/全部浏览和设置工资入口优化版本 0.5.1/code16。

**Architecture:** SalaryRootScreen 保留工资首页和编辑流程，并以起始目标支持从设置打开复用的导入、导出和收入项页面。MainActivity 维护设置来源、SAF 请求与过期结果门闩；Room 与 XLSX 数据层不变。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、Android Storage Access Framework。

**Spec:** `docs/superpowers/specs/2026-11-05-salary-browsing-settings-design.md`

## Global Constraints

- 不新建工作区或分支；在 `main` 完成并正常推送。
- 应用版本 `0.5.1`、versionCode `16`；沿用正式签名。
- 工资数据库、账本 schema 和备份格式不变；XLSX 继续使用现有 `Sheet1` codec。
- UI 改动限定在已批准高保真设计。

## Review Focus

- 当前年边界与箭头禁用、年份选择范围、空年份零统计。
- 全部模式跨年过滤和含年份日期，现金筛选不改变期间统计。
- 设置来源返回/系统返回和编辑草稿恢复。
- SAF 取消、重选、快速离页后旧解析结果不得覆盖新状态。
- 全部/指定年份导出应与首页浏览选择无关。

---

### Task 1: 工资总览期间筛选

**Files:** `SalaryScreens.kt`、`SalaryScreensTest.kt`

- [x] 为默认当年、年份切换、全部模式、空年份、筛选往返及统计范围添加失败仪器测试。
- [x] 验证测试因现有月/年/全部控件与模式语义不匹配而失败。
- [x] 移除工资标题、更多及月模式；实现按年/全部控件、年份箭头和年份选择（1900..当前年），全部右侧显示“全部记录”。
- [x] 全部列表日期含年份；按当前期间计算统计，并保留现金筛选只作用明细。
- [x] 运行 `./gradlew :app:connectedDebugAndroidTest`，AndroidJUnitRunner 可用 `-Pandroid.testInstrumentationRunnerArguments.class=...` 选择测试类和方法。

### Task 2: 设置工资管理入口与往返导航

**Files:** `MainActivity.kt`、`SalaryScreens.kt`、`SettingsScreen.kt`、相关设置和工资仪器测试

- [x] 测试设置中工资区三入口、顶部/系统返回、导入取消/成功、编辑草稿返回和失效的异步导入结果。
- [x] 验证测试因设置缺少入口且管理子页仅从工资首页可达而失败。
- [x] 设置在账本下方加入三个入口；经 MainActivity 设置来源打开复用工资管理页，返回时回设置并隐藏子页底栏；编辑器内收入项管理维持原有流程。
- [x] 将 SAF 导入结果有效性与请求失效规则覆盖设置工资路由；成功反馈回设置，取消留在页内。
- [x] 运行设置及工资仪器测试。

### Task 3: 独立导出范围和版本文档

**Files:** `SalaryScreens.kt`、`MainActivity.kt`、`app/build.gradle.kts`、`AGENTS.md` 及相关测试

- [x] 测试导出默认全部、指定年份、年份预选当年；与工资首页筛选无关。
- [x] 验证现有导出页只有当前期间/全部且测试失败。
- [x] 将导出页改为全部/指定年份；复用 SAF 与现有 XLSX codec。
- [x] 将版本更新至 0.5.1/code16；项目规则记录 dms-commit-msg 和 Release title“财记 <版本号>”。
- [x] 运行 `./gradlew :app:testDebugUnitTest :app:lintDebug :app:connectedDebugAndroidTest`。

### Task 4: 文档、发布与用户回归

**Files:** `docs/features/salary-records.md`、`docs/features/index.md`、`docs/work-items/WORK-20261105-003.md`、发布产物

- [x] 完成 API26/API34 仪器回归、正式 APK 签名/包名/版本/对齐/SHA-256 核验及代码审查。
- [x] 更新工资档案并通过结构/fresh 校验；记录实机回归所需操作，无数据库迁移。
- [ ] 生成并核验中文 commit 与 Release notes；提交并推送 main，创建 `v0.5.1`、“财记 0.5.1” Release，上传 APK 和 SHA-256 sidecar。

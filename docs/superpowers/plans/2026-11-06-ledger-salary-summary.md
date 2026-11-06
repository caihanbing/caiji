# 账单与工资汇总入口优化实施计划

> **For agentic workers:** 按顺序实现并验证导航命名、工资汇总、收入表单和设置说明。

**Goal:** 发布账单命名、工资累计汇总、新增收入扣款折叠及设置文案优化版本 0.5.2/code17。

**Architecture:** MainActivity 保留现有主导航目标并改文案。SalaryRootScreen 增加汇总页状态，统一复用记录、统计和筛选组件；将编辑来源、首页/汇总筛选及列表滚动状态保存在父级，以恢复正确的返回页面。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room。

**Spec:** `docs/superpowers/specs/2026-11-06-ledger-salary-summary-design.md`

## Global Constraints

- 留在当前工作区和 `main`，不新增分支。
- 应用版本 `0.5.2`、versionCode `17`，沿用现有正式签名。
- 不新增外部 API、数据库迁移或备份格式变化。
- 所有可见页面、导航、图标和文案须符合批准高保真稿。

## Review Focus

- 账单新入口仍打开月/年汇总，报告标题一致，钻取和返回正常。
- 工资首页年份筛选不变；汇总包括跨年数据并按日期排序，现金筛选只影响列表。
- 汇总页编辑/取消/保存/删除后返回来源并刷新，两个列表筛选和滚动状态分离。
- 新增收入默认收起、编辑原记录仍展开；管理收入项不丢草稿或展开状态。
- 设置文案明确说明工资、问题反馈和主题不属于账单备份。

---

### Task 1: 账单导航与汇总名称

**Files:** `MainActivity.kt`、`LedgerScreens.kt`、`BillReportsScreen.kt` 及对应仪器测试

- [x] 先为底栏“账单”、首页“汇总”按钮和汇总页标题添加失败测试。
- [x] 运行测试确认当前界面名称不匹配。
- [x] 更新导航标签、首页按钮文字及语义描述、月/年汇总页面标题；维持原统计、年份浏览和钻取。
- [x] 运行账单与主导航仪器测试。

### Task 2: 工资全部记录汇总与编辑返回

**Files:** `SalaryScreens.kt`、`SalaryScreensTest.kt`

- [x] 添加失败测试：汇总入口、全部年份金额和日期、现金/非现金筛选、点记录编辑后取消/保存/删除返回汇总及刷新。
- [x] 运行测试确认旧首页没有独立汇总页。
- [x] 增加 `SR.SUMMARY`；首页入口打开累计卡及全部逐笔列表，日期含年份且金额复用 `SalaryReports`。
- [x] 在父级保存编辑来源；为首页和汇总分别保存现金筛选与 `LazyListState`；编辑完成返回对应来源。
- [x] 运行工资浏览、编辑和 Activity 重建仪器测试。

### Task 3: 新增收入初始状态及设置说明

**Files:** `SalaryScreens.kt`、`SettingsScreen.kt` 和设置/工资仪器测试

- [x] 添加失败测试：新增表单扣款项初始不可见、展开后金额参与纯收入计算；编辑旧记录初始仍展开；管理收入项往返保留草稿和展开状态。
- [x] 为设置页已批准文案添加精确测试，覆盖本机数据卡片、账单/工资/使用偏好、备份范围及反馈/更新说明。
- [x] 将新建/编辑区分初始化状态；在 SettingsScreen 更新批准稿文案。
- [x] 运行设置及工资仪器测试，并检查 320dp/大字体/五套主题。

### Task 4: 档案与版本交付

**Files:** `app/build.gradle.kts`、相关 `docs/features/*.md`、Work Item、发布产物

- [x] 更新版本至 0.5.2/code17；更新账单、工资及本次调整文案涉及的功能档案。
- [x] 运行 JVM、Lint、API26/API34 回归；完成代码审查与 Feature Docs/Work Item 校验。
- [x] 构建并核验正式 APK 包名、版本、签名、zipalign 和 SHA-256。
- [ ] 使用 dms-commit-msg 生成提交与 Release notes；推送 `main` 并发布 `v0.5.2`，标题“财记 0.5.2”，附 APK 和 SHA-256 sidecar。
- [x] 记录实机回归步骤；无数据库迁移或服务端重启。

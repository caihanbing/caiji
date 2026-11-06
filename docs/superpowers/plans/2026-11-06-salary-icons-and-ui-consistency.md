# 财记界面统一与收入项编辑实施计划

> 执行者按任务顺序实施；每个生产行为先写失败测试，再以最小改动通过测试。

**Goal:** 发布 0.5.3/code18，统一账单、工资、记账和收入项界面，支持收入项改名及图标并兼容旧工资库和 Excel。

**Architecture:** Room `salary_income_types.iconKey` 经 schema 1→2 显式迁移持久化；`RoomSalaryRepository` 校验名称与图标并按稳定 ID 编辑。工资 UI 复用分类目录/选择器。XLSX 通过附加元数据保存完整目录，同时读取既有格式。

**Tech Stack:** Kotlin、Jetpack Compose Material 3、Room、现有 XLSX OOXML codec。

**Spec:** `docs/superpowers/specs/2026-11-06-salary-icons-and-ui-consistency-design.md`

## Global Constraints

- 留在当前工作区与 `main`，不创建分支。
- 发布版本 `0.5.3`、versionCode `18`，沿用现有正式签名；签名材料不入库。
- 显式迁移工资 schema 1 至 2；禁止 destructive migration；账本 schema 与加密备份格式不变。
- 已批准视觉稿约束所有可见 UI 变化；收入项现金性质创建后固定；名称上限 100 字符。

## Review Focus

- Schema migration 后所有历史行、项目 ID、现金性质及顺序完整，失败会回滚。
- 内置收入项被改名后，旧名 XLSX 可按原 ID 导入，不会重复创建或错误重分现金性质。
- XLSX 新元数据导入有本机目录、空白新库以及旧格式三条确定路径；预览/取消不改库，确认目录与工资原子写入。
- Room 查询的工资行始终显示最新收入项名称和图标；改名/改图标不改变记录 ID 和金额。
- 居中标题和右侧期间箭头在 320dp 和大字体下不重叠；管理对话框错误时保留草稿。

---

### Task 1: 工资目录迁移与默认图标

**Files:** `SalaryDatabase.kt`、工资数据库 Room schema、迁移和数据库测试。

- [x] 写失败测试：v1 数据库升级保留工资行/项目/顺序/现金属性，并为 17 个预置项映射批准稿图标、自定义项回退“other”。
- [x] 运行迁移测试，确认当前 schema 1 不满足 schema 2 结果。
- [x] 为 `SalaryIncomeTypeRow` 增加非空 `iconKey`，数据库版本升至 2，提供并注册 `MIGRATION_1_2`；新库预置目录使用迁移相同的固定图标映射。
- [x] 验证迁移成功与事务失败回滚，不触碰账本数据库。

### Task 2: 收入项编辑与名称/图标校验

**Files:** `SalaryModels.kt`、`SalaryRepository.kt`、Repository 测试。

- [x] 写失败测试：新建持久化图标；按 ID 修改名称/图标；固定现金性质、builtIn 与 sortOrder；拒绝空名、101 字符、重复名及非法图标；失败不变更目录。
- [x] 为 `SalaryIncomeType` 增加 `iconKey`；为 `RoomSalaryRepository` 增加 `updateIncomeType(id, name, iconKey)`，创建方法接收 `iconKey`。验证在数据库事务中名称唯一检查和更新，稳定 ID/现金性质保持不变。
- [x] 运行 Repository 与已有工资记录回归。

### Task 3: XLSX 目录元数据兼容

**Files:** `SalaryXlsxCodec.kt`、`SalaryXlsxCodecTest.kt`、`SalaryRepository.kt`、导入事务测试。

- [x] 写失败测试：旧三字段元数据与无元数据文件可读；新版本目录元数据导出后导入恢复 ID/名称/现金/图标；改名内置项的旧名文件映射回原 ID；未知图标回退 `other`。
- [x] 测试本机已有目录优先、首次恢复空白新库、现金性质冲突拒绝、预览/取消不写入、目录与工资同事务失败回滚。
- [x] 增加版本化可选目录元数据，同时继续输出旧自定义项目元数据供旧格式消费者读取；扩展 `SalaryImportPreview` 携带待恢复目录，不在解析预览时写数据库。
- [x] 在用户确认后的同一 `Room.withTransaction` 中应用允许的目录恢复并写入工资；ID 优先、名称回退，现金不一致仍整批拒绝。
- [x] 运行单测和仪器测试，验证样例表格列、公式、合计及安全解析不变。

### Task 4: 导航文案、标题和期间控件

**Files:** `MainActivity.kt`、`LedgerScreens.kt`、`BillReportsScreen.kt`、`EntryEditorScreen.kt`、`SalaryScreens.kt`、`SettingsScreen.kt`、相关 UI 测试。

- [x] 写失败测试：底栏/空状态入口命名，账单汇总及报告标题中心对齐、月/年行为，工资年份箭头语义与点击区域，收入项入口/导入/备份文案及 Excel 错误提示路径。
- [x] 将账单总表月模式年份控件置于共用居中标题下一行；年模式隐藏年份控件；工资年箭头替换为 `PeriodArrowButton`。
- [x] 共用居中 56dp 标题栏用于账单总表、期间报告、分类明细和工资子页；记一笔编辑页保持删除动作且标题居中。
- [x] 改正“记一笔”“收入项管理”“导入 Excel”“加密备份”入口及对应提示；保留按语义描述的“记账”用语。
- [x] 运行账单、主导航、工资入口和窄屏标题测试。

### Task 5: 收入项管理 UI 和工资各处图标

**Files:** `CaijiIcons.kt`/共用图标选择器、`SalaryScreens.kt`、`MainActivity.kt`、工资 UI 测试。

- [x] 写失败测试：管理列表图标/现金行、右上角添加且无底部按钮、整行进入编辑、添加现金选择默认、编辑现金性质固定、校验失败保留对话框、改名/图标返回后各列表和记录更新、重建保留草稿。
- [x] 以复用 `CategoryManagerScreen` 的 `IconCatalogPicker` 提供添加/编辑对话框，并让整行点击打开已加载项目；列表和工资记录使用项目 `iconKey` 对应的 `CaijiIcon`/CategoryCatalog 映射。
- [x] 在工资首页、汇总列表和收入项目选择菜单显示图标；选择菜单采用可滚动图标/名称行，现金选择属性在新增时填写、编辑时只读。
- [x] 为对话框字段/状态和收入编辑草稿添加 `rememberSaveable` 支持；保存成功刷新并返回原来源，失败保留草稿。
- [x] 运行工资 UI 测试、设置测试、320dp/大字体/五主题回归。

### Task 6: 验证、档案和发布

**Files:** 0.5.3 版本配置、工资/账单/分类功能档案、Work Item、发布 APK。

- [x] 运行 `:app:testDebugUnitTest`、`:app:lintDebug`、`:app:connectedDebugAndroidTest`（API26/API34）；按失败输出修复并重跑受影响套件。
- [x] 完成独立代码审查；更新相关 Feature Docs、索引及 Work Item 并通过 fresh/结构校验。
- [x] 构建正式 APK，核验包名、versionName/code、既有证书、zipalign、SHA-256；核验迁移覆盖升级。
- [x] 使用 dms-commit-msg 生成提交和 Release notes，提交 `d510300` 并推送 `main`；创建 `v0.5.3` Release，标题“财记 0.5.3”，上传 APK 与 SHA-256 sidecar 并下载校验。
- [x] 写明用户实体设备覆盖安装检查清单；实机结果未取得前保持 handoff-ready。

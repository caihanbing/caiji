# 工资记录与 Excel 导入导出 Implementation Plan

> **For agentic workers:** 按本计划逐项实现并验证；工作于用户指定的当前工作区和 main，不可新建分支。

**Goal:** 为财记添加与账本隔离的工资记录、汇总和与样例一致的 XLSX 导入导出。

**Architecture:** 独立 Room 数据库保存工资和收入项。纯 Kotlin 金额/聚合/OOXML 模块保持可单测；Compose 屏幕与现有 MainActivity、主题和文件选择器集成。

**Tech Stack:** Kotlin、Room 2.6.1、Jetpack Compose Material 3、Kotlin ZIP/XML API、JUnit、AndroidX instrumentation。

**Spec:** docs/superpowers/specs/2026-11-04-salary-records-design.md

## 全局约束

- 仅支持 .xlsx，无网络及广泛存储权限。
- 保持现有 ledger Room schema 1 和加密账本备份格式 2。
- 发布 0.4.0/code12，沿用私有正式签名材料；不提交密钥。
- 在当前 main 提交、推送；创建 v0.4.0 Release，附验证 APK 和 SHA-256 sidecar。

## Review Focus

- 负税额、全扣款和金额边界：纯收入精确到分且不可为负。
- 样例累计行、空行、零值和 Excel 日期序列：不作为错误明细导入。
- 同一工资的多笔重复：按出现次数跳过，不能按集合去重。
- 未知及冲突收入项：不错误归类现金属性或部分导入。
- 压缩包超大、格式伪装和畸形 XML：拒绝解析，不写入数据。

---

### Task 1: 工资领域、分类与独立数据库

创建 SalaryModels.kt、SalaryDatabase.kt、SalaryRepository.kt、SalaryReports.kt 和纯单测。使用 long 整数分，合计 BigInteger；Room schema 1 新库独立命名。先写测试证明净收入/导入重复键、现金汇总、日期范围，再实现。独立数据库 instrumented 测试验证 CRUD、事务原子导入、工资写入不触碰账本库。

### Task 2: XLSX 编解码与兼容样例

创建 SalaryXlsxCodec.kt 和单元测试/脱敏 fixture。解析 Sheet1 的 A:K 数据行；接受 Excel 日期序列或 ISO 日期；将空数值视为零；核对提供的纯收入；忽略指定三类合计行。使用重复键多重计数去重。导出样式化 11 列模板，明细按日期升序，动态扩展公式、过滤和下拉范围；使用兼容公式，写入/读取隐藏自定义收入性质属性，并用隐藏 M 列命名范围为自定义项目提供下拉。限制压缩体积与 XML 部件，使用禁用外部实体的 parser；未知项和类型冲突时整体报错。测试 180 行样例总量、往返、重复、Excel 格式/公式和异常文件。

### Task 3: Compose UI 与导航集成

创建工资 SalaryHomeScreen、SalaryEditorScreen、SalaryIncomeTypeScreen 和 SalaryViewModel。支持月/年/全部、现金筛选、总览、按日期录入/编辑、折叠扣款、显式现金类型管理、删除确认和空状态。沿用五套 CaijiPalette。MainActivity 按已批准稿调整底栏，加图表占位页与工资路由；在工资编辑/导入页暂存剪贴板账单通知，离开后按原规则处理。增加仪器测试覆盖导航、CRUD、筛选、计算与主题。

### Task 4: SAF 导入导出流程与回归

MainActivity 通过 OpenDocument 与 CreateDocument（application/vnd.openxmlformats-officedocument.spreadsheetml.sheet）接入 codec。导入先完整解析、验证及预览，再在确认时单个 Room 事务按重复多重集重核并写入。失败保留数据并列明行号。导出支持全部/当前期间，选择路径取消无副作用。instrumented tests 检验取消、失败回滚、账本恢复隔离和往返。

### Task 5: 文档、版本与发布

完成 JVM、lint、API34 instrumented、APK 签名/包名/版本/SDK/对齐/SHA 校验。创建 salary-records Feature Doc 并更新 ledger、local-backup 档案，Work Item 验收和 fresh 校验。递增至 0.4.0/code12，提交并直推 main，创建包含 APK 和 checksum 的 GitHub Release；发布后提供用户覆盖安装与逐项回归步骤。

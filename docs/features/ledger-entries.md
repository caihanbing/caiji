# 收支账单与月度明细

<!-- feature-id: ledger-entries -->
<!-- feature-title: 收支账单与月度明细 -->
<!-- feature-aliases: ledger, 账单, 月度汇总 -->
<!-- feature-status: active -->
<!-- feature-summary: 在本机维护整数分人民币收支账单，按日查看并计算月度收支。 -->
<!-- last-verified: 2026-10-31 -->
<!-- code-basis: HEAD unborn; worktree-digest sha256:923d6f192377049e497ec56675f6889af190d95c8e18e216a25acb77219ebc75 -->

## 快速上下文

V0.1 账单功能已实现，入口为底部“明细”和“记账”。数据由 `LedgerViewModel` 经 `RoomLedgerRepository` 保存到 Room；不需要账号或网络服务。

金额保存为正整数分，日期使用本地 `LocalDate`。汇总使用 `BigInteger`，避免多笔账单相加后溢出；编辑时允许把支出改为收入或反向修改，并需选择目标类型的启用分类。

## 目标与边界

### 目标

- 记录、修订、删除日常收入与支出。
- 以本地日期按日分组，并查看所选月份的收入、支出和每日小计。

### 范围内

- 新增账单默认支出和今天；金额键盘支持单次加减，分类必须主动选择。
- 可更改收支类型、金额、分类、日期和备注；日期不能晚于今天。
- 首页默认当前月，可前后切换月份；按日期倒序显示账单，月度汇总只统计该月记录。
- 编辑、删除完成后刷新列表和汇总；删除及放弃未保存内容均需确认。

### 范围外

- 多账本、账户转账、多币种、云同步、登录、报表、预算和资产管理。

### 不变量

- `amountFen > 0`；输入至多两位小数，溢出、零值、负值及未来日期不能保存。
- 账单通过稳定 ID 引用分类；账单类型必须与所选分类类型一致。
- 备注最多 20,000 个字符；月度和每日合计以任意精度整数计算。

## 业务行为

- 用户在“记账”选择收支类型和启用分类后输入金额、日期、备注并保存；默认不预选分类。
- 编辑时可切换支出/收入。切换后旧分类清空，需重新选择相同类型的启用分类；数据库类型字段与月汇总同步更新。
- 历史账单引用停用分类时，编辑其他字段可保留该分类；新选分类仍须启用。
- 首页使用本地日历日期，不做 UTC 日期转换；跨月或跨年账单只进入其本地月份。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CaijiRoot`、`LedgerHome`、`EntryEditor`：页面状态、输入校验提示、月份入口和账单编辑流程。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — `LedgerViewModel`：提供月份、账单、分类、汇总状态并调用 Repository。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — `RoomLedgerRepository`、`MonthTotals`：业务校验、事务写入、月份快照和精确汇总。
- `app/src/main/java/app/caiji/android/data/LedgerDatabase.kt` — `EntryRow`、`LedgerDao`、`LedgerDatabase`：Room 表、索引、外键及 SQL。
- `app/src/main/java/app/caiji/android/domain/Money.kt` — `Money`：十进制金额解析、加减及溢出检查。

## API、事件与任务

### `LedgerViewModel.addEntry` / `updateEntry`

- 输入类型、分值金额、稳定分类 ID、本地日期及可选备注；成功后刷新 UI 状态，失败时更新错误状态并通知编辑器。
- `RoomLedgerRepository` 在数据库事务内校验金额、日期、备注、分类状态和类型；更新操作也写回账单类型字段。
- 这是应用内部 Kotlin 契约，不提供外部 HTTP API。

### `LedgerViewModel.changeMonth`

- 输入 `YearMonth`，读取一个事务快照中的月份账单和汇总；请求序号丢弃过期月份结果。

## 数据模型与迁移

`entries` 保存 `id`、`type`、`amountFen`、`categoryId`、ISO `occurredOn`、`note` 及创建/更新时间；`categories` 保存稳定 ID、类型、名称、图标键、排序和启用状态。`entries.categoryId` 外键限制引用类别删除。

数据库初始版本为 Room schema 1，实体定义在 `LedgerDatabase.kt`，快照在 `app/schemas/app.caiji.android.data.LedgerDatabase/1.json`。这是 V0.1 首次发布库，没有既有用户库迁移；后续字段变化必须提供显式迁移，不得 destructive fallback。

## 核心流程

1. `EntryEditor` 将金额表达式解析为整数分，并要求分类、有效金额和不晚于今天的日期。
2. ViewModel 调用 Repository；新增或编辑在 `database.withTransaction` 内校验分类并写入 Room。
3. Room 提交后 ViewModel 重新读取所选月 `MonthSnapshot`；按日期倒序更新列表和 `BigInteger` 汇总。
4. 删除前显示确认框，确认后删除记录并重新查询月快照。

## 异常与边界条件

- 格式错误、精度超过两位、非正金额或加减溢出 — 不提交并显示输入错误。
- 未来日期 — 保存按钮不执行；日期选择器上限为今天。
- 被并发删除的编辑记录或无效分类 — Repository 校验失败，原有数据库事务回滚并显示错误。
- 合计超过 `Long` 范围 — 通过 `BigInteger` 显示，不发生整数回绕。

## 并发与幂等

- Room 写入事务保证单次修改原子性；`RestoreWriteGate` 等待已开始的写入，并在恢复会话内拒绝新的用户写入。
- 编辑期间通过账单 ID 定位记录；重复点击“完成”由 `saving` 状态禁用。
- 月份请求序号防止较早的异步查询覆盖较新月份。
- 新账单 ID 使用 UUID；重复 ID 由主键约束拒绝。

## 安全与权限

- 所有账单存于应用私有 Room 数据库；应用不申请网络或广泛存储权限，也不集成广告和分析 SDK。
- Manifest 禁用 Android 自动应用备份，避免账单随系统云备份导出。
- 日常页面不输出账单或备注日志；本机拥有设备解锁权限的用户可使用应用数据。

## 配置与依赖

- Android `minSdk=26`，应用版本 `0.1.0`；Room schema 版本为 1。
- 依赖 Kotlin、Jetpack Compose Material 3、Lifecycle ViewModel、Kotlin Coroutines 和 AndroidX Room；无外部业务服务。

## 可观测性与运维

- 没有遥测、后台任务或服务器日志；读写失败通过页面错误提示显示。
- 更新安装应保留应用数据并沿用相同签名；数据库结构变化必须在后续版本提供迁移。

## 测试与验证

### 已执行

- `.\gradlew.bat testDebugUnitTest` — 通过；金额解析、分类目录、加减精度、溢出和大数汇总测试通过。
- `.\gradlew.bat compileDebugAndroidTestKotlin` — 通过；Room 持久化、收支类型切换和 Compose 交互仪器测试源码编译成功。
- `.\gradlew.bat lintDebug` — 通过，0 errors、2 个工具建议警告。
- `.\gradlew.bat assembleRelease` — 通过；release APK 已生成并完成包信息、签名与对齐核验。

### 未执行

- `connectedDebugAndroidTest` — 未运行成功，Gradle 报 `No connected devices!`；本机 API 34 模拟器启动因模拟器实例冲突退出，Room 实际运行、Compose 布局、API 26 及覆盖升级仍需设备回归。

## 关键决策

### 金额以分保存，合计用任意精度整数

- **决定**：账单存 `Long` 分值，统计用 `BigInteger`。
- **原因**：避免浮点小数误差和多笔合法金额累加溢出。
- **取舍**：显示层将精确分值格式化为人民币元。
- **证据**：`MoneyTest`、`MonthTotalsTest` 和 `LedgerRepositoryTest`。

### 编辑账单允许切换收支类型

- **决定**：切换后要求用户从目标类型中选择启用分类，并更新数据库类型及对应月度汇总。
- **原因**：用户在本版本实施期间确认该行为。
- **取舍**：旧分类在类型切换后清空，避免类型与类别不匹配。
- **证据**：`LedgerRepositoryTest.changingEntryTypeRequiresTheMatchingEnabledCategoryAndMovesMonthlyTotals`；Compose 仪器用例已编译，尚待设备执行。

## 已知问题与后续工作

### 设备端回归待执行

- **影响**：实际 Room 行为、软键盘/小屏布局、API 26 兼容和覆盖安装保留数据尚无设备运行证据。
- **当前处理**：Room 与 Compose 仪器测试源码已编译；本机没有可连接设备，模拟器启动失败。
- **后续动作**：按 Work Item `WORK-20261031-001` 的用户回归步骤，在 Android 8.0+ 真机和可用 API 34 设备运行。

## 变更记录

### 2026-10-31 — V0.1 首版

- **状态**：已完成
- **变化**：建立 Room 本机账单、月度快照和 Compose 收支编辑流程。
- **原因**：支持离线人民币日常记账。
- **兼容性**：Android 8.0+；Room schema 1；后续数据库版本需显式迁移。
- **验证**：单元测试、lint、Android 仪器测试源码编译及签名 APK 构建通过；设备运行未执行。

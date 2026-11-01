# 收支账单与月度明细

<!-- feature-id: ledger-entries -->
<!-- feature-title: 收支账单与月度明细 -->
<!-- feature-aliases: ledger, 账单, 月度汇总 -->
<!-- feature-status: active -->
<!-- feature-summary: 在本机维护整数分人民币账单，按日、月、年浏览及汇总收支。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD 7e9cc5743f1c; worktree-digest sha256:21fc9ce0fc332f187f08394a6f8e3fc4c4e5ed08104c1fe9fcb206d1fbbc4da1 -->

## 快速上下文

财记 0.2.0 通过底部“明细、记账、设置”导航记录本地人民币账单。明细页显示月度结余、日分组账单和空账本入口；“账单”页提供月/年总表、期间报告、分类排行和分类明细。记账页支持新增、编辑、两种入口的删除确认，以及选择分类后展开金额键盘。账单仍由 `LedgerViewModel` 和 `RoomLedgerRepository` 写入 Room。

金额以正整数分保存，汇总用 `BigInteger`；日期以本地 `LocalDate` 存储。UI 金额格式化会加千位分组并保留两位小数，不参与金额计算。

## 目标与边界

### 目标

- 新增、编辑、删除收入与支出账单。
- 按本地日期查看所选月份的账单、月度收支与每日小计。
- 按自然年和自然月汇总历史收支，查看分类排行并钻取筛选账单。

### 范围内

- 默认新增支出和今天；收支类型需要明确选择，分类不预选。
- 金额键盘支持单次加减，日期不能晚于今天，备注为选填。
- 首页可切换月份；空月份提供“开始记账”入口。
- 月账单显示所选年 12 个月收支，年账单显示最早账单年至当前年的汇总；空月和空年显示零值。
- 年/月报告展示结余、收入、支出和分类排行；点击分类显示对应期间账单，点击账单可编辑并保留筛选。
- 账单行可左滑展开删除，点击后仍需二次确认；同一时间只展开一行。
- 编辑、删除完成后刷新月度明细和汇总；放弃未保存编辑时显示确认。

### 范围外

- 多账本、转账、多币种、云同步、登录、报表、预算和资产管理。

### 不变量

- `amountFen > 0`；最多输入两位小数；金额溢出、零值、负值及未来日期不能保存。
- 账单以稳定 ID 引用分类，账单类型必须与分类类型一致。
- 备注最多 20,000 个字符；总和采用任意精度整数。

## 业务行为

- `LedgerHome` 显示本月结余、收入、支出和每日分类账单；每条明细显示类别、备注及带正负号的金额。
- 新增、编辑共享 `EntryEditorScreen`；类型切换后清空原分类，要求选目标类型的启用分类。
- 在记账页管理分类后返回会保留类型、金额、日期和备注；新分类显示但不会自动选中。
- 新账单已选分类若在管理期间被停用，返回后清空选择并要求重选；编辑历史账单可以保留其已停用分类。
- 编辑可切换收支类型并更新对应月度汇总；删除需二次确认。
- 备注输入期间显示系统文字键盘、隐藏自定义数字键盘；表单可滚动，日期和备注可触达，保存按钮固定在底部。输入完成或清除焦点后可继续使用金额键盘。
- 320dp 或较大字体下，月标题切换为上下布局；金额、分类和表单通过省略或滚动避免互相遮挡。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CaijiRoot`、页面切换、月份回调和账单写入回调。
- `app/src/main/java/app/caiji/android/ui/LedgerScreens.kt` — `LedgerHome`、月度汇总、每日分组、账单行和空状态。
- `app/src/main/java/app/caiji/android/ui/BillReportsScreen.kt` — 月/年总表、期间报告、分类排行和筛选账单。
- `app/src/main/java/app/caiji/android/data/LedgerReports.kt` — `BigInteger` 期间、年度和分类聚合。
- `app/src/main/java/app/caiji/android/data/LatestRequestGate.kt` — 报告请求序号门闩，丢弃过期异步结果。
- `app/src/main/java/app/caiji/android/ui/EntryEditorScreen.kt` — `EntryEditorScreen`、金额/分类/日期/备注控件、数字键盘和删除/放弃确认。
- `app/src/main/java/app/caiji/android/ui/MoneyDisplayFormat.kt` — `formatYuan`，仅格式化 Long/BigInteger 分值。
- `app/src/main/java/app/caiji/android/ui/theme/CaijiTheme.kt`、`CaijiIcons.kt` — 共用暖白/琥珀主题、图标和系统栏视觉。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — UI 状态与 Repository 协调。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — `RoomLedgerRepository`、金额/日期/分类校验和汇总。
- `app/src/main/java/app/caiji/android/domain/Money.kt` — 十进制输入解析、加减和溢出检查。

## API、事件与任务

### `LedgerViewModel.addEntry` / `updateEntry`

- 输入收支类型、分值金额、分类 ID、本地日期和可选备注；成功后刷新状态，失败时保留表单并显示错误。
- `RoomLedgerRepository` 在数据库事务内检查金额、日期、备注和分类状态/类型；不是外部 HTTP API。

### `LedgerViewModel.changeMonth`

- 输入 `YearMonth`，读取该月账单和精确汇总；月份请求序号避免旧查询覆盖新月份。

### `RoomLedgerRepository.monthlyReports` / `yearlyReports` / `periodReport`

- 在 Room 只读快照内读取账单，再由 Kotlin 以 `BigInteger` 聚合月份、年份和分类金额，避免 SQLite `SUM(Long)` 溢出。
- 报告查询前初始化默认分类目录，确保首启空数据库可提供完整分类榜单。
- `LedgerViewModel` 通过 `LatestRequestGate` 阻止较早月份/年份查询覆盖当前报告；`LedgerReportsTest.newerPeriodRequestPreventsOlderQueryFromUpdatingTheScreen` 覆盖顺序规则。

## 数据模型与迁移

`entries` 保存 ID、类型、分值金额、分类 ID、本地日期、备注及创建/更新时间。账单通过外键关联稳定分类 ID。当前 Room schema 为 1；本次 UI 更新没有数据库字段、索引或迁移变化。将来结构更新必须添加显式 Room Migration。

## 核心流程

1. `EntryEditorScreen` 将金额表达式解析为整数分，并验证分类、日期和备注。
2. 编辑器通过 `CategoryManagerScreen` 保留草稿；分类停用会触发新建账单重选提示。
3. `LedgerViewModel` 调用 Repository；Room 在事务中写入或删除账单。
4. 成功后重新读取所选月快照，按本地日期显示列表并用 `BigInteger` 更新汇总。
5. 空月份使用现有记账页入口新增第一笔账单。
6. 报告页按期间加载汇总和分类排行；选择分类后提供该期间分类账单的编辑入口。

## 异常与边界条件

- 格式错误、超过两位小数、非正金额或加减溢出 — 不提交并显示输入错误。
- 未来日期 — 日期选择器不可选，保存校验仍拒绝。
- 无效/停用分类或并发失效记录 — Repository 校验失败并回滚事务；编辑输入保留。
- 月度合计超过 `Long` 范围 — 以 `BigInteger` 计算和显示。

## 并发与幂等

- Room 写事务保证单次修改原子性；`RestoreWriteGate` 在恢复期间拒绝普通写入。
- 编辑通过账单 ID 定位；`saving` 状态阻止重复提交。
- 月份请求序号避免较早查询覆盖新选择；账单 ID 使用 UUID，重复 ID 受主键约束。

## 安全与权限

- 账单保存在应用私有数据库；应用不申请网络或广泛存储权限。
- Manifest 禁用 Android 自动备份，避免账单随系统云备份导出。
- 页面不记录账单、备注或备份密码日志。

## 配置与依赖

- Android `minSdk=26`、`targetSdk=34`；版本 `0.2.0`、`versionCode=3`；Room schema 1。
- 使用 Kotlin、Jetpack Compose Material 3、Lifecycle ViewModel、Coroutines 和 Room，无业务后端。
- macOS release 构建说明位于 `docs/release/macOS-install.md`。

## 可观测性与运维

- 无遥测、后台任务或服务器日志；读写失败通过统一反馈提示显示。
- 生产安装包沿用 0.1.1 的签名并支持覆盖升级；更新前建议导出加密备份。含新增分类图标键的备份不能由 0.1.1 恢复。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug` — 通过；API34 AVD 最终回归 49 项 instrumentation，包含 320dp/1.3 字体布局。
- API34 AVD 最终运行 49 项 instrumentation；测试覆盖报告钻取、编辑后筛选刷新和首次报告查询初始化，单测覆盖闰日、空月、跨年、过期请求和超过 Long 的精确汇总。
- UI 运行时查看了月度空账本、账单明细、记账页和真实 Gboard；系统键盘出现后自定义键盘隐藏，日期、备注和保存操作可见。
- `./scripts/build-release-macos.sh` — 通过，生成并签名 0.2.0/code 3 版本化 APK，校验签名与 SHA-256。
- API34 release 成功导入 0.1.1 导出的格式 2 文件；预览和恢复均核对 1 笔账单、16 个分类、收入 ¥0、支出 ¥1。

### 未执行

- 未在 Android API 26 设备上运行实际 UI；最低 API 由 manifest/aapt metadata 确认为 26。
- 原始 0.1.1 APK 不在工作树；从对应基线源码用相同 release key 重建 0.1.1，恢复旧备份后在 API34 AVD 覆盖升级到 0.2.0，确认账单保留。
- 用户实体设备上的备份后卸载/安装与常用操作回归仍需用户按 release guide 执行。

## 关键决策

### 金额以分保存，合计用任意精度整数

- **决定**：账单金额保存为 Long 分值，汇总用 `BigInteger`。
- **原因**：避免浮点误差和多笔合法金额累加溢出。
- **取舍**：展示格式与业务计算分离。
- **证据**：`MoneyTest`、`MonthTotalsTest`、`MoneyDisplayFormatTest` 和 `LedgerScreensTest`。

### 编辑账单允许切换收支类型

- **决定**：切换后清空原分类，要求选择目标类型启用分类，并同步月度汇总。
- **原因**：账单类型必须与分类类型一致。
- **取舍**：用户不能保留与新类型不匹配的分类 ID。
- **证据**：`LedgerRepositoryTest.changingEntryTypeRequiresTheMatchingEnabledCategoryAndMovesMonthlyTotals`、`EverydayLedgerFlowTest.anExistingEntryCanSwitchFromExpenseToIncomeAndBeDeleted`。

### 记账页与小屏输入布局

- **决定**：记账页保留现有数字键盘；备注获得焦点时显示系统键盘，隐藏自定义键盘并固定保存按钮；小屏表单可滚动。
- **原因**：输入备注时仍能到达日期、备注及保存，320dp/大字体也可浏览类别。
- **取舍**：小屏下分类、日期和备注需要在表单内容区滚动查看。
- **证据**：API 34 AVD 320dp/1.3 字体交互测试与 `task-6-editor-ime-small-large-text.png` 运行截图。

## 已知问题与后续工作

### 用户实体设备回归

- **影响**：用户真实设备型号、字体和自有历史账本尚未在本轮实测。
- **当前处理**：API34 AVD 已验证旧备份恢复、同签名覆盖升级、报表和记账交互。
- **后续动作**：用户按 `docs/release/macOS-install.md` 在 Android 8.0+ 实机覆盖升级，核对历史账单与常用流程。

## 变更记录

### 2026-11-01 — 月/年账单报告和实机反馈优化

- **状态**：已完成
- **变化**：增加月/年报告与分类明细、按分类展开金额键盘、左滑删除确认、48 个分类图标及拖动排序。
- **原因**：根据用户实机验证的优化清单改进浏览和常用操作。
- **兼容性**：Room schema 1、整数分金额和备份格式 2 不变；沿用 0.1.1 签名覆盖升级；新版图标键备份不能由 0.1.1 恢复。
- **验证**：API34 AVD 43 个仪器测试通过；release 成功恢复旧格式 2 文件并核对数量和汇总。

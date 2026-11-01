# 收支账单与月度明细

<!-- feature-id: ledger-entries -->
<!-- feature-title: 收支账单与月度明细 -->
<!-- feature-aliases: ledger, 账单, 月度汇总 -->
<!-- feature-status: active -->
<!-- feature-summary: 在本机维护整数分人民币账单，按日、月、年浏览及汇总收支。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD f24b3b149f14; worktree-digest sha256:94a0b5f1a41df92b388f6f7f011a2e1af217ba1263b4769673db1af739eeb427 -->

## 快速上下文

财记 0.2.1 通过底部“明细、记账、设置”导航记录本地人民币账单。明细页显示月度结余、日期右侧每日收支、日分组账单和空账本入口；“账单”页提供月/年总表、期间报告、分类排行和分类明细。记账页支持新增、编辑和删除；分类选择后才显示日期/备注/金额键盘，键盘内“保存”为唯一保存入口。账单仍由 `LedgerViewModel` 和 `RoomLedgerRepository` 写入 Room。

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
- 明细每日收入/支出位于日期右侧；月账单标题栏可切换年份；窄屏月/年表格与表头使用同一横向滚动区域。
- 明细、月/年账单及年月报告使用同一结余汇总卡，期间切换箭头为透明 48dp 点击区。
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
- 新账单已选分类若在管理期间被停用，返回后清空选择、折叠输入区并要求重选；编辑历史账单可以保留其已停用分类。
- 编辑可切换收支类型并更新对应月度汇总；删除需二次确认。
- 新建和编辑初入均折叠分类以下输入区；新建选分类、编辑点金额/分类后展开。系统返回先收起输入区，再使用原放弃草稿确认。
- 键盘日期键打开系统日期选择器；默认显示“今天”，其他日期显示月日，日期不能晚于今天。备注获得焦点时隐藏数字键盘，IME 完成后恢复。
- 数字键盘右下“保存”为唯一保存动作；保存期间禁用，失败保留草稿。金额仍以整数分精确校验。
- 320dp/大字体下，每日摘要保持在日期右侧；账单表头和数值列保持对齐并可整体横向滚动；分类、备注与键盘可滚动到达。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CaijiRoot`、页面切换、月份回调和账单写入回调。
- `app/src/main/java/app/caiji/android/ui/LedgerScreens.kt` — `LedgerHome`、月度汇总、每日分组、账单行和空状态。
- `app/src/main/java/app/caiji/android/ui/BillReportsScreen.kt` — 月/年总表、期间报告、分类排行和筛选账单。
- `app/src/main/java/app/caiji/android/ui/PeriodControls.kt` — 明细和报告共用的期间箭头及结余汇总卡。
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

- Android `minSdk=26`、`targetSdk=34`；版本 `0.2.1`、`versionCode=4`；Room schema 1。
- 使用 Kotlin、Jetpack Compose Material 3、Lifecycle ViewModel、Coroutines 和 Room，无业务后端。
- macOS release 构建说明位于 `docs/release/macOS-install.md`。

## 可观测性与运维

- 无遥测、后台任务或服务器日志；读写失败通过统一反馈提示显示。
- 生产安装包沿用 0.1.1/0.2.0 的签名并支持覆盖升级；更新前建议导出加密备份。0.2.1 与 0.2.0 图标键兼容；0.1.1 不能恢复含新增图标键的备份。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug` — 0.2.1 源码通过；API34 AVD 57 项 instrumentation 全通过，包含 320dp/1.3 字体布局。
- API34 AVD 仪器覆盖期间报告钻取与编辑返回、日期按键选择和完整辅助说明、IME 键盘切换、键盘唯一保存、小屏输入区、每日两项长金额及明细/账单点击；`LedgerReportsTest` 继续覆盖闰日、空月、跨年、过期请求和超出 Long 的精确汇总。
- API34 AVD 使用同签名 0.2.0 release 创建 ¥5 餐饮账单，再覆盖安装 0.2.1 release；启动后仍显示 ¥5.00 支出和原账单。
- `./scripts/test-build-release-macos.sh` 和 `./scripts/build-release-macos.sh` — 通过；0.2.1/code 4 包名、minSdk26、targetSdk34、zipalign、v2 签名和 SHA-256 核验通过。
- API34 release 成功导入 0.1.1 导出的格式 2 文件；预览和恢复均核对 1 笔账单、16 个分类、收入 ¥0、支出 ¥1。

### 未执行

- 未在 Android API 26 设备上运行实际 UI；最低 API 由 APK 元数据确认为 26。
- 原始 0.1.1 APK 不在工作树；从对应基线源码用相同 release key 重建 0.1.1，恢复旧备份后在 API34 AVD 覆盖升级到 0.2.0，确认账单保留。
- 用户实体设备上的系统键盘、排序箭头、长金额及版本覆盖升级仍需用户最终回归。

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

### 记账页展开状态和唯一保存

- **决定**：新增和编辑初始隐藏分类以下输入；选分类后显示，编辑点金额/分类可展开；日期由键盘“今天”键打开；右下“保存”是唯一保存入口。备注 IME 打开时隐藏数字键盘，IME 完成后恢复。
- **原因**：记账首屏聚焦金额与分类，保留数字录入、日期、备注和草稿行为。
- **取舍**：日期选择入口只在展开后的数字键盘中显示；再次进入输入区可继续编辑。
- **证据**：API34 AVD `EntryEditorScreenTest` 和 `EverydayLedgerFlowTest`，含备注 IME 和金额精确保存。

## 已知问题与后续工作

### 用户实体设备回归

- **影响**：用户真实设备型号、字体和自有历史账本尚未在本轮实测。
- **当前处理**：API34 AVD 已验证旧备份恢复、同签名覆盖升级、报表和记账交互。
- **后续动作**：用户按 `docs/release/macOS-install.md` 在 Android 8.0+ 实机覆盖升级，核对历史账单与常用流程。

## 变更记录

### 2026-11-01 — 0.2.1 明细、记账与汇总布局调整

- **状态**：已完成
- **变化**：每日摘要置于日期右侧；统一期间箭头/结余卡及月年表格滚动；新增/编辑折叠输入区，日期按键和键盘保存统一。
- **原因**：执行用户实机反馈。
- **兼容性**：Room schema 1、加密备份格式 2、金额与账单 Repository 契约不变；沿用同一正式签名，0.2.0 数据可覆盖保留。
- **验证**：57 项 API34 instrumentation、26 项 JVM 单测、lint、debug/release 构建通过；签名覆盖 0.2.0 并保留账单。

### 2026-11-01 — 月/年账单报告和实机反馈优化

- **状态**：已完成
- **变化**：增加月/年报告与分类明细、按分类展开金额键盘、左滑删除确认、48 个分类图标及拖动排序。
- **原因**：根据用户实机验证的优化清单改进浏览和常用操作。
- **兼容性**：Room schema 1、整数分金额和备份格式 2 不变；沿用 0.1.1 签名覆盖升级；新版图标键备份不能由 0.1.1 恢复。
- **验证**：API34 AVD 43 个仪器测试通过；release 成功恢复旧格式 2 文件并核对数量和汇总。

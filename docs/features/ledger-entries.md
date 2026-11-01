# 收支账单与月度明细

<!-- feature-id: ledger-entries -->
<!-- feature-title: 收支账单与月度明细 -->
<!-- feature-aliases: ledger, 账单, 月度汇总 -->
<!-- feature-status: active -->
<!-- feature-summary: 在本机维护整数分人民币账单，按日、月、年浏览及汇总收支。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD a893bb7a4a7b; worktree-digest sha256:b646951f0127aa684070299d64b2359f1f1504fbdd406127cce872c7211f79d1 -->

## 快速上下文

财记 0.2.3 通过底部“明细、记账、设置”导航记录本地人民币账单。明细页显示月度结余、日期右侧并排且右对齐的每日收支、日分组账单和空账本入口；“账单”页提供月/年总表、期间报告、分类排行和分类明细。收入、支出、结余列等宽填满扣除期间列和左右留白后的可用宽度；每日摘要与月/年结余文字右缘对齐到距屏幕右边 18dp 的内容边界；超宽表格整体横向滚动。记账页支持新增、编辑和删除；点金额或选分类可展开输入区，备注与数字键盘相邻。前台窗口获得焦点时读取一次剪贴板，匹配指定银行提醒则打开待保存表单。账单仍由 `LedgerViewModel` 和 `RoomLedgerRepository` 写入 Room。

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
- 月账单显示所选年 12 个月收支，并可浏览 1900 年至当年的任意年份；年账单显示最早账单年至当前年的汇总；空月和空年显示零值。
- 年/月报告展示结余、收入、支出和分类排行；点击分类显示对应期间账单，点击账单可编辑并保留筛选。
- 明细每日收入/支出位于日期右侧并排，整组右缘距屏幕 18dp；长金额可在摘要内横向滚动。月账单标题栏可切换年份；三项金额列等宽填满扣除期间列与内边距后的宽度，结余文字右缘距屏幕 18dp；窄屏月/年表格与表头使用同一横向滚动区域，滑至末端时保留该右侧边距。
- 前台剪贴板仅识别“账户变动提醒/代发工资”和“交易成功提醒/网上快捷支付”。交易类型、交易金额、交易时间字段须唯一且通过格式校验；分类留空，不自动保存。当前草稿或备份恢复期间暂存最新匹配通知。
- 相同剪贴板文本按 SHA-256 指纹持久去重，仅存指纹、不存通知原文；保存或取消后均不再次自动打开。
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
- 新增、编辑共享 `EntryEditorScreen`；类型切换后清空原分类，要求选目标类型的启用分类。未选分类时可点击金额展开，但保存仍要求分类。
- 在记账页管理分类后返回会保留类型、金额、日期和备注；新分类显示但不会自动选中。
- 新账单已选分类若在管理期间被停用，返回后清空选择、折叠输入区并要求重选；编辑历史账单可以保留其已停用分类。
- 编辑可切换收支类型并更新对应月度汇总；删除需二次确认。
- 新建和编辑初入均折叠分类以下输入区；点击金额或选择分类后展开。备注和数字键盘相邻，上方分类可滚动。系统返回先收起输入区，再使用原放弃草稿确认。
- 键盘日期键打开系统日期选择器；默认显示“今天”，其他日期显示月日，日期不能晚于今天。备注获得焦点时隐藏数字键盘，IME 完成后恢复。
- 数字键盘右下“保存”为唯一保存动作；保存期间禁用，失败保留草稿。金额仍以整数分精确校验。
- 320dp/大字体下，每日摘要保持在日期右侧；账单表头和数值列保持对齐并可整体横向滚动；分类、备注与键盘可滚动到达。摘要和结余文字的实际渲染边缘对齐到 18dp 内容边界。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CaijiRoot`、页面切换、月份回调和账单写入回调。
- `app/src/main/java/app/caiji/android/ui/LedgerScreens.kt` — `LedgerHome`、月度汇总、每日分组、账单行和空状态。
- `app/src/main/java/app/caiji/android/ui/BillReportsScreen.kt` — 月/年总表、期间报告、分类排行和筛选账单。
- `app/src/main/java/app/caiji/android/ui/PeriodControls.kt` — 明细和报告共用的期间箭头及结余汇总卡。
- `app/src/main/java/app/caiji/android/data/LedgerReports.kt` — `BigInteger` 期间、年度和分类聚合。
- `app/src/main/java/app/caiji/android/data/LatestRequestGate.kt` — 报告请求序号门闩，丢弃过期异步结果。
- `app/src/main/java/app/caiji/android/ui/EntryEditorScreen.kt` — `EntryEditorScreen`、金额/分类/日期/备注控件、数字键盘和删除/放弃确认。
- `app/src/main/java/app/caiji/android/ui/MoneyDisplayFormat.kt` — `formatYuan`，仅格式化 Long/BigInteger 分值。
- `app/src/main/java/app/caiji/android/domain/ClipboardTransactionParser.kt` — 两种银行通知模板、交易金额字段和日期年份推断。
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

### `ClipboardTransactionParser.parse`

- 输入剪贴板文本和当天 `LocalDate`；只识别已配置模板并返回类型、整数分金额、最近的非未来日期，或无匹配结果。
- `MainActivity` 在应用回到前台且窗口取得焦点时读取剪贴板；保存内容指纹用于去重，不保存原文。

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
- 页面不记录账单、备注、剪贴板原文或备份密码；持久去重信息仅为剪贴板内容指纹。
- Android 10 及以上只在应用取得焦点后读取剪贴板；Android 12 及以上系统会显示读取提示。

## 配置与依赖

- Android `minSdk=26`、`targetSdk=34`；版本 `0.2.3`、`versionCode=6`；Room schema 1。
- 使用 Kotlin、Jetpack Compose Material 3、Lifecycle ViewModel、Coroutines 和 Room，无业务后端。
- macOS release 构建说明位于 `docs/release/macOS-install.md`。

## 可观测性与运维

- 无遥测、后台任务或服务器日志；读写失败通过统一反馈提示显示。
- 生产安装包沿用同一正式签名并支持覆盖升级；更新前建议导出加密备份。0.2.2 不改 schema 或备份格式。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug` — 0.2.3 JVM 与 API34 AVD 67 项仪器测试全部通过；包含每日摘要三种类型组合及月/年、零值、负值的实际渲染像素右缘检查。
- API34 仪器覆盖两种通知格式、字段唯一性、余额不串入金额、跨年和闰日；每日收支同一行、年份上下界、类别提示移除、金额展开、键盘邻接、分类箭头均通过。
- API34 AVD 以同签名 0.2.1 安装包新增 ¥1.23 支出，再覆盖安装 0.2.2；启动后仍显示原 ¥1.23 账单。
- `bash scripts/test-build-release-macos.sh` 与 `./scripts/build-release-macos.sh` 通过；0.2.3/code 6、包名、minSdk26、targetSdk34、zipalign、v2 证书 SHA-256 `692d2387ba18f63ed43548d153af2a611384d8bffe1c603c3314df56ef006e1a` 和 APK SHA-256 `02e3285f9b0f32df9f687b1c5e2e972b953a835fc333dda6739d7cf3191d3019` 已核验。
- API34 release 成功导入 0.1.1 导出的格式 2 文件；预览和恢复均核对 1 笔账单、16 个分类、收入 ¥0、支出 ¥1。

### 未执行

- 未在 Android API 26 设备上运行实际 UI；最低 API 由 APK 元数据确认为 26。
- 未在 Android API 26 实机及不同厂商系统验证剪贴板焦点时序。
- 用户实体设备上的剪贴板系统提示、系统键盘、排序尖角触控和长金额仍需最终回归。

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

### 银行通知剪贴板预填

- **决定**：只在应用回到前台并取得窗口焦点时读取；限定两类通知；选择最近有效且不晚于今天的日期；暂存期间不覆盖草稿；表单首次展示才持久记录 SHA-256 指纹。
- **原因**：自动填表减少重复录入，同时避免错误分类或覆盖草稿。
- **取舍**：分类留空且不自动保存；Android 系统会提示剪贴板访问。
- **证据**：`ClipboardTransactionParserTest` 和 API34 `MainActivityClipboardTest.foregroundClipboardNotificationOpensPrefilledEntryOnlyOnce`。

## 已知问题与后续工作

### 用户实体设备回归

- **影响**：用户真实设备型号、字体和自有历史账本尚未在本轮实测。
- **当前处理**：API34 AVD 已验证旧备份恢复、同签名覆盖升级、报表和记账交互。
- **后续动作**：用户按 `docs/release/macOS-install.md` 在 Android 8.0+ 实机覆盖升级，核对历史账单与常用流程。

## 变更记录

### 2026-11-01 — 0.2.3 每日摘要和账单结余右对齐

- **状态**：已完成
- **变化**：每日摘要短/长文本均右锚到 18dp 边界；月/年三金额列扣除月份/年份和内边距后等分剩余宽度，结余靠齐同一边界；超宽表格继续整表滚动。
- **原因**：依据用户实机截图减少每日摘要左侧漂移及结余右侧空白。
- **兼容性**：只改 UI 布局，Room schema 1、加密备份格式 2 和汇总数据模型不变。
- **验证**：API34 67 项仪器、JVM、lint、正式 APK、覆盖安装保留 ¥1.23 通过；截图位于 `captures/0.2.3-ledger.png` 和 `captures/0.2.3-month-bills.png`。

### 2026-11-01 — 0.2.2 实机反馈与剪贴板记账

- **状态**：已完成
- **变化**：每日收支并排；月账单年份范围扩至 1900 年至当年；调整期间表格和记账输入区；允许未选分类时展开金额键盘；新增两种交易提醒的剪贴板预填及指纹去重。
- **原因**：按用户实机验证结果优化常用浏览和记账流程。
- **兼容性**：Room schema 1、整数分金额、备份格式 2 均不变；使用既有正式签名覆盖升级。
- **验证**：JVM、60 项 API34 仪器、lint、0.2.2/code5 APK 包名/版本/签名/zipalign/SHA-256 及同签名 0.2.1 覆盖升级保留 ¥1.23 均通过。

### 2026-11-01 — 0.2.3 摘要与表格右对齐

- **状态**：已完成
- **变化**：每日摘要整组靠齐 18dp 右边界；月/年表格三列均分期间列及内边距后的宽度，结余文字靠齐 18dp 边界；超长金额保持整表滚动。
- **原因**：依据用户实机截图消除摘要和结余右侧空白。
- **兼容性**：Room schema 1、备份格式 2 和报表数据接口不变。
- **验证**：API34 67 项仪器测试覆盖截图金额、单/双项摘要、月/年、零结余、负结余及窄屏；正式 APK 和数据覆盖升级验证通过。

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

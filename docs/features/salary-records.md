# 工资收入记录

<!-- feature-id: salary-records -->
<!-- feature-title: 工资收入记录 -->
<!-- feature-aliases: salary, 工资, 工资明细 -->
<!-- feature-status: active -->
<!-- feature-summary: 独立保存工资收入，按月年统计纯收入与现金类别，并支持与样例版式一致的 XLSX 导入导出。 -->
<!-- last-verified: 2026-11-04 -->
<!-- code-basis: HEAD b82e98e8ba27; worktree-digest sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 -->

## 快速上下文

底部“工资”页面提供工资和福利收入的独立记录、统计及 Excel 导入导出。工资保存在独立 caiji-salary.db 中，不计入账本账单或账本加密备份。支持按月、年、全部统计毛收入、扣款、纯收入及现金/非现金金额。

仅支持 .xlsx。SalaryXlsxCodec 使用样例格式模板生成和解析 Sheet1。导入先预览校验，确认后在单一 Room 事务内写入。

## 目标与边界

### 目标

- 单独记录工资、补贴、积分、卡券及退税收入。
- 按月、年及全部记录统计，并查看单笔工资。
- 通过样例版式工作簿导入和导出。

### 范围内

- 记录日期、收入项、毛收入、工会费、养老保险、医疗保险、失业保险、公积金、企业年金及税。
- 提供样例全部 17 种收入项；支持创建名称唯一且明确标记现金性质的自定义项目。
- 自动计算纯收入并按期间统计现金和非现金合计。
- 预览 Excel 行错误、重复记录和收入项后确认导入；支持导出全部或当前期间。

### 范围外

- .xls、云同步、多币种及服务器接口。
- 图表分析；当前底部图表入口只显示占位页面。
- 将工资记录纳入账本加密备份。

### 不变量

- 金额以整数分保存，汇总使用 BigInteger。
- 毛收入和税以外扣款非负；税允许负数；空值按零处理；纯收入不得为负。
- 工资日期不能晚于当前日期。
- 导入存在任何错误行时整批禁止写入。

## 业务行为

- 总览提供月、年、全部期间和现金类别筛选；摘要按选择期间统计。
- 录入及编辑支持七项扣款折叠和即时纯收入计算；删除前确认。
- 项目管理按名称唯一性创建收入类型并指定现金属性。
- 导入只接受样例 11 列结构；校验金额、日期、纯收入、现金性质后呈现预览。
- 导入按日期、项目与全部金额构成的多重集匹配重复笔数；错误、取消及事务失败均不留下部分导入。
- 导出使用 SAF，包含全部收入、现金收入、非现金收入的明细公式、合计和下拉项目。

## 架构与代码地图

- app/src/main/java/app/caiji/android/data/SalaryModels.kt — SalaryEntry、SalaryIncomeType、SalaryReports 与金额验证。
- app/src/main/java/app/caiji/android/data/SalaryDatabase.kt — SalaryDatabase、SalaryDao、工资和收入项 Room 实体及样例目录。
- app/src/main/java/app/caiji/android/data/SalaryRepository.kt — RoomSalaryRepository、工资 CRUD、重复统计和原子导入。
- app/src/main/java/app/caiji/android/data/SalaryXlsxCodec.kt — OOXML 解析、样例版式导出及文件边界保护。
- app/src/main/java/app/caiji/android/ui/SalaryScreens.kt — 工资总览、表单、收入项、导入及导出界面。
- app/src/main/java/app/caiji/android/MainActivity.kt、CaijiApplication.kt — 导航、SAF 与独立工资数据库创建。
- app/src/main/assets/salary-template.xlsx — 删除账目内容后保留的格式模板。

## API、事件与任务

### RoomSalaryRepository.save

- 校验日期、金额、扣款和收入类型后新增或更新一笔记录；失败由调用页面展示，不覆盖数据库内容。

### RoomSalaryRepository.importNew

- 输入经过预览校验的工资列表及需创建的自定义收入项；在一个事务内再次校验类型和重复多重集，再原子写入。错误时整笔事务回滚。

### SalaryXlsxCodec.importPreview

- 输入 XLSX、当前项目列表和当前日期，返回有效工资行、错误行及工作簿携带的自定义项目。不改数据库。

### SalaryXlsxCodec.export

- 按样例格式把工资行和项目性质写入 XLSX 输出流；模板和明细均不依赖网络。

## 数据模型与迁移

新增独立 Room schema 1，文件为 caiji-salary.db。salary_income_types 用名称唯一索引；salary_entries 以外键关联项目，保存稳定 ID、本地日期、八项金额及创建/更新时间。账本数据库 schema 1 和账本备份格式 2 未更改。

## 核心流程

1. 首次读取时事务性插入 17 种固定样例收入项。
2. 总览加载工资及项目，由 SalaryReports 按期间聚合。
3. 表单将人民币转换为整数分，验证后通过 Repository 保存。
4. XLSX 先完整解析成预览；用户确认后在 Room 事务中重新匹配重复并插入。
5. 导出时从本机读取项目和工资，按日期升序构建工作簿并写入用户选择的 SAF URI。

## 异常与边界条件

- 日期、金额格式、精度或净收入错误 — 显示行号及原因，阻止整批导入。
- 未知项目、现金属性不匹配或净收入缓存不一致 — 显示错误，不写数据。
- 缓存中只有 K 列公式值的样例空行 — 忽略。
- 无效 URI、压缩包过大、部件数量超限、宏、外链、嵌入文件或 XML 外部实体 — 拒绝解析或写出并在界面反馈。
- 用户取消文件选择 — 数据库保持不变。

## 并发与幂等

导入确认时在事务内重读工资并按 SalaryDuplicateKey 多重集重新计数，处理并发确认不会重复累计。项目名称由数据库唯一索引约束。每条保存记录使用新 UUID。

## 安全与权限

工资保存在应用私有数据库；无网络及广泛存储权限。Excel 经 SAF 授权流访问。ZIP 展开总量限制为 16 MiB、部件限制 40 个；XML 禁用 DOCTYPE 和外部实体；拒绝宏、外部链接和嵌入内容。XLSX 是明文文件，由用户选择位置及保管。系统自动备份不包含工资数据库。

## 配置与依赖

Kotlin、Room 2.6.1、Jetpack Compose Material 3，minSdk 26。工作簿格式模板打包在 app/src/main/assets；不新增第三方 Excel SDK 或后端依赖。

## 可观测性与运维

无服务端遥测；解析、数据库及 SAF 错误在页面展示。正式版本沿用既有应用签名。

## 测试与验证

### 已执行

- :app:testDebugUnitTest — 通过；货币边界、负税额、期间汇总、样例结构、XLSX 往返、重复和异常输入。
- :app:lintDebug — 通过。
- :app:connectedDebugAndroidTest — API34 108 项通过，覆盖工资数据库事务、多重集去重、录入和汇总界面。
- 临时本地集成测试实际读取用户样例并核验 180 笔和四项合计；临时测试源码已删除。

### 未执行

- 最终 0.4.0 正式 APK 签名、覆盖升级及 Release 附件校验待发布验证。
- 尚未在用户实体设备和独立桌面 Excel/WPS GUI 上回归；工作簿公式与结构以程序测试覆盖。

## 关键决策

### 工资数据与账本隔离

- **决定**：使用单独工资数据库及 XLSX 备份。
- **原因**：维持账本 Room schema 1 和加密备份格式 2 兼容。
- **取舍**：工资需单独导出。
- **证据**：CaijiApplication、SalaryDatabase 及 Repository 测试。

### 重复工作簿行按多重集处理

- **决定**：使用日期、项目及所有金额字段组成重复键，按重复数量抵消。
- **原因**：允许合法的同日、同项目、同金额多笔收入。
- **取舍**：任一金额字段不同即为不同记录。
- **证据**：SalaryRepositoryTest.multisetImportSkipsExistingCopiesAndKeepsTheRemainingMultiplicity。

## 已知问题与后续工作

### 用户实体设备验证

- **影响**：不同厂商 SAF、系统字体及 WPS/Excel GUI 尚未实测。
- **当前处理**：API34 仪器测试及真实用户样例数值核对已通过。
- **后续动作**：按 Work Item WORK-20261104-003 回归步骤安装正式包并检查 Excel 展示。

## 变更记录

### 2026-11-04 — 添加工资收入记录与 XLSX 导入导出

- **状态**：已完成
- **变化**：新增独立工资数据、统计、项目管理、底栏入口、图表占位页和样例版式工作簿导入导出。
- **原因**：支持单独记录和统计每笔工资及福利。
- **兼容性**：账本 Room schema 1 和加密备份格式 2 未变；新增工资 Room schema 1。
- **验证**：JVM、Lint、API34 108 项仪器及样例 180 笔金额核对通过。

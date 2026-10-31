# 本地加密备份与恢复

<!-- feature-id: local-backup -->
<!-- feature-title: 本地加密备份与恢复 -->
<!-- feature-aliases: backup, 恢复, 加密备份 -->
<!-- feature-status: active -->
<!-- feature-summary: 以用户选择的本地加密文件完整导出或事务性恢复账单和分类。 -->
<!-- last-verified: 2026-10-31 -->
<!-- code-basis: HEAD unborn; worktree-digest sha256:923d6f192377049e497ec56675f6889af190d95c8e18e216a25acb77219ebc75 -->

## 快速上下文

V0.1 在“设置”提供加密全量备份和恢复。通过 Android `Storage Access Framework` 由用户选文件位置；不申请联网或广泛存储权限。生产导出直接从 Room 快照流式写出，恢复按批次在一个 Room 事务内替换数据库。

备份使用格式版本 2、`PBKDF2-HMAC-SHA256` 派生 AES-256 密钥和 `AES-GCM` 认证加密。每份文件使用新的随机盐和 nonce。密码不会保存到数据库、文件偏好或系统状态；忘记密码无法解密备份。

## 目标与边界

### 目标

- 用户可将全部本机账单和分类保存为密码保护文件。
- 在替换现存账本前另存用户选择的加密备份，并在失败时保留原账本。

### 范围内

- 包含账单金额、收支类型、日期、备注、分类 ID、时间戳及分类名称、类型、图标、排序和启用状态。
- 使用系统文件选择器导出与选择恢复文件；导入先解密、验证并显示数量与收支汇总。
- 对非空账本必须先成功另存当前数据；随后在单一事务内全量替换。
- 总备份文件大小及账单总数没有应用层上限；导出用 `Room cursor`，导入按 500 笔批次处理。
- 单条备注和分类名称最多 20,000 个字符；密码至少 8 个字符，UTF-8 编码不超过 4,096 字节。

### 范围外

- 云备份、网络传输、账号同步、自动定期备份、导入其他账本产品格式或明文 CSV。

### 不变量

- 每次导出生成随机 16 字节盐和 12 字节 nonce；`AES-GCM` 标签和文件头参与认证。
- 恢复前完整读取到加密认证标签并验证内容；分类 ID、类型、金额、日期和文本字段必须有效。
- 非空账本仅在预备份写出成功后才开始替换；整库替换与解密记录写入同属 Room 单事务。
- 导入失败、数据损坏、错误密码或事务异常后回滚全部表；恢复会话锁定应用写入。
- 文件格式版本通过文件头校验；当前只接受版本 2。

## 业务行为

- 导出时输入至少八字符密码及二次确认，再由系统“创建文档”选择目的地；输出为 .cjbackup 加密文件。
- 恢复时由系统“打开文档”选择文件并输入密码；应用解密全部内容、验证引用和收支，并显示分类数、账单数及总额摘要。
- 若当前账本只有默认分类且没有账单，可直接事务恢复；有账单或自定义分类时，须先另选位置保存当前加密备份。
- 确认保存位置后，应用冻结写入、以当前快照写出预备份，再在 Room 事务中替换账本；完成后刷新首页。
- 用户取消文件选择不修改账本；恢复前文件保存失败、输入源变化或解密失败时显示错误并保留现有数据。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `SettingsPage`、`RestoreFlow`、SAF 文件选择器：密码表单、摘要确认、预备份/恢复状态及文件流。
- `app/src/main/java/app/caiji/android/backup/BackupCodec.kt` — `BackupCodec`、`BackupSummary`：版本化编码、密码派生、`AES-GCM`、字段验证、预览、流式解密和 500 笔写入批次。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — `exportBackup`、`replaceAllForRestore`、`beginRestoreSession`：连接 Repository 与 codec。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — `withBackupSnapshot`、`hasExistingData`、`replaceAllForRestore`：一致性快照、写入门闩和整库事务。
- `app/src/main/java/app/caiji/android/data/RestoreWriteGate.kt` — `RestoreWriteGate`：排空已开始写入并冻结恢复期间写操作。
- `app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml` — 禁止系统备份账单数据。

## API、事件与任务

### `BackupCodec.exportTo`

- 接收密码、分类列表、账单总数、输出流和异步账单生产器；以当前文件版本写出所有字段并关闭加密流，但保留外层 `SAF` 流。
- 错误密码规则、无效字段、计数不一致、加密或输出异常以 `BackupException` 报告。

### `BackupCodec.preview` / `BackupCodec.restore`

- `preview` 完整消费用户选择的输入流，验证 GCM 标签、结构、引用及记录值，返回 `BackupSummary`，不保留账单列表。
- `restore` 将记录通过 `LedgerRestoreSink` 按批次交给 Repository 事务；任何末尾认证错误由事务回滚。
- 预览和恢复得到的内容摘要必须一致，防止预览后文件被替换。

## 数据模型与迁移

备份头包含 magic CAIJI、格式版本、`KDF` 轮数、盐和 nonce；头作为 GCM AAD。版本 2 密文正文有分类数及逐字段分类数据、`Long` 账单数及逐笔账单。字符串采用 UTF-8 长度前缀；字段编码上限为 80,000 字节，对应允许的 20,000 个 Unicode 字符。备份数量及汇总使用 `Long` / `BigInteger`。

当前 `Room schema` 是首次版本 1；文件格式版本独立于 `Room schema`。应用代码没有迁移既有备份版本 1；未发布过更早兼容格式，生产界面只产生版本 2。

## 核心流程

1. 导出：在 Room 只读事务内取得分类、记录总数和排序 cursor；`BackupCodec` 逐笔验证并流式写入 `SAF` 输出流。
2. 预览：读取文件头参数并派生密钥；解密类别与账单，校验全部引用、日期和字段，读取到末尾以验证 GCM 标签，再计算摘要。
3. 备份当前数据：若账本非空，先取得密码和用户选择目的地；开始恢复会话等待现有写操作完成，锁定后从一致快照生成加密预备份。
4. 恢复：同一写入锁下清除原行并插入新类别；解析器每满 500 笔调用 sink，Room 在同一 `withTransaction` 中提交或整体回滚。
5. 校验二次内容摘要一致后结束恢复会话并刷新 UI；失败路径在 `finally` 中解锁，事务恢复原数据。

## 异常与边界条件

- 密码不足八字符或错误 — 导出按钮禁用或解密失败，原数据不变。
- 不支持版本、畸形字段、引用不存在、金额/日期无效、密文被修改 — 拒绝恢复；已开始事务则整体回滚。
- 用户取消 `SAF` 选择或预备份目的地写入失败 — 取消恢复，不触碰当前数据库。
- 预览与恢复期间文件内容变化 — 摘要不匹配，Room 事务回滚。
- 大记录集 — 使用 cursor 和批处理避免将全部账单保存在应用内存；分类表与当前月份列表仍按 Android/Room 可用内存读取。

## 并发与幂等

- `RestoreWriteGate` 用 `Mutex` 串行化账本写入和恢复会话；开始恢复前等待正在进行的用户写入。
- 恢复期间普通新增、编辑、删除和分类修改会失败，只有当前恢复过程可以进入替换事务。
- 恢复事务无法中途中止成半库；取消和异常通过事务回滚，并在 `NonCancellable` `finally` 释放门闩。
- 重复点击保存由 UI `saving` 状态阻止；未采用后台重试、网络任务或重复投递。

## 安全与权限

- `KDF` 为 `PBKDF2-HMAC-SHA256`，导出使用 600,000 轮；密钥 256 位，`AES/GCM` 使用 128 位认证标签。
- 每次导出 `SecureRandom` 生成全新 16 字节盐和 12 字节 nonce；`KDF` 参数范围校验，文件头认证。
- 密码仅在页面生命周期的内存态使用，不写入日志、持久化偏好或 Room。
- 使用 `Storage Access Framework` 用户选择的 URI；无需 `INTERNET`、`READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE` 等权限。
- Android 自动整机/云备份被禁用；签名私钥不是备份文件的一部分并留在开发机私有目录。

## 配置与依赖

- 应用格式版本为 2；加密轮数、盐长、nonce 长和记录批量在 `BackupCodec` 常量定义。
- 依赖 Android `Storage Access Framework`、Java Cryptography Architecture、PBKDF2、`AES-GCM`、AndroidX Room transaction 和 Kotlin Coroutines。
- 没有后端、云服务、外部密钥管理服务或外部 SDK。

## 可观测性与运维

- 成功、取消与失败通过设置页内联提示反馈；不输出密码、明文账单、备注、密文或摘要日志。
- 用户必须在设备外保管备份密码；密码遗失后没有找回机制。
- 版本后续若更改备份正文，必须更新格式版本并明确兼容策略；Room 更新仍须提供显式迁移。

## 测试与验证

### 已执行

- `.\gradlew.bat testDebugUnitTest` — 通过；往返、随机盐/nonce、错误密码、篡改、不支持版本、非法记录引用、流式预览/恢复摘要及文本边界测试通过。
- `.\gradlew.bat compileDebugAndroidTestKotlin` — 通过；整库事务回滚与 Room 持久化测试源码编译成功。
- `.\gradlew.bat lintDebug` — 通过，0 errors、2 个工具建议警告。
- `.\gradlew.bat assembleRelease` — 通过；签名、包名、API floor、文件对齐及 APK SHA-256 已核验。

### 未执行

- `connectedDebugAndroidTest` — 因 `No connected devices!` 未运行；当前没有 API 26 镜像，API 34 模拟器报告实例冲突无法启动。
- `SAF` 真实文件选择、系统拒绝保存、已安装 APK 覆盖升级、Room 事务在设备上的回滚由用户设备回归完成。

## 关键决策

### 大账本使用流式备份

- **决定**：不设账单总数或备份文件字节上限；导出从 `Room cursor` 逐条处理，恢复按 500 笔批次写入单事务。
- **原因**：用户确认优先支持更大的完整备份，不以用户录入限额解决容量问题。
- **取舍**：用户明确确认保留单条备注及分类名称 20,000 字符上限；Room 和设备物理容量仍是平台实际限制。
- **证据**：Room 计数为 `Long`，备份格式计数为 `Long`；`BackupCodecTest.streamsPreviewAndRestoreAndDetectsChangedContent`。

### 非空账本恢复前必须另存

- **决定**：由系统文件选择器选择恢复前备份位置，成功写出后才替换。
- **原因**：避免误操作或恢复失败使唯一的本机数据不可恢复。
- **取舍**：多一次文件位置选择与加密写入时间。
- **证据**：`MainActivity` 恢复状态机、`RestoreWriteGate` 与 `LedgerRepositoryTest.failedFullReplacementRollsBackTheExistingLedger`。

## 已知问题与后续工作

### 设备端完整性回归待执行

- **影响**：`SAF` 授权、长时文件流、加密恢复和实际 Room 事务尚无设备运行证据。
- **当前处理**：JVM codec 测试通过，Room 事务仪器测试源码已编译；当前环境没有可运行设备。
- **后续动作**：用户在测试账本完成正确/错误密码、取消、文件篡改和事务失败后的原数据比对。

## 变更记录

### 2026-10-31 — V0.1 首版

- **状态**：已完成
- **变化**：增加格式版本 2 的 `AES-GCM` 全量备份、流式预览和事务恢复。
- **原因**：支持离线数据迁移并避免大账单文件整体载入内存。
- **兼容性**：`Room schema` 1；当前只读取备份格式 2。
- **验证**：Codec 单测、Room/`SAF` 仪器源码编译、lint 与 release APK 检查通过；设备运行未执行。

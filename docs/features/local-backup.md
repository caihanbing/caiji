# 本地加密备份与恢复

<!-- feature-id: local-backup -->
<!-- feature-title: 本地加密备份与恢复 -->
<!-- feature-aliases: backup, 恢复, 加密备份 -->
<!-- feature-status: active -->
<!-- feature-summary: 以用户选择的本地加密文件完整导出或事务性恢复账单和分类。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD 7e9cc5743f1c; worktree-digest sha256:21fc9ce0fc332f187f08394a6f8e3fc4c4e5ed08104c1fe9fcb206d1fbbc4da1 -->

## 快速上下文

财记 0.2.0 的“设置”提供加密导出和恢复。通过 Android Storage Access Framework 由用户选择 `.cjbackup` 文件和位置；不申请联网或广泛存储权限。导出从 Room 一致快照流式写出，恢复按批次在单个 Room 事务内替换数据库。

备份格式当前为版本 2：`PBKDF2-HMAC-SHA256` 派生 AES-256 密钥，使用 `AES-GCM` 认证加密，每份文件使用新的随机盐和 nonce。密码仅在页面内存使用；忘记密码无法解密。

## 目标与边界

### 目标

- 将本机全部账单与分类导出成加密文件。
- 预览、确认后安全恢复账本；已有数据时先另存当前备份。

### 范围内

- 导出账单金额、类型、日期、备注、分类 ID、时间戳，以及分类名称、类型、图标、排序和启用状态。
- 使用系统文件选择器导出或选取文件；解密后显示账单数、分类数和收支总额。
- 对非空账本，必须先成功写出当前加密备份再替换数据。
- 备注、分类名最多 20,000 字符；密码至少 8 字符、UTF-8 长度不超过 4,096 字节。
- 导出使用 Room cursor；导入按 500 笔批次写入同一事务。

### 范围外

- 云备份、账号同步、自动定时、明文 CSV、导入其他产品格式或密码找回。

### 不变量

- 当前文件格式仅接受版本 2；文件头参与 GCM AAD 认证。
- 0.2.0 扩充图标白名单但保持版本 2 文件结构；旧图标键的 0.1.1 备份可以恢复，含新增图标键的 0.2.0 备份不能由 0.1.1 恢复。
- 恢复验证完整密文认证、字段和值及分类引用。
- 非空账本须成功保存预备份后才能替换；恢复写入必须为原子事务。
- 错误密码、损坏、文件变化、取消及事务异常不得留下部分替换。
- 恢复会话期间，`RestoreWriteGate` 拒绝普通账本写入。

## 业务行为

- 导出时要求至少八字符密码及二次确认，再由“创建文档”选择位置。
- 恢复时选择文件并输入密码；解密验证完成后先显示数量及收入/支出汇总。
- 只有默认分类且没有账单时，确认后直接恢复；存在账单或自定义分类时，会要求另存当前加密备份。
- 当前备份保存成功后开始恢复；操作期间展示不可关闭的处理中对话框。
- 恢复成功显示绿色结果反馈；取消和错误分别显示提示/错误反馈，当前数据保持原状。
- 用户取消文档选择也会得到提示，不会触碰数据库。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `RestoreFlow` 状态机、SAF 选择器、Room/Codec 协调和恢复写锁。
- `app/src/main/java/app/caiji/android/ui/BackupDialogs.kt` — 密码、恢复摘要、处理中和成功/取消/错误反馈组件。
- `app/src/main/java/app/caiji/android/ui/SettingsScreen.kt` — 设置页导出密码及导入入口。
- `app/src/main/java/app/caiji/android/backup/BackupCodec.kt` — `BackupCodec`、`BackupSummary`、版本 2 编码、密钥派生、GCM、验证和流式预览/恢复。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — `exportBackup`、`replaceAllForRestore`、恢复会话代理。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — 一致快照、`hasExistingData`、写门闩和整库事务。
- `app/src/main/java/app/caiji/android/data/RestoreWriteGate.kt` — 恢复期间冻结普通写入。
- `app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml` — 禁止 Android 系统备份账本。

## API、事件与任务

### `BackupCodec.exportTo`

- 接收密码、分类、账单总数、输出流和账单异步生产器；逐条验证后写出格式 2 文件。
- 密码规则、字段、计数、加密和输出错误通过 `BackupException` 报告。

### `BackupCodec.preview` / `BackupCodec.restore`

- `preview` 完整解密验证并返回 `BackupSummary`，不保留账单列表。
- `restore` 通过 `LedgerRestoreSink` 按批次写入 Repository 事务。
- 预览与恢复的内容摘要必须一致，防止文件在两次读取间被替换。

## 数据模型与迁移

备份头包含 magic CAIJI、格式版本、KDF 轮数、盐和 nonce；头作为 GCM AAD。正文保存分类和账单字段，字符串使用 UTF-8 长度前缀。`Room schema` 为 1，备份文件版本与 Room schema 独立；本次未更改数据库结构或备份格式。

## 核心流程

1. 导出在 Room 只读事务获取分类、记录数和排序 cursor，再逐条流式写到 SAF 输出。
2. 预览派生密钥，解密并验证分类、账单字段、引用和 GCM 标签，再计算摘要。
3. 非空账本恢复时先锁定普通写入，从一致快照写出用户选定的预备份文件。
4. Repository 在一个 `withTransaction` 内清除旧行、写入分类并每 500 笔追加账单。
5. 再次校验摘要一致，释放恢复锁并刷新页面；失败时事务回滚并在 `finally` 解锁。

## 异常与边界条件

- 密码少于 8 字符 — 导出确认不可用；恢复密码错误或文件损坏 — 显示错误并保留旧库。
- 不支持文件版本、畸形字段、无效引用、日期或金额 — 拒绝恢复；已开始事务整体回滚。
- 用户取消选择源文件或预备份目标 — 显示取消/未更改提示，不更新账本。
- 预览后文件变化 — 摘要不匹配并回滚。
- 大账本 — 导出使用 cursor，导入分批写入；文件大小仍受设备存储/Room 容量约束。

## 并发与幂等

- `RestoreWriteGate` 等待已开始写入并冻结新的写操作。
- 恢复期间普通新增、编辑、删除和分类操作会被拒绝。
- 全量恢复在一个 Room 事务中执行；异常时全部回滚，非可取消 `finally` 释放门闩。
- UI `saving` 状态阻止密码表单重复提交；没有网络重试或重复投递。

## 安全与权限

- 使用 600,000 轮 PBKDF2-HMAC-SHA256 派生 256 位 AES 密钥；AES-GCM 使用 128 位标签。
- 每次导出通过 `SecureRandom` 生成 16 字节盐和 12 字节 nonce。
- 密码不写入 Room、持久化偏好或日志；用户必须在设备外保管密码。
- 通过 Storage Access Framework 使用用户选择的 URI，无需 INTERNET 或广泛存储权限。
- 禁用 Android 自动应用数据备份；release 签名密钥不包含在用户备份中。

## 配置与依赖

- 当前应用版本 0.2.0/code 3；备份格式仍为版本 2，Room schema 1。
- 依赖 Java Cryptography Architecture、PBKDF2、AES-GCM、Android Storage Access Framework、Room transaction 和 Coroutines。
- 无后端、云密钥服务或外部备份 SDK。

## 可观测性与运维

- 成功、取消、错误及处理中状态通过统一暖色反馈组件显示；不记录密码、账单明文或密文日志。
- 0.2.0 沿用 0.1.1 release 签名并可覆盖升级；新增图标键的向后备份兼容限制和回滚步骤见 `docs/release/macOS-install.md`。
- 后续若改变正文格式，必须升级格式版本并明确兼容策略。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug` — 通过；API34 AVD 最终回归 49 项仪器测试。
- `BackupCodecTest` 覆盖正确/错误密码、修改文件、版本、字段边界和流式格式 2；`BackupFeedbackToneTest` 确认损坏文件和未知失败不能显示成成功；`LedgerRepositoryTest.wrongPasswordRestoreRollsBackTheExistingLedger` 验证错误密码后原库不变。
- `BackupCodecTest.roundTripsCategoriesUsingNewIconKeysWithoutChangingBackupFormat` 覆盖新增图标键；API34 AVD 真实恢复旧文件后覆盖升级并核对数据库仍在。
- AVD SAF 实测：导出 `.cjbackup`，查看预览摘要，另存恢复前备份，取消恢复保持原数据，之后成功恢复。
- 在旧 0.1.1 release 安装中从 Downloads 真实 SAF 导入旧格式 2 文件，再覆盖安装 0.2.0 并验证账本仍在；旧文件摘要为 1 笔账单、16 个分类、收入 ¥0、支出 ¥1。

### 未执行

- 未在 Android API 26 设备实际运行 SAF/加密；最低版本已由 APK metadata 核对为 API 26。
- 用户自己的数据和设备行为仍需用户实体设备最终回归。

## 关键决策

### 大账本使用流式备份

- **决定**：导出从 Room cursor 逐条处理，恢复按 500 笔批次进入单事务；备份格式保持 2。
- **原因**：避免把所有账单一次载入内存，并保持完整账本恢复原子性。
- **取舍**：单条备注/分类名称仍有 20,000 字符限制，实际文件大小受设备存储约束。
- **证据**：`BackupCodecTest.streamsPreviewAndRestoreAndDetectsChangedContent`、`BackupCodec.kt` 格式版本常量。

### 非空账本恢复前必须另存

- **决定**：只有预备份成功后才替换非空账本；取消或失败保留现有数据库。
- **原因**：避免恢复错误或误操作丢失本机唯一数据。
- **取舍**：增加一次 SAF 位置选择与加密写入等待。
- **证据**：API34 AVD 预备份/恢复流程、`LedgerRepositoryTest.failedFullReplacementRollsBackTheExistingLedger`、错误密码回滚用例。

## 已知问题与后续工作

### 用户实体设备回归

- **影响**：用户自己的数据和真实设备行为仍需最终确认。
- **当前处理**：API34 AVD 用 0.1.1 的旧格式文件恢复账单，然后同签名覆盖到 0.2.0，数据库内容保留。
- **后续动作**：用户按 release guide 在自有 Android 8.0+ 设备核对账单和分类。

## 变更记录

### 2026-11-01 — 新增分类图标的备份兼容

- **状态**：已完成
- **变化**：48 图标白名单加入格式 2 恢复校验；加密、SAF 文件流程、数据库事务及文件结构保持不变。
- **原因**：财记扩充分类图标且继续兼容旧备份。
- **兼容性**：0.1.1 格式 2 备份可由 0.2.0 恢复；含新键的 0.2.0 文件不能由 0.1.1 恢复；Room schema 1。
- **验证**：新增图标键加密 round-trip 单测；API34 AVD 真实恢复 0.1.1 导出的账单文件成功。

# 本地加密备份与恢复

<!-- feature-id: local-backup -->
<!-- feature-title: 本地加密备份与恢复 -->
<!-- feature-aliases: backup, 恢复, 加密备份 -->
<!-- feature-status: active -->
<!-- feature-summary: 以用户选择的本地加密文件完整导出或事务性恢复账单和分类。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD 337efbd2d13f; worktree-digest sha256:8544c0565bd9308207c73a340fba06f1ee42362709f879c40dda93505091d34c -->

## 快速上下文

财记 0.1.1 的“设置”提供加密导出和恢复。通过 Android Storage Access Framework 由用户选择 `.cjbackup` 文件和位置；不申请联网或广泛存储权限。导出从 Room 一致快照流式写出，恢复按批次在单个 Room 事务内替换数据库。

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

- 当前应用版本 0.1.1/code 2；备份格式仍为版本 2，Room schema 1。
- 依赖 Java Cryptography Architecture、PBKDF2、AES-GCM、Android Storage Access Framework、Room transaction 和 Coroutines。
- 无后端、云密钥服务或外部备份 SDK。

## 可观测性与运维

- 成功、取消、错误及处理中状态通过统一暖色反馈组件显示；不记录密码、账单明文或密文日志。
- 新签名 release APK 的安装和回滚步骤见 `docs/release/macOS-install.md`；旧版卸载前先导出加密备份。
- 后续若改变正文格式，必须升级格式版本并明确兼容策略。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug` — 通过；API34 AVD 320dp、`font_scale=1.3` 下 34 个设备用例通过。
- `BackupCodecTest` 覆盖正确/错误密码、修改文件、版本、字段边界和流式格式 2；`BackupFeedbackToneTest` 确认损坏文件和未知失败不能显示成成功；`LedgerRepositoryTest.wrongPasswordRestoreRollsBackTheExistingLedger` 验证错误密码后原库不变。
- AVD SAF 实测：导出 `.cjbackup`，查看预览摘要，另存恢复前备份，取消恢复保持原数据，之后成功恢复。
- release APK 在卸载 debug 包后全新安装；从 Downloads 导入格式 2 加密备份，恢复后账单/分类数和汇总与摘要一致。

### 未执行

- 未在 Android API 26 设备实际运行 SAF/加密；最低版本已由 APK metadata 核对为 API 26。
- 用户自己的旧版数据需用户在实体设备操作；仓库内无旧 0.1.0 签名 APK 可做真实旧证书覆盖对照。

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

### 用户设备和原版签名回归

- **影响**：用户实际旧数据需要按自己的账本在实体设备回归；没有原 0.1.0 签名 APK 供自动升级对照。
- **当前处理**：release guide 说明先导出并核验加密备份，再卸载旧包；API34 AVD 已完成 release fresh install 与格式 2 restore。
- **后续动作**：用户按 release guide 在自有 Android 8.0+ 设备核对账单和分类。

## 变更记录

### 2026-11-01 — 加密备份与恢复反馈 UI

- **状态**：已完成
- **变化**：密码、恢复摘要、处理中及成功/取消/错误反馈使用统一样式；恢复前备份和事务/Codec 流程保持不变。
- **原因**：用户确认“轻盈账本”完整设计。
- **兼容性**：备份格式 2、Room schema 1、SAF 调用和密码规则均不变。
- **验证**：API34 AVD 真实 SAF 导出、取消、预备份和恢复成功；新 release 全新安装后导入格式 2 备份成功。

# 应用版本更新

<!-- feature-id: app-updates -->
<!-- feature-title: 应用版本更新 -->
<!-- feature-aliases: 检查更新, 版本更新, Gitee Release, GitHub Release -->
<!-- feature-status: active -->
<!-- feature-summary: 设置页手动检查及前台自动检查均先查询 Gitee 正式 Release，失败后回退 GitHub，并提示高于当前安装版本的更新。 -->
<!-- last-verified: 2026-11-11 -->
<!-- code-basis: HEAD 7dd1b1e08116; worktree-digest sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 -->

## 快速上下文

设置页“关于财记”提供手动检查更新；Android 进程启动及从后台回到前台时，由 `ProcessLifecycleOwner` 自动检查。手动和自动均先查 Gitee `caihanbingo/caiji` 最新正式 Release，仅当 Gitee 网络、HTTP 或返回内容校验失败时，再查 GitHub `caihanbing/caiji`。Gitee 成功返回同版本或旧版本属于有效结果，不再请求 GitHub。版本标签要求 `v主版本.次版本.修订版本`，使用数值顺序比较。

只有高于当前版本的 Release 会提示。用户可稍后处理或在浏览器打开 Release 附带 APK；APK 缺失时打开 Release 页面。检查不上传账本信息，不修改数据库或备份文件。

## 目标与边界

### 目标

- 自动发现高于当前安装版本的正式版更新。
- 提供手动检查并在设置页反馈检查中、已是最新和失败状态。

### 范围内

- 冷启动及进程返回前台自动检查，合并重叠的网络请求。
- 手动检查显示“已是最新版本”或失败提示；自动无更新或失败时不打断记账。
- 版本高于当前版本时展示版本号和 Release 说明；打开 APK 链接或 Release 页面。
- 当前进程内推迟同一更新后不再自动提示；手动检查仍可提示。

### 范围外

- 应用内下载、安装器授权与静默安装。
- GitHub 身份验证、预发布版本检查、强制升级及后台定时检查。

### 不变量

- 只接受严格格式的 `vMAJOR.MINOR.PATCH` 标签，并按数值比较。
- Gitee Release 与 APK 链接只接受 HTTPS `gitee.com` 主机；GitHub 链接只接受 HTTPS `github.com` 主机。Gitee 未提供 Release 页面链接时，根据固定仓库地址及已验证标签生成。
- 自动检查失败保持静默；用户手动操作须获得结果反馈。
- 用户账本、工资、反馈、备份及密码不随更新请求发送。

## 业务行为

- 应用进程进入前台时先请求 Gitee 最新正式 Release，Gitee 请求或正式 Release 数据校验失败时请求 GitHub；网络异常不阻止页面加载或本地数据读写。
- 设置页检查入口显示当前版本及“优先检查 Gitee，失败后尝试 GitHub”；检查期间禁用重复点击，完成后显示最新或失败状态。
- 高版本对话框显示当前版本、最新版本和 Release 说明。“稍后再说”抑制本进程内同一版本；“下载更新”使用系统浏览器。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/update/ReleaseVersion.kt` — `ReleaseVersion`：严格解析和数值比较版本标签。
- `app/src/main/java/app/caiji/android/update/GitHubReleaseSource.kt` — `AppRelease`、`ReleaseSource`、`OrderedReleaseSource`、`GiteeReleaseSource` 与 `GitHubReleaseSource`：合并各源在途请求，按 Gitee→GitHub 顺序回退，解析元数据并按来源验证链接。
- `app/src/main/java/app/caiji/android/update/UpdateManager.kt` — `UpdateManager`：自动/手动结果状态、版本过滤和本进程提示抑制。
- `app/src/main/java/app/caiji/android/CaijiApplication.kt` — `ProcessLifecycleOwner.onStart` 触发自动检查，并提供进程级 manager。
- `app/src/main/java/app/caiji/android/ui/SettingsScreen.kt`、`ui/UpdateAvailableDialog.kt`、`MainActivity.kt` — 手动入口、更新说明与冲突时序展示。

## API、事件与任务

### `ReleaseSource.latest(): AppRelease`

- Gitee 获取 `https://gitee.com/api/v5/repos/caihanbingo/caiji/releases/latest`；GitHub 获取 `https://api.github.com/repos/caihanbing/caiji/releases/latest`，每个来源连接和读取超时各 10 秒。
- 每个来源的并发调用共享在途请求；HTTP、JSON、标签及链接异常使优先来源失败并触发后备来源；取消直接传播，不触发回退。

### `UpdateManager.check(manual: Boolean)`

- 比较已安装版本和 Release 版本；自动无更新或失败时回到静默状态，手动检查发布 `UpToDate` 或 `Failed`。
- 更高版本发布 `Available`；`dismissAvailable()` 在进程内抑制该 tag 的自动提示，手动检查仍可重新显示。

## 数据模型与迁移

不增加持久数据实体或迁移。当前应用版本为 `0.5.11/code26`，版本值取自 `BuildConfig.VERSION_NAME`；推迟的版本仅保存在 `UpdateManager` 内存中，进程重启后恢复提醒。

## 核心流程

1. `CaijiApplication` 观察 `ProcessLifecycleOwner`，每次进程 `ON_START` 调用自动检查。
2. `OrderedReleaseSource` 先请求 Gitee，`GiteeReleaseSource` 校验 HTTPS 主机、正式 Release 和 `vMAJOR.MINOR.PATCH` 标签；有效 Release 页面 URL 缺失时按标签生成固定 Gitee 页面地址。
3. Gitee 请求或数据校验失败时，`GitHubReleaseSource` 请求 GitHub 并按 GitHub 主机校验链接。
3. `UpdateManager` 将版本与应用当前版本数值比较；相同或更低版本在自动检查时静默返回。
4. 用户手动检查时，设置行显示进度及最新/失败结果。新版本由应用级对话框展示。
5. 用户选择下载时由系统浏览器打开 APK，缺少附件时打开 Release 页面；Intent 失败时设置页显示失败状态。

## 异常与边界条件

- 网络断开、HTTP 错误或超时 — 自动检查静默结束，手动检查显示失败；再次进入前台或手动重试。
- 标签或 JSON 无效、正式 Release 校验失败、Gitee 链接主机不合法 — 视为 Gitee 失败并回退 GitHub。GitHub 的标签、JSON 或链接无效时，手动显示失败、自动不提示。
- Release 不含匹配 APK 附件 — 打开 Release 页面。
- 浏览器 Intent 无可用处理应用 — 关闭更新提示并在设置页显示检查失败。

## 并发与幂等

在途请求由 `GitHubReleaseSource` 的共享 `Deferred` 合并；完成或失败后清除以允许下一次检查。提示抑制以 Release tag 为键保存在内存中。不同更新 tag 可正常提示。

## 安全与权限

仅请求固定的 Gitee/GitHub Release API，无认证凭证。Release 页面和 APK 按来源仅接受 HTTPS `gitee.com` 或 `github.com` 主机。应用联网权限仅用于版本检查；账本、工资、反馈、备份和密码不会上传。

## 配置与依赖

AndroidManifest 声明 `android.permission.INTERNET`。运行时依赖 `androidx.lifecycle:lifecycle-process` 和 Kotlin Coroutines。无服务端配置或用户开关。

## 可观测性与运维

无遥测、持久检查历史或服务端运维。错误显示在手动检查行，自动失败不弹窗。手动和自动均优先检查 Gitee，只有 Gitee 失败才查询 GitHub；两源均失败时沿用原有手动/自动状态行为。发布附件约定为 `caiji-<version>-release.apk`；名称不匹配或附件缺失时打开 Release 页面。

## 测试与验证

### 已执行

- `:app:testDebugUnitTest :app:lintDebug` — 通过；覆盖版本比较、非法标签、请求并发合并、失败重试、自动/手动结果和版本提示抑制。
- API26 `:app:connectedDebugAndroidTest` — 全量 126 项通过；API34 最终全量 127 项通过；两端最后各 7 项更新相关仪器测试通过。
- `./scripts/build-release-macos.sh` — 0.5.0/code15 正式签名包、包身份、SDK、zipalign、证书和 SHA-256 校验通过。
- API34 正式签名 v0.4.2 新增 ¥3.00 购物账单后覆盖安装正式 v0.5.0 — 账单保留，安装后 versionCode15。

### 未执行

- 实体设备浏览器下载和安装确认 — 尚未在用户设备确认；API 模拟器验证了更新提示，系统浏览器负责实际下载。

## 关键决策

### 自动联网只检查双平台发布元数据

- **决定**：手动和前台自动检查都先请求 Gitee 最新正式 Release，失败后再请求 GitHub；账本相关本地数据不随请求发送。
- **原因**：优先使用 Gitee 更新源，并在其不可用时回退至 GitHub。
- **取舍**：需要 `INTERNET` 权限；离线且两个源都不可用时无法检查更新。
- **证据**：`OrderedReleaseSource`、`GiteeReleaseSource` 与 `GitHubReleaseSource` 及来源顺序回归测试。

### 浏览器负责安装包下载

- **决定**：用户确认更新后用 ACTION_VIEW 打开 APK，缺少 APK 时打开 Release 页面。
- **原因**：复用仓库 GitHub Release 与系统下载体验。
- **取舍**：安装由用户在系统界面完成；应用不请求安装未知来源权限。
- **证据**：`UpdateAvailableDialog` 调用及 `AppRelease` 的来源 HTTPS URL 校验。

## 已知问题与后续工作

### 实体设备更新安装

- **影响**：浏览器行为和安装确认依 Android 厂商系统而异。
- **当前处理**：API26/API34 验证设置入口和下载动作 UI。
- **后续动作**：用户安装正式 APK 后核对浏览器下载、系统安装和离线记账。

## 变更记录

### 2026-11-11 — 增加 Gitee 优先的双来源更新

- **状态**：已完成
- **变化**：设置手动检查和前台自动检查统一先查 Gitee 正式 Release；仅请求失败或返回数据无效时回退 GitHub。来源链接按各自 HTTPS 主机验证；Gitee 缺少 Release 页 URL 时按合法版本标签生成。
- **原因**：优先使用 Gitee 更新源，Gitee 服务异常时仍保留 GitHub 发现更新的能力。
- **兼容性**：不改变版本标签、版本比较、更新提示抑制、浏览器下载、账本/工资数据库及备份格式。
- **验证**：Gitee 返回解析、来源回退、取消、链接主机、当前版本和无附件边界测试通过；API26/API34 全量各 186 项及 JVM/Lint 通过；发布交付待 WORK-20261111-002 最终核实。

### 2026-11-06 — 调整设置页更新说明

- **状态**：已完成
- **变化**：设置页更新入口说明为“检查 GitHub 上的新版本”，与当时只检查正式 Release 的能力一致。
- **原因**：让入口文字直接说明检查来源和动作。
- **兼容性**：仅调整设置说明文案；网络请求、版本比较和更新流程不变。
- **验证**：设置页文案仪器测试及当时 API26/API34 全回归通过。

### 2026-11-05 — GitHub 检查更新

- **状态**：已完成
- **变化**：增加进程前台及手动检查正式 Release、版本比较、更新对话框与浏览器下载入口。
- **原因**：让用户从设置和应用启动流程发现新版本。
- **兼容性**：0.5.0/code15；不变更 Room schema 和备份格式；新增仅用于更新检查的 INTERNET 权限。
- **验证**：更新模块 JVM 测试、API26 全量 126 项与 API34 全量 127 项仪器测试、lint 和正式构建通过；API34 v0.4.2→v0.5.0 正式签名覆盖升级后账单保留。

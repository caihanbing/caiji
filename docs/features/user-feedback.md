# 本机问题反馈

<!-- feature-id: user-feedback -->
<!-- feature-title: 本机问题反馈 -->
<!-- feature-aliases: feedback, 问题反馈, 截图分享 -->
<!-- feature-status: active -->
<!-- feature-summary: 在本机记录体验问题及最多 5 张截图，按用户选择通过 Android 分享面板外发。 -->
<!-- last-verified: 2026-11-02 -->
<!-- code-basis: HEAD 8d2f173e8547; worktree-digest sha256:1d2ebaf4cfee15e5d383de512046acad66b3261b4b1bac58dd54648e97b305be -->

## 快速上下文

设置 → 问题反馈是本机的问题清单。问题由标题、描述及最多 5 张选填截图组成，可新建、编辑、分享和确认删除。内容保存在独立的 `caiji-feedback.db` 和应用私有 `files/feedback` 目录。0.3.1 修复草稿图片引用解析，并使五张缩略图可横向浏览。

问题记录与主题偏好均独立于账本数据库和加密账本备份；恢复账本不覆盖反馈。用户点分享后，应用只将当前问题的文字和截图交给系统分享面板，不自动上传。

## 目标与边界

### 目标

- 记录账单/分类/页面的体验问题，包含实机截图。
- 允许用户在选择接收应用前查看/编辑；删除时清理关联文件。

### 范围内

- 设置页入口、新建、列表、编辑和二次确认删除。
- 标题与问题描述必填；截图选填且最多 5 张。
- 使用 Android 系统图片选择器选图，复制到应用私有存储，预览使用降采样缩略图。
- Android Sharesheet 通过 FileProvider 将当前问题文字和已选截图临时只读分享给目标应用。
- 反馈、截图及草稿在账本恢复时不被清空；反馈独立持久化。

### 范围外

- 后端提交、自动上传、账号登录、附件云存储、账本备份嵌入反馈、主题偏好随账本备份。
- 服务端工单状态或用户身份字段。

### 不变量

- 反馈记录 ID 为 UUID；截图按 0 起始 position 保序，外键关联并级联删除数据库行。
- 只有成功复制到私有草稿目录后，截图才能进入编辑器的已选集合。
- 新增记录时标题和描述须有非空首尾空格裁剪结果；图片数量不得超过 5。
- 分享 URI 只映射 `files/feedback/` 私有目录，并授予接收方临时读取权限。

## 业务行为

- 列表按 `updatedAtMillis` 倒序显示标题、描述、最多 3 个缩略图和日期；额外截图显示数量角标。
- 编辑允许修改标题/描述、添加和移除图片；保存成功后更新时间，保留原创建时间。
- 删除需要二次确认；确认后删除 Room 记录并清理该记录的截图文件。
- 分享不包含账单、主题设置或其他问题记录。无截图时使用文本分享；有截图时使用多图分享。
- 选择器取消不会更改当前文字和已选图片；图片读取或存储失败显示错误，保留当前草稿。
- 编辑问题时收到剪贴板记账事件，通知只暂存，离开反馈编辑器后才允许进入账单编辑页。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/data/FeedbackDatabase.kt` — 独立 Room schema 1、反馈与截图 DAO、私有文件导入/删除仓库。
- `app/src/main/java/app/caiji/android/ui/FeedbackScreens.kt` — 反馈列表/编辑、截图缩略图、主题选择页和 FileProvider 分享 Intent。
- `app/src/main/java/app/caiji/android/ui/CommonNavigationComponents.kt` — 反馈列表/编辑和主题页共用的居中标题栏。
- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CaijiApplication.feedbackRepository`、反馈页面路由、选择器回调、删除确认及剪贴板草稿保护。
- `app/src/main/AndroidManifest.xml` 与 `res/xml/feedback_file_paths.xml` — 非导出 FileProvider 和反馈目录窄路径授权。

## API、事件与任务

### `FeedbackRepository.copySelectedImages(draftId, uris, existingCount)`

- 输入系统选图 URI 列表；只接受图片媒体类型并复制到 `cache/feedback-drafts/<draftId>`；返回按原顺序排列的待存路径 reference。
- URI 失效、IO 错误或超过 5 张时抛错，不向 Room 写入记录；调用者保留标题/描述草稿。

### `FeedbackRepository.resolveEditorImage(draftId, reference)`

- 暂存 reference 必须以 `pending:<draftId>/` 开头；返回 `cache/feedback-drafts/<draftId>/<filename>`，草稿 ID 只加入一次。
- 非 pending reference 解析到应用私有 `files/feedback/`；草稿不匹配或文件名含路径分隔符时失败并显示不可用缩略图。

### `FeedbackRepository.save(item, imageRefs, draftId)` / `delete(id, screenshots)`

- 保存先把选中的新图拷贝到私有最终目录，再在 Room 事务中替换反馈与截图行；事务失败时删除此次新增文件。
- 删除记录并清理相关文件；删除失败时不继续清理图片。

### `shareFeedback(context, record, repository)`

- 构造 `ACTION_SEND` 或 `ACTION_SEND_MULTIPLE` chooser；图片仅通过 `content://` URI 和临时 `FLAG_GRANT_READ_URI_PERMISSION` 分享。
- 不执行网络请求；无可用图片文件时只分享标题及描述。

## 数据模型与迁移

- 独立 `FeedbackDatabase`（`caiji-feedback.db`）schema version 1，账本数据库 schema 仍为 1。
- `feedback_items` 主键 UUID，保存标题、描述、创建/更新时间。
- `feedback_screenshots` 以 `(feedbackId, position)` 为主键，保存应用私有目录相对路径；外键 `ON DELETE CASCADE`。
- 图片原文件保存在 `files/feedback/`，临时编辑副本位于 `cache/feedback-drafts/<draftId>`；原数据库不保存图片字节。
- 暂存引用必须匹配当前草稿 ID，仅使用引用中的文件名组成一次缓存路径。
- 反馈和截图不进入现有加密账本备份格式 2。卸载应用会清除反馈和截图。

## 核心流程

1. 设置入口打开本机列表；新建时生成 draft ID，编辑时装载 Room 记录及图片路径。
2. 系统 Photo Picker 返回 URI；仓库在 IO 线程验证媒体类型并复制到 `cache/feedback-drafts/<draftId>/<filename>`；`pending:<draftId>/<filename>` 引用解析一次后用于后台缩略图解码。
3. 保存先将 pending 图片复制到最终私有目录，再用 Room 事务更新反馈行和截图顺序；成功后删除草稿目录和已移除旧图。
4. 列表分享构建仅含当前记录文本及图片 URI 的系统 chooser；不自动发送。
5. 删除确认后移除数据库行和本记录图片；取消编辑删除暂存目录但不动已保存记录。

## 异常与边界条件

- 系统选择器返回空列表 — 保持原表单。
- URI 失效/读取异常/目标目录写入异常 — 保留标题、描述和此前成功导入的截图，并提示重试。
- 已选 5 张继续选图 — 显示数量错误并保留已选图片。
- 本机截图文件丢失 — 缩略图标记不可用；分享跳过丢失文件，其余选定文件和文字可分享。
- 删除时数据库异常 — 由 Room 事务保持记录；文件仅在删除成功后清理。
- ShareSheet 无接收应用/Activity 无法启动 — 显示分享失败提示，不修改反馈记录。

## 并发与幂等

- Room 保存反馈与截图引用处于同一 DB transaction，避免半写关系。
- `saving` 状态关闭重复提交；每次导入/截图文件使用随机 UUID 命名。
- 删除仅接受指定反馈 ID 与其加载到的图片列表。

## 安全与权限

- 反馈和截图保存在应用私有目录，不请求广泛存储或网络权限。
- FileProvider `exported=false`，路径限定 `files/feedback/`；接收方只有显式分享时获得临时 URI 读取权限。
- 不生成日志、不自动上传，也不把图片并入账本备份。

## 配置与依赖

- 使用现有 Room、AndroidX Activity Result Photo Picker fallback、AndroidX Core FileProvider；无新增后端。
- `MainActivity` 将反馈编辑器加入剪贴板待处理保护列表。
- 应用版本 0.3.1/versionCode 8；账本 schema/备份不变，反馈 schema 单独版本 1。

## 可观测性与运维

- 无服务端日志、遥测或后台任务；选图、保存和分享错误通过当前页面错误提示呈现。
- 反馈内容由用户通过系统分享主动外发，不触发网络请求。

## 测试与验证

### 已执行

- `FeedbackRepositoryTest.feedbackRecordsPersistAcrossDatabaseReopenOutsideTheLedgerDatabase` 验证独立数据库跨关闭/重开保留，并验证删除清空关系。
- `FeedbackRepositoryTest.selectedScreenshotIsCopiedPrivatelyAndDeletedWithItsFeedbackItem` 验证选图复制到私有目录、FileProvider URI 可读及删除时同步清理。
- `FeedbackRepositoryTest.unavailableImageUriDoesNotCreateAFeedbackRecordOrAStagedFile` 验证失效 URI 返回错误且不保留暂存文件。
- `FeedbackAndThemeScreenTest.feedbackEditorRequiresTitleAndDescriptionBeforeSaving` 验证必填字段和保存状态。

### 未执行

- 尚未在 API 26 实机调用系统 Photo Picker fallback 和第三方 Sharesheet；当前只有 API 34 模拟器。
- 尚未逐厂商验证图片分享 MIME 渲染；接收应用依其自身支持行为显示附件。

## 关键决策

### 反馈独立于账本备份，本机记录并由用户分享

- **决定**：采用独立 DB/截图目录；账本恢复不覆盖反馈；用户选中系统分享目标后只外发当前记录。
- **原因**：用户选择反馈只保存在本机，并保留显式控制分享对象。
- **取舍**：卸载会删除反馈；反馈不随格式 2 账本备份恢复。
- **证据**：用户确认的备份范围选择、独立 `FeedbackDatabase` 和 FileProvider 设置。

### 截图用暂存文件加 Room 提交

- **决定**：截图先导入 draft cache，保存时复制到最终目录并以 Room transaction 更新关系，失败清理新文件。
- **原因**：系统选择 URI 生命周期不应与反馈草稿和保存事务绑定。
- **取舍**：删除必须同步清理私有图片文件。
- **证据**：三个 `FeedbackRepositoryTest` 数据/文件生命周期用例。

## 已知问题与后续工作

### 老系统选择器和外部分享适配

- **影响**：API 26 选择器 fallback、老版本邮件/消息应用对多图 MIME 的呈现需要实体设备确认。
- **当前处理**：选图回调复制 URI 到私有目录后再保存；分享只授予已选图片 URI。
- **后续动作**：在 Android 8.0+ 实机新增问题、导入图片、分享后验证目标应用收到附件。

## 变更记录

### 2026-11-02 — 0.3.0 本机问题反馈

- **状态**：已完成
- **变化**：新增标题/描述/最多 5 张截图反馈记录、独立数据库与私有图片存储、确认删除和按需 FileProvider 分享。
- **原因**：按用户确认的高保真反馈页记录体验问题。
- **兼容性**：反馈数据库 version 1 独立于账本 schema 1；账本备份格式 2 不含反馈。
- **验证**：反馈 DB/图片生命周期、79 项 API34、JVM、lint 和正式 APK 校验通过；Photo Picker API26 fallback 未能在本机镜像测试。

### 2026-11-02 — 0.3.1 草稿图片预览与反馈顶部栏

- **状态**：已完成
- **变化**：修复暂存文件路径重复拼接，反馈截图横向展示最多五张，反馈列表/编辑使用统一居中标题栏。
- **原因**：修复实机新增图片显示“图片不可用”及反馈页顶部样式不一致。
- **兼容性**：Room schema 1 和分享 URI 合约不变。
- **验证**：真实 PNG 五图暂存编辑仪器测试验证缩略图解码、第五图横滚和反馈标题中心；0.3.0→0.3.1 AVD 覆盖后记录及截图保留；90 项全量测试通过。

# 账单分类管理

<!-- feature-id: entry-categories -->
<!-- feature-title: 账单分类管理 -->
<!-- feature-aliases: categories, 分类 -->
<!-- feature-status: active -->
<!-- feature-summary: 管理收支类别、图标、顺序和启用状态，并保留历史账单关联。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD 35a0d1b123f6; worktree-digest sha256:6677b16332d9d92a617f94a5bfc34803c3557e86564256bbeb962f98cf8721cb -->

## 快速上下文

V0.1 分类管理既可从“设置—分类管理”进入，也可在记账页分类标题行右侧点击“管理分类”进入。记账页入口按当前支出/收入类型打开分类管理；新建账单默认打开支出分类。首次数据库初始化写入 11 个支出与 5 个收入分类。分类由 Room 持久化，账单通过分类稳定 ID 建立外键关联；停用只影响新选择，不清除历史记录。

用户可以新增、改名、选内置图标、排序、停用和重新启用分类。创建后的分类收支类型保持不变；编辑账单时可以切换收支类型，但必须另选目标类型启用分类。

## 目标与边界

### 目标

- 按支出、收入管理可选类别。
- 在历史账单不丢失关联的前提下调整分类名称和状态。

### 范围内

- 支出预置：餐饮、购物、交通、住房、日用、娱乐、医疗、学习、通讯、人情、其他。
- 收入预置：工资、奖金、兼职、理财、其他。
- 新增分类并选择内置图标；编辑时可改名和图标。
- 通过上下移动调整同类型分类顺序；支持停用和重新启用。

### 范围外

- 删除分类、用户上传图标、网络同步分类、多级分类及自定义账本间共享分类。

### 不变量

- 分类 ID 稳定；创建后的收支类型不允许更改。
- 同类型启用分类的去首尾空格名称不能重复；停用项可保留同名，但重新启用前会重新校验。
- 新账单只能选择同类型启用分类；历史账单可继续显示已停用分类。
- 分类名称最多 20,000 个字符，图标只能使用 `CategoryCatalog` 白名单内置键。

## 业务行为

- 首次初始化时若分类表为空，Repository 在事务中插入批准的默认目录；重复初始化不会重复插入。
- 分类管理页面按支出/收入切换并按 `sortOrder` 显示；创建、改名、图标选择、调整顺序、停用和启用通过 Repository 操作。
- 编辑分类时只显示原类型标签，不允许改变类型。
- 同类型重名或数据写入失败时，错误显示在页面，编辑对话框保留输入以便修正。
- 停用分类仍保留数据库行和账单外键，历史账单列表通过该 ID 查询名称和图标。
- 记账页的分类管理入口与分类标题同行；进入管理页时默认选中当前记账类型，返回恢复原表单草稿。设置入口仍默认打开支出分类。
- 新账单从记账页进入分类管理后，如果当前已选分类被停用，返回时自动清空选择并提示“该分类已停用，请重新选择启用分类”；编辑历史账单时允许保留原先关联的停用分类。
- 从记账页新建的分类返回后出现在分类列表中，但不自动选中，由用户明确选择。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/MainActivity.kt` — `CategoryManager`、`CategoryDialog`：分类页面、表单、图标和顺序/启用操作；`EntryEditor` 提供记账期间的快捷入口并保留草稿。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — `saveCategory`、`setCategoryActive`、`reorderCategory`：异步 Repository 调用和错误状态。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — `initialize`、`createCategory`、`renameCategory`、`setCategoryActive`、`reorderCategory`：事务及规则验证。
- `app/src/main/java/app/caiji/android/domain/Category.kt` — `Category`、`CategoryCatalog`：类别模型、预置值、图标白名单与名称唯一性判断。
- `app/src/main/java/app/caiji/android/data/LedgerDatabase.kt` — `CategoryRow`、`LedgerDao`：Room 分类实体与排序读取。

## API、事件与任务

### `RoomLedgerRepository.createCategory` / `renameCategory`

- 输入分类类型、名称、内置图标键，以及改名时的分类 ID；成功返回更新后的 `Category`。
- 空名称、过长名称、无效图标、重复启用名称或不存在的 ID 会拒绝操作，不会部分更新数据库。
- 创建与改名通过 Room 事务串行验证后写入；类型只在创建时确定。

### `RoomLedgerRepository.setCategoryActive` / `reorderCategory`

- 启用前重新检查同类型名称唯一性；停用不会删除已有关联。
- 排序操作只重排当前类型列表，并重新编号该组的 `sortOrder`。

## 数据模型与迁移

`categories` 表的主键为稳定字符串 ID，字段包括 `type`、`name`、`iconKey`、`sortOrder` 和 `active`。`entries.categoryId` 有索引及指向 `categories.id` 的 `RESTRICT` 外键。分类排序索引为 `(type, sortOrder)`，名称另有索引。

首次 schema 是 Room version 1；默认种子由 `CategoryCatalog.defaults()` 提供。当前首版没有既有库迁移；未来修改必须添加显式 Room Migration 并更新 schema 导出，不允许 destructive fallback。

## 核心流程

1. `LedgerViewModel.refresh` 调用 Repository 初始化；表为空时在 Room 事务内插入默认分类。
2. 分类管理页面提交新增或改名；Repository 清理首尾空格并校验名称长度、图标和同类型唯一性。
3. `setCategoryActive` 在重新启用时再次校验唯一性，成功后更新分类列表；排序通过相邻位置重排并持久化。
4. 账单列表用 `categoryId` 查名称和图标；停用不改变历史外键或统计。
5. 记账页进入管理器会传入当前收支类型；从管理器返回后保留金额、日期、备注、类型和分类选择。新账单中的所选分类在管理期间被停用时，选择会清空并显示重新选择提示。

## 异常与边界条件

- 重名、空名称、无效图标或超长名称 — 拒绝保存并显示错误，对话框保留用户输入。
- 启用与现存同名分类冲突 — 提示先改名再启用，当前停用状态保留。
- 分类被历史账单引用 — 禁止通过删除移除，停用仍允许读取旧记录。
- 账单类型切换 — 必须另选目标类型启用分类；原分类不改类型。

## 并发与幂等

- 创建/改名/启用校验和更新均在 Room 事务内执行，避免两个并发动作同时通过名称检查。
- 排序在同一事务中读取并重排该类型列表。
- 首次种子插入使用 `INSERT OR IGNORE`，初始化前后检查空表，避免重复种子。
- 用户操作受 `RestoreWriteGate` 保护，恢复期间拒绝新写入。

## 安全与权限

- 分类和账单在应用私有 Room 数据库中；不调用网络、不申请存储权限。
- 图标使用应用内置键映射到 emoji，不接受外部资源或文件路径。
- Android 自动应用数据备份已禁用。

## 配置与依赖

- Android `minSdk=26`；使用 AndroidX Room、Kotlin Coroutines 和 Compose Material 3。
- 预置分类和图标白名单集中定义于 `CategoryCatalog`；无需后台配置或外部服务。

## 可观测性与运维

- 创建/改名/排序/启用错误由 ViewModel 错误状态在分类页面提示；没有遥测、后台任务或服务器日志。
- 更新版本需保留 Room 数据库并提供兼容迁移；当前首发 schema 为 version 1。

## 测试与验证

### 已执行

- `.\gradlew.bat testDebugUnitTest` — 通过；预置目录和名称去空格规则测试通过。
- `.\gradlew.bat compileDebugAndroidTestKotlin` — 通过；默认种子、唯一性、停用历史、排序和分类保存交互测试源码编译通过。
- `.\gradlew.bat lintDebug` — 通过，0 errors、2 个建议警告。
- `testDebugUnitTest compileDebugAndroidTestKotlin lintDebug assembleDebug` — 2026-11-01 通过；包括记账页分类管理入口、草稿返回、类型默认、系统返回及停用分类提示交互测试的 Kotlin 编译。
- `.\gradlew.bat assembleRelease` — 通过，release APK 已签名并完成 package/signature/alignment 检查。

### 未执行

- `connectedDebugAndroidTest` — 未运行成功，当前 ADB 没有连接设备；API 34 与 API 30 模拟器均因本机已有实例过多无法启动。Room 运行时和实际分类页面需在设备回归中验证，源码编译不代表 UI 仪器运行通过。

## 关键决策

### 分类类型在创建后固定

- **决定**：编辑分类只能改名、图标；不能更改支出/收入类型。
- **原因**：账单类型必须始终和所引用分类一致。
- **取舍**：如录入类型有误，用户在账单编辑中切换类型并选择目标分类。
- **证据**：`CategoryDialog` 仅显示既有分类类型；Repository 校验账单和分类类型一致。

### 停用保留历史关联

- **决定**：分类停用时不删除分类或账单引用。
- **原因**：保留历史描述和分类统计语义。
- **取舍**：重新启用需重新验证名称唯一性。
- **证据**：`LedgerRepositoryTest.deactivatedCategoryStaysLinkedToHistoryButCannotBePickedForNewEntries` 已编译，仪器运行待设备。

### 记账页快捷管理分类

- **决定**：记账页分类标题行右侧提供“管理分类”，同时保留设置入口；记账页进入时分类管理默认选中当前收支类型，返回恢复未保存的账单草稿；新建分类只加入列表，不自动选择。
- **原因**：让维护分类可以在录入流程中完成，并维持设置页原有入口。
- **取舍**：分类标题行增加入口；新分类仍需用户主动选中。
- **证据**：2026-10-31 用户确认的 A 版方案；`EntryEditor` 内复用 `CategoryManager`，UI 仪器源码覆盖草稿返回与新分类选择。

## 已知问题与后续工作

### 设备端分类回归待执行

- **影响**：Room 查询及分类表单的实际设备交互尚未运行。
- **当前处理**：相应 Room/UI 仪器用例源码已编译；当前环境无可用模拟器或真机。新账单已选分类被停用后的清除及提示行为已有仪器用例，但未能在设备运行。
- **后续动作**：设备可用后执行 Work Item `WORK-20261031-001` 的入口/草稿返回、新增、停用、历史显示和重启持久性回归。

## 变更记录

### 2026-11-01 — 记账页分类快捷入口

- **状态**：部分完成
- **变化**：记账页分类标题行增加“管理分类”入口，分类管理跟随当前收支类型打开；返回保留草稿，新增分类由用户手动选择；切换支出/收入使用清晰分段控件。新账单当前分类被停用后会清空并提示重新选择。
- **原因**：分类管理原来仅位于设置中，录入期间切换收支样式不够清楚。
- **兼容性**：数据库结构与 Repository 分类规则不变，无 Room 迁移；设置入口保留。
- **验证**：单元测试、仪器源码编译、lint 与 debug 构建通过；设备端仪器运行未执行，原因是无连接设备且模拟器实例冲突。

### 2026-10-31 — V0.1 首版

- **状态**：已完成
- **变化**：新增默认分类、分类维护操作及历史账单稳定引用。
- **原因**：支持用户按收支类别记录日常账单。
- **兼容性**：Room schema version 1；未来结构变化需显式迁移。
- **验证**：单测、lint 和 Android 仪器源码编译通过；设备运行未执行。

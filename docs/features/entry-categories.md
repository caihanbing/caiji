# 账单分类管理

<!-- feature-id: entry-categories -->
<!-- feature-title: 账单分类管理 -->
<!-- feature-aliases: categories, 分类 -->
<!-- feature-status: active -->
<!-- feature-summary: 管理收支类别、48 个内置图标、拖动顺序和启停状态，并保留历史关联。 -->
<!-- last-verified: 2026-11-01 -->
<!-- code-basis: HEAD 7e9cc5743f1c; worktree-digest sha256:21fc9ce0fc332f187f08394a6f8e3fc4c4e5ed08104c1fe9fcb206d1fbbc4da1 -->

## 快速上下文

财记 0.2.0 的分类管理可从“设置—分类管理”进入，也能在记账页点“管理分类”进入。默认仍有 11 个支出与 5 个收入分类；应用内图标目录扩充为 48 个键。分类持久化在 Room，账单通过稳定 ID 关联。停用只影响新账单选择，不删除历史分类或关联。

页面使用暖白背景、琥珀主色、分段收支选择和分类卡片。保留现有 `iconKey` 与 emoji 映射。

## 目标与边界

### 目标

- 按支出、收入管理可选分类。
- 在保留历史账单描述和关联的前提下新增、改名、换图标、排序、启用或停用分类。

### 范围内

- 支出预置：餐饮、购物、交通、住房、日用、娱乐、医疗、学习、通讯、人情、其他。
- 收入预置：工资、奖金、兼职、理财、其他。
- 新增分类可选收支类型和 48 个按用途分组的内置图标；编辑时保留分类类型。
- 同类型分类按 `sortOrder` 排列；长按拖动排序，开关控制启停。

### 范围外

- 删除分类、上传图标、多级分类、跨账本共享和网络同步。

### 不变量

- 分类 ID 稳定；创建后收支类型固定。
- 同类型启用分类名称（去首尾空格）不能重复；重新启用时重新校验。
- 新账单只能选同类型启用分类；历史账单可显示已停用分类。
- 分类名最多 20,000 个字符；图标键必须在 `CategoryCatalog` 白名单内。

## 业务行为

- 首次初始化在 Room 事务中写入 16 个默认分类；重复初始化不会重复添加。
- 分类管理页在支出/收入间切换，分类项显示图标、名称、拖动反馈和启用开关。
- 拖动范围包含当前类型的停用分类；预览顺序于松手后一次保存，取消或失败会回滚。
- 编辑分类只显示固定类型；新建分类才允许选类型。
- 新增/重名/数据库写入失败时保留输入，并在对话框中显示错误。
- 记账页入口以当前收支类型打开；从设置打开则默认支出。返回记账页后保留账单草稿。
- 新分类返回记账页后仅显示、不自动选择；用户需主动点选。
- 新账单所选分类在管理期间被停用，返回后清空选择并提示重选；编辑历史账单可保留原停用分类。
- 停用分类仍可编辑名称/图标；重新启用前会校验同类型名称唯一性。

## 架构与代码地图

- `app/src/main/java/app/caiji/android/ui/CategoryManagerScreen.kt` — `CategoryManagerScreen`、分类列表、排序与启用操作、分类编辑对话框。
- `app/src/main/java/app/caiji/android/ui/SettingsScreen.kt` — 设置页分类入口和备份入口。
- `app/src/main/java/app/caiji/android/ui/EntryEditorScreen.kt` — 记账页分类入口、当前类型传递、草稿与停用分类重新选择提示。
- `app/src/main/java/app/caiji/android/domain/Category.kt` — `Category`、`CategoryCatalog`、默认目录和图标白名单。
- `app/src/main/java/app/caiji/android/ui/CaijiIcons.kt` — 48 个分类图标键的显示映射。
- `app/src/main/java/app/caiji/android/ui/LedgerViewModel.kt` — 保存、启停和排序调用及错误状态。
- `app/src/main/java/app/caiji/android/data/LedgerRepository.kt` — `createCategory`、`renameCategory`、`setCategoryActive`、`reorderCategory` 事务实现。
- `app/src/main/java/app/caiji/android/data/LedgerDatabase.kt` — Room 分类实体、索引和外键。

## API、事件与任务

### `RoomLedgerRepository.createCategory` / `renameCategory`

- 输入分类类型、名称、内置图标键；编辑时再输入稳定 ID。
- 空名、超长名称、非法图标、重复启用名或不存在的分类会拒绝保存；数据库操作在事务内执行。
- 这是应用内部 Kotlin 契约，不提供外部 HTTP API。

### `RoomLedgerRepository.setCategoryActive` / `reorderCategory`

- 重新启用时校验同类型名称；停用不删除历史行。
- 顺序只影响同类型列表，并在事务中重新编号。

## 数据模型与迁移

`categories` 表字段为稳定 ID、`type`、`name`、`iconKey`、`sortOrder`、`active`。`entries.categoryId` 有 `RESTRICT` 外键。Room schema 当前为 1；本次 UI 更新无字段、索引或迁移变化。未来结构变更必须提供显式 Room Migration。

## 核心流程

1. Repository 初始化时，在空表中插入 `CategoryCatalog.defaults()`。
2. 分类管理页按收支类型加载列表并排序显示。
3. ViewModel 调用 Repository；名称、白名单和启用规则在事务中校验。
4. 成功后刷新分类列表；记账页保留未保存表单并展示返回的分类。
5. 所选新建账单分类在管理期间被停用时，返回后清空选择并显示提示。

## 异常与边界条件

- 空名称、超长名称、重复名称或非法图标 — 拒绝保存，保留输入并在弹窗显示错误。
- 启用时与同类型活动分类冲突 — 拒绝启用，保留原停用状态。
- 历史账单引用已停用分类 — 列表仍显示历史名称/图标；禁止删除分类。
- 排序边界 — 第一项不能上移，其他排序动作只影响同类型列表。

## 并发与幂等

- 新增、改名、启用校验和更新在 Room 事务内执行，避免并发重复名通过校验。
- 排序在事务内读取并重排同类型分类。
- 初始化使用空表检查与 `INSERT OR IGNORE`，避免重复种子。
- 恢复期间受 `RestoreWriteGate` 保护，普通写入会被拒绝。

## 安全与权限

- 分类和账单均保存在应用私有 Room 数据库；无网络调用和外部权限。
- `CategoryCatalog.iconKeys()` 提供 48 个内置键；`iconLabel` 为选择器提供可访问标签，`CaijiIcons.kt` 映射应用内 emoji。
- Android 自动备份禁用。

## 配置与依赖

- Android `minSdk=26`、`targetSdk=34`；版本 0.2.0/code 3；Room schema 1。
- 依赖 Kotlin、Compose Material 3、Room 和 Coroutines；默认分类不依赖后端配置。

## 可观测性与运维

- ViewModel 的分类错误在弹窗和页面反馈中显示；无遥测、后台任务或服务器日志。
- 当前版本沿用 0.1.1 签名，升级保留本机数据库；含新增图标键的新版备份不能由 0.1.1 恢复。

## 测试与验证

### 已执行

- `./gradlew --max-workers=2 :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:connectedDebugAndroidTest :app:assembleDebug` — 通过；API34 AVD 最终回归 48 项 instrumentation，含重启后顺序持久化。
- 分类类型切换、48 个唯一图标、图标选择/重名错误、记账页入口、草稿保留、停用分类处理及拖动取消/失败回滚均有用例。
- Repository instrumentation 覆盖停用分类仍可持久排序；新图标键可通过格式 2 备份 round-trip。
- API34 AVD 逐页查看分类列表与新增对话框；长列表可滚动，类型和图标按钮可达。

### 未执行

- 未在 Android API 26 设备实测系统栏或实际窗口尺寸；manifest 最低 API 为 26。
- 不同厂商和字体配置下的长按拖动仍需用户实机确认；API34 AVD 上拖动和回滚已通过。

## 关键决策

### 分类类型在创建后固定

- **决定**：编辑分类只改名称和图标；不能改变支出/收入类型。
- **原因**：账单类型必须与所引用分类类型一致。
- **取舍**：录错类型需在编辑账单时改类型并重选目标分类。
- **证据**：分类编辑对话框固定显示类型；`EntryEditorScreenTest` 和 Repository 类型校验。

### 停用保留历史关联

- **决定**：停用不删除分类或账单外键；新账单不可选择停用项。
- **原因**：保留历史描述和记录关联。
- **取舍**：重新启用前必须重新检查同类型名称唯一性。
- **证据**：`LedgerRepositoryTest.deactivatedCategoryStaysLinkedToHistoryButCannotBePickedForNewEntries` 与 API34 交互测试。

### 记账页快捷管理分类

- **决定**：记账页和设置页均可管理分类；记账页按当前类型打开，并在返回时保留草稿。
- **原因**：让录入中可维护分类，同时保留设置中的入口。
- **取舍**：新增分类返回后仍须用户主动选择。
- **证据**：`EverydayLedgerFlowTest.categoryManagementFromEntryPreservesDraftAndUsesCurrentType`。

## 已知问题与后续工作

### 用户实体设备排序回归

- **影响**：不同 Android 设备/字体下的长按拖动仍需在用户设备走查。
- **当前处理**：API34 AVD 完成跨行拖动、取消回滚和失败回滚；48 键目录和备份 round-trip 单测通过。
- **后续动作**：用户安装后回归分类排序、启停和历史账单显示。

## 变更记录

### 2026-11-01 — 分类拖动排序和 48 图标目录

- **状态**：已完成
- **变化**：移除排序箭头，分类行改成长按拖动；新增 33 个图标键并按用途分组显示。
- **原因**：按用户实机反馈改善排序操作并扩充图标选择。
- **兼容性**：Room schema、默认分类 ID、原图标键和备份格式 2 不变；旧版白名单不能读取新增图标键。
- **验证**：API34 AVD 43 个设备用例通过；`CategoryCatalogTest` 校验 48 个唯一键；新图标的加密备份 round-trip 通过。

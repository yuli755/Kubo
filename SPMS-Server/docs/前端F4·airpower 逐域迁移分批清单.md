# 前端 F4 · airpower 逐域迁移分批清单

> 目的：把「换底座」从口号变成可勾选的清单。数字取自本次实测（`grep @airpower/web`）。
> 上游：F1 契约替代（`src/framework`，commit `5659a341`）、F2 组件层（`useTable`/`KTable`/`KFormDialog`，commit `bc57dc9`）、试点页 `src/view/console/system/unit/list.vue`。

## 一、现状数字（实测）

| 范围 | 文件数 | 说明 |
| --- | --- | --- |
| `import ... from '@airpower/web'` 的文件 | **177** | 含只取装饰器/枚举的模型层 |
| 视图页面（`src/view/console/**`） | **90** | 7 个域：channel 23 · system 21 · wms 20 · personnel 11 · asset 8 · factory 6 · index 1 |
| 共享组件 / hook / 路由 | **12** | `src/component/{bill,user}/*`、`AuditFlow`、`PublishButton`、`UserCard`、`hook/useTanStackTable`、`hook/billTable/*`、`router/appRouter` |
| 模型实体（`src/model/**`） | 72 | 只用到装饰器与枚举，随域迁移时同步换 `KEntity` |

组件级用量（按**文件数**，同一文件可能同时用多个）：

| airpower 成员 | 文件数 | 新底座对应物 | 状态 |
| --- | --- | --- | --- |
| `DialogProps` (60) / `ADialog` (42+) | 51 | `KFormDialog`（`:id` 有值=编辑） | ✅ 已有 |
| `ATable` | 44 | `KTable` + `useTable` | ✅ 已有 |
| `AFormField` | 35 | `KFormDialog` 自动生成 | ✅ 已有（选择器类字段还缺） |
| `APanel` | 26 | 待补 `KPanel`（卡片壳 + 标题栏 + 右侧操作区） | ⬜ F2 第二批 |
| `useEditor` | 20 | `KFormDialog` 内部状态 | ✅ 已有（`useEditor` 退役） |
| `DialogUtil` | 16 | 待补 `KConfirm`（`confirm/alert/prompt` 语义化封装） | ⬜ F2 第二批 |
| `ASelector` | 16 | 待补 `KSelector`（远程下拉/弹窗选择器，含单选与多选） | ⬜ F2 第二批 |
| `AButton` / `AInput` / `ASelect` / `AGroup` / `AMoney` | 15 / 8 / 7 / 8 / 3 | 直接用 `ElButton`/`ElInput`/`ElSelect`；`AGroup` → `ElCard`+分栏；`AMoney` → 金额格式化（`@/framework` 里补 formatMoney） | ⬜ 轻量 |
| `useTableTree` | 5 | 待补 `useTreeTable`（懒加载树 + 搜索） | ⬜ F2 第二批 |
| `useDetail` | 5 | 待补 `useDetail`（详情只读渲染，基于 `@Descriptions` 元数据） | ⬜ F2 第二批 |

**结论**：F2 第二批的缺口集中在 5 个件：`KPanel`、`KSelector`、`KConfirm`、`useTreeTable`、`useDetail`（+ `formatMoney`）。
在这 5 个件补齐前，任何一批页面迁移都会被迫在页面里手写这五类逻辑，等于把旧债原地平移。

## 二、批 0：共享件先换（所有域的共同依赖）

必须先做的原因：`src/component/bill/*`、`src/component/user/*` 是**单据类页面**的公共底座，wms / channel / asset 三域的编辑器都在用；
不先换，后面每批都要为同一批公共组件打补丁。

| 文件 | 用到的 airpower | 迁移动作 |
| --- | --- | --- |
| `src/hook/useTanStackTable.ts` | ATable / useEditor / DialogUtil | 直接删除（F2 的 `useTable` 已替代），确认无引用后删 |
| `src/hook/billTable/useBillDetail.ts` + `IUseBillDetailResult.ts` | useDetail / DialogUtil | 重写为基于 `useTable` + `useDetail` 的组合 |
| `src/component/bill/BillAddFinishDialog.vue` | ADialog / DialogProps | → `KFormDialog`（自定义 footer 插槽） |
| `src/component/bill/BillRejectDialog.vue` | ADialog / DialogProps | → `KFormDialog` |
| `src/component/bill/BillFormCode.vue` / `BillFormMoreDetail.vue` | AFormField / AGroup | → 表单渲染交给 `KFormDialog` 的字段推导 |
| `src/component/bill/BillAuditOrReject.vue`、`PublishButton.vue`、`UserCard.vue`、`AuditFlow.vue` | AButton / DialogUtil | → `ElButton` / `KConfirm` |
| `src/component/user/{ModifyPassword,PersonalToken,UserAccount}.vue` | useEditor / ADialog / AButton / DialogProps | → `KFormDialog` |
| `src/router/appRouter.ts`、`src/view/console/index/index.vue` | APanel | → `KPanel` |

验收：批 0 完成后，`src/component/**` 与 `src/hook/**` 里不再出现 `@airpower/web`（模型层的装饰器除外）。

## 三、批 1~5：按域推进（先小后大，先低风险后高敏感）

| 批次 | 域 | 视图文件 | 依赖缺口（除批 0 外） | 备注 |
| --- | --- | --- | --- | --- |
| 批 1 | factory + index | 6 + 1 | `useTreeTable`（storage / structure 均为树表） | 最小域，先打通「树表 + 表单」全链路 |
| 批 2 | asset | 8 | `KSelector`、`useDetail` | 合同域有明细行编辑器（`editParticipant`）与只读详情页，正好验证 `useDetail` |
| 批 3 | personnel | 11 | `useTreeTable`（部门树）、`KSelector`（用户/角色选择器） | 角色页的权限树编辑器是唯一复杂点，单独拆子任务 |
| 批 4 | wms | 20 | `KSelector`、`DialogUtil`、单据公共件（批 0 已备） | 单据闭环最完整（出入库/移库/库存），是**业务主线**，放在渠道域之前 |
| 批 5 | channel | 23 | `KSelector`、`AMoney` | 采购/销售/价格 + 客户/供应商，文件最多但结构高度同构，可脚本化批量替换 |
| 批 6 | system | 21 | `useTreeTable`（菜单/权限树） | 权限、数据权限、日志页是「平台自身的门」，最后动，避免迁移期自锁 |

估时口径（供排期）：每域按「列表页 1 个 / 编辑弹窗 1 个 / 选择器 1 个 / 详情 1 个」为 1 单位，批 1 约 3 单位、批 2 约 4 单位、批 3 约 5 单位、批 4 约 8 单位、批 5 约 9 单位、批 6 约 8 单位。

## 四、每批的完成定义（DoD，缺一不可）

1. **零 airpower import**：该域所有 `.vue` 内 `import ... from '@airpower/web'` 为 0（门禁用剥注释后的源码断言）。
2. **探针全绿**：`src/demo/__tests__/menuSmoke.probe.spec.ts`（逐页独立挂载，`errorHandler`/`console.error` 计硬错误）在该域页面全部 ✅。
3. **新增域门禁 spec**：仿 `src/config/__tests__/frameworkComponents.spec.ts`，至少覆盖「请求体契约 + 元数据驱动渲染 + 该域独有交互」三件事。
4. **类型与体积**：`vue-tsc --noEmit`、`eslint src` 干净；`pnpm build && pnpm size` 达标（当前 首屏 343/450 KB · 总体 712.1/900 KB）。
5. **手工点检**：`pnpm dev:demo` 下按「列表 → 新增 → 编辑 → 删除 → 分页 → 排序 → 搜索 → 选择器」走一遍（用真实浏览器，探针只保证渲染不炸）。

## 五、迁移机制（沿用 F2 已落地的双底座过渡）

- 新页实体基类 = `src/base/KEntity.ts`（注册 `@/framework` 元数据）；**旧页 `BaseEntity` 不动**，否则 144 个页面同时失效。
- 单个实体在过渡期**双套装饰器**：airpower 别名 + `KModel/KField/KForm/KSearch/KTable`（见 `src/model/system/unit/UnitEntity.ts`）。
- 服务可并存：`UnitService` 与 `KUnitService`（同 `baseUrl`，无数据漂移），页面切完后删旧。
- 视图层不直接 `import @/model` 的 Service，统一经 `src/hook/system/*` 转发（ESLint `no-restricted-imports` 已拦）。
- 域内页面全部切完后，**一次性**删除该域模型上的 airpower 装饰器与旧 Service —— 这一步必须整域做完再做，半域删除会让旧页面拿不到元数据。

## 六、风险与对策

| 风险 | 对策 |
| --- | --- |
| 选择器（`KSelector` 16 处）语义复杂：单选/多选、回显、`withParam` 过滤 | 先用「弹窗内嵌 `KTable` + 已选行」实现，覆盖面优先；树形选择器单独排期 |
| 单据页共享件（批 0）被改坏 → 多域同时炸 | 批 0 独立提交 + 探针全绿后再开批 1；批 0 期间冻结单据相关页面改动 |
| 权限树 / 菜单树迁移期自锁（改坏就进不了后台） | 放在批 6；迁移前先确认超管旁路可用，并保留 `git revert` 单批回滚能力 |
| 模型层双装饰器遗漏 → 页面「字段不见了」但不报错 | 域门禁 spec 断言「实体字段数 == 表格列数 + 表单字段数」，用数量对账替代肉眼检查 |

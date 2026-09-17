# 前端 F4 · airpower 逐域迁移分批清单

> 目的：把「换底座」从口号变成可勾选的清单。数字取自本次实测（`grep @airpower/web`）。
> 上游：F1 契约替代（`src/framework`，commit `5659a341`）、F2 组件层（`useTable`/`KTable`/`KFormDialog`，commit `bc57dc9`）、试点页 `src/view/console/system/unit/list.vue`。

## 〇、进度（每轮更新）

| 批次 | 内容 | 状态 |
| --- | --- | --- |
| F2 第一批 | `useTable` / `KTable` / `KFormDialog` + `KEntity`，试点页 `system/unit` | ✅ `bc57dc9` |
| **F2 第二批** | `KPanel` / `KSelector` / `KDetail` + `useDetail`、树表（`useTable({ tree })` + KTable 树渲染）、单元格与金额格式化（`framework/cell.ts`、`format.ts`）、字典兼容 airpower 枚举（`getEnumItems/getEnumLabel/getEnumColor`） | ✅ 本轮 |
| **批 1** | `factory`（仓库 / 生产单元，树表）+ `index` 首页 | ✅ 本轮 |
| 批 0 | 共享件 `src/component/{bill,user}/*`、`hook/useTanStackTable` 退役 | ⬜ 下一轮 |
| 批 2~6 | asset → personnel → wms → channel → system | ⬜ |

**本轮实测校验**：`vue-tsc` 干净、ESLint 干净、`pnpm build` 通过、体积 首屏 343/450 KB · 总体 715/900 KB；
vitest 44 文件 / 373 用例全绿，其中逐页冒烟探针 27/27 页 ✅（含新迁的仓库管理与库位结构）。

## 一、现状数字（实测）

| 范围 | 文件数 | 说明 |
| --- | --- | --- |
| `import ... from '@airpower/web'` 的文件 | **177** | 含只取装饰器/枚举的模型层 |
| 视图页面（`src/view/console/**`） | **90** | 7 个域：channel 23 · system 21 · wms 20 · personnel 11 · asset 8 · factory 6 · index 1 |
| 共享组件 / hook / 路由 | **12** | `src/component/{bill,user}/*`、`AuditFlow`、`PublishButton`、`UserCard`、`hook/useTanStackTable`、`hook/billTable/*`、`router/appRouter` |
| 模型实体（`src/model/**`） | 72 | 只用到装饰器与枚举，随域迁移时同步换 `KEntity` |

组件级用量（按**文件数**，同一文件可能同时用多个）。右列是**本轮补齐后**的真实状态：

| airpower 成员 | 文件数 | 新底座对应物 | 状态 |
| --- | --- | --- | --- |
| `DialogProps` (60) / `ADialog` (42+) / `useEditor` (20) | 51 | `KFormDialog`：`:id` 有值=编辑、rules 由 `@Form` 自动生成；`preset` 预置字段、`columns: 2` 两列布局、`#field-*` 覆盖控件、`#extra` 追加内容、`@loaded` 交出表单对象 | ✅ |
| `ATable` | 44 | `KTable` + `useTable`：搜索/分页/排序/选择/行操作/空态，列与单元格走框架注册表 | ✅ |
| `AFormField` | 35 | `KFormDialog` 自动生成（控件类型由 `@Form` 布尔标志推导） | ✅ |
| `APanel` | 26 | `KPanel`（标题 + 右上角插槽 + 留白收敛） | ✅ 本轮 |
| `DialogUtil` | 16 | `@/framework` 的 `DialogUtil.show/select/selectList`（**与 airpower 同契约，改 import 来源即可**）+ `FeedbackUtil`（toast/confirm/alert） | ✅ 无需新组件 |
| `ASelector` | 16 | `KSelector`（单选/多选、回显、固定过滤、分页搜索）；存量 airpower 选择器可用 `DialogUtil` 继续打开，按域替换 | ✅ 本轮 |
| `AButton` / `AInput` / `ASelect` | 15 / 8 / 7 | 直接用 `ElButton`/`ElInput`/`ElSelect`（显式 import，不依赖自动导入） | ✅ 轻量 |
| `AGroup` | 8 | `KFormDialog` 的 `columns: 2`（两列表单）+ `#extra` 插槽 | ✅ 本轮 |
| `AMoney` | 3 | `framework/format.ts` 的 `formatMoney`（金额列右对齐 + 千分位 + 两位小数），`@Table({ money: true })` 即生效 | ✅ 本轮 |
| `useTableTree` | 5 | `useTable({ tree: true })` → `getTreeList`，KTable 自动树渲染（`row-key` + `children`）、隐藏分页器、给「添加子级」 | ✅ 本轮 |
| `useDetail` | 5 | `useDetail` hook（按 id 重新查，避免列表快照过期）+ `KDetail`（元数据驱动的只读描述列表） | ✅ 本轮 |

**结论（修正上一轮的判断）**：上一版把 `KConfirm`/`DialogUtil` 也算成"缺口"，其实 `@/framework` 的
`FeedbackUtil` + `DialogUtil` 已经覆盖，迁移只需改 import 来源；真正的缺口是 4 个件
（`KPanel`、`KSelector`、树表、`useDetail`）加一套统一单元格渲染，**本轮已全部补齐**，
批 0 与批 2 起不再需要"边迁边造轮子"。

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
| 批 1 | factory + index | 6 + 1 | 无（已具备） | ✅ **本轮完成**：仓库/生产单元改用 `useTable({ tree: true })` + `KTable` + `KFormDialog`，首页 `APanel` → `KPanel`；两个跨域选择器**按设计保留**（wms 的入库/移库页仍用它们，属批 4） |
| 批 2 | asset | 8 | `KSelector`、`useDetail` | 合同域有明细行编辑器（`editParticipant`）与只读详情页，正好验证 `useDetail` |
| 批 3 | personnel | 11 | `useTreeTable`（部门树）、`KSelector`（用户/角色选择器） | 角色页的权限树编辑器是唯一复杂点，单独拆子任务 |
| 批 4 | wms | 20 | `KSelector`、`DialogUtil`、单据公共件（批 0 已备） | 单据闭环最完整（出入库/移库/库存），是**业务主线**，放在渠道域之前 |
| 批 5 | channel | 23 | `KSelector`、`AMoney` | 采购/销售/价格 + 客户/供应商，文件最多但结构高度同构，可脚本化批量替换 |
| 批 6 | system | 21 | `useTreeTable`（菜单/权限树） | 权限、数据权限、日志页是「平台自身的门」，最后动，避免迁移期自锁 |

估时口径（供排期）：每域按「列表页 1 个 / 编辑弹窗 1 个 / 选择器 1 个 / 详情 1 个」为 1 单位，批 1 约 3 单位、批 2 约 4 单位、批 3 约 5 单位、批 4 约 8 单位、批 5 约 9 单位、批 6 约 8 单位。

## 四、每批的完成定义（DoD，缺一不可）

1. **零 airpower import**：该域所有 `.vue` 内 `import ... from '@airpower/web'` 为 0。批 1 的门禁断言的是
   **精确剩余集合**（两个跨域选择器）而不是"数量为 0" —— 新增一处 airpower 用法会让用例变红，
   集合变空时也提示可以收紧断言。见 `src/config/__tests__/frameworkComponents.spec.ts` 的
   `f4 · 批 1（factory + index）迁移完成度`。
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
| 双底座**枚举基类不同**：airpower 的 `WebEnum` 只有 `toArray()/getLabel()`，本仓是 `getList()` —— 新表格读 airpower 枚举会「列头有、值是数字」 | `framework/enums.ts` 统一取值 `getEnumItems/getEnumLabel/getEnumColor`（两种形状都吃），并有单测钉住；`IWebEnumConstructor` 的静态方法声明为可选 |
| 迁移中把**跨域公共件**（选择器、单据组件）一起改掉，会连带打挂尚未迁移的域 | 只改"叶子页"，跨域件按"最后一个使用方"排期；新页面通过 `DialogUtil` 打开存量 airpower 选择器（契约一致，可混用） |

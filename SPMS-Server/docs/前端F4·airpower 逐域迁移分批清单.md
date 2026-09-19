# 前端 F4 · airpower 逐域迁移分批清单

> 目的：把「换底座」从口号变成可勾选的清单。数字取自本次实测（`grep @airpower/web`）。
> 上游：F1 契约替代（`src/framework`，commit `5659a341`）、F2 组件层（`useTable`/`KTable`/`KFormDialog`，commit `bc57dc9`）、试点页 `src/view/console/system/unit/list.vue`。

## 〇、进度（每轮更新）

| 批次 | 内容 | 状态 |
| --- | --- | --- |
| F2 第一批 | `useTable` / `KTable` / `KFormDialog` + `KEntity`，试点页 `system/unit` | ✅ `bc57dc9` |
| **F2 第二批** | `KPanel` / `KSelector` / `KDetail` + `useDetail`、树表（`useTable({ tree })` + KTable 树渲染）、单元格与金额格式化（`framework/cell.ts`、`format.ts`）、字典兼容 airpower 枚举（`getEnumItems/getEnumLabel/getEnumColor`） | ✅ 本轮 |
| **批 1** | `factory`（仓库 / 生产单元，树表）+ `index` 首页 | ✅ 本轮 |
| **批 0A** | 应用外壳（旧壳 `AFrame` 分支 + `VITE_LEGACY_SHELL` 开关一起删）、`KDialog`、两个单据弹窗（`BillAddFinishDialog`/`BillRejectDialog`）、`BillAuditOrReject` | ✅ 本轮 |
| **批 0B（已消解）** | ~~`BillFormCode` / `BillFormMoreDetail` / `hook/billTable/*`~~、`UserCard` + `component/user/*`、`router/appRouter.ts` | ✅ 前三件被批 5 复核为**无消费者**（真实消费者是旧 airpower 单据编辑器），随批 5 一并删除；`UserCard` + `component/user/*` 依赖 `AImage`，与 `appRouter.ts` 同归 F5（见 §3.9 第 2 条） |
| **批 2** | `asset`：物料 / 合同（列表 + 详情 + 参与方）、框架层补 3 件（异步导出、日期控件、单元格插槽） | ✅ |
| **批 3** | `personnel`：部门（树）/ 角色（+菜单与权限授权）/ 用户（部门树筛选 + 角色部门多选），连带改 `factory/structure` 的部门选择器 | ✅ 本轮（§3.7） |
| **批 4** | `wms`：入库 / 出库 / 移库 / 库存（**单据闭环最完整的一域**），连带删掉 `factory/storage` 的仓库选择器 | ✅ 本轮（§3.8） |
| **批 5** | `channel`：客户 / 供应商 / 采购 / 销售 / 采购价 / 销售价（**旧链最乱、并线最多的一域**：typed client + 旧服务 + 页面直连三层并存），连带重指 `wms ↔ channel` 的两个详情接缝 | ✅ 本轮（§3.9） |
| **批 6（并入收官）** | `system`：菜单 / 权限 / 编码规则 / 参数 / 日志 / 数据权限（策略 / 资源 / 列级） | ✅ 收官（§3.10）：批 6 不再单独分批，与"一次删净"合并执行 |
| **收官 · airpower 整体退场** | 删依赖 + `airpower-bootstrap` + `airpowerCompat` + `useTanStackTable` + 两个 Vite 插件；重写 `UserCard`/`user/*`/`error.vue`/`appRouter`；`pnpm airpower:check` 从度量转硬门禁 | ✅ **0 引用**（2026-09-18） |

**收官实测校验（批 6 + 整体退场）**：`vue-tsc` 干净、`eslint .` 全量干净、`pnpm build` 通过、`pnpm check` exit=0 —— **51 文件 / 459 用例**全绿，
体积 首屏 **270.4/450 KB** · 总体 **630.4/900 KB**（对比退场前：首屏 −97.5 KB、总体 −105.3 KB —— airpower 单体与它强拉进来的 EP 组件壳退出首屏闭包），
**airpower 引用 92 → 0**。新增 `elementPlusAggregate.spec.ts`（2 例：别名聚合入口缺导出只在 `pnpm build` 炸，静态钉住）+ `authStorage.spec.ts`（4 例：token/权限缓存的 key 不变）。
**批 5 复核值（保留对照）**：51 文件 / 457 用例、首屏 367.9 KB、总体 735.7 KB、airpower 120 → 92。
**批 4 / 批 3 复核值**：50 文件 / 441 用例（首屏 368.2 KB、总体 732.6 KB）；49 文件 / 426 用例（首屏 369.7 KB、总体 722.8 KB）。

## 一、现状数字（实测）

| 范围 | 文件数 | 说明 |
| --- | --- | --- |
| `import ... from '@airpower/web'` 的文件 | **0** | 2026-09-18 收官（口径见 `pnpm airpower:check`：剥注释后的真实 import + 依赖表；该命令已从"进度度量"改为硬门禁）。历史数字：92（批 5 后）→ 120 → 135 → 145 → 159 → 165；更早的 177/160/156 来自 `grep` 粗口径，不可直接比较 |
| 视图页面（`src/view/**`） | **0** | system 最后 17 个视图（coderule / config / datapermission 三页 / log / menu / permission / error）在收官轮全部换到 K 底座 |
| 共享组件 / hook / 路由 | **0** | `UserCard` + `user/*` 重写为 K 底座（上传走 `DialogUtil.showUpload`）；`appRouter.ts` 换成 native `vue-router`；`airpower-bootstrap` / `airpowerCompat` / `useTanStackTable` 三件直接删除 |
| 模型实体（`src/model/**`） | **0** | 实体只挂 K 装饰器（`@KModel/@KTable/@KForm/@KField/@KSearch`）；`BaseEntity` 仍作继承根（F5 换 `KEntity`），但它已不再 import airpower |

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
| `useTableTree` | 5 | `useTable({ tree: true })` → `getList`（树形返回），KTable 自动树渲染（`row-key` + `children`）、隐藏分页器、给「添加子级」 | ✅ 本轮 |
| `useDetail` | 5 | `useDetail` hook（按 id 重新查，避免列表快照过期）+ `KDetail`（元数据驱动的只读描述列表） | ✅ 本轮 |

**结论（修正上一轮的判断）**：上一版把 `KConfirm`/`DialogUtil` 也算成"缺口"，其实 `@/framework` 的
`FeedbackUtil` + `DialogUtil` 已经覆盖，迁移只需改 import 来源；真正的缺口是 4 个件
（`KPanel`、`KSelector`、树表、`useDetail`）加一套统一单元格渲染，**本轮已全部补齐**，
批 0 与批 2 起不再需要"边迁边造轮子"。

## 二、批 0：共享件先换（所有域的共同依赖）

必须先做的原因：`src/component/bill/*`、`src/component/user/*` 是**单据类页面**的公共底座，wms / channel / asset 三域的编辑器都在用；
不先换，后面每批都要为同一批公共组件打补丁。

### 为什么拆成 0A / 0B

原先的验收口径是「`src/component/**` 与 `src/hook/**` 里不再出现 `@airpower/web`」。真做起来发现这个口径**混了三种性质完全不同的事**，
硬凑在一个批次里只会互相阻塞：

| 性质 | 谁 | 什么时候能做 |
| --- | --- | --- |
| **A. 不依赖任何模型元数据**（外壳、纯输入弹窗、按钮） | `Console.vue`、`KDialog`、`BillAddFinishDialog`、`BillRejectDialog`、`BillAuditOrReject` | **现在**（本轮已完成） |
| **B. 要按元数据渲染字段** | `BillFormCode`、`BillFormMoreDetail`、`hook/billTable/*` | 得等单据实体（入库/出库/移库/采购/销售/合同）补上框架层装饰器 —— 那是各域迁移的产物，先做等于把 6 个域的模型工作提前 |
| **C. 依赖"还缺的件"或整片 airpower 组件仍在位** | `UserCard` + `component/user/*`（头像上传要上传组件，框架层 `showUpload` 未实现）、`router/appRouter.ts`（`RouterUtil` 与 airpower 组件共用同一份内联 vue-router，组件不消失就不能换） | 上传组件补齐后 / 批 6 之后（F5） |

**0A 已完成的动作**

| 文件 | 原用法 | 迁移动作 |
| --- | --- | --- |
| `src/component/Console.vue` | `AFrame` 旧壳分支 + `WebConfig.isTimeout` 复位 | 删掉旧壳分支与 `VITE_LEGACY_SHELL` 开关（改版期 A/B 已结束）；`WebConfig.isTimeout` 复位挪到 `router/appRouter.ts` 的 `initVueRouter()` —— 与真正消费它的 airpower 调用放一起，外壳不再 import airpower |
| **新增** `src/component/framework/KDialog.vue` | — | 命令式弹窗的通用外壳（ElDialog），契约 = `IDialogActionProps`：`param` 进、`onConfirm(data)` 出（`framework/feedback.ts` 新增该接口类型） |
| `src/component/bill/BillAddFinishDialog.vue`、`BillRejectDialog.vue` | `ADialog + DialogProps` | → `KDialog`，**props 契约不变**，所以还没迁移的 wms/channel/asset 单据页照旧打开它们 |
| `src/component/bill/BillAuditOrReject.vue` | `AButton` | → `ElButton`（link 文字按钮），可用性仍来自实体状态机 `canAudit()/canReject()` |
| `src/component/bill/BillFormCode.vue` / `BillFormMoreDetail.vue` | `AFormField` / `AGroup` / `ADateTime` | ⬜ 0B（要元数据） |
| `src/component/user/{ModifyPassword,PersonalToken,UserAccount}.vue`、`UserCard.vue` | `useEditor`/`ADialog`/`AImage` | ⬜ 0B（缺上传组件） |
| `src/hook/billTable/useBillDetail.ts` + `IUseBillDetailResult.ts` | `useDetail`/`DialogUtil` | ⬜ 0B（同上） |
| `src/hook/useTanStackTable.ts` | 旧适配器 | **退役口径改了**：它被 16 个未迁移的列表页 + 8 个 hook 使用，只有在最后一个域迁完之后才能删（属 F5 收尾）。本轮起**禁止新增使用**，由批 2~6 的门禁逐域削减 |

**批 0A 的验收（已落在 `src/config/__tests__/sharedComponents.spec.ts`）**

1. `src/component/**` 里仍 import airpower 的文件 = **精确集合**（当前 6 个，每个都在用例里写清了卡在哪）——
   多一个（新代码又用了 airpower）就红；少一个（又迁完一个）也提示可以收紧断言。
2. 已迁的 5 个文件断言「不再 import airpower」；旧壳开关与 `AFrame` 断言「已删除」。
3. `KDialog` 的命令式契约逐条钉住：标题取 `param`、`disable-confirm` 时点确定不回调、
   取消/关窗触发 `onCancel`、确认把数据交回宿主。
4. 两个单据弹窗的行为：驳回原因空 → 确定禁用；填了 → `onConfirm('原因')`；数量弹窗默认值取 `param`、回传数字。

**本轮踩到的坑（已写进 KDialog 注释）**：外层必须写 `@confirm` / `@cancel`，不能写 `:on-confirm="handler"` ——
后者编译成 kebab 键 `on-confirm`，而 `emit('confirm')` 只在 `vnode.props` 里找 `onConfirm`，
找不到就**静默不派发**（弹窗点确定没反应、且不报错）。这条正是本批新增的测试抓出来的。

## 三、批 2：asset 域（本轮完成）

### 3.1 为 asset 补的三件框架层能力（都是"页面真的需要"才加）

| 文件 | 原用法 | 迁移动作 |
| --- | --- | --- |
| **新增** `src/framework/export.ts`（`ExportUtil`） | airpower 的导出模型 + 轮询 | 建任务 `POST /{baseUrl}/export`（请求体 = 查询请求**去掉 page**，导出的是整个筛选结果）→ 轮询 `POST /{baseUrl}/queryExport` `{fileCode}` → 拼 `WebConfig.staticUrl` 打开。轮询**有上限**（默认 60 次）并抛"导出超时"，不允许死循环 |
| `src/framework/http.ts` | — | 新增 `createExportTask` / `queryExport` 两个方法与 url 常量 |
| `src/hook/kubo/useTable.ts` | — | 新增 `export`（声明式开关）/ `onExport` / `canExport` / `beforeSearch`（搜索条件整形，如把"单位名"拼成 `filter.unit.name` —— 搜索由 KTable 内部触发，页面只能在这里介入）；**删除** `EditView` / `DetailView`（页面改为自己开 `KFormDialog` 后已无使用者） |
| `src/component/framework/KTable.vue` | `ATable` 的导出按钮 / 自定义列 | `show-export` + 导出按钮（带 loading）、`cell-<key>` 单元格插槽（金额脱敏这类"按列覆盖"用它）、`disable-edit` / `disable-delete` |
| `src/component/framework/KFormDialog.vue` | `ADateTime` / 提交前校验 | `beforeSubmit`（返回 `null` 即中止，页面自己给提示）、日期控件（`dateType`；EP `value-format="x"` = **毫秒时间戳**，对齐后端 `Long`） |

### 3.2 域内迁移

| 文件 | 原用法 | 迁移动作 |
| --- | --- | --- |
| asset 四实体（material / contract / participant / contractDocument） | 单套 airpower 装饰器 | **双注册**（airpower 别名 + `KModel/KField/KForm/KSearch/KTable`），批 2B 后一次性删旧 |
| **新增** `model/asset/{material,contract}/K*Service.ts` | `MaterialService` / `ContractService` | 新底座服务（`KContractService` 含 `enforce` / `stop`）；旧服务与 7 个死 API 文件 `git rm` |
| `asset/material/list.vue` | `APanel` + `ATable` + `useTanStackTable` | `KPanel` + `KTable` + `KFormDialog`（价格被遮蔽时 `beforeSubmit` 守卫、`#cell-*` 覆盖价格列、`#field-unit` 换 `KSelector` + `KUnitService`、`beforeSearch` 拼 `filter.unit.name`） |
| `asset/contract/list.vue` | 同上 | 行操作「生效 / 终止 / 查看」：参与方不足两方 → 提示且**不发请求**；终止 → 二次确认后才调服务 |
| `asset/contract/component/detail.vue` | airpower `useDetail` | `KDialog` + `KDetail` + 本仓 `useDetail`（与列表同 `queryKey`，打开即用缓存再回源）+ 两个明细页签 |
| 删除 | — | `asset/material/component/editor.vue`（被 `KFormDialog` 取代）、`system/unit/component/{editor,selector}.vue` + `unit/component/index.ts` + `model/system/unit/UnitService.ts`（它们唯一的消费者就是被删的物料编辑器 —— 单位页早就走 `KFormDialog` + `KUnitService`） |

### 3.3 批 2 的验收

1. **域门禁** `src/config/__tests__/assetDomain.spec.ts`（6 用例）：域内 airpower 精确集合 = **1 个文件**（`material/selector.vue`，被 wms/channel 共用；合同三件经复查是零消费者死件，已删，见 §3.6）、列表与详情走新底座、双注册且 `baseUrl` 一致、价格列 `money + hide`、参与方不足不发请求、终止需二次确认。
2. **框架层行为** `src/config/__tests__/frameworkBatch2.spec.ts`（7 用例）：导出（去 page / 轮询重试 / 超时上限 / 空 `fileCode` 立即失败）、日期控件（渲染成 `ElDatePicker`、`valueFormat='x'`、提交的是数字）、`beforeSubmit` 返回 `null` 时不发写请求、`#cell-<key>` 槽位覆盖单元格。
3. **全链**：vitest **47 文件 / 399 用例**全绿；`vue-tsc`、ESLint 干净；`pnpm build` 通过；体积 首屏 **370/450** · 总体 **745.1/900** KB；`demo:check`、`deadcode:check`、`exports:check` 全绿；逐页探针里两个 asset 页 ✅。
4. **airpower 退场**：生产文件 **165 → 159**（本轮退 6 个：asset 三页 + 物料编辑器 + 单位编辑器/选择器）。新工具 `pnpm airpower` 统一口径（`scripts/find-airpower.mjs`，`--list` 列文件）。

### 3.4 体积 +27 KB 的来历（本轮唯一"不干净"的数字，已定位）

给 `KFormDialog` 加日期控件后首屏 **342.5 → 369.9 KB**。根因不是"组件在首屏被用到"，而是：

> `src/types/element-plus-aggregate.ts`（EP 聚合入口）被 `src/api/axios.ts` 静态引用（首屏 eager），
> 于是**从聚合入口 import 的每个组件都会进首屏**。日期面板 + dayjs ≈ +27.4 KB gzip，而用它的页面全是懒加载的。

三条改法都实测过：

| 改法 | 首屏 | 总体 | 为什么不采用 |
| --- | --- | --- | --- |
| ① 把 `date-picker`/`time-picker` 排除出 `vendor-element` | 370.3（≈0） | — | 模块只是挪进同样在首屏的 `vendor-airpower` |
| ② `KFormDialog` 直连 EP 子路径 + 聚合入口删 `ElDatePicker` | ≈0 | — | 模块换了个在首屏的 chunk |
| ③ `manualChunks` 只把「首屏真要用的 EP」固定进 `vendor-element`（message/icon/popper/overlay…），其余交给 Rollup 自然分包 | **358.2（−11.8）** | 747.9（+2.8） | 首屏确实降了，但代价是 EP 被拆得七零八落：`vendor-airpower` 88.4 → **214.7 KB**、CSS 的 chunk 归属也变了（首屏 CSS 35.8 → 24.9 KB）。**CSS 分包变化在沙箱里无法用真实浏览器验证视觉**，且 F5 一删 airpower 这份分组就作废 —— 收益 11.8 KB 不值得带一个看不见的风险跨两个批次 |

结论：**airpower 还在位时这是结构性开销**，预算内（450 KB）仍有余量；F5（airpower 退场 + EP chunk 重新分组）时用改法 ③ 的思路重做（届时 `vendor-airpower` 这个"接盘侠"不存在了，正好是自然分包）。已在 `vite.config.mts` 与聚合入口留下注释，避免下一个人重复实验。

### 3.4b 残留清理第三轮（2026-09，本次「先删前端不要的代码」的产出）

原则不变（§六 A/B/C）：**A 类（零消费者）立刻删，B/C 类跟着域迁移走**。本轮动的是 A 类 + 文档僵引用：

| 删了什么 | 依据 |
| --- | --- |
| 根目录 `.env.production` + `src/env.d.ts` 里 `VITE_APP_NAME`/`VITE_APP_API_URL` 的声明 + README 对应段落 | 两个变量**全仓零读取**（代码只读 `import.meta.env.MODE`），API 地址由 Vite 代理/nginx 承担；README 那段还在教人配它们 |
| `src/types/element-plus-aggregate.ts` 的 5 条 re-export（`ElDropdown*`、`ElTimeline*`） | 全仓零引用，且**不在** airpower shim 的具名清单里（`ElCheckTag`/`ElPopover` 在其中 → 留到 F5） |
| `src/api/types.ts`（整文件）+ `packages/shared/src/types/api.ts`（18 个类型只活 1 个） | 前者是"兼容 shim"，零 import；后者是脚手架遗留（`PageResponse` 还是 Spring Data 的 `content/totalElements` 口径，与本仓 `{ list, total }` 相冲突）。保留的 `TenantOption` 先迁到 `packages/shared/src/types/tenant.ts`，**2026-09-17 该包删除后回到 `src/api/system/types.ts`** |
| `packages/shared/src/utils/mask.ts` 的 `maskPolicyKeepTail`（该包 2026-09-17 删除，mask 实现并入 `src/framework/mask.ts`） | 唯一调用方是它自己的 spec；`useMaskPolicy` + `applyMaskPolicy` 已覆盖遮蔽计算 |
| `src/demo/mock/specs.ts` 的 `app`/`notify` 两个演示规格 | 对应域早删、后端 `module/` 下也没有对应模块 —— 留着只会生成没有页面消费的假数据 |
| `package.json` 的 `production` / `s` 两个脚本别名 | 分别是 `build` / `dev` 的重复别名（`s` 全仓零引用） |
| 文档僵引用 3 处 | `DEVELOPMENT_GUIDE` 的 `pnpm run production`、`--mode=analyze` + `dist/stats.html`（**仓库没有 rollup-plugin-visualizer**，那个模式不存在）→ 改成 `pnpm build` / `pnpm size:report`；`API_DOCS_GUIDE` 里 `git add src/api/types.ts`（文件已删） |

**顺带修好两处「门禁还在跑、脚本早没了」**（S0 把手工 SQL 收编进 Flyway 时留下的死引用，本次审计发现）：

| 位置 | 症状 | 修法 |
| --- | --- | --- |
| `infra/scripts/check-migration-dialect.mjs`（`pnpm migration:check`） | 硬编码 `docs/database/migration`，那里已无 `.sql` → **空转通过**（"0 个脚本 ⇒ 0 处错误"），方言检查形同虚设 | 路径改到 `src/main/resources/db/migration`，并加"**一个脚本都没扫到就失败**"的兜底；现在输出 `✓ 10 个脚本` |
| `infra/scripts/check-ddl-entity-drift.mjs`（`pnpm ddl:check`）+ `.github/workflows/ci.yml` 的 `migrations` job | 同一个旧路径：门禁直接红（"门禁空转"），CI 里 `mysql < *.sql` 的 glob 匹配不到文件 → 列数 0 → job 必红 | 门禁默认路径改到 Flyway 目录（现在 `✓ 8 张表 / 8 个实体 / 10 个迁移脚本`）；CI 的 glob 与注释同步改到 `src/main/resources/db/migration/*.sql` |

连带把 3 份还在教人"跑手工 SQL / 不用 Flyway"的文档改成现状：`README.md`、`DATABASE.md`（原文写着"不用 Flyway/Liquibase"）、`恢复演练DR-Runbook.md`。

**还顺手修好了两处「`pnpm test` 一直是红的」**（用例全绿，但 vitest 退出码 1 —— 谁都不敢信的门禁）：

| 症状 | 根因 | 修法 |
| --- | --- | --- |
| 39 个未处理拒绝 `TypeError: Cannot set properties of undefined (setting 'scrollTop')` | airpower 表组件在数据变化后执行 `document.getElementById(tableId)?.querySelector('.el-scrollbar__wrap').scrollTop = 0`（`bodyWrap` 那行漏了可选链）；探针 `mount()` 默认挂在**游离 DOM** 上 → `getElementById` 返回 null → 每次数据到达都炸在 `nextTick` 里 | 探针改成 `attachTo: document.body`（更贴近真实渲染）+ 一条只针对 `.el-scrollbar__wrap` 的 jsdom 补丁（EP 的表格只有在设了 height 时才渲染该容器）。**F5 删 airpower 时补丁一并删**，spec 里已写明 |
| 7 个未处理拒绝 `TypeError: result.map is not a function` | 演示 mock 对 `getList`/`get*List` 返回了**分页信封** `{list,total}`，而 airpower 的 `Http.requestModelList` 与框架 `AbstractCurdService.getList(): Promise<E[]>` 都要求**裸数组** | mock engine 拆成 `PAGE_ACTIONS`（信封）与 `ARRAY_ACTIONS`（裸数组，含 `getXxxList` 后缀兜底）；`demoPreview.spec.ts` 新增一条契约用例锁形状 —— 顺带修好了预览态里 5 个旧页的树/列表（之前静默为空） |

**另外两个隐藏问题**（都已在代码里标注，不需要现在动）：

1. **`'./types'` 解析陷阱**：`src/api/system.ts` 的 `import ... from './types'` 一直解析到 `src/api/types.ts`（TypeScript 里**文件优先于同目录文件夹**），也就是说登录认证那 5 个类型其实取自脚手架 shim，而不是本该的 `./system/types`。删 shim 后已显式指向 `./system/types`，并把其中的 `UserEntity` 改名 `CurrentUserInfo`（避免与真实实体 `model/personnel/user/UserEntity` 重名误导）。
2. **`/user/refresh` 端点在后端不存在**（后端只有 `login` / `logout` / `getMyInfo` / `getMyTenantList` / `updateMyPassword`）：`authApi.refreshToken` 的唯一调用方 `stores/user.refreshToken()` 自己**没有任何调用方** —— 整条"刷新令牌"链路是悬空的。重写登录态时一起处置（不在这轮动它，避免顺手改出半截登录流程）。

### 3.5 上传组件（0B 与 2B 的共同卡点，本轮补齐）

原库把上传拆成三处（`AFormField showUpload` 表单字段、`AImage` 图片、`DialogUtil.showUpload()` 命令式弹窗），
三处各有各的 props。这里收敛成**一个组件 + 一个工具类 + 一个命令式入口**：

| 件 | 位置 | 契约 |
| --- | --- | --- |
| `KUpload.vue` | `src/component/framework/` | `v-model` = `IUploadedFile[]`；`category / accept / maxSizeMB / multiple / tip / readonly / compact`；支持点击与拖拽；图片给缩略图（可点开预览）；只读模式给详情页展示 |
| `UploadUtil` | `src/framework/upload.ts` | `validate()`（accept + 大小，返回中文原因）、`upload()`、`uploadList()`、`resolveUrl()`（拼 `WebConfig.staticUrl`）、`sizeText()`、`isImage()`、`normalize()`（后端响应缺 `name/size/extension` 时用本地 File 兜底） |
| `KUploadDialog.vue` + `DialogUtil.showUpload()` | `component/framework/` + `framework/feedback.ts` | 命令式上传弹窗：`await DialogUtil.showUpload({ category })` → 上传记录（多选为数组）；取消 resolve `undefined`（调用方判空） |

**后端契约（已核对 `FileController.upload`）**：`POST /api/file/upload`，multipart 字段 `file` + `category`，
响应 `Json.data(FileEntity)`；后端标了 `authorize = false`（允许匿名），但带登录态时会把上传人落库。

三个坑（都写进代码注释与单测）：

1. **URL 会拼成 `/api/api/...`**：`WebConfig.uploadUrl = '/api/file/upload'` 是给 airpower 用的（它的 axios 没有 baseURL），
   而本仓统一实例 `baseURL = '/api'` —— `UploadUtil.getRequestUrl()` 负责剥前缀，单测钉住。
2. **框架层不能静态 import 组件层**：`showUpload` 用**动态 `import()`** 拿 `KUploadDialog`，
   否则 `framework/feedback.ts → KUploadDialog → KUpload → @/framework` 形成求值期循环依赖（顺带让上传代码进懒加载 chunk）。
3. **演示模式要能"看见自己传的图"**：`demo/mock/adapter.ts` 把 multipart 里的 `File` 变成 `objectURL` 返回 ——
   回一张静态占位图会让人以为"上传成功但内容不对"。

**验收**：`src/config/__tests__/frameworkUpload.spec.ts` 13 个用例（校验 / URL / category 字段 / 响应补齐 /
组件选文件与删除 / 只读 / 命令式弹窗的确定与取消 / airpower 旧契约 `{ data: { category } }`）；全量 vitest 48 文件 412 用例。

### 3.6 「批 2B」的结论：不用迁了，其中三件是死件（2026-09 复查）

原本计划 2B 迁移 `contract/{editor,editParticipant,selector}.vue` + `material/selector.vue`。逐文件查消费者后发现两类情况：

| 文件 | 真实状态 | 处置 |
| --- | --- | --- |
| `contract/component/{editor,editParticipant,selector}.vue` + `component/index.ts` | **零消费者**：批 2 把合同列表改成 `KFormDialog` 后，这个 barrel 只剩这几个文件**互相 import**（谁也没从外面引用它），`list.vue` 只 import `detail.vue` | ✅ **已删**（连同 barrel 4 个文件）。它们不是"待迁"，是"孤岛" |
| `material/component/selector.vue` | 仍在用：**wms** 的入库/出库明细编辑器、**channel** 的采购/销售明细编辑器都靠它选物料 | 随批 4/5 一起换成 `KSelector`（提前删会把 wms/channel 打挂） |

> ⚠️ **能力缺口（不是"不需要"）**：合同那三件承载的**参与方编辑与附件上传**，目前新底座里没有替代实现
> （合同列表的 `KFormDialog` 只编字段，参与方是只读校验）。上传件已就位（§3.5），重建时用
> `KFormDialog` + `KUpload` 补上；已写进 `assetDomain.spec.ts` 的注释，避免被误读成"这功能砍掉了"。

同一轮复查还删掉了另外 12 个**零消费者的 airpower 选择器**（都不属于任何批次的"待迁移"，而是从来没人引用）：
`channel/purchase/{selector,detailSelector}.vue`、`channel/sale/selector.vue`、`factory/structure/{selector.vue,index.ts}`、
`personnel/user/selector.vue`、`system/{menu,permission}/selector.vue`、`wms/{input/detailSelector,input/selector,move/selector,output/selector}.vue`。
处理方式是**收紧两个域门禁的 ALLOWED 集合**（`frameworkComponents.spec.ts` 2→1、`assetDomain.spec.ts` 4→1）——
这正是那两条断言当初写成"精确集合"而不是"数量为 0"的目的：集合该变小时会被断言本身提示。

### 3.7 批 3：personnel 域（本轮完成）

人员域是**别域要引用的基础数据**（`StructureEntity.departmentList`、`UserEntity.roleList/departmentList`），
所以这轮除了迁自己，还得处理一个跨域连带：`factory/structure/list.vue` 用 `DialogUtil.selectList` 命令式打开旧的 airpower
`DepartmentSelector` —— 它是那个组件**唯一的外部消费者**，不一起改就没法删旧件。

| 文件 | 原用法 | 迁移动作 |
| --- | --- | --- |
| department / role / user 三实体 | 单套 airpower 装饰器 | **双注册**（`@K*` 与 airpower 别名并存）。airpower 那套保留的原因很具体：`UserEntity` / `StructureEntity` 的 airpower `@Type(...)` 还指向它们，而消费方（`component/UserCard.vue`、wms 库存页）属 F5 |
| **新增** `KDepartmentService` / `KRoleService` / `KUserService` | `DepartmentService` / `RoleService` / `UserService` | 新底座服务。**旧 `DepartmentService` / `RoleService` 已 `git rm`**（迁移后零消费者）—— 这两个域现在只有一份实现；`KUserService` 只做管理页 CRUD，认证链（`login`/`logout`/`getMyInfo`…）仍留在旧 `UserService`，等 F5 一起迁 |
| **新增** `system/{menu,permission}/K*Service.ts` | 授权弹窗原来用 `MenuService` / `PermissionService` | 两个薄服务（`baseUrl` + `entityClass`）。它们在 system 域迁移时会被复用，提前建不增加后续工作量 |
| `personnel/department/list.vue` | `APanel` + `ATable` + `useTableTree` | `KPanel` + `KTable` + `useTable({ tree: true })` + `KFormDialog`；「有子级禁删」改由 `KTable` 内置，`beforeAddRow` 回填父级改成 `preset: { parentId, parent }`；**不传 `enable` 权限**（原页没有启停动作，旧页是靠 `@Table` 有没有 `isDisabled` 列隐式决定的） |
| `personnel/role/list.vue` | `ATable` + 两个命令式弹窗 | `KTable` + `KFormDialog`；「菜单 / 权限」两个授权动作走 `KTable` 的 `:actions`（与合同页「生效 / 终止」同一条路：权限码由页面给，组件不认识业务常量） |
| `personnel/role/component/{menuEditor,permissionEditor}.vue` | `ADialog + DialogProps`，靠 `DialogUtil.show` 打开 | `KDialog + v-model`、回调只剩 `saved`。菜单树用 `ElTree`（**`check-strictly` 必须保留**：勾父节点不该连带勾子项，旧版就是这个语义）；权限树用 `ElTable` 树表 + 多选列，不换 `KTable` 的理由写在文件头（权限实体属 system 域，为一个弹窗去给系统域实体补元数据等于把批次边界搞糊） |
| `personnel/user/list.vue` | `ATreeBox` + `hook.request.value.filter.departmentId` | 左侧改 `ElTree`（数据走 `KDepartmentService.getList`，走 TanStack 缓存）+ `table.search({ departmentId })`；角色/部门多选走 `#extra` 插槽 + `KSelector(multiple/tree)`；提交前把两个列表裁成只带 id（旧 `copyOnlyId()` 语义） |
| `factory/structure/list.vue` | `DialogUtil.selectList(DepartmentSelector, ...)` | `KSelector(:service="KDepartmentService" multiple tree)` + 页面自己持有弹窗显隐 |
| **删除** | — | `personnel/{department,role,user}/component/*` 8 件（department 的 editor/selector/index、role 的 editor/selector/index、user 的 editor/index；两个 barrel 只是 re-export）+ `model/personnel/{department/DepartmentService,role/RoleService}.ts`（共 10 个文件） |

### 3.8 批 4：wms 域（本轮完成）

**范围**：入库（input）/ 出库（output）/ 移库（move）/ 库存（inventory）四个子域，含 4 个列表页、3 个单据编辑器、3 个单据详情、3 个明细编辑器、3 个「确认入库/出库/作业」弹窗、库存选择器；连带删掉 `factory/storage` 的 airpower 仓库选择器。

**本轮新增的框架件（后面 channel/system 批直接复用）**

| 件 | 作用 | 为什么必须有 |
| --- | --- | --- |
| `src/framework/bill.ts` | `AbstractBillService`：`audit` / `reject` / `addDetailFinishQuantity`（**三个**动作，2026-09-19 更正） | 单据动作是后端 `BaseBillController` 的固定契约（只有 audit / reject / addFinish），三个域各写一遍必然漂移 |
| `src/component/framework/KDetailTable.vue` | 内存明细表（`rows` + `entity` + `exclude` + `row-actions` + `#cell-*`） | 单据明细是**表单对象上的数组**（没有服务、没有分页、没有搜索），塞不进 `KTable`（它的契约是 `useTable`） |
| `KTable` 两处扩展 | `exclude-columns`（同表不同维度要的列不同）与 `hide-delete` / `hide-enable` | `v-permission` 未传值时**不拦截**，所以「没传权限码 ≠ 不渲染按钮」——单据没有删除端点与启停语义，必须显式关掉 |

**几个迁移时才暴露的真问题（已修，勿退回去）**

1. **出库域从来没被 lint 过**：`@antfu/eslint-config` 的默认 `GLOB_EXCLUDE` 含一条匹配「任意层级 `output` 目录」的规则（为 Nuxt 的 `.output` 准备），把业务域 `src/**/output/**` 一并排除了 —— 表现为「`eslint .` 全绿，可出库域里明明有排序/逗号类违规」。已在 `eslint.config.js` 里用一个**只含 `ignores` 的独立配置对象**反向取消（混进 `antfu({ ignores })` 那份列表会连带放开 `docs/**`/`infra/**`，实测 430 条噪音）。
2. **单据状态/类型/驳回原因不能进 K 表单**：`KFormDialog` 会按 `@KForm` 元数据**自动渲染**字段，而旧 airpower 编辑器是手写模板（只渲染 `billCode` + 目标仓库）。把状态挂进 `@KForm` 等于让用户直接改状态机结果；把 `type` 挂进去等于让用户改来源单据类型。新增时的类型默认值改由页面 `preset` 提供（与旧编辑器 `type ??= 普通X库` 同义）。
3. **库存选择器的物料过滤是空转**：旧代码把 `material` 放进 `param` 却从未使用，用户能选到别的物料的库存，提交后由后端抛「物料信息不匹配」（403）。选择器现已按 `param.material.id` 过滤（库存读接口的筛选只支持 `type`/`storageIds`/`structureIds`，所以是客户端过滤），后端那道校验保持不变。
4. **演示模式的详情端点以前是「空转」的**：自研 client 的 `getDetail` 是 `GET /{res}/getDetail/{id}`，
   而 mock 适配器把 URL 末段当成**动作段**（`action='3'`）→ 引擎命中不了任何端点，落到「未覆盖的读取端点」
   返回 `true` → 详情弹窗字段全是 `-`、明细永远为空（**列表页完全正常**，所以很难联想到 URL 解析）。
   修法：末段是数字时把动作还原成它的前一段（`payload.__action`），并让 blob 判定也走还原后的动作段。
   本批的入库/出库/移库详情弹窗都在演示模式里跑通了；asset/personnel 的详情弹窗同时受益。
5. **跨域跳转留成「接缝」而不是把 airpower 拖回来**：入库详情要能点开采购单、出库详情要能点开销售单，而 channel 域批 5 才迁。做法是在 channel 侧新增 `component/openDetail.ts`（两个小函数），wms 只调用函数 —— 于是 **wms 视图域内零 airpower import**，批 5 只需改那两个函数的实现。

**验收**

| 项 | 结果 |
| --- | --- |
| wms 域视图与 hook 的 airpower import | **0**（门禁用例钉死，域内没有例外文件） |
| `pnpm check` | exit=0：50 文件 / **441 用例**、首屏 **368.2/450 KB**、总体 **732.6/900 KB**、死件 0 / 未引用导出 0 |
| 单据闭环行为不漂移 | `wmsDomain.spec.ts` 14 例：端点与载荷（审核/驳回 `{id, rejectReason}`/作业**明细**载荷/一键完成）、审核二次确认、驳回「填原因 → 再确认」两步、详情「作业」仅状态机的可作业态可点 |
| 元数据与 `@KType` | 四实体列/表单非空、单据状态与类型不在表单、明细分层实例化（`fill()` 后 `constructor.name` 正确、明细带 `copyOnlyId`） |
| 演示 | ① 单据行补 `billCode` + `details[]`（含嵌套 `inventory.material.unit` / `storage`，三种完成度），避免「点开详情空空如也」这种**形似功能坏**的演示缺口；② 修掉 mock 的「路径 id 当动作段」缺陷（见上文第 4 条），并加回归用例；③ 端到端两条：demo 数据 → 列表 → 点「查看」→ 详情弹窗里真的有明细行 |

**删除清单（本轮 20 个已跟踪文件 + 1 个未跟踪残件）**：4 个旧服务（`Input/InputDetail/Output/OutputDetailService`）+ 5 个随 K 服务到位的旧服务（`Move/MoveDetail/Inventory/Storage/StructureService`）+ `base/bill/AbstractBaseBillDetailService.ts` + 3 个 barrel（input/output/move 的 `component/index.ts`）+ `wms/inventory/component/selector.vue`（重写）+ `factory/storage/component/{index,selector}.vue` + 5 个 `api/wms/*` typed client（`index/input/move/output/types`）。
airpower 生产足迹 **135 → 120**（净 −15）：wms 四个子域 16 个视图件 + `factory/storage` 选择器 1 件 + 随 K 服务到位后无消费者的旧服务/基类，扣掉 channel 侧新增的 2 个接缝文件。

#### 三个框架层补丁（都是"页面真的需要"才加）

| 件 | 变化 | 为什么必须 |
| --- | --- | --- |
| `framework/validate.ts` | 新增 `WebValidator.ifContain(text)` | 旧角色编辑器有一条业务规则「角色名不允许带『管理员』三个字」。换底座时这类**页面级自定义校验**最容易连规则一起丢；补回链式方法，规则本身仍写在页面里 |
| `KFormDialog` | 新增 `extraRules`（按字段补充/覆盖规则） | 自动生成的规则只能表达元数据里有的东西（必填/长度/格式/范围）。没有这个口子，上面那条业务规则就只能塞进实体装饰器——那是**把页面规则写进模型**，比丢规则好不了多少 |
| `MenuEntity` / `PermissionEntity` | 补 `@KType(MenuEntity)` / `@KType(PermissionEntity)`（**只补这一条**，其余 K 装饰器等 system 批） | 树接口（当时的 `getTreeList`，2026-09-19 起统一为 `getList`）用框架的 `fill()` 递归实例化，递归依据是框架注册表里的 `@KType`，airpower 的 `@Type` 只喂 airpower 的转换器。缺了它，`children` 是裸 JSON：表单**看起来正常**，但勾选子级菜单后 `KRoleService.authorizeMenu` 逐项调 `copyOnlyId()` 会 TypeError ——「能显示、提交才炸」是这批最隐蔽的坑 |

#### 批 3 的验收

1. **域门禁** `src/config/__tests__/personnelDomain.spec.ts`（16 用例）：域内 airpower 精确集合 = **空集**（并连带断言 `factory/structure` 不再用 `DialogUtil`）；旧件与两个旧服务「文件不存在」；三页走新底座（含 `useTanStackTable` / 直连 `@/model/*Service` 的禁令）；两个实体实例化（`fill()` 后 `children[0].copyOnlyId` 是函数）；`isDisabled` 与基类 `isPublished` 不同时出现在同一张表；
2. **演示模式端到端**（同文件）：真挂 `menuEditor` / `permissionEditor`，用 demo 适配器喂**真实菜单树与真实权限码**，点「保存」后断言授权项是实体实例、id 正确 —— 这条用例专门守 `@KType` 那个坑；
3. **全链**：vitest **49 文件 / 426 用例**；`vue-tsc`、ESLint、`pnpm build`、`demo:check`、`deadcode:check`、`exports:check`、`size` 全绿；
4. **airpower 退场**：生产文件 **145 → 135**（−10 = 部门/角色/用户三页重写 3 + 角色组件 4（编辑器/选择器/菜单授权/权限授权）+ 部门编辑器与选择器 2 + 用户编辑器 1）。
   另**删除 10 个文件**：8 个旧 `component/**`（两个 barrel 本来就不 import airpower）+ 旧 `DepartmentService`/`RoleService`（它们只 import 本仓 `AbstractBaseService`，不计入 airpower 足迹）。
   模型层 personnel 三实体保留双注册（5 个文件），退役留给 F5；
5. **演示数据补齐**（`pnpm dev:demo` 下的可看性，属"预览优先"要求）：① `menu/getList` 与 `permission/getList`（当时写作 `getTreeList`）不再走通用造数器，改为返回**真实菜单树**（与左侧导航同源）与**按真实权限码分组的权限树** —— 授权弹窗是"真的把 identity 提交给后端"的地方，编造的编码会让人误判授权对象；② 演示行补 `isDisabled`（K 侧状态列与启停按钮读它，缺了就永远显示"启用中"）；③ 用户行补 `departmentId`/`roleList`/`departmentList`，否则用户页点部门树会筛成空表（看起来像功能坏了）。三条都有契约用例（`demoPreview.spec.ts`）。

> ⚠️ **一条记录在案的边界**：`isDisabled`（本实体的状态列）与 `KEntity.isPublished`（基类的状态列）在语义上是同一件事。
> 三个实体目前都还继承 airpower 的 `BaseEntity`，所以不冲突；**将来切 `KEntity` 时必须二选一**，
> 域门禁里已经用一条断言把这个口径钉住了（同时声明两者即失败）。

### 3.9 批 5：channel 域（本轮完成）

**范围**：客户 / 供应商 / 采购 / 销售 / 采购价 / 销售价六个子域，含 6 个列表页、采购与销售的编辑器 + 明细编辑器 + 详情 + 作业弹窗、5 个明细/旧编辑器删除、`wms ↔ channel` 的两个详情接缝函数重指。

**这一批没有新增框架件**——批 4 建好的 `AbstractBillService`（审核/驳回/作业）与 `KDetailTable` 正好是 channel 要的全部：采购/销售也是"单据"，价目表是普通 CRUD 页。这也验证了批 4 把单据能力收到框架层的判断（否则这里必然要抄第二遍）。

**几个迁移时才暴露的真问题（已修，勿退回去）**

1. **命令式弹窗从来没有接过应用上下文**（本轮最大的一处）：`DialogUtil.show/select/selectList` 把弹窗渲染在**独立容器**里（`framework/feedback.ts` 的宿主），是一棵新的组件树，不继承 `main.ts` 那个 app 的注入；而 `setDialogAppContext` 这个入口**全仓无人调用**。后果是所有命令式弹窗里的 `useQuery` / `usePermissionStore` 直接抛 `No 'queryClient' found in Vue context` —— 异常发生在 `DialogUtil.show()` 内部的 `render()` 里，调用方 `void` 掉就再也看不见，页面表现只是**「点查看没反应」**。
   批 3/4 的详情弹窗是声明式（`:model-value`）所以没踩到；channel 的「查看」还是命令式，一踩就中。修法：`main.ts` 注册完指令后 `setDialogAppContext(app._context)`，并在 `sharedComponents.spec.ts` 加两条回归（一条真开弹窗验 `useQuery` 能拿到 client，一条钉住 `main.ts` 里那行接线）。
2. **批 0B 的三件"待迁件"其实没人要**：`component/bill/BillFormCode.vue`、`BillFormMoreDetail.vue`、`hook/billTable/*`。批 5 复核发现它们的真实消费者是**旧 airpower 单据编辑器**（`ABillFormCode` 之类的模板写法），channel 换成 `ElDescriptions` + `@KTable` 元数据渲染后，连测试都只能靠 barre 引用它们——`deadcode:check` 直接点出"没有被任何 import 或路径字符串引用"。故一并删除，0B 就此**消解**（不是"待迁"）。`base/bill/AbstractBaseBillService.ts` 同理（五个实体都已改用 `framework/bill.ts` 的 `AbstractBillService`）。
3. **模拟数据要跟后端形状对齐**：采购/销售在演示里也要是"单据"（`billCode` + `details[]` + `totalPrice` + `reason`/`rejectReason`），价目表行要有 `price` 与嵌套 `material`/`supplier`/`customer` —— 否则页面看起来"能开但全是空/全是 `-`"，是**形似功能坏**的演示缺口。
4. **域内旧缺陷顺手修掉（不在迁移范围但挡在路上）**：`SalePriceEntity` 三处（`customerName`/`customerCode` 的取值与标题互换、残留 `materialId` 扁平字段、`customer` 缺 `@Table()`）、`sale/component/editor.vue` 文案两处「采购单/采购明细」、`SaleEntity` 里悬挂与重复的 K 装饰器组（锚点插 K 时留下的空组，`@KForm` 下没有成员）、`SupplierEntity` 的类名仍是 `CustomerEntity`（模板复制残留）。都在 K 侧按后端契约修正，函数签名与端点未动。

**验收**

| 项 | 结果 |
| --- | --- |
| channel 域视图与 hook 的 airpower import | **0**（门禁逐文件扫描，域内无例外） |
| 旧链退场 | `api/channel/*`（4 件）+ 8 个旧服务 + 5 个 barrel 全部 `existsSync === false`；视图不再出现 `useTanStackTable` / 直连 `@/model/**Service` |
| `pnpm check` | exit=0：**51 文件 / 457 用例**、首屏 **367.9/450 KB**、总体 **735.7/900 KB**、死件 0 / 未引用导出 0 |
| 单据动作不漂移 | `channelDomain.spec.ts` 13 例：审核二次确认、驳回「填原因 → 再确认」两步与文案（`销售单` 不再写成采购单）、四动作端点与载荷（`addFinish` 载荷=明细+本次数量）、报价查询载荷 `{material:{id}, supplier|customer:{id}}`、价目表脱敏 `blockedSubmit` 仍在 |
| 元数据与 `@KType` | 六实体列/表单非空、状态与 `rejectReason` 不进表单、明细分层实例化（`constructor.name` + `copyOnlyId`）、销售价 `customer` 递归成 `CustomerEntity` |
| 演示 | 采购/销售列表 → 点「查看」→ 详情弹窗里出现**真实明细行**（整体走 demo 适配器，同时回归批 4 修的「路径 id 当动作段」） |
| 接缝 | `channel/{purchase,sale}/component/openDetail.ts` 改指 `@/framework` 的 `DialogUtil`，**wms 出库/入库详情调用方一行未动** |

**删除清单（34 个已跟踪文件）**：8 个旧服务 + `api/channel/*` 4 件 + 5 个 barrel + 7 个旧视图件（customer 详情/编辑器/选择器、supplier 编辑器/选择器、两处 price 编辑器）+ 物料选择器三件（`asset/material/component/{selector.vue,index.ts}` + `MaterialService.ts`）+ `component/bill/{BillFormCode,BillFormMoreDetail}.vue` 与 `component/index.ts` + `hook/billTable/*`（2）+ `base/bill/AbstractBaseBillService.ts`。
airpower 生产足迹 **120 → 92**（−28 = 重写 16 个文件不再 import + 删除 12 个 airpower 文件）。剩余 92 个里 **36 个是 system 域**（批 6 一次收口），其余是各域实体的双注册装饰器与 F5 拆伞项。

### 3.10 批 6（system）+ 整体退场：**一次删净，再成批修复**

**范围**：system 域最后 17 个视图（menu / permission / coderule / config / log 列表与详情 / datapermission 的策略、资源、列级三页 + 三页编辑器 + `view/error.vue`），加上所有还挂在 airpower 上的基础设施件 —— 一次做完。

**为什么改口径（这一步推翻的是本文件前面"按域分批"的原则，值得记下原因）**：边删边补时，
每迁一个域都要重跑全链才能发现"下一处缺什么"，而缺口的形状高度相似（旧基类、旧服务、旧装饰器、旧适配器），
一次修一处等于把同一类判断做几十遍。收官这一步改成：**先把 airpower 的调用面一次砍掉（接受构建红）→ 让编译器把缺口全列出来 → 成批修**。
实测从 typecheck ~200 处降到 0 用了两轮（框架补口一轮、页面重写一轮），比逐域快得多。

**成批修复清单（编译器列出来的，不是猜的）**

| 类别 | 内容 |
| --- | --- |
| 框架补口 | `KTable.hideEdit` / `disableAddChild(row)`；`IFormModel`（KFormDialog 的 `@loaded` 与插槽类型）；`WebValidator.ifNotOnlyNumberAndLetter()`；`requiredString` 支持自定义消息串；`Permissions.System.Config.delete`（后端 `ConfigController` 未排除删除，前端原先没有这个码）；`element-plus` 聚合入口补回 `ElAvatar` / `ElDropdown*` |
| 重写 | `UserCard.vue`（头像上传改走 `DialogUtil.showUpload`，登出只走 `userStore.logout()`）、`user/{UserAccount,ModifyPassword,PersonalToken}.vue`（不再用 `AUser`/`ADialog`/`DesensitizeType`）、`error.vue`、`appRouter.ts`、`authStorage.ts`（原 `airpowerCompat`） |
| 删除 | `airpower-bootstrap.ts`、`useTanStackTable.ts`（零消费者）、`infra/plugins/airpower-{css-strip,element-plus-shim}.ts` + `vite.config.mts` 接线 + vendor-airpower 分包、`@airpower/web` 依赖、`find-airpower.mjs`（改写成门禁） |
| 连带死码 | `FileEntity` / `PermissionService` / `ParticipantRoleEnum` / `UserGenderEnum`（失去最后消费者，`deadcode:check` 抓出） |

**收官轮修掉的两个静默缺陷**

1. **EP 语言包从未生效**：它挂在 `WebConfig.elementPlusLocale` 上等 airpower 组件来读，而按需导入下项目没有
   `app.use(ElementPlus)` —— 日期选择器、分页器、`ElMessageBox` 的文案一直是英文。改为
   `main.ts` 的 `provideGlobalConfig({ locale: zhCn }, app, true)`（app 级 provide，命令式弹窗那棵独立树同样继承）。
2. **`error.vue` 的 5 秒熔断后门**：旧版停留 5 秒就把 `WebConfig.isTimeout` 置真，而 `RouterUtil.initVueRouter()`
   在它置真时跳过全部动态菜单注册 —— 症状是"进错一次页面，整个控制台点哪都是 404"。新版按路径直接判 403/404/500，
   不再有任何全局开关。`sharedComponents.spec.ts` 里那条断言同步改成"路由层不该再有 isTimeout"。

**验收**

| 项 | 结果 |
| --- | --- |
| airpower 引用 | **0**（`pnpm airpower:check`：`src/`、`infra/`、`scripts/`、根配置、`package.json` 共 365 个文件，剥注释后 0 命中；注释里的历史提及 27 处不计） |
| `pnpm check` | exit=0：**51 文件 / 459 用例**、首屏 **270.4/450 KB**、总体 **630.4/900 KB**、死件 0 / 未引用导出 0 |
| 体积 | 对比退场前（367.9 / 735.7）：**首屏 −97.5 KB、总体 −105.3 KB** |
| 新增门禁 | `elementPlusAggregate.spec.ts`（2 例，钉住"源码里 `from 'element-plus'` 的具名导入必须在别名聚合入口里有导出"—— 这个坑本轮踩了两次，只在 `pnpm build` 炸）+ `authStorage.spec.ts`（4 例，钉住 localStorage key 不变） |
| 演示 | `pnpm dev:demo` 登录 → 控制台（顶栏菜单/账号安全/令牌/头像上传入口）→ 各域列表页正常渲染 |

**残留（已知，不在本轮范围）**：`src/base/` 只剩两个薄壳 —— `BaseEntity`（唯一实体基类，继承 `@/framework` 的 `RootEntity`，`KEntity` 已删）与 `AbstractBaseService`（在 `@/framework` 的 `AbstractCurdService` 上加一个带提示的 `publish()`），二者都已不 import airpower；`ITableColumn.hide` 在新底座里等价于"列仍显示"（没有列选择器），
`metadata.spec.ts` 里把这个语义写死为契约 —— 14 处 `hide: true` 的列因此比 airpower 时代更显眼，要收口需先决定"加列选择器"还是"改成不渲染"。

## 四、批 3~6：按域推进（先小后大，先低风险后高敏感）

| 批次 | 域 | 视图文件 | 依赖缺口（除批 0 外） | 备注 |
| --- | --- | --- | --- | --- |
| 批 1 | factory + index | 6 + 1 | 无（已具备） | ✅ **本轮完成**：仓库/生产单元改用 `useTable({ tree: true })` + `KTable` + `KFormDialog`，首页 `APanel` → `KPanel`；两个跨域选择器**按设计保留**（wms 的入库/移库页仍用它们，属批 4） |
| 批 2 | asset | 8 | `KSelector`、`useDetail` | ✅ **本轮完成**；另补了异步导出、日期控件、`cell-*` 插槽。原计划的"批 2B"经复查撤销：合同三件是零消费者死件（已删），物料选择器随批 4/5 迁移（见 §3.6） |
| 批 3 | personnel | 11 | `useTreeTable`（部门树）、`KSelector`（用户/角色选择器） | ✅ **本轮完成**（§3.7）：三页 + 两个授权弹窗 + 两个新服务；旧 `DepartmentService`/`RoleService` 已删，`UserService` 留给认证链（F5）。连带改掉 `factory/structure` 的部门选择器 |
| 批 4 | wms | 20 | `KSelector`、`KDetailTable`、单据服务基类（本轮补）、单据公共件（批 0 已备） | 单据闭环最完整（出入库/移库/库存），是**业务主线**，放在渠道域之前 —— ✅ **本轮完成**（§3.8） |
| 批 5 | channel | 23 | `KSelector`、`AMoney` | 采购/销售/价格 + 客户/供应商，文件最多但结构高度同构，可脚本化批量替换 |
| 批 6 | system | 21 | `useTreeTable`（菜单/权限树） | 权限、数据权限、日志页是「平台自身的门」，最后动，避免迁移期自锁 |

估时口径（供排期）：每域按「列表页 1 个 / 编辑弹窗 1 个 / 选择器 1 个 / 详情 1 个」为 1 单位，批 1 约 3 单位、批 2 约 4 单位、批 3 约 5 单位、批 4 约 8 单位、批 5 约 9 单位、批 6 约 8 单位。

## 五、每批的完成定义（DoD，缺一不可）

1. **零 airpower import**：该域所有 `.vue` 内 `import ... from '@airpower/web'` 为 0。批 1 的门禁断言的是
   **精确剩余集合**（两个跨域选择器）而不是"数量为 0" —— 新增一处 airpower 用法会让用例变红，
   集合变空时也提示可以收紧断言。见 `src/config/__tests__/frameworkComponents.spec.ts` 的
   `f4 · 批 1（factory + index）迁移完成度`。
2. **探针全绿**：`src/demo/__tests__/menuSmoke.probe.spec.ts`（逐页独立挂载，`errorHandler`/`console.error` 计硬错误）在该域页面全部 ✅。
3. **新增域门禁 spec**：仿 `src/config/__tests__/frameworkComponents.spec.ts`，至少覆盖「请求体契约 + 元数据驱动渲染 + 该域独有交互」三件事。
4. **类型与体积**：`vue-tsc --noEmit`、`eslint src` 干净；`pnpm build && pnpm size` 达标（当前 首屏 370/450 KB · 总体 745.1/900 KB；批 2 的 +27 KB 来历见 3.4）。
5. **手工点检**：`pnpm dev:demo` 下按「列表 → 新增 → 编辑 → 删除 → 分页 → 排序 → 搜索 → 选择器」走一遍（用真实浏览器，探针只保证渲染不炸）。

## 六、迁移机制（沿用 F2 已落地的双底座过渡）

- 新页实体基类 = `src/base/KEntity.ts`（注册 `@/framework` 元数据）；**旧页 `BaseEntity` 不动**，否则 144 个页面同时失效。
- 单个实体在过渡期**双套装饰器**：airpower 别名 + `KModel/KField/KForm/KSearch/KTable`（见 `src/model/system/unit/UnitEntity.ts`）。
- 服务可并存：`UnitService` 与 `KUnitService`（同 `baseUrl`，无数据漂移），页面切完后删旧。
- 视图层不直接 `import @/model` 的 Service，统一经 `src/hook/system/*` 转发（ESLint `no-restricted-imports` 已拦）。
- 域内页面全部切完后，**一次性**删除该域模型上的 airpower 装饰器与旧 Service —— 这一步必须整域做完再做，半域删除会让旧页面拿不到元数据。

## 七、风险与对策

| 风险 | 对策 |
| --- | --- |
| 选择器（`KSelector` 16 处）语义复杂：单选/多选、回显、`withParam` 过滤 | 先用「弹窗内嵌 `KTable` + 已选行」实现，覆盖面优先；树形选择器单独排期 |
| 单据页共享件（批 0）被改坏 → 多域同时炸 | 批 0 独立提交 + 探针全绿后再开批 1；批 0 期间冻结单据相关页面改动 |
| 权限树 / 菜单树迁移期自锁（改坏就进不了后台） | 放在批 6；迁移前先确认超管旁路可用，并保留 `git revert` 单批回滚能力 |
| 模型层双装饰器遗漏 → 页面「字段不见了」但不报错 | 域门禁 spec 断言「实体字段数 == 表格列数 + 表单字段数」，用数量对账替代肉眼检查 |
| 双底座**枚举基类不同**：airpower 的 `WebEnum` 只有 `toArray()/getLabel()`，本仓是 `getList()` —— 新表格读 airpower 枚举会「列头有、值是数字」 | `framework/enums.ts` 统一取值 `getEnumItems/getEnumLabel/getEnumColor`（两种形状都吃），并有单测钉住；`IWebEnumConstructor` 的静态方法声明为可选 |
| 迁移中把**跨域公共件**（选择器、单据组件）一起改掉，会连带打挂尚未迁移的域 | 只改"叶子页"，跨域件按"最后一个使用方"排期；新页面通过 `DialogUtil` 打开存量 airpower 选择器（契约一致，可混用） |
| 树表的**响应形状**搞错：`getTree` 返回 `{ list, total }`，树导航用的 `getList` 返回**裸数组** —— 形状不对时表现为"树表永远空"且**不报错** | 框架 `getList` 对非数组返回 `[]`；演示 mock 已按端点分别返回正确形状（见下） |

## 八、树查询端点的事实（本轮查证，避免再踩）

| 端点 | 谁在用 | 响应形状 | 说明 |
| --- | --- | --- | --- |
| ~~`POST /{baseUrl}/getTreeList`~~ | —— | —— | **2026-09-19 更正：后端没有这个端点**（本条曾是 airpower 时代的端点名）。前端已全部改走下一行的 `getList`；`pnpm api:check` 现在会拦住这类"前端有、后端无"的端点 |
| `POST /{baseUrl}/getList` | **所有树页与树选择器**（`useTable({ tree: true })`、`KSelector`）、`ASelector` 下拉 | `E[]`（树实体是嵌套树） | 后端 `ITree` 实体的树导航就在这条上：`beforeGetList` 空 `parentId` 兜成根、`afterGetList` 递归回填 `children`；部门/菜单/权限再套 `TreeUtil.buildTreeList` |
| `POST /{baseUrl}/getTree` | `src/api/{personnel,system}` 的 typed client | `{ list, total, ... }` | 演示 mock 原本只支持这一条的树形状 |

**演示 mock 的坑（本轮已修）**：`src/demo/mock/engine.ts` 之前只处理 `getTree`，
当时前端调的 `getTreeList` 落到 `action.startsWith('get')` 的兜底分支返回 `{}` → 框架层当成空数组 →
**树表页在预览里永远是空的**（旧 airpower 树页也一样，不报错所以一直没被发现）。
修复后仓库/库位结构页的渲染体积从 13 KB / 15 KB 涨到 110 KB / 128 KB（真实树数据）。

> **2026-09-19 更正（本轮抓到的真问题）**：上表原先写 `getTreeList` 是"仓库/生产单元/部门/菜单/权限
> 树页都走它"—— 查后端源码后确认**后端从没有 `getTreeList` 映射**（`grep '@PostMapping("getTreeList")'` = 0），
> 那些树页在**真实后端**上是一次 404：演示 mock 实现了这个动作，所以只在预览里"看起来能用"。
> 已按后端事实统一改走 `getList`，并新增门禁 `pnpm api:check`（前端端点字面量 → 后端 `@*Mapping` 对账，
> 任一前端端点在后端没有映射即红；防空转：后端 < 40 条或前端 < 12 条同样判红）。
> 同批清掉的另一个幻影端点是 `setBillDetailsAllFinished`（后端只有同名 Service 内部回调，没有控制器映射）。

**遗留观测的后续（批 3 已解一半）**：当时部门/菜单/权限三页（airpower `useTableTree`）在探针里
字节数几乎不受该修复影响（30 KB → 30 KB），说明它们**即使拿到数据也没渲染出行**。
批 3 把部门页迁到 `useTable({ tree: true })` + `KTable` 后，探针字节数 **30 → 53 KB**，树真的渲染出行了
（同一轮把 personnel 三页都跑了一遍：用户 91 KB、角色 76 KB、部门 53 KB，均 ✅ 无硬错误）。
菜单/权限两页仍属 system 批，到那批再核同样的现象。

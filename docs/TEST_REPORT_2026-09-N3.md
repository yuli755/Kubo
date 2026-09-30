# SPMS 真机测试报告 · 2026-09 · N3（round 9–12）

> 承接 `docs/TEST_REPORT_2026-09-N.md`（round 1–4）与 `docs/TEST_REPORT_2026-09-N2.md`（round 5–8）。
> 本轮把**采购 → 入库 → 库存 → 销售 → 出库**全链路跑通到底，并修掉两个真缺陷（N-11、N-12）。
> 本模型无图像输入：所有判定来自 DOM 量测（`getBoundingClientRect` / `getComputedStyle` / `__vueParentComponent` 遍历）与真实网络响应，截图仅供人复核。

---

## 0. 判定表

| 编号 | 现象 | 判定 | 处置 | 提交 |
| --- | --- | --- | --- | --- |
| **闭环** | 采购→入库→库存→销售→出库 全链路 | ✅ **打通** | 证据见 §1 | — |
| **N-12** | 出库「确认出库」与移库「从库存选择」：选中库存被静默丢弃、选择器关不掉 | 🔴 **真缺陷（P0）** | 已修 + 新守卫 + 变异自检 | `3de9287b`、`58cbf4ba` |
| **N-11** | 六个详情页头部把字段名印成原始英文键 | 🔴 **真缺陷（P1）** | 已修 6 处 + 新守卫 + 变异自检 | `a0b8cca6` |
| N-10 | 列表默认升序，最新单据沉底 | 🟢 已在 round 8 修 | **本轮在销售、出库、移库三个模块复验通过** | `685762cf` |
| N-13 | `KDialog.vue` 头注释自相矛盾，且错的那半正是 N-12 的源头 | 🟡 文档缺陷 | 已改写为「两种接线契约」 | `58cbf4ba` |
| N-6 | `menu/getList` 对零权限用户开放 | ⚪ 设计取舍 | 不修（依据见 N2 §6） | — |
| — | b107 记载「确认出库提交后零 toast」 | ⚪ **探针误差，已更正** | 见 §4 | — |
| N-9 | `PurchaseService.java:220` 日志打出 null 单号 | 🟡 低危 | 未修，见 §7 | — |
| N-5 | 零权限用户落地 404 | 🟡 中危 | 未修，见 §7 | — |
| — | 采购详情弹窗写操作不回刷外层列表 | 🟡 低危 | 未修，见 §7 | — |

---

## 1. 全链路闭环：证据链

一条业务线走完，每一步都有真机请求/响应或数据库读数：

| # | 动作 | 端点 | 结果 |
| --- | --- | --- | --- |
| 1 | 采购制单 | `POST /api/purchase/add` | `PC202609300004`（id=10），明细 MacBook Pro M4 Max ×2，单价 28000 |
| 2 | 采购审核 | `POST /api/purchase/audit` | traceId `ed0dc969-3e36-456f-8fb8-469baa4ac5d1`；**自动创建采购入库单** `IN202609300007`（id=170，type=3，status=1） |
| 3 | 采购作业（登记到货） | `POST /api/purchase/addDetailFinishQuantity` | traceId `9b655030-cc7e-4804-bc00-fd72e9018201`；明细 `finishQuantity 0→2`、`isFinished=true`；单据 `status→5 已完成`、`totalRealPrice→56000` |
| 4 | 入库审核 | `POST /api/input/audit` | traceId `7aaed2ae-fd48-4d7e-a6d6-dacc276876d5`；`status 1→3` |
| 5 | 确认入库（作业） | `POST /api/input/addFinish` | traceId `e49d87b0-9d34-42c5-b7f5-e387a8f49515`；`status 3→4 已完成` |
| 6 | **库存增加** | `inventory/getPage` | MacBook Pro M4 Max \| 东部1仓 **31 → 33** |
| 7 | 销售制单 | `POST /api/sale/add` | traceId `27b199ff-5244-41e5-83ee-4ec44c97ad13`；`SL202609300001`（id=5），单价 **29999.00 由销售价协议自动带入**，总额自动核算 `59998.00`；列表**第 1 行 / 共 5 行** |
| 8 | 销售审核 | `POST /api/sale/audit` | traceId `e823e7bc-1dc4-423f-8a2d-06c5d12b0b81`；`status→3 出库中`；**自动创建销售出库单** `OUT202609300005`（id=114，type=3，saleId=5） |
| 9 | 出库审核 | `POST /api/output/audit` | traceId `fc71ec8c-ea75-4605-8849-c6b33cafc74c`；`status 1→3`；行内按钮变为 `查看=en 审核=dis 驳回=dis 编辑=dis` |
| 10 | **确认出库（作业）** | `POST /api/output/addFinish` | **本轮完成**。traceId `2985d9b7-c4bd-4c80-8a3e-92364a76cba2`；响应 `200 {"code":200,"message":"添加完成数量成功"}`；toast `明细出库成功` |
| 11 | **库存扣减** | `inventory/getPage` | MacBook Pro M4 Max \| 东部1仓 **33 → 31**（东部2仓 15 未动） |
| 12 | **反向回填** | `output/getDetail`、`sale/getDetail` | 出库明细 `fin 0→2`、`done=true`、单据 `status 3→4 已完成`；销售明细 `fin 0→2`、`done=true`、单据 `status→4 已完成` |

第 10–12 步的界面证据（修复 N-12 后）：
```
pickerClosed = true                        ← 修复前恒为 false
labels = ["物料信息","库存来源","当前库存","待出数量","出库数量"]
inputs = ["text|v=MacBook Pro M4 Max|dis=true",
          "text|v=东部1仓(SRG0002)|dis=false",   ← 选中的库存写回了
          "text|v=33|dis=true",                  ← 「当前库存」条件字段出现（v-if="inventory"）
          "text|v=2|dis=true",
          "number|v=2|dis=false"]
toastNow = ["明细出库成功"]
errs = []                                   ← 0 console error / 0 pageerror
```
移库侧的对照复验（`58cbf4ba` 之后，`/console/wms/move/list`）：点 `从库存选择` → `选择库存` 弹窗 → 点行内 `选择`（`MA20260001|MacBook Pro M4 Max|东部1仓(SRG0002)|按仓库|31|台`）→ **`pickerClosed=true`**，自动弹出「移库明细」，其字段为 `转移物料=[text|v=MacBook Pro M4 Max|dis=true]`、`移动数量=[input|v=31]`，按钮 `全部转移（31 台）=en / 取消=en / 确定=en`。
⇒ **选中值不仅让弹窗关掉了，还真的被带进了下游表单**；`31` 这个数字同时反证第 11 步的库存扣减已落库。

**N-10 三次复验**：本轮在销售（新单落第 1 行 / 共 5 行）、出库（首行 `OUT202609300005`）、移库（首行 `MV202609300002`）三个模块都确认「最新单据在第 1 行」。

**顺带的正向发现**（报告用，非缺陷）：
- 销售编辑器（`900x720`）比采购编辑器多两项能力：**扫码枪录入框**（`扫码枪扫入物料条码 (回车直接录入/累加)`）与 **批量添加** 按钮。
- 销售明细的单价 `29999.00` 由销售价协议自动带入，总额 `59998.00` 自动核算，用户无需手算。
- 客户选择器 `选择销售客户` 电话列显示 `176****6666`（脱敏在渲染层生效，与既有结论一致）。

---

## 2. N-12 · 弹窗接线契约：`:on-confirm` 与 `@confirm` 不是同一回事（P0，已修）

### 2.1 现象（round 11 实测）
出库单 `OUT202609300005` 已审核为「出库中」，点「作业」打开 `确认出库`，点 `选择` 打开 `选择库存`，点中 `MacBook Pro M4 Max | 东部1仓 | 33` 那行的 `选择`：

- **`pickerClosed = false`** —— 选择库存弹窗 9 秒内没关；
- `确认出库` 的 `库存来源` 输入框**仍为空**，且**没有出现 `当前库存` 这个 `v-if="inventory"` 条件字段** ⇒ 选中值压根没写回；
- 点 `确认出库` ⇒ toast `请选择库存来源`，两个弹窗都关了，**零请求发出**，后端零变化（`output id=114 status=3 / fin=0`、库存仍 33、销售仍 `fin=0`）。

即：**出库作业这条路在 UI 上是死路**，而它是销售闭环的最后一步。

### 2.2 根因
`src/view/console/wms/inventory/component/selector.vue`（402 行，库存选择器）的确认路径**只 emit**：
```ts
// selector.vue:148-151
function pick(row: InventoryEntity) { emit('confirm', row); visible.value = false }
// selector.vue:158-161
function onCancel() { emit('cancel'); visible.value = false }
// selector.vue:60-63  visible 依赖宿主回写 modelValue
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
// selector.vue:47-52  modelValue 默认 true
```
而宿主写的是**连字符 props**：
```vue
<!-- 修复前 addFinishEditor.vue:210-215，move/component/editor.vue:262-267 同构 -->
<InventorySelector
  v-if="inventoryPicking"
  :param="inventorySeed"
  :on-cancel="() => inventoryPicking = false"
  :on-confirm="onInventoryPicked as never"
/>
```
机制（Vue 3）：
1. `@confirm="h"` 编译成 vnode prop 键 **`onConfirm`**（驼峰）；`:on-confirm="h"` 编译成 **`on-confirm`**（连字符）。
2. `emit('confirm')` 内部只按 `toHandlerKey(camelize('confirm')) = 'onConfirm'` 去 `vnode.props` 里查 —— 它**不会 camelize `vnode.props` 的键**。事件名是单词，`event !== event.toLowerCase()` 的兜底分支也不触发 ⇒ **查不到，静默不派发，且不报错**。
3. 同时 `props.modelValue` 恒为默认 `true`，而宿主没绑 `@update:model-value` ⇒ 选择器**结构上无法自己关闭**（`pickerClosed=false` 精确由此而来）。
4. `inventoryPicking` 只在 `onInventoryPicked()` 里被置 false，该函数从未被调用 ⇒ `库存来源` 恒空 ⇒ `onConfirm()` 撞 `if (!source?.id) { FeedbackUtil.toastWarning('请选择库存来源'); return }`（`addFinishEditor.vue:103-127`）。

`as never` 是它能长期潜伏的第二个原因：**`:on-confirm` 的类型检查比 `@confirm` 松**，`as never` 直接把检查掐掉了。改成 `@confirm` 后 `vue-tsc` 立刻报 `TS2322`，逼出了正确的处理器签名（见 2.4）。

### 2.3 爆炸半径：两类契约并存，只有 emit-only 型会坏
grep `emit\('confirm'\)|props\.onConfirm|onConfirm\?\.\(|defineEmits` 在 `src/component` 的 21 处命中，分成两型：

| 型 | 组件内部行为 | 外层正确写法 | 实例 |
| --- | --- | --- | --- |
| **emit-only** | 只 `emit('confirm', data)` | **`@confirm`** | `framework/KDialog.vue:91`、`framework/KSelector.vue:97`、`wms/inventory/component/selector.vue:148` |
| **props-call** | 内部自己 `@confirm="() => props.onConfirm?.(data)"`，对外暴露 `IDialogActionProps` | **`:on-confirm`** | `bill/BillRejectDialog.vue:29`、`bill/BillAddFinishDialog.vue:32`、`framework/KUploadDialog.vue:47` |

**关键结论（避免误伤）**：仓库里另有 6 处 `:on-confirm`，全部挂在 **props-call 型** 组件上，**都是正确的、功能未受影响**：
- `wms/output/list.vue:152`、`wms/input/list.vue:155`、`channel/purchase/list.vue:160`、`channel/sale/list.vue:153`、`wms/move/list.vue:159`（五处驳回弹窗 `<BillRejectDialog :on-confirm="(data) => onRejectConfirm(String(data ?? ''))" />`）
- `wms/move/component/detail.vue:229`（移库作业 `<BillAddFinishDialog :on-confirm="(data) => onFinishConfirm(Number(data ?? 0))" />`）

判据是 `src/component/bill/BillRejectDialog.vue`（61 行，全文已读）：它**没有 `defineEmits`**，模板是
`<KDialog … @cancel="() => props.onCancel?.()" @confirm="() => props.onConfirm?.(rejectReason)">`（L28-29）。Vue 解析 props 时会对原始键 camelize，`on-confirm` → `onConfirm` → 落进 `props.onConfirm` ⇒ 连字符写法在这一型下**恰好成立**。
⇒ **N-12 的实际爆炸半径是 2 处（出库 + 移库），不是 8 处。** 这条区分很重要：不加区分地「全量改 `@confirm`」会把五个模块的驳回功能改坏。

### 2.4 修复
两处（`src/view/console/wms/output/component/addFinishEditor.vue`、`src/view/console/wms/move/component/editor.vue`）统一改为事件写法，并各加中文注释说明机制：
```vue
<InventorySelector
  v-if="inventoryPicking"
  :param="inventorySeed"
  @cancel="inventoryPicking = false"
  @confirm="onInventoryPicked"
/>
```
移库侧还被迫修了处理器签名（`editor.vue:86-94`）—— `@confirm` 的监听器类型是 props 契约 `(data?: unknown) => void` 与 emit 声明 `(data: InventoryEntity | InventoryEntity[]) => any` 的**交集**，所以处理器必须能吃 `unknown`：
```ts
function onInventoryPicked(pickedRaw: unknown) {
  const picked = (Array.isArray(pickedRaw) ? pickedRaw[0] : pickedRaw) as InventoryEntity | undefined
  inventoryPicking.value = false
  if (!picked) { return }
  …
```
（出库侧本来就是 `(picked: unknown)`，所以没报。）

**同文件内的强对照**：`move/component/editor.vue:253-260` 紧邻上方的 `<KSelector … @confirm="onStoragePicked" />` 本来就是正确写法 ⇒ 同一个文件里两种写法并存，连字符那处是笔误而非设计。

### 2.5 N-13 · 头注释自相矛盾，且错的那半正是源头
`src/component/framework/KDialog.vue:20-24` 原文（逐字）：
> `**只 emit，不再手动调 props.onConfirm**。原因：@confirm="h" 与 :on-confirm="h" 在 Vue 里是同一个东西（都会进 vnode.props.onConfirm），emit('confirm') 会把它派发到 —— 两种写法本来就同一条路。`

而同一注释块的 L31-34 写的却是**相反且正确**的：
> `⚠️ 外层**必须写 @confirm / @cancel**，不要写 :on-confirm="handler"。后者在编译产物里是 kebab 键 on-confirm，而 emit('confirm') 只在 vnode.props 里找 onConfirm / camelize 后的键 —— 找不到就**静默不派发**…`

L20-22 断言「两种写法本来就同一条路」，L31-34 断言「连字符键永远命中不了」—— 两者不能同时为真，**真相是后者**。前者还给出了错误的机制解释（`vnode.props.onConfirm`），这正是两处 `:on-confirm` 得以写出来的理由。
已改写为 §2.3 那张「两种接线契约」表，并补上记忆口诀：**看组件内部有没有 `props.onConfirm?.()` —— 有就写 `:on-confirm`，没有就写 `@confirm`。**

### 2.6 守卫 + 变异自检
新增 `src/config/__tests__/dialogWiring.spec.ts`（6 条，全绿，518ms）。核心是**先给组件分型，再双向断言接线**：
- `stripComments()` 分类前剥掉 `/* … */` 与 `<!-- … -->`（**必需**：`KDialog.vue` 的头注释里就写着示例 `<KDialog @confirm="props.onConfirm?.(rejectReason)" />`，不剥掉会把纯 emit-only 组件误判成 props-call，于是「`@confirm` 只能挂 emit-only」那条把 **28 处正确的 `<KDialog>` 接线**全报成违规 —— 首跑 `5 passed / 1 failed` 就是这个原因）；
- `openTagAttrs()` 手写线性扫描器（**不能用正则**：原先的 `/<([A-Z][\w-]*)((?:[^<>"']|"[^"]*"|'[^']*')*)>/g` 触发 `regexp/no-super-linear-backtracking`；手写版还能正确处理属性值里带 `>` 的情况）；
- `resolveModule()` 支持 `@/`、相对路径、`.vue`、`.ts` 桶文件（`wms/inventory/component/index.ts` → `selector.vue` 就是靠这层解析）；
- 双向断言：`:on-confirm` 只能挂 props-call 型、`@confirm` 只能挂 emit-only 型，两条都必须 `toEqual([])`；
- 三条自检防退化：必须真扫到接线且两种写法都在场、分类器把三个已知样本分到正确两侧、**每处接线都必须能解析到源文件**（否则守卫有盲区）；
- 一条回归钉：两个 `InventorySelector` 接线的 `kind` 必须是 `'event'`。

**变异自检（双向）**：把 `move/component/editor.vue` 临时改回 `:on-cancel` / `:on-confirm` ⇒ **2 failed | 4 passed**，报错逐字点名 `view/console/wms/move/component/editor.vue → <InventorySelector> :on-cancel=` 与 `:on-confirm=`（第 3 条）+ `expected 'prop' to be 'event'`（第 6 条）；改回后复跑 6/6 绿 ⇒ **守卫非空洞**。

### 2.7 为什么绿测试没抓住
`src/config/__tests__/sharedComponents.spec.ts:156` 用 `{ props: { onConfirm: onConfirmProp } }`（**驼峰**）挂 `KDialog`。驼峰键能被 emit 找到，所以这条测试一直是绿的 —— 它测的是**测试自己写的那种接线**，不是页面里真实存在的那种。
这与本项目已付过的两次学费同类（`consoleMount` 曾量到 404 页；`wmsDomain.spec.ts:322` 的「一键满额速填」是对着 mock 过的）。**教训：绿测试掩盖线上缺陷的典型形态是「量错了对象」——守卫必须扫真实源码的写法，而不是在测试里重演一种理想写法。**

---

## 3. N-11 · 详情页头部把字段名印成原始英文键（P1，已修 6 处）

### 3.1 现象
出库单详情头部实测原文（round 10）：
```
出库单详情 billCode OUT202609300005 type 销售出库 status 出库中
createTime 2026-09-30 18:58:11 updateTime 2026-09-30 18:59:20 销售单号 SL202609300001 …
```
**同一页下面的明细表列头却是正常中文**（`#,物料编码,物料名称,来源仓库,出库数量,已出库数量,操作`）⇒ 同一页一半中文一半英文。

### 3.2 根因
`src/framework/metadata.ts` 有两个取值函数，差别是要害：
```ts
// L152-154  读 @KField（全字段都有），返回一定是 string
export function getFieldLabel(Class: object, field: string): string {
  return getFieldConfig(Class, field)?.label || field
}
// L162-164  只读 @KTable 列配置
export function getTableConfig(Class: object, field: string): ITableColumn | undefined {
  return collectChain(Class, store => store.table).get(field)
}
```
六个详情页头部的 `fieldLabel(key)` 全部写成 `getTableConfig(Model, key)?.label ?? key`。而头部字段恰恰大多**不是列表列**（`status` / `createTime` / `updateTime` / `billCode` 在实体上只有 `@KField`，或被 `hide: true` 排除）⇒ 查不到配置 ⇒ `?? key` 把内部字段名兜底印了出来。
明细表之所以正常，是因为它走 `getTableConfigList`，而后者内部会做 `label: item.label || getFieldLabel(Class, item.key)` 回填。**框架自己的范本就在 `src/component/framework/KDetail.vue:48`：`return getFieldLabel(EntityClass.value, key)`。**

### 3.3 爆炸半径与修复
六处**逐字同型**（grep `getTableConfig\([A-Za-z]+, ?key\)\?\.label|getFieldLabel\(` 确认），全部改为 `getFieldLabel(Model, key)` 并加中文注释：

| 文件 | 原行 | 模型 |
| --- | --- | --- |
| `src/view/console/wms/output/component/detail.vue:93` | `getTableConfig(Output, key)?.label ?? key` | Output |
| `src/view/console/wms/move/component/detail.vue:54` | `getTableConfig(Move, key)?.label ?? key` | Move |
| `src/view/console/wms/input/component/detail.vue:95` | `getTableConfig(Input, key)?.label ?? key` | Input |
| `src/view/console/channel/sale/component/detail.vue:41` | `getTableConfig(Sale, key)?.label ?? key` | Sale |
| `src/view/console/channel/purchase/component/detail.vue:48` | `getTableConfig(Purchase, key)?.label ?? key` | Purchase |
| `src/view/console/channel/supplier/component/detail.vue:41` | `getTableConfig(Supplier, key)?.label ?? key` | Supplier |

每个文件里 `getTableConfig` 还有**第二处必须保留的合法用法**（`getCellText(entity, { ...(getTableConfig(X, key) ?? {}), key } as never)`，分别在 output L104 / move L65 / input L106 / supplier L49 / sale L56 / purchase L59）⇒ import 只新增 `getFieldLabel`，不删 `getTableConfig`。
其它 detail 组件**本来就是对的**（`output/component/detailEditor.vue:97`、`output/component/addFinishEditor.vue:188`、`move/component/detailEditor.vue:85`、`sale/component/detailEditor.vue:129,159`、`input/component/detailEditor.vue:142`、`input/component/addFinishEditor.vue:157`、`purchase/component/addFinishEditor.vue:115`、`purchase/component/detailEditor.vue:160,190` 全用 `getFieldLabel`）；`asset/contract/component/detail.vue` 用 `getTableConfigList`，不受影响。

### 3.4 真机复验（1440×900，0 console error / 0 pageerror）
```
出库单详情 labels = ["出库单号","出库类型","出库状态","创建时间","更新时间","销售单号"]
           cells  = ["OUT202609300005","销售出库","已完成","2026-09-30 18:58:11","2026-09-30 19:38:28","SL202609300001"]
销售详情   labels = ["销售单号","销售状态","客户名称","销售说明","总金额","创建时间","驳回原因"]
           cells  = ["SL202609300001","已完成","腾讯科技","阶段N闭环测试-销售出库","59,998.00","2026-09-30 18:57:32","-"]
```
（`.el-descriptions__label` / `.el-descriptions__content` 两组选择器逐格量取。）同时第三次确认闭环完成：出库单与销售单状态均为**已完成**。

### 3.5 守卫 + 变异自检
新增 `src/config/__tests__/detailHeaderLabel.spec.ts`（2 条）：扫全仓 `.vue`，用花括号配平切出 `function fieldLabel(key: string): string { … }` 的函数体，断言其**用 `getFieldLabel`** 且**不再用 `getTableConfig(...)?.label`**；自检要求至少扫到 6 个助手、且六个已知页面全在场（防规则退化成永真）。
**变异自检**：把 supplier 那处改回旧写法 ⇒ **1 failed | 1 passed**，报错逐字点名 `view/console/channel/supplier/component/detail.vue → return getTableConfig(Supplier, key)?.label ?? key`；改回后 **2 files / 8 tests 全绿**（含 dialogWiring 的 6 条）。

### 3.6 原则沉淀
**`?? key` 这种兜底本身就是坏味道**：它把「查不到元数据」静默翻译成「把内部字段名给用户看」，与 N-2 的 `1970-01-01` 假时间、N-5 的 404 落地同属一类 —— **假提示比没提示更糟**。六处同时写错说明它是「照着邻居抄」扩散的，没有守卫就还会再抄第七处。

---

## 4. 更正与撤销

- **b107 记载「确认出库提交后 `toasts=[]`、零提示」是探针误差，已更正。** round 12 复测抓到 `toastNow = ["请选择库存来源"]` ⇒ 警告 toast 确实会弹。原因：`page.waitForResponse` 15s 超时 + 3s 之后才去读 `.el-message`，而 EP toast 只活 ~3s。**结论不变**（选中值确实没写回、确实零请求），但「静默失败」这个描述要收回：它给了用户正确的警告，只是没有给出**为什么选不上**的线索。
- **N-7（round 8 撤销）与 N-8（round 8 撤销）的撤销结论继续成立**，但要在报告里区分清楚：N-8 撤销否掉的是「审核后没建下游单」这个误判（下游单确实建了，误判来自只读了升序列表的第 1 页），**不是**否掉「详情头部渲染英文键名」这个现象本身 —— 后者是独立的 N-11，本轮在出库单详情页复现并已修。
- 移库「移库明细」子弹窗**一次点「取消」没关掉、第二次关掉**（`titlesAfterCancel` 仍是 `["移库单","移库明细"]`）。**未定级**：最可能是 EP 离场过渡期间 overlay 仍留在 DOM 且 `style.display` 未置 `none`，被探针的 `slice(-1)[0]` 选中（时序假象）。按 N-7 的教训，**不凭一次观测下结论**，需重探并把等待拉到 2500ms 以上、同时打印 overlay 的 `style.display` 与 `getBoundingClientRect()`。列表未产生任何数据（仍 20 行），0 error。

---

## 5. 探针铁律（本轮新增 3 条，累计 13 条）

| # | 规则 | 代价 |
| --- | --- | --- |
| R11 | **只等弹窗标题出现是不够的**，必须轮询「目标按钮/目标行真的出现」。详情弹窗的 `bill.details`、picker 的 `loadList()` 都是异步的，标题先到、表格后到 | 两轮探针空转（`finish=no-button`、`pick=no-row`） |
| R12 | **toast 必须在点击后 ~400ms 内立刻读** `.el-message`；EP toast 只活 ~3s | 误记「零提示」，需回来更正 |
| R13 | `browser_run_code_unsafe` 的 `code` **必须是一个 `async (page) => { … }` 箭头函数**；裸语句会 `SyntaxError: Unexpected token 'const'` | 一次调用直接失败 |

（R1–R10 见 N2 §5。另外沿用：每轮开头先确认 3000/8080 都在监听，`about:blank` + `net::ERR_CONNECTION_REFUSED` 是宿主重启特征而非代码问题，重启后 token 仍有效。）

本轮还发生过一次**前后端双双宕机**（`listen3000=0 listen8080=0`，旧日志尾部无异常无堆栈，最后一行是我自己的查询 ⇒ 判定为外部 kill，非应用崩溃）。19:06:48 重启、19:07:33 双端口就绪，`Started Application in 20.731 seconds`。**会话与数据全部跨双重启存活**（`hasToken:true`、27 个菜单项、20 行列表、首行不变）。启动窗口内前端日志出现**预期内的瞬时代理错误**（`[vite] http proxy error: /user/getMyTenantList`、`/error-report`，均 `ECONNREFUSED 127.0.0.1:8080`），后端就绪后消失。

---

## 6. 提交与环境

本轮新增提交：
| 提交 | 内容 |
| --- | --- |
| `3de9287b` | N-12 出库侧：`addFinishEditor.vue` 改 `@confirm`/`@cancel` |
| `58cbf4ba` | N-12 移库侧 + N-13 头注释更正 + `dialogWiring.spec.ts`（3 files, +402/−13） |
| `a0b8cca6` | N-11 六处详情页头部 + `detailHeaderLabel.spec.ts`（7 files, +141/−12） |

N 轮累计：`05f0fc7d`（N-1 + N-2）、`8e294bb9`（N 报告）、`c6b5d2f5`（N-3）、`685762cf`（N-10）、`19907173`（N2 报告）、`3de9287b`、`58cbf4ba`、`a0b8cca6`。
分支 `dev` 领先 `main` **150** 个提交，`main` 仍冻结在 `a243b3e9`；除 `src/types/components.d.ts` 的 EOL-only 漂移（惯例 `git checkout --` 复原）外工作区干净。

环境：Spring Boot 后端 8080（含 N-1/N-10 修复，ready marker `Hi Guy, Kubo is running at [8080] !`）、Vite v7.3.6 dev server 3000、Chromium viewport 1440×900、MySQL 8.0.37（`kubo` 库）。

业务数据现状（**有意的闭环测试数据，非污染**）：
```
purchase        id=10  PC202609300004   status=5 已完成   totalRealPrice=56000
purchase_detail id=10                    quantity=2 finishQuantity=2 isFinished=true
input           id=170 IN202609300007   type=3 采购入库   status=4 已完成  purchase_id=10
sale            id=5   SL202609300001   status=4 已完成   totalPrice=59998
sale_detail     id=5                     quantity=2 finishQuantity=2 isFinished=true
output          id=114 OUT202609300005  type=3 销售出库   status=4 已完成  sale_id=5
output_detail   id=114                   quantity=2 finishQuantity=2 isFinished=true
inventory       MacBook Pro M4 Max | 东部1仓 | 31（31 → 33 → 31，回到起点）
                MacBook Pro M4 Max | 东部2仓 | 15（未动）
operation_log   id=13  DISABLE personnel.user 2 UserEntity#2  create_time=1790753837843
```
⇒ 净效果：一条完整业务线走通，库存回到原值，没有留下需要清理的脏数据（除既有的遗留探针数据 `purchase id=9`）。

---

## 7. 遗留待办（下一轮优先级）

1. **后端全量回归** `mvn -B -o clean test`（round-L 基线 `Tests run: 927 / Failures 0 / Errors 0 / Skipped 1`，约 159 s）。**必须先停后端。** `buildSpringSort`（N-10）与本轮无后端改动，但 `BaseService` 是全局路径，这条是安全网。
2. **前端全链路** `pnpm check`（22 步，预期 Test Files 70+ / Tests 791+；本轮新增 8 条守卫），再 `pnpm build` + bundle-size 闸门复测（round-L 基线：首屏 275.6/450 KB、全部产物 697.3/900 KB over 204 files、PWA precache 209 entries、构建 35.72 s）。**`pnpm check` 会杀掉 dev server，与真机验证互斥，放最后跑。**
3. **N-9（低危）**：`PurchaseService.java:220 log.info("采购单审核完成，已创建采购入库单：{}", inputBill.getBillCode())` 打出 `null` —— `InputService.add()` 不把生成的 `billCode` 回写到调用方实例（DB 里 `bill_code=IN202609300007` 是对的，`input` 表 `null_code=0`）。误导运维日志，非功能缺陷。
4. **采购详情弹窗写操作后列表不回刷**（低危）：`detail.vue:131-134 onFinishConfirmed()` 只调 `detail.reload()`，从不触达外层 `bill`，`list.vue` 也不监听详情弹窗的写入。对照组证明能力存在（入库列表审核后原地刷新、销售列表审核后原地刷新）。
5. **N-5（中危）**：零权限用户落 `/console/index` 看到 404 页。路由行为是刻意且有测试守护的（`src/router/appRouter.ts:82,114-116`），修点在**登录后的落地地址选择 + 文案**，不在路由。需先确认是否已存在「无权限」页组件。
6. **移库「移库明细」取消按钮的重探**（见 §4 第三条，先排除探针时序再定级）。
7. **截图落 `docs/evidence/`**（目录已存在）作为人读证据。
8. 报告类遗留（仅在用户要求时修）：L 报告 §4 的 P1-1（`operation_log` 历史行未回溯脱敏，需用户裁定 append-only 审计表能否原地改写）、P1-2（`ParticipantEntity` 的 phone/identification/email 未脱敏）、P1-3（`data_resource.id` unsigned 与 `data_policy.resource_id` signed 不匹配，启动日志唯一的 DDL 失败，建议 V16 复用 `V12__retired_module_catalog_convergence.sql:174-186` 的 `kubo_align_resource_id(t)`）、P2-13；以及 N-4（欠宽留白，真修法是主从双栏 = M 报告 **P2-16**）。

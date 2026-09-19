# WMS 四页 · 后端能力对照（2026-09-19）

> 用途：把「后端有什么读出口 / 前端用没用起来 / 差在哪」一次写清，作为**推进 1（WMS 域页面补全）**
> 的施工与验收依据。所有结论都是对 `SPMS-Server/src/main/java/cn/hamm/spms/module/wms/**`
> 与 `src/view/console/wms/**` 的源码核对结果，数字可复跑（`pnpm api:check` 会拦住端点级不一致）。

## 一、四个资源 × 后端端点 × 前端消费

| 资源 | 后端端点（本类显式） | 继承的框架端点 | 前端消费 |
| --- | --- | --- | --- |
| `wms.input` 入库单 | `getPage` / `getDetail` / `getCount` / `getStat` / `export` | `@Extends({Export, QueryExport})`，**排除 `Delete`**；单据动作 `audit` / `reject` / `addFinish`（`BaseBillController`） | `getPage`（列表）、`getDetail`（「查看」→ `useDetail` 按 id 重查）、`getCount`（**2026-09-19 新增**统计条）、`export`（异步导出任务）、`audit` / `reject`（列表动作，二次确认 + 驳回原因）、`addFinish`（详情页行内「作业」，仅「入库中」可点） |
| `wms.output` 出库单 | 同上 | 同上 | 同上（作业状态是「出库中」；出库明细带来源库存 `inventory.id`） |
| `wms.move` 移库单 | 同上 | 同上 | 同上（作业状态是「移动中」） |
| `wms.inventory` 库存 | `getPage` / `getDetail` / `getCount` / `getStat` / `export` | `@Extends({Curd.Export, Curd.QueryExport})` | `getPage`（双维度：按仓库 / 按生产单元）、`getList`（**库存选择器**读库存行，库存不是树实体，这里是平铺列表）、`getCount`（**2026-09-19 新增**统计条两项：库存总记录 / 当前筛选）、`export` |

## 二、三个「差」以及为什么这样处理

### 1. `getStat` 前端不消费（有意的，不是漏了）

后端 `ReadStat` = `{ total, publishedTotal }`，**只回行数**，且注释写明「不做任何敏感字段
（价格/金额等）的聚合，避免把聚合结果暴露成绕过列级脱敏的旁路」；单据类资源的
`publishedTotal` 按定义通常是 0。把它塞进 WMS 页面只会变成"为了用而用"的数字。

它继续作为**读侧验收口径**存在（`八资源读侧导出验收矩阵.md` 的 COUNT/stat 列、
`CountStatWiringTest` 的运行时引脚），前端消费的是同一族里的 `getCount`（业务上有意义：
按状态计数）。

### 2. `setBillDetailsAllFinished` 已删除（幻影端点）

前端单据基类曾有一个"一键完成"方法，指向 `POST /{baseUrl}/setBillDetailsAllFinished` ——
**后端没有这个端点**：`AbstractBaseBillService#setBillDetailsAllFinished(long)` 是**内部回调**
（`addFinish` 里判定"所有明细已完成"后由后端自己调用），直接从控制器暴露出去会允许
「明细没完成、单据先完成」的状态。页面也从未有调用点，故连同注释一起删除。

> 想要真正的「一键完成」= 后端按剩余数量批量 `addFinish`（属能力建设，归推进 2），
> 不是前端能自己凑出来的。

### 3. 树导航走 `getList`（同批修的 404）

仓库 / 生产单元 / 部门 / 菜单 / 权限这些 `ITree` 实体，后端的树**不是独立端点**，而是
`getList` 的后置行为：`beforeGetList` 把空 `parentId` 兜成根、`afterGetList` 递归回填 `children`；
部门 / 菜单 / 权限再套一层 `TreeUtil.buildTreeList`。前端此前调 `getTreeList`（airpower 时代的
端点名，后端没有映射）→ 真机 404、树永远空，演示 mock 恰好实现了它所以只在预览里"看起来能用"。
已统一改为 `getList`，并新增门禁 `pnpm api:check` 防止同类回归。

## 三、本轮新增的页面能力：状态统计条（`getCount`）

| 页面 | 统计项 | 交互 |
| --- | --- | --- |
| 入库单 | 审核中 / 已驳回 / 入库中 / 已完成 | 点某项 → 该状态进表格筛选；再点一次取消（高亮当前生效项） |
| 出库单 | 审核中 / 已驳回 / 出库中 / 已完成 | 同上 |
| 移库单 | 审核中 / 已驳回 / 移动中 / 已完成 | 同上 |
| 库存 | 库存总记录（忽略筛选）/ 当前筛选 | 只读（库存的筛选来自左侧树与维度开关） |

实现：`src/hook/kubo/useResourceCount.ts`（数据流，与表格共享 `filter` 与 `queryKey` 前缀）+ 
`src/component/framework/KStat.vue`（渲染）。约定三条：

1. **有 `key` 的项才可点**（没有 `key` 的总数项渲染成静态文本，避免"点了没反应"）；
2. **当前生效的筛选项高亮**，统计条与表格永远是同一份 filter；
3. **单项失败显示 `-`**（例如该角色没有 `{Entity}_getCount` 权限码，后端 403）——
   统计条是辅助信息，不能因为它把整页拖成错误态。

权限码：`Input_getCount` / `Output_getCount` / `Move_getCount` / `Inventory_getCount`
（前后端一致性由 `pnpm perms:check` 守；页面用的码在 `src/config/permissions.ts` 的 `Wms` 段）。

## 四、还没做的（已核对，别当成"已完成"）

| 项 | 现状 | 归属 |
| --- | --- | --- |
| 库存页搜索 | `InventoryEntity`（前端）**没有 `@KSearch`** —— 库存页只有左侧树 + 维度筛选，不能按物料名/编码搜；要加需先确认后端 mapper filter 支持哪些字段（`material.name`？`materialId`？），不能只加装饰器 | 推进 1 后续 / 推进 2 |
| 库存页「查看」详情 | 后端 `getDetail` 有，前端没接（表格列已展示主要字段，缺的是单行详情弹窗） | 推进 1 后续 |
| 「一键完成剩余数量」 | 需要后端批量 `addFinish`（按明细剩余数量逐条作业）+ 权限码 + 页面按钮 | 推进 2 |
| 批次/效期、序列号、盘点、波次拣选、货主与计费 | 库里**一张表都没有**（`infra/init-sql/init.sql` 48 表核对），五项全需新建 | 推进 2 主线 |

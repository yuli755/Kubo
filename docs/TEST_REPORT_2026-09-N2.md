# SPMS 真机测试报告 · 2026-09 · N 轮增补（round 5–8）

> 承接 `docs/TEST_REPORT_2026-09-N.md`（commit `8e294bb9`）。该文件记录的是 N 轮前半段（26 个列表页走查 + N-1/N-2/N-3/N-4）。
> 本增补记录 round 5–8：**采购→入库→库存闭环在真机上跑通**，期间撤销两个误判缺陷（N-7、N-8）、定位并修复一个新缺陷（N-10）、新增两个待修项（N-9、N-5），以及 N-6 的定级结论。
>
> 测试方式：Playwright MCP 驱动真实 Chromium（viewport 1440×900）+ 真实 MySQL + 真实 Spring Boot 后端，所有判定来自 DOM 量测、网络响应与数据库查询，不采信任何 mock。
> 测试身份：凌小云（`admin@hamm.cn`，id 1，permissions 309，33 条路由）；对照组：张三（`admin@hamm.com`，id 2，`permissions=[]`，6 条路由）。
> 分支：全程 `dev`，`main` 未动（仍为 `a243b3e9`）。

---

## 0. 结论速览

| 编号 | 结论 | 状态 | 提交 |
|---|---|---|---|
| N-1 | `operation_log.create_time = 0`，前端渲染出 `1970-01-01 08:00:00` | **已修已验** | `05f0fc7d` |
| N-2 | `system/log/list.vue` 横向溢出 35 px | **已修已验** | `05f0fc7d` |
| N-3 | 禁用用户后该用户从列表消失且无 UI 找回路径 | **已修已验** | `c6b5d2f5` |
| N-4 | 列少时唯一弹性列吞掉全部富余（`system/unit` 名称列 606 px 装 12 px 内容） | 判定不修，见 M 报告 P2-16 | — |
| N-5 | 零权限用户落 `/console/index` 看到 404「地址不正确」，真实原因是未授权 | **未修**（改登录落地逻辑，影响面大） | — |
| N-6 | `menu/getList` 对零权限用户返回全部 34 个菜单节点 | **判定非缺陷**（设计取舍，代码已注释声明） | — |
| N-7 | ~~采购详情页「作业」弹窗是空壳，点「添加完成」静默失败~~ | **撤销：探针假象，产品无缺陷** | — |
| N-8 | ~~`采购入库(3)` 单据 UI 层无法创建，库存永不增加~~ | **撤销：后端在审核时自动建单，UI 层本就无此入口** | — |
| N-9 | `PurchaseService.java:220` 日志打出 `已创建采购入库单：null`，库里单号其实是 `IN202609300007` | **未修**（低危，日志误导排障） | — |
| N-10 | 所有列表默认 `id ASC`，新建/刚审核的单据沉到最后一页，用户找不到自己的单 | **已修已验** | `685762cf` |
| — | **采购→入库→库存闭环** | **真机跑通** | — |

---

## 1. 闭环证据链（采购 → 入库 → 库存）

测试单据：`PC202609300004`，事由 `阶段N闭环测试-采购入库销售出库`，物料 `MacBook Pro M4 Max`（`MA20260001`，单位 `台`），供应商 `Apple 中国`（`SUP20260001`），数量 2，协议单价 28,000，总金额 56,000.00。全部通过 UI 操作产生（工具栏「新增」→ 900×720「采购单」→「添加明细」→ 620×405「采购明细」→「选择」→ 740×874「选择采购物料」→ 勾选 + 确定 →「选择供应商」→ 740×818「选择供应商」）。

### 1.1 制单
`POST /api/purchase/add` → **200**，toast「新增成功」，列表 9 → 10 行。落库 `purchase id=10`。

### 1.2 审核（后端同时自动创建采购入库单）
`POST /api/purchase/audit` → **200**
```json
{"code":200,"message":"审核成功","data":null,"traceId":"ed0dc969-3e36-456f-8fb8-469baa4ac5d1"}
```
状态 `审核中 → 采购中`。同一 traceId 在后端日志留下一行（`%TEMP%\spms-backend.log:6088`）：
```
2026-09-30 16:36:01 [TRACE_ID=ed0dc969-3e36-456f-8fb8-469baa4ac5d1] [http-nio-8080-exec-2]
INFO  c.h.s.m.c.purchase.PurchaseService - 采购单审核完成，已创建采购入库单：null
```
MySQL 侧对应产物（表名是 `input` / `input_detail`，不是 `input_bill`）：
```sql
SELECT id, bill_code, type, status, purchase_id, create_time
  FROM input ORDER BY id DESC LIMIT 1;
-- 170 | IN202609300007 | 3 | 1 | 10 | 1790757361084
```
`type=3` 即 `采购入库`，`purchase_id=10` 正是本次采购单，且 `input_detail` 也建出了 1 条明细。

### 1.3 作业：登记实际到货数量
入口在**采购详情页**（不是列表行）：`src/view/console/channel/purchase/component/detail.vue:225-236` 给 `KDetailTable` 传了 `row-actions=[{ label:'作业', permission: Permissions.Channel.Purchase.addFinish, disabled: () => !canFinish(), handler: openFinish }]`，`canFinish()`（L88-91）只在 `PurchaseStatusEnum.PURCHASING` 时为真。

点开「作业」→「添加完成数量」弹窗实测全对：
```
物料信息   MacBook Pro M4 Max   (disabled)
待采购数量 2 台                 (disabled)
本次采购数量 2                  (number, 已自动填)
全部完成（2 台）= enabled      添加完成 = enabled
```
点「添加完成」→ `POST /api/purchase/addFinish` **200**
```json
{"code":200,"message":"添加完成数量成功","data":null,"traceId":"9b655030-cc7e-4804-bc00-fd72e9018201"}
```
toast「明细采购数量登记成功」，弹窗自动关闭。后端回写（`POST /api/purchase/getDetail {id:10}`）：
```
billStatus      4(采购中) → 5(已完成)
totalRealPrice  0.00     → 56,000.00
明细 id=10      quantity=2, finishQuantity 0 → 2, isFinished=true
```
载荷形状与后端 `afterDetailFinishAdded` 的累加语义一致（传的是**本次**数量，不是累计值）：`{ id, quantity, billId }`。
`totalRealPrice` 的来源是 `PurchaseService.java:223-232 afterAllBillDetailFinished`，逐条 `DecimalMath.add(totalRealPrice, DecimalMath.multiply(price, finishQuantity))`。

### 1.4 入库单审核
`/console/wms/input/list` 首行即 `1|IN202609300007|采购入库|审核中|2026-09-30 16:36:01|查看 审核 驳回 编辑`（`编辑=dis`，符合状态机门控）。
点行内「审核」→ 确认框「审核入库单 / 是否确认审核选择的入库单？」→ `POST /api/input/audit` **200**
```json
{"code":200,"message":"审核成功","data":null,"traceId":"7aaed2ae-fd48-4d7e-a6d6-dacc276876d5"}
```
toast「审核成功」，**列表行原地刷新为「入库中」**（`status 1 → 3`）。

### 1.5 作业：确认入库 → 库存增加
入库详情页点「作业」→「确认入库」弹窗实测全对：
```
物料信息   MacBook Pro M4 Max   (disabled)
目标仓库   东部1仓(SRG0002)      （由「选择」按钮开选择器指定，共 6 仓）
待入数量   2 台                 (disabled, 由采购明细 quantity 带出)
入库数量   2                    (number, 已自动填)
全部入库（2）= enabled          确认入库 = enabled
```
点「确认入库」→ `POST /api/input/addFinish` **200**
```json
{"code":200,"message":"添加完成数量成功","data":null,"traceId":"e49d87b0-9d34-42c5-b7f5-e387a8f49515"}
```
toast「明细入库成功」，弹窗自动关闭（`titles: ["入库单详情"]`）。回写与库存：
```
input id=170 status  3(入库中) → 4(已完成)
明细          quantity=2, finishQuantity 0 → 2, isFinished=true

POST /api/inventory/getPage {filter:{}, page:{pageNum:1,pageSize:20}} → total=5
  MacBook Pro M4 Max | 东部1仓 | 31 → 33      ← 闭环终点，正好 +2
  MacBook Pro M4 Max | 东部2仓 | 15
  键盘               | 东部1仓 | 100
  键盘               | 东部2仓 | 100
  键盘               | 西部2仓 | 0
```

**⇒ 采购→入库→库存 全链路在真机上跑通，数据一致、状态机门控正确、每一步都有 toast 与 traceId 可追溯。** 全程 0 个 pageerror。

销售→出库闭环未在本轮执行（销售明细**刻意没有**「作业」按钮，`src/view/console/channel/sale/component/detail.vue:22-24` 注明由出库单自己的「作业」（确认出库）驱动），列入遗留待办。

---

## 2. N-10 · 列表默认排序让新单据沉底（已修 `685762cf`）

### 2.1 现象
双模块硬证据：
- 新建的采购单 `PC202609300004` 落在采购列表 **第 10/10 行**（不是第一行）；
- 审核采购单后自动生成的 `IN202609300007`（`type=3` 共 17 条）**不在第 1 页**，第 1 页全是 09-28 的种子数据。

工作流列表把用户刚操作的单据埋到最后一页，是可用性缺陷：用户建完单要翻页去找它，很容易误判「没建成功」而重复提交。

### 2.2 根因（六跳链路，逐跳都有源码定位）
1. `src/hook/kubo/useTable.ts:97-100` — `sort = ref(option.defaultSortField ? {...} : null)`；页面没传 `defaultSortField` 时 sort 就是 **null**。
2. `src/hook/kubo/useTable.ts:109-116` — `buildSort()` 在 `!sort.value` 时 **return undefined**。
3. `src/hook/kubo/useTable.ts:125-127 / 138-140` — `if (nextSort) payload.sort = nextSort`，**请求体里压根没有 sort 字段**。
4. `SPMS-Server/.../base/BaseService.java:382` — `Sort sort = queryPageRequest.getSort() != null ? queryPageRequest.getSort() : new Sort();`，把「没传」物化成 `new Sort()`。
5. `SPMS-Server/.../framework/query/Sort.java:19-20` — `private String field;`（null）/ `private String direction = ASC;`。
6. `SPMS-Server/.../base/BaseService.java:522-534 buildSpringSort` — `field` 有 null 兜底（落到 `"id"`），**`direction` 没有兜底**（`boolean isAsc = sort != null && sort.isAsc()` 直接继承 ASC）；又因为 `field.equals("id")`，L528 的两个 tiebreaker（`createTime desc`、`id desc`）全被跳过 ⇒ 最终是纯 **`id ASC`**。

**一句话：field 有兜底、direction 没兜底，「没表达排序意见」被翻译成了「按 id 升序」。**

`SPMS-Server/src/test` 下 grep `buildSpringSort|buildPageable` **无任何命中** —— 排序构建从来没有测试覆盖，这是它能长期潜伏的原因。

### 2.3 修法与取舍
改 `buildSpringSort`，让 direction 的兜底与 field 对称：
```java
boolean hasField = sort != null && StringUtils.hasText(sort.getField());
String field = hasField ? sort.getField() : "id";
boolean isAsc = hasField && sort.isAsc();   // 只有显式给了字段才尊重 ASC
```
**为什么不改 `Sort.java:20` 的 `direction = ASC` 默认值**：`MenuController.java:42-45` 显式 `new Sort().setField(MenuService.ORDER_FIELD_NAME)` **依赖** ASC 默认值来排菜单树，改默认值会掀翻侧栏菜单顺序。改在 `buildSpringSort` 是安全的——它是所有查询路径（`getList`→`query`、`getPage`→`buildPageable`、树查询）的唯一收敛点，且只影响「没给字段」这一种情况，所有显式排序（含菜单树）行为不变。L528 之后的 tiebreaker 逻辑未动。

### 2.4 验证（重启后端后三项全部翻转，415 个源文件编译无错，0 console error）
```
POST /api/purchase/getPage {filter:{}, page:{pageNum:1,pageSize:3}} → 200 total:10
  PC202609300004 | id=10 | st=5 | ct=1790757080556     ← 修前在 10/10 行
  PC202609300003 | id=9  | st=3 | ct=1790705190269
  PC202609300002 | id=8  | st=5 | ct=1790705072414

POST /api/input/getPage {filter:{type:3}, page:{pageNum:1,pageSize:3}} → 200 total:17
  IN202609300007 | id=170 | st=1 | ct=1790757361084    ← 修前不在第 1 页
  IN202609300006 | id=169 | st=1 | ct=1790705190419
  IN202609300004 | id=167 | st=4 | ct=1790705072566

采购列表 UI（page.reload 后首行）
  1|PC202609300004|阶段N闭环测试-采购入库销售出库|56,000.00|已完成|2026-09-30 16:31:20|…
```
提交信息用 PowerShell 单引号字符串（内部无 `$`、无 `\"`、无 `'`），遵守本仓库既有提交铁律。

---

## 3. N-7 撤销 · 空壳弹窗是探针假象，产品无缺陷

### 3.1 误判过程（同一件事翻了四次，全部记录在案）
- **round 3**：判「采购→入库 UI 断链，三个入口全缺」——只量了列表行按钮。
- **round 4**：撤销。入口在**采购详情页**，`detail.vue:27` 明写「「作业」按钮**只在「采购中」可点**」，L220 还有提示文案「采购进行中 · 可点击操作列「作业」登记实际到货量」。
- **round 5**：改判「点开是空壳弹窗」。实测「添加完成数量」弹窗：物料信息 `-`、待采购数量 `0`、单位后缀空、`全部完成（0 ）` disabled、`添加完成` disabled；强填 `2` 后「添加完成」变 enabled，但点击后 **零请求、零 toast、弹窗直接关、已采购数量仍 0**。三条独立证据：`waitForResponse(/purchase\/(addFinish|finish)/, 8000)` → `no matching response`；`.el-message` 数组为空 `[]`；详情行仍是 `…|2|0|作业`。0 pageerror、0 console error。
  同时后端是干净的：`purchase/getDetail {id:10}` → `id:10, billId:10, quantity:2, finishQuantity:0, material.name="MacBook Pro M4 Max", material.unit.name="台"`。
  决定性探针：`finishing.value` 与 `props.detail` 都是 **空普通对象**（`ctor:"Object"`, `keys:[]`, `JSON.stringify = {}`），而同一组件 `setupState.bill.details[0]` 是完整的 `PurchaseDetailEntity`（18 个 key，`id:10, quantity:2, finishQuantity:0, price:28000`），`sameIdentity:false`。EP store 的 `store.states.data.value[0]` 与 `bill.details[0]` 是**同一对象**（`sameAsStore:true`），无拷贝。⇒ 断点被锁死在「`KDetailTable.vue:177 @click.stop="action.handler(row as E)"` 交出去的 `row` 是 `{}`」。
- **round 6（终局）**：真凶找到了——**是我的探针自己**。

### 3.2 真凶：Element Plus `.hidden-columns` 里的插槽探测副本
把页面上**所有**「作业」按钮按祖先归类：

| # | 在 `.hidden-columns` | 在 body td | `visibility` | 尺寸 | `disabled` |
|---|---|---|---|---|---|
| 0 | **是** | 否 | **`hidden`** | 34×20 | **false** |
| 1 | 否 | **是** | `visible` | 34×20 | false |

`.hidden-columns` 的计算样式 = `{visibility:"hidden", position:"absolute", z-index:"-1"}`；全页共 2 个（采购列表表 + 明细表各一）。

机制：Element Plus 的 `ElTableColumn.render()` 会**主动调用一次自己的 default 插槽并传占位参数**（用于探测是否有嵌套子列以支持多级表头），产物挂进 `ElTable` 模板里的 `<div class="hidden-columns"><slot /></div>`。这份副本里的 `ElButton` 闭包捕获的 `row` 就是那个 `{}`。它**真实存在于 DOM、`disabled === false`、尺寸 34×20**，而 `.hidden-columns` 在 `ElTable` 模板中排在 body **之前** ⇒ `document.querySelectorAll('button')` 按文档序**先命中它**；`page.evaluate` 里的裸 `.click()` **不做可见性检查** ⇒ 点中了一个真实用户永远点不到的副本。

点**真正那颗**（`all.find(b => !b.closest('.hidden-columns'))`）后，弹窗立刻全对：
```
inputs:  text|MacBook Pro M4 Max|dis=true   text|2|dis=true   number|2|dis=false
buttons: X=en   全部完成（2 台）=en   取消=en   添加完成=en
```

这一个根因同时解释了全部七个矛盾现象：`finishing={}`、`props.detail={}`、`sameIdentity:false`、`addFinishEditor.vue:60 if (!detail?.id) return` 静默返回、`waitingQuantity` 恒 0、零请求零 toast、以及同一颗按钮出现两条不同组件链（`TableColumnRenderer < ElTableColumn`（副本）vs `TableTdWrapper < ElTableBody`（真实））。

### 3.3 附带结论
- `addFinishEditor.vue:60` 的静默 `return` 在真实路径下永不触发，是合理的防御式代码；`src/component/framework/KDetailTable.vue` 的数据列与操作列插槽写法**完全同构**（都是 `#default="{ row }"`），模板没写错（L124-131 `<ElTable :data="rows">`；L139-160 数据列；L162-182 操作列 `fixed="right"`；L176-177 `:disabled="action.disabled?.(row as E)"` / `@click.stop="action.handler(row as E)"`）。
- 纠正一处误记：「一键全部到货恒 disabled」不成立——实测它是 `enabled`；恒 disabled 的是弹窗内的 `全部完成（0 ）`，两者被混淆了。
- 既有 vitest 用例 `src/config/__tests__/wmsDomain.spec.ts:322`「采购作业与移库明细支持一键满额速填」在 mock 下通过、而我在真机上看到空壳弹窗时曾以为「绿灯掩盖了活缺陷」——现在证明**测试是对的，是我的探针错了**。

---

## 4. N-8 撤销 · `采购入库` 单由后端在审核时自动创建

### 4.1 误判依据（都是真的，但推论错了）
- `src/view/console/wms/input/component/editor.vue:180` `:preset="{ type: InputTypeEnum.NORMAL.key }"` 硬编码普通入库；
- `src/model/wms/input/InputEntity.ts:71-73` `@KField({ label: '采购单' }) purchase!: PurchaseEntity` **没有 `@KForm`**（L75 的「移库单」同样没有）；L37 注释「不给 `@KForm`：入库类型由来源单据（采购/移库）或页面预设决定，编辑器里不让用户改」；
- `InputController.java` 只 `@PostMapping` 了 `getPage`(L39)/`getDetail`(L52)/`getCount`(L70)/`getStat`(L83)/`export`(L97)，**没有「从采购单生成入库单」的端点**；
- 采购单列表行按钮只有 查看/审核/驳回/再来一单/编辑，详情页只有 作业/关闭，**没有「生成入库单」**；
- `POST /api/input/getPage {filter:{type:3}}` 第 1 页全是 `IN20260928000x` 种子数据。

⇒ 我当时推论「UI 层无法创建采购入库单，后端 `InputEntity.purchase` 与 `InputService` 的 `case PURCHASE` 分支不可达，库存永不增加」。**前半段观察正确，后半段推论错误。**

### 4.2 真相
`SPMS-Server/.../module/channel/purchase/PurchaseService.java:204-221`：
```java
@Override
protected void afterBillAudited(long billId) {
    PurchaseEntity purchaseBill = get(billId);
    // 采购单审核完毕后创建入库单
    InputEntity inputBill = new InputEntity()
            .setStatus(InputStatus.AUDITING.getKey())
            .setPurchase(purchaseBill)
            .setType(InputType.PURCHASE.getKey());
    List<PurchaseDetailEntity> details = detailService.getAllByBillId(purchaseBill.getId());
    List<InputDetailEntity> inputDetails = details.stream()
            .map(detail -> new InputDetailEntity()
                    .setMaterial(detail.getMaterial())
                    .setQuantity(detail.getQuantity()))
            .collect(Collectors.toList());
    inputBill.setDetails(inputDetails);
    WmsServices.getInputService().add(inputBill);
    log.info("采购单审核完成，已创建采购入库单：{}", inputBill.getBillCode());
}
```
**采购单审核通过的瞬间，入库单（type=3、status=审核中）和它的明细就已经建好了**，只是明细不带仓库（仓库在「确认入库」时按明细指定，`InputEntity.ts:48` 注释已明说）。前端那些「堵死的入口」全是**正确设计**：人工只能建普通入库单，采购入库单必须由采购单派生，所以 `purchase` 字段和 `type` 字段都不进表单。

铁证：日志 traceId `ed0dc969-…` 与我审核采购单的响应 traceId **完全一致**；MySQL `input` 表 `id=170 | IN202609300007 | type=3 | purchase_id=10`，`input_detail` 同批建出 1 条；`SELECT COUNT(*), SUM(bill_code IS NULL) FROM input` → `170 / 0`（无空单号）。后续 §1.4–1.5 走完审核与确认入库、库存 31→33，闭环成立。

### 4.3 我为什么会误判（可复用的方法论教训）
用 `getPage` 的**第 1 页前几条**当作「最新的记录」。列表当时是 `id ASC`（正是 N-10），新单在最后一页，第 1 页永远看不到 —— 同一个错误我在采购列表上已经犯过一次（`PC202609300004` 在 10/10 行）。
**判「有没有新增记录」不能看第 1 页前几条**：要么 `ORDER BY id DESC` 直查数据库，要么显式传降序 sort，要么修好默认排序后看首行。

---

## 5. 探针铁律（本轮新增/强化，后续所有真机测试必须遵守）

1. **EP 表格里任何元素查找都要排除 `.hidden-columns`**（判据 `!el.closest('.hidden-columns')`，或加 `getComputedStyle(el).visibility === 'visible'`）。EP 的 `ElTable` 对每个带 default 插槽的列都在里面留一份不可见副本，`querySelectorAll` 按文档序会**先**命中它。这是 N-7 误判的唯一原因。
2. **裸 `.click()` 绕过 Playwright 可见性检查** ⇒「点得动」≠「用户点得到」。与「`locator.click` 对 disabled 按钮会等满 timeout（实测 6000ms 与 30000ms 都超时，不是『点了没反应』）」是同一类陷阱的两面。
3. **判缺陷前必须分别核对三层**：数据源（EP store / `props.rows`）→ 组件接线（`props.detail`）→ 触发路径（点的到底是哪颗 DOM）。N-7 前两层全对，只有第三层选错。
4. **等嵌套弹窗关闭不能用固定延时**，要轮询 overlay 数量或标题集合的变化（本轮 `确认入库` 卡在 1200ms 固定延时，改成轮询后一次通过）。
5. `page.evaluate` 里才能用相对路径 `/api/...`（Node 侧裸 `fetch` 抛 `TypeError: Failed to parse URL from /api/purchase/getPage`）。分页结果取 `data.list`（不是 `records`）；token 在 `localStorage.Authorization`（裸 token，需自己拼 `Bearer `）。
6. EP `.el-table-column--selection` 选择列**行点击不选中**（实测 `tr.click()` → `checkedRows: 0`），必须点行内 `.el-checkbox`。
7. `document.querySelector('.el-dialog')` 只返回**第一个** dialog；有嵌套 overlay 时要按 `display !== 'none' && getBoundingClientRect().width > 0` 过滤再取最后一个。采购新建流程有 13 个 overlay 元素，其中 5 个是常年 `display:none` 的 `el-popper`。
8. 明细行的单价/数量是行内 `<input>`，`td.innerText` 返回**空字符串**，必须读 `td input.value`。
9. 覆盖 `setupState.openFinish` **抓不到实参**（模板里的 `(row) => openFinish(row)` 是闭包绑定 setup 作用域的 `openFinish`）；但**原地改 `props.rowActions[0].handler` 能抓到**（`v-for` + `@click` 编译出的闭包在点击时才读 `action.handler` 属性）。注意同一个 handler 被包两层会让一次点击产生两条捕获记录。
10. `inventory` 列表行是**扁平字段**（`id/createTime/updateTime/tenantId/creatorId/quantity/type`），物料名与仓库名不在标量字段里 —— 上一轮读 `materialName`/`storageName` 得到 undefined 就是这个原因，要读嵌套对象。

---

## 6. 其余条目

### N-9 · 日志打出 `已创建采购入库单：null`（低危，未修）
`PurchaseService.java:220` 打的是 `inputBill.getBillCode()`，而库里 `bill_code = IN202609300007`、`input` 表 170 行 `SUM(bill_code IS NULL) = 0`。⇒ `InputService.add()` 生成单号后**没把 billCode 回写到调用方传入的那个实例**上。
危害：排障时这行日志会把人引向「单号没生成」的错误方向（我本轮就被它误导过一次，差点判成缺陷）。修法：日志改打 `add()` 的返回值，或 `add` 之后重新 `get(id)` 取 billCode（需先确认 `BaseService.add(E)` 的返回类型）。

### N-5 · 零权限用户落 404（未修）
张三（`permissions=[]`）登录后落 `/console/index`，实测 `$route.fullPath = '/console/index'`、`matched = ['/', '/:catchAll(.*)']`、`getRoutes().length = 6`、`routes.some(r => r.path === '/console/index') = false`，正文显示「404 很抱歉，没有找到你想访问的资源 请确认你的访问地址是否正确，或 返回首页」。
**地址是对的，缺的是授权** ⇒ 又是「假提示比没提示更糟」的实例（与 N-2 的 1970 假时间同类）。
路由行为本身是**刻意且有测试守护的**（`src/router/appRouter.ts:114-116` 对缺失视图 `console.warn` 后跳过注册，落到 catchAll 404；由 `src/router/__tests__/appRouter.spec.ts` 覆盖），真正的缺口在**登录后落地地址的选择与文案**。修它要动登录跳转逻辑，影响面比 N-3 大，需先确认是否已存在「无权限」页组件。

### N-6 · `menu/getList` 对零权限用户开放（判定非缺陷）
`POST /api/menu/getList` 用张三的 token 返回 `200 / 获取成功 / array(7)`，展开是**全部 34 个菜单节点**（7 个顶级分组 + 27 个叶子，含每个 `path`）。
判定：**设计取舍，不修**。依据是 `MenuController.java:38 @Permission(authorize = false)` 加上 L26-34 的 load-bearing javadoc —— 注释明说这棵开放菜单树的真正消费者是 `RoleController.authorizeMenu` 的复选树，若要求 `Menu_getList` 权限会把角色管理员锁在授权页外面；注释甚至预判了「34 节点树」这个数量。侧栏**不走**这个端点，走 `/user/getMyMenuList`，实测对张三返回 0 节点（`.el-menu-item` 与 `.el-sub-menu` 均为 0）。
数据面对照组（同一 token）：`user/getPage` → `403 你无权访问 用户-分页查询 (User_getPage)`；`purchase/getPage` → `403 你无权访问 采购单-getPage (Purchase_getPage)`；`material/getPage` → `403 你无权访问 物料-getPage (Material_getPage)`。⇒ 数据面守得住，只有这条元数据面按设计开放。

### 详情弹窗内写操作后列表不回刷（未修，低危）
采购详情页「作业」提交成功后，仍开着的详情弹窗与背后的列表都停在旧状态（弹窗 `已采购数量` 仍 0、`status` 仍「采购中」、`totalRealPrice` 仍 0.00；列表行仍「采购中」），必须关闭弹窗重开才见真值（重开后 `已完成 / 56,000.00 / …|2|2|作业`、`作业=dis`）。
根因方向：`detail.vue:131-134 onFinishConfirmed()` 调了 `detail.reload()` 但没落到 `bill`；且没有任何地方回刷列表（`list.vue` 未监听详情弹窗的写操作）。
对照组：**入库列表是有写后回刷的**（审核成功后行内状态原地变「入库中」），说明能力存在，采购侧只是没接。
危害定级从「较高」下调为「低危」：陈旧视图里 `作业` 仍 enabled，理论上可能诱导重复提交，但重新打开即恢复真值，且后端有 `isFinished` 门控。

---

## 7. 提交与环境

本轮新增提交：`685762cf` — `fix(server): 列表默认排序改为最新在前，修复单据沉底找不到的问题`（1 file changed, +10 / −2）。
N 轮累计：`05f0fc7d`（N-1 + N-2）、`8e294bb9`（N 报告前半段）、`c6b5d2f5`（N-3）、`685762cf`（N-10）。
分支 `dev` 领先 `main` **146** 个提交，`main` 仍冻结在 `a243b3e9`，工作区干净。
环境：Spring Boot 后端 8080（含 N-1/N-10 修复，415 个源文件编译无错，ready marker `Hi Guy, Kubo is running at [8080] !`）、Vite v7.3.6 dev server 3000、Chromium viewport 1440×900、MySQL 8.0.37（`kubo` 库）。

业务数据现状（**有意的闭环测试数据，非污染**）：
```
purchase        id=10  PC202609300004   status=5 已完成   totalRealPrice=56000
purchase_detail id=10                    quantity=2 finishQuantity=2 isFinished=true
input           id=170 IN202609300007   type=3 采购入库   status=4 已完成  purchase_id=10
input_detail    bill_id=170             quantity=2 finishQuantity=2 isFinished=true
inventory       MacBook Pro M4 Max | 东部1仓 | 33（原 31）
operation_log   id=13  DISABLE personnel.user 2 UserEntity#2  create_time=1790753837843
```

---

## 8. 遗留待办（下一轮优先级）

1. **销售 → 出库闭环**：销售单制单/审核 → 出库单审核 → 出库「作业」（确认出库，需先选一条具体库存，`src/view/console/wms/output/component/addFinishEditor.vue:16`）→ 验证库存扣减。
2. **N-9**：修 `PurchaseService.java:220` 的 null 单号日志。
3. **采购详情弹窗写后回刷**：`onFinishConfirmed()` 里补列表 `table.search()`。
4. **N-5**：决定零权限用户落地体验（先查是否已有「无权限」页组件）。
5. **后端全量回归** `mvn -B -o clean test`（round-L 基线 `Tests run: 927 / Failures 0 / Errors 0 / Skipped 1`，约 159 s；**必须先停后端**，否则端口/上下文冲突）。
6. **前端全链路** `pnpm check`（22 步，预期 Test Files 70+ / Tests 791+），再 `pnpm build` + bundle-size 闸门复测（round-L 基线：首屏 275.6/450 KB、全部产物 697.3/900 KB over 204 files、PWA precache 209 entries、构建 35.72 s；EP 固定样式新增 +28.2 KB gzip）。**`pnpm check` 会杀掉 dev server，与真机验证互斥，放最后跑。**
7. **截图落 `docs/evidence/`**（目录已存在）作为人读证据 —— 本模型无图像输入，所有判定均来自 DOM 量测，截图仅供人复核。
8. 报告类遗留（仅在用户要求时修）：L 报告 §4 的 P1-1（`operation_log` 历史行未回溯脱敏，需用户裁定 append-only 审计表能否原地改写）、P1-2（`ParticipantEntity` 的 phone/identification/email 未脱敏）、P1-3（`data_resource.id` unsigned 与 `data_policy.resource_id` signed 不匹配，启动日志唯一的 DDL 失败，建议 V16 复用 `V12__retired_module_catalog_convergence.sql:174-186` 的 `kubo_align_resource_id(t)`）、P2-13。

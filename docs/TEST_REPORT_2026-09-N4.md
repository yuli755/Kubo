# SPMS 真机测试报告 · 2026-09 · N4（round 13–15）+ N-15（round 22–23）+ R24-25（round 24–25）+ R26（round 26）+ R27-31（round 27–31）

> 承接 `docs/TEST_REPORT_2026-09-N.md`（round 1–4）、`docs/TEST_REPORT_2026-09-N2.md`（round 5–8）与 `docs/TEST_REPORT_2026-09-N3.md`（round 9–12）。
> 本轮走查**主数据创建链 + 系统治理闭环（角色/菜单授权/权限授权/挂用户/登录取证）+ 采购→入库→库存→销售→出库全业务闭环 + 移库闭环与未覆盖系统页**，并修复四个真实缺陷（N-13 菜单授权计数器、N-14 表单默认值、**N-15 合同生效参与方闸门**、N-2 遗留 lint）。
> 本模型无图像输入：所有判定来自 DOM 量测（`getBoundingClientRect` / `getComputedStyle` / `__vueParentComponent` 遍历）与真实网络响应，截图仅供人复核。

## 0. 判定表
| 编号 | 现象 | 判定 | 处置 | 提交 |
| --- | --- | --- | --- | --- |
| **N-13** | 角色·菜单授权「已选 N 项」计数器钉死、清空按钮不消失（树勾选/保存正确） | 🐞 **真缺陷（P1）** | 已修 `checkedIds()` 去副作用 + 守卫 | 见 §2 |
| **N-14** | 新增合同/物料时 `@KForm({ defaultValue })` 全部落空（合同类型/起止时间空 → 保存被 requiredNumber 拦截） | 🐞 **真缺陷（P1）** | 已修 `resetForm()` 回退元数据默认值 + 守卫 | 见 §3 |
| **N-15** | 后端 `ContractService.enforce` 只查状态不查参与方，0 方合同被直接生效（status 1→2） | 🐞 **真缺陷（P1）** | 已修后端参与方闸门 + 单测 | 见 §7 |
| 治理闭环 | 角色→菜单授权→权限授权→挂用户→登录取证 全链路 | ✅ **打通** | 证据见 §4 | — |
| N-2 | `system/log/list.vue:201` lint `vue/max-attributes-per-line` | 🧹 遗留风格 | 已修（换行排版） | 见 §2 |
| — | 全量 vitest 并行跑偶发 EnvironmentTeardownError | ⚪ 测试基建竞态 | 非改动引入；单独重跑全过 | §5 |
| — | 主数据创建链：物料/供应商/客户/计量单位/仓库/合同 | ✅ **打通** | 证据见 §6 | — |

---

## 1. 探针规则迭代（本轮新增/修正）
- **trusted click 铁律**：对 EP 复选框/按钮，DOM `el.click()` 可能只翻视觉类不触发组件事件链（KSelector 行勾选即如此）——一律用 Playwright locator `click({force:true})` / `getByRole().click()`。
- **select 值读取**：`.el-select__selected-item` 才是已选文本；`input.value` 恒空（R16 的正式版）。
- **isEmail 正则**：`src/utils/validators.ts:16` 不含连字符（`test-r16@hamm.cn` 会被拒）；含 `-` 的邮箱需规避或改字面邮箱。
- **join 表列名**：`user_role_list` 是 `user_entity_id`/`role_list_id`（不是 `role_entity_id`，初探报 1054 是探针错，非产品）。

## 2. N-13 · 菜单授权计数器不同步（P1，已修）
### 2.1 现象
`角色 · 菜单授权` 弹窗（`menuEditor.vue`）里勾选树后：
- 树勾选本身**正常**（清空→0、全选→34，DOM 与保存结果都正确，DB `role_menu_list` 34 行佐证）；
- 但顶部「已选 N 项」tag **钉死**在弹窗打开时的初值不动，`清空` 按钮（`v-if="checkedCount>0"`）也因此**永不隐藏**。

### 2.2 根因
`checkedIds()` 原实现带副作用 `checkedCount.value = ids.length`，而模板把它绑定在
`:default-checked-keys="checkedIds()"` 上——该表达式**每次渲染都执行**，于是任何一次重渲染都会把
`onCheckChange()`（`@check` 事件）刚更新的计数器**永久钉回 `props.role.menuList.length`**。
EP 的 `default-checked-keys` 只在挂载时应用一次，所以树勾选与保存不受影响——缺陷只伤计数器与清空按钮。

### 2.3 修复（`src/view/console/personnel/role/component/menuEditor.vue`）
```ts
/** 返回角色已授权的菜单 id 列表。
 *  注意：本函数被模板 :default-checked-keys="checkedIds()" 绑定，会在每次渲染时重新执行，
 *  因此内部绝对不能修改任何 ref/状态（副作用会把计数器钉回初始值，见 N-13）。
 *  计数器的初始值在 loadTree() 里设置，后续更新只依赖 onCheckChange()/checkAllMenus()/clearAllMenus()。 */
function checkedIds(): number[] {
  return (props.role?.menuList ?? []).map((item) => item.id);
}
```
- 计数器初始值仍在 `loadTree()`（`checkedCount.value = (props.role?.menuList ?? []).length`）；
- 更新只靠 `onCheckChange` / `checkAllMenus` / `clearAllMenus`。
- **live 复验**：重开弹窗 → `已选 34 项` → 清空 → `已选 0 项` + 清空按钮隐藏 → 全选 → `已选 34 项` + 清空按钮重现。

### 2.4 防回归守卫
新建 `src/config/__tests__/defaultCheckedKeysGuard.spec.ts`（3 tests）：正则抠出 `checkedIds()` 函数体，
断言 ① 不含 `checkedCount.value =` 赋值；② 单条 return 且含 `props.role?.menuList` 与 `.map`；③ 模板 `:default-checked-keys="checkedIds()"` 锚点仍在。

### 2.5 同类排查
`permissionEditor.vue` 的 `checkedIds` 是 `computed(() => new Set(...))` —— 无副作用，非同类模式，安全。

### 2.6 N-2 遗留 lint
`system/log/list.vue:201` `<span v-else class="...">` 触犯 `vue/max-attributes-per-line`（多属性须换行），`pnpm check` 第一步 lint:ci 即失败。已改为属性换行排版，`ESLINT_EXIT=0`。

## 3. N-14 · KFormDialog 忽略 `@KForm({ defaultValue })`（P1，已修）
### 3.1 现象
新增合同：弹窗里 `合同类型`/`开始时间`/`终止时间` **全为空**（尽管实体声明了
`type=ContractTypeEnum.OTHER`、`startTime=now`、`endTime=+1年`）；只填名称点保存 → 无 toast 无新行，
`.el-form-item__error` 显示「请输入合同类型 / 请输入开始时间 / 请输入终止时间」。

### 3.2 根因
`KFormDialog.vue` 的 `resetForm()` 原写法 `next[field.key] = instance[field.key] ?? ''` **只读类实例属性**。
本工程实体字段几乎全部是 `field!: number`（TS 非空断言，无运行时初始化器），所以 `new Ctor()[key]` 恒为 `undefined` ⇒
`@KForm({ defaultValue })` 元数据在新增时**全部落空**。全库检索确认 `defaultValue` 除类型声明与装饰器外没有任何消费点——是死元数据。
这是换底座（airpower → KFormDialog）回归家族（N-11/12/13 同类）；旧 airpower 编辑器会应用 defaultValue。
波及 21 处声明：合同 type/start/end、物料类型/使用类型/采购价/销售价、结构、部门、参与方、配置、编号规则、数据权限策略、菜单等。

### 3.3 修复（`src/component/framework/KFormDialog.vue`）
```ts
// 实体字段用 `field!: number` 声明（无运行时初始化器），new Ctor()[key] 恒为 undefined，
// 因此实例值缺省时必须回退到元数据默认值，否则 @KForm({ defaultValue }) 在新增时全部落空（N-14）。
next[field.key] = instance[field.key] ?? field.defaultValue ?? '';
```
优先级：**实例值 > 元数据默认值 > 空串**。

### 3.4 live 复验
- 新增合同：合同类型=`其他合同`、开始=2026-10-01、终止=2027-10-01 预填；仅填名称→保存→「新增成功」→
  row1 `CON2026100001 | 其他合同 | 真机测试合同 R16 | 未生效 | 剩余 365 天`；DB `contract` 表 id=1（注意表名不是 asset_contract），type=0, status=1, start+1yr。
- 新增物料：物料类型=`自产品`、使用类型=`消耗品`、采购单价=`0.00`、销售单价=`0.00` 预填。
- 合同详情：`合同详情 · 真机测试合同 R16`（参与方 0 / 附件 0 两个空子表），0 pageerror / 0 console error。

### 3.5 防回归守卫
新建 `src/config/__tests__/formDefaultValueGuard.spec.ts`（3 tests）：① resetForm 函数体必须含 `field.defaultValue`；
② 必须匹配 `instance[field.key] ?? field.defaultValue ?? ''`；③ 锚点 fields.value + resetForm 存在。

## 4. 系统治理闭环（端到端 PASS）
| # | 步骤 | 结果 |
| --- | --- | --- |
| 1 | 新建角色 `真机测试角色 R16` | `RO0001`（coderule 自动），DB id=20 |
| 2 | 菜单授权：34 节点全选保存 | DB `role_menu_list` role_entity_id=20 恰 34 行（menu_list_id 127,128,129,130,132-143,154-164,166-168,172-175） |
| 3 | 新建用户 `真机测试用户 R16` | user id=73；挂角色 20（`user_role_list` 恰 1 行）；设已知密码 |
| 4 | 以 R16 登录 | token `1790870449094.eyJpZCI6Nz…`（id=73） |
| 5 | 侧栏 34 菜单全部可见 | 与角色 20 授权一致 ⇒「菜单=可见范围」生效 |
| 6 | 任意写操作 | 无「新增」按钮（权限门禁隐藏）+ toast `无权限：你无权访问 单位-不分页查询 (Unit_getList)` ⇒「权限=可执行动作」生效 |
| 7 | 回登 admin | `1790870583779.eyJpZC…`，恢复正常 |

**结论**：菜单授权/权限授权分开授予的语义端到端成立。

## 5. 全量 vitest 并行竞态（非改动引入）
全量 `vitest run` 偶发：`menuClick` / `consoleMount` / `commandPaletteAndDraft` 各 1 fail，
错误为 `EnvironmentTeardownError: Cannot load '…useBusinessOverview.ts' after the environment was torn down` + `[Vue warn] Failed to resolve directive: loading`。
**单独重跑三文件全部通过**（24 / 3 / 1 passed）⇒ 并行跑的环境拆毁竞态，与本轮改动无关。CI 口径为 `pnpm test:coverage`。

## 6. 主数据创建链（全部 PASS，N-10 写路径 row1 全部复验）
| 单据 | 名称 | 编码 | 附加验证 |
| --- | --- | --- | --- |
| 物料 | 真机测试物料 R15 | MA20260004（id=5） | 采购价 12345 / 销售价 23456（API 明文，UI 不展示=布局） |
| 供应商 | 真机测试供应商 R15 | SUP20260002 | 电话 13900001111 → `139****1111`（渲染层脱敏） |
| 客户 | 真机测试客户 R15 | CT20260003 | 电话 13700002222 → `137****2222` |
| 计量单位 | 千克 R16 | UT0002 | coderule 自动 |
| 仓库 | 华中新仓 R16 | SRG0007 | 树表「添加子级」行操作 |
| 合同 | 真机测试合同 R16 | CON2026100001（id=1） | type=其他、start+1yr、到期 tag「剩余 365 天」 |

供应商是**采购明细级**字段（`detailEditor.vue`：物料+供应商 → 自动查 `KPurchasePriceService.getByMaterialAndSupplier`，命中→协议专属价，未命中→toast 提示并回落参考价）——采购单头刻意无供应商字段（既有设计，文档已录）。采购编辑器批量选料为 `<KSelector multiple title="批量挑选采购物料">`。

## 7. 未修复项（仅记录，需时才动）
- N-5 零权限用户落 404（路由设计，已守卫）；N-9 `PurchaseService.java:220` 日志打 null 单号（DB 正确）；采购明细弹窗写操作不刷新外层列表（低）。
- 全量 vitest 并行竞态属测试基建，未根治（CI 单进程口径不受影响）。

## 7. N-15 · 合同生效后端未校验参与方数量（P1，已修）
### 7.1 现象
`ContractService.enforce(id)` 只查合同状态（`INVALID` 才可生效），**不查参与方数量**。API 探针实证：
`POST /contract/enforce {"id":1}`（0 参与方）→ HTTP 200 `{"code":200,"message":"生效合同成功"}`，合同 status 被直接 1→2。
业务上合同未确认参与方便已生效，属领域闸门缺失。

### 7.2 根因
`enforce()` 逻辑：状态检查 → `setStatus(EFFECTIVE)`，中间缺「参与方至少两方且角色去重 ≥2」的校验。

### 7.3 修复（`SPMS-Server/.../asset/contract/ContractService.java`）
```java
// enforce() 状态检查之后、setStatus 之前：
FORBIDDEN.when(distinctParticipantRoles(exist.getParticipantList()) < 2, "参与方至少有两方，请先确认");

// 新增纯函数（与 enforce 的两条 FORBIDDEN 逐位对齐，供单测直接驱动）：
public boolean canEnforce(@NotNull ContractEntity entity) {
    return ContractStatus.INVALID.equalsKey(entity.getStatus())
        && distinctParticipantRoles(entity.getParticipantList()) >= 2;
}

// 新增私有 helper：null/空→0；role 为 null 过滤；按 role 去重计数
private int distinctParticipantRoles(Set<ParticipantEntity> participants) { ... }
```
`stop()` 保持仅 EFFECTIVE 可终止（对称不放大）。

### 7.4 单测
新建 `ContractEnforceGateTest.java`（仿 `BillStateMachineGateTest` 纯函数风格，无 Spring/Mockito，
因 `BaseService.get(long)` 是 `public final` 无法 spy）：**6 用例全过**——
① 0 参与方拒；② null 列表拒；③ 1 方拒；④ 同角色 2 方（去重 1）拒；⑤ 2 不同角色通过；⑥ 已生效再生效拒。

### 7.5 live 复验（重启前后端后）
- **负例**：合同 id=1（0 参与方，状态=未生效）点「生效」→ 前端 `onEnforce` 守卫直接 toast「参与方至少有两方，请先确认」（不发请求）；
  绕过前端直接打后端 `POST /contract/enforce {"id":1}` → **HTTP 200 `{"code":403,"message":"参与方至少有两方，请先确认"}`**，状态保持未生效。
- **正例**：给合同 id=1 关联 2 个不同角色参与方（甲方 role1 + 乙方 role2）→ 点「生效」→ `confirmWarning`「没有上传任何附件，是否继续生效合同？」→ 确认 →
  toast「已生效」→ 状态变**生效中**；详情弹窗「合同参与方（2）」tab 列出两参与方（名称/电话/邮箱/证件脱敏）。
- 0 console error / 0 pageerror。
- 证据截图：docs/evidence/34（生效中）、35（详情·参与方 2）。

## 8. 回归与提交
- 后端 `mvn -B -o clean test`：**933 tests（927 基线 + 6 N-15 新增）, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS**。
- 前端守卫 vitest：defaultCheckedKeysGuard 3 + formDefaultValueGuard 3 + dialogWiring 6 = **12 passed**。
- `pnpm check` 全门（lint→airpower→typecheck→coverage→perms/auth/errcode/contract/text/ddl/migration/monitoring/canary/docker/drill→build→demo→size）：
  **22 步全绿**。其中首屏发现 N-2 遗留 lint 与 N-3 遗留 unused-export 并顺手修复；产物体积（gzip）首屏 290.8 KB / 总量 715.4 KB，低于闸门 450 / 900 KB。
- **重启前后端真机复验 PASS**：admin 登录 → 合同列表 row1 `CON2026100001 | 其他合同 | 真机测试合同 R16 | 未生效 | 2026-10-01 | 2027-10-01 剩余 365 天`（生效 en / 终止 dis 正确）；角色·菜单授权弹窗 `已选 34 项` → 清空 → `已选 0 项` + 清空按钮隐藏（N-13 修复持久）。0 console error / 0 pageerror。
- 证据截图：docs/evidence/15–35（本提交含 15–35）。

## 9. R24-25 · 采购→入库→库存→销售→出库 全业务闭环（round 24–25，端到端 PASS）
> 复用真实业务流程数据，以 admin 在真机浏览器走完整条供应链闭环。全程 0 console error / 0 pageerror。
> 单据号由日期前缀 coderule 自动生成（PC/IN/OUT/SL 前缀 + 当天序号）。

### 9.1 采购闭环（`/console/channel/purchase/list`）
| # | 步骤 | DOM 量测结果 |
| --- | --- | --- |
| 1 | 新增采购单 | 弹窗「采购单」，采购单号占位「不填写按编码规则自动生成」 |
| 2 | 添加明细 → 选物料 | 勾 `MA20260004 真机测试物料 R15` → 回填 |
| 3 | 填单价/数量 | 单价 50 / 数量 2 → **小计自动 ¥100.00**（50×2 联动正确） |
| 4 | 选供应商 | 选 `Apple 中国 SUP20260001` → **单价被覆盖为专属价 12345.00**（见 §9.5 说明项） |
| 5 | 填事由 | 「R24真机闭环-采购新建」（textarea 字段） |
| 6 | 保存 | toast「新增成功」→ 列表新行 `PC202610010001 | 审核中`（编辑 disabled，门禁正确） |
| 7 | 审核 | 确认弹窗 → toast「审核成功」→ **状态 待审核→采购中**（审核/驳回/编辑 dis，查看/再来一单 en） |

### 9.2 自动生成入库单 + 入库作业（`/console/wms/input/list`）
- **采购审核后自动生成关联入库单** `IN202610010001 采购入库`（创建 03:08:55，共 171 条 9 页分页正常）。
- 审核入库单 → toast「审核成功」→ 状态→**入库中**（作业按钮由 disabled 变 enabled）。
- 作业 → 「确认入库」：选目标仓库 `华中新仓 R16(SRG0007)` → 待入/入库数量 2 → 确认 → toast「明细入库成功」→ 刷新列表 → **状态→已完成**。

### 9.3 库存联动 +2（`/console/wms/inventory/list`）
- 入库后库存查询：row1 = **`MA20260004 真机测试物料 R15 / 华中新仓 R16(SRG0007) / 2 台`** ✔ 入库→库存 +2 联动验证通过。

### 9.4 销售→自动出库→库存回写
| # | 步骤 | DOM 量测结果 |
| --- | --- | --- |
| 1 | 新增销售单 `/console/channel/sale/list` | 客户 `真机测试客户 R15 CT20260003`；物料 `MA20260004` → 销售单价自动带出 23456.00，数量 2 → 小计 ¥46,912.00 |
| 2 | 保存 | toast「新增成功」→ `SL202610010001 | 审核中` |
| 3 | 审核销售单 | toast「审核成功」→ **状态→出库中**（审核自动生成出库单 `OUT202610010001 销售出库`） |
| 4 | 出库作业 `/console/wms/output/list` | 审核出库单 → 出库中 → 作业 →「确认出库」选库存来源 `华中新仓 R16`（恰 2 台可用）→ 确认 →「明细出库成功」→ 刷新 → **已完成** |
| 5 | 库存回写 | `/console/wms/inventory/list` row1 = **`MA20260004 / 华中新仓 R16 / 0 台`** ✔ 出库 -2 库存回到 0 |

**最终采购单** `PC202610010001` → **已完成**（8 步全链路闭环打通）。

### 9.5 说明项：选供应商覆盖手输单价（设计行为，非缺陷）
采购明细弹窗里先手输单价 50，再选供应商后单价被覆盖为 `12345.00`。根因在
`src/view/console/channel/purchase/component/detailEditor.vue` 的 `lookupPrice()`：**物料+供应商都齐后**自动调
`KPurchasePriceService.getByMaterialAndSupplier(materialId, supplierId)`——命中→`detail.price = price.price`
（协议专属价，绿 tag「协议专属价」）；未命中→toastWarning「该供应商未提供该物料的采购报价…」+ 回落
`material.purchasePrice`（黄 tag「物料参考价」）。这是**旧版语义逐字保真的报价回退链**（`onMaterialPicked`/`onSupplierPicked`
都会触发 lookupPrice），非缺陷。用户若想自定义价，可在选完供应商后**再次手输单价覆盖**。

### 9.6 证据截图（docs/evidence）
- `36-r24-purchase-audited.png`：采购列表 PC202610010001 审核中
- `37-r24-input-completed.png`：入库单 IN202610010001 已完成
- `38-r24-inventory-2.png`：库存 +2 台
- `39-r24-sale-created.png`：销售单 SL202610010001 审核中
- `40-r24-inventory-0.png`：出库后库存回 0 台

### 9.7 本轮改动
- 无代码改动（纯真机验证 + 证据落盘）；全量回归见 §8。

## 10. R26 · 移库闭环 + 未覆盖系统页走查（round 26，端到端 PASS）
> round 26 补走仓储移库闭环与全部未覆盖系统治理页。全程 0 console error / 0 pageerror（各页仅 1 条无内容 warning）。

### 10.1 移库闭环（`/console/wms/move/list`）
| # | 步骤 | DOM 量测结果 |
| --- | --- | --- |
| 1 | 新增移库单 | 弹窗「移库单」，移库单号占位「不填写按编码规则自动生成」 |
| 2 | 选目标仓库 | 选 `西部2仓(SRG0006)`（7 仓可勾） |
| 3 | 从库存挑明细 | 「选择库存」弹窗（维度 按仓库/按生产单元 + 仓库树）选 `键盘 MA20260002 东部2仓(SRG0003) 100台` 行「选择」→ 移动数量 100 预填 → 确定 |
| 4 | 保存 | toast「新增成功」→ `MV202610010001 | 审核中` |
| 5 | 审核 | 确认 → 状态→**移动中**；查看详情「移库单明细」提示「可点击操作列「作业」登记实际移动数量」 |
| 6 | 作业 | 「添加完成数量」100 → 添加完成 → 状态→**已完成** → 刷新列表 row1 `MV202610010001 | 已完成` |

**库存联动验证**（`/console/wms/inventory/list`）：总库存件数 246（移库不增减总量）；`键盘 MA20260002` 西部2仓 0→**100 台**（目标）、东部2仓 100→**0 台**（来源）✔ 移库库存联动通过。

### 10.2 未覆盖系统页走查（全部 0 console error）
| 菜单 | 路由 | 量测 |
| --- | --- | --- |
| 采购价 | `/console/channel/purchasePrice/list` | 1 条；采购单价 `2***0`（字段级脱敏，见 fieldPolicy） |
| 销售价 | `/console/channel/salePrice/list` | 1 条；销售单价 `2***9`（脱敏） |
| 编号规则 | `/console/system/coderule/list` | 22 条；CON `yyyymm` 每月更新、MV/OUT `yyyymmdd` 每日更新 → 印证 MV202610010001 编码递增 |
| 系统配置 | `/console/system/config/list` | 13 条；PURCHASE/MOVE/INPUT/OUTPUT/SALE_BILL_AUTO_AUDIT 均 是（布尔） |
| 操作日志 | `/console/system/log/list` | 27 条；表头 #/时间/动作/资源/目标/操作者/租户/快照/操作，行「查看变更」 |
| 数据资源 | `/console/system/datapermission/resource/list` | 17 条 ENABLED；动作 QUERY,DETAIL,CREATE,UPDATE,DELETE,EXPORT；工具「一键复制当前批次配置/一键放量就绪体检/一键切换 ENFORCE」 |
| 数据策略 | `/console/system/datapermission/policy/list` | 65 条；`personnel_user_delete` 等 系统(放行全部)/优先级100/启用是 |
| 字段脱敏 | `/console/system/datapermission/fieldPolicy/list` | 4 条；`salePrice/purchasePrice/price` default 列表查询 **部分遮蔽(留首尾)** → 印证采购价/销售价脱敏显示 |
| 仓库管理 | `/console/factory/storage/list` | 7 条（华中新仓 R16 SRG0007…东部大仓 SRG0001），树形「添加子级」 |
| 库位结构 | `/console/factory/structure/list` | 3 条（笔记本电脑产线 ST0001/工位1/工位2 单人工位） |
| 计量单位 | `/console/system/unit/list` | 3 条（台 UT0001/千克 R16 UT0002/联调探针单位 UNIT-PROBE-1） |
| 权限管理 | `/console/system/permission/list` | API 权限树：系统状态 Status/权限变更死信 system:data-permission:outbox/迁移归属 ownership/绕过 bypass/超级 system:root/数据资源 DataResource |
| 菜单管理 | `/console/system/menu/list` | 树结构：工作台(1)、仓储管理 WMS(20) 下 入库/出库/移库/库存(1-4)、渠道管理(30) 下 客户/供应商/采购单(1-3)、资产管理 等 |

**字段脱敏闭环**：fieldPolicy 定义 `purchasePrice`/`salePrice` 为「部分遮蔽(留首尾)」→ 采购价页显示 `2***0`、销售价页 `2***9`，与 fieldPolicy 配置逐位对证。

### 10.3 证据截图（docs/evidence）
- `41-r26-inventory-after-move.png`：移库后库存（键盘 西部2仓 100 / 东部2仓 0，总量 246）
- `42-r26-move-completed.png`：移库列表 MV202610010001 已完成

### 10.4 本轮改动
- 无代码改动（纯真机验证 + 证据落盘）。

---

## 11. R27-31 · 采购驳回→编辑→重提闭环 + 分页/低库存/库位热力图 + 多角色 RBAC 门禁（round 27–31）

> round 27-31 补走采购单**驳回→编辑→重新提交**审计闭环、入库分页翻页、低库存预警核对、库位热力图筛选，
> 以及**多角色登录与 RBAC 403 门禁**。并**修复 1 处真实缺陷**：登录页 `isEmail` 拒绝下划线邮箱，
> 与框架编辑器/后端判定分歧，导致系统内置的下划线邮箱用户无法经密码页登录（R-31，P1，见 §11.5）。

### 11.1 采购 驳回→编辑→重新提交 闭环（`/console/channel/purchase/list`，PASS）
| # | 步骤 | DOM 量测结果 |
| --- | --- | --- |
| 1 | 再来一单（copy-create） | PC202610010001 行点「再来一单」→ 预填同供应商 Apple 中国/物料 真机测试物料 R15/数量 2，采购事由自动加前缀 `[再来一单] R24真机闭环-采购新建` → toast「新增成功」→ 列表 row1 = `PC202610010002 \| 审核中`（24,690.00） |
| 2 | 驳回（两步） | 行内「驳回」→ 弹窗「驳回采购单的原因」（ElAlert + textarea maxlength 200 + show-word-limit 0/200 计数）→ 填「R27真机闭环-驳回测试原因」→ 确定 → 二次 confirmWarning「是否确认驳回选择的采购单」→ toast「驳回成功」→ **状态→已驳回** |
| 3 | 驳回态门禁 | 已驳回行：审核/驳回 disabled、**编辑 enabled**（`list.vue` `:disable-edit="!PurchaseStatusEnum.REJECTED.equalsKey(row.status)"`——驳回态可编辑以重新提交）、再来一单 enabled |
| 4 | 编辑-重新提交 | 点「编辑」→ 弹窗 prefill 明细 → 事由覆盖为「R27真机闭环-驳回后重新提交」→ 保存 → toast「保存成功」→ **状态→审核中** |
| 5 | 查看详情 | 「采购详情」含 ElAlert「**单据已被驳回：R27真机闭环-驳回测试原因**」+ 采购单号 PC202610010002/状态 审核中/总金额 24,690.00/实际金额 0.00/驳回原因 R27真机闭环-驳回测试原因；明细 MA20260004 采购单价 12,345.00（协议专属价）数量 2 已采购数量 2 |
| 6 | 搜索/筛选/重置 | 已驳回 radio → 0 条（单已重新提交非驳回态，正确）；搜索 PC202610010002 → 1 条 审核中；重置 → 12 条/清空搜索/radio 回全部 |

**说明**：驳回弹窗「确定」按钮由 `:disable-confirm="rejectReason === ''"`（v-model 绑定）控制，
浏览器合成事件只改 DOM 不改 Vue ref → 按钮不放开；真机需 real keystrokes（Playwright `pressSequentially`）才触发。非缺陷。

### 11.2 入库分页翻页（`/console/wms/input/list`，PASS）
- 共 172 条、20/页；row1 `IN202610010002 移动入库 已完成`、row2 `IN202610010001 采购入库 已完成`。
- 分页器：页码 1(active) 2-6,9（省略号），`btn-prev`/`btn-next` 均 enabled。
- 点 `btn-next` → **第 2 页 active**，20 行（IN202609280007 … IN202609160073），上一页/下一页仍均 enabled。翻页渲染正确。

### 11.3 低库存预警 + 库位热力图（`/console/wms/inventory/list`，PASS + 1 观察项）
- 库存统计条：`在库物料 SKU：6 种 / 总库存件数：246 件 / 低库存预警（< 10 件）：2 项`。
- 精确核对 2 行 qty<10：`MA20260004 真机测试物料 R15 @ 华中新仓 R16(SRG0007) 0 台`（销售出库回写）、`MA20260002 键盘 @ 东部2仓(SRG0003) 0 台`（移库转出）——与预警项数逐一对证。
- **观察项（非缺陷）**：`.stat-pill.is-warning` 虽带 onClick handler 但 `cursor:auto`、点击无任何反应（仍 6 行、无弹窗）——低库存预警为**被动展示指标**，非可交互 drilldown 入口。
- **库位热力图**：2.5D 货架立面 + ECharts 热力色阶；统计 总监控储位 72/库区平均利用率 50%/满载高危>80% 15/空闲低位<20% 15；状态筛选（全量/饱和高载≥80%/正常周转/空闲闲置≤20%）为 `el-radio-button` 组。**点「饱和高载」→ is-active 生效，非匹配 slot-cell 加 `is-low is-filtered-out` class + opacity 0.15**——筛选真实生效（非仅 radio 状态切换）。

### 11.4 多角色登录 / RBAC 403 门禁（PASS）
- **受限角色 R16（testr16@hamm.cn，RO0001，0 权限）**：登录成功 → 见全 34 菜单（RO0001 授权了全部菜单，菜单可见性≠数据权限）→ 导航 `/console/personnel/user/list` → **表格 0 条 + toast「无权限：你无权访问 用户-分页查询」** —— **403 后端 API 级授权门禁生效，独立于菜单可见性**。
- **WMS 仓储专员（wms_1790583398729@kubo.test，WMS_OPERATOR，62 权限）**：登录成功 → 菜单**仅 5 项**（仓储管理 WMS 父 + 入库单/出库单/移库单/库存查询），与 `role_menu_list`（role19 = menu 139-143）逐项对应——**角色菜单授权真实收窄**。

### 11.5 R-31 · 登录页 isEmail 拒绝下划线邮箱（P1，真缺陷，已修）
**现象**：系统内置 WMS_OPERATOR 仓储专员账号 `wms_1790583398729@kubo.test`（下划线邮箱）在登录页填写账号/密码后，
登录按钮仍 `disabled`，表单报「请输入正确的账号、邮箱或手机号」——无法经密码页登录（只能脚本/直连 API）。

**根因（两处邮箱校验器分歧）**：
- 登录页 `src/utils/validators.ts:16`：`isEmail = /^[a-z0-9]+(?:\.[a-z0-9]+)*@.../` —— **拒绝下划线**；
- 框架编辑器 `src/framework/validate.ts:34`：`ValidateUtil.isEmail = /^[\w.%+-]+@.../`（`\w` 含 `_`）—— **允许下划线**；
  用户编辑弹窗（KFormDialog + `ifNotEmail`）用它校验，能创建下划线邮箱用户；
- 后端 `WebhookUrlGuard.EMAIL`（`/^[A-Za-z0-9._%+-]+@.../`）亦允许下划线。

**影响**：编辑器能创建下划线邮箱用户，但登录页拒绝其登录 → **形成死锁**；受限角色无法以 UI 正常登录。

**修复（`src/utils/validators.ts`）**：`isEmail` 改为与框架/后端同源的宽松模式
`/^[\w.%+-]+@[\w.-]+\.[a-z]{2,}$/i`，并更新函数头注释说明真机发现与统一依据。
**回归单测（`src/utils/__tests__/validators.spec.ts`）**：新增「接受下划线本地名」与「接受 `+`/`%`/`-`」两组用例
（原 12 tests → 现覆盖下划线/符号，全过）。

**真机复验**：以 `wms_1790583398729@kubo.test` + `Aa123456` 登录 → **立即登录按钮 enabled、无表单错误**，
提交后进入 `/console/index`，菜单恰为仓储 5 项。修复生效。

### 11.6 证据截图（docs/evidence）
- `43-r27-input-page2.png`：入库列表第 2 页
- `44-r27-heatmap-filter.png`：库位热力图「饱和高载」筛选生效
- `45-r27-r16-403-user.png`：受限角色 R16 访问用户页 403「无权限」
- `46-r27-purchase-detail.png`：采购详情（驳回原因保留 + 重新提交审核中）
- `47-r27-inventory-lowstock.png`：库存低库存预警 2 项
- `48-r27-wms-login-fixed.png`：WMS 仓储专员下划线邮箱登录（修复后按钮可用）

### 11.7 本轮改动
- 修复 `src/utils/validators.ts` isEmail 下划线分歧（R-31，P1）。
- 新增 `src/utils/__tests__/validators.spec.ts` 下划线/符号用例。
- 其余为纯真机验证 + 证据落盘。全量回归见 §8。

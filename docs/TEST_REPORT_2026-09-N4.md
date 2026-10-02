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

---

## 12. R32-34 · 受限角色全链路闭环：落地页 404 与嵌套脱敏值写回（round 32）

> round 32 以 **WMS 仓储专员（WMS_OPERATOR，62 权限，受限字段角色）** 真机走完
> **登录 → 落地 → 新增入库单 → 审核 → 作业确认入库 → 库存联动** 全闭环，
> 定位并修复 **2 处真实缺陷（R-32 P2、R-34 P1）**，另有 1 项经核查判定为测量假象（R-33，非缺陷）。

### 12.1 R-32 · 受限角色登录后 404（P2，真缺陷，已修）

**现象**：`wms_1790583398729@kubo.test`（WMS_OPERATOR）登录成功后，被送到 `/console/index`，
但该角色未授权「工作台」菜单 → 路由未注册 → 命中 catch-all，整屏是
「很抱歉，没有找到你想访问的资源」。**功能不受损，但受限角色一进来就看见 404，观感即故障。**

**根因**：登录成功固定跳工作台，而 `initVueRouter` 只按**已授权菜单树**注册路由；
两者没有交集时，默认落地地址本身就是未注册路径。

**修复（复用已有数据，不加新状态）**：`Console.vue` 本已持有 `menuList` **■**
`src/router/appRouter.ts` 新增纯函数 `firstNavigableMenuPath(menuList, fallback)`（深度优先，
跳过带 `redirect` 的跳板节点与无 path 的分组节点，与 `registerRoute` 的注册语义逐位对齐）
和常量 `DEFAULT_HOME_PATHS`；`src/component/Console.vue` 在 `reloadCurrentRoute()` 之后、
`routesReady` 之前判断：**仅当**当前路由仍是 catch-all **且** 路径属于默认落地地址集合时，
`replace(firstNavigableMenuPath(menuList))`。

> 设计要点：判定条件严格限定「默认落地地址 + catch-all」，
> 因此用户手输一个不存在地址**照常显示 404**，不吞掉真实错误；
> 回退候选直接取自已下发的权限菜单树，不新增接口、不新增 store 状态。

**真机复验**：WMS 账号重新登录 → 落地 **`/console/wms/move/list`（移库单，标题正常）**，
无 404 文案；admin 登录仍落 `/console/index` 工作台（0 console error）。
证据 `56-r32-wms-landing-first-menu.png`。
**说明（非缺陷）**：回退目标是菜单树 DFS 首个可导航节点，实际为「移库单」而非渲染顺序首位的
「入库单」——下发树序与侧栏展示序不同，二者都是该角色有权访问的真实页面。

### 12.2 R-33 · 「移库单新增按钮可见」疑点（核查后关闭，非缺陷）

上一轮用 `evaluate` 合成 click 探测到 WMS 角色移库单页「新增」按钮可点，疑似越权。
本轮改为：① `getBoundingClientRect` + `getComputedStyle` 量测——该按钮 `display:none`；
② 用页面 token 直连 `POST /user/getMyPermissionList` → 62 条权限中含全套 `Input_*`，
**不含任何 `Move_add`/`Output_add`**；③ 真实 `browser_click` 无反应。
**结论**：前端 `v-permission` 门禁与后端 `role_permission_list` 完全一致，
此前是「对 `display:none` 元素派发合成事件仍会触发 handler」造成的**测量假象**。
新增判定规则：*程序化 DOM click 不等于受信事件，权限门禁的否定性断言必须用真实点击 + 后端权限清单双证。*

### 12.3 R-34 · 受限角色保存单据被自家脱敏值拒收（P1，真缺陷，已修）

**现象**：WMS 角色新增入库单，填好物料/仓库/数量点「保存」→
toast「**提交内容有误：字段[purchasePrice]对当前角色受限，无法以脱敏占位值提交；请重新加载后再提交，或联系管理员开通该字段的编辑权限**」，
单据**完全无法新增**。

**根因（跨层协作缺陷，单看任一层都"没错"）**：
1. 该角色字段策略把 `purchasePrice` 读侧脱敏为 `h:41d06a0e9908`（HASH）；
2. `src/view/console/wms/input/component/detailEditor.vue:61-70` `onMaterialPicked`
   把选择器返回的**完整物料对象**整只塞进 `form.value.material`（明细的 material 是 `@ManyToOne`
   引用，后端只按 id 绑定，其余字段从不回写）；
3. `src/framework/http.ts:86` 用 `toJson` 序列化整个请求体，而旧 `toJson`（`src/framework/entity.ts:121-133`）
   **只递归数组元素，嵌套 plain object 原样透传** → 嵌套掩码值原封发给后端；
4. 后端序列化层拒收（**刻意姿态，不改**）：`SensitiveFieldDeserializer.deserialize` 识别
   `looksLikeMaskedPlaceholder` → `handleMaskedPlaceholder` 把**非文本列**（数值 `purchasePrice`）直接
   `PARAM_INVALID.show(...)`；`SensitiveWriteProtection.rejectMaskedOnAdd` 对新增一律拒掩码
   （数值列置 null 会被 `@NotNull(WhenUpdate)` 伪装成「采购单价不能为空」，哨兵值又会静默污染金额，
   故后端选择"宁可让受限角色这条记录改不动"）。

同一缺陷潜伏在 出库单/移库单/采购单 的明细选择器，以及**所有"编辑已有单据再保存"**路径（加载的明细自带掩码）。

**修复（通用序列化层，一处覆盖全部；后端一字未动）**：`src/framework/entity.ts` 把 `toJson`
重写为 `toJsonAt(instance, depth)`——递归进入嵌套 plain Object，**`depth > 0` 时剔除 `looksMasked(value)` 的键**；
**顶层（depth 0）字段一律保留**（顶层掩码数值被拒是后端刻意的写保护姿态，各页面对自有受限字段
已有 blockedSubmit 提示口径，不应由框架静默抹掉）；`instanceof Date` 直接保留
（本文件 `isPlainObject` 极宽松，否则 Date 会被递归成 `{}`）；数组元素按 `depth + 1` 递归。

> **为什么修在框架而不是页面**：判定复用 `src/framework/mask.ts:123 looksMasked`，
> 它与后端 `MaskedWriteGuard.looksLikeMaskedPlaceholder` 的口径由
> `src/config/__tests__/maskContract.spec.ts`（94 tests）**契约锁定**；
> 修在 `http.ts` 唯一的序列化收口处，`entity`/`plain object`/`数组`三类请求体共用同一规则，
> 免去每个编辑器各写一份 `delete row.material.purchasePrice`。
> 剔除是零信息损失（掩码本身不携带业务值），后端只按 `id` 绑定引用。
> **可选后续演进**（评估后暂缓）：后端对"仅作外键引用的嵌套对象"跳过写护栏，
> 需要反序列化器感知级联语义，复杂度与风险都明显更高，不建议。

**单测**：`src/framework/__tests__/entity.spec.ts` 新增「toJson() 剔除nesting层级里的脱敏占位值，顶层掩码保留」——
`details[].material.purchasePrice:'h:41d06a0e9908'` / `salePrice:'**'` 被剔、`{id,code,name}` 保留、
`mainDetail.storage {id:3, code:'SRG****001'} → {id:3}`、顶层 `note:'h:…'` 保留、Date 保原型。
`pnpm vitest run src/framework/__tests__ src/composables/__tests__ src/directives/__tests__ src/config/__tests__/maskContract.spec.ts`
→ **14 files / 194 tests passed**。

### 12.4 WMS 受限角色入库全闭环真机复验（修复后，端到端 PASS）

| # |  steps | DOM 量测结果 |
| --- | --- | --- |
| 1 | 登录落地 | 见 §12.1，落 `/console/wms/move/list`，无 404 |
| 2 | 入库单新增 | 弹窗「入库单」→手动选择→「入库明细」→选物料 `MA20260004 真机测试物料 R15`→选仓库 `华中新仓 R16(SRG0007)`→数量 1→确定 |
| 3 | **保存** | toast「**新增成功**」→ 列表 172→**173** 条，row1 `IN202610010003 \| 普通入库 \| 审核中`（**R-34 修复生效，同一操作此前被 400 拒收**） |
| 4 | 审核 | 确认弹窗「是否确认审核选择的入库单？」→ toast「审核成功」→ 状态→**入库中** |
| 5 | 作业确认入库 | 查看→toolbar「作业」→「确认入库」→「全部入库（1）」+「选择」目标仓库→`华中新仓 R16(SRG0007)`→确认→toast「**明细入库成功**」 |
| 6 | 状态闭环 | 刷新列表 → row1 状态 **已完成** |
| 7 | 库存联动 | `/console/wms/inventory/list`：`MA20260004 @ 华中新仓 R16(SRG0007)` = **1 台**（入库前 0）；统计条 SKU 6 种 / 总库存 **246→247 件** / 低库存预警仍 2 项 ✔ |

**admin 回归（明文全权角色，证明修复对其完全惰性）**：admin 登录落 `/console/index` 工作台（无 404），
入库单页新增 `MA20260001 MacBook Pro M4 Max` @ `SRG0007` 数量 1 → toast「新增成功」→
列表 173→**174** 条 `IN202610010004`。顶层字段不受影响 ⇒ 无回归。

### 12.5 交互层经验（本轮新增，写入门禁）
- **KSelector 勾选必须用真实点击**：对 `.el-checkbox` 派发合成 click 勾不上（组件读原生 change），
  真实 `browser_click` 才出「1 项已选」。
- `el-input-number` 的 `+` 键可用 `mousedown→mouseup→click` 三连合成触发。
- `page.goto` 会清掉 MutationObserver，每次导航后须重新挂 toast 采集器。
- 会销毁导航上下文的 click（提交/跳转）在同一个 evaluate 里 `await` 结果会抛
  「Execution context was destroyed」，属预期，改为 click 后即返回、下一次调用再读状态。

### 12.6 证据截图（docs/evidence）
- `49-r32-workbench-404-wms.png`：受限角色落地 `/console/index` 命中 404（修复前）
- `50-r32-wms-inventory-ok.png`：WMS 角色库存查询页正常渲染（数据权限收窄）
- `51-r32-wms-sidebar-menus.png`：WMS 角色侧栏仅 5 项菜单
- `52-r34-masked-purchasePrice-reject.png`：R-34 修复前保存被拒 toast（purchasePrice 脱敏占位值）
- `53-r34-wms-input-created-173.png`：修复后 WMS 新增入库单成功，共 173 条
- `54-r34-wms-inventory-plus1.png`：确认入库后库存 0→1 台、总量 246→247
- `55-r34-admin-regression-input-174.png`：admin 回归新增成功，共 174 条
- `56-r32-wms-landing-first-menu.png`：R-32 修复后 WMS 登录落到首个授权页（移库单）

### 12.7 本轮改动
- `src/router/appRouter.ts`：新增 `DEFAULT_HOME_PATHS` + `firstNavigableMenuPath()`（R-32）。
- `src/component/Console.vue`：默认落地命中 catch-all 时回退首个授权页（R-32）。
- `src/router/__tests__/appRouter.spec.ts`：回退选择器单测（跳板/分组/兜底）。
- `src/framework/entity.ts`：`toJson` 改深度感知、剔除嵌套脱敏占位值（R-34）。
- `src/framework/__tests__/entity.spec.ts`：嵌套脱敏剔除 + 顶层保留 + Date 保原型。
- 后端与脱敏策略**零改动**；其余为真机验证 + 证据落盘。全量回归见 §8。

## 13. R35 · 会话失效不回登录页：前端兜底体系对标与收口（round 33）

> 本轮由一个真实使用反馈驱动：**「某个页面无法正常加载时，不会返回登录页面」**。
> 以已上线项目 `law-firm-platform`（Vue3 + Element Plus，同为中后台）的前端兜底设计为基准，
> 把 SPMS 的四类「页面加载不正常」场景逐一在真机上跑出来，修掉其中确凿的一处（R-35），
> 并给出可落地的兜底体系统一方案。

### 13.1 四类失效场景真机矩阵（Playwright，dev：vite 3000 + 后端 8080）

| 场景 | 注入方式 | 修复前实测 | 结论 |
| --- | --- | --- | --- |
| **A 无 token 深链** | 清空 `Authorization` 后**全页**导航 `/console/wms/input/list` | `authGuard` 拦下 → `/login?redirect=%2Fconsole%2Fwms%2Finput%2Flist` | **无缺陷**（`src/guard/authGuard.ts:17-34` + `src/view/login.vue:42,93` 回跳链路本就通） |
| **B 陈旧 token 深链** | `localStorage.Authorization = 'Bearer …STALE…'` 后全页导航 | `authGuard` **只看 token 存在即放行** → `POST /api/user/getMyTenantList` 返回 **HTTP 200 + 业务码 401** → axios 只 toast → `Console.init()` catch → 整屏「控制台初始化失败」错卡，**重试/返回首页永远再失败，从不回登录页** | **R-35 真缺陷（P1），已修** |
| **C 懒加载 chunk 失败** | 路由拦截把 `…/wms/move/list.vue` 永久 `abort()`（等价发版后旧 index.html 引用已替换的 chunk） | 第 1 次失败 → `router.onError` → **自动刷新一次**（`sessionStorage['kubo:router-error-reload']` 已写入）→ 第 2 次仍失败 → 错卡「控制台初始化失败 / Failed to fetch dynamically imported module」；网络恢复后点「重试」**页面自愈**（标题回到「移库单」） | **兜底有效**（`src/utils/routerErrorHandler.ts`），无需改动 |
| **D 后端宕机** | 路由拦截 `abort()` 全部 `/api/**`（等价网络层全失败） | 错卡「控制台初始化失败 / **网络连接失败**」，**token 未被清掉**（正确：网络故障 ≠ 会话失效），网络恢复后点「重试」→ 列表 20 行正常 | **兜底有效**，行为正确 |

> **场景 C 值得记录的机制细节**：chunk 失败的报错最终**不是**由 `router.onError` 展示，而是被
> `Console.vue` 的 `init()` catch 接管——因为 `getMenuList()` 内部 `await reloadCurrentRoute()`
> （`src/component/Console.vue:61`）把导航错误变成了 init 的同步异常。`router.onError` 仍然负责
> 「自动刷新一次」这一段（时间窗 10s 内最多一次，防坏部署把用户困在无限刷新里）。
> 两层兜底**串起来了**，所以用户看到的是「闪一下 → 一张可重试的错卡」，而不是白屏。

### 13.2 R-35 · 业务码 401 无人收口（P1，真缺陷，已修）

**根因（跨层口径不一致）**：后端三个 `@RestControllerAdvice` 全部标注
`@ResponseStatus(HttpStatus.OK)`，鉴权失败因此**以 HTTP 200 + 业务码 401 的形态**返回
（`Errors.UNAUTHORIZED`「获取你的身份信息失败，请重新登录后再试」、
`RequestInterceptor.java:156,224`「登录状态已失效，请重新登录」、`AccessTokenUtil`）。
而前端 axios 的登出流程**只挂在 HTTP 状态码 401 的 `case 401:` 分支上**——对本项目而言
那条分支几乎是**死代码**（既然后端永远返回 200），真正的会话失效路径反而没人处理。
`src/api/errorPolicy.ts:42-47` 的注释其实**早就写下了这个缺口**，只是一直没人来补。

**修复（`src/api/axios.ts`，三处，后端零改动）**：
1. 响应成功拦截器的 `code !== 200/0` 分支补上收口：
   ```ts
   if (res.code === BusinessCode.UNAUTHORIZED && !isAuthEndpoint(response.config.url)) {
     void handleSessionExpired()
   }
   return Promise.reject(error)
   ```
   `void` fire-and-forget（原请求照常 reject，页面各自的 catch 逻辑不受影响），
   登出与跳转由 `sessionExpiredHandling` 标志统一防重入。
2. 抽出 `isAuthEndpoint(url)`（`/user/login`、`/user/refresh`、`/user/logout` 前缀白名单）——
   **登录/登出接口自身返回 401 是业务语义**（密码错、重复登出），绝不能触发「会话失效」递归跳登录；
   HTTP 401 分支原本内联的同类判断也改为复用这个函数（口径只有一处）。
3. `handleSessionExpired()` 的跳转从裸 `/login` 改为
   **`/login?redirect=<当前 path + query>`**，与 `authGuard` 完全同口径。

> **设计取向：为什么收口在 axios 而不是守卫或组件**
> 「会话失效」的唯一权威信号是**服务端应答**，而 axios 是服务端应答的唯一入口。
> 收口在这里 ⇒ 任何一次业务请求（不管来自哪个页面、哪个 store）失效都会**同一条路径**登出并带回跳上下文，
> 不必在每个 `catch` 里补 `if (is401) router.push('/login')`；
> 守卫侧（`authGuard`）只负责「本地根本没有 token」这种可静态判定的情形，两层职责不再互相期待。
> 附带收益：`Console.vue` 那张错卡的「返回首页」死链问题（会话失效时点了也只是再撞一次 401）
> 自然消失——用户在看到错卡之前就已经被送到登录页了。

**单测（`src/api/__tests__/axios.spec.ts`，26 tests 全绿）**：新增 3 例
① HTTP 200 + 业务码 401 打在 `/user/getMyTenantList` → `logout` 恰 1 次 + `location.replace('/login?redirect=%2F')`；
② `/user/login` 自身返回业务码 401 → **不登出、不跳转**；
③ `Promise.allSettled` **并发**打两个业务 401 → 登出/跳转各恰 1 次
（**关键**：串行 `await` 会让 `finally` 复位 `sessionExpiredHandling`，第二次也照样放行，测不出防重入）。

**真机复验**：
- 场景 B 修复后：stale token 全页导航 → 自动 `location.replace('/login?redirect=%2Fconsole%2Fwms%2Finput%2Flist')`；
  用 WMS 账号重新登录 → **准确回到原页面**「入库单」（标题、URL 均正确）。
  证据 `57`（修复前死循环错卡）/ `58`（已跳登录且带 redirect）/ `59`（重登回到原页）。
- 会话**中途**失效（页面已渲染，直接把 store 里的 token 换坏再点「刷新」）：
  → 自动清 token + 跳登录带 redirect。证据 `60`。

### 13.3 与 `law-firm-platform` 的兜底设计对标

基准：`D:\JAVA\Code_repository\law-firm-platform\law-firm-web\src`
（`utils/request.js`、`router/permission.js`、`utils/lazyLoad.js`）。

| 兜底维度 | law-firm 做法 | SPMS 修复前 | SPMS 修复后 |
| --- | --- | --- | --- |
| 业务码 401 | `request.js`：`ElMessageBox.confirm` → `resetState()` → `push('/login?redirect=…')` | **完全没人处理**（死循环错卡） | axios 收口 → 登出 + `replace('/login?redirect=…')`（**不弹窗**：本项目失效即失效，确认框只是多一次点击） |
| HTTP 401 | `request.js` 同分支处理 | 有分支但**近乎死代码**（后端永远 200） | 保留，且与业务码共用 `isAuthEndpoint` 白名单 |
| 路由/权限初始化失败 | `permission.js`：catch → `ElMessage('登录已过期…')` + `resetState()` + `next('/login?redirect')` | 只有静态守卫，**运行时初始化失败无出口** | 会话类失败有出口；其余失败保留可重试错卡 |
| 懒加载失败 | `lazyLoadWithErrorHandling(importFunc, path)` → 渲染降级 `ElResult`（重新加载/返回首页）+ `__ERROR_TRACKER__.captureException` | `router.onError` → 刷新一次（10s 时间窗）→ init 错卡 | 维持（实测可自愈，见场景 C）；**降级组件列为可选演进** |
| 导航进度反馈 | NProgress 顶部进度条 | **无**（冷启动 1.4–5s 期间零反馈） | 未改，列为建议 D3 |
| 目标回跳上下文 | 所有失效路径都带 `redirect` | 登录页支持读，但会话失效路径**丢掉**了上下文 | 三类路径统一带 `redirect` |
| 网络/服务类错误文案 | 403/404/413/500 逐码文案兜底 + 全局 `ElLoading` 计数 | 有 `errorPolicy.ts` 分类（33 tests） | 维持，文案分层列为建议 D5 |

**对标结论**：law-firm 的做法**更朴素但覆盖面完整**——它的核心不是某个技巧，而是
「**所有会话失效信号在 HTTP 层汇聚成一个动作**」。SPMS 的分层（守卫/axios/errorPolicy/错误边界/路由错误兜底）
**每一层单独看都比它强**，缺的恰恰是层与层之间那条「会话失效」的连线。
本轮补的就是这根线，**没有新增一层**。

### 13.4 可行性设计建议（按性价比排序，均未实施）

| 编号 | 建议 | 做法 | 成本 | 收益 / 风险 |
| --- | --- | --- | --- | --- |
| **D1** | **会话失效单一事件源** | 新建 `src/utils/session.ts`：`expireSession(reason, source)` + `onSessionExpired(cb)`；axios 只**发事件**，登录跳转/提示文案由**唯一订阅方**（壳层）决定 | 小（≈1 文件 + 3 处调用改线） | 收益：现在「登出 + 跳转」写死在 axios 里，测试要 stub `location`；发事件后可分别单测。风险：低 |
| **D2** | **清理死掉的 refreshToken 路径**（或后端补真端点） | `src/api/system.ts:59-63` 已注明后端**没有** `/user/refresh`，`useUserStore().refreshToken()` 必抛 ⇒ HTTP-401 分支先试刷新是**纯浪费一次往返**。二选一：删掉刷新分支只留登出；或后端补 `POST /user/refresh` 做滑动续期，把「会话失效」从用户可见路径上移走 | 删：极小<br>补：后端中等 | 收益：少一条不可测分支 / 直接消灭这类失效。风险：补端点需同步 token 策略与 Redis TTL |
| **D3** | **顶部进度条**（NProgress 或 2KB 自研） | 挂在 `router.beforeEach/afterEach` + `Console.init()` 起止；只动 `transform` | 小 | 收益：冷启动 1.4–5s 期间不再「看起来像坏了」，直接压这类工单。风险：几乎没有（记得 `prefers-reduced-motion` 时降级为纯进度） |
| **D4** | **把 console 初始化上提为路由守卫** | 现状 `Console.vue` 的 `init()`（租户/用户/权限/菜单四连）**在组件 setup 里**跑，深链冷启动必然先渲染一帧外壳/404 再纠正。改为 `beforeResolve` 在 `/` 父路由上解析，失败即可在**导航阶段**决定去登录页或错误页 | 中（涉及 `routesReady`、R-32 落地页回退逻辑一并迁移） | 收益：消除「先闪 404/空壳再纠正」整类观感问题，兜底决策点唯一。风险：中——与 R-32 的 `firstNavigableMenuPath` 回退耦合，需连带回归 |
| **D5** | **错卡文案分类** | 场景 D 实测文案是「控制台初始化失败 / 网络连接失败」，用户分不清是**服务挂了**还是**自己断网**。按 `errorPolicy` 分三档标题：服务不可用（含自动重试倒计时）/ 网络异常 / 权限异常 | 小 | 收益：降低无效报障。风险：低 |
| **D6** | **chunk 失败降级组件**（对标 `lazyLoad.js`） | 包裹 `import.meta.glob` 的 loader，失败除刷新外提供可点降级块 | 小 | 收益**有限**：场景 C 实测「刷新一次 + 可重试错卡」已能自愈，属锦上添花。**优先级最低** |
| **D7** | `error-report` 免鉴权/失败静默 | 会话失效瞬间的 `/api/error-report` 也会带上坏 token；目前返回 200 无副作用，建议在 `isAuthEndpoint` 口径外再显式忽略上报接口的 401 | 极小 | 收益：避免噪音。风险：低 |

**推荐落地顺序**：D2（删，10 分钟）→ D3（观感收益最大）→ D1（结构收敛，为 D4 铺路）→ D5 → D4 → D6。
D1/D4 **不建议同时改**：D4 会挪动 D1 的订阅方位置，先做 D4 会把 D1 的改动重做一遍。

### 13.5 证据截图（docs/evidence）
- `57-r35-initialz-fail-deadloop-before-fix.png`：修复前——stale token 卡在「控制台初始化失败」，重试无出口
- `58-r35-stale-token-redirected-to-login.png`：修复后——自动跳 `/login?redirect=%2Fconsole%2Fwms%2Finput%2Flist`
- `59-r35-relogin-returned-to-origin.png`：重新登录后**准确回到**原页面（入库单）
- `60-r35-mid-session-401-to-login.png`：会话**中途**失效（点「刷新」）同样回登录页并保留 redirect
- `61-r35c-chunk-fail-reload-once-then-card.png`：场景 C——chunk 永久失败：刷新一次后给可重试错卡
- `62-r35d-backend-down-card-no-logout.png`：场景 D——后端宕机：错卡但**不清 token**（不误判为会话失效）

### 13.6 本轮改动
- `src/api/axios.ts`：业务码 401 收口 `handleSessionExpired()` + `isAuthEndpoint()` 白名单 +
  会话失效跳转携带 `?redirect=`（R-35）。
- `src/api/__tests__/axios.spec.ts`：location 替身补 `pathname/search`，
  2 处既有断言改为断言 redirect 口径，新增 3 例（业务码 401 收口 / 登录接口豁免 / **并发**防重入）。
- 后端、路由、守卫、`Console.vue`、脱敏策略**零改动**；场景 A/C/D 经真机确认**无需改动**。
- 新增契约认识（写入 §12.5 同一门禁）：
  **「兜底是否有效」的判据不是有没有 catch，而是失败之后用户有没有一条能走通的路**——
  一张只有「重试」的错卡，在服务端持续拒绝时等价于死路。

## 14. R36–R38 · §13 修复引入的新边界实测（round 34）

> §13 把会话失效收口到 axios 之后，本轮按「修一处、验一圈」的规矩，把**收口本身引入的新边界**
> 逐条在真机上打穿：`?redirect` 开放重定向、`/login` 页上的业务码 401 环路、多标签页登出同步、
> 离线/恢复、坏 JWT 形态。三处真缺陷（R-36/R-37/R-38）修复并补单测，其余实测**无缺陷**并记录判据。

### 14.1 边界实测矩阵（Playwright，dev：vite 3000 + 后端 8080）

| 场景 | 注入方式 | 实测 | 结论 |
| --- | --- | --- | --- |
| **`?redirect` 开放重定向** | 登录页带 `?redirect=` 各类外域/混淆写法登录后观察落点 | 旧判据（前缀检查）被 `/\evil.test`、`/<TAB>//evil.test`、`/<LF>//evil.test` 绕过 → 会话送外域 | **R-36 真缺陷（P1），已修**，见 14.2 |
| **`/login` 页业务码 401 环路** | `localStorage.Authorization='Bearer …DEAD-STALE…'` 后全页导航 `/login` 并驻留 | 登录页**不发任何业务 `/api` 请求**，`/user/login` 自身 401 又被 `isAuthEndpoint` 豁免（axios.ts:259,288）→ 零 replace、零循环 | **无缺陷**（观察：登录页驻留时残留的 stale token 无人清，无实际危害） |
| **多标签页登出同步** | A/B 两标签页同会话，A 用真实 UI（`.user-card__trigger`→退出登录，无确认框）登出，观察 B | 修复前：B **毫无反应**——停留在原页、20 行数据照拉、还能翻页导航，`{"code":200,"message":"获取成功"}`；修复后：B **无交互自动**落到 `/login?reason=signedOut&redirect=…` 并显示原因提示 | **R-38 真缺陷（P1），已修**，见 14.4 |
| **离线 → 恢复** | `ctx.setOffline(true)` 后：页内「刷新」、离线软导航、恢复网络 | 刷新 → toast『网络连接失败』（axios 无响应分支，axios.ts:272-278），表格保留最后好数据、会话不误清；软导航 → 懒加载 chunk 失败 → `router.onError` 刷新一次（10s 时间窗）→ 浏览器离线页（**dev 无 SW**：`hasServiceWorker=0`，`registerPWA()` 仅生产注册）；恢复 → `KAppShell.vue:151-159` toast『网络连接已恢复』，再点刷新数据回 20 行 | **无新缺陷**：数据层有准确 toast、恢复有 shell 级提示；生产离线壳由 SW 覆盖（dev 不可测，不强造） |
| **坏 JWT 形态** | 把 `Authorization` 换成非 JWT 字符串/截断 JWT 后发业务请求 | 前端 `src/` 对 `atob|jwt|payload.exp` **零引用**——前端从不解析 token，坏形态只会由后端回业务码 401 → 走 R-35 既有收口 | **无前端缺口**（收口早已覆盖，本轮确认判据成立） |

### 14.2 R-36 · `?redirect` 开放重定向绕过（P1，已修）

**根因**：旧 `safeRedirect` 只做 `startsWith('/') && !startsWith('//')` 前缀检查，
但 `window.location` 按 **WHATWG URL 规范**解析：反斜杠等同斜杠（`/\evil.test`→`http://evil.test/`）、
TAB/LF/CR 在解析前被剔除（`/<TAB>//evil.test`→`http://evil.test/`）。
「我们的判据」与「浏览器的判据」不是同一件事，`//evil.test` 拦住了、`/\evil.test` 漏了。

**修复（`src/utils/safeRedirect.ts`，纯函数 +47）**：①先拒含反斜杠/C0/C1 控制字符的目标
（合法站内路由不存在这类字符）；②**让浏览器自己的解析器当终判**——`new URL(target, FAKE_BASE)`
后只认 `resolved.origin === FAKE_BASE`，规范再出新规整规则也绕不过 origin 比对。
编码反斜杠 `/%5Cevil.test` 解析后仍在站内，照常放行（单测钉住不误伤）。

**单测**：`src/utils/__tests__/safeRedirect.spec.ts` 9 例（含用 `String.fromCharCode(92/9/10/13)`
构造的全部绕过变体 + 「若真放行就会跨 origin」的前提自证）。
**真机**：`?redirect=/%5Cevil.probe.test` 登录后落 `http://localhost:3000/console/index`，`originOk=true`。证据 `63`。

### 14.3 R-37 · 登录失败页内零反馈（P2，已修）

**症状**：密码输错时 R-35 的会话收口豁免了 `/user/login`（正确！密码错是业务结果），
全局 toast 又 3 秒即逝——用户盯着表单看不到任何失败原因，等价「点了没反应」。
**静默的前提是有人呈现**：`SILENT_CODES` 里豁免一条码，就得有人负责呈现它。

**修复**：`src/api/errorPolicy.ts` 新增 `loginInlineError(error)`（只认 `typeof code === 'number'`，
`kind==='credential'` 才返回文案，其余 `undefined`——全局 toast 已说过的事不重复说）；
`src/view/login.vue` 三个登录入口（账密/邮箱/手机号）统一走 `onLoginFailure`，
页内插 `<div class="login-feedback" role="alert" aria-live="assertive">`。
**单测**：`errorPolicy.spec.ts` +5（现 38 例）：200001 精确文案、业务码 401 含『登录状态已失效』、
500/4002/403→undefined、非 ApiError（Error/null/字符串/字符串 code）→undefined。
**真机**：错误密码 → `.login-feedback` 显示『登录未通过：用户账号或密码错误』，`toastCount=0`，停在 `/login`；
正确密码重试 → `/console/index`。证据 `64`。

### 14.4 R-38 · 跨标签页登出不同步（P1，已修）

**根因链（全部实测钉死）**：登录态有**两处**——`Authorization`（localStorage，权威）与
`useUserStore().token`（**内存**，请求拦截器真正读的那份，axios.ts:190-192）。A 登出只摘自己的两处；
B 内存 token 还在、继续带旧凭证发请求。且后端 `/user/logout` 的撤销依赖 Redis
（`kubo:token:revoked:<sha256>`，本地 dev 无 Redis = **不撤销**），实测登出后旧 JWT 照旧
`{"code":200,"message":"获取成功"}` 拉到真数据。**共享电脑上的「注销」在其它标签页形同虚设。**
E-C1 的 `onRestore` 只兜「重新加载」情形（实测 reload 安全），活标签页无人管。
对标 `law-firm-platform` 同样**没有** storage/BroadcastChannel 监听——这是行业性缺口，不是对标倒退。

**修复（3 改 + 1 新增）**：
1. `src/stores/user.ts`：抽出 `clearLocalSession()`（L105-122，清内存态 + `authToken.remove()`
   + `kubo:user` + queryClient + 脱敏缓存 + 权限 store，**不打后端**）；`logout()` 改为
   `authApi.logout()` try/catch → 同一收口。
2. **新增 `src/utils/authSync.ts`**：`installAuthSync()` 监听 `window 'storage'`，
   只认 `key==='Authorization' && newValue===null`（对端登出）→ `clearLocalSession()` →
   非登录页时 `replace('/login?reason=signedOut&redirect=<当前 path+query>')`。
   **选 `storage` 事件而非 BroadcastChannel**：事件挂在唯一权威来源上，零新增状态；
   BC 需要每条登出路径显式广播，漏一处就静默失效。对端**登录**（newValue 有值）刻意忽略——
   不能抢占可能正在填表单的标签页。
3. `src/main.ts`：pinia 就绪后、挂载前 `installAuthSync()`。
4. `src/view/login.vue`：`signedOutReasonHint(route.query.reason)` →
   `<div class="login-hint" role="status">`『当前账号已在其他标签页退出登录，请重新登录』
   （认不出的 reason 不编造原因）。

**单测**：新增 `src/utils/__tests__/authSync.spec.ts` 10 例（对端登出→清态+精确 replace+
**不**重复打 `/user/logout`；对端登录/其它键/本标签页未登录→无动作；登录页上只清态不 replace；
dispose 摘监听；`clearLocalSession` 无后端调用；`logout` 打后端恰 1 次）+ `user.spec.ts` 8 例，18 全绿。
**测试课（值得记录）**：`storage` 监听挂在 window 上**跨用例存活**，早先安装的监听会用
「当时激活的 pinia」响应后续用例——所有安装必须经统一 `setup()` 收口、`afterEach` 逐个 dispose。

**真机（修复后）**：A 真实 UI 登出 → B **无交互自动**落
`/login?reason=signedOut&redirect=%2Fconsole%2Fwms%2Finput%2Flist`，hint 正确、表格消失、token=null；
从 B 自己的登录页重新登录 → **准确回到**其原页（20 行，errorCard=0）；登出态新开标签页 →
`/login?redirect=…`；B 强制 reload 无快照复活（E-C1 兜底在位）。证据 `65`。

### 14.5 可行性建议落地结论（本轮验证驱动）

| 编号 | 决定 | 依据 |
| --- | --- | --- |
| **D2** 清理 refreshToken 死分支 | **不改** | 实测 `useUserStore().refreshToken()` 在进入网络前即抛 `No refresh token available`——「浪费一次往返」的**前提不成立**；该分支是未来后端补 `/user/refresh` 的挂载点，且被 4 条 axios 契约测试钉住，删除是零收益的破坏性重构 |
| **D7** error-report 401 豁免 | **不改** | `src/utils/errorReportClient.ts` 走**裸 `fetch`**（不经 axios 拦截器），`REPORT_URL='/api/error-report'`、60s 去重、`keepalive:true`——「上报请求带坏 token 触发收口」的前提不成立 |
| **D3** 顶部进度条 | **暂不落地** | 本轮边界矩阵未产出支持它的缺陷证据；冷启动反馈是观感增强非正确性缺口，且新增全局动效需连 `prefers-reduced-motion` 一起做，建议单独立项 |
| **D5** 错卡文案分档 | **暂不落地** | 离线实测显示**交互路径**已有准确分档反馈（『网络连接失败』toast + 恢复提示），仅**冷启动 init 错卡**标题仍是统一的「控制台初始化失败」；属文案打磨，维持 §13.4 优先级排序 |

### 14.6 证据截图（docs/evidence）
- `63-r36-redirect-bypass-fixed.png`：R-36 修复后——混淆 redirect 登录落回站内 `console/index`
- `64-r37-login-inline-feedback.png`：R-37——错误密码时登录按钮上方常驻『登录未通过：…』
- `65-r38-multitab-logout-sync.png`：R-38——A 登出后 B 自动落到带原因提示与 redirect 的登录页

### 14.7 本轮改动
- `src/utils/safeRedirect.ts`（新）+ 9 例单测：浏览器解析器终判的开放重定向防护（R-36）。
- `src/api/errorPolicy.ts`（`loginInlineError`）+ `src/view/login.vue`（三入口内联反馈 + `role=alert`）
  + `errorPolicy.spec.ts` +5 例（R-37）。
- `src/utils/authSync.ts`（新）+ `src/main.ts`（安装）+ `src/stores/user.ts`（`clearLocalSession` 抽口）
  + `login.vue`（`login-hint`）+ `authSync.spec.ts` 10 例（R-38）。
- 后端、路由、守卫**零改动**；`/login` 401 环路、离线/恢复、坏 JWT 三场景实测确认**无需改动**。
- 新增契约认识（并入 §12.5 门禁）：
  **「白名单/前缀检查不等于浏览器的判断」——凡是把用户输入喂给 `location` 的地方，终判必须交给同一个 URL 解析器的 origin 比对**；
  **登出/会话类修复必须把「活标签页」当作默认场景**——localStorage 只是持久层，内存 store 才是请求真正携带的凭证。

## 15. R39 · D3/D5 落地与逐模块横向收口（round 35）

> 本轮把 §13/§14 挂账的 D3（顶部进度条）、D5（错卡文案分档）正式落地，并对 api / stores /
> router / framework / utils / views 做了一次**横向**排查（此前各轮多为纵向打穿一条链路）。
> 判定标准不变：真机或单测能复现才修，修完钉单测；不能复现的写判据；动全局的先立契约。

### 15.1 D3 · 顶部导航进度条（落地，nprogress ^0.2.0）

**选型**：复用现成方案 `nprogress@0.2.0`（~2KB gzip、零依赖、benchmark `law-firm-web` 同款），
不手写。与 benchmark 的两处刻意差异（写进 `src/utils/navProgress.ts` 头注释）：
1. **补 `router.onError` 腿**——benchmark 只挂 beforeEach/afterEach，chunk 失败时条永远悬在 99%；
   判据：**一条死在 99% 的进度条比没有更糟**（用户会等一个永远不会来的页面）。
2. `minimum:0.1` 而非 0.3——0.3 起步是假装已有 30% 进度，撒谎从起点开始。
   配置全量：`{showSpinner:false, trickleSpeed:200, minimum:0.1, speed:400}`。

**实现**（`src/utils/navProgress.ts` 92 行 + `src/main.ts:77` 安装 + `src/styles/motion.scss:162-179,182-206`）：
- `installNavProgress(router)` 幂等闩（`installed`），返回卸载函数（3 个钩子逐一摘除 + `NProgress.remove()`）；
- 色带 2px、`background: var(--el-color-primary)`（跟主题）、`aria-hidden="true"`（装饰性动效不进读屏）、spinner 关闭；
- **`prefers-reduced-motion: reduce` 时 `#nprogress { visibility: hidden }`**——用 visibility 而非 display，
  因为 nprogress 会读写 `.bar.style.width`，display:none 会让它在不可见布局里做无意义回流；
- `afterEach` **无条件** `done()`：vue-router 4.6 在 redirect/abort 时同样触发 afterEach（版本已核实），
  不判断「是否成功导航」反而不会漏收尾。

**真机（证据 66/66b/67）**：
- 导航空档期（对懒加载 chunk 注入 2.2s 延迟 + 真实侧边栏点击）：条存在、`visibility:visible`、
  `opacity:1`、theme 主色、高 2px、spinner 无、`aria-hidden="true"`；
- 同一实例驱动 `start()→done()` 的生命周期采样：`+600ms` trickle 至 12.7%，`done()+400ms` 淡出，
  **`done()+1600ms` 元素已从 DOM 移除**；全新 context 复测：idle 无元素 → mid 有 → settled 无；
- `reducedMotion:'reduce'` 上下文：条全程 `visibility:hidden`（用户看不到动效），
  导航照常完成（`navWorked=true`，表格可见、标题正确）——**隐藏动画 ≠ 隐藏功能**。
- 曾观察到的「导航结束后条滞留」为探针伪影：`browser_run_code_unsafe` 跨调用**累积**的
  `page.route` 处理器让 chunk 请求永远悬挂，导航 legitimately 未完成。教训入档：
  **每个测量用全新 context；探针注入比被测代码更易出 bug**。

**评估后不做**：`setTimeout(done, N)` 兜底上限——若懒加载请求真的永远悬挂，页面本身同样悬挂，
此时条仍在 trickle 是**诚实**的状态指示；强行到点隐藏等于宣称「导航完成」而页面没变，
比滞留更接近「撒谎的条」，与被否决的 99% 死条同罪。

### 15.2 D5 · 错卡文案分档（落地，`src/utils/errorCopy.ts`）

新增纯函数 `describeUserError(error, fallbackTitle?): UserErrorCopy{title,detail,hint,raw}`，
四档判据（每档必给「下一步」，见文件头注释）：
chunk 失败→「页面资源加载失败」+明说 **Ctrl+F5**（这类错的真相是 index.html 引用的文件已不存在，
点重试按钮不重拉 index.html，不写清就是死路一条）；网络/超时→「网络连接失败」（与 axios 无响应分支同口径）；
数字 code→直接透传 `classifyBusinessError`（同一句话只说一遍，不另写一套）；兜底→fallbackTitle，
**原文永远保留在 detail 槽**（降级不丢弃，运维要靠它定位）。
接线：`Console.vue`（init catch 给「控制台初始化失败」+ `clear()` 权限 + 原文灰字 + 重试/返回首页；
`onErrorCaptured` 给「页面渲染异常」）、`ErrorBoundary.vue`（「页面渲染出错」，仍单次上报）。

**真机（证据 68/68b）**：kill 8080 → 冷启动错卡
`控制台初始化失败 / 请稍后重试；若持续失败，请把本次操作时间与 traceId 告知管理员 /
"Request failed with status code 500"（灰色次要色）/ [重试][返回首页]`；
重启后端 → 点**重试**原地恢复（卡消失、菜单在、无刷新）。这是 §12.5 判据的正向用例：错卡必须有出路。

### 15.3 api 模块 · 提示权威唯一化（R-39）

本轮通读 `src/api/axios.ts` 全文（497 行）后确认并修复：网络失败（超时/断线）、404、429、
未知状态码四类此前是**裸 `ElMessage`**——绕过分档、绕过去重、无视 `showError:false`
（`hideError()` 形同虚设；断线时列表页 5 个并发请求=5 条红条糊屏）。现在全部经
`notifyBusinessError`，`src/api/` 内 `ElMessage.error` 仅剩 :159 一处（唯一出口），契约由
tests 钉住（状态码 422/500 的既有用例本就是同句式，本轮补齐缺的三档）。
配套 `errorPolicy.ts`：`NETWORK_ERROR_CODE={timeout:-1,offline:-2}`、`RATE_LIMITED:429`、
429 档放在 `isUnsupportedCode` **之前**（否则「请求过于频繁」会被误分档成「接口未开放」——
后者教用户「别重试了去找人」，前者教用户「稍等几秒重试」，指引完全相反）；
断线通知传 `path:''`——「断网」是全局一件事，按路径去重压不住 N 个并发路径。
`classifyBusinessError` 42 例、axios 36 例全绿。

### 15.4 其余模块横向排查（复现即修，全部钉单测）

| 模块 | 缺陷（复现方式） | 修复 | 单测 |
| --- | --- | --- | --- |
| `framework/entity.ts` | `copy()` 顶层循环未过滤 `__proto__`（`JSON.parse` 出带 `__proto__` 自有属性的对象再 `copy` → `result['__proto__']=…` 触发 setter，**换掉克隆原型的 getter**） | `UNSAFE_KEYS` 同时挡在 `deepCopy` 嵌套层与 `copy` 顶层 | entity.spec +2（`Object.defineProperty` 造枚举 `__proto__`） |
| `framework/http.ts` | `queryExport` 500ms 轮询文件生成状态，未就绪是**预期业务态**却每次失败弹 toast（3s 去重窗口一过又弹） | 加 `hideError()`（`ExportUtil.waitForFile` 的 catch 就是轮询信号） | service.spec +1 |
| `framework/validate.ts` | `isMobilePhone` 与 `utils/validators` 正本漂移（副本 `\d{1,3}` 国际前缀放行 `+651…` 错号段） | 委托正本，删除副本正则 | 新 validate.spec 4 例（`+6513812348888` 是分水岭） |
| `router/appRouter.ts` | 菜单某行 `name` 为空 → **throw** → 整控制台进不去：一行坏数据（管理端表单留空即可造成）放大成全站故障 | 跳过该节点 + `console.warn` 留诊断；**检查放在 children 递归之后**（分组节点缺 name 不殃及子树） | appRouter.spec +2 **「坏一行、跳一行、绝不全崩」** |
| `stores/persist.ts` | pick 只在**写入侧**过滤，恢复侧照单全收 → 旧版快照里的 `userInfo` 明文 PII 每次加载被 `$patch` 回内存，「不落盘」承诺被绕过一半 | 恢复侧同样按 pick 过滤 | persist.spec +1（预置 flatted 旧快照验证恢复侧被挡） |
| `stores/permission.ts` | 权限列表镜像写 `localStorage['permissions']`，**生产零读取方**（活数据只进 sessionStorage `kubo:permission`）→ 第三份无人读的权限副本，登出漏清就是陈旧面 | 删镜像 + 一次性 `removeItem` 清残留（老用户浏览器里的旧键） | 由既有 persist/auth 面覆盖 |
| `utils/storage.ts`（新） | 隐私模式/配额满时 `localStorage.setItem` 直接 throw，主题切换/折叠/登录态写入这类**便利数据**能炸掉整个交互 | `safeSetItem/safeGetItem/safeRemoveItem`，接线 `stores/theme.ts`、`utils/authStorage.ts`、`KAppShell.vue` | storage.spec 3 例 |
| `utils/draftStorage.ts`（新） | 草稿键 `kubo:draft:<service>` **不带身份**：登出后下一个账号打开同服务新建弹窗，会恢复**上一个账号**的草稿（客户电话等 PII） | 登出 `clearLocalSession()` 末尾 `clearAllDrafts()`（草稿可弃，身份隔离不靠它）；键名收口 `draftKeyOf` | draftStorage.spec 3 例 + KFormDialog 接线 |
| `component/framework/KFormDialog.vue` | 草稿 JSON 截断/旧格式 → `restoreDraft` 抛错，**一次坏草稿永久挡住该表单**（catch 缺失） | catch → `discardDraft()` 静默作废 | 走既有 KFormDialog 用例面 |
| `utils/audioFeedback.ts` | 每声 beep `new AudioContext()`：浏览器上限 ~4-6 个，手持扫码枪连续扫描后**中班中途静音** | 模块级共享单例 + suspended 时 resume + 坏了重建 | audioFeedback.spec 5 例（fake ctor 必须 `vi.fn(function(){})`——箭头函数过不了 `new`） |
| `utils/errorHandler.ts` | `ElMessageBox` 取消的 `'cancel'/'close'` rejection 全量进全局错误上报：确认框点「取消」也被当异常上报（噪音淹真异常） | 早退 + `preventDefault()`（26 处确认框共用此机制） | errorHandler.spec +1 |
| `api/axios.ts` | ①401 重试重新过请求拦截器 → **无条件重新生成 Idempotency-Key**，服务端幂等去重被自己的重试打败；②调用方自带的 key 会被覆写 | `if (!config.headers['Idempotency-Key'])` 一行挡两头（覆写 + 重试漂移） | axios.spec +2（自带 key 原样保留；401 重试复用同一把） |
| `api/axios.ts` | `api.download` blob URL 未 `revokeObjectURL`（Safari 泄漏） | setTimeout 1000ms 回收 | 既有 download 用例覆盖 |
| `utils/errorCopy.ts` | 回归门禁 `exports:check` 逮到本轮新代码的**无人引用导出** `UserErrorCopy` interface | 收回为非导出（调用方用 `ReturnType<typeof describeUserError>`） | 门禁本身即测试 |

### 15.5 §14.1 观察项终局：登录页驻留 stale token —— **论证后不清**

§14.1 记录「登录页驻留时残留的 stale token 无人清，无实际危害」，本轮给出**终局判据**：
1. 残留无害：登录页零业务 `/api` 请求（§14.1 实测）；成功登录会**覆写** token；
   带去 `/console` 的任何 stale token 都被 R-35 收口（业务码 401 → 登出 + 带 redirect 回登录页）。
   stale token 的存活区间 = 到下一次业务请求为止，无逃逸路径。
2. **主动清理反而引入新缺陷**：`/login` 是 `meta.guest`，已登录用户在新标签页打开 `/login`
   不会被弹走（authGuard.ts:22-24 设计如此，用户就是要开第二个登录入口）。若登录页挂载即清 token，
   `removeItem('Authorization')` 会触发 **storage 事件（newValue=null）**，R-38 的 `installAuthSync`
   会让其它已登录标签页把它当「对端登出」——**把正常多标签用户踢下线**。
   清理动作与既有正确机制（R-38）直接冲突，收益为零、伤害真实 → **不改**。

### 15.6 全量回归（round 35 门禁，全部未降）

- `pnpm check` 全链（lint:ci → airpower×2 → pom → deadcode → exports → typecheck → test:coverage → perms → auth → errcode → contract → text → ddl → migration → monitoring → canary → docker → drill → build → demo → size）exit 0，整链日志 `.tmp-r35-check.log`（paren 分组后统一重定向）。
- vitest **83 文件 / 894 测试全过**（round 34：77/845 → +6 文件 +49 测试）；
  覆盖率 **91.07 / 82.23 / 82.82 / 90.93**（阈值 50/65/50/75 未动，Stmts/Lines 大幅抬升来自本轮 utils 新面）。
- gzip：**首屏 293.8/450 KB · 总体 719.4/900 KB**（nprogress 净增首屏 ≈2.7KB，门禁内）。
- 后端 `mvn -B -o clean test`：**933 / 0 失败 / 0 错误 / 1 跳过 · BUILD SUCCESS**（后端本轮零改动）。
- 探针课（新增案例法）：**`page.route('**/api/**')` 会连 vite 的 `/src/api/*.ts` 源码模块一起拦**——
  dev 模式下按 `net::ERR_FAILED` 炸掉模块图、整页意外重载，「toast 没出现」全是探针干的；
  拦后端必须用锚定正则 `/^https?:\/\/localhost:3000\/api\/[a-zA-Z]/`。
  另：`cmd /c "a && b > log && echo 0 || echo %ERRORLEVEL%"` 的重定向只绑最后一条命令、
  `%ERRORLEVEL%` 在解析期展开——**整链日志要 paren 分组** `[a && b && c] > log`，否则门禁报告说谎。

### 15.7 证据截图（docs/evidence）
- `66-r35-d3-progress-bar-mid-nav.png`：D3 导航空档期进度条（2px、主题色、spinner 关）
- `66b-r35-d3-after-nav-done.png`：导航落定后条已消失（afterEach→done→remove 全链路）
- `67-r35-d3-reduced-motion-bar-hidden.png`：`prefers-reduced-motion: reduce` 下条不可见、导航照常完成
- `68-r35-d5-init-error-card-tiered.png`：后端宕机冷启动——D5 分档错卡（中文标题/指引 + 灰色原文 + 双出路）
- `68b-r35-d5-retry-recovers-after-backend-up.png`：后端恢复后点「重试」原地自愈
- `69-r35-network-offline-toast.png`：断网时 N 个并发失败合并为**一条**「网络连接失败」toast

### 15.8 设计优化建议清单（本轮验证驱动；✅=已落地）

| 项 | 判定 | 依据/建议 |
| --- | --- | --- |
| D3 顶部进度条 | ✅ 落地 | 见 15.1；复用 nprogress，onError 腿 + minimum:0.1 两处优于 benchmark 原样 |
| D5 错卡分档 | ✅ 落地 | 见 15.2；纯函数 + 两处接线，文案单点维护 |
| 提示唯一出口 | ✅ 落地 | 见 15.3；判据：**任何绕过 notifyBusinessError 的 ElMessage 都是架构回归** |
| stale token on /login | ✅ 终局=不改 | 见 15.5；主动清会与 R-38 多标签同步互相打架 |
| 删 refresh 死链 | **挂账** | 后端无 `/user/refresh`、`refreshTokenValue` 永空；删除行为等价，但被 4 条契约测试钉成「未来挂载点」。等后端真补接口再动 |
| rememberMe 空转 | **挂账（建议删）** | `login.vue:34/:357` 绑定后无任何消费者（登录态持久化不看它）。要么实装（token 存 sessionStorage），要么删控件——**给用户一个不生效的勾选项比没有更糟**。倾向删：后端 JWT 有效期说了算，前端无裁量权 |
| AbortController 透传 | **挂账** | 路由离开后在途请求继续跑完并可能弹 toast（低危：数据进的是已卸载组件）。axios signal 已内建，需 `useTable`/`KFormDialog` 统一接 cancel token，影响面大，建议与 vue-query 的 per-page QueryClient 作用域一并设计 |
| vue-query key 加身份维度 | **已用更简方案替代** | query key 带 tenantId/userId 可彻底隔离，但登出时 `queryClient.clear()` + `clearAllDrafts()` 已切断跨账号数据流；等出现「登出不清」的真实缺口再升级 |
| 换 pinia-plugin-persistedstate | **不换** | benchmark 用它；自研 `stores/persist.ts` 105 行、pick/onRestore 语义被 9 测试钉住、本轮刚修了恢复侧过滤。换库收益≈0，迁移风险>0 |
| `Intl.NumberFormat` 金额格式化 | **挂账（低优）** | `utils/format.ts:29-48` 手写四舍五入字符串拼接；`Intl` 原生可用、千分位/货币符号/舍入交给 ICU。改动会动快照类断言，单独小轮做 |
| dayjs | **不换** | 现有日期用法少且浅；dayjs ~3KB 但每个新依赖都要过 gzip 门禁与 supply-chain 审视，收益不抵成本 |
| axios-retry | **等后端** | 前端幂等基建就绪后，**真**重试的前提是后端 Idempotency-Key 落地生效（现为声明级契约）；先行接入只会放大重复写风险 |
| `api.download` blob 内容守卫 | **挂账（0 调用方）** | 后端以 200+blob 回业务错误时会存下 JSON 当文件；当前 0 生产调用方，接线前先加 |
| errorReportClient 补头 | **挂账** | `errorReportClient.ts:52-59` 裸 fetch 缺 Authorization/tenantId/traceId，上报侧无法回溯身份；但它必须**永不抛错**，改动要保静默语义 |

### 15.9 本轮改动与新增案例法

**改动**：`api/axios.ts`（网络/状态码收口 + 幂等守卫 + download 回收）、`api/errorPolicy.ts`
（网络合成码/429 档/去重键）、`utils/navProgress.ts`+`styles/motion.scss`+
`main.ts`+`package.json`/`pnpm-lock.yaml`（nprogress + @types/nprogress，D3）、`utils/errorCopy.ts`+`Console.vue`+`ErrorBoundary.vue`（D5）、
`utils/storage.ts`/`draftStorage.ts`（新）+ `stores/theme|permission|persist|user` +
`KAppShell.vue`/`authStorage.ts`/`KFormDialog.vue`、`framework/entity|http|validate`、
`router/appRouter.ts`、`utils/audioFeedback|errorHandler` + 对应 14 个测试文件（6 新建）。
`UserErrorCopy` 由 exports:check 当场逮出收回。**后端零改动。**

**新增案例法**：
- **便利数据不配炸掉交互**：主题/折叠/tab 标题这类丢了无所谓的写入必须 safe-wrapped，抛出只配进 console；
- **单行坏数据的爆炸半径 = 一行**：路由注册、菜单渲染按行降级+告警，绝不 throw 全站（appRouter）；
- **写入侧过滤必须镜像到恢复侧**：持久化的字段白名单只写一半 = 没写（persist pick）；
- **可弃数据也要有登出路径**：草稿/缓存这类不带身份的痕迹，是跨账号 PII 的隐形通道；
- **探针先于被测者出错**：本轮两次「产品缺陷」实为 Playwright 路由残留/过宽拦截所致——
  下结论前先做一次「无注入对照」，全新 context 复测才算数；
- **门禁日志必须整链捕获**：`&&` 链上挂重定向只会吞掉最后一环，验证「全链绿」= 看整链日志尾部，
  不是看包装器的 exit code。

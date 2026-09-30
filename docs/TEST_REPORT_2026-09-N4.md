# SPMS 真机测试报告 · 2026-09 · N4（round 13–15）

> 承接 `docs/TEST_REPORT_2026-09-N.md`（round 1–4）、`docs/TEST_REPORT_2026-09-N2.md`（round 5–8）与 `docs/TEST_REPORT_2026-09-N3.md`（round 9–12）。
> 本轮走查**主数据创建链 + 系统治理闭环（角色/菜单授权/权限授权/挂用户/登录取证）**，并修复三个真实缺陷（N-13 菜单授权计数器、N-14 表单默认值、N-2 遗留 lint）。
> 本模型无图像输入：所有判定来自 DOM 量测（`getBoundingClientRect` / `getComputedStyle` / `__vueParentComponent` 遍历）与真实网络响应，截图仅供人复核。

## 0. 判定表
| 编号 | 现象 | 判定 | 处置 | 提交 |
| --- | --- | --- | --- | --- |
| **N-13** | 角色·菜单授权「已选 N 项」计数器钉死、清空按钮不消失（树勾选/保存正确） | 🐞 **真缺陷（P1）** | 已修 `checkedIds()` 去副作用 + 守卫 | 见 §2 |
| **N-14** | 新增合同/物料时 `@KForm({ defaultValue })` 全部落空（合同类型/起止时间空 → 保存被 requiredNumber 拦截） | 🐞 **真缺陷（P1）** | 已修 `resetForm()` 回退元数据默认值 + 守卫 | 见 §3 |
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

## 8. 回归与提交
- 后端 `mvn -B -o clean test`：**927 tests, 0 failures, 0 errors, 1 skipped, BUILD SUCCESS**（与基线一致）。
- 前端守卫 vitest：defaultCheckedKeysGuard 3 + formDefaultValueGuard 3 + dialogWiring 6 = **12 passed**。
- `pnpm check` 全门（lint→airpower→typecheck→coverage→perms/auth/errcode/contract/text/ddl/migration/monitoring/canary/docker/drill→build→demo→size）：
  **22 步全绿**。其中首屏发现 N-2 遗留 lint 与 N-3 遗留 unused-export 并顺手修复；产物体积（gzip）首屏 290.8 KB / 总量 715.4 KB，低于闸门 450 / 900 KB。
- **重启前后端真机复验 PASS**：admin 登录 → 合同列表 row1 `CON2026100001 | 其他合同 | 真机测试合同 R16 | 未生效 | 2026-10-01 | 2027-10-01 剩余 365 天`（生效 en / 终止 dis 正确）；角色·菜单授权弹窗 `已选 34 项` → 清空 → `已选 0 项` + 清空按钮隐藏（N-13 修复持久）。0 console error / 0 pageerror。
- 证据截图：docs/evidence/15–33（本提交含 15–33）。

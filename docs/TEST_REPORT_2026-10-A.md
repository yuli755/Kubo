# SPMS 测试报告 2026-10-A（Round 47：真机全链路测试 + 六项缺陷修复 + 证据）

> 上一轮：TEST_REPORT_2026-09-N5.md（R46，commit `56a2769c`）。
> 本轮输入（用户原话，m01638）：「继续。模拟各种用户角色真机（浏览器）上跑通 SPMS 全链路：重启前后端 → 多角色登录 → 逐模块走完核心业务闭环（采购→入库→库存→销售→出库、主数据、系统治理工程化）测试，每步用 DOM 量测判定渲染与交互是否符合预期，发现缺陷就定位根因并自主修复，最后交付一份带证据的测试报告与 dev 提交。对于需要修复的地方，有没有现成方案可以复用？怎么写更简洁、更易维护、可扩展？还有哪些地方或方面需要补充优化修复设计的建议？」
> 方法：Playwright（`browser_evaluate`）真机点击 + **页内 DOM 探针**（XHR 拦截 `window.__reqs`、`queryClient.invalidateQueries` 包装 `window.__invalidations`、组件树遍历 `#app.__vue_app__._instance` 递归 `subTree`）+ MySQL 直查三方对齐。每条结论 = DOM 量测 ∧ DB 复核（∧ 截图）。

## 0. 环境与可复现性

- 前端 `kubo-web@1.0.0`：Vite dev **3000**（`/api`、`/oauth2` → 8080）。**5173 是无关的 OpsBrain 项目，勿测**。
- 后端 `SPMS-Server`：Spring Boot 3.4.4 / Java 17，profile `local`，Flyway V1..V16。
- 本机只有 MySQL 3306 跨轮存活；redis/backend/前端每轮需重启（`run-backend.cmd`/`run-frontend.cmd` 启动器，后端 ~90–105 s）。
- 账号：admin 凌小云 `17666666666`；无角色用户 张三 `13888888888`；WMS_OPERATOR（原密码未知，见 §5-6；Round 48 已补种子账号，见 §7.4）。

## 1. 业务闭环（全部真机走通，DOM + DB 双证）

| 闭环 | 单据链 | 量测证据 |
|---|---|---|
| 采购→到货→自动入库 | `PC202610030001` 审核→到货 → 自动生成采购入库 `IN202610030002` | 截图 82/83；DOM：审核状态位翻转、入库列表出现新单 |
| 销售→自动出库→原子扣库存 | `SL202610030001`（¥59,998.00）→ 自动销售出库 `OUT202610030001` 完成，库存原子扣减（TRACE_ID=384bf969） | 截图 84；DB 库存行前后差 = 出库数量 |
| 移库→移动入库 | `MV202610030001` 5 台@东部大仓 100→95 @东部1仓；`MV202610030002` → `IN202610030004` 已完成 | 截图 85 |
| 主数据 | 物料 4、合同 1、仓库 7（树形）、库位 3、计量单位 3 | 列表 DOM 行数 = DB count |
| 系统治理 | 权限 309、菜单 34、配置 13、编号规则 20、操作日志 20、数据资源 17、行级策略 20、列级脱敏 4（`2***0`/`2***9` 掩码渲染；服务端 `138****3333`、`te***@hamm.cn`） | 各列表页 DOM 抓取 |
| 多角色 | admin 侧栏 27 菜单；张三（无角色）→ 404 | 截图 86（缺陷候选 §5-2） |
| V16 迁移（真库） | flyway `version=16, success=1`；`data_policy.resource_id`/`data_field_policy.resource_id` = `bigint unsigned`；FK `fk_data_policy_resource`、`fk_data_field_policy_resource` 存在 | information_schema 直查 |

## 2. 六项缺陷修复（根因 → 复用 → 实现论证 → 量测 → 建议）

### FIX 1 — `-parameters` 编译标志静默失效（后端 500）
- **症状**：后端启动正常，但带 `@RequestParam`/`@PathVariable` 无显式名的接口 500：`IllegalArgumentException: Name for argument of type [java.lang.String] not specified, and parameter name information not found via reflection. Ensure that the compiler uses the '-parameters' flag.`
- **根因**：`SPMS-Server/pom.xml` 有 `<parameters>true</parameters>`，但仓内 Maven 3.8.3（`.mvn/wrapper`）super-POM 把 maven-compiler-plugin 解析到 **3.1**，`<parameters>` 是 3.6.0 才引入的选项——3.1 **静默忽略**它（不报错、不警告）。B0 退役 airpower 时丢失的三处配置之一，且是唯一运行期才暴露的。
- **复用方案**：不逐个给 8 个接口补 `@RequestParam("x")` 显式名（治标且易再犯），而是钉死插件版本 `<version>3.13.0</version>`（= Spring Boot 3.4.4 官方 parent 管理的版本，单一事实源）。
- **验证**：`javap -v -p` 目标 class：修复前 `MethodParameters` 属性 0 处，修复后存在；enforce-readiness / deny-logs 接口 200。
- **进一步建议**：用 **maven-enforcer `RequirePluginVersions`**（官方现成规则）把「所有插件必须显式版本」变成构建期硬门禁，或并入 `infra/scripts/check-*` 系列——否则下一个升级还会踩同一坑。

### FIX 2 — KDetailTable `entity` prop 类型告警（Vue 运行时告警刷屏）
- **症状**：任何单据详情页控制台刷 `Invalid prop: type check failed for prop "entity". Expected Object, got Function.`
- **根因**：`KDetailTable.vue` 把 `entity` 当普通对象 prop 声明，但调用方传的是**类构造器**（`InputDetailEntity` 等，运行时是 Function）。系统性问题：全仓 ~8 个视图都往 props 传构造器。
- **复用方案**：Vue 3.5 官方推荐的 props 泛型写法（本仓已在用），把类型声明改成诚实的构造器签名即可：`defineProps<{ entity: abstract new (...args: never[]) => E; ... }>()`。构造器类型编译为运行时 `type: null`（放弃浅检查而不是撒谎说是 Object）。
- **实现论证**：改 1 个组件的 1 行类型 = 8 个调用方全部消音；调用方零改动；类型系统继续拿到 `E` 做行类型推断。
- **验证**：详情页控制台 0 该类告警；截图 81。
- **进一步建议**：给「props 传类构造器」立个约定名（如 `entityCtor`）或包一层 `InstanceType` 工厂；可在 eslint 层禁止把构造函数标成 `PropType<object>`。

### FIX 3 — 作业后详情陈旧（`refetch()` + staleTime 陷阱）
- **症状**：明细「作业」成功（接口 200、toast 正常）后再开详情，`finishQuantity` 还是旧值——`queryClient` `staleTime: 30_000` 下 `refetch()` 命中缓存直接返回。
- **根因**：`useDetail.ts` 的 `reload()` 用 `refetch()`；列表侧 `useTable.reload`（`useTable.ts:293`）早已改用 `invalidateQueries` 前缀失效——详情侧漏改。
- **复用方案**：照抄仓内已验证的 `useTable.reload` 惯例，复用单例 `queryClient`（`src/query/queryClient.ts`，staleTime 30_000 不动）。
- **实现**：
  ```ts
  async function reload(extra?: string[][]): Promise<void> {
    const keys = [option.queryKey, ...(extra ?? [])]
    await Promise.all(keys.map(key => queryClient.invalidateQueries({ queryKey: key })))
  }
  ```
  明细 key 是 `[...queryKey, 'detail', id]`，前缀失效天然覆盖列表+全部详情；可选 `extra` 参数是**扩展缝**——跨域联动方（入库→库存/采购）按需传自己的前缀，数据层不 import 任何业务域。
- **验证**：入库 `detail.vue:130` `CROSS_DOMAIN_KEYS=[['wms:inventory'],['channel:purchase']]`；DOM spy 实测失效序列 `["wms:input","wms:inventory","channel:purchase",...]`；截图 85。
- **进一步建议**：约定「查询驱动列表禁用 `refetch()`」，可上一条 eslint/no-restricted-syntax 规则防回退。

### FIX 4 — 列设置弹层全仓失效（EP ElOnlyChild + Fragment 根）
- **症状**：所有 KTable 右上角「列设置」齿轮点击无反应，控制台 `Runtime directive used on component with non-element root node. The directives will not function as intended.`
- **根因**：`KTable.vue` 的 `#reference` 插槽根是 Fragment（多根），element-plus `ElOnlyChild` 对非元素根**丢失指令**（popover 定位/触发指令），弹层永远不出现。
- **复用方案**：EP 文档对 `#reference` 的契约就是"单个元素"——包一个 `<span class="k-table__col-settings-reference">` 元素根即完全合规，不 patch、不升级、不加依赖。
- **验证**：修复后齿轮点击弹出含列勾选框的 popper，勾选即隐藏列（DOM：可见 `.el-popper` + 表头列数变化）；截图 87；控制台指令警告 0。
- **进一步建议**：此坑上游可报告；仓内可加一个组件单测断言 `#reference` 渲染根为元素节点（负样本探针法同 R46 §2）。

### FIX 5 — 列表从不刷新（`@saved` 断线 + DialogUtil promise 被丢弃）
- **症状**：入库/出库/移库列表、采购/销售列表在详情弹窗里完成作业后，**列表行状态纹丝不动**，必须手动刷新。
- **根因**：两条断线——① wms 三域 `list.vue` 只把 `@saved` 绑在 `<*Editor>`（新建）上，`<*Detail>`（详情内作业）的 `saved` 没人听；② channel 两域用 `DialogUtil.show(XDetail, row)` 打开详情，返回的 promise 被**丢弃**，详情内部再正确也传不到列表层。
- **复用方案**：零新 API——① 绑**已存在**的 `saved` 发射缝：`<*Detail ... @saved="onSaved">`（input/list.vue:147、output/list.vue:144、move/list.vue:151）；② 复用 DialogUtil 的 promise 契约（`src/framework/feedback.ts:187-211`：confirm/cancel/关闭都必然 settle，`.finally` 安全）：`void DialogUtil.show(XDetail, row).finally(() => table.reload())`（purchase/list.vue:83、sale/list.vue:76）。与既有 Editor 路径完全对称。
- **扩展性论证**：以后任何新域只要详情组件 emit `saved`（或走 DialogUtil），列表刷新**自动成立**；这条约定写进了组件契约而不是散落在页面里。
- **验证（本轮全四域真机点击 + spy）**：
  - INPUT `IN202609150007`：全部入库(20)→西部1仓 → 失效序列 `["wms:input","wms:inventory","channel:purchase","wms:input"]`，请求 `addFinish→getPage→getDetail×2`，行 入库中→已完成；DB status=4、明细 20/20。截图 88。
  - OUTPUT `OUT202609150011`：确认出库 60 → 失效 `["wms:output","wms:inventory","channel:sale",...]`，行→已完成；DB status=4、库存 95→35。截图 89。
  - MOVE `MV202609280001`：添加完成 2 → 失效 `["wms:move","wms:inventory","wms:input","wms:output",...]`，行→已完成；DB status=4、2/2。截图 90。
  - PURCHASE `PC202609300003`：到货登记 1 →（详情层）失效 `["channel:purchase","wms:input"]`；**关闭采购详情时**列表层 `.finally(table.reload())` 触发追加 `getPage` + 失效 `["channel:purchase"]`，行 采购中→已完成；DB status=5、1/1。截图 91。
- **进一步建议**：中期可让 `useTable` 暴露 `onDomainRefresh(key)`，把「弹窗关闭即 reload」下沉为框架行为；短期以 `saved` 约定 + code review 检查表覆盖。

### FIX 6 — KDialog 提前关窗吞掉异步提交结果（无报错的静默失效，最隐蔽）
- **症状**：入库「确认入库」接口成功、toast 弹出、弹窗关闭，但列表**不刷新**、无任何控制台报错。
- **根因**：`KDialog.confirm()` 同步 `visible=false` → 外层 `v-if` **立即卸载**内容组件 → 内容里 `await 提交` 之后才执行的 `emit('confirmed')` 命中 Vue 的 emit 守卫 `if (instance.isUnmounted) return`（`runtime-core.esm-bundler.js:4442`）被**静默丢弃** → 失效链从未触发。
- **复用方案**：不重构两套弹窗接线（emit-only 型 vs props-call 型，见 KDialog 头注 6–49），而是在共享组件上**加一个向后兼容的退出开关**：`closeOnConfirm?: boolean` 默认 `true`（所有既有调用方行为不变），异步提交的 emit-only 弹窗传 `false`，由内容在提交成功后自己 `emit('update:modelValue', false)` 关窗。
- **实现**：`confirm()` → `if (props.disableConfirm) return; if (props.closeOnConfirm) visible.value = false; emit('confirm')`；三个异步编辑器（input/output/purchase 的 `addFinishEditor.vue`）传 `:close-on-confirm="false"` 并把 emit 顺序固化为「先 `confirmed` 再关窗」。
- **附带收益**：提交失败时弹窗保持打开可重试（旧行为是失败也关窗，用户丢失输入）。
- **验证**：FIX 5 四域序列全部以 `confirmed→失效→关窗→列表翻页` 的正确顺序发生（见上）；`editorOpen=false` 均在 toast 之后。
- **进一步建议**：更彻底但破坏性的方案是 KDialog 对「内容为 emit-only 异步型」**永不自动关窗**；务实替代是静态守卫——检测「内容含 `await` 后 emit 且未传 `close-on-confirm=false`」并 lint 报错。JSDoc 已把契约写死在属性上。

## 3. 收尾门禁（本轮实跑）

- `pnpm exec vue-tsc --noEmit -p tsconfig.json` → **EXIT 0**。
- `pnpm exec eslint <18 个改动文件>` → **0 errors**（唯一 warning 是 `infra/scripts/*.mjs` 本就在 eslint ignore 清单，非代码问题）。
- `vitest run src/config/__tests__/schemaContract.spec.ts` → **7/7 绿**（V16 追加已同步进清单断言——设计要求新增迁移必须改这里一次）；此前全量 `src/config/__tests__/` 27/27。
- 后端 V16 已在真库应用并复核（§1 末行）。

## 4. 改动清单

- 后端：`SPMS-Server/pom.xml`（compiler 插件钉 3.13.0）、新增 `V16__align_policy_resource_fk_signedness.sql`（逐字复用 V12 的 `kubo_align_resource_id` 存储过程，幂等：MODIFY 仅在 `COLUMN_TYPE <> 'bigint unsigned'` 时执行、FK 固定名 `fk_<t>_resource` 判存在、补建前清悬挂引用；修复 §1-V16 行的静默参照完整性失效——P1-3 收口）。
- 工程化：`infra/scripts/check-ddl-entity-drift.mjs` 增 ④实体 columnDefinition vs 迁移整型族/UNSIGNED 一致性（失败）、⑤`--live-detail` information_schema 实库比对（失败）——正是 V16 这类「列名全对、FK 静默缺失」的探测器；`schemaContract.spec.ts` V16 入列。
- 前端：`KDetailTable.vue`、`KDialog.vue`、`KTable.vue`、`useDetail.ts`、4 个 `detail.vue`、5 个 `list.vue`、3 个 `addFinishEditor.vue`（详见 §2）。
- 本报告 + 截图 81–91（`docs/evidence/`）。

## 5. 非代码发现（本轮实测，建议立案）

| # | 发现 | 证据 | 级别/建议 |
|---|---|---|---|
| 1 | 「新增用户」弹窗**没有密码字段**，且 `UserService` 无管理员重置密码接口 → 新建用户**永远无法登录** | 弹窗 DOM 字段清单 + 后端方法扫描 | **P2 产品缺口**：补初始密码字段或「邀请制」（首登邮件设密）**→ 已修，见 §7.1** |
| 2 | 无角色用户登录后落到**通用 404**，而非「无授权」引导页 | 张三登录截图 86 | P3 UX：路由守卫区分「无权限」与「不存在」**→ 已修，见 §7.2** |
| 3 | 数据权限执行处于观察态：0 enforced 资源、0 deny 事件；导出缺口 4（asset.material ×2） | 治理面板 DOM | 按计划灰度 ENFORCE；先补导出缺口（本轮未动，维持原议） |
| 4 | 请求日志**后端从未实现**：`/api/log/getPage|getDetail` 404，前端是活桩（`src/view/console/system/log/list.vue:16`） | XHR 404 | 实现或下架菜单，别让菜单点出 404 **→ 口径更正 + 实测通过，见 §7.3**（该页已换数据源为操作审计；请求日志后端仍未实现） |
| 5 | 登录强制「隐私政策」确认弹窗 | 登录流程截图链 | 合规权衡，产品拍板（本轮未动，维持原议） |
| 6 | WMS_OPERATOR 角色存在但无已知密码账号 → 仓管角色回归未覆盖 | 账号盘点 | 测试环境补种子账号 **→ 已补并回归，见 §7.4** |
| 7 | **自动化测试陷阱（非产品缺陷）**：element-plus `TableColumnRenderer` 用 `slots.default({row:{},$index:-1})` 探测子列（`table-column/…lang.mjs:97-103`），操作列插槽因此在 `.hidden-columns`（`visibility:hidden; position:absolute`）里多渲染一套绑定**空行**的按钮；`offsetParent!==null` 过滤会先命中它，点击产生「空 detail」幽灵 | 源码定位 + 点击复现 | 自动化侧统一用 `!b.closest('.hidden-columns') && getComputedStyle(b).visibility!=='hidden'`；可选产品侧防御：行操作 handler 开头 `if (!row?.id) return` **→ 框架侧 $index 守卫已落地，见 §7.5** |

## 6. 结论

三条业务闭环 + 主数据 + 系统治理 + 多角色在真机浏览器全程走通；六项缺陷全部**根因级修复**且每项都复用了仓内/上游现成方案（parent 版本管理、props 泛型、queryClient 失效惯例、EP `#reference` 契约、既有 `saved` 缝与 DialogUtil promise、V12 存储过程），无新增依赖；每项修复都是**加法式、默认行为不变**的形态，新场景按约定自动获得同等能力。全部改动过 vue-tsc/eslint/契约测试门禁，已在 `dev` 分支提交（未推送——本地领先 `github/dev` 较多，推送前先打招呼）。

## 7. 追加轮（Round 48）：§5 立案项修复 + 「无滚轮/丢内容/缺底栏」缺陷根治

用户复测反馈（原话）：「修复报告5存在的问题，并修复页面无滚轮滑动存在页面展示丢失的问题？应该是缺少底部栏？」。本轮全部真机（:3001，1280×720/1280×500）验证，截图 92–98。

### 7.0 「无滚轮 / 内容丢失 / 缺底栏」——根因不在外壳，在错误页的 fixed 铺满

- **量测排除法**：`/console/wms/input/list` 真滚轮下 `.k-shell__content` scrollTop 0→500，页脚 rect `top=685.2 h=34.8 bottom=720` 可见 —— 外壳与页脚本来就正确；全仓 grep `wheel|preventDefault` 无任何滚轮劫持。症状只在**壳内 404**（catch-all → `error.vue`）复现。
- **根因**（`src/view/error.vue`）：旧样式 `.error-page { position: fixed; inset: 0; justify-content: center }` 在壳内挂载时一石三鸟 —— ① fixed 层盖住侧栏/顶栏/`.k-shell__footer`（「缺底栏」）；② fixed 层不是滚动容器、也不给外层滚动区贡献 scrollHeight（「滚轮没反应」）；③ flex `justify-content:center` 内容超高时**两端都裁**，「返回首页」恰在被裁端（「内容丢失」）。
- **修复（纯 CSS + 一个计算属性，不动外壳）**：改普通文档流；居中用 `margin: auto`（溢出时归零、从顶部可滚，永不裁剪）；仅独立页（`/403`，判别式 `route.name === '无权限'`）挂 `--standalone` 类自铺一屏。
- **量测**（截图 92）：1280×500 壳内 404，真滚轮 `scrollTop 0→38.4`、`footerTop=465` 可见、链接保持InView；1280×720 下 `.error-page` rect 在文档流内、页脚 `bottom=720`、链接 hit-test `(935,439)` 命中自身，点击后落回 `/console/index`；独立 `/403` 仍 `top=0 bottom=720` 满屏。

### 7.1 §5-1 初始密码（P2 产品缺口 → 已修）

- **根因两层**：① `UserEntity.password` 从无 `@KForm` 声明，表单根本不渲染；② 补声明后发现 `KFormDialog.fieldType()` 虽有 `'password'` 分支，**模板却只映射 textarea/text** —— password 会静默降级成明文输入框。
- **复用**：框架既有 `IFormField.password` 标志 + `fieldType()` 优先级 + `extraRules`/`beforeSubmit` 缝；后端 `UserService`（`applyFreshPassword`/`normalizeSubmittedPassword`/`isPreHashedPair`）**零改动**；页面零新组件、API 零新面。
- **简洁性论证**：「新增必填 / 编辑留空=不修改」是**页面语义**，放 `list.vue` 的 `passwordRules`（computed extraRules）+ `beforeSubmit` 删空键；实体只留长度 6~32。服务端 `WRITE_ONLY` 使详情永不回传密码 → 表单永远从空开始 → 「空=未改」天然成立。
- **量测**：新增留空 → 表单错误「请设置初始密码」、弹窗不关（截图 94）；填 `Wms#2026test` + 角色 19 → 「新增成功」，DB `user.id=74` password=40 位 hex、salt 4 位（截图 95）；编辑弹窗 `input[type=password].value === ""`，留空改名保存 → DB hash **前后一致** `ecca171c…6533a`/`FBdL`（截图 97）。
- 附带修复 `KFormDialog.vue` 模板：`type` 三元补 password 分支 + `show-password` 小眼睛（`type="password"` 实测生效）。

### 7.2 §5-2 无角色用户落 404（P3 UX → 已修）

- **根因**：`Console.getMenuList()` 里 `firstNavigableMenuPath(menuList)` 对无菜单用户返回默认 `/console/index` → 重定向后又撞 catch-all → 壳内 404，语义错（是「无权」不是「不存在」）。
- **复用**：既有 `/403` 路由 + 既有 fallback 形参 —— `firstNavigableMenuPath(menuList, '')`，空则 `replace('/403')`。无新页面、无新守卫。
- **量测**（截图 93）：张三 `13888888888` 登录后 `location.href = /403`、标题「无权限 - Kubo」、`error-page--standalone` 满屏、`hasShell=false`。

### 7.3 §5-4 日志页（口径更正 + 实测通过）

原报告「前端是活桩、点出 404」已过时：该页早已**换数据源不新造后端**（复用 `useOperationLogReport` + `GET /dataResource/operation-logs`，删除 LogService/LogEntity/useLog 三件死代码；口径：opt-in 审计、前端筛选、200 条硬上限明示）。「请求日志后端从未实现」仍成立 —— 现在页面展示的是**操作审计**，不是请求日志。实测（截图 98）：菜单 → `/console/system/log/list`，20 行真实记录，表头 `# 时间 动作 资源 目标 操作者 租户 快照 操作`，首行即本轮 7.1 的用户修改（`personnel.user UserEntity#74`，含变更快照「查看变更」）。

### 7.4 §5-6 WMS 角色回归（已补种子账号，顺带端到端验证 7.1）

新建 `仓储测试员-1790583398729`（id=74，手机 13900001234，邮箱 wms.test19@kubo.local，初始密码 `Wms#2026test`，角色 19 `WMS_OPERATOR`，DB `user_role_list 74→19`）。**用新密码真机登录** → 落 `/console/wms/move/list`，侧栏仅仓储管理 4 菜单（入库/出库/移库/库存），页脚正常（截图 96）。登录→建号→授权→回归一条链同时验证了 7.1 与 7.2 的正常分支。

### 7.5 §5-7 自动化陷阱的产品侧防御（可选项已落地）

`KTable.vue` 与 `KDetailTable.vue` 操作列插槽改取 `$index`，每个按钮点击包 `$index >= 0 &&` —— element-plus 探测渲染（`$index:-1`，stub 行 `{}`）落在 `.hidden-columns`，真人点不到，但自动化点击会命中；一处框架守卫胜过每页写 `if (!row?.id) return`。

### 7.6 本轮门禁与改动清单

- 门禁：eslint 8 文件 0 错；`vue-tsc --noEmit` EXIT 0；vitest `personnelDomain + appRouter + routes` 32/32、`frameworkBatch2 + formDefaultValueGuard + frameworkComponents` 23/23（KFormDialog 相关守卫全绿）。
- 改动：`src/view/error.vue`（fixed→文档流 + standalone 态）、`src/component/Console.vue`（FIX B fallback）、`src/model/personnel/user/UserEntity.ts`（password 元数据）、`src/view/console/personnel/user/list.vue`（passwordRules + beforeSubmit 语义）、`src/component/framework/KFormDialog.vue`（password 控件渲染）、`src/component/framework/KTable.vue` / `KDetailTable.vue`（$index 守卫）、`src/config/__tests__/personnelDomain.spec.ts`（password 入列断言）。
- 截图 92–98（`docs/evidence/`）。§5-3（数据权限灰度）与 §5-5（隐私弹窗）维持原议：前者按计划 ENFORCE，后者待产品拍板，本轮不动代码。

## 8. 追加轮（Round 49）：菜单图标全雷同 + 宽屏右侧大片空白 —— 根因与修复

用户复测反馈（原话）：「菜单项的图标应该是不同才对？还有页面布局结构存在大量问题？比如：为啥页面右边存在大量空白处？除了面包屑、导航栏页面还有哪些部分组成？」。本轮真机（:3001）量测验证，截图 99–101。

### 8.1 图标雷同 —— 根因是数据全空，不是组件缺失

- **量测**：1920×1080 下侧栏渲染 41 个 `el-icon`，SVG path 去重后只有 **2 种**（MenuIcon 兜底 + 子菜单箭头）。查 `menu` 表：**34 行全部 `icon=''`**。
- **链路本身是通的**：`menu.icon` → `getMyMenuList` → `MenuItem.icon` → `KAppMenuNode.vue` 的 `resolveMenuIcon(item.icon)`，菜单管理页 `MenuEntity.icon` 也早有 `@KForm` 可编辑。缺的只是**数据**（全空）与少量 **ICON_MAP 注册**（`shell/icons.ts` 是白名单，未注册名静默回落 Menu 图标）。
- **修复（数据驱动，零新组件）**：① 新增 Flyway `V17__menu_icons_seed.sql` —— 按 name 幂等 UPDATE，34 个菜单各给一个语义不同的 element-plus 图标（工作台=DataBoard、入库=Download、出库=Upload、供应商=School、采购价=PriceTag、列级脱敏=Hide…全表见迁移文件）；② `icons.ts` 补注册 Goods/Hide/Management/PriceTag/School 五个新名（与 V17 必须同版本发布，否则静默兜底）；③ `MenuService.initMenu()` 同步补 `setIcon`，让全新环境首启即带图标；④ demo 夹具 `src/demo/fixtures/menu.ts` 对齐同一套图标名。
- **契约防回归**：`menuContract.spec.ts` 新增 4 条断言 —— 后端种子 34 项图标名全部已注册进 ICON_MAP、图标互不重复、V17 与种子双向一致、demo 图标已注册。以后任何人加菜单忘了注册图标，测试直接红。
- **复测**（截图 99）：展开全部一级/二级菜单，34 个菜单项 → **34 种不同 SVG**，重复组 0；「菜单管理」用 Menu 图标属语义选择非兜底。

### 8.2 右侧大片空白 —— 根因是 1600px 内容上限在宽屏居中

- **量测**：`.k-content__inner { max-width:1600px; margin:0 auto }`（`KAppShell.vue`）。1920 屏左右各留 ~52px 无感；**2560 屏右侧空白 377px**（左侧对称也 377px）——即用户看到的「右边大量空白」。
- **修复（令牌级，不碰 22 个页面）**：`tokens.scss` 新增 `--kubo-content-max-width: 2200px`，`KAppShell.vue` 改读该变量。1920 及以下行为完全不变（内容本来 <2200 就撑满）；2560 下内容区 1600→2200，右侧空白 377→72px。
- **复测**（截图 100/101）：1366/1920/2560 三档 `.k-content__inner` 宽 1110/1663/2200，`scrollWidth==clientWidth` 全档无横向溢出。

### 8.3 页面解剖图（用户问「除了面包屑导航栏还有什么」）

外壳 `KAppShell.vue`（所有 /console 页共用）：**侧栏**（品牌区 logo+标题、el-menu 菜单树、折叠钮）→ **顶栏**（折叠钮、面包屑（带图标）、⌘K 全局搜索触发器、离线/演示标记、租户切换、主题切换、用户卡）→ **内容区**（滚动容器 `.k-shell__content` + 定宽内芯 `.k-content__inner` + 路由过渡）→ **页脚**（34px：当前页标题 + 产品名）。全局面板：⌘K 命令面板、全局加载遮罩。窄屏（<1024）侧栏变抽屉。
内容区内典型列表页（`list.vue` 惯例）：**搜索表单 → 工具条（新增/导入/导出）→ 表格（含行操作列）→ 分页**，可选**左侧工具栏 `.k-page__aside`**（用户管理=部门树、库存查询=维度筛选），弹窗/抽屉按需挂载。登录/403/404 为壳外独立页。
**DOM 实证**（2560，计量单位/用户/工作台三页）：侧栏、品牌、菜单、折叠钮、顶栏、面包屑、⌘K、租户、主题、用户卡、页脚（文本含当前页名）逐项为 true；列表页搜索表单/工具条/表格/分页逐项为 true；工作台无这四件属预期（仪表盘页）。

### 8.4 布局遗留优化项（本轮量测发现，未动代码）

| # | 发现 | 证据 | 建议 |
|---|---|---|---|
| 1 | `.k-page` 布局样式在 **22+ 个 list.vue 的 scoped style 里各自重复**一份 | grep 计数 | 收敛为全局/共享样式或 `KPage` 容器组件，一处改动全站生效 |
| 2 | 用户管理页 `.k-page__aside` 高 190 vs 主卡 641，侧栏卡未拉伸、下方悬空 | 1920 DOM | aside 改 `align-self:stretch` 或树卡 `height:100%` |
| 3 | 工作台 `.hero-workflow` 行末留白 662px（wrap 容器内两子块合计 919/1591） | 1920 DOM | 流程块改 grid 自适应列或 flex:1 |

### 8.5 本轮门禁与改动清单

- 门禁：eslint 改动 4 文件 0 错；`vue-tsc --noEmit` EXIT 0；vitest `menuContract + menuNavigation + consoleMount` 22/22；后端 `mvn compile` EXIT 0；Flyway 实跑 `flyway_schema_history` 17|menu icons seed|success=1，`menu.icon<>''` 计数 34。
- 改动：`V17__menu_icons_seed.sql`（新）、`MenuService.java`（listPage 图标筛选 + initMenu 种子）、`src/component/shell/icons.ts`（+5 图标注册）、`src/demo/fixtures/menu.ts`（对齐）、`src/styles/tokens.scss` + `src/component/KAppShell.vue`（内容宽度令牌 2200px）、`src/config/__tests__/menuContract.spec.ts`（图标三方一致契约 ×4）。
- 截图 99–101（`docs/evidence/`）。

## 9. 追加轮（Round 50）：多设备响应式审计 —— 「过度放大/缩小 + 内容丢失」专项

用户复测反馈（原话）：「标准的页面一般哪些部分组合成一个完整的前端页面？你再仔细看看当前修复后的页面情况……我不希望你把页面样式过度写死写出固定值。因为一旦用户使用不同设备尺寸的 web 端或 app 端，都会导致页面出现过度放大或放小尺寸的效果，再加上因过度放大页面展示而不能滚动页面，导致用户无法看到丢失页面部分内容……从用户的角度，不断挖掘修复系统隐藏的前端各页面问题。」

### 9.0 先自证：§8 的修复不是「写死新固定值」

`--kubo-content-max-width: 2200px` 是**上限**不是定宽：内容区照常随视口流式伸缩，实测 768/1366/1920/2560 内容宽 744/1099/1663/2200（<2200 时全由容器决定），任何设备不会因此放大或缩小。但本轮按用户要求把全站其它「写死像素」翻了一遍，真机跑 **375/768/1024/1366/1920/2560 六档视口矩阵**（工作台/计量单位/用户/库存/入库单五类页），挖出 5 处真实的设备适配缺陷。

### 9.1 双指缩放被锁死（P1 无障碍硬伤）

- **根因**：`index.html` viewport 写 `minimum-scale=1, maximum-scale=1, user-scalable=no` —— 页面被永久锁在 1 倍。小屏上内容偏小时，用户唯一的自救手段（捏合放大）也被禁用，这就是「放小尺寸 → 看不清 → 无法操作」链条的第一环。
- **修复**：改 `width=device-width,initial-scale=1,viewport-fit=cover`（去缩放锁 + 刘海屏安全区）。桌面端行为不变。

### 9.2 弹窗固定 600~900px 在小屏溢出且不可滚动（P1 内容彻底丢失）

- **量测**：全站 **29 处**弹窗声明固定宽 600~900px。375 屏实测：新增弹窗 560px 溢出右缘，且遮罩层横向**不可滚**（scrollWidth−clientWidth=0）——超出屏幕的表单与确定按钮彻底不可达，完全命中用户描述的「放大后不能滚动导致内容丢失」。 safezone 768 屏实测 900px 弹窗同样溢出。
- **修复（全局一条兜底，不逐页改 29 个文件）**：`styles/index.scss` 加 `.el-dialog { max-width: calc(100vw - 24px) }`、`.el-message-box { max-width: calc(100vw - 32px) }`。小屏自动收缩、大屏不干涉（2560 实测 900px 弹窗仍按业务声明=900px）。
- **复测**（截图 102/104）：375 弹窗 351px 完整在屏、确定按钮可见；登录同意框 343px 完整在屏；768 弹窗 744px 完整在屏。1024×600 矮视口打开 640px 高表单弹窗：EP 自身遮罩纵向可滚，确定按钮滚后可达。

### 9.3 顶栏 375 屏右簇被裁 28px，用户卡不可达（P2）

- **量测**：375 屏 topbar scrollWidth−clientWidth=28~30，用户卡右侧被裁出屏（搜索框 99px + 租户选择器 190px 固定宽是主因）。
- **修复**：左簇允许收缩裁剪（面包屑先让位），右簇不再被裁；≤640 手机档搜索框退化为纯图标（点击仍开命令面板）、租户选择器 190→96px（EP 自带 ellipsis，点开下拉仍可看全名并切换）、右簇间距收紧。
- **复测**：375 屏 topbar 溢出 **0**、末元素右缘 363≤375；1920 租户选择器仍 190px、溢出 0。

### 9.4 双栏页 240px 固定侧栏把主表挤成 55px 细条（P1 功能不可用）

- **量测**：用户管理/库存查询的 `.k-page` 为「240px 侧栏 + 主表」并排；375 屏主卡被挤到 **55px**，表格、表单、分页全部不可用。
- **修复**：≤767 改上下堆叠（`flex-direction: column` + `align-items: stretch` —— 桌面态的 flex-start 在列方向会把卡片收缩成内容宽 838px，实测曾把内容区撑出 485px 溢出，已一并修正）。
- **复测**（截图 103）：375 屏两页侧栏/主卡均 341px 全宽、主卡位于侧栏之下、内容区溢出 0。

### 9.5 移动端 100vh 高估可见高度，页脚藏进 URL 栏（P3）

- **根因**：手机浏览器地址栏收起/展开会改变可视视口，`100vh` 按「地址栏收起时」计高 —— 地址栏展开时页脚被顶到 URL 栏底下，看不见也滚不到（又一类「内容丢失」）。
- **修复**：`.k-shell` 用 `height: 100dvh`（动态视口单位，旧浏览器自动落回 100vh）。

### 9.6 视口矩阵复扫（修复后全档）

375/768/1024/1366/1920/2560 × 工作台/计量单位/用户：文档与内容区横向溢出**全 0**，顶栏溢出全 0；2560 内容宽仍 2200、1920 仍 1663（§8 行为不回退）。截图 102–105。

### 9.7 未修遗留（量测在案）

| # | 發現 | verdict |
|---|---|---|
| 1 | 29 处弹窗 600~900px 声明本身 | 宽屏是业务要的（表格编辑器要宽），已被 9.2 全局上限兜底，不必逐页改 |
| 2 | KTable 搜索字段固定 150px | 有 flex-wrap，窄屏自动换行，可接受 |
| 3 | `.k-page` 布局 22+ 文件重复（§8.4-1） | 仍建议收敛为共享样式，收敛时顺带把本轮手机档堆叠规则收进一处 |

### 9.8 本轮门禁与改动清单

- 门禁：eslint 改动 4 文件 0 错；`vue-tsc --noEmit` EXIT 0；vitest `styleContract + consoleMount + personnelDomain + wmsDomain + tableLayout + smartSelectorInteractions + productionDeliveryAndCanary`（即所有触及改动画像的套件）7 文件 **86/86**。
- 改动：`index.html`（viewport 解锁缩放）、`src/styles/index.scss`（弹窗/消息盒全局 max-width 兜底）、`src/component/KAppShell.vue`（顶栏左右簇收缩策略 + 手机档搜索退化 + 100dvh）、`src/component/TenantSwitcher.vue`（手机档 96px）、`src/view/console/personnel/user/list.vue` + `src/view/console/wms/inventory/list.vue`（≤767 堆叠）。
- 截图 102–105（`docs/evidence/`）。

# SPMS 前后端联调与功能验证报告 · 阶段 M（2026-09）

> 上一轮：`docs/TEST_REPORT_2026-09-L.md`（L-1…L-14，11 个提交）。
> 本轮目标（用户原话）：
> ①「针对上一轮报告 §4 那几处问题提供具体决策推荐方案」；
> ②「我发现前端页面展示设计结构存在很大问题。分页导航组件展示错误、表格数据显示存在冗余无用字段及大量空白，浪费很大空间，没有进行合理安排利用设计页面布局结构」；
> ③「每完成一步都要你自己验证能否正常工作，通过截图查看界面渲染效果，判断布局和交互是否符合预期，发现问题就自主修复」。
> 硬约束全程遵守：`main` 不动（冻结在 `a243b3e9`），工作在 `dev`；每步以编译 / 测试 / 真机实测验证。

---

## 0. 结论速览

- **②已完成并验证**：分页导航、冗余字段、空白浪费、布局结构四项都定位到根因、改完、真机量测通过，`pnpm check` 22 步全绿（`outer-exit=0`），4 个提交落在 `dev`。
- **①已完成**：L 报告 §4 的 5 个 P1 + 9 个 P2 逐项给出「推荐方案 / 备选 / 理由 / 代价 / 谁来拍板」，见本报告 §4。**其中 P1-1 需要你拍一个策略问题**（审计表允许被修正吗），我给的是「不允许 + 读取侧惰性归一」，理由与备选都写在 §4.1。
- **③的执行方式需要如实说明**：当前模型**没有图像输入能力**，截图只能作为给你的证据留存，所有「布局是否符合预期」的判定都来自 DOM 几何与文本量测（`getBoundingClientRect` / `getClientRects` / `getComputedStyle` / `document.styleSheets` 规则计数）。这是本轮反复出现「探针口径写错就得出反结论」的原因，教训记在 §3.5。
- 本轮新增 **6 条缺陷**（M-1…M-6，见 §1）、**2 个守卫**（EP 样式聚合双向校验、表格列宽 5 条用例）、**1 个共用模块**（`tableActionWidth.ts`）。测试数 791 → **798**，覆盖率四项指标与 L 轮**完全一致**（86.35 / 81.68 / 82.5 / 86.08），首屏 gzip **+15.2 KB**（预算余量仍有 159.2 KB）。
- **未做完的**：13 个已改实体只做了静态守卫覆盖、缺真机量测；后端 `mvn -B -o clean test` 本轮未跑。都记在 §3.4，不含糊过去。

---

## 1. 缺陷记录（本轮新增）

### M-1（P0）分页导航组件零样式，整块布局塌陷

- **现象**：`.el-pagination` 计算样式 `display: block`、盒子 **151×216**（一个横条组件占了两百多像素高），把外层 `.k-table__pagination` 顶到 **1182×222**，分页条在表格底下摊成一大片空白，`total / sizes / prev / pager / next / jumper` 六段竖着排。
- **根因**：`vite.config.mts:22-25` 用 `Components({ resolvers: [ElementPlusResolver()] })` 做按需导入。**`ElementPlusResolver` 只给「它自己从模板里解析出来」的组件注入 `style/css`**；一旦页面手写了 `import { ElPagination } from 'element-plus'`，标识符已经被绑定，解析器直接跳过 ⇒ 该组件**零样式**。全仓 `from 'element-plus'` 有 **70 处 / 约 60 个文件**，所以这不是改一处能解决的问题。
  `src/main.ts` 原先补了 `message` / `message-box` / `loading` 三行样式 —— 当初的判断只对了一半：**解析器跳过的是「被手写导入过的组件」**，不是「所有组件」。
- **为什么修在聚合入口而不是清理 60 个文件**：`vite.config.mts:74-81` 把裸 `element-plus` 与 `element-plus/es`（都 `$` 锚定；`element-plus/es/components/...` 子路径不受影响）一起指向 `src/types/element-plus-aggregate.ts`。**这个文件是两条路径的共同落点**，在它里面钉样式能同时覆盖「手写导入」和「模板自动解析」两种形态；而清理 60 个文件会让 `src/types/components.d.ts` 漂移，还会波及 12 个依赖当前挂载路径的 `hide` / 列选择器用例。EP 自己的 `es/index.mjs` 有顶层副作用（`makeInstaller([...])`）会击穿 tree-shaking，这也是聚合入口存在的原因。
- **改法**：聚合入口里按字母序钉 **38 条 `import 'element-plus/es/components/<dir>/style/css'`**，与 38 个 re-export 一一对应（38 个目录都验证过存在）；`src/main.ts` 那三行删掉并留注释说明原委；文件头写清「本模块是**唯一**的 EP 样式入口，别处不要再零散补」，附组件 / 渲染类名 / 缺失症状对照表、级联顺序说明、体积说明（将来要收缩应按路由拆样式，而不是删某一行退回偶然加载）。
- **守卫**：`src/config/__tests__/elementPlusAggregate.spec.ts` 加第 4 条用例做**双向校验** —— re-export 的组件必须钉样式（`missingStyle`）、钉了样式的组件必须 re-export（`orphanStyle`），并禁止聚合入口之外再出现 EP 组件样式导入。用例先反向自检（`components.size >= 30`、必须含 `table` 与 `pagination`、`styled.size >= 30`），扫描逻辑失效时直接红。
- **首跑就抓到一个真问题**：`AssertionError: 样式区钉了却没 re-export 的组件… expected [ 'config-provider' ] to deeply equal []`。原因是 `provideGlobalConfig`（`element-plus-aggregate.ts:45`）从**深子路径** `element-plus/es/components/config-provider/src/hooks/use-global-config.mjs` 导出，而守卫的正则只认 `index.mjs` ⇒ `config-provider` 没被算成组件，它的样式就成了「孤儿」。**判对了、结论错了**（`config-provider.css` 正是 `--el-*` 变量所在）。把正则放宽成 `/element-plus\/es\/components\/([a-z0-9-]+)\/(?!style\/)/g`（负向前瞻排除 `style/css` 自身，否则 `orphanStyle` 永远为空）后通过。教训：**守卫的正则必须覆盖真实的导入形态**。
- **真机证据**：EP theme-chalk 样式表 **22 → 51** 张；规则计数 `el-table__cell` **0 → 34**、`el-pagination` **0 → 37**、`el-form--inline` **0 → 4**；`.el-pagination` 回到 `display: flex`、**448×32**；`.k-table__pagination` **1182×222 → 1142×38**，**收回 184px**。剩下 12 个零规则类名核对过，是 EP 自己的无样式标记 / 状态类，不是缺失。

### M-2（P1）操作列档位公式对短标签严重超配

- **现象**：采购单列表操作列 **290px**，而 5 个按钮的文字加起来只要 **174px**；合同页 **348px**；`KDetailTable` 写死 `actionWidth: 160`，里面只放一个「作业」按钮（文字 30px）。
- **根因**：`actionsColumnWidth` 是档位公式（`<=1→90 / <=2→140 / <=3→190 / <=4→230 / else min(count*58, 360)`），**按按钮个数估而不是按文字宽估**；中文按钮普遍 2 字，个数与宽度不成比例。另外 `.k-table__actions` 的 `gap: 4px` 与 EP 自带的 `.el-button + .el-button { margin-left: 12px }` **叠加**：4 个间距实测 93.2px = 每个 23.3px，比声明值大近 6 倍。
- **改法**：新建共用模块 `src/component/framework/tableActionWidth.ts`，`KTable.vue` 与 `KDetailTable.vue` 都调它（两份实现必然漂移，所以只留一份）。
  `actionButtonWidth(label)` = CJK 12px/字 + 其它 7px/字 + 6px 按钮内边距；
  `estimateActionColumnWidth(labels, { min=90, max=360, gap=8 })` = `max(min, min(Σ文字宽 + (n−1)*gap + 24, max))`。
  标定依据是真机量测：`查看` = **29.6px**、`再来一单` = **53.6px** ⇒ CJK 每字 12px、底噪 5.6px。
  `gap` 必须与组件里的**真实**间距一致：`KTable` 传 8（`.k-table__actions` 是 `flex` + `gap: 8px`，并新增 `.k-table__actions :deep(.el-button + .el-button) { margin-left: 0 }` 清零 EP 外边距）；`KDetailTable` 传 12（按钮直接排在单元格里，没有 flex 容器，吃的就是 EP 那 12px）。`KDetailTable` 的 `min` 取 60（2 字按钮 30 + 单元格内边距 24 = 54，表头「操作」48）。
- **真机证据**：采购单操作列 **290 → 230**，`.k-table__actions` 内容宽 **205.2**（= 230 − 24 内边距），与估宽 **174 + 4×8 + 24 = 230** 吻合到 1px 以内；合同 **348 → 244**；明细抽屉 **160 → 60**。

### M-3（P1）树表整页滚动，表头随内容滚走

- **现象**：权限树 309 行时 `.k-shell__content` 的 `scrollHeight` 达 **14521**（`clientHeight` 只有 808），表头在滚动中一起消失；菜单树同类（`scrollHeight` 1790）。
- **根因**：EP 的 `parseHeight` **只接受数字或 `/^\d+(?:px)?$/`**，`height="calc(100vh - 320px)"` 会被当成 `auto` ⇒ 表格不限高，高度全推给外层容器。
- **改法**（`KTable.vue`）：`MIN_TREE_TABLE_HEIGHT = 360`；`measureTreeHeight()` 在 `isTree && props.height === undefined` 时量 `$el.getBoundingClientRect().top`，`available = Math.round(window.innerHeight - top - 56)`（56 = `.k-shell__footer` 35 + `.k-shell__content` 下内边距 20 + 1px 余量），取 `Math.max(360, available)` 存进 `treeAutoHeight`；`tableHeight = computed(() => props.height ?? treeAutoHeight.value ?? undefined)`；`onMounted` 里 `void nextTick(measureTreeHeight)` 并监听 `resize`，`onBeforeUnmount` 摘监听。`IElTableInstance`（L325-328）补 `$el?: HTMLElement`。
- **否决过的方案（记录以免重做）**：给 `.el-table__header-wrapper` 注入 `position: sticky`。计算样式确实是 `sticky`，但祖先链上 `.el-card__body{overflow:auto}`、`.el-card{overflow:hidden}`、`.el-table{overflow:hidden}`、`.k-table__body{overflow:hidden}` **四层都是滚动容器且自身不滚**，sticky 相对最近的那层解析 ⇒ 滚 600px 表头 `top` 从 279.2 变 **−320.8**，滚 3000px 变 −2720.8。覆盖这四处会波及全部 **26 个 KTable 使用点**（25 个 `list.vue` + `KSelector.vue:132` 的对话框表格），不划算。
- **真机证据**：权限树 `.el-table` 高 **565px**，`.k-shell__content` `scrollHeight` **14521 → 828**（`clientHeight` 808），内部滚动交给 `.el-table__body-wrapper .el-scrollbar__wrap`（`scrollHeight 14214 / clientHeight 521`），滚动 2000px 时表头 `top` **恒定 279.2**，309 行；菜单树 **509px**、`1790 → 828`、`1564/465`、表头 `top` **335.2**、34 行；**非树的物料页不受影响**（`.el-table` 182px 无内联高度，`scrollHeight 808 == clientHeight 808`，3 行）。

### M-4（P1）明细抽屉横向溢出 186px，三列被推出视口

- **现象**：采购单「查看」抽屉里 `.k-detail-table` 宽 1120，列宽渲染合计 **1306** ⇒ 溢出 **186px**，采购单价 / 采购数量 / 已采购数量三列在视口外，必须横向滚动才看得见。
- **根因**：`PurchaseDetailEntity` 7 个列 Σ 声明 **1090**，全部用 `minWidth` 且普遍超配（物料编码 140 装 75px 内容、供应商名称 180 装 70px、单价 150 装 35–70px、数量 150 装 23px）；加上写死的操作列 160。明细表容器只有 1120（对话框 1152 − 边距），比主列表的 1131–1142 更窄。
- **改法**：按实测内容宽收紧 7 列 —— `materialCode` `minWidth:140 → width:120`、`materialName` `minWidth:180 → minWidth:160`（唯一弹性列）、`supplierName` `→ width:140`、`supplierCode` `→ width:120`、`price` `→ width:110`、`quantity` `→ width:100`、`finishQuantity` `→ width:110`，Σ **1090 → 860**；操作列由 M-2 的估宽接管（160 → 60）。类内注释记下改前 Σ、溢出量与各列实测内容宽。
- **真机证据**：列宽渲染 `#56 / 物料编码120 / 物料名称304(弹性) / 供应商名称140 / 供应商编码120 / 采购单价110 / 采购数量100 / 已采购数量110 / 操作60`，**Σ = 1120 == 容器 1120**，`wrapScrollW 1120 == wrapClientW 1120`，1 行，0 pageerror，无 vite overlay（截图证据的缺口见 §5.4）。同页主列表未回归（`#56 / 采购单号150 / 采购事由315 / 总金额120 / 采购状态100 / 创建时间160 / 操作230`，Σ 1131 == tableW，9 行）。

### M-5（P1）冗余字段与裸 ID 列占用可见宽度

| 位置 | 问题 | 处置 |
| --- | --- | --- |
| `src/model/channel/purchaseprice/PurchasePriceEntity.ts` | `supplier` 列与 `supplierName` **内容完全相同**（重复投影） | 删掉 `supplier` 上的 `@KTable` |
| `src/model/channel/sale/SaleEntity.ts` | `customerCode` 与 `customerName` 是同实体冗余投影 | `hide: true` |
| `src/model/channel/purchase/PurchaseEntity.ts` | `totalRealPrice` 原为 `force: true`，但**入库完成前它与 `totalPrice` 恒等**（`editor.vue:67-71,94-95` 用 `totalAmount` 填空值）⇒ 两个一样的金额列并排 | `force: true → hide: true`；详情抽屉的 `headerFields` 仍展示，列选择器仍可勾回 |
| `src/model/system/permission/PermissionEntity.ts` | 裸 `id` 作为可见列 | `hide: true`，名称 / 标识按实测内容加宽（330 / 300） |
| `src/model/system/datapermission/*` | 主体ID、条件树JSON、数据资源ID、遮蔽表达式都是机器字段 | 全部 `hide: true`，策略编码放宽成弹性列 300、支持的动作 260、可过滤列用 `label` 缩短表头 |

> 口径提醒（已写进 `src/framework/types.ts`）：**`hide: true` 是布局取舍，不是权限控制**。被 hide 的列在列选择器里仍可勾回，因此不能拿它藏敏感数据 —— 敏感字段走 `@SensitiveField` / `@Desensitize` / `data_field_policy`。

### M-6（P2）字典表大量空白：两三个列被撑成几百像素的空壳

- **现象**：单位表一个 **448px** 的列只装 **84px** 内容；编码规则表 4 个文本列**都没声明宽度**，一律吃 `KTable.vue` 的 `:min-width="column.minWidth ?? 120"` 回落，再平分富余宽度（实测：编码所属字段 182/84、规则前缀 181/23、序列号初始长度 160/8、规则模板 181/60）。
- **改法**：确立列宽口径（见 §3.2 表格）并逐实体落实；字典表（≤4 个业务列）统一补 `createTime` 列（`order: -90, width: 170, datetime: true`），避免出现「两列被撑成 448px 空壳」。`RoleEntity` 的 `createTime` 排在 `order: -99`（低于 `isDisabled` 的 `-98`），因为状态列要跟名称 / 编码挨着。
- **真机证据**（Σ 均 == 容器 1142）：生产单元 `# / 名称376 / 编码180 / 类型130 / 创建时间170 / 操作230`；客户 `# / 名称446 / 编码180 / 电话150 / 创建时间170 / 操作140`；部门 `# / 名称406 / 编码180 / 排序100 / 创建时间170 / 操作230`；销售单 Σ **1496 → 1142**（隐藏客户编码）；合同 Σ **1244 → 1142**（操作列 348 → 244，实体未改）。

### 已定位、本轮刻意不改（记入口径差异）

- **`KDetailTable` 不认 `hide`**：它的列集合是 `getTableConfigList(props.entity).filter(c => !(props.exclude ?? []).includes(c.key))`，只honour 页面传的 `exclude`，不过滤 `hide`（`KTable` 会过滤）。⇒ 在明细实体上写 `hide: true` **不会生效**。目前没有明细实体用 `hide`，所以列宽守卫按 `hide` 算可见列仍然准确；等第一个明细实体需要藏列时，要么给 `KDetailTable` 补 `hide` 过滤，要么在页面传 `exclude`（二者必须选一个并写进注释，不能两边都半做）。
- **`src/view/console/system/log/list.vue` 不是 KTable**：手绘表格（探针报 `ktH=0`），实测「目标」列 200px 装 98px、「操作者」100px 装 8px。列宽守卫覆盖不到它。
- **`src/view/console/asset/contract/component/detail.vue:96-97,116`** 是**第二套**列渲染实现（抽屉表格），重复了 `:min-width="column.minWidth ?? 120"` / `?? 140`，也没走守卫。见 §4 的 P2-15。

---

## 2. 提交清单（4 个，全在 `dev`）

| 提交 | 主题 | 覆盖文件 |
| --- | --- | --- |
| `0d89274b` | `fix(ui)` EP 组件样式统一钉在聚合入口，修分页导航无样式导致的布局塌陷 | `src/types/element-plus-aggregate.ts`、`src/main.ts`、`src/config/__tests__/elementPlusAggregate.spec.ts` |
| `be313c08` | `perf(table)` 操作列按按钮文字估宽、树表按视口自动高度 | `src/component/framework/tableActionWidth.ts`（新）、`KTable.vue`、`KDetailTable.vue` |
| `850bbf5c` | `refactor(model)` 列表列宽按实测内容收紧，冗余列与裸 ID 列 hide，字典表补创建时间 | 19 个 `src/model/**` 实体 + `src/framework/types.ts` |
| `c42d0faf` | `test(layout)` 新增表格列宽守卫，读装饰器注册表而不是正则扒源码 | `src/config/__tests__/tableLayout.spec.ts`（新） |

### 基线完整性（硬约束核验）

- `main` 仍冻结在 `a243b3e9`，本轮**零**提交落在 `main`；`dev` 领先 `main` **141** 个提交。
- 覆盖率阈值 **50 / 65 / 50 / 75 未下调**，实测 86.35 / 81.68 / 82.5 / 86.08 与 L 轮一致。
- 未新增 `infra/scripts/check-*.mjs`，因此不触发 `src/config/__tests__/maskContract.spec.ts:618` 的元守卫（它会 `readdirSync` 扫 `check-` 前缀脚本，要求每个都在 `package.json` 的 `check` 链和 `.github/workflows/ci.yml` 里各有一步）。列宽守卫**故意做成 vitest 用例**，理由见 §3.3。
- 未新增 Flyway 迁移（下一个空闲版本号仍是 **V16**），`src/config/__tests__/schemaContract.spec.ts` 无需改动。
- `src/types/components.d.ts` 构建后只有 EOL 差异，提交前已 `git checkout --` 还原。

---

## 3. 验证证据

### 3.1 构建与测试（`pnpm check`，22 步，`outer-exit=0`）

```
lint:ci → airpower:check → airpower:backend --check → pom:check → deadcode:check → exports:check
→ typecheck → test:coverage → perms:check → auth:check → errcode:check → contract:check → text:check
→ ddl:check → migration:check → monitoring:check → canary:check → docker:check → drill:check
→ build → demo:check → size
```

| 指标 | L 轮基线 | 本轮 | 判读 |
| --- | --- | --- | --- |
| Test Files | 70 passed | **71 passed** | +1（`tableLayout.spec.ts`） |
| Tests | 791 passed | **798 passed** | +7（列宽守卫 5 条 + EP 聚合第 4 条 + `elementPlusAggregate` 拆出的自检断言） |
| 覆盖率（stmts/branch/func/lines） | 86.35 / 81.68 / 82.5 / 86.08 | **完全一致** | 新增代码全部被守卫覆盖，阈值未动 |
| build | 35.72s | **29.73s** | — |
| 首屏 gzip | 275.6 / 450 KB | **290.8 / 450 KB** | **+15.2 KB**，余量 159.2 KB |
| 全部产物 gzip | 697.3 / 900 KB（204 文件） | **714.7 / 900 KB（205 文件）** | **+17.4 KB**，余量 185.3 KB |
| PWA precache | 209 entries | **210 entries（2210.40 KiB）** | +1（新 chunk） |
| 最大懒加载分包 | — | `assets/vendor-echarts-DgMby0gR.js` **192.5 KB** | 单包上限 300 KB，未超 |

**+15.2 KB gzip 就是钉住 38 个 EP 组件样式的真实代价**（此前按 raw 205.2 KB 估的 +28.2 KB gzip 偏保守）。换来的是分页 / 表格 / 表单三类组件的样式**从「偶然加载」变成「必然加载」**——L 轮之前它们能不能出样式，取决于某个页面有没有恰好在模板里用到该组件。

本轮 `pnpm check` 失败过两次，都记下来：

1. **`eslint .` 以 `0x80000003` 崩溃**（`outer-exit=-2147483645`，`Command failed with exit code 2147483651`），日志里**没有任何 lint 明细**。单独重跑 `pnpm lint:ci` 才看到 10 条真错误：`tableActionWidth.ts:24 regexp/no-dupe-characters-character-class`（`U+3000-U+303F` 已被 `U+2E80-U+9FFF` 包含）、`tableLayout.spec.ts:29 style/arrow-parens`、`:93` 三条 `unicorn/prefer-string-starts-ends-with`、5 条 `style/indent-binary-ops`。`pnpm eslint --fix <两个文件>` 一次修完。**教训：eslint 以 `0x80000003` 退出时必须单独重跑一次才能看到真实报告**，那个崩溃码本身不含信息。
2. **`exports:check` 拦下 2 个没人 import 的导出**：`tableActionWidth.ts` 的 `actionButtonWidth`（L21）与 `IActionColumnWidthOptions`（L29），分类是「文件内自己还在用」⇒ 去掉 `export` 关键字即通过。这道门挡住了「为了写测试而导出、实际只有模块内部用」的常见泄漏（该门也提供 `node scripts/prune-unused-exports.mjs` 自动清理）。

### 3.2 列宽口径（写进 `src/framework/types.ts`，由 `tableLayout.spec.ts` 钉住）

| 列类型 | 口径 | 依据 |
| --- | --- | --- |
| 序号 `#` | 56（`KTable` 固定） | EP `type="index"` |
| 名称类（唯一弹性列） | `minWidth: 180` | 富余宽度只给它，避免多列平分产生空壳 |
| 编码 / 单号 | `width: 150–180` | 实测内容 75–105px + 排序图标 |
| 电话 | `width: 150` | 11 位 + 脱敏星号 |
| 枚举 / 状态 / 类型 | `width: 100–130` | 中文枚举最长 5 字 |
| 小整数（排序号、长度） | `width: 100` | — |
| 金额 | `width: 110–140` | 千分位 + 两位小数 |
| 时间 | `width: 160–170` | `yyyy-MM-dd HH:mm:ss` |
| 布尔「启用」 | `width: 90` | — |
| 裸 ID / 外键 ID / JSON 片段 | **`hide: true`** | 机器字段，占宽无信息量 |

- 预算：**Σ 可见数据列声明宽 ≤ 900px**（1142 可用宽 − 56 序号 − 最多 190 操作）。超出时优先 `hide` 低价值列，而不是让页面出现横向滚动条。
- 表头 **≤ 8 个中文字符**；括号里的解释挪到字段注释 / tooltip（编码规则表原先有超长括号表头）。
- 可用宽实测：1440×900 下 shell chrome 占 132px（topbar 57 + footer 34+1 边框 + `.k-shell__content` padding 40），`.k-shell__content` `clientHeight` 808，**主列表可用表宽 1131–1142px**；`.k-page__aside` / `.k-page__main` 双栏页（inventory、user）只有 **856px**。

### 3.3 新增守卫 `src/config/__tests__/tableLayout.spec.ts`（5 条用例，9.34s）

| # | 用例 | 首跑结果 |
| --- | --- | --- |
| 1 | Σ 可见数据列声明宽 ≤ 900px | 红 2 个 → 采购明细 1090（已收到 860）、库存 950（**合法豁免**，见下） |
| 2 | 每个可见列必须显式声明 `width` 或 `minWidth`（不许吃 120 回落） | 绿（说明本轮已把编码规则表那类「4 个文本列都不声明宽度」清干净） |
| 3 | 同一张表的可见列表头不重复 | 绿（采购价的重复 `supplier` 列本轮已删） |
| 4 | 可见列表头 ≤ 8 字符 | 绿 |
| 5 | 裸 ID / 外键 ID / JSON 片段不得可见（`RAW_ID_KEYS = {id, tenantId, creatorId, parentId}` 或 `endsWith('Id'/'Json'/'Expression')`） | 绿，零命中（M-5 里 hide 的那批正是它要防的回归） |

- **为什么用 vitest 而不是 `infra/scripts/check-*.mjs`**：要读的是**装饰器注册表本身** —— `getTableConfigList(Class)`（`src/framework/metadata.ts:167-174`）已经做了 `sortByOrder` 降序、剔除 `removed`、回填 `label || getFieldLabel(Class, key)`。写静态正则等于把注册表逻辑抄一遍，且会慢慢漂移。附带好处：不必往 `package.json` 的 `check` 链和 `ci.yml` 各加一步，也就不触发元守卫。
- 扫描方式：递归 `readdirSync(dir, { withFileTypes: true })` 收集 `src/model/**/*.ts` 并**跳过 `__tests__`**（桩实体会造假违规）；顶层 `await import(file)` 后取 `Object.values(module)` 里 `typeof === 'function' && getTableConfigList(...).length > 0` 的类，存进 `Map<object, ITableColumn[]>`（Map 天然去重跨文件 re-export 的同一个类）。
- **反向自检** `expectRegistryAlive()`：`models.size > 15`，且 `getModelName` 结果必须含「物料」「仓库」「采购单」「权限」四个 —— 扫描逻辑漏域时直接红，不会静默通过。
- **结构化豁免** `BUDGET_EXEMPT: Record<string, { excludedKey: string; reason: string }>`，目前只有一条 `库存 → storageName`：`src/view/console/wms/inventory/list.vue:74` 按库存类型二选一（`InventoryTypeEnum.STRUCTURE` 时排 `storageName`，否则排 `structureName`，两列都 180px）⇒ 任一时刻只有 6 列可见；且该页是双栏布局，可用宽只有 856px，扣掉一列后 Σ = **770**。豁免是**自校验**的：算预算时扣掉该列的声明宽，并断言它真的在可见列里 —— 实体改了列名 / 列宽而豁免没跟上就红。
- **删掉过一条规则并留注释防止后人加回来**：「每个实体最多一个弹性列」。它命中 11 个实体（合同附件、参与方、采购明细、销售明细、UserEntity、菜单、权限、入库明细、移库明细、移库单、出库明细），但其中菜单（菜单路径内容 **352px** + 组件地址 150px）、权限（权限名称 **374px** + 权限标识 **285px**）、移库单（移库单号 302px + 目标仓库 335px，Σ 正好 1131 不溢出）**两列都真的需要弹性**；而真正量测到「448px 列装 84px 内容」的单位表**只有一个**弹性列。⇒ **弹性列个数与空白浪费没有因果关系**，这条规则产出 11 个假阳性、0 个真阳性。替换成第 5 条（裸 ID / JSON 不得可见），零命中通过。

### 3.4 未完成的验证（如实记录）

- **13 个已改实体缺真机量测**：`storage`、`supplier`、`role`、`codeRule`、`config`、`menu`（列表列）、`permission`（列表列）、`resource` / `policy` / `fieldPolicy`、`purchasePrice`、`salePrice`、`inventory`。它们**已被 `tableLayout.spec.ts` 静态覆盖**（守卫读的就是这些实体的注册表，Σ ≤ 900 / 显式宽度 / 表头不重复 / ≤8 字 / 无裸 ID 五条全绿），但没有逐个开页面量渲染宽度。已真机量测通过的是：`unit`、`structure`、`customer`、`department`、`purchase`、`sale`、`contract`、采购明细抽屉、`permission` / `menu` 树高、`material`（非树回归对照）。
- **后端回归未跑**：`mvn -B -o clean test` 本轮没执行（L 轮基线 Tests run 927 / Failures 0 / Errors 0 / Skipped 1，约 159s）。本轮**没有改任何后端代码**，风险面为零；但按硬约束「每步以编译/测试验证」，下次开后端前应先补跑一次。注意不能在后端存活时跑 `mvn clean`。
- 环境说明：验证期间后端 java 存活于 8080（就绪标记 `Hi Guy, Kubo is running at [8080] !`），vite dev server 存活于 3000（VITE v7.3.6），Playwright 以 1440×900 登录、0 console error。为跑 `pnpm check` 曾杀 node 进程（沙箱对多数进程返回 `Access is denied`，只杀掉 vite / playwright 那几个），**跑完 check 后 dev server 与后端需要重启才能继续真机验证**。

### 3.5 探针口径教训（下一轮复用前必读）

| 陷阱 | 错在哪 | 正确口径 |
| --- | --- | --- |
| `document.querySelector('.el-scrollbar__wrap')` | 命中**侧边栏菜单**的滚动器（报 `scrollHeight == clientHeight == 784`），让 sticky 实验先看起来成功、再看起来失败，两次都是错因 | 必须限定 `.el-table__body-wrapper .el-scrollbar__wrap` |
| `.el-table__body-wrapper.scrollWidth` | 报的是**它自己的宽**（1120），不是溢出内容宽 ⇒ 溢出 186px 时报「无溢出」 | 判断横向溢出要把 `th` 宽度**求和**再与容器宽比 |
| 等待 2400ms | `wms/output`、`wms/move`、`wms/inventory` 报 `rows=0 / cols=0 / ktH=0` 且有 42 条 console error | 懒加载分包需要 **≥3500ms**，改完代码后 **≥5000ms**；单独复测 `wms/output/list` 时页面完全正常（`errCount=0`）⇒ **不是缺陷**。「页面看起来坏了，先等 5 秒再下结论」 |
| Element Plus 复选框点不动 | `input.el-checkbox__original` 是隐藏的 | 这类 UI 契约用 vitest 证，不用 Playwright |
| `localStorage` 污染量测 | 列选择器的偏好会改列宽 | 量测前确认 `kubo:table:columns:*` 为 0 项（实测 3 项里没有它）⇒ 所有宽度都是默认态 |

---

## 4. 对 L 报告 §4 的决策推荐（objective ①）

> 格式：**推荐** / 备选 / 理由 / 代价 / **需要谁拍板**。凡标「需要你拍板」的，我没有替你决定。

### 4.1 P1-1 `operation_log` 历史行不回溯脱敏 —— **需要你先拍一个策略问题**

**策略问题（一句话）**：一张 append-only 的审计表，允许被就地改写吗？

**推荐：不允许改写，改为「读取侧惰性归一 + 写入侧版本标记」。**

- 具体做法：
  1. `V16` 给 `operation_log` 加一列 `schema_version INT NOT NULL DEFAULT 0`（历史行天然为 0，新写入 = 当前脱敏规则版本号）。
  2. `GET /dataResource/operation-logs`（`DataResourceController.java:171`）是**唯一出口**，在返回前对 `schema_version < 当前版本` 的行重放一次当前脱敏规则（命中 `isNeverPlaintext` 的键改写成摘要 `h:xxxxxxxx`），原文仍留在库里。
  3. 加一条守卫：`@SensitiveField` 的键集合指纹变了而「当前版本号」常量没跟着动就红（指纹可用反射扫描算 hash，不需要新依赖）。
- **理由**：审计表的价值在于**可对账** —— 一旦就地改写，就再也无法证明「当时看到的」是什么，等于把审计降级成普通业务表。而洗库需要一份「哪些键在哪个版本前是明文」的时间线，这条时间线现在**只存在于提交历史里**，把它抄进脚本等于制造第二个真相源，之后每次加注解都要同步维护。读取侧归一让**原文保留 + 任何出口都不吐明文**，两个目标同时满足。
- **代价**：读取侧要能重放脱敏（一次 JSON 解析 + 键匹配）；日志页是分页读取（20–50 行），成本可忽略。真正的工作量是第 3 条守卫 —— 没有它，「当前版本号」会被人忘记推进，惰性归一就静默失效。
- **备选（不推荐）**：一次性 `V16` 洗库 `UPDATE`，把 logId 1/2 那两条明文（`purchasePrice: 1.0` / `salePrice: 2.0`）改写成摘要。优点是简单、一次到位、读取侧零改动；缺点是破坏可对账性，且需要手工维护那份明文时间线。
- **当前暴露面**：生产为零（该功能上线时表是空的），风险只在开发库；但**结构上**任何一次「先上线、后加注解」都会重演，所以这条不能靠「现在没事」关闭。
- **需要你拍板**：如果你判断「开发库那两条明文必须物理消失」（例如库要交付给别人），那就走备选，我不再坚持 append-only。

### 4.2 P1-2 `ParticipantEntity` 的 PII 未脱敏 —— **推荐做，且顺序不能反**

**推荐执行顺序（三步，任一步颠倒都会重演 L-8）**：

1. 先建 `ParticipantMapper` 并注册 `asset.participant` 数据资源。**这一步是硬前置**：只注册不绑定会让 `DataResourceBindingRegistry.java:115` 抛 `has @DataResource(x) but no mapped statements`、`DimensionBindingAnalyzer.java:180` 报 `KIND_ENTITY_NOT_MAPPED`、`DataPermissionEnforceReadiness.java:115`「拒绝用不确定的结果启动」。
2. 再给 `phone` / `identification` / `email` 加 `@SensitiveField` + `@Desensitize`。
3. **最后**才给实体标 `@OperationLog`。

- **理由**：现在不标 `@OperationLog` 是**刻意的** —— 三个字段一个脱敏注解都没有，此刻标注等于把身份证号明文写进审计表。第 2 步必须在第 3 步之前。
- **明确不要做的事**：**暂不**把 `ParticipantEntity` 迁到 `TenantAwareEntity`。它属于 `V2` 的 [T4] 层，整个 T4 家族目前仍是 `BaseEntity`（55 个实体对 32 张已迁移表），提前上 `@MappedSuperclass` 会让 `ddl-auto=validate` **启动即失败**。
- **代价**：1 个 mapper 接口 + 1 条数据资源注册 + 3 组注解 + 1 个 `@OperationLog`。附带收益：`/participant/*` 的泛型在 L-3 已修正（此前读写的是 `contract` 表），补上 mapper 之后这条链路才第一次真正可治理。
- **连带决定 P2-13**：见 §4.7 —— 推荐**删掉** `AssetServices` 那两个零调用点的 getter，而不是为了「用上」而硬接。

### 4.3 P1-3 数据权限三表外键符号不匹配 —— **推荐立刻做，本轮所有建议里性价比最高**

**推荐**：出 `V16`，对 `data_policy` 与 `data_field_policy` 各 `CALL kubo_align_resource_id('表名')`（存储过程已在 `V12__retired_module_catalog_convergence.sql:174-186`，直接复用，不要重写），并把「**启动日志零 DDL 失败**」做成门禁。

- **理由**：这是启动日志里**唯一**一条 DDL 失败 —— `Referencing column 'resource_id' and referenced column 'id' are incompatible`（`data_resource.id` 是 `bigint unsigned`，两张引用表的 `resource_id` 是有符号）。后果是**参照完整性静默失效**：外键没建上，删除数据资源不会级联、也不会报错，孤儿策略会一直留在库里。而这条失败目前**被淹没在正常启动输出里**，没人会注意到。
- **门禁怎么落（推荐后者）**：
  - A：启动后端 → 抓 `incompatible` / `DDL` 失败关键字 → 非零即红。缺点是需要真数据库、慢、且与 `ddl:check` 职责重叠。
  - **B（推荐）**：在既有 `ddl:check` 里比对 Flyway 声明与 `information_schema.COLUMNS` 的**列类型签名**（含 `unsigned` 标志），不一致即红。确定性、不需要启动后端，而且能防住未来同类漂移（不只这一处外键）。
- **代价**：一个迁移文件 + 同步更新 `src/config/__tests__/schemaContract.spec.ts`（它钉住了迁移清单和 `at(-1)`）+ 一条 `ddl:check` 规则。**注意**：绝不能改已应用的 `V1..V15`（不能编辑、不能重排），只能加 `V16`。
- **需要你拍板**：无。这条我建议直接排到下一轮第一位。

### 4.4 P1-4 两处工程守卫缺口（R1 / R2）—— **推荐都补，但 R1 优先**

- **R1（推荐先做）**：加一条 ArchUnit 规则，禁止在 `BaseService` 子类的方法上标 `@Transactional`：
  `methods().that().areDeclaredInClassesThat().areAssignableTo(BaseService.class).should().notBeAnnotatedWith(Transactional.class)`。
  **理由**：`PermissionService` 就因为 CGLIB 代理破坏了 `@Autowired(required=false)` 的字段注入而 500 过，现在**只靠注释约束** —— 注释不会红。这是一次真实事故的复发防线。
- **R2**：后端 `resourceKey` 的 fail-open 目前只被**部分**守卫覆盖。推荐把「解析失败必须抛而不是放行」写成单元测试（覆盖 null / 空串 / 未注册 key 三种输入），再补一条 grep 门禁盯住 fail-open 的写法。
- **代价**：R1 一条规则；R2 一个测试类 + 一条门禁。**需要你拍板**：无，但 R2 的覆盖面要先盘一次「还有哪些地方 fail-open」，这个盘点本身是半天工作量，建议单独排。

### 4.5 P1-5 legacy 表清单与 DROP 草案 —— **推荐放 Flyway 目录外 + 两步法**

- **推荐**：草案写在 `docs/ddl/legacy-drop-draft.sql`（**Flyway 目录之外**），并采用两步法：先 `RENAME TABLE x TO _deprecated_x`，观察一个发布周期，确认无读写后再 `DROP`。
- **理由**：`ddl-auto=validate` 环境**严禁 `DROP COLUMN`**（`V2` 笔记 L536），把 DROP 放进 Flyway 会让应用启动即失败；`V2` L808 另注明「不建 FOREIGN KEY」、L814「本表必须一字不差」，说明这个目录的约束比一般仓库严。RENAME 是可逆的，DROP 不是。
- **代价**：18 张表的盘点（阶段 K 的 P2-2/R8 已开了头）+ 一个草案文件。**需要你拍板**：观察周期定多长（我建议一个发布周期，即下一次 `V` 号推进之后）。

### 4.6 P2-6 / P2-7 / P2-11 —— **推荐合成一个「退场语义收敛」提交**

| 项 | 推荐 | 理由 |
| --- | --- | --- |
| **P2-6** `PurchaseStatus.INPUTTING(4,"入库中")` 不可达却出现在字典与前端下拉 | **摘掉**（从字典与下拉移除），不要补产生它的状态转移 | 补一个没人需要的转移等于给状态机加一条未验证路径。核心原则：**码表是语义，不是数字区间**（`docs/DESIGN_THINKING.md` 案例 J）—— `4` 这个编号存在不代表这个状态存在。用户现在会选到一个永远查不出结果的状态，这是**假提示**，比没有提示更糟。 |
| **P2-7** 已删模块的配置项仍可见可改（`PICKING_BILL_AUTO_AUDIT`、`ORDER_AUTO_START_AFTER_AUDIT`；同族：退场的 `ConfigFlag` 11/12/18/19/20） | **配置页隐藏 + 数据保留** | 改了没有任何效果（`OutputEntity.java:71-77` 只剩 `@JsonIgnore @Column(name="picking_id", insertable=false, updatable=false) Long legacyPickingId`），所以是纯粹的用户陷阱。但**数据要留着** —— 与 `hide` 同理，隐藏是展示层取舍，删数据会破坏历史可对账。 |
| **P2-11** `base/mapper/ReadStat.java:13` 注释说 `publishedTotal`「通常为 0」 | **删字段** | L-1 之后它**恒为 0**，已经没有信息量。改注释是把死字段合法化，删字段才是收敛。删之前 grep 一次调用点（L 轮已确认只有注释在提它）。 |

### 4.7 P2-13 死注入 —— **推荐删，不推荐「为了用上而接上」**

- `AssetServices.getContractService()` 与 `getParticipantService()` **零调用点**（该文件 36 行，只有 `getMaterialService()` 被读：`DevDataInitRunner.java:837`、`SalePriceService.java:160`、`PurchasePriceService.java:160`、`DashboardService.java:53`）。
- **推荐删掉这两个 getter**。L-3 修好泛型之后它们第一次「能用」了，但前端合同详情抽屉是通过 `/contract/getDetail` 带出的**嵌套数组**渲染参与方的（`KDetailTable` 吃 `rows`，不吃服务），**不需要独立服务入口**。将来真要独立分页再加回来，成本是一次 `git revert` 级别的事。
- **理由**：`deadcode:check` 与 `exports:check` 这两道门本轮刚刚拦下我写的 2 个多余导出 —— 同一套标准应该用在后端。留着零调用点的公共入口，下一个读代码的人会以为它是契约的一部分。
- **顺带**：清掉开发库里的遗留探针数据 `purchase id=9`（`reason='联调-越权探针'`）。它会让业务列表的统计对不上，也会让下一轮做数据收敛指标时多一个需要解释的异常值。

### 4.8 P2-8 脱敏可反推 —— **推荐用既有的 `data_field_policy` 机制，不要新造开关**

- **问题**：单价遮蔽成 `2***0` / `2***9`，但 `sale` 的总额是明文 `119,996.00` ⇒「数量 + 总额」足以反推单价，脱敏形同虚设。
- **推荐**：给 `sale.totalPrice`（以及同族的 `totalRealPrice`）加一条 **`data_field_policy`** 记录，让它跟单价走同一套字段级策略 —— 有金额权限的角色看明文，只有基础权限的看遮蔽值。
- **理由**：项目**已经有**这个机制（`data_field_policy` 现有 4 条记录），复用它比「要么全遮要么全放」的二选一更符合实际业务（单据要能对账，总额对财务必须可读；对无关人员则不该可读）。**不推荐**「在策略里承认聚合值不敏感」那条路：用户既然给单价标了敏感，说明单价是商业秘密，那么 `数量 × 单价` 的乘积同样是 —— 承认它不敏感等于承认脱敏无效。
- **代价**：2 条策略记录 + 一个测试（断言「单价敏感 ⇒ 同单据金额聚合值必须也在策略里」，否则下次加金额字段又会漏）。**需要你拍板**：哪些角色算「有金额权限」（我建议按现有的角色编码指定，不要新建角色）。

### 4.9 P2-9 / P2-10 / P2-12 —— **三项都是小改，推荐一并处理**

- **P2-9 `CREATE` 快照带 `"id":null` 与 `"isPublished":null`**：**推荐在快照序列化上加 `@JsonInclude(NON_NULL)`**，不要挪审计时机。挪到 id 分配之后（`@PostPersist`）会引入「保存失败就没有审计」的新风险，而剔除 null 键是一行注解，Diff 立刻不再显示「新增了一个 null」。
- **P2-10 两处信息面**：① 给 `PurchaseController.getDetail` 补 `@Description`（1 行），让 403 文案不再泄露原始方法名「采购单-getDetail (Purchase_getDetail)」；② `/menu/getList` 的 34 条路径枚举面**推荐不改**，但补一条决策注释（与 L-14 对 `MenuController` / `DepartmentController` 的 `authorize = false` 同一做法）说明「菜单是导航元数据、不含业务数据；侧栏走 `getMyMenuList`，实测 0 节点已正确收敛」。**理由**：没有注释的「故意放开」和「忘记收紧」在代码里长得一模一样，下一个人只能猜。
- **P2-12 控制台噪音**：ECharts 那条改成 `grid.outerBounds`（一行）；`Runtime directive used on component with non-element root node … at <ElTooltip>` 是 EP 自身在 fragment 根节点上的指令警告，**推荐不改**（要升级 EP 才有解），但记录在案，免得下一轮又查一遍。

### 4.10 P2-14 设计令牌化（T3）—— **推荐棘轮一次只推一格**

- `src/component/framework/KPanel.vue:65` 的 `transition: all 0.2s ease` 一类硬编码值收敛到令牌（`src/styles/tokens.scss` 165 行 / `motion.scss` 151 行已有体系），并把 `designTokenContract.spec.ts` 的棘轮阈值**下调到「当前值 − 本轮实际改掉的条数」**。
- **理由**：棘轮的价值在于「只许减不许增」。一次推太多格会让阈值变成愿望而不是约束；一次推一格，每格都有提交对应，回滚成本可控。
- **注意**：`src/styles/index.scss`（32 行）只有 `@use './tokens.scss'`(L29)、`@use './motion.scss'`(L30)、`@import 'element-plus/theme-chalk/dark/css-vars.css'`(L32)，**零组件样式**；`@import` 会被提升，所以真实打包顺序是 EP-dark → tokens → motion，而且**不能调整**（Sass 要求 `@use` 在最前）。EP 组件样式现在全部经 `main.ts:2` 的聚合导入进来，**永远早于 tokens** ⇒ 令牌层必然覆盖 EP 默认值。这条级联关系是 M-1 的隐含前提，改样式入口顺序前必须重读这段。

### 4.11 本轮新发现的两项（L 报告里没有）

- **P2-15（推荐做）**：`src/view/console/asset/contract/component/detail.vue:96-97,116` 是**第二套**列渲染实现，重复了 `:min-width="column.minWidth ?? 120"` / `?? 140`，既不走 `KTable` 也不走 `KDetailTable` ⇒ **列宽守卫覆盖不到它**。推荐改成复用 `KDetailTable`（它已支持 `exclude` / `rowActions` / 估宽操作列）；如果因为交互差异不能复用，至少把 120 回落改成与守卫一致的口径，并在守卫里为它加一条「非 KTable 的手绘表格必须登记」的例外清单。**同类**：`src/view/console/system/log/list.vue` 也是手绘表格（实测「目标」200px 装 98px、「操作者」100px 装 8px），一起登记。
- **P2-16（推荐下一轮做，本轮刻意没做）**：WMS 单据页（`input` / `output`）只有 **4 个数据列**，弹性的「单号」列吃掉 **527px** 却只装 **105px** 内容 —— 这是本轮量测到的**最大单块空白**。推荐改成**主从双栏**（左单据列表、右选中单据的明细），直接复用 `inventory` / `user` 两页已有的 `.k-page__aside` / `.k-page__main` 布局（可用宽 856px 那套）。
  **本轮不动的理由**：这是布局重构而不是宽度调整，风险面覆盖 3 个页面 + 明细联动选中态，与本轮「先把宽度和样式修对」的范围不同类；而且它需要双栏下重新标定列宽预算（856px 而非 1142px），会改动守卫的预算常量。**这是本轮唯一一个「量测到了、判断值得做、但主动推迟」的项。**

### 4.12 建议的下一轮顺序

1. **P1-3**（一个 `V16` + `ddl:check` 的列类型签名比对）—— 代价最小、收益是参照完整性真正生效，且是**唯一一条现在正在静默失败**的项。
2. **P1-2**（participant 的 mapper + 数据资源注册 + 脱敏注解，顺序严格按 §4.2），**同提交里**顺手做 **P2-13**（删死注入、清 `purchase id=9`）。
3. **P2-6 / P2-7 / P2-11** 合成一个「退场语义收敛」提交（与 `V12` 的思路一致）。
4. **P1-1** —— 等你回答「审计表允许被修正吗」之后再动（§4.1）。
5. **P2-16** 主从双栏（最大的一块空白，但范围最大，单独排一轮）。
6. **P1-4 / P1-5 / P2-8 / P2-9 / P2-10 / P2-12 / P2-14 / P2-15** 按上面的推荐逐项收敛，其中 P1-4 的 R2 需要先做半天盘点。

---

## 5. 复现与工具

### 5.1 前端全链

```powershell
# 22 步全链（约 3-5 分钟；不要与后端 mvn 或 Playwright 并发，会 OOM）
cmd /c "cd /d D:\JAVA\Code_repository\SPMS-Web&& pnpm check > %TEMP%\spms-check.log 2>&1"
# 只看关键行
Get-Content "$env:TEMP\spms-check.log" | Select-String -Pattern "Test Files |Tests  |FAIL |All files|built in |precache"

# eslint 崩在 0x80000003 时必须单独重跑才看得到真实报告
cmd /c "cd /d D:\JAVA\Code_repository\SPMS-Web&& pnpm lint:ci > %TEMP%\spms-lint.log 2>&1"; Get-Content "$env:TEMP\spms-lint.log" -Tail 30

# 单跑守卫
cmd /c "cd /d D:\JAVA\Code_repository\SPMS-Web&& pnpm vitest run src/config/__tests__/tableLayout.spec.ts src/config/__tests__/elementPlusAggregate.spec.ts > %TEMP%\spms-fe-guard.log 2>&1"
```

### 5.2 服务与账号

```powershell
# 后端（就绪标记：Hi Guy, Kubo is running at [8080] !，28-42s）
Start-Process cmd.exe -ArgumentList "/c set JAVA_HOME=D:\JAVA\JDK\jdk-18.0.2.1&& mvn -B spring-boot:run > `"$env:TEMP\spms-backend.log`" 2>&1" -WorkingDirectory D:\JAVA\Code_repository\SPMS-Web\SPMS-Server -WindowStyle Hidden
# 前端
Start-Process cmd.exe -ArgumentList "/c pnpm dev > `"$env:TEMP\spms-frontend.log`" 2>&1" -WorkingDirectory D:\JAVA\Code_repository\SPMS-Web -WindowStyle Hidden
# 后端回归（不可在后端存活时跑）
cmd /c "set JAVA_HOME=D:\JAVA\JDK\jdk-18.0.2.1&& cd /d D:\JAVA\Code_repository\SPMS-Web\SPMS-Server&& mvn -B -o clean test > %TEMP%\spms-mvn.log 2>&1"
# 直查数据库（insecure-password 警告走 stderr，会让 PowerShell 报 exit 1，数据照常打印）
D:\JAVA\MySQL\mysql-8.0.37-winx64\bin\mysql.exe -h 127.0.0.1 -uroot -proot kubo -e "SELECT ..."
```

- 账号：`admin@hamm.cn` / `17666666666`（凌小云，id 1，全权限）；`admin@hamm.com` / `13888888888`（张三，id 2，受限）。密码 **`Aa123456` 必须字面输入** —— 走占位符替换会静默得到 `code=200001`。
- 登录页 `http://127.0.0.1:3000/` → `/login?redirect=/console/index`，占位符「请输入账号、邮箱或手机号」/「请输入密码」，按钮「立即登录」，`waitForURL(/\/console/)` 落到「工作台 - Kubo」。探针若发现自己在 `/login?redirect=…`，说明会话过期，重新登录。
- 代理：`'/api' → 'http://127.0.0.1:8080/'`（`rewrite: path => path.replace(/^\/api/, '')` + `changeOrigin: true`），`'/oauth2' → 'http://127.0.0.1:8080'`（不 rewrite）⇒ 走 3000 用 `/api/xxx`，直连 8080 用 `/xxx`。鉴权头是 `Authorization: Bearer <token>`（`src/api/axios.ts:170`），不是 `hamm_token`。`operation-logs` 是 **GET**（`DataResourceController.java:171`）。

### 5.3 真机量测口径（Playwright MCP，`ws_mcp_call` + server `"@global/playwright"`）

- `browser_run_code_unsafe` 的 `code` 形如 `async (page) => { … }`；`browser_take_screenshot` 必须传 `scale`；`browser_click` 必须传 `target`。一次调用最多量 3–5 个页面。
- 等待：懒加载分包 **≥3500ms**，改完代码后 **≥5000ms**。
- 量列宽：把 `th` 的 `getBoundingClientRect().width` **求和**与容器宽比（不要用 `.el-table__body-wrapper.scrollWidth`）。
- 量表格内滚动：选择器写全 `.el-table__body-wrapper .el-scrollbar__wrap`（裸写会命中侧边栏菜单）。
- 判吸顶：滚到 `scrollTop = 2000` 后再读一次表头 `getBoundingClientRect().top`，与滚动前比较（本轮权限树恒定 279.2、菜单树 335.2）。

### 5.4 关于截图证据（本轮的一个执行缺口，如实记录）

- 本轮验证过程中确实截过图（采购明细抽屉改后等），但截图是用 Playwright MCP 的相对路径 `./xxx.png` 写的，**落在了 Playwright 服务进程自己的工作目录，不在仓库里**；事后在仓库内递归查找 `m-round-*.png` **零命中**，`docs/evidence/` 目录也不存在。⇒ **本轮没有可提交、可回看的截图证据**，我不把它列成已交付物。
- 因此本报告里的所有布局结论都以**数值量测**为准（§1 每条缺陷都带改前/改后的像素与规则计数，§5.3 给了可复现的量测口径）。数值量测比截图更强的一点是它可重放：换一个视口宽度也能立刻算出结论，而截图只能证明「那一刻、那个宽度下长这样」。
- 下一轮的正确做法（写在这里避免重犯）：截图前先 `New-Item -ItemType Directory docs\evidence`，并把文件名以**绝对路径**传给截图工具（`D:\JAVA\Code_repository\SPMS-Web\docs\evidence\<名字>.png`），截完立刻 `Test-Path` 确认，再 `git add`。另外要在跑 `pnpm check` **之前**截完 —— check 会杀掉 vite dev server，之后要重启后端（28–42s）+ 前端 + 重新登录才能继续真机验证。
- 补充说明：当前模型**没有图像输入能力**，即使截图落在仓库里，它也只是给你的证据，不是我的判定依据 —— 这一点在 §0 已经声明，此处不重复作为借口，只是说明「截图缺失」并不影响本轮结论的成立。

---

## 附：本轮涉及的完整文件清单

**新增（2）**：`src/component/framework/tableActionWidth.ts`、`src/config/__tests__/tableLayout.spec.ts`

**修改（28）**：
`src/types/element-plus-aggregate.ts`、`src/main.ts`、`src/config/__tests__/elementPlusAggregate.spec.ts`、`src/component/framework/KTable.vue`、`src/component/framework/KDetailTable.vue`、`src/framework/types.ts`、
`src/model/system/unit/UnitEntity.ts`、`src/model/system/coderule/CodeRuleEntity.ts`、`src/model/system/config/ConfigEntity.ts`、`src/model/system/menu/MenuEntity.ts`、`src/model/system/permission/PermissionEntity.ts`、`src/model/system/datapermission/DataResourceEntity.ts`、`src/model/system/datapermission/DataPolicyEntity.ts`、`src/model/system/datapermission/DataFieldPolicyEntity.ts`、
`src/model/factory/storage/StorageEntity.ts`、`src/model/factory/structure/StructureEntity.ts`、
`src/model/channel/customer/CustomerEntity.ts`、`src/model/channel/supplier/SupplierEntity.ts`、`src/model/channel/purchase/PurchaseEntity.ts`、`src/model/channel/purchase/PurchaseDetailEntity.ts`、`src/model/channel/purchaseprice/PurchasePriceEntity.ts`、`src/model/channel/sale/SaleEntity.ts`、`src/model/channel/saleprice/SalePriceEntity.ts`、
`src/model/personnel/role/RoleEntity.ts`、`src/model/personnel/department/DepartmentEntity.ts`、
`src/model/asset/material/MaterialEntity.ts`、`src/model/wms/inventory/InventoryEntity.ts`

> 注：`src/model/channel/purchaseprice/` 与 `saleprice/` 的实际目录是**全小写**，而 `src/config/__tests__/channelDomain.spec.ts:106,147` 里硬编码成 `purchasePrice/` / `salePrice/`（大写 P）。Windows 大小写不敏感所以两边都能解析，但**新代码请优先用 `readdirSync` 的结果，不要照抄那份大小写**。

# SPMS 系统设计思想（Design Thinking）

> **这份文档解决什么问题**：测试报告（`docs/TEST_REPORT_2026-09-*.md`）记录「发现了什么缺陷、怎么修的」，
> 本文记录**「为什么这么修、当时还有哪些选项、为什么不选它们、下次遇到同类问题怎么判断」**。
> 前者是病历，后者是诊疗思路 —— 病历会过期，思路要能复用。
>
> **怎么读**：第 1 节是五条可判定的核心目标（每条都配了「靠什么守卫」）；第 2 节用本轮 7 个真实案例
> 走完「需求 → 逻辑拆解 → 方案选型 → 风险预判」的完整链路；第 3~5 节是可复用清单、写法模式与风险登记表；
> 第 6~7 节是待办建议与验证矩阵。只想快速上手的人读第 1、5 节即可。
>
> **本文的事实基线**：`dev` 分支，后端 907 tests / 0 fail / 1 skip，前端 66 files / 710 tests，
> Flyway `V1..V13`，6 个后端模块（`asset` / `channel` / `factory` / `personnel` / `system` / `wms`）。
> 所有数字都来自本轮实测，不是估算。

---

## 1. 核心目标（可判定，不是口号）

一条设计原则如果不能被机器判定，它就只是愿望。下面五条都配了「靠什么守住」——
守卫缺失的原则，等于没有原则。

| # | 目标 | 含义（反例长什么样） | 靠什么守住 |
| --- | --- | --- | --- |
| G1 | **单一真源** | 同一个语义有两处定义，改一处忘一处：导出按钮的显示条件曾同时由 `showExport` prop 与 `useTable({export})` 决定；初始化「跑没跑过」曾同时由锁文件与工作目录决定 | `KTable` 的 `showExport ?? table.canExport.value`；`ConfigFlag.DEV_DATA_INIT_DONE`；`designTokenContract.spec.ts`（阴影只准取 token） |
| G2 | **失败要看得见** | 静默空白页、静默 fail-open、静默吞异常：菜单指向的视图不存在时路由照样注册成功、渲染一片白、控制台干净；前端写错 `resourceKey` 时数据权限**放行**而不是报错 | `initVueRouter` 缺组件 → 跳过注册 + `console.warn` → 落 404；`resourceKey` 注册校验用例；启动期 `DataPermissionEnforceReadiness` 门禁 |
| G3 | **幂等与可重入** | 脚本只能跑一次、跑第二次就炸或数据翻倍：迁移脚本、权限同步、演示数据播种都属于这类 | Flyway 版本化 + 尾部自检 SQL；`syncPermissionCatalog()` 的「扫描结果即真相」；`shouldInitialize(...)` 纯函数真值表 |
| G4 | **契约先行、守卫自动化** | 文档说的和代码做的不一致，靠人肉发现：`LOGIN.md` 曾声称支持手机号登录而代码只查邮箱；`STARTUP.md` 曾写 V1~V10 而实际到 V13 | `menuContract` / `schemaContract` / `maskContract` / `designTokenContract` / `backendAirpowerGate` 等契约 spec；后端源码契约用例（如 `DevDataInitGateTest`） |
| G5 | **语义准确的错误码** | 用 403 表达「你参数传错了」，让调用方与监控误判为权限事故；用 500 表达「本接口不提供此操作」 | `Errors` 的错误码分层（4001/4002/403/4031-4039/404/500）+ 同文件既有口径对齐（`OutputService` L224 先例） |

> **G1 与 G2 的关系**：单一真源消灭的是「两处不一致」，看得见消灭的是「不一致发生时的静默」。
> 只做 G1 会留下「唯一真源本身错了也没人知道」的盲区，所以两者要成对出现。

---

## 2. 需求 → 逻辑拆解 → 方案选型 → 风险预判（9 个真实案例）

每个案例的固定结构：**需求原话**（用户/报告怎么说的）→ **逻辑拆解**（问题的真实形状）→
**方案选型**（候选与取舍）→ **风险预判**（会怎么坏、拿什么守）→ **验证**（怎么证明修好了）。

### 案例 A：权限目录同步（K 报告 P0-1 / P0-2，提交 `08f400cf`）

**需求原话**：「生产环境新增任何端点，在权限行落库前对所有角色都是 403『权限不存在』」；
「`loadPermission()` 只 insert-or-update、从不 delete，权限目录永不收敛（死权限码 152/463）」。

**逻辑拆解**：问题不是一个 bug，而是**三件事被塞进了一个错误的宿主**：
1. *谁触发*：权限目录同步挂在 `DevDataInitRunner`（dev 数据初始化器）里 → 被 `isDevMode` 与 `init.lock` 双重闸门挡住 → 生产环境永不同步；
2. *同步什么*：只有 upsert，没有 delete → 目录单调增长，退场端点的权限码永久残留；
3. *事务边界*：整个同步在一个 `@Transactional` 里 → 与「批量删除 + 逐条 upsert」的粒度不匹配。

拆到这一层，方案就基本自己浮出来了：**同步逻辑要从「初始化器」里搬出来，变成启动期的独立幂等步骤**。

**方案选型**：

| 候选 | 优点 | 为什么不选 |
| --- | --- | --- |
| (a) 保留在 `DevDataInitRunner`，另加一个管理员可触发的 rescan 端点 | 改动最小 | 生产仍要靠人记得点；「新增端点后必须有人手动同步」是把 G3 的责任推给运维 |
| (b) **启动期无条件同步（选中）** | 部署即收敛，零人工；扫描代码注解 = 天然真源 | 启动耗时略增（实测可忽略：305 项扫描 + 差量写） |
| (c) 构建期生成权限清单文件，启动时读文件 | 启动更快 | 多一个构建产物要维护，且与「注解即真源」重复 |

选 (b) 后立刻踩到一个**非显性风险**：给 `syncPermissionCatalog()` 加 `@Transactional` 会让 Spring 生成 CGLIB 代理，
而 `BaseService.repository` 是**字段注入** —— 代理对象上的字段是 null，实测 `/permission/getList` 直接 500 NPE。
最终形态：方法上**不加** `@Transactional`，只把需要事务的 purge 段落包进 `transactionHelper.run(...)`。

> **可复用的教训**：在字段注入的基类体系里，「给方法加个事务注解」不是无害操作。
> 需要事务时优先用显式的 `transactionHelper.run(...)` 包住**那一段**，而不是注解整个方法。

**风险预判**：
- *删除能力引入的新风险*：误删在用权限 → 角色失去入口。守卫：删除只针对「扫描结果里没有、但库里有」的项，
  并连带清理 `role_permission_list`；启动日志打出「扫描 N 项，库内 M 项，过期 K 项」，K 不为 0 时肉眼可见。
- *生产首次同步的写入量*：4 条治理权限是代码里补的、不在扫描结果中 → 不能被当作「过期」删掉（已在实现里区分）。

**验证**：`mvn -o clean test` 907/0/1；真机 `/permission/getList` 200；日志「权限目录已收敛：扫描 305 项，库内 309 项，无过期项」；
DB 实测 `permission`=309（305 扫描 + 4 治理）、`role_permission_list`=370。

---

### 案例 B：树形加载的 N+1（K 报告 P1-3，提交 `74b36f07`）

**需求原话**：「`StorageService`/`StructureService.afterGetList` 对每个节点递归调用 `getList` 查其子级，
N 个节点 = O(N) 次查询，且递归深度 = 树深（深树有栈风险）。项目里已有现成方案 `Trees.buildTreeList()`」。

**逻辑拆解**：报告已经指出「有现成方案」，但**现成方案不能直接用**，原因有两层：
1. `beforeGetList` 在 `parentId == null` 时会强制置为 `ROOT_ID` → 无法一次性取全量节点；
2. 在 `afterGetList` 里改用 `filter()` 取全量会**绕过数据权限谓词**（`buildPredicates` 注入了
   `factory.storage` / `factory.structure` 的 QUERY 策略）→ 越权读取。

也就是说：这不是性能问题，是**「性能优化与安全边界冲突」**的问题。任何方案都必须同时满足
「查询次数与节点数解耦」和「仍然走数据权限谓词」。

**方案选型**：

| 候选 | 查询次数 | 数据权限 | 结论 |
| --- | --- | --- | --- |
| (a) 现状：每节点一次 `getList` | O(N) | ✅ | 慢 + 深树栈风险 |
| (b) `Trees.buildTreeList()` 一次取全量 | O(1) | ❌ 绕过谓词 | **安全上不可接受** |
| (c) **逐层批量：新增 `queryChildrenByParentIds`，BFS 装配（选中）** | O(D)，D=树深 | ✅ 仍走谓词 | 兼顾两者 |
| (d) 递归 CTE 一次取子树 | O(1) | ⚠️ 需在 SQL 里重写谓词 | 与 JPA 谓词体系割裂，维护成本高 |

选 (c)：`attachChildren` 用 BFS 逐层调用 `queryChildrenByParentIds(parentIds)`，
每层一次 `IN` 查询。当前数据量下 D≈2~3，实测端点 200 且响应稳定。

> **可复用的教训**：看到「项目里已有现成方案」时，先问**它为什么没被用在这里**。
> 本例的答案是「它会绕过数据权限」——这个约束在报告里只有一句话，却决定了整个方案空间。

**风险预判**：
- *`IN` 列表过长*：极宽的树（某层上千节点）会拼出超长 `IN`。守卫：树深/宽目前极小（仓库 6、库位 3）；
  真出现规模问题时分批（chunk）而不是回退到逐节点查询。
- *环*：`parent_id` 成环会让 BFS 死循环。守卫：装配时按层推进且每层只处理新节点，
  同一节点不会被重复入队（实现里以 id 集合去重）。

**验证**：`mvn test`；真机 `/storage/getList`、`/structure/getList` 200；子级装配结果与改造前一致（同样的树形状）。

---

### 案例 C：退场资源的目录收敛（K 报告 P1-4，提交 `1000f0a5`）

**需求原话**：「`data_policy` 里仍有已退场资源的策略行（实测 93 行中含 `asset.device`，该模块已退场）。
建议 V13 清理 `data_resource` / `data_policy` / `data_field_policy` 中指向已退场资源的行，并加对账巡检」。

**逻辑拆解**：退场模块（MES/设备/聊天室）已经在 V12 清掉了菜单与权限身份，但**数据权限目录是另一套账**：
`data_resource`（资源）← `data_policy`（行级策略）← `data_field_policy`（列级策略），
三者由外键串联，且外键是 `NO ACTION`（不级联）。所以清理必须**按 FK 反序**，且必须幂等（迁移可能被重跑）。

**方案选型**：

| 候选 | 优点 | 为什么不选 |
| --- | --- | --- |
| (a) 启动期代码清理（在 `DevDataInitRunner` 里删） | 与权限同步同一套机制 | 数据修正是**一次性**的，放进启动路径会永久背着它；且与 G3「迁移用 Flyway」相冲突 |
| (b) **V13 迁移脚本（选中）** | 版本化、可审计、只跑一次、天然幂等 | 需要小心 FK 顺序与自检断言 |
| (c) 手工执行 SQL | 快 | 不可复现，新库/其他环境会漏 |

**风险预判 —— 本案例真的踩了坑**：脚本尾部原本写了自检 SQL `resource_id IN (7,8,9,10,11,12,18)`，
用**id 快照**断言清理结果。但 id 是历史累积的：真实退场资源 id 是 `5,6,7,8,9,10,18`，
而 `11`/`12` 是**在世的** `channel.sale` / `channel.purchase` → 自检报出 8 行「未清理」，全是假阳性。
更糟的是脚本已经执行过，改完要 `DELETE FROM flyway_schema_history WHERE version='13'` 再重启才能重新应用。

修正后的自检改为**与 id 无关的断言**：孤儿行数 = 0、`data_resource` 总数 = 17、`data_policy` 总数 = 65，
并在脚本头写明「不要用 id 快照断言，删除之后 id 集合就变了」。

> **可复用的教训（两条）**：
> 1. 迁移脚本的自检断言必须**与自增 id 解耦**（用孤儿数/总数/存在性），否则数据一变断言就撒谎；
> 2. 已应用过的迁移脚本要改，必须同步处理 `flyway_schema_history`，且改完的脚本要能在
>    「已经清理过的库」上重跑成功（幂等）—— 这两件事要一起验证，不能只做一件。

**验证**：真机 DB `data_resource`=17、`data_policy`=65、孤儿 0；`flyway_schema_history` v13 success=1；
`/dataResource/getList`、`/dataPolicy/getList` 均 200；前端 `schemaContract.spec.ts` 的迁移清单断言同步到 V13 结尾。

---

### 案例 D：初始化闸门的幂等性（K 报告 P2-10，提交 `3762b206`）

**需求原话**：「`init.lock` 的 CWD 相对语义。建议改为基于数据源的标志位（如 `config` 表 `INIT_DONE`），
使初始化幂等性与运行目录彻底解耦」。

**逻辑拆解**：「初始化只跑一次」需要一个**状态位**，状态位放哪决定了幂等性的边界：
- 放文件系统（相对路径）→ 边界是「工作目录」，而工作目录是**启动方式**的函数，不是数据的属性；
- 放数据源 → 边界就是库本身，与从哪启动无关。

库才是唯一的共享事实，所以状态位应该在库里。但直接搬会引入一个**过渡期漏洞**：
老库的标志位行还不存在，如果它首次就从别的目录启动（看不到遗留锁文件），会被重新播种、演示数据翻倍。

**方案选型**：

| 候选 | CWD 无关 | 过渡期安全 | 可显式重播种 | 结论 |
| --- | --- | --- | --- | --- |
| (a) 只认锁文件（现状） | ❌ | ✅ | ✅（删文件） | 就是问题本身 |
| (b) 只认数据源标志位 | ✅ | ❌ 老库首次换目录启动会重播种 | ✅ | 有过渡漏洞 |
| (c) 数据推导（库里已有种子用户就跳过） | ✅ | ✅ | ❌ 无法显式重播种 | 缺可运维性 |
| (d) **三态：数据源明确表态 > 遗留证据（选中）** | ✅ | ✅ | ✅ | 兼顾三者 |

(d) 的关键是**区分「数据源说没初始化」与「数据源还没这一行」**（`Boolean` 三态而非 `boolean`）：
- 行存在 ⇒ 以它为准。管理员显式置 0 就是要重新播种，遗留证据不得否决（否则文档里那条操作指引是假话）；
- 行不存在（老库）⇒ 看遗留证据：`init.lock` **或** 种子用户已在库里（与 `initRootUser` 的
  `getMaybeNull(1L)` 判据同源），任一命中即跳过并把状态迁入数据源；
- `create-drop` 是显式重建，优先级最高。

判据抽成**纯函数** `shouldInitialize(flag, lockFile, seedPresent, rebuild)`，于是整张真值表能脱离 Spring 容器测完。

**风险预判**：
- *配置项对用户可见*（会出现在「系统配置」页）→ 可能被误改。守卫：`UtilityWiringTest` 钉住它的类型与默认值
  （默认必须是 `0`，默认 `1` 等于全新库永不初始化），描述文案写明「置 0 会在开发模式重新播种，生产 `isDevMode=false` 时不起作用」。
- *新增枚举项打破既有守卫*：`UtilityWiringTest.configFlagKeysTypesAndDefaultsRemainUnchanged` 会失败
  （这是**故意**的：它逼一次「我知道我加了什么」的确认）。处置：同步清单并注明缘由，而不是删守卫。

**验证**：`DevDataInitGateTest` 7 例（真值表 + 默认值 + 源码契约）；`mvn test` 907/0/1；
真机跨目录实测 —— 把 `init.lock` 改名后从仓库根启动：日志「遗留证据命中…本次把状态迁入数据源」，
行数与基线**完全一致**（`user=5 dept=2 material=3 storage=6 customer=2 supplier=1`，未翻倍），
`DEV_DATA_INIT_DONE`=1，两处都不再出现锁文件；二次启动日志「数据源标志位已置位」，登录邮箱/手机号均 200。

---

### 案例 E：错误码的语义定位（K 报告 P2-9 / P2-8，提交 `1bd91800` / `81e10e59`）

**需求原话（P2-9）**：「『物料信息不匹配』用 `FORBIDDEN.whenNotEquals(...)`。从『调用方传了不一致的值』看
应是 `PARAM_INVALID`(4002)；但它同时承担防止跨单据串改明细的安全语义，403 在那个视角下也讲得通。
需要先确定这条校验的**定位**，再连带复核测试口径与前端提示文案」。

**逻辑拆解**：报告把球踢回给「定位」，这是对的 —— 错误码不是风格问题，是**契约**。判定标准只有一条：
*这个失败是因为「调用方的输入自相矛盾」，还是因为「调用方无权做这件事」？*
- 越权由**行级数据权限 + 所有权守卫**负责（那是真正的授权边界，且已有独立机制）；
- 「出库来源库存的物料 ≠ 明细物料」是两个入参互相对不上 → 输入一致性校验 → `PARAM_INVALID`。

同文件 L224 已有先例（「请求少传了字段」用 `PARAM_MISSING` 而非 `FORBIDDEN`，注释写明
「403 会让调用方与监控误判为权限问题」）—— **口径就近对齐**比抽象讨论更可靠。

还有一层拆解：三处调用点里只有 `OutputService` L232 真能把码抛给调用方，另两处是传给
`BaseBillDetailService#updateDetailQuantity` 的**跳行谓词**，异常在 `catch (Exception e) { continue; }` 里被吞掉
（用作行过滤器）。所以改码对那两处没有行为影响 —— 但仍然要改，理由是口径统一：
万一将来去掉那个 `catch`，不会突然开始吐 403。

**方案选型**：改码（`FORBIDDEN` → `PARAM_INVALID`）而不是保留 403 加注释。
配套核查：`grep` 后端测试与前端源码，确认没有任何测试或文案断言这几条字符串/状态码 → 改动无连带影响；
前端 `src/framework/http.ts` 也没有针对 403/4002 的特殊路由（只有注释），所以不会触发意外的登出/跳转。

**需求原话（P2-8）**：「`LOGIN.md` 与 `STARTUP.md` 都声称支持『手机号或邮箱 + 密码』登录，但代码只接受邮箱。
实测手机号登录返回 `code 4002 「请确认传入有效的邮箱」`」。

**逻辑拆解 → 关键判断：失实的是代码还是文档？** 直觉是「文档写错了，改文档」。但证据指向另一边：
- 登录页占位符写着「账号 / 邮箱 / 手机号」，且 `isValidAccount = isEmail(...) || isNaturalNumber(...)` **主动接受纯数字**；
- 实测返回的错误文案是「请确认传入有效的邮箱」—— 对着一屏写着「手机号也可以」的界面说这句话，是产品在自相矛盾。

所以按产品意图补齐后端：`getByEmail` 未命中时回落 `getByPhone`（`phone` 自 V8 起唯一键，至多命中一行）。
**刻意不做格式预判**：`PhoneRules.OPTIONAL_PHONE` 只覆盖手机号，座机/区号写法会被漏掉，而多一次空查询的代价可忽略。
两种形态都查不到仍统一返回 `200001`，不暴露账号是否存在（防枚举）。

> **可复用的教训**：「文档过期」类问题要先判定**哪一边是真相的持有者**。
> 判据是看产品意图的其它证据（UI 文案、校验逻辑、错误提示），而不是看哪边改起来便宜。
> 本例若只改文档，UI 会继续对用户撒谎。

**验证**：`mvn test` 907/0/1；真机登录矩阵 6 例 —— 邮箱 200、两个手机号 200、错误密码 200001、
不存在账号 200001（不泄露存在性）、空账号 4002（文案已改为「请确认传入有效的邮箱或手机号」）；
WMS 四端点 `/output|/input|/inventory|/move getList` 全 200（P2-9 无回归）。

---

### 案例 F：菜单 ↔ 视图的对账守卫（K 报告 P2-7，提交 `269aecd4` + `fe9ac477`）

**需求原话**：「`menuContract.spec.ts` 只校验 `src/demo/fixtures/menu.ts`，不校验真实 DB 菜单与
`src/view/console/**` 的对应关系 —— K-5 的 5 个死链正是这样漏过的。建议加一个对账用例」。

**逻辑拆解**：静态对账（种子 ↔ 夹具 ↔ 磁盘）能覆盖「代码里的菜单」，但**覆盖不到运行期下发的菜单**
（库里的行可以被手改）。这类问题只能在运行期兜。而运行期的现状是：
`componentsMap[key]` 取不到时照旧 `addRoute({ component: undefined })` → 路由匹配成功、渲染空白、控制台干净。

**方案选型**：

| 候选 | 效果 | 取舍 |
| --- | --- | --- |
| (a) 只加静态对账用例 | 覆盖不到 DB 漂移 | 就是报告指出的缺口本身 |
| (b) **运行期跳过注册 + `console.warn`（选中）** | 缺失视图 → 落 catch-all 404 + 打出菜单名与缺失键 | 404 比白屏「更吵」，但吵得有价值 |
| (c) 运行期回退到一个占位组件 | 用户看到「页面维护中」 | 掩盖问题，且占位组件本身要维护 |

选 (b)，理由是 G2：**看得见的 404 比看不见的白屏便宜得多** —— 前者控制台里有菜单名和缺失键，后者什么都没有。

**加守卫之后暴露的第二个问题（这才是本案例最有价值的部分）**：全量测试随即打出 `appRouter.spec.ts` 3 例失败。
插桩后发现夹具写的是 `component: 'dashboard'`（**不带前导斜杠**），拼出的键是 `/src/viewdashboard.vue`，
在组件表里**永远匹配不上** —— 也就是说旧实现下这条路由的 `component` 一直是 `undefined`，
而「路由注册成功」的断言照样通过。**这些用例一直在为静默空白页背书。**

修法不是改夹具去迁就实现，而是补真实的健壮性缺口：对 `componentPath` 做前导斜杠归一化
（库里的 `menu.component` 两种写法都真实存在，少一个斜杠就整页 404），
并新增一条契约用例锁住归一化，注释写明「不是洁癖」以防后人当冗余清掉。

> **可复用的教训**：加守卫之后**跑全量**，不要只跑新增用例。本例若只跑 `menuNavigation`（当时 15/15 全绿就提交了），
> 就会把一个「既有测试其实在测错误行为」的事实继续埋着。守卫的价值常常不在于它拦下什么，
> 而在于它**让原本通过的测试失败** —— 那些失败通常指向真正的缺陷。

**验证**：`pnpm typecheck` 干净；`appRouter` + `menuNavigation` + `menuContract` 23/23；
全量 66 files / 710 tests；新增幽灵菜单用例断言「未注册 + 落 catchAll + 告警含缺失键」。

---

### 案例 G：视觉一致性的 token 化与棘轮（Track 3，提交 `25a56937`）

**需求原话**：「美化页面视觉效果、统一展示风格，提升代码可复用性/模块化/组件化/常量化/规范化/工程化」。

**逻辑拆解**：「统一风格」的敌人不是某个页面丑，而是**同一个语义在十几个文件里各写一遍字面值**。
写死 `box-shadow: 0 1px 3px rgba(0,0,0,.02)` 的后果是具体的：暗色模式下同一份黑色落影在深色背景上等于没有，
而改一次视觉要全仓搜替，漏一处就是不一致。

**方案选型**：
- *只做一次性搜替*：能清零存量，但下一个 PR 又会写死 → 无效；
- ***token 分层 + CI 棘轮*（选中）**：先补齐 token 层级（阴影 `sm/base/lg/overlay`，明暗两套），
  再把「不许写死」做成 spec（扫 `src/**/*.vue` 的 `box-shadow` 值里出现 `rgb/rgba/#hex` 即失败）。

棘轮第一次跑就抓出**用窄正则没搜到的 2 处**（`BillStatusFilter` 的白色光晕、`TenantSwitcher` 的 inset 描边）。
这两处的语义不是「投影」而是「描边环」，硬套 `--kubo-shadow-*` 会把「描边」错当成「高度」，
于是单独立了一组 ring token：`--kubo-ring-halo`（取 `var(--el-bg-color)`，跟着行背景走，明暗自动正确）
与 `--kubo-ring-inset-on-brand`（用在品牌色头部里的透明输入框，两种主题背景都是深色，故无需暗色覆盖）。

> **可复用的教训**：token 化的难点从来不是替换字面值，而是**先把语义分对类**。
> 分类错了（把描边塞进投影层级），后面每一次暗色适配都要为它打补丁。

**风险预判**：棘轮范围过宽会误伤（例如把「颜色字面值」一次性拉闸，存量太大 → 守卫被绕过或删掉）。
处置：把范围**刻意收窄到已经有完整 token 层级的阴影**，并在 spec 文件尾把扩展路线写进代码
（下一步 transition 时长/缓动 → border-radius → 颜色；每加一类先跑出存量清单、清零后再从告警升级为失败）。

**验证**：`designTokenContract` 3/3；全量 710 tests；`pnpm build` 成功（2443 modules / 32.6s，证明 SCSS 与新 token 能编译）。

---

### 案例 H：把 20 步质量链路从「第 1 步就断」修到全绿（K 报告 P1-3 / 本文 R10，提交 `bef31e7d` + `e305a657` + appConfig 修复）

**需求原话**：K 报告 §4 P1-3 记「`pnpm check` 的 21 步链从未整体跑过，只分项验证过」；本文 R10 把它列为「⚠️ 未知风险」。

**逻辑拆解**：一条 `&&` 串联的链路，价值等于**最弱的那一步**——只要有一步长期红着，整条链在工程上就等于不存在
（没人会去跑一条注定失败的命令，于是所有步骤都退化成"自觉执行"）。所以第一步不是修某处代码，而是**先跑一次，看它到底断在哪**。
实跑结果：断在**第 1 步**；修完第 1 步又断在第 4 步；再修又断在第 6 步；再修断在第 8 步。四个断点性质完全不同：

| 断点 | 报错 | 真值来源（判定依据） | 谁错了 | 处置 |
| --- | --- | --- | --- | --- |
| ① `lint:ci`（第 1 步） | 11 errors | eslint 规则本身：`regexp/no-super-linear-backtracking` 指出 `\s*` 与 `[^;]*` 可交换 → 多项式回溯；另一处是我自己误删换行把两条语句粘成一行 | **代码错**（本轮新增的两个 spec） | 去掉多余量词并写明「为什么不能加回去」；恢复换行 |
| ② `pom:check`（第 4 步） | `com.mysql:mysql-connector-j` 没写 `<version>` 且不在 BOM 白名单 | 直接读 `~/.m2/.../spring-boot-dependencies-3.4.4.pom`：BOM 里明确有该坐标，`${mysql.version}` = **9.1.0**，本地仓库也有 9.1.0 的 jar；离线构建与真机连库一直正常 | **守卫错**（脚本白名单缺项 → 报的是**假问题**） | 按脚本自己的规矩（"读过 BOM 才加"）补精确坐标；顺带修掉一句自相矛盾的报告文案 |
| ③ `exports:check`（第 6 步） | 7 个「没人 import 的导出」 | 全仓 import 扫描 + 逐项人工确认（其中 2 个在 SFC 里，脚本不动 `.vue` 偏移） | **代码错**（存量死导出） | 脚本自动清理 2 个文件（删 `occupancyLegend`、收回 3 个类型、删 1 条 re-export），人工收回 2 个 SFC 的 `export` |
| ④ `test:coverage`（第 8 步） | 731 例全绿，但 `Coverage for branches (74.39%) does not meet global threshold (75%)` | v8 覆盖率报告逐文件分支数：`palette.ts` branch 12.5% / funcs 11%，`errorHandler.ts` branch 25% | **代码错**（两个纯逻辑模块没测；配置注释里的"基线 85% branches"是错的，正是它让人误判余量充足） | 补 21 例（palette 17 + errorHandler 4）→ 分支 74.39% → **79.22%**；**不下调阈值** |

**方案选型**：④ 处有两条路——把阈值从 75% 降到 70%（1 分钟，链路立刻绿）或补测试（半小时，链路绿且质量真涨）。
选后者：阈值是"地板"不是"目标"，**因为跑不过就下调，等于把闸门改成装饰**，而且下一次改动还会以同样的方式再断一次。
②处同样有两条路——往 `pom.xml` 里硬写 `<version>9.1.0</version>`（让守卫闭嘴）或修守卫的白名单。
选后者：硬写版本会与 BOM 的 `dependencyManagement` 冲突，Spring Boot 升级时这个坐标不会跟着 BOM 走，
将来出现"其他依赖升了、驱动没升"的版本漂移。**守卫报错时的第一反应不该是改守卫、也不该是改代码，而是先找真值来源。**

第 8 步绿了之后还暴露出一个**抖动**：`appConfig.spec.ts` 在 coverage 步骤报 `Test timed out in 30000ms`，
而它单独跑只要 8.9s。根因是用例里写 `await import('@/config/AppConfig')`，把整条依赖图的转换 + v8 插桩
算进了 `it` 的 30s 时钟；机器一忙（那一轮 transform 从 50s 涨到 86s）就顶穿。
处置：这两个用例没有 `vi.resetModules()` 的顺序需求，改成顶层静态导入即可把成本挪到收集阶段；
**刻意不调全局 `testTimeout`**——那会同时放宽其余 730 个用例的失败检测灵敏度，用全局代价换局部问题。
（仓库既有先例：`menuSmoke.probe` 单个用例要跑 55~67s，靠的是自带超时。）

**风险预判**：
- 收回 `export` 有真实回归风险（若确有跨文件引用，`typecheck` 会炸）→ 清理后必须跑 `typecheck` + 全量测试，两者都绿才算完（已跑：731/731）。
- 覆盖率闸门余量太薄（74.39 vs 75，只差 0.61 点）时，任何一次新增未测代码都会再次断链 → 补到 79.22%（余量 4.2 点），并把**实测**基线写进 `vitest.config.ts` 注释，替换掉那句错的"~85% branches"。
- 链路是 `&&` 串联，只暴露第一个断点 → 定位时单独跑同名脚本（`pnpm pom:check`）比整链快一个数量级；但**修完必须整条重跑**，本轮共重跑 5 次（4 次定位 + 1 次全绿）。

> **可复用的教训**：一条从未整体跑过的质量链路，通常不是"还差一点"，而是**从第一步就断着**，
> 且断点里往往混着「守卫自己错了」的假问题。判据只有一个：找到该项的真值来源
> （BOM 文件、eslint 规则、覆盖率报告、UI 文案），而不是看谁报错声音大。

**验证**：`pnpm check` **20 步全绿、退出码 0、总耗时 210.4s**；其中 `test:coverage` 731/731、
All files 84.08% stmts / 79.22% branch（阈值 75%）、`build` 2443 modules / 34.47s、
`docker:check` 10 项达标、`text:check` 1057 文件无 U+FFFD、`auth:check` 无隐式公开端点、
`perms:check` 前端权限码全部能对应到后端端点。

---

### 案例 I：错误码分流 —— 先确认「后端怎么表达失败」，再写前端（本文 R9 / P1-2）

**需求原话**：本文 §5 R9「前端缺 403/4002 的全局处理，`src/framework/http.ts` 仅有注释」、§6 P1-2「按码分流：403 → 无权限说明与申请入口；4002 → 表单定位」。

**逻辑拆解**：动手写前端之前先问三件事，顺序错了就会写出漂亮的死代码。

1. **后端到底怎么表达失败？** 读三个 advice（`BusinessExceptionAdvice` / `RequestBindingExceptionAdvice` /
   `DataIntegrityViolationAdvice`）——全部标了 `@ResponseStatus(HttpStatus.OK)`，注释写明「沿用 HTTP 200 + 业务码，
   不擅自改成真实 401/403」。也就是说 `axios.ts` 里 `switch (status)` 的 `case 403` / `case 422`
   对后端业务错误是**死分支**，真正承接的是「HTTP 200 但 code≠200」那一支 —— 而那一支此前**只 reject、不提示**。
2. **不信推断，真机抓取**（本机 8080，admin 与受限账号张三两个身份）：

   | 探针 | 实测结果 |
   | --- | --- |
   | `POST /user/login {"email":"","password":"x"}` | **HTTP 200** `code=4002` `请确认传入有效的邮箱或手机号` |
   | `POST /user/login`（密码错） | **HTTP 200** `code=200001` `用户账号或密码错误` |
   | `POST /permission/getList`（张三，无该权限） | **HTTP 200** `code=403` `你无权访问 权限-不分页查询 (Permission_getList)` |
   | 同一端点用 admin | **HTTP 200** `code=200` `获取成功` |

   第三条是意外收获：**403 文案里带着权限码**。于是指引可以写成「请联系管理员为当前角色开通权限「Permission_getList」」——
   用户截图给管理员，管理员直接搜到那一条授权，不必再猜"到底是哪个权限"。
3. **用户需要知道的是"下一步做什么"，不是"失败了"**：403 → 开通哪条、找谁；4002 → 哪一项错了
   （长表单里只弹一句文字，用户还得自己翻）。
4. **加提示前先查会不会造成双份提示** → 查出 `RequestConfig.showError` / `customErrorHandler` 声明了却从没被读过，
   且 `framework/http.ts` 的 `send()` 组装配置时没带 `showError`，`hideError()` / `throwError()` 是**空操作**。
   先修通道，再加提示。

**方案选型**：

| 决策点 | 备选 | 选择与理由 |
| --- | --- | --- |
| 「码 → 文案」放哪 | 散在各调用点 / 拦截器里 switch / 独立纯逻辑模块 | **独立 `src/api/errorPolicy.ts`**：散在调用点会让同类错误在不同页面说法不一；写在拦截器里无法穷举测试（要挂 jsdom + mock axios）；纯函数可在 node 下逐码断言，也已纳入覆盖率 include |
| 403 指引 | 通用一句「请联系管理员」 / 带权限码 | **能解析出权限码就带上**，解析不出退回通用话术（不硬编一个假码） |
| 并发失败 | 每条都弹 / 同一「码+接口」窗口内一次 | **3s 去重**：列表页常并发 3~5 个请求，没权限时同时失败，逐条弹就是 5 条同样的红条糊屏。但**字段定位信号不去重** —— 弹窗可以省，把出错项滚到视野内不该省 |
| 字段定位怎么传到表单 | 拦截器直接拿 formRef / 全局事件总线 / 模块级 ref 信箱 | **ref 信箱**（`src/stores/fieldError.ts`）：拦截器拿不到 formRef；事件总线无类型约束、容易漏 off；一个瞬时信号用模块级 `ref` 足够，测试可直接 import（Pinia 还得先 `setActivePinia`）。不用 Pinia 的判据是「它要不要活着穿过路由」 |
| 静默口子 | 只按码静默 / 只按调用方意图静默 | **两者都要**：200001 由登录页自己呈现（按码）；`hideError()` 的调用点自己 catch（按意图）。少任何一个都会出现双份提示 |

**风险预判**：
- 文案里带后端原文有信息泄露面 → 500 类指引统一要求提供 **traceId**（而非堆栈）；数据库约束类消息由后端
  `DataIntegrityViolationAdvice` 负责改写成用户可读文案，前端不再兜底拼接 SQL 片段。
- 去重窗口太长会吞掉用户重试后的新错误 → 取 3s，并暴露 `resetNotifyDedupe()` 供「手动重试」清空。
- 字段名解析错会把用户带到无关输入框 → 解析不出就不定位（宁缺勿错），消费方还要再校验该字段真在本表单的 `fields` 里。
- `scrollToField` 依赖 `ElFormItem` 的 `prop` 与后端字段名同源（本项目确实同源：`prop` 就是实体属性名），
  一旦有人改前端 key 命名就会静默失效 → 消费前的 `fields.some(...)` 校验能让它"不生效"而不是"跳错地方"。

> **可复用的教训**：做前端错误处理之前，先确认后端用 **HTTP 状态码**还是**业务码**表达失败。
> 这条前提错了，写得再漂亮的 `switch (status)` 都是死代码 —— 本仓的 `case 403` 就这样静静躺了很久，
> 每个人都以为它在生效。判定方法很便宜：抓一次真实响应看 `HTTP status` 与 body 里的 `code` 是否一致。

**验证**：新增/扩充 4 个 spec 共 **52 例**（errorPolicy 24 · fieldError 6 · httpOption 5 · axios 拦截器扩 10 等），
真机抓取的四类响应码与文案作为夹具写进用例；`pnpm lint:ci` 0 error、`pnpm typecheck` 干净、`pnpm check` 全链复跑。

---

## 3. 可复用的现成方案（先找轮子，再造轮子）

本项目已有的可复用资产，按「用它而不是自己写」的优先级排列：

| 能力 | 现成方案 | 什么时候**不要**用它 |
| --- | --- | --- |
| 树形装配 | `Trees.buildTreeList()`（O(n)、零额外查询），`Permission`/`Menu`/`Department` 控制器都在用 | 当取全量会绕过数据权限谓词时（见案例 B）→ 改用逐层批量 |
| CRUD 骨架 | `BaseEntity` / `BaseService` / `BaseController`（分页、过滤、审计、`addAndGet`/`updateToDatabase`） | 需要绕过谓词或跳过审计的批量运维操作 |
| 事务 | `transactionHelper.run(...)`（显式段落级事务） | 在字段注入的基类体系里给方法加 `@Transactional`（CGLIB 代理会让 `repository` 变 null，见案例 A） |
| 错误码 | `Errors`（4001/4002/403/4031-4039/404/500）+ `CustomError`（业务码，如 200001） | 用异常类型表达业务分支（如用 `IllegalStateException` 表达「本接口不提供」，K-9 已修） |
| 数据权限 | `DataPermissionService#findResource` + `buildPredicates` + `effectiveMode(resourceKey)` | 直接 `filter()` 取全量（绕过谓词 = 越权） |
| 表格/查询 | `KTable` + `useTable`（权限、导出、列掩码单一真源） | 页面自己算「该不该显示导出按钮」（两个真源，见 G1） |
| 视觉 | `src/styles/tokens.scss` 的 `--kubo-*` 语义 token（颜色/圆角/阴影/间距/动效/字体，明暗两套） | 在 `.vue` 里写死 `rgba`/`#hex`（`designTokenContract.spec.ts` 会失败） |
| 迁移 | Flyway `V1..V13` + 尾部自检 SQL | 用启动期代码做一次性数据修正（见案例 C） |
| 配置开关 | `ConfigFlag` + `ConfigService.getOrNull(flag)`（可缺失、缺失走默认值） | 新增文件系统标志位（CWD 敏感，见案例 D） |

**判断「该不该自研」的三条判据**（本项目实际用的）：
1. 现成方案是否**绕过某条安全/一致性边界**？绕过就不用（案例 B 的 `buildTreeList`）；
2. 现成方案是否让**状态跑到数据之外**（文件、内存、构建产物）？是就不用（案例 D 的锁文件）；
3. 自研是否只需要**薄薄一层**且能被契约测试钉住？是就自研（`appRouter` 替代 airpower `RouterUtil`，
   三套 spec 守行为对齐）。

---

## 4. 更简洁 / 可维护 / 可扩展的写法（模式清单）

每条都是「反例 → 正例 → 为什么 → 谁守着」，可直接照抄。

| 模式 | 反例 | 正例 | 为什么 | 守卫 |
| --- | --- | --- | --- | --- |
| **单一真源** | `v-if="showExport"` 与 `useTable({export:true})` 各算一遍 | `v-if="showExport ?? table.canExport.value"` | 显式 prop 优先，缺省回落到唯一算法 | `maskContract.spec.ts` |
| **决策纯函数化** | 判据散在 `run()` 的 if 链里，只能靠启动容器验证 | `static boolean shouldInitialize(flag, lock, seed, rebuild)` | 真值表可脱离容器穷举；启动类逻辑最容易「改了没人测」 | `DevDataInitGateTest` |
| **批量优先** | `list.forEach(item -> query(item.getId()))` | `queryChildrenByParentIds(ids)` 逐层 BFS | O(N) 次查询 → O(D) 次；同时消除深树栈风险 | 树接口用例 + 真机响应 |
| **错误码语义对齐** | `FORBIDDEN.whenNotEquals(a, b, "物料信息不匹配")` | `PARAM_INVALID.whenNotEquals(...)` | 403 会让调用方与监控误判为权限事故；就近对齐同文件既有口径 | `Errors` 分层 + 契约测试 |
| **失败要吵** | `addRoute({ component: undefined })` | 跳过注册 + `console.warn(菜单名, 缺失键)` → 落 404 | 白屏无线索，404 + 告警可直接定位 | `menuNavigation.spec.ts` |
| **输入归一化** | 直接把库里的字符串拼进键 | `path.startsWith('/') ? path : '/' + path` | 库里两种写法都真实存在，少一个斜杠就整页 404 | `appRouter.spec.ts` 归一化用例 |
| **迁移自检与 id 解耦** | `WHERE resource_id IN (7,8,9,...)` | 孤儿数 = 0、总数 = 17、存在性断言 | 删除之后 id 集合就变了，快照断言会撒谎 | 迁移脚本尾部 SQL |
| **迁移只追加不重排** | 改已应用的 V11 | 新问题写 V13，追加在尾部 | 重排会让已部署环境的校验和失配 | `schemaContract.spec.ts` |
| **三态而非布尔** | `boolean done = flag != null && flag.isDone()` | `Boolean flag`（`null` = 行不存在） | 「明确说没有」与「还没这一行」是两件事，合并会引入过渡漏洞 | `DevDataInitGateTest` |
| **常量化 + 棘轮** | 每处写死 `rgba(0,0,0,.02)` | `var(--kubo-shadow-sm)` + CI 扫描 | 暗色适配与全局改视觉都只需改一处 | `designTokenContract.spec.ts` |
| **注释写「为什么」和「别改回去的理由」** | `// 设置阴影` | 写明载荷性约束（如 `@import` 提升与 `@use` 顺序冲突）与踩过的坑 | 没有理由的约束会被后人当冗余清掉 | 代码评审 |
| **先确认失败的表达方式** | 用 `switch (httpStatus)` 处理后端业务错误 | 先抓一次真实响应：本仓业务失败是 **HTTP 200 + 业务码**，分流必须挂在 `code≠200` 那一支 | 前提错了整段代码是死的，而且"看起来在生效"（`case 403` 就这么躺了很久） | `errorPolicy.spec.ts` + 真机探针（案例 I） |
| **提示要可操作** | `ElMessage.error('没有权限访问')` | `无权限：你无权访问 权限-不分页查询（请联系管理员为当前角色开通权限「Permission_getList」）` | 用户要的是"下一步做什么"；带上权限码，管理员搜一下就能开通 | `errorPolicy.spec.ts` 用真机文案作夹具 |
| **选项要真的传下去** | `hideError()` 只改内部字段，`send()` 组装配置时不带它 | 下发 `showError`，并逐项断言最终 config | 名不副实的选项比没有选项更坏：调用方以为自己静默了，实际收到双份提示 | `httpOption.spec.ts` |

---

## 5. 风险登记表（Risk Register）

| # | 风险 | 触发条件 | 现有守卫 | 缺口 | 建议 |
| --- | --- | --- | --- | --- | --- |
| R1 | 事务注解破坏字段注入 → 运行期 NPE | 给继承 `BaseService` 的方法加 `@Transactional` | 无（靠人知道） | ⚠️ 高 | 加一条源码契约：禁止在 `BaseService` 子类方法上直接标 `@Transactional`，改用 `transactionHelper.run` |
| R2 | 数据权限静默 fail-open | 前端/后端写错 `resourceKey`，`findResource` 返回 null → 放行 | 「前端引用的 resourceKey 都必须在后端注册」用例 | 只覆盖前端引用点 | 后端也加：`enforce-resources` 白名单与 `data_resource` 对账（启动期 `DataPermissionEnforceReadiness` 已做一半） |
| R3 | 菜单漂移导致白屏/404 | 库里 `menu.component` 被手改、视图被删改名 | 静态三方对账 + 运行期跳过注册与告警 | 告警只在浏览器控制台 | 可选：把缺失视图计入启动/巡检日志，便于无头环境发现 |
| R4 | 迁移脚本改坏已部署环境 | 修改已应用的 V1~V13 | `schemaContract.spec.ts` 钉清单与顺序 | 校验和变化只能靠 Flyway 启动失败发现 | 保留「改已应用脚本必须同步 `flyway_schema_history`」的规程（案例 C 踩过） |
| R5 | 演示数据重复播种 | 换工作目录启动 / 标志位被误改 | 三态闸门 + 真值表用例 | `config` 表里的标志位对用户可见 | 描述文案已写明；如需更严可加「置 0 需要二次确认」 |
| R6 | `updateDetailQuantity` 吞掉所有异常 | 传入的 `detailCheck` 抛任何异常都被 `catch { continue; }` | `BillDetailQuantityFilterTest`（4 例） | ✅ 已处置 | 已改为 `Predicate<E> detailFilter`：返回 false 跳行，异常照常冒泡（提交 `3b3b7e35`） |
| R7 | 错误码语义漂移 | 新代码用 403 表达输入错误 | 同文件先例注释 | 无自动化 | 见 §6 P2-1：补一条「FORBIDDEN 只用于授权边界」的源码扫描 |
| R8 | 退场模块的历史表长期滞留 | `ddl-auto=update` 不删表：`bom`/`device`/`operation`/`picking`/`plan`/`routing`/`room`/`member`/`orders` 等仍在库里且仍有历史行 | V12/V13 清了目录，没清表 | 新人会以为这些域还在 | 见 §6 P2-2：出一份「遗留表清单 + 观察期后 DROP 的迁移」文档 |
| R9 | 前端缺 403/4002 的全局处理 | 后端返回 403/4002 时前端只有通用提示 | `errorPolicy.spec.ts`（24 例）+ axios 拦截器 10 例 | ✅ 已处置 | 已按码分流：403 带**权限码**的开通指引、4002 解析字段名并把出错项滚到视野内、并发失败 3s 去重（提交见 §8；案例 I） |
| R10 | `pnpm check` 21 步链从未整体跑通 | 一次性全跑可能暴露步骤间依赖/端口冲突 | **本轮已整体跑通：20 步全绿，退出 0，210.4s** | ✅ 已处置 | 见案例 H：链路原先断在第 1/4/6/8 步（lint、pom:check、exports:check、覆盖率闸门），四处断点分别判定「代码错 vs 守卫错」后修通（提交 `bef31e7d` / `e305a657` / `fix(test) appConfig`） |

---

## 6. 补充优化建议（按优先级，含验收判据）

### P1（会造成静默错误或阻碍交付）

1. **`updateDetailQuantity` 的谓词化**（R6）—— ✅ **本轮已完成**（提交 `3b3b7e35`）
   原状：`Consumer<E> detailCheck` 抛异常即 `continue`，异常被当作控制流；真实故障（NPE、懒加载失败、
   数据权限拒绝、写库失败）会被静默降级成「跳过这一行」，症状是数量没被反写、账对不上、日志里什么都没有。
   现状：签名改为 `Predicate<E> detailFilter`，`false` = 跳行（正常业务分支），异常 = 真故障照常冒泡；
   `InputService`/`OutputService` 两处调用点改为布尔谓词（物料一致性的**校验**仍在 `OutputService` L235
   用 `PARAM_INVALID` 做，行筛选不再借用异常）。
   *已满足的验收*：`BillDetailQuantityFilterTest` 4 例覆盖「false 跳行不写库」「谓词异常冒泡」
   「数量按行消耗且全部完成后回写单据状态」「写库故障冒泡」；`mvn -o clean test` 911/0/1。
   *踩坑备忘*：测试实体必须给不同 id —— `BaseEntity` 的 `@EqualsAndHashCode` 按字段比较，
   两行 id 都为 null 时互相相等，Mockito 的 `verify(never())` 会把另一行的调用算成命中。
2. **前端 403/4002 分流**（R9）—— ✅ **本轮已完成**（见案例 I）
   *已满足的验收*：`src/api/errorPolicy.ts` 有按码分流的**实现**（不只是注释）；403 给出「开通哪条权限、找谁」
   的可操作指引（权限码从后端真机文案里解析）；4002 解析出字段名 → `fieldError` 信箱 →
   `KFormDialog` 用 EP 的 `scrollToField()` 把出错项滚到视野内；`pnpm test` 有 52 例对应断言。
   *顺带修掉的死契约*：`showError` / `customErrorHandler` 此前声明了却没人读，`Http.send()` 也没下发
   `showError` —— `hideError()` / `throwError()` 是空操作。不先修它，加全局提示就会让"自己 catch 的调用点"收到双份提示。
3. **`pnpm check` 全链跑通**（R10）—— ✅ **本轮已完成**（见案例 H）
   *已满足的验收*：一条命令 **20 步全绿、退出码 0、总耗时 210.4s**（lint → airpower:check →
   airpower:backend → pom:check → deadcode:check → exports:check → typecheck → test:coverage →
   perms:check → auth:check → text:check → ddl:check → migration:check → monitoring:check →
   canary:check → docker:check → drill:check → build → demo:check → size）。
   注：K 报告写「21 步」，实测链上是 20 个 `&&` 步骤。
   *失败重试策略*（本轮实际用到的）：链路是 `&&` 串联，**任一步失败即中断，只暴露第一个断点**，
   所以修完一步必须整条重跑（本轮共重跑 5 次：4 次定位断点 + 1 次全绿）；
   单独跑某一步用同名脚本（如 `pnpm pom:check`）比整链快一个数量级，适合定位；
   `test:coverage` 与 `build` 各占约 60s / 35s，是链路里最慢的两步，改前端时可先跑 `pnpm test` 再上链。
   *不要与后端 `mvn` 并发跑*：`mvn clean` 会清掉 `target/`，正在运行的后端会失联；
   同理链路跑 `build` 时别再手动 `pnpm build`。

### P2（一致性与可维护性）

1. **`FORBIDDEN` 用途扫描**（R7）：新增源码契约 —— `Errors.FORBIDDEN*` 只允许出现在授权判定路径，
   输入校验一律 4001/4002。*验收*：spec 扫全仓 `FORBIDDEN.` 调用点并白名单化。
2. **遗留表清单与退场计划**（R8）：出一份文档列出 `bom`/`device`/`operation`/`picking`/`plan`/`routing`/
   `room`/`member`/`orders`/`order_detail`/`parameter`/`device_parameters`/`routing_operation`/
   `structure_operation_list` 等历史表，标注「无实体、无页面、不播种」，并给出观察期后 `DROP` 的 V14 迁移草案。
   *验收*：文档存在；V14 草案在开发库上演练过且可回滚说明清楚。
3. **退场域的配置项清理**：`ConfigFlag` 里 `ORDER_AUTO_FINISH`(11)、`ORDER_MANUAL_FINISH`(12)、
   `ORDER_ENABLE_SUBMIT_WORK`(18)、`ORDER_AUTO_START_AFTER_AUDIT`(19)、`PICKING_BILL_AUTO_AUDIT`(20)
   属于已退场的 MES 域，仍会在「系统配置」页显示为可开关项。*验收*：确认无代码消费后，
   用一条迁移把这些 `config` 行标记为停用或删除，并同步 `UtilityWiringTest` 的清单守卫。
4. **token 棘轮扩展**（案例 G 的路线）：按 transition 时长/缓动 → border-radius → 颜色 的顺序推进。
   *验收*：每类先跑出存量清单并清零，再把断言从告警升级为失败。

### P3（体验与工程化增益）

1. **性能基线**：给树接口（`/storage/getList`、`/structure/getList`）与列表接口建立 p95 基线，
   让「N+1 回归」能被数字发现而不是被人发现。
2. **E2E 冒烟**：登录 → 打开一个列表 → 导出，三步的最小 E2E（当前只有真机手工冒烟）。
3. **数据权限证据面板可视化**：`data_permission_diff_log` / `deny_log` / `orm_parity_log` 已有数据，
   缺一个能一眼看出「哪条策略在生效、哪次拒绝发生在哪个字段」的界面。

---

## 7. 验证矩阵（怎么证明「真的好了」）

| 层面 | 命令 | 期望 |
| --- | --- | --- |
| 后端单测/契约 | `$env:JAVA_HOME='D:\JAVA\JDK\jdk-18.0.2.1'; mvn -B -o clean test`（在 `SPMS-Server/`） | `Tests run: 911, Failures: 0, Errors: 0, Skipped: 1` + `BUILD SUCCESS` |
| 前端类型 | `pnpm typecheck`（vue-tsc） | 无输出即通过 |
| 前端测试 | `pnpm test` | `Test Files 70 passed (70)` / `Tests 770 passed (770)` |
| 前端覆盖率闸门 | `pnpm test:coverage` | 退出 0；All files 85.14% stmts / **81.16% branch** / 85.23% funcs（阈值 lines 50 / funcs 65 / stmts 50 / **branch 75**） |
| 前端构建 | `pnpm build` | `✓ built in ~39s`（2443 modules） |
| **全链（20 步）** | `pnpm check` | **退出码 0，总耗时 ~203s**；任一步红即中断，只暴露第一个断点（定位用同名单步脚本，见案例 H） |
| 部署预演 | `pnpm deploy:drill` | 19 项全通过 |
| 迁移/DDL 一致性 | `pnpm migration:check`、`pnpm ddl:check` | 全绿 |
| 真机（启动） | `Start-Process cmd.exe "/c set JAVA_HOME=...&& mvn -B spring-boot:run > log 2>&1"` | 端口 8080 就绪；日志含「权限目录已收敛」「演示数据已初始化（数据源标志位已置位）」 |
| 真机（登录） | `POST /user/login {"email":"admin@hamm.cn","password":"Aa123456"}` | `code=200`，token 长度 83；手机号 `17666666666` 同样 200 |
| 真机（数据） | `mysql -h 127.0.0.1 -uroot -proot kubo -e "..."` | `data_resource`=17、`data_policy`=65、`permission`=309、`role_permission_list`=370、孤儿 0 |
| 真机（错误码） | 受限账号 `POST /permission/getList`；空账号 `POST /user/login` | 均为 **HTTP 200**：`code=403` `你无权访问 权限-不分页查询 (Permission_getList)`；`code=4002` `请确认传入有效的邮箱或手机号`（案例 I 的设计前提） |

**踩坑备忘（省后来人的时间）**：
- `mvn test` 与真机冒烟**不要并发**：`mvn clean` 会清 `target/`，运行中的后端会失联（本轮出现过
  「无法连接到远程服务器」）；
- PowerShell 里 `` -e "SELECT ... FROM \`$t\`" `` 的反引号会被 PS 吃掉 → 用单引号拼接：
  `'SELECT COUNT(*) FROM `' + $t + '`'`；
- 读含中文的日志/文件用 `[System.IO.File]::ReadAllText` 或 Node（PS 5.1 的 `Get-Content` 会把 UTF-8 渲染成 GBK 乱码）；
  运行中的日志被进程占用时，用 Node 的 `readFileSync`（共享读）；
- `git commit` 多行消息用 `-F <utf8 文件>`，多个 `-m` 会被当 pathspec；
- `web_fetch` 解析不了 `127.0.0.1` → 用 `Invoke-RestMethod`；
- 本仓 eslint 有几条容易踩的排版规则：`perfectionist/sort-imports`（**external 必须在 internal 之前**，
  即 `vitest` 要写在 `@/config/...` 前面）、`perfectionist/sort-named-imports`（大小写敏感的自然序，
  `OCCUPANCY_LEVELS` 要排在 `occupancyColor` 前）、`test/prefer-lowercase-title`
  （`it('...')` 标题不能以大写字母开头，`it('CSS 变量…')` 会被拦，改成中文起头即可）；
- **不要把重的动态 `import()` 写在 `it()` 里**：`await import('@/config/AppConfig')` 会把整条依赖图的
  转换 + 覆盖率插桩算进 `testTimeout`（30s），在 `test:coverage` 叠加机器负载时随机超时。
  没有 `vi.resetModules()` 的顺序需求就用顶层静态导入；确实需要长超时的（如 `menuSmoke.probe` 55~67s）
  按仓库既有做法给该用例单独传超时，不要动全局值；
- 写完新 spec 记得跑 `pnpm lint:ci`：`pnpm test` 不检查排版，而 `pnpm check` 的第 1 步就是它；
- PowerShell 函数里**别用 `Write-Output` 打进度**：它会并进函数的返回值（调用方一旦 `| Out-Null`
  或赋值给变量，进度行就全没了，看起来像"什么都没发生"）。要打印用 `Write-Host`，
  或者把返回值与日志分成两条管道；
- 跑 `pnpm check` 时**别并发别的重活**（后端 JVM、另一个 vitest、`mvn`）：`test:coverage` 的 worker 会被压到
  `FATAL ERROR: AlignedAlloc Allocation failed - process out of memory`，pnpm 报
  `exit 3221226505`（即 `0xC0000409`）。这**不是测试失败**，是进程被内存压死 ——
  处置是先停掉后端与残留的 node/java 进程再单独重跑，而不是去改用例或调阈值；
- 后台任务莫名以 `exit 2` 结束、且没写出日志文件时，别急着重跑：先看是不是同一时刻有别的重活在抢内存。
  要让日志一定落盘，用 `cmd /c "pnpm check > log 2>&1"` 直接重定向，而不是等脚本结束再 `WriteAllText`；
- 新增导出前先想清楚"谁会 import 它"：`pnpm exports:check` 会把只有测试引用的**类型**导出也判为死导出
  （本轮 `BusinessErrorKind` / `ClassifiedBusinessError` / `BusinessErrorInput` / `FieldErrorNotice` 四个都被拦），
  按仓库约定先收回 `export`，等真有消费者再放开。

---

## 8. 附：本轮提交与 K 报告条目对照

| 提交 | 内容 | K 报告条目 |
| --- | --- | --- |
| `08f400cf` | 权限目录同步解耦 + 过期清理（去 `@Transactional`，purge 走 `transactionHelper`） | P0-1、P0-2 |
| `74b36f07` | Storage/Structure 树子级加载改逐层批量（`attachChildren` BFS + `queryChildrenByParentIds`） | P1-3 |
| `5c793247` | 7 个 datapermission 实体删除遮蔽 `BaseEntity` 的重复 `@Id` | P2-6 |
| `1000f0a5` | V13 收敛退场资源的 `data_resource`/`data_policy`/`data_field_policy` 引用 | P1-4 |
| `1bd91800` | 「物料信息不匹配」`FORBIDDEN`(403) → `PARAM_INVALID`(4002) | P2-9 |
| `81e10e59` | 账号密码登录支持手机号；LOGIN/STARTUP/README 全面重写 | P2-8 |
| `269aecd4` | 菜单指向缺失视图时跳过注册并告警（不再静默空白） | P2-7 |
| `25d63e66` | `queryExport` 口径统一为 `useTable` 单一真源；`.env` 生成改合并模式 | P2-5（+ 原「未授权」的 `.env` 项） |
| `3762b206` | 初始化标志位迁入数据源（三态闸门 + 纯函数真值表） | P2-10 |
| `fe9ac477` | 组件路径前导斜杠归一化 + 契约用例 | P2-7（守卫暴露的连带缺陷） |
| `25a56937` | 阴影全面 token 化 + `designTokenContract.spec.ts` CI 棘轮 | Track 3（非 K 条目） |
| `4e92e73b` | K 报告 §4.0 处置台账（10 项全关闭 + 验证证据） | 文档 |
| `3b3b7e35` | `updateDetailQuantity` 回调 `Consumer` → `Predicate`，异常不再当控制流 | 本文 R6 / P1-1 |
| `bef31e7d` | 修通 `pnpm check` 断掉的三步：lint（11 errors）、`pom:check`（BOM 白名单缺 `com.mysql:mysql-connector-j` 导致的**假问题**）、`exports:check`（死导出 7 → 0） | 本文 R10 / P1-3 |
| `e305a657` | 补 palette / errorHandler 纯逻辑测试 21 例，分支覆盖率 74.39% → 79.22%（不下调阈值）；修掉 `vitest.config.ts` 里过期的基线注释 | 本文 R10 / P1-3（案例 H 断点 ④） |
| `ef639042` | `appConfig.spec.ts` 改静态导入，消除 coverage 插桩下的 30s 超时抖动 | 本文 R10 / P1-3（案例 H 抖动） |
| `71f2a24a` | 业务错误按码分流：403 带权限码指引、4002 字段定位、并发 3s 去重；顺带修掉 `showError`/`customErrorHandler` 死契约 | 本文 R9 / P1-2（案例 I） |

> 处置细节与验证证据见 `docs/TEST_REPORT_2026-09-K.md` §4.0 台账。

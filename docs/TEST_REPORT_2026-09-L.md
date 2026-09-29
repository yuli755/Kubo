# SPMS 前后端联调测试报告 —— 2026-09 阶段 L（UI 级复验 · 隐形过滤条件 · 审计闭环）

- 日期：2026-09-30
- 分支：`dev`（`main` 全程未改动，仍停在 `a243b3e9`；本轮结束时 dev 领先 main **136** 个提交，其中本轮 10 个）
- 方式：真实启动前后端（Spring Boot `:8080` + Vite `:3000` + MySQL 8.0.37 `kubo` 库），**双通道验证** ——
  HTTP 探针证明业务闭环与越权边界，Playwright（`@global/playwright`，Chromium 真页面）证明「人点得到」。
  阶段 K 的断言全部来自 HTTP 层；本轮补上 UI 层，并因此抓到两个只有 UI 能暴露的缺陷（L-4、L-9/L-10）。
- 状态：**完成**。缺陷记录 §1，修复清单 §2，验证证据 §3，未修项与建议 §4，复现与工具 §5。

---

## 0. 结论速览

| 维度 | 结果 |
| --- | --- |
| 后端单测 | `mvn -B -o clean test` → `Tests run: 927, Failures: 0, Errors: 0, Skipped: 1`（阶段 K 基线 915 ⇒ **+12**） |
| 前端全链 | `pnpm check`（**22 步**）→ 全绿，`outer-exit=0`；vitest `Test Files 70 passed (70)` / `Tests 791 passed (791)` / 61.82s |
| 覆盖率 | 语句 **86.35%** / 分支 **81.68%** / 函数 **82.50%** / 行 **86.08%**（阈值 50/65/50/75，未下调任何阈值） |
| 业务闭环 | 采购 → 自动派生入库 → 库存 → 出库 → 移库（含自动派生镜像单）全链跑通，**32 条断言 FAIL=0** |
| 多角色扮演 | 张三（零授权）46 个端点：43 拒 / 3 放行（3 条全是 `@Permission(authorize = false)` 且承重） |
| UI 级复验 | 治理三页 **17 / 65 / 4 行**（修复前 0 行）；侧栏 7 组全出；菜单树 34 节点 / 27 叶路径；审计页 12 行、六种动作齐全 |
| 本轮缺陷 | **14 个**（P0×3、P1×7、P2×4），全部已修并逐项验证 |
| 新增守卫 | 后端 4 个测试类（+12 例）、前端 3 处用例、1 个新门禁脚本 `check-api-contract.mjs`（已接入 `package.json` 与 CI） |
| 提交 | 10 个（`7d302a68`…`697d0b70`）+ 本报告，见 §2 |

一句话结论：**系统的核心业务链路是通的、闭环的、越权边界是对的**；本轮真正的收获不在业务逻辑，而在三类「静默失效」——
① 一个框架级陷阱（实体字段初始值变成隐形 WHERE）先后打掉了 9 个菜单页和 3 个治理页；
② 一组前后端契约漂移（路径式 `getDetail`、不存在的「一键完成」、未分类的 `code 501`）；
③ 两处「声明了但没人执行」的语义（`@KTable({hide:true})`、对象单元格兜底）。
三类的共同点是：**编译通过、启动通过、测试全绿、控制台干净**，只有真机走查能发现。所以本轮把每一类都补了一条会红的守卫。

---

## 1. 缺陷记录（发现即报）

### L-1（P0）实体字段初始值成为隐形 WHERE 条件 —— 事故一：9 个菜单页整页不可达

- **症状**：侧栏少 7 组中的若干项，9 个页面直接进不去；`/menu/getList` 返回的树里缺节点。后端 911 测试全绿、前端 778 测试全绿、控制台零报错。
- **机制（两层叠加）**：
  - **D1 框架层**：`QueryListRequest<T>` 的 `filter` **就是实体本身**，Jackson 反序列化会执行字段初始化器；而 `BaseService.buildPredicates` 只跳过 `null`。于是 `BaseEntity.isPublished = false`（Java 默认值）被当成调用方传来的条件 ⇒ 每一次默认列表查询实际都是 `WHERE is_disabled = 0 AND is_published = 0`。
  - **D2 数据层**：`V10__menu_canonical_paths.sql` 给 4 个菜单组播了 `is_published = 1`（把「已发布」当成了「可见」）。两者相遇 ⇒ 组节点被过滤掉，挂在下面的叶子全部失联。
- **影响**：9 个页面不可达；第三个症状是 `base/mapper/ReadStat.java:13` 声称 `publishedTotal`「通常为 0」——实际是**恒为 0**。
- **同类面**：全库 59 张带 `is_published` 的表里，只有 `menu`（4/34）与 `tenant`（1/1）非零；`tenant` 刻意保留 `pub=1`（无控制器，见 L-2 的说明）。
- **修复**：`@FilterIgnore`（新注解，`framework/web/annotation/FilterIgnore.java`）+ `buildPredicates` 的跳过分支 + `BaseEntity.isPublished` 标注 + `V14__menu_unpublish_canonical_groups.sql`（把 menu 的 `is_published` 归零，让数据与语义一致）。`isDisabled` **刻意不标**——「列表只出未停用的行」是产品语义，改由守卫白名单显式承载（`ALLOWED_DEFAULT_FILTERS`，带理由文本）。
- **验证**：侧栏 7 组全出（工作台 / 仓储管理 WMS / 渠道管理 / 资产管理 / 工厂与库位 / 人员组织 / 系统管理），菜单树拍平 34 节点 / 27 叶路径，9 个页面全部渲染；`appRouter.ts:116` 的「菜单指向的视图缺失」告警一次都没触发。
- **提交**：`77e6e8f5`

### L-2（P0）同一机制的事故二：数据权限治理三页 0 行

- **症状**：`/console/system/datapermission/{resource,policy,fieldPolicy}/list` 表头正常、**0 行**。
- **证据（真机 + 直查对账）**：MySQL 直查 `data_resource` 17 行 / `data_policy` 65 行 / `data_field_policy` 4 行（全部 `is_disabled=0`、`status='ENABLED'`、`version_no=1`、`tenant_column='tenant_id'`）；admin 直连 8080 探针 `POST /dataResource/getList {filter:{}}` → `code 200「获取成功」rows 0`，`getPage` → `total 0 pageCount 0 rows 0`，`/dataPolicy/getList` → 0，`/dataFieldPolicy/getList` → 0。
- **机制**：`DataResourceEntity.status = "DRAFT"` 而库里 17 行全是 `ENABLED` ⇒ 永久 0 行。同批初始值：`tenantColumn = "tenant_id"`、`versionNo = 1L`、`DataPolicyEntity.conditionJson = "{}"` / `priority = 0` / `enabled = true`、`DataFieldPolicyEntity.allowPlaintext = false` / `priority = 0` / `enabled = true`、`PermissionChangeOutboxEntity.status = "PENDING"` / `retryCount = 0`。
- **为什么拖到现在**：项目里已有**两次书面先例**在调用点绕过同一个陷阱（`DataPolicyRepository.java:19-21`、`DataResourceService.java:691-693`），但都是「换个查法」而不是「堵住源头」，所以第三次仍然以同样的形式出现。
- **修复**：`@FilterIgnore` 扩面到 6 个实体 / 14 个字段（`DataResourceEntity`、`DataPolicyEntity`、`DataFieldPolicyEntity`、`PermissionChangeOutboxEntity`、`TenantEntity.status`、`UserTenantEntity.isDefault`），每处附「为什么这个初始值不能当条件」的注释。
  - 标注前做了安全检查：三个治理页**没有** enabled/status/priority 搜索框；受影响字段的读取全部走 repository 派生方法（`findByUserIdAndIsDefaultTrue`、`findByStatusOrderByIdDesc`），`@FilterIgnore` 只影响 `buildPredicates`，不影响派生查询；`TenantEntity` 无 `new TenantEntity()` 调用点、无控制器（`tenant` 不在 31 个 `@Api` 基路径里）——若不标注，将来加一个 `/tenant/getList` 会静默变成 `WHERE status=1`，语义是「停用的租户消失」而不是「按状态筛选」。
- **验证**：探针复测 **17 / 65 / 4 行**；启动日志里 `select dre1_0.* from data_resource dre1_0` **已无 WHERE 子句**；Playwright 走查三页 = resource 17 行 / 10 表头、policy 20 行（分页，共 65）/ 11 表头、fieldPolicy 4 行 / 12 表头，`is404=false`、console 0 errors。
- **守卫**：新增 `EntityFilterDefaultTest`（2 例）——扫全部 `@Entity`，把「有初始值的可过滤字段」逐个要求「要么 `@FilterIgnore`、要么进白名单」，并反向钉住已知覆盖项（`DataResourceEntity#status`、`DataPolicyEntity#enabled`、`DataFieldPolicyEntity#enabled`、`BaseEntity#isPublished`）与「白名单里已不再触发的项」。
  - 写这条守卫时踩了两个坑，都已修并留在代码里：① `@Entity` 扫描会连**测试类路径上的探针实体**一起收进来（`DataOwnershipMigrationServiceTest$OwnedEntity` 触发 `IllegalAccess`）⇒ 加 `isProductionClass()`（按 code source 路径排除 `test-classes`/`/test/`，并排除 member/local/anonymous 类）；② 键必须用 `field.getDeclaringClass()` 而不是被扫描的具体实体，否则继承来的 `isDisabled` 会散成 55 条 `MaterialEntity#isDisabled`，白名单里的 `BaseEntity#isDisabled` 永远匹配不上。
  - **原则**：一条因为别人测试探针的形状就变红的守卫，会训练所有人忽略它。
- **提交**：`77e6e8f5`

### L-3（P0）`/participant/*` 的泛型指错实体：读写的是 `contract` 表

- **症状**：无（前端从不调 `/participant/*`，没有 `baseUrl='participant'`）。这是本轮唯一**只有代码审查 + 定向探针**能发现的 P0。
- **机制**：`ParticipantController` / `ParticipantService` / `ParticipantRepository` 三个类都写成 `<ContractEntity, …>`，而 `ParticipantEntity` 与 `participant` 表被完全绕过。后果是三者不同源：
  - `/participant/getList` 返回**合同**；`/participant/delete` 删**合同**；
  - 它要的权限码却是 `Participant_delete`（权限身份来自 **controller**：`PermissionScanner` → `identity + "_" + method.getName()`）；
  - `DataPermissionResourceResolver.resolve(ParticipantService.class)` → `asset.participant`（资源键来自 **service 类**），而查询根是 `ContractEntity`（表来自 **实体泛型**）⇒ 数据权限按错的资源判定。
- **为什么静默**：编译通过、启动通过、911 个后端测试全绿。28 个控制器里 27 个满足 `XController → XEntity`，只有这一个例外。
- **决定性真机证据**：修复后 `POST /contract/getDetail {id:2}` → `404「没有查询到ID为2的合同」`（**修复前这一步会返回一份合同**，因为参与方 add 写进的是 contract 表）。其余：`/participant/add` → 200 id=1/2；`getList` 的行键全是参与方字段；`/participant/update` 改 phone 后在 participant 表读回；`/participant/delete` → 501。
- **修复**：三处泛型复位到 `ParticipantEntity`；`Delete` 收进 `@Extends(exclude = {Curd.Delete})` —— 参与方是合同 `@ManyToMany(cascade = PERSIST)` 的子实体（`ContractEntity.java:80-86`），走这条路删会留下悬挂的中间表行 ⇒ MySQL 外键违规 → 500；解绑应在合同侧改 `participantList`。
- **守卫**：新增 `ControllerEntityBindingTest`（3 例）——扫全部控制器，比对「类名词干」与 `BaseController` 的三个泛型实参（单据类另加 3 个明细参数），白名单 `ALLOWED_ENTITY_MISMATCH` **刻意为空**。
  - 首跑失败暴露了守卫自身的缺陷：`BaseController`/`BaseBillController` 是**具体类**（`Modifier.isAbstract` 为 false），扫描器把它们收进来了，其类型实参是 `TypeVariable`，报成「实际是 <非具体类型>」。改为按 `clazz.getTypeParameters().length > 0` 排除框架模板，并加**双向自检**（扫描结果必须含 `MaterialController`/`InputController`，且必须不含 `BaseController`/`BaseBillController`）。
  - **原则**：守卫自己的扫描范围需要双向自检——「什么都没扫到」是假阴性，「把框架模板扫进来」是假阳性。
- **提交**：`fd59a6cd`

### L-4（P1）深链冷启动「404 一闪」，且被一条绿色用例掩盖过

- **症状**：冷缓存硬加载 `/console/factory/storage/list`，1.4s 时 `body` 是「404 很抱歉，没有找到你想访问的资源」，约 5s 后自愈；热加载正常。
- **机制**：业务路由是**运行时**按菜单注册的（`initVueRouter(menuList, import.meta.glob('/src/view/**/*.vue'), '/src/view', 'console')`，`src/router/appRouter.ts:149`）。注册完成前，深链只会被 `src/config/routes.ts:47` 的 `:catchAll(.*)` → `@/view/error.vue` 接住；而 `tenantReady` 在第一个 `await` 之后就为 `true`，外壳与内层 `router-view` 立即渲染 ⇒ 404 被画在 90% 不透明的 `v-loading` 遮罩**底下**。
- **修复**：`src/component/Console.vue` 增 `routesReady`，在 `getMenuList()` 里 `await reloadCurrentRoute()`（`appRouter.ts:160-163`）**之后**才置真；`v-loading="isLoading || !routesReady"`，`<router-view v-if="routesReady" v-slot="{ Component }">`（`v-if` 必须在 `v-slot` 前，否则 `vue/attributes-order` 报错）。
- **验证**：Playwright 每 150ms 采样 × 7s = **45 个样本**，`ever404Count = 0`，终态 6 行 / 4 表头 / `isError=false` / console 0 errors。
- **附带发现（更重要）**：`src/demo/__tests__/consoleMount.spec.ts` 原先在断言**之前**就 `wrapper.unmount()`，然后同步量 `.k-shell__content` 的长度——它量到的其实是 catchAll 的 **404 页**（体积够大，所以过了 `> 200` 的阈值）。也就是说，**这个真机缺陷曾被一条绿色用例掩盖过**。加了 `v-if` 之后它立刻以 `expected 452 to be greater than 2000` 失败（首次跑成 42390ms 超时，是 java+vite+Chromium 争用把同一个失败拖成了 timeout；杀掉 java 后单跑 10.71s 给出明确断言）。已改为在 `vi.waitFor(…, { timeout: 8000 })` 里断言两条：内容区**不得含**「很抱歉，没有找到」+ 长度 > 2000，`unmount()` 移到断言之后。重跑 3/3 绿（10.14s）。
- **原则**：页面看起来坏了，先等 5 秒再下结论（冷启动动态路由）；但**「等一下就好了」不等于没有缺陷**——用户看到的就是那一闪。
- **提交**：`852d0681`

### L-5（P1）前端 `getDetail` 拼了后端不存在的路径

- **机制**：`src/framework/http.ts` 原来 POST 到 `getDetail/${id}`；后端**零路径变量**——31 个基路径下全部是 `POST /{base}/{action}` + body（`getDetail` 是 `@RequestBody E source`）。真机 404。
- **修复**：改为 POST `{id}` 到 `getDetail`；`src/framework/__tests__/service.spec.ts` 同步断言 `config.url === '/demo/getDetail'` **且** `config.data` 里带 `id`。
- **原则**：只断言前端自己拼出来的 URL 字符串，锁住的是实现而不是契约；这条用例现在锁的是「id 在体内」这个后端事实。
- **提交**：`69657337`

### L-6（P1）前端声明了后端不存在的路由 `setBillDetailsAllFinished`

- **机制**：`src/framework/bill.ts` 有 `urlSetBillDetailsAllFinished` 与对应方法；后端 `BaseBillController` 只有 `audit` / `reject` / `addFinish` 三个 `@PostMapping`，「一键完成」是服务内部方法。调用会得到 Spring 404（不是 `code 501`，因为根本没注册）。
- **修复**：删除 URL 常量与方法，在 `AbstractBillService` 头注释里写明「为什么这里没有『一键完成全部明细』」——完成度是明细逐条 `addFinish` **累积**出来的，`finishQuantity` 是每次调用的**增量**而非累计值（真机验证：入库明细先 3 后 4 ⇒ `fq=3`(fin=false) → `fq=7`(fin=true)，采购明细同步镜像）。三个 K*Service 的头注释同步从「四个动作」改成「三个动作」。
- **守卫**：`src/config/__tests__/wmsDomain.spec.ts:254` 断言该属性不得再出现（消息直接写「后端没有这个路由」）。
- **提交**：`69657337`

### L-7（P1）启用 / 停用 / 发布三种状态变更不落审计

- **症状**：审计页只有 CREATE/UPDATE/DELETE，「谁把某个用户停用了」「谁发布了这条价格」查不到。而 `Curd.Enable/Disable` 恰好只在三个控制器上重开（`UserController.java:55`、`RoleController.java:21`、`PersonalTokenController.java:31`），停用/启用是真实在用的动作。
- **修复**：`OperationLogRecorder` 增 `ACTION_ENABLE/DISABLE/PUBLISH`；`BaseService.enable/disable/publish` 在 `updateToDatabase(...)` **之后**调 `recordOperationLog(action, id, entity, null)`——只记 before 快照，因为写库参数是稀疏的 `getEntityInstance(id).setIsDisabled(false)`，拿它当 `after` 会把其他所有字段渲染成「已删除」。
- **为什么不并进 UPDATE**：审计里「谁改了数据」与「谁让数据不可用」是两类问题，并进 UPDATE 就再也查不出第二类。
- **配套**：`V15__operation_log_action_vocabulary.sql`（只改列注释；用 `MODIFY COLUMN` 以同一类型与可空性重写，所以 CI 的「重复应用」测试能过）；`OperationLogEntity.java:42` 的 `@Description` 同步词表；20 个实体标 `@OperationLog`（`@Target(TYPE)`、**无成员**、选择性开关：没标就不记）。
- **真机**：审计页 12 行，`actionsSeen = [CREATE, DELETE, DISABLE, ENABLE, PUBLISH, UPDATE]` 六种齐全；PUBLISH/ENABLE/DISABLE 行带 before 快照且 `after=null`（设计如此），CREATE 行 `before=null`。
- **提交**：`7d302a68`

### L-8（P1）审计快照会把敏感字段原样落库，且脱敏结果随查看者变化

- **机制**：`operation_log.before_snapshot/after_snapshot` 是 TEXT，`OperationLogSnapshot` 走反射拿实体真值 ⇒ 单价、姓名、身份证会原样进审计表。
- **两个坑（都试过）**：
  - 不能复用 `ColumnMaskingService.maskFieldValue`——那个判定依赖**当前请求者**的策略，而审计落库时没有「查看者」，写进去的值会随谁触发而不同（同一行数据两个管理员看到不同摘要，审计就失去对账价值）；
  - 不能写常量占位（`****`）——before/after 相等会让前端 Diff 报告「没有变化」，而实际上变了（一条假提示比没有提示更糟）。
- **修复**：新增 `MaskedValueDigest`（SHA-256 → hex → `h:` + 前 12 位，长度恒 14；`NoSuchAlgorithmException` 兜底 `****`；`ofValue(null)` 返回 `null` 而 `of("")` 会摘要空串）；`OperationLogSnapshot.isNeverPlaintext(field)` = `@SensitiveField || @Desensitize || @JsonIgnore || (@JsonProperty && access()==WRITE_ONLY)`；`ColumnMaskingService.digest` 委托到同一个类，保证「列表页看到的遮蔽值」与「审计里的摘要」同源。
- **顺带堵住一个真漏洞**：`shouldSkip` 丢弃 `@Transient` 是**安全**理由，不是性能理由——`UserEntity.oldPassword` 装的是用户输入的明文密码，`UserEntity.code` 装的是邮箱验证码。这两个字段既不是持久列也没有脱敏注解，只有「跳过 @Transient」这一条规则拦得住它们。
- **测试**：`OperationLogSnapshotMaskingTest`（5 例），含「摘要稳定且与查看者无关」「`@Transient` 一律不进快照」。
- **提交**：`7d302a68`

### L-9（P1）`@KTable({ hide: true })` 的语义整条丢失（全库 12 处声明失效）

- **症状（真机）**：数据字段策略页首行第三列显示 `[object Object]`，而该列 `resource` 在 `src/model/system/datapermission/DataFieldPolicyEntity.ts:37-43` 明确声明了 `@KTable({ hide: true })`。
- **机制**：`src/framework/metadata.ts:167 getTableConfigList` **刻意不剔除** `hide`（`src/framework/__tests__/metadata.spec.ts:62` 钉住语义：「hide 只是默认隐藏，仍在列里（列选择器可恢复）」）⇒ 剔除动作本该由 KTable 执行；但 `src/component/framework/KTable.vue:228-230` 只有 `rawColumns.filter(col => !hiddenColumnKeys.includes(col.key))`，`hiddenColumnKeys` 只装「用户勾掉的键」，**从不读 `col.hide`**。契约的两半各做了一半，中间没人接。
- **影响面**：全库 `hide: true` 共 12 处——`MaterialEntity.ts:67 purchasePrice` / `:72 salePrice`、`SaleEntity.ts:86`、`OutputEntity.ts:54`、`PurchaseEntity.ts:58`、`MoveEntity.ts:62`、`InputEntity.ts:64`（这 5 处是 `order:-91, datetime:true` 的时间列）、`DataResourceEntity.ts:45,53`、`DataFieldPolicyEntity.ts:38`、`ParticipantEntity.ts:23 email`。
- **修复**：KTable 增 `defaultHiddenColumnKeys = computed(() => rawColumns.filter(col => col.hide).map(col => col.key))`；`loadColumnPreferences()` 里「**有存档 → 完全以用户勾的为准**（否则『显示某列』刷新一次就被抹掉）；无存档 → `hiddenColumnKeys = [...defaultHiddenColumnKeys]`」；`resetColumns()` 改为重置到元数据默认而不是「全部显示」。列选择器本来就 `v-for="col in rawColumns"`（`KTable.vue:624`），所以「可恢复」这半边契约不破。
- **安全语义澄清（写进 `src/model/personnel/user/UserEntity.ts` 类注释，替换掉一段已过时的说法）**：**`hide: true` 是排版手段，不是权限控制**——任何能打开这张表的人都能在列选择器里把默认隐藏的列勾回来。所以 `idCard` 仍然「根本不声明列」，不能改成 `hide: true`。这条必须留着，否则本次修复会被读成「现在可以用 hide:true 声明身份证列了」。
- **验证**：Playwright 复测 fieldPolicy 表头变为「# 数据资源ID 字段名 角色编码（default 为通配） 动作 脱敏类型 遮蔽表达式 允许明文 优先级 启用 操作」（`数据资源` 列已按声明隐藏）；material 页表头「# 物料名称 物料编码 物料类型 使用类型 规格型号 计量单位 操作」（采购单价/销售单价两列已隐），首行 `1 MacBook Pro M4 Max MA20260001 外购品 工具类 32G-1TB 台 (UT0001)`；resource 页表头 8 个。
- **测试**：`frameworkComponents.spec.ts` 新增一例，三段断言——无存档时表头无「销售单价」、存档为 `'[]'`（用户勾回来了）时两列都在、存档为 `'["name","salePrice"]'` 时两列都不在（**存档双向优先**，不与元数据默认求并）。
  - 浏览器里验不成这一条：两次 `page.click` 都 30s 超时（Element Plus 的原生 `input.el-checkbox__original` 是隐藏的，改点 label 仍报 `element is not visible`，popover 内元素被判不可见）。**这类「UI 契约」用单测证明比用截图证明更稳、也更省时。**存档键 `kubo:table:columns:<queryKey>`，且 `loadColumnPreferences()` 在 setup 期**同步**执行 ⇒ 测试必须在挂载前写好 localStorage。
- **提交**：`cf1f6834`

### L-10（P1）`getCellText` 对对象值兜底成 `[object Object]`（表格 / 详情 / 导出三处一起脏）

- **机制**：`src/framework/cell.ts` 末尾 `return String(value)`。该函数被三处共用：`KTable.vue:314`、`KDetail.vue:57`、`KDetailTable.vue:97`，另有 8 个 view 的 `detail.vue` 直接调 ⇒ 漏标一个 `payload: true`，就是列表、详情、导出三处同时显示 `[object Object]`（导出还会把它写进 CSV）。
- **修复**：新增 `FALLBACK_LABEL_KEYS = ['resourceKey','label','title','nickname','realName']` 与 `labelOfObject(value)`，顺序为 `getPayloadLabel()` → `name`(+`code`) → FALLBACK_LABEL_KEYS → `#id` → `''`；`payloadText` 的对象分支改为 `labelOfObject(value) || '-'`；`getCellText` 在 `String(value)` 之前插入 `value instanceof Date`（保留旧的可读行为，别被对象兜底吃成短横）与 `typeof value === 'object'` 两个分支。
- **测试**：`frameworkCell.spec.ts` 新增一例——`{id:15,resourceKey:'wms.inventory'}` → `wms.inventory`（业务标识键优先于 `#id`）、`{name:'主仓库',code:'A01'}` → `主仓库(A01)`、`{id:7}` → `#7`、`{}` → `-`、`[{id:1},{name:'甲'}]` → `#1、甲`、`new Date(2026,8,17,10,30,0)` → 含 `2026`。
- **为什么和 L-9 一起修**：hide 丢失是「不该出现的列出现了」，`[object Object]` 是「出现了就该显示成人话」——只修前者会让后者继续潜伏在别的漏标列上。真机上它们是同一格里同时看到的。
- **提交**：`cf1f6834`

### L-11（P2）`code 501` 在前端没有分类，被当成「服务错误」提示重试

- **机制**：被 `@Extends(exclude=…)` 排除的端点**仍然注册**，运行期由 `BaseController.checkApiAvailable(Curd)`（`BaseController.java:53-56`）拒绝，回 HTTP 200 + `{code:501, message:"当前接口不支持此操作"}`（`Errors.API_SERVICE_UNSUPPORTED`）——全库 **98** 条。前端 `errorPolicy.ts` 没有 501 分支 ⇒ 落到 `SERVICE_ERROR`，文案暗示「稍后重试」，而重试永远不会成功。
- **修复**：`BusinessCode.API_UNSUPPORTED = 501`、`'unsupported'` 加入 `BusinessErrorKind`、`isUnsupportedCode()`、在 `SERVICE_ERROR` 之前插分支 → `{kind:'unsupported', title:'此操作在当前模块不可用', hint:'该模块未开放此接口，重试不会成功；如确需此功能请联系管理员确认', silent:false}`；`axios.ts:134` 的 toast 时长对 `forbidden`/`unsupported` 给 6000ms（需要读完），其余 4000ms。
- **实测的门禁顺序（顺手记录，非缺陷）**：**权限门在 Curd 门之前**。零授权用户打 `/material/disable` 得到 `403「权限不存在」`（被排除的动作从未进权限目录），只有 admin 才看得到 501。所以「501 能不能被普通用户看到」取决于该动作是否进了权限目录，这一点对写文案很关键。
- **提交**：`e740938a`

### L-12（P2）`log` 模块是三个死文件，指向一个后端不存在的接口

- **症状**：菜单项叫「操作日志」，而 `src/model/system/log/{LogService,LogEntity}.ts` 与 `src/hook/system/useLog.ts` 指向一个后端不存在的 `log` 基路径（新门禁的「悬空 baseUrl」规则抓出）。
- **调研结论（对应「未实现的功能先找现成方案」这一条要求）**：**复用已有的 `operation_log` 审计链，不新建请求日志模块**。理由：菜单名就是「操作日志」；复用 ⇒ 零新增后端写路径、零新增权限码、零新表；而请求日志需要新拦截器 + 新表 + 保留期与脱敏策略——请求体里带密码与手机号，等于**再多一份敏感副本**。
- **落地**：删 3 个死文件；`list.vue` 重写到 `useOperationLogReport()`（`GET /dataResource/operation-logs`，200 行上限 `MAX_ROWS`，本地 `resourceKey`/`action`/`keyword` 过滤 + 客户端分页，`report.failed` 用 `ElAlert` 暴露而不是静默空表，空态文案如实说明 `@OperationLog` 是选择性开关）；动作下拉改为 `v-for="(label, key) in ACTION_LABEL"` 而不是硬编码 `ElOption`（注释写明：后端多一个 `ACTION_*` 常量时，硬编码的筛选器会**静默漏掉**它）；`detail.vue` 不再发任何请求（没有按 id 查审计的路由），并在文件头声明它必须与 `list.vue` 及后端词表一致、由 `backendRegressionPhase3` 对账。
- **演示夹具**：`demoOperationLogs()` 补到 **7 行覆盖全部六种动作**，价格字段一律是 `h:` 摘要（`MaskedValueDigest` 的真机输出，如 `480.0→h:41d06a0e9908`、`29999.0→h:5fa8b3fee6fe`）。顺带修掉 mock 的两处静默失效：`deny-log` 拼写（应为 `deny-logs`）与缺失的 `operation-logs` 分支——两者原先都**静默返回空对象**。
- **跨层对账守卫**：`backendRegressionPhase3.spec.ts` 新增 describe-5（4 例）——后端 `ACTION_*` 常量集合（正则 `/public static final String ACTION_\w+\s*=\s*"(\w+)"/g`）排序后 === `['CREATE','DELETE','DISABLE','ENABLE','PUBLISH','UPDATE']`；两个 vue 的 `ACTION_LABEL`/`ACTION_TYPE` 覆盖它；`list.vue` 含 `v-for` 写法；夹具覆盖六动作、保留摘要、无明文价格、无 `wms.inventory`。带自检（后端至少 3 个常量且含 CREATE）。
  - 写这条守卫时踩到一个**断言形状**坑并已修：守卫读的是**源码文本**（TS 对象字面量，键不带引号 `purchasePrice: 'h:…'`），而运行时形状（`JSON.stringify` 之后，键带引号）属于 `demoPreview.spec.ts` 的职责。**夹具是对的，断言写错了**——一条跨层守卫必须说清自己读的是哪一层。
- **提交**：`697d0b70`

### L-13（P2）`operation_log` 是 append-only 且永不清理

- **机制**：`DataPermissionEvidenceRetention` 原本只清 3 张证据表；审计表按 cron `0 15 3 * * *` 之外无任何清理路径，长期跑会无限增长（TEXT 快照 × 每次状态变更）。
- **修复**：并入同一个 cron 与同一个 `retention-days`，用 `ObjectProvider<OperationLogRepository>` 注入（构造器 4→5 参数）、`getIfAvailable()` 判空、`catch RuntimeException` 只 warn「清理操作审计表失败，下一轮重试」——**一张表失败不能拖垮其他三张**；日志行加 `operation-log={}`。复用已有的 `@Modifying @Transactional @Query(nativeQuery=true) int deleteExpiredBefore(@Param("cutoff") long)`。
- **测试**：`DataPermissionEvidenceRetentionTest` +2 例（表缺失不阻塞其他表、单表失败不中断整轮），原用例改名为「覆盖全部四张证据表」。
- **提交**：`7d302a68`

### L-14（P2）`MenuController` / `DepartmentController` 的 `authorize = false` 缺决策注释（防回归，非行为变更）

- **现象**：两处 `@Permission(authorize = false)` 的 `getList` 覆写看着像越权口子，很容易被后人「顺手收紧」。
- **实际是承重的**：`RoleController.java:25 authorizeMenu` 需要整棵菜单树；用户编辑器需要部门树；持有 `Role_authorizeMenu` / `User_*` 的人**不必**再持有 `Menu_getList` / `Department_getList`。收紧会让角色授权页与用户编辑页直接空掉。`DashboardController.java:17` 原本就有同类注释。
- **修复**：补决策 javadoc，写清「为什么不能收紧」而不只是「这里关了授权」。
- **提交**：`2dd8534a`

---

## 2. 修复清单（10 个提交）

| # | 提交 | 主题 | 对应缺陷 |
| --- | --- | --- | --- |
| 1 | `7d302a68` | `feat(audit)`：审计动作词表扩到 ENABLE/DISABLE/PUBLISH；快照敏感字段改存不可逆摘要；保留期清理并入 `operation_log`；20 个实体标 `@OperationLog`；`V15` | L-7、L-8、L-13 |
| 2 | `77e6e8f5` | `fix(base)`：新增 `@FilterIgnore` 与 `buildPredicates` 跳过分支；`BaseEntity.isPublished`、`TenantEntity.status`、`UserTenantEntity.isDefault` 标注；`BaseService` 三个状态动作补审计；`V14`；`EntityFilterDefaultTest` + `BaseServiceFilterIgnoreTest` | L-1、L-2 |
| 3 | `fd59a6cd` | `fix(asset)`：participant 三件套泛型复位到 `ParticipantEntity`，`Delete` 收口；`ControllerEntityBindingTest` | L-3 |
| 4 | `2dd8534a` | `docs(system)`：记录 Menu/Department `getList` 为何必须 `authorize=false` | L-14 |
| 5 | `41acb253` | `ci(contract)`：新增前后端 API 契约门禁并接入 `package.json` + CI；迁移顺序清单同步到 V15 | 守卫（防 L-5/L-6/L-12 复发） |
| 6 | `852d0681` | `fix(console)`：`routesReady` 消除深链冷启动 404 一闪；`consoleMount` 加严为「内容区不得是 404 页」 | L-4 |
| 7 | `69657337` | `fix(framework)`：`getDetail` 走请求体；删除后端不存在的 `setBillDetailsAllFinished` | L-5、L-6 |
| 8 | `e740938a` | `feat(errors)`：`code 501` 归为「此操作在当前模块不可用」，不再暗示重试 | L-11 |
| 9 | `cf1f6834` | `fix(table)`：KTable 恢复 `hide` 语义（存档双向优先）；`getCellText` 对象兜底链；`UserEntity` 安全澄清 | L-9、L-10 |
| 10 | `697d0b70` | `feat(log)`：操作日志页复用 `operation_log`；删 3 个死文件；演示夹具补六动作；跨层词表对账守卫 | L-12 |

本轮同时**删除**了 7 个一次性探针脚本（`infra/scripts/tmp-probe1..7.mjs`）——它们的用途与结论已写进本报告 §3/§5，脚本本身不入库（一次性脚本留在仓库里会让人误以为是可复跑的门禁）。

### 基线完整性（硬约束核验）

- `main` 全程未改动，仍在 `a243b3e9`；所有工作在 `dev`（现领先 136 个提交）。
- 未修改、未重排任何**已应用**的 Flyway 文件；本轮只**追加** `V14`、`V15`（下一个空闲版本号 **V16**）。
- 未下调任何阈值：覆盖率仍是 50/65/50/**75**（实测 86.35/81.68/82.50/86.08）；`pnpm check` 仍是 22 步、只增不减；`ControllerEntityBindingTest.ALLOWED_ENTITY_MISMATCH` 与 `EntityFilterDefaultTest.ALLOWED_DEFAULT_FILTERS` 的每一项都带理由文本，且后者有「失效白名单」反向断言。
- 每一步都以编译 / 测试 / 真机实测验证（逐项证据见 §3）。

---

## 3. 验证证据

### 3.1 构建与测试

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 后端全量 | `mvn -B -o clean test`（`JAVA_HOME=D:\JAVA\JDK\jdk-18.0.2.1`） | `Tests run: 927, Failures: 0, Errors: 0, Skipped: 1` / `BUILD SUCCESS`（基线 915 ⇒ +12） |
| 前端全链 | `pnpm check`（22 步） | `outer-exit=0`；步序 lint:ci → airpower:check → airpower:backend → pom:check → deadcode:check → exports:check → typecheck → test:coverage → perms:check → auth:check → errcode:check → **contract:check** → text:check → ddl:check → migration:check → monitoring:check → canary:check → docker:check → drill:check → build → demo:check → size，全绿 |
| 前端单测 | 同上 `test:coverage` | `Test Files 70 passed (70)` / `Tests 791 passed (791)` / 61.82s |
| 覆盖率 | `coverage/coverage-summary.json` | 语句 86.35% / 分支 81.68% / 函数 82.50% / 行 86.08% |
| 产物 | `pnpm build` | `built in 35.72s`；PWA precache 209 entries（2082.13 KiB）；首屏闭包 **275.6 / 450 KB**，全部产物 **697.3 / 900 KB**（204 文件），单文件上限 300 KB 未破 |
| 演示产物洁净 | `demo:check` | 132 个 JS 文件均不含演示模式标记 |
| 端点授权门禁 | `auth:check` | 已核对 34 个控制器、333 个 HTTP 端点方法，全部有显式 `@Permission`（类级或方法级），无依赖隐式默认的公开面 |
| **新增**契约门禁 | `contract:check` | 35 个 `*Controller.java` → 31 个基路径、424 条已注册路由、98 条被 `@Extends` 排除（运行时 501）；290 个 ts/vue → 29 处 `baseUrl`、118 个类；**自检 8 项全过**；披露 89 条「前端基类有、后端已排除」的端点；结论「前端拼出的每个端点都存在于后端路由表」 |
| 定向守卫 | `mvn -o test -Dtest=EntityFilterDefaultTest` | 2/2 绿（首两轮失败均为守卫自身缺陷，见 L-2） |
| 定向守卫 | `mvn -o test -Dtest=ControllerEntityBindingTest` | 3/3 绿，2.582s |
| 定向守卫 | `pnpm vitest run src/config/__tests__/backendRegressionPhase3.spec.ts` | 17 tests 绿（含新 describe-5 四例） |
| 定向守卫 | `pnpm vitest run src/config/__tests__/schemaContract.spec.ts` | 7 tests 绿（508ms） |
| 定向守卫 | `pnpm vitest run src/demo/__tests__/consoleMount.spec.ts` | 3 tests 绿（10.14s） |

`pnpm check` 本轮红过两次，两次都是**守卫过期**而不是生产代码问题，都已按「守卫要求你确认」的方式修：① `lint:ci` 报 `backendRegressionPhase3.spec.ts:237 antfu/consistent-chaining`（链式调用每节需独立成行）；② `test:coverage` 报 `schemaContract.spec.ts:199 expected 'V15__operation_log_action_vocabulary.sql' to be 'V13__retired_resource_policy_convergence.sql'`（该用例把「最后一个迁移」写死，新增迁移必然让它红一次）。两处都补了断言消息与注释，说明红是设计如此。

### 3.2 业务全链路闭环（objective ①）

探针脚本（已删除，用途记录于此）：以 admin 直连 8080，按「采购 → 入库 → 库存 → 出库 → 移库」跑完整链，每步读回库存与单据状态，**32 条断言 FAIL=0**。

- 链路：`purchase/add` → `purchase/audit`（**自动派生**一张入库单：`PurchaseService.java:204-221 afterBillAudited` 里 `setStatus(InputStatus.AUDITING).setPurchase(bill).setType(InputType.PURCHASE)`）→ `input/addFinish` → `output/addFinish` → `move/addFinish`（**自动派生**一进一出两张镜像单：`MoveService.java:271-308`，派生单 `status` 直接是 `DONE`）。
- 数据：物料 1、仓库 2→3、供应商 1「Apple 中国」。库存轨迹第一次跑 33→36→40→37→32，目标仓库 3 从 5→10；第二次重读基线 32 后 39→36→31，仓库 3 从 10→15；**库存行数始终 5**（没有凭空长出新行）。
- 单号：`PC202609300001/2`、`IN202609300002/3/4/5`、`OUT202609300001/2/3/4`、`MV202609300001/2`（`@AutoGenerateCode`，按日递增）。
- 状态机：purchase 1→3→5、input 1→3→4、output 1→3→4、move 1→3→4，全部与设计一致。
- 金额：`PurchaseService.java:238-248 afterDetailSaved` 置 `totalPrice = Σ price×quantity`；`PurchaseService.java:223-236 afterAllBillDetailFinished` 只在**全部明细完成**时置 `totalRealPrice = Σ DecimalMath.multiply(price, finishQuantity)` ⇒ 链路中途 `totalRealPrice=0` 是**正确**的（这条曾是阶段 K 的未决问题，本轮闭合）。
- 反向用例：入库 `addFinish` 不带 `storage` → `code 4001「请传入入库仓库」`；出库 `addFinish` 带不匹配的 `inventory` → `code 4002「物料信息不匹配」`。两者刻意**不是 403**（`MoveService.java:214-219` 的注释：回 403 会让调用方和监控去追角色配置，而真正的问题是载荷）。
- 载荷不对称（记录以免后人踩）：移库 `addFinish` 只需 `{id, quantity}`（来源从 DB 明细读，`MoveService.java:207`）；出库 `addFinish` 必须带 `inventory:{id}`（从载荷读）。移库的增量取**载荷**里的 `moveDetail.getQuantity()`——曾有一次改成读 DB 合计，结果把同一批库存又移了一遍。锁键 `"lock:wms:inventory:storage:" + storageId + ":material:" + materialId`；跨仓移库两行按 row ID 升序加锁（`MoveService.java` 阶段 F-3）以避免 A→B / B→A 死锁。
- 审计面：跑完整链后 `resourceKeysSeen` 仍只有 `[asset.material, personnel.user, system.unit]` ⇒ **单据类实体刻意未标 `@OperationLog`**（每次移库都写会刷爆审计表），链条本身不产生审计行。

### 3.3 多角色扮演（objective ①）

以**张三**（`admin@hamm.com`，id 2，零权限、零菜单、单租户）跑 46 个端点：

- `matrixSummary = { total: 46, agreed: 43, deniedCount: 43, unsupportedCount: 0, allowedCount: 3 }`。
- 3 条放行是 `/menu/getList`、`/department/getList`、`/dashboard/getConsole`——全部 `@Permission(authorize = false)`，且全部承重（见 L-14）。
- 链路中途越权：admin 建 `purchase id=9` → 张三 `/purchase/audit` → `403「你无权访问 采购单-审核 (Purchase_audit)」`；张三 `/purchase/getDetail` → `403 …（Purchase_getDetail）`；admin 审核 → 200；admin **再审核一次** → `403「该单据状态无法审核」`；随后驳回 → `403「该单据状态无法驳回」`（`AbstractBaseBillService.java:396-408` 的 `canAudit`/`canReject` 只接受 `AUDITING`）。
- 结论：**授权门与状态机门都到位，且两者的错误文案可区分**（`errorPolicy.ts` 按措辞区分 403 授权拒绝与状态机拒绝，`LEGACY_FORBIDDEN_FAMILY_MIN/MAX = 4031/4039`）。
- 遗留数据：探针留下 `purchase id=9`（`reason='联调-越权探针'`），已在报告里点名，便于后续清理。

### 3.4 UI 级复验（本轮新增的能力）

阶段 K 的所有断言都来自 HTTP 层；本轮起可用 Playwright MCP 驱动真页面，因此「接口通」与「人点得到」第一次被分开验证。

| 页面 | 复验结果 |
| --- | --- |
| 侧栏 | 7 组全出（工作台 / 仓储管理 WMS / 渠道管理 / 资产管理 / 工厂与库位 / 人员组织 / 系统管理） |
| 菜单树 | 拍平 34 节点 / 27 叶路径；`appRouter.ts:116` 的「视图缺失」告警 0 次 |
| 曾不可达的 9 页 | 全部渲染 |
| 数据资源 | 17 行 / 10 表头；首行 `1 wms.inventory inventory t tenant_id FACTORY:storage.id - QUERY,DETAIL,EXPORT ENABLED 编辑 禁用 删除` |
| 数据策略 | 20 行（分页，共 65）/ 11 表头 |
| 数据字段策略 | 4 行；表头已不含被 `hide` 的「数据资源」列；`hasObjectObject=false` |
| 物料 | 3 行；表头「# 物料名称 物料编码 物料类型 使用类型 规格型号 计量单位 操作」（两个价格列按声明隐藏）；首行 `1 MacBook Pro M4 Max MA20260001 外购品 工具类 32G-1TB 台 (UT0001)` |
| 仓库 | 6 行 / 4 表头（「仓库 / 按层级维护仓库与库区…」）；采样探针 45 样本 `ever404Count=0` |
| 操作审计 | 12 行 / 9 表头，标题「操作审计」；六种动作齐全；`untaggedResourceLeak = []` |

操作层面的经验（写下来以免重复踩）：侧栏是自定义 `KAppShell` 而不是裸 `.el-menu`（选择器 `.el-menu > li` 返回 0，是探针写法问题不是缺陷，应改用 `body.innerText`）；行数统计需要约 1200ms 以上；一次调用最多驱动 3–5 个页面；**截图对本模型不可读**，所有 UI 结论都来自 DOM 文本，截图只作为交付给人的证据。

### 3.5 数据收敛指标（真机 + 直查对账）

| 对象 | 修复前 | 修复后 | 直查基准 |
| --- | --- | --- | --- |
| `data_resource` | API 0 行 | API **17** 行 | MySQL 17 行 |
| `data_policy` | API 0 行 | API **65** 行 | MySQL 65 行 |
| `data_field_policy` | API 0 行 | API **4** 行 | MySQL 4 行 |
| `menu` 可达叶子 | 27 中的 9 页不可达 | 全部可达 | 34 节点 / 27 叶路径 |
| `operation_log` | 只有 3 种动作 | **6** 种动作齐全 | 12 行 |
| 后端测试 | 915 | **927** | — |
| 前端测试 | 778 | **791** | — |
| 前端调用的端点 | 未校验 | **0 条悬空**（424 条路由表内） | 契约门禁 8 项自检 |

---

## 4. 未修项与建议（按优先级）

阶段 K 报告 §4 的 10 项已全部关闭（逐项处置见 K 报告 §4.0 台账）。本轮新增/仍在的项如下——**全部是「已定位、未动手」**，不动的原因写在每条里。

### P1

1. **`operation_log` 的历史行不会回溯脱敏（机制性风险，非当前暴露）**
   审计表是 append-only，且由 `GET /dataResource/operation-logs` 原样返回；后加的 `@SensitiveField` **不回溯**已写入的行。真机证据：logId 1/2（`asset.material` CREATE/DELETE，`targetLabel` = `MA-PROBE-1`）仍是明文 `purchasePrice: 1.0` / `salePrice: 2.0`，而 logId 3/4/7/8 已是 `h:8aed642bf511`。生产暴露为零（该功能上线时表是空的），风险目前只在开发库，但**结构上**任何一次「先上线、后加注解」都会重演。
   建议：给 `operation_log` 加一次性洗库脚本（把命中 `isNeverPlaintext` 的键改写成摘要），或在读取侧对历史行做一次惰性归一。**未动手的原因**：洗库需要一份「哪些键在哪个版本前是明文」的时间线，而这条时间线现在只存在于提交历史里，贸然改写会破坏审计的可对账性——需要先决定「审计表允许被修正吗」这个策略问题。
2. **`ParticipantEntity` 的 PII 未脱敏，因此刻意没有标 `@OperationLog`**
   `phone` / `identification` / `email` 三个字段既无 `@SensitiveField` 也无 `@Desensitize`。若现在就给它标 `@OperationLog`，等于把身份证号明文写进审计表（正是 L-8 要防的事）。
   前置条件（都验证过，不是猜测）：要先注册 `asset.participant` 数据资源**并绑定 mapper**——项目里没有 `ParticipantMapper`，只注册不绑定会让 `DataResourceBindingRegistry.java:115` 抛 `has @DataResource(x) but no mapped statements`、`DimensionBindingAnalyzer.java:180` 报 `KIND_ENTITY_NOT_MAPPED`、`DataPermissionEnforceReadiness.java:115`「拒绝用不确定的结果启动」。另外**暂不要**把它迁到 `TenantAwareEntity`（它属于 `V2` 的 [T4] 层，整个 T4 家族目前仍是 `BaseEntity`；55 个实体对 32 张已迁移表，提前上 `@MappedSuperclass` 会让 `ddl-auto=validate` 启动即失败）。
3. **数据权限三表的外键符号不匹配，参照完整性静默失效**
   `data_resource.id` 是 `bigint unsigned`，而 `data_field_policy.resource_id` / `data_policy.resource_id` 是有符号 ⇒ 启动日志里**唯一**一条 DDL 失败：`Referencing column 'resource_id' and referenced column 'id' are incompatible`。`V12` 里已有 `kubo_align_resource_id(t)` 存储过程（`V12__retired_module_catalog_convergence.sql:174-186`），但该报错在本轮启动日志里仍出现。
   建议：出一个 `V16` 对两张表分别 `CALL kubo_align_resource_id(...)`，并把「启动日志零 DDL 失败」做成门禁（现在这条失败被淹没在正常启动输出里）。
4. **两处工程守卫缺口（阶段 K 的 R1/R2 仍在）**
   R1：没有守卫禁止在 `BaseService` 子类方法上标 `@Transactional`（`PermissionService` 就因 CGLIB 代理破坏了 `@Autowired(required=false)` 的字段注入而 500 过，现在只靠注释约束）；R2：后端 `resourceKey` 的 fail-open 只被部分守卫覆盖。
5. **legacy 表清单与 DROP 草案（阶段 K 的 P2-2/R8）**
   18 张表的盘点与 DROP 草案要放在 **Flyway 目录之外**（`ddl-auto=validate` 环境严禁 `DROP COLUMN`，见 `V2` 笔记 L536；`V2` L808 另注明「不建 FOREIGN KEY」，L814「本表必须一字不差」）。下一个空闲版本号是 **V16**。

### P2

6. **`PurchaseStatus.INPUTTING(4,"入库中")` 不可达**：没有任何方法返回它，但它出现在字典与前端筛选下拉里 ⇒ 用户会选到一个永远查不出结果的状态。与 L-12 同源的问题：**码表是语义，不是数字区间**（`docs/DESIGN_THINKING.md` 案例 J）。
7. **已删模块的配置项仍可见可改**：`PICKING_BILL_AUTO_AUDIT` 与 `ORDER_AUTO_START_AFTER_AUDIT` 对应的模块已删除（`OutputEntity.java:71-77` 只剩 `@JsonIgnore @Column(name="picking_id", insertable=false, updatable=false) Long legacyPickingId`），但配置页仍能改它们——改了没有任何效果。同族问题：退场的 `ConfigFlag` 11/12/18/19/20 仍用户可见。
8. **脱敏不一致导致可反推**：单价遮蔽成 `2***0` / `2***9`，但 `sale` 的总额是明文 `119,996.00` ⇒ 「数量 + 总额」足以反推单价。要么总额同族脱敏，要么在策略里承认「聚合值不敏感」并写明理由。
9. **`CREATE` 快照带 `"id":null` 与 `"isPublished":null`**：快照在 DB 分配 id 之前取（`BaseService` 的 add 钩子时机）。纯观感问题，但审计页的 Diff 会把 id 显示成「新增了一个 null」。
10. **两处信息面观察（不改，只记录）**
    - 零授权用户可用 `/menu/getList` 枚举全部 34 条后端页面路径（菜单是导航元数据；侧栏走 `getMyMenuList`，实测 0 节点 ⇒ 已正确收敛）。
    - `/purchase/getDetail` 的 403 文案泄露原始方法名「采购单-getDetail (Purchase_getDetail)」，因为该方法没有 `@Description`。建议补 `@Description`，或让 403 文案只出中文名。
11. **`base/mapper/ReadStat.java:13` 的注释仍说 `publishedTotal`「通常为 0」**：L-1 之后它**恒为 0**（该字段已无信息量）。建议删字段或改注释。
12. **控制台噪音（不影响功能）**：`[ECharts] Specified 'grid.containLabel' but no 'use(LegacyGridContainLabel)'; use 'grid.outerBounds' instead.`，以及重复的 `Runtime directive used on component with non-element root node … at <ElTooltip>`。
13. **死注入清理候选**：`AssetServices.getContractService()` 与 `getParticipantService()` **零调用点**（该文件 36 行，只有 `getMaterialService()` 被读：`DevDataInitRunner.java:837`、`SalePriceService.java:160`、`PurchasePriceService.java:160`、`DashboardService.java:53`）。L-3 修好泛型之后，这两个入口第一次「能用」了，但依然没人用——要么接上（合同/参与方页面），要么删掉。
14. **设计令牌化（阶段 K 的 T3）**：`src/component/framework/KPanel.vue:65` 的 `transition: all 0.2s ease` 一类硬编码值仍需收敛到令牌，并把 `designTokenContract.spec.ts` 的棘轮往前推。

### 建议的下一步顺序

先做 P1-3（一个 `V16` + 一条「启动日志零 DDL 失败」门禁，代价最小、收益是参照完整性真正生效），再做 P1-2（participant 的 mapper 与数据资源注册，顺带决定 P2-13 那两个死注入的去留），最后处理 P1-1（需要先定策略：审计表允许被修正吗）。P2 的 6/7/11 可以合成一个「退场语义收敛」提交，与 `V12` 的思路一致。

---

## 5. 复现与工具

| 用途 | 命令 / 位置 |
| --- | --- |
| 启动后端 | `Start-Process cmd.exe -ArgumentList "/c set JAVA_HOME=D:\JAVA\JDK\jdk-18.0.2.1&& mvn -B spring-boot:run > `"$log`" 2>&1" -WorkingDirectory <SPMS-Server> -WindowStyle Hidden`；就绪标记 `Hi Guy, Kubo is running at [8080] !`（约 28–42s） |
| 启动前端 | `Start-Process cmd.exe -ArgumentList "/c pnpm dev > `"$flog`" 2>&1" -WorkingDirectory <repo root> -WindowStyle Hidden`（约 3–4s，VITE v7.3.6，端口 3000） |
| 后端全量测试 | `cmd /c "set JAVA_HOME=D:\JAVA\JDK\jdk-18.0.2.1&& cd /d <SPMS-Server>&& mvn -B -o clean test > log 2>&1"`（约 159s） |
| 前端全链 | `cmd /c "cd /d <repo root>&& pnpm check > log 2>&1"`（22 步，约 4–6 分钟） |
| 定向 vitest | `cmd /c "cd /d <repo root>&& pnpm vitest run <files> > %TEMP%\spms-fe-guard.log 2>&1"` 后 `Get-Content -Tail 40`（这里 `$LASTEXITCODE` **确实**反映 vitest 结果） |
| 契约门禁 | `node infra/scripts/check-api-contract.mjs`（`pnpm contract:check` / `pnpm contract:report`） |
| 数据库直查 | `D:\JAVA\MySQL\mysql-8.0.37-winx64\bin\mysql.exe -h 127.0.0.1 -uroot -proot kubo -e "..."`（`-proot` 会把不安全密码警告写到 stderr ⇒ PowerShell 报 exit 1 而数据照常打印） |
| 代理关系 | `vite.config.mts`：`'/api' → 'http://127.0.0.1:8080/'` + `rewrite: path => path.replace(/^\/api/, '')` + `changeOrigin: true`；另有 `'/oauth2' → 'http://127.0.0.1:8080'`（不重写）⇒ 走 3000 用 `/api/xxx`，直连 8080 用 `/xxx` |
| 种子账号 | `admin@hamm.cn` / `17666666666` / 凌小云（id 1，全权限）；`admin@hamm.com` / `13888888888` / 张三（id 2，受限）；密码 `Aa123456`——**必须字面输入**（占位符替换会静默得到 `code=200001`） |
| 种子数据 | 物料 3（`MA20260001` MacBook Pro M4 Max / `MA20260002` 键盘 / `MA20260003` 屏幕）；仓库 6（`SRG0001` 东部大仓 … `SRG0006` 西部2仓，id 5/6 挂在 parent 4 下）；供应商「Apple 中国」；客户「腾讯科技」；单据 `input#1 IN202609150001 status=3`、`purchase#1 PC202609280001 status=3 totalPrice=280000 totalRealPrice=0`、`output#1 OUT202609150001 status=3`、`move#1 MV202609150001 status=3 storage=3` |
| 测试环境 | `SPMS-Server/src/test/resources/application-test.yml`：H2 `jdbc:h2:mem:testdb;MODE=MySQL;…INIT=RUNSCRIPT FROM 'classpath:schema-test.sql'`，**`ddl-auto: none`**、`flyway.enabled: false`、redis/mqtt 关、`app.isDevMode: false`；L49-51 记着历史陷阱——多一个顶层 `spring:` 键会触发 SnakeYAML `DuplicateKeyException` 并让约 100 个测试上下文加载失败。`schema-test.sql` 里没有 `participant` 表（`ddl-auto: none` 下无害） |

**已删除的一次性探针（用途存档）**：`tmp-probe1.mjs` 只读端点普查与树形返回结构核对（发现 `storage` 的 `getList` 返回根 + 嵌套子节点，拍平后 4 根 6 节点，不是「少两行」）；`tmp-probe2/3.mjs` 单据与库存链路分段验证；`tmp-probe4.mjs` 全链闭环 32 断言（§3.2 的数据来源）；`tmp-probe5.mjs` 张三零授权角色矩阵 46 端点（§3.3）；`tmp-probe6.mjs` 错误码与门禁顺序实测；`tmp-probe7.mjs` 治理三页 0 行的 admin 直连取证（L-2 的证据来源）。

**本轮踩到的工具/环境陷阱（供后续复现者省时间）**：

- `cmd /c "... > log 2>&1"` 会把外层 `$LASTEXITCODE` 留成 0，即使 `BUILD FAILURE` ⇒ **永远从日志里读 `BUILD SUCCESS/FAILURE` 与 `Tests run:`**。
- `mvn -Dtest=A+B+C` 在 surefire 3.2.5 下报 `No tests matching pattern` ⇒ 用逗号。
- 一个真实的参数个数错误会让 Lombok 的注解处理轮被 javac 截断，从而在无关文件里炸出 28 条 `找不到符号 方法 setRemark(java.lang.String)`（`CsvFormulaSanitizerTest.java`）⇒ 用 `Select-String '^\[ERROR\] /D:'` 按文件聚合，先修真错。
- `verify(mock, "中文说明")` 不编译（第二参数是 `VerificationMode`）⇒ 用注释或 `description("...")`。
- 后台 job 的 id 不跨 harness 重启（旧 id 会报 `unknown job`）⇒ 进度只从日志文件判断。
- 覆盖率 worker 在并发下会 OOM（`exit 3221226505` = `0xC0000409`）⇒ 跑 `pnpm check` 前先杀 java/node，且不要与别的重活并行。
- `src/types/components.d.ts` 在 build 后只剩 EOL 差异 ⇒ `git checkout --` 还原，别让它进提交（本轮已两次还原）。
- 刚创建的文件第一次 `edit` 可能报 `ReplaceFileW EIO (Win32 1175)`，同样内容重试即成功。
- vitest 的中文输出在 GBK 控制台里是乱码 ⇒ 只从 `Test Files` / `Tests` 行判断结果。
- 三个 CommandLine 为空、`Stop-Process` 报 `Access is denied` 的 java 进程不影响 `mvn`，不用管。

**本轮建立的原则（已写进代码注释与 `docs/DESIGN_THINKING.md` 的同一精神）**：

1. 数字码区间不等于语义类别（501 不是「服务错误」，4031 不是「403」）。
2. 一条假提示比没有提示更糟（「稍后重试」用于永不成功的操作 = 撒谎）。
3. 守卫规则需要双向自检，且豁免项要逐字钉住理由文本。
4. 当规则命中了正确的文本，先怀疑规则（本轮两次红都是守卫过期）。
5. 一条因为别人测试探针的形状就变红的守卫，会训练所有人忽略它。
6. 断言前端自己拼的 URL 字符串，锁住的是实现不是契约。
7. mock/演示跑通不算证据；跨层守卫必须说清自己读的是源码文本还是运行时形状。
8. 新增 `check-*.mjs` 必须同时接进 `package.json` 与 `.github/workflows/ci.yml`（否则元守卫会红）。
9. **可过滤实体字段上的成员初始值就是一条隐形 WHERE**——要么标注、要么进白名单，永远不要只在调用点绕过。
10. 页面看起来坏了先等 5 秒（冷启动动态路由）；但「等一下就好」不等于没缺陷。
11. UI 契约优先用单测证明，而不是用截图或点击证明（截图本模型读不到，Element Plus 的隐藏 input 点不动）。

# SPMS 测试报告 —— 阶段 K（真机前后端联调 · 业务闭环 + 多角色扮演）

- 日期：2026-09-28
- 分支：`dev`（`main` 全程未改动，仍为 `a243b3e9` 交付基线）
- 方式：**真实启动**前后端（Spring Boot :8080 + Vite :3000，MySQL 8.0.37 `kubo` 库 70 表 + Redis 容器 `kubo-redis`），
  以管理员会话对 `/v3/api-docs` 的 **400 个端点 / 31 个控制器**逐个探测；
  再用脚本扮演**采购→入库→销售→出库→移库**全链路，并新建受限角色与用户做**越权/脱敏差分**验证。
- 状态：**完成**。已确认缺陷见 §1，修复清单见 §2，验证证据见 §3，未修建议见 §4。

---

## 0. 结论速览

| 维度 | 结果 |
| --- | --- |
| 后端单测 | `BUILD SUCCESS`，**892 / 0 failures / 0 errors / 1 skipped**（与基线一致） |
| 前端 | `lint:ci` **0** · `typecheck` **0** · `test` **699 / 698 passed / 1 failed** · `build` ✓ |
| 唯一失败项 | `productionDeliveryAndCanary` —— **既有**失败，卡在未授权的 `.env` 供给（7 处部署必炸点），与本轮无关 |
| 业务闭环 | 采购/入库/销售/出库/移库 **46/46 通过**，含库存增减与总量守恒 |
| 多角色扮演 | **27/27 通过**，含 RBAC fail-closed、菜单过滤、列级脱敏差分 |
| 只读端点探针 | `SERVER_ERROR` **8 → 0**；`OK` 83 → 89 |
| 本轮缺陷 | 发现 **17** 项，修复 **17** 项（其中 2 项是 `main` 已修、`dev` 丢失的**回归**） |
| 提交 | 9 个修复提交（`36391014`…`71319375`）+ 本报告 |

> **一条贯穿性根因**：本轮 17 项缺陷里有 **4 项**同源 —— B0 退役 `cn.hamm:airpower` 父 POM 时，
> 只把「能用 `maven.compiler.*` 属性表达」的构建基线搬成了项目自持，漏掉了**没有标准 Maven 属性**
> 或**只存在于依赖传递/父 POM 插件配置**里的部分。这类缺失的共同特征是
> **编译期完全静默**，直到运行期甚至直到某个具体请求打进来才暴露。见 K-1 / K-2 / K-13。

---

## 1. 缺陷记录（发现即报）

### K-1 后端启动即崩：`Cannot load driver class: com.mysql.cj.jdbc.Driver`（已修复）

- **现象**：`dev` 上后端无法启动，DataSource 初始化阶段直接失败。
- **根因**：`dev` 的 `SPMS-Server/pom.xml` 里 `mysql-connector` **0 次出现**。
  `main` 能跑是因为它继承 airpower 父 POM、驱动由依赖传递带入；B0 退役父 POM 后这条传递链断了。
- **为何比 K-2 容易被发现**：它在启动期就炸；K-2 要等请求打进来才炸。
- **修复**：显式声明 `com.mysql:mysql-connector-j`，`<scope>runtime</scope>`，**不写版本**
  （交由 `spring-boot-dependencies` BOM 管理，3.4.4 → 9.1.0），置于 flyway 依赖块之前。
- **注**：`main` 无此问题（走传递依赖），故这是 `dev` 专属回归。

### K-2 数据权限治理诊断面**全灭**：编译缺 `-parameters`（已修复 · `main` 已修而 `dev` 丢失的回归）

- **现象**：7 个 GET 端点全部 HTTP 500，且返回的是 **Spring 默认错误体**而非本项目 `ApiResponse`：
  ```
  /dataResource/operation-logs、/deny-logs、/enforce-readiness
  /dataFieldPolicy/my-mask、/my-mask-map、/my-export-visibility、/by-resource-field-role
  ```
  即整个数据权限治理诊断面不可用（`/dataResource/*` 是 16 个 GET 治理端点的主体）。
- **根因链**：
  1. **Spring Framework 6.1 已删除 `LocalVariableTableParameterNameDiscoverer`**，
     未写显式 `name` 的 `@RequestParam`/`@PathVariable` 完全依赖编译期参数名元数据；
  2. 缺失时抛 `IllegalArgumentException: Name for argument of type [java.lang.String] not specified,
     and parameter name information not available via reflection`；
  3. 该异常在 **Filter 链内**抛出，因此**绕过 `@RestControllerAdvice`**，落到容器默认错误页。
- **受影响站点（静态扫描恰为 7 处）**：`DataFieldPolicyController` L48/49/50、L77、L95/96、L118
  的 `@RequestParam` 未写 `value`；`DataResourceController` L139/157/173 是**完全无注解的裸 `String resourceKey`**。
- **为何 `properties` 自持没覆盖到它**：`source`/`target`/`encoding` 都有同名标准 Maven 属性，
  写进 `<properties>` 即生效；但 **`-parameters` 没有任何标准 Maven 属性**，只能落在插件
  `<configuration>` 里。`dev` 保留了紧邻的 `maven-surefire-plugin` 块，却丢掉了
  `maven-compiler-plugin` 块 —— 等于静默回退了一条**已修复**的缺陷。
- **交叉印证**：`main` 的 pom L190-200 **有** `<parameters>true</parameters>`，并附注释直指
  本报告前一阶段的 **GUI-2-B**（`/dataResource/deny-logs` 实锤）。故本项是**回归**，不是新缺陷。
- **三重证据**：`javap -v` 编译产物的 `MethodParameters` 属性由 **0 → 命中**
  （`DataResourceController` 7、`DataFieldPolicyController` 5、`BaseController` 32、`UserController` 14）；
  7 个端点由 500 → 200；airpower `6.4.3` 的 POM（253 行）经核对**无 `maven-compiler-plugin`、
  无 `parameters`、无 `compilerArg`**，即 airpower 从来不提供 `-parameters`，
  是 `main` 自己补的 —— `dev` 把它丢了。
- **修复**：还原 `maven-compiler-plugin` 块（位置与 `main` 一致，紧邻 surefire 之前），
  附约 30 行注释记录症状、绕过多异常处理器的原因、6 个受影响端点、以及「为何 `maven.compiler.*` 不够」。
- **防回归**：`backendHttpBoundary.spec.ts` 新增静态契约测试，断言 pom 含
  `maven-compiler-plugin` 与 `<parameters>true</parameters>`，且要求两者**在邻近范围内共现**。

### K-3 全新库上 V2 迁移**必然失败**：`is_published` 无 DB 默认值（已修复 · `main` 同样带病）

- **现象**：未初始化过的 MySQL 库首次启动，Flyway 直接失败：
  ```
  Script V2__data_permission_migrations.sql failed
  java.sql.SQLException: Field 'is_published' doesn't have a default value
  ```
- **定位**：V2 **L252** `INSERT INTO user_tenant (user_id, tenant_id, is_default) SELECT ...`
  没列出 `is_published`，依赖列默认值；而 V2 **L248** 插入 `tenant` 时显式列出了它 ——
  **同一脚本里两种写法，只有一种能活**。
- **根因在实体层**：`BaseEntity` 四个继承字段中 `isDisabled`/`createTime`/`updateTime` 的
  `columnDefinition` 都带 `default`，**唯独 `isPublished` 漏了**，于是 JPA 建出的列
  `COLUMN_DEFAULT = NULL`。实测 70 表里 **6 张**中招
  （`tenant`、`user_tenant`、`operation_log`、`data_permission_deny_log`、
  `data_permission_diff_log`、`data_permission_orm_parity_log`），其余 53 张都是 `b'0'`。
- **对照佐证**：`src/test/resources/schema-test.sql` 里 `is_published` 一直写的是
  `is_published BOOLEAN DEFAULT FALSE NOT NULL`（L10/29/43/64/84/100）——
  **H2 侧从来是对的**，只有 MySQL/JPA 这条路缺默认值。补上之后
  「迁移脚本 / 实体 / H2 schema 三处同源」这条项目既有约定才真正成立。
- **修复**：实体 `columnDefinition` 补 `default 0`（与三个兄弟字段对齐；字段本身已有
  Java 侧 `= false` 初值，DB 默认值是给「绕过 JPA 的裸 SQL 插入」兜底）；
  新增 **V11** 用 `information_schema.COLUMNS` 找出 `COLUMN_DEFAULT IS NULL` 的表逐表 `ALTER`，幂等。
- **为何不改 V2**：V2 在已部署库上早已应用，改它会造成 checksum mismatch。
  V11 头部同时记录了它的**执行顺序局限**：一个「已卡在 V2 失败状态」的库需先手工执行修复 SQL 并
  `DELETE FROM flyway_schema_history WHERE version='2' AND success=0` 再重启；本轮联调即按此恢复。
- **注**：`main` 的 `BaseEntity` **同样缺**这个默认值 → 这是 `main` 也带的潜伏缺陷。

### K-4 数据权限外键符号性不匹配，引用完整性**静默失效**（已修复 · `main` 同样带病）

- **现象**：启动日志里唯一的 DDL 失败（非致命，故长期无人注意）：
  ```
  alter table data_field_policy add constraint FK6oky... foreign key (resource_id) references data_resource (id)
  -> Referencing column 'resource_id' and referenced column 'id' ... are incompatible
  ```
- **根因**：`data_resource.id` = `bigint unsigned`，而 `data_field_policy.resource_id` 与
  `data_policy.resource_id` = `bigint`（有符号）。**MySQL 拒绝有符号↔无符号建外键**。
  于是「列级脱敏策略 → 数据资源」的引用完整性在库层**根本没建起来**：
  删掉一个 `data_resource`，它的字段策略会变成悬挂引用而无人报错。
- **成因**：V2 的 DDL 写的是 `BIGINT UNSIGNED`（正确），但实体 `@Column` 没有 `columnDefinition`，
  JPA 建出有符号列；`CREATE TABLE IF NOT EXISTS` 又因表已存在而跳过 → 两条路径长期不一致。
- **为何只有一张表报错**：只有 `DataFieldPolicyEntity` 有 `@ManyToOne`，Hibernate 才会尝试建外键；
  `DataPolicyEntity` 是纯 `@Column`，**类型照样是错的，只是不报错**。故一并修正。
- **修复**：两个实体的 `resource_id` 补 `bigint UNSIGNED` `columnDefinition`
  （`DataFieldPolicyEntity` 需同时改 `@Column` 与 `@JoinColumn`，物理列由后者拥有）；
  **V12 第 3 部分**修存量库并补建 `fk_data_field_policy_resource` / `fk_data_policy_resource`
  （外键名**固定**而非沿用 Hibernate 随机名，便于幂等判断与运维识别）；建约束前先清悬挂引用
  （实测两表孤儿行均为 0，属防御性处理）。
- **验证**：`GenerationTarget encountered exception` 计数 **1 → 0**；两条外键均已建立。

### K-5 菜单死链 5 个 + 空壳分组 2 个（V10 声称达成却未达成）（已修复）

- **现象**：32 个可导航菜单里 **5 个是死的（15.6%）**，点进去是空白路由：
  ```
  [131] 资产管理/设备管理 -> /console/asset/device/list
  [152] 设备物联/设备概览 -> /console/iot/monitor/preview
  [153] 设备物联/参数管理 -> /console/iot/parameter/list
  [169] 系统管理/应用管理 -> /console/open/app/list
  [170] 系统管理/通知管理 -> /console/open/notify/list
  ```
  连带两个空壳一级分组：`设备物联(151)` 与**重复的** `系统管理(165)`。
- **根因**：`V10__menu_canonical_paths.sql` 自称「期望：分组恰为上述 7 个一级节点」，
  但它只 `DELETE ... WHERE path LIKE '/console/mes/%'`：
  既**漏了** `iot`/`open`/`asset-device`，也**没有清理空壳分组的逻辑**
  （分组节点 `path` 为空串，`LIKE` 规则永远匹配不到）。
  另核对 `MenuService.initMenu()` 里已无这些菜单（关键字命中 0），
  确认是**存量库残留**而非种子问题。
- **修复**：**V12 第 1 部分** —— 先删 `role_menu_list` 关联再删菜单，
  然后用存储过程 `kubo_prune_empty_menu_group()` **循环**删空分组直到 `ROW_COUNT()=0`
  （支持嵌套分组，不是一次性删一层）。
- **验证**：死链菜单 **0**；一级分组**恰为 7 个且无重名**（工作台 / 仓储管理 WMS / 渠道管理 /
  资产管理 / 工厂与库位 / 人员组织 / 系统管理）—— V10 未达成的后置条件至此满足；
  `menu` 41 → 34。

### K-6 死权限码 **152 / 463（32.8%）**（已修复）

- **现象**：用 `/v3/api-docs` 的 400 个真实端点反推**存活**权限身份
  （规则见 `PermissionScanner` L105：`identity + "_" + method.getName()`）与库对账，
  **152 条**对应的控制器源码已不存在，并逐个确认实体也已删除：
  `MemberEntity` / `RoomEntity` / `NotifyEntity` / `DeviceEntity` / `ParameterEntity` /
  `OrderEntity` / `BomEntity` 全部「不存在」。
- **根因**：`PermissionService.loadPermission()` **只增改、从不删除**，
  且只被 `DevDataInitRunner` 调用（需 dev 模式且无 `init.lock`）→ **权限目录永不收敛**。
- **危害**：角色授权界面里**三分之一**授了也没用；任何基于权限总数的统计与巡检都被污染。
- **修复**：**V12 第 2 部分** —— 先删 `role_permission_list` 关联（外键指向 `permission`）再删权限行。
  匹配用「identity 恰等」或「身份 + **下划线**开头」的正则，下划线锚点是**必要**的：
  `Operation_%` 不会误伤 `OperationLog_%`，`Routing_%` 不会误伤 `RoutingOperation_%`。
- **刻意保留**：4 个 `system:*` 治理权限（扫描器不产生，由 V2/V7 播种）；
  `Index` 与 `Index_index`（`IndexController` 仍存活，只删 3 个 AI 子权限）；
  `Participant*`（合同参与人，与已退场的聊天室 `Member` **不同物**）。
- **验证**：死权限码 **0**；`permission` 463 → **311**（-152，与审计数一致）；
  `role_permission_list` 同步 463 → 311。

### K-7 `filter` 为 null 导致 `beforeGetList` 解引用 NPE（已修复）

- **现象**：`/structure/getList`、`/storage/getList` → HTTP 500：
  ```
  NullPointerException: Cannot invoke "StructureEntity.getParentId()" because "filter" is null
  ```
- **根因**：`QueryRequest` 的无参构造**不初始化** `filter`，前端只传分页/排序、
  或清空筛选后提交 `{}` 时 `filter` 就是 null；而 `createSpecification` 下游本来就有
  `filter != null ? filter : getEntityInstance()` 的替换。
  **框架自己知道 `filter` 可空，却把这个负担推给了每一个覆写钩子的业务 Service** ——
  谁忘判空谁就 500。
- **修复（单点收敛，而非逐处打补丁）**：在 `BaseService.getList` / `getPage` 调用钩子**之前**
  归一化（新增 `private normalizeFilter` 助手），`createSpecification` 复用同一助手。
  因为下游做的是**同样的替换**，**查询语义完全不变**，只是把 N 处易漏的判空
  收敛成 1 处不可能漏的。
- **附带**：给 `QueryRequest.filter` 与 `getFilter()` 补 Javadoc，写明可空契约、
  标准读链路已受保护、直接构造的路径仍需自行判空。
  该类文档声明「纯 JDK 17 实现，无第三方注解或框架依赖」，
  故用 **Javadoc 而非 `@Nullable`** 表达可空性，尊重既有设计约定。

### K-8 `InventoryService` 对 `type` 拆箱 NPE，库存四个读端点全灭（已修复 · `main` 已修而 `dev` 丢失的回归）

- **现象**：`/inventory/{getPage,getList,getCount,getStat}` → HTTP 500：
  ```
  NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because "InventoryEntity.getType()" is null
  at InventoryService.java:256
  ```
- **根因**：`EnumDictionaries.require` 的形参是**原始 `int`**，传 `null` 的 `Integer` 直接拆箱炸。
  库存列表是高频入口，**不带筛选打开就 500**。
- **交叉印证**：本报告前一阶段 **GUI-2-A** 记录的正是同一处（当时在 `beforeCreatePredicate:279`），
  且 `main` 的 `InventoryService` **有** `getType() == null` 守卫 → 本项是**回归**。
- **修复**：补 null 守卫，`type` 为空时按「不限类型」原样返回实体，与该方法其余分支语义一致。

### K-9 `PersonalTokenController` 用 `IllegalStateException` 表达「本接口不提供」（已修复 · `main` 同样带病）

- **现象**：`/personalToken/{getPage,getList}` → HTTP 500。
- **根因**：6 个继承自 `BaseController` 的通用 CRUD 端点**仍被 Spring 映射**
  （因此照样出现在 `/v3/api-docs` 里、调用方能打到），只能在钩子里显式拒绝；
  但抛的是 `IllegalStateException`，被兜成 500 ——
  **一个设计上就不提供的端点表现得像服务端故障**，污染错误率监控与 canary 告警，
  调用方也拿不到可判读的业务码。
- **修复**：改为共享助手 `unsupported(useInstead)` 抛 `BusinessException(API_SERVICE_UNSUPPORTED)`，
  与本项目对 `queryExport` 等「接口未实现」场景的既有口径一致（**501**）。
  6 处调用点分别指向 `createMy`/`disableMy`/`getMyList`×2/`enableMy`/`deleteMy`。
- **验证**：`/personalToken/getPage` 由 500 → `200 + code=501 + 「私人令牌不支持此操作，请改用 getMyList」`。
- **注**：`main` 的该文件仍是 `throw new IllegalStateException("请使用createMy")` → `main` 同样带病。

### K-10 请求绑定异常**无人接管**，几十条手写校验消息全部不可达（已修复 · `main` 同样带病）

- **现象**：全局只有 2 个 advice（`BusinessExceptionAdvice` 只管 `BusinessException`、
  `DataIntegrityViolationAdvice` 只管数据库约束冲突）。Spring MVC 在进入控制器方法**之前**
  抛出的那批异常全部落到容器默认错误页：
  ```json
  {"timestamp":"...","status":400,"error":"Bad Request","path":"/sale/add"}
  ```
- **危害链**：该响应体**没有 `code` 也没有 `message`**，而前端 `src/api/axios.ts` 的错误分支读的
  正是 `data?.message`（L215/224/234），取不到就退回泛化文案。
  直接后果是**实体层几十条手写校验消息全部无法到达用户** ——
  例如 `SaleEntity.customer` 上的 `@NotNull(groups = WhenAdd.class, message = "客户不能为空")`，
  用户只看到「参数校验失败」。探针统计：28 处校验错误返回裸 Spring 体。
- **修复**：新增 `RequestBindingExceptionAdvice`，按房内规则
  （见 `DataIntegrityViolationAdvice` L31-32：**HTTP 200 + 业务码，不真返回 4xx/5xx**）映射 6 类异常，
  错误码语义**分开**：
  | 异常 | 码 | 说明 |
  | --- | --- | --- |
  | `BindException`（含子类 `MethodArgumentNotValidException`） | 4002 | 汇总字段级消息，`字段: 消息`，最多 3 条以 `；` 连接 |
  | `ConstraintViolationException` / `HandlerMethodValidationException` | 4002 | |
  | `MissingServletRequestParameterException` / `MissingPathVariableException` | 4001 | 带参数名 |
  | `MethodArgumentTypeMismatchException` | 4002 | 带参数名与期望类型 |
  | `HttpMessageNotReadableException` | **按 cause 分流** | 见下 |
  | `HttpRequestMethodNotSupportedException` / `HttpMediaTypeNotSupportedException` | 405 / 415 | |
- **`HttpMessageNotReadableException` 必须按 cause 分流**：它混装两种完全不同的客户端错误，
  否则会把「值填错了」误报成「你发的不是 JSON」，把人往错误方向带：
  - cause 是 `MismatchedInputException` → **4002**，并从 Jackson 引用路径点出字段名；
  - 其余（`JsonParseException` 等语法错误）→ **5003**。
  - **只取路径不取 Jackson 原始消息**：原文含内部类全限定名
    （如 `cn.hamm.spms.framework.query.Page`），对用户是噪音，还**泄露包结构**。
- **刻意不做 `Exception` 兜底**：`backendHttpBoundary.spec.ts` 对该边界有明文约束。
  保持这一纪律的收益是**意外异常仍产生真实 HTTP 500**，网关与 Prometheus
  `http_server_requests` 不会因全量转 200 而致盲；这类异常本来也没有可转达的具体消息，
  完整堆栈进日志即可。（本轮已验证 canary **不**依赖 HTTP 5xx：
  `CANARY_BATCHES` 是数据权限 enforce 放量、`check-canary-readiness.mjs` 是静态门禁，
  故兜底在技术上安全 —— 但仍按规范纪律拒绝。）
- **顺序**：本 advice 用 `LOWEST_PRECEDENCE`，让两个 `HIGHEST_PRECEDENCE` 的 advice 先接管各自异常。
- **验证（10/10）**：
  ```
  POST /sale/add {}                     → 200 code=4002 "customer: 客户不能为空"
  GET  /dataFieldPolicy/my-mask-map     → 200 code=4001 "缺少必要的请求参数「resourceKey」"
  POST /user/getDetail {}               → 200 code=4002 "id: ID 不能为空"
  POST /material/getPage 非法 JSON      → 200 code=5003
  GET  /material/add（仅 POST）         → 200 code=405  "不支持 GET 请求方法，本接口支持：[POST]"
  POST /material/getPage text/plain     → 200 code=415
  POST /user/getDetail {"id":"abc"}     → 200 code=4002 "字段「id」取值与期望类型不符，请检查提交内容"
  ```
  正常请求不受影响；**错误响应同样带 `traceId`**（`ApiResponseAdvice` 对异常处理器返回值也生效）。
- **探针复测**：`OK` 83 → **89**，`SERVER_ERROR` 8 → **0**，原 28 个裸 400 转为带业务码的 4002。

### K-11 树形页面与授权弹窗请求了**后端不存在**的 `getTreeList`（已修复）

- **现象**：真机实测 6 个树形服务的 `getTreeList` **全部 HTTP 404**，而 `getList` 全部 200：
  ```
  /permission/getTreeList  404     /permission/getList  200，29/35 个节点带 children
  /menu/getTreeList        404     /menu/getList        200，4/5
  /department/getTreeList  404     /department/getList  200，1/1
  /storage/getTreeList     404     /storage/getList     200，1/4
  /structure/getTreeList   404     /structure/getList   200
  /inventory/getTreeList   404     /inventory/getList   200
  ```
- **影响面（7 处页面 + 2 个弹窗，全部渲染为空白）**：
  `factory/storage`、`factory/structure`、`personnel/department`、
  `personnel/user` 的部门筛选树、`system/menu`、`system/permission`、
  `wms/inventory` 的仓库树与库位树；以及角色授权的 `permissionEditor.vue` / `menuEditor.vue`。
  404 响应体没有 `code`/`message`，前端只能显示泛化网络错误，
  **从界面上完全看不出「端点不存在」**，排查成本极高。
- **根因**：`src/framework/http.ts` 头部自己写着「URL 拼法与后端 airpower 控制器保持一致（后端不动）」——
  `getTreeList` 是 **airpower `CurdController` 的旧契约**。B0 退役 airpower 底座时后端没有实现它，
  前端却继续按旧名字发请求。**这是本轮第 3 个 airpower 退役回归，差别在于它断在前端一侧。**
- **为何修前端而不是给后端补端点**（重要，避免后人改回去）：
  后端 `getList` 对树形实体**本来就返回嵌套树** —— `Trees.buildTreeList` 在三个层次被调用
  （`PermissionController` 的 controller 级 `afterGetList`、`Menu`/`DepartmentController` 直接覆写端点、
  `Storage`/`StructureService` 的 service 级 `afterGetList`）。
  而新增端点会新增权限身份 `X_getTreeList`，`RequestInterceptor.checkUserPermission`
  是**从数据库查权限并 fail-closed**：
  ```java
  PermissionEntity needPermission = permissionService.getPermissionByIdentity(permissionIdentity);
  FORBIDDEN.whenNull(needPermission, "权限不存在");
  ```
  且该判空在 SYSTEM_ROOT 的 bypass 判断**之前**；而把扫描结果写库的
  `PermissionService.loadPermission()` **只被 `DevDataInitRunner` 调用**，
  受 `init.lock` 与 dev profile 双重限制。
  结果是：**新端点在权限行落库前对所有角色都是 403「权限不存在」，包括超管。**
  为一个后端早已用 `getList` 满足的契约去手写权限播种脚本
  （还必须与 `PermissionScanner.toEntity` 的输出逐字一致，否则下次 `loadPermission` 会冲突），
  得不偿失。
- **修复**：`urlGetTreeList` 由 `'getTreeList'` 改为 `'getList'`，同步头部 URL 对照表。
  **保留 `getTreeList()` 方法本身** —— 它与 `getList()` 除默认 `apiUrl` 外逐字相同，
  调用点 `useTable({ tree: true })` 表达的是「不分页 + 要嵌套 children」的语义，
  这个名字在调用侧仍有价值，只是不再对应一个独立的后端 URL。
- **连带修复（K-12）**：演示 mock 的树形分派。

### K-12 演示 mock 未镜像「`getList` 返回树」的真实契约（已修复）

- **现象**：K-11 修复后，演示模式下权限授权弹窗渲染出的行**有名无 identity**
  （"库存导出"、"用户修改"…），不是权限树；`personnelDomain.spec.ts` 既有用例随之失败。
- **根因**：`src/demo/mock/engine.ts` 按 URL 末段 `action` 分派，
  `resolveAuthTree` 只认 `action === 'getTreeList'`；而通用分支里
  `ARRAY_ACTIONS` 又排在树分支**之前**，`getList` 会被拍平成扁平假数据 ——
  表现为**树表渲染得出行、却永远没有层级，且不报错**。
- **修复**：
  - 新增 `TREE_RESOURCES`（`menu`/`permission`/`department`/`storage`/`structure`）与 `isTreeRequest()`，
    **与后端严格对齐**（这 5 个实体在后端都 `implements TreeNode`，且 `getList` 本身返回嵌套树）；
  - 把树分支**提到 `ARRAY_ACTIONS` 之前**，并注释说明为什么必须在前；
  - 响应形状按 `action` 区分：`getTree` 返回信封，`getTreeList` 与树形资源的 `getList` 都返回**裸数组**
    （框架层见非数组直接返回 `[]`，形状错会静默变空树）。
- **验证**：`demoPreview.spec.ts` 16/16 未受影响；`personnelDomain.spec.ts` 恢复全绿。

### K-13 权限授权弹窗**勾父节点授不出任何权限**（静默失败，已修复）

- **现象（多角色扮演实测）**：只给角色绑 6 个**父节点**权限
  （`Inventory`、`Input`、`Storage`、`Structure`、`Material`、`Unit`）时，该角色用户访问任何端点都被拒：
  ```
  /inventory/getPage → 403 你无权访问 库存-分页查询 (Inventory_getPage)
  ```
  改绑 **65 条（含 59 个叶子身份）**后同一请求返回 **200**。
- **根因是两侧语义不匹配**：
  - 后端 `RequestInterceptor` 按**精确叶子身份**匹配且 fail-closed，
    `identity = 控制器身份 + "_" + 方法名`；**父身份 `Inventory` 不对应任何端点，授了等于没授**；
  - 前端 `permissionEditor.vue` 用的是 **`ElTable` 树表**（`:tree-props` + `type="selection"`），
    而 **`ElTable` 的树模式不做父子级联勾选**（级联是 `ElTree` 的语义）。
- **对比佐证这是缺陷而非设计**：`menuEditor.vue` L20 明确写了
  「**`check-strictly` 必须保留**：菜单树里父节点只是分组，勾父节点不该连带勾选全部子项」——
  菜单树不级联是**刻意且有文档**的；但权限树的父节点**根本不是一个可用授权**，情况完全不同，
  而权限树侧没有任何这类说明。
- **危害等级**：这是最坏的一类缺陷 —— 管理员勾「库存」→ 保存 → 提示「授权成功」→
  该角色实际**零权限**，**全程无任何报错**，反馈是正向的、结果是空的。
- **修复**：新增 `onRowSelect`，在 `@select` 时把勾选状态**向下级联**到全部后代，
  复用既有的 `flatten` 助手（`flatten([row])` 去掉首项即后代），不另写一份递归。
  **刻意不向上联动父节点**：父身份本身不授权任何东西、留着无害；
  而「取消一个子操作就顺带取消父节点」会让**部分授权**在树上无法表达。
- **测试**：`personnelDomain.spec.ts` 新增 1 例（16 → 17）。
  用 `.text-identity` **全等**定位行（父身份是子身份的前缀 `Inventory`/`Inventory_getPage`，
  用 `includes` 会串到子行）；并**先断言父节点自身被勾上**，
  以便失败时区分「jsdom 事件模拟没打通」与「级联逻辑有问题」两种完全不同的原因。

### K-14 `authorizePermission` 的成功文案误抄自 `authorizeMenu`（已修复 · `main` 同样带病）

- **现象**：`RoleController.authorizePermission` 返回 `"授权菜单成功"`，与紧邻的 `authorizeMenu` 完全相同。
- **危害**：两个授权弹窗在角色列表里是**并排的两个按钮**，管理员授权「权限」后弹出「授权菜单成功」，
  会让人以为保存到了错误的一侧，进而重复操作或误判授权未生效。
- **修复**：改为 `"授权权限成功"`，留注释说明成因避免再被抄回去。
- **顺带确认两处「不是缺陷、无需改动」**（记录以免后人误改）：
  - `authorizeMenu`/`authorizePermission` 直接调 `service.update(role)`，走的是 **`BaseService` 层**
    `beforeUpdate`；而类内覆写的 `beforeAppUpdate` 把 `menuList`/`permissionList` 都置 null，
    只在 **controller 层** `beforeUpdate`（即 `@PostMapping("update")`）链路上生效。
    所以普通 `/role/update` **不会**悄悄改写授权集合，必须走这两个专用端点 —— 是**刻意设计**。
  - 授权语义为**整体替换**而非累加，已真机验证：同一角色先绑 6 条父节点，
    再经 `authorizePermission` 提交 65 条，回读角色详情为 **65** 条，未出现 71 条的累加结果。

### K-15 退场模块的目录数据未随之收敛（已修复，即 V12 的 K-5 + K-6 + K-4 三部分）

见 K-4 / K-5 / K-6。三者同源（模块退场但目录数据滞留），故合并为一个迁移 **V12**，
头部有总述、尾部有 4 条自检 SQL。**V12 已成功应用**
（`Successfully applied 1 migration to schema kubo, now at version v12`），
故**不可再编辑或拆分**（Flyway checksum drift）；后续任何 schema/数据修复走 **V13**。

### K-16 8 个只读端点 500 的汇总（已修复）

K-7 / K-8 / K-9 三项合计造成 **8 个只读端点** HTTP 500
（`/inventory/{getPage,getList,getCount,getStat}`、`/structure/getList`、`/storage/getList`、
`/personalToken/{getPage,getList}`）。探针复测 `SERVER_ERROR` **8 → 0**。
另注：这 8 个都在**只读**路径上 —— 而本轮之前的探测脚本只 POST 写端点，
**16 个 GET 端点从未被测过**，这也是它们长期潜伏的原因。

### K-17 `403` 被用来表达「缺参数 / 数据不完整」（已修复 · `main` 同样带病）

- **现象**：WMS 三个单据服务把两类完全不同的问题都报成 `FORBIDDEN`(403)：
  ```
  InputService  L245  FORBIDDEN.show("请传入入库仓库")
  OutputService L223  FORBIDDEN.show("请传入出库来源库存信息")
  MoveService   L214  FORBIDDEN.whenNull(storage, "移库单目标仓库未配置")
  MoveService   L220  FORBIDDEN.whenNull(from,    "移库明细来源库存未配置")
  ```
- **危害**：403 的语义是**授权失败**。调用方与监控看到它会去查角色配置、权限目录、
  令牌作用域 —— 而真正的问题在**请求体**或**数据完整性**上，排查方向被彻底带偏。
  前端 `axios.ts` 的 403 分支还会渲染「没有权限访问」（L215），
  用户得到的提示与真实原因完全无关。
- **修复按「数据来源」区分两种语义**（这是本项最重要的判断，两处**不该**统一成一个码）：
  - **读请求载荷的 → `PARAM_MISSING`(4001)**：`InputService.afterDetailFinishAdded` 的
    `inputDetail.getStorage()`、`OutputService` 的 `outputDetail.getInventory()`。
    这两个入参实体来自 `addFinish` 的**请求体**，而 `addFinish` 的校验分组
    `WhenAddFinish` **只强制 `quantity`** —— 调用方确实可能漏传，名副其实是「缺参数」。
    （对照：`InputService` L248 与 `OutputService` L214 都另外用 `detailService.get()`
    取 `existDetail`，可见入参那个是**载荷**、不是库中副本。）
  - **读库中已持久化数据的 → `DATA_NOT_FOUND`(404)**：`MoveService` 的
    `bill.getStorage()` 与 `existDetail.getInventory()`，分别来自
    `get(existDetail.getBillId())` 与 `detailService.get(detailId)` 取回的**库中单据**。
    而 `MoveEntity.storage` 与 `MoveDetailEntity.inventory` 在 `WhenAdd`/`WhenUpdate`
    分组下都是 `@NotNull`，正常写入路径不可能为空 → 触发即属**数据完整性**问题，
    既不是授权失败(403)也不是调用方漏传(4001)。
- **顺带清掉两处死代码**：`InputService` L246 与 `OutputService` L224 在 `show()` 之后
  各有一个 `return;`。`ErrorCode.show(...)` 是 `default void` 且**恒抛** `BusinessException`
  （`ErrorCode` L26），编译器无从得知才让作者补了防御性 `return`，实际**永不可达**。
  房内其他位置（如 `FORBIDDEN.whenNull(...)`）也都不带 `return`，去掉后与既有风格一致。
- **刻意未改的相邻调用**（语义不同，改了反而错）：
  `InputService` L287 / `OutputService` L229-230、L247 的
  `FORBIDDEN.whenNotEquals(..., "物料信息不匹配")`、
  `FORBIDDEN.whenNull(inventory, "未找到指定的出库来源库存")`、
  `MoveService` L224 的 `FORBIDDEN.whenNull(material, "来源库存物料信息缺失")`
  —— 前者是业务规则/一致性校验，后者是库中数据缺关系，都不属上述两类。
  其中「物料信息不匹配」是否应改为 `PARAM_INVALID`(4002) 值得单独评估，
  但它同时承担「防止跨单据串改明细」的**安全语义**，改动需连带复核测试口径，见 §4 P2-9。
- **验证**：`mvn -B clean test` → `BUILD SUCCESS`，**892 / 0 / 0 / 1 skipped**（与基线一致）。
  已 grep 确认 `SPMS-Server/src/test` 下**无任何用例**断言这四条消息文本或依赖其 403 码；
  业务全链路脚本 `bizscenario2.js` 走的正是这三条代码路径，修复后仍 **46/46**。
  真机实测（脚本 `%TEMP%\k17check.js`，新建入库单 id=163 / 出库单各一张）：
  ```
  入库漏传 storage      → code=4001  「请传入入库仓库」            ✅（修复前为 403）
  入库再漏传 quantity   → code=4002  「quantity: 数量不能为空」     ✅（K-10 的 advice 生效）
  入库补齐 storage      → code=200   「添加完成数量成功」           ✅ 正常路径未破坏
  出库漏传 inventory    → code=4001  「请传入出库来源库存信息」      ✅（修复前为 403）
  出库补齐 inventory    → code=200   「添加完成数量成功」           ✅ 正常路径未破坏
  ```
  「补齐后仍 200」这一条是**必要**对照：只把 403 改成 4001 很容易顺手把校验写严，
  导致原本能过的请求被拒；有了它才能证明改的只是**错误码语义**、不是行为。

---

## 2. 修复清单（9 个提交）

| # | 提交 | 类型 | 内容 | 对应缺陷 |
| --- | --- | --- | --- | --- |
| 1 | `36391014` | `fix(build)` | pom：`mysql-connector-j`（runtime，版本交 BOM）+ `maven-compiler-plugin` 的 `<parameters>true</parameters>` | K-1、K-2 |
| 2 | `b07fa3a7` | `fix(db)` | `BaseEntity.isPublished` 补 DB 默认值 + **V11** 幂等修存量库 | K-3 |
| 3 | `97c3e1a6` | `fix(db)` | `DataFieldPolicy`/`DataPolicy` 实体 `resource_id` 补 `bigint UNSIGNED` + **V12**（外键对齐 / 菜单收敛 / 死权限清理） | K-4、K-5、K-6、K-15 |
| 4 | `d705ed14` | `fix(api)` | `BaseService.normalizeFilter` 单点归一化 + `QueryRequest` 可空契约 Javadoc + `InventoryService` 空守卫 + `PersonalTokenController` 改 `BusinessException` + 新增 `RequestBindingExceptionAdvice` | K-7、K-8、K-9、K-10、K-16 |
| 5 | `a4716f2f` | `test(frontend)` | `backendHttpBoundary.spec.ts` 钉住 `-parameters` 与绑定异常边界（11 → 13）；`schemaContract.spec.ts` 迁移清单纳入 V11/V12 | K-2、K-10 防回归 |
| 6 | `8bbcbe8a` | `fix(api)` | `RoleController.authorizePermission` 文案 | K-14 |
| 7 | `cab18543` | `fix(web)` | `urlGetTreeList` → `getList` + 3 条契约测试（13 → 16） | K-11 |
| 8 | `e93816d8` | `fix(web)` | `permissionEditor.vue` 父子级联 + 演示 mock 镜像树契约 + 1 条测试（16 → 17） | K-12、K-13 |
| 9 | `71319375` | `fix(api)` | WMS 三服务：载荷缺字段 → `PARAM_MISSING`(4001)，库中数据不完整 → `DATA_NOT_FOUND`(404)，并清掉 2 处死 `return;` | K-17 |

*（本报告自身为第 10 个提交 `aa6ba340` 及其后续修订。）*

**新增文件**：`RequestBindingExceptionAdvice.java`、`V11__is_published_column_default.sql`、
`V12__retired_module_catalog_convergence.sql`。
**改动文件**：`pom.xml`、`BaseEntity.java`、`BaseService.java`、`QueryRequest.java`、
`InventoryService.java`、`PersonalTokenController.java`、`DataFieldPolicyEntity.java`、
`DataPolicyEntity.java`、`RoleController.java`、`http.ts`、`engine.ts`、
`permissionEditor.vue`、`backendHttpBoundary.spec.ts`、`schemaContract.spec.ts`、`personnelDomain.spec.ts`。

### 基线完整性（硬约束核验）

| 项 | 结果 |
| --- | --- |
| `main` | `a243b3e9`，`git diff main a243b3e9` **为空** → 全程未改动 ✅ |
| bf0c 树 | `git rev-parse 'bf83e95e^{tree}'` = `a7af126f117862ca9ea6bbb600fe9499adce99c6`，与归档记录**逐字一致** ✅ |
| bf0c 祖先关系 | `git merge-base --is-ancestor bf83e95e dev` → **是** ✅ |
| 临时 worktree | `D:\JAVA\Code_repository\_kubo_main_wt` 已 `git worktree remove`，`git worktree list` 只剩主工作区 ✅ |
| 工作区 | `git status --short` **为空**（`src/types/components.d.ts` 经 `git diff --numstat` 证实为纯行尾噪音，已 `git restore`，未提交） ✅ |
| 已应用的迁移 | V11、V12 均已应用到本地 `kubo` 库 → **不可再编辑或拆分**，后续走 V13 |

---

## 3. 验证证据

### 3.1 构建与测试

| 项 | 命令 | 结果 | 基线对照 |
| --- | --- | --- | --- |
| 后端单测 | `mvn -B clean test` | `BUILD SUCCESS`，**892 / 0 / 0 / 1 skipped**，1:51 | 与基线**完全一致** |
| 前端 lint | `pnpm lint:ci` | **0 error** | 一致 |
| 前端类型 | `pnpm typecheck` | **0 error** | 一致 |
| 前端测试 | `pnpm test` | **699 / 698 passed / 1 failed**（64 文件 / 63 passed） | 693 → 699（本轮 **+6** 用例） |
| 前端构建 | `pnpm build` | ✓ **29.04s**，dist **208** 文件 / **2.09 MB** | 基线 32.64s / 208 / 2.09 → **一致** |

唯一失败项 `productionDeliveryAndCanary.spec.ts` 是**既有**失败，
根因为 `node scripts/deploy-drill.mjs` 报「❌ 部署预演未通过（发现 **7 处**实机必炸点）」：
`JWT_SECRET` 不是 64 位十六进制强随机；`MYSQL_ROOT_PASSWORD`/`MYSQL_PASSWORD`/`REDIS_PASSWORD` 弱密码；
`.env` 缺 `KUBO_ALERT_WEBHOOK_URL`；`MYSQL_ROOT_PASSWORD` 与 `KUBO_ALERT_WEBHOOK_URL` 未在 `.env` 定义且无默认值。
**修复需 `pnpm docker:env` 供给 `.env`，该操作未获授权**，故保留为已知阻塞项（见 §4）。

> ⚠️ `-parameters` 生效后，理论上可能改变「依赖参数名缺失」的测试行为 ——
> 已 grep 排查，无此类测试；892 全绿亦为实证。

### 3.2 业务全链路闭环（46/46）

脚本 `bizscenario2.js` 以真实 HTTP 会话驱动，覆盖单据状态机与库存守恒：

```
采购  add → status 1 审核中 → audit → status 3 采购中
入库  add(type=3 PURCHASE) → audit → addFinish(+storage)
      → 库存 20 → 30（+10）
      → input status 4 已完成
      → 采购单**自动**翻为 5 已完成        ← InputService.afterDetailFinishAdded 的 case PURCHASE
                                            调 ChannelServices.getPurchaseService().setBillFinished()
销售  add(customer 在**单头**) → audit → status 3 销售中
出库  add(type=3, 明细引用 inventory id) → audit → addFinish(+inventory)
      → 库存 30 → 26（−4）
      → output status 4 → 销售单 status 4 已完成
移库  add(storage 单头=目标仓, 明细引用 inventory) → audit → addFinish{id, quantity}
      → 源仓 26 → 24（−2），目标仓 0 → 2（+2），**总量守恒 26 → 26**
治理  16 个 GET 端点全部 200
```

关键契约（本轮踩坑后固化，均已写入脚本注释；刻意按**方法名**而非行号引用，避免行号漂移失效）：
- `InputService.afterDetailFinishAdded` 要求载荷里 `inputDetail.getStorage()` 非空，
  否则 `PARAM_MISSING「请传入入库仓库」`（见 K-17）；
- `OutputService.afterDetailFinishAdded` 要求载荷里 `outputDetail.getInventory().getId()`，
  并校验 `inventory.material.id == detail.material.id`；
- `MoveService.afterDetailFinishAdded` 从**库里的明细**读源库存、从 `bill.getStorage()` 读目标仓，
  故 `addFinish` 载荷只需 `{id, quantity}`；
- 状态机终值：**采购 5**，**销售/入库/出库/移库 4**（`SaleStatus.DONE = 4`，不是 5）；
- 库存 Redis 锁：`lock:wms:inventory:storage:<id>:material:<id>` / `lock:wms:inventory:<inventoryId>`。

### 3.3 多角色扮演（27/27）

脚本 `roleplay2.js` **走真实 UI 路径**（`/role/authorizeMenu` + `/role/authorizePermission`，
而非 `add` 时一次性带上），新建受限角色 `WMS_OPERATOR`（65 条权限 / 5 条菜单）与用户后以其身份登录：

| 维度 | 结果 |
| --- | --- |
| 权限树 | `/permission/getList` 拍平 **311** 条（顶层 35 + 子 276），确认返回的是**嵌套树** |
| 授权语义 | 6 条父节点 → **整体替换**为 65 条（回读角色详情 = 65，未累加成 71） |
| 文案 | `authorizeMenu` → 「授权菜单成功」；`authorizePermission` → 「**授权权限成功**」（K-14 已生效） |
| 身份 | `/user/getMyInfo` 返回本人 email |
| 菜单过滤 | 我的顶层菜单 **1** 个（仓储管理 WMS）/ 全量 5 个 |
| 权限清单 | **65** 条，与授权集完全一致；系统管理类（`User`/`Role`/`Permission`/`Config`/`DataPolicy`/`DataResource`）泄漏 **0** 条 |
| 放行 | `/inventory/getPage`、`/input/getPage`、`/storage/getList`、`/material/getList` 全部 **200** |
| 树形数据源 | `/storage/getList` 顶层 4 条（1 条带 children）；`/structure/getList` 顶层 3 条 |
| 越权拒绝 | `/user/getPage`、`/role/add`、`/permission/getPage`、`/user/delete`、`/sale/add` 全部 **403**，消息精确到 identity（如「你无权访问 销售单-添加 (Sale_add)」） |
| **列级脱敏差分** | 超管 `{"name":"屏幕","buy":0,"sale":0}`（明文，走 bypass）<br>受限 `{"name":"屏幕","buy":"0*0","sale":"0*0"}`（**PARTIAL 脱敏**）<br>→ `data_field_policy(role_code='default')` 与 SYSTEM_ROOT bypass **两侧都真实生效** |
| 匿名 | 无 token → `http=200 code=401 「身份验证失败，请重新登录」`（符合房内 HTTP 200 + 业务码规则） |

### 3.4 端点探针（只读面）

`probe.js readonly` 对全部只读端点 POST `{}` 并分类：

| 分类 | 修复前 | 修复后 |
| --- | --- | --- |
| `OK` | 83 | **89** |
| `SERVER_ERROR` | **8** | **0** |
| `VALIDATION`（带业务码的 4002） | 0 | **28** |
| `OTHER`（裸 Spring 错误体） | 58 | **30** |
| `DEAD_NO_PERMISSION` | 0 | **0** |

`bindingcheck.js` 对 `RequestBindingExceptionAdvice` 的 10 条断言 **10/10 通过**。

### 3.5 数据收敛指标（V12 应用后实测）

| 指标 | 前 | 后 |
| --- | --- | --- |
| `menu` | 41 | **34** |
| 一级分组 | 8（含重复的「系统管理」） | **7，无重名** |
| 死链菜单 | 5 | **0** |
| `permission` | 463 | **311** |
| 死权限码 | 152（32.8%） | **0** |
| `role_permission_list` | 463 | **311** |
| `role_menu_list` | 0 | 0 |
| `GenerationTarget` DDL 异常 | 1 | **0** |
| 数据权限外键 | 0 / 2 | **2 / 2** |

保留项已核对：`Index`、`Index_index`、`Participant`（8 条）均在位；4 个 `system:*` 治理权限未动。

---

## 4. 未修项与建议（按优先级）

### P0 —— 会造成静默错误结果，建议尽快处理

1. **新增端点的权限行无法在生产落库**
   `RequestInterceptor` 从 **DB** 查权限且 fail-closed（判空还在 bypass 之前），
   而写库的 `PermissionService.loadPermission()` **只被 `DevDataInitRunner` 调用**，
   受 `init.lock` 与 dev profile 双重限制。
   ⇒ **生产环境新增任何端点，在权限行落库前对所有角色都是 403「权限不存在」**。
   建议：把权限目录同步从「dev 数据初始化器」里解耦出来，做成
   启动期的幂等同步（或管理员可触发的 rescan 端点），并同时具备**删除**能力
   （见下条）。这也是 K-11 选择「不新增后端端点」的直接原因。

2. **权限目录永不收敛**（K-6 的根因，本轮只用 V12 做了一次性清理）
   `loadPermission()` 只 insert-or-update、从不 delete，且只在 dev 初始化时跑。
   建议：改为「扫描结果即真相」的幂等同步（含删除与角色关联清理），
   并加一个 DB↔代码对账的巡检（本轮的对账脚本 `permaudit.js` 可直接产品化）。

> ✅ 原列于此的「`403` 被误用于缺参数」已在本轮修复，见 **K-17**（提交 `71319375`）。
> 其残留的一小项（「物料信息不匹配」是否应改 `PARAM_INVALID`）下移至 P2-9。

### P1 —— 性能与稳定性

3. **`Storage`/`StructureService.afterGetList` 的 O(N) 查询递归**
   现实现对**每个节点**递归调用 `getList` 查其子级：
   ```java
   list.forEach(item -> {
       queryListRequest.setFilter(new StructureEntity().setParentId(item.getId()));
       item.setChildren(getList(queryListRequest));   // ← 递归，每节点一次查询
   });
   ```
   N 个节点 = **O(N) 次查询**，且递归深度 = 树深（深树有栈风险）。
   项目里**已有现成方案**：`Trees.buildTreeList()` 是 **O(n)、零额外查询**，
   且 `PermissionController`/`MenuController`/`DepartmentController` 都在用。
   *未直接改的原因*：`beforeGetList` 在 `parentId == null` 时会强制置为 `ROOT_ID`
   （`StorageService` L220 / `StructureService` L186），因此**无法一次性取全量节点**；
   而在 `afterGetList` 里改用 `filter()` 取全量会**绕过数据权限谓词**（`buildPredicates`
   里注入了 `factory.storage` / `factory.structure` 的 QUERY 策略），造成越权读取。
   安全做法二选一：(a) 新增「按 `parentId IN (...)` 批量取子级」的仓储方法，
   把逐节点查询改成**逐层**查询（深度 D 的树只需 D 次）；
   (b) 提供一条「取全量但仍施加数据权限谓词」的查询路径，再交给 `buildTreeList`。
   当前数据量极小（仓库 4、库位 3），故列为 P1 而非 P0。

4. **`data_policy` 里仍有已退场资源的策略行**
   实测 93 行中含 `asset.device`（该模块已退场，菜单与权限已在 V12 清掉）。
   建议：V13 清理 `data_resource` / `data_policy` / `data_field_policy` 中
   指向已退场资源的行，并加对账巡检。

### P2 —— 一致性与可维护性

5. **`queryExport` 口径不一致**
   14 个控制器返回 `200/code=501 当前接口不支持此操作`
   （`user`、`unit`、`role`、`personalToken`、`permission`、`participant`、`menu`、`file`、
   `department`、`dataResource`、`dataPolicy`、`dataFieldPolicy`、`config`、`coderule`），
   另 14 个返回 `200/code=404 文件暂未准备完毕`。
   建议：以 `/dataResource/export-gaps`（当前返回 **4 项**）为权威清单，
   核对前端是否仍在不支持导出的页面渲染导出按钮。

6. **`DataResourceEntity` 重复声明 `@Id` 遮蔽 `BaseEntity`**
   `DataResourceEntity extends BaseEntity<DataResourceEntity>` 却又自己声明
   `@Id @GeneratedValue(IDENTITY) @Column(name="id", nullable=false) private Long id`（L33-36），
   **遮蔽**了 `BaseEntity.id` 的 `bigint UNSIGNED` `columnDefinition` 与 `@Meta`；
   其余 datapermission 实体同模式。这正是 K-4 外键符号性不匹配的**同类**成因。
   建议：删除重复声明，统一继承。

7. **菜单↔前端视图缺对账守卫**
   `menuContract.spec.ts` 只校验 `src/demo/fixtures/menu.ts`，
   **不校验真实 DB 菜单与 `src/view/console/**` 的对应关系** —— K-5 的 5 个死链正是这样漏过的。
   建议：加一个对账用例（可用 V12 尾部那 4 条自检 SQL 的等价断言）。

8. **文档过期**
   - `LOGIN.md` 仍记载已退场的 设备/MES/聊天室 种子数据；
   - `LOGIN.md` 与 `STARTUP.md` 都声称支持「手机号或邮箱 + 密码」登录，
     但代码只接受邮箱（`resolveLoginAccount` 对 `VIA_ACCOUNT_PASSWORD`/`VIA_EMAIL_CODE` 用 email，
     phone 只用于 `VIA_PHONE_CODE`）。实测手机号登录返回
     `code 4002 「请确认传入有效的邮箱」`。

9. **「物料信息不匹配」的错误码待评估**（K-17 的残留项）
   `InputService` L287、`OutputService` L230/L247 用
   `FORBIDDEN.whenNotEquals(detail.getMaterial().getId(), ..., "物料信息不匹配")`。
   从「调用方传了不一致的值」看应是 `PARAM_INVALID`(4002)；
   但它同时承担**防止跨单据串改明细**的安全语义，403 在那个视角下也讲得通。
   本轮刻意未改：需要先确定这条校验的**定位**（输入校验 vs 授权边界），
   再连带复核相关测试口径与前端提示文案。建议单独评估。

10. **`init.lock` 的 CWD 相对语义**（GUI-1 的遗留建议，本轮未处理）
    建议改为基于数据源的标志位（如 `config` 表 `INIT_DONE`），
    使初始化幂等性与运行目录彻底解耦。

### 已获授权范围外 / 用户明确推迟

- `.env` 供给（`pnpm docker:env`）—— **未授权**，故 §3.1 那 1 个失败项保留。
- `P2-token-scope`、`P3-legal-page` —— 用户明确推迟。
- SSRF 残留缺口（**已接受**）：DNS rebinding 与响应重定向需要一个「永不跟随重定向」的 sender。
- 56 个已归档独有提交的 triage。
- `pnpm check` 完整 21 步链从未整体跑过（本轮分别跑了 lint/typecheck/test/build 四项）。

---

## 5. 复现与工具

| 工具 | 位置 | 用途 |
| --- | --- | --- |
| `bizscenario2.js` | `%TEMP%` | 业务全链路闭环驱动（46 断言），产出 `kubo_biz_scenario.json` |
| `roleplay2.js` | `%TEMP%` | 多角色扮演 + RBAC + 脱敏差分（27 断言） |
| `probe.js readonly` | `%TEMP%` | 只读端点全量探测与分类 |
| `bindingcheck.js` | `%TEMP%` | `RequestBindingExceptionAdvice` 的 10 条断言 |
| `k17check.js` | `%TEMP%` | K-17 错误码语义验证（缺字段 4001 / 校验 4002 / 补齐后仍 200） |
| `permaudit.js` | `%TEMP%` | 用 `/v3/api-docs` 反推存活权限身份，与 DB 对账 |
| `apiexplore.js` | `%TEMP%` | OpenAPI 文档检索（`op`/`grep`/`tag`） |

登录：`POST /user/login {"email":"admin@hamm.cn","password":"Aa123456"}` → `data` 为 token（长度 83）。
调用统一用 `Authorization: Bearer <token>` —— .NET 的 `CookieContainer` 会拒绝
plain http 下带 `Secure` 的 `authorization-key` cookie。

日志：`%TEMP%\kubo_backend{3..9}.log`、`kubo_test_params.log`、`kubo_test_final.log`、
`kubo_fe_full.log`、`kubo_build_final.log`、`kubo_frontend.log`。

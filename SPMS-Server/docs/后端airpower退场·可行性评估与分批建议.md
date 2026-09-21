# 后端 airpower 退场 · 可行性评估与分批建议（2026-09-18）

> 触发：连续两次指令「继续删除 `@airpower/web`，直到删干净为止；一次性或多次删净再一起修，别边修边补，**并提供可行性建议**」。
>
> 本文回答三件事：**还剩什么**（实测）、**能不能删**（结论 + 代价量级）、**怎么删**（分批与验收）。
> 所有数字都能用文中的命令复现；工具已入库：`pnpm airpower:backend`。

---

## 2026-09-22 终态更新：P5 协议边界完全解耦与 POM 物理依赖拔除，AirPower 彻底归零（0/0/0）

- **后端足迹归零**：0/521 个 Java 文件 · 0 个符号 · 0 处引用（剥注释口径）。
- **物理依赖拔除**：`pom.xml` 中已彻底移除 `cn.hamm:airpower-curd` 及 `airpower.version` 属性，全面由 Spring Boot 3.4.4 BOM 托管原生 Starter。
- **全栈协议与工具自持**：
  - 核心基类体系：`BaseController`、`BaseService`、`BaseEntity`、`BaseRepository`、`RequestInterceptor` 全部自持；
  - 核心工具链：自持 `RedisHelper`（带分布式互斥锁）、`AccessTokenUtil`、`PasswordHasher`、`JsonCodec`、`CsvFormulaSanitizer`；
  - 异常与元数据：自持 `ErrorCode`、`BusinessException`、`ApiResponse`、`ApiResponseAdvice`、`@Description`、`@ReadOnly`；
  - 统一配置前缀：已全部收敛至 `spms.api.*`。
- **验证通过**：`pnpm airpower:backend --check` 0/0/0 门禁锁死，`pnpm check` 18 项门禁全绿，纯 JDK 17（17.0.8）编译与 138 项底座契约测试全过。
- 详见 [主干 P5 执行记录](后端主干迁移·P5协议边界解耦与POM物理依赖拔除.md)。

## 2026-09-21 历史记录：P1b 密码散列自持与成功响应接入，耦合破 500

## 2026-09-21 P1 阶段记录（数量已被 P1b 更新）

- 不再把外围工具减量当成核心替换：先接管请求/响应/异常/权限契约，再替换 Controller/Service/Entity/Repository 继承链，
  然后整仓编译和登录/权限/库存/导出回归，最后删除依赖/配置。不同时迁 ORM。
- P1 本批落实错误响应与业务异常：项目 ErrorCode/BusinessException/ApiResponse 不引用旧框架；
  Errors/CustomError 退出 IException，数据库错误处理器改自持返回契约，登录保留新旧失败计数。
- 显式 HTTP 桥仍把 ApiResponse 接回旧 Json，防止跳过字段过滤/脱敏/trace。桥和旧成功响应不可假称已删除。
- 当前 **155 / 478 文件、42 符号、511 引用**。真实 MVC 21 项通过；旧 core 为固定源码校验编译参考，未冒充发布 JAR/整仓验收。
- 详见 [主干 P1 执行记录](后端主干迁移·P1错误契约接管.md)。

## 2026-09-21 B7a 阶段记录（数量已被 P1 更新）

- 当前工作区实测 **159 / 470 文件、43 符号、517 引用**；当前 Flyway 为 V1–V10，共 10 个 SQL。
  用户引用的 171/534、593 引用、26 迁移不是此工作区快照；不按旧数字或棘轮通过宣称完成。
- 已自建 Cookie 经过真实 Spring/Boot/Servlet 切片的 14 项验证，然后在 curd 依赖上排除旧 Cookie 模块、
  删除旧 Auto 启动引用；源码 cookie 模块归零。实际 Maven 依赖树/全应用启动仍待验收。
- 新的声明解析器区分 dependency、dependencyManagement、exclusion，`stack:project` 显示真实排除项与“后端未完成”。
- `pnpm check` 58 文件 / 584 用例通过；JDK 107、Jackson 25、Cookie 14 契约分别通过。
  详见 [B7a 记录](后端B7a·Cookie传递模块退场.md)。

## 2026-09-21 B4a 阶段记录（数量已被 B7a 更新）

- 当前 **160 / 468 文件、44 符号、518 引用**。14 服务移除 Page/Sort 的直接导入，现有 BaseService 保留明确 DTO 边界。
- 原 14 份分页计算归并到 JDK-only ReadPage；服务文件净删 331 行（未扣共享代码与测试），
  默认值、200 上限、int 偏移/总数、页数计算不变；筛选、shadow、权限 Mapper、关系补齐、JPA 回退保留。
- 17 个 JDK 生产源码 / 107 契约通过，正式 Jackson 子集 25 契约通过；`pnpm check` 57 文件 / 566 用例通过。
  本轮新增 5 项真实 DTO 接线测试未执行，完整 Maven 重试仍在下载阶段 TLS 握手失败。
- 不是把查询模型整体改包名或退场；详见 [B4a 记录](后端B4a·读侧分页收口.md)。

## 2026-09-21 B2d 阶段记录（数量已被 B4a 更新）

- 当前 **160 / 466 文件、44 符号、540 引用**；四处内部 Json 文本调用已迁，自持 JsonCodec 不含 HTTP 封装。
- 新取得正式 Jackson 2.18.3/Lombok 1.18.36，字节经 Maven Central 发布校验和核对，依赖不进入 Git。
  真实编译 5 个生产源码（含真实 DTO 与审计快照）并执行 25 项协议契约，不生成框架/注解处理器 stub。
- 原 JDK-only 的 91 项契约与 `pnpm check` 536 用例通过。旧 HTTP Json、用户缓存和异常运行时仍保留。
- **部分依赖可取得不等于 Maven 全链路打通**：当前全应用编译/Redis/HTTP 接线仍待验收。
  详见 [B2d 内部 JSON 文本退场](后端B2d·内部JSON文本退场.md)。

## 2026-09-20 B3a 阶段记录（数量已被 B2d 更新）

- 当前 **161 / 462 个 Java 文件、44 个符号、544 处引用**。airpower-curd 6.4.3 仍在。
- 三个 Phone 字段改标准 Pattern，保留座机/手机/空串/分组/文案；UserEntity 的通配导入曾隐藏第三处使用。
- 通配展开真实增加 3 引用，三枚举移除无用字典标记，不藏增量、不放宽预算；本批净减 2。
- 16 个生产源码独立 Java 17 编译，91 项契约通过；`pnpm check` 55 文件 / 523 用例通过。
  新增 17 项真实 Jakarta/错误/配置接线契约待 Maven 环境验证。代码检查点 54d13b8 已推送；
  CI run 35522617966 仍因账户付款/额度限制未启动任何构建步骤，没有合并或发布。
- 详见 [B3a 记录](后端B3a·电话约束与通配清理.md)。

## 2026-09-20 B2c 阶段记录（数量已被 B3a 更新）

- 当前 **162 / 459 个 Java 文件、46 个符号、546 处引用**。airpower-curd 6.4.3 仍在。
- 5 个业务枚举去掉不必要的 IDictionary 依赖和代码生成注解，15 个生产源码能真实独立编译；
  仍被旧 Dictionary 约束的 20 个枚举、ConfigFlag 与单据状态/异常接口保留，未硬删校验。
- TaskUtil 的两处直接调用改为同步 try/catch / 手工测试 JDK 执行器。没有改 JWT 算法、事务与 HTTP 错误协议。
- 79 项 Java 独立契约通过；`pnpm check` 54 文件 / 508 用例通过。Maven 下载重试仍被 TLS 握手中断，
  新增 6 项接线测试未执行。详见 [B2c 记录](后端B2c·普通枚举与任务残留清理.md)。

## 2026-09-20 B2b 阶段记录（数量已被 B2c 更新）

- 当前：**168 / 457 个 Java 文件、47 个符号、556 处引用**；airpower-curd 6.4.3 仍在。
- 14 个 Mapper 导出的 CSV 组装与 3 处反射工具导入已替换。三个旧元数据注解在适配点显式保留；
  JPA 回退仍运行 jar 内的旧导出，不等于所有 CSV 已脱离 AirPower。
- 9 个自持工具类真实 Java 17 编译、64 项契约通过；全量源码另过 javac AST（不含类型检查）。
  `pnpm check` 通过，53 文件 / 492 用例。完整 Maven 下载仍因 TLS 握手失败未进入编译。
- 修复 B2a 的 List 漏导入、历史重复注解与悬空领料实体关联；没有改旧 Flyway 校验和或删库数据。
- 详见 [后端B2b·CSV与反射退场](后端B2b·CSV与反射退场.md)。

## 2026-09-20 B2a 阶段记录（数量已被 B2b 更新）

- 当前实测：**168 / 450 个 Java 文件、49 个符号、568 处引用**，仍依赖 `airpower-curd:6.4.3`。
- B2a 替换了日期、数字、随机值、文件、字符串、枚举查找、树处理；删掉无行为的定时任务占位类。
  `ITree` 的直接用法也已迁走。未动 CRUD/JPA、权限规则、旧异常运行时和 Json 协议。
- **不是简单搬包**：6 个 `framework/util` 生产类在没有第三方 classpath 的条件下，
  通过真实 JDK 17 编译和 32 项契约。未知枚举/有子节点禁止删除仍经原错误边界发出。
- **环境现状已变化**：JDK 17 已临时补齐；Maven 下载仍被 TLS 连接中断阻塞；
  本会话基线 CI 因 GitHub 付款/额度限制未启动。完整应用编译与 8 项新增接线测试仍待验证。
- `pnpm check` 通过（475 用例）；棘轮下调到 568，只说明引用下降，**不代表 B2 或后端退场完成**。
- 本批细节、兼容差异、复现命令见 [后端B2a·工具层退场与验证](后端B2a·工具层退场与验证.md)。

**下文 §一–四、§六–七是 2026-09-18 的历史盘点/决策记录，不应拿其中的数量或“没有 JDK”作为当前证据。**
用户已明确继续后端清理，不再需要重复确认是否包含后端；每批整体验收要求仍不变。

---

## 一、先对齐事实：`@airpower/web` 已经删干净了

| 检查 | 结果 | 复现命令 |
| --- | --- | --- |
| 前端源码 / 构建配置 / `package.json` 里的 `@airpower/*` 引用 | **0 处**（扫 365 个文件，剥注释） | `pnpm airpower:check` |
| `package.json` 依赖表 / `pnpm-lock.yaml` | **0 命中**；全新 `pnpm install` 后 `node_modules/@airpower` 根本不存在 | `grep -c airpower package.json pnpm-lock.yaml` |
| `pnpm check` 全链（lint → 死代码 → typecheck → 459 用例 → 各契约门禁 → build → size） | **exit=0**，首屏 270.4/450 KB · 总体 630.4/900 KB（退场前 367.9 / 735.7） | `pnpm check` |
| 门禁（防回潮） | `scripts/check-airpower-gone.mjs`，已接 CI 与 `pnpm check` | 同第一行 |

**所以「继续删 `@airpower/web`」这条指令在前端已经没有对象可删了。**
仓库里剩下的 airpower 只有一个：**后端的 Maven 框架 `cn.hamm:airpower`（6.4.3，parent POM）**。
如果你要的正是它，下面是评估；如果你指的是别的（比如某个我没识别出的前端残留），说一声，我按你的口径重新盘。

---

## 二、后端还剩多少 airpower（实测）

```
🔍 后端 airpower 足迹（180 / 436 个 Java 文件，657 处引用）
  父 POM：cn.hamm:airpower（决定 Spring Boot 版本 —— 最硬的耦合）
  依赖：airpower, airpower-curd, airpower-email
  配置键：application.yml:75、application-production.yml:49
```

| 维度 | 数字 | 说明 |
| --- | --- | --- |
| 触及 airpower 的 Java 文件 | **180 / 436（41%）** | main 158 / test 22 |
| 不同符号 | **73** | 用量 ≥3 的有 **40 个**（= 通用件，先做收益最大） |
| 引用次数 | **657** | 改动面 ≈ 这个数 |
| 依赖耦合 | **parent POM** + `airpower-curd` + `airpower-email` | parent 决定 Spring Boot / Jackson / 校验 / Redis 的版本，删掉它 = 自己接 BOM |
| 配置耦合 | `airpower.api.access-token-secret`、`request-log`、`response-log` | 生产 yml 覆盖前两项；Dockerfile 注释里的 `AIRPOWER_API_ACCESS_TOKEN_SECRET` 是同一处 |

模块分布（= 要自建的五大块）：

| 模块 | 引用 | 内容 |
| --- | --- | --- |
| `core` | 281 | `@Description`(79) `Json`(34) `IDictionary`(33) `@Meta`(26) `CollectionUtil` `TreeUtil` `DictionaryUtil` `ReflectUtil` `AccessTokenUtil` `ServiceException` … |
| `curd` | 263 | `QueryPageRequest`(40) `QueryListRequest` `Page` `Sort` `@Extends`(20) `@Search`(20) `Curd` 端点(20) `ExportHelper`(14) `Permission`(13) `CurdEntity` `CurdConfig` … |
| `exception` | 55 | `Errors.*`（`DATA_NOT_FOUND` 17 / `PARAM_INVALID` 10 / `FORBIDDEN` 10 / `FORBIDDEN_EXIST` / `FORBIDDEN_EDIT` / `FORBIDDEN_DELETE` / `UNAUTHORIZED` …） |
| `api` | 41 | `@Api`(33) `ApiController` `ApiConfig` |
| `redis` | 13 | `RedisHelper` |
| `cookie` | 3 | `CookieConfig` / `CookieHelper` |
| `email` | 1 | `EmailHelper`（**只有 1 个文件用** —— 最容易摘的第一颗果子） |

---

## 三、结论：**技术上可行，但这是"重写框架"，不是"删一个库"**

前端的 `@airpower/web` 是一个**库**：删掉它，缺什么由 `vue-tsc` 立刻列出来（十几秒一轮），
所以"一次删净 → 成批修"能成立。后端的 `cn.hamm:airpower` 是**框架 + parent POM**：
`@Api`/`@Search`/`@Extends` 这些注解驱动着 Controller 的路由、分页、搜索、导出的**运行时行为**，
`CurdController` 提供 124 个端点的公共实现，`Json` 是全站序列化口径。删掉它 = 自己实现同等语义。

两个必须说清的量级差异：

1. **收益不是"变快"，而是"不再受制于人"**：没有可测的性能/体积收益（后端不打包前端那套体积预算），
   主要是依赖收敛、版本自主、少一层黑箱。若目标是"技术栈统一、好维护"，收益成立；若目标是"删了就快"，不成立。
2. **验证成本高一个数量级**：前端一条 `pnpm check` 约 2 分钟；后端要 `mvn -q compile` + `mvn test` + 集成测试（H2/MySQL），
   单轮 5–15 分钟，且**必须能拉到 Maven 依赖**。

### 2026-09-18 的环境阻塞（历史记录，最新状态见置顶更新）

| 约束 | 实测 | 后果 |
| --- | --- | --- |
| 无 JDK / Maven | `java`、`javac`、`mvn` 均 not found | 改完 Java **无法编译**，无法验证 |
| Maven Central 不可达 | `repo.maven.apache.org` / `repo1.maven.org` 探测 **000（被墙）** | 就算装上 JDK，也拉不到依赖，`mvn compile` 跑不起来 |
| 已有的替代 | `pnpm java:check`（`scripts/java-syntax-check.mjs`）只做括号/分号/类方法计数 | **不是编译器**，抓不住"注解删了、语义没了"这类错误 |

也就是说：前端那套"让编译器把缺口列出来"的闭环，**在 Java 侧当前环境不存在**。
在这种情况下按"一次删净、不管构建失败"执行 180 个文件的改写，产出会是一大坨无法编译、无法验证、
也无法回滚定位的改动 —— 那不是"根据失败不断修复"，是**没有反馈的盲写**。

我把能做、且确定有价值的部分做完了（见 §六），把需要你拍板的放在 §七。

---

## 四、三条路线（选一条）

| 路线 | 做法 | 工作量 | 风险 | 结果 | 建议 |
| --- | --- | --- | --- | --- | --- |
| **A. 硬删** | 直接换掉 parent POM（改继承 `spring-boot-starter-parent`）+ 删 `airpower-*` 依赖 → 180 文件全红 → 成批自建 73 个符号 | 大（数周） | **极高**：一次性打穿 Spring Boot 版本管理、Jackson 配置、权限拦截、分页导出全链路；期间**任何批次都不可发布** | 彻底 | ❌ 不推荐：这不是"删库"，是"重写框架"，中途没有可用状态 |
| **B. 先摘 parent，再逐块自建**（推荐） | ① 把 parent POM 从 `cn.hamm:airpower` 换成 `spring-boot-starter-parent`，把 airpower 降级为**普通依赖**并显式钉住版本 —— 这一步就解除"框架决定我的 Spring Boot 版本"这个最硬耦合，**且可直接编译通过**；② 之后按 §五 的批次逐块自建，每块**自建 → 双实现并存 → 切调用方 → 删旧**；③ 最后删 `airpower-curd` 依赖，棘轮归零 | 中（分批各 1–3 天） | 中：每批有可编译可发布状态，可单批回滚 | 可控、可持续 | ✅ 首选 |
| **C. 只摘无用的** | `airpower-email`（1 个文件用）→ 换 `spring-boot-starter-mail`；`Errors.*`（55 处）→ 收编成项目自己的 `CustomError`（仓库里已有这套）；`application.yml` 的 `airpower.*` 键随 owner 改动 | 小（1–2 天） | 低 | 立刻减少 ~12% 耦合（56/657 + 1 个依赖） | ✅ 可作为第一步 |

> 路线 B/C 的关系：C 是 B 的**第一批**。两者都要求"每批可编译"。

---

## 五、路线 B 的分批方案（按依赖倒序，每批都可编译可测）

原则与前端同一句：**先补等价物，再切调用方，最后删旧依赖**；区别是**一批一验**（Java 侧没有一次删净的反馈闭环）。

| 批 | 自建什么 | 替换哪些符号 | 验收 |
| --- | --- | --- | --- |
| **B0** ✅ 2026-09-18 已做 | 摘掉 `cn.hamm:airpower` 父 POM，构建基线自持（Java 17 / UTF-8 / `spring-boot-dependencies` 3.4.4 BOM import / `springboot.version` / `airpower.version`） | —— | `pnpm pom:check` 离线闸门 ✅（无版本依赖在 BOM 家族白名单内、`${...}` 属性都有定义、无发版插件残留）；**待在有 JDK 的机器上补跑 `mvn -q compile && mvn test`**；`pnpm airpower:backend -- --check` 数字不变（只换版本管理，不动代码） |
| **B1** ✅ 2026-09-18 已做 | `cn.hamm.spms.common.exception.Errors`（**整只枚举逐行搬来**：常量名 / code 数值 / 文案一字未改），49 个文件的静态导入改指向本包 —— **exception 模块归零（55 → 0）** | `Errors.*`（12 个常量 + 全量枚举为将来兜底） | ✅ `pnpm text:check` / `java:check` / `auth:check` / `perms:check` 全绿；棘轮 174/60/603；**code 与文案零变化**（前端 axios 按 code 分流，故本批只搬不改） |
| **B2（部分落地）** | B2a 工具；B2b CSV / 反射；B2c 普通枚举与任务直接调用。B2d 已迁内部 Json 文本；HTTP Json、异常运行时、令牌工具未迁，JPA 导出/CRUD 内部任务仍在旧 jar | 11 个旧工具 + ITree 直接用法清零；累计净减 57 引用 | 独立 Java 17 / 79 契约、459 源文件 AST 与 `pnpm check` 通过；**完整 Maven 接线待验收**，未标整批完成 |
| **B3** | 元数据注解自建：`@Description`(79) `@Meta`(26) `@Dictionary`(13) `@ReadOnly`(12) `@Export`(12) `@ExposeAll`/`@Phone`/`@Desensitize` → `framework.metadata` | core 注解 ≈ 150 处 | 注解是**行为开关**（导出列、只读、脱敏），必须与 `ExportHelper`/`Permission` 的使用处一起切；`pnpm java:check` + 现有 `maskContract` 契约测试 |
| **B4** | 查询模型：`QueryPageRequest`(40) `QueryListRequest`(32) `Page`(25) `Sort`(17) `QueryPageResponse`(14) → `framework.query` | curd 查询 ≈ 130 处 | 124 个端点的分页/排序/搜索回归；前端 `useTable` 的请求体形状不变（`pnpm test` 里的请求体契约用例是现成的验收） |
| **B5** | `@Api`(33) + `ApiController` + `ApiConfig` + 请求日志/traceId → `framework.api` | api 41 处 + 配置键 3 处 | 路由与 traceId 行为对齐；`application.yml` 的 `airpower.api.*` 键改为自有前缀并同步 Dockerfile |
| **B6** | `Curd` 端点（`GetPage`/`GetDetail`/`Export`…）`CurdController`/`CurdService`/`CurdEntity` + `@Search`(20)/`@Extends`(20) + `Permission`(13)/`PermissionUtil` → `framework.curd` | curd 主干 ≈ 130 处 | **最硬的一批**：20 个 Controller + 124 端点；建议双实现并存（旧 `CurdController` 仍可被未迁移实体继承），一域一切 |
| **B7** | `RedisHelper`(13) → 直接用 `RedisTemplate` 封装；`CookieHelper`(3) → `CookieConfig`；`EmailHelper`(1) → `spring-boot-starter-mail` | redis / cookie / email | 登录会话、验证码、导出任务等用 Redis 的点逐个回归 |
| **B8** | 删 `airpower-curd` / `airpower-email` 依赖，`pnpm airpower:backend -- --check` 基线改 `{0,0,0}`，CI 也把后端棘轮接到 `0` | —— | 与前端同款"0 引用"门禁 |

**每批的 DoD（缺一不可）**：① `mvn -q compile` + `mvn test` 绿；② 该批符号的棘轮数字下降并更新 BASELINE；
③ 行为等价证据（同一组请求的前后响应 diff 或既有契约测试）；④ 一批一提交，可单独回滚。

---

## 六、本仓现在就能做、且我已经做了的（不依赖 JDK）

1. **后端足迹盘点器 + 棘轮门禁**（新）：`infra/scripts/check-airpower-backend.mjs`
   - `pnpm airpower:backend` —— 一眼看清"还剩什么"：文件数 / 符号数 / 引用数 / 依赖 / 配置键 /
     按模块分布 / **"要自建什么"清单（用量倒序，≥3 次的 40 个就是通用件）**；
   - `pnpm airpower:backend -- --check` —— CI 棘轮：**数字只许降**，已接进 `pnpm check` 与 CI。
     基线写在脚本里（2026-09-18 第一批后为 `{files: 183, symbols: 72, references: 655}`），每批完成时手工下调（改动可见、可 review）。
   - `--json` 给脚本读，后续可以据此自动生成"本批要改哪些文件"的工作清单。
2. **把"缺什么"变成机器答案**：这正是你说的痛点 —— 边修边补时不知道缺什么。
   现在缺什么由脚本回答（40 个通用件按用量排序），不必靠人回忆。

---

## 七、需要你拍板的三件事

1. **口径**：你说的"删干净"是否包含**后端** `cn.hamm:airpower`？
   - 含 → 我按 §五 的批次推进（**B0 已完成，见 §五**），后续每批仍需在有 JDK 的环境里验（见下）。
   - 不含（只指前端 web 包）→ 那件事已经完成（§一），我转去别的待办（显示字段 A/B/C、菜单播种、api-docs 处置）。
2. **执行环境**：后端的每一步都必须过 `mvn compile/test`。当前沙箱**没有 JDK、Maven 仓库也被墙**。
   建议二选一：
   - **你在本地/CI 跑**：我把每批的改动做成小 PR（可编译可回滚），你在有 JDK 的机器上验；
   - **给沙箱开一条依赖通道**（内部 Maven 镜像 / 预置 `~/.m2` 缓存 + `mvn -o` 离线模式）—— 有这条路我就能像前端一样自己闭环验证。
3. **起点**：要不要先做 §四 的 **C 组小刀**（`airpower-email` → `spring-boot-starter-mail`；`Errors.*` 收编到既有 `CustomError`；yml 的三个 `airpower.api.*` 键迁到自有前缀）？
   代价 1–2 天、风险低，能让后端足迹从 657 降到 ~600 并把"怎么改"的先例立起来。

---

## 附：复现命令

```bash
pnpm airpower:check                        # 前端：0 引用（硬门禁）
pnpm airpower:backend                      # 后端：足迹盘点 + 要自建清单
pnpm airpower:backend -- --json            # 机器可读
pnpm airpower:backend -- --check           # 棘轮（CI 已接）
grep -rl 'cn\.hamm\.airpower' SPMS-Server/src --include=*.java | wc -l   # 180
```

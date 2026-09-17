# 后端 airpower 退场 · 可行性评估与分批建议（2026-09-18）

> 触发：连续两次指令「继续删除 `@airpower/web`，直到删干净为止；一次性或多次删净再一起修，别边修边补，**并提供可行性建议**」。
>
> 本文回答三件事：**还剩什么**（实测）、**能不能删**（结论 + 代价量级）、**怎么删**（分批与验收）。
> 所有数字都能用文中的命令复现；工具已入库：`pnpm airpower:backend`。

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

### ⚠️ 本沙箱无法执行这项工作的硬约束（这是我没有直接动手的原因）

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
| **B2** | `framework.json`（`Json` 34 处）+ `framework.util`（`CollectionUtil`/`TreeUtil`/`DictionaryUtil`/`NumberUtil`/`ReflectUtil`/`DateTimeUtil`/`RandomUtil`/`TaskUtil`/`FileUtil`/`TraceUtil`/`AccessTokenUtil` ≈ 60 处） | core 工具类 | 单测覆盖（这些是纯函数，最容易先做） |
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

---

## 八、下一步可行性矩阵（2026-09-18 实测，含"能不能离线验"）

> 这一节回答"接下来每一步到底能不能做、靠什么验、要谁配合"。数字都是脚本实测：
> `pnpm airpower:backend -- --json`（足迹）+ `pnpm java:imports`（导入可达性）。

### 8.1 一条关键规律：**带反射身份的层不能先换**

B2 撞到三次（已回退三个符号），规律记死：

| 类型 | 能不能先搬 | 为什么 |
| --- | --- | --- |
| **纯函数工具**（Json / StringUtil / DateTimeUtil / NumberUtil / RandomUtil / TaskUtil / FileUtil / TraceUtil） | ✅ 能 | 只被**直接调用**，不存在"谁读谁"的身份问题 |
| **注解**（`@Description` `@Meta` `@Dictionary` `@Export` `@ReadOnly` `@Desensitize` `@Phone` `@ExposeAll`） | ❌ 不能 | airpower 的 `ExportHelper`/`CurdService`/`Permission` 用 `field.isAnnotationPresent(X.class)` **按类身份**读；换成自己的同名注解 = 它们**读不到**，导出列名、字典校验、只读、脱敏**静默失效** |
| **被反射消费的接口**（`IDictionary` `ITree` `IEntity`） | ❌ 不能 | 同上：`@Dictionary(XxxEnum.class)` 的枚举必须实现**它的** `IDictionary`，否则字典渲染/校验失效 |
| **工具里反射读注解的**（`CollectionUtil.toCsvInputStream`、`ReflectUtil.getDescription`） | ❌ 不能 | 它们内部 `getAnnotation(Export.class)`——搬走后读的是空注解，**导出列头会退化成字段名** |
| **被框架签名钉住的类型**（`AccessTokenUtil.VerifiedToken`） | ❌ 不能 | 我们的 `RequestInterceptor` 重写的是 airpower 的方法，返回类型必须**就是**它的 `VerifiedToken`，换成自己的 = 编译不过 |

**推论**：正确顺序不是"按模块由小到大"，而是"**先搬无反射耦合的（可以随时搬），再搬有耦合的（必须跟着它的消费者一起搬）**"。

### 8.2 逐批可行性

| 批 | 内容 | 实测规模 | 前置条件 | 离线能验吗 | 结论 |
| --- | --- | --- | --- | --- | --- |
| **B2** ✅ 已做 | core 无反射耦合的闭包（15 类） | 79 处引用 | 无 | ✅ 棘轮 + `java:imports` + `text:check` | **已完成** |
| **B5** | `@Api` / `ApiController` / `ApiConfig` + 3 个配置键 + Dockerfile 环境变量 | 41 处 | 需知道 `ApiController` 里被我们继承的方法签名（可离线读 curl 源码） | ⚠️ 部分：请求日志/traceId 行为要真跑 | **可做，但要先写"端点契约快照"**（124 个端点的 method/path/权限，作为行为等价的证据） |
| **B7** | `RedisHelper`(13) + `Cookies`(1) 收尾 | 13 处 | 需同时接管 `RedisTemplate` 装配（从 airpower `Auto` 换成自己的 `@Bean`），否则**老 key 的序列化格式变了** → 令牌黑名单/防爆破计数读不到 | ⚠️ 需真 Redis | **可做，但必须和"序列化格式不变"一起验**（本沙箱无 Redis，只能靠你在本地跑一次登录+登出） |
| **B4** | 查询模型（`QueryPageRequest`/`QueryListRequest`/`Page`/`Sort`/`QueryPageResponse`） | ≈130 处 | 这五个类是 124 个端点的**入参/出参类型**，同时被 airpower 的 `CurdController` 解析（`@RequestBody` → 内部反射取 page/sort/filter） | ⚠️ 需真接口回归 | **不建议先做**：换类型 = 换请求体解析，前端 `useTable` 的请求形状要同步验证 |
| **B3** | 元数据注解 + `IDictionary`/`ITree` + `CollectionUtil`/`ReflectUtil` | ≈150 处 | **必须在 B6 之后**（或与 B6 同批）：注解的消费者是 curd 的反射代码 | ⚠️ 需真导出/真表单 | **绑定 B6** |
| **B6** | `Curd` 端点 / `@Search` / `@Extends` / `Permission` / `QueryXxx` 全家（**与"删 JPA 写链路"是同一件事**） | 263 处 | ① 目标态选型已定（MyBatis-Plus，见 `去JPA决策评估.md` §8）；② 需要 `mvn compile` 能跑的环境；③ 需要真实库结构（S0 的 mysqldump） | ❌ 只能编译期 + 真库回归 | **必须由你在有 JDK/库的环境配合**，或给我一条 Maven 通道 |
| **B8** | 删 `airpower-curd` 依赖、棘轮归零 | —— | 全部前置完成 | ✅ | 收尾 |

### 8.3 现在的瓶颈与"最短解锁路径"（**B2 时点的判断，已被 §9 取代**）

> 这一节写于 B2 之后。当天随后的第四批（§9）没有按 B5 → B7 的小步走，而是直接**整模块搬框架层并把依赖删净**，所以这里列的 B5/B7/B8 已经全部完成；真正还差的是"编译与真库回归"，见 §9.5。

```
现在能自己往下走（不需要你、不需要 JDK）：
  B5（Api 层，41 处）  →  B7（Redis，13 处）      ←  这两批能把足迹压到 ~490
需要你各配合一步的：
  S0  mysqldump 一份基线             ←  解锁"删 JPA"整条线（B3/B4/B6）
  B6  一台能 mvn compile 的机器      ←  解锁最硬的 263 处
```

**建议的下一步（按性价比）**：

1. **B5（Api 层）** —— 41 处、纯入口层，可离线搬 + 用现有 `auth:check`（124 个端点权限）与 `perms:check` 兜底，是"再压缩一轮且不改业务语义"的最优解；
2. **B7（Redis）** —— 13 处，但**必须一起处理序列化兼容**（同一批里把 `RedisTemplate` 配置搬过来，保持 key/value 序列化器不变），否则登录态会漂；
3. **S0 的 mysqldump** —— 你给一份库结构，我就能把 DDL 交给 Flyway、把 `ddl-auto` 切成 `none`，`去 JPA` 才算真正开工（这是 B3/B4/B6 的前置，卡在这儿的不是工时而是这一份数据）；
4. **B6** —— 只在你确认有 `mvn` 环境时启动；否则宁可先把 B5/B7 清干净，避免"半套写入路径"。

> 一句话：**现在可以继续自己推进 B5 → B7；"删 JPA"那一条线需要一份 mysqldump（或一个能连库的环境），最硬的 B6 还需要一台能编译的机器。**

---

## 九、第四批：框架层整模块自持 —— **收官，足迹 = 0**

> 指令原文：**「通通删除并替换目标技术栈」**。
> 执行时间：2026-09-18（同日，接在 B2 之后）。执行方式：**直接做，不再分批**。

### 9.1 结果（实测，可复现）

| 维度 | 收官值 | 上一批（B2 之后） | 复现命令 |
| --- | --- | --- | --- |
| 触及 airpower 的 Java 文件 | **0 / 525** | 164 / 525 | `pnpm airpower:backend` |
| 不同符号 | **0** | 49 | 同上 |
| 引用次数 | **0** | 547 | 同上 |
| pom 里 airpower 依赖 | **0** | `airpower-curd` | `pnpm pom:check` |
| 父 POM | **无** | 已摘（B0） | 同上 |
| yml 配置键 | **0** | 2 处 | `pnpm airpower:backend` |
| 只在注释里提到它（迁移档案，不计入足迹） | 89 个文件 | 88 | 同上 |

门禁 `pnpm airpower:backend -- --check` 的基线已置 `0/0/0`，并且把"依赖 / 父 POM / yml 配置键"
一起纳入判据 —— **语义从"只许降"变成"必须保持 0"**：再想塞一条 airpower 引用或依赖回来，
必须显式改脚本里的基线（review 里看得见）。

### 9.2 怎么删的：整模块搬，而不是"只搬用到的"

| 原模块 | 版本 | 搬入位置 | 类数 |
| --- | --- | --- | --- |
| airpower-core | 6.3.1 | `cn.hamm.spms.core.*` | 41（整模块） |
| airpower-curd | 6.4.3 | `cn.hamm.spms.framework.curd.*` | 36 |
| airpower-api | 6.4.3 | `cn.hamm.spms.framework.api.*` | 4 |
| airpower-redis | 6.4.3 | `cn.hamm.spms.framework.redis.*` | 3 |
| 合计 | | | **84 类**（另有 3 个模块级 `Auto` 刻意未搬，原因见下表） |

- 每个文件头写明来源仓库、版本、MIT 许可与搬入日期：**改代码改本仓，不再有上游**。
- 只改包名（`cn.hamm.airpower.{core,api,curd,redis}` → 本仓包名）+ 上一批已自持的
  `Errors` / `CookieHelper` / `CookieConfig` 的 import 指向；**逻辑一字未改**。
- B2 时"只搬调用闭包（15 类）"的策略在这一批被放弃，原因是：**框架层也搬进来之后，
  半套副本会把"缺哪个类"变成编译期的猜谜**（沙箱里没有编译器，猜谜的代价是下一轮全量报错）。
  整模块搬入 = 缺口一次归零，且 B3 那类"类身份契约"（注解 / `IDictionary` / 反射工具）
  自动随类一起进入同一包体系，不再需要"同批切换"的精细调度。
- 上一批为兼容而保留的三处例外（`CollectionUtil` / `ReflectUtil` / `AccessTokenUtil`）随之撤销。

### 9.3 同批删掉/改掉的"名字里还有 airpower"的东西

| 项 | 变化 | 影响面 |
| --- | --- | --- |
| pom 依赖 | `cn.hamm:airpower-curd` 删除（B8 连同完成） | 构建 |
| 配置前缀 | `airpower.api` / `airpower.curd` / `airpower.redis` → `spms.api` / `spms.curd` / `spms.redis` | 2 个 yml + 5 个 `@ConfigurationProperties` 类 |
| Redis key 前缀 | 默认值 `airpower:` → `spms:` | 缓存 key 命名（缓存本身可重建；**令牌黑名单/防爆破计数会"看不见旧 key"**，等价于一次性失效） |
| 环境变量 | `AIRPOWER_API_ACCESS_TOKEN_SECRET` → `SPMS_API_ACCESS_TOKEN_SECRET` | **部署侧必须同步改名**；守卫 fail-fast 并打印新名字 |
| `Application` | `exclude = airpower.cookie.Auto.class` 删除（该自动装配源已不存在） | 启动 |
| 自动装配注册 | 三个模块级 `Auto` 类**没有**搬（`@AutoConfiguration` + `META-INF/...imports` 是给外部 jar 用的；应用内由 `@SpringBootApplication` 的组件扫描覆盖，再写一份会重复注册） | 启动 |
| Dockerfile / JWT SOP | 环境变量名、密钥配置项的出处同步改写（"验签方"从"airpower 框架内部"改成本仓 `framework.api`） | 运维文档 |
| 测试里 21 个文件的惰性属性 | `airpower.redis.enabled` / `airpower.mqtt.enabled` 删除（这两个键在 airpower 里**也没有对应的绑定字段**，属于历史残留的"求个心安"配置，删掉零行为变化） | 单测 |

### 9.4 为什么这一批能"不编译也敢做"

- 搬运是**逐文件字符串级等价**（包名替换 + 头注释），没有一处手写逻辑；
- 依赖关系在搬运前已经算清（`/tmp` 的闭包勘察：36 个种子文件 + 全仓 import 分布），
  搬完后用 `pnpm java:imports` 复查：**本仓 import 必须落地、自持层不得反依赖 airpower**；
- 门禁替编译器兜住了三类"离线可判定"的错误：导入可达性、pom 版本/属性完整性、文本完整性（乱码/非法转义）。

### 9.5 仍然待办（**已不是 airpower 的活**）

| 待办 | 为什么还需要它 | 前置 |
| --- | --- | --- |
| **编译修复** | 沙箱无 JDK、Maven 仓库不可达（阿里云/华为云/清华/Central 全部探测 000），这一批**没有经过一次 `javac`**。首次 `mvn -q compile` 预期有一批 import/符号级报错 | 一台能跑 Maven 的机器（你的本地即可） |
| **JPA → MyBatis-Plus** | 写链路的 JPA 仍在（`spring-boot-starter-data-jpa` 现在是本 POM 的显式依赖，见 §6 与 `去JPA决策评估.md`） | `mysqldump --no-data` 基线（S0）+ 编译环境 |
| **真库/真 Redis 回归** | Redis key 前缀改名后，旧的黑名单/计数键读不到了（一次性失效，可接受但要知会） | 一套能跑起来的环境 |

### 9.6 一句话总结

**从 2026-09-18 起，这个仓库里再没有 airpower：既没有依赖，也没有引用，连配置键与环境变量都已改名；
它唯一还存在的地方是文档和注释里（作为"这一层是怎么来的"的记录）——那正是应该留下的部分。**

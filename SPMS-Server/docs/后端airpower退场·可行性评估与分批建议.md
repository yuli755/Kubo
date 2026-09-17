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
| **B0** | 换 parent（`spring-boot-starter-parent` + 显式 `mybatis-plus` 等版本） | —— | `mvn -q compile` + `mvn test` 绿；`pnpm airpower:backend -- --check` 的**文件数/引用数不变**（这步只换版本管理，不动代码） |
| **B1** | `cn.hamm.spms.framework.error`（收编 `Errors.*` 55 处到项目自定义错误码） | `Errors.*`（12 个常量） | 错误码门禁 `pnpm auth:check` / 集成测试；错误响应的 code 值与旧值映射表留档 |
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
     基线 `{files: 180, symbols: 73, references: 657}` 写在脚本里，每批完成时手工下调（改动可见、可 review）。
   - `--json` 给脚本读，后续可以据此自动生成"本批要改哪些文件"的工作清单。
2. **把"缺什么"变成机器答案**：这正是你说的痛点 —— 边修边补时不知道缺什么。
   现在缺什么由脚本回答（40 个通用件按用量排序），不必靠人回忆。

---

## 七、需要你拍板的三件事

1. **口径**：你说的"删干净"是否包含**后端** `cn.hamm:airpower`？
   - 含 → 我按 §五 的批次推进（**B0 换 parent 可以现在就写，代码零改动、风险最低**），但**只能在能编译的环境里验**（见下）。
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

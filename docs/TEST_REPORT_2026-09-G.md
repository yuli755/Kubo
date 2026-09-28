# Kubo 平台 G 阶段：前端定向修复 + 全系统复检测试报告（2026-09-16）

> 承接 `AUDIT_REPORT_2026-09.md`（A–F 阶段）。本轮按「前端先行、逐模块推进」处理
> 报告记录项 3/4/5/6 及脱敏半成品（记录项 2 的落地延伸），并完成后端 Influx/数据权限
> 注入面加固（S-1）、MCP 端点鉴权头改造；最后对整个系统做核心链路复检与深度安全审查。
> 测试全程不触碰业务数据（见 §7）。

## 0. TL;DR

| 验证项 | 结果 |
| --- | --- |
| 前端单测/契约测试（vitest） | **302/302 PASS** |
| 前端类型检查（vue-tsc） | **0 错误** |
| 前端静态检查（eslint --max-warnings 0） | **0 告警** |
| 后端单测/集成（mvnw test -o，全量） | **677/677 PASS · BUILD SUCCESS** |
| 黑盒探针 A–G（核心业务链路全流程） | **80/80 PASS** |
| 深度安全审查（XSS / SQL 注入 / URL 携带令牌 / 越权 / 反序列化等） | 见 §5，可修项已全部修复并加测试锁 |

计划外的重要发现：**WebSocket 在此前从未真正工作过**——框架在握手线程内直接执行
Jedis 阻塞订阅，导致连接虽能建立但所有后续消息（含 PING/PONG）永远积压。本轮定位
（jstack 实证）并在应用层修复，详见 §4。

## 1. 范围与方法

- 输入：`docs/AUDIT_REPORT_2026-09.md` 的「仅记录、未改动」清单 + 用户五项要求
  （前端逐模块修复 / 全系统核心链路复检 / 深度安全审查并修复 / 不破坏现有数据 / 详细测试报告）。
- 手段：单元测试 + 源码契约测试（maskContract 类）+ 黑盒 HTTP/WS 探针（auditA–G，
  直连 8080）+ 静态全仓扫描 + 对框架 jar 的字节码反编译核对（不 fork、不改框架）。
- 环境：Windows / JDK 17.0.15 / Node 22（内置 WebSocket）/ MySQL 8.0.37 / Redis(6379)；
  重负载任务串行执行（并发跑曾出现 Node 与 JVM OOM，均已规避，非代码缺陷）。

## 2. 本轮修复清单（按模块）

### 2.1 模块 G-1 · 缺失路由落地（记录项 5）
- `src/config/routes.ts` 新增 `/privacy`、`/terms`、`/forgot-password` 三条
  `meta.guest` 路由 → 统一渲染 `src/view/legal.vue`；`routes.spec.ts` 契约钉死。
  此前登录页/文案引用这些路径但路由不存在，点击即 404。

### 2.2 模块 G-3 · rememberMe 语义落地（记录项 4）
- 新增 `src/utils/tokenScope.ts`（`markTokenScope` / `clearTokenScope` /
  `enforceTokenScope`）：令牌写入位置与「记住我」意图绑定——勾选才允许跨标签页/
  重启存活（localStorage），未勾选则锁定在当次会话（sessionStorage 指纹，
  `kubo:token-live`），应用启动时 `enforceTokenScope` 清理错位的令牌。
- 接线点：`main.ts` 启动时强制、`login.vue` 登录成功后打标、user store 登出时清除；
  `tokenScope.spec.ts` 覆盖三种场景。

### 2.3 错误上报脱敏（记录项 6）
- `src/utils/errorReportClient.ts` 增加 `redactSecrets()`：对 `token` /
  `password` / `authorization` 三类键值按模式表遮蔽后才出网；
  `errorReportClient.spec.ts` 钉死。

### 2.4 模块 H · 用户列表接入后端脱敏策略（记录项 2 的落地延伸）
- `src/view/console/personnel/user/list.vue` 使用
  `useMaskPolicy('personnel.user', ['phone', 'email'])`：`#phone` / `#email`
  具名插槽优先于框架默认渲染，策略说「遮」就遮（策略未到达/取回失败一律先遮）。
  `idCard` 维持静态遮蔽（防肩窥的展示设计，不随策略放开）。
- `maskContract.spec.ts` 新增「策略脱敏列必须真的渲染进业务表格」契约：扫描 4 个
  已接线列表视图，要求 `useMaskPolicy('资源键')` 字面量、`Policy.load()` 调用、
  每个字段的 `restricted()/masked()` 分支同时在场——**接线被删时测试先红**。

### 2.5 模块 I · WebSocket 票据化（记录项 3 的 WS 部分）
公开 URL 不再出现长期访问令牌。协议：

1. 已登录前端 `POST /websocket-ticket`（`Authorization: Bearer <token>`）→
   返回 48 位 hex 一次性票据（SecureRandom 24 字节，TTL 30s，全量上限 10000，
   同 IP 限流 30 次/分钟）。令牌吊销服务参与校验：已登出/吊销的令牌换不到票。
2. 建连 `wss://host/websocket?ticket=<票据>`。服务端在注册边缘套
   `WebSocketTicketResolvingDecorator`（框架 `afterConnectionEstablished` 是
   `final`，不可覆写）：票据**原子兑现（一次性，get-and-remove）后**把会话
   `getUri()` 改写为框架原生契约（query 即裸令牌），框架鉴权链路原样工作。
3. 兑现失败（未知/重放/过期）→ `1003 NOT_ACCEPTABLE` 关闭，不回显原因。
4. 兼容窗口：不带 `ticket` 的连接仍按框架原生「query 即裸令牌」处理（旧客户端），
   前端 `WebSocketManager` 已改为票据优先、换票失败退回裸令牌。

> 框架原生契约的反编译核实过程同时发现：**`?accessToken=<token>` 参数名写法从未
> 被框架支持**（旧前端拼接方式即此形态，即历史上 WS 鉴权从未成功，见 §4）。

新增文件：`SPMS-Server/.../common/websocket/WebSocketTicketService.java`
（票据核心 + 单测 6 项：hex 长度/一次性/未知与空值/过期/空令牌拒绝/8 线程并发
兑现仅 1 胜者）、`WebSocketTicketController.java`、
`WebSocketTicketResolvingDecorator.java`；`WebConfig.registerWebSocketHandlers`
改为装饰器注册；前端 `src/websocket/WebSocketManager.ts` 票据优先 + 建连防重入。

### 2.6 后端注入面加固（S-1，随本轮复检收口）
- `InfluxHelper.validateFluxIdentifiers`：进入 Flux 查询的 code/uuid 白名单校验
  `^[A-Za-z0-9_:.-]{1,128}$`，不合规直接拒绝（原拼接路径可注入 Flux 谓词）。
  配套单测入 `src/test/java/.../common/influx/`。
- `KuboDataPermissionHandler`：行权限 SQL 字符串常量转义——反斜杠加倍 + 拒绝
  NUL 字符，堵住以反斜杠吞噬结尾引号的经典逃逸。配套单测入
  `src/test/java/.../datapermission/mybatis/`。

### 2.7 MCP 端点鉴权头改造（记录项 3 的 URL 令牌面）
- `McpController.messages` 由「仅 `?token=`」改为：**优先 `Authorization: Bearer`
  头**，`?token=` 保留为存量 MCP 客户端的兼容通道（响应行为逐字节一致，实测
  头/参数两通道结果相同、缺令牌正确报 `accessToken is required`）。
  前端 `tools.vue` 展示的 `?token=` 连接串因此可逐步迁移到请求头形态。

## 3. 核心功能链路复检（全流程）

黑盒探针按业务域组织（脚本在 git-ignored 的 `SPMS-Server/target/`，说明见 §9）：

| 探针 | 链路 | 结果 |
| --- | --- | --- |
| auditA | 认证/会话全流程：登录→失败锁定→改密→令牌轮换→登出→他人令牌不受影响 | 22/22 |
| auditB | RBAC/数据权限：建角色→授权→越权拒绝→绑定保护→解绑回收 | 17/17 |
| auditC | WMS 核心事务：入库→库存事务→出库→并发竞争→单据状态机 | 12/12 |
| auditD | 系统配置与公共能力：私人令牌寿命/上限/属主隔离、文件秒传属主、配置权限、守卫开关 | 10/10 |
| auditE | 前端契约的后端侧回归（E 阶段修复项防回退） | 6/6 |
| auditF | F 阶段遗留专项：XFF 伪造、约束语义、交叉移库、菜单权限 | 5/5 |
| auditG | **WebSocket 票据全链路**：登录保护→票据格式→建连 PING/PONG→重放拒绝→伪造拒绝→裸令牌兼容→参数名写法必须失败→吊销令牌换票 401 | 8/8 |

前端侧全流程由 32 个 spec（302 用例）覆盖：路由/菜单契约、脱敏契约（含本轮新增
接线锁）、令牌作用域、错误上报脱敏、WS 管理器等；类型与 lint 零告警。

> 备注：全量 vitest 首跑出现过 1 例负载超时抖动（15s timeout 类），单独复跑与二次
> 全量均 302/302；后端全量在 MCP 改动前后各跑一次均 BUILD SUCCESS。

## 4. 计划外发现：框架级 WS 握手阻塞缺陷（已修复）

**现象**：票据建连成功（HTTP 101）但服务端永不回 PONG；close 前连接形同僵尸。

**定位**（jstack 实证）：`airpower-websocket` 在 `support=REDIS` 时于
`WebSocketHandler.afterConnectionEstablished` 内**同步调用**
`JedisConnection.subscribe`。Jedis 的订阅是阻塞式（Lettuce 才是注册即返回），
于是 Tomcat 分发线程被永久占用——握手不返回，该连接的后续消息
（含 PING/PONG）永远不会被分发。框架有意排除 Lettuce 并捆绑 Jedis
（`airpower-redis` pom 实测），即当前依赖组合下 **WS 功能从未真正可用**；
旧前端又叠加了「参数名拼接」的契约错误（§2.5 注），两重缺陷互相掩盖。

**修复**（不动框架 jar）：装饰器把整个 `super.afterConnectionEstablished` 交给
专用守护线程池（`ws-handshake`，cached 语义）。连接关闭时框架会
`redisConnection.close()`，阻塞的订阅随即返回、worker 线程自然回收，
无泄漏。异常路径对齐框架原语义（日志 + 1011 关闭）。auditG G03/G06 从 FAIL
转为 PASS，PING/PONG 全链路实测可达。

**部署提醒**：升级 airpower 依赖时可复核此装饰器是否仍必要（若框架改用
非阻塞订阅或把订阅挪出握手线程，可去掉线程池直接委托）。

## 5. 深度安全审查结论

| 攻击面 | 扫描方法 | 结论 |
| --- | --- | --- |
| XSS（存储/反射/DOM） | 全仓扫描 `v-html` / `innerHTML` / `eval` / `new Function` / `srcdoc` / `postMessage`；Vue 模板默认转义 | **0 命中**。前端不存在 raw-HTML 通道；无第三方 HTML 注入点 |
| SQL 注入（MyBatis XML） | 逐一复核 21 处 `${}` 拼接 | 全部安全：仅限白名单排序运算符/`#{}` 绑定参数/带令牌黑名单的工具方法；无用户可控 QueryWrapper 原生 SQL |
| Flux 注入（InfluxDB） | 拼接路径追踪 | 本轮加白名单校验（§2.6），越界输入直接拒绝 |
| 行权限 SQL 逃逸 | 数据权限 SQL 字符串生成 | 本轮加反斜杠加倍 + NUL 拒绝（§2.6） |
| URL 携带令牌 | 前端全部出网 URL 构造点 | WS 已票据化（§2.5）；MCP 增加 header 通道（§2.7）；**遗留**：`@airpower/web` 库内 `getUrlWithAccessToken` 在导出/模板下载 URL 拼令牌（库产物，无法在不 fork 的前提下修补，属短时效会话令牌 + 同源下载场景，风险有限，见 §8） |
| 命令执行/反序列化/SpEL/JNDI | 后端全仓扫描 `Runtime.exec` / `ProcessBuilder` / `ObjectInputStream` / `ExpressionParser` / `InitialContext` | **0 命中** |
| 越权 | 探针 B/D 专项 + `@Permission` 注解矩阵复查 | 属主隔离、root 边界、授权回收均按预期（auditB 17/17、auditD D03–D07） |
| 登录爆破/伪造头 | 探针 A/F 专项 | 邮箱锁定 + IP 计数 + X-Forwarded-For 不信任原始值，全部按预期 |

## 6. 验证矩阵（最终态）

```text
前端  vitest        302/302 PASS（32 文件；负载抖动 1 例复跑即过）
前端  vue-tsc       0 错误
前端  eslint        0 告警（--max-warnings 0）
后端  mvnw test -o  677/677 PASS，BUILD SUCCESS（含 WS 票据 6 项新测试）
黑盒  auditA–G      80/80 PASS（8080 实跑，探针自建自清测试数据）
```

## 7. 数据保护说明（测试未破坏现有数据）

- 探针只操作自建数据：账号全部为 `*@kubo.test`，角色 code 全部 `AUDIT*` 前缀，
  文件名 `auditd-*` 前缀；业务实体（物料/库存/单据/客户等）只做**读**与
  状态机合法性验证（auditC 的写事务在探针创建的单据内进行）。
- 提供 `SPMS-Server/target/cleanProbe.cjs`：按上述前缀清场探针数据（API 删除 +
  Redis 登录锁键清理）。发现并已修复两处**探针自身**的重跑障碍：
  ① `/user/delete` 是软删且 Redis 登录锁按邮箱长驻留（跨轮误锁）→ 清场脚本补
  Redis 键清理；② auditD 清场顺序未先删 `user_role_list` 外键引用 → 已修。
  两处均为测试基础设施问题，与被测系统无关。
- 硬删仅限 `@kubo.test` / `AUDIT*` / `auditd-*` 模式行（与 auditD 既有清场同式），
  未触碰任何业务数据行；复检前用 SQL 核对了探针账号残留为 0。

## 8. 遗留项与建议（下一迭代）

1. **库内导出 URL 拼令牌**（`@airpower/web` dist）：需框架侧支持 header 下载或
   一次性下载票据才能根除；当前为短时效会话令牌 + 同源场景，建议与框架升级一并处理。
2. **`?token=` 兼容通道退场计划**：MCP 客户端普遍支持自定义 header 后，可给
   `McpController` 的查询串通道加观测计数并下线。
3. **WS 握手线程池**：若框架后续版本修复 Jedis 阻塞订阅，可移除 §4 的线程池直委托
   （装饰器保留票据逻辑即可）。
4. **v-mask 本地规则**：`useMaskPolicy` 已接管 `personnel.user`；其余资源可按
   `maskContract.spec.ts` 的 WIRING 表逐视图扩展（新增即锁）。
5. **盘点模块缺失**（记录项 10）：纯业务功能缺口，非缺陷，按产品排期处理。
6. 部署检查清单仍适用 A–F 阶段版本（环境变量密钥等），本轮未新增环境变量。

## 9. 复现与回归入口

```bash
# 后端（勿与其他重负载并发，防 JVM native OOM）
cd SPMS-Server && MAVEN_OPTS="-Xmx1g" JAVA_HOME=<jdk17> ./mvnw test -o
# 前端（建议限制 worker 防 Node OOM）
NODE_OPTIONS="--max-old-space-size=6144" npx vitest run --pool=threads --maxWorkers=2
npx vue-tsc --noEmit && npx eslint . --max-warnings 0
# 黑盒探针（需后端 8080 运行；auditD 需在 SPMS-Server 目录下跑）
node SPMS-Server/target/cleanProbe.cjs   # 先清场上轮探针数据
node SPMS-Server/target/auditA.cjs && node SPMS-Server/target/auditB.cjs
node SPMS-Server/target/auditC.cjs && (cd SPMS-Server && node target/auditD.cjs)
node SPMS-Server/target/auditE.cjs && node SPMS-Server/target/auditF.cjs
node SPMS-Server/target/auditG.cjs       # WS 票据协议：见 §2.5
```

> 探针脚本位于 git-ignored 的 `SPMS-Server/target/`，重建环境后可依 §2.5/§3 的
> 协议说明重建；本轮所有**测试锁**（单测/契约测试）均已入库，探针仅作
> 部署后抽验手段。

# SPMS 测试报告 —— 阶段 H（后端安全修复专项 K-1~K-4）

- 日期：2026-09-16
- 范围：针对 G 阶段报告遗留项 + 新一轮侦察发现，按模块修复**后端**：
  K-1 提权链、K-2 通知出站（SSRF）、K-3 文件链、K-4 信息泄露/攻击面收敛。
- 约束执行：**全程未改动任何既有业务数据**（探针数据全部使用 `@kubo.test` 账号、`AUDIT*` 前缀，
  测后清理）；发现问题即修复并当场回归。
- 上承：`docs/TEST_REPORT_2026-09-G.md`。

## 1. 结论速览

| 验证层 | 结果 |
|---|---|
| 后端单元测试（`mvnw test -o` 全量） | **684/684 通过**（新增 WebhookUrlGuard 6 例、CSV CR 绕过 1 例；更新 Advice 列名不回显契约 1 例） |
| 黑盒探针 auditA–H（含新增 auditH 10 例） | **90/90 通过**（A22/B17/C12/D10/E6/F5/G8/**H10**） |
| 编译 | `test-compile` 零告警通过 |

## 2. 修复矩阵

### K-1 自我提权（高危，黑盒 H01 复现→修复→回归）

| 项 | 内容 |
|---|---|
| 缺陷 | `POST /user/updateMyInfo` 走框架「非 null 即覆盖」合并语义，普通用户提交 `{id:self, roleList:[{id:root}]}` 即可给自己挂 root 角色 |
| 修复 | `UserController.updateMyInfo` 强制 `setId(currentUserId)` 并显式清空 `roleList/departmentList/storageList/factoryList/phone/email/realName/idCard` |
| 兼容性 | 不能加 `@Null(groups=WhenUpdateMyInfo)`——前端 UserCard 提交整对象（含 roleList）会 400，破坏合法编辑；清空方案对前端透明 |
| 验证 | auditH H01：提权请求返回成功但角色未变（`roles=[]`），昵称等合法字段正常更新 |

### K-2 通知出站 SSRF / 凭据回显（高危）

| 项 | 内容 |
|---|---|
| 缺陷 | notify 的 url 运营可配且发送时无任何校验：`http://169.254.169.254/` 等内网/metadata 可达；EMAIL 收件人可携带 CRLF 注入邮件头；`token` 字段在查询接口回显；企微 AccessToken/短信验证码进 INFO 日志；AppSecret 非常量时间比较 |
| 修复 | 新增 `common/security/WebhookUrlGuard`（协议白名单 + 解析后逐地址拒绝 环回/链路本地/site-local/组播/全零/CGNAT 100.64/10/IPv6 ULA fc00::/7；EMAIL 单地址正则）；`NotifyService` 保存侧 `beforeAdd/beforeUpdate` + 发送侧 `doRequest` 双重校验，发送改用 `HttpClient.Redirect.NEVER`（禁重定向绕过）；`NotifyEntity.token` 加 `@JsonProperty(WRITE_ONLY)`；`WeComCallback` 令牌不落日志、code 做 URL 编码；`SmsHelper` 验证码日志降为 DEBUG 且手机号脱敏；`OauthController.accessToken` 改 `MessageDigest.isEqual` 常量时间比较 |
| 已知残余 | DNS rebinding（校验后重解析）与响应重定向之外的网关层问题，注释中声明；彻底闭环需固定解析直连，留待网关层 |
| 验证 | 单测 6 例（全部字面 IP，无 DNS 依赖）；auditH H02（metadata 拒绝）/H03（CRLF 收件人拒绝）/H04（update 侧同样拦截、原值保留） |

### K-3 文件链（中危）

| 项 | 内容 |
|---|---|
| 缺陷 | `file/getDetail`、`file/update` 无属主校验（任意登录用户可读/改他人文件元数据）；秒传按 md5 复用可命中 `creatorId=null` 的历史行；上传无频率限制；`CsvFormulaSanitizer` 只判首字符，值内 `\r` 借「Spring CSV 拼列不处理 \r」拆行绕过公式检查；multipart 上限 1MB（Boot 默认）先于应用层 10MB 口径生效 |
| 修复 | `FileController` 覆写 `beforeAppUpdate`/`afterGetDetail`（属主或 root，否则 FORBIDDEN_EDIT/查看拒绝）；上传加按 IP 限流（60 次/分，超限 429 文案）且 creatorId 直接取当前登录用户；秒传仅复用同属主记录；`sanitizeValue` 先把值内 `\r/\n` 替换为空格再判首字符（首字符 `\r` 由「前缀」改为「替换空格」，防止前缀版被拆行绕过）；`application.yml` 显式 `multipart 10MB/12MB` 与应用层对齐 |
| 验证 | CSV 单测含 `abc\r=HYPERLINK(..)` 绕过用例；auditH H05：非属主 getDetail/update 均 403 |

### K-4 信息泄露与攻击面收敛（中低危批量）

| # | 缺陷 | 修复 | 验证 |
|---|---|---|---|
| 1 | 匿名 error-report 的 source/context 未净化、无长度上限；message/stack 可携 CRLF 伪造日志行 | source/context 走 `sanitizeShort`（CRLF→空格 + 200 截断），`sanitize` 增加 CRLF 替换 | auditH H10 |
| 2 | MQTT 上报 `deviceId` 未校验直进日志（CRLF 注入） | `ReportMqCallback.safeIdForLog`：非 `^[A-Za-z0-9._-]{1,64}$` 一律替换占位符 | 编译+代码评审 |
| 3 | 约束错误 advice 回显 MySQL 列名（表结构枚举面） | 超长/非空改通用文案，列名只进服务端 warn 日志 | Advice 单测更新为「绝不包含列名」契约 |
| 4 | WebSocket `allowedOrigins` 框架默认 `*` | `WebConfig` 改 `setAllowedOriginPatterns`（支持逗号列表与 `http://localhost:*` 模式）；base profile 收紧为 localhost；production 用 `WS_ALLOWED_ORIGINS`（默认 `https://kubo.hamm.cn`） | auditH H09：evil Origin 403 / localhost 101 |
| 5 | coderule 三个字典端点匿名暴露表结构信息 | 改需登录（前端仅控制台登录后使用，实测匿名 401、登录 200） | auditH H06 |
| 6 | `OpenTestController` 匿名测试端点 | `@Profile("!production")`，生产不注册 | production profile 门控（同 D10 机制） |
| 7 | `getWebAuthnAssertionOptions` 硬编码 challenge 匿名空壳 | 删除端点（全仓无调用方） | auditH H07 |
| 8 | `wechat/init` 占位空壳 | 删除整个 `WechatController`（无引用） | auditH H08 |
| 9 | 生产 actuator（health/info/prometheus）与业务同端口匿名暴露 | production `management.server.port=9080`（见 §4 部署检查） | 配置评审 |

## 3. 回归

- auditA 22/22 · auditB 17/17 · auditC 12/12 · auditD 10/10 · auditE 6/6 · auditF 5/5 · auditG 8/8 · **auditH 10/10**（核心链路：登录/权限、单据状态机、库存守恒、菜单授权、WS 票据、通知/文件/提权）。
- 后端全量 `684/684`（含既有数据权限、Influx、票据吊销等回归，无失败无错误，1 skip 为既有）。
- 前端本轮无代码改动（上轮 302/302 + tsc/lint 0 结论仍有效）。

## 4. 部署检查清单（本轮新增）

1. **WS_ORIGIN**：生产站点域名非 `kubo.hamm.cn` 时必须注入 `WS_ALLOWED_ORIGINS`（逗号分隔多域）。
2. **MANAGEMENT_PORT**：actuator 已挪至 `9080`（默认）。k8s liveness/readiness 探针与 Prometheus 抓取需改指 9080；LB/ingress 不得转发 9080。
3. multipart 上限 10MB/12MB 生效后，>10MB 的旧上传习惯会收到明确超限错误（应用层 429/400 口径）。
4. 既有项不变：`AIRPOWER_API_ACCESS_TOKEN_SECRET`、`DB_USERNAME/DB_PASSWORD`、production profile 激活。

## 5. 记录项（本轮确认不修/需外部配合）

| 项 | 说明 | 去向 |
|---|---|---|
| datapermission shadow 模式 | 生产读侧行权限零拦截（仅 audit 记账），写侧闸门生效；观察窗口内 binding 指标归零后逐资源 enforce | 既有 §8 计划 |
| MQTT 上报无消息级认证 | 设备伪造 deviceId 可写参数缓存/时序；需 broker 侧 ACL/一机一密 | 网关/broker |
| 文件静态直链可预测名、无鉴权 | 存储/网关重构范畴 | 存储重构 |
| DNS rebinding 残余 | 需固定解析结果直连（网关层） | 网关层 |
| request-log 基础 profile 默认 true | production 已显式 false；若误部署未带 production profile 会回退 | 部署检查（profile 激活） |
| 框架 propertyReferenceExceptionHandle 回显属性名 / Idempotency-Key 文档失实 / isProtected 无消费点 / export fileCode 不校验属主 / getApiKey 可重复回显 | airpower 框架或需产品决策 | 上承 G 报告 §8 |

## 6. 复现入口

```bash
# 后端需已启动（dev profile，8080）
node SPMS-Server/target/cleanProbe.cjs     # 清残留探针账号/角色/Redis 登录锁
node SPMS-Server/target/auditH.cjs         # 本轮新增 10 例
# 全量回归
cd SPMS-Server && JAVA_HOME=<jdk17> ./mvnw test -o
```

auditH 数据足迹：用户 `k-h1@kubo.test`（脚本尾已软删，cleanProbe 兜底）、notify 一条（先删后清）、
文件 `auditH-probe.png` 一条（属主软删）。均不触既有业务数据。

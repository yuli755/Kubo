# 后端 B2d · 内部 JSON 文本退场（2026-09-21）

> **真实 Jackson/Lombok 子集已编译/执行；完整 Maven、Redis 与 HTTP 接线仍未验收。**
> 本批接着拆 B2 未完成的 JSON 责任，不把它与 HTTP 响应封装、鉴权、异常运行时混成一次替换。

**后续状态**：B4a 已继续收敛读侧分页，最新数量与边界见 [B4a 记录](后端B4a·读侧分页收口.md)。本页保留 B2d 的验证证据。

## 实际迁移与数字

| 指标 | B3a 后 | B2d 后 |
| --- | --- | --- |
| 耦合 Java 文件 | 161 / 462 | **160 / 466** |
| 符号 / 引用 | 44 / 544 | **44 / 540** |
| core / curd / api / redis / cookie | 226 / 263 / 41 / 13 / 1 | **222** / 263 / 41 / 13 / 1 |
| core.Json 引用 | 33 | **29** |

替换四个实际调用方：DataPermissionEventPublisher、DataPermissionEventListener、PermissionChangeOutboxRecorder、OperationLogSnapshot。
自持 `framework/json/JsonCodec` 直接调用 Jackson，不继承/调用旧 Json，也没有复制一套 JSON 解析器。
`jackson-databind` 在项目 POM 中显式声明，继续由 Spring Boot 3.4.4 BOM 管版本（2.18.3），没有升级现有 Jackson 版本。
POM 门禁只增加这一条已核实的坐标，没有放宽为整个 com.fasterxml 家族。

**明确保留**：控制器/HTTP 的 code、message、data、traceId 协议；用户菜单/权限缓存；Errors 异常运行时；
审计快照用于跳过旧父类的包名前缀（仍计 1 处引用）。Json 未整体清零，airpower-curd 依赖不能删。

## 兼容边界

- `ObjectMapper` 完成配置后经 static final 发布，不暴露可变 mapper，不引用 Spring 的 HTTP ObjectMapper。
- 保留原三项设置：忽略未知属性、空 Bean 输出空对象、Map 类型属性 NON_EMPTY。
  **真实 Jackson 验证**发现这不是“删除 Map 中所有空项”：Map 内 null/空串/空列表仍保留，只有空 Map 属性被省略。
- 未注册额外 JavaTime 模块、未启用默认多态类型；未知枚举仍拒绝，旧数字枚举/尾随内容策略仍保留。
  本批不是 JSON 严格化工程；权限条件 DSL 的 PermissionRuleParser 继续使用自己的严格配置，不得换成此兼容 codec。
- 事件 DTO 的六个字段、enum 名、Long 数值与默认时间戳契约保持；Redis 报文字节明确 UTF-8，不再由机器字符集决定。
- Outbox 仍先编码成功再保存，失败通过已有 SERVICE_ERROR 边界传播 code=500 与 JSON 错误文案；不会写入空 payload。
  Outbox 写入仍在 Redis 发布 try/catch 之外；广播失败继续由既有 WARN 边界处理。
- 审计仍先投影字段，失败仍返回 snapshot-error 占位；本批未改变字段选择、权限规则或触发懒加载的方式。

## 新的真实依赖验证通道

常规 curl/Maven 下载仍遭 TLS 中断，但本轮从公开依赖缓存取得了正式发布包，并与 Maven Central 官方 `.jar.sha1`
逐项核对一致后，再以 SHA-256 固定测试输入。未执行不匹配的包；最初找到的 90-byte LFS 占位文件并未当作 JAR 使用。

| 正式依赖 | 官方 SHA-1 |
| --- | --- |
| jackson-annotations 2.18.3 | `7fa21cf7da4598f8240e4ebd9779249622af1acd` |
| jackson-core 2.18.3 | `78f80c259268200e588aa204dd97ecf09b76916e` |
| jackson-databind 2.18.3 | `537e3886263e3b3464385040453e92567fd509e2` |
| lombok 1.18.36 | `5a30490a6e14977d97d9c73c924c1f1b5311ea95` |

官方核验地址分别为：

- `https://repo.maven.apache.org/maven2/com/fasterxml/jackson/core/<artifact>/2.18.3/<artifact>-2.18.3.jar.sha1`
- `https://repo.maven.apache.org/maven2/org/projectlombok/lombok/1.18.36/lombok-1.18.36.jar.sha1`

正式字节的临时传输来源是 GitHub 中的依赖缓存 blob（Jackson：`hailtonreis/backend`；Lombok：
`Nourroun02/Business-Process-Automation-PFE` 等相同 blob）。**信任依据是独立取得的官方校验和，不是第三方仓库名称。**
二进制只保存在沙箱缓存，未加入 Git，也未将第三方 GitHub 仓库配置成生产 Maven 镜像。

`infra/scripts/backend-json-artifacts.mjs` 记录 SHA-256；runner 复制刚核验的字节到临时目录后才执行，退出即清理。
默认读取 Maven 本地库，也可设置 `KUBO_JSON_JARS` 指向离线平铺目录；不自动联网安装、不缺包跳过、不造框架 stub。

## 验证结果

| 检查 | 已执行结果与范围 |
| --- | --- |
| `pnpm java:json` | **5 个真实生产源码编译、25 项 JSON 契约通过**，UTF-8/US-ASCII 各跑同一组；真实事件 DTO 经正式 Lombok 编译 |
| `pnpm java:unit` | 原 JDK-only **16 个源码 / 91 项契约通过**；全量 466 文件另过 AST（不是全量类型检查） |
| `pnpm check` | **56 文件 / 536 用例通过**，含依赖缺失/同名伪 JAR 的拒绝测试、POM 精确坐标门禁、Outbox/DSL 边界；构建与体积通过 |
| 后端棘轮 | **160 / 44 / 540**；四个已迁调用方不得重新 import 旧 Json，HTTP 存量不误报 |
| 新增应用边界测试 | **3 项未执行**：Outbox 编码失败映射、真实 Listener UTF-8 路由、null/未知事件不触发缓存 |
| 完整 Maven / Redis / HTTP | **未验收**；不把部分正式依赖可取得写成整仓构建通道已恢复 |

25 项 JSON 契约使用实际生产 JsonCodec、DataPermissionChangedEvent、DataPermissionAction、OperationLogSnapshot；
覆盖字节/字段形状、旧配置交叉读取、数值精度、异常原因、UTF-8、审计 fallback 与并发。
它不执行旧 Json 类，也不启动 Spring/Redis。相同用例由 JsonCodecContractTest 接入 Maven JUnit，避免两套断言漂移。

CI 在 Maven Compile 后增加正式依赖 JSON 契约步骤，原整仓 Compile/Test 硬门禁未删。
上一个已推送检查点 54d13b8 的 run 35522617966 因账户付款/额度没有执行步骤；它不是本轮代码的测试证据。

```bash
# 已有 Maven 本地依赖
JAVA_HOME=/path/to/jdk-17 pnpm java:json
# 或使用四个经过校验的正式 JAR 的离线目录
JAVA_HOME=/path/to/jdk-17 KUBO_JSON_JARS=/path/to/verified-jars pnpm java:json

pnpm airpower:backend -- --check
pnpm stack:project
# 仍必须补齐的整仓验收
cd SPMS-Server
./mvnw -B -s settings.xml compile
./mvnw -B -s settings.xml test
```

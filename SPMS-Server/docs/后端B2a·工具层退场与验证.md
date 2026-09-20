# 后端 B2a · 工具层退场与验证（2026-09-20）

> **状态：代码已替换、独立工具已验证，完整应用接线未验收。不是“后端已清干净”。**
> 写路径仍为 JPA，MyBatis-Plus 读侧开关不变，没有删除 CRUD/API/Redis 依赖或改变权限规则。

**后续复核说明（B2b）**：本批 `CodeRuleService` 曾漏掉 `java.util.List` 导入，旧 JDK import
检查因静态通配导入而跳过该文件，所以当时 `pnpm check` 未发现。本轮已修复代码与门禁，并补了全量
Java 17 AST 语法检查。这也证明上面“完整接线未验收”的边界不能省略。
最新足迹/结果见 [B2b 记录](后端B2b·CSV与反射退场.md)；下文保留 B2a 的当时数据，不作为当前数量。

## 1. 实测进度

| 指标 | 本轮前 | 本轮后 |
| --- | --- | --- |
| 含 AirPower 引用的 Java 文件 | 174 / 440 | **168 / 450** |
| 符号 | 60 | **49** |
| 引用（剥注释） | 603 | **568** |
| 模块 | core 285 / curd 263 / api 41 / redis 13 / cookie 1 | **core 250**，其余不变 |
| 依赖 | airpower-curd 6.4.3 | **仍在，未删除** |

共移除 36 处旧工具/接口引用，新增接线测试明确断言旧 `ServiceException` 协议（1 处），净减 **35**。
文件总数增加来自 8 个生产类、3 个测试文件，删除了 1 个空定时任务组件，不是扫描范围变化。

复现：`pnpm stack:project`、`pnpm airpower:backend -- --json`、`pnpm airpower:backend -- --check`。
棘轮收紧为 `{files:168, symbols:49, references:568}`，**通过只表示符合退场边界，不代表整体清零**。

## 2. 替换内容与兼容基线

`airpower-curd:6.4.3` 的父 POM 指向 **airpower-core:6.3.1**，不能拿 core 最新版做行为基准。
本轮对照了上游固定快照：

- [AirPower4J 6.4.3 POM（c07a6bd3f4d7）](https://github.com/AirPowerTeam/AirPower4J/blob/c07a6bd3f4d7/pom.xml)
- [AirPower-Core 6.3.1（fe1691b2c978）](https://github.com/AirPowerTeam/AirPower-Core/tree/fe1691b2c978)

上游源码仅用于核对契约，没有复制整套框架、JAR 或工具链到 Git。

| 原调用 | 当前实现 | 保留的契约 |
| --- | --- | --- |
| NumberUtil | `framework.util.DecimalMath` | 业务实际使用的双操作数 add/subtract/multiply；BigDecimal.valueOf，不另做舍入 |
| RandomUtil | `framework.util.RandomValues` | 默认 32 位大小写字母/数字、指定盐长、纯数字验证码、非正长度按 1 位；不改历史密码散列 |
| FileUtil | `framework.util.FilePaths` | 后缀、平台目录分隔符、yyyyMMdd 今日目录、创建目录与覆盖截断；IO 错误交给现有 FileService 边界 |
| DateTimeUtil | `Duration` / `LocalDateTime` | 验证码 300 秒、Cookie 86400 秒、房间缓存 30 天；编码规则沿用 JVM 时区 |
| StringUtil | Spring `StringUtils` | null / 空串 / 空白判定 |
| DictionaryUtil | `framework.util.EnumValues` + `common.dictionary.EnumDictionaries` | 枚举声明顺序、显式 key/label/defaultPrefix/desc/demo 字段；未知值仍是 code 500 + 原提示 + 选项数据 |
| TreeUtil / ITree | `framework.util.Trees` / `TreeNode` + `common.tree.TreeDeletionGuard` | 空父 ID 视作 0、输入顺序、原节点引用、只读 children、孤儿不提升、后代集合可追加起点；删除错误协议不变 |
| TraceUtil | 删除空 `ServiceCron` | 类中仅有注释掉的调度方法，无运行行为 |

`framework/util` 只用 JDK；应用错误转换留在 `common`，仍借用现有 Errors 异常运行时，**没有宣称它也已退场**。
四个实体不再实现旧 ITree；PermissionEntity 仍实现旧 IPermission 供权限扫描使用，另外实现新 TreeNode。
`@Dictionary` 与枚举上的旧 IDictionary 仍在，因为注解校验的泛型和运行行为尚未迁移。

### 本批明确的行为改进（不是逐字节等价）

- 随机值使用 **SecureRandom**，不再用可预测的 ThreadLocalRandom 生成认证材料；长度与字符集不变。
- 树处理改为迭代；20,000 层树不会递归溢出；后代查询成环或重复 ID 时每个 ID 最多查询一次。
  组树遇到重复 ID、空 ID 或占用根 ID 0 时拒绝构建；孤儿/断开的环不会被提升成新根。
- 文件后缀使用 Locale.ROOT，避免土耳其语环境把 GIF 的 I 转换成另一字符。
  没有趁本批重新定义无点文件名的后缀规则；白名单校验仍由上传入口负责。
- 编码规则年/月/日/小时来自同一 LocalDateTime 快照，避免跨午夜读到混合日期。

## 3. 验证结果与边界

| 验证 | 本轮结果 | 证明范围 |
| --- | --- | --- |
| `JAVA_HOME=<JDK17> pnpm java:unit` | **通过：6 个生产类真实编译、32 项契约** | 无第三方 classpath，`javac --release 17 -Xlint:all -Werror`；数字、随机形状/并发、文件读写/失败、枚举选项、树契约 |
| `pnpm check` | **通过：52 文件 / 475 项 Vitest 用例**，含 16 项新门禁测试 | lint、类型、覆盖率、前后端静态契约、构建、产物；不是 Maven 测试 |
| `pnpm airpower:backend -- --check` | **通过：168 / 49 / 568** | 总量棘轮 + 已退场工具零回潮 + 自持 framework 零引用 |
| `./mvnw -B -s settings.xml -DskipTests compile` | **未进入编译** | Wrapper 下载 Maven 时 TLS 握手被远端中断，不能据此判断应用编译结果 |
| GitHub CI 基线任务 | **未启动构建步骤** | 账户付款/额度限制；并非代码测试失败，也不算通过 |
| 新增 `UtilityWiringTest` 的 8 项接线测试 / 整仓 Maven test | **未执行，待验收** | 真实实体接口、选项接口 JSON 数据、异常 code/message/data、Cookie/上传默认值 |

基线 CI 证据：[run 35515416860](https://github.com/yuli755/Kubo/actions/runs/35515416860)。
任务没有 steps，Server annotation 明确报告 account payments / spending limit；没有降低 CI 门禁或跳过测试来制造绿灯。

独立测试使用 `UtilityContracts`，Maven 下由 `UtilityContractTest` 转成同一组 JUnit DynamicTest，**不维护两套不同断言**。
测试 runner 缺 JDK 17、编译失败、运行失败或零用例均非零退出，不生成占位依赖，也不自动联网装工具链。
独立 runner 已加入 server CI 的 Maven 编译之前；web-only `pnpm check` 不强制安装 JDK。

## 4. 防回潮与下一批

本批 8 个旧工具和 ITree 的源码/FQCN/静态导入不得回潮，core/接口通配导入也不能绕过。
即使总引用低于 568，重新引入其中任何一个也会失败；新 `framework` 不能包装旧 Json 伪装成独立底座。
历史注释仍不计入引用，剩余框架契约仍如实计数。

下一步先恢复 Maven 下载或 CI 额度，然后执行：

```bash
pnpm java:unit
cd SPMS-Server
./mvnw -B -s settings.xml compile
./mvnw -B -s settings.xml test
```

完整接线通过后再推进 B2 剩余部分：Json、异常运行时、CollectionUtil/CSV、ReflectUtil、TaskUtil、AccessTokenUtil。
其中 CSV 必须连同导出脱敏/公式注入保护一起验，Json/异常必须守住 code/message/data 协议；
CRUD、API 注解、查询模型、Redis 和旧配置仍需后续批次，不能先删 Maven 依赖或改成兼容壳掩盖耦合。

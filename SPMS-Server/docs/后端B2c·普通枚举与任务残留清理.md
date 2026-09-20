# 后端 B2c · 普通枚举与任务残留清理（2026-09-20）

> **代码已清理、独立范围已验证，完整 Maven 应用编译/接线仍未验收。**
> 不改 Java 17、JWT 签名/验证逻辑、持久化键值、权限规则、数据库迁移或 HTTP 错误协议。

**后续状态**：B3a 已继续处理标准电话约束、通配与无用标记，最新数据见 [B3a 记录](后端B3a·电话约束与通配清理.md)。本页保留阶段结果。

## 实际减少的耦合

| 指标 | B2b 后 | B2c 后 |
| --- | --- | --- |
| 含 AirPower 引用的 Java 文件 | 168 / 457 | **162 / 459** |
| 符号 / 引用 | 47 / 556 | **46 / 546** |
| core / curd / api / redis / cookie | 238 / 263 / 41 / 13 / 1 | **228** / 263 / 41 / 13 / 1 |
| 独立 Java 17 真实编译范围 | 9 个工具源码 | **15 个生产源码**（9 工具 + 5 业务枚举 + AppConstant） |

净减 10 引用：5 个枚举的 IDictionary 导入、2 处 TaskUtil 导入、3 处确实无用的导入
（SpringDocResponseAdvice 的 Json、PermissionEntity 的 Dictionary/Meta）。没有挪包或排除扫描。
新增的两个 Java 文件都是测试，没有新增生产兼容壳。20 个旧注解校验枚举仍保留接口。

## 普通枚举不再绑框架

迁移对象：UserTokenType、CodeRuleParam、FileCategory、FileUploadPlatform、PermissionType。

- 没有另造一个同形接口，也没有继承旧接口。它们现在就是普通 enum，构造器/getter 明写。
- 声明顺序、数字 key、label、desc/demo、TYPE="type"、扩展名白名单、受保护文件标记不变。
  FileCategory 的 key 仍是 0/1/1001/2001，不用 ordinal 冒充；API 权限类型仍为 0。
- 对照了固定 core 6.3.1 的 IDictionary，以及 curd/api 6.4.3 源码：这五个类型既不在
  旧 Dictionary 校验约束里，也不承担单据状态接口；调用方已使用 B2a 的 EnumValues/EnumDictionaries。
- ConfigType、CodeRuleField、SerialNumberUpdate 等被旧 Dictionary 绑定的枚举没有删接口。
  ConfigFlag 返回旧 ConfigType，本批也未将它宣称为独立业务类型。

`infra/scripts/backend-independent-sources.mjs` 是独立业务源码清单：真实 javac 和零回潮门禁共用，
重新引入旧接口即使总量低于 546 也会失败。还补了“已迁枚举不得重新用作旧 Dictionary 约束”的静态检查。

## 任务调用只替换项目实际用到的部分

- `afterAppAdd`：旧 TaskUtil.run 实际为**同步执行**，仅捕获最后一个钩子的 Exception 并记日志。
  改为本地 try/catch；不创建线程，不捕获 Error，不把前面的 get/saveDetails/audit 包进 catch。
  日志文案/异常仍写 ERROR，日志类目由旧 TaskUtil 变为业务基类。
- 手工 `ApplicationTest`：JDK 固定执行器 + invokeAll 的总 20 秒上限 + Future.get + finally 关闭，
  不再固定 sleep 20 秒后忽略任务结果。保留原有 Disabled，仍需人工启用及本地 Redis，未冒充 CI 实测。
- **只清零直接用法**。CurdService/ExportHelper 在 jar 内仍调用旧任务工具，不能据此删除 core/curd 依赖。

## 验证记录

| 验证 | 本轮结果 |
| --- | --- |
| `JAVA_HOME=<JDK17> pnpm java:unit` | **15 个生产源码真实编译、79 项独立契约通过**；UTF-8 与 US-ASCII 默认字符集各跑同一组 |
| 同命令的 javac AST | **459 个 Java 文件、8 项自检通过**；只证明语法及少量已知注解规则，不是完整类型检查 |
| `pnpm check` | **54 文件 / 508 用例通过**，含 26 项 AirPower 门禁与 8 项枚举/钩子边界静态检查；构建、体积通过 |
| `pnpm airpower:backend -- --check` | **162 / 46 / 546**，已退场工具及独立业务类型零回潮 |
| Maven compile 重试 | **未进入项目编译**：Wrapper 下载 Maven 时 SSLHandshakeException / EOFException |
| 本批 6 项新增 Maven 接线测试 | **未执行**：5 项真实 afterAppAdd 同步/异常边界 + 1 项普通枚举接原错误边界 |

新增 15 项独立 Java 契约直接引用真实业务枚举，验证数值、标签、getter 签名、白名单、查找/投影与非 ordinal 规则；
没有旧框架占位类或注解处理器 stub。B2a/B2b 的 29 项新增接线测试也仍待完整 Maven 验收。

当前仍有 546 引用与 airpower-curd 6.4.3。下一阶段的 Json/异常、元数据、查询模型、CRUD/API、Redis
及旧配置清理仍需要完整依赖环境；这三批局部验证不能替代整仓编译和关键接口回归。

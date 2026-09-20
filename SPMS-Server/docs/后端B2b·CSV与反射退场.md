# 后端 B2b · Mapper CSV 与反射工具退场（2026-09-20）

> **代码已替换，独立工具已验证；完整应用仍未通过 Maven 类型检查/接线验收。**
> 不改 Java 17、JPA 写路径、Mapper 读侧开关、权限规则或 `fileCode → queryExport` 下载协议。

**后续状态**：B2c 已继续清理普通枚举/任务残留，最新数据见 [B2c 记录](后端B2c·普通枚举与任务残留清理.md)。本页保留 B2b 阶段的验证结果。

## 实测进度

| 项 | B2a 后 | B2b 后 |
| --- | --- | --- |
| 触及 AirPower 的 Java 文件 | 168 / 450 | **168 / 457** |
| 符号 | 49 | **47** |
| 引用 | 568 | **556** |
| core / curd / api / redis / cookie | 250 / 263 / 41 / 13 / 1 | **238** / 263 / 41 / 13 / 1 |
| Maven 依赖 | airpower-curd 6.4.3 | **仍在** |

- 去掉 `CollectionUtil` 的 14 处导入、`ReflectUtil` 的 3 处导入。
- 新增元数据边界明确保留 3 处旧注解导入，已有接线测试增加 2 处注解导入，**都照常计数**。
  因此净减 **12**，不是声称减了 17。文件数持平：CodeRuleService 清零，新增了一个显式耦合的元数据适配点。
- 棘轮下调为 `{files:168, symbols:47, references:556}`；两个旧工具都纳入零回潮检查。
- 新增 5 个生产类与 2 个测试文件，没有改变扫描口径、把业务源码移出扫描目录或排除测试。

## 实际替换

1. **CSV 编码器自持**：`framework/util/CsvWriter`、`CsvText` 只依赖 JDK。
   `common/export/EntityCsvExport` 将已遮蔽的实体交给编码器，不负责查询、授权或扩大字段范围。
2. **旧元数据没有假装消失**：`common/metadata/LegacyEntityMetadata` 读取 Description/Export/Dictionary，
   不再调用旧反射、字典或 CSV 实现。B3 迁注解时再换这处；当前明确保留旧注解耦合。
3. **反射实现自持**：`BeanFields` 用 ClassValue 缓存字段（不按类名混用不同类加载器），
   读取后不反复关闭 Field 的访问标志，避免并发导出争抢；自动编码写入失败不再静默吞掉。
4. **十四个 Mapper 出口统一替换**：material、contract、customer、supplier、purchase、sale、
   purchasePrice、salePrice、storage、structure、input、output、move、inventory。
   仍然先 `selectForExport`，再在请求线程 `maskForExport`，最后创建异步任务做 CSV 编码/落盘。

**边界必须保留：JPA 回退仍调用旧 CurdService 内部的 CollectionUtil，ExportHelper 也仍在。**
源码中两个工具的直接引用清零，不等于 jar 内调用已经消失，不能据此删掉 airpower-curd。

## 兼容与有意变化

兼容基准仍是 B2a 核对过的 **core 6.3.1**：

- 只导出选中列；要有 public getter；getter 优先决定移除/排序，字段注解决定格式。
  getter-only 列按旧实现 RAW 输出，不顺手改变这项行为。sort 实际为**降序**且同序稳定。
- TEXT 的制表符、中文布尔值、缺值横杠、字典标签/未知值回退、毫秒时间戳和 JVM 时区不变。
- 不给空配置猜列。当前 purchase/sale 没有 Export 列，仍是空表；完善业务报表需要单独定义列范围。
- **有意改善**：CSV 显式 UTF-8，不再依赖机器字符集；保留逗号/双引号/CR/LF 并正确引用，
  不再用空格替换原内容。没有增加 BOM 或末尾空行。
- 公式保护收敛为同一 `CsvText.protectFormula`，JPA 实体预清洗与新编码器共用。
  覆盖 LF、空白/BOM 后的公式符号、字典标签和格式回退，已加单引号的内容保持幂等。
  原生数值负数仍是数字，字符串不能因列标了 NUMBER 就绕过防护。
- 真正的读取/元数据回调错误向上传播，不把失败伪装成一份成功的空值 CSV。原始实体不被编码器修改。

## 本轮纠正的验证漏洞与残留

- B2a 的 `CodeRuleService` 漏了 `java.util.List` import。旧门禁因 `import static CodeRuleParam.*`
  跳过整个文件，导致上轮 pnpm check 没拦住。已补导入，并验证“修门禁后先红、补代码后再绿”；
  同时纠正了扫描表把 `java.lang.Iterable` 错列为 java.util 类型的问题。
- 新的 **javac AST** 门禁查出了 `InputEntity.move` 前残留两份不可重复注解，已去掉已删字段留下的那一组。
- `OutputEntity` 仍声明不存在的 PickingEntity（仓内无类、无调用方）。改成 `@JsonIgnore`、
  `insertable=false/updatable=false` 的只读 `legacyPickingId` 映射，保留历史 picking_id 列。
  曾直接移除字段，但 schemaContract 正确拦住了 schema 漂移；最终没有放宽该门禁，也没有改 V1 或删历史数据。

## 验证结果（严格区分层级）

| 验证 | 结果 | 不能据此声称 |
| --- | --- | --- |
| `JAVA_HOME=<JDK17> pnpm java:unit` | **9 个生产工具类真实编译，64 项独立契约通过**；UTF-8/US-ASCII 默认字符集各跑同一组 | 业务类和第三方接口已全部类型检查 |
| 同命令内 javac AST | **457 个 Java 源文件通过**，8 项门禁自检 | Maven 编译通过；AST 只解析语法，另核对少数已知非重复注解 |
| `pnpm check` | **53 文件 / 492 项 Vitest 用例通过**，含 18 项零回潮测试与 15 项导出接线静态契约；构建/体积通过 | 后端集成测试已执行 |
| `pnpm text:check` | **13 项门禁自检通过**，缺少 JDK import / 字面量 / 编码检查通过 | 所有 Java 符号都能解析 |
| `./mvnw -B -s settings.xml -DskipTests compile` | **未进入编译**：下载 Maven 时 SSLHandshakeException / EOFException | 有任何完整后端编译结论 |
| 本批新增 21 项 Maven 接线测试 | **未执行**（14 个实体表头 + 4 项实体/格式/只读列 + 3 项元数据/脱敏联动） | 已验证实际注解、Lombok、Spring、JPA、异步任务的整条运行链 |

完整 Maven 仍是待验收项；不能继续扩大到 Json/异常/鉴权主干后，把这些局部绿灯当作整仓绿灯。
上一轮基线 CI 曾因账户付款/额度问题未启动（run 35515416860），本轮没有合并、发布或放松门禁。

复现：

```bash
pnpm airpower:backend -- --check
JAVA_HOME=/path/to/jdk-17 pnpm java:unit
pnpm check
cd SPMS-Server
./mvnw -B -s settings.xml compile
./mvnw -B -s settings.xml test
```

工具源代码在 `tools/java/JavaSourceCheck.java`，没有 Maven/JDK/JAR 二进制进入 Git。
后续仍需 Json、异常运行时、TaskUtil、AccessTokenUtil、元数据、CRUD、API、Redis 与旧配置的迁移。

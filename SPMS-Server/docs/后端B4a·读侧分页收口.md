# 后端 B4a · 读侧分页收口（2026-09-21）

> **纯 Java 分页计算已实际编译/验证；旧 DTO 与完整应用接线仍待 Maven 验收。**
> 不删除请求/响应 DTO，不改 Mapper SQL、排序能力、权限规则、JPA 回退、写入事务或数据库迁移。

**后续状态**：B7a 已进一步排除 Cookie 传递模块；最新数量与验证边界见 [B7a 记录](后端B7a·Cookie传递模块退场.md)。本页保留阶段证据。

## 本轮实际改动

14 个 Mapper 读服务原本各自重复默认值、条数钳制、偏移校验、总页数和响应组装。
现在分成两层，不创建一个继承旧 Page 的“新 Page”来假装退场：

- `framework/query/ReadPage`：JDK-only 不可变值，真正持有默认值、边界校验、offset 和 counts 算法。
- **现有 `BaseService` 边界**：`mapperReadPage` 读取旧 Page；`mapperPageResponse` 把计算结果接回旧 QueryPageResponse/Page/Sort。
  明确保留这些依赖，不把它们藏进声称独立的 framework。
- 14 个业务服务只负责业务筛选、影子采样、权限 Mapper 查询、关系补齐；不再直接 import Page/Sort。
  新增逐文件零回潮检查，不能靠删除别处引用抵消重新引入这两个 DTO。

覆盖 material、contract、customer、supplier、purchase、sale、purchasePrice、salePrice、storage、structure、input、output、move、inventory。
对应服务文件合计由 3505 行变为 3174 行，净删 **331 行**；这不是全补丁净行数，新增共享代码与测试另计。

## 保留的契约

- 默认页码/条数 1/20，条数先钳制到 200，再计算偏移；不会因大 pageSize 先发生整数溢出。
- 页码/条数非正仍为 `IllegalArgumentException("分页页码和分页大小必须为正数")`；
  偏移超 int 上限仍为 `IllegalArgumentException("分页页码超出可查询范围")`。
- `total`/`pageCount` 仍为 int；先 `Math.toIntExact(total)` 再计算页数，大总数报错，不截断也不悄悄扩成新协议。
  空集页数为 0；COUNT 正常不会负数，本批未另行改变异常 DAO 返回值的历史数值规则。
- 行列表与显式 Sort 引用不复制；响应 Page 新建，不改写请求 Page。缺少 Sort 时每次新建旧 Sort。
- 响应仍含 list/total/pageCount/page/ sort 及原字段名；**没有把 ReadPage 本身序列化成 API 响应**。
- JPA 回退先于本次规范化逻辑；没有把 Mapper 的 200 上限强加到旧 JPA 路径。
- 排序仍由原 Mapper SQL 决定，响应只是沿用原排序信息；本批不顺手“修复”或扩展排序功能。

对照了上游固定 `c07a6bd3f4d7` 的 Page/Sort/QueryPageResponse/PageData：Page 初始 pageNum=1、pageSize=null，
Sort 的 field/direction 初始为 null。改动前后逐项比对 14 个方法的 select/count/shadow/hydration 表达式，
仅将原 offset/pageSize 局部变量替换为同值的 ReadPage 结果，查询与筛选没有改写。

## 足迹

| 指标 | B2d 后 | B4a 后 |
| --- | --- | --- |
| 耦合 Java 文件 | 160 / 466 | **160 / 468** |
| 符号 / 引用 | 44 / 540 | **44 / 518** |
| core / curd / api / redis / cookie | 222 / 263 / 41 / 13 / 1 | 222 / **241** / 41 / 13 / 1 |

14 服务删除 Page/Sort 共 28 个导入；BaseService 明确增加 3 个 DTO 导入，已有接线测试增加 3 处，净减 **22**。
文件数和符号数没有假报减少：旧 Page/Sort、QueryPageRequest/Response、CurdService、airpower-curd 仍然存在。
这是读侧逻辑去重与依赖收口，不是 B4 查询模型整体退场。

## 验证

| 检查 | 结果与边界 |
| --- | --- |
| `JAVA_HOME=<JDK17> pnpm java:unit` | **17 个生产源码真实编译、107 项契约通过**；新增 16 项分页契约，含 5000 组固定种子差分；UTF-8/US-ASCII 各跑同一组 |
| 全后端 javac AST | **468 文件 / 8 项自检通过**，不是完整类型检查 |
| `pnpm java:json` | 已恢复并核验正式 Jackson/Lombok 依赖缓存，原 **25 项协议契约通过** |
| `pnpm check` | **57 文件 / 566 用例通过**，包含 14 服务的回退/筛选/查询链路静态契约；构建与体积通过 |
| 后端棘轮 | **160 / 44 / 518**；已迁服务不得直接重引 Page/Sort |
| 新增 5 项 Maven 接线测试 | **未执行**：真实 Page 默认值、请求不变性、列表/排序引用、JSON 响应字段、溢出边界 |
| 整仓 Maven compile 重试 | **未进入项目编译**：Wrapper 下载 Maven 仍发生 TLS 握手中断 |

本次没有伪造旧 DTO 或依赖来凑编译。纯算法测试、静态调用链契约、Maven 的真实 DTO/应用验证是三层不同证据。
没有修改 JPA、Redis、鉴权或事务主干，也未反复触发已知受额度阻塞的 CI；最后的完整 CI 阻塞证据仍见 B3a 记录。
临时工具链/依赖缓存重新取得后均已核验，未把 JDK/JAR/上游源码归档加入 Git。

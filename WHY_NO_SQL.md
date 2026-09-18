# 为什么（曾经）没有 SQL 文件？—— 以及现在的口径

> 本文档曾在早期声称「Kubo 使用 JPA/Hibernate 自动建表，不需要手写 SQL」。那个口径在
> 2026-09 的数据权限二次改造与 2026-09-18 的「去 JPA + 数据库收敛」之后**整体失效**，
> 这里给出当前准确口径。**看这一页就够了，不用再去翻历史版本。**

## 历史（截至 2026-08）

- 建表与常规 DDL 完全由 **Spring Boot JPA + Hibernate** 从 `@Entity` 自动生成，不维护手写建表脚本。
- 因此仓库里一度没有任何 `.sql` 文件，改表的动作是"改实体 + 重启"。

## 现状（2026-09-18 起）：两个 SQL 文件，一个 ORM

**全仓只有两个数据库脚本**（`pnpm migration:check` 守着"目录里恰好两个"）：

| 文件 | 管什么 | 内容 |
| --- | --- | --- |
| [`infra/init-sql/init.sql`](./infra/init-sql/init.sql) | **结构** | 49 张表（幂等 `CREATE TABLE IF NOT EXISTS`，含索引与唯一键） |
| [`infra/init-sql/init_data.sql`](./infra/init-sql/init_data.sql) | **数据** | 默认租户、系统权限身份、角色绑定、归属列回填 |

- **建表不再由 ORM 负责**：`spring-boot-starter-data-jpa` 与 `spring.jpa`（含 `ddl-auto`）已于 2026-09-18
  整体退场（J4，见 `SPMS-Server/docs/去JPA决策评估.md` §9.8.7）。应用**不建表、不改表**。
- **实体映射**改由 MyBatis-Plus 回答：表名 `@TableName`、列 `@TableField`、主键 `@TableId(type = IdType.AUTO)`、
  唯一列 `@Unique`（列与库表的双向对账见 `pnpm ddl:check`、`schemaContract.spec.ts`）。
- **原 Flyway V1–V10 与 `SPMS-Server/docs/database/migration/*.sql` 已折叠进这两个文件**
  （2026-09-18 数据库收敛）：以后任何库改动都写进 `init.sql` / `init_data.sql`，**不再新增迁移文件**。

## 那"改表结构"现在怎么做

1. 改实体（列的事实源是实体类）；
2. 改 `infra/init-sql/init.sql` 里对应的 `CREATE TABLE`（新列 / 新索引 / 新唯一键一并写上）；
3. 存量库要跟进的数据回填写进 `init_data.sql`（幂等 `INSERT ... WHERE NOT EXISTS` / `UPDATE ... WHERE 列 IS NULL`）；
4. 跑 `pnpm check`；推送后看 CI 的 `migrations` job（真实 MySQL 8 空库连跑两遍比对 `information_schema`）。

## 谁在守这件事（机器判据）

| 判据 | 命令 | 拦什么 |
| --- | --- | --- |
| 目录里恰好两个 `.sql` + 幂等写法 + 禁 `DROP/DELETE/TRUNCATE` | `pnpm migration:check` | 新增迁移文件、漏文件、非幂等语句 |
| 结构 ↔ 实体列漂移 | `pnpm ddl:check` | 实体加了列没改 `init.sql`（运行期 500），脚本有列没人映射 |
| 实体 ↔ 脚本双向一致（含唯一键） | `npx vitest run src/config/__tests__/schemaContract.spec.ts` | 列名集合必须相等；`@Unique` 必须有库面唯一键 |
| JPA 不许回来 | `pnpm jpa:check -- --check` | pom 里塞回 starter、yml 里写回 `spring.jpa`/`ddl-auto`、实体上写回 JPA 注解 |

## 相关文档

- 数据库脚本策略（为什么是两文件而不是版本化迁移）：`SPMS-Server/docs/DATABASE_MIGRATION_STRATEGY.md`
- 表结构总览（自动生成 + 人工维护的列表）：`DATABASE.md`
- 多租户归属列矩阵：`SPMS-Server/docs/database/tenant_readiness_matrix.md`

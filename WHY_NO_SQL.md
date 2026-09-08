# 为什么（曾经）没有 SQL 文件？—— 以及现在的口径

> 本文档曾在早期声称「Kubo 使用 JPA/Hibernate 自动建表，不需要手写 SQL」。
> 该口径在**数据权限二次改造**（2026-09 起）之后已部分失效，这里给出当前准确口径。

## 历史（截至 2026-08）

- 建表与常规 DDL 完全由 **Spring Boot JPA + Hibernate** 从 `@Entity` 自动生成，不维护手写建表脚本。
- 因此仓库里一度没有任何 `.sql` 文件。

## 现状（2026-09 起，数据权限改造引入手写迁移）

数据权限改造引入了**手写、幂等的增量迁移脚本**，目录在
`SPMS-Server/docs/database/migration/`（如 `20260906_multitenant_foundation.sql`、
`20260909_factory_owner_columns.sql` 等）。注意区分两种 SQL：

| 类型 | 谁生成 | 例子 |
| --- | --- | --- |
| **建表 / 实体结构** | 仍由 JPA `@Entity` 定义，生产环境 `ddl-auto=validate` 只校验不建表 | `user`、`permission_change_outbox` |
| **增量迁移 / 回填 / 约束降级** | 手写幂等 SQL（每个文件可重复执行） | 补 `tenant_id`/`creator_id` 列、`user_factory` 建表、`owner_columns` 回填 |

> 为什么建表不手写、迁移却手写：建表的唯一真值是实体类（手写会与代码漂移），
> 而「给既存表补列 / 回填存量数据 / 把唯一约束降级为 (tenant_id, col) 复合唯一」这类
> **一次性数据手术**必须手写且幂等，否则升级即事故。

## 迁移怎么被守住

- CI 的 **Migrations (MySQL 8 dialect / idempotency)** job 会对每份迁移脚本跑两遍，
  断言两遍结果一字不差（幂等）。
- 本地可用 `node infra/scripts/check-ddl-entity-drift.mjs` 核对「DDL ↔ 实体」是否漂移。

## 相关文档

- 迁移策略与约定：`SPMS-Server/docs/DATABASE_MIGRATION_STRATEGY.md`
- 迁移脚本目录：`SPMS-Server/docs/database/migration/`
- 多租户归属列矩阵：`SPMS-Server/docs/database/tenant_readiness_matrix.md`

# 数据库版本迁移脚本与整合总览（2026-09-25）

本系统的数据库迁移完全由 **Flyway** 纳管，标准位置为：

    SPMS-Server/src/main/resources/db/migration/

| 版本 | 文件名 | 说明 |
| :--- | :--- | :--- |
| **V1** | `V1__baseline_schema.sql` | 41 个 JPA 实体的基准 DDL 建表脚本 |
| **V2** | `V2__data_permission_migrations.sql` | 数据权限体系（策略/资源/拦截器）表结构与初始化数据 |
| **V3** | `V3__operation_log.sql` | 操作审计日志表结构 |
| **V4** | `V4__role_tenant_backfill.sql` | 角色租户归属数据回填 |
| **V5** | `V5__file_creator_id.sql` | 文件创建人标识字段补充 |
| **V6** | `V6__menu_permission.sql` | 菜单与权限关联校验补全 |
| **V7** | `V7__system_permission_identities.sql` | 系统内置权限标识标准化 |
| **V8** | `V8__user_email_phone_unique_null_default.sql` | 用户邮箱手机号唯一约束与空值兼容 |
| **V9** | `V9__inventory_dimension_unique.sql` | 库存多维唯一约束强化 |
| **V10** | `V10__menu_canonical_paths.sql` | 规范化菜单路由与层级对齐 |

---

## 生产部署执行方式

为便于不同运维场景，提供两条接入路径：

### 路径 A：Spring Boot 应用自启动自动迁移（推荐）
在 `application.yml` 中开启 Flyway：
- 全新数据库：启动时自动按顺序执行 V1 ~ V10；
- 存量数据库：基于 `baseline-on-migrate: true` 标记基线后按需执行新增增量脚本。

### 路径 B：DBA 手工一键建库与批量执行（整合脚本）
已将 Flyway V1~V10 全量脚本整合为单文件：

    SPMS-Server/docs/database/migration/20260925_flyway_v1_to_v10_consolidated.sql

执行命令：
```bash
mysql -u root -p kubo < SPMS-Server/docs/database/migration/20260925_flyway_v1_to_v10_consolidated.sql
```
该脚本完全保持幂等（`IF NOT EXISTS`、存储过程探测与幂等 `INSERT`），可安全重复执行。
后续版本演进请在 `SPMS-Server/src/main/resources/db/migration/` 按照 `V11__xxx.sql` 递增。

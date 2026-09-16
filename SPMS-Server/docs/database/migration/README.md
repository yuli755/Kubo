# 迁移脚本已收编到 Flyway（2026-09-17）

本目录下的 9 个手工 SQL **已迁移**到：

    SPMS-Server/src/main/resources/db/migration/

| 原文件 | 现位置 |
| --- | --- |
| 20260909_data_permission_migrations_consolidated.sql | `V2__data_permission_migrations.sql` |
| 20260910_operation_log.sql | `V3__operation_log.sql` |
| 20260910_role_tenant_backfill.sql | `V4__role_tenant_backfill.sql` |
| 20260914_file_creator_id.sql | `V5__file_creator_id.sql` |
| 20260914_menu_permission.sql | `V6__menu_permission.sql` |
| 20260914_system_permission_identities.sql | `V7__system_permission_identities.sql` |
| 20260915_user_email_phone_unique_null_default.sql | `V8__user_email_phone_unique_null_default.sql` |
| 20260916_inventory_dimension_unique.sql | `V9__inventory_dimension_unique.sql` |
| 20260917_menu_canonical_paths.sql | `V10__menu_canonical_paths.sql` |

**不要再往本目录加脚本**：新增变更写成 `db/migration/V11__xxx.sql`（版本号递增），
由 Flyway 记账执行。详见 `docs/database/Flyway-S0·基线与存量脚本收编.md`。

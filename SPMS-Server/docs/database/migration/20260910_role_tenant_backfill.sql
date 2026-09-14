-- ============================================================================
-- [T1] personnel.role  ← RoleEntity.java
-- B5 换轨前置（2026-09-10 拍板：role 纳入行级过滤）。
--
-- 背景：20260909 那份 consolidated 回填 40+ 表的 tenant_id/creator_id 时，按「角色
-- 不按归属过滤、全量管理面」的既有意图**跳过了 role**；现拍板 role 也纳入行级，故补上
-- role 的租户列回填 + 唯一约束改造（name / code 两个唯一列都改为 (tenant_id, 列)）。
--
-- 依赖：20260909 脚本先执行（kubo_mt_add_column / kubo_mt_add_index /
--       kubo_mt_replace_unique 三个存储过程在其中定义）。三个过程均幂等（information_schema
--       先判存在性），本脚本可重复执行。
-- ============================================================================
CALL kubo_mt_add_column('role', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('role', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('role', 'idx_role_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('role', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('role', 'name');  -- 原唯一列 name → (tenant_id, name)
UPDATE `role` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `role` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

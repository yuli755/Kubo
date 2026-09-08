-- ============================================================================
-- B1 余半块：归属迁移特权权限的落库
-- ============================================================================
-- `system:data-permission:ownership` 是「默认归属不可变」的唯一豁免：持有它的角色
-- 可调 POST /dataResource/migrate-ownership 迁移一条带归属数据的 tenant_id/creator_id，
-- 且每次迁移都落 data_permission_deny_log（reason=ownership-migrated）审计。
--
-- 为什么这里要显式补授权，而不是只 INSERT 权限本身：
--   20260817_data_permission_model.sql 里那条「SYSTEM_ROOT 全量授权所有 is_system 权限」的
--   INSERT 只在它自己执行的那一刻生效；对本脚本之后新增的权限（本行）不会再次运行。
--   所以这里既登记权限，也显式把权限授给 SYSTEM_ROOT（幂等，重复执行无副作用）。
--
-- 与 system:root / system:data-permission:bypass 同款形状：is_system=1（管理员面可见、可授），
-- 只授 SYSTEM_ROOT（root 在 SystemPermissionService 语义里本身视为持有）。
-- ============================================================================
INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:ownership', '迁移数据归属', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:ownership');

INSERT INTO `role_permission_list` (`role_entity_id`, `permission_list_id`)
SELECT r.`id`, p.`id`
FROM `role` r
CROSS JOIN `permission` p
WHERE r.`code` = 'SYSTEM_ROOT'
  AND p.`identity` = 'system:data-permission:ownership'
  AND NOT EXISTS (
      SELECT 1
      FROM `role_permission_list` x
      WHERE x.`role_entity_id` = r.`id`
        AND x.`permission_list_id` = p.`id`
  );

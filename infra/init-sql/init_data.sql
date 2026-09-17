-- ============================================================================
-- Kubo 库 · 唯一数据脚本（init_data.sql）
-- ============================================================================
-- 【规则】全仓只有两个数据库脚本：init.sql（结构）+ 本文件（数据）。
--         以后任何"库里必须先有某行"的变更都写在这里，不再新增迁移文件。
-- 【幂等】每条语句都带 WHERE NOT EXISTS / SELECT ... WHERE，可重复执行。
-- 【执行】docker compose up mysql 首次启动会自动跑（挂在 /docker-entrypoint-initdb.d）；
--         已有库手动：mysql -uroot -p kubo < infra/init-sql/init_data.sql
-- 【边界】下面这些**不在**本文件（由应用代码播种，改它们要改 Java）：
--         · 菜单树            → MenuService.initMenu()
--         · 权限行（扫描注册） → PermissionService.loadPermission()
--         · 数据资源/策略/字段策略 → DevDataInitRunner（owner_columns / filterable_columns 亦在此）
--         这里只放"库必须先有、且应用不负责创建"的行：默认租户、系统权限身份、
--         根角色与绑定、用户↔租户回填、以及历史归属列回填。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. 平台默认租户（id = 1）：全库 tenant_id 的默认值，也是多租户过滤的兜底
-- ---------------------------------------------------------------------------
INSERT INTO `tenant` (`id`, `code`, `name`, `status`, `is_published`)
SELECT 1, 'default', '平台默认租户', 1, b'1'
WHERE NOT EXISTS (SELECT 1 FROM `tenant` WHERE `id` = 1);

-- ---------------------------------------------------------------------------
-- 2. 系统权限身份（is_system = 1）：与 SystemPermissionIdentities 常量一一对应
--    应用扫描注册的是普通业务权限；这四个是平台特权，必须由库先有行
--    非 root 角色要拿到它们，只能在角色管理端显式勾选
-- ---------------------------------------------------------------------------
INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:root', '系统超级权限', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:root');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:bypass', '绕过数据权限', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:bypass');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:ownership', '迁移数据归属', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:ownership');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:outbox', '权限变更死信管理', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:outbox');

-- ---------------------------------------------------------------------------
-- 3. 系统超级管理员角色（SYSTEM_ROOT）＋ 它对全部 is_system 权限的绑定
--    ＋ root 用户（id = 1）对该角色的绑定
--    应用侧 initRootAuthorization() 也会做同样的事（幂等）；这里保证"库先自洽"
-- ---------------------------------------------------------------------------
INSERT INTO `role` (`name`, `code`, `is_disabled`, `is_published`)
SELECT '系统超级管理员', 'SYSTEM_ROOT', b'0', b'0'
WHERE NOT EXISTS (SELECT 1 FROM `role` WHERE `code` = 'SYSTEM_ROOT');

INSERT INTO `role_permission_list` (`role_entity_id`, `permission_list_id`)
SELECT r.`id`, p.`id`
FROM `role` r
CROSS JOIN `permission` p
WHERE r.`code` = 'SYSTEM_ROOT'
  AND p.`is_system` = b'1'
  AND NOT EXISTS (
      SELECT 1 FROM `role_permission_list` x
      WHERE x.`role_entity_id` = r.`id` AND x.`permission_list_id` = p.`id`
  );

INSERT INTO `user_role_list` (`user_entity_id`, `role_list_id`)
SELECT u.`id`, r.`id`
FROM `user` u
CROSS JOIN `role` r
WHERE u.`id` = 1
  AND r.`code` = 'SYSTEM_ROOT'
  AND NOT EXISTS (
      SELECT 1 FROM `user_role_list` x
      WHERE x.`user_entity_id` = u.`id` AND x.`role_list_id` = r.`id`
  );

-- ---------------------------------------------------------------------------
-- 4. 用户 ↔ 租户：每个用户至少有一条（默认租户）；新库上是 no-op
-- ---------------------------------------------------------------------------
INSERT INTO `user_tenant` (`user_id`, `tenant_id`, `is_default`)
SELECT u.`id`, 1, b'1' FROM `user` u
WHERE NOT EXISTS (SELECT 1 FROM `user_tenant` ut WHERE ut.`user_id` = u.`id`);

-- ---------------------------------------------------------------------------
-- 5. 历史归属列回填（幂等；新库上是 no-op）
--    tenant_id / creator_id 是数据权限的归属维度，历史行缺值时补成默认租户/系统账号
-- ---------------------------------------------------------------------------
UPDATE `contract` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `contract` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `contract_document` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `contract_document` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `customer` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `customer` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `department` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `department` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `input` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `input` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `input_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `input_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `inventory` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `inventory` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `material` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `material` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `move` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `move` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `move_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `move_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `output` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `output` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `output_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `output_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `participant` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `participant` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `personal_token` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `personal_token` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `purchase` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `purchase_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `purchase_price` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase_price` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `sale` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `sale_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `sale_price` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale_price` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `storage` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `storage` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `structure` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `structure` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `supplier` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `supplier` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `unit` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `unit` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

UPDATE `user` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `user` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

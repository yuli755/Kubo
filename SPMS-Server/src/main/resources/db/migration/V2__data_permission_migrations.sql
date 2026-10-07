-- ============================================================================
-- Kubo 数据权限二次改造 · 迁移脚本合并版（单文件，按执行顺序）
-- ============================================================================
-- 生成：2026-09-09，由原 9 份分步脚本按时间顺序合并（各阶段内容逐字节保留，仅加本总头与
--       「阶段 N/9」分隔注释；原分步文件可在 git 历史中找到）。
-- 用法：mysql -u<user> -p <db> < 本文件
-- 幂等：可重复执行（CI 连跑两遍、比对 information_schema 列清单一致，判据见
--       .github/workflows/ci.yml「Migrations」job）。唯一预期内报错是 1146
--       「表不存在」——permission/role/user/bom 等业务表由 Hibernate ddl-auto 生成，
--       本脚本假定它们已存在，只建 data_* 与关系表。
-- 顺序：模型 → 多租户地基 → 规则内核 → 拒绝审计 → ORM 对账 → 证据留存 → 工厂归属列
--       → 归属迁移特权 → 用户-工厂关系。
-- ============================================================================

-- ########## 阶段 1/9：数据权限模型（原 20260817_data_permission_model.sql）##########
-- 供应链权限治理阶段 1：权限资源、策略、字段策略和 Outbox 模型
-- 执行前提：MySQL 8.0+；脚本可重复执行，不修改既有业务表数据。

CREATE TABLE IF NOT EXISTS `data_resource` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_key` VARCHAR(128) NOT NULL,
    `table_name` VARCHAR(128) NOT NULL,
    `table_alias` VARCHAR(64) NULL,
    `tenant_column` VARCHAR(64) NOT NULL DEFAULT 'tenant_id',
    `owner_columns` VARCHAR(512) NULL,
    `supported_actions` VARCHAR(256) NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    `version_no` BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_resource_key` (`resource_key`),
    KEY `idx_data_resource_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限资源';

CREATE TABLE IF NOT EXISTS `data_policy` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `policy_code` VARCHAR(128) NOT NULL,
    `resource_id` BIGINT UNSIGNED NOT NULL,
    `subject_type` VARCHAR(16) NOT NULL,
    `subject_id` BIGINT UNSIGNED NULL,
    `role_code` VARCHAR(128) NULL,
    `action_name` VARCHAR(16) NOT NULL,
    `condition_json` JSON NOT NULL,
    `priority` INT NOT NULL DEFAULT 0,
    `enabled` BIT(1) NOT NULL DEFAULT b'1',
    `version_no` BIGINT NOT NULL DEFAULT 1,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_policy_code` (`policy_code`),
    KEY `idx_data_policy_lookup` (`resource_id`, `action_name`, `enabled`, `priority`),
    CONSTRAINT `fk_data_policy_resource` FOREIGN KEY (`resource_id`) REFERENCES `data_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限策略';

CREATE TABLE IF NOT EXISTS `data_field_policy` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_id` BIGINT UNSIGNED NOT NULL,
    `field_name` VARCHAR(128) NOT NULL,
    `role_code` VARCHAR(128) NOT NULL,
    `action_name` VARCHAR(16) NOT NULL,
    `mask_type` VARCHAR(32) NOT NULL,
    `mask_expression` VARCHAR(512) NULL,
    `allow_plaintext` BIT(1) NOT NULL DEFAULT b'0',
    `priority` INT NOT NULL DEFAULT 0,
    `enabled` BIT(1) NOT NULL DEFAULT b'1',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_field_policy` (`resource_id`, `field_name`, `role_code`),
    KEY `idx_data_field_policy_lookup` (`resource_id`, `action_name`, `enabled`, `priority`),
    CONSTRAINT `fk_data_field_policy_resource` FOREIGN KEY (`resource_id`) REFERENCES `data_resource` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字段权限策略';

CREATE TABLE IF NOT EXISTS `permission_change_outbox` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `event_key` VARCHAR(192) NOT NULL,
    `aggregate_type` VARCHAR(64) NOT NULL,
    `aggregate_id` BIGINT UNSIGNED NOT NULL,
    `event_type` VARCHAR(64) NOT NULL,
    `payload` JSON NOT NULL,
    `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    `retry_count` INT NOT NULL DEFAULT 0,
    `next_retry_at` BIGINT NULL,
    `processed_at` BIGINT NULL,
    `last_error` VARCHAR(2000) NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_outbox_event` (`event_key`),
    KEY `idx_permission_outbox_dispatch` (`status`, `next_retry_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限变更 Outbox';

-- 显式系统权限：仅用于从旧的 ID=1 旁路迁移到可审计的权限关系。
INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:root', '系统超级权限', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:root');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:bypass', '绕过数据权限', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:bypass');

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
      SELECT 1
      FROM `role_permission_list` x
      WHERE x.`role_entity_id` = r.`id`
        AND x.`permission_list_id` = p.`id`
  );

INSERT INTO `user_role_list` (`user_entity_id`, `role_list_id`)
SELECT u.`id`, r.`id`
FROM `user` u
CROSS JOIN `role` r
WHERE u.`id` = 1
  AND r.`code` = 'SYSTEM_ROOT'
  AND NOT EXISTS (
      SELECT 1
      FROM `user_role_list` x
      WHERE x.`user_entity_id` = u.`id`
        AND x.`role_list_id` = r.`id`
  );

-- ########## 阶段 2/9：多租户地基（原 20260906_multitenant_foundation.sql）##########
-- ============================================================================
-- Kubo 真多租产地基 + 数据权限归属列
-- 由 tools/scripts/gen_multitenant_migration.py 自动生成（请勿手改逻辑，改生成器）
--
-- 特性：幂等可重复执行 / 不删列 / 不截断 / 每步可独立回滚
-- 前置：MySQL 8.0+；执行前备份；`spring.jpa.hibernate.ddl-auto` 必须为 validate
-- 语义：存量数据全部归属「平台默认租户」tenant_id = 1
-- 执行：mysql -u<user> -p <db> < 20260906_multitenant_foundation.sql
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. 幂等工具过程
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS kubo_mt_add_column;
DROP PROCEDURE IF EXISTS kubo_mt_add_index;
DROP PROCEDURE IF EXISTS kubo_mt_replace_unique;
DROP PROCEDURE IF EXISTS kubo_mt_backfill_tenant;
DROP PROCEDURE IF EXISTS kubo_mt_check_tenant;
DELIMITER $$

CREATE PROCEDURE kubo_mt_add_column(IN t VARCHAR(64), IN c VARCHAR(64), IN col_ddl TEXT)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', col_ddl);
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

CREATE PROCEDURE kubo_mt_add_index(IN t VARCHAR(64), IN idx VARCHAR(64), IN cols TEXT)
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t)
     AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = idx) THEN
    SET @s = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', idx, '` (', cols, ')');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

-- 单列唯一 → (tenant_id, col) 复合唯一；先按列反查真实索引名再 DROP（Hibernate 生成的索引名不可预测）
CREATE PROCEDURE kubo_mt_replace_unique(IN t VARCHAR(64), IN c VARCHAR(64))
BEGIN
  DECLARE old_idx VARCHAR(64) DEFAULT NULL;
  IF NOT EXISTS (SELECT 1 FROM information_schema.TABLES
                  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t) THEN
    -- 表不存在 = 该资源还没建表 / 不属于本库：与 kubo_mt_add_column、kubo_mt_add_index 同构地静默跳过。
    -- 不能只设置 old_idx 后继续查询：MySQL 对不存在表的 information_schema.STATISTICS 查询虽可返回空，
    -- 但后续仍会尝试 ALTER TABLE，导致迁移在可选业务表缺席时失败。
    SET old_idx = NULL;
  ELSE
    SELECT INDEX_NAME INTO old_idx
      FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND NON_UNIQUE = 0
     GROUP BY INDEX_NAME
    HAVING COUNT(*) = 1 AND MAX(CASE WHEN COLUMN_NAME = c THEN 1 ELSE 0 END) = 1
     LIMIT 1;

    IF old_idx IS NOT NULL THEN
      SET @s = CONCAT('ALTER TABLE `', t, '` DROP INDEX `', old_idx, '`');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = CONCAT('uk_', t, '_tenant_', c)) THEN
      SET @s = CONCAT('ALTER TABLE `', t, '` ADD UNIQUE KEY `uk_', t, '_tenant_', c, '` (tenant_id, `', c, '`)');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
  END IF;
END$$

CREATE PROCEDURE kubo_mt_backfill_tenant(IN t VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t) THEN
    SET @s = CONCAT('UPDATE `', t, '` SET tenant_id = 1 WHERE tenant_id IS NULL OR tenant_id = 0');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    SET @s = CONCAT('UPDATE `', t, '` SET creator_id = 1 WHERE creator_id IS NULL');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

CREATE PROCEDURE kubo_mt_check_tenant(IN t VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.TABLES
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t) THEN
    SET @s = CONCAT('SELECT ''', t, ''' tbl, COUNT(*) bad_tenant FROM `', t, '` WHERE tenant_id IS NULL OR tenant_id = 0');
    PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
  END IF;
END$$

DELIMITER ;

-- ---------------------------------------------------------------------------
-- 1. 租户主表 + 用户-租户关系（服务端可信租户来源，替代 X-Tenant-Id 请求头）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `code` VARCHAR(64) NOT NULL COMMENT '租户编码',
    `name` VARCHAR(255) NOT NULL COMMENT '租户名称',
    `status` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
    `expire_time` BIGINT NULL COMMENT '到期时间(ms)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tenant_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户';

CREATE TABLE IF NOT EXISTS `user_tenant` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `user_id` BIGINT UNSIGNED NOT NULL,
    `tenant_id` BIGINT UNSIGNED NOT NULL,
    `is_default` BIT(1) NOT NULL DEFAULT b'0' COMMENT '登录后的默认租户',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_tenant` (`user_id`, `tenant_id`),
    KEY `idx_user_tenant_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可访问的租户集合';

INSERT INTO `tenant` (`id`, `code`, `name`, `status`, `is_published`)
SELECT 1, 'default', '平台默认租户', 1, b'1'
 WHERE NOT EXISTS (SELECT 1 FROM `tenant` WHERE `id` = 1);

INSERT INTO `user_tenant` (`user_id`, `tenant_id`, `is_default`)
SELECT u.`id`, 1, b'1' FROM `user` u
 WHERE NOT EXISTS (SELECT 1 FROM `user_tenant` ut WHERE ut.`user_id` = u.`id`);

-- ---------------------------------------------------------------------------
-- 2. 业务表：加列 + 索引 + 唯一域降级 + 存量回填
-- ---------------------------------------------------------------------------
-- [T1] asset.contract  ← ContractEntity.java
CALL kubo_mt_add_column('contract', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('contract', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('contract', 'idx_contract_tenant', 'tenant_id');
-- ⚠️ @ManyToMany 无标量列（中间表）：document_list, participant_list → 只能服务端解析成 ID 集合再注入 IN
CALL kubo_mt_replace_unique('contract', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `contract` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `contract` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] asset.contractDocument  ← ContractDocumentEntity.java
CALL kubo_mt_add_column('contract_document', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('contract_document', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('contract_document', 'idx_contract_document_tenant', 'tenant_id');
UPDATE `contract_document` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `contract_document` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] channel.customer  ← CustomerEntity.java
CALL kubo_mt_add_column('customer', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('customer', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('customer', 'idx_customer_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('customer', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `customer` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `customer` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] personnel.department  ← DepartmentEntity.java
CALL kubo_mt_add_column('department', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('department', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('department', 'idx_department_tenant', 'tenant_id');
-- 可用归属列：parent_id
CALL kubo_mt_replace_unique('department', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `department` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `department` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] wms.input  ← InputEntity.java
CALL kubo_mt_add_column('input', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('input', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('input', 'idx_input_tenant', 'tenant_id');
-- 可用归属列：move_id, order_id, purchase_id, structure_id
CALL kubo_mt_replace_unique('input', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `input` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `input` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] wms.inputDetail  ← InputDetailEntity.java
CALL kubo_mt_add_column('input_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('input_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('input_detail', 'idx_input_detail_tenant', 'tenant_id');
-- 可用归属列：material_id
UPDATE `input_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `input_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] wms.inventory  ← InventoryEntity.java
CALL kubo_mt_add_column('inventory', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('inventory', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('inventory', 'idx_inventory_tenant', 'tenant_id');
-- 可用归属列：material_id, storage_id, structure_id
UPDATE `inventory` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `inventory` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] asset.material  ← MaterialEntity.java
CALL kubo_mt_add_column('material', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('material', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('material', 'idx_material_tenant', 'tenant_id');
-- 可用归属列：unit_id
CALL kubo_mt_replace_unique('material', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('material', 'name');  -- 原唯一列 name → (tenant_id, name)
UPDATE `material` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `material` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] chat.member  ← MemberEntity.java
CALL kubo_mt_add_column('member', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('member', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('member', 'idx_member_tenant', 'tenant_id');
-- 可用归属列：room_id, user_id
CALL kubo_mt_backfill_tenant('member');

-- [T1] wms.move  ← MoveEntity.java
CALL kubo_mt_add_column('move', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('move', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('move', 'idx_move_tenant', 'tenant_id');
-- 可用归属列：storage_id
CALL kubo_mt_replace_unique('move', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `move` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `move` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] wms.moveDetail  ← MoveDetailEntity.java
CALL kubo_mt_add_column('move_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('move_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('move_detail', 'idx_move_detail_tenant', 'tenant_id');
-- 可用归属列：inventory_id
UPDATE `move_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `move_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] wms.output  ← OutputEntity.java
CALL kubo_mt_add_column('output', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('output', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('output', 'idx_output_tenant', 'tenant_id');
-- 可用归属列：move_id, picking_id, sale_id
CALL kubo_mt_replace_unique('output', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `output` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `output` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] wms.outputDetail  ← OutputDetailEntity.java
CALL kubo_mt_add_column('output_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('output_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('output_detail', 'idx_output_detail_tenant', 'tenant_id');
-- 可用归属列：inventory_id, material_id
UPDATE `output_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `output_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] asset.participant  ← ParticipantEntity.java
CALL kubo_mt_add_column('participant', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('participant', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('participant', 'idx_participant_tenant', 'tenant_id');
UPDATE `participant` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `participant` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] personnel.personalToken  ← PersonalTokenEntity.java
CALL kubo_mt_add_column('personal_token', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('personal_token', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('personal_token', 'idx_personal_token_tenant', 'tenant_id');
-- 可用归属列：user_id
CALL kubo_mt_replace_unique('personal_token', 'token');  -- 原唯一列 token → (tenant_id, token)
UPDATE `personal_token` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `personal_token` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] channel.purchase  ← PurchaseEntity.java
CALL kubo_mt_add_column('purchase', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('purchase', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('purchase', 'idx_purchase_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('purchase', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `purchase` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] channel.purchaseDetail  ← PurchaseDetailEntity.java
CALL kubo_mt_add_column('purchase_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('purchase_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('purchase_detail', 'idx_purchase_detail_tenant', 'tenant_id');
-- 可用归属列：material_id, supplier_id
UPDATE `purchase_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] channel.purchasePrice  ← PurchasePriceEntity.java
CALL kubo_mt_add_column('purchase_price', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('purchase_price', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('purchase_price', 'idx_purchase_price_tenant', 'tenant_id');
-- 可用归属列：material_id, supplier_id
UPDATE `purchase_price` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `purchase_price` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] chat.room  ← RoomEntity.java
CALL kubo_mt_add_column('room', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('room', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('room', 'idx_room_tenant', 'tenant_id');
-- 可用归属列：owner_id
CALL kubo_mt_replace_unique('room', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('room', 'name');  -- 原唯一列 name → (tenant_id, name)
CALL kubo_mt_backfill_tenant('room');

-- [T1] channel.sale  ← SaleEntity.java
CALL kubo_mt_add_column('sale', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('sale', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('sale', 'idx_sale_tenant', 'tenant_id');
-- 可用归属列：customer_id
CALL kubo_mt_replace_unique('sale', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `sale` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] channel.saleDetail  ← SaleDetailEntity.java
CALL kubo_mt_add_column('sale_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('sale_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('sale_detail', 'idx_sale_detail_tenant', 'tenant_id');
-- 可用归属列：material_id
UPDATE `sale_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] channel.salePrice  ← SalePriceEntity.java
CALL kubo_mt_add_column('sale_price', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('sale_price', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('sale_price', 'idx_sale_price_tenant', 'tenant_id');
-- 可用归属列：customer_id, material_id
UPDATE `sale_price` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `sale_price` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] factory.storage  ← StorageEntity.java
CALL kubo_mt_add_column('storage', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('storage', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('storage', 'idx_storage_tenant', 'tenant_id');
-- 可用归属列：parent_id
CALL kubo_mt_replace_unique('storage', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `storage` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `storage` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] factory.structure  ← StructureEntity.java
CALL kubo_mt_add_column('structure', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('structure', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('structure', 'idx_structure_tenant', 'tenant_id');
-- 可用归属列：parent_id
-- ⚠️ @ManyToMany 无标量列（中间表）：operation_list → 只能服务端解析成 ID 集合再注入 IN
CALL kubo_mt_replace_unique('structure', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `structure` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `structure` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] channel.supplier  ← SupplierEntity.java
CALL kubo_mt_add_column('supplier', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('supplier', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('supplier', 'idx_supplier_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('supplier', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `supplier` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `supplier` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] system.unit  ← UnitEntity.java
CALL kubo_mt_add_column('unit', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('unit', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('unit', 'idx_unit_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('unit', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('unit', 'name');  -- 原唯一列 name → (tenant_id, name)
UPDATE `unit` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `unit` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] personnel.user  ← UserEntity.java
CALL kubo_mt_add_column('user', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('user', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('user', 'idx_user_tenant', 'tenant_id');
-- 可用归属列：department_id
-- ⚠️ @ManyToMany 无标量列（中间表）：role_list, department_list, storage_list → 只能服务端解析成 ID 集合再注入 IN
CALL kubo_mt_replace_unique('user', 'email');  -- 原唯一列 email → (tenant_id, email)
CALL kubo_mt_replace_unique('user', 'phone');  -- 原唯一列 phone → (tenant_id, phone)
UPDATE `user` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `user` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] open.userThirdLogin  ← UserThirdLoginEntity.java
CALL kubo_mt_add_column('user_third_login', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('user_third_login', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('user_third_login', 'idx_user_third_login_tenant', 'tenant_id');
-- 可用归属列：user_id
CALL kubo_mt_backfill_tenant('user_third_login');

-- ---------------------------------------------------------------------------
-- 3. 执行后校验（任一非 0 都不得开启 enforce；建议做成 CI 检查）
-- ---------------------------------------------------------------------------
SELECT 'contract' tbl, COUNT(*) bad_tenant FROM `contract` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'contract_document' tbl, COUNT(*) bad_tenant FROM `contract_document` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'customer' tbl, COUNT(*) bad_tenant FROM `customer` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'department' tbl, COUNT(*) bad_tenant FROM `department` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input' tbl, COUNT(*) bad_tenant FROM `input` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input_detail' tbl, COUNT(*) bad_tenant FROM `input_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'inventory' tbl, COUNT(*) bad_tenant FROM `inventory` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'material' tbl, COUNT(*) bad_tenant FROM `material` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
CALL kubo_mt_check_tenant('member');
SELECT 'move' tbl, COUNT(*) bad_tenant FROM `move` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'move_detail' tbl, COUNT(*) bad_tenant FROM `move_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output' tbl, COUNT(*) bad_tenant FROM `output` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output_detail' tbl, COUNT(*) bad_tenant FROM `output_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'participant' tbl, COUNT(*) bad_tenant FROM `participant` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'personal_token' tbl, COUNT(*) bad_tenant FROM `personal_token` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase' tbl, COUNT(*) bad_tenant FROM `purchase` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_detail' tbl, COUNT(*) bad_tenant FROM `purchase_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_price' tbl, COUNT(*) bad_tenant FROM `purchase_price` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
CALL kubo_mt_check_tenant('room');
SELECT 'sale' tbl, COUNT(*) bad_tenant FROM `sale` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'sale_detail' tbl, COUNT(*) bad_tenant FROM `sale_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'sale_price' tbl, COUNT(*) bad_tenant FROM `sale_price` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'storage' tbl, COUNT(*) bad_tenant FROM `storage` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'structure' tbl, COUNT(*) bad_tenant FROM `structure` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'supplier' tbl, COUNT(*) bad_tenant FROM `supplier` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'unit' tbl, COUNT(*) bad_tenant FROM `unit` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'user' tbl, COUNT(*) bad_tenant FROM `user` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
CALL kubo_mt_check_tenant('user_third_login');

-- ---------------------------------------------------------------------------
-- 4. 回滚
--    · 数据：UPDATE <表> SET tenant_id = 1;
--    · 能力：kubo.data-permission.mode = shadow|off（应用侧开关，不依赖本脚本）
--    · 索引：ALTER TABLE <表> DROP INDEX uk_<表>_tenant_<col>; 仅在确认无跨租户重复后重建单列唯一
--    · 严禁在 ddl-auto=validate 环境 DROP COLUMN（会让启动失败）
-- ---------------------------------------------------------------------------

-- ########## 阶段 3/9：规则内核（原 20260907_data_permission_rule_kernel.sql）##########
-- ============================================================================
-- 数据权限内核（阶段 P1）：条件树白名单列 + 影子双跑差异表
-- 生成时间：2026-09-07    幂等：是（可重复执行）
-- 前置：20260906_multitenant_foundation.sql（租户地基）
-- 说明：本文件手工维护（表结构稳定、只有一列一张表，不值得进生成器）；
--       T1/T2 业务表的归属列变更请改 tools/scripts/gen_multitenant_migration.py 后重生成。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1) data_resource.filterable_columns：条件树可引用的列白名单
--    没有它，"策略里写 {"field":"xxx"}" 就无法判断是合法列还是注入尝试；
--    白名单为空时一律拒绝（安全默认），因此**接入某个资源的条件过滤 = 先登记这一列**。
-- ---------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS kubo_rk_add_column;
DELIMITER $$
CREATE PROCEDURE kubo_rk_add_column(IN t VARCHAR(64), IN c VARCHAR(128), IN ddl TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME = c) THEN
        SET @s = CONCAT('ALTER TABLE `', t, '` ADD COLUMN ', ddl);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$
DELIMITER ;

CALL kubo_rk_add_column('data_resource', 'filterable_columns',
  '`filterable_columns` VARCHAR(512) NULL COMMENT ''条件树可引用的列（CSV）''');
DROP PROCEDURE kubo_rk_add_column;

-- 示例：让库存资源可以按仓库/状态做条件过滤（按需在管理端维护，这里只给形状）
-- UPDATE `data_resource` SET `filterable_columns` = 'warehouse_id,storage_id,status'
--  WHERE `resource_key` = 'wms.inventory';

-- ---------------------------------------------------------------------------
-- 2) data_permission_diff_log：影子双跑差异
--    回答"enforce 后会少返回多少行"。只在 shadow 模式采样写入，默认 2%。
--    审计留存默认 30 天（enforce 决策要看趋势），由 DataPermissionEvidenceRetention 按 create_time
--    滚动清理；长期归档必须在数据库备份/归档层先完成，应用不把历史证据复制到第三张业务表。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_permission_diff_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_key` VARCHAR(128) NOT NULL COMMENT '数据资源标识',
    `action_name` VARCHAR(16) NOT NULL COMMENT 'QUERY/CREATE/UPDATE/DELETE',
    `trace_id` VARCHAR(64) NULL COMMENT '链路标识',
    `user_id` BIGINT UNSIGNED NULL,
    `tenant_id` BIGINT UNSIGNED NULL,
    `baseline_count` BIGINT NULL COMMENT '不加权限条件的行数',
    `guarded_count` BIGINT NULL COMMENT '加条件后的行数',
    `verdict` VARCHAR(16) NOT NULL COMMENT 'CONSISTENT/EXPECTED_FILTER/ANOMALY/ERROR',
    `cost_ms` BIGINT NULL COMMENT '比对耗时',
    `detail` TEXT NULL COMMENT '明细/异常',
    PRIMARY KEY (`id`),
    KEY `idx_dp_diff_resource_time` (`resource_key`, `create_time`),
    KEY `idx_dp_diff_verdict` (`verdict`, `create_time`),
    KEY `idx_dp_diff_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限影子双跑差异';

-- ---------------------------------------------------------------------------
-- 回滚（只回滚结构，不删数据；数据由归档任务负责）
-- ALTER TABLE `data_resource` DROP COLUMN `filterable_columns`;
-- RENAME TABLE `data_permission_diff_log` TO `data_permission_diff_log_disabled`;
-- ---------------------------------------------------------------------------

-- ########## 阶段 4/9：拒绝审计（原 20260908_data_permission_deny_log.sql）##########
-- ============================================================================
-- 数据权限（阶段 B4 · §19.31）：拒绝事件落库审计表
-- 生成时间：2026-09-08    幂等：是（可重复执行）
-- 前置：无（本表不依赖其它数据权限表；框架列 id/create_time/update_time/is_disabled/is_published
--       由 airpower 基类与 BaseEntity 提供，与其余 data_* 表一致）
-- 说明：手工维护。表结构必须与 DataPermissionDenyLogEntity 逐列对齐 —— 这条由
--       pnpm ddl:check（CI 里还会用真实 information_schema 反向核对）看着，写错列名当场红。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- data_permission_deny_log：数据权限「拒绝事件」审计
--   「被数据权限拒了」此前只有 Micrometer 计数 + WARN 日志，事后追认（谁、何时、因何、在哪个
--   档位下被拒）无处可查。四个拒答出口（写侧动作级 / 写侧行级 / 导出旁路 / 读侧恒假条件）统一
--   落这一张表。语义边界：本仓策略是「过滤条件」而非「allow/deny 规则」，因此记的是拒绝事件，
--   不是「某条策略命中」—— 原因由 reason 编码、明细由 detail 承载，不含策略 ID。
--   落库由 DataPermissionDenyLogRecorder 异步写入（拒绝路径不因落库失败而改变结果）；
--   默认留存与清理策略与其余两张证据表一致（DataPermissionEvidenceRetention，见 §19.37）。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_permission_deny_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_key` VARCHAR(128) NOT NULL COMMENT '数据资源标识',
    `reason` VARCHAR(64) NOT NULL COMMENT '拒绝原因（unregistered-resource/write-without-policy/row-not-writable/export-bypass…）',
    `mode` VARCHAR(16) NOT NULL COMMENT '拒绝发生时该资源的生效档位 OFF/SHADOW/ENFORCE',
    `user_id` BIGINT UNSIGNED NULL COMMENT '被拒主体用户',
    `tenant_id` BIGINT UNSIGNED NULL COMMENT '被拒主体租户',
    `detail` TEXT NULL COMMENT '运维明细（ID、字段名、动作等）',
    PRIMARY KEY (`id`),
    KEY `idx_dp_deny_resource_time` (`resource_key`, `create_time`),
    KEY `idx_dp_deny_reason_time` (`reason`, `create_time`),
    KEY `idx_dp_deny_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限拒绝事件';

-- ---------------------------------------------------------------------------
-- 回滚（只回滚结构，不删数据）
-- RENAME TABLE `data_permission_deny_log` TO `data_permission_deny_log_disabled`;
-- ---------------------------------------------------------------------------

-- ########## 阶段 5/9：ORM 对账（原 20260908_data_permission_orm_parity.sql）##########
-- ============================================================================
-- 数据权限（阶段 P2 · §19.36）：两套 ORM 的行数对账表
-- 生成时间：2026-09-08    幂等：是（可重复执行）
-- 前置：20260907_data_permission_rule_kernel.sql（影子差异表）
-- 说明：手工维护。表结构必须与 DataPermissionOrmParityEntity 逐列对齐 —— 这条由
--       pnpm ddl:check（CI 里还会用真实 information_schema 反向核对）看着，写错列名当场红。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- data_permission_orm_parity_log：JPA 与 MyBatis 对同一棵条件树数出的行数对账
--   与 data_permission_diff_log 分开两张表是刻意的：那张表回答"加条件会不会少行"，
--   这张回答"两套 ORM 会不会数出不同的行"。混在一张表里，放开前置检查的正向样本
--   （EXPECTED_FILTER）就会被另一种语义污染，而这两种一致谁也不能替谁作证。
--   只在 shadow 采样且该资源登记了 Mapper 探针时写入（kubo.data-permission.mapper-read.enabled
--   默认 false ⇒ 这张表在生产默认是空的，不是"没有差异"）。
--   默认留存 30 天，由 DataPermissionEvidenceRetention 按 create_time 滚动清理；长期归档
--   必须在数据库备份/归档层先行完成，应用不会把历史证据复制到第三张业务表。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `data_permission_orm_parity_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_key` VARCHAR(128) NOT NULL COMMENT '数据资源标识',
    `action_name` VARCHAR(16) NOT NULL COMMENT 'QUERY/CREATE/UPDATE/DELETE',
    `table_name` VARCHAR(128) NULL COMMENT 'Mapper 侧数的是哪张物理表',
    `user_id` BIGINT UNSIGNED NULL,
    `tenant_id` BIGINT UNSIGNED NULL,
    `jpa_count` BIGINT NULL COMMENT 'JPA 侧加条件后的行数',
    `mapper_count` BIGINT NULL COMMENT 'Mapper 侧加同一段片段后的行数',
    `verdict` VARCHAR(16) NOT NULL COMMENT 'CONSISTENT/MAPPER_STRICT/MAPPER_LOOSE/ERROR',
    `fragment_sql` VARCHAR(512) NULL COMMENT 'Mapper 侧片段原文（值均为参数占位）',
    `cost_ms` BIGINT NULL COMMENT '对账耗时',
    `detail` TEXT NULL COMMENT '明细/异常',
    PRIMARY KEY (`id`),
    KEY `idx_dp_parity_resource_time` (`resource_key`, `create_time`),
    KEY `idx_dp_parity_verdict` (`verdict`, `create_time`),
    KEY `idx_dp_parity_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限 ORM 行数对账';

-- ---------------------------------------------------------------------------
-- 回滚（只回滚结构，不删数据）
-- RENAME TABLE `data_permission_orm_parity_log` TO `data_permission_orm_parity_log_disabled`;
-- ---------------------------------------------------------------------------

-- ########## 阶段 6/9：证据留存（原 20260909_data_permission_evidence_retention.sql）##########
-- ============================================================================
-- 数据权限（阶段 P2 · §19.37）：证据留存清理索引
-- 生成时间：2026-09-07    幂等：是（可重复执行）
-- 前置：20260907_data_permission_rule_kernel.sql、20260908_data_permission_orm_parity.sql
-- 说明：两张证据表由 DataPermissionEvidenceRetention 按 create_time 滚动清理。
--       这是一份独立迁移，保证已经执行过前两份建表脚本的环境也能补上索引；
--       不把"改过 CREATE TABLE"误当成"存量库已经变更"。
-- ============================================================================

DROP PROCEDURE IF EXISTS kubo_dp_add_evidence_time_index;
DELIMITER $$
CREATE PROCEDURE kubo_dp_add_evidence_time_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64))
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table
    ) AND NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @kubo_evidence_index_sql = CONCAT(
            'ALTER TABLE `', p_table, '` ADD INDEX `', p_index, '` (`create_time`)'
        );
        PREPARE kubo_evidence_index_stmt FROM @kubo_evidence_index_sql;
        EXECUTE kubo_evidence_index_stmt;
        DEALLOCATE PREPARE kubo_evidence_index_stmt;
    END IF;
END$$
DELIMITER ;

CALL kubo_dp_add_evidence_time_index('data_permission_diff_log', 'idx_dp_diff_create_time');
CALL kubo_dp_add_evidence_time_index('data_permission_orm_parity_log', 'idx_dp_parity_create_time');
DROP PROCEDURE kubo_dp_add_evidence_time_index;

-- ---------------------------------------------------------------------------
-- 回滚（只回滚索引，不删除证据）
-- ALTER TABLE `data_permission_diff_log` DROP INDEX `idx_dp_diff_create_time`;
-- ALTER TABLE `data_permission_orm_parity_log` DROP INDEX `idx_dp_parity_create_time`;
-- ---------------------------------------------------------------------------

-- ########## 阶段 7/9：工厂归属列（原 20260909_factory_owner_columns.sql）##########
-- ============================================================================
-- D13-B 工厂维度：给既有部署回填 wms.inventory / wms.move 的工厂归属列登记
-- ============================================================================
-- 决策（D13 方案 A）：工厂 = `storage` 树顶层节点，业务实体的工厂归属就是它身上的
-- `@ManyToOne storage` 关联。FACTORY 策略列绑定走 `FACTORY:storage.id` 关联路径
-- （OwnerColumnRegistration 的「DIM:path」语法，一跳封顶）。
--
-- 为什么需要本脚本，而不是只在 DevDataInitRunner 里补 .setOwnerColumns(...)：
--   DevDataInitRunner 只在「资源不存在」时才写行（if filter(...).isEmpty()），
--   现网这两个资源早已存在且 owner_columns 为空/NULL。若不回填，翻
--   `factory-dimension-enabled: true` 后 FactoryDimensionReadiness 启动校验会报
--   「没有任何已注册资源具备工厂归属列」→ 启动即红。所以回填必须幂等地放进迁移脚本，
--   与启动校验同一批上线。
--
-- 幂等：UPDATE 天然幂等。对已有 owner_columns 采用 CONCAT 追加，不覆盖既有登记
--   （比如某部署已经手登了 WAREHOUSE:storage.id），并且不重复追加（LIKE 判断）。
--   重复执行无副作用（CI 连跑两遍判据）。
-- ============================================================================
UPDATE `data_resource`
SET `owner_columns` = CASE
    WHEN `owner_columns` IS NULL OR `owner_columns` = '' THEN 'FACTORY:storage.id'
    WHEN `owner_columns` NOT LIKE '%FACTORY:storage.id%' THEN CONCAT(`owner_columns`, ',FACTORY:storage.id')
    ELSE `owner_columns`
END
WHERE `resource_key` IN ('wms.inventory', 'wms.move');

-- ########## 阶段 8/9：归属迁移特权（原 20260909_system_permission_ownership.sql）##########
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

-- ########## 阶段 9/9：用户-工厂关系（原 20260909_user_factory.sql）##########
-- ============================================================================
-- D13 工厂维度：用户 ↔ 工厂（仓库/工厂树的顶层 storage 节点）关系表
-- ============================================================================
-- 决策（D13 方案 A）：本仓没有独立的 factory 实体，`storage` 树（parentId 自关联）
-- 就是文档里的「仓库/工厂树」；工厂 = 该树顶层节点。于是 user_factory 是与 user.storageList
-- 同构的 M2M 中间表，只是语义上代表「用户所属的工厂」。
--
-- 与 user_tenant 同构的取舍：
--   * 不建 FOREIGN KEY：框架表（user / storage）由 Hibernate ddl-auto 生成，本迁移脚本
--     假定它们已存在（CI 里这批脚本以 --force 跑在空库上，只容忍 1146）；FK 会让脚本在
--     空库上必然失败。Hibernate 的 schema validate 只核对表与列存在，不核对 FK，因此
--     不建 FK 不影响生产启动校验。
--   * 列名与 @JoinTable 显式对齐：UserEntity.factoryList 声明了
--     @JoinTable(name="user_factory", joinColumns=user_entity_id, inverseJoinColumns=factory_id)，
--     本表必须一字不差，否则生产 ddl-auto=validate 启动即 Schema-validation 失败。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；重复执行无副作用（CI 连跑两遍判据）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_factory` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（UserEntity.id）',
    `factory_id` BIGINT UNSIGNED NOT NULL COMMENT '工厂 ID（storage 树节点 id，通常为顶层节点）',
    PRIMARY KEY (`user_entity_id`, `factory_id`),
    KEY `idx_user_factory_factory` (`factory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可访问的工厂集合（D13 工厂维度）';


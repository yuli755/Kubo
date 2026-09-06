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
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'kubo_mt_replace_unique: table not found';
  END IF;

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
-- [T1] mes.bom  ← BomEntity.java
CALL kubo_mt_add_column('bom', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('bom', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('bom', 'idx_bom_tenant', 'tenant_id');
-- ⚠️ @ManyToMany 无标量列（中间表）：details → 只能服务端解析成 ID 集合再注入 IN
CALL kubo_mt_replace_unique('bom', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `bom` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `bom` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] mes.bomDetail  ← BomDetailEntity.java
CALL kubo_mt_add_column('bom_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('bom_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('bom_detail', 'idx_bom_detail_tenant', 'tenant_id');
-- 可用归属列：material_id
UPDATE `bom_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `bom_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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

-- [T1] asset.device  ← DeviceEntity.java
CALL kubo_mt_add_column('device', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('device', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('device', 'idx_device_tenant', 'tenant_id');
-- ⚠️ @ManyToMany 无标量列（中间表）：parameters → 只能服务端解析成 ID 集合再注入 IN
CALL kubo_mt_replace_unique('device', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('device', 'name');  -- 原唯一列 name → (tenant_id, name)
CALL kubo_mt_replace_unique('device', 'uuid');  -- 原唯一列 uuid → (tenant_id, uuid)
UPDATE `device` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `device` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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
UPDATE `member` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `member` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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

-- [T1] mes.operation  ← OperationEntity.java
CALL kubo_mt_add_column('operation', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('operation', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('operation', 'idx_operation_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('operation', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('operation', 'name');  -- 原唯一列 name → (tenant_id, name)
UPDATE `operation` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `operation` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] mes.orderDetail  ← OrderDetailEntity.java
CALL kubo_mt_add_column('order_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('order_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('order_detail', 'idx_order_detail_tenant', 'tenant_id');
UPDATE `order_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `order_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] mes.order  ← OrderEntity.java
CALL kubo_mt_add_column('orders', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('orders', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('orders', 'idx_orders_tenant', 'tenant_id');
-- 可用归属列：customer_id, material_id, plan_id
CALL kubo_mt_replace_unique('orders', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `orders` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `orders` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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

-- [T4] iot.parameter  ← ParameterEntity.java
CALL kubo_mt_add_column('parameter', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('parameter', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('parameter', 'idx_parameter_tenant', 'tenant_id');
CALL kubo_mt_replace_unique('parameter', 'code');  -- 原唯一列 code → (tenant_id, code)
CALL kubo_mt_replace_unique('parameter', 'label');  -- 原唯一列 label → (tenant_id, label)
UPDATE `parameter` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `parameter` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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

-- [T1] mes.picking  ← PickingEntity.java
CALL kubo_mt_add_column('picking', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('picking', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('picking', 'idx_picking_tenant', 'tenant_id');
-- 可用归属列：order_id, structure_id
CALL kubo_mt_replace_unique('picking', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `picking` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `picking` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] mes.pickingDetail  ← PickingDetailEntity.java
CALL kubo_mt_add_column('picking_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('picking_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('picking_detail', 'idx_picking_detail_tenant', 'tenant_id');
-- 可用归属列：material_id
UPDATE `picking_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `picking_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] mes.plan  ← PlanEntity.java
CALL kubo_mt_add_column('plan', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('plan', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('plan', 'idx_plan_tenant', 'tenant_id');
-- 可用归属列：customer_id
CALL kubo_mt_replace_unique('plan', 'bill_code');  -- 原唯一列 bill_code → (tenant_id, bill_code)
UPDATE `plan` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `plan` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T2] mes.planDetail  ← PlanDetailEntity.java
CALL kubo_mt_add_column('plan_detail', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('plan_detail', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('plan_detail', 'idx_plan_detail_tenant', 'tenant_id');
-- 可用归属列：material_id
UPDATE `plan_detail` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `plan_detail` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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
UPDATE `room` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `room` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T1] mes.routing  ← RoutingEntity.java
CALL kubo_mt_add_column('routing', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('routing', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('routing', 'idx_routing_tenant', 'tenant_id');
-- 可用归属列：bom_id, material_id
CALL kubo_mt_replace_unique('routing', 'code');  -- 原唯一列 code → (tenant_id, code)
UPDATE `routing` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `routing` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- [T4] mes.routingOperation  ← RoutingOperationEntity.java
CALL kubo_mt_add_column('routing_operation', 'tenant_id', 'tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''租户ID''');
CALL kubo_mt_add_column('routing_operation', 'creator_id', 'creator_id BIGINT UNSIGNED NULL COMMENT ''创建人（owner 维度）''');
CALL kubo_mt_add_index('routing_operation', 'idx_routing_operation_tenant', 'tenant_id');
-- 可用归属列：bom_id, operation_id, routing_id
UPDATE `routing_operation` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `routing_operation` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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
UPDATE `user_third_login` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `user_third_login` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

-- ---------------------------------------------------------------------------
-- 3. 执行后校验（任一非 0 都不得开启 enforce；建议做成 CI 检查）
-- ---------------------------------------------------------------------------
SELECT 'bom' tbl, COUNT(*) bad_tenant FROM `bom` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'bom_detail' tbl, COUNT(*) bad_tenant FROM `bom_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'contract' tbl, COUNT(*) bad_tenant FROM `contract` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'contract_document' tbl, COUNT(*) bad_tenant FROM `contract_document` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'customer' tbl, COUNT(*) bad_tenant FROM `customer` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'department' tbl, COUNT(*) bad_tenant FROM `department` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'device' tbl, COUNT(*) bad_tenant FROM `device` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input' tbl, COUNT(*) bad_tenant FROM `input` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input_detail' tbl, COUNT(*) bad_tenant FROM `input_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'inventory' tbl, COUNT(*) bad_tenant FROM `inventory` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'material' tbl, COUNT(*) bad_tenant FROM `material` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'member' tbl, COUNT(*) bad_tenant FROM `member` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'move' tbl, COUNT(*) bad_tenant FROM `move` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'move_detail' tbl, COUNT(*) bad_tenant FROM `move_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'operation' tbl, COUNT(*) bad_tenant FROM `operation` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'order_detail' tbl, COUNT(*) bad_tenant FROM `order_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'orders' tbl, COUNT(*) bad_tenant FROM `orders` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output' tbl, COUNT(*) bad_tenant FROM `output` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output_detail' tbl, COUNT(*) bad_tenant FROM `output_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'parameter' tbl, COUNT(*) bad_tenant FROM `parameter` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'participant' tbl, COUNT(*) bad_tenant FROM `participant` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'personal_token' tbl, COUNT(*) bad_tenant FROM `personal_token` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'picking' tbl, COUNT(*) bad_tenant FROM `picking` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'picking_detail' tbl, COUNT(*) bad_tenant FROM `picking_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'plan' tbl, COUNT(*) bad_tenant FROM `plan` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'plan_detail' tbl, COUNT(*) bad_tenant FROM `plan_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase' tbl, COUNT(*) bad_tenant FROM `purchase` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_detail' tbl, COUNT(*) bad_tenant FROM `purchase_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_price' tbl, COUNT(*) bad_tenant FROM `purchase_price` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'room' tbl, COUNT(*) bad_tenant FROM `room` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'routing' tbl, COUNT(*) bad_tenant FROM `routing` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'routing_operation' tbl, COUNT(*) bad_tenant FROM `routing_operation` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'sale' tbl, COUNT(*) bad_tenant FROM `sale` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'sale_detail' tbl, COUNT(*) bad_tenant FROM `sale_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'sale_price' tbl, COUNT(*) bad_tenant FROM `sale_price` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'storage' tbl, COUNT(*) bad_tenant FROM `storage` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'structure' tbl, COUNT(*) bad_tenant FROM `structure` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'supplier' tbl, COUNT(*) bad_tenant FROM `supplier` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'unit' tbl, COUNT(*) bad_tenant FROM `unit` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'user' tbl, COUNT(*) bad_tenant FROM `user` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'user_third_login' tbl, COUNT(*) bad_tenant FROM `user_third_login` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;

-- ---------------------------------------------------------------------------
-- 4. 回滚
--    · 数据：UPDATE <表> SET tenant_id = 1;
--    · 能力：kubo.data-permission.mode = shadow|off（应用侧开关，不依赖本脚本）
--    · 索引：ALTER TABLE <表> DROP INDEX uk_<表>_tenant_<col>; 仅在确认无跨租户重复后重建单列唯一
--    · 严禁在 ddl-auto=validate 环境 DROP COLUMN（会让启动失败）
-- ---------------------------------------------------------------------------

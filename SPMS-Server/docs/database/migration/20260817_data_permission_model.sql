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

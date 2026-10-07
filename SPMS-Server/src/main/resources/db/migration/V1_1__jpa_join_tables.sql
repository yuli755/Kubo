-- =====================================================================
-- Flyway V1.1 · JPA 多对多连接表前置
--
-- V2 数据权限迁移需要 role_permission_list / user_role_list 在执行时已存在，
-- 但 Flyway 迁移发生在生产 ddl-auto=validate 之前，不能依赖 Hibernate 事后建表。
-- 本迁移放在 V1 与 V2 之间，先建立所有当前实体声明的连接表，避免全新库
-- 在 V2 阶段失败，也让生产 validate 与本地 staging 使用同一份 schema 契约。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `role_menu_list` (
    `role_entity_id` BIGINT UNSIGNED NOT NULL,
    `menu_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`role_entity_id`, `menu_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `role_permission_list` (
    `role_entity_id` BIGINT UNSIGNED NOT NULL,
    `permission_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`role_entity_id`, `permission_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_role_list` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL,
    `role_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`user_entity_id`, `role_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_department_list` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL,
    `department_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`user_entity_id`, `department_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_storage_list` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL,
    `storage_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`user_entity_id`, `storage_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `contract_document_list` (
    `contract_entity_id` BIGINT UNSIGNED NOT NULL,
    `document_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`contract_entity_id`, `document_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `contract_participant_list` (
    `contract_entity_id` BIGINT UNSIGNED NOT NULL,
    `participant_list_id` BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (`contract_entity_id`, `participant_list_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- Kubo 库 · 唯一结构脚本（init.sql）
-- ============================================================================
-- 【规则】全仓只有两个数据库脚本：本文件（结构）+ init_data.sql（数据）。
--         以后任何建表 / 加列 / 改索引都在这两个文件里改，不再新增迁移文件，
--         也不再引入迁移框架（Flyway 已在本批退场：它的版本化文件与"只有两个
--         文件"直接冲突）。
-- 【幂等】每条语句都可以重复执行：建表一律带 IF NOT EXISTS 前缀。
-- 【怎么执行】
--     docker compose up mysql          # compose 已把本目录挂进
--                                      # /docker-entrypoint-initdb.d，首次启动自动建库
--     手动（已有库）：
--         mysql -uroot -p kubo < infra/init-sql/init.sql
--         mysql -uroot -p kubo < infra/init-sql/init_data.sql
-- 【判据】三处机器判据盯着本文件，改错了当场红：
--     pnpm ddl:check       结构 ↔ @Entity 列漂移（新表/新列必须两边同时改）
--     pnpm migration:check MySQL 8 方言 + 幂等写法（禁 DELIMITER / 存储过程 / H2 专有语法）
--     npx vitest run src/config/__tests__/schemaContract.spec.ts
--                          实体 ↔ 本文件（列名双向一致）+ "目录里恰好两个 .sql"
-- 【事实源】实体（src/main/java/**/XxxEntity.java）是列的事实源；两边一致才绿。
-- 【历史】本文件 = 2026-09 的 S0 基线（V1）折叠了 V2..V10 的全部结构变更：
--         补 tenant_id/creator_id 列（contract_document / participant /
--         personal_token / unit）、补 28 个索引、把 20 个单列唯一键升级为
--         (tenant_id, 列) 复合唯一键（V2/V4）、operation_log 的时间索引（V3）、
--         file.creator_id 索引（V5）。数据类变更在 init_data.sql。
-- ============================================================================

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `coderule` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `rule_field` int UNSIGNED default 0 comment '规则字段' UNIQUE,
    `sn_type` int UNSIGNED default 1 comment '流水号更新方式',
    `sn_length` int UNSIGNED default 4 comment '流水号起始长度',
    `prefix` varchar(255) default '' comment '编码前缀',
    `template` varchar(255) default '' comment '编码规则模板',
    `current_sn` int UNSIGNED default 0 comment '当前序列号',
    `current_year` int UNSIGNED default 0 comment '重置年',
    `current_month` int UNSIGNED default 0 comment '重置月',
    `current_day` int UNSIGNED default 0 comment '重置日',
    `is_system` bit(1) default 0 comment '是否内置参数',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'coderule';

CREATE TABLE IF NOT EXISTS `config` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `flag` varchar(255) comment '规则字段' UNIQUE,
    `name` varchar(255) default '' comment '配置名称',
    `description` varchar(255) default '' comment '配置描述',
    `config` varchar(255) default '' comment '字符串值',
    `type` int UNSIGNED default 0 comment '配置类型',
    `is_system` bit(1) default 0 comment '内置配置',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'config';

CREATE TABLE IF NOT EXISTS `contract` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `code` varchar(255) default '' comment '合同编码',
    `name` varchar(255) default '' comment '合同名称',
    `content` text comment '合同内容',
    `start_time` bigint UNSIGNED default 0 comment '开始时间',
    `end_time` bigint UNSIGNED default 0 comment '结束时间',
    `type` bigint UNSIGNED default 0 comment '合同类型',
    `status` bigint UNSIGNED default 1 comment '合同状态',
    KEY `idx_contract_tenant` (`tenant_id`),
    UNIQUE KEY `uk_contract_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'contract';

CREATE TABLE IF NOT EXISTS `contract_document` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `url` varchar(255) default '' comment '附件地址',
    `name` varchar(255) default '' comment '名称',
    `extension` varchar(255) default '' comment '扩展名',
    `hash_md5` varchar(255) default '' comment 'MD5',
    `size` bigint UNSIGNED default 0 comment '文件大小',
    tenant_id bigint UNSIGNED NOT null DEFAULT 1 COMMENT '租户ID',
    creator_id bigint UNSIGNED null COMMENT '创建人（owner 维度）',
    KEY `idx_contract_document_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'contract_document';

CREATE TABLE IF NOT EXISTS `customer` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `name` varchar(255) default '' comment '客户名称',
    `code` varchar(255) default '' comment '客户编码',
    `phone` varchar(255) default '' comment '联系电话',
    KEY `idx_customer_tenant` (`tenant_id`),
    UNIQUE KEY `uk_customer_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'customer';

CREATE TABLE IF NOT EXISTS `data_field_policy` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_id` bigint UNSIGNED,
    `field_name` varchar(255),
    `role_code` varchar(255),
    `action_name` varchar(255),
    `mask_type` varchar(255),
    `mask_expression` varchar(255),
    `allow_plaintext` tinyint(1),
    `priority` int UNSIGNED,
    `enabled` tinyint(1),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_data_field_policy` UNIQUE (`resource_id`, `field_name`, `role_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_field_policy';

CREATE TABLE IF NOT EXISTS `data_permission_deny_log` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_key` varchar(255),
    `reason` varchar(255),
    `mode` varchar(255),
    `user_id` bigint UNSIGNED,
    `tenant_id` bigint UNSIGNED,
    `detail` text,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_permission_deny_log';

CREATE TABLE IF NOT EXISTS `data_permission_diff_log` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_key` varchar(255),
    `action_name` varchar(255),
    `trace_id` varchar(255),
    `user_id` bigint UNSIGNED,
    `tenant_id` bigint UNSIGNED,
    `baseline_count` bigint UNSIGNED,
    `guarded_count` bigint UNSIGNED,
    `verdict` varchar(255),
    `cost_ms` bigint UNSIGNED,
    `detail` text,
    KEY `idx_dp_diff_create_time` (`create_time`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_permission_diff_log';

CREATE TABLE IF NOT EXISTS `data_permission_orm_parity_log` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_key` varchar(255),
    `action_name` varchar(255),
    `table_name` varchar(255),
    `user_id` bigint UNSIGNED,
    `tenant_id` bigint UNSIGNED,
    `jpa_count` bigint UNSIGNED,
    `mapper_count` bigint UNSIGNED,
    `verdict` varchar(255),
    `fragment_sql` varchar(255),
    `cost_ms` bigint UNSIGNED,
    `detail` text,
    KEY `idx_dp_parity_create_time` (`create_time`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_permission_orm_parity_log';

CREATE TABLE IF NOT EXISTS `data_policy` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `policy_code` varchar(255),
    `resource_id` bigint UNSIGNED,
    `subject_type` varchar(255),
    `subject_id` bigint UNSIGNED,
    `role_code` varchar(255),
    `action_name` varchar(255),
    `condition_json` json,
    `priority` int UNSIGNED,
    `enabled` tinyint(1),
    `version_no` bigint UNSIGNED,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_data_policy_code` UNIQUE (`policy_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_policy';

CREATE TABLE IF NOT EXISTS `data_resource` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_key` varchar(255),
    `table_name` varchar(255),
    `table_alias` varchar(255),
    `tenant_column` varchar(255),
    `owner_columns` varchar(255),
    `supported_actions` varchar(255),
    `filterable_columns` varchar(255),
    `status` varchar(255),
    `version_no` bigint UNSIGNED,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_data_resource_key` UNIQUE (`resource_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'data_resource';

CREATE TABLE IF NOT EXISTS `department` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `code` varchar(255) default '' comment 'code',
    `name` varchar(255) default '' comment '部门名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    `order_no` int UNSIGNED default 0 comment '排序号',
    KEY `idx_department_tenant` (`tenant_id`),
    UNIQUE KEY `uk_department_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'department';

CREATE TABLE IF NOT EXISTS `file` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `name` varchar(255) default '' comment '名称',
    `extension` varchar(255) default '' comment '扩展名',
    `url` varchar(255) default '' comment '文件路径',
    `platform` int UNSIGNED default 0 comment '存储平台',
    `category` int UNSIGNED default 1 comment '文件类别',
    `hash_md5` varchar(255) default '' comment 'MD5',
    `size` bigint UNSIGNED default 0 comment '文件大小',
    `creator_id` bigint UNSIGNED default null comment '上传人ID',
    KEY `idx_file_creator` (`creator_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'file';

CREATE TABLE IF NOT EXISTS `input` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `reject_reason` varchar(255) default '' comment '驳回原因',
    `bill_code` varchar(255) default '' comment '入库单号',
    `status` int UNSIGNED default 1 comment '入库状态',
    `type` int UNSIGNED default 1 comment '入库类型',
    `purchase_id` bigint UNSIGNED DEFAULT 0,
    `structure_id` bigint UNSIGNED DEFAULT 0,
    `move_id` bigint UNSIGNED DEFAULT 0,
    KEY `idx_input_tenant` (`tenant_id`),
    UNIQUE KEY `uk_input_tenant_bill_code` (`tenant_id`, `bill_code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'input';

CREATE TABLE IF NOT EXISTS `input_detail` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `bill_id` bigint UNSIGNED DEFAULT 0,
    `is_finished` tinyint(1) DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '入库数量',
    `finish_quantity` double(20, 6) UNSIGNED default 0 comment '已入库数量',
    KEY `idx_input_detail_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'input_detail';

CREATE TABLE IF NOT EXISTS `inventory` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '库存数量',
    `type` int UNSIGNED default 1 comment '存储类型',
    `material_id` bigint UNSIGNED DEFAULT 0,
    `storage_id` bigint UNSIGNED DEFAULT 0,
    `structure_id` bigint UNSIGNED DEFAULT 0,
    KEY `idx_inventory_tenant` (`tenant_id`),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_inventory_material_storage` UNIQUE (`material_id`, `storage_id`),
    CONSTRAINT `uk_inventory_material_structure` UNIQUE (`material_id`, `structure_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'inventory';

CREATE TABLE IF NOT EXISTS `material` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `name` varchar(255) default '' comment '物料名称',
    `code` varchar(255) default '' comment '物料编码',
    `spc` varchar(255) default '' comment '规格型号',
    `material_type` bigint UNSIGNED default 1 comment '物料类型',
    `use_type` bigint UNSIGNED default 1 comment '使用方式',
    `purchase_price` double(20, 6) UNSIGNED default 0 comment '采购标准价',
    `sale_price` double(20, 6) UNSIGNED default 0 comment '销售标准价',
    KEY `idx_material_tenant` (`tenant_id`),
    UNIQUE KEY `uk_material_tenant_code` (`tenant_id`, `code`),
    UNIQUE KEY `uk_material_tenant_name` (`tenant_id`, `name`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'material';

CREATE TABLE IF NOT EXISTS `menu` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `name` varchar(255) default '' comment '名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    `path` varchar(255) default '' comment '菜单路径',
    `component` varchar(255) default '' comment '组件路径',
    `icon` varchar(255) default '' comment '菜单图标',
    `order_no` int UNSIGNED default 0 comment '排序号',
    `permission` varchar(255) default '' comment '权限标识，如 Material_getPage；非空时前端路由守卫将要求当前用户拥有该权限',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'menu';

CREATE TABLE IF NOT EXISTS `move` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `reject_reason` varchar(255) default '' comment '驳回原因',
    `bill_code` varchar(255) default '' comment '移库单号',
    `status` int UNSIGNED default 1 comment '移库状态',
    KEY `idx_move_tenant` (`tenant_id`),
    UNIQUE KEY `uk_move_tenant_bill_code` (`tenant_id`, `bill_code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'move';

CREATE TABLE IF NOT EXISTS `move_detail` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `bill_id` bigint UNSIGNED DEFAULT 0,
    `is_finished` tinyint(1) DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '移动数量',
    `finish_quantity` double(20, 6) UNSIGNED default 0 comment '已移动数量',
    KEY `idx_move_detail_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'move_detail';

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `resource_key` varchar(255),
    `action` varchar(255),
    `user_id` bigint UNSIGNED,
    `tenant_id` bigint UNSIGNED,
    `target_id` bigint UNSIGNED,
    `target_label` varchar(255),
    `before_snapshot` text,
    `after_snapshot` text,
    KEY `idx_operation_log_resource_time` (`resource_key`, `create_time`),
    KEY `idx_operation_log_create_time` (`create_time`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'operation_log';

CREATE TABLE IF NOT EXISTS `output` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `reject_reason` varchar(255) default '' comment '驳回原因',
    `bill_code` varchar(255) default '' comment '出库单号',
    `status` int UNSIGNED default 1 comment '出库状态',
    `type` int UNSIGNED default 1 comment '出库类型',
    `sale_id` bigint UNSIGNED DEFAULT 0,
    `move_id` bigint UNSIGNED DEFAULT 0,
    `picking_id` bigint UNSIGNED DEFAULT 0,
    KEY `idx_output_tenant` (`tenant_id`),
    UNIQUE KEY `uk_output_tenant_bill_code` (`tenant_id`, `bill_code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'output';

CREATE TABLE IF NOT EXISTS `output_detail` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `bill_id` bigint UNSIGNED DEFAULT 0,
    `is_finished` tinyint(1) DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '出库数量',
    `finish_quantity` double(20, 6) UNSIGNED default 0 comment '已出库数量',
    KEY `idx_output_detail_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'output_detail';

CREATE TABLE IF NOT EXISTS `participant` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `name` varchar(255) default '' comment '参与方名称',
    `phone` varchar(255) default '' comment '联系电话',
    `email` varchar(255) default '' comment '电子邮箱',
    `identification` varchar(255) default '' comment '证件号',
    `type` bigint UNSIGNED default 0 comment '身份类型',
    `role` bigint UNSIGNED default 1 comment '参与人角色',
    `certificate_type` bigint UNSIGNED default 0 comment '证件类型',
    tenant_id bigint UNSIGNED NOT null DEFAULT 1 COMMENT '租户ID',
    creator_id bigint UNSIGNED null COMMENT '创建人（owner 维度）',
    KEY `idx_participant_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'participant';

CREATE TABLE IF NOT EXISTS `permission` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `identity` varchar(255) default '' comment '权限标识' UNIQUE,
    `is_system` bit(1) default 0 comment '系统权限',
    `type` int UNSIGNED default 0 comment '权限类型',
    `name` varchar(255) default '' comment '名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'permission';

CREATE TABLE IF NOT EXISTS `permission_change_outbox` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `event_key` varchar(255),
    `aggregate_type` varchar(255),
    `aggregate_id` bigint UNSIGNED,
    `event_type` varchar(255),
    `payload` json,
    `status` varchar(255),
    `retry_count` int UNSIGNED,
    `next_retry_at` bigint UNSIGNED,
    `processed_at` bigint UNSIGNED,
    `last_error` varchar(255),
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_permission_outbox_event` UNIQUE (`event_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'permission_change_outbox';

CREATE TABLE IF NOT EXISTS `personal_token` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `name` varchar(255) default '' comment '令牌名称',
    `token` varchar(255) default '' comment '令牌',
    `user_id` bigint UNSIGNED DEFAULT 0,
    tenant_id bigint UNSIGNED NOT null DEFAULT 1 COMMENT '租户ID',
    creator_id bigint UNSIGNED null COMMENT '创建人（owner 维度）',
    KEY `idx_personal_token_tenant` (`tenant_id`),
    UNIQUE KEY `uk_personal_token_tenant_token` (`tenant_id`, `token`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'personal_token';

CREATE TABLE IF NOT EXISTS `purchase` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `reject_reason` varchar(255) default '' comment '驳回原因',
    `bill_code` varchar(255) default '' comment '采购单号',
    `reason` varchar(255) default '' comment '采购事由',
    `total_price` double(20, 6) UNSIGNED default 0 comment '总金额',
    `total_real_price` double(20, 6) UNSIGNED default 0 comment '实际金额',
    `status` int UNSIGNED default 1 comment '采购状态',
    KEY `idx_purchase_tenant` (`tenant_id`),
    UNIQUE KEY `uk_purchase_tenant_bill_code` (`tenant_id`, `bill_code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'purchase';

CREATE TABLE IF NOT EXISTS `purchase_detail` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `bill_id` bigint UNSIGNED DEFAULT 0,
    `is_finished` tinyint(1) DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '采购数量',
    `price` double(20, 6) UNSIGNED default 0 comment '采购单价',
    `finish_quantity` double(20, 6) UNSIGNED default 0 comment '已入库数量',
    KEY `idx_purchase_detail_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'purchase_detail';

CREATE TABLE IF NOT EXISTS `purchase_price` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `price` double(20, 6) UNSIGNED default 0 comment '采购单价',
    KEY `idx_purchase_price_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'purchase_price';

CREATE TABLE IF NOT EXISTS `role` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `name` varchar(255) default '' comment '角色名称',
    `code` varchar(255) default '' comment '角色编码',
    UNIQUE KEY `uk_role_tenant_code` (`tenant_id`, `code`),
    UNIQUE KEY `uk_role_tenant_name` (`tenant_id`, `name`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'role';

CREATE TABLE IF NOT EXISTS `sale` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `reject_reason` varchar(255) default '' comment '驳回原因',
    `bill_code` varchar(255) default '' comment '销售单号',
    `reason` varchar(255) default '' comment '销售说明',
    `total_price` double(20, 6) UNSIGNED default 0 comment '总金额',
    `status` int UNSIGNED default 1 comment '销售状态',
    KEY `idx_sale_tenant` (`tenant_id`),
    UNIQUE KEY `uk_sale_tenant_bill_code` (`tenant_id`, `bill_code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'sale';

CREATE TABLE IF NOT EXISTS `sale_detail` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `bill_id` bigint UNSIGNED DEFAULT 0,
    `is_finished` tinyint(1) DEFAULT 0,
    `quantity` double(20, 6) UNSIGNED default 0 comment '销售数量',
    `price` double(20, 6) UNSIGNED default 0 comment '销售单价',
    `finish_quantity` double(20, 6) UNSIGNED default 0 comment '已出库数量',
    KEY `idx_sale_detail_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'sale_detail';

CREATE TABLE IF NOT EXISTS `sale_price` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `price` double(20, 6) UNSIGNED default 0 comment '销售单价',
    KEY `idx_sale_price_tenant` (`tenant_id`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'sale_price';

CREATE TABLE IF NOT EXISTS `storage` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `code` varchar(255) default '' comment '仓库编码',
    `name` varchar(255) default '' comment '名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    KEY `idx_storage_tenant` (`tenant_id`),
    UNIQUE KEY `uk_storage_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'storage';

CREATE TABLE IF NOT EXISTS `structure` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `code` varchar(255) default '' comment '生产单元编码',
    `name` varchar(255) default '' comment '名称',
    `type` bigint UNSIGNED default 1 comment '生产单元类型',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    KEY `idx_structure_tenant` (`tenant_id`),
    UNIQUE KEY `uk_structure_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'structure';

CREATE TABLE IF NOT EXISTS `supplier` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `name` varchar(255) default '' comment '供应商名称',
    `code` varchar(255) default '' comment '供应商编码',
    `phone` varchar(255) default '' comment '联系电话',
    KEY `idx_supplier_tenant` (`tenant_id`),
    UNIQUE KEY `uk_supplier_tenant_code` (`tenant_id`, `code`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'supplier';

CREATE TABLE IF NOT EXISTS `tenant` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `code` varchar(255),
    `name` varchar(255),
    `status` int UNSIGNED default 1 comment '1=启用 0=停用',
    `expire_time` bigint UNSIGNED,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_tenant_code` UNIQUE (`code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'tenant';

CREATE TABLE IF NOT EXISTS `unit` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `name` varchar(255) default '' comment '单位名称',
    `code` varchar(255) default '' comment '单位编码',
    tenant_id bigint UNSIGNED NOT null DEFAULT 1 COMMENT '租户ID',
    creator_id bigint UNSIGNED null COMMENT '创建人（owner 维度）',
    KEY `idx_unit_tenant` (`tenant_id`),
    UNIQUE KEY `uk_unit_tenant_code` (`tenant_id`, `code`),
    UNIQUE KEY `uk_unit_tenant_name` (`tenant_id`, `name`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'unit';

CREATE TABLE IF NOT EXISTS `user` (
    `id` bigint UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `update_time` bigint UNSIGNED DEFAULT 0 NOT NULL,
    `is_disabled` tinyint(1) DEFAULT 0 NOT NULL,
    `is_published` tinyint(1) DEFAULT 0 NOT NULL,
    `tenant_id` bigint UNSIGNED DEFAULT 0,
    `creator_id` bigint UNSIGNED DEFAULT 0,
    `nickname` varchar(255) default '' comment '昵称',
    `avatar` varchar(255) default '' comment '头像',
    `real_name` varchar(255) default '' comment '真实姓名',
    `id_card` varchar(255) default '' comment '身份证号',
    `email` varchar(255) comment '邮箱',
    `phone` varchar(255) comment '手机号',
    `gender` int UNSIGNED default 0 comment '性别',
    `password` varchar(255) default '' comment '密码',
    `salt` varchar(255) default '' comment '密码盐',
    KEY `idx_user_tenant` (`tenant_id`),
    UNIQUE KEY `uk_user_tenant_email` (`tenant_id`, `email`),
    UNIQUE KEY `uk_user_tenant_phone` (`tenant_id`, `phone`),
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'user';


-- ============================================================================
-- Hibernate 隐式命名策略生成的关联表 + 显式 @JoinTable
-- ----------------------------------------------------------------------------
-- 这些表没有独立实体（是 @ManyToMany / @JoinTable 的承载表），历史上由
-- `ddl-auto: update` 建出来，因此**不在 S0 基线里**。既然"只有两个脚本"是新的
-- 唯一口径，它们必须显式建在这里，否则新环境起库后 JPA 校验/关联读写会缺表。
-- 列名取 Hibernate 的隐式约定（<属主表>_<字段名>）：
--   User.roleList       → user_role_list        (user_entity_id, role_list_id)
--   User.departmentList → user_department_list  (user_entity_id, department_list_id)
--   User.storageList    → user_storage_list     (user_entity_id, storage_list_id)
--   User.factoryList    → user_factory          （显式 @JoinTable，列名 factory_id）
--   Role.menuList       → role_menu_list        (role_entity_id, menu_list_id)
--   Role.permissionList → role_permission_list  (role_entity_id, permission_list_id)
--   Contract.documentList    → contract_document_list    (contract_entity_id, document_list_id)
--   Contract.participantList → contract_participant_list (contract_entity_id, participant_list_id)
-- 若你的真库上列名与此不同，以真库为准改这里（这张表只由 ORM 读写，没有 DTO 依赖列名）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_role_list` (
    `user_entity_id` bigint UNSIGNED NOT NULL COMMENT '用户 ID',
    `role_list_id` bigint UNSIGNED NOT NULL COMMENT '角色 ID',
    PRIMARY KEY (`user_entity_id`, `role_list_id`),
    KEY `idx_user_role_list_role` (`role_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户 ↔ 角色';

CREATE TABLE IF NOT EXISTS `user_department_list` (
    `user_entity_id` bigint UNSIGNED NOT NULL COMMENT '用户 ID',
    `department_list_id` bigint UNSIGNED NOT NULL COMMENT '部门 ID',
    PRIMARY KEY (`user_entity_id`, `department_list_id`),
    KEY `idx_user_department_list_department` (`department_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户 ↔ 部门';

CREATE TABLE IF NOT EXISTS `user_storage_list` (
    `user_entity_id` bigint UNSIGNED NOT NULL COMMENT '用户 ID',
    `storage_list_id` bigint UNSIGNED NOT NULL COMMENT '仓库 ID',
    PRIMARY KEY (`user_entity_id`, `storage_list_id`),
    KEY `idx_user_storage_list_storage` (`storage_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户 ↔ 仓库授权';

CREATE TABLE IF NOT EXISTS `role_menu_list` (
    `role_entity_id` bigint UNSIGNED NOT NULL COMMENT '角色 ID',
    `menu_list_id` bigint UNSIGNED NOT NULL COMMENT '菜单 ID',
    PRIMARY KEY (`role_entity_id`, `menu_list_id`),
    KEY `idx_role_menu_list_menu` (`menu_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色 ↔ 菜单';

CREATE TABLE IF NOT EXISTS `role_permission_list` (
    `role_entity_id` bigint UNSIGNED NOT NULL COMMENT '角色 ID',
    `permission_list_id` bigint UNSIGNED NOT NULL COMMENT '权限 ID',
    PRIMARY KEY (`role_entity_id`, `permission_list_id`),
    KEY `idx_role_permission_list_permission` (`permission_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色 ↔ 权限';

CREATE TABLE IF NOT EXISTS `contract_document_list` (
    `contract_entity_id` bigint UNSIGNED NOT NULL COMMENT '合同 ID',
    `document_list_id` bigint UNSIGNED NOT NULL COMMENT '附件 ID',
    PRIMARY KEY (`contract_entity_id`, `document_list_id`),
    KEY `idx_contract_document_list_document` (`document_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '合同 ↔ 附件';

CREATE TABLE IF NOT EXISTS `contract_participant_list` (
    `contract_entity_id` bigint UNSIGNED NOT NULL COMMENT '合同 ID',
    `participant_list_id` bigint UNSIGNED NOT NULL COMMENT '参与方 ID',
    PRIMARY KEY (`contract_entity_id`, `participant_list_id`),
    KEY `idx_contract_participant_list_participant` (`participant_list_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '合同 ↔ 参与方';

-- 多租户：用户可访问的租户集合（V2 原始 DDL，逐字保留）
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

-- 用户可访问的工厂集合（D13 工厂维度；显式 @JoinTable）
CREATE TABLE IF NOT EXISTS `user_factory` (
    `user_entity_id` BIGINT UNSIGNED NOT NULL COMMENT '用户 ID（UserEntity.id）',
    `factory_id` BIGINT UNSIGNED NOT NULL COMMENT '工厂 ID（storage 树节点 id，通常为顶层节点）',
    PRIMARY KEY (`user_entity_id`, `factory_id`),
    KEY `idx_user_factory_factory` (`factory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户可访问的工厂集合（D13 工厂维度）';

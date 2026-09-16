-- =====================================================================
-- Flyway V1 · 基线 schema（由实体定义生成，勿手改）
--
-- 生成：node scripts/gen-flyway-baseline.mjs --write
-- 事实源：src/main/java 下 40 个 @Table 实体 + 4 个 @MappedSuperclass 基类
-- 生成器：scripts/gen-flyway-baseline.mjs（带 --check 与真库对账）
--
-- ## 与存量库的关系
-- 存量库（开发/生产）在 Flyway 里以 `baseline-on-migrate` 记为 **baseline 1**，
-- 本文件**不会**被执行；只有全新环境才会跑它。差异用 mysqldump 对账：
--     mysqldump --no-data --skip-comments --compact kubo > /tmp/real.sql
--     node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
--
-- ## 为什么不是 mysqldump 产物
-- 因为仓库里没有可信的完整建表脚本，mysqldump 需要一台真库；实体是随代码走的，
-- 生成器让"改实体忘了改 schema"当场暴露（CI 里由 schemaContract.spec.ts 守）。
-- =====================================================================

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
    `code` varchar(255) default '' comment '合同编码' UNIQUE,
    `name` varchar(255) default '' comment '合同名称',
    `content` text comment '合同内容',
    `start_time` bigint UNSIGNED default 0 comment '开始时间',
    `end_time` bigint UNSIGNED default 0 comment '结束时间',
    `type` bigint UNSIGNED default 0 comment '合同类型',
    `status` bigint UNSIGNED default 1 comment '合同状态',
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
    `code` varchar(255) default '' comment '客户编码' UNIQUE,
    `phone` varchar(255) default '' comment '联系电话',
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
    `code` varchar(255) default '' comment 'code' UNIQUE,
    `name` varchar(255) default '' comment '部门名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
    `order_no` int UNSIGNED default 0 comment '排序号',
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
    `bill_code` varchar(255) default '' comment '入库单号' UNIQUE,
    `status` int UNSIGNED default 1 comment '入库状态',
    `type` int UNSIGNED default 1 comment '入库类型',
    `purchase_id` bigint UNSIGNED DEFAULT 0,
    `structure_id` bigint UNSIGNED DEFAULT 0,
    `move_id` bigint UNSIGNED DEFAULT 0,
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
    `name` varchar(255) default '' comment '物料名称' UNIQUE,
    `code` varchar(255) default '' comment '物料编码' UNIQUE,
    `spc` varchar(255) default '' comment '规格型号',
    `material_type` bigint UNSIGNED default 1 comment '物料类型',
    `use_type` bigint UNSIGNED default 1 comment '使用方式',
    `purchase_price` double(20, 6) UNSIGNED default 0 comment '采购标准价',
    `sale_price` double(20, 6) UNSIGNED default 0 comment '销售标准价',
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
    `bill_code` varchar(255) default '' comment '移库单号' UNIQUE,
    `status` int UNSIGNED default 1 comment '移库状态',
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
    `bill_code` varchar(255) default '' comment '出库单号' UNIQUE,
    `status` int UNSIGNED default 1 comment '出库状态',
    `type` int UNSIGNED default 1 comment '出库类型',
    `sale_id` bigint UNSIGNED DEFAULT 0,
    `move_id` bigint UNSIGNED DEFAULT 0,
    `picking_id` bigint UNSIGNED DEFAULT 0,
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
    `token` varchar(255) default '' comment '令牌' UNIQUE,
    `user_id` bigint UNSIGNED DEFAULT 0,
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
    `bill_code` varchar(255) default '' comment '采购单号' UNIQUE,
    `reason` varchar(255) default '' comment '采购事由',
    `total_price` double(20, 6) UNSIGNED default 0 comment '总金额',
    `total_real_price` double(20, 6) UNSIGNED default 0 comment '实际金额',
    `status` int UNSIGNED default 1 comment '采购状态',
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
    `name` varchar(255) default '' comment '角色名称' UNIQUE,
    `code` varchar(255) default '' comment '角色编码' UNIQUE,
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
    `bill_code` varchar(255) default '' comment '销售单号' UNIQUE,
    `reason` varchar(255) default '' comment '销售说明',
    `total_price` double(20, 6) UNSIGNED default 0 comment '总金额',
    `status` int UNSIGNED default 1 comment '销售状态',
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
    `code` varchar(255) default '' comment '仓库编码' UNIQUE,
    `name` varchar(255) default '' comment '名称',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
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
    `code` varchar(255) default '' comment '生产单元编码' UNIQUE,
    `name` varchar(255) default '' comment '名称',
    `type` bigint UNSIGNED default 1 comment '生产单元类型',
    `parent_id` bigint UNSIGNED default 0 comment '父级ID',
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
    `code` varchar(255) default '' comment '供应商编码' UNIQUE,
    `phone` varchar(255) default '' comment '联系电话',
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
    `name` varchar(255) default '' comment '单位名称' UNIQUE,
    `code` varchar(255) default '' comment '单位编码' UNIQUE,
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
    `email` varchar(255) comment '邮箱' UNIQUE,
    `phone` varchar(255) comment '手机号' UNIQUE,
    `gender` int UNSIGNED default 0 comment '性别',
    `password` varchar(255) default '' comment '密码',
    `salt` varchar(255) default '' comment '密码盐',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'user';

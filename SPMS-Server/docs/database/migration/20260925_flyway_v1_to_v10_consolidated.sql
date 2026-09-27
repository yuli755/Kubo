-- =====================================================================
-- Kubo SPMS · Flyway V1 ~ V10 全量增量迁移整合脚本（权威整合版）
--
-- 生成日期：2026-09-25
-- 用途：生产环境/全新测试库一键建库、结构同步与基线校验
-- 执行方式：
--     mysql -u root -p kubo < SPMS-Server/docs/database/migration/20260925_flyway_v1_to_v10_consolidated.sql
--
-- 覆盖版本：
--     1. V1__baseline_schema.sql                      (基线结构: 41个实体表)
--     2. V2__data_permission_migrations.sql          (数据权限体系: 策略/资源/拦截器)
--     3. V3__operation_log.sql                        (操作审计日志表)
--     4. V4__role_tenant_backfill.sql                 (角色租户归属回填)
--     5. V5__file_creator_id.sql                      (文件创建人字段)
--     6. V6__menu_permission.sql                      (菜单与权限关联补全)
--     7. V7__system_permission_identities.sql         (系统内置权限标识标准化)
--     8. V8__user_email_phone_unique_null_default.sql (用户邮箱手机唯一性索引修复)
--     9. V9__inventory_dimension_unique.sql           (库存多维唯一约束强化)
--    10. V10__menu_canonical_paths.sql                (规范化菜单路由与层级对齐)
--
-- 幂等性保证：全部建表采用 IF NOT EXISTS；所有存储过程采用 DROP -> CREATE -> CALL -> DROP；
-- 插入语句带唯一判定或 NOT EXISTS 防重；兼容 MySQL 8.0+。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;


-- ---------------------------------------------------------------------
-- [阶段 1/10] V1 · 基线结构（41个实体表） (V1__baseline_schema.sql)
-- ---------------------------------------------------------------------

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


-- ---------------------------------------------------------------------
-- [阶段 2/10] V2 · 数据权限体系（策略/资源/拦截器） (V2__data_permission_migrations.sql)
-- ---------------------------------------------------------------------

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
    -- 这里原本 SIGNAL SQLSTATE 45000。CI 的 MySQL 8 job（跑真脚本、跑两遍）实测出它是有害的：
    -- 空库上会打出 64 条 ERROR 1644，而任何非 --force 的客户端在第一条就中止 ——
    -- 于是排在后面的 20260907（filterable_columns、data_permission_diff_log）永远建不出来，
    -- "权限内核表"会静默缺席。真正需要响的"表在但唯一键形状不对"仍由下面的 old_idx/新键分支处理。
    SET @s = NULL;
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
UPDATE `room` SET `tenant_id` = 1 WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
UPDATE `room` SET `creator_id` = 1 WHERE `creator_id` IS NULL;

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
SELECT 'contract' tbl, COUNT(*) bad_tenant FROM `contract` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'contract_document' tbl, COUNT(*) bad_tenant FROM `contract_document` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'customer' tbl, COUNT(*) bad_tenant FROM `customer` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'department' tbl, COUNT(*) bad_tenant FROM `department` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input' tbl, COUNT(*) bad_tenant FROM `input` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'input_detail' tbl, COUNT(*) bad_tenant FROM `input_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'inventory' tbl, COUNT(*) bad_tenant FROM `inventory` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'material' tbl, COUNT(*) bad_tenant FROM `material` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'member' tbl, COUNT(*) bad_tenant FROM `member` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'move' tbl, COUNT(*) bad_tenant FROM `move` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'move_detail' tbl, COUNT(*) bad_tenant FROM `move_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output' tbl, COUNT(*) bad_tenant FROM `output` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'output_detail' tbl, COUNT(*) bad_tenant FROM `output_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'participant' tbl, COUNT(*) bad_tenant FROM `participant` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'personal_token' tbl, COUNT(*) bad_tenant FROM `personal_token` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase' tbl, COUNT(*) bad_tenant FROM `purchase` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_detail' tbl, COUNT(*) bad_tenant FROM `purchase_detail` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'purchase_price' tbl, COUNT(*) bad_tenant FROM `purchase_price` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
SELECT 'room' tbl, COUNT(*) bad_tenant FROM `room` WHERE `tenant_id` IS NULL OR `tenant_id` = 0;
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


-- ---------------------------------------------------------------------
-- [阶段 3/10] V3 · 操作审计日志表 (V3__operation_log.sql)
-- ---------------------------------------------------------------------

-- ============================================================================
-- 通用操作审计日志（P3 · 2026-09-10）
-- ============================================================================
-- operation_log：记录「谁在何时对哪条业务记录做了什么（改前/改后）」。
--
-- 与 data_permission_deny_log 的边界（刻意写清，避免被当成同一张表）：
--   * deny 日志记「数据权限拒绝了什么」（拒绝事件，由 DataPermissionGuard/RowWriteGuard 落库）；
--   * 本表记「普通业务写操作改了什么」（CREATE/UPDATE/DELETE 轨迹，由 BaseService 的
--     afterAdd/afterUpdate/beforeDelete 钩子对 @OperationLog 标注的实体落库）。
--   两者互补：前者回答「能不能看/改」，后者回答「改没改、改了啥」。
--
-- 触发口径：
--   * 只对标记 @OperationLog 的实体生效（opt-in，避免全仓 40+ 资源无差别刷审计）；
--   * 身份（user_id/tenant_id）在业务线程取成值后异步落库（OperationLogRecorder），
--     业务回滚不吞证据、落库失败不影响业务结果。
--
-- 幂等：CREATE TABLE IF NOT EXISTS；重复执行无副作用（CI 连跑两遍判据）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `create_time` BIGINT NULL,
    `update_time` BIGINT NULL,
    `is_disabled` BIT(1) NOT NULL DEFAULT b'0',
    `is_published` BIT(1) NOT NULL DEFAULT b'0',
    `resource_key` VARCHAR(128) NOT NULL COMMENT '资源标识（如 asset.material）',
    `action` VARCHAR(32) NOT NULL COMMENT '动作 CREATE / UPDATE / DELETE',
    `user_id` BIGINT UNSIGNED NULL COMMENT '操作者用户',
    `tenant_id` BIGINT UNSIGNED NULL COMMENT '操作者租户',
    `target_id` BIGINT UNSIGNED NULL COMMENT '目标记录 ID',
    `target_label` VARCHAR(255) NULL COMMENT '目标记录摘要（编码/名称）',
    `before_snapshot` TEXT NULL COMMENT '变更前快照（JSON，只含标量字段）',
    `after_snapshot` TEXT NULL COMMENT '变更后快照（JSON，只含标量字段）',
    PRIMARY KEY (`id`),
    KEY `idx_operation_log_resource_time` (`resource_key`, `create_time`),
    KEY `idx_operation_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通用操作审计日志';


-- ---------------------------------------------------------------------
-- [阶段 4/10] V4 · 角色租户归属回填 (V4__role_tenant_backfill.sql)
-- ---------------------------------------------------------------------

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


-- ---------------------------------------------------------------------
-- [阶段 5/10] V5 · 文件创建人字段 (V5__file_creator_id.sql)
-- ---------------------------------------------------------------------

-- =====================================================================
-- 文件表属主列（阶段 D-4/D-5）
-- 问题：file 表没有任何属主信息——MD5 秒传跨用户返回他人文件行（泄露他人
--       文件名与上传存在性），file/delete 也无从校验归属（拿到 id 即可删）。
-- 方案：加 creator_id 列（上传时从登录态落库；匿名/历史行为 NULL），
--       秒传改为仅同属主复用，删除仅属主或 root 可执行。
-- 幂等：可重复执行；列已存在时跳过。
-- 关联实体：cn.hamm.spms.module.system.file.FileEntity
-- =====================================================================
DROP PROCEDURE IF EXISTS kubo_add_file_creator_id;
DELIMITER $$
CREATE PROCEDURE kubo_add_file_creator_id()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'file'
                     AND column_name = 'creator_id') THEN
        ALTER TABLE file ADD COLUMN creator_id bigint UNSIGNED DEFAULT NULL COMMENT '上传人ID';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'file'
                     AND index_name = 'idx_file_creator') THEN
        CREATE INDEX idx_file_creator ON file (creator_id);
    END IF;
END$$
DELIMITER ;
CALL kubo_add_file_creator_id();
DROP PROCEDURE IF EXISTS kubo_add_file_creator_id;


-- ---------------------------------------------------------------------
-- [阶段 6/10] V6 · 菜单与权限关联补全 (V6__menu_permission.sql)
-- ---------------------------------------------------------------------

-- =====================================================================
-- 菜单权限标识列（阶段 F-4）
-- 问题：前端路由守卫 permissionGuard 与 Console.vue 的 applyMenuPermissions
--       均已按 menu.permission → meta.permission 接通，但后端 menu 表没有
--       permission 列，权限与菜单是两套互不关联的数据——知道路径就能直接
--       访问任意已注册路由，菜单可见性形同虚设。
-- 方案：menu 表加 permission 列（默认空串 = 不校验，行为与存量完全一致；
--       非空时前端要求当前用户权限集合包含该标识才放行该路由）。
-- 幂等：可重复执行；列已存在时跳过。
-- 关联实体：cn.hamm.spms.module.system.menu.MenuEntity
-- =====================================================================
DROP PROCEDURE IF EXISTS kubo_add_menu_permission;
DELIMITER $$
CREATE PROCEDURE kubo_add_menu_permission()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'menu'
                     AND column_name = 'permission') THEN
        ALTER TABLE menu ADD COLUMN permission varchar(255) DEFAULT '' COMMENT '权限标识，如 Material_getPage；非空时前端路由守卫要求用户拥有该权限';
    END IF;
END$$
DELIMITER ;
CALL kubo_add_menu_permission();
DROP PROCEDURE IF EXISTS kubo_add_menu_permission;


-- ---------------------------------------------------------------------
-- [阶段 7/10] V7 · 系统内置权限标识标准化 (V7__system_permission_identities.sql)
-- ---------------------------------------------------------------------

-- 2026-09-14：补全 SystemPermissionIdentities 声明的全部系统权限身份。
--
-- 背景：20260909 合并迁移只种了 system:root 与 system:data-permission:bypass 两行，
-- 而 SystemPermissionIdentities 声明了四个身份。ownership（归属迁移）与 outbox（死信管理）
-- 没有权限行，管理端就无法把这两项特权授予任何非 root 角色 —— 两个特权端点只剩
-- isRootUser() 兜底一条路，"仅平台管理员可执行"退化成"仅 root 可执行"。
--
-- 幂等：与 20260909 相同的 INSERT ... WHERE NOT EXISTS 模式，可安全重放。
-- 授予关系：SYSTEM_ROOT 角色对 is_system 权限的绑定由合并迁移里的
-- role_permission_list CROSS JOIN（p.is_system = b'1'）覆盖；此处补行后该绑定
-- 对重放后的库自动生效。已执行过合并迁移且不会再重放的部署，如需把特权授予
-- 非 root「平台管理员」角色，请在执行本文件后于角色管理端自行勾选。

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:ownership', '迁移数据归属', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:ownership');

INSERT INTO `permission` (`identity`, `name`, `is_system`, `type`)
SELECT 'system:data-permission:outbox', '权限变更死信管理', b'1', 0
WHERE NOT EXISTS (SELECT 1 FROM `permission` WHERE `identity` = 'system:data-permission:outbox');

-- 把新增的两条 is_system 权限补授给已存在的 SYSTEM_ROOT 角色（与合并迁移的授权口径一致）
INSERT INTO `role_permission_list` (`role_entity_id`, `permission_list_id`)
SELECT r.`id`, p.`id`
FROM `role` r
CROSS JOIN `permission` p
WHERE r.`code` = 'SYSTEM_ROOT'
  AND p.`identity` IN ('system:data-permission:ownership', 'system:data-permission:outbox')
  AND NOT EXISTS (
      SELECT 1
      FROM `role_permission_list` x
      WHERE x.`role_entity_id` = r.`id`
        AND x.`permission_list_id` = p.`id`
  );


-- ---------------------------------------------------------------------
-- [阶段 8/10] V8 · 用户邮箱手机唯一性索引修复 (V8__user_email_phone_unique_null_default.sql)
-- ---------------------------------------------------------------------

-- =====================================================================
-- 审核项 A-3：user.email / user.phone「unique + default ''」缺陷修复
-- 日期：2026-09-15    执行环境：所有环境（dev/test/prod）
-- ---------------------------------------------------------------------
-- 缺陷机理：
--   Hibernate 对值为 null 的字段做动态 INSERT（列直接省略），MySQL 于是补列默认值。
--   这两列的列默认值是 ''，而唯一索引把 '' 当普通值参与去重 ⇒ 第一个未填手机号的
--   用户占用了 '' 这个"槽位"，此后每一个未填手机号的用户创建都会撞
--   UK(user.phone)（未填邮箱的用户撞 UK(user.email)），并被框架统一报成
--   4034「删除失败，数据正在使用中」，极具误导性。
--   /user/add 不带 phone 的第二个用户 100% 复现（阶段 A 黑盒探针实测）。
--
-- 修复：'' 一律改为 NULL（唯一索引允许多行 NULL，"未填写"本就该是 NULL 语义），
--       并把列默认值改为 NULL，与 UserEntity 上移除 default '' 的注解保持一致。
-- 幂等：两条语句可重复执行。
-- =====================================================================

-- 1) 存量清洗：空串哨兵 → NULL
UPDATE `user` SET `phone` = NULL WHERE `phone` = '';
UPDATE `user` SET `email` = NULL WHERE `email` = '';

-- 2) 列默认值归位（去掉 default ''；保持 varchar(255) 可空，唯一索引自动保留）
ALTER TABLE `user`
    MODIFY COLUMN `email` varchar(255) NULL COMMENT '邮箱',
    MODIFY COLUMN `phone` varchar(255) NULL COMMENT '手机号';

-- 3) 验证：执行后应返回 0 行
-- SELECT COUNT(*) FROM `user` WHERE `phone` = '' OR `email` = '';


-- ---------------------------------------------------------------------
-- [阶段 9/10] V9 · 库存多维唯一约束强化 (V9__inventory_dimension_unique.sql)
-- ---------------------------------------------------------------------

-- =====================================================================
-- 库存维度唯一键（阶段 C-3）
-- 问题：inventory 表对「物料×仓库」「物料×生产单元」两个维度没有唯一约束，
--       并发「查无则建」（入库/移库完成时 create-if-absent）会插入重复行；
--       出库路径按单行读库存，重复行意味着后续出库永远少算总量。
-- 方案：先去重（同维度保留最小 id，数量合并到该行），再建唯一索引。
-- 约束：storage_id 与 structure_id 互斥使用（NULL 在 MySQL 唯一索引中互不
--       冲突），因此每个维度一条唯一索引，另一维度的 NULL 行不受该索引约束。
-- 幂等：可重复执行；索引存在时跳过创建。
-- 关联实体：cn.hamm.spms.module.wms.inventory.InventoryEntity
-- =====================================================================

-- 1) 去重：仓库维度（type=1，storage 非空）
UPDATE inventory i
JOIN (
    SELECT material_id, storage_id, MIN(id) AS keep_id, SUM(quantity) AS total_qty
    FROM inventory
    WHERE storage_id IS NOT NULL
    GROUP BY material_id, storage_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.storage_id = d.storage_id
SET i.quantity = IF(i.id = d.keep_id, d.total_qty, i.quantity);

DELETE i FROM inventory i
JOIN (
    SELECT material_id, storage_id, MIN(id) AS keep_id
    FROM inventory
    WHERE storage_id IS NOT NULL
    GROUP BY material_id, storage_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.storage_id = d.storage_id
WHERE i.id <> d.keep_id;

-- 2) 去重：生产单元维度（type=2，structure 非空）
UPDATE inventory i
JOIN (
    SELECT material_id, structure_id, MIN(id) AS keep_id, SUM(quantity) AS total_qty
    FROM inventory
    WHERE structure_id IS NOT NULL
    GROUP BY material_id, structure_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.structure_id = d.structure_id
SET i.quantity = IF(i.id = d.keep_id, d.total_qty, i.quantity);

DELETE i FROM inventory i
JOIN (
    SELECT material_id, structure_id, MIN(id) AS keep_id
    FROM inventory
    WHERE structure_id IS NOT NULL
    GROUP BY material_id, structure_id
    HAVING COUNT(*) > 1
) d ON i.material_id = d.material_id AND i.structure_id = d.structure_id
WHERE i.id <> d.keep_id;

-- 3) 建唯一索引（MySQL 的 CREATE INDEX 不支持 IF NOT EXISTS，用存储过程判重）
DROP PROCEDURE IF EXISTS kubo_add_inventory_unique;
DELIMITER $$
CREATE PROCEDURE kubo_add_inventory_unique()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'inventory'
                     AND index_name = 'uk_inventory_material_storage') THEN
        CREATE UNIQUE INDEX uk_inventory_material_storage ON inventory (material_id, storage_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.statistics
                   WHERE table_schema = DATABASE() AND table_name = 'inventory'
                     AND index_name = 'uk_inventory_material_structure') THEN
        CREATE UNIQUE INDEX uk_inventory_material_structure ON inventory (material_id, structure_id);
    END IF;
END$$
DELIMITER ;
CALL kubo_add_inventory_unique();
DROP PROCEDURE IF EXISTS kubo_add_inventory_unique;


-- ---------------------------------------------------------------------
-- [阶段 10/10] V10 · 规范化菜单路由与层级对齐 (V10__menu_canonical_paths.sql)
-- ---------------------------------------------------------------------

-- =====================================================================
-- 菜单规范化（路由路径 == 视图路径）+ 清理 MES 残留菜单
--
-- ## 背景（本次前端排查发现的三类隐患）
-- 1. 后端菜单种子里仍有 6 条 MES 菜单（`/console/mes/**`），而 MES 前端视图已整体删除：
--    这些行在前端会**注册出没有组件的路由**，菜单点进去是空白页；
-- 2. 存量库的菜单名/路径与前端视图布局长期各写一份，出现
--    「路径能开、名字对不上」与「名字对、路径不通」两类问题（如 `生产单元` ↔ `库位结构`）；
-- 3. `首页` 的 path 是 `/console`，它不是任何页面：会被前端 catch-all 接住当 404，
--    而 404 页停留 5s 会触发 airpower 的 `WebConfig.isTimeout` 全局熔断，
--    之后动态菜单路由**永久不再注册**（前端已修：入口显式重定向 + 每次初始化复位标志）。
--
-- ## 规范化约定（与 `MenuService.initMenu()`、`src/demo/fixtures/menu.ts` 同一套）
-- - **路由路径 == 视图路径**：`/console/wms/input/list` ↔ `src/view/console/wms/input/list.vue`
-- - 工作台是唯一例外：`path=/console/index` → `component=/console/index/index`
-- - 一级分组：工作台 / 仓储管理 WMS / 渠道管理 / 资产管理 / 工厂与库位 / 人员组织 / 系统管理
--
-- ## 幂等
-- 可重复执行：全部为 UPDATE / DELETE / INSERT ... WHERE NOT EXISTS。
-- 根节点 ID 从库里已存在的一级菜单推导（不硬编码 `TreeUtil.ROOT_ID`）。
--
-- ## 执行后自检
--   SELECT id, name, path, component, order_no FROM menu ORDER BY parent_id, order_no;
--   -- 期望：不存在 `/console/mes/%`；不存在 `首页`；存在 `工作台`；分组恰为上述 7 个一级节点。
--
-- 关联实体：cn.hamm.spms.module.system.menu.MenuEntity
-- 关联代码：MenuService.initMenu()、src/demo/fixtures/menu.ts
-- =====================================================================

-- 根节点 ID：取库里现有一级菜单的 parent_id（首页/工作台）
SET @root := COALESCE((
    SELECT parent_id FROM `menu`
    WHERE `name` IN ('首页', '工作台') AND (`path` = '' OR `path` IS NULL OR `path` LIKE '/console%')
    ORDER BY id LIMIT 1
), 0);

-- ── 1. 清理 MES 残留菜单（前端视图已删除） ───────────────────────────────
DELETE FROM `menu` WHERE `path` LIKE '/console/mes/%';

-- ── 2. 工作台 ───────────────────────────────────────────────────────────
UPDATE `menu`
SET `name` = '工作台', `path` = '/console/index', `component` = '/console/index/index', `order_no` = 1
WHERE `path` = '/console'
   OR (`name` = '首页' AND (`component` = '/console/index/index' OR `component` = ''));

-- ── 3. 一级分组：改名 + 排序，缺的补上 ─────────────────────────────────
UPDATE `menu` SET `name` = '仓储管理 WMS', `order_no` = 20 WHERE `parent_id` = @root AND `name` IN ('仓储管理', '仓储管理 WMS');
UPDATE `menu` SET `order_no` = 30 WHERE `parent_id` = @root AND `name` = '渠道管理';
UPDATE `menu` SET `order_no` = 40 WHERE `parent_id` = @root AND `name` = '资产管理';

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '工厂与库位', @root, 50, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @root AND `name` = '工厂与库位');

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '人员组织', @root, 60, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @root AND `name` = '人员组织');

UPDATE `menu` SET `name` = '系统管理', `order_no` = 70 WHERE `parent_id` = @root AND `name` IN ('系统管理', '超管配置');

SET @g_wms := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '仓储管理 WMS' ORDER BY id LIMIT 1);
SET @g_channel := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '渠道管理' ORDER BY id LIMIT 1);
SET @g_asset := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '资产管理' ORDER BY id LIMIT 1);
SET @g_factory := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '工厂与库位' ORDER BY id LIMIT 1);
SET @g_personnel := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '人员组织' ORDER BY id LIMIT 1);
SET @g_system := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '系统管理' ORDER BY id LIMIT 1);

-- ── 4. 叶子页：统一名称 / 归属 / 排序，并清空 component（路径==视图路径） ──
UPDATE `menu` m
JOIN (
    SELECT '/console/wms/input/list' AS path, '入库单' AS name, '仓储管理 WMS' AS grp, 1 AS ord
    UNION ALL SELECT '/console/wms/output/list', '出库单', '仓储管理 WMS', 2
    UNION ALL SELECT '/console/wms/move/list', '移库单', '仓储管理 WMS', 3
    UNION ALL SELECT '/console/wms/inventory/list', '库存查询', '仓储管理 WMS', 4
    UNION ALL SELECT '/console/channel/customer/list', '客户管理', '渠道管理', 1
    UNION ALL SELECT '/console/channel/supplier/list', '供应商管理', '渠道管理', 2
    UNION ALL SELECT '/console/channel/purchase/list', '采购单', '渠道管理', 3
    UNION ALL SELECT '/console/channel/purchasePrice/list', '采购价', '渠道管理', 4
    UNION ALL SELECT '/console/channel/sale/list', '销售单', '渠道管理', 5
    UNION ALL SELECT '/console/channel/salePrice/list', '销售价', '渠道管理', 6
    UNION ALL SELECT '/console/asset/material/list', '物料主数据', '资产管理', 1
    UNION ALL SELECT '/console/asset/contract/list', '合同管理', '资产管理', 2
    UNION ALL SELECT '/console/factory/storage/list', '仓库管理', '工厂与库位', 1
    UNION ALL SELECT '/console/factory/structure/list', '库位结构', '工厂与库位', 2
    UNION ALL SELECT '/console/personnel/user/list', '用户管理', '人员组织', 1
    UNION ALL SELECT '/console/personnel/role/list', '角色管理', '人员组织', 2
    UNION ALL SELECT '/console/personnel/department/list', '部门管理', '人员组织', 3
    UNION ALL SELECT '/console/system/unit/list', '计量单位', '系统管理', 1
    UNION ALL SELECT '/console/system/permission/list', '权限管理', '系统管理', 2
    UNION ALL SELECT '/console/system/menu/list', '菜单管理', '系统管理', 3
    UNION ALL SELECT '/console/system/config/list', '系统配置', '系统管理', 4
    UNION ALL SELECT '/console/system/coderule/list', '编号规则', '系统管理', 5
    UNION ALL SELECT '/console/system/log/list', '操作日志', '系统管理', 6
) c ON m.`path` = c.path
JOIN `menu` g ON g.`parent_id` = @root AND g.`name` = c.grp
SET m.`name` = c.name, m.`parent_id` = g.id, m.`order_no` = c.ord, m.`component` = '';

-- ── 5. 补插缺失页面（操作日志），权限标识留空（与现网一致） ──────────────
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '操作日志', @g_system, 6, '/console/system/log/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/log/list');

-- MCP 工具菜单已随 airpower-ai 一并移除（本页从未实现前端交互）
DELETE FROM `menu` WHERE `path` = '/console/system/mcp/tools';

-- ── 6. 数据权限子分组（挂在系统管理下） ─────────────────────────────────
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '数据权限', @g_system, 8, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @g_system AND `name` = '数据权限');

SET @g_dp := (SELECT id FROM `menu` WHERE `parent_id` = @g_system AND `name` = '数据权限' ORDER BY id LIMIT 1);

UPDATE `menu` m
JOIN (
    SELECT '/console/system/datapermission/resource/list' AS path, '数据资源' AS name, 1 AS ord
    UNION ALL SELECT '/console/system/datapermission/policy/list', '行级策略', 2
    UNION ALL SELECT '/console/system/datapermission/fieldPolicy/list', '列级脱敏', 3
) c ON m.`path` = c.path
SET m.`name` = c.name, m.`parent_id` = @g_dp, m.`order_no` = c.ord, m.`component` = '';

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '数据资源', @g_dp, 1, '/console/system/datapermission/resource/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/resource/list');
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '行级策略', @g_dp, 2, '/console/system/datapermission/policy/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/policy/list');
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '列级脱敏', @g_dp, 3, '/console/system/datapermission/fieldPolicy/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/fieldPolicy/list');

-- ── 7. 清理改名后遗留的空分组（原「超管配置」等） ────────────────────────
DELETE g FROM `menu` g
LEFT JOIN `menu` c ON c.`parent_id` = g.id
WHERE g.`parent_id` = @root
  AND g.`path` = ''
  AND c.id IS NULL
  AND g.`name` NOT IN ('仓储管理 WMS', '渠道管理', '资产管理', '工厂与库位', '人员组织', '系统管理');

-- ── 8. 自检输出 ────────────────────────────────────────────────────────
SELECT g.`name` AS `分组`, m.`name` AS `页面`, m.`path` AS `路径`, m.`order_no` AS `排序`
FROM `menu` m
LEFT JOIN `menu` g ON g.id = m.`parent_id`
ORDER BY g.`order_no`, m.`order_no`;


-- ---------------------------------------------------------------------
-- [收尾阶段] 恢复外键检查与同步 Flyway 记账记录 (可选)
-- ---------------------------------------------------------------------
SET FOREIGN_KEY_CHECKS = 1;

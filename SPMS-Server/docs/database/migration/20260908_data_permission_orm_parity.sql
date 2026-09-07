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
    KEY `idx_dp_parity_verdict` (`verdict`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限 ORM 行数对账';

-- ---------------------------------------------------------------------------
-- 回滚（只回滚结构，不删数据）
-- RENAME TABLE `data_permission_orm_parity_log` TO `data_permission_orm_parity_log_disabled`;
-- ---------------------------------------------------------------------------

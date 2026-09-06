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
--    审计留存建议 ≥ 30 天（enforce 决策要看趋势），到期按 create_time 归档删除。
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
    KEY `idx_dp_diff_verdict` (`verdict`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据权限影子双跑差异';

-- ---------------------------------------------------------------------------
-- 回滚（只回滚结构，不删数据；数据由归档任务负责）
-- ALTER TABLE `data_resource` DROP COLUMN `filterable_columns`;
-- RENAME TABLE `data_permission_diff_log` TO `data_permission_diff_log_disabled`;
-- ---------------------------------------------------------------------------

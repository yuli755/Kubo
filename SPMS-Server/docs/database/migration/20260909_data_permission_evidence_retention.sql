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

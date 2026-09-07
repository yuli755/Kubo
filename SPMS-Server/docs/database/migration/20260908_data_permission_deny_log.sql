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

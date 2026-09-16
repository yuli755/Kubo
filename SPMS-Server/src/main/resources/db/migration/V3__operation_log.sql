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

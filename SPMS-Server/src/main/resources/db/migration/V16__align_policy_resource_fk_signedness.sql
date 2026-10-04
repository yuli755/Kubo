-- ============================================================================
-- V16：数据权限两策略表 resource_id 符号位对齐 + 外键补建（P1-3，R47）
-- ============================================================================
-- 治什么：启动日志里唯一一条、也一直被淹没的 DDL 失败——
--   `Referencing column 'resource_id' and referenced column 'id' are incompatible`
-- 机制（docs/TEST_REPORT_2026-09-M.md §4.3）：`data_resource.id` 是 `BIGINT UNSIGNED`
-- （V2:24 起），而老开发库的 `data_policy` / `data_field_policy` 是 Hibernate
-- `ddl-auto=update` 在实体补 `columnDefinition` **之前**建的——`resource_id` 为有符号
-- BIGINT；V2 的外键声明在 `CREATE TABLE IF NOT EXISTS` 体内（V2:57、V2:78），对已存在
-- 的旧表整体跳过，于是 FK 从未建立。后果是参照完整性**静默失效**：删除数据资源既不
-- 级联也不报错，孤儿策略永远留在库里。实体注释（DataPolicyEntity.java:41-44、
-- DataFieldPolicyEntity.java:45-49）记录了另一半：ddl-auto 不会 MODIFY 已有列，
-- `ADD CONSTRAINT` 每次启动重复失败。
-- 做法：逐字复用 V12__retired_module_catalog_convergence.sql:151-194 的
-- kubo_align_resource_id(t)：类型未对齐才 MODIFY，外键名固定 fk_<t>_resource 判存在，
-- 补建前清悬挂引用（V12 实测两表孤儿行均为 0，防御性保留）。两条 CALL 均幂等：
-- 新库（V2 直接建对）两个分支都跳过；老库被一次性矫正。CI migrations job
-- 连跑两遍 + information_schema 幂等 diff 会同时验证这一点。
-- 红线：不改不排 V1..V15；本脚本只追加在尾部（schemaContract.spec.ts 会红一次，
-- 那是设计要求你确认「加的是尾部新脚本」）。
-- ---------------------------------------------------------------------------

DROP PROCEDURE IF EXISTS kubo_align_resource_id;

DELIMITER $$

-- （自 V12:155-187 逐字复用）把 resource_id 对齐成 BIGINT UNSIGNED（与 V2 的 DDL 及
-- data_resource.id 一致），仅在类型尚未对齐时执行；随后补建外键（已存在则跳过）。
CREATE PROCEDURE kubo_align_resource_id(IN t VARCHAR(64))
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.`TABLES`
              WHERE `TABLE_SCHEMA` = DATABASE() AND `TABLE_NAME` = t) THEN

    IF EXISTS (SELECT 1 FROM information_schema.`COLUMNS`
                WHERE `TABLE_SCHEMA` = DATABASE()
                  AND `TABLE_NAME` = t
                  AND `COLUMN_NAME` = 'resource_id'
                  AND `COLUMN_TYPE` <> 'bigint unsigned') THEN
      SET @s = CONCAT('ALTER TABLE `', t,
                      '` MODIFY COLUMN `resource_id` BIGINT UNSIGNED NOT NULL COMMENT ''数据资源ID''');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;

    -- 外键名固定，便于幂等判断与后续运维识别（不用 Hibernate 的随机 FK 名）
    IF NOT EXISTS (SELECT 1 FROM information_schema.`TABLE_CONSTRAINTS`
                    WHERE `TABLE_SCHEMA` = DATABASE()
                      AND `TABLE_NAME` = t
                      AND `CONSTRAINT_NAME` = CONCAT('fk_', t, '_resource')) THEN
      -- 悬挂引用会让建约束失败，先清掉（实测两表孤儿行均为 0，此处为防御）
      SET @s = CONCAT('DELETE FROM `', t, '` WHERE `resource_id` NOT IN (SELECT `id` FROM `data_resource`)');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

      SET @s = CONCAT('ALTER TABLE `', t, '` ADD CONSTRAINT `fk_', t, '_resource`',
                      ' FOREIGN KEY (`resource_id`) REFERENCES `data_resource` (`id`)');
      PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;

  END IF;
END$$

DELIMITER ;

CALL kubo_align_resource_id('data_field_policy');
CALL kubo_align_resource_id('data_policy');

DROP PROCEDURE IF EXISTS kubo_align_resource_id;

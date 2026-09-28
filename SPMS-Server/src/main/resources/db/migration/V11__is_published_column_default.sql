-- =====================================================================
-- is_published 列默认值补齐（存量库自愈）
--
-- ## 背景（2026-09-28 联调启动实测发现）
-- `BaseEntity.isPublished` 原先只写 `@Column(name = "is_published", nullable = false)`，
-- 而同批的 `isDisabled` / `createTime` / `updateTime` 都在 columnDefinition 里写了 `default 0`。
-- 于是 Hibernate（ddl-auto=update）建出的列是「NOT NULL 且**无 DB 级默认值**」：
--
--     information_schema: is_published | bit(1) | IS_NULLABLE=NO | COLUMN_DEFAULT=NULL
--
-- JPA 路径的 INSERT 总会带上该字段（成员初始化 `= false`），所以长期看不出问题；
-- 但任何**裸 SQL** 只要没显式列出 `is_published` 就会撞：
--
--     java.sql.SQLException: Field 'is_published' doesn't have a default value
--
-- `V2__data_permission_migrations.sql` 第 252 行正是如此：
--     INSERT INTO `user_tenant` (`user_id`, `tenant_id`, `is_default`) SELECT ...
-- 而同文件第 248 行的 `INSERT INTO tenant` 显式列出了 `is_published`，故那一行能过。
--
-- 后果：凡是「JPA 先建表、Flyway 后迁移」的库（STARTUP.md 明文规定的本地开发流程），
-- V2 直接失败、`flyway_schema_history` 记 success=0，**后端根本起不来**。
-- 全新库不受影响：V1/V2 自己建表时写的是 `is_published BIT(1) NOT NULL DEFAULT b'0'`。
--
-- ## 本次修复的两处
-- 1. 根因：`BaseEntity.isPublished` 补 `columnDefinition = "bit(1) NOT NULL default 0 ..."`，
--    使 JPA 建出的列与 V1/V2 的 DDL 一致（项目自身的「迁移脚本 / 实体 / H2 schema 三处同源」约定）。
-- 2. 存量：本脚本把所有缺默认值的 `is_published` 列补齐。
--
-- ## 为什么不改 V2
-- V2 在别处可能已成功执行；改动已应用的迁移会导致 Flyway 校验和漂移
-- （`Migration checksum mismatch for version 2`），迫使每个环境手工 `flyway repair`。
-- 故 V2 保持原样，用新增版本解决。
--
-- ## ⚠️ 执行顺序限制（重要）
-- Flyway 按版本号顺序执行，本脚本是 V11，**在 V2 之后**。
-- 因此对「已经卡在 V2 失败」的库，本脚本没有机会运行 —— 需先手工执行下面这条
-- 幂等修复（等价于本脚本做的事），再删掉 flyway_schema_history 里 V2 的失败记录，
-- 重启后 V2 会重跑成功、V11 再作为兜底空跑一次：
--
--     -- 手工修复（可重复执行；把 kubo 换成实际库名）
--     ALTER TABLE `kubo`.`tenant`                         MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     ALTER TABLE `kubo`.`user_tenant`                    MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     ALTER TABLE `kubo`.`operation_log`                  MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     ALTER TABLE `kubo`.`data_permission_deny_log`       MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     ALTER TABLE `kubo`.`data_permission_diff_log`       MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     ALTER TABLE `kubo`.`data_permission_orm_parity_log` MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b'0';
--     DELETE FROM `kubo`.`flyway_schema_history` WHERE version = '2' AND success = 0;
--
-- ## 幂等
-- 只处理 `COLUMN_DEFAULT IS NULL` 的列，已带默认值的表不受影响；可重复执行。
-- 用临时表先物化待修表名，避免游标遍历 information_schema 时被自身的 ALTER 干扰。
--
-- ## 执行后自检
--   SELECT TABLE_NAME, COLUMN_DEFAULT FROM information_schema.COLUMNS
--    WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'is_published' AND COLUMN_DEFAULT IS NULL;
--   -- 期望：0 行
-- =====================================================================

DROP TEMPORARY TABLE IF EXISTS `kubo_tmp_is_published_fix`;

CREATE TEMPORARY TABLE `kubo_tmp_is_published_fix` (
    `tbl` VARCHAR(64) NOT NULL PRIMARY KEY
) ENGINE = Memory
  AS SELECT c.`TABLE_NAME` AS `tbl`
       FROM information_schema.`COLUMNS` c
      WHERE c.`TABLE_SCHEMA` = DATABASE()
        AND c.`COLUMN_NAME` = 'is_published'
        AND c.`COLUMN_DEFAULT` IS NULL;

DROP PROCEDURE IF EXISTS kubo_fix_is_published_default;

DELIMITER $$

CREATE PROCEDURE kubo_fix_is_published_default()
BEGIN
  DECLARE v_done INT DEFAULT 0;
  DECLARE v_tbl  VARCHAR(64);
  DECLARE cur CURSOR FOR SELECT `tbl` FROM `kubo_tmp_is_published_fix`;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

  OPEN cur;
  fix_loop: LOOP
    FETCH cur INTO v_tbl;
    IF v_done = 1 THEN
      LEAVE fix_loop;
    END IF;
    SET @s = CONCAT('ALTER TABLE `', v_tbl,
                    '` MODIFY COLUMN `is_published` BIT(1) NOT NULL DEFAULT b''0''',
                    ' COMMENT ''是否已发布''');
    PREPARE st FROM @s;
    EXECUTE st;
    DEALLOCATE PREPARE st;
  END LOOP;
  CLOSE cur;
END$$

DELIMITER ;

CALL kubo_fix_is_published_default();

DROP PROCEDURE IF EXISTS kubo_fix_is_published_default;
DROP TEMPORARY TABLE IF EXISTS `kubo_tmp_is_published_fix`;

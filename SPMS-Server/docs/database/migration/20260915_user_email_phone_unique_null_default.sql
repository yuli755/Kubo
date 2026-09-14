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

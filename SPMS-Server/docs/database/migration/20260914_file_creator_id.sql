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

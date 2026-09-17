-- OrmParityEvidenceTest 的夹具：material 与 unit 的最小结构 + 每例前清空。
-- owner_id 是刻意多出来的一列（实体没有），用来造 MAPPER_STRICT 那条证据。
-- 对账表不在这里建：它由 src/test/resources/schema-test.sql 建（生产侧对应迁移脚本）。
CREATE TABLE IF NOT EXISTS unit (id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS material (id BIGINT PRIMARY KEY, tenant_id BIGINT, creator_id BIGINT, owner_id BIGINT, unit_id BIGINT);
ALTER TABLE material ADD COLUMN IF NOT EXISTS name VARCHAR(255);
DELETE FROM material;
DELETE FROM data_permission_orm_parity_log;

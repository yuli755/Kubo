-- MaterialMapperReadParityTest 的夹具。
-- unit 表是为 JPA 侧准备的：MaterialEntity 有个 EAGER 的 @ManyToOne unit，COUNT 今天不 join，
-- 但"少一张表"这种红要留给真出事的那次。
-- owner_id 是刻意多出来的一列：数据库里有、实体上没有，用来把两边的差异摆到桌面上。
CREATE TABLE IF NOT EXISTS unit (id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS material (id BIGINT PRIMARY KEY, tenant_id BIGINT, creator_id BIGINT, owner_id BIGINT, unit_id BIGINT);
DELETE FROM material;

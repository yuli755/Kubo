-- StructureMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS structure (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), type INT, parent_id BIGINT);
ALTER TABLE structure ADD COLUMN IF NOT EXISTS create_time BIGINT DEFAULT 0;
ALTER TABLE structure ADD COLUMN IF NOT EXISTS update_time BIGINT DEFAULT 0;
ALTER TABLE structure ADD COLUMN IF NOT EXISTS is_disabled BOOLEAN DEFAULT FALSE;
ALTER TABLE structure ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE;
ALTER TABLE structure ADD COLUMN IF NOT EXISTS code VARCHAR(255);
ALTER TABLE structure ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE structure ADD COLUMN IF NOT EXISTS type INT;
ALTER TABLE structure ADD COLUMN IF NOT EXISTS parent_id BIGINT;
DELETE FROM structure;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) VALUES (1, 10, 7, 'ST-001', '组装工位', 1, 0, TRUE);
INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) VALUES (2, 10, 8, 'ST-002', '质检工位', 2, 1, FALSE);
INSERT INTO structure (id, tenant_id, creator_id, code, name, type, parent_id, is_published) VALUES (3, 20, 7, 'ST-003', '包装工位', 3, 0, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9001, 'factory.structure', 'structure', 'structure', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9002, 'test-structure-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

-- StorageMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS storage (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), parent_id BIGINT);
ALTER TABLE storage ADD COLUMN IF NOT EXISTS create_time BIGINT DEFAULT 0;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS update_time BIGINT DEFAULT 0;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS is_disabled BOOLEAN DEFAULT FALSE;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE;
ALTER TABLE storage ADD COLUMN IF NOT EXISTS code VARCHAR(255);
ALTER TABLE storage ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE storage ADD COLUMN IF NOT EXISTS parent_id BIGINT;
DELETE FROM storage;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO storage (id, tenant_id, creator_id, code, name, parent_id, is_published) VALUES (1, 10, 7, 'S-001', '一号仓', 0, TRUE);
INSERT INTO storage (id, tenant_id, creator_id, code, name, parent_id, is_published) VALUES (2, 10, 8, 'S-002', '二号仓', 1, FALSE);
INSERT INTO storage (id, tenant_id, creator_id, code, name, parent_id, is_published) VALUES (3, 20, 7, 'S-003', '三号仓', 0, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9001, 'factory.storage', 'storage', 'storage', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9002, 'test-storage-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

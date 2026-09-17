-- ContractMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS contract (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, code VARCHAR(255), name VARCHAR(255), content TEXT, start_time BIGINT, end_time BIGINT, type INT, status INT);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS create_time BIGINT DEFAULT 0;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS update_time BIGINT DEFAULT 0;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS is_disabled BOOLEAN DEFAULT FALSE;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS code VARCHAR(255);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS name VARCHAR(255);
ALTER TABLE contract ADD COLUMN IF NOT EXISTS content TEXT;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS start_time BIGINT;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS end_time BIGINT;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS type INT;
ALTER TABLE contract ADD COLUMN IF NOT EXISTS status INT;
DELETE FROM contract;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) VALUES (1, 10, 7, 'C-001', '采购合同A', '内容A', 1000, 2000, 1, 1, TRUE);
INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) VALUES (2, 10, 8, 'C-002', '采购合同B', '内容B', 1000, 2000, 1, 1, FALSE);
INSERT INTO contract (id, tenant_id, creator_id, code, name, content, start_time, end_time, type, status, is_published) VALUES (3, 20, 7, 'C-003', '销售合同C', '内容C', 1000, 2000, 2, 1, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9001, 'asset.contract', 'contract', 'contract', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9002, 'test-contract-tenant', 9001, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

-- InputMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS input (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), status INT, type INT, purchase_id BIGINT, structure_id BIGINT, move_id BIGINT);
DELETE FROM input;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO input (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (1, 10, 7, 'IN-001', 2, 1, TRUE);
INSERT INTO input (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (2, 10, 8, 'IN-002', 3, 2, FALSE);
INSERT INTO input (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (3, 20, 7, 'IN-003', 2, 1, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9301, 'wms.input', 'input', 'input', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9302, 'test-input-tenant', 9301, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

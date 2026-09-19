-- OutputMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS output (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), status INT, type INT, sale_id BIGINT, move_id BIGINT);
DELETE FROM output;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (1, 10, 7, 'OUT-001', 2, 1, TRUE);
INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (2, 10, 8, 'OUT-002', 3, 2, FALSE);
INSERT INTO output (id, tenant_id, creator_id, bill_code, status, type, is_published) VALUES (3, 20, 7, 'OUT-003', 2, 1, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9401, 'wms.output', 'output', 'output', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9402, 'test-output-tenant', 9401, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

-- MoveMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS move (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), status INT, storage_id BIGINT);
DELETE FROM move;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) VALUES (1, 10, 7, 'MOVE-001', 2, 201, TRUE);
INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) VALUES (2, 10, 8, 'MOVE-002', 3, 202, FALSE);
INSERT INTO move (id, tenant_id, creator_id, bill_code, status, storage_id, is_published) VALUES (3, 20, 7, 'MOVE-003', 2, 203, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9501, 'wms.move', 'move', 'move', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9502, 'test-move-tenant', 9501, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

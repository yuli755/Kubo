-- SupplierMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS supplier (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, name VARCHAR(255), code VARCHAR(255), phone VARCHAR(255));
DELETE FROM supplier;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO supplier (id, tenant_id, creator_id, name, code, phone, is_published) VALUES (1, 10, 7, '华东供应商', 'S-001', '13900000001', TRUE);
INSERT INTO supplier (id, tenant_id, creator_id, name, code, phone, is_published) VALUES (2, 10, 8, '华南供应商', 'S-002', '13900000002', FALSE);
INSERT INTO supplier (id, tenant_id, creator_id, name, code, phone, is_published) VALUES (3, 20, 7, '华东供应商', 'S-003', '13900000003', TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9701, 'channel.supplier', 'supplier', 'supplier', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9702, 'test-supplier-tenant', 9701, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

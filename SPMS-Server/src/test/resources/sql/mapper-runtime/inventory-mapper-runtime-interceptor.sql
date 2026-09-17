-- InventoryMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS inventory (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, quantity DOUBLE, type INT, storage_id BIGINT, structure_id BIGINT);
DELETE FROM inventory;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) VALUES (1, 10, 7, 101, 5.0, 1, 201, NULL, TRUE);
INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) VALUES (2, 10, 8, 102, 8.0, 1, 202, NULL, FALSE);
INSERT INTO inventory (id, tenant_id, creator_id, material_id, quantity, type, storage_id, structure_id, is_published) VALUES (3, 20, 7, 103, 13.0, 1, 203, NULL, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9201, 'wms.inventory', 'inventory', 'inventory', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9202, 'test-inventory-tenant', 9201, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

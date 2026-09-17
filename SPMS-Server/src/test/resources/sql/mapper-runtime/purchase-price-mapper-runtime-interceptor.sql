-- PurchasePriceMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS purchase_price (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, supplier_id BIGINT, price DOUBLE);
DELETE FROM purchase_price;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) VALUES (1, 10, 7, 101, 201, 12.5, TRUE);
INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) VALUES (2, 10, 8, 102, 202, 8.8, FALSE);
INSERT INTO purchase_price (id, tenant_id, creator_id, material_id, supplier_id, price, is_published) VALUES (3, 20, 7, 103, 203, 99.9, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9801, 'channel.purchasePrice', 'purchase_price', 'purchase_price', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9802, 'test-purchase-price-tenant', 9801, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

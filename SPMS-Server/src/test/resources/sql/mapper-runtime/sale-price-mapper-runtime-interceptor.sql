-- SalePriceMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS sale_price (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, material_id BIGINT, customer_id BIGINT, price DOUBLE);
DELETE FROM sale_price;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO sale_price (id, tenant_id, creator_id, material_id, customer_id, price, is_published) VALUES (1, 10, 7, 101, 301, 20.0, TRUE);
INSERT INTO sale_price (id, tenant_id, creator_id, material_id, customer_id, price, is_published) VALUES (2, 10, 8, 102, 302, 15.5, FALSE);
INSERT INTO sale_price (id, tenant_id, creator_id, material_id, customer_id, price, is_published) VALUES (3, 20, 7, 103, 303, 88.0, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9901, 'channel.salePrice', 'sale_price', 'sale_price', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9902, 'test-sale-price-tenant', 9901, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

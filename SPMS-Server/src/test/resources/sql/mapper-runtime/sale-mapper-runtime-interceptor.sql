-- SaleMapperRuntimeInterceptorTest 的 H2 夹具（从 Java 字符串搬来，2026-09-18 SQL 分层门禁）
-- 规则：禁止 .java 写 SQL —— 可执行语句一律住这里，Java 侧用 SqlFixtures.execute() 调用。
CREATE TABLE IF NOT EXISTS sale (id BIGINT PRIMARY KEY, create_time BIGINT DEFAULT 0, update_time BIGINT DEFAULT 0, is_disabled BOOLEAN DEFAULT FALSE, is_published BOOLEAN DEFAULT FALSE, tenant_id BIGINT, creator_id BIGINT, reject_reason VARCHAR(255), bill_code VARCHAR(255), reason VARCHAR(255), total_price DOUBLE, status INT, customer_id BIGINT);
DELETE FROM sale;
DELETE FROM data_policy;
DELETE FROM data_resource;
INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) VALUES (1, 10, 7, 'SA-001', '分销', 2, 9001, TRUE);
INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) VALUES (2, 10, 8, 'SA-002', '直销', 3, 9002, FALSE);
INSERT INTO sale (id, tenant_id, creator_id, bill_code, reason, status, customer_id, is_published) VALUES (3, 20, 7, 'SA-003', '分销', 2, 9003, TRUE);
INSERT INTO data_resource (id, resource_key, table_name, table_alias, tenant_column, owner_columns, supported_actions, status, version_no) VALUES (9501, 'channel.sale', 'sale', 'sale', 'tenant_id', 'creator_id', 'QUERY', 'ENABLED', 1);
INSERT INTO data_policy (id, policy_code, resource_id, subject_type, subject_id, action_name, condition_json, priority, enabled, version_no) VALUES (9502, 'test-sale-tenant', 9501, 'TENANT', 10, 'QUERY', '{}', 0, TRUE, 1);

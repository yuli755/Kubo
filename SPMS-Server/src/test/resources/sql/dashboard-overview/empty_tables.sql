-- DashboardOverviewContractTest：为 8 个资源各建一张只有 id 的空表。
-- ${table} 由 Java 侧按资源表名传入（语句本身住在文件里，Java 不拼 SQL）。
CREATE TABLE IF NOT EXISTS ${table} (id BIGINT PRIMARY KEY);
DELETE FROM ${table};

-- OrmParityEvidenceTest 读对账证据。
-- 别名一律带双引号：H2 会把裸标签大写化，那时 row.get("verdict") 读到 null，
-- 于是"null 等于 null"这种断言会全都绿着 —— 比红难发现得多。
SELECT verdict AS "verdict", jpa_count AS "jpaCount", mapper_count AS "mapperCount",
       table_name AS "tableName", fragment_sql AS "fragmentSql", detail AS "detail"
FROM data_permission_orm_parity_log ORDER BY id;

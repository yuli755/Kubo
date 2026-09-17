-- MybatisJpaCoexistenceTest：同一条 COUNT 分别走 JPA 与 MyBatis，断言两套 ORM 看到同一份 schema
select count(1) from data_resource;

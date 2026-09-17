-- KuboDataPermissionHandlerTest：构造 MappedStatement 用的探针语句。
-- 它是被测对象的输入（让 JSqlParser 解析后再改写），不是要执行的 SQL —— 但仍然住文件里。
SELECT 1 FROM material;

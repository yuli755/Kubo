-- 各 *MapperRuntimeInterceptorTest 的"把策略清空"场景（断言无策略时不放开全表）。
-- 原来每个测试类各写一遍 DELETE FROM data_policy；它是"无策略"这条路径的输入，抽出来共用。
DELETE FROM data_policy;

-- ============================================================================
-- V13 —— 退场资源的 data_resource / data_policy / data_field_policy 收敛
--
-- 背景（K 报告 §4 P2-4）：
--   mes.* 六资源（order/plan/picking/bom/routing/operation）与 asset.device 的
--   模块本体已随「整域瘦身」退场 —— module/mes 目录、设备视图、菜单与权限身份均已
--   随 V12 清掉，DevDataInitRunner 的资源种子也只剩 17 个在世资源；但 data_resource
--   里仍有 7 行滞留、data_policy 里仍有 28 行（4×7）策略指向它们。运行期
--   DataPermissionService#findResource 按 resourceKey 反查，这 7 个键已无任何服务
--   派生 ⇒ 策略永不生效，只让「数据资源 / 行级策略」面板长期挂着死项。
--
-- 执行顺序（FK 约束，均为 NO ACTION）：
--   data_field_policy.resource_id / data_policy.resource_id 均 REFERENCES
--   data_resource(id)，故必须：列级策略 → 行级策略 → 资源行。
--   当前库中退场资源的列级策略为 0 行（实测），第 1 段为幂等兜底，覆盖历史差异库。
--
-- 幂等性：三段全部按 resource_key 匹配，重复执行影响 0 行（Flyway checksum 不变）。
--
-- 刻意不动的三件事：
--   1. 证据表（data_permission_diff_log / data_permission_orm_parity_log）里退场
--      resource_key 的历史行保留（实测 0 行；若有也交给既有 retention 清理按 TTL 归档，
--      审计证据不做级联删除）；
--   2. application-production.yml 的 enforce-resources 白名单实测 6 项全为在世资源，
--      不需改；DataPermissionEnforceReadiness 启动闸不受影响；
--   3. 退场键写进排除清单而非硬编码全表清理 —— 未来某资源合法退场时，向本文件同款
--      追加 V14 而不是原地改已应用脚本（V13 起与 V11/V12 一样不可再编辑）。
--
-- 尾部自检（迁移后在业务库执行，四条应全部为 0 / 空）：
--   SELECT COUNT(*) FROM data_resource WHERE resource_key IN ('mes.order','mes.plan','mes.picking','mes.bom','mes.routing','mes.operation','asset.device');
--   SELECT COUNT(*) FROM data_policy WHERE resource_id IN (SELECT id FROM data_resource WHERE 1=0)  -- 见下：删除后只能靠孤儿与总量断言
--   SELECT COUNT(*) FROM data_policy WHERE resource_id NOT IN (SELECT id FROM data_resource);
--   SELECT COUNT(*) FROM data_field_policy WHERE resource_id NOT IN (SELECT id FROM data_resource);
--   -- 资源按 key 删除后 id 无法回查，故「按 id 快照」那类断言在迁移后不成立（易写出
--   -- 误把在世资源 id 算进去的假阳性）；权威判据是上面两条孤儿断言 = 0，加上总量：
--   -- data_resource = 17（= DevDataInitRunner 种子的在世资源数），data_policy = 65。
-- ============================================================================

-- 1. 列级策略先行（FK 指向 data_resource；退场资源当前 0 行，保留语句以覆盖历史差异库）
DELETE dfp FROM `data_field_policy` dfp
    JOIN `data_resource` dr ON dr.`id` = dfp.`resource_id`
WHERE dr.`resource_key` IN (
    'mes.order', 'mes.plan', 'mes.picking', 'mes.bom', 'mes.routing', 'mes.operation', 'asset.device'
);

-- 2. 行级策略（实测 28 行 = 4 × 7）
DELETE dp FROM `data_policy` dp
    JOIN `data_resource` dr ON dr.`id` = dp.`resource_id`
WHERE dr.`resource_key` IN (
    'mes.order', 'mes.plan', 'mes.picking', 'mes.bom', 'mes.routing', 'mes.operation', 'asset.device'
);

-- 3. 资源行本身（实测 7 行；此后 data_resource = DevDataInitRunner 种子的 17 个在世资源）
DELETE FROM `data_resource`
WHERE `resource_key` IN (
    'mes.order', 'mes.plan', 'mes.picking', 'mes.bom', 'mes.routing', 'mes.operation', 'asset.device'
);

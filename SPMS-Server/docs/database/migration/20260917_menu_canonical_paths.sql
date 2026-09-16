-- =====================================================================
-- 菜单规范化（路由路径 == 视图路径）+ 清理 MES 残留菜单
--
-- ## 背景（本次前端排查发现的三类隐患）
-- 1. 后端菜单种子里仍有 6 条 MES 菜单（`/console/mes/**`），而 MES 前端视图已整体删除：
--    这些行在前端会**注册出没有组件的路由**，菜单点进去是空白页；
-- 2. 存量库的菜单名/路径与前端视图布局长期各写一份，出现
--    「路径能开、名字对不上」与「名字对、路径不通」两类问题（如 `生产单元` ↔ `库位结构`）；
-- 3. `首页` 的 path 是 `/console`，它不是任何页面：会被前端 catch-all 接住当 404，
--    而 404 页停留 5s 会触发 airpower 的 `WebConfig.isTimeout` 全局熔断，
--    之后动态菜单路由**永久不再注册**（前端已修：入口显式重定向 + 每次初始化复位标志）。
--
-- ## 规范化约定（与 `MenuService.initMenu()`、`src/demo/fixtures/menu.ts` 同一套）
-- - **路由路径 == 视图路径**：`/console/wms/input/list` ↔ `src/view/console/wms/input/list.vue`
-- - 工作台是唯一例外：`path=/console/index` → `component=/console/index/index`
-- - 一级分组：工作台 / 仓储管理 WMS / 渠道管理 / 资产管理 / 工厂与库位 / 人员组织 / 系统管理
--
-- ## 幂等
-- 可重复执行：全部为 UPDATE / DELETE / INSERT ... WHERE NOT EXISTS。
-- 根节点 ID 从库里已存在的一级菜单推导（不硬编码 `TreeUtil.ROOT_ID`）。
--
-- ## 执行后自检
--   SELECT id, name, path, component, order_no FROM menu ORDER BY parent_id, order_no;
--   -- 期望：不存在 `/console/mes/%`；不存在 `首页`；存在 `工作台`；分组恰为上述 7 个一级节点。
--
-- 关联实体：cn.hamm.spms.module.system.menu.MenuEntity
-- 关联代码：MenuService.initMenu()、src/demo/fixtures/menu.ts
-- =====================================================================

-- 根节点 ID：取库里现有一级菜单的 parent_id（首页/工作台）
SET @root := COALESCE((
    SELECT parent_id FROM `menu`
    WHERE `name` IN ('首页', '工作台') AND (`path` = '' OR `path` IS NULL OR `path` LIKE '/console%')
    ORDER BY id LIMIT 1
), 0);

-- ── 1. 清理 MES 残留菜单（前端视图已删除） ───────────────────────────────
DELETE FROM `menu` WHERE `path` LIKE '/console/mes/%';

-- ── 2. 工作台 ───────────────────────────────────────────────────────────
UPDATE `menu`
SET `name` = '工作台', `path` = '/console/index', `component` = '/console/index/index', `order_no` = 1
WHERE `path` = '/console'
   OR (`name` = '首页' AND (`component` = '/console/index/index' OR `component` = ''));

-- ── 3. 一级分组：改名 + 排序，缺的补上 ─────────────────────────────────
UPDATE `menu` SET `name` = '仓储管理 WMS', `order_no` = 20 WHERE `parent_id` = @root AND `name` IN ('仓储管理', '仓储管理 WMS');
UPDATE `menu` SET `order_no` = 30 WHERE `parent_id` = @root AND `name` = '渠道管理';
UPDATE `menu` SET `order_no` = 40 WHERE `parent_id` = @root AND `name` = '资产管理';

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '工厂与库位', @root, 50, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @root AND `name` = '工厂与库位');

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '人员组织', @root, 60, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @root AND `name` = '人员组织');

UPDATE `menu` SET `name` = '系统管理', `order_no` = 70 WHERE `parent_id` = @root AND `name` IN ('系统管理', '超管配置');

SET @g_wms := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '仓储管理 WMS' ORDER BY id LIMIT 1);
SET @g_channel := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '渠道管理' ORDER BY id LIMIT 1);
SET @g_asset := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '资产管理' ORDER BY id LIMIT 1);
SET @g_factory := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '工厂与库位' ORDER BY id LIMIT 1);
SET @g_personnel := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '人员组织' ORDER BY id LIMIT 1);
SET @g_system := (SELECT id FROM `menu` WHERE `parent_id` = @root AND `name` = '系统管理' ORDER BY id LIMIT 1);

-- ── 4. 叶子页：统一名称 / 归属 / 排序，并清空 component（路径==视图路径） ──
UPDATE `menu` m
JOIN (
    SELECT '/console/wms/input/list' AS path, '入库单' AS name, '仓储管理 WMS' AS grp, 1 AS ord
    UNION ALL SELECT '/console/wms/output/list', '出库单', '仓储管理 WMS', 2
    UNION ALL SELECT '/console/wms/move/list', '移库单', '仓储管理 WMS', 3
    UNION ALL SELECT '/console/wms/inventory/list', '库存查询', '仓储管理 WMS', 4
    UNION ALL SELECT '/console/channel/customer/list', '客户管理', '渠道管理', 1
    UNION ALL SELECT '/console/channel/supplier/list', '供应商管理', '渠道管理', 2
    UNION ALL SELECT '/console/channel/purchase/list', '采购单', '渠道管理', 3
    UNION ALL SELECT '/console/channel/purchasePrice/list', '采购价', '渠道管理', 4
    UNION ALL SELECT '/console/channel/sale/list', '销售单', '渠道管理', 5
    UNION ALL SELECT '/console/channel/salePrice/list', '销售价', '渠道管理', 6
    UNION ALL SELECT '/console/asset/material/list', '物料主数据', '资产管理', 1
    UNION ALL SELECT '/console/asset/contract/list', '合同管理', '资产管理', 2
    UNION ALL SELECT '/console/factory/storage/list', '仓库管理', '工厂与库位', 1
    UNION ALL SELECT '/console/factory/structure/list', '库位结构', '工厂与库位', 2
    UNION ALL SELECT '/console/personnel/user/list', '用户管理', '人员组织', 1
    UNION ALL SELECT '/console/personnel/role/list', '角色管理', '人员组织', 2
    UNION ALL SELECT '/console/personnel/department/list', '部门管理', '人员组织', 3
    UNION ALL SELECT '/console/system/unit/list', '计量单位', '系统管理', 1
    UNION ALL SELECT '/console/system/permission/list', '权限管理', '系统管理', 2
    UNION ALL SELECT '/console/system/menu/list', '菜单管理', '系统管理', 3
    UNION ALL SELECT '/console/system/config/list', '系统配置', '系统管理', 4
    UNION ALL SELECT '/console/system/coderule/list', '编号规则', '系统管理', 5
    UNION ALL SELECT '/console/system/log/list', '操作日志', '系统管理', 6
) c ON m.`path` = c.path
JOIN `menu` g ON g.`parent_id` = @root AND g.`name` = c.grp
SET m.`name` = c.name, m.`parent_id` = g.id, m.`order_no` = c.ord, m.`component` = '';

-- ── 5. 补插缺失页面（操作日志 / MCP 工具），权限标识留空（与现网一致） ────
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '操作日志', @g_system, 6, '/console/system/log/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/log/list');

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT 'MCP 工具', @g_system, 7, '/console/system/mcp/tools', '/console/system/mcp/tools', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/mcp/tools');

-- ── 6. 数据权限子分组（挂在系统管理下） ─────────────────────────────────
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '数据权限', @g_system, 8, '', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `parent_id` = @g_system AND `name` = '数据权限');

SET @g_dp := (SELECT id FROM `menu` WHERE `parent_id` = @g_system AND `name` = '数据权限' ORDER BY id LIMIT 1);

UPDATE `menu` m
JOIN (
    SELECT '/console/system/datapermission/resource/list' AS path, '数据资源' AS name, 1 AS ord
    UNION ALL SELECT '/console/system/datapermission/policy/list', '行级策略', 2
    UNION ALL SELECT '/console/system/datapermission/fieldPolicy/list', '列级脱敏', 3
) c ON m.`path` = c.path
SET m.`name` = c.name, m.`parent_id` = @g_dp, m.`order_no` = c.ord, m.`component` = '';

INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '数据资源', @g_dp, 1, '/console/system/datapermission/resource/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/resource/list');
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '行级策略', @g_dp, 2, '/console/system/datapermission/policy/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/policy/list');
INSERT INTO `menu` (`name`, `parent_id`, `order_no`, `path`, `component`, `is_published`, `is_disabled`)
SELECT '列级脱敏', @g_dp, 3, '/console/system/datapermission/fieldPolicy/list', '', 1, 0 FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `menu` WHERE `path` = '/console/system/datapermission/fieldPolicy/list');

-- ── 7. 清理改名后遗留的空分组（原「超管配置」等） ────────────────────────
DELETE g FROM `menu` g
LEFT JOIN `menu` c ON c.`parent_id` = g.id
WHERE g.`parent_id` = @root
  AND g.`path` = ''
  AND c.id IS NULL
  AND g.`name` NOT IN ('仓储管理 WMS', '渠道管理', '资产管理', '工厂与库位', '人员组织', '系统管理');

-- ── 8. 自检输出 ────────────────────────────────────────────────────────
SELECT g.`name` AS `分组`, m.`name` AS `页面`, m.`path` AS `路径`, m.`order_no` AS `排序`
FROM `menu` m
LEFT JOIN `menu` g ON g.id = m.`parent_id`
ORDER BY g.`order_no`, m.`order_no`;

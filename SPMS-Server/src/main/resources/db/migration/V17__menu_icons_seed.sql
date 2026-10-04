-- ============================================================================
-- 菜单图标种子：为现存 34 条菜单回填 icon，让侧边栏图标各不相同
-- ============================================================================
-- 背景（2026-10 浏览器实测，测试报告 §8）：
--   menu.icon 全部是空串 → 前端 resolveMenuIcon() 统一回退 MenuIcon，
--   1920×1080 实测侧边栏渲染 41 个 svg、去重后只有 2 种 path
--   （菜单图标 + 子菜单箭头）——整棵菜单看起来"图标全一样"。
--   字段链路（DB icon → getMyMenuList → MenuItem.icon → KAppMenuNode）本就完好，
--   缺的只是数据：种子 MenuService.initMenu() 从没写 icon，历史行也没有值。
--
-- 本脚本与两处配套，缺一会漂移（由 src/config/__tests__/menuContract.spec.ts 对账）：
--   1) MenuService.initMenu() 的种子同步写入 setIcon(...)（新库首启即带图标）；
--   2) 前端 src/component/shell/icons.ts 的 ICON_MAP 登记全部用到的图标名
--      （未登记的名字会**静默**回退 MenuIcon，不报错）。
--   图标名一律取 Element Plus 组件的 PascalCase 名（受控子集，控制打包体积）。
--
-- 幂等：UPDATE ... WHERE name = ...；重复执行结果相同（CI 连跑两遍判据）。
-- 按 name 定位：这 34 个名称在 menu 表内各自唯一，且 name 比自增 id 稳定
--   （不同环境的 id 序列不保证一致，name 是业务事实）。
-- ============================================================================

-- 一级节点
UPDATE `menu` SET `icon` = 'DataBoard'      WHERE `name` = '工作台';
UPDATE `menu` SET `icon` = 'Box'            WHERE `name` = '仓储管理 WMS';
UPDATE `menu` SET `icon` = 'Van'            WHERE `name` = '渠道管理';
UPDATE `menu` SET `icon` = 'Management'     WHERE `name` = '资产管理';
UPDATE `menu` SET `icon` = 'OfficeBuilding' WHERE `name` = '工厂与库位';
UPDATE `menu` SET `icon` = 'Avatar'         WHERE `name` = '人员组织';
UPDATE `menu` SET `icon` = 'Setting'        WHERE `name` = '系统管理';

-- 仓储管理 WMS
UPDATE `menu` SET `icon` = 'Download'       WHERE `name` = '入库单';
UPDATE `menu` SET `icon` = 'Upload'         WHERE `name` = '出库单';
UPDATE `menu` SET `icon` = 'Sort'           WHERE `name` = '移库单';
UPDATE `menu` SET `icon` = 'Grid'           WHERE `name` = '库存查询';

-- 渠道管理
UPDATE `menu` SET `icon` = 'User'           WHERE `name` = '客户管理';
UPDATE `menu` SET `icon` = 'School'         WHERE `name` = '供应商管理';
UPDATE `menu` SET `icon` = 'ShoppingBag'    WHERE `name` = '采购单';
UPDATE `menu` SET `icon` = 'PriceTag'       WHERE `name` = '采购价';
UPDATE `menu` SET `icon` = 'Sell'           WHERE `name` = '销售单';
UPDATE `menu` SET `icon` = 'Money'          WHERE `name` = '销售价';

-- 资产管理
UPDATE `menu` SET `icon` = 'Goods'          WHERE `name` = '物料主数据';
UPDATE `menu` SET `icon` = 'Document'       WHERE `name` = '合同管理';

-- 工厂与库位
UPDATE `menu` SET `icon` = 'House'          WHERE `name` = '仓库管理';
UPDATE `menu` SET `icon` = 'Coordinate'     WHERE `name` = '库位结构';

-- 人员组织
UPDATE `menu` SET `icon` = 'UserFilled'     WHERE `name` = '用户管理';
UPDATE `menu` SET `icon` = 'Postcard'       WHERE `name` = '角色管理';
UPDATE `menu` SET `icon` = 'Connection'     WHERE `name` = '部门管理';

-- 系统管理（含数据权限子分组）
UPDATE `menu` SET `icon` = 'ScaleToOriginal' WHERE `name` = '计量单位';
UPDATE `menu` SET `icon` = 'Key'             WHERE `name` = '权限管理';
UPDATE `menu` SET `icon` = 'Menu'            WHERE `name` = '菜单管理';
UPDATE `menu` SET `icon` = 'Tools'           WHERE `name` = '系统配置';
UPDATE `menu` SET `icon` = 'Tickets'         WHERE `name` = '编号规则';
UPDATE `menu` SET `icon` = 'Notebook'        WHERE `name` = '操作日志';
UPDATE `menu` SET `icon` = 'Lock'            WHERE `name` = '数据权限';
UPDATE `menu` SET `icon` = 'Files'           WHERE `name` = '数据资源';
UPDATE `menu` SET `icon` = 'Filter'          WHERE `name` = '行级策略';
UPDATE `menu` SET `icon` = 'Hide'            WHERE `name` = '列级脱敏';

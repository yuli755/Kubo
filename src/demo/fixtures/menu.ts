/**
 * # Demo 菜单树（仅演示模式）
 *
 * 真实环境里菜单由后端 `user/getMyMenuList` 下发，`Console.vue` 据此调用
 * `initVueRouter()` 动态注册路由。演示模式没有后端，于是这里给出一棵**与仓库视图目录
 * 一一对应**的菜单树：
 *
 * - `path`      → 路由绝对路径（`/console/...`，与 `src/config/routes.ts` 的 `console` 父路由对齐）；
 * - `component` → 相对 `src/view` 的组件路径（`/console/wms/input/list` → `src/view/console/wms/input/list.vue`）；
 * - 分组节点不给 `path`/`component`，`registerRoute()` 会跳过注册、只用于展开收起。
 *
 * ⚠️ 增删页面时同步维护：**菜单缺项 = 演示时看不到该页**（不影响真实环境，真实环境走后端下发）。
 */

import type { MenuItem } from '@/router/appRouter'

interface DemoMenuNode extends MenuItem {
  children?: DemoMenuNode[]
}

/** 生成「列表页」菜单节点（占据绝大多数） */
function listPage(
  id: number,
  name: string,
  area: string,
  page: string,
  icon: string,
  orderNo: number,
): DemoMenuNode {
  return {
    id,
    name,
    path: `/console/${area}/${page}`,
    component: `/console/${area}/${page}/list`,
    icon,
    orderNo,
  }
}

/** 生成分组节点（只展开收起，不注册路由） */
function group(id: number, name: string, icon: string, orderNo: number, children: DemoMenuNode[]): DemoMenuNode {
  return { id, name, path: '', icon, orderNo, children }
}

export function demoMenuTree(): DemoMenuNode[] {
  return [
    {
      id: 1,
      name: '工作台',
      path: '/console/index',
      component: '/console/index/index',
      icon: 'DataBoard',
      orderNo: 1,
    },
    group(100, '生产执行 MES', 'SetUp', 10, [
      listPage(101, '生产订单', 'mes', 'order', 'Tickets', 1),
      listPage(102, '生产计划', 'mes', 'plan', 'Calendar', 2),
      listPage(103, '物料清单', 'mes', 'bom', 'Files', 3),
      listPage(104, '工艺路线', 'mes', 'routing', 'Guide', 4),
      listPage(105, '工序管理', 'mes', 'operation', 'Operation', 5),
      listPage(106, '领料单', 'mes', 'picking', 'ShoppingCart', 6),
    ]),
    group(200, '仓储管理 WMS', 'Box', 20, [
      listPage(201, '入库单', 'wms', 'input', 'Download', 1),
      listPage(202, '出库单', 'wms', 'output', 'Upload', 2),
      listPage(203, '移库单', 'wms', 'move', 'Sort', 3),
      listPage(204, '库存查询', 'wms', 'inventory', 'Grid', 4),
    ]),
    group(300, '渠道管理', 'Van', 30, [
      listPage(301, '客户管理', 'channel', 'customer', 'User', 1),
      listPage(302, '供应商管理', 'channel', 'supplier', 'OfficeBuilding', 2),
      listPage(303, '采购单', 'channel', 'purchase', 'ShoppingBag', 3),
      listPage(304, '采购价', 'channel', 'purchasePrice', 'Coin', 4),
      listPage(305, '销售单', 'channel', 'sale', 'Sell', 5),
      listPage(306, '销售价', 'channel', 'salePrice', 'Money', 6),
    ]),
    group(400, '资产与设备', 'Monitor', 40, [
      listPage(401, '设备管理', 'asset', 'device', 'Cpu', 1),
      listPage(402, '物料主数据', 'asset', 'material', 'Box', 2),
      listPage(403, '合同管理', 'asset', 'contract', 'Document', 3),
    ]),
    group(500, '工厂与库位', 'School', 50, [
      listPage(501, '仓库管理', 'factory', 'storage', 'House', 1),
      listPage(502, '库位结构', 'factory', 'structure', 'Coordinate', 2),
    ]),
    group(600, '人员组织', 'UserFilled', 60, [
      listPage(601, '用户管理', 'personnel', 'user', 'User', 1),
      listPage(602, '角色管理', 'personnel', 'role', 'Avatar', 2),
      listPage(603, '部门管理', 'personnel', 'department', 'OfficeBuilding', 3),
    ]),
    group(700, '系统管理', 'Setting', 70, [
      listPage(701, '计量单位', 'system', 'unit', 'ScaleToOriginal', 1),
      listPage(702, '权限管理', 'system', 'permission', 'Key', 2),
      listPage(703, '菜单管理', 'system', 'menu', 'Menu', 3),
      listPage(704, '系统配置', 'system', 'config', 'Tools', 4),
      listPage(705, '编号规则', 'system', 'coderule', 'Postcard', 5),
      listPage(706, '操作日志', 'system', 'log', 'Notebook', 6),
      group(720, '数据权限', 'Lock', 7, [
        listPage(721, '数据资源', 'system/datapermission', 'resource', 'Coin', 1),
        listPage(722, '行级策略', 'system/datapermission', 'policy', 'Filter', 2),
        listPage(723, '列级脱敏', 'system/datapermission', 'fieldPolicy', 'View', 3),
      ]),
    ]),
    group(800, '物联网 IoT', 'Connection', 80, [
      listPage(801, '参数管理', 'iot', 'parameter', 'Odometer', 1),
    ]),
    group(900, '开放平台', 'Link', 90, [
      listPage(901, '开放应用', 'open', 'app', 'Platform', 1),
      listPage(902, '通知公告', 'open', 'notify', 'Bell', 2),
    ]),
  ]
}

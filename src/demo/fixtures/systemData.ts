/**
 * # Demo 看板 / 数据权限报表数据（仅演示模式）
 *
 * 形状严格对齐 `src/api/system/types.ts` 的接口定义：
 * - `DashboardOverviewItem[]`（`dashboard/overview`）
 * - `DataPermissionRuntimeStatus`（`dataResource/runtime-status`）
 * - `DenyLogItem[]`（`dataResource/deny-log`）
 *
 * 数值刻意造得「有差异、有异常项」：安全态势看板只有在存在 SHADOW / 未登记资源 /
 * 拒绝事件时才有内容可看，全绿的数据反而看不出界面的信息层级。
 */

/** 业务概况：各资源当前用户可见行数 */
export function demoOverview(): Array<{ resourceKey: string, label: string, count: number }> {
  return [
    { resourceKey: 'mes.order', label: '生产订单', count: 126 },
    { resourceKey: 'mes.plan', label: '生产计划', count: 48 },
    { resourceKey: 'wms.input', label: '入库单', count: 312 },
    { resourceKey: 'wms.output', label: '出库单', count: 268 },
    { resourceKey: 'wms.inventory', label: '库存记录', count: 1042 },
    { resourceKey: 'channel.purchase', label: '采购单', count: 156 },
    { resourceKey: 'channel.sale', label: '销售单', count: 203 },
    { resourceKey: 'asset.material', label: '物料主数据', count: 421 },
    { resourceKey: 'personnel.user', label: '用户', count: 57 },
  ]
}

/** 数据权限运行时状态：混合 OFF / SHADOW / ENFORCE，并留一个未登记资源 */
export function demoRuntimeStatus(): Record<string, unknown> {
  const resources = [
    ['asset.material', 'ENABLED', 'ENFORCE', true, true, 3, 2],
    ['channel.purchase', 'ENABLED', 'ENFORCE', true, true, 2, 1],
    ['channel.sale', 'ENABLED', 'ENFORCE', true, true, 2, 1],
    ['factory.storage', 'ENABLED', 'ENFORCE', true, false, 1, 0],
    ['factory.structure', 'ENABLED', 'SHADOW', false, false, 1, 0],
    ['mes.order', 'ENABLED', 'SHADOW', false, false, 2, 1],
    ['mes.plan', 'ENABLED', 'OFF', false, false, 0, 0],
    ['wms.input', 'ENABLED', 'SHADOW', false, false, 1, 1],
    ['wms.output', 'ENABLED', 'SHADOW', false, false, 1, 1],
    ['wms.inventory', 'ENABLED', 'ENFORCE', true, false, 2, 0],
    ['personnel.user', 'DRAFT', 'OFF', false, false, 0, 0],
  ] as const

  return {
    globalMode: 'shadow',
    enforceResources: ['asset.material', 'channel.purchase', 'channel.sale', 'factory.storage', 'wms.inventory'],
    denyUnregistered: true,
    rowWriteEnabled: true,
    hierarchyDepartmentEnabled: false,
    unmanagedServiceKeys: [],
    resources: resources.map(([resourceKey, resourceStatus, effectiveMode, inEnforceList, rowWriteGuarded, queryPolicyCount, writePolicyCount], index) => ({
      resourceId: index + 1,
      resourceKey,
      resourceStatus,
      effectiveMode,
      inEnforceList,
      rowWriteGuarded,
      queryPolicyCount,
      writePolicyCount,
    })),
  }
}

/** 拒绝事件（安全态势看板的「最近拒绝」列表） */
export function demoDenyLogs(): Array<Record<string, unknown>> {
  const now = new Date('2026-09-17T09:30:00+08:00').getTime()
  const items: Array<[string, string, string, number | null, string]> = [
    ['wms.inventory', 'unregistered-resource', 'SHADOW', null, 'resourceKey=wms.inventory'],
    ['mes.order', 'row-not-writable', 'ENFORCE', 12, 'id=1042, action=UPDATE'],
    ['channel.purchase', 'write-without-policy', 'ENFORCE', 7, 'id=88, action=DELETE'],
    ['factory.structure', 'export-bypass', 'SHADOW', 12, 'action=EXPORT, fields=11'],
  ]
  return items.map(([resourceKey, reason, mode, userId, detail], index) => ({
    id: index + 1,
    createTime: now - index * 3600_000 * 5,
    resourceKey,
    reason,
    mode,
    userId,
    tenantId: userId === null ? null : 1,
    detail,
  }))
}

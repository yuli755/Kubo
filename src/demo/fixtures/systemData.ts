import type { OperationLogItem } from '@/api/system/types'

/**
 * # Demo 看板 / 数据权限报表数据（仅演示模式）
 *
 * 形状严格对齐 `src/api/system/types.ts` 的接口定义：
 * - `DashboardOverviewItem[]`（`dashboard/overview`）
 * - `DataPermissionRuntimeStatus`（`dataResource/runtime-status`）
 * - `DenyLogItem[]`（`dataResource/deny-logs`）
 * - `OperationLogItem[]`（`dataResource/operation-logs`）
 *
 * 数值刻意造得「有差异、有异常项」：安全态势看板只有在存在 SHADOW / 未登记资源 /
 * 拒绝事件时才有内容可看，全绿的数据反而看不出界面的信息层级。
 */

/** 业务概况：各资源当前用户可见行数 */
export function demoOverview(): Array<{ resourceKey: string, label: string, count: number }> {
  return [
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

/**
 * 操作审计（操作日志页的主数据源）
 *
 * 形状对齐 `OperationLogItem`：快照是**JSON 字符串**（后端 `OperationLogSnapshot` 只序列化
 * 标量字段）。六种动作各造一行，好让列表页的筛选下拉、详情页的「变更前 / 变更后」空态
 * 与 Diff 页签在演示时都能被看到。
 *
 * 三处刻意贴合后端真实行为，不要「为了好看」改回去：
 * 1. **价格字段是 `h:` 摘要而不是数字**。`MaterialEntity.purchasePrice / salePrice` 标了
 *    `@SensitiveField`，`OperationLogSnapshot.isNeverPlaintext` 会把它们换成
 *    `MaskedValueDigest.ofValue(...)`（SHA-256 前 12 位）。演示数据给明文，就等于告诉
 *    看的人「审计表里存着明文价格」——那是这个功能明确拒绝的结果。
 *    摘要仍然随值变化，所以 Diff 页签照旧能报「这个字段改了」，只是看不出改成多少。
 * 2. **没有 `wms.inventory` 一行**。库存没标 `@OperationLog`（每次移库都写会把审计表刷爆），
 *    演示里出现它就会谎报覆盖面。
 * 3. **`DISABLE` 用供应商而不是单据**。单据类资源排除了 `Delete`，「停用」才是用户退役
 *    一条数据的唯一手段，这正是后端给它单独一个动作码（而不是复用 `UPDATE`）的理由。
 */
export function demoOperationLogs(): OperationLogItem[] {
  const now = new Date('2026-09-17T18:05:00+08:00').getTime()
  const hour = 3600_000
  return [
    {
      id: 1,
      createTime: now,
      resourceKey: 'asset.material',
      action: 'UPDATE',
      userId: 1,
      tenantId: 1,
      targetId: 3,
      targetLabel: 'MA20260003',
      beforeSnapshot: JSON.stringify({ id: 3, code: 'MA20260003', name: '固态硬盘 1TB', purchasePrice: 'h:41d06a0e9908', salePrice: 'h:4753265241f8', isDisabled: false }),
      afterSnapshot: JSON.stringify({ id: 3, code: 'MA20260003', name: '固态硬盘 1TB（企业级）', purchasePrice: 'h:6a4dfc937b5c', salePrice: 'h:4753265241f8', isDisabled: false }),
    },
    {
      id: 2,
      createTime: now - hour * 2,
      resourceKey: 'channel.supplier',
      action: 'DISABLE',
      userId: 1,
      tenantId: 1,
      targetId: 12,
      targetLabel: 'SUP20260012',
      beforeSnapshot: JSON.stringify({ id: 12, code: 'SUP20260012', name: '华东电子耗材', contact: '王工', isDisabled: false }),
      afterSnapshot: JSON.stringify({ id: 12, code: 'SUP20260012', name: '华东电子耗材', contact: '王工', isDisabled: true }),
    },
    {
      id: 3,
      createTime: now - hour * 3,
      resourceKey: 'channel.supplier',
      action: 'CREATE',
      userId: 1,
      tenantId: 1,
      targetId: 12,
      targetLabel: 'SUP20260012',
      beforeSnapshot: null,
      afterSnapshot: JSON.stringify({ id: 12, code: 'SUP20260012', name: '华东电子耗材', contact: '王工', isDisabled: false }),
    },
    {
      id: 4,
      createTime: now - hour * 7,
      resourceKey: 'channel.customer',
      action: 'DELETE',
      userId: 2,
      tenantId: 1,
      targetId: 88,
      targetLabel: 'CUS20260088',
      beforeSnapshot: JSON.stringify({ id: 88, code: 'CUS20260088', name: '南方贸易（已注销）', isDisabled: false }),
      afterSnapshot: null,
    },
    {
      id: 5,
      createTime: now - hour * 26,
      resourceKey: 'asset.material',
      action: 'ENABLE',
      userId: 2,
      tenantId: 1,
      targetId: 1,
      targetLabel: 'MA20260001',
      beforeSnapshot: JSON.stringify({ id: 1, code: 'MA20260001', name: 'MacBook Pro M4 Max', salePrice: 'h:5fa8b3fee6fe', isDisabled: true }),
      afterSnapshot: JSON.stringify({ id: 1, code: 'MA20260001', name: 'MacBook Pro M4 Max', salePrice: 'h:5fa8b3fee6fe', isDisabled: false }),
    },
    {
      id: 6,
      createTime: now - hour * 31,
      resourceKey: 'channel.salePrice',
      action: 'PUBLISH',
      userId: 1,
      tenantId: 1,
      targetId: 5,
      targetLabel: 'SP20260005',
      beforeSnapshot: JSON.stringify({ id: 5, code: 'SP20260005', name: '2026 Q3 华东价目表', isPublished: false }),
      afterSnapshot: JSON.stringify({ id: 5, code: 'SP20260005', name: '2026 Q3 华东价目表', isPublished: true }),
    },
    {
      id: 7,
      createTime: now - hour * 50,
      resourceKey: 'asset.material',
      action: 'CREATE',
      userId: 1,
      tenantId: 1,
      targetId: 2,
      targetLabel: 'MA20260002',
      beforeSnapshot: null,
      afterSnapshot: JSON.stringify({ id: 2, code: 'MA20260002', name: '键盘', purchasePrice: 'h:184999fc4112', salePrice: 'h:78154e8350b0', isDisabled: false }),
    },
  ]
}

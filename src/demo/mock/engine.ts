/**
 * # Demo Mock 引擎（仅演示模式）
 *
 * 把「HTTP 请求」映射为「生成好的假数据」，是演示模式的数据中枢。三类端点：
 *
 * 1. **身份类**：`user/getMyInfo`、`user/getMyTenantList`、`user/getMyPermissionList`、
 *    `user/getMyMenuList` —— 让控制台初始化流程（`Console.vue`）在无后端时也能跑通：
 *    有用户、有租户、有全部权限、有完整菜单树；
 * 2. **看板/报表类**：`dashboard/overview`、`dataResource/runtime-status`、
 *    `dataResource/deny-log` —— 给安全态势看板与数据权限页喂形状正确的数据；
 * 3. **资源 CRUD**：`{resource}/getPage|getDetail|add|update|delete|…` —— 由
 *    `./specs` 的生成器按资源名产出行数据。
 *
 * 未命中的端点统一返回 `null` / 空列表，页面自行降级（演示模式不追求全端点覆盖）。
 */

import type { Row } from './specs'
import { demoTenants, demoUserInfo } from '../fixtures/identity'
import { demoMenuTree } from '../fixtures/menu'
import { demoPermissions } from '../fixtures/permissions'
import { demoDenyLogs, demoOverview, demoRuntimeStatus } from '../fixtures/systemData'
import { buildRow, totalOf } from './specs'

/** 读取嵌套字段（`filter.xxx` 这类）时的安全取值 */
type Payload = Record<string, unknown>

function asRecord(value: unknown): Payload {
  return value && typeof value === 'object' ? (value as Payload) : {}
}

function numeric(...values: unknown[]): number | undefined {
  for (const value of values) {
    const parsed = typeof value === 'string' ? Number(value) : value
    if (typeof parsed === 'number' && Number.isFinite(parsed)) {
      return parsed
    }
  }
  return undefined
}

function pageSizeOf(payload: Payload): number {
  const page = asRecord(payload.page)
  return numeric(payload.pageSize, page.pageSize, payload.size) ?? 15
}

function pageNumOf(payload: Payload): number {
  const page = asRecord(payload.page)
  return numeric(payload.pageNum, page.pageNum, payload.current) ?? 1
}

/** 过滤条件宽松匹配：字符串包含（忽略大小写），数字/布尔全等 */
function matchFilter(row: Row, filter: Payload): boolean {
  for (const [key, expected] of Object.entries(filter)) {
    if (expected === undefined || expected === null || expected === '') {
      continue
    }
    const actual = row[key]

    // 关联对象过滤（如 filter.unit = { name: '件' }）→ 用其 name / code 参与比较
    if (typeof expected === 'object') {
      const expectedRecord = asRecord(expected)
      const probe = String(expectedRecord.name ?? expectedRecord.code ?? '')
      if (!probe) {
        continue
      }
      if (!String(actual ?? '').toLowerCase().includes(probe.toLowerCase())) {
        return false
      }
      continue
    }

    if (typeof actual === 'number' || typeof expected === 'number') {
      if (Number(actual) !== Number(expected)) {
        return false
      }
      continue
    }

    if (!String(actual ?? '').toLowerCase().includes(String(expected).toLowerCase())) {
      return false
    }
  }
  return true
}

/** 生成分页响应（`{list,total,pageNum,pageSize}`，兼容 airpower 与 typed client 两种形状） */
function buildPage(resourceKey: string, payload: Payload): Row {
  const total = totalOf(resourceKey)
  const pageSize = Math.max(1, Math.min(200, pageSizeOf(payload)))
  const pageNum = Math.max(1, pageNumOf(payload))
  const filter = asRecord(payload.filter)

  const all: Row[] = []
  for (let index = 1; index <= total; index++) {
    all.push(buildRow(resourceKey, index))
  }
  const filtered = Object.keys(filter).length ? all.filter(row => matchFilter(row, filter)) : all

  const start = (pageNum - 1) * pageSize
  const list = filtered.slice(start, start + pageSize).map(row => ({ ...row }))

  return {
    list,
    total: filtered.length,
    pageNum,
    pageSize,
  }
}

/** 单条详情：按 id 生成，保证与列表里的同一行一致 */
function buildDetail(resourceKey: string, payload: Payload): Row {
  const id = numeric(payload.id, asRecord(payload.data).id) ?? 1
  return { ...buildRow(resourceKey, Math.max(1, id)) }
}

const LIST_ACTIONS = new Set(['getPage', 'getList', 'getAll', 'getTableList', 'getSelectList'])
const DETAIL_ACTIONS = new Set(['getDetail', 'get', 'getOne', 'getInfo'])
/** 写操作：演示模式一律「成功」，不做真实落库（刷新即回到初始数据） */
const WRITE_ACTIONS = new Set([
  'add',
  'create',
  'insert',
  'update',
  'edit',
  'save',
  'delete',
  'remove',
  'disable',
  'enable',
  'audit',
  'reject',
  'publish',
  'finish',
  'start',
  'pause',
  'stop',
  'enforce',
  'addFinish',
  'setBillDetailsAllFinished',
  'addOrderDetail',
  'addDetail',
  'updateDetail',
  'deleteDetail',
  'replay',
  'replayAll',
  'discard',
  'resetPassword',
  'updateMyInfo',
  'updateMyPassword',
  'logout',
])

/** 身份类端点（无需登录即可返回） */
function resolveIdentity(resourcePath: string, action: string): { hit: boolean, data?: unknown } {
  if (resourcePath === 'user') {
    if (action === 'getMyInfo' || action === 'getCurrentUser') {
      return { hit: true, data: demoUserInfo() }
    }
    if (action === 'getMyTenantList') {
      return { hit: true, data: demoTenants() }
    }
    if (action === 'getMyPermissionList') {
      return { hit: true, data: demoPermissions() }
    }
    if (action === 'getMyMenuList') {
      return { hit: true, data: demoMenuTree() }
    }
    if (action === 'login' || action === 'loginViaEmail' || action === 'loginViaPhone') {
      return { hit: true, data: 'demo-token' }
    }
    if (action === 'refreshToken' || action === 'refresh') {
      return { hit: true, data: { accessToken: 'demo-token', refreshToken: 'demo-refresh' } }
    }
  }
  return { hit: false }
}

/** 看板 / 数据权限报表端点 */
function resolveReport(resourcePath: string, action: string): { hit: boolean, data?: unknown } {
  if (resourcePath === 'dashboard' && action === 'overview') {
    return { hit: true, data: demoOverview() }
  }
  if (resourcePath === 'dataResource') {
    if (action === 'runtime-status') {
      return { hit: true, data: demoRuntimeStatus() }
    }
    if (action === 'deny-log') {
      return { hit: true, data: demoDenyLogs() }
    }
    if (action === 'enforce-readiness') {
      return { hit: true, data: [] }
    }
  }
  if (resourcePath === 'dataFieldPolicy' && (action === 'my-mask-map' || action === 'my-mask')) {
    // 不遮蔽：演示时让明文可见（列级脱敏的真实行为依赖后端策略）
    return { hit: true, data: {} }
  }
  return { hit: false }
}

/**
 * 解析一次请求应返回的数据。
 *
 * @param resourcePath URL 中动作段之前的部分，如 `wms/input`、`dashboard`
 * @param action      动作段，如 `getPage`、`runtime-status`
 * @param payload     合并后的请求体 / 查询参数 / 路径参数
 */
export function resolveMock(resourcePath: string, action: string, payload: Payload): unknown {
  const resourceKey = resourcePath.split('/').filter(Boolean).pop() ?? resourcePath

  const identity = resolveIdentity(resourcePath, action)
  if (identity.hit) {
    return identity.data
  }

  const report = resolveReport(resourcePath, action)
  if (report.hit) {
    return report.data
  }

  if (LIST_ACTIONS.has(action)) {
    return buildPage(resourceKey, payload)
  }
  if (DETAIL_ACTIONS.has(action)) {
    return buildDetail(resourceKey, payload)
  }
  if (action === 'getTree') {
    const page = buildPage(resourceKey, { ...payload, pageSize: 24 })
    const list = page.list as Row[]
    // 树形页：把前 6 行作为根，其余挂到「父级」下，验证树表的展开/折叠视觉
    const roots = list.slice(0, 6).map(row => ({
      ...row,
      parentId: 0,
      children: list.slice(6, 12).map(child => ({ ...child, parentId: row.id, children: [] })),
    }))
    return { list: roots, total: roots.length, pageNum: 1, pageSize: 24 }
  }
  if (WRITE_ACTIONS.has(action)) {
    return true
  }
  if (action.startsWith('get')) {
    // 未覆盖的读取端点：返回空列表，让页面走「空状态」分支而不是报错
    return {}
  }
  return true
}

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
import { demoPermissions, demoPermissionTree } from '../fixtures/permissions'
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

/**
 * 分页信封端点：返回 `{ list, total, pageNum, pageSize }`。
 *
 * 依据：框架 `framework/http.ts` 的 `getPage(): Promise<QueryResponsePage<E>>`；
 * airpower 的 `urlGetPage = 'getPage'`（`getPage` 里读 `responsePage.list`）。
 */
/**
 * 树形实体（后端实现 `ITree`）：它们的 `getList` 返回的是**嵌套树**而不是平铺列表。
 *
 * 依据（后端源码，不是约定俗成）：
 * - `StorageService#beforeGetList/#afterGetList`：空 `parentId` 兜成根，再递归回填 `children`；
 * - `StructureService` 同型；`DepartmentController#getList`、`MenuController#getList`、
 *   `PermissionController#afterGetList` 再套一层 `TreeUtil.buildTreeList`。
 * 前端 `useTable({ tree: true })` 正是靠这一点 —— 别在这里按"平铺列表"造数，
 * 否则演示模式的树表看起来是空的。
 */
const TREE_RESOURCES = new Set(['storage', 'structure', 'department', 'menu', 'permission'])

/** 把平铺行拼成"根 + 一层子节点"的树（演示数据量小，够验证展开/折叠） */
function treeRoots(list: Row[]): Row[] {
  return list.slice(0, 6).map(row => ({
    ...row,
    parentId: 0,
    children: list.slice(6, 12).map(child => ({ ...child, parentId: row.id, children: [] })),
  }))
}

const PAGE_ACTIONS = new Set(['getPage', 'getTableList'])
/**
 * 列表端点：返回**裸数组**。
 *
 * 依据（两边都是硬契约，给错形状就在运行期炸）：
 * - 框架 `framework/http.ts` 的 `getList(): Promise<E[]>`；
 * - airpower `AbstractCurdService.getList` 走 `Http.requestModelList`，
 *   里面直接 `result.map(...)` —— 收到信封会抛 `TypeError: result.map is not a function`，
 *   而且是在 `nextTick`/定时器里抛，表现为**未处理拒绝**（2026-09 的 `pnpm test` 就是因为
 *   这里返回了信封而有 7 个未处理拒绝、退出码 1，用例却全绿）。
 *
 * `getAll` / `getSelectList` 在 airpower 2.9 与本仓都没有调用点，是按同一语义保留的别名。
 */
const ARRAY_ACTIONS = new Set(['getList', 'getAll', 'getSelectList'])
const DETAIL_ACTIONS = new Set(['getDetail', 'get', 'getOne', 'getInfo'])
/**
 * 计数端点（后端 `{baseUrl}/getCount`）：返回**裸数字**（`Json.data(service.getMapperCount(filter))`）。
 * 列表页上方的统计条按状态各查一次 —— 形状给错（例如返回 `{}`）会渲染成 `NaN`/`-`，
 * 而且不报错，所以这里单独列出来，别落进 `startsWith('get')` 的空对象兜底。
 */
const COUNT_ACTIONS = new Set(['getCount'])
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

/**
 * 角色授权树端点（F4 批 3 起）。
 *
 * 菜单/权限两棵授权树**不能走通用树生成器**：
 * - `menu/getList` 返回的是应用菜单树本身（与 `user/getMyMenuList` 同源，
 *   演示时授权的菜单名与左侧导航一致，评审时对得上）；
 * - `permission/getList` 返回按真实权限码分组的树（identity 提交给后端，
 *   演示数据里编造的编码会让人误判授权对象）。
 *
 * ⚠️ 2026-09-19：这里原先是 `getTreeList`。后端**没有**这个端点（树是 `getList` 的后置行为），
 * 演示适配器却实现了它 —— 于是"预览一切正常、连真后端 404、树永远空"。现在两边都按 `getList` 走。
 */
function resolveAuthTree(resourcePath: string, action: string): { hit: boolean, data?: unknown } {
  if (action !== 'getList') {
    return { hit: false }
  }
  if (resourcePath === 'menu') {
    return { hit: true, data: demoMenuTree() }
  }
  if (resourcePath === 'permission') {
    return { hit: true, data: demoPermissionTree() }
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

  const authTree = resolveAuthTree(resourcePath, action)
  if (authTree.hit) {
    return authTree.data
  }

  if (PAGE_ACTIONS.has(action)) {
    return buildPage(resourceKey, payload)
  }
  if (COUNT_ACTIONS.has(action)) {
    return buildPage(resourceKey, payload).total
  }
  if (action === 'getStat') {
    // 读侧统一形状（后端 `base/mapper/ReadStat`）：行数 + 已发布行数，不做敏感字段聚合
    const total = buildPage(resourceKey, payload).total
    return { total, publishedTotal: 0 }
  }
  if (action === 'getList' && TREE_RESOURCES.has(resourceKey)) {
    // 树形实体的 `getList` 返回**裸数组的根节点树**（后端 `ITree` 契约）。
    // menu / permission 走上面的 resolveAuthTree（返回真实菜单树 / 权限树）。
    const page = buildPage(resourceKey, { ...payload, pageSize: 24 })
    return treeRoots(page.list as Row[])
  }
  // 之所以还按**名字后缀**兜一层：存量 airpower 服务里有一批 `getXxxList`
  // （`coderule` 的 `getFieldList` / `getParamList`、`getSelectList` …），它们同样走
  // `requestModelList`（裸数组）。落到下面"未覆盖端点返回 {}"的分支会在 `nextTick` 里炸成未处理拒绝。
  if (ARRAY_ACTIONS.has(action) || /(?:List|Items|Fields|Params|Options|All)$/.test(action)) {
    // 只取信封里的 list：见 ARRAY_ACTIONS 的注释（airpower 直接 `.map`）
    return buildPage(resourceKey, payload).list
  }
  if (DETAIL_ACTIONS.has(action)) {
    return buildDetail(resourceKey, payload)
  }
  if (action === 'getTree') {
    // `getTree` 是 typed client（`api/personnel`、`api/system`）的端点，返回**信封**；
    // 它与 `getList` 的裸数组形状不同，别合并。
    const page = buildPage(resourceKey, { ...payload, pageSize: 24 })
    const list = page.list as Row[]
    const roots = treeRoots(list)
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

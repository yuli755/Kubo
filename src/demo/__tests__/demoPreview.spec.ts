import type { demoPermissionTree } from '@/demo/fixtures/permissions'
import { existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import { demoMenuTree } from '@/demo/fixtures/menu'
import { demoPermissions } from '@/demo/fixtures/permissions'
import { createDemoAdapter, parseRequestUrl } from '@/demo/mock/adapter'

/**
 * # 演示模式可用性门禁
 *
 * 演示模式（`pnpm dev:demo`）的价值全在「**打开就能看到页面**」——一旦菜单指向不存在的
 * 视图文件、或身份接口返回空，用户看到的是空白控制台/白屏，而 lint/typecheck 全绿。
 * 这类"预览静默失效"只能靠契约测试拦，故本文件锁三件事：
 *
 * 1. **菜单 ↔ 视图文件一一对应**：每个菜单叶子节点的 `component` 必须在 `src/view` 下
 *    真实存在（`registerRoute()` 找不到组件时会注册出 `component: undefined` 的路由，
 *    点击后白屏，控制台不报错）；
 * 2. **身份链完整**：`user/getMyInfo|getMyTenantList|getMyPermissionList|getMyMenuList`
 *    四个端点必须被 mock 命中且非空 —— 它们是 `Console.vue` 初始化流程的全部输入；
 * 3. **降级不抛错**：未覆盖的端点返回空对象/空列表而非异常，页面走空状态。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')

/** 扁平化菜单树，取出所有带 component 的叶子节点 */
function menuLeaves(): { name: string, component: string }[] {
  const leaves: { name: string, component: string }[] = []
  const walk = (nodes: ReturnType<typeof demoMenuTree>): void => {
    for (const node of nodes) {
      if (node.component) {
        leaves.push({ name: node.name, component: node.component })
      }
      if (node.children && node.children.length > 0) {
        walk(node.children as ReturnType<typeof demoMenuTree>)
      }
    }
  }
  walk(demoMenuTree())
  return leaves
}

/** 以 [0,0] 延迟创建适配器，测试不需要"看得见的加载态" */
const adapter = createDemoAdapter([0, 0])

/** 造一个最小可用的 axios 请求配置 */
function request(url: string, options: { params?: Record<string, unknown>, data?: unknown, responseType?: string } = {}) {
  return adapter({
    url,
    baseURL: '/api',
    params: options.params,
    data: options.data,
    responseType: options.responseType,
    headers: {},
  } as never) as Promise<{ data: { code: number, data: unknown } }>
}

describe('演示模式 · 菜单与视图文件一一对应', () => {
  it('每个菜单叶子节点都指向真实存在的 .vue 视图（否则演示时点击白屏）', () => {
    const leaves = menuLeaves()
    // 自检：扫不到叶子说明菜单结构变了，本用例失效
    expect(leaves.length, '菜单树没有解析出任何叶子节点').toBeGreaterThan(20)

    const missing = leaves.filter(leaf => !existsSync(resolve(ROOT, `src/view${leaf.component}.vue`)))
    expect(
      missing.map(m => `${m.name} → src/view${m.component}.vue`),
      '演示菜单指向了不存在的视图文件：点击这些菜单会白屏（不报错），文件不做存在性检查抓不到',
    ).toEqual([])
  })

  it('菜单 path 与 component 同源（约定：component 缺省取 path，视图路径 == 路由路径）', () => {
    const walk = (nodes: ReturnType<typeof demoMenuTree>): void => {
      for (const node of nodes) {
        if (node.path) {
          // 列表页 component 可以省略（前端 registerRoute 会回落到 path 本身）；
          // 一旦显式写了 component，就必须与 path 同源，否则菜单高亮/跳转会错位。
          const component = node.component || node.path
          expect(
            component === node.path || component === `${node.path}/index`,
            `${node.name}: component(${component}) 与 path(${node.path}) 不同源，菜单高亮/跳转会错位`,
          ).toBe(true)
        }
        if (node.children) {
          walk(node.children as ReturnType<typeof demoMenuTree>)
        }
      }
    }
    walk(demoMenuTree())
  })
})

describe('演示模式 · 控制台初始化链（Console.vue 的四个输入）', () => {
  it('getMyInfo 返回可用于顶栏用户卡的用户信息', async () => {
    const res = await request('/user/getMyInfo')
    expect(res.data.code).toBe(200)
    const user = res.data.data as Record<string, unknown>
    expect(user.nickname).toBeTruthy()
    expect(user.isSuperAdmin).toBe(true)
  })

  it('getMyTenantList 返回非空租户列表（否则租户切换器空白）', async () => {
    const res = await request('/user/getMyTenantList')
    expect(Array.isArray(res.data.data)).toBe(true)
    expect((res.data.data as unknown[]).length).toBeGreaterThan(0)
  })

  it('getMyPermissionList 返回非空权限码（否则按钮全被隐藏、页面像没功能）', async () => {
    const res = await request('/user/getMyPermissionList')
    const codes = res.data.data as string[]
    expect(Array.isArray(codes)).toBe(true)
    expect(codes.length).toBeGreaterThan(50)
    // 权限码形状：`{Entity}_{action}`，抽查一个后端确实存在的动作
    expect(codes.some(code => code.endsWith('_getPage'))).toBe(true)
  })

  it('getMyMenuList 返回非空菜单树（否则侧栏空白）', async () => {
    const res = await request('/user/getMyMenuList')
    const tree = res.data.data as unknown[]
    expect(Array.isArray(tree)).toBe(true)
    expect(tree.length).toBeGreaterThan(3)
  })

  it('演示权限码与 fixtures 自洽（menu fixture 不提供权限时用同一份来源）', () => {
    expect(demoPermissions()).toContain('Menu_getMyMenuList')
  })
})

describe('演示模式 · URL 解析与降级', () => {
  it('parseRequestUrl 能剥离域名/查询串/ /api 前缀', () => {
    expect(parseRequestUrl('https://kubo.example.com/api/user/getMyInfo?x=1&y=2')).toEqual({
      resourcePath: 'user',
      action: 'getMyInfo',
    })
    expect(parseRequestUrl('/api/wms/input/getPage')).toEqual({
      resourcePath: 'wms/input',
      action: 'getPage',
    })
  })

  it('带路径 id 的详情端点（`/{res}/getDetail/{id}`）命中的是**单条详情**，不是降级空对象', async () => {
    // 回归（2026-09 批 4 发现）：URL 末段是数字时，若不把「动作段」还原成它的**前一段**，
    // 引擎看到的是 `resourcePath='wms/input/getDetail'`、`action='3'` —— 任何端点都命不中，
    // 最后落到「未覆盖的读取端点」返回 `true`，页面表现是**详情弹窗字段全是 '-'、明细永远为空**；
    // 而列表页完全正常，很难联想到 URL 解析。自研 client 的 `getDetail` 正是 `GET /{res}/getDetail/{id}`。
    const res = await request('/wms/input/getDetail/3')
    const row = res.data.data as { billCode?: string, details?: unknown[] }
    expect(row.billCode, '详情端点没有返回单据主表字段').toBeTruthy()
    expect(Array.isArray(row.details)).toBe(true)
    expect((row.details as unknown[]).length).toBeGreaterThan(0)
  })

  it('cRUD 读取端点返回分页信封（演示时表格有数据可看）', async () => {
    const res = await request('/wms/input/getPage', { params: { pageNum: 1, pageSize: 15 } })
    expect(res.data.code).toBe(200)
    const page = res.data.data as { list?: unknown[], total?: number }
    expect(Array.isArray(page.list)).toBe(true)
    expect((page.list as unknown[]).length).toBeGreaterThan(0)
    expect(typeof page.total).toBe('number')
  })

  it('`getList` 返回裸数组、`getPage` 才返回信封；树形实体的树也从 `getList` 出（形状不同，别合并）', async () => {
    // 旧页（personnel/wms 的树、wms 库存选择器）用 airpower 的 `getList`，其
    // `Http.requestModelList` 直接 `result.map(...)`：收到信封会抛 TypeError，
    // 而且抛在 nextTick 里 → 变成未处理拒绝（用例全绿但 pnpm test 退出码 1）。
    const list = await request('/storage/getList', { params: { pageNum: 1, pageSize: 24 } })
    expect(Array.isArray(list.data.data)).toBe(true)
    expect((list.data.data as unknown[]).length).toBeGreaterThan(0)

    // 树形实体：`getList` 返回的是**带 children 的根节点数组**（后端 ITree 契约）。
    // 这条断言守的是 2026-09-19 修的那处「演示正常、真机 404」：端点曾经是 getTreeList。
    const roots = list.data.data as { children?: unknown[] }[]
    expect(roots.some(root => (root.children?.length ?? 0) > 0), 'storage/getList 没返回树').toBe(true)

    const page = await request('/storage/getPage', { params: { pageNum: 1, pageSize: 24 } })
    expect(Array.isArray((page.data.data as { list?: unknown[] }).list)).toBe(true)
  })

  it('角色授权树：菜单树用真实菜单、权限树用真实权限码（不是造出来的假行）', async () => {
    // 授权弹窗是**真的把 identity 提交给后端**的地方：演示数据里编造的权限码
    // 会让人误判"我授权了什么"。所以这两棵树必须是 fixtures 本身。
    const menu = await request('/menu/getList', { data: {} })
    const menuNames: string[] = []
    const walkMenu = (nodes: { name: string, children?: unknown[] }[]): void => {
      for (const node of nodes) {
        menuNames.push(node.name)
        if (node.children?.length) {
          walkMenu(node.children as { name: string, children?: unknown[] }[])
        }
      }
    }
    walkMenu(menu.data.data as { name: string, children?: unknown[] }[])
    expect(menuNames).toContain('部门管理')
    expect(menuNames.length).toBeGreaterThan(demoMenuTree().length)

    const permission = await request('/permission/getList', { data: {} })
    const roots = permission.data.data as ReturnType<typeof demoPermissionTree>
    expect(Array.isArray(roots)).toBe(true)
    const identities = roots.flatMap(root => root.children.map(child => child.identity))
    // 与真实权限码同源：授权弹窗里勾的就是后端认的编码
    expect(identities).toContain('Role_authorizeMenu')
    expect(identities.every(code => demoPermissions().includes(code)), '演示权限树出现了权限常量里没有的编码').toBe(true)
  })

  it('用户行带部门归属与启停状态（演示时部门筛选与状态列才有东西可看）', async () => {
    // 用户页左侧部门树点一下会把 `filter.departmentId` 放进请求体 —— 行里没有这个字段时，
    // 演示环境会筛出空表（看起来像"功能坏了"而不是"没数据"）。
    const page = await request('/user/getPage', { data: { pageNum: 1, pageSize: 15, filter: { departmentId: 1 } } })
    const rows = (page.data.data as { list: { departmentId?: unknown, isDisabled?: unknown }[] }).list
    expect(rows.length, '按部门 1 筛选后一行都没有：演示行的 departmentId 与部门树对不上').toBeGreaterThan(0)
    expect(rows.every(row => row.departmentId === 1)).toBe(true)

    // 状态列/启停按钮读 `isDisabled`（K 侧的公共列）：全 false 的话"已禁用"与"启用"按钮永远不出现
    const all = await request('/user/getPage', { data: { pageNum: 1, pageSize: 30 } })
    const allRows = (all.data.data as { list: { isDisabled?: boolean }[] }).list
    expect(allRows.some(row => row.isDisabled === true), '演示数据里没有一条禁用记录').toBe(true)
    expect(allRows.some(row => row.isDisabled === false), '演示数据里没有一条启用记录').toBe(true)
  })

  it('未覆盖的读取端点降级为空对象而不是抛错（页面走空状态）', async () => {
    const res = await request('/some/unknown/getSomething')
    expect(res.data.code).toBe(200)
  })

  it('导出类请求返回 Blob（避免浏览器把 JSON 当文件下载）', async () => {
    const res = await request('/wms/input/export', { responseType: 'blob' })
    expect(res.data).toBeInstanceOf(Blob)
  })

  it('写操作（add/update/delete）返回成功，演示时表单可提交', async () => {
    for (const action of ['add', 'update', 'delete']) {
      const res = await request(`/wms/input/${action}`, { data: { id: 1 } })
      expect(res.data.code).toBe(200)
    }
  })
})

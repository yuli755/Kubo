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

  it('cRUD 读取端点返回分页信封（演示时表格有数据可看）', async () => {
    const res = await request('/wms/input/getPage', { params: { pageNum: 1, pageSize: 15 } })
    expect(res.data.code).toBe(200)
    const page = res.data.data as { list?: unknown[], total?: number }
    expect(Array.isArray(page.list)).toBe(true)
    expect((page.list as unknown[]).length).toBeGreaterThan(0)
    expect(typeof page.total).toBe('number')
  })

  it('`getList`/`getTreeList` 返回裸数组，`getPage` 才返回信封（形状不同，别合并）', async () => {
    // 旧页（personnel/wms 的树、wms 库存选择器）用 airpower 的 `getList`，其
    // `Http.requestModelList` 直接 `result.map(...)`：收到信封会抛 TypeError，
    // 而且抛在 nextTick 里 → 变成未处理拒绝（用例全绿但 pnpm test 退出码 1）。
    const list = await request('/storage/getList', { params: { pageNum: 1, pageSize: 24 } })
    expect(Array.isArray(list.data.data)).toBe(true)
    expect((list.data.data as unknown[]).length).toBeGreaterThan(0)

    const tree = await request('/storage/getTreeList', { params: { pageNum: 1, pageSize: 24 } })
    expect(Array.isArray(tree.data.data)).toBe(true)

    const page = await request('/storage/getPage', { params: { pageNum: 1, pageSize: 24 } })
    expect(Array.isArray((page.data.data as { list?: unknown[] }).list)).toBe(true)
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

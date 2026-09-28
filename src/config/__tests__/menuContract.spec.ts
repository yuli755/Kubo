import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { demoMenuTree } from '@/demo/fixtures/menu'

/**
 * # 菜单 ↔ 路由 ↔ 视图 三方契约（严格对账）
 *
 * 「点开菜单是空白页 / 名字和页面对不上 / 路径跳不过去」这类问题在运行时才有症状，
 * 静态检查全绿。这里把四条线钉死：
 *
 * 1. **演示菜单**（`src/demo/fixtures/menu.ts`）里每个页面的 `component`（缺省取 `path`）
 *    都能在 `src/view` 下找到 `.vue` 文件；
 * 2. **磁盘上的列表页**都能被菜单点到（没有「做了页面却没入口」的半成品）；
 * 3. **后端菜单种子**（`MenuService.initMenu()`）里的每个 `path` 也都能找到视图，
 *    且**名字 / 路径与演示菜单逐条一致**（两边各写一份必然漂移，所以由 CI 对账）；
 * 4. 已删除的域（`/console/mes/**`）不得再出现在任何一处菜单里 —— 这些行会注册出
 *    「没有组件的路由」，点进去是空白页。
 */

const ROOT = process.cwd()
const VIEW_DIR = join(ROOT, 'src/view')
const MENU_SERVICE = join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/module/system/menu/MenuService.java')

interface MenuLeaf {
  id: number
  name: string
  path?: string
  component?: string
  children?: MenuLeaf[]
}

function leaves(nodes: MenuLeaf[]): MenuLeaf[] {
  const out: MenuLeaf[] = []
  for (const node of nodes) {
    if (node.children?.length) {
      out.push(...leaves(node.children))
    }
    else if (node.path) {
      out.push(node)
    }
  }
  return out
}

/** 路由路径 → 视图文件（约定：`component` 优先，其次 `path`；均为 `/src/view` 下的相对路径） */
function viewFileOf(leaf: MenuLeaf): string {
  return join(VIEW_DIR, `${leaf.component || leaf.path}.vue`)
}

/** 收集磁盘上「可直达的列表页/页面视图」（排除 component/ 子目录里的弹窗、编辑器） */
function collectPageViews(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === 'component') {
        continue
      }
      out.push(...collectPageViews(full))
    }
    else if (entry.endsWith('.vue')) {
      // 归一成正斜杠：join() 在 Windows 上产出反斜杠路径，而菜单侧的 component/path
      // 一律是正斜杠（`/console/wms/input/list`）。不归一则 referenced 永不命中，
      // 磁盘上每一个页面视图都会被判成「没有菜单入口」的孤儿（Windows 恒红 30 个）。
      out.push(`/${full.slice(VIEW_DIR.length + 1, -4).replace(/\\/g, '/')}`)
    }
  }
  return out
}

/** 后端菜单种子的一条记录（`listPage(...)` 或 `setName(...).setPath(...)` 链） */
interface BackendSeed {
  name: string
  path: string
  component?: string
}

/**
 * 解析 `MenuService.initMenu()` 的种子。
 *
 * 种子有两种写法：列表页用 `listPage("名称", "路径", 父级)`（不写 component，靠
 * 「路径 == 视图路径」约定），工作台这类非列表页用链式 `setName().setPath().setComponent()`。
 */
function backendSeeds(): BackendSeed[] {
  const source = readFileSync(MENU_SERVICE, 'utf8')
  const seeds: BackendSeed[] = []
  for (const match of source.matchAll(/listPage\("([^"]+)",\s*"([^"]+)"/g)) {
    seeds.push({ name: match[1], path: match[2] })
  }
  for (const match of source.matchAll(/setName\("([^"]+)"\)([\s\S]{0,400}?);/g)) {
    const block = match[2]
    const path = block.match(/setPath\("(\/console[^"]*)"\)/)?.[1]
    const component = block.match(/setComponent\("(\/console[^"]*)"\)/)?.[1]
    if (path) {
      seeds.push({ name: match[1], path, component })
    }
  }
  return seeds
}

/** 后端种子 → 视图文件（与前端同一条约定：`component` 优先，其次 `path`） */
function backendViewFile(seed: BackendSeed): string {
  return join(VIEW_DIR, `${seed.component || seed.path}.vue`)
}

const demoPages = leaves(demoMenuTree() as unknown as MenuLeaf[])

describe('菜单契约 · 演示菜单 ↔ 视图文件', () => {
  it('每个菜单页面都能找到对应的 .vue 视图', () => {
    const missing = demoPages.filter(page => !existsSync(viewFileOf(page)))
    expect(
      missing.map(page => `${page.name} → ${page.path}（期望文件 ${viewFileOf(page)}）`),
      '菜单指向的视图不存在：点进去会是空白页',
    ).toEqual([])
  })

  it('路径与视图路径同源（component === path），工作台等非列表页也遵守该约定', () => {
    const drifted = demoPages.filter((page) => {
      const expected = page.component || page.path!
      // 列表页 / 二级页面：视图路径就是路由路径；工作台是 `/console/index/index`
      return !(expected === page.path || expected === `${page.path}/index`)
    })
    expect(drifted.map(page => `${page.name}: path=${page.path} component=${page.component}`)).toEqual([])
  })

  it('磁盘上的页面视图都有菜单入口（没有「做了页面却点不到」）', () => {
    const referenced = new Set(demoPages.map(page => `${page.component || page.path}.vue`))
    const orphans = collectPageViews(join(VIEW_DIR, 'console'))
      .filter(view => !referenced.has(`${view}.vue`))
      // 详情/子页面由父页面跳转，不需要菜单入口
      .filter(view => !/\/(?:detail|BusinessOverview|SecurityDashboard)$/.test(view))
    expect(orphans, '这些页面没有菜单入口，用户点不到').toEqual([])
  })

  it('已删除的域不得再出现在菜单里（会注册出没有组件的路由）', () => {
    const deadPaths = demoPages.filter(page => /\/console\/(?:mes|open|iot|chat|wechat)\//.test(page.path!))
    expect(deadPaths.map(page => `${page.name} ${page.path}`)).toEqual([])
  })
})

describe('菜单契约 · 后端种子 ↔ 演示菜单逐条对账', () => {
  const seeds = backendSeeds()

  it('后端种子的每个页面都能在前端找到视图', () => {
    const missing = seeds.filter(seed => !existsSync(backendViewFile(seed)))
    expect(
      missing.map(seed => `${seed.name} → ${seed.path}（期望文件 ${backendViewFile(seed)}）`),
      '后端菜单下发的路径没有对应视图：生产环境点进去是空白页（MES 残留就是这样漏出来的）',
    ).toEqual([])
  })

  it('后端种子不得包含已删除域的路径', () => {
    expect(seeds.filter(seed => /\/console\/(?:mes|open|iot|chat|wechat)\//.test(seed.path)).map(seed => seed.path)).toEqual([])
  })

  it('后端种子里的名称与路径，与演示菜单逐条一致（名字漂移也算不一致）', () => {
    const demoPairs = new Map(demoPages.map(page => [page.path!, page.name]))
    const mismatched: string[] = []
    for (const { name, path } of seeds) {
      const demoName = demoPairs.get(path)
      if (!demoName) {
        mismatched.push(`后端有、前端演示菜单没有：${path}（${name}）`)
        continue
      }
      if (demoName !== name) {
        mismatched.push(`${path}：后端叫「${name}」，演示菜单叫「${demoName}」`)
      }
    }
    expect(mismatched, '两边菜单名/路径不一致：预览看到的与生产下发的不是同一套').toEqual([])
  })

  it('演示菜单里的每个页面，后端种子也必须有（不能只在 mock 里存在）', () => {
    const backendPathSet = new Set(seeds.map(seed => seed.path))
    const onlyInDemo = demoPages.filter(page => !backendPathSet.has(page.path!)).map(page => `${page.name} ${page.path}`)
    expect(onlyInDemo, '这些页面只在演示菜单里，生产环境用户看不到').toEqual([])
  })
})

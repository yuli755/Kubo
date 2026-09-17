import type { Pinia } from 'pinia'
import type { MenuItem } from '@/router/appRouter'
// @vitest-environment jsdom
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { demoMenuTree } from '@/demo/fixtures/menu'
import { demoPermissions } from '@/demo/fixtures/permissions'
import { createDemoAdapter } from '@/demo/mock/adapter'

/**
 * 逐页冒烟探针：逐个挂载菜单里的每个路由页组件，找出渲染期**硬错误**的页面。
 *
 * 为什么不走 Console：`Console.vue` 的 `onErrorCaptured` 会把整页换成一个**粘住**的错卡，
 * 后一页会被前一页的错卡污染；独立挂载 + app 级 `errorHandler` 才能一页一账。
 *
 * 判定口径：`errorHandler` / `console.error` = 硬错误（判失败）；
 * `console.warn` 只记录（底座替换期的 prop 类型告警等噪音不该拦 CI）。
 * 报告落盘 `/tmp/probe-report.txt` 便于排查。
 */

if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

/**
 * ## jsdom 补丁：airpower `ATable` 的滚动容器（**F5 删 airpower 时一并删除**）
 *
 * `@airpower/web` 的表组件在数据变化时执行（dist/main.js 内 `watch(dataListRef)`）：
 *
 * ```js
 * const table = document.getElementById(tableId)
 * const bodyWrap = table?.querySelector('.el-scrollbar__wrap')
 * bodyWrap.scrollTop = 0      // ← `table` 加了可选链，`bodyWrap` 没加
 * ```
 *
 * EP 的 `ElTable` **只有在设置了 height/maxHeight 时**才渲染 `.el-scrollbar__wrap`，
 * 而测试里（jsdom，无布局）拿不到它 → 每个用到该表组件的页面都会在 `nextTick` 里抛
 * `TypeError: Cannot set properties of undefined (setting 'scrollTop')`，
 * 被 vitest 记成**未处理拒绝**：用例全绿但 `pnpm test` / `pnpm check` 退出码 1。
 * 真实浏览器里这个容器一定存在，所以它是**第三方在测试环境下的缺陷**，不是本仓的问题。
 *
 * 处置：查该选择器、查不到时返回一个只带 `scrollTop` 的无害占位 —— 只影响这一条选择器，
 * 本仓代码从不读 `.el-scrollbar__wrap`，因此不会掩盖本仓自己的错误。
 * **F5（airpower 退场）后必须删掉这一段。**
 */
const FAKE_SCROLL_WRAP = { scrollTop: 0 } as unknown as Element
const realQuerySelector = Element.prototype.querySelector
Element.prototype.querySelector = function patchedQuerySelector(this: Element, selector: string) {
  const found = realQuerySelector.call(this, selector)
  if (found || selector !== '.el-scrollbar__wrap') {
    return found
  }
  return FAKE_SCROLL_WRAP
} as typeof Element.prototype.querySelector

const views = import.meta.glob('/src/view/**/*.vue') as Record<string, (() => Promise<{ default: unknown }>) | undefined>

/** 渲染期抛错（判失败） */
const errors: string[] = []
/** 渲染期告警（只记录） */
const warnings: string[] = []

/** 环境噪音：vitest 不加载 unplugin-vue-components 的自动导入 */
const NOISE = /Failed to resolve (?:component|directive)|injection "(?:Symbol\(pinia\)|Symbol\(router\))" not found/

/** 安全字符串化：告警参数里可能有不可字符串化的对象，直接 `String()` 会自己抛错 */
function safeText(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }
  if (value instanceof Error) {
    return `[Error] ${value.message}\n${(value.stack || '').split('\n').slice(1, 7).join('\n')}`
  }
  try {
    return JSON.stringify(value) ?? Object.prototype.toString.call(value)
  }
  catch {
    return Object.prototype.toString.call(value)
  }
}

function leaves(nodes: MenuItem[]): MenuItem[] {
  const out: MenuItem[] = []
  for (const node of nodes) {
    const children = (node as MenuItem & { children?: MenuItem[] }).children
    if (children && children.length) {
      out.push(...leaves(children))
    }
    else if (node.path && node.component) {
      out.push(node)
    }
  }
  return out
}

async function mountPage(pinia: Pinia, path: string, component?: string) {
  await import('@/config/airpower-bootstrap')
  const { createAppRouter } = await import('@/router/appRouter')
  const { routes } = await import('@/config/routes')
  const router = createAppRouter(routes)
  await router.push(path)
  await router.isReady()

  // 路径规则：路由路径 == 视图路径（`component` 字段优先，其次 path 本身）
  const key = `/src/view${component || path}.vue`
  const loader = views[key] || views[`/src/view${path}/list.vue`] || views[`/src/view${path}/index.vue`]
  if (!loader) {
    throw new Error(`未找到视图文件：${key}`)
  }
  const Comp = (await loader()).default

  const wrapper = mount(Comp as never, {
    // ⚠️ 必须 `attachTo`：airpower 的表组件在数据变化后靠
    // `document.getElementById(tableId)` 找回自己的表格 DOM 再重置滚动条。
    // 不挂到 document 上时它拿到 null，随即 `bodyWrap.scrollTop = 0` 抛 TypeError
    // （`bodyWrap` 那行漏了可选链）→ 39 个未处理拒绝 → `pnpm test` 退出码 1。
    // 挂上去还有一个附带好处：探针更接近真实渲染环境（能被 `document.getElementById` 找到）。
    attachTo: document.body,
    global: {
      plugins: [
        pinia,
        router,
        [VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }],
      ],
      config: { errorHandler: (err: unknown) => errors.push(`[errorHandler] ${safeText(err)}`) },
    },
  })
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 150))
  await flushPromises()
  return wrapper
}

describe('探针 · 全菜单逐页冒烟', () => {
  beforeEach(() => {
    errors.length = 0
    warnings.length = 0
    vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
      const text = args.map(safeText).join(' ')
      if (!NOISE.test(text)) {
        errors.push(text)
      }
    })
    vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      const text = args.map(safeText).join(' ')
      if (!NOISE.test(text)) {
        warnings.push(text)
      }
    })
    axios.defaults.adapter = createDemoAdapter([0, 0])
  })

  it('每一页都能渲染', async () => {
    const pages = leaves(demoMenuTree())
    const report: string[] = []
    const failed: string[] = []
    for (const page of pages) {
      errors.length = 0
      warnings.length = 0
      const pinia = createPinia()
      setActivePinia(pinia)
      // 真实 Console 先拉权限再渲染页面；裸挂载不种权限就会满屏「权限不足」告警
      const { usePermissionStore } = await import('@/stores/permission')
      usePermissionStore().setPermissions(demoPermissions())
      try {
        const wrapper = await mountPage(pinia, page.path!, page.component)
        const html = wrapper.html()
        if (errors.length) {
          failed.push(page.name)
          report.push(`❌ ${page.name} (${page.path})\n${errors.slice(0, 1).join('\n').slice(0, 1200)}`)
        }
        else if (html.length < 120) {
          failed.push(page.name)
          report.push(`⚠ ${page.name} (${page.path}) → 渲染几乎为空 (${html.length} 字符)`)
        }
        else {
          const noise = warnings.length ? `（告警 ${warnings.length} 条，例：${warnings[0].slice(0, 140)}）` : ''
          report.push(`✅ ${page.name} (${page.path}) [${html.length}]${noise}`)
        }
        // 实验：暂时不卸载
      }
      catch (error) {
        failed.push(page.name)
        report.push(`💥 ${page.name} (${page.path}) → ${safeText(error).slice(0, 600)}`)
      }
      await flushPromises()
    }
    // 探针的输出走文件，不用 console（CI 只允许 warn/error）
    const { writeFileSync } = await import('node:fs')
    writeFileSync('/tmp/probe-report.txt', report.join('\n\n'))
    expect(failed).toEqual([])
  }, 600000)
})

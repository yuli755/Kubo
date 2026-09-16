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

async function mountPage(pinia: Pinia, path: string) {
  await import('@/config/airpower-bootstrap')
  const { createAppRouter } = await import('@/router/appRouter')
  const { routes } = await import('@/config/routes')
  const router = createAppRouter(routes)
  await router.push(path)
  await router.isReady()

  // 列表页统一 list.vue，工作台是 index.vue
  const key = `/src/view${path}/list.vue`
  const loader = views[key] || views[`/src/view${path}/index.vue`]
  if (!loader) {
    throw new Error(`未找到视图文件：${key}`)
  }
  const Comp = (await loader()).default

  const wrapper = mount(Comp as never, {
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
        const wrapper = await mountPage(pinia, page.path!)
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
        wrapper.unmount()
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

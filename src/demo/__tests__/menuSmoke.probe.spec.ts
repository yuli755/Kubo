// @vitest-environment jsdom
import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import type { Pinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { MenuItem } from '@/router/appRouter'
import { demoMenuTree } from '@/demo/fixtures/menu'
import { demoPermissions } from '@/demo/fixtures/permissions'
import { createDemoAdapter } from '@/demo/mock/adapter'

/**
 * 探针（用完即删）：逐个挂载菜单里的每个路由页组件，找出渲染期抛错的页面。
 *
 * 为什么不走 Console：Console 的 onErrorCaptured 会把整页换成一个**粘住**的错卡，
 * 后续页面会被上一页的错卡污染；而 app 级 errorHandler 对每个独立挂载都清零。
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

/** 环境噪音：vitest 不加载 unplugin-vue-components；权限告警在裸挂载时必然出现（真实 Console 会先拉权限） */
const NOISE = /Failed to resolve (?:component|directive)|injection "(?:Symbol\(pinia\)|Symbol\(router\))" not found|RouterLink|\[AButton\]|\[ATable\]|\[ALink\]/
const capturedErrors: string[] = []

function leaves(nodes: MenuItem[]): MenuItem[] {
  const out: MenuItem[] = []
  for (const node of nodes) {
    const children = (node as MenuItem & { children?: MenuItem[] }).children
    if (children && children.length) {
      out.push(...leaves(children))
    } else if (node.path && node.component) {
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

  const key = `/src/view${path}/list.vue`  // 列表页统一 list.vue；工作台是 index.vue，下面单独兜
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
      config: { errorHandler: (err: unknown) => capturedErrors.push(String(err)) },
    },
  })
  await flushPromises()
  await new Promise(resolve => setTimeout(resolve, 150))
  await flushPromises()
  return wrapper
}

describe('probe · 全菜单逐页冒烟', () => {
  beforeEach(() => {
    capturedErrors.length = 0
    vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
      capturedErrors.push(args.map(String).join(' '))
    })
    vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      const text = args.map(String).join(' ')
      if (!NOISE.test(text)) {
        capturedErrors.push(`[warn] ${text}`)
      }
    })
    axios.defaults.adapter = createDemoAdapter([0, 0])
  })

  it('每一页都能渲染', async () => {
    const pages = leaves(demoMenuTree())
    const report: string[] = []
    const failed: string[] = []
    for (const page of pages) {
      capturedErrors.length = 0
      const pinia = createPinia()
      setActivePinia(pinia)
      // 真实 Console 先拿权限再渲染页面；裸挂载不种权限就会满屏「权限不足」告警
      const { usePermissionStore } = await import('@/stores/permission')
      usePermissionStore().setPermissions(demoPermissions())
      try {
        const wrapper = await mountPage(pinia, page.path!)
        const html = wrapper.html()
        if (capturedErrors.length) {
          failed.push(page.name)
          report.push(`❌ ${page.name} (${page.path}) → ${capturedErrors.slice(0, 2).join(' || ').replace(/\s+/g, ' ').slice(0, 320)}`)
        } else if (html.length < 120) {
          failed.push(page.name)
          report.push(`⚠ ${page.name} (${page.path}) → 渲染几乎为空 (${html.length} 字符)`)
        } else {
          report.push(`✅ ${page.name} (${page.path}) [${html.length}]`)
        }
        wrapper.unmount()
      } catch (error) {
        failed.push(page.name)
        const stack = error instanceof Error ? (error.stack || '').split('\n').slice(1, 6).join(' ⏎ ') : ''
        report.push(`💥 ${page.name} (${page.path}) → ${String(error).replace(/\s+/g, ' ').slice(0, 200)} ⇐ ${stack.replace(/\s+/g, ' ').slice(0, 400)}`)
      }
      await flushPromises()
    }
    console.log(`\n==== 逐页结果 (${pages.length} 页) ====\n${report.join('\n')}\n==================`)
    expect(failed).toEqual([])
  }, 600000)
})

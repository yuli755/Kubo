// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { demoUserInfo } from '@/demo/fixtures/identity'
import { demoMenuTree } from '@/demo/fixtures/menu'
import { demoPermissions } from '@/demo/fixtures/permissions'
import { createDemoAdapter } from '@/demo/mock/adapter'

/**
 * # 演示模式 · 控制台冒烟（真挂载 `Console.vue`）
 *
 * ## 为什么必须挂真组件
 * 演示预览最典型的失败是**白屏**：某个子组件在渲染期抛错，被 `Console.vue` 的
 * `onErrorCaptured` 兜住 → 页面只剩一张「页面渲染异常」错卡。
 * 这类问题 lint / typecheck / 构建**全绿**（模板里的运行时取值错误不可能被静态检查抓到），
 * 只有真挂载一次才会暴露。本用例即为此存在。
 *
 * ## 覆盖链路
 * 路由创建 → 演示适配器接管 axios（全局默认实例，`Console` 与 airpower `Http` 共用）
 * → `Console.init()`（租户/用户/权限/菜单四个请求）
 * → 新壳 `KAppShell` 渲染（侧栏 + 菜单 + 顶栏 + 面包屑 + 内容区）。
 */

/**
 * jsdom 30 未实现 `window.matchMedia`（真实浏览器都有），而主题 store 在**模块加载时**
 * 就读它一次。补桩必须在动态 import 之前完成，否则模块初始化即抛错。
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

/** 收集渲染/初始化期抛出的错误（Vue 的 errorHandler 会吞掉异常继续渲染，必须自己接住） */
const capturedErrors: unknown[] = []

async function mountConsole() {
  const { createAppRouter } = await import('@/router/appRouter')
  const { routes } = await import('@/config/routes')
  const Console = (await import('@/component/Console.vue')).default
  // 先把「路由页 + 其静态子模块」拉进模块图：否则测试结束时这些懒加载仍在飞行中，
  // vitest 会在环境销毁后继续加载并报 EnvironmentTeardownError（假失败）。
  await import('@/view/console/index/index.vue')

  const router = createAppRouter(routes)
  await router.push('/console/index')
  await router.isReady()

  const wrapper = mount(Console, {
    global: {
      plugins: [router],
      config: {
        errorHandler: (err: unknown) => capturedErrors.push(err),
      },

    },
  })
  // 预热路由页 chunk：路由组件是 `import.meta.glob` 的懒加载，若解析动作落在测试收尾之后，
  // vitest 会报 EnvironmentTeardownError（"Cannot load ... after the environment was torn down"）。
  // 模块缓存之后这行是零成本的。
  await import('@/view/console/index/index.vue')
  // init() 是异步的：等四个假接口返回 + 路由动态注册 + 渲染落地
  await vi.waitFor(() => {
    expect(wrapper.find('.console-loading').exists()).toBe(false)
  }, { timeout: 5000 })
  // 再等「菜单 + 路由页内容」真正挂上（菜单来自异步接口，页内容是懒加载 + Transition out-in）
  await vi.waitFor(() => {
    if (wrapper.find('.console-error').exists()) {
      return
    }
    const nodes = wrapper.findAll('el-menu-item').length + wrapper.findAll('.el-menu-item').length
    expect(nodes, '菜单尚未渲染').toBeGreaterThan(20)
    expect(wrapper.find('.k-shell__content').html().length).toBeGreaterThan(200)
  }, { timeout: 8000 })
  await nextTick()
  return wrapper
}

describe('演示模式 · 控制台冒烟', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    capturedErrors.length = 0
    /**
     * 捕获 console 的错误与告警。
     *
     * 过滤规则：vitest 不加载 `unplugin-vue-components`（自动导入 el-* / v-loading 的插件），
     * 因此「Failed to resolve component/directive」在测试环境必然出现，属**环境噪音**；
     * 其余一切告警（尤其是 `injection "Symbol(router)" not found` —— airpower 注入不到路由的
     * 直接证据）都必须当作真问题。
     */
    const isEnvNoise = (text: string) =>
      /Failed to resolve (?:component|directive)/.test(text)
    vi.spyOn(console, 'error').mockImplementation((...args: unknown[]) => {
      const text = args.map(String).join(' ')
      if (!isEnvNoise(text)) {
        capturedErrors.push(text)
      }
    })
    vi.spyOn(console, 'warn').mockImplementation((...args: unknown[]) => {
      const text = args.map(String).join(' ')
      if (!isEnvNoise(text)) {
        capturedErrors.push(`[warn] ${text}`)
      }
    })
    // 演示模式接管全局默认 axios 实例：自研 client 与 airpower Http 两条路径一起生效
    axios.defaults.adapter = createDemoAdapter([0, 0])
  })

  afterEach(async () => {
    // 先让待决的微任务（含异步组件解析）落地，再恢复 mock，避免它们跑到环境销毁之后
    await flushPromises()
    vi.restoreAllMocks()
  })

  it('挂载真实 Console.vue：不出现「页面渲染异常」错卡，且新壳侧栏渲染出来', async () => {
    const wrapper = await mountConsole()
    await vi.waitFor(() => {
      expect(wrapper.find('.k-shell').exists()).toBe(true)
    }, { timeout: 5000 })

    // 错卡是 Console.onErrorCaptured 的产物，出现即代表渲染链上有人抛错
    const errorCard = wrapper.find('.console-error')
    expect(
      errorCard.exists(),
      errorCard.exists() ? `Console 渲染出错：${errorCard.text()}` : '',
    ).toBe(false)
    expect(capturedErrors, `渲染期抛错：${capturedErrors.map(String).join(' | ')}`).toEqual([])

    expect(wrapper.find('.k-shell__sider').exists()).toBe(true)
    expect(wrapper.html()).toContain('Kubo')
    // 侧栏菜单真的渲染出来。
    // 注意：vitest 不加载 unplugin-vue-components，el-* 组件在此环境退化为同名原生标签
    // （菜单名放在 `#title` 具名插槽里，未解析的组件会丢弃具名插槽，因此这里数**节点**而不是查文本）。
    const sider = wrapper.find('.k-shell__sider').html()
    expect(sider.length, '侧栏几乎是空的：菜单没渲染').toBeGreaterThan(3000)
    const menuNodes = wrapper.findAll('el-menu-item').length + wrapper.findAll('.el-menu-item').length
    expect(menuNodes, '侧栏一个菜单项都没渲染').toBeGreaterThan(20)

    // 收尾：卸载组件，避免遗留的异步任务在环境销毁后继续跑
    wrapper.unmount()
    // 内容区也要有东西：路由页（airpower 组件）能渲染，而不是被错卡顶掉
    expect(wrapper.find('.k-shell__content').html().length).toBeGreaterThan(2000)
  })

  it('菜单数据本身自洽（每个菜单叶子都能被演示权限放行）', () => {
    const codes = new Set(demoPermissions())
    const missing: string[] = []
    const walk = (nodes: ReturnType<typeof demoMenuTree>): void => {
      for (const node of nodes) {
        if (node.permission && !codes.has(node.permission)) {
          missing.push(`${node.name}: ${node.permission}`)
        }
        if (node.children) {
          walk(node.children as ReturnType<typeof demoMenuTree>)
        }
      }
    }
    walk(demoMenuTree())
    expect(missing, '菜单声明了权限但权限集合里没有：菜单会被隐藏/页面不渲染').toEqual([])
  })

  it('演示适配器返回的用户信息可直接喂给顶栏用户卡', () => {
    expect(demoUserInfo().nickname).toBeTruthy()
  })
})

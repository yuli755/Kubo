/**
 * # 演示模式入口（仅演示模式）
 *
 * ## 它做什么
 * 1. 把 axios 适配器换成 Mock（`@airpower/web` 与自研 client 两条请求路径一起接管）；
 * 2. 打开运行时标记 `window.__KUBO_DEMO__`（顶栏展示「演示数据」徽标）；
 * 3. 预置登录态（token / 租户），使用户**跳过登录**直接进入控制台。
 *
 * ## 为什么是「编译期开关」而不是「登录页放个开关按钮」
 * 绕过登录的代码一旦进了生产环境就是前端鉴权后门；后端对此毫不知情（它看到的是带着
 * 合法 token 的请求）。因此门禁做成三层：
 *
 * | 层 | 手段 | 效果 |
 * | --- | --- | --- |
 * | 1 | `main.ts` 里 `if (DEMO_ENABLED)`（`import.meta.env.MODE`，构建期替换为字面量） | 生产构建里整段成为死代码，**动态 import 不产出 chunk** |
 * | 2 | `infra/plugins/demo-gate.ts` | `--mode demo` 只允许 `serve`；`build --mode demo` 直接报错 |
 * | 3 | `infra/scripts/check-demo-build.mjs` | 扫描 `dist/`，发现演示标记即 CI 失败 |
 *
 * ## 移除方式（接真实后端时）
 * 删除 `src/demo/`、`src/component/shell/`（如需保留新壳则只删 demo 相关）、
 * `.env.demo`、`infra/plugins/demo-gate.ts`，以及 `main.ts`/`Console.vue`/`KAppShell.vue`
 * 中标记为 `demo` 的分支与 `package.json` 的 `dev:demo` 脚本。详见 `src/demo/README.md`。
 */

import axios from 'axios'
import { useUserStore } from '@/stores/user'
import { createDemoAdapter } from './mock/adapter'

/** 演示模式标记：CI 门禁用它断言产物里不含演示代码 */
export const DEMO_MARKER = 'KUBO_DEMO_MODE_v1'

interface DemoWindow extends Window {
  __KUBO_DEMO__?: boolean
  /** 供 `infra/scripts/check-demo-build.mjs` 在 CI 里断言「产物不含演示模式」 */
  __KUBO_DEMO_MARKER__?: string
}

/**
 * 安装演示模式。**必须在任何业务请求发生之前调用**（`main.ts` 中位于 pinia 安装之后、
 * 路由创建之前）。
 */
export async function installDemoMode(): Promise<void> {
  const adapter = createDemoAdapter()

  // 全局默认实例：`@airpower/web` 的 Http 类正是用它发请求（axios.request(...)）
  axios.defaults.adapter = adapter

  // 自研 client：模块可能已在别处被 import 过（其 axios.create 已快照默认值），
  // 这里显式再挂一次，保证两条路径都走 Mock。
  const { request } = await import('@/api/axios')
  request.defaults.adapter = adapter

  ;(window as DemoWindow).__KUBO_DEMO__ = true
  ;(window as DemoWindow).__KUBO_DEMO_MARKER__ = DEMO_MARKER

  // 预置登录态：跳过登录页直接进控制台（租户列表由 mock 的 getMyTenantList 下发）
  const userStore = useUserStore()
  userStore.setToken('demo-token')
}

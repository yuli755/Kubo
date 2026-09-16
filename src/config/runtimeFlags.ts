/**
 * # 运行时开关
 *
 * 只放「必须能在运行时读到、且不依赖演示模式代码」的极轻量标记。
 *
 * 为什么不用直接 `import { isDemo } from '@/demo'`：演示模块是**懒加载 + 生产构建被
 * 常量消除**的，任何静态 import 都会把它重新拉回产物。这里改成读一个由演示模块在
 * 启动时写入的 window 标记 —— 生产构建里没人写它，永远为 `false`。
 */

import { DEMO_ENABLED } from '@/config/buildFlags'

interface DemoWindow extends Window {
  /** 由 `src/demo/index.ts` 在启动时置位 */
  __KUBO_DEMO__?: boolean
}

/**
 * 是否运行在演示模式（无后端、假数据）。
 *
 * 用于：顶栏展示「演示数据」徽标、跳过真实接口调用（演示模式没有后端可连）。
 *
 * ⚠️ 必须**同时**满足两个条件，缺一不可：
 * 1. `DEMO_ENABLED`（构建期常量，见 `@/config/buildFlags`）—— 生产构建里恒为 `false`，
 *    整个函数会被折叠成 `return false`，`__KUBO_DEMO__` 字符串随之被消除。这样既保证
 *    产物里没有演示痕迹（CI `pnpm demo:check` 会因此通过），也堵住「线上打开控制台手写
 *    `window.__KUBO_DEMO__ = true` 就把自己变成演示环境」的口子；
 * 2. window 标记 —— 演示模块真正装载完成（适配器已替换）后才会置位，避免常量开关
 *    打开了、mock 还没就绪时界面先误报「演示数据」。
 */
export function isDemoRuntime(): boolean {
  if (!DEMO_ENABLED || typeof window === 'undefined') {
    return false
  }
  return Boolean((window as DemoWindow).__KUBO_DEMO__)
}

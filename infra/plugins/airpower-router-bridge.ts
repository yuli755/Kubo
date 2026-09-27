/**
 * # airpower vue-router 注入桥接插件（FIX-G1）
 *
 * ## 问题
 * `@airpower/web/dist/main.js` 内嵌了一整份独立的 vue-router 源码（其 `inject` 用的
 * `routerKey` / `routeLocationKey` / `routerViewLocationKey` 是该模块私有的
 * `Symbol()` 实例）。项目用原生 `vue-router` 创建 router 并 `app.use(router)`，
 * provide 的是**另一份** vue-router 模块的 Symbol key。
 * Symbol 的唯一性由语言保证：描述符相同也不是同一个 key，因此在项目侧
 * `app.provide(Symbol('router'), router)` 无法让 airpower 的 `inject` 命中——
 * 其组件（Menu/Panel/ChildMenu）的 `useRoute()`/`useRouter()` 恒为 `undefined`，
 * 运行期抛 `Cannot read properties of undefined (reading 'currentRoute')`。
 *
 * ## 方案
 * 构建期把 airpower bundle 里这三个私有 Symbol 常量替换为挂在 `globalThis`
 * 上的稳定实例（`globalThis.__KUBO_AP_KEYS__`），项目侧（`src/router/airpower-router-bridge.ts`）
 * 用**同一组** Symbol 向 app provide router / 响应式 route。两边 key 对象同一，
 * `inject` 直接命中。
 *
 * ## 为什么必须 exclude 预构建
 * Vite dev 对 node_modules 依赖做 esbuild 预打包（optimizeDeps），transform 钩子
 * **不会**在预打包产物上重新执行。因此 vite.config 需 `optimizeDeps.exclude: ['@airpower/web']`，
 * 让该文件以源码形态进入模块图、transform 才在 dev 与 build 一致生效
 * （其外部依赖仅 vue/element-plus/axios 三个纯 ESM 包，exclude 安全）。
 *
 * airpower 升级后若行结构变化，正则不匹配时 warn 提示，不会静默半修。
 */
import type { Plugin } from 'vite'

/** 单个 Symbol 常量：变量名 → 替换到 globalThis 上的存储键名 */
const SYMBOL_KEYS = [
  { constName: 'routerKey', storeKey: 'routerKey', desc: 'router' },
  { constName: 'routeLocationKey', storeKey: 'routeLocationKey', desc: 'route location' },
  { constName: 'routerViewLocationKey', storeKey: 'routerViewLocationKey', desc: 'router view location' },
] as const

export function airpowerRouterBridgePlugin(): Plugin {
  return {
    name: 'airpower-router-bridge',
    enforce: 'pre',
    transform(code, id) {
      const normalizedId = id.replace(/\\/g, '/')
      if (!normalizedId.includes('@airpower/web/dist/main.js')) {
        return null
      }
      // 快速短路：不含特征即返回
      if (!code.includes('Symbol(process.env.NODE_ENV')) {
        return null
      }

      let replaced = code
      for (const { constName, storeKey, desc } of SYMBOL_KEYS) {
        // 精确锚定变量名与描述符，避免误伤 bundle 内其它 Symbol（如 "navigation failure"）
        const re = new RegExp(
          `const ${constName} = (?:/\\* @__PURE__ \\*/ )?Symbol\\(process\\.env\\.NODE_ENV !== "production" \\? "${desc.replace(/ /g, '\\s')}" : ""\\);`,
        )
        if (!re.test(replaced)) {
          this.warn(
            `airpower-router-bridge: 未匹配到 ${constName} 定义（airpower 版本可能已变更，桥接不完整，inject 将回退 undefined）`,
          )
          continue
        }
        replaced = replaced.replace(
          re,
          `const ${constName} = (globalThis.__KUBO_AP_KEYS__ ??= {}).${storeKey} ??= Symbol("${desc}");`,
        )
      }

      if (replaced === code) {
        return null
      }
      return { code: replaced, map: null }
    },
  }
}

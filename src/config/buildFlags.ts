/**
 * # 构建期开关（唯一入口）
 *
 * 这里集中演示模式与新壳 A/B 两个开关的判定，原因：
 * 1. **必须是静态可替换表达式** —— `import.meta.env.MODE` 在构建时会被 Vite 替换成字面量，
 *    于是 `if (DEMO_ENABLED)` 成为死分支，动态 import 的演示模块**不产出 chunk**（生产产物无后门）。
 * 2. 曾用 `define: { __DEMO_ENABLED__: ... }` + `declare const`，但 Vite 的 `define` 只在
 *    **build** 阶段替换，dev 下保留原标识符 → 运行到 `if (__DEMO_ENABLED__)` 直接
 *    ReferenceError，演示预览起不来。`import.meta.env` 在 dev/build 两种模式下都可用。
 * 3. 开关分散在多个文件里各自 `import.meta.env.xxx === 'true'` 时，很容易漏改一处造成
 *    「A/B 开关一半生效」，所以收敛到本文件。
 *
 * ⚠️ 两个常量**只能用于可以整段消除的分支**（`if (...)` / 提前 return）。若用于运行时拼接
 * 字符串等无法折叠的位置，演示代码就会进产物 —— `pnpm demo:check` 会拦住。
 */

/**
 * 演示模式（`pnpm dev:demo`，= `vite --mode demo`）。
 *
 * 开启后：所有 HTTP 请求由本地 mock 适配器接管、启动时预置登录态（跳过登录）。
 * 生产构建 `MODE === 'production'` ⇒ 恒为 `false` ⇒ 演示模块物理上进不了产物。
 */
export const DEMO_ENABLED: boolean = import.meta.env.MODE === 'demo'

/**
 * 旧壳 A/B 开关：`VITE_LEGACY_SHELL=true pnpm dev` 时走 airpower `AFrame` 旧壳，默认新壳。
 *
 * 仅用于改版期间的并排评审；评审结束后连同 `Console.vue` 里的旧壳分支一起删除。
 */
export const LEGACY_SHELL: boolean = import.meta.env.VITE_LEGACY_SHELL === 'true'

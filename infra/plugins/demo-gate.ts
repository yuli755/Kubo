import type { Plugin } from 'vite'

/**
 * # 演示模式门禁插件（构建期）
 *
 * 演示模式（`--mode demo`）会替换 axios 适配器、预置登录态 —— 它**只能用于本地预览**，
 * 绝不能进生产产物。这里做第一道闸：**禁止以 demo 模式构建**。
 *
 * 三道闸的分工：
 *
 * | 层 | 手段 | 效果 |
 * | --- | --- | --- |
 * | 1（本插件） | `--mode demo` 只允许 `serve`，`build` 直接抛错 | 从流程上不可能产出 demo 产物 |
 * | 2（vite define） | `__DEMO_ENABLED__` 编译期常量 | 生产构建里演示代码是死代码，动态 import 不产出 chunk |
 * | 3（CI 脚本） | `infra/scripts/check-demo-build.mjs` 扫描 `dist/` | 万一有人绕过前两道，CI 拦截 |
 *
 * @param enabled 当前是否处于演示模式（由 `mode === 'demo'` 判定）
 */
export function demoGatePlugin(enabled: boolean): Plugin {
  return {
    name: 'kubo-demo-gate',

    config(_config, env) {
      if (!enabled) {
        return undefined
      }
      if (env.command === 'build') {
        throw new Error(
          [
            '⛔ 禁止构建演示模式产物（--mode demo + build）。',
            '演示模式会替换请求适配器并预置登录态，属于"绕过登录的后门"，只能用于本地/预览。',
            '请改用：pnpm dev:demo（serve），或去掉 --mode demo 做正式构建。',
          ].join('\n'),
        )
      }
      console.log('\n⚠️  演示模式已启用（demo）：所有接口由本地 mock 接管，登录已跳过 —— 仅用于视觉/交互预览。\n')
      return undefined
    },
  }
}

import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * # Vitest 配置
 *
 * 覆盖两类可测单元：
 * 1. `packages/shared`：纯函数（脱敏策略归一化等），无 DOM 依赖；
 * 2. `src/**`：指令 / hook 适配器 / 权限码常量不变量，需要 jsdom。
 *
 * 注意：这里**不加载** vite.config.mts 的 PWA / airpower shim 插件，
 * 避免测试环境被构建期插件影响（shim 只服务于产物体积）。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  test: {
    // 默认 node 环境（纯函数最快）；需要 DOM 的用例在文件头写 `// @vitest-environment jsdom`
    environment: 'node',
    include: ['src/**/*.spec.ts', 'packages/shared/**/*.spec.ts'],
    // 组件测试需要 element-plus 的解析器时，按需在具体用例里注册，避免全局开销
  },
})

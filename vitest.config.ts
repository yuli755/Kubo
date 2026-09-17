import { resolve } from 'node:path'
import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * # Vitest 配置
 *
 * 覆盖 `src/**` 的可测单元：纯函数（脱敏策略归一化等）走默认 node 环境，
 * 指令 / hook 适配器 / 权限码常量不变量需要 jsdom（文件头写 `// @vitest-environment jsdom`）。
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
    include: ['src/**/*.spec.ts'],
    // 冷启动时模块链首次编译可能超过默认 5s（appConfig 首次 import airpower shim 实测 ~3s，
    // CI 上依赖包未预热会更慢），抬高到 15s 消除平台相关的偶发超时。
    testTimeout: 15000,
    // vitest 4 defaults to `forks`：每个 worker 是完整 Node 进程，Windows 提交限制（页面文件）
    // 紧张时 worker 会被 OOM 静默杀掉（表现：Worker exited unexpectedly + 用例数缺失）。
    // threads 共享主进程堆，jsdom 环境同样支持，代价更低也更稳。
    pool: 'threads',
    // 组件测试需要 element-plus 的解析器时，按需在具体用例里注册，避免全局开销
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json-summary', 'html'],
      // 覆盖率只统计「有单元测试价值的纯逻辑层」，排除视图(.vue)/网络层/模型实体：
      // 那些要么是不可单测的模板渲染，要么是 OpenAPI 生成的一次性胶水代码。
      include: [
        'src/utils/**/*.ts',
        'src/composables/**/*.ts',
        'src/stores/**/*.ts',
        'src/directives/**/*.ts',
        'src/config/**/*.ts',
        'src/guard/**/*.ts',
        // 框架层只把"与后端逐分支对齐的纯逻辑"纳入基线（脱敏策略）；
        // 其余框架模块是元数据/HTTP/校验胶水，纳入会稀释阈值而不是提高质量
        'src/framework/mask.ts',
      ],
      exclude: [
        '**/__tests__/**',
        '**/*.spec.ts',
        '**/*.d.ts',
      ],
      // 防回归地板（非追求指标）：核心纯逻辑（脱敏/权限/持久化/守卫/错误上报）
      // 一旦被删改导致覆盖率骤降，这里会拦。当前基线 ~56% lines / 71% funcs / 85% branches。
      thresholds: {
        lines: 50,
        functions: 65,
        statements: 50,
        branches: 75,
      },
    },
  },
})

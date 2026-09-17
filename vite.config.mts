import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import { demoGatePlugin } from './infra/plugins/demo-gate'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 演示模式：`pnpm dev:demo`（= vite --mode demo）。仅 serve，禁止 build（见 demoGatePlugin）。
  const isDemo = mode === 'demo'
  return {
    plugins: [
      vue(),
      demoGatePlugin(isDemo),
      // element-plus 按需自动导入：仅打包实际使用的 El 组件及其样式，
      // 替代 main.ts 中 app.use(ElementPlus) 的全量注册（原 2.0MB / gzip 423KB）
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/types/components.d.ts',
      }),
      // 注：迁移期这里挂着两个 airpower 专用构建插件（EP 默认导入 shim、整包 CSS 剥离），
      // 2026-09-18 随 `@airpower/web` 一起删除：没有 airpower 产物，就没有要打的补丁。
      // 注：vite-plugin-vue-devtools 已从依赖里移除（开发模式底部注入的悬浮按钮会挡住页面预览）

      // PWA 支持 (离线缓存 + 预加载 + 后台同步)
      VitePWA({
        registerType: 'autoUpdate',
        includeAssets: ['favicon.ico'],
        manifest: {
          name: 'Kubo 平台',
          short_name: 'Kubo',
          description: 'Kubo 企业级数字化管理平台',
          theme_color: '#409eff',
          icons: [
            {
              src: 'pwa-192x192.png',
              sizes: '192x192',
              type: 'image/png',
            },
            {
              src: 'pwa-512x512.png',
              sizes: '512x512',
              type: 'image/png',
            },
          ],
        },
        workbox: {
          maximumFileSizeToCacheInBytes: 4 * 1024 * 1024, // 4 MiB (echarts 约 2.77 MiB)
          globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        // 刻意**不**对 /api/ 做 runtimeCaching：API 响应是「按当前用户租户 + 行级/列级权限过滤后的
        // 业务数据」，落到浏览器 Cache Storage 后（原 NetworkFirst + 5min）会在共享浏览器上把上一
        // 个用户的可见数据喂给下一个用户（离线兜底时），是跨租户泄露面。PWA 的离线价值只保留在
        // 「静态壳」（上面 globPatterns 预缓存的 js/css/html），动态数据一律走网络、不落缓存。
        },
      }),
    ],
    resolve: {
      alias: [
        { find: '@', replacement: '/src/' },
        // 重定向到聚合入口 `src/types/element-plus-aggregate.ts`。该入口仅从子路径
        // re-export 实际使用的组件，绕开 element-plus `es/index.mjs` 的顶层副作用
        // （→ defaults.mjs → 全量 80 组件），在 minify:false 下也能恢复树摇。
        //
        // 两类 source 都需命中：
        //  1. 裸 `element-plus`（项目源码 `import { ElMessage } from 'element-plus'`）
        //  2. `element-plus/es`（unplugin-vue-components 的 ElementPlusResolver 注入的
        //     `from`，按版本会解析到 `es/index.mjs`，同样含顶层副作用）
        // 正则的 `$` 锚点确保 `element-plus/es/components/...` 子路径不受影响。
        {
          find: /^element-plus$/,
          replacement: resolve(__dirname, 'src/types/element-plus-aggregate.ts'),
        },
        {
          find: /^element-plus\/es$/,
          replacement: resolve(__dirname, 'src/types/element-plus-aggregate.ts'),
        },
      ],
    },
    build: {
      assetsDir: 'assets',
      // 使用 terser 压缩。`keep_classnames` 保留：自研元数据层按构造器取值，
      // 且日志/错误上报里的类名是排查线索，省这点体积不值得。
      minify: 'terser',
      terserOptions: {
        compress: {
        // keep_fnames: 普通函数名对反射无意义，去掉可省约 30 KB（实测）。
          keep_classnames: true,
          keep_fnames: false,
        },
        mangle: {
          keep_classnames: true,
          keep_fnames: false,
        },
      },
      cssMinify: 'esbuild',
      sourcemap: false,
      chunkSizeWarningLimit: 500,
      // 代码分割: 将第三方依赖拆分为独立 chunk
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (id.includes('node_modules')) {
              if (id.includes('echarts') || id.includes('zrender')) {
                return 'vendor-echarts'
              }
              if (id.includes('vue-router') || id.includes('pinia') || /[\\/]vue[\\/]dist/.test(id) || id.includes('@vue/runtime') || id.includes('@vue/shared') || id.includes('@vue/reactivity')) {
                return 'vendor-vue'
              }
              if (id.includes('element-plus') || id.includes('@element-plus/icons-vue')) {
                return 'vendor-element'
              }
              if (id.includes('@tanstack/vue-query') || id.includes('@tanstack/query-core')) {
                return 'vendor-query'
              }
              if (id.includes('axios')) {
                return 'vendor-axios'
              }
            }
            return undefined
          },
        },
      },
    },
    server: {
    // 不自动拉起浏览器：在无图形环境（WSL/远程容器/CI）下 `open: true` 会因缺少
    // xdg-open 等 opener 直接崩溃退出（spawn xdg-open ENOENT），导致 `pnpm dev` 起不来。
    // Vite 启动后会在终端打印本地/网络地址，手动访问即可。
      open: false,
      host: '0.0.0.0',
      port: 3000,
      // 允许任意 Host 访问（开发服务器）。默认只放行 localhost/绑定地址，会导致
      // 云端预览、反向代理、隧道（ngrok / cloud dev 环境 / e2b 等）访问时被 403 拒绝。
      // 仅影响 dev server，不影响生产构建产物。
      allowedHosts: true,
      https: false,
      // 产物目录不参与热更新监听：`coverage/`（pnpm test:coverage）与 `dist/`（pnpm build）
      // 是每次跑门禁都会重写的生成物，被 watcher 盯上会导致开发服务器每轮刷上百次
      // reload（表现为"只是跑了个测试，页面自己狂刷新"）。它们本来也不该被热加载。
      watch: {
        ignored: ['**/coverage/**', '**/dist/**'],
      },
      proxy: {
        '/api': {
        // target: 'https://spms.hamm.cn/api/',
        // target: 'http://10.0.0.8:8080/',
          target: 'http://127.0.0.1:8080/',
          rewrite: path => path.replace(/^\/api/, ''),
          changeOrigin: true,
        },
        // '/oauth2': 'https://spms.hamm.cn',
        '/oauth2': 'http://127.0.0.1:8080',
      },
    },
  }
})

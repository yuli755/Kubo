import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import vue from '@vitejs/plugin-vue'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import Components from 'unplugin-vue-components/vite'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import vueDevTools from 'vite-plugin-vue-devtools'
import { airpowerElementPlusShimPlugin } from './infra/plugins/airpower-element-plus-shim'

const __dirname = fileURLToPath(new URL('.', import.meta.url))

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    // element-plus 按需自动导入：仅打包实际使用的 El 组件及其样式，
    // 替代 main.ts 中 app.use(ElementPlus) 的全量注册（原 2.0MB / gzip 423KB）
    Components({
      resolvers: [ElementPlusResolver()],
      dts: 'src/types/components.d.ts',
    }),
    // airpower 预构建产物含 `import ElementPlus from "element-plus"` 默认导入，
    // 会触发 element-plus 全量 80 组件进入 bundle（详见插件文件头注释）。
    // 此处在构建期将其替换为最小 shim（仅 v-loading 指令 + locale），恢复树摇。
    airpowerElementPlusShimPlugin(),
    // vueDevTools() — 已禁用：开发模式底部注入的悬浮按钮影响页面预览
    // vueDevTools(),

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
        runtimeCaching: [
          {
            urlPattern: /^\/api\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'kubo-api-cache',
              expiration: {
                maxEntries: 100,
                maxAgeSeconds: 300,
              },
            },
          },
        ],
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
      //  1. 裸 `element-plus`（项目源码 `import { ElMessage } from 'element-plus'`、
      //     airpower dist 经 shim 替换后的具名 import）
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
    // 使用 terser 压缩，保留类名以兼容 airpower 反射机制
    minify: 'terser',
    terserOptions: {
      compress: {
        keep_classnames: true,
        keep_fnames: true,
      },
      mangle: {
        keep_classnames: true,
        keep_fnames: true,
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
            if (id.includes('@airpower/web') || id.includes('@airpower/core')) {
              return 'vendor-airpower'
            }
          }
          return undefined
        },
      },
    },
  },
  server: {
    open: true,
    host: '0.0.0.0',
    port: 3000,
    https: false,
    proxy: {
      '/api': {
        // target: 'https://spms.hamm.cn/api/',
        // target: 'http://10.0.0.8:8080/',
        target: 'http://127.0.0.1:8080/',
        rewrite: path => path.replace(/^\/api/, ''),
        changeOrigin: true,
      },
      // WebSocket 代理（airpower 原生 WebSocket，端点 /websocket）
      '/websocket': {
        target: 'ws://127.0.0.1:8080',
        ws: true,
        changeOrigin: true,
      },
      // '/oauth2': 'https://spms.hamm.cn',
      '/oauth2': 'http://127.0.0.1:8080',
    },
  },
})

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),

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
    alias: {
      '@': '/src/',
    },
  },
  build: {
    assetsDir: 'assets',
    // 暂时设置为false: vite的BUG keep_classnames不生效导致反射获取类失败
    minify: false,
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

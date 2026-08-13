import { RouterUtil, WebConfig } from '@airpower/web'
import { VueQueryPlugin } from '@tanstack/vue-query'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { createPinia } from 'pinia'
import { createApp, version } from 'vue'
import App from '@/App.vue'
import { routes } from '@/config/routes'
import { directives } from '@/directives'
import { initAuthGuard } from '@/guard/authGuard'
import { initPermissionGuard } from '@/guard/permissionGuard'
import { registerPWA } from '@/pwa/registerSW'
import { queryClient } from '@/query/queryClient'
import { createPersistPlugin } from '@/stores/persist'
import '@airpower/web/dist/web.css'
import '@/assets/css/main.scss'

WebConfig.elementPlusLocale = zhCn
WebConfig.appKey = 'kubo'
WebConfig.product = 'Kubo'
WebConfig.uploadUrl = '/api/file/upload'
WebConfig.staticUrl = '/static/'

const app = createApp(App)

const pinia = createPinia()
pinia.use(createPersistPlugin())

app.use(pinia)

const router = RouterUtil.createRouter(routes)

// 注册路由守卫（顺序：先认证 → 再权限）
initAuthGuard(router)
initPermissionGuard(router)

app.use(router)
app.use(ElementPlus, {
  zIndex: 3000,
  locale: zhCn,
})
app.use(VueQueryPlugin, { queryClient })

// 注册自定义指令
for (const [name, directive] of Object.entries(directives)) {
  app.directive(name, directive)
}

app.mount('#app')

// PWA: 注册 Service Worker（仅 production 环境生效）
registerPWA()

console.warn(`Vue Version: ${version}`)
console.warn(`ElementPlus Version: ${ElementPlus.version}`)

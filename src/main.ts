import { RouterUtil, WebConfig } from '@airpower/web'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { ElLoading } from 'element-plus'
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

// element-plus 命令式 API 与指令的样式需显式引入（按需导入下 resolver 不会注入它们）：
// ElMessage / ElMessageBox / ElNotification 函数式调用 + v-loading 指令
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/notification/style/css'

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
app.use(VueQueryPlugin, { queryClient })

// v-loading 指令：原由 app.use(ElementPlus) 全局注册，
// 按需导入后需手动注册，否则 23+ 处加载态遮罩失效
app.directive('loading', ElLoading.directive)

// 注册自定义指令
for (const [name, directive] of Object.entries(directives)) {
  app.directive(name, directive)
}

app.mount('#app')

// PWA: 注册 Service Worker（仅 production 环境生效）
registerPWA()

console.warn(`Vue Version: ${version}`)

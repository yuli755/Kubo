import { VueQueryPlugin } from '@tanstack/vue-query'
import { ElLoading } from 'element-plus'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from '@/App.vue'
import { routes } from '@/config/routes'
import { directives } from '@/directives'
import { initAuthGuard } from '@/guard/authGuard'
import { initPermissionGuard } from '@/guard/permissionGuard'
import { registerPWA } from '@/pwa/registerSW'
import { queryClient } from '@/query/queryClient'
import { provideAirpowerRouter } from '@/router/airpower-router-bridge'
import { createAppRouter } from '@/router/appRouter'
import { createPersistPlugin } from '@/stores/persist'
import { useThemeStore } from '@/stores/theme'
import { installGlobalErrorHandler, setErrorReporter } from '@/utils/errorHandler'
import { reportErrorToServer } from '@/utils/errorReportClient'
import { installRouterErrorHandler } from '@/utils/routerErrorHandler'
import { enforceTokenScope } from '@/utils/tokenScope'
// airpower 的 WebConfig 配置 + web.css 已移至 src/config/airpower-bootstrap.ts，
// 随业务页（Console.vue 懒加载）初始化，避免首屏加载 airpower 单体（107 KB）。
// V1+V2：设计 token 层 + Element Plus 暗色变量（顺序敏感，必须在本文件其它样式之后引入）
import '@/styles/index.scss'

// element-plus 命令式 API 与指令的样式需显式引入（按需导入下 resolver 不会注入它们）：
// ElMessage / ElMessageBox 函数式调用 + v-loading 指令（ElNotification 项目未使用，不引入）
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/loading/style/css'

const app = createApp(App)

// G-3：必须早于任何 store/router 创建——session 级登录（未勾选「记住我」）在
// 浏览器重启后视为会话结束，先清掉残留 token 再初始化，避免守卫带着过期会话放行
enforceTokenScope()

// P1：全局错误兜底（未捕获异常落日志 + 预留上报钩子），在挂载前注册
installGlobalErrorHandler(app)
// 生产可观测性闭环：把错误上报钩子接到后端（限流+脱敏+落结构化日志），
// 失败静默 + 去重，见 src/utils/errorReportClient.ts
setErrorReporter(reportErrorToServer)

const pinia = createPinia()
pinia.use(createPersistPlugin())

app.use(pinia)

// V2：挂载前应用主题偏好，确保首帧渲染就带正确的 html.dark（避免浅色闪烁）
useThemeStore()

const router = createAppRouter(routes)

// 路由级错误兜底：懒加载 chunk 加载失败（发版时新旧 bundle 不匹配）走 router.onError、
// 不经过 ErrorBoundary 的 onErrorCaptured；此处上报 + 刷新恢复，补上「导航侧」这条腿
installRouterErrorHandler(router)

// 注册路由守卫（顺序：先认证 → 再权限）
initAuthGuard(router)
initPermissionGuard(router)

app.use(router)
// FIX-G1：airpower bundle 内嵌的 vue-router 使用 globalThis 桥接 Symbol（见
// infra/plugins/airpower-router-bridge.ts），此处用同一组 Symbol 向 app provide router，
// 使 AFrame/Menu/APanel 等 airpower 组件的 useRoute()/useRouter() 拿到真实路由，
// 而非 undefined（否则渲染即抛 "Cannot read properties of undefined (reading 'currentRoute')"）
provideAirpowerRouter(app, router)
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

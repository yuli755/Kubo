import { VueQueryPlugin } from '@tanstack/vue-query'
import { ElLoading } from 'element-plus'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import App from '@/App.vue'
import { DEMO_ENABLED } from '@/config/buildFlags'
import { routes } from '@/config/routes'
import { directives } from '@/directives'
import { initAuthGuard } from '@/guard/authGuard'
import { initPermissionGuard } from '@/guard/permissionGuard'
import { registerPWA } from '@/pwa/registerSW'
import { queryClient } from '@/query/queryClient'
import { createAppRouter } from '@/router/appRouter'
import { createPersistPlugin } from '@/stores/persist'
import { useThemeStore } from '@/stores/theme'
import { installGlobalErrorHandler, setErrorReporter } from '@/utils/errorHandler'
import { reportErrorToServer } from '@/utils/errorReportClient'
import { installRouterErrorHandler } from '@/utils/routerErrorHandler'
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
app.use(VueQueryPlugin, { queryClient })

// v-loading 指令：原由 app.use(ElementPlus) 全局注册，
// 按需导入后需手动注册，否则 23+ 处加载态遮罩失效
app.directive('loading', ElLoading.directive)

// 注册自定义指令
for (const [name, directive] of Object.entries(directives)) {
  app.directive(name, directive)
}

/**
 * 启动：演示模式（`pnpm dev:demo`，仅本地预览）先安装 mock 请求适配器与预置登录态再挂载。
 *
 * 顺序不能反——`app.mount()` 会触发首次导航，若此时还没有登录态，authGuard 会把人送去登录页。
 *
 * ⚠️ `DEMO_ENABLED` 取自 `import.meta.env.MODE`（构建期被替换成字面量）：
 * 生产构建恒为 `false`，因此这段 if 是死代码，动态 import 不产出 chunk ——
 * 演示模式（含跳过登录）在物理上不可能进入生产产物（CI `pnpm demo:check` 复核）。
 */
async function bootstrap() {
  if (DEMO_ENABLED) {
    const { installDemoMode } = await import('@/demo')
    await installDemoMode()
  }
  app.mount('#app')
}

bootstrap()

// PWA: 注册 Service Worker（仅 production 环境生效）
registerPWA()

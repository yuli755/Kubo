<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import { onErrorCaptured, ref } from 'vue'
import { authApi } from '@/api/system'
import KAppShell from '@/component/KAppShell.vue'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { DEFAULT_HOME_PATHS, firstNavigableMenuPath, getRouter, initVueRouter, reloadCurrentRoute } from '@/router/appRouter'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { describeUserError } from '@/utils/errorCopy'
import { reportError } from '@/utils/errorHandler'

const components = import.meta.glob('/src/view/**/*.vue')
const currentUserInfo = ref(new UserEntity())
const menuList = ref<MenuEntity[]>([])
const isLoading = ref(true)
const loadError = ref(false)
/** D5：错卡文案分档后的结构化文案（标题/详情/指引），不再把 error.message 原样甩给用户 */
const errorCopy = ref<ReturnType<typeof describeUserError> | null>(null)
const errorTitle = ref('')
const tenantReady = ref(false)
/**
 * 动态菜单路由是否已经注册完成。
 *
 * 本页的路由不是静态表，而是**运行时**从 `/user/getMyMenuList` 生成后 `initVueRouter` 注进去的。
 * 在它跑完之前，任何 `/console/...` 深链都只能被 `config/routes.ts` 的 `:catchAll(.*)` 接住，
 * 渲染出 `view/error.vue` 的 404；而 `tenantReady` 在第一个 await 之后就变 true，外壳随即挂出
 * `<router-view>` —— 于是「深链冷启动」会先把 404 页画在 `v-loading` 遮罩底下，
 * 等 `reloadCurrentRoute()` 再翻回真页面。
 *
 * 真机实测（Playwright）：冷缓存硬加载 `/console/factory/storage/list`，1.4s 时 DOM 里仍是
 * 「很抱歉，没有找到」，约 5s 后自愈；同一地址热加载始终正常。症状只在冷启动出现、又只闪一下，
 * 所以极容易被误判成「这个页面坏了」—— 排查时值得先等 5 秒再下结论。
 * 遮罩是 90% 不透明白，用户看到的是隐约透出的 404 文案。
 *
 * 用这个开关把 `router-view` 压到路由注册完成之后再渲染，404 就没有机会被画出来。
 */
const routesReady = ref(false)
const userStore = useUserStore()

onErrorCaptured((error) => {
  reportError(error, 'console-render')
  loadError.value = true
  errorTitle.value = '页面渲染异常'
  errorCopy.value = describeUserError(error)
  return false
})

function applyMenuPermissions(menus: MenuEntity[]): MenuEntity[] {
  return menus.map((menu) => {
    if (menu.permission)
      menu.meta = { ...menu.meta, permission: menu.permission }
    if (menu.children?.length)
      menu.children = applyMenuPermissions(menu.children)
    return menu
  })
}

async function getMenuList() {
  menuList.value = applyMenuPermissions(await UserService.create(isLoading).getMyMenuList())
  initVueRouter(menuList.value, components, '/src/view', 'console')
  await reloadCurrentRoute()
  // 受限角色（如 WMS 仓储专员）未授权工作台：登录默认落在 `/console/index` 会命中
  // catch-all 的 404 页。路由注册完毕后若当前仍是工作台 404，回退到其首个可导航授权菜单，
  // 让登录后看到的是可用页面，而不是一片「很抱歉，没有找到你想访问的资源」。
  // 只在「默认落地地址」命中 catchAll 时回退，手动输入的不存在地址仍照常显示 404，不受影响。
  const current = getRouter().currentRoute.value
  const onWorkbenchFallback = (current.matched.at(-1)?.path ?? '').includes('catchAll')
    && (DEFAULT_HOME_PATHS as readonly string[]).includes(current.path)
  if (onWorkbenchFallback) {
    // 回退要区分两种情形：有授权菜单 → 去第一个可导航页；一个可导航菜单都没有
    // （例如没分配任何角色的用户）→ 去 /403 无授权页。若仍用默认兜底 '/console/index'，
    // 等于换条路再撞同一个 catch-all —— 无角色用户永远看到「404 地址不存在」，
    // 语义错了，而且该页此前以 fixed 覆盖整个外壳，还连带制造"滚不动/底部栏没了"的观感
    // （见 view/error.vue 的注释）。
    const target = firstNavigableMenuPath(menuList.value, '')
    await getRouter().replace(target || '/403')
  }
  // 必须排在 reloadCurrentRoute 之后：那时当前路由已经从 catchAll 换成真实页面，
  // 再放开 router-view，用户看到的第一帧就是正确内容，而不是「404 → 真页面」的跳变。
  routesReady.value = true
}

async function init() {
  try {
    // 先刷新服务端允许的租户集合，再请求任何业务列表；这样持久化的旧租户不会污染首个请求。
    const tenants = await authApi.getMyTenantList()
    userStore.setTenantList(tenants || [])
    // 在服务端选项返回并校正持久化租户前，不渲染旧租户的 header 或页面状态。
    tenantReady.value = true
    currentUserInfo.value = await UserService.create().getMyInfo()
    userStore.setUserInfo(currentUserInfo.value)
    const permissions = await UserService.create(isLoading).getMyPermissionList()
    usePermissionStore().setPermissions(permissions)
    await getMenuList()
    loadError.value = false
  }
  catch (error) {
    reportError(error, 'console-init')
    loadError.value = true
    // D5：初始化失败最常见的原因是「后端没起 / 网络不通 / 发版后旧 chunk 失效」，
    // 这三种情形的下一步完全不同；把 error.message（多为英文原文）当副标题等于没指引。
    errorTitle.value = '控制台初始化失败'
    errorCopy.value = describeUserError(error)
    usePermissionStore().clear()
  }
  finally {
    isLoading.value = false
  }
}

async function retryInit() {
  loadError.value = false
  tenantReady.value = false
  isLoading.value = true
  await init()
}

function backToHome() {
  getRouter().push('/console/index')
}

init()
</script>

<template>
  <div
    v-if="loadError"
    class="console-error"
  >
    <el-result
      icon="error"
      :title="errorTitle"
      :sub-title="errorCopy?.hint"
    >
      <template #extra>
        <!-- 原始错误文本：排查必需（多为英文原文），降级为次要信息常驻可见 -->
        <div
          v-if="errorCopy?.detail"
          class="console-error__raw"
        >
          {{ errorCopy.detail }}
        </div>
        <div class="console-error__actions">
          <el-button
            type="primary"
            @click="retryInit"
          >
            重试
          </el-button>
          <el-button @click="backToHome">
            返回首页
          </el-button>
        </div>
      </template>
    </el-result>
  </div>
  <div
    v-else-if="!tenantReady"
    v-loading="true"
    class="console-loading"
  />
  <!-- 新壳（KAppShell，P0 视觉焕新）：设计 token + Element Plus 自研布局 -->
  <KAppShell
    v-else
    v-loading="isLoading || !routesReady"
    :header-height="56"
    :menu-list="menuList"
    :user="currentUserInfo"
  >
    <!-- V3：路由过渡（淡入 + 轻微上移，只动 opacity/transform，合成器友好） -->
    <!-- v-if 的理由见 routesReady 的注释：路由注册前渲染 router-view 等于先把 404 页画出来 -->
    <router-view
      v-if="routesReady"
      v-slot="{ Component }"
    >
      <Transition
        name="fade-slide"
        mode="out-in"
      >
        <component :is="Component" />
      </Transition>
    </router-view>
  </KAppShell>
</template>

<style lang="scss" scoped>
.console-error,
.console-loading {
  min-height: 100vh;
}

.console-error {
  display: flex;
  align-items: center;
  justify-content: center;
}

/* D5：原始错误文本降为次要信息（排查需要，但不与中文指引抢主角位） */
.console-error__raw {
  max-width: 560px;
  margin: 0 auto var(--kubo-spacing-4);
  font-size: var(--el-font-size-extra-small);
  color: var(--el-text-color-placeholder);
  text-align: left;
  word-break: break-all;
}

.console-error__actions {
  display: flex;
  justify-content: center;
  gap: var(--kubo-spacing-3);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  text-decoration: none;
  color: white;
  margin-top: 5px;
}

/* V3：路由过渡（只动 opacity/transform） */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition:
    opacity var(--kubo-duration-base) var(--kubo-ease-out),
    transform var(--kubo-duration-base) var(--kubo-ease-out);
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* V8：用户偏好减少动效时关闭路由过渡 */
@media (prefers-reduced-motion: reduce) {
  .fade-slide-enter-active,
  .fade-slide-leave-active {
    transition: none;
  }
}
</style>

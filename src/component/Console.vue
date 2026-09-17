<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import { onErrorCaptured, ref } from 'vue'
import { authApi } from '@/api/system'
import KAppShell from '@/component/KAppShell.vue'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { getRouter, initVueRouter, reloadCurrentRoute } from '@/router/appRouter'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { reportError } from '@/utils/errorHandler'
import '@/config/airpower-bootstrap'

const components = import.meta.glob('/src/view/**/*.vue')
const currentUserInfo = ref(new UserEntity())
const menuList = ref<MenuEntity[]>([])
const isLoading = ref(true)
const loadError = ref(false)
const errorMessage = ref('')
const errorTitle = ref('')
const tenantReady = ref(false)
const userStore = useUserStore()

onErrorCaptured((error) => {
  reportError(error, 'console-render')
  loadError.value = true
  errorTitle.value = '页面渲染异常'
  errorMessage.value = error instanceof Error ? error.message : '页面渲染异常，请刷新或重试'
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
  // `WebConfig.isTimeout` 的复位已挪进 `@/router/appRouter` 的 `initVueRouter()`
  // —— 与真正消费它的 airpower `RouterUtil.initVueRouter` 放在一起，
  // 于是 Console（应用外壳）不再需要 import airpower。
  initVueRouter(menuList.value, components, '/src/view', 'console')
  await reloadCurrentRoute()
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
    errorTitle.value = '控制台初始化失败'
    errorMessage.value = error instanceof Error ? error.message : '菜单或权限加载失败，请重试'
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
  getRouter().push('/home')
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
      :sub-title="errorMessage"
    >
      <template #extra>
        <el-button
          type="primary"
          @click="retryInit"
        >
          重试
        </el-button>
        <el-button @click="backToHome">
          返回首页
        </el-button>
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
    v-loading="isLoading"
    :header-height="56"
    :menu-list="menuList"
    :user="currentUserInfo"
  >
    <!-- V3：路由过渡（淡入 + 轻微上移，只动 opacity/transform，合成器友好） -->
    <router-view v-slot="{ Component }">
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

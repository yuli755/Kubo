<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import { AFrame, RouterUtil } from '@airpower/web'
import { onErrorCaptured, ref } from 'vue'
import { authApi } from '@/api/system'
import TenantSwitcher from '@/component/TenantSwitcher.vue'
import UserCard from '@/component/UserCard.vue'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import { webSocketManager } from '@/websocket/WebSocketManager'

const components = import.meta.glob('/src/view/**/*.vue')
const currentUserInfo = ref(new UserEntity())
const menuList = ref<MenuEntity[]>([])
const isLoading = ref(true)
const loadError = ref(false)
const errorMessage = ref('')
const errorTitle = ref('')
const userStore = useUserStore()

onErrorCaptured((error) => {
  console.error('子组件渲染错误:', error)
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
  RouterUtil.initVueRouter(menuList.value, components, '/src/view', 'console')
  await RouterUtil.reloadCurrentRoute()
}

async function init() {
  try {
    // 先刷新服务端允许的租户集合，再请求任何业务列表；这样持久化的旧租户不会污染首个请求。
    const tenants = await authApi.getMyTenantList()
    userStore.setTenantList(tenants || [])
    currentUserInfo.value = await UserService.create().getMyInfo()
    userStore.setUserInfo(currentUserInfo.value)
    const permissions = await UserService.create(isLoading).getMyPermissionList()
    usePermissionStore().setPermissions(permissions)
    await getMenuList()
    loadError.value = false
    webSocketManager.connect()
  }
  catch (error) {
    console.error('初始化控制台失败:', error)
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
  isLoading.value = true
  await init()
}

function backToHome() {
  RouterUtil.router.push('/home')
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
  <AFrame
    v-else
    v-loading="isLoading"
    :header-height="50"
    :menu-list="menuList"
  >
    <template #logo>
      <img
        class="logo"
        height="40"
        src="@/assets/img/logo.svg"
      >
    </template>
    <template #user>
      <div class="header-actions">
        <TenantSwitcher />
        <UserCard :user="currentUserInfo" />
      </div>
    </template>
    <router-view />
  </AFrame>
</template>

<style lang="scss" scoped>
.console-error {
  min-height: 100vh;
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
</style>

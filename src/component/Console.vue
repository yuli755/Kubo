<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import { AFrame, RouterUtil } from '@airpower/web'
import { ref } from 'vue'
import UserCard from '@/component/UserCard.vue'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { usePermissionStore } from '@/stores/permission'
import { webSocketManager } from '@/websocket/WebSocketManager'

const components = import.meta.glob('/src/view/**/*.vue')

const currentUserInfo = ref(new UserEntity())
const menuList = ref<MenuEntity[]>([])
const isLoading = ref(false)

async function getMenuList() {
  menuList.value = await UserService.create(isLoading).getMyMenuList()
  RouterUtil.initVueRouter(menuList.value, components, '/src/view', 'console')
  await RouterUtil.reloadCurrentRoute()
}

async function init() {
  currentUserInfo.value = await UserService.create().getMyInfo()
  const permissions = await UserService.create(isLoading).getMyPermissionList()
  const permissionStore = usePermissionStore()
  permissionStore.setPermissions(permissions)
  await getMenuList()
  // 用户信息加载完成后建立 WebSocket 连接（此时 token 已就绪）
  webSocketManager.connect()
}

init()
</script>

<template>
  <AFrame
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
      <UserCard :user="currentUserInfo" />
    </template>
    <router-view />
  </AFrame>
</template>

<style lang="scss" scoped>
.logo {
  text-decoration: none;
  color: white;
  margin-top: 5px;
}
</style>

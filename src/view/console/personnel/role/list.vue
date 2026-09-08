<script lang="ts" setup>
import type { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { AButton, APanel, ATable, DialogUtil } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { vPermission } from '@/directives'
import { RoleService } from '@/hook/personnel/useRole'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { RoleEditor, RoleMenuEditor, RolePermissionEditor } from './component'

async function onMenuEditor(role: RoleEntity) {
  DialogUtil.show(RoleMenuEditor, role)
}

async function onPermissionEditor(role: RoleEntity) {
  DialogUtil.show(RolePermissionEditor, role)
}

const hook = useTanStackTable(RoleService, {
  queryKey: ['personnel:role'],
  editView: RoleEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Personnel.Role.add"
      :delete-permission="Permissions.Personnel.Role.delete"
      :disable-permission="Permissions.Personnel.Role.disable"
      :edit-permission="Permissions.Personnel.Role.update"
      :enable-permission="Permissions.Personnel.Role.enable"
      ctrl-width="200"
      show-enable-and-disable
    >
      <template #customRow="{ data }">
        <AButton
          v-permission="Permissions.Personnel.Role.authorizeMenu"
          link
          @click="onMenuEditor(data)"
        >
          菜单
        </AButton>
        <AButton
          v-permission="Permissions.Personnel.Role.authorizePermission"
          link
          @click="onPermissionEditor(data)"
        >
          权限
        </AButton>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

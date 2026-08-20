<script lang="ts" setup>
import type { IWebEnum } from '@airpower/web'
import { APanel, ATable } from '@airpower/web'
import { ref } from 'vue'
import { useMyTable } from '@/hook/useMyTable'
import { NotifyService } from '@/model/open/notify/NotifyService'
import { Permissions } from '@/config/permissions'
import { NotifyEditor } from '@/view/console/open/notify/component'

const hook = useMyTable(NotifyService, {
  editView: NotifyEditor,
})

const sceneList = ref<IWebEnum[]>([])
NotifyService.create().getSceneList().then((list) => {
  sceneList.value = list
})
</script>

<template>
  <APanel>
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Open.Notify.add"
      :delete-permission="Permissions.Open.Notify.delete"
      :disable-permission="Permissions.Open.Notify.disable"
      :edit-permission="Permissions.Open.Notify.update"
      :enable-permission="Permissions.Open.Notify.enable"
      ctrl-width="130"
      show-enable-and-disable
    >
      <template #scene="{ data }">
        {{ sceneList.find(item => item.key === data.scene)?.label || '-' }}
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

<script lang="ts" setup>
import { AButton, APanel, ATable, DialogUtil } from '@airpower/web'
import { ref } from 'vue'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DeviceEntity } from '@/model/asset/device/DeviceEntity'
import { DeviceService } from '@/model/asset/device/DeviceService'
import { DeviceEditor, DeviceMonitor } from './component'
import { Permissions } from '@/config/permissions'

const filter = ref(new DeviceEntity())
const hook = useTanStackTable(DeviceService, {
  queryKey: ['asset:device'],
  editView: DeviceEditor,
})

function showMonitor(device: DeviceEntity) {
  DialogUtil.show(DeviceMonitor, device)
}
</script>

<template>
  <APanel>
    <ATable
      :default-filter="filter"
      :use-hook="hook"
      :add-permission="Permissions.Asset.Device.add"
      :delete-permission="Permissions.Asset.Device.delete"
      :edit-permission="Permissions.Asset.Device.update"
      ctrl-width="130"
    >
      <template #customRow="{ data }">
        <AButton
          :disabled="!data.isReporting"
          link
          @click="showMonitor(data)"
        >
          监控
        </AButton>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

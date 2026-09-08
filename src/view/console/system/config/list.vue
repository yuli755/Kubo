<script lang="ts" setup>
import { AButton, APanel, ATable, FeedbackUtil } from '@airpower/web'
import { Constant } from '@/config/Constant'
import { Permissions } from '@/config/permissions'
import { ConfigService } from '@/hook/system/useConfig'

import { useTanStackTable } from '@/hook/useTanStackTable'
import { ConfigType } from '@/model/system/config/ConfigType'
import { ConfigurationEditor } from '@/view/console/system/config/component'

const hook = useTanStackTable(ConfigService, {
  queryKey: ['system:config'],
  editView: ConfigurationEditor,
})
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.isSystem"
      :use-hook="hook"
      :edit-permission="Permissions.System.Config.update"
      ctrl-width="90"
      hide-add
    >
      <template #name="{ data }">
        <!-- permission-exempt: 只弹一条本地说明文案，不读写服务端数据 -->
        <AButton
          link
          @click="FeedbackUtil.toastInfo(data.description)"
        >
          {{ data.name }}
        </AButton>
      </template>
      <template #config="{ data }">
        <template v-if="ConfigType.BOOLEAN.equalsKey(data.type)">
          <el-switch
            :model-value="data.config === Constant.BOOLEAN_YES"
            disabled
          />
        </template>
        <template v-else>
          {{ data.config }}
        </template>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

<script lang="ts" setup>
import type { OpenLogEntity } from '@/model/open/log/OpenLogEntity'
import { ADialog, ATable, DialogProps, DialogUtil } from '@airpower/web'
import { OpenLogService } from '@/hook/open/useOpenLog'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { OpenAppEntity } from '@/model/open/app/OpenAppEntity'

import { OpenAppLogDetail } from './index'

const props = defineProps(DialogProps.withParam(new OpenAppEntity()))

const hook = useTanStackTable(OpenLogService, {
  queryKey: ['open:log'],
  beforeSearch(requestData) {
    requestData.filter.openApp = props.param.copyOnlyId()
    return requestData
  },
})

function onDetail(log: OpenLogEntity) {
  DialogUtil.show(OpenAppLogDetail, log)
}
</script>

<template>
  <ADialog
    height="80%"
    title="请求日志"
    width="80%"
    @cancel="onCancel"
    @confirm="onConfirm"
  >
    <ATable
      :on-detail="onDetail"
      :use-hook="hook"
      ctrl-width="100"
      hide-delete
      hide-edit
      show-detail
    >
      <template #mSecond="{ data }">
        {{ data.updateTime - data.createTime }}ms
      </template>
    </ATable>
  </ADialog>
</template>

<style lang="scss" scoped></style>

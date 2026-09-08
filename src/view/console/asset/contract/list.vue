<script lang="ts" setup>
import type { ContractEntity } from '@/model/asset/contract/ContractEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { Permissions } from '@/config/permissions'
import { vPermission } from '@/directives'
import { ContractService } from '@/hook/asset/useContract'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { ContractStatusEnum } from '@/model/asset/contract/ContractStatusEnum'
import { ContractDetail, ContractEditor } from './component'

const hook = useTanStackTable(ContractService, {
  queryKey: ['asset:contract'],
  editView: ContractEditor,
})

async function onEnforce(contract: ContractEntity) {
  const roles: number[] = []
  contract.participantList.forEach((item) => {
    if (!roles.includes(item.role)) {
      roles.push(item.role)
    }
  })
  if (roles.length < 2) {
    FeedbackUtil.toastError('参与方至少有两方，请先确认')
    return
  }
  if (contract.documentList.length === 0) {
    await FeedbackUtil.confirmWarning('没有上传任何附件，是否继续生效合同？', '生效合同')
  }
  else {
    await FeedbackUtil.confirmWarning('是否确认生效这个合同？', '生效合同')
  }
  await ContractService.create(hook.isLoading).enforce(contract)
  hook.onReloadData()
}

async function onStop(contract: ContractEntity) {
  await FeedbackUtil.confirmWarning('是否确认终止这个合同？', '终止合同')
  await ContractService.create(hook.isLoading).stop(contract)
  hook.onReloadData()
}

async function onDetail(contract: ContractEntity) {
  DialogUtil.show(ContractDetail, contract)
}
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="row => !ContractStatusEnum.INVALID.equalsKey(row.status)"
      :disable-edit="row => !ContractStatusEnum.INVALID.equalsKey(row.status)"
      :use-hook="hook"
      :add-permission="Permissions.Asset.Contract.add"
      :delete-permission="Permissions.Asset.Contract.delete"
      :edit-permission="Permissions.Asset.Contract.update"
      ctrl-width="200"
    >
      <template #customRow="{ data }">
        <AButton
          v-permission="Permissions.Asset.Contract.enforce"
          :disabled="!ContractStatusEnum.INVALID.equalsKey(data.status)"
          link
          @click="onEnforce(data)"
        >
          生效
        </AButton>
        <AButton
          v-permission="Permissions.Asset.Contract.stop"
          :disabled="!ContractStatusEnum.EFFECTIVE.equalsKey(data.status)"
          link
          @click="onStop(data)"
        >
          终止
        </AButton>
        <AButton
          v-permission="Permissions.Asset.Contract.getDetail"
          link
          @click="onDetail(data)"
        >
          查看
        </AButton>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

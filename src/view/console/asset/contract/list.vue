<script lang="ts" setup>
import type { ContractEntity } from '@/model/asset/contract/ContractEntity'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KContractService } from '@/hook/asset/useContract'
import { useTable } from '@/hook/kubo/useTable'
import { ContractStatusEnum } from '@/model/asset/contract/ContractStatusEnum'
import ContractDetail from '@/view/console/asset/contract/component/detail.vue'

/**
 * # 合同（F4 批 2 · 新底座）
 *
 * 这一页比批 1 的树表多了两类"组件没内置"的东西，正好验证 `KTable` 的扩展点够不够：
 *
 * 1. **状态相关的行操作**（生效 / 终止 / 查看）：走 `:actions`（`RowAction[]`），
 *    可用性判据来自实体状态机 `ContractStatusEnum.equalsKey(row.status, ...)`；
 * 2. **状态相关的编辑/删除禁用**：`:disable-edit` / `:disable-delete` —— 只有「未生效」的合同能改。
 *
 * 三处提示语与判据是**照搬旧页**的（参与方至少两方、没附件要多问一句），
 * 因为这些是业务规则、不是组件行为：换底座不该顺手改规则。
 */
const table = useTable(KContractService, { queryKey: ['asset:contract'] })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

const isInvalid = (row: ContractEntity) => ContractStatusEnum.INVALID.equalsKey(row.status)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: ContractEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: ContractEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

/**
 * 合同生效。
 *
 * 判据（参与方至少两方）在前端先给一次提示，是因为"点下去才被后端拒"体验太差；
 * **真正的闸门仍在后端**（状态机 + 审计），前端这层只是提前说明。
 */
async function onEnforce(contract: ContractEntity) {
  const roles = new Set(contract.participantList.map(item => item.role))
  if (roles.size < 2) {
    FeedbackUtil.toastError('参与方至少有两方，请先确认')
    return
  }
  if (contract.documentList.length === 0) {
    await FeedbackUtil.confirmWarning('没有上传任何附件，是否继续生效合同？', '生效合同')
  }
  else {
    await FeedbackUtil.confirmWarning('是否确认生效这个合同？', '生效合同')
  }
  await table.service.enforce(contract)
  FeedbackUtil.toastSuccess('已生效')
  table.reload()
}

async function onStop(contract: ContractEntity) {
  await FeedbackUtil.confirmWarning('是否确认终止这个合同？', '终止合同')
  await table.service.stop(contract)
  FeedbackUtil.toastSuccess('已终止')
  table.reload()
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="合同"
      description="合同档案、参与方与附件；生效/终止是领域动作，判据在后端"
    >
      <KTable
        :actions="[
          {
            label: '生效',
            permission: Permissions.Asset.Contract.enforce,
            disabled: row => !isInvalid(row),
            handler: onEnforce,
          },
          {
            label: '终止',
            permission: Permissions.Asset.Contract.stop,
            disabled: row => !ContractStatusEnum.EFFECTIVE.equalsKey(row.status),
            handler: onStop,
          },
          {
            label: '查看',
            permission: Permissions.Asset.Contract.getDetail,
            handler: openDetail,
          },
        ]"
        :disable-delete="row => !isInvalid(row)"
        :disable-edit="row => !isInvalid(row)"
        :permissions="{
          add: Permissions.Asset.Contract.add,
          edit: Permissions.Asset.Contract.update,
          delete: Permissions.Asset.Contract.delete,
        }"
        :table="table"
        @add="openCreate"
        @edit="openEdit"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :columns="2"
      :service="KContractService"
      title="合同"
      width="760px"
      @saved="onSaved"
    />

    <ContractDetail
      :id="detailId"
      v-model="detailVisible"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  padding: 16px;
}
</style>

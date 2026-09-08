<script lang="ts" setup>
import { APanel, ATable, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'
import PublishButton from '@/component/PublishButton.vue'
import { Permissions } from '@/config/permissions'
import { BomService, useBomPublish } from '@/hook/mes/useBom'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { BomDetail, BomEditor } from './component'

const queryClient = useQueryClient()

const hook = useTanStackTable(BomService, {
  queryKey: ['mes:bom'],
  editView: BomEditor,
  detailView: BomDetail,
})

const { mutateAsync: publishMutate } = useBomPublish()
async function onPublish(entity: { id: number }) {
  await FeedbackUtil.confirmWarning('发布后将无法再次修改，是否确认？', '确认发布')
  await publishMutate({ id: entity.id })
  hook.onReloadData()
  queryClient.invalidateQueries({ queryKey: ['mes:bom'] })
}
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.isPublished"
      :disable-edit="(row) => row.isPublished"
      :use-hook="hook"
      :add-permission="Permissions.Mes.Bom.add"
      :delete-permission="Permissions.Mes.Bom.delete"
      :edit-permission="Permissions.Mes.Bom.update"
      ctrl-width="160"
    >
      <template #customRow="{ data }">
        <PublishButton
          v-if="!data.isPublished"
          :data="data"
          :publish-permission="Permissions.Mes.Bom.publish"
          @click="onPublish(data)"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

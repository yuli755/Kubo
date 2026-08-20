<script lang="ts" setup>
import { APanel, ATable, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'
import PublishButton from '@/component/PublishButton.vue'
import { useBomPublish } from '@/hook/mes/useBom'
import { useMyTable } from '@/hook/useMyTable'
import { BomService } from '@/model/mes/bom/BomService'
import { BomDetail, BomEditor } from './component'

const queryClient = useQueryClient()

const hook = useMyTable(BomService, {
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
      add-permission="Bom_add"
      delete-permission="Bom_delete"
      edit-permission="Bom_update"
      ctrl-width="160"
    >
      <template #customRow="{ data }">
        <PublishButton
          v-if="!data.isPublished"
          :source="data"
          @publish="onPublish(data)"
        />
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

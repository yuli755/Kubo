<script lang="ts" setup>
import type { RoutingEntity } from '@/model/mes/routing/RoutingEntity'
import { AButton, APanel, ATable, DialogUtil, FeedbackUtil } from '@airpower/web'
import { useQueryClient } from '@tanstack/vue-query'
import PublishButton from '@/component/PublishButton.vue'
import { useRoutingPublish } from '@/hook/mes/useRouting'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { RoutingService } from '@/model/mes/routing/RoutingService'
import { Permissions } from '@/config/permissions'
import { RoutingEditor, RoutingProgress } from './component'

const queryClient = useQueryClient()

const hook = useTanStackTable(RoutingService, {
  queryKey: ['mes:routing'],
  editView: RoutingEditor,
})

const { mutateAsync: publishMutate } = useRoutingPublish()
async function onPublish(entity: { id: number }) {
  await FeedbackUtil.confirmWarning('发布后将无法再次修改，是否确认？', '确认发布')
  await publishMutate({ id: entity.id })
  hook.onReloadData()
  queryClient.invalidateQueries({ queryKey: ['mes:routing'] })
}

async function onProgress(data: RoutingEntity) {
  await DialogUtil.show(RoutingProgress, data)
}
</script>

<template>
  <APanel>
    <ATable
      :disable-delete="(row) => row.isPublished"
      :disable-edit="(row) => row.isPublished"
      :use-hook="hook"
      :add-permission="Permissions.Mes.Routing.add"
      :delete-permission="Permissions.Mes.Routing.delete"
      :edit-permission="Permissions.Mes.Routing.update"
      ctrl-width="160"
    >
      <template #customRow="{ data }">
        <PublishButton
          v-if="!data.isPublished"
          :data="data"
          :publish-permission="Permissions.Mes.Routing.publish"
          @click="onPublish(data)"
        />
        <AButton
          v-if="data.isPublished"
          link
          @click="onProgress(data)"
        >
          流程
        </AButton>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped></style>

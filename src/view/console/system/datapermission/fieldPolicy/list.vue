<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useInertPolicyReport } from '@/composables/useInertPolicyReport'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DataFieldPolicyService } from '@/model/system/datapermission/DataFieldPolicyService'
import { DataFieldPolicyEditor } from './component'

// 存量盘点：哪些行是"配了但没人读"的动作。判定在后端，这里只负责列出来。
const { count: inertCount, preview: inertPreview, failed: inertFailed } = useInertPolicyReport()

const hook = useTanStackTable(DataFieldPolicyService, {
  queryKey: ['system:datapermission:fieldPolicy'],
  editView: DataFieldPolicyEditor,
})
</script>

<template>
  <APanel>
    <ElAlert
      v-if="inertCount"
      class="inert-warning"
      type="warning"
      :closable="false"
      show-icon
      title="有已启用的列级策略，其动作不会被列级遮蔽读到"
      :description="`${inertCount} 条（${inertPreview.join('、')}）。这类行保存后会留在表里、计入「已配置」，但不改变任何输出：要么停用，要么到「数据权限-行级策略」按行级方式配置。`"
    />
    <ElAlert
      v-else-if="inertFailed"
      class="inert-warning"
      type="info"
      :closable="false"
      show-icon
      title="未能取得存量盘点结果"
      description="刷新本页或检查 /dataResource/inert-policies 是否可用；取不到结果时不会显示为「没有孤儿策略」。"
    />
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.System.DataFieldPolicy.add"
      :delete-permission="Permissions.System.DataFieldPolicy.delete"
      :edit-permission="Permissions.System.DataFieldPolicy.update"
    />
  </APanel>
</template>

<style lang="scss" scoped>
.inert-warning {
  margin-bottom: 8px;
}
</style>

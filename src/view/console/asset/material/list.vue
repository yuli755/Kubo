<script lang="ts" setup>
import { APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { useExportVisibility } from '@/composables/useExportVisibility'
import { Permissions } from '@/config/permissions'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { MaterialService } from '@/model/asset/material/MaterialService'
import { UnitEntity } from '@/model/system/unit/UnitEntity'
import { MaterialEditor } from './component'

// 导出可见性：只渲染后端结论。取数失败时 blocked 为空 ⇒ 什么都不提示（见 composable 的失败取向）
const { blocked: exportBlockedFields } = useExportVisibility('asset.material')

const hook = useTanStackTable(MaterialService, {
  queryKey: ['asset:material'],
  editView: MaterialEditor,
  beforeSearch: (requestData) => {
    if (requestData.filter.unitName) {
      const unit = new UnitEntity()
      unit.name = requestData.filter.unitName
      requestData.filter.unit = unit
      requestData.filter.unitName = undefined
    }
    return requestData
  },
})
</script>

<template>
  <APanel>
    <ElAlert
      v-if="exportBlockedFields.length"
      class="export-warning"
      type="warning"
      :closable="false"
      show-icon
      title="导出会被拒绝"
      :description="`这些字段你只能看脱敏值、且没有导出明文授权：${exportBlockedFields.join('、')}。导出是一整份文件，缺一个字段就整份被拒；请到「列级脱敏策略」申请 actionName=EXPORT 且允许明文。`"
    />
    <ATable
      :use-hook="hook"
      :add-permission="Permissions.Asset.Material.add"
      :delete-permission="Permissions.Asset.Material.delete"
      :edit-permission="Permissions.Asset.Material.update"
      :export-permission="Permissions.Asset.Material.export"
      show-export
    />
  </APanel>
</template>

<style lang="scss" scoped>
.export-warning {
  margin-bottom: 8px;
}
</style>

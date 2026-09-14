<script lang="ts" setup>
import { AMoney, APanel, ATable } from '@airpower/web'
import { ElAlert } from 'element-plus'
import { onMounted } from 'vue'
import { useExportVisibility } from '@/composables/useExportVisibility'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { MaterialService } from '@/hook/asset/useMaterial'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { UnitEntity } from '@/model/system/unit/UnitEntity'
import { MaterialEditor } from './component'

// 导出可见性：只渲染后端结论。取数失败时 blocked 为空 ⇒ 什么都不提示（见 composable 的失败取向）
const { blocked: exportBlockedFields } = useExportVisibility('asset.material')

// 采购/销售标准价被后端 @SensitiveField 标记。这两列默认隐藏（hide: true），
// 但用户可通过列选择器打开 —— 打开后同样按 my-mask 策略遮蔽，不因"隐藏"而漏遮。
const materialPricePolicy = useMaskPolicy('asset.material', ['purchasePrice', 'salePrice'])
onMounted(() => materialPricePolicy.load())

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
    >
      <template #purchasePrice="{ data }">
        <AMoney
          v-if="!materialPricePolicy.restricted('purchasePrice')"
          :money="data.purchasePrice"
        />
        <span v-else>{{ materialPricePolicy.masked('purchasePrice', data.purchasePrice) }}</span>
      </template>
      <template #salePrice="{ data }">
        <AMoney
          v-if="!materialPricePolicy.restricted('salePrice')"
          :money="data.salePrice"
        />
        <span v-else>{{ materialPricePolicy.masked('salePrice', data.salePrice) }}</span>
      </template>
    </ATable>
  </APanel>
</template>

<style lang="scss" scoped>
.export-warning {
  margin-bottom: 8px;
}
</style>

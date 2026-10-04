<script lang="ts" setup>
import type { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { ElAlert, ElButton, ElMessage, ElOption, ElSelect } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KSelector from '@/component/framework/KSelector.vue'
import KTable from '@/component/framework/KTable.vue'
import { useExportVisibility } from '@/composables/useExportVisibility'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { formatMoney, QueryRequest } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { useTable } from '@/hook/kubo/useTable'
import { KUnitService } from '@/hook/system/useUnit'
import { UnitEntity } from '@/model/system/unit/UnitEntity'

/**
 * # 物料（F4 批 2 · 新底座）
 *
 * 迁移对照（三处不是"换个组件名"而是语义修正）：
 *
 * | 点 | airpower 写法 | 这里 |
 * | --- | --- | --- |
 * | 列表 | `useTanStackTable(MaterialService, { editView })` | `useTable(KMaterialService, { export: true })` |
 * | 编辑 | `editor.vue` + `useEditor` + `AFormField` 手写字段 | `KFormDialog` 按 `@Form` 元数据渲染（编辑器文件已删） |
 * | 金额列 | `AMoney` + `#purchasePrice` 插槽 | `#cell-purchasePrice` 插槽（同样的遮蔽判据，渲染走 `formatMoney`） |
 * | 计量单位 | `UnitSelector`（airpower `ASelector`） | `KSelector` + `KUnitService`（单位是系统域的实体，支持下拉直选与选择器双模） |
 * | 导出 | `ATable show-export`（内部弹窗轮询） | `export: true` + `ExportUtil`（建任务 → 轮询 → 下载） |
 *
 * **价格列必须过脱敏策略**：后端把 `purchasePrice` / `salePrice` 标成敏感字段，
 * 受限角色拿到的是掩码。列表侧先问一次 `my-mask` 结论再决定渲染什么 ——
 * 不让前端自己判断"该不该遮"（那样就有了第三份真相）。
 */
const { blocked: exportBlockedFields } = useExportVisibility('asset.material')

const materialPricePolicy = useMaskPolicy('asset.material', ['purchasePrice', 'salePrice'])

const unitList = ref<UnitEntity[]>([])

onMounted(async () => {
  materialPricePolicy.load()
  try {
    unitList.value = await new KUnitService().getList(new QueryRequest(UnitEntity))
  }
  catch {
    unitList.value = []
  }
})

const table = useTable(KMaterialService, {
  queryKey: ['asset:material'],
  export: true,
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const unitPicking = ref(false)
const form = ref<Record<string, unknown>>({})
const unitLabel = ref('')

const selectedUnitId = computed(() => {
  const u = form.value.unit as UnitEntity | undefined
  return u?.id ?? (form.value.unitId as number | undefined)
})

function onUnitDropdownChange(id: number | null) {
  if (!id) {
    form.value.unit = undefined
    form.value.unitId = undefined
    unitLabel.value = ''
    return
  }
  const hit = unitList.value.find(u => u.id === id)
  if (hit) {
    pickUnit([hit])
  }
}

function openCreate() {
  editingId.value = null
  unitLabel.value = ''
  editorVisible.value = true
}

function openEdit(row: MaterialEntity) {
  editingId.value = row.id
  unitLabel.value = ''
  editorVisible.value = true
}

function onLoaded(next: Record<string, unknown>) {
  form.value = next
  const unit = next.unit as UnitEntity | undefined
  unitLabel.value = unit ? `${unit.name}${unit.code ? `(${unit.code})` : ''}` : ''
}

function pickUnit(rows: unknown[]) {
  const unit = rows[0] as UnitEntity | undefined
  if (!unit)
    return
  form.value.unit = unit
  form.value.unitId = unit.id
  unitLabel.value = `${unit.name}${unit.code ? `(${unit.code})` : ''}`
  unitPicking.value = false
}

/**
 * 提交前守卫：价格被脱敏策略限制时**不许保存**。
 *
 * 判据来自后端（`my-mask` 结论），不是前端猜的：受限角色看到的掩码值一旦提交回后端，
 * 等于把"看不见的字段"写成掩码字符串。这里提前拦住并说明原因，返回 `null` 中止提交。
 */
function beforeSubmit(payload: Record<string, unknown>): Record<string, unknown> | null {
  const warning = materialPricePolicy.blockedSubmit(payload)
  if (warning) {
    ElMessage.warning(warning)
    return null
  }
  return payload
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="物料"
      description="物料档案与标准价；价格字段受列级脱敏策略管控"
    >
      <template #extra>
        <ElAlert
          v-if="exportBlockedFields.length"
          :description="`这些字段你只能看脱敏值、且没有导出明文授权：${exportBlockedFields.join('、')}。导出是一整份文件，缺一个字段就整份被拒；请到「列级脱敏策略」申请 actionName=EXPORT 且允许明文。`"
          :closable="false"
          class="export-warning"
          show-icon
          title="导出会被拒绝"
          type="warning"
        />
      </template>

      <KTable
        :permissions="{
          add: Permissions.Asset.Material.add,
          edit: Permissions.Asset.Material.update,
          delete: Permissions.Asset.Material.delete,
          export: Permissions.Asset.Material.export,
        }"
        :table="table"
        show-export
        @add="openCreate"
        @edit="openEdit"
      >
        <!-- 金额列：受策略限制时显示掩码，否则走框架统一的金额格式（右对齐 + 千分位 + 两位小数） -->
        <template #cell-purchasePrice="{ row }">
          <span v-if="materialPricePolicy.restricted('purchasePrice')">
            {{ materialPricePolicy.masked('purchasePrice', row.purchasePrice) }}
          </span>
          <span v-else>{{ formatMoney(row.purchasePrice) }}</span>
        </template>
        <template #cell-salePrice="{ row }">
          <span v-if="materialPricePolicy.restricted('salePrice')">
            {{ materialPricePolicy.masked('salePrice', row.salePrice) }}
          </span>
          <span v-else>{{ formatMoney(row.salePrice) }}</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :before-submit="beforeSubmit"
      :columns="2"
      :service="KMaterialService"
      title="物料"
      width="640px"
      @loaded="onLoaded"
      @saved="onSaved"
    >
      <!-- 计量单位：下拉直接点选 + 弹窗完整筛选双模协同 -->
      <template #field-unit="{ field }">
        <div class="unit-field">
          <ElSelect
            :model-value="selectedUnitId"
            :placeholder="`请选择${field.label}`"
            clearable
            filterable
            style="flex: 1"
            @change="onUnitDropdownChange"
          >
            <ElOption
              v-for="u in unitList"
              :key="u.id"
              :label="`${u.name} (${u.code})`"
              :value="u.id"
            />
          </ElSelect>
          <ElButton @click="unitPicking = true">
            高级选择
          </ElButton>
        </div>
      </template>
    </KFormDialog>

    <KSelector
      v-model="unitPicking"
      :service="KUnitService"
      title="选择计量单位"
      @confirm="pickUnit"
    />
  </div>
</template>

<style lang="scss" scoped>
.export-warning {
  margin-bottom: 8px;
}

.unit-field {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>

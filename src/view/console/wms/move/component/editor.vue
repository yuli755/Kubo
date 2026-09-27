<script lang="ts" setup>
import { Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElInput, ElInputNumber, ElMessage, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KStorageService } from '@/hook/factory/useStorage'
import { KMoveService } from '@/hook/wms/useMove'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { InventoryEntity } from '@/model/wms/inventory/InventoryEntity'
import { InventoryTypeEnum } from '@/model/wms/inventory/InventoryTypeEnum'
import { MoveDetailEntity } from '@/model/wms/move/MoveDetailEntity'
import { playScanSuccess, playScanWarning } from '@/utils/audioFeedback'
import { InventorySelector } from '@/view/console/wms/inventory/component'
import MoveDetailEditor from './detailEditor.vue'

/**
 * # 移库单编辑器（F4 批 4 · 新底座）
 *
 * 单据编辑器 = **主表单 + 明细数组**，这是它与普通实体编辑器的全部差别：
 * 主表单交给 `KFormDialog`（字段来自 `@KForm` 元数据），明细用 `KDetailTable`
 * 渲染同一个表单对象上的数组（还没提交给后端，因此没有服务、没有分页）。
 *
 * ## 三个子弹窗，三种取数方式
 * | 要选什么 | 用什么 | 为什么 |
 * | --- | --- | --- |
 * | 目标仓库 | `KSelector`（`tree`） | 单一实体、数据源固定，声明式最省事 |
 * | 来源库存 | `InventorySelector`（库存域选择器） | 它要「按仓库/生产单元两棵树筛选 + 按物料过滤」，不是 `KSelector` 能表达的形态；靠 `IDialogActionProps` 契约声明式打开（不再需要全局 `DialogUtil` 上下文） |
 * | 移动数量 | `MoveDetailEditor` | 明细行的局部编辑，`v-model` + `@confirm` |
 *
 * ## 一条业务判据（不许在迁移时丢）
 * 明细为空时**不发请求**，直接提示「请添加明细后再提交」——
 * 空明细的移库单在后端没有意义（既没有来源也没有数量），发出去只会换来一个 500。
 */
const props = defineProps<{
  modelValue: boolean
  /** 编辑的记录 id（不传为新增） */
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const form = ref<Record<string, unknown>>({})
const storagePicking = ref(false)
const inventoryPicking = ref(false)
/** 库存选择器的种子（带上类型，选择器据此决定左侧树用仓库还是生产单元） */
const inventorySeed = ref<InventoryEntity | undefined>(undefined)
const detailEditorVisible = ref(false)
const editingDetail = ref<MoveDetailEntity | null>(null)

const details = computed<MoveDetailEntity[]>(() => (form.value.details ?? []) as MoveDetailEntity[])

const storageLabel = computed(() => {
  const s = form.value.storage as { name?: string, code?: string } | undefined
  if (!s?.name) {
    return ''
  }
  return s.code ? `${s.name}(${s.code})` : s.name
})

/** 表单初始化：明细数组补空（新增时 `KFormDialog` 只重置 `@Form` 字段） */
function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
}

function onStoragePicked(rows: unknown[]) {
  form.value.storage = rows[0]
  storagePicking.value = false
}

function openInventoryPicker() {
  // 「按仓库」维度挑库存：移库的来源是仓库里的库存
  const seed = new InventoryEntity()
  seed.type = InventoryTypeEnum.STORAGE.key
  inventorySeed.value = seed
  inventoryPicking.value = true
}

function onInventoryPicked(picked: InventoryEntity) {
  inventoryPicking.value = false
  if (!picked) {
    return
  }
  const detail = new MoveDetailEntity()
  detail.inventory = picked
  // 默认填满可用数量：手填比库存多会被后端拒绝，默认值贴近上限少一次试错
  detail.quantity = picked.quantity
  editingDetail.value = detail
  detailEditorVisible.value = true
}

function onDetailConfirm(detail: MoveDetailEntity) {
  details.value.push(detail)
  detailEditorVisible.value = false
}

const scanBarcode = ref('')

function onScanEnter() {
  const code = scanBarcode.value.trim()
  if (!code) {
    playScanWarning()
    return
  }
  const found = details.value.find(d => d.materialCode === code || d.inventory?.material?.code === code)
  if (found) {
    found.quantity = (Number(found.quantity) || 0) + 1
    playScanSuccess()
    FeedbackUtil.toastSuccess(`移库物料 [${code}] 数量累加至 ${found.quantity}`)
    scanBarcode.value = ''
    return
  }

  const newDetail = new MoveDetailEntity()
  newDetail.materialCode = code
  newDetail.materialName = `物料 ${code}`
  newDetail.inventory = Object.assign(new InventoryEntity(), {
    material: Object.assign(new MaterialEntity(), {
      code,
      name: `物料 ${code}`,
    }),
  })
  newDetail.quantity = 1
  details.value.push(newDetail)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已快速录入移库物料 [${code}]，数量: 1`)
  scanBarcode.value = ''
}

function duplicateDetail(row: MoveDetailEntity) {
  const clone = row.copy() as MoveDetailEntity
  delete (clone as { id?: number }).id
  details.value.push(clone)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已复制移库明细 [${row.materialName || row.inventory?.material?.name || '物料'}]`)
}

function removeDetail(row: MoveDetailEntity) {
  const index = details.value.indexOf(row)
  if (index >= 0) {
    details.value.splice(index, 1)
  }
}

function beforeSubmit(payload: Record<string, unknown>) {
  if (!(payload.details as unknown[] | undefined)?.length) {
    ElMessage.warning('请添加明细后再提交')
    return null
  }
  return payload
}
</script>

<template>
  <KFormDialog
    :id="props.id"
    :model-value="props.modelValue"
    :before-submit="beforeSubmit"
    :columns="2"
    :service="KMoveService"
    title="移库单"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @loaded="onLoaded"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #field-storage>
      <div class="field-select-row">
        <ElInput
          :model-value="storageLabel"
          placeholder="请选择目标仓库..."
          readonly
          @click="storagePicking = true"
        />
        <ElButton @click="storagePicking = true">
          选择
        </ElButton>
      </div>
    </template>

    <template #extra>
      <div class="move-editor__details">
        <div class="move-editor__head">
          <div class="move-editor__head-left">
            <span class="move-editor__title">移库明细</span>
            <ElTag
              effect="plain"
              round
              size="small"
              type="info"
            >
              共 {{ details.length }} 项
            </ElTag>
          </div>
          <div class="move-editor__head-actions">
            <ElInput
              v-model="scanBarcode"
              placeholder="扫码枪扫入物料条码 (按回车直接录入/累加)"
              size="small"
              clearable
              class="scan-input"
              @keyup.enter="onScanEnter"
            >
              <template #prefix>
                <ElIcon><Search /></ElIcon>
              </template>
            </ElInput>
            <ElButton
              v-permission="[Permissions.Wms.Move.add, Permissions.Wms.Move.update]"
              :icon="Plus"
              size="small"
              type="primary"
              @click="openInventoryPicker"
            >
              从库存选择
            </ElButton>
          </div>
        </div>

        <KDetailTable
          :entity="MoveDetailEntity"
          :exclude="['createTime']"
          :row-actions="[
            { label: '复制', permission: [Permissions.Wms.Move.add, Permissions.Wms.Move.update], handler: duplicateDetail },
            { label: '删除', danger: true, permission: [Permissions.Wms.Move.add, Permissions.Wms.Move.update], handler: removeDetail },
          ]"
          :rows="details"
          empty-text="还没有明细，点「添加明细」从库存里挑一条"
        >
          <template #cell-quantity="{ row }">
            <ElInputNumber
              v-model="(row as MoveDetailEntity).quantity"
              :min="1"
              :max="Number((row as MoveDetailEntity).inventory?.quantity) || undefined"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
          </template>
        </KDetailTable>
      </div>
    </template>
  </KFormDialog>

  <KSelector
    v-model="storagePicking"
    :selected="form.storage ? [form.storage as never] : []"
    :service="KStorageService"
    title="选择目标仓库"
    tree
    @confirm="onStoragePicked"
  />

  <InventorySelector
    v-if="inventoryPicking"
    :param="inventorySeed"
    :on-cancel="() => inventoryPicking = false"
    :on-confirm="onInventoryPicked as never"
  />

  <MoveDetailEditor
    v-model="detailEditorVisible"
    :detail="editingDetail"
    @confirm="onDetailConfirm"
  />
</template>

<style lang="scss" scoped>
.field-select-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.move-editor__details {
  margin-top: 4px;
}

.move-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.move-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.move-editor__head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.scan-input {
  width: 280px;
}

.move-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

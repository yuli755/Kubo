<script lang="ts" setup>
import { Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElInput, ElInputNumber, ElMessage, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KSelector from '@/component/framework/KSelector.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KMaterialService } from '@/hook/asset/useMaterial'
import { KOutputService } from '@/hook/wms/useOutput'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { OutputDetailEntity } from '@/model/wms/output/OutputDetailEntity'
import { OutputTypeEnum } from '@/model/wms/output/OutputTypeEnum'
import { playScanSuccess, playScanWarning } from '@/utils/audioFeedback'
import OutputDetailEditor from './detailEditor.vue'

/**
 * # 出库单编辑器（F4 批 4 · 新底座）
 *
 * 与入库单编辑器同构：主表单（单号 + 出库类型）交给 `KFormDialog`，明细数组放 `#extra` 用
 * `KDetailTable` 渲染。
 *
 * 三条与旧页面对齐的判据：
 * 1. **明细为空不发请求**（空出库单没有业务含义）；
 * 2. **只有「普通出库」能改明细**：移动出库/销售出库的明细由移库单/销售单决定；
 * 3. **删除明细要二次确认**。
 *
 * 与入库单的差别：出库单**不显示来源单号的只读字段**（销售单号/移库单号在详情页可见）——
 * 旧编辑器同样只在 `#extra` 里放明细表，`@KForm` 元数据里没有 `sale`/`move`，这里保持一致。
 */
const props = defineProps<{
  modelValue: boolean
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const form = ref<Record<string, unknown>>({})
const detailEditorVisible = ref(false)
const editingDetail = ref<OutputDetailEntity | null>(null)

const details = computed<OutputDetailEntity[]>(() => (form.value.details ?? []) as OutputDetailEntity[])
const type = computed(() => form.value.type as number | undefined)
// `type` 可能暂时是 undefined（新增的 preset 尚未生效 / 详情未加载完），
// 不要把 undefined 喂给 `equalsKey`
const isDetailEditable = computed(() => type.value !== undefined && OutputTypeEnum.NORMAL.equalsKey(type.value))

function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
}

function openAddDetail() {
  editingDetail.value = new OutputDetailEntity()
  detailEditorVisible.value = true
}

const scanBarcode = ref('')

function onScanEnter() {
  const code = scanBarcode.value.trim()
  if (!code) {
    playScanWarning()
    return
  }
  const found = details.value.find(d => d.materialCode === code || d.material?.code === code)
  if (found) {
    found.quantity = (Number(found.quantity) || 0) + 1
    playScanSuccess()
    FeedbackUtil.toastSuccess(`出库物料 [${code}] 数量累加至 ${found.quantity}`)
    scanBarcode.value = ''
    return
  }

  const newDetail = new OutputDetailEntity()
  newDetail.material = Object.assign(new MaterialEntity(), {
    code,
    name: `物料 ${code}`,
  })
  newDetail.materialCode = code
  newDetail.materialName = `物料 ${code}`
  newDetail.quantity = 1
  details.value.push(newDetail)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已快速录入出库物料 [${code}]，数量: 1`)
  scanBarcode.value = ''
}

const batchPicking = ref(false)

function onBatchPicked(rows: unknown[]) {
  batchPicking.value = false
  const materials = rows as MaterialEntity[]
  if (!materials?.length) {
    return
  }
  for (const m of materials) {
    const detail = new OutputDetailEntity()
    detail.material = m
    detail.materialId = m.id
    detail.materialName = m.name
    detail.materialCode = m.code
    detail.quantity = 1
    details.value.push(detail)
  }
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已批量添加 ${materials.length} 条出库物料明细`)
}

function duplicateDetail(row: OutputDetailEntity) {
  const clone = row.copy() as OutputDetailEntity
  delete (clone as { id?: number }).id
  details.value.push(clone)
  playScanSuccess()
  FeedbackUtil.toastSuccess(`已复制出库明细 [${row.materialName || row.material?.name || '物料'}]`)
}

function onDetailConfirm(detail: OutputDetailEntity) {
  details.value.push(detail)
  detailEditorVisible.value = false
}

async function removeDetail(row: OutputDetailEntity) {
  const index = details.value.indexOf(row)
  if (index < 0) {
    return
  }
  await FeedbackUtil.confirmWarning('是否删除选中行的计划明细？', '删除明细')
  details.value.splice(index, 1)
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
    :preset="{ type: OutputTypeEnum.NORMAL.key }"
    :columns="2"
    :service="KOutputService"
    title="出库单"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @loaded="onLoaded"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #extra>
      <div class="output-editor__details">
        <div class="output-editor__head">
          <div class="output-editor__head-left">
            <span class="output-editor__title">出库明细</span>
            <ElTag
              effect="plain"
              round
              size="small"
              type="info"
            >
              共 {{ details.length }} 项
            </ElTag>
          </div>
          <div
            v-if="isDetailEditable"
            class="output-editor__head-actions"
          >
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
              v-permission="[Permissions.Wms.Output.add, Permissions.Wms.Output.update]"
              size="small"
              plain
              @click="batchPicking = true"
            >
              批量添加
            </ElButton>
            <ElButton
              v-permission="[Permissions.Wms.Output.add, Permissions.Wms.Output.update]"
              :icon="Plus"
              size="small"
              type="primary"
              @click="openAddDetail"
            >
              手动选择
            </ElButton>
          </div>
        </div>

        <KDetailTable
          :entity="OutputDetailEntity"
          :exclude="['createTime', 'updateTime']"
          :row-actions="isDetailEditable
            ? [
              {
                label: '复制',
                permission: [Permissions.Wms.Output.add, Permissions.Wms.Output.update],
                handler: duplicateDetail,
              },
              {
                label: '删除',
                danger: true,
                permission: [Permissions.Wms.Output.add, Permissions.Wms.Output.update],
                handler: removeDetail,
              },
            ]
            : []"
          :rows="details"
          empty-text="还没有明细，支持扫码录入、批量挑选或手动新增"
        >
          <template #cell-quantity="{ row }">
            <ElInputNumber
              v-if="isDetailEditable"
              v-model="(row as OutputDetailEntity).quantity"
              :min="1"
              size="small"
              controls-position="right"
              style="width: 100px"
            />
            <span v-else>{{ (row as OutputDetailEntity).quantity }}</span>
          </template>
        </KDetailTable>
      </div>
    </template>
  </KFormDialog>

  <OutputDetailEditor
    v-model="detailEditorVisible"
    :detail="editingDetail"
    @confirm="onDetailConfirm"
  />

  <KSelector
    v-model="batchPicking"
    :service="KMaterialService"
    multiple
    title="批量挑选出库物料"
    @confirm="onBatchPicked"
  />
</template>

<style lang="scss" scoped>
.output-editor__details {
  margin-top: 4px;
}

.output-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.output-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.output-editor__head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.scan-input {
  width: 280px;
}

.output-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

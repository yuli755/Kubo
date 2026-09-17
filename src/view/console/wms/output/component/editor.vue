<script lang="ts" setup>
import { ElButton, ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KOutputService } from '@/hook/wms/useOutput'
import { OutputDetailEntity } from '@/model/wms/output/OutputDetailEntity'
import { OutputTypeEnum } from '@/model/wms/output/OutputTypeEnum'
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
          <span class="output-editor__title">出库明细</span>
          <ElButton
            v-if="isDetailEditable"
            v-permission="[Permissions.Wms.Output.add, Permissions.Wms.Output.update]"
            link
            type="primary"
            @click="openAddDetail"
          >
            添加明细
          </ElButton>
        </div>

        <KDetailTable
          :entity="OutputDetailEntity"
          :exclude="['createTime', 'updateTime']"
          :row-actions="isDetailEditable
            ? [{
              label: '删除',
              danger: true,
              permission: [Permissions.Wms.Output.add, Permissions.Wms.Output.update],
              handler: removeDetail,
            }]
            : []"
          :rows="details"
          empty-text="还没有明细，点「添加明细」选一条物料"
        />
      </div>
    </template>
  </KFormDialog>

  <OutputDetailEditor
    v-model="detailEditorVisible"
    :detail="editingDetail"
    @confirm="onDetailConfirm"
  />
</template>

<style lang="scss" scoped>
.output-editor__details {
  margin-top: 8px;
}

.output-editor__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.output-editor__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

<script lang="ts" setup>
import { ElButton, ElFormItem, ElInput, ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KInputService } from '@/hook/wms/useInput'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'
import { InputTypeEnum } from '@/model/wms/input/InputTypeEnum'
import InputDetailEditor from './detailEditor.vue'

/**
 * # 入库单编辑器（F4 批 4 · 新底座）
 *
 * 主表单（单号 + 入库类型）由 `KFormDialog` 按 `@KForm` 元数据生成；明细数组放在 `#extra` 里，
 * 用 `KDetailTable` 渲染（还没提交给后端，所以没有服务、没有分页、没有搜索）。
 *
 * ## 三条业务判据（迁移不许丢）
 * 1. **明细为空不发请求**：空明细的入库单在后端没有意义，`beforeSubmit` 直接提示并中止；
 * 2. **只有「普通入库」能改明细**：采购入库/移动入库的明细来自采购单/移库单，
 *    人工加一条会让单据与来源对不上 —— 旧页面用 `isDetailEditable` 控制，这里照旧；
 * 3. **采购入库要能看到来源采购单号**（只读展示，来源在采购单那边维护）。
 *
 * 明细行的删除走 `KDetailTable` 的 `row-actions`（`danger` + 权限码与「添加明细」同口径）：
 * 旧页面用 `FeedbackUtil.confirmWarning('是否删除选中行的计划明细？')` 二次确认，这里保留。
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
const detailEditorVisible = ref(false)
const editingDetail = ref<InputDetailEntity | null>(null)

const details = computed<InputDetailEntity[]>(() => (form.value.details ?? []) as InputDetailEntity[])
const type = computed(() => form.value.type as number | undefined)

// `type` 在新增那一刻来自 `preset`（普通入库），编辑时来自详情 —— 都可能暂时是 undefined，
// 所以判断统一走 `statusIs()`，不要把 undefined 喂给 `equalsKey`（那会直接抛类型错误）
function typeIs(target: { equalsKey: (key: number) => boolean }): boolean {
  return type.value !== undefined && target.equalsKey(type.value)
}

/** 只有普通入库允许增删明细（采购/移动入库的明细由来源单据决定） */
const isDetailEditable = computed(() => typeIs(InputTypeEnum.NORMAL))
const isPurchaseInput = computed(() => typeIs(InputTypeEnum.PURCHASE))
const purchaseBillCode = computed(() => (form.value.purchase as { billCode?: string } | undefined)?.billCode ?? '')

function onLoaded(next: Record<string, unknown>) {
  next.details ??= []
  form.value = next
}

function openAddDetail() {
  const detail = new InputDetailEntity()
  // 采购入库时明细继承单据上的仓库（手动指定的话后端也会再校验一次）
  const storage = form.value.storage as InputDetailEntity['storage'] | undefined
  if (storage?.id) {
    detail.storage = storage
    detail.storageId = storage.id
  }
  editingDetail.value = detail
  detailEditorVisible.value = true
}

function onDetailConfirm(detail: InputDetailEntity) {
  details.value.push(detail)
  detailEditorVisible.value = false
}

async function removeDetail(row: InputDetailEntity) {
  const index = details.value.indexOf(row)
  if (index < 0) {
    return
  }
  // 与旧页面同口径：删明细前再确认一次（明细是单据的组成部分，误删要重建）
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
    :preset="{ type: InputTypeEnum.NORMAL.key }"
    :columns="2"
    :service="KInputService"
    title="入库单"
    width="900px"
    @closed="emit('update:modelValue', false)"
    @loaded="onLoaded"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #extra>
      <ElFormItem
        v-if="isPurchaseInput"
        label="采购单号"
      >
        <ElInput
          :model-value="purchaseBillCode"
          disabled
        />
      </ElFormItem>

      <div class="input-editor__details">
        <div class="input-editor__head">
          <span class="input-editor__title">入库明细</span>
          <ElButton
            v-if="isDetailEditable"
            v-permission="[Permissions.Wms.Input.add, Permissions.Wms.Input.update]"
            link
            type="primary"
            @click="openAddDetail"
          >
            添加明细
          </ElButton>
        </div>

        <KDetailTable
          :entity="InputDetailEntity"
          :exclude="['createTime', 'updateTime']"
          :row-actions="isDetailEditable
            ? [{
              label: '删除',
              danger: true,
              permission: [Permissions.Wms.Input.add, Permissions.Wms.Input.update],
              handler: removeDetail,
            }]
            : []"
          :rows="details"
          empty-text="还没有明细，点「添加明细」选一条物料"
        />
      </div>
    </template>
  </KFormDialog>

  <InputDetailEditor
    v-model="detailEditorVisible"
    :detail="editingDetail"
    @confirm="onDetailConfirm"
  />
</template>

<style lang="scss" scoped>
.input-editor__details {
  margin-top: 8px;
}

.input-editor__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.input-editor__title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>

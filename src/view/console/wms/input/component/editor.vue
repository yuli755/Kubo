<script lang="ts" setup>
import { Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElFormItem, ElIcon, ElInput, ElMessage, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { KInputService } from '@/hook/wms/useInput'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
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

const scanBarcode = ref('')

function onScanEnter() {
  const code = scanBarcode.value.trim()
  if (!code) {
    return
  }
  // 如果已存在该物料编码的明细，则直接数量 +1
  const found = details.value.find(d => d.materialCode === code || d.material?.code === code)
  if (found) {
    found.quantity = (Number(found.quantity) || 0) + 1
    FeedbackUtil.toastSuccess(`物料 [${code}] 数量累加至 ${found.quantity}`)
    scanBarcode.value = ''
    return
  }

  // 否则新增明细行
  const newDetail = new InputDetailEntity()
  newDetail.material = Object.assign(new MaterialEntity(), {
    code,
    name: `物料 ${code}`,
  })
  newDetail.materialCode = code
  newDetail.materialName = `物料 ${code}`
  newDetail.quantity = 1
  const storage = form.value.storage as InputDetailEntity['storage'] | undefined
  if (storage?.id) {
    newDetail.storage = storage
    newDetail.storageId = storage.id
  }
  details.value.push(newDetail)
  FeedbackUtil.toastSuccess(`已快速录入物料 [${code}]，数量: 1`)
  scanBarcode.value = ''
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
          <div class="input-editor__head-left">
            <span class="input-editor__title">入库明细</span>
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
            class="input-editor__head-actions"
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
              v-permission="[Permissions.Wms.Input.add, Permissions.Wms.Input.update]"
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
  margin-top: 4px;
}

.input-editor__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.input-editor__head-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.input-editor__head-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.scan-input {
  width: 280px;
}

.input-editor__title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

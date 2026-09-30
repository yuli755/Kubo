<script lang="ts" setup>
import type { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'
import type { InputEntity } from '@/model/wms/input/InputEntity'
import { Link } from '@element-plus/icons-vue'
import { ElAlert, ElDescriptions, ElDescriptionsItem, ElIcon, ElLink, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { Permissions } from '@/config/permissions'
import { getCellText, getEnumLabel, getFieldLabel, getTableConfig } from '@/framework'
import { useDetail } from '@/hook/kubo/useDetail'
import { KInputService } from '@/hook/wms/useInput'
import { InputDetailEntity as InputDetail } from '@/model/wms/input/InputDetailEntity'
import { InputEntity as Input } from '@/model/wms/input/InputEntity'
import { InputStatusEnum } from '@/model/wms/input/InputStatusEnum'
import { InputTypeEnum } from '@/model/wms/input/InputTypeEnum'
import { openPurchaseDetail } from '@/view/console/channel/purchase/component/openDetail'
import MoveDetail from '@/view/console/wms/move/component/detail.vue'
import InputAddFinishEditor from './addFinishEditor.vue'

/**
 * # 入库单详情（F4 批 4 · 新底座）
 *
 * 只读主表信息 + 明细表 + 行内「作业」（确认入库）。
 *
 * ## 与旧版的差别
 * - 数据用本仓 `useDetail`（按 id 重新查，避免列表快照过期）而不是 airpower 的 `useBillDetail`；
 * - 主表信息按 `@KTable` 元数据渲染（`ElDescriptions`），字典/时间的显示口径与列表一致，
 *   不再需要 `BillFormCode` / `BillFormMoreDetail` 那两个只服务于 airpower 页面的组件；
 * - 「采购单号」「移库单号」两个来源单据**点得开**：采购单详情属 channel 域（批 5 才迁），
 *   所以通过 `openPurchaseDetail()` 这个接缝打开（那几个 airpower import 留在 channel 域内）；
 *   移库单已在本批迁完，直接声明式打开 `MoveDetail`。
 *
 * **作业按钮只在「入库中」可点**（`InputStatusEnum.INPUTTING`）：状态机后端也会再校验一次，
 * 迁移不许把它简化成「永远可点」。
 */
const props = defineProps<{
  modelValue: boolean
  /** 单据 id */
  id: number | null | undefined
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const detail = useDetail(KInputService, {
  queryKey: ['wms:input'],
  id: computed(() => props.id),
})

const bill = computed<InputEntity | undefined>(() => detail.entity.value)

const headerFields = computed(() => ['billCode', 'type', 'status', 'createTime', 'updateTime'])

/** 单据类型判断（`type` 未加载完时是 undefined，不能喂给 `equalsKey`） */
function typeIs(target: { equalsKey: (key: number) => boolean }): boolean {
  const type = bill.value?.type
  return type !== undefined && target.equalsKey(type)
}

const isPurchaseInput = computed(() => typeIs(InputTypeEnum.PURCHASE))
const isMoveInput = computed(() => typeIs(InputTypeEnum.MOVE))

const statusTagType = computed<'danger' | 'warning' | 'primary' | 'success' | 'info'>(() => {
  const status = bill.value?.status
  if (status === undefined) {
    return 'info'
  }
  if (InputStatusEnum.REJECTED.equalsKey(status)) {
    return 'danger'
  }
  if (InputStatusEnum.AUDITING.equalsKey(status)) {
    return 'warning'
  }
  if (InputStatusEnum.INPUTTING.equalsKey(status)) {
    return 'primary'
  }
  if (InputStatusEnum.DONE.equalsKey(status)) {
    return 'success'
  }
  return 'info'
})

const isRejected = computed(() => {
  const status = bill.value?.status
  return status !== undefined && InputStatusEnum.REJECTED.equalsKey(status)
})

const moveVisible = ref(false)
const finishing = ref<InputDetailEntity | null>(null)

function fieldLabel(key: string): string {
  // 头部字段未必是列表列（status / createTime 等没有 @KTable），必须查 @KField 的 getFieldLabel；
  // 查 getTableConfig 会拿不到配置而退回原始英文键名（N-11）。
  return getFieldLabel(Input, key)
}

function fieldText(key: string): string {
  const entity = bill.value
  if (!entity) {
    return '-'
  }
  if (key === 'status') {
    return entity.status === undefined ? '-' : (getEnumLabel(InputStatusEnum, entity.status) ?? '-')
  }
  return getCellText(entity, { ...(getTableConfig(Input, key) ?? {}), key } as never)
}

/** 「作业」按钮是否可用：只有「入库中」的单据才能登记入库数量 */
function canFinish(): boolean {
  const status = bill.value?.status
  return status !== undefined && InputStatusEnum.INPUTTING.equalsKey(status)
}

function openFinish(row: InputDetailEntity) {
  finishing.value = row
}

async function onFinishConfirmed() {
  finishing.value = null
  await detail.reload()
  emit('saved')
}

function openMoveDetail() {
  if (bill.value?.move?.id) {
    moveVisible.value = true
  }
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    confirm-text="关闭"
    hide-cancel
    min-height="70%"
    title="入库单详情"
    width="80%"
    @confirm="emit('update:modelValue', false)"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <ElAlert
      v-if="isRejected || bill?.rejectReason"
      :closable="false"
      class="input-detail__alert"
      show-icon
      :title="`单据已被驳回${bill?.rejectReason ? `：${bill.rejectReason}` : ''}`"
      type="error"
    />

    <ElDescriptions
      :column="3"
      border
      size="small"
    >
      <ElDescriptionsItem
        v-for="field in headerFields"
        :key="field"
        :label="fieldLabel(field)"
      >
        <ElTag
          v-if="field === 'status'"
          :type="statusTagType"
          effect="light"
          size="small"
        >
          {{ fieldText('status') }}
        </ElTag>
        <span v-else>{{ fieldText(field) }}</span>
      </ElDescriptionsItem>

      <ElDescriptionsItem
        v-if="isPurchaseInput"
        label="采购单号"
      >
        <ElLink
          v-if="bill?.purchase?.id"
          type="primary"
          @click="bill && openPurchaseDetail(bill.purchase)"
        >
          <ElIcon class="input-detail__link-icon">
            <Link />
          </ElIcon>
          {{ bill.purchase.billCode }}
        </ElLink>
        <span v-else>-</span>
      </ElDescriptionsItem>

      <ElDescriptionsItem
        v-if="isMoveInput"
        label="移库单号"
      >
        <ElLink
          v-if="bill?.move?.id"
          type="primary"
          @click="openMoveDetail"
        >
          <ElIcon class="input-detail__link-icon">
            <Link />
          </ElIcon>
          {{ bill.move.billCode }}
        </ElLink>
        <span v-else>-</span>
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="input-detail__table">
      <div class="input-detail__table-header">
        <div class="input-detail__table-title">
          <span>入库物资明细清单</span>
          <ElTag
            effect="plain"
            round
            size="small"
            type="info"
          >
            共 {{ bill?.details?.length ?? 0 }} 项
          </ElTag>
        </div>
        <div
          v-if="canFinish()"
          class="input-detail__table-tip"
        >
          <ElTag
            effect="plain"
            size="small"
            type="primary"
          >
            入库进行中 · 可点击操作列「作业」登记实际入库量
          </ElTag>
        </div>
      </div>

      <KDetailTable
        :entity="InputDetail"
        :exclude="['createTime', 'updateTime']"
        :row-actions="[{
          label: '作业',
          permission: Permissions.Wms.Input.addFinish,
          disabled: () => !canFinish(),
          handler: (row: InputDetailEntity) => openFinish(row),
        }]"
        :rows="(bill?.details ?? []) as InputDetailEntity[]"
        empty-text="这张入库单还没有明细"
      />
    </div>
  </KDialog>

  <InputAddFinishEditor
    v-if="finishing"
    :detail="finishing"
    :model-value="!!finishing"
    @confirmed="onFinishConfirmed"
    @update:model-value="value => !value && (finishing = null)"
  />

  <MoveDetail
    :id="bill?.move?.id"
    v-model="moveVisible"
  />
</template>

<style lang="scss" scoped>
.input-detail__alert {
  margin-bottom: 12px;
}

.input-detail__link-icon {
  margin-right: 4px;
}

.input-detail__table {
  margin-top: 16px;
}

.input-detail__table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.input-detail__table-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>

<script lang="ts" setup>
import type { MoveDetailEntity } from '@/model/wms/move/MoveDetailEntity'
import type { MoveEntity } from '@/model/wms/move/MoveEntity'
import { ElDescriptions, ElDescriptionsItem, ElInput, ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import BillAddFinishDialog from '@/component/bill/BillAddFinishDialog.vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { Permissions } from '@/config/permissions'
import { getCellText, getEnumLabel, getTableConfig } from '@/framework'
import { useDetail } from '@/hook/kubo/useDetail'
import { KMoveService } from '@/hook/wms/useMove'
import { MoveDetailEntity as MoveDetail } from '@/model/wms/move/MoveDetailEntity'
import { MoveEntity as Move } from '@/model/wms/move/MoveEntity'
import { MoveStatusEnum } from '@/model/wms/move/MoveStatusEnum'

/**
 * # 移库单明细（F4 批 4 · 新底座）
 *
 * 单据详情页 = 只读主表信息 + 明细表 + 行内「作业」（把这次实际移动的数量累加进明细）。
 *
 * 与旧版的差别：
 * - 数据用本仓 `useDetail`（按 id 重新查，避免用列表里的过期快照）而不是 airpower 的 `useDetail`；
 * - 主表信息用 `ElDescriptions` 按 `@KTable` 元数据渲染（金额/时间/字典的显示口径与列表一致），
 *   不再依赖 `AFormField` + `AGroup`（那两个读 airpower 注册表）；
 * - 「更多信息」（创建/更新/状态/驳回原因）由元数据里的 `createTime/updateTime/status/rejectReason`
 *   四个字段自然带出，不再需要单独的 `BillFormMoreDetail` 组件。
 *
 * **作业按钮只在「移动中」可点**：这是单据状态机的判据，写在页面上（`disabled`），
 * 后端也会再校验一次 —— 迁移不许把它简化成"永远可点"。
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

const detail = useDetail(KMoveService, {
  queryKey: ['wms:move'],
  id: computed(() => props.id),
})

const bill = computed<MoveEntity | undefined>(() => detail.entity.value)

/** 主表只读区显示的字段（顺序即展示顺序） */
const headerFields = computed(() => ['billCode', 'storageName', 'status', 'createTime', 'updateTime'])

function fieldLabel(key: string): string {
  return getTableConfig(Move, key)?.label ?? key
}

function fieldText(key: string): string {
  const entity = bill.value
  if (!entity) {
    return '-'
  }
  if (key === 'status') {
    return entity.status === undefined ? '-' : (getEnumLabel(MoveStatusEnum, entity.status) ?? '-')
  }
  return getCellText(entity, { ...(getTableConfig(Move, key) ?? {}), key } as never)
}

/** 本次作业的明细（打开数量弹窗后才有值） */
const finishing = ref<MoveDetailEntity | null>(null)

/** 默认值 = 待完成数量（总量 − 已完成），不是累计值：后端做的是累加 */
const finishDefault = computed(() => {
  const row = finishing.value
  if (!row) {
    return 0
  }
  return Math.max(0, Number(row.quantity ?? 0) - Number(row.finishQuantity ?? 0))
})

function openFinish(row: MoveDetailEntity) {
  finishing.value = row
}

/** 「作业」按钮是否可用：只有「移动中」的单据才能登记实际移动数量 */
function canFinish(): boolean {
  const status = bill.value?.status
  return status !== undefined && MoveStatusEnum.MOVING.equalsKey(status)
}

async function onFinishConfirm(quantity: number) {
  const row = finishing.value
  finishing.value = null
  if (!row?.id) {
    return
  }
  // 载荷只给 id 与本次数量（来源库存由后端按明细 id 从库里读，见 AbstractBillService 的说明）
  await new KMoveService().addDetailFinishQuantity({ id: row.id, quantity } as never)
  ElMessage.success('已添加完成数量')
  await detail.reload()
  emit('saved')
}
</script>

<template>
  <KDialog
    :model-value="modelValue"
    confirm-text="关闭"
    hide-cancel
    min-height="70%"
    title="移库单明细"
    width="80%"
    @confirm="emit('update:modelValue', false)"
    @update:model-value="value => emit('update:modelValue', value)"
  >
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
        {{ fieldText(field) }}
      </ElDescriptionsItem>
      <ElDescriptionsItem
        v-if="bill?.rejectReason"
        label="驳回原因"
        :span="3"
      >
        <ElInput
          :model-value="bill.rejectReason"
          disabled
          type="textarea"
        />
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="move-detail__table">
      <KDetailTable
        :entity="MoveDetail"
        :exclude="['createTime', 'updateTime']"
        :row-actions="[
          {
            label: '作业',
            permission: Permissions.Wms.Move.addFinish,
            disabled: () => !canFinish(),
            handler: (row: MoveDetailEntity) => openFinish(row),
          },
        ]"
        :rows="(bill?.details ?? []) as MoveDetailEntity[]"
        empty-text="这张移库单还没有明细"
      />
    </div>
  </KDialog>

  <BillAddFinishDialog
    v-if="finishing"
    :on-cancel="() => finishing = null"
    :on-confirm="(data) => onFinishConfirm(Number(data ?? 0))"
    :param="finishDefault"
  />
</template>

<style lang="scss" scoped>
.move-detail__table {
  margin-top: 12px;
}
</style>

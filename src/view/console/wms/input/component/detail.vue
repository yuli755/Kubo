<script lang="ts" setup>
import type { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'
import type { InputEntity } from '@/model/wms/input/InputEntity'
import { ElDescriptions, ElDescriptionsItem, ElLink } from 'element-plus'
import { computed, ref } from 'vue'
import KDetailTable from '@/component/framework/KDetailTable.vue'
import KDialog from '@/component/framework/KDialog.vue'
import { Permissions } from '@/config/permissions'
import { getCellText, getEnumLabel, getTableConfig } from '@/framework'
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

const moveVisible = ref(false)
const finishing = ref<InputDetailEntity | null>(null)

function fieldLabel(key: string): string {
  return getTableConfig(Input, key)?.label ?? key
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
        v-if="isPurchaseInput"
        label="采购单号"
      >
        <ElLink
          v-if="bill?.purchase?.id"
          type="primary"
          @click="bill && openPurchaseDetail(bill.purchase)"
        >
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
          {{ bill.move.billCode }}
        </ElLink>
        <span v-else>-</span>
      </ElDescriptionsItem>
    </ElDescriptions>

    <div class="input-detail__table">
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
.input-detail__table {
  margin-top: 12px;
}
</style>

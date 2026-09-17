<script lang="ts" setup>
import type { ExportGapItem } from '@/api/system/types'
import type { DataFieldPolicyEntity } from '@/model/system/datapermission/DataFieldPolicyEntity'
import { ElAlert, ElButton } from 'element-plus'
import { computed, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import { gapsForField, useExportGapReport } from '@/composables/useExportGapReport'
import { DataFieldPolicyService } from '@/hook/system/useDataFieldPolicy'
import { DataFieldPolicyEntity as DataFieldPolicy } from '@/model/system/datapermission/DataFieldPolicyEntity'
import { DataPermissionActionType } from '@/model/system/datapermission/DataPermissionActionType'
import { DataPermissionMaskType } from '@/model/system/datapermission/DataPermissionMaskType'

/**
 * # 列级脱敏策略编辑器（F4 批 6 · 新底座）
 *
 * 字段由 `@KForm` 元数据渲染，页面额外做三件事 —— 它们都是**这个页面才有的判断**：
 *
 * 1. **导出缺口提示 + 一键预填**：同字段（`resourceId` + `fieldName`）还有多少处导出缺口
 *    （判定在后端）。放在编辑器里而不是只留在资源列表页：补漏的动作就发生在这里，
 *    此刻是"该不该顺手补一条 EXPORT 明文"唯一的决策窗口。
 * 2. **"动作不会被列级读到"的提示**：`actionName` 是字符串，选 `DETAIL` 后端不会报错，
 *    保存成功、列表可见、只有"界面毫无变化"这一个证据 —— 配了却不生效比报错更难发现。
 * 3. **编辑态不许预填**：把已有策略改成导出授权会把这条遮蔽删掉。新增态才给按钮。
 *
 * ## 与旧实现的差别（行为保真，写法换底座）
 * 表单对象来自 `@loaded`（同一个引用），不再是 `useEditor` 返回的 `formData` ref；
 * 预填的"快照 / 撤销"逻辑逐字保留（`PREFILL_KEYS` 与后端 NOT NULL 列一一对应，
 * 多写一个键都会让保存变成 500）。
 */
const props = defineProps<{
  /** 显隐 */
  modelValue: boolean
  /** 编辑的记录 id（不传为新增） */
  id?: number | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

/** 表单对象（`@loaded` 回传，与弹窗内部同一个引用） */
const form = ref<DataFieldPolicyEntity>(new DataFieldPolicy())

const { items: gapItems, failed: gapsUnavailable } = useExportGapReport()

const fieldGaps = computed(() => gapsForField(gapItems, form.value.resourceId, form.value.fieldName))

/** 新增态判定：用主键是否存在（比文案判断稳） */
const isCreating = computed(() => !form.value.id)

/** 当前动作在列级遮蔽里是否无人消费（列级只认 QUERY / EXPORT） */
const inertAction = computed(() => {
  const action = form.value.actionName
  return !!action && !DataPermissionActionType.isColumnMasked(action)
})

/** 预填只碰这几个键：与后端 `data_field_policy` 的 NOT NULL 列一一对应 */
const PREFILL_KEYS = ['actionName', 'roleCode', 'maskType', 'allowPlaintext', 'priority', 'enabled'] as const

const snapshot = ref<Partial<DataFieldPolicyEntity> | null>(null)
const prefilledFrom = ref('')

/**
 * 把本表单预填成"补齐这条缺口所需的 EXPORT 明文授权"。
 *
 * - 只写非空列（漏一个 → 保存时 500，界面上只有一句服务器错误）；`roleCode` 取缺口条目里的值（含 `default`）；
 * - `maskType` 只是占位：`allowPlaintext=true` 时后端不走遮蔽分支，取 `FULL` 是为了让这一行一眼看出"它不是遮蔽策略"；
 * - 不猜语义、不查库：只把后端已经算好的 (字段, 角色) 搬进表单，"补完是否就没缺口"仍由下一次自查回答。
 */
function prefill(gap: ExportGapItem) {
  const entity = form.value
  snapshot.value = PREFILL_KEYS.reduce<Record<string, unknown>>((acc, key) => {
    acc[key] = (entity as unknown as Record<string, unknown>)[key]
    return acc
  }, {}) as Partial<DataFieldPolicyEntity>
  prefilledFrom.value = `${gap.fieldName} / ${gap.roleCode}`
  entity.actionName = DataPermissionActionType.EXPORT.key
  entity.roleCode = gap.roleCode
  entity.maskType = DataPermissionMaskType.FULL.key
  entity.allowPlaintext = true
  entity.priority = 0
  entity.enabled = true
}

function undoPrefill() {
  const backup = snapshot.value
  if (!backup) {
    return
  }
  Object.assign(form.value, backup)
  snapshot.value = null
  prefilledFrom.value = ''
}
</script>

<template>
  <KFormDialog
    :id="props.id"
    :model-value="props.modelValue"
    :service="DataFieldPolicyService"
    width="860px"
    @closed="emit('update:modelValue', false)"
    @loaded="form = $event as DataFieldPolicyEntity"
    @saved="emit('saved')"
    @update:model-value="value => emit('update:modelValue', value)"
  >
    <template #extra>
      <ElAlert
        v-if="fieldGaps.length"
        class="gap-hint"
        type="warning"
        :closable="false"
        show-icon
        :title="`这个字段还有 ${fieldGaps.length} 处导出缺口`"
      >
        <div
          v-for="gap in fieldGaps"
          :key="`${gap.resourceKey}-${gap.roleCode}`"
          class="gap-row"
        >
          <span class="gap-row-text">在 {{ gap.resourceKey }}：{{ gap.reason }}</span>
          <ElButton
            v-if="isCreating"
            size="small"
            type="primary"
            link
            @click="prefill(gap)"
          >
            按此预填
          </ElButton>
          <span
            v-else
            class="gap-row-note"
          >（编辑已有策略时不给预填：那会把这一行改成导出授权，遮蔽就被删了）</span>
        </div>
        <div
          v-if="prefilledFrom"
          class="gap-row"
        >
          <span class="gap-row-text">已按「{{ prefilledFrom }}」预填本表单，核对后即可保存</span>
          <ElButton
            size="small"
            link
            @click="undoPrefill"
          >
            撤销
          </ElButton>
        </div>
      </ElAlert>
      <ElAlert
        v-else-if="gapsUnavailable"
        class="gap-hint"
        type="info"
        :closable="false"
        show-icon
        title="未能取得导出缺口自查结果"
        description="不影响保存这条策略；但此刻看不到缺口，别把它读成「没有缺口」。"
      />
      <ElAlert
        v-if="inertAction"
        class="gap-hint"
        type="warning"
        :closable="false"
        show-icon
        title="这个动作不会被列级遮蔽读到"
        description="列级只消费 QUERY（界面与详情）和 EXPORT（导出）。DETAIL / CREATE / UPDATE / DELETE 只在行级策略里按动作精确匹配：这一行保存后不会改变任何输出，要配条件请到「数据权限-行级策略」。"
      />
      <ElAlert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        title="这条策略会怎么生效"
      >
        只有被 @SensitiveField 标记的字段会被它影响；字段名必须与实体的 JSON 属性名一致，写错等于没配。
        actionName=QUERY 决定界面与详情是否遮蔽；actionName=EXPORT 决定导出能否带该列明文 ——
        受限角色导出时，被 QUERY 遮蔽的每个字段都要有一条 EXPORT 且「允许明文＝是」，缺一条整个导出被拒
        （导出是一整份文件，不部分放行）。roleCode 留空或填 default 表示对所有角色兜底，精确角色优先。
        后端 kubo.data-permission.mode=shadow 时只记 export-bypass 账、不拦；enforce 才拦。
      </ElAlert>
    </template>
  </KFormDialog>
</template>

<style scoped>
.gap-hint {
  margin-top: 8px;
}

.gap-row {
  display: flex;
  gap: 8px;
  align-items: center;
  line-height: 1.8;
}

.gap-row-text {
  flex: 1;
}

.gap-row-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.tip {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
}
</style>

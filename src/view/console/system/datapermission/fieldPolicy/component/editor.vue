<script lang="ts" setup>
import type { ExportGapItem } from '@/api/system/types'
import { ADialog, AFormField, DialogProps, useEditor } from '@airpower/web'
import { ElAlert, ElButton } from 'element-plus'
import { computed, ref } from 'vue'
import { gapsForField, useExportGapReport } from '@/composables/useExportGapReport'
import { DataFieldPolicyService } from '@/hook/system/useDataFieldPolicy'
import { DataFieldPolicyEntity } from '@/model/system/datapermission/DataFieldPolicyEntity'

import { DataPermissionActionType } from '@/model/system/datapermission/DataPermissionActionType'
import { DataPermissionMaskType } from '@/model/system/datapermission/DataPermissionMaskType'

const props = defineProps(DialogProps.withParam(new DataFieldPolicyEntity()))

const {
  formRef,
  formData,
  isLoading,
  onSubmit,
  rules,
  title,
} = useEditor(props, DataFieldPolicyService, {})

/**
 * 与当前这条策略同字段的导出缺口（按 resourceId + fieldName 筛，判定仍在后端）
 *
 * 为什么放在编辑器里而不是只留在资源列表页：补漏的动作发生在这里 —— 管理员正在写这条策略，
 * 此刻是"该不该顺手补一条 EXPORT 明文"唯一的决策窗口。列表页的汇总只是入口，
 * 真到编辑时还得切页面去回忆是哪个字段，等于没有。
 *
 * 这里必须写 `formData.value.*`：`useEditor` 返回的是 `Ref<Entity>`（模板里自动解包，
 * script 里不解包 —— 这一点不是推测，是 vue-tsc 在本轮 CI 上撞出来的：
 * "Property 'resourceId' does not exist on type 'Ref<DataFieldPolicyEntity, …>'"）。
 * 既然 `.value` 本身就是响应式依赖，computed 不会读到过期值，无需退回每次渲染重算。
 */
const { items: gapItems, failed: gapsUnavailable } = useExportGapReport()

const fieldGaps = computed(() => gapsForField(gapItems, formData.value.resourceId, formData.value.fieldName))

/**
 * 新增态判定：`useEditor` 不暴露 isAdd（已从 @airpower/web 的 IEditorResult/IDetailResult 核对，
 *  它只给 title/formData/isLoading/service/getDetail），而比对"新增"标题文案会随语言环境失效，
 *  所以用主键是否存在 —— 列表页"新增"传的是 `new DataFieldPolicyEntity()`，id 为空。
 */
const isCreating = computed(() => !formData.value.id)

/**
 * 当前选的动作在列级遮蔽里是否无人消费
 *
 * 为什么值得在表单里挡一句：`action_name` 是**字符串存的**，后端不会因为选了 `DETAIL` 而报错，
 * 于是这条策略保存成功、列表里看得见、编辑时一切正常 —— 只有"界面没有任何变化"这一个证据。
 * "配了却没人读"比报错更难发现，因为它不产生任何失败信号。
 * （详情页与列表页共用同一份遮蔽：脱敏发生在 Jackson 序列化层、按实体生效，与端点无关，
 * 所以"只配 QUERY 会不会漏掉详情"这件事不成立 —— 取证见施工文档 §19.21。）
 */
const inertAction = computed(() => {
  const action = formData.value.actionName
  return !!action && !DataPermissionActionType.isColumnMasked(action)
})

/** 预填要动的键：与后端 `data_field_policy` 的 NOT NULL 列一一对应，多一个都不碰 */
const PREFILL_KEYS = ['actionName', 'roleCode', 'maskType', 'allowPlaintext', 'priority', 'enabled'] as const

const snapshot = ref<Partial<DataFieldPolicyEntity> | null>(null)
const prefilledFrom = ref('')

/**
 * 把本表单预填成"补齐这条缺口所需的 EXPORT 明文授权"
 *
 * 三条刻意的边界：
 * 1. **只写非空列**：后端 `resource_id/field_name/role_code/action_name/mask_type/allow_plaintext/
 *    priority/enabled` 都是 NOT NULL，漏一个就变成保存时 500（界面上只有一句服务器错误）。
 *    `roleCode` 直接取缺口条目里的值（含 `default`）—— 这也顺手堵掉"角色留空写库报错"的老坑。
 * 2. **maskType 只是占位**：`allowPlaintext=true` 时后端不会走遮蔽分支，取 `FULL` 是为了让这一行
 *    在读侧一眼看出"它不是遮蔽策略"。
 * 3. **不猜语义、不查库**：预填只把后端已经算好的 (字段, 角色) 搬进表单；
 *    "补完是不是就没缺口了"仍由下一次自查结果回答，这里不写"已修复"那种话。
 */
function prefill(gap: ExportGapItem) {
  const form = formData.value
  snapshot.value = PREFILL_KEYS.reduce<Record<string, unknown>>((acc, key) => {
    acc[key] = (form as unknown as Record<string, unknown>)[key]
    return acc
  }, {}) as Partial<DataFieldPolicyEntity>
  prefilledFrom.value = `${gap.fieldName} / ${gap.roleCode}`
  form.actionName = DataPermissionActionType.EXPORT.key
  form.roleCode = gap.roleCode
  form.maskType = DataPermissionMaskType.FULL.key
  form.allowPlaintext = true
  form.priority = 0
  form.enabled = true
}

function undoPrefill() {
  const backup = snapshot.value
  if (!backup) {
    return
  }
  Object.assign(formData.value, backup)
  snapshot.value = null
  prefilledFrom.value = ''
}
</script>

<template>
  <ADialog
    :form-ref="formRef"
    :loading="isLoading"
    :title="title"
    @cancel="onCancel"
    @confirm="onSubmit"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      label-width="150px"
      @submit.prevent
    >
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
      <div class="tip">
        只有被 @SensitiveField 标记的字段会被这条策略影响；字段名必须与实体的 JSON 属性名一致，写错等于没配。
        actionName=QUERY 决定界面与详情是否遮蔽；actionName=EXPORT 决定导出能否带该列明文 ——
        受限角色导出时，被 QUERY 遮蔽的每一个字段都要有一条 EXPORT 且「允许明文＝是」，缺一条整个导出被拒
        （导出是一整份文件，不部分放行）。roleCode 留空或填 default 表示对所有角色兜底，精确角色优先。
        后端 kubo.data-permission.mode=shadow 时只记 export-bypass 账、不拦；enforce 才拦。
        动作下拉里 QUERY 与 EXPORT 是两条独立策略：只配 QUERY 会得到「界面遮蔽 + 导出被拒」，这是预期行为，不是漏配。
      </div>
      <AFormField field="resourceId" />
      <AFormField field="fieldName" />
      <AFormField field="roleCode" />
      <AFormField field="actionName" />
      <AFormField field="maskType" />
      <AFormField field="maskExpression" />
      <AFormField field="allowPlaintext" />
      <AFormField field="priority" />
      <AFormField field="enabled" />
    </el-form>
  </ADialog>
</template>

<style lang="scss" scoped>
.gap-hint {
  margin-bottom: 8px;

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
}

.tip {
  padding: 0 0 8px 150px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
</style>

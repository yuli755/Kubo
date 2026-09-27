<script lang="ts" setup>
import type { WebValidateRuleSet } from '@/framework'
import type { CodeRuleEntity } from '@/model/system/coderule/CodeRuleEntity'
import type { CodeRuleField } from '@/model/system/coderule/CodeRuleField'
import type { CodeRuleParam } from '@/model/system/coderule/CodeRuleParam'
import { ElFormItem, ElOption, ElSelect, ElTag } from 'element-plus'
import { computed, onMounted, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { DateTimeUtil, WebValidator } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { CodeRuleService } from '@/hook/system/useCodeRule'

/**
 * # 编码规则（F4 批 6 · 新底座）
 *
 * 列表只读（`hide-add` + `hide-delete`，只有编辑权限）：编码规则由后端初始化，
 * 运维调整的是模板与流水号。
 *
 * ## 编辑器为什么留在页面里（而不是 `component/editor.vue`）
 * 旧编辑器有三块**页面级**内容：字段下拉（数据来自 `getFieldList`）、可选参数标签
 * （点一下把占位符追加到模板）、示例编码预览。新版全部由 `KFormDialog` 的
 * `#field-*` / `#extra` 插槽承担 —— 弹窗里没有"业务逻辑"，所以不需要再包一个组件文件。
 *
 * 两处**派生展示**也留在页面：`ruleField` 列读的是字段字典（实体上只有 key）、
 * 「下一个编码」是按模板算出来的预览（日期占位符替换 + 流水号补零），
 * 它们不属于实体字段，塞进实体就是把展示逻辑写进模型。
 */
const table = useTable(CodeRuleService, { queryKey: ['system:coderule'] })

const fieldList = ref<CodeRuleField[]>([])
const paramList = ref<CodeRuleParam[]>([])

onMounted(async () => {
  const service = new CodeRuleService()
  const [fields, params] = await Promise.all([service.getFieldList(), service.getParamList()])
  fieldList.value = fields
  paramList.value = params
})

function getFieldName(codeRule: CodeRuleEntity) {
  return fieldList.value.find(item => item.key === codeRule.ruleField)?.label || ''
}

/** 把模板里的日期占位符（yyyy/mm/dd/hh/yy）换成当前时间，得到示例编码 */
function previewTemplate(template: string) {
  let code = template ?? ''
  for (const item of paramList.value) {
    if (['yyyy', 'mm', 'dd', 'hh'].includes(item.label)) {
      code = code.replaceAll(item.label, DateTimeUtil.formatFromDate(new Date(), item.label.toUpperCase()))
    }
    if (['yy'].includes(item.label)) {
      code = code.replaceAll(item.label, DateTimeUtil.formatFromDate(new Date(), 'YYYY').substring(2))
    }
  }
  return code
}

function nextCode(codeRule: CodeRuleEntity) {
  return `${codeRule.prefix ?? ''}${previewTemplate(codeRule.template)}${(codeRule.currentSn + 1).toString().padStart(codeRule.snLength, '0')}`
}

// ── 编辑器 ──
const editorVisible = ref(false)
const editingId = ref<number | null>(null)

function openEdit(row: CodeRuleEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}

/** 表单对象由 `@loaded` 交出（`#extra` 里的预览要跟着输入实时变） */
const form = ref<Record<string, unknown>>({})

/** 自定义校验：前缀与模板只允许字母/数字（旧编辑器的 `WebValidator.ifNotOnlyNumberAndLetter`） */
const extraRules = computed<WebValidateRuleSet>(() => ({
  prefix: [WebValidator.show('前缀只允许字母/数字').ifNotOnlyNumberAndLetter()],
  template: [WebValidator.show('前缀只允许字母/数字').ifNotOnlyNumberAndLetter()],
}))

function fieldChanged(fieldId: number) {
  const matched = fieldList.value.find(item => item.key === fieldId)
  if (matched?.defaultPrefix) {
    form.value.prefix = matched.defaultPrefix
  }
}

function appendParam(param: CodeRuleParam) {
  form.value.template = `${form.value.template ?? ''}${param.label}`
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="编码规则"
      description="单据编号/物料编码等按此规则生成；改模板前先确认存量编码不会撞号"
    >
      <KTable
        :table="table"
        :permissions="{ edit: Permissions.System.CodeRule.update }"
        hide-add
        hide-delete
        hide-enable
        @edit="openEdit"
      >
        <template #cell-ruleField="{ row }">
          <span class="font-medium text-slate-700 dark:text-slate-300">{{ getFieldName(row as CodeRuleEntity) }}</span>
        </template>
        <template #cell-prefix="{ row }">
          <code
            v-if="(row as CodeRuleEntity).prefix"
            class="font-mono text-xs font-semibold px-1.5 py-0.5 rounded bg-blue-50 dark:bg-blue-950/40 text-blue-600 dark:text-blue-400 border border-blue-200 dark:border-blue-900/60"
          >
            {{ (row as CodeRuleEntity).prefix }}
          </code>
          <span
            v-else
            class="text-slate-400"
          >-</span>
        </template>
        <template #cell-template="{ row }">
          <code
            v-if="(row as CodeRuleEntity).template"
            class="font-mono text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700"
          >
            {{ (row as CodeRuleEntity).template }}
          </code>
          <span
            v-else
            class="text-slate-400"
          >-</span>
        </template>
        <template #cell-nextCode="{ row }">
          <ElTag
            type="info"
            disable-transitions
            class="font-mono tabular-nums font-semibold"
          >
            {{ nextCode(row as CodeRuleEntity) }}
          </ElTag>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :columns="2"
      :extra-rules="extraRules"
      :service="CodeRuleService"
      title="编码规则"
      width="800px"
      @loaded="form = $event"
      @saved="onSaved"
    >
      <!-- 字段下拉：选项来自后端字典，改字段时带出该字段的默认前缀 -->
      <template #field-ruleField="{ form: formData }">
        <ElSelect
          v-model="(formData as CodeRuleEntity).ruleField"
          :disabled="!!(formData as CodeRuleEntity).id"
          placeholder="请选择编码所属字段"
          @change="fieldChanged"
        >
          <ElOption
            v-for="item in fieldList"
            :key="item.key"
            :label="item.label"
            :value="item.key"
          />
        </ElSelect>
      </template>

      <template #extra="{ form: formData }">
        <ElFormItem label="可选参数">
          <ElTag
            v-for="param in paramList"
            :key="param.value"
            class="param-item"
            @click="appendParam(param)"
          >
            {{ param.desc }}
          </ElTag>
        </ElFormItem>
        <ElFormItem
          v-if="(formData as CodeRuleEntity).prefix || (formData as CodeRuleEntity).template"
          label="示例编码"
        >
          <span class="demo-prefix">{{ (formData as CodeRuleEntity).prefix }}</span>
          <span class="demo-template">{{ previewTemplate((formData as CodeRuleEntity).template) }}</span>
          <span class="demo-sn">{{ '1'.padStart(Math.min((formData as CodeRuleEntity).snLength ?? 1, 10), '0') }}</span>
        </ElFormItem>
      </template>
    </KFormDialog>
  </div>
</template>

<style scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.param-item {
  margin: 0 2px;
  cursor: pointer;
}

.demo-prefix {
  margin: 0 2px;
  font-weight: bold;
  color: red;
}

.demo-template {
  margin: 0 2px;
  font-weight: bold;
  color: darkgreen;
}

.demo-sn {
  margin: 0 2px;
  font-weight: bold;
  color: blue;
}
</style>

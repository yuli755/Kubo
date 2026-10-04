<script lang="ts" setup>
import type { IFormModel } from '@/framework'
import { ElInput, ElInputNumber, ElLink, ElSwitch, ElTag } from 'element-plus'
import { computed, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Constant } from '@/config/Constant'
import { Permissions } from '@/config/permissions'
import { FeedbackUtil } from '@/framework'
import { useTable } from '@/hook/kubo/useTable'
import { ConfigService } from '@/hook/system/useConfig'
import { ConfigEntity } from '@/model/system/config/ConfigEntity'
import { ConfigType } from '@/model/system/config/ConfigType'

/**
 * # 参数配置（F4 批 6 · 新底座）
 *
 * 参数是**字符串存储 + 类型化编辑**：`config` 列在后端永远是 string，前端按 `type`
 * 决定用什么控件（布尔开关 / 数字框 / 文本），提交前再转回字符串。
 *
 * ## 三处口径（照抄旧页面，都是行为不是样式）
 * - **系统内置参数不可删**（`isSystem` 行 `disable-delete`），标识与类型也不可改
 *   （后端按 `flag` 读参数，改掉名字 = 参数静默失效）；
 * - 名称列点一下弹 `description`：`flag` 是给程序看的，说明才是给人看的；
 * - 类型转换放 `@loaded` / `beforeSubmit` 两个钩子里各做一次 —— 转换写在模板里
 *   会在每次渲染时重算，把用户输入冲掉。
 */
const table = useTable(ConfigService, { queryKey: ['system:config'] })

// ── 编辑器 ──
const editorVisible = ref(false)
const editingId = ref<number | null>(null)
/**
 * 表单对象（`@loaded` 回传）。
 *
 * 这里**复制**一份而不是留住弹窗内部那个对象：页面只读它来渲染「按 type 换控件」与提示，
 * 留住引用会让两处都以为自己是唯一写入者（旧 airpower 编辑器正是这么埋过坑）。
 */
const form = ref<ConfigEntity>(new ConfigEntity())
const config = computed(() => form.value)

function openEdit(row: ConfigEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}

/**
 * `@loaded`：收起一份表单快照，用于渲染 `type` 分支与系统参数提示。
 *
 * 旧实现（airpower 编辑器）在这里把字符串值转成数字/布尔再交给控件 —— 新底座不需要：
 * 控件读的是下面两个计算属性，它们的 `get`/`set` 各自负责转换，页面不必参与，
 * 也就不会再出现「转换写在 loaded 里、用户一输入又被冲掉」那类问题。
 */
function onLoaded(data: IFormModel) {
  form.value = Object.assign(new ConfigEntity(), data)
}

/**
 * 提交前兜底：`config` 列在后端是字符串。
 *
 * 正常情况下值已经由两个计算属性的 `set` 写成字符串，这里只是防御历史数据/预置值
 * （数字/布尔）溜进来 —— 传 `true` 后端会存成缺失值。
 */
function beforeSubmit(data: IFormModel): IFormModel {
  const entity = data as unknown as ConfigEntity
  if (entity.config !== undefined && entity.config !== null) {
    entity.config = String(entity.config)
  }
  return data
}

/** 布尔开关要 boolean，而字段是字符串 —— 用带 get/set 的计算属性桥接，读写都不丢类型 */
const booleanConfig = computed<boolean>({
  get: () => config.value.config === Constant.BOOLEAN_YES,
  set: (value) => {
    config.value.config = value ? Constant.BOOLEAN_YES : Constant.BOOLEAN_NO
  },
})

const numberConfig = computed<number>({
  get: () => Number(config.value.config ?? 0),
  set: (value) => {
    config.value.config = String(value)
  },
})
</script>

<template>
  <div class="k-page">
    <KPanel
      title="参数配置"
      description="运行参数按类型编辑；系统内置参数不可删除，且标识与类型不可改"
    >
      <KTable
        :table="table"
        :disable-delete="(row: ConfigEntity) => row.isSystem"
        :permissions="{
          edit: Permissions.System.Config.update,
          delete: Permissions.System.Config.delete,
        }"
        hide-add
        hide-enable
        @edit="openEdit"
      >
        <template #cell-name="{ row }">
          <ElLink
            type="primary"
            :underline="false"
            @click="FeedbackUtil.toastInfo((row as ConfigEntity).description || '该参数没有说明')"
          >
            {{ (row as ConfigEntity).name }}
          </ElLink>
        </template>
        <template #cell-flag="{ row }">
          <code class="font-mono text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-700 dark:text-slate-300 border border-slate-200 dark:border-slate-700 select-all">
            {{ (row as ConfigEntity).flag }}
          </code>
        </template>
        <template #cell-config="{ row }">
          <ElSwitch
            v-if="ConfigType.BOOLEAN.equalsKey((row as ConfigEntity).type)"
            :model-value="(row as ConfigEntity).config === Constant.BOOLEAN_YES"
            disabled
          />
          <span
            v-else
            class="font-mono tabular-nums"
          >{{ (row as ConfigEntity).config }}</span>
        </template>
        <template #cell-type="{ row }">
          <ElTag
            :type="ConfigType.STRING.equalsKey((row as ConfigEntity).type) ? 'info' : 'warning'"
            disable-transitions
          >
            {{ ConfigType.getLabel((row as ConfigEntity).type) }}
          </ElTag>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :before-submit="beforeSubmit"
      :service="ConfigService"
      width="640px"
      @loaded="onLoaded"
      @saved="onSaved"
    >
      <!-- 标识：内置参数只读（与后端读参一致），自定义参数可改 -->
      <template #field-flag="{ form: formData }">
        <ElInput
          v-model="(formData as ConfigEntity).flag"
          :disabled="!!(formData as ConfigEntity).isSystem"
          placeholder="程序读取用的标识"
        />
      </template>

      <template #field-type="{ form: formData }">
        <ElInput
          :model-value="ConfigType.getLabel((formData as ConfigEntity).type)"
          disabled
        />
      </template>

      <!-- 值：按类型换控件 -->
      <template #field-config>
        <ElInputNumber
          v-if="ConfigType.NUMBER.equalsKey(config.type)"
          v-model="numberConfig"
        />
        <ElSwitch
          v-else-if="ConfigType.BOOLEAN.equalsKey(config.type)"
          v-model="booleanConfig"
        />
        <ElInput
          v-else
          v-model="config.config"
        />
      </template>

      <template #extra>
        <el-form-item
          v-if="config.isSystem"
          label="提示"
        >
          <span class="system-hint">系统内置参数：标识与类型由后端读取，界面上只能改值</span>
        </el-form-item>
      </template>
    </KFormDialog>
  </div>
</template>

<style scoped>
.system-hint {
  color: var(--el-text-color-secondary);
}
</style>

<script lang="ts" setup generic="E extends IIdentifiableEntity">
import type { FormInstance } from 'element-plus'
import type { AbstractCurdService, IFormField, IFormModel, IIdentifiableEntity, WebValidateRuleSet } from '@/framework'
import {
  ElAlert,
  ElButton,
  ElDatePicker,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import { computed, nextTick, ref, watch } from 'vue'
import { FeedbackUtil, getEnumItems, getFormConfigList, getModelName, WebValidator } from '@/framework'
import { clearFieldError, fieldError } from '@/stores/fieldError'
import { draftKeyOf } from '@/utils/draftStorage'

/**
 * # KFormDialog —— 自研表单弹窗（替代 airpower `ADialog` + `useEditor` 的组合）
 *
 * ## 为什么要有它
 * airpower 的写法是「`useEditor(props, ServiceClass)` 管状态 + `<ADialog>` 管壳 + `AFormField`
 * 按字段渲染」，三者通过 `DialogProps.create/withParam/withId/withSelector` 这套静态方法串联，
 * 调用方必须记住「弹窗怎么开、回调怎么接」。换底座时最该改的就是这里：**把协议收敛成一个组件**。
 *
 * ## 契约（页面怎么写）
 * ```vue
 * <KFormDialog
 *   v-model="visible"
 *   :service="UnitService"
 *   :id="editingId"
 *   @saved="() => table.reload()"
 * />
 * ```
 * - `modelValue` 控制显隐；`:id` 有值 = 编辑（先拉详情），无值 = 新增；
 * - 校验规则由 `@Form` 元数据经 `WebValidator.createRules` **自动生成**（不手写 rules）；
 * - 提交成功 `emit('saved')`，页面只负责刷新列表 —— 不需要再接 `onCallback`。
 *
 * ## 字段渲染
 * 目前支持 `@Form` 元数据覆盖的常见类型：字符串（含 textarea）、数字、布尔（开关）、
 * 字典（下拉）。选择器（远程下拉/树选择）留到 F2 第二批，页面可先用 `#field-<name>` 插槽自定义。
 */

const props = withDefaults(defineProps<{
  modelValue: boolean
  /** 服务类（new 一个实例用于 `add/update/getDetail`） */
  service: new () => AbstractCurdService<E>
  /** 编辑的记录 id；不传为新增 */
  id?: number | null
  /** 弹窗标题（默认按实体名自动拼「新增/编辑」） */
  title?: string
  /** 提交成功后是否自动关闭 */
  closeOnSaved?: boolean
  /** 弹窗宽度 */
  width?: string
  /**
   * 新增时的预置字段（如「添加子级」要带上 `parentId`）。
   * 只在新增（`:id` 为空）时生效 —— 编辑时以服务端详情为准，预置值不能覆盖真实数据。
   */
  preset?: Record<string, unknown>
  /** 表单列数（2 列用于字段多的实体，替代原库 `AGroup :column="2"`） */
  columns?: 1 | 2
  /**
   * 提交前的最后一道闸门：拿到即将提交的数据，返回 `null` 表示**中止提交**。
   *
   * 页面用它做"提交前还要拦一下"的事（如物料的价格字段被脱敏策略限制时不许保存），
   * 也可以返回一份改写后的数据。中止时页面自己给提示 —— 框架不猜原因。
   */
  beforeSubmit?: (form: Record<string, unknown>) => Record<string, unknown> | null | Promise<Record<string, unknown> | null>
  /**
   * 页面补充/覆盖的校验规则（按字段名给）。
   *
   * 自动生成的规则只能表达元数据里有的东西（必填、长度、格式、范围），
   * 而业务侧还有"这一列不许出现某些字"这类判断（如角色名不能带「管理员」）。
   * 那些规则属于**页面**，框架只负责把它们和元数据规则合并后交给表单 ——
   * 不提供这个口子，业务规则就只能被丢掉或塞进实体装饰器。
   */
  extraRules?: WebValidateRuleSet
}>(), {
  closeOnSaved: true,
  width: '560px',
  columns: 1,
})

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', id: number): void
  (event: 'closed'): void
  /**
   * 表单已初始化（新增重置完成 / 编辑详情加载完成）。
   *
   * 为什么要把表单对象交出去：页面偶尔需要在**表单之外**动数据（如「所属部门」多选、
   * 明细行），而这些数据是提交体的一部分。交出去的引用在每次初始化后都会重新 emit，
   * 页面持有最新的一份即可，不需要在弹窗内部开洞。
   */
  (event: 'loaded', form: IFormModel): void
}>()

/**
 * 插槽声明。
 *
 * 不写 `defineSlots` 时，页面用 `#extra` / `#field-xxx` 会报
 * `Property 'extra' does not exist on type '__VLS_Slots'` —— 泛型组件在 vue-tsc 下
 * 必须显式声明插槽，否则页面「明明能跑、类型却过不了」。
 */
defineSlots<{
  /** 页面自定义内容（多选标签、明细行等），放在元数据字段之后 */
  extra?: (props: { form: IFormModel }) => unknown
  /** 覆盖任意字段的控件（选择器/上传等） */
  [key: `field-${string}`]: (props: { field: IFormField, form: IFormModel }) => unknown
  /** 覆盖底部按钮区 */
  footer?: (props: { submit: () => void, cancel: () => void, submitting: boolean }) => unknown
}>()

/**
 * 实例化服务类。
 * 服务类来自 props（运行时名字），`new props.service()` 会被 `new-cap` 误判，
 * 统一在参数名大写的 helper 里 new 一次。
 */
function createService(ServiceClass: new () => AbstractCurdService<E>): AbstractCurdService<E> {
  return new ServiceClass()
}

const service = computed(() => createService(props.service))
/**
 * 实体构造器。
 * 单独取一个 `EntityClass` 变量再 `new EntityClass()`：直接写 `new (x as ...)()`
 * 会触发 `new-cap`（构造器名必须首字母大写），而这里的名字来自运行时。
 */
const EntityClass = computed(() => service.value.entityClass)

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const form = ref<Record<string, unknown>>({})
const isLoading = ref(false)
const isSubmitting = ref(false)

/** 表单字段（来自 `@Form` 元数据，已剔除 `hide`） */
const fields = computed<IFormField[]>(() => getFormConfigList(EntityClass.value) as IFormField[])

/** 校验规则：由 `@Form` 元数据生成（必填、长度、正则…），页面不写 rules */
/**
 * 校验规则：由 `@Form` 元数据生成（必填、长度、正则…），页面不写 rules。
 * 断言是边界收口：`createRules` 的返回类型是它自己的规则集，而 Element Plus 的 `FormRules`
 * 结构兼容但有名义差异，硬转一次比在每个页面各写一遍 rules 划算。
 */
const rules = computed(() => WebValidator.createRules(EntityClass.value as never, props.extraRules ?? {}) as unknown as Record<string, unknown[]>)

const title = computed(() => {
  if (props.title)
    return props.title
  const name = getModelName(EntityClass.value)
  return props.id ? `编辑${name}` : `新增${name}`
})

/**
 * 控件类型：`@Form` 里是一组**布尔标志**（`switch` / `textarea` / `password` / `radio`…），
 * 没有 `type` 字符串 —— 按标志优先级推导，顺序即优先级（多行 > 开关 > 下拉 > 普通输入）。
 * `password` 由同一个 ElInput 承接（`type="password"` + `show-password` 小眼睛），
 * 模板没有单独分支 —— §5-1 补 password 字段时发现旧模板只映射 textarea/text，
 * `password` 会静默降级成明文输入框，故 fallback 的 type 三元里必须带上它。
 */
function fieldType(field: IFormField): 'date' | 'textarea' | 'switch' | 'select' | 'password' | 'number' | 'input' {
  if (field.dateType)
    return 'date'
  if (field.textarea)
    return 'textarea'
  if (field.switch)
    return 'switch'
  if (field.dictionary)
    return 'select'
  if (field.password)
    return 'password'
  if (field.number)
    return 'number'
  return 'input'
}

/**
 * 「所属上级」的展示文本。
 *
 * 取 `preset.parent`（页面点「添加子级」时带进来的父行）或详情里的 `parent`，
 * 两者都没有就不渲染（新增顶级节点时本来就没有上级）。
 */
const parentLabel = computed(() => {
  const parent = (form.value.parent ?? props.preset?.parent) as { name?: string, code?: string } | undefined
  if (!parent || typeof parent !== 'object') {
    return ''
  }
  if (!parent.name) {
    return ''
  }
  return parent.code ? `${parent.name}(${parent.code})` : parent.name
})

/** 日期控件的选择器类型（`dateType` 的 `toString()` 就是 EP 的 type） */
function datePickerType(field: IFormField): string {
  const type = field.dateType as { toString?: () => string } | undefined
  return type?.toString?.() ?? 'date'
}

/**
 * 日期控件的值与显示格式。
 *
 * Element Plus 的 `value-format="x"` 表示**毫秒时间戳**，正好对上后端契约
 * （`dateFormatter: DateTimeFormatter.TIMESTAMP`）；不写 `value-format` 时拿到的是 Date 对象，
 * 提交给后端会变成 ISO 字符串 —— 后端字段是 `Long`，那就是类型不匹配。
 */
function dateValueFormat(field: IFormField): string {
  const formatter = field.dateFormatter as { toString?: () => string } | undefined
  const key = formatter?.toString?.()
  if (!key || key === 'timestamp') {
    return 'x'
  }
  return key
}

/** 显示格式（不传则与取值格式一致） */
function dateShowFormat(field: IFormField): string {
  const formatter = field.dateShowFormatter as { toString?: () => string } | undefined
  return formatter?.toString?.() ?? dateValueFormat(field)
}

function resetForm() {
  const Ctor = EntityClass.value
  const instance = new Ctor() as Record<string, unknown>
  const next: Record<string, unknown> = {}
  for (const field of fields.value) {
    // 实体类字段大多用 `!` 声明（无初始化器），`new Ctor()[field.key]` 恒为 undefined，
    // 此时必须落到 `@Form({ defaultValue })` 元数据 —— 否则合同类型/起止时间这类默认值
    // 在「新增」时永远为空，requiredNumber 字段直接拦住保存，默认值声明形同虚设。
    // 顺序：实例上已有值（成员初始化器/编辑回填）> 元数据默认值 > 空字符串。
    next[field.key] = instance[field.key] ?? field.defaultValue ?? ''
  }
  // 预置字段（如 parentId）覆盖实体默认值：值可能是 0，所以要按 key 存在与否判断而不是真值
  for (const [key, value] of Object.entries(props.preset ?? {})) {
    next[key] = value
  }
  form.value = next
  emit('loaded', form.value)
}

async function loadDetail(id: number) {
  isLoading.value = true
  try {
    const entity = await service.value.getDetail(id)
    form.value = { ...(entity as unknown as Record<string, unknown>) }
    emit('loaded', form.value)
  }
  finally {
    isLoading.value = false
  }
}

const draftStorageKey = computed(() => {
  if (props.id != null) {
    return null
  }
  return draftKeyOf(props.service.name)
})

const hasDraft = ref(false)
const draftTime = ref('')

function checkDraft() {
  if (!draftStorageKey.value) {
    hasDraft.value = false
    return
  }
  try {
    const raw = localStorage.getItem(draftStorageKey.value)
    if (raw) {
      const parsed = JSON.parse(raw) as { data: Record<string, unknown>, time: number }
      if (parsed && parsed.data && Object.keys(parsed.data).length > 0) {
        hasDraft.value = true
        draftTime.value = new Date(parsed.time).toLocaleTimeString()
        return
      }
    }
  }
  catch {
    // ignore
  }
  hasDraft.value = false
}

function restoreDraft() {
  if (!draftStorageKey.value) {
    return
  }
  try {
    const raw = localStorage.getItem(draftStorageKey.value)
    if (raw) {
      const parsed = JSON.parse(raw) as { data: Record<string, unknown> }
      if (parsed?.data) {
        Object.assign(form.value, parsed.data)
        FeedbackUtil.toastSuccess('已恢复此前未保存的草稿')
      }
    }
  }
  catch {
    // 草稿坏损（JSON 截断 / 旧格式）：静默作废它，别让一次坏草稿挡住整个表单
    discardDraft()
  }
  finally {
    hasDraft.value = false
  }
}

function discardDraft() {
  if (draftStorageKey.value) {
    try {
      localStorage.removeItem(draftStorageKey.value)
    }
    catch {
      // ignore
    }
  }
  hasDraft.value = false
  FeedbackUtil.toastInfo('已放弃未保存草稿')
}

function clearDraft() {
  if (draftStorageKey.value) {
    try {
      localStorage.removeItem(draftStorageKey.value)
    }
    catch {
      // ignore
    }
    hasDraft.value = false
  }
}

let draftDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(
  () => form.value,
  (val) => {
    if (!draftStorageKey.value || props.id != null) {
      return
    }
    if (draftDebounceTimer) {
      clearTimeout(draftDebounceTimer)
    }
    draftDebounceTimer = setTimeout(() => {
      const hasContent = Object.values(val).some(v => v !== undefined && v !== null && v !== '')
      if (hasContent) {
        try {
          localStorage.setItem(draftStorageKey.value!, JSON.stringify({ data: val, time: Date.now() }))
        }
        catch {
          // ignore
        }
      }
    }, 600)
  },
  { deep: true },
)

/**
 * 打开时初始化：
 * - 编辑：拉详情并回填；
 * - 新增：按字段重置成空值（**不能复用上一次的表单对象**，否则新增会把上次编辑的残留带过去，
 *   这是"新建出来一条脏数据"最常见的原因）。
 */
watch(
  () => [props.modelValue, props.id] as const,
  async ([opened, id]) => {
    if (!opened)
      return
    await nextTick()
    formRef.value?.clearValidate()
    if (id) {
      hasDraft.value = false
      await loadDetail(id)
    }
    else {
      resetForm()
      checkDraft()
    }
  },
  { immediate: true },
)

/**
 * 后端参数校验失败（HTTP 200 + `code=4002`）时的**字段定位**。
 *
 * 拦截器解析出字段名后发布到 `fieldError`（见 `src/stores/fieldError.ts`），这里订阅并把出错的
 * 表单项滚到视野内。此前后端明明说了是哪个字段，前端却只在角落弹一句文字，长表单里用户还得自己找
 * —— 「知道错了但不知道错在哪」是表单页最常见的挫败感。
 *
 * 三条纪律：
 * - 只由**当前打开**的对话框消费（同一页面可能挂了多个 KFormDialog）；
 * - 字段名必须真的在本表单的 `fields` 里，否则不滚（后端字段名与前端 key 偶有出入，
 *   宁可不动也不要跳到无关的输入框）；
 * - 消费后立刻清空信号，避免下次打开表单被旧信号误触发。
 */
watch(fieldError, async (notice) => {
  if (!notice || !props.modelValue)
    return
  if (!fields.value.some(field => field.key === notice.field))
    return
  await nextTick()
  formRef.value?.scrollToField(notice.field)
  clearFieldError()
})

async function onSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (valid === false)
    return

  // 页面可以在这里拦一道（返回 null = 中止），如物料价格被脱敏策略限制时不许保存
  let payload = form.value
  if (props.beforeSubmit) {
    const next = await props.beforeSubmit(form.value)
    if (!next) {
      return
    }
    payload = next
  }

  isSubmitting.value = true
  try {
    const Ctor = EntityClass.value
    const entity = Object.assign(new Ctor(), payload) as unknown as E
    const id = props.id
      ? await service.value.update({ ...entity, id: props.id } as E)
      : await service.value.add(entity)
    clearDraft()
    FeedbackUtil.toastSuccess(props.id ? '保存成功' : '新增成功')
    emit('saved', Number(id))
    if (props.closeOnSaved)
      visible.value = false
  }
  finally {
    isSubmitting.value = false
  }
}

function onCancel() {
  visible.value = false
  emit('closed')
}
</script>

<template>
  <ElDialog
    v-model="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    @closed="emit('closed')"
  >
    <ElAlert
      v-if="hasDraft"
      type="info"
      :closable="false"
      show-icon
      class="k-form-dialog__draft-alert"
      title="检测到此前未保存的表单草稿"
      :description="`自动暂存于 ${draftTime}，是否恢复继续编辑？`"
    >
      <template #default>
        <div class="k-form-dialog__draft-actions">
          <ElButton
            size="small"
            type="primary"
            link
            @click="restoreDraft"
          >
            恢复草稿
          </ElButton>
          <ElButton
            size="small"
            type="info"
            link
            @click="discardDraft"
          >
            放弃草稿
          </ElButton>
        </div>
      </template>
    </ElAlert>

    <ElForm
      ref="formRef"
      v-loading="isLoading"
      :model="form"
      :rules="rules"
      label-width="110px"
      :class="{ 'k-form-dialog--two-columns': columns === 2 }"
      @submit.prevent="onSubmit"
      @keydown.ctrl.enter="onSubmit"
      @keydown.meta.enter="onSubmit"
    >
      <!-- 树形实体（仓库/生产单元/部门/菜单）的「所属上级」：不在 @Form 里，
           由页面通过 `preset` 带进来，只读展示，避免用户以为可以在这里改父级 -->
      <ElFormItem
        v-if="parentLabel"
        label="所属上级"
      >
        <span class="k-form-dialog__readonly">{{ parentLabel }}</span>
      </ElFormItem>

      <ElFormItem
        v-for="field in fields"
        :key="field.key"
        :label="field.label"
        :prop="field.key"
      >
        <!-- 页面可对任意字段覆盖控件（选择器/上传等），不必等框架支持 -->
        <slot
          :name="`field-${field.key}`"
          :field="field"
          :form="form"
        >
          <ElDatePicker
            v-if="fieldType(field) === 'date'"
            v-model="form[field.key] as number | string"
            :format="dateShowFormat(field)"
            :placeholder="field.placeholder || `请选择${field.label}`"
            :type="datePickerType(field) as never"
            :value-format="dateValueFormat(field)"
            class="k-form-dialog__control"
          />
          <ElSwitch
            v-else-if="fieldType(field) === 'switch' || typeof form[field.key] === 'boolean'"
            v-model="form[field.key] as boolean"
          />
          <ElSelect
            v-else-if="fieldType(field) === 'select'"
            v-model="form[field.key] as string | number"
            :placeholder="field.placeholder || `请选择${field.label}`"
            clearable
            class="k-form-dialog__control"
          >
            <ElOption
              v-for="option in getEnumItems(field.dictionary)"
              :key="String(option.key)"
              :label="option.label"
              :value="option.key"
            />
          </ElSelect>
          <ElInputNumber
            v-else-if="fieldType(field) === 'number'"
            v-model="form[field.key] as number"
            :min="field.min"
            :max="field.max"
            :precision="field.precision"
            class="k-form-dialog__control"
          />
          <ElInput
            v-else
            v-model="form[field.key] as string"
            :type="fieldType(field) === 'textarea' ? 'textarea' : fieldType(field) === 'password' ? 'password' : 'text'"
            :show-password="fieldType(field) === 'password'"
            autocomplete="off"
            :rows="3"
            :placeholder="field.placeholder || `请输入${field.label}`"
            clearable
          />
        </slot>
      </ElFormItem>
    </ElForm>

    <div
      v-if="$slots.extra"
      class="k-form-dialog__extra"
    >
      <slot
        name="extra"
        :form="form as IFormModel"
      />
    </div>

    <template #footer>
      <slot
        name="footer"
        :submit="onSubmit"
        :cancel="onCancel"
        :submitting="isSubmitting"
      >
        <ElButton @click="onCancel">
          取消
        </ElButton>
        <ElButton
          type="primary"
          :loading="isSubmitting"
          @click="onSubmit"
        >
          保存
        </ElButton>
      </slot>
    </template>
  </ElDialog>
</template>

<style scoped>
.k-form-dialog__draft-alert {
  margin-bottom: 14px;
}

.k-form-dialog__draft-actions {
  margin-top: 6px;
  display: flex;
  gap: 12px;
}

.k-form-dialog__control {
  width: 100%;
}

.k-form-dialog__readonly {
  color: var(--el-text-color-regular);
}

/* 两列表单：字段多的实体（单据、合同）在一列里滚太长，扫读成本高 */
.k-form-dialog--two-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 16px;
}

.k-form-dialog--two-columns :deep(.el-form-item:has(textarea)) {
  grid-column: span 2;
}

.k-form-dialog__extra {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
</style>

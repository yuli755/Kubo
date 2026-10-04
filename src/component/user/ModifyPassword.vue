<script lang="ts" setup>
import type { FormInstance } from 'element-plus'
import type { WebValidateRule } from '@/framework'
import { ElButton, ElForm, ElFormItem, ElInput } from 'element-plus'
import { onBeforeUnmount, reactive, ref } from 'vue'
import { FeedbackUtil } from '@/framework'
import { UserService } from '@/model/personnel/user/UserService'

/**
 * # 修改密码（F4 批 6 · 新底座）
 *
 * 这一块**刻意不用 `KFormDialog`**：它不是实体 CRUD —— 提交的是 `user/updateMyPassword`，
 * 载荷是「原密码 + 新密码 + 确认密码」，其中确认密码根本不是实体字段。
 * 把它硬塞进元数据驱动的表单，等于往实体上加一个只在这一处存在的字段。
 *
 * 三条行为与旧实现逐条一致：
 * 1. **两次密码不一致直接本地报错**，不发请求；
 * 2. 成功后提示「请重新登录」并**延迟 3 秒**跳登录页（服务端已把会话吊销）；
 * 3. 三个输入都非空才允许提交。
 *
 * ## R52 人性化提示补齐
 * 原来表单只有 `:model`，没有 `:rules`、也没有一个 `ElFormItem` 带 `:prop` ——
 * Element Plus 于是**没有任何一处能渲染字段级错误**：三个框没填时按钮只是变灰
 * （用户看到的是「按钮点不动，但不知道为什么」），两次密码不一致也只有一条
 * 3 秒就消失的 toast，确认框本身既不标红也不提示。现在：
 * - 每个输入项挂 `prop` + `rules`，没填就在**该项下方**红字写清「请输入原密码…」；
 * - 不一致时确认密码项下方常驻「两次密码不一致」（toast 保留，双通道）；
 * - 按钮不再靠 `disabled` 拦人：点击即校验并点名出错项，理由可见。
 */
const form = reactive({
  oldPassword: '',
  password: '',
  confirmPassword: '',
})

const isSubmitting = ref(false)
// R43 生命周期修复：3 秒跳转的定时器原来没留句柄、无法撤销——成功后 3 秒内
// 关掉抽屉/切走页面，仍会被 window.location.replace 强制甩到登录页。
// 卸载即撤销：用户已经自己走了，不该再替他跳。
let redirectTimer: ReturnType<typeof setTimeout> | null = null

onBeforeUnmount(() => {
  if (redirectTimer) {
    clearTimeout(redirectTimer)
    redirectTimer = null
  }
})

/**
 * 规则写在页面里而不是 `WebValidator.createRules(UserEntity)`：确认密码不是实体字段，
 * 且这里三条都是纯字符串校验，直接写字面量最直白（trigger 取 blur + change，
 * 免得用户填完第一项就再看不到提示）。
 *
 * ⚠️ 这里**故意不用 computed**：Element Plus 的 ElForm 在 `validate-on-rule-change`（默认开）
 * 下会 watch `rules` 的引用变化并立刻全量校验一次 —— 用 computed 时任何一次输入都会
 * 重建这个对象，于是「刚在原密码框敲第一个字符，三个框就全标红」。规则本身是静态的，
 * 比对用的 `form.password` 由 validator 在调用时刻读，不需要响应式。
 */
const rules: Record<string, WebValidateRule[]> = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: ['blur', 'change'] },
  ],
  password: [
    { required: true, message: '请输入新密码', trigger: ['blur', 'change'] },
    { min: 6, message: '至少输入 6 个字符', trigger: ['blur', 'change'] },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: ['blur', 'change'] },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次密码不一致'))
          return
        }
        callback()
      },
      trigger: ['blur', 'change'],
    },
  ],
}

/** 校验失败时要点名的字段中文名（这里三个字段都是本页面私有，不走 @Form 元数据） */
const FIELD_LABELS: Record<string, string> = {
  confirmPassword: '确认密码',
  oldPassword: '原密码',
  password: '新密码',
}

const formRef = ref<FormInstance>()

async function onSubmit() {
  // 顺序很讲究：①「两次密码不一致」是**同步**判定并立刻 toast —— 它不依赖任何异步校验，
  // 放在最前面保证点下按钮那一刻就有反馈（也便于用测试钉死）；
  // ② 再走 EP 的整表校验，把没填/太短的行内红字渲染出来，并点名第一个出错项。
  if (form.confirmPassword !== form.password) {
    FeedbackUtil.toastError('两次密码不一致')
    formRef.value?.validateField('confirmPassword').catch(() => {})
    return
  }
  const invalid = await formRef.value?.validate()
    .then(() => null)
    // element-plus 校验不通过时把「出错字段表」整体 reject 出来（键 = prop）
    .catch((fields: Record<string, unknown>) => (fields && typeof fields === 'object' ? fields : {}))
  if (invalid) {
    const key = Object.keys(invalid)[0]
    FeedbackUtil.toastError(key ? `请先完善：${FIELD_LABELS[key] ?? key}` : '请先完善必填项')
    if (key) {
      formRef.value?.scrollToField(key)
    }
    return
  }
  isSubmitting.value = true
  try {
    const { oldPassword, password } = form
    await new UserService().updateMyPassword({ oldPassword, password } as never)
    await FeedbackUtil.toastSuccess('密码修改成功，请重新登录')
    redirectTimer = setTimeout(() => {
      redirectTimer = null
      window.location.replace('/login')
    }, 3000)
  }
  finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <ElForm
    ref="formRef"
    class="modify-password"
    label-width="120px"
    :model="form"
    :rules="rules"
    @submit.prevent
  >
    <ElFormItem
      label="原密码"
      prop="oldPassword"
    >
      <ElInput
        v-model="form.oldPassword"
        clearable
        placeholder="请输入原密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem
      label="新密码"
      prop="password"
    >
      <ElInput
        v-model="form.password"
        clearable
        placeholder="请输入新密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem
      label="确认密码"
      prop="confirmPassword"
    >
      <ElInput
        v-model="form.confirmPassword"
        clearable
        placeholder="请再次输入新密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem>
      <ElButton
        :loading="isSubmitting"
        type="primary"
        @click="onSubmit"
      >
        确认修改密码
      </ElButton>
    </ElFormItem>
  </ElForm>
</template>

<style scoped>
.modify-password {
  padding-top: 20px;
}
</style>

<script lang="ts" setup>
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
 */
const form = reactive({
  oldPassword: '',
  password: '',
})

const confirmPassword = ref('')
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

const canSubmit = () => !!form.oldPassword && !!form.password && !!confirmPassword.value

async function onSubmit() {
  if (confirmPassword.value !== form.password) {
    FeedbackUtil.toastError('两次密码不一致')
    return
  }
  isSubmitting.value = true
  try {
    await new UserService().updateMyPassword({ ...form } as never)
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
    class="modify-password"
    label-width="120px"
    :model="form"
    @submit.prevent
  >
    <ElFormItem label="原密码">
      <ElInput
        v-model="form.oldPassword"
        clearable
        placeholder="请输入原密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem label="新密码">
      <ElInput
        v-model="form.password"
        clearable
        placeholder="请输入新密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem label="确认下">
      <ElInput
        v-model="confirmPassword"
        clearable
        placeholder="确认下密码"
        type="password"
      />
    </ElFormItem>
    <ElFormItem>
      <ElButton
        :disabled="!canSubmit()"
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

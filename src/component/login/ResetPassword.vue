<script lang="ts" setup>
/**
 * 忘记密码 · 面板内重置（R41 ①a）
 *
 * ## 为什么存在
 * 登录页的「忘记密码？」此前是 `href="/forgot-password"` 的**死链**（§16.6 实测：点了被
 * 守卫弹回登录页）。而后端 `user/resetMyPassword` 一直是 `login = false` 可用的
 * （email/phone + 验证码 + 新密码，UserController.resetMyPassword），验证码复用登录页
 * 同一套 `sendEmail`/`sendSms` 通道 —— 也就是说「真出口」早就在，只是前端从未接上。
 *
 * ## 为什么做成面板内嵌而不是独立路由页
 * 重置的终点是「用新密码登录」：跳走再跳回会丢 `?redirect=` 回跳链，也逼用户重开流程。
 * 内嵌面板由 login.vue 持有 `resetMode` 开关，成功后把账号回填密码表单，一步到登录。
 *
 * ## 前端校验只到「能看懂」的深度
 * 后端对 reset 只强制 @NotBlank（格式错误也会以业务码回来并 toast）。前端做同构预检
 * （邮箱/手机格式、6 位码、6 位密码、两次一致）是为了**少跑一次必然失败的往返**，
 * 不发明后端没有的规则 —— 后端将来收紧，前端不会反向拦人。
 */
import { ElButton, ElInput, ElMessage } from 'element-plus'
import { computed, onBeforeUnmount, ref } from 'vue'
import { authApi } from '@/api/system'
import { reportError } from '@/utils/errorHandler'
import { isEmail, isMobilePhone } from '@/utils/validators'

const props = withDefaults(defineProps<{
  /** 从登录表单带来的邮箱，减少一次输入 */
  initialEmail?: string
}>(), { initialEmail: '' })

const emit = defineEmits<{
  /** 返回登录 */
  back: []
  /** 重置成功，携带账号供登录表单回填 */
  success: [account: string]
}>()

type ResetChannel = 'email' | 'phone'

const channel = ref<ResetChannel>('email')
const email = ref(props.initialEmail)
const phone = ref('')
const code = ref('')
const password = ref('')
const confirm = ref('')

/** 验证码冷却：后端对同一账号有 EMAIL_SEND_BUSY 防刷，这里做同构体验（按钮禁用倒计时） */
const CODE_COOLDOWN_SECONDS = 60
const cooldown = ref(0)
let cooldownTimer: ReturnType<typeof setInterval> | null = null
// R43 生命周期修复：请求在途时组件可能被卸载（关闭面板/返回登录），
// 迟到的 startCooldown 会造出无人认领的僵尸 interval（60s 写死引用）。
let disposed = false

const isValidEmail = computed(() => !!email.value && isEmail(email.value))
const isValidPhone = computed(() => !!phone.value && isMobilePhone(phone.value))
const isValidCode = computed(() => code.value.length === 6)
const isValidPassword = computed(() => password.value.length >= 6)
const confirmMatches = computed(() => !!confirm.value && confirm.value === password.value)

const canSendCode = computed(() => (channel.value === 'email' ? isValidEmail.value : isValidPhone.value))
const canSubmit = computed(() =>
  canSendCode.value && isValidCode.value && isValidPassword.value && confirmMatches.value,
)

function switchChannel(next: ResetChannel) {
  if (channel.value === next) {
    return
  }
  channel.value = next
  // 清掉凭证类输入：跨渠道带着旧验证码/密码提交只会必然失败
  code.value = ''
  password.value = ''
  confirm.value = ''
}

function startCooldown() {
  // 请求在途时组件可能已卸载（见 disposed），迟到的启动直接作废
  if (disposed) {
    return
  }
  cooldown.value = CODE_COOLDOWN_SECONDS
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

onBeforeUnmount(() => {
  disposed = true
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
})

const isSending = ref(false)

async function onSendCode() {
  if (!canSendCode.value || cooldown.value > 0) {
    return
  }
  isSending.value = true
  try {
    if (channel.value === 'email') {
      await authApi.sendEmailCode(email.value)
      ElMessage.success('验证码已发送，请注意查收（请检查垃圾箱）')
    }
    else {
      await authApi.sendSmsCode(phone.value)
      ElMessage.success('验证码已发送，请注意查收')
    }
    startCooldown()
  }
  catch (error) {
    reportError(error, 'reset-send-code')
  }
  finally {
    isSending.value = false
  }
}

const isSubmitting = ref(false)

async function onSubmitReset() {
  if (!canSubmit.value) {
    return
  }
  isSubmitting.value = true
  try {
    const account = channel.value === 'email' ? { email: email.value } : { phone: phone.value }
    await authApi.resetMyPassword({
      ...account,
      code: code.value,
      password: password.value,
    })
    emit('success', channel.value === 'email' ? email.value : phone.value)
  }
  catch (error) {
    reportError(error, 'reset-my-password')
  }
  finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <div class="reset-password">
    <div class="reset-header">
      <button
        class="back-link"
        type="button"
        @click="emit('back')"
      >
        ‹ 返回登录
      </button>
      <span class="reset-title">重置密码</span>
    </div>

    <div
      class="channel-tabs"
      role="tablist"
      aria-label="验证方式"
    >
      <button
        class="channel-tab"
        :class="{ active: channel === 'email' }"
        role="tab"
        :aria-selected="channel === 'email'"
        type="button"
        @click="switchChannel('email')"
      >
        邮箱验证
      </button>
      <button
        class="channel-tab"
        :class="{ active: channel === 'phone' }"
        role="tab"
        :aria-selected="channel === 'phone'"
        type="button"
        @click="switchChannel('phone')"
      >
        手机号验证
      </button>
    </div>

    <form
      class="reset-form"
      @submit.prevent="onSubmitReset"
    >
      <div
        v-if="channel === 'email'"
        class="form-item"
      >
        <!-- 实测（vitest dump）：本版本 el-input 把 id 声明为 prop 却不绑定到内层
             <input>，label[for] 因此指向不存在的 id（渲染器还会把 for 抄错元素）。
             与其留一个坏关联，不如 label 做纯视觉、可访问名由 placeholder 承载。 -->
        <span class="form-label">邮箱地址</span>
        <ElInput
          v-model="email"
          type="text"
          placeholder="请输入注册邮箱"
          autocomplete="email"
        />
        <div
          v-if="!isValidEmail && email"
          class="form-error"
        >
          请输入正确的邮箱地址
        </div>
      </div>
      <div
        v-else
        class="form-item"
      >
        <span class="form-label">手机号</span>
        <ElInput
          v-model="phone"
          type="text"
          placeholder="请输入注册手机号"
          autocomplete="tel"
        />
        <div
          v-if="!isValidPhone && phone"
          class="form-error"
        >
          请输入正确的手机号
        </div>
      </div>

      <div class="form-item">
        <span class="form-label">验证码</span>
        <div class="code-row">
          <ElInput
            v-model="code"
            maxlength="6"
            placeholder="6 位验证码"
            autocomplete="one-time-code"
          />
          <ElButton
            :disabled="!canSendCode || cooldown > 0"
            :loading="isSending"
            class="send-code-btn"
            @click="onSendCode"
          >
            {{ cooldown > 0 ? `${cooldown}s 后重发` : '发送验证码' }}
          </ElButton>
        </div>
      </div>

      <div class="form-item">
        <span class="form-label">新密码</span>
        <ElInput
          v-model="password"
          show-password
          type="password"
          placeholder="至少 6 位"
          autocomplete="new-password"
        />
        <div
          v-if="password && !isValidPassword"
          class="form-error"
        >
          密码至少 6 位
        </div>
      </div>

      <div class="form-item">
        <span class="form-label">确认新密码</span>
        <ElInput
          v-model="confirm"
          show-password
          type="password"
          placeholder="再次输入新密码"
          autocomplete="new-password"
          @keyup.enter="onSubmitReset"
        />
        <div
          v-if="confirm && !confirmMatches"
          class="form-error"
        >
          两次输入的密码不一致
        </div>
      </div>

      <ElButton
        :disabled="!canSubmit"
        :loading="isSubmitting"
        class="submit-btn"
        native-type="submit"
        type="primary"
      >
        重置密码
      </ElButton>
    </form>
  </div>
</template>

<style lang="scss" scoped>
.reset-password {
  display: flex;
  flex-direction: column;
  width: 100%;

  .reset-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;

    .back-link {
      padding: 0;
      border: none;
      background: transparent;
      font-size: 13px;
      color: var(--el-text-color-secondary);
      cursor: pointer;
      transition: color 0.2s;

      &:hover {
        color: var(--el-color-primary);
      }
    }

    .reset-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
    }
  }

  .channel-tabs {
    display: flex;
    gap: 8px;
    margin-bottom: 16px;

    .channel-tab {
      flex: 1;
      padding: 8px 0;
      border: 1px solid var(--el-border-color);
      border-radius: 8px;
      background: var(--el-fill-color-blank);
      font-size: 13px;
      color: var(--el-text-color-regular);
      cursor: pointer;
      transition: all 0.2s;

      &.active {
        border-color: var(--el-color-primary);
        color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
      }
    }
  }

  .reset-form {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .form-item {
      display: flex;
      flex-direction: column;
      margin-bottom: 12px;

      .form-label {
        margin-bottom: 6px;
        font-size: 13px;
        font-weight: 500;
        color: var(--el-text-color-regular);
      }

      .code-row {
        display: flex;
        gap: 8px;

        .send-code-btn {
          flex-shrink: 0;
          min-width: 108px;
        }
      }

      .form-error {
        margin-top: 4px;
        font-size: 12px;
        line-height: 1.5;
        color: var(--el-color-danger);
      }
    }

    .submit-btn {
      width: 100%;
      margin-top: 4px;
    }
  }
}
</style>

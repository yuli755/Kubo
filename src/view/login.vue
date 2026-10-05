<script lang="ts" setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { loginInlineError } from '@/api/errorPolicy'
import { authApi } from '@/api/system'
import Logo from '@/component/login/Logo.vue'
import ResetPassword from '@/component/login/ResetPassword.vue'
import { useRoutePreload } from '@/composables/useRoutePreload'
import { LoginAction } from '@/model/common/LoginAction'
import { useAppRoute } from '@/router/appRouter'
import { useUserStore } from '@/stores/user'
import { signedOutReasonHint } from '@/utils/authSync'
import { reportError } from '@/utils/errorHandler'
import { safeRedirect } from '@/utils/safeRedirect'
import { isEmail, isMobilePhone, isNaturalNumber } from '@/utils/validators'

const userStore = useUserStore()
const route = useAppRoute()

const { preload } = useRoutePreload()

/**
 * ### 当前登录方式
 */
const currentAction = ref(LoginAction.LOGIN_VIA_PASSWORD)

/**
 * ### 是否已阅读协议
 *
 * R41 ①b：**默认不勾选**（合规口径）。默认勾选是让「同意」成为不作为的副产品，
 * 同意必须是主动动作 —— 这正是它作为合规前置的意义。未勾选时提交会走确认弹窗
 * （onSubmit），点了「我已阅读并同意」同样生效，多一条补救路径而非拦路。
 */
const isAgreed = ref(false)
/** R53：密码框的「请输入密码」红字只在用户碰过密码框之后出现（原来越早显示越像报错） */
const passwordTouched = ref(false)

// 「记住我」控件已移除（R39 §15.8 → §16）：登录态有效期由后端 JWT 说了算，前端无裁量权；
// 该勾选框绑定后无任何消费者，纯属空转。若实装为 sessionStorage，还会静默破坏 R-38
// 跨标签登出同步（storage 事件不跨 storage 区域）——删除是唯一不回退安全特性的选择。

const user = ref({
  email: '',
  password: '',
  code: '',
  phone: '',
})
// 阶段 E-L1：authGuard 写入的是 ?redirect=（原代码只读 ?redirectUri=，登录后回跳被静默丢弃）。
// OAuth 流程（后端 redirectLogin）用 ?redirectUri=；两者都读，优先 OAuth 参数。
const redirectUri = (route.query.redirectUri || route.query.redirect || '/console/index').toString()

// 阶段 E-C2：?redirectUri=/?redirect= 完全由访问者控制（guest 路由），直接拼进
// location 跳转会把登录后的会话送到攻击者域。只接受站内相对路径。
//
// 注：原先还有一条「OAuth 内部应用可跳到登记回调」的例外，随开放平台（module/open、
// /authorize 授权页）整体移除而删除——现在**只有** safeRedirect 一条路径，攻击面更小。
const redirectTarget = computed(() => safeRedirect(redirectUri))

// Loading 状态
const isLoadingLogin = ref(false)
const isEmailCodeLoading = ref(false)
const isPhoneCodeLoading = ref(false)

/**
 * ### 登录失败的表单内联提示（R-37）
 *
 * 后端把「账号或密码错误」渲染成 HTTP 200 + `200001`，而 `errorPolicy` 对这个码**全局静默**
 * （约定"由登录页自己呈现"）。修复前登录页 catch 里只有 `reportError`（只落 console），
 * 于是密码输错后界面毫无变化 —— 静默码没人呈现，就成了「点了没反应」。
 *
 * 只登记 `credential` 类（见 `loginInlineError`）：其余码全局 toast 已经说过同一句话，
 * 这里再显示一条就是双份提示。
 */
const loginFeedback = ref('')

/**
 * ### 被别的标签页登出的来源提示（R-38）
 * `installAuthSync` 在别的标签页登出时带 `?reason=signedOut` 送回来。
 * 只认这个 reason，直接访问登录页时保持安静。
 */
const signedOutHint = signedOutReasonHint(route.query.reason)

// 表单验证
const isValidCode = computed(() => user.value.code && user.value.code.length === 6)
const isValidEmail = computed(() => user.value.email && isEmail(user.value.email))
const isValidPhone = computed(() => user.value.phone && isMobilePhone(user.value.phone))
const isValidAccount = computed(
  () => user.value.email && (isEmail(user.value.email) || isNaturalNumber(user.value.email)),
)

/**
 * ### 计算是否禁用登录按钮
 */
const isButtonDisabled = computed(() => {
  switch (currentAction.value) {
    case LoginAction.LOGIN_VIA_EMAIL:
      return !isValidEmail.value || !isValidCode.value
    case LoginAction.LOGIN_VIA_PHONE:
      return !isValidPhone.value || !isValidCode.value
    case LoginAction.LOGIN_VIA_PASSWORD:
      return !user.value.password || !isValidAccount.value
    default:
      return true
  }
})

/**
 * ### 处理登录重定向
 */
async function loginRedirect(token: string) {
  userStore.setToken(token)

  // 登录成功后预加载工作台需要的 chunk，缩短进入工作台的等待时间
  preload([
    () => import('@/component/Console.vue'),
    () => import('@/view/console/index/index.vue'),
  ])

  // 正常登录（开放平台移除后不再有 OAuth 授权跳转分支）
  window.location.replace(redirectTarget.value)
}

/**
 * ### 登录失败的统一善后（R-37）
 *
 * 三件事：上报（可观测）、内联呈现（用户可见）、其余交给全局 toast（已存在的通用提示）。
 * 判据在 `loginInlineError` 里 —— 不在页面里散写码值，码表仍是唯一事实来源。
 */
function onLoginFailure(error: unknown, context: string) {
  reportError(error, context)
  loginFeedback.value = loginInlineError(error) ?? ''
}

/**
 * ### 账号密码登录
 */
async function onLogin() {
  isLoadingLogin.value = true
  loginFeedback.value = ''
  try {
    const token = await authApi.login({
      email: user.value.email,
      password: user.value.password,
    })
    await loginRedirect(token)
  }
  catch (error) {
    onLoginFailure(error, 'login')
  }
  finally {
    isLoadingLogin.value = false
  }
}

/**
 * ### 邮箱验证码登录
 */
async function onEmailLogin() {
  isLoadingLogin.value = true
  loginFeedback.value = ''
  try {
    const token = await authApi.loginViaEmail({
      email: user.value.email,
      code: user.value.code,
    })
    await loginRedirect(token)
  }
  catch (error) {
    onLoginFailure(error, 'login-email')
  }
  finally {
    isLoadingLogin.value = false
  }
}

/**
 * ### 手机验证码登录
 */
async function onPhoneLogin() {
  isLoadingLogin.value = true
  loginFeedback.value = ''
  try {
    const token = await authApi.loginViaPhone({
      phone: user.value.phone,
      code: user.value.code,
    })
    await loginRedirect(token)
  }
  catch (error) {
    onLoginFailure(error, 'login-phone')
  }
  finally {
    isLoadingLogin.value = false
  }
}

/**
 * ### 登录按钮事件
 */
async function onSubmit() {
  if (!isAgreed.value) {
    await ElMessageBox.confirm('请阅读并同意《隐私政策》以及《服务条款》相关内容。', '确认提示', {
      type: 'warning',
      confirmButtonText: '我已阅读并同意',
    })
    isAgreed.value = true
    return
  }

  switch (currentAction.value) {
    case LoginAction.LOGIN_VIA_PASSWORD:
      await onLogin()
      break
    case LoginAction.LOGIN_VIA_EMAIL:
      await onEmailLogin()
      break
    case LoginAction.LOGIN_VIA_PHONE:
      await onPhoneLogin()
      break
    default:
  }
}

/**
 * ### 发送邮箱验证码
 */
async function onSendEmailCode() {
  if (!isValidEmail.value) {
    ElMessage.warning('请先输入正确的邮箱地址')
    return
  }
  isEmailCodeLoading.value = true
  try {
    await authApi.sendEmailCode(user.value.email)
    ElMessage.success('邮箱验证码发送成功，请注意查收（请检查垃圾箱）')
  }
  catch (error) {
    reportError(error, 'login-send-email-code')
  }
  finally {
    isEmailCodeLoading.value = false
  }
}

/**
 * ### 发送短信验证码
 */
async function onSendSmsCode() {
  if (!isValidPhone.value) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }
  isPhoneCodeLoading.value = true
  try {
    await authApi.sendSmsCode(user.value.phone)
    ElMessage.success('短信验证码发送成功，请注意查收')
  }
  catch (error) {
    reportError(error, 'login-send-sms-code')
  }
  finally {
    isPhoneCodeLoading.value = false
  }
}

/**
 * ### 切换登录方式
 */
function switchLoginAction(action: LoginAction) {
  currentAction.value = action
  // 切换方式时清空验证码
  user.value.code = ''
}

/**
 * ### 忘记密码：面板内重置（R41 ①a）
 *
 * 此前这里是 `href="/forgot-password"` 死链（守卫弹回，§16.6 实测）。后端
 * `user/resetMyPassword` 一直可用，缺的只是前端入口。做成面板内嵌而不是路由页：
 * 重置的终点是「用新密码登录」，跳走会丢 ?redirect= 回跳链。
 */
const resetMode = ref(false)

function onResetSuccess(account: string) {
  resetMode.value = false
  // 回填账号、清空密码：用户只差「输入新密码 → 登录」一步
  currentAction.value = LoginAction.LOGIN_VIA_PASSWORD
  user.value.email = account
  user.value.password = ''
  loginFeedback.value = ''
  ElMessage.success('密码重置成功，请使用新密码登录')
}
</script>

<template>
  <div class="login">
    <Logo />
    <div class="card">
      <div class="app-name">
        {{ resetMode ? '重置密码' : '欢迎登录 Kubo' }}
      </div>

      <div class="body">
        <!-- 忘记密码：面板内重置（R41 ①a），整体替换表单区 -->
        <ResetPassword
          v-if="resetMode"
          :initial-email="user.email"
          @back="resetMode = false"
          @success="onResetSuccess"
        />

        <!-- 二维码登录 -->
        <div
          v-else-if="currentAction === LoginAction.LOGIN_VIA_QRCODE"
          class="qrcode-login"
        >
          <div class="qrcode-img">
            <img
              alt="登录二维码"
              src="@/assets/img/login/qrcode.png"
            >
          </div>
          <div class="qrcode-desc">
            请使用 Kubo App 或微信扫码登录
          </div>
          <div class="qrcode-hint">
            扫码后在手机端确认即可登录
          </div>
        </div>

        <!-- 表单登录 -->
        <div
          v-else
          class="form-container"
        >
          <!-- 账号密码登录 -->
          <form
            v-if="currentAction === LoginAction.LOGIN_VIA_PASSWORD"
            class="form"
            @submit.prevent="onSubmit"
          >
            <div class="form-item">
              <label
                class="form-label"
                for="account"
              >
                账号 / 邮箱 / 手机号
              </label>
              <el-input
                id="account"
                v-model="user.email"
                type="text"
                placeholder="请输入账号、邮箱或手机号"
                :class="{ 'is-error': !isValidAccount && user.email }"
                autocomplete="username"
              />
              <div
                v-if="!isValidAccount && user.email"
                class="form-error"
              >
                请输入正确的账号、邮箱或手机号
              </div>
            </div>

            <div class="form-item">
              <label
                class="form-label"
                for="password"
              >密码</label>
              <el-input
                id="password"
                v-model="user.password"
                type="password"
                show-password
                placeholder="请输入密码"
                :class="{ 'is-error': passwordTouched && !user.password }"
                autocomplete="current-password"
                @input="passwordTouched = true"
                @keyup.enter="onSubmit"
              />
              <div
                v-if="passwordTouched && !user.password"
                class="form-error"
              >
                请输入密码
              </div>
            </div>

            <div class="form-options">
              <!-- R41 ①a：原 href="/forgot-password" 是死链（守卫弹回，§16.6 实测）。
                   后端 resetMyPassword 一直可用，这里接上真正的面板。 -->
              <button
                class="forgot-password"
                type="button"
                @click="resetMode = true"
              >
                忘记密码？
              </button>
            </div>
          </form>

          <!-- 邮箱验证码登录 -->
          <form
            v-if="currentAction === LoginAction.LOGIN_VIA_EMAIL"
            class="form"
            @submit.prevent="onSubmit"
          >
            <div class="form-item">
              <label
                class="form-label"
                for="email"
              >邮箱地址</label>
              <el-input
                id="email"
                v-model="user.email"
                type="text"
                placeholder="请输入邮箱地址"
                :class="{ 'is-error': !isValidEmail && user.email }"
                autocomplete="email"
              >
                <template #suffix>
                  <el-button
                    :disabled="!isValidEmail || isEmailCodeLoading"
                    :loading="isEmailCodeLoading"
                    text
                    type="primary"
                    @click="onSendEmailCode"
                  >
                    {{ isEmailCodeLoading ? '发送中...' : '发送验证码' }}
                  </el-button>
                </template>
              </el-input>
              <div
                v-if="!isValidEmail && user.email"
                class="form-error"
              >
                请输入正确的邮箱地址
              </div>
            </div>

            <div class="form-item">
              <label
                class="form-label"
                for="email-code"
              >邮箱验证码</label>
              <el-input
                id="email-code"
                v-model="user.code"
                maxlength="6"
                type="text"
                placeholder="请输入6位验证码"
                :class="{ 'is-error': !isValidCode && user.code }"
                autocomplete="one-time-code"
                @keyup.enter="onSubmit"
              />
              <div
                v-if="!isValidCode && user.code"
                class="form-error"
              >
                验证码为6位数字
              </div>
            </div>
          </form>

          <!-- 手机验证码登录 -->
          <form
            v-if="currentAction === LoginAction.LOGIN_VIA_PHONE"
            class="form"
            @submit.prevent="onSubmit"
          >
            <div class="form-item">
              <label
                class="form-label"
                for="phone"
              >手机号</label>
              <el-input
                id="phone"
                v-model="user.phone"
                type="text"
                placeholder="请输入手机号"
                :class="{ 'is-error': !isValidPhone && user.phone }"
                autocomplete="tel"
              >
                <template #suffix>
                  <el-button
                    :disabled="!isValidPhone || isPhoneCodeLoading"
                    :loading="isPhoneCodeLoading"
                    text
                    type="primary"
                    @click="onSendSmsCode"
                  >
                    {{ isPhoneCodeLoading ? '发送中...' : '发送验证码' }}
                  </el-button>
                </template>
              </el-input>
              <div
                v-if="!isValidPhone && user.phone"
                class="form-error"
              >
                请输入正确的手机号
              </div>
            </div>

            <div class="form-item">
              <label
                class="form-label"
                for="phone-code"
              >短信验证码</label>
              <el-input
                id="phone-code"
                v-model="user.code"
                maxlength="6"
                type="text"
                placeholder="请输入6位验证码"
                :class="{ 'is-error': !isValidCode && user.code }"
                autocomplete="one-time-code"
                @keyup.enter="onSubmit"
              />
              <div
                v-if="!isValidCode && user.code"
                class="form-error"
              >
                验证码为6位数字
              </div>
            </div>
          </form>

          <!-- 被别的标签页登出的来源提示（R-38：installAuthSync 带 ?reason=signedOut 送回） -->
          <div
            v-if="signedOutHint && !loginFeedback"
            class="login-hint"
            role="status"
          >
            {{ signedOutHint }}
          </div>

          <!-- 登录失败的内联反馈（R-37：200001 这类全局静默码必须有人呈现） -->
          <div
            v-if="loginFeedback"
            class="login-feedback"
            role="alert"
            aria-live="assertive"
          >
            {{ loginFeedback }}
          </div>

          <!-- 协议同意 -->
          <div class="agreement">
            <el-checkbox
              v-model="isAgreed"
              class="agreement-checkbox"
            >
              我已阅读并同意
              <!-- @click.stop：链接在 el-checkbox 的 label 里，不拦截点击会让
                   「看条款」顺手切换勾选状态 —— 同意状态只能由勾选框本身决定 -->
              <a
                href="/privacy"
                target="_blank"
                class="agreement-link"
                @click.stop
              >《隐私政策》</a>
              以及
              <a
                href="/terms"
                target="_blank"
                class="agreement-link"
                @click.stop
              >《服务条款》</a>
            </el-checkbox>
          </div>

          <!-- 登录按钮 -->
          <div class="submit-area">
            <el-button
              :disabled="isButtonDisabled"
              :loading="isLoadingLogin"
              type="primary"
              native-type="submit"
              class="login-btn"
              @click="onSubmit"
            >
              立即登录
            </el-button>
          </div>
        </div>

        <!-- 登录方式切换 + 第三方登录（重置面板打开时隐藏：重置不是登录方式） -->
        <div
          v-if="!resetMode"
          class="login-methods"
        >
          <div class="divider">
            <span>其他登录方式</span>
          </div>
          <div
            class="tabs"
            role="tablist"
            aria-label="登录方式"
          >
            <button
              v-for="(item, index) in [
                LoginAction.LOGIN_VIA_PASSWORD,
                LoginAction.LOGIN_VIA_EMAIL,
                LoginAction.LOGIN_VIA_PHONE,
                LoginAction.LOGIN_VIA_QRCODE,
              ]"
              :key="item"
              :class="[`tab-${index}`, { active: currentAction === item }]"
              class="tab-item"
              :aria-selected="currentAction === item"
              role="tab"
              @click="switchLoginAction(item)"
            >
              {{ item }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.login {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-bg-color-page) url(@/assets/img/login/bg.jpg) no-repeat bottom center;
  background-size: cover;
  align-items: center;
  justify-content: center;
  min-height: 100vh;

  .card {
    /* F54-1：竖屏断点里 `width: 100%` + 左右 24px 内边距在 content-box 下算出
       「视口 + 8px」的外宽，卡片左右各被裁掉 4px（768/640/480/375 均复现）。
       项目没有全局 border-box 重置，这里就地修正，不动全局盒模型。 */
    box-sizing: border-box;
    /* F54-2：视口高度小于卡片高度时（页缩放 150%、矮屏、键盘弹出），
       原来 overflow:hidden 的 fixed 容器里卡片上下各被裁一截且**无处可滚**——
       这就是「内容被裁剪、点 F12 改变尺寸才恢复」的本体。改为卡片内部滚动。 */
    overflow: auto;
    max-height: calc(100vh - 32px);
    background: var(--el-bg-color);
    box-shadow: var(--kubo-shadow-lg);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 48px 56px;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 400px;
    position: absolute;
    right: 8%;
    border: 1px solid var(--el-border-color-lighter);

    .app-name {
      font-size: 28px;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin-bottom: 8px;
      letter-spacing: -0.5px;
    }

    .body {
      display: flex;
      flex-direction: column;
      width: 100%;
      padding: 0;
      flex: 1;
      margin-top: 24px;

      .qrcode-login {
        display: flex;
        flex-direction: column;
        align-items: center;
        padding: 20px 0;

        .qrcode-img {
          background: var(--el-bg-color);
          padding: 20px;
          border-radius: 12px;
          box-shadow: var(--kubo-shadow-base);

          img {
            width: 180px;
            height: 180px;
          }
        }

        .qrcode-desc {
          font-size: 15px;
          color: var(--el-text-color-primary);
          margin-top: 16px;
          font-weight: 500;
        }

        .qrcode-hint {
          font-size: 13px;
          color: var(--el-text-color-secondary);
          margin-top: 8px;
        }
      }

      .form-container {
        width: 100%;

        .form {
          display: flex;
          flex-direction: column;
          gap: 4px;

          .form-item {
            position: relative;
            margin-bottom: 4px;

            .form-label {
              display: block;
              font-size: 13px;
              color: var(--el-text-color-primary);
              font-weight: 500;
              margin-bottom: 8px;
            }

            .form-error {
              position: absolute;
              bottom: -18px;
              left: 0;
              font-size: 12px;
              color: var(--el-color-danger);
              height: 16px;
            }
          }

          ::v-deep(.el-input) {
            .el-input__wrapper {
              box-shadow: none;
              border-radius: 10px;
              border: 1px solid var(--el-border-color);
              transition: all 0.2s ease;
              background: var(--el-bg-color);

              &:hover {
                border-color: var(--el-border-color-hover);
              }

              &.is-focus {
                border-color: var(--el-color-primary);
                box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
              }

              &.is-error {
                border-color: var(--el-color-danger);

                &:hover {
                  border-color: var(--el-color-danger);
                }
              }
            }

            .el-input__inner {
              color: var(--el-text-color-primary);
              font-size: 14px;
              padding: 8px 12px;
              height: auto;
              background: transparent;

              &::placeholder {
                color: var(--el-text-color-placeholder);
              }
            }

            .el-input__suffix {
              .el-button {
                padding: 0 10px;
                height: 28px;
                font-size: 12px;
              }
            }
          }

          // R-37：登录失败的内联反馈（全局对 200001 静默，这里就是唯一的呈现处）
          .login-feedback {
            margin-top: 12px;
            padding: 8px 12px;
            border-radius: 8px;
            font-size: 13px;
            line-height: 1.5;
            color: var(--el-color-danger);
            background: var(--el-color-danger-light-9);
            border: 1px solid var(--el-color-danger-light-7);
          }

          // R-38：来源说明（被别的标签页登出）——不是错误，用信息色与 login-feedback 区分；
          // 出现错误反馈时让位（模板里 v-else 语义），同屏最多一条消息
          .login-hint {
            margin-top: 12px;
            padding: 8px 12px;
            border-radius: 8px;
            font-size: 13px;
            line-height: 1.5;
            color: var(--el-color-info);
            background: var(--el-color-info-light-9);
            border: 1px solid var(--el-color-info-light-7);
          }

          .form-options {
            display: flex;
            justify-content: flex-end;
            align-items: center;
            margin: 8px 0 16px;

            .forgot-password {
              // R41 ①a：死链 <a href="/forgot-password"> 改为真按钮（开面板），
              // button 默认样式需逐项归零，保持与原链接视觉一致。
              padding: 0;
              border: none;
              background: transparent;
              font-family: inherit;
              font-size: 13px;
              color: var(--el-color-primary);
              text-decoration: none;
              cursor: pointer;
              transition: color 0.2s;

              &:hover {
                text-decoration: underline;
                color: var(--el-color-primary-light-3);
              }
            }
          }
        }

        .agreement {
          margin: 8px 0 16px;

          .agreement-checkbox {
            ::v-deep(.el-checkbox__label) {
              font-size: 12px;
              color: var(--el-text-color-secondary);
              line-height: 1.6;
            }

            .agreement-link {
              color: var(--el-color-primary);
              text-decoration: none;
              transition: color 0.2s;

              &:hover {
                text-decoration: underline;
                color: var(--el-color-primary-light-3);
              }
            }
          }
        }

        .submit-area {
          margin-top: 8px;

          .login-btn {
            width: 100%;
            padding: 14px 0;
            font-size: 16px;
            font-weight: 500;
            border-radius: 10px;
            background: linear-gradient(135deg, var(--el-color-primary), var(--el-color-primary-light-3));
            border: none;
            box-shadow: 0 4px 16px var(--el-color-primary-light-7);
            transition: all 0.2s ease;

            &:hover:not(:disabled) {
              transform: translateY(-1px);
              box-shadow: 0 6px 20px var(--el-color-primary-light-5);
            }

            &:active:not(:disabled) {
              transform: translateY(0);
            }

            &:disabled {
              opacity: 0.6;
            }
          }
        }
      }

      .login-methods {
        margin-top: 24px;
        width: 100%;

        .divider {
          display: flex;
          align-items: center;
          text-align: center;
          margin-bottom: 16px;

          &::before,
          &::after {
            content: '';
            flex: 1;
            height: 1px;
            background: var(--el-border-color);
          }

          span {
            padding: 0 12px;
            font-size: 12px;
            color: var(--el-text-color-secondary);
            white-space: nowrap;
          }
        }

        .tabs {
          display: flex;
          gap: 4px;
          margin-bottom: 16px;
          padding: 4px;
          background: var(--el-fill-color-light);
          border-radius: 10px;

          .tab-item {
            flex: 1;
            padding: 10px 16px;
            border-radius: 8px;
            border: none;
            background: transparent;
            font-size: 14px;
            font-weight: 500;
            color: var(--el-text-color-regular);
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;

            &:hover:not(.active) {
              color: var(--el-text-color-primary);
              background: var(--el-fill-color-lighter);
            }

            &.active {
              background: var(--el-bg-color);
              color: var(--el-color-primary);
              box-shadow: var(--kubo-shadow-sm);
            }
          }
        }

        .third-login {
          width: 100%;
        }
      }
    }
  }
}

@media screen and ((orientation: portrait) and (max-width: 768px)) {
  .login {
    padding: 20px;

    .card {
      width: 100% !important;
      max-width: 100% !important;
      position: static !important;
      padding: 32px 24px !important;
      border-radius: 16px !important;
      box-shadow: none !important;
      background: var(--el-bg-color) !important;
      backdrop-filter: none !important;
      border: none !important;

      .app-name {
        font-size: 24px;
      }

      .body {
        margin-top: 20px;

        .form-container {
          .form {
            ::v-deep(.el-input) {
              .el-input__inner {
                padding: 12px 14px;
                font-size: 16px; /* 防止 iOS 自动放大 */
              }
            }
          }

          .submit-area {
            .login-btn {
              padding: 16px 0;
              font-size: 16px;
            }
          }
        }

        .login-methods {
          .tabs {
            .tab-item {
              padding: 8px 12px;
              font-size: 13px;
            }
          }
        }
      }
    }
  }
}

@media screen and (max-width: 480px) {
  .login {
    .card {
      .body {
        .login-methods {
          .tabs {
            .tab-item:nth-child(4) {
              display: none;
            }
          }
        }
      }
    }
  }
}
</style>

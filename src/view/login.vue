<script lang="ts" setup>
import { RouterUtil, ValidateUtil } from '@airpower/web'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, ref } from 'vue'
import { authApi } from '@/api/system'
import Logo from '@/component/login/Logo.vue'
import ThirdLogin from '@/component/login/ThirdLogin.vue'
import { useRoutePreload } from '@/composables/useRoutePreload'
import { OpenAppService } from '@/hook/open/useApp'
import { UserService } from '@/hook/personnel/useUser'
import { LoginAction } from '@/model/common/LoginAction'
import { OpenAppEntity } from '@/model/open/app/OpenAppEntity'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const { preload } = useRoutePreload()

/**
 * ### 当前登录方式
 */
const currentAction = ref(LoginAction.LOGIN_VIA_PASSWORD)

/**
 * ### 是否已阅读协议
 */
const isAgreed = ref(true)

/**
 * ### 是否记住我
 */
const rememberMe = ref(true)

const user = ref({
  email: '',
  password: '',
  code: '',
  phone: '',
  appKey: '',
})

const appKey = (RouterUtil.router.currentRoute.value.query.appKey || '').toString()
const scope = (RouterUtil.router.currentRoute.value.query.scope || '').toString()
const redirectUri = (RouterUtil.router.currentRoute.value.query.redirectUri || '/console').toString()

const appInfo = ref(new OpenAppEntity())

// Loading 状态
const isLoadingApp = ref(false)
const isLoadingLogin = ref(false)
const isEmailCodeLoading = ref(false)
const isPhoneCodeLoading = ref(false)

// 表单验证
const isValidCode = computed(() => user.value.code && user.value.code.length === 6)
const isValidEmail = computed(() => user.value.email && ValidateUtil.isEmail(user.value.email))
const isValidPhone = computed(() => user.value.phone && ValidateUtil.isMobilePhone(user.value.phone))
const isValidAccount = computed(
  () => user.value.email && (ValidateUtil.isEmail(user.value.email) || ValidateUtil.isNaturalNumber(user.value.email)),
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
 * ### 获取当前应用信息
 */
async function getAppInfo() {
  if (appKey) {
    user.value.appKey = appKey
    appInfo.value = await OpenAppService.create(isLoadingApp).getAppByKey(appKey)
  }
}

/**
 * ### 处理登录重定向
 */
async function loginRedirect(token: string) {
  userStore.setToken(token)

  // 登录成功后预加载首页需要的 chunk，缩短进入控制台的等待时间
  preload([
    () => import('@/component/Console.vue'),
    () => import('@/view/home.vue'),
  ])

  if (appKey) {
    if (appInfo.value.isInternal) {
      const code = await UserService.create(isLoadingLogin).authorize(appKey, scope)
      window.location.replace(`${redirectUri}?code=${code}`)
      return
    }
    // OAuth 登录重定向
    window.location.replace(`/authorize${window.location.search}`)
    return
  }
  // 正常登录
  window.location.replace(redirectUri)
}

/**
 * ### 账号密码登录
 */
async function onLogin() {
  isLoadingLogin.value = true
  try {
    const token = await authApi.login({
      email: user.value.email,
      password: user.value.password,
    })
    await loginRedirect(token)
  }
  catch (error) {
    console.error('登录失败:', error)
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
  try {
    const token = await authApi.loginViaEmail({
      email: user.value.email,
      code: user.value.code,
    })
    await loginRedirect(token)
  }
  catch (error) {
    console.error('邮箱登录失败:', error)
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
  try {
    const token = await authApi.loginViaPhone({
      phone: user.value.phone,
      code: user.value.code,
    })
    await loginRedirect(token)
  }
  catch (error) {
    console.error('手机登录失败:', error)
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
    console.error('发送邮箱验证码失败:', error)
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
    console.error('发送短信验证码失败:', error)
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

getAppInfo()
</script>

<template>
  <div class="login">
    <Logo />
    <div
      v-loading="isLoadingApp"
      class="card"
    >
      <div class="app-name">
        {{ appInfo.appName || '欢迎登录 Kubo' }}
      </div>

      <div class="body">
        <!-- 二维码登录 -->
        <div
          v-if="currentAction === LoginAction.LOGIN_VIA_QRCODE"
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
                :class="{ 'is-error': !user.password }"
                autocomplete="current-password"
                @keyup.enter="onSubmit"
              />
              <div
                v-if="!user.password"
                class="form-error"
              >
                请输入密码
              </div>
            </div>

            <div class="form-options">
              <el-checkbox
                v-model="rememberMe"
                class="remember-me"
              >
                记住我（7天免登录）
              </el-checkbox>
              <a
                href="/forgot-password"
                class="forgot-password"
              >
                忘记密码？
              </a>
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

          <!-- 协议同意 -->
          <div class="agreement">
            <el-checkbox
              v-model="isAgreed"
              class="agreement-checkbox"
            >
              我已阅读并同意
              <a
                href="/privacy"
                target="_blank"
                class="agreement-link"
              >《隐私政策》</a>
              以及
              <a
                href="/terms"
                target="_blank"
                class="agreement-link"
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

        <!-- 登录方式切换 + 第三方登录 -->
        <div class="login-methods">
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

          <ThirdLogin />
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
  background: #f0f2f5 url(@/assets/img/login/bg.jpg) no-repeat bottom center;
  background-size: cover;
  align-items: center;
  justify-content: center;
  min-height: 100vh;

  .card {
    overflow: hidden;
    background: rgba(255, 255, 255, 0.95);
    box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
    backdrop-filter: blur(20px);
    border-radius: 16px;
    padding: 48px 56px;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 400px;
    position: absolute;
    right: 8%;
    border: 1px solid rgba(255, 255, 255, 0.3);

    .app-name {
      font-size: 28px;
      font-weight: 600;
      color: #1a1a2e;
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
          background: #fff;
          padding: 20px;
          border-radius: 12px;
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.06);

          img {
            width: 180px;
            height: 180px;
          }
        }

        .qrcode-desc {
          font-size: 15px;
          color: #333;
          margin-top: 16px;
          font-weight: 500;
        }

        .qrcode-hint {
          font-size: 13px;
          color: #999;
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
              color: #333;
              font-weight: 500;
              margin-bottom: 8px;
            }

            .form-error {
              position: absolute;
              bottom: -18px;
              left: 0;
              font-size: 12px;
              color: #f56c6c;
              height: 16px;
            }
          }

          ::v-deep(.el-input) {
            .el-input__wrapper {
              box-shadow: none;
              border-radius: 10px;
              border: 1px solid #e0e0e0;
              transition: all 0.2s ease;
              background: #fff;

              &:hover {
                border-color: #bdbdbd;
              }

              &.is-focus {
                border-color: var(--primary-color, #409eff);
                box-shadow: 0 0 0 3px rgba(64, 158, 255, 0.15);
              }

              &.is-error {
                border-color: #f56c6c;

                &:hover {
                  border-color: #f56c6c;
                }
              }
            }

            .el-input__inner {
              color: #1a1a2e;
              font-size: 14px;
              padding: 8px 12px;
              height: auto;
              background: transparent;

              &::placeholder {
                color: #bbb;
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

          .form-options {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin: 8px 0 16px;

            .remember-me {
              ::v-deep(.el-checkbox__label) {
                font-size: 13px;
                color: #666;
              }
            }

            .forgot-password {
              font-size: 13px;
              color: var(--primary-color, #409eff);
              text-decoration: none;
              transition: color 0.2s;

              &:hover {
                text-decoration: underline;
                color: #66b1ff;
              }
            }
          }
        }

        .agreement {
          margin: 8px 0 16px;

          .agreement-checkbox {
            ::v-deep(.el-checkbox__label) {
              font-size: 12px;
              color: #999;
              line-height: 1.6;
            }

            .agreement-link {
              color: var(--primary-color, #409eff);
              text-decoration: none;
              transition: color 0.2s;

              &:hover {
                text-decoration: underline;
                color: #66b1ff;
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
            background: linear-gradient(135deg, var(--primary-color, #409eff), #66b1ff);
            border: none;
            box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
            transition: all 0.2s ease;

            &:hover:not(:disabled) {
              transform: translateY(-1px);
              box-shadow: 0 6px 20px rgba(64, 158, 255, 0.4);
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
            background: #e0e0e0;
          }

          span {
            padding: 0 12px;
            font-size: 12px;
            color: #999;
            white-space: nowrap;
          }
        }

        .tabs {
          display: flex;
          gap: 4px;
          margin-bottom: 16px;
          padding: 4px;
          background: rgba(0, 0, 0, 0.03);
          border-radius: 10px;

          .tab-item {
            flex: 1;
            padding: 10px 16px;
            border-radius: 8px;
            border: none;
            background: transparent;
            font-size: 14px;
            font-weight: 500;
            color: #666;
            cursor: pointer;
            transition: all 0.2s ease;
            white-space: nowrap;

            &:hover:not(.active) {
              color: #333;
              background: rgba(255, 255, 255, 0.5);
            }

            &.active {
              background: #fff;
              color: var(--primary-color, #409eff);
              box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
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
      background: #fff !important;
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

<script lang="ts" setup>
import { WebConfig } from '@airpower/web'
import { onScopeDispose, ref, watch } from 'vue'
import { useAppRoute } from '@/router/appRouter'

const isLoading = ref(true)
const isError = ref(false)
const route = useAppRoute()

/**
 * ⚠️ 本页**不得**写 `WebConfig.isTimeout`。
 *
 * 那是 airpower 的全局熔断开关：`RouterUtil.initVueRouter()` 开头判断
 * `if (!WebConfig.isTimeout) registerRoute(...)` —— 一旦被置真，**动态菜单路由永久不再注册**
 * （谁都不会把它复位），此后点任何菜单都落到 catch-all 的 404 页。
 * 本页是 catch-all（404）兜底页，只要停留 `WebConfig.timeout`（5s）就会触发这个开关：
 * 「进错一次页面 → 整个控制台再也点不开」。错误页只该管自己的展示状态。
 */
const errorCode = ref(WebConfig.successCode)

const errorTitle = ref('')

const errorDesc = ref('')

const FORBIDDEN = 403
const NOT_FOUND = 404
const GATEWAY_TIMEOUT = 504
const INTERNAL_SERVER_ERROR = 500

const timer = setTimeout(() => {
  isLoading.value = false
  isError.value = true
  errorCode.value = GATEWAY_TIMEOUT
}, WebConfig.timeout)

// 组件卸载时清理超时计时器（本页只改变自己的展示状态）
onScopeDispose(() => clearTimeout(timer))

watch(errorCode, () => {
  switch (errorCode.value) {
    case FORBIDDEN:
      errorTitle.value = '很抱歉,你可能无权访问此服务'
      errorDesc.value = '请确认你的身份权限, '
      break
    case INTERNAL_SERVER_ERROR:
      errorTitle.value = '很抱歉,服务器发生了一点小错误'
      errorDesc.value = '服务器可能正在维护中, '
      isError.value = true
      break
    case NOT_FOUND:
      errorTitle.value = '很抱歉,没有找到你想访问的资源'
      errorDesc.value = '请确认你的访问地址是否正确, 或 '
      break
    case GATEWAY_TIMEOUT:
      errorTitle.value = '很抱歉,服务可能依然还没有正确的响应.'
      errorDesc.value = '建议尝试检查你的网络后刷新重试或 '
      break
    default:
  }
  if (errorCode.value !== WebConfig.successCode) {
    isLoading.value = false
    isError.value = true
  }
})

function checkErrorCode() {
  const code = route.path.replace('/', '')
  switch (code) {
    case FORBIDDEN.toString():
      clearTimeout(timer)
      errorCode.value = FORBIDDEN
      break
    case NOT_FOUND.toString():
      clearTimeout(timer)
      errorCode.value = NOT_FOUND
      break
    default:
      errorCode.value = WebConfig.successCode
  }
}

checkErrorCode()
</script>

<template>
  <div
    v-loading="isLoading"
    class="error-page big-loading"
  >
    <div
      v-if="errorCode !== WebConfig.successCode"
      class="box"
    >
      <div class="code">
        {{ errorCode }}
      </div>
      <div class="title">
        {{ errorTitle }}
      </div>
      <div class="description">
        {{ errorDesc }}
        <router-link to="/">
          {{ '返回首页' }}
        </router-link>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.error-page {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  /* V2：走 EP 页面底色变量，深浅色自动适配（不再硬编码 white） */
  background-color: var(--el-bg-color-page, #f5f7fa);

  .box {
    width: 1200px;
    max-width: 90vw;
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    margin-bottom: 100px;

    .code {
      color: var(--el-color-primary);
      font-size: 200px;
      font-weight: bold;
    }

    .title {
      font-size: 20px;
      color: var(--el-text-color-primary);
    }

    .description {
      font-size: 14px;
      color: var(--el-text-color-secondary);
      margin-top: 10px;

      a {
        color: var(--el-color-primary);
        text-decoration: none;
      }

      a:hover {
        text-decoration: underline;
        font-weight: bold;
      }
    }
  }
}
</style>

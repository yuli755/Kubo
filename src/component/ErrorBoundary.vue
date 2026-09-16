<script lang="ts" setup>
import { onErrorCaptured, ref, watch } from 'vue'
import { useAppRoute } from '@/router/appRouter'
import { reportError } from '@/utils/errorHandler'

/**
 * # 错误边界组件（全局错误兜底的「降级 UI」）
 *
 * `errorHandler.ts` 的 `installGlobalErrorHandler` 只负责**记录**错误、不阻止错误沿组件树传播——
 * 页面级渲染错误仍会让整页白掉。本组件用 `onErrorCaptured` 在路由出口这一层把错误**兜住**：
 * 出错的页面被替换成「出错提示 + 重试」，其余布局/导航不受影响，用户不至于面对一块白屏。
 *
 * ## 语义（为什么返回 false）
 * `onErrorCaptured` 返回 `false` 表示「已处理、停止传播」：
 * - 错误不再冒泡到 `app.config.errorHandler`（避免同一错误被全局兜底再记一次）；
 * - 因此这里**主动** `reportError(err, 'error-boundary')` 上报一次，闭环到后端
 *   `/api/error-report`（复用 `errorHandler` 的 reporter，生产已接到后端，见 main.ts）。
 *
 * ## 明确不做的事
 * - 不「吞错继续渲染坏页面」：出错子树整体替换为兜底 UI，宁可少一块也不展示半成品；
 * - 不自动重试（避免坏数据反复触发同一次错误、把用户淹没）；只提供「重试」按钮 + 路由切换复位。
 *
 * ## 路由切换自动复位
 * `watch(route.fullPath)`：切走再切回，给页面一次全新渲染机会（同页反复崩则再次兜底，不无限循环）。
 */
interface BoundaryError {
  message: string
  info?: string
}

const error = ref<BoundaryError | null>(null)

onErrorCaptured((err, _instance, info) => {
  error.value = {
    message: err instanceof Error ? err.message : String(err),
    info,
  }
  reportError(err, 'error-boundary')
  return false
})

const route = useAppRoute()
watch(
  () => route.fullPath,
  () => {
    error.value = null
  },
)

function retry(): void {
  error.value = null
}
</script>

<template>
  <div
    v-if="error"
    class="error-boundary"
    role="alert"
  >
    <div class="error-boundary__title">
      页面渲染出错
    </div>
    <div class="error-boundary__msg">
      {{ error.message }}
    </div>
    <div
      v-if="error.info"
      class="error-boundary__info"
    >
      {{ error.info }}
    </div>
    <button
      type="button"
      class="error-boundary__retry"
      @click="retry"
    >
      重试
    </button>
  </div>
  <slot v-else />
</template>

<style lang="scss" scoped>
.error-boundary {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 240px;
  padding: var(--kubo-spacing-6);
  text-align: center;

  &__title {
    font-size: var(--el-font-size-large);
    font-weight: 600;
    color: var(--el-text-color-primary);
  }

  &__msg {
    margin-top: var(--kubo-spacing-3);
    max-width: 480px;
    color: var(--el-text-color-regular);
    word-break: break-all;
  }

  &__info {
    margin-top: var(--kubo-spacing-2);
    font-size: var(--el-font-size-extra-small);
    color: var(--el-text-color-secondary);
  }

  &__retry {
    margin-top: var(--kubo-spacing-5);
    padding: var(--kubo-spacing-2) var(--kubo-spacing-5);
    border: none;
    border-radius: var(--el-border-radius-base);
    background: var(--el-color-primary);
    color: var(--el-color-white);
    cursor: pointer;

    &:hover {
      background: var(--el-color-primary-light-3);
    }
  }
}
</style>

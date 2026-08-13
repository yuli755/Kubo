/**
 * 全局错误处理
 *
 * 统一处理 API 错误、业务错误码、网络异常等。
 * 与 src/api/error-codes.ts 的错误码体系配合使用。
 *
 * @module api/errorHandler
 */
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { isAuthError, isPermissionError, isRetryableError } from './error-codes'

/** 错误处理选项 */
export interface ErrorHandlerOptions {
  /** 是否显示错误提示（默认 true） */
  showToast?: boolean
  /** 是否自动处理认证错误（默认 true） */
  handleAuth?: boolean
  /** 自定义错误处理回调 */
  onError?: (error: ApiError) => void
}

/** API 错误结构（与 axios.ts 对齐） */
export interface ApiError {
  code: number
  message: string
  data?: unknown
  timestamp: string
  path: string
}

/**
 * 处理 API 错误
 *
 * @param error - 错误对象
 * @param options - 处理选项
 */
export function handleApiError(error: ApiError, options: ErrorHandlerOptions = {}): void {
  const {
    showToast = true,
    handleAuth = true,
    onError,
  } = options

  // 认证错误处理
  if (handleAuth && isAuthError(error.code as never)) {
    const userStore = useUserStore()
    userStore.logout()
    window.location.href = '/login'
    return
  }

  // 权限错误处理
  if (isPermissionError(error.code as never)) {
    if (showToast) {
      ElMessage.error('没有权限执行此操作')
    }
    return
  }

  // 可重试错误（静默处理，由调用方决定是否重试）
  if (isRetryableError(error.code as never)) {
    if (showToast) {
      ElMessage.warning(error.message || '服务暂时不可用，请稍后重试')
    }
    return
  }

  // 通用错误提示
  if (showToast && error.message) {
    ElMessage.error(error.message)
  }

  // 自定义处理
  onError?.(error)
}

/**
 * 创建带默认配置的错误处理器
 */
export function createErrorHandler(defaultOptions: ErrorHandlerOptions = {}) {
  return (error: ApiError, options?: ErrorHandlerOptions) => {
    handleApiError(error, { ...defaultOptions, ...options })
  }
}

export default handleApiError

import type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { configureTransport } from '@/framework/transport'
import { useUserStore } from '@/stores/user'

// 链路追踪（可观测性 P1）：与后端 airpower 的 HttpConstant.Header.TRACE_ID 对齐 —— airpower 的
// CurdRequestInterceptor 读此头写入 MDC X-Trace-ID（logback 打印 TRACE_ID=...），CurdResponseInterceptor
// 按 bodyTraceId 把同一 traceId 回写到响应。前端生成并发送，即可把「一次用户操作」关联到后端日志。
// ⚠️ 头名字符串待与 airpower jar 的 HttpConstant 核对（发错头名不影响功能，只是链路不通）。
const TRACE_ID_HEADER = 'X-Trace-Id'

// 请求配置接口
export interface RequestConfig extends AxiosRequestConfig {
  /** 是否显示错误提示 */
  showError?: boolean
  /** 是否需要幂等键 */
  idempotent?: boolean
  /** 自定义错误处理 */
  customErrorHandler?: (error: ApiError) => void
  /** 是否附带当前租户意图；获取可选租户列表时应关闭 */
  tenantAware?: boolean
}

/** 上传请求的可选项 */
export interface IUploadRequestOption {
  /** 上传进度（0-100） */
  onProgress?: (progress: number) => void
  /** 随文件一起提交的表单字段（如后端要求的 `category`） */
  fields?: Record<string, string | number>
  /** 其他 axios 配置 */
  config?: RequestConfig
}

// API 错误类型
export interface ApiError {
  code: number
  message: string
  data?: unknown
  timestamp: string
  path: string
  /** 本次请求的 traceId（关联后端日志用） */
  traceId?: string
}

// 统一响应格式
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
}

// —— 阶段 E-M4：401 刷新单飞（single-flight）与会话终结兜底 ——
// 原实现对每个撞上 401 的请求各自发起刷新：页面并发 N 个请求 → N 次
// /auth/refreshToken 调用（刷新常伴随轮换/失效，后到的会连环报「刷新失败」）；
// 且刷新成功但重试仍 401 时直接落到 reject，用户「已登录」但处处失败，卡死在假会话里。
let refreshPromise: Promise<void> | null = null
let sessionExpiredHandling = false

function refreshAccessToken(): Promise<void> {
  if (!refreshPromise) {
    refreshPromise = useUserStore().refreshToken().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function handleSessionExpired(): Promise<void> {
  if (sessionExpiredHandling) {
    return
  }
  sessionExpiredHandling = true
  try {
    await useUserStore().logout()
  }
  finally {
    // replace 不留历史，避免「后退」回到过期页面
    window.location.replace('/login')
    // 浏览器里 replace 后本页即卸载，重置只为可测试/异常降级场景留口子
    sessionExpiredHandling = false
  }
}

// 创建 axios 实例
function createAxiosInstance(): AxiosInstance {
  const instance = axios.create({
    baseURL: '/api',
    timeout: 30000,
    headers: {
      'Content-Type': 'application/json',
    },
    withCredentials: true,
  })

  // 请求拦截器
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const userStore = useUserStore()

      // 添加 Token
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
      }

      // 添加租户切换意图；服务端仍会校验 user_tenant 成员关系。
      // 获取可选租户列表必须跳过旧的持久化值，否则旧租户已解绑时会在列表接口之前被 403。
      const tenantAware = (config as InternalAxiosRequestConfig & { tenantAware?: boolean }).tenantAware
      if (tenantAware !== false && userStore.tenantId !== undefined) {
        config.headers['X-Tenant-Id'] = userStore.tenantId.toString()
      }

      // 添加幂等键 (POST/PUT/PATCH 请求)
      // 后端 OpenApiConfig 已把它声明为 Idempotency-Key security scheme（外部 API 契约的一部分）。
      if (config.method && ['post', 'put', 'patch'].includes(config.method.toLowerCase())) {
        const idempotentKey = crypto.randomUUID()
        config.headers['Idempotency-Key'] = idempotentKey
      }

      // 请求时间戳：仅作服务端观测/审计预留。注意它**不构成防重放防护**——后端当前不校验此值，
      // 攻击者直接构造请求即可绕过；真正的防重放需后端配合（时间窗口校验 + 幂等去重），前端加 header 无防护意义。
      config.headers['X-Request-Time'] = Date.now().toString()

      // 链路追踪：生成 traceId 发头，后端读入 MDC 后可在日志里关联本次请求；
      // 同时挂到 config，供响应错误分支回填（网络错误无响应体，只能靠请求侧的值）。
      const traceId = crypto.randomUUID()
      config.headers[TRACE_ID_HEADER] = traceId
      ;(config as InternalAxiosRequestConfig & { traceId?: string }).traceId = traceId

      return config
    },
    (error) => {
      return Promise.reject(error)
    },
  )

  // 响应拦截器
  instance.interceptors.response.use(
    (response: AxiosResponse) => {
      const res = response.data

      // 文件下载等特殊响应直接返回
      if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
        return response
      }

      // 统一响应格式处理
      if (res && typeof res === 'object' && 'code' in res) {
        if (res.code === 200 || res.code === 0) {
          return res.data
        }

        // 业务错误码处理
        const error: ApiError = {
          code: res.code,
          message: res.message || '请求失败',
          data: res.data,
          timestamp: res.timestamp || new Date().toISOString(),
          path: response.config.url || '',
          // airpower bodyTraceId=true 时响应 body 带 traceId，回填以便关联后端日志
          traceId: (res as { traceId?: string }).traceId,
        }

        return Promise.reject(error)
      }

      return response.data
    },
    async (error: AxiosError<ApiError>) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean, traceId?: string }

      // 网络错误或超时
      if (!error.response) {
        const message = error.code === 'ECONNABORTED' ? '请求超时' : '网络连接失败'
        ElMessage.error(message)
        const networkError = new Error(message) as Error & { traceId?: string }
        // 网络错误无响应体，只能用请求侧生成的 traceId
        networkError.traceId = originalRequest?.traceId
        return Promise.reject(networkError)
      }

      const { status, data } = error.response

      // 处理特定状态码
      switch (status) {
        case 401: {
          // 认证接口自身（/user/login* /user/refresh /user/logout）的 401 是业务
          // 结果，不走刷新/登出流程：否则密码输错也会触发刷新 + 登出跳转，登录页变死循环。
          const url = originalRequest?.url || ''
          const isAuthEndpoint = ['/user/login', '/user/refresh', '/user/logout']
            .some(prefix => url.startsWith(prefix))
          if (isAuthEndpoint) {
            break
          }
          if (!originalRequest?._retry) {
            originalRequest._retry = true
            try {
              // 并发 401 共享同一次刷新；失败由 catch 统一收口
              await refreshAccessToken()
              // 重试原请求（请求拦截器会带上新 token）
              return instance(originalRequest)
            }
            catch {
              await handleSessionExpired()
            }
          }
          else {
            // 刷新并重试后仍 401：会话确凿失效，不能再把用户留在「看似已登录」的状态
            await handleSessionExpired()
          }
          break
        }

        case 403:
          ElMessage.error(data?.message || '没有权限访问')
          break

        case 404:
          ElMessage.error('请求的资源不存在')
          break

        case 422: {
          // 参数校验错误
          const validationMessage = data?.message || '参数校验失败'
          ElMessage.error(validationMessage)
          break
        }

        case 429:
          ElMessage.error('请求过于频繁，请稍后再试')
          break

        case 500:
          ElMessage.error(data?.message || '服务器内部错误')
          break

        default:
          ElMessage.error(data?.message || `请求失败 (${status})`)
      }

      return Promise.reject(Object.assign(new Error(data?.message || error.message), {
        code: status,
        data: data?.data,
        timestamp: data?.timestamp || new Date().toISOString(),
        path: originalRequest?.url || '',
        // 优先用后端回传的 traceId（响应 body/header），回退到请求侧生成的值
        traceId: data?.traceId || error.response?.headers?.[TRACE_ID_HEADER.toLowerCase()]
          || (originalRequest as InternalAxiosRequestConfig & { traceId?: string }).traceId,
      }))
    },
  )

  return instance
}

// 导出单例实例
export const request = createAxiosInstance()

// 响应拦截器已把统一响应格式解包为 `res.data`，因此把 axios 的响应类型收窄为数据类型的工具。
// （axios 1.20 起 `get/post/...` 默认返回 `Promise<AxiosResponse<T>>`，而本项目实际返回的是 data。）
function unwrapData<T>(promise: Promise<unknown>): Promise<T> {
  return promise as Promise<T>
}

// 封装常用请求方法
export const api = {
  get<T = unknown>(url: string, params?: Record<string, unknown>, config?: RequestConfig): Promise<T> {
    return unwrapData<T>(request.get(url, { params, ...config }))
  },

  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return unwrapData<T>(request.post(url, data, config))
  },

  put<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return unwrapData<T>(request.put(url, data, config))
  },

  patch<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return unwrapData<T>(request.patch(url, data, config))
  },

  delete<T = unknown>(url: string, params?: Record<string, unknown>, config?: RequestConfig): Promise<T> {
    return unwrapData<T>(request.delete(url, { params, ...config }))
  },

  /**
   * 文件上传（multipart）。
   *
   * `fields` 是**随文件一起提交的表单字段**（后端 `FileController.upload` 要 `category`），
   * 不传也能传文件，只是后端会按默认类别（`FileCategory.NORMAL`）落库。
   */
  upload<T = unknown>(url: string, file: File, option: IUploadRequestOption = {}): Promise<T> {
    const { onProgress, fields, config } = option
    const formData = new FormData()
    formData.append('file', file)
    for (const [key, value] of Object.entries(fields ?? {})) {
      formData.append(key, String(value))
    }

    return unwrapData<T>(request.post(url, formData, {
      ...config,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
        }
      },
    }))
  },

  // 文件下载
  download(url: string, params?: Record<string, unknown>, filename?: string): Promise<void> {
    return request.get(url, {
      params,
      responseType: 'blob',
    }).then((response: AxiosResponse) => {
      const blob = response.data as Blob
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = filename || 'download'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      window.URL.revokeObjectURL(url)
    })
  },
}

// 导出类型
export type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse }

// —— 传输层装配（框架侧只认契约，见 src/framework/transport.ts）——
// 框架的 HTTP / 上传都往这里走：凭据、租户头、401 刷新、幂等键、链路追踪、演示适配器
// 全在同一个实例上生效。以前是框架直接 import 本文件（层次反了，门禁里记成"例外"）；
// 现在是应用把实例注册给框架。**注意**：本文件必须被显式引入（`src/main.ts` 里那一行 +
// 启动自检），否则框架侧第一个请求会以"传输层未装配"直接抛错。
configureTransport({
  request: config => unwrapData(request(config as AxiosRequestConfig)),
  upload: (url, file, option) => api.upload(url, file, option),
})

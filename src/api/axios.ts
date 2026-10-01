import type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import {
  BusinessCode,
  classifyBusinessError,
  notificationText,
  notifyKey,
  shouldNotify,
} from '@/api/errorPolicy'
import { publishFieldError } from '@/stores/fieldError'
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

/**
 * 会话确凿失效的统一收口：清状态 + 带来源回登录页。
 *
 * 两个入口共用（此前只有第一个）：
 * 1. HTTP 401 且刷新失败/重试仍 401（见响应错误拦截器）；
 * 2. **业务码 401**（HTTP 200 + `code=401`）——后端三个 advice 全部
 *    `@ResponseStatus(HttpStatus.OK)`，鉴权失败（`Errors.UNAUTHORIZED`、
 *    `RequestInterceptor` 的「登录状态已失效」）都是这种形态，按 HTTP status
 *    分流的 switch 对它们是死分支。此前只 toast 不跳转：stale token 冷启动时
 *    Console 初始化首个请求就业务码 401，用户永远停在「控制台初始化失败」错卡上，
 *    「重试/返回首页」再点一百次也回不来（真机复现 2026-10-01，报告 §13/R-35）。
 *
 * 携带 `?redirect=` 是为了重新登录后能回到原页面（login.vue 的 safeRedirect 收口）。
 */
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
    const back = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
    window.location.replace(`/login?redirect=${back}`)
    // 浏览器里 replace 后本页即卸载，重置只为可测试/异常降级场景留口子
    sessionExpiredHandling = false
  }
}

/** 认证接口自身的 401 是业务结果，绝不触发刷新/登出（否则密码输错也登出，登录页死循环） */
function isAuthEndpoint(url: string | undefined): boolean {
  const path = url || ''
  return ['/user/login', '/user/refresh', '/user/logout'].some(prefix => path.startsWith(prefix))
}

/**
 * 业务错误（HTTP 200 + `code≠200`，以及网关直出的 403/422/500）的统一用户提示。
 *
 * ## 为什么必须有这一层
 * 后端三个 advice 全部 `@ResponseStatus(HttpStatus.OK)`，业务失败一律是 **HTTP 200 + 业务码**。
 * 因此按 HTTP status 分流的 `switch` 对它们是死分支，真正承接的是「code≠200」那一支 ——
 * 而那一支此前**只 reject 不提示**：用户点「保存」后界面毫无反应，只能开控制台才知道失败了。
 *
 * ## 三个让路的口子（避免双份提示）
 * 1. 调用方传了 `customErrorHandler` → 完全交给它；
 * 2. 调用方传了 `showError: false`（`new Http(url).hideError()`）→ 只 reject，不弹；
 * 3. 分类结果 `silent`（如登录失败 200001，登录页自己呈现）→ 不弹。
 *
 * ## 去重
 * 一个列表页常并发 3~5 个请求，权限没开通时它们**同时**失败；逐条弹窗就是 5 条同样的红条糊屏。
 * 同一「码 + 接口」在 {@link NOTIFY_DEDUPE_MS} 内只弹一次，但**字段定位信号仍照常发布**
 * （弹窗可以去重，把出错字段滚到视野内不该被去重）。
 */
function notifyBusinessError(
  error: ApiError,
  option: { showError?: boolean, customErrorHandler?: (error: ApiError) => void, authorizationKnown?: boolean } = {},
): void {
  if (option.customErrorHandler) {
    option.customErrorHandler(error)
    return
  }
  if (option.showError === false) {
    return
  }
  // authorizationKnown：HTTP 层直出的 403 没有业务文案，不能让"措辞判断"把它降级
  const verdict = classifyBusinessError({ ...error, authorizationKnown: option.authorizationKnown })
  if (verdict.silent) {
    return
  }
  publishFieldError(verdict.field, verdict.detail)
  if (!shouldNotify(notifyKey(verdict.code, error.path))) {
    return
  }
  ElMessage.error({
    message: notificationText(verdict),
    // 无权限要读完「找谁开通」，接口未开放要读完「重试没用、该找谁确认」，都给更长的停留时间；
    // 其余保持默认节奏
    duration: verdict.kind === 'forbidden' || verdict.kind === 'unsupported' ? 6000 : 4000,
    showClose: true,
  })
}

/** 把 HTTP 层错误包装成与业务错误同构的对象，好让两条路径共用一套文案与去重 */
function toApiError(status: number, data: ApiError | undefined, path: string, fallback: string): ApiError {
  return {
    code: status,
    message: data?.message || fallback,
    data: data?.data,
    timestamp: data?.timestamp || new Date().toISOString(),
    path,
    traceId: data?.traceId,
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

        // 用户提示与字段定位（此前这一支只 reject，用户看到的是「点了没反应」）
        notifyBusinessError(error, response.config as RequestConfig)

        // R-35：业务码 401（HTTP 200）同样代表会话失效 —— 后端鉴权失败全走这条形态，
        // 只 toast 不跳转会把用户锁死在假会话里（见 handleSessionExpired 注释）。
        // 与 HTTP 401 不同：后端没有给出「刷新」的机会，直接收口登出 + 回登录页。
        // 登录/登出/刷新接口自身的 401（如密码错误）是业务结果，不能触发登出跳转。
        if (res.code === BusinessCode.UNAUTHORIZED && !isAuthEndpoint(response.config.url)) {
          void handleSessionExpired()
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
          if (isAuthEndpoint(originalRequest?.url)) {
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

        case 403: {
          // HTTP 层直出的 403（网关 / Spring Security）没有业务码，但用户需要的指引与业务码 403 一样，
          // 所以复用同一套分类文案与去重，不再单独写一句「没有权限访问」
          const path = originalRequest?.url || ''
          notifyBusinessError(
            toApiError(BusinessCode.FORBIDDEN, data, path, '没有权限访问'),
            { ...(originalRequest as RequestConfig | undefined), authorizationKnown: true },
          )
          break
        }

        case 404:
          ElMessage.error('请求的资源不存在')
          break

        case 422: {
          // 参数校验错误：走业务码 4002 的同一套文案，顺带解析出字段名以便表单定位
          const path = originalRequest?.url || ''
          notifyBusinessError(
            toApiError(BusinessCode.PARAM_INVALID, data, path, '参数校验失败'),
            originalRequest as RequestConfig | undefined,
          )
          break
        }

        case 429:
          ElMessage.error('请求过于频繁，请稍后再试')
          break

        case 500: {
          const path = originalRequest?.url || ''
          notifyBusinessError(
            toApiError(BusinessCode.SERVICE_ERROR, data, path, '服务器内部错误'),
            originalRequest as RequestConfig | undefined,
          )
          break
        }

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

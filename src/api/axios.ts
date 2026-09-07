import type { AxiosError, AxiosInstance, AxiosRequestConfig, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

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

// API 错误类型
export interface ApiError {
  code: number
  message: string
  data?: unknown
  timestamp: string
  path: string
}

// 统一响应格式
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: string
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
      if (config.method && ['post', 'put', 'patch'].includes(config.method.toLowerCase())) {
        const idempotentKey = crypto.randomUUID()
        config.headers['Idempotency-Key'] = idempotentKey
      }

      // 添加请求时间戳 (用于防重放攻击)
      config.headers['X-Request-Time'] = Date.now().toString()

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
        }

        return Promise.reject(error)
      }

      return response.data
    },
    async (error: AxiosError<ApiError>) => {
      const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

      // 网络错误或超时
      if (!error.response) {
        const message = error.code === 'ECONNABORTED' ? '请求超时' : '网络连接失败'
        ElMessage.error(message)
        return Promise.reject(new Error(message))
      }

      const { status, data } = error.response

      // 处理特定状态码
      switch (status) {
        case 401:
          // Token 过期，尝试刷新
          if (!originalRequest._retry) {
            originalRequest._retry = true
            try {
              const userStore = useUserStore()
              await userStore.refreshToken()
              // 重试原请求
              return instance(originalRequest)
            }
            catch {
              // 刷新失败，跳转登录
              const userStore = useUserStore()
              userStore.logout()
              window.location.href = '/login'
            }
          }
          break

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
      }))
    },
  )

  return instance
}

// 导出单例实例
export const request = createAxiosInstance()

// 封装常用请求方法
export const api = {
  get<T = unknown>(url: string, params?: Record<string, unknown>, config?: RequestConfig): Promise<T> {
    return request.get(url, { params, ...config })
  },

  post<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.post(url, data, config)
  },

  put<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.put(url, data, config)
  },

  patch<T = unknown>(url: string, data?: unknown, config?: RequestConfig): Promise<T> {
    return request.patch(url, data, config)
  },

  delete<T = unknown>(url: string, params?: Record<string, unknown>, config?: RequestConfig): Promise<T> {
    return request.delete(url, { params, ...config })
  },

  // 文件上传
  upload<T = unknown>(url: string, file: File, onProgress?: (progress: number) => void, config?: RequestConfig): Promise<T> {
    const formData = new FormData()
    formData.append('file', file)

    return request.post(url, formData, {
      ...config,
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
        }
      },
    })
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

import { QueryClient } from '@tanstack/vue-query'

/**
 * # TanStack Query 全局配置
 *
 * 为 @tanstack/vue-query 提供统一的 QueryClient 实例。
 * Phase 2 将逐步把 airpower 的 API 调用迁移至此。
 */
export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000, // 30s 内认为数据新鲜，避免频繁重复请求
      retry: 1, // 失败重试 1 次
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 0,
    },
  },
})

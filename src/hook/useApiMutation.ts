import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { request } from '@/api/axios'

/**
 * # 通用 API 变更 Hook
 *
 * 封装 @tanstack/vue-query 的 useMutation，对接 `src/api/axios.ts` 的 request 实例。
 *
 * @example
 * ```ts
 * const { mutate, isLoading } = useApiMutation('/system/user')
 * mutate({ nickname: 'test' })
 * ```
 *
 * @param url - API 路径
 * @param method - HTTP 方法（默认 post）
 * @param invalidateKeys - 成功后需要失效的查询键列表
 */
export function useApiMutation<T, V = unknown>(
  url: string,
  method: 'post' | 'put' | 'patch' | 'delete' = 'post',
  invalidateKeys?: string[][],
) {
  const queryClient = useQueryClient()

  return useMutation<T, Error, V>({
    mutationFn: (data: V) => request[method](url, data) as Promise<T>,
    onSuccess: () => {
      if (invalidateKeys && invalidateKeys.length > 0) {
        invalidateKeys.forEach((key) => {
          queryClient.invalidateQueries({ queryKey: key })
        })
      }
    },
  })
}

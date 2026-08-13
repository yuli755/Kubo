import type { UseQueryOptions, UseQueryReturnType } from '@tanstack/vue-query'
import { useQuery } from '@tanstack/vue-query'
import { request } from '@/api/axios'

/**
 * # 通用 API 查询 Hook
 *
 * 封装 @tanstack/vue-query 的 useQuery，对接 `src/api/axios.ts` 的 request 实例。
 *
 * @example
 * ```ts
 * const { data, isLoading } = useApiQuery(['users', page], '/system/user/page', {
 *   params: { page: 1, pageSize: 20 },
 * })
 * ```
 *
 * @param key - 查询键（用于缓存标识）
 * @param url - API 路径
 * @param options - 附加选项（如 params、staleTime 等）
 */
export function useApiQuery<T>(
  key: string[],
  url: string,
  options?: Partial<UseQueryOptions<T>>,
): UseQueryReturnType<T, Error> {
  return useQuery<T>({
    queryKey: key,
    queryFn: () => request.get(url) as Promise<T>,
    ...options,
  }) as UseQueryReturnType<T, Error>
}

/**
 * # 带参数的 API 查询 Hook
 *
 * 与 `useApiQuery` 类似，但支持附加查询参数。
 *
 * @example
 * ```ts
 * const { data } = useApiQueryWithParams(['users', page], '/system/user/page', { page: 1, pageSize: 20 })
 * ```
 */
export function useApiQueryWithParams<T>(
  key: string[],
  url: string,
  params?: Record<string, unknown>,
  options?: Partial<UseQueryOptions<T>>,
): UseQueryReturnType<T, Error> {
  return useQuery<T>({
    queryKey: key,
    queryFn: () => request.get(url, { params }) as Promise<T>,
    ...options,
  }) as UseQueryReturnType<T, Error>
}

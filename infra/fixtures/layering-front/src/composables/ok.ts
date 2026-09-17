/**
 * 口径①：注释里的示例不算依赖 —— 这份文档正是希望大家这么用
 *
 * ```ts
 * import { preload } from '@/composables/ok'
 * preload(() => import('@/view/home.vue'))
 * ```
 */
export function preload(loader: () => Promise<unknown>) {
  return loader()
}

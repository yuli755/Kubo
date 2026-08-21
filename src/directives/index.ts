import type { Directive } from 'vue'
import { vMask } from './mask'
import { vPermission } from './permission'

/**
 * # 自定义指令统一入口
 *
 * 汇总所有自定义指令，供 main.ts 注册。
 *
 * @example
 * ```ts
 * import { directives } from '@/directives'
 * for (const [name, directive] of Object.entries(directives)) {
 *   app.directive(name, directive)
 * }
 * ```
 */
export const directives: Record<string, Directive> = {
  mask: vMask,
  permission: vPermission,
}

export { vMask } from './mask'
export { vPermission } from './permission'

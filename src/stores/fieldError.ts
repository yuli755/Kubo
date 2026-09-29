import { ref } from 'vue'

/**
 * # 出错字段的定位信号（跨组件的一次性信箱）
 *
 * ## 为什么需要一个共享 ref
 *
 * 后端把参数校验失败渲染成 **HTTP 200 + `code=4002`**，文案里带字段名
 * （`email: 邮箱格式不正确` 或 `缺少必要的请求参数「email」`）。
 * 这条消息在 `src/api/axios.ts` 的响应拦截器里被统一接住，但**弹提示的地方不知道表单在哪**：
 * 拦截器拿不到 `KFormDialog` 的 `formRef`，而表单也不该各自去解析后端文案
 * （否则每个页面都要抄一遍正则，且文案一改就全线失效）。
 *
 * 于是用「发布 / 订阅」解耦：拦截器解析出字段名后 `publishFieldError()`，
 * 当前打开的表单 `watch(fieldError)` 后调用 EP 的 `scrollToField()` 把出错项滚到视野内。
 *
 * ## 为什么不是 Pinia store
 *
 * 它只有一个瞬时信号、没有派生状态、不需要持久化，也不需要跨路由存活；
 * 用模块级 `ref` 足够，且能被测试直接 import（Pinia 还得先 `setActivePinia`）。
 * 与 `src/stores/persist.ts` 的取舍同理：**状态该放哪一层，取决于它要不要活着穿过路由**。
 *
 * ## 契约
 *
 * - 一次失败只发布一次；消费方读完必须 `clearFieldError()`，否则下次打开表单会被旧信号误触发；
 * - `at` 是发布时间戳，消费方可用它判断信号是否已过期（例如表单在 5 秒后才打开，就不该再跳焦点）。
 */

/** 一条待处理的字段定位信号 */
interface FieldErrorNotice {
  /** 后端字段名，与 `ElFormItem` 的 `prop`（即实体属性名）同源 */
  field: string
  /** 后端给的原始文案，供消费方决定要不要就地显示 */
  message: string
  /** 发布时间（`Date.now()`） */
  at: number
}

/** 当前待处理的字段定位信号；`null` 表示没有 */
export const fieldError = ref<FieldErrorNotice | null>(null)

/**
 * 发布一条字段定位信号。
 *
 * @param field 字段名（空值直接忽略：没有字段名就没什么可定位的）
 * @param message 后端文案
 */
export function publishFieldError(field: string | undefined, message: string): void {
  if (!field) {
    return
  }
  fieldError.value = { field, message, at: Date.now() }
}

/** 清空信号（消费方处理完、或表单关闭时调用） */
export function clearFieldError(): void {
  fieldError.value = null
}

import type { PiniaPluginContext, StateTree } from 'pinia'
import { parse, stringify } from 'flatted'

/**
 * # Pinia 持久化插件
 *
 * 为声明了 `persist` 配置的 store 提供 localStorage / sessionStorage 持久化。
 *
 * ## 使用方式
 * ```ts
 * // main.ts
 * const pinia = createPinia()
 * pinia.use(createPersistPlugin())
 * ```
 *
 * ## store 配置
 * ```ts
 * export const useUserStore = defineStore('user', () => {
 *   // ...
 * }, {
 *   persist: {
 *     key: 'kubo:user',
 *     storage: 'local',          // 'local' | 'session'
 *     pick: ['token', 'userInfo'], // 只持久化指定字段
 *   },
 * })
 * ```
 */
/**
 * Pinia 模块增强：为 `DefineStoreOptionsBase` 添加 `persist` 选项，
 * 使 `defineStore` 的 setup store 和 options store 都能接受 `persist` 配置。
 */
declare module 'pinia' {
  export interface DefineStoreOptionsBase<S extends StateTree, Store> {
    /** 持久化配置（由 persist 插件消费） */
    persist?: PersistOptions
  }
}

export interface PersistOptions {
  /** 存储 key，默认自动从 $id 生成 */
  key?: string
  /** 存储介质 */
  storage?: 'local' | 'session'
  /** 仅持久化这些字段（不填则持久化全部） */
  pick?: string[]
  /** 自定义序列化 */
  serializer?: {
    serialize: (state: StateTree) => string
    deserialize: (raw: string) => StateTree
  }
}

export function createPersistPlugin(options: PersistOptions = {}) {
  return (context: PiniaPluginContext): void => {
    const { store, options: storeOptions } = context
    const persistConfig = storeOptions.persist as PersistOptions | undefined

    // 未声明 persist 的 store 直接跳过
    if (!persistConfig) {
      return
    }

    const merged = { ...options, ...persistConfig }
    const key = merged.key ?? `kubo:${store.$id}`
    const storage = merged.storage === 'session' ? sessionStorage : localStorage
    const serializer = merged.serializer ?? { serialize: stringify, deserialize: parse }

    // ── 恢复 ──
    try {
      const raw = storage.getItem(key)
      if (raw) {
        const state = serializer.deserialize(raw)
        store.$patch(state)
      }
    }
    catch {
      // 反序列化失败时静默忽略，保持默认状态
    }

    // ── 订阅变化 ──
    store.$subscribe(
      (_mutation, state) => {
        try {
          let snapshot = state as StateTree
          if (merged.pick && merged.pick.length > 0) {
            snapshot = {} as StateTree
            for (const field of merged.pick) {
              if (field in state) {
                snapshot[field] = state[field as keyof typeof state]
              }
            }
          }
          storage.setItem(key, serializer.serialize(snapshot))
        }
        catch {
          // 序列化失败时静默忽略（如存储配额不足）
        }
      },
      { detached: true },
    )
  }
}

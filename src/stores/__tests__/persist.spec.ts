// @vitest-environment jsdom
import type { PiniaPluginContext } from 'pinia'
import type { PersistOptions } from '@/stores/persist'
import { parse as flattedParse, stringify as flattedStringify } from 'flatted'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPersistPlugin } from '@/stores/persist'

/**
 * `createPersistPlugin` 契约测试。
 *
 * 锁的是持久化插件这条「刷新不丢状态」基础设施的不变量：
 * - 未声明 `persist` 的 store 不挂订阅（不产生多余写入）；
 * - 恢复 / 落盘走同一个 serializer（默认 flatted），反序列化失败静默回退默认状态；
 * - `pick` 只持久化白名单字段（避免把一次性敏感字段写进 localStorage）；
 * - 序列化失败（如配额不足）静默忽略，不打断业务。
 *
 * 注意：默认 serializer 是 flatted（引用压缩格式），**不是** JSON，
 * 因此断言一律用 `flatted.parse` / `flatted.stringify`，不要用 `JSON.*`。
 */

function makeStore(overrides: Partial<{
  id: string
  state: Record<string, unknown>
  persist: PersistOptions
}> = {}) {
  const state = overrides.state ?? { token: 'abc', userInfo: { nickname: 'Hamm' } }
  const store = {
    $id: overrides.id ?? 'user',
    $patch: vi.fn((patch: Record<string, unknown>) => Object.assign(state, patch)),
    $subscribe: vi.fn(),
  }
  const context = {
    store,
    options: { persist: overrides.persist },
  } as unknown as PiniaPluginContext
  return { context, store, state }
}

beforeEach(() => {
  localStorage.clear()
  sessionStorage.clear()
})

describe('createPersistPlugin · 持久化不变量', () => {
  it('未声明 persist 的 store 不挂订阅、不写入', () => {
    const { context, store } = makeStore({ persist: undefined })
    createPersistPlugin()(context)
    expect(store.$subscribe).not.toHaveBeenCalled()
    expect(localStorage.length).toBe(0)
  })

  it('从 localStorage 恢复已持久化的状态', () => {
    localStorage.setItem('kubo:user', flattedStringify({ token: 'restored-token' }))
    const { context, state } = makeStore({ persist: { key: 'kubo:user', storage: 'local' } })
    createPersistPlugin()(context)
    expect(state.token).toBe('restored-token')
  })

  it('状态变化时序列化落盘', () => {
    const { context, store } = makeStore({ persist: { key: 'kubo:user', storage: 'local' } })
    createPersistPlugin()(context)
    const subscribeCb = (store.$subscribe as ReturnType<typeof vi.fn>).mock.calls[0][0]
    subscribeCb({}, { token: 'new-token' })
    const persisted = flattedParse(localStorage.getItem('kubo:user') || '[]') as Record<string, unknown>
    expect(persisted.token).toBe('new-token')
  })

  it('pick 白名单只持久化指定字段', () => {
    const { context, store } = makeStore({
      persist: { key: 'kubo:user', storage: 'local', pick: ['token'] },
    })
    createPersistPlugin()(context)
    const subscribeCb = (store.$subscribe as ReturnType<typeof vi.fn>).mock.calls[0][0]
    subscribeCb({}, { token: 't', userInfo: { nickname: 'secret' } })
    const persisted = flattedParse(localStorage.getItem('kubo:user') || '[]') as Record<string, unknown>
    expect(persisted.token).toBe('t')
    expect(persisted.userInfo).toBeUndefined()
  })

  it('session storage 选择生效', () => {
    const { context, store } = makeStore({ persist: { key: 'kubo:user', storage: 'session' } })
    createPersistPlugin()(context)
    const subscribeCb = (store.$subscribe as ReturnType<typeof vi.fn>).mock.calls[0][0]
    subscribeCb({}, { token: 's' })
    expect(localStorage.getItem('kubo:user')).toBeNull()
    const persisted = flattedParse(sessionStorage.getItem('kubo:user') || '[]') as Record<string, unknown>
    expect(persisted.token).toBe('s')
  })

  it('反序列化失败静默回退，不抛异常', () => {
    localStorage.setItem('kubo:user', '{bad json')
    const { context, state } = makeStore({ persist: { key: 'kubo:user', storage: 'local' } })
    expect(() => createPersistPlugin()(context)).not.toThrow()
    expect(state.token).toBe('abc')
  })

  it('自定义 serializer 被采用', () => {
    const serialize = vi.fn(() => 'custom')
    const deserialize = vi.fn(() => ({ token: 'custom-restored' }))
    localStorage.setItem('kubo:user', 'whatever')
    const { context, state } = makeStore({
      persist: { key: 'kubo:user', storage: 'local', serializer: { serialize, deserialize } },
    })
    createPersistPlugin()(context)
    expect(deserialize).toHaveBeenCalled()
    expect(state.token).toBe('custom-restored')
  })

  it('默认 key 由 store.$id 生成', () => {
    const { context, store } = makeStore({ id: 'theme', persist: { storage: 'local' } })
    createPersistPlugin()(context)
    const subscribeCb = (store.$subscribe as ReturnType<typeof vi.fn>).mock.calls[0][0]
    subscribeCb({}, { a: 1 })
    expect(localStorage.getItem('kubo:theme')).toBeTruthy()
  })
})

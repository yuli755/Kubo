// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 跨标签页登出同步（R-38）契约。
 *
 * 锁的不变量：
 * - 别的标签页把 `Authorization` 清掉（登出）→ 本标签页清空内存会话、
 *   replace 回 `/login`，带 `reason=signedOut` 与来源 `redirect`；
 * - **绝不**再打一次后端登出（对端已经登出过，重复请求是噪音）；
 * - 别的标签页登录（newValue 有值）、其它键、本标签页本来就没登录
 *   —— 一律无动作，不抢占当前页。
 *
 * jsdom 的 localStorage 不会自动派发 `storage` 事件（真实浏览器也只发给
 * 其它标签页），所以测试里手动 `dispatchEvent(new StorageEvent(...))` 模拟对端。
 */

const authApi = {
  logout: vi.fn(),
  refreshToken: vi.fn(),
}

vi.mock('@/api/system', () => ({
  authApi,
}))

const permissionClear = vi.fn()

vi.mock('@/stores/permission', () => ({
  usePermissionStore: () => ({ clear: permissionClear }),
}))

const locationReplace = vi.fn()
const location = {
  replace: locationReplace,
  href: 'http://localhost:3000/',
  pathname: '/console/wms/input/list',
  search: '',
}

Object.defineProperty(window, 'location', {
  configurable: true,
  writable: true,
  value: location,
})

const { useUserStore } = await import('@/stores/user')
const { installAuthSync, signedOutReasonHint } = await import('@/utils/authSync')

/**
 * `storage` 监听挂在 window 上、跨用例存活，必须逐个注销，
 * 否则先装上的监听会用例间串台（回调里取的是当时激活的 pinia store）。
 */
const disposers: (() => void)[] = []

function setup() {
  disposers.push(installAuthSync())
}

/** 模拟「另一个标签页登出」：Authorization 被清空 */
function emitPeerLogout() {
  window.dispatchEvent(new StorageEvent('storage', {
    key: 'Authorization',
    oldValue: 'peer-token',
    newValue: null,
  }))
}

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  location.pathname = '/console/wms/input/list'
  location.search = ''
})

afterEach(() => {
  for (const dispose of disposers.splice(0)) {
    dispose()
  }
})

describe('authSync · 跨标签页登出同步（R-38）', () => {
  it('对端登出 → 清空本标签页会话并 replace 回登录页（带 reason 与 redirect）', () => {
    const store = useUserStore()
    store.setToken('t-live')
    localStorage.setItem('kubo:user', '{}')
    location.search = '?page=2'
    setup()

    emitPeerLogout()

    expect(store.token).toBe('')
    expect(store.isLoggedIn()).toBe(false)
    expect(localStorage.getItem('Authorization')).toBeNull()
    expect(localStorage.getItem('kubo:user')).toBeNull()
    expect(locationReplace).toHaveBeenCalledWith(
      '/login?reason=signedOut&redirect=%2Fconsole%2Fwms%2Finput%2Flist%3Fpage%3D2',
    )
    // 对端已经调用过 /user/logout，本标签页绝不重复登出
    expect(authApi.logout).not.toHaveBeenCalled()
  })

  it('对端登录（newValue 有值）→ 无动作，不抢占当前页', () => {
    const store = useUserStore()
    store.setToken('t-live')
    setup()

    window.dispatchEvent(new StorageEvent('storage', {
      key: 'Authorization',
      oldValue: null,
      newValue: 't-other-tab',
    }))

    expect(store.token).toBe('t-live')
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('其它键的变化（主题/权限缓存…）→ 与登录态无关，无动作', () => {
    const store = useUserStore()
    store.setToken('t-live')
    setup()

    window.dispatchEvent(new StorageEvent('storage', {
      key: 'permissions',
      newValue: null,
    }))

    expect(store.token).toBe('t-live')
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('本标签页本来就没登录 → 无动作（登录页上对端登出也安静）', () => {
    useUserStore()
    location.pathname = '/login'
    setup()

    emitPeerLogout()

    expect(locationReplace).not.toHaveBeenCalled()
    expect(authApi.logout).not.toHaveBeenCalled()
  })

  it('在登录页但内存里还有会话 → 清状态但不 replace（already at destination）', () => {
    const store = useUserStore()
    store.setToken('t-live')
    location.pathname = '/login'
    setup()

    emitPeerLogout()

    expect(store.token).toBe('')
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('dispose 之后的对端登出不受影响（注销函数摘掉监听）', () => {
    const store = useUserStore()
    store.setToken('t-live')
    const dispose = installAuthSync()
    disposers.push(dispose)
    dispose()

    emitPeerLogout()

    expect(store.token).toBe('t-live')
    expect(locationReplace).not.toHaveBeenCalled()
  })

  it('clearLocalSession 清空会话但不请求后端（同步专用收口）', async () => {
    const store = useUserStore()
    store.setToken('t')
    store.setTenantId(9)

    await store.clearLocalSession()

    expect(store.token).toBe('')
    expect(store.tenantId).toBeUndefined()
    expect(localStorage.getItem('Authorization')).toBeNull()
    expect(authApi.logout).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(permissionClear).toHaveBeenCalled())
  })

  it('logout 走后端登出 + 同一套本地收口', async () => {
    const store = useUserStore()
    store.setToken('t')
    authApi.logout.mockResolvedValue(undefined)

    await store.logout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(store.token).toBe('')
    expect(localStorage.getItem('Authorization')).toBeNull()
  })
})

describe('authSync · 登录页 reason 文案映射', () => {
  it('signedOut → 给出来源说明', () => {
    expect(signedOutReasonHint('signedOut')).toContain('退出登录')
  })

  it('认不出的 reason 不编造原因', () => {
    expect(signedOutReasonHint('whatever')).toBeUndefined()
    expect(signedOutReasonHint(undefined)).toBeUndefined()
  })
})

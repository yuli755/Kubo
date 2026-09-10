// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `useUserStore` 契约测试。
 *
 * 锁的是登录态这条主线的不变量：
 * - token 与 airpower `WebConfig` 双向同步（框架与自研请求拦截器都能读到同一份）；
 * - 租户列表只接受后端下发、且过滤非法 id（不自行拼接）；
 * - logout 无论后端成败都清空本地状态、摘 token、断开 WS、清权限。
 */

const webConfig = {
  getAccessToken: vi.fn(() => ''),
  saveAccessToken: vi.fn(),
  removeAccessToken: vi.fn(),
}

vi.mock('@airpower/web', () => ({
  WebConfig: webConfig,
}))

const authApi = {
  logout: vi.fn(),
  refreshToken: vi.fn(),
}

vi.mock('@/api/system', () => ({
  authApi,
}))

const webSocketManager = {
  disconnect: vi.fn(),
}

vi.mock('@/websocket/WebSocketManager', () => ({
  webSocketManager,
}))

const permissionClear = vi.fn()

vi.mock('@/stores/permission', () => ({
  usePermissionStore: () => ({ clear: permissionClear }),
}))

const { useUserStore } = await import('@/stores/user')

beforeEach(() => {
  localStorage.clear()
  setActivePinia(createPinia())
  webConfig.getAccessToken.mockReturnValue('')
  vi.clearAllMocks()
})

describe('useUserStore · 登录态不变量', () => {
  it('setToken 同步写入 WebConfig', () => {
    const store = useUserStore()
    store.setToken('t-123')
    expect(store.token).toBe('t-123')
    expect(webConfig.saveAccessToken).toHaveBeenCalledWith('t-123')
  })

  it('初始化从 WebConfig 恢复 token（刷新不丢）', () => {
    webConfig.getAccessToken.mockReturnValue('restored')
    const store = useUserStore()
    expect(store.token).toBe('restored')
    expect(store.isLoggedIn()).toBe(true)
  })

  it('setTenantList 过滤非法 id（0 / 负数 / 非整数）', () => {
    const store = useUserStore()
    store.setTenantList([
      { id: 1, code: 'a', name: 'A', isDefault: false },
      { id: 0, code: 'b', name: 'B', isDefault: false },
      { id: -2, code: 'c', name: 'C', isDefault: false },
      { id: 1.5, code: 'd', name: 'D', isDefault: false },
    ])
    expect(store.tenantList).toHaveLength(1)
    expect(store.tenantList[0].id).toBe(1)
  })

  it('setTenantList 无选中时优先 isDefault 租户', () => {
    const store = useUserStore()
    store.setTenantList([
      { id: 10, code: 'x', name: 'X', isDefault: false },
      { id: 20, code: 'y', name: 'Y', isDefault: true },
    ])
    expect(store.tenantId).toBe(20)
  })

  it('setTenantList 已选租户仍在列表时保持不变', () => {
    const store = useUserStore()
    store.setTenantId(20)
    store.setTenantList([
      { id: 10, code: 'x', name: 'X', isDefault: false },
      { id: 20, code: 'y', name: 'Y', isDefault: false },
    ])
    expect(store.tenantId).toBe(20)
  })

  it('logout 清空本地状态、摘 token、断 WS、清权限', async () => {
    const store = useUserStore()
    store.setToken('t')
    store.setTenantId(9)
    store.setUserInfo({ nickname: 'Hamm' })
    authApi.logout.mockResolvedValue(undefined)

    await store.logout()

    expect(store.token).toBe('')
    expect(store.tenantId).toBeUndefined()
    expect(store.userInfo).toBeNull()
    expect(webConfig.removeAccessToken).toHaveBeenCalled()
    expect(localStorage.getItem('kubo:user')).toBeNull()
    // 异步副作用
    await vi.waitFor(() => expect(webSocketManager.disconnect).toHaveBeenCalled())
    await vi.waitFor(() => expect(permissionClear).toHaveBeenCalled())
  })

  it('logout 后端失败仍清空本地状态', async () => {
    const store = useUserStore()
    store.setToken('t')
    authApi.logout.mockRejectedValue(new Error('network down'))

    await store.logout()

    expect(store.token).toBe('')
    expect(webConfig.removeAccessToken).toHaveBeenCalled()
  })

  it('无 refreshToken 时 refreshToken 抛错', async () => {
    const store = useUserStore()
    await expect(store.refreshToken()).rejects.toThrow('No refresh token available')
  })
})

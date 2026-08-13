/**
 * Token 刷新逻辑
 *
 * 使用双 Token 机制（Access Token + Refresh Token）实现静默刷新。
 * 当 Access Token 过期时，自动使用 Refresh Token 获取新的 Access Token。
 *
 * @module api/tokenRefresh
 */
import { useUserStore } from '@/stores/user'

/** 刷新 Token 的 API 路径 */
const REFRESH_TOKEN_URL = '/api/auth/refresh'

/** 并发刷新请求锁 */
let refreshPromise: Promise<boolean> | null = null

/**
 * 刷新 Token
 *
 * 使用 Refresh Token 换取新的 Access Token。
 * 同一时刻只允许一个刷新请求进行（锁机制防止并发刷新生效）。
 *
 * @returns 是否刷新成功
 */
export async function handleTokenExpiry(): Promise<boolean> {
  const userStore = useUserStore()

  // 没有 Refresh Token 则无法刷新
  if (!userStore.refreshTokenValue) {
    userStore.logout()
    return false
  }

  // 防止并发刷新
  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = (async () => {
    try {
      const response = await fetch(REFRESH_TOKEN_URL, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${userStore.refreshTokenValue}`,
        },
        body: JSON.stringify({}),
      })

      if (!response.ok) {
        throw new Error('Token refresh failed')
      }

      const data = await response.json()
      userStore.setToken(data.accessToken || data.token)

      if (data.refreshToken) {
        userStore.setRefreshToken(data.refreshToken)
      }

      return true
    }
    catch {
      userStore.logout()
      window.location.href = '/login'
      return false
    }
    finally {
      refreshPromise = null
    }
  })()

  return refreshPromise
}

export default handleTokenExpiry

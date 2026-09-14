// @vitest-environment jsdom
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * `useThemeStore` 三态（light/dark/system）契约测试。
 *
 * 锁的是深色模式这条 V2 主线的核心不变量：
 * - 「是否暗色」是**派生值**，只有一份真相（system 跟随 `prefers-color-scheme`），组件不再各自读 matchMedia；
 * - 切换偏好必须同步挂/摘 `html.dark` 类（Element Plus 暗色变量与 tokens.scss 都以它为开关）；
 * - 偏好持久化，刷新后不丢。
 */

// 模块顶层会调用 window.matchMedia，必须在 import 前 stub
const mediaQueryList = {
  matches: false,
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
}
vi.stubGlobal('matchMedia', vi.fn(() => mediaQueryList))

const { useThemeStore } = await import('@/stores/theme')

describe('useThemeStore · 深色模式三态', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.classList.remove('dark')
    setActivePinia(createPinia())
    mediaQueryList.matches = false
  })

  it('默认 system，系统浅色时 isDark=false 且不挂 dark 类', () => {
    const store = useThemeStore()
    expect(store.preference).toBe('system')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('切到 dark 时挂 dark 类并持久化到 localStorage', () => {
    const store = useThemeStore()
    store.setPreference('dark')
    expect(store.preference).toBe('dark')
    expect(store.isDark).toBe(true)
    expect(document.documentElement.classList.contains('dark')).toBe(true)
    expect(localStorage.getItem('kubo:theme')).toBe('dark')
  })

  it('切到 light 时摘掉 dark 类', () => {
    const store = useThemeStore()
    store.setPreference('dark')
    store.setPreference('light')
    expect(store.isDark).toBe(false)
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })

  it('cycle 三态循环：light → dark → system → light', () => {
    const store = useThemeStore()
    store.setPreference('light')
    expect(store.cycle()).toBe('dark')
    expect(store.cycle()).toBe('system')
    expect(store.cycle()).toBe('light')
  })

  it('system 模式下跟随系统暗色偏好', () => {
    mediaQueryList.matches = true
    const store = useThemeStore()
    expect(store.preference).toBe('system')
    expect(store.isDark).toBe(true)
  })

  it('持久化的 dark 偏好在初始化时即生效', () => {
    localStorage.setItem('kubo:theme', 'dark')
    const store = useThemeStore()
    expect(store.preference).toBe('dark')
    expect(store.isDark).toBe(true)
  })

  it('非法持久化值回落到 system', () => {
    localStorage.setItem('kubo:theme', 'bogus')
    const store = useThemeStore()
    expect(store.preference).toBe('system')
  })
})

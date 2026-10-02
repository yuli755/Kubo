import { defineStore } from 'pinia'
import { ref } from 'vue'
import { safeGetItem, safeSetItem } from '@/utils/storage'

/**
 * # 主题 Store（V2 深色模式）
 *
 * 三态：`light` / `dark` / `system`（跟随系统 `prefers-color-scheme`）。
 *
 * ## 为什么把「是否暗色」的最终判定也放这里
 * `system` 模式下实际生效的暗色取决于系统，而系统偏好可能随时变化；组件若各自去读
 * `matchMedia` 就会多出若干份「当前是不是暗色」的真相。这里统一维护 `isDark` 并在
 * `system` 模式下监听变化同步刷新，组件只消费 `isDark`。
 *
 * ## 与 DOM 的关系
 * `applyTheme()` 负责在 `<html>` 根节点上切换 `dark` 类（Element Plus 暗色变量、
 * `tokens.scss` 的 `html.dark` 覆盖都以它为开关）。persist 插件只持久化「偏好」，
 * 「是否暗色」是派生值，不重复持久化。
 */
export type ThemePreference = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'kubo:theme'
const DARK_CLASS = 'dark'

/** 系统是否偏好暗色（在模块加载时确定一次，后续由监听更新） */
const systemDarkQuery = window.matchMedia('(prefers-color-scheme: dark)')

function readPreference(): ThemePreference {
  const raw = safeGetItem(STORAGE_KEY)
  if (raw === 'light' || raw === 'dark' || raw === 'system') {
    return raw
  }
  return 'system'
}

function resolveDark(preference: ThemePreference): boolean {
  return preference === 'dark' || (preference === 'system' && systemDarkQuery.matches)
}

function applyDarkClass(dark: boolean): void {
  document.documentElement.classList.toggle(DARK_CLASS, dark)
}

export const useThemeStore = defineStore('theme', () => {
  const preference = ref<ThemePreference>(readPreference())
  const isDark = ref<boolean>(resolveDark(preference.value))

  /** 把当前偏好与「是否暗色」同步到 DOM（`html.dark`） */
  function applyTheme(): void {
    isDark.value = resolveDark(preference.value)
    applyDarkClass(isDark.value)
    // 同步 PWA manifest 主题色，让系统状态栏/标题栏与界面一致
    syncThemeColorMeta()
  }

  /** 切换偏好；`system` 下监听系统偏好变化，保持派生值一致 */
  function setPreference(next: ThemePreference): void {
    preference.value = next
    // 隐私模式配额为 0：主题只是偏好，存不下也要能当场切换
    safeSetItem(STORAGE_KEY, next)
    applyTheme()
  }

  /** 三态循环：light → dark → system → light（供紧凑的切换按钮使用） */
  function cycle(): ThemePreference {
    const order: ThemePreference[] = ['light', 'dark', 'system']
    const next = order[(order.indexOf(preference.value) + 1) % order.length]
    setPreference(next)
    return next
  }

  function syncThemeColorMeta(): void {
    const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
    if (meta) {
      meta.content = isDark.value ? '#141414' : '#ffffff'
    }
  }

  // system 模式下跟随系统偏好实时变化
  systemDarkQuery.addEventListener('change', () => {
    if (preference.value === 'system') {
      applyTheme()
    }
  })

  // 模块首次被使用时应用一次（由 main.ts 或 Console 调用，保证挂载前 `html.dark` 已就位）
  applyTheme()

  return {
    preference,
    isDark,
    setPreference,
    cycle,
    applyTheme,
  }
})

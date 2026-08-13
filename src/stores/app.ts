/**
 * # 应用配置 Store
 *
 * 管理应用级配置：主题、语言、布局、侧边栏状态等。
 * 持久化策略：localStorage + 部分字段 sessionStorage。
 *
 * @module stores/app
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface AppConfig {
  /** 主题: light | dark */
  theme: 'light' | 'dark'
  /** 语言 */
  locale: string
  /** 侧边栏是否折叠 */
  sidebarCollapsed: boolean
  /** 是否显示标签栏 */
  showTabs: boolean
  /** 主题色 */
  primaryColor: string
}

export const useAppStore = defineStore('app', () => {
  const theme = ref<'light' | 'dark'>('light')
  const locale = ref('zh-CN')
  const sidebarCollapsed = ref(false)
  const showTabs = ref(true)
  const primaryColor = ref('#409eff')

  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
    document.documentElement.setAttribute('data-theme', theme.value)
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setLocale(lang: string) {
    locale.value = lang
  }

  function setPrimaryColor(color: string) {
    primaryColor.value = color
    document.documentElement.style.setProperty('--primary-color', color)
  }

  return {
    theme,
    locale,
    sidebarCollapsed,
    showTabs,
    primaryColor,
    toggleTheme,
    toggleSidebar,
    setLocale,
    setPrimaryColor,
  }
}, {
  persist: {
    key: 'kubo:app',
    storage: 'local',
    pick: ['theme', 'locale', 'sidebarCollapsed', 'primaryColor'],
  },
})

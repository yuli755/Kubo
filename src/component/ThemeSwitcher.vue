<script lang="ts" setup>
import { Monitor, Moon, Sunny } from '@element-plus/icons-vue'
import { computed } from 'vue'
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

const LABEL: Record<string, string> = {
  light: '浅色',
  dark: '深色',
  system: '跟随系统',
}

const icon = computed(() => {
  if (themeStore.preference === 'light') {
    return Sunny
  }
  if (themeStore.preference === 'dark') {
    return Moon
  }
  return Monitor
})

const tooltip = computed(() => `主题：${LABEL[themeStore.preference]}（点击切换）`)
</script>

<template>
  <el-tooltip
    :content="tooltip"
    placement="bottom"
    effect="dark"
  >
    <button
      class="theme-switcher"
      type="button"
      aria-label="切换主题"
      @click="themeStore.cycle()"
    >
      <el-icon :size="16">
        <component :is="icon" />
      </el-icon>
    </button>
  </el-tooltip>
</template>

<style lang="scss" scoped>
.theme-switcher {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: var(--kubo-radius-sm);
  background: transparent;
  color: inherit;
  cursor: pointer;
  transition:
    background-color var(--kubo-duration-fast) var(--kubo-ease-out),
    transform var(--kubo-duration-fast) var(--kubo-ease-out);

  &:hover {
    // 通用中性 hover 轨（灰 15% 叠加）：两种主题下灰半透明都成立，故不随主题翻转
    background: var(--kubo-bg-hover-neutral);
  }

  &:active {
    transform: scale(0.92);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: 2px;
  }
}
</style>

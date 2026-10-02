<script lang="ts" setup>
import type { MenuItem } from '@/router/appRouter'
import {
  DataAnalysis,
  Expand,
  Grid,
  HomeFilled,
  Moon,
  Right,
  Search,
  Sunny,
} from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { computed, nextTick, ref, watch } from 'vue'
import { getRouter } from '@/router/appRouter'
import { useThemeStore } from '@/stores/theme'

interface ICommandItem {
  id: string
  title: string
  category: string
  path?: string
  icon?: unknown
  action?: () => void
  breadcrumb?: string
}

const props = defineProps<{
  modelValue: boolean
  menus?: MenuItem[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'toggleCollapse'): void
}>()

const keyword = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement>()
const themeStore = useThemeStore()

function close() {
  emit('update:modelValue', false)
}

/** 递归展平所有可访问的叶子菜单 */
function flattenMenus(items: MenuItem[], parents: string[] = []): ICommandItem[] {
  const result: ICommandItem[] = []
  for (const item of items) {
    const currentBreadcrumb = [...parents, item.name]
    if (item.path && (!item.children || item.children.length === 0)) {
      result.push({
        id: `menu:${item.path}`,
        title: item.name,
        category: '系统导航',
        path: item.path,
        breadcrumb: currentBreadcrumb.join(' / '),
      })
    }
    if (item.children?.length) {
      result.push(...flattenMenus(item.children, currentBreadcrumb))
    }
  }
  return result
}

/** 静态快捷系统操作项 */
const systemActions = computed<ICommandItem[]>(() => [
  {
    id: 'act:home',
    title: '工作台首页',
    category: '快捷操作',
    path: '/console/index',
    icon: HomeFilled,
    breadcrumb: '工作台 / 概览',
  },
  {
    id: 'act:security',
    title: '安全态势看板',
    category: '快捷操作',
    path: '/console/index',
    icon: DataAnalysis,
    breadcrumb: '工作台 / 数据权限治理',
  },
  {
    id: 'act:heatmap',
    title: '库位热力态势',
    category: '快捷操作',
    path: '/console/wms/inventory/list?view=heatmap',
    icon: Grid,
    breadcrumb: '仓储管理 / 实时库存 / 库位态势',
  },
  {
    id: 'act:theme',
    title: themeStore.isDark ? '切换至浅色模式' : '切换至深色模式',
    category: '系统设置',
    icon: themeStore.isDark ? Sunny : Moon,
    action: () => themeStore.cycle(),
    breadcrumb: '外观偏好',
  },
  {
    id: 'act:collapse',
    title: '展开 / 收起侧边栏',
    category: '系统设置',
    icon: Expand,
    action: () => emit('toggleCollapse'),
    breadcrumb: '工作区布局',
  },
])

const allItems = computed<ICommandItem[]>(() => {
  const menuCommands = flattenMenus(props.menus ?? [])
  return [...systemActions.value, ...menuCommands]
})

/** 模糊匹配与关键字加权检索 */
const filteredItems = computed<ICommandItem[]>(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return allItems.value.slice(0, 15)
  }
  return allItems.value.filter((item) => {
    return (
      item.title.toLowerCase().includes(kw)
      || (item.path && item.path.toLowerCase().includes(kw))
      || (item.breadcrumb && item.breadcrumb.toLowerCase().includes(kw))
    )
  }).slice(0, 20)
})

watch(
  () => props.modelValue,
  async (visible) => {
    if (visible) {
      keyword.value = ''
      activeIndex.value = 0
      await nextTick()
      inputRef.value?.focus()
    }
  },
)

watch(filteredItems, () => {
  activeIndex.value = 0
})

function executeItem(item: ICommandItem) {
  if (item.action) {
    item.action()
  }
  else if (item.path) {
    try {
      getRouter().push(item.path)
    }
    catch {
      window.location.hash = item.path
    }
  }
  close()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (filteredItems.value.length > 0) {
      activeIndex.value = (activeIndex.value + 1) % filteredItems.value.length
    }
  }
  else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (filteredItems.value.length > 0) {
      activeIndex.value = (activeIndex.value - 1 + filteredItems.value.length) % filteredItems.value.length
    }
  }
  else if (e.key === 'Enter') {
    e.preventDefault()
    const selected = filteredItems.value[activeIndex.value]
    if (selected) {
      executeItem(selected)
    }
  }
  else if (e.key === 'Escape') {
    close()
  }
}
</script>

<template>
  <Transition name="palette-fade">
    <div
      v-if="modelValue"
      class="k-command-palette-mask"
      role="dialog"
      aria-modal="true"
      @click.self="close"
    >
      <div class="k-command-palette-card">
        <!-- 搜索输入条 -->
        <div class="k-command-palette__search">
          <ElIcon class="search-icon">
            <Search />
          </ElIcon>
          <input
            ref="inputRef"
            v-model="keyword"
            type="text"
            class="search-input"
            placeholder="搜索菜单、快速跳转页面或执行命令..."
            @keydown="onKeydown"
          >
          <kbd
            class="esc-badge font-mono"
            @click="close"
          >ESC</kbd>
        </div>

        <!-- 结果列表 -->
        <div class="k-command-palette__list">
          <div
            v-if="filteredItems.length === 0"
            class="k-command-palette__empty"
          >
            没有找到与 “{{ keyword }}” 相关的菜单或命令
          </div>
          <div
            v-for="(item, idx) in filteredItems"
            :key="item.id"
            class="k-command-palette__item"
            :class="{ 'is-active': idx === activeIndex }"
            @mouseenter="activeIndex = idx"
            @click="executeItem(item)"
          >
            <div class="item-main">
              <span class="item-title">{{ item.title }}</span>
              <span
                v-if="item.breadcrumb"
                class="item-breadcrumb"
              >{{ item.breadcrumb }}</span>
            </div>
            <div class="item-meta">
              <span class="item-tag">{{ item.category }}</span>
              <ElIcon class="item-arrow">
                <Right />
              </ElIcon>
            </div>
          </div>
        </div>

        <!-- 底部快捷按键提示 -->
        <div class="k-command-palette__footer">
          <span class="hint-item"><kbd>↑</kbd><kbd>↓</kbd> 切换选项</span>
          <span class="hint-item"><kbd>↵</kbd> 确认跳转</span>
          <span class="hint-item"><kbd>Esc</kbd> 退出</span>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.k-command-palette-mask {
  position: fixed;
  inset: 0;
  z-index: 2500;
  background: var(--kubo-scrim);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 14vh;
}

.k-command-palette-card {
  width: 580px;
  max-width: 90vw;
  background: var(--el-bg-color-overlay, #ffffff);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  border-radius: 12px;
  box-shadow: var(--kubo-shadow-overlay);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.k-command-palette__search {
  display: flex;
  align-items: center;
  padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter, #f1f5f9);
  gap: 12px;
}

.search-icon {
  font-size: 18px;
  color: var(--el-text-color-secondary, #94a3b8);
}

.search-input {
  flex: 1;
  border: none;
  background: transparent;
  outline: none;
  font-size: 15px;
  color: var(--el-text-color-primary, #1e293b);
}

.search-input::placeholder {
  color: var(--el-text-color-placeholder, #94a3b8);
}

.esc-badge {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color-light, #f1f5f9);
  border: 1px solid var(--el-border-color-lighter, #cbd5e1);
  color: var(--el-text-color-secondary, #64748b);
  cursor: pointer;
}

.k-command-palette__list {
  max-height: 360px;
  overflow-y: auto;
  padding: 8px;
}

.k-command-palette__empty {
  padding: 32px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-secondary, #94a3b8);
}

.k-command-palette__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-radius: 8px;
  cursor: pointer;
  transition: all var(--kubo-duration-fast, 120ms) ease;
}

.k-command-palette__item.is-active {
  background: var(--el-fill-color-light, #f1f5f9);
}

html.dark .k-command-palette__item.is-active {
  background: var(--el-color-primary-light-9, rgba(64, 158, 255, 0.15));
}

.item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary, #1e293b);
}

.item-breadcrumb {
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.item-tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-fill-color, #f8fafc);
  color: var(--el-text-color-secondary, #64748b);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
}

.item-arrow {
  font-size: 12px;
  color: var(--el-text-color-placeholder, #cbd5e1);
  opacity: 0;
  transition: opacity 0.15s ease;
}

.k-command-palette__item.is-active .item-arrow {
  opacity: 1;
  color: var(--el-color-primary, #409eff);
}

.k-command-palette__footer {
  padding: 8px 18px;
  border-top: 1px solid var(--el-border-color-lighter, #f1f5f9);
  background: var(--el-fill-color-light, #f8fafc);
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary, #64748b);
}

.hint-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.hint-item kbd {
  display: inline-block;
  padding: 1px 4px;
  border-radius: 3px;
  background: var(--el-bg-color, #ffffff);
  border: 1px solid var(--el-border-color, #cbd5e1);
  font-size: 11px;
  font-family: var(--kubo-font-mono);
}

.palette-fade-enter-active,
.palette-fade-leave-active {
  transition: opacity 0.18s cubic-bezier(0.22, 1, 0.36, 1);
}

.palette-fade-enter-from,
.palette-fade-leave-to {
  opacity: 0;
}
</style>

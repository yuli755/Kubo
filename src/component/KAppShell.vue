<script lang="ts" setup>
import type { RouteMeta } from 'vue-router'
import type { UserEntity } from '@/model/personnel/user/UserEntity'
/**
 * # KAppShell · 自研控制台外壳（替代 airpower `AFrame`）
 *
 * ## 为什么要有它
 * 页面观感由「壳 + 表格 + 卡片 + 表单」四层决定，而壳是第一眼。此前壳是 `AFrame`
 * （airpower 默认皮肤），底层无论怎么重构，视觉都不会变。本组件用 Element Plus + 设计
 * token 重做壳层，**业务页零改动**即可全站换观感。
 *
 * ## 视觉方向：A · 现代企业风
 * - 浅色侧栏 + 细分隔线 + 圆角高亮（Linear / Vercel 气质），大留白、克制强调色；
 * - 侧栏可折叠（状态本地持久化），窄屏自动切抽屉；
 * - 顶栏承载面包屑与操作区（租户/主题/用户），内容区统一容器宽度与内边距；
 * - 全部颜色走 `--kubo-*` token，深色模式自动跟随（`html.dark`）。
 *
 * ## 关于旧壳
 * 改版期曾用 `VITE_LEGACY_SHELL` 切回 airpower `AFrame` 做 A/B 并排评审；
 * 批 0 已把旧壳分支与开关一起删除（`Console.vue` 只剩这一个外壳），
 * 因此本文件现在是控制台唯一的壳（见 `SPMS-Server/docs/前端F4·airpower 逐域迁移分批清单.md`）。
 */
import type { MenuItem } from '@/router/appRouter'
import { Expand, Fold, Search, Warning } from '@element-plus/icons-vue'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import KCommandPalette from '@/component/framework/KCommandPalette.vue'
import TenantSwitcher from '@/component/TenantSwitcher.vue'
import ThemeSwitcher from '@/component/ThemeSwitcher.vue'
import UserCard from '@/component/UserCard.vue'
import { isDemoRuntime } from '@/config/runtimeFlags'
import { FeedbackUtil } from '@/framework'
import { getRouter, useAppRoute } from '@/router/appRouter'
import { safeGetItem, safeSetItem } from '@/utils/storage'
import { resolveMenuIcon } from './shell/icons'
import KAppMenuNode from './shell/KAppMenuNode.vue'

const props = withDefaults(defineProps<{
  /** 后端下发的菜单树 */
  menuList: MenuItem[]
  /** 当前登录用户（顶栏用户卡使用） */
  user: UserEntity
  /** 顶栏高度（与旧壳保持一致的入参，默认 56） */
  headerHeight?: number
  /** 内容是否使用统一容器宽度（表单/表格页建议开启） */
  bordered?: boolean
}>(), {
  headerHeight: 56,
  bordered: false,
})

const route = useAppRoute()
const router = getRouter()
const collapsed = ref(safeGetItem('kubo:shell:collapsed') === 'true')
const isNarrow = ref(false)
const drawerVisible = ref(false)
const paletteVisible = ref(false)

function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    paletteVisible.value = !paletteVisible.value
  }
}

/** 按 orderNo 稳定排序的菜单树（后端通常已排好序，这里兜底，避免顺序随数据漂移） */
const menuTree = computed<MenuItem[]>(() => sortMenus(props.menuList ?? []))

function sortMenus(items: MenuItem[]): MenuItem[] {
  return [...items]
    .sort((a, b) => (a.orderNo ?? 0) - (b.orderNo ?? 0))
    .map(item => (item.children?.length ? { ...item, children: sortMenus(item.children) } : { ...item }))
}

/** 展平菜单树，便于做「当前路由 → 菜单项 / 面包屑」的反查 */
interface FlatMenu {
  item: MenuItem
  parents: MenuItem[]
}

const flatMenus = computed<FlatMenu[]>(() => {
  const result: FlatMenu[] = []
  const walk = (items: MenuItem[], parents: MenuItem[]) => {
    for (const item of items) {
      result.push({ item, parents })
      if (item.children?.length) {
        walk(item.children, [...parents, item])
      }
    }
  }
  walk(menuTree.value, [])
  return result
})

/** 归一化路径：去掉首尾斜杠，便于前缀比较 */
function normalize(path?: string): string {
  return (path ?? '').replace(/^\/+|\/+$/g, '')
}

/**
 * 当前激活的菜单项：取「路径匹配且最长」的那个。
 * 用最长匹配而非相等比较，才能让 `/console/system/datapermission/resource` 命中子菜单
 * 而不是外层分组。
 */
const activeMenu = computed<FlatMenu | undefined>(() => {
  const current = normalize(route.path)
  return flatMenus.value
    .filter(({ item }) => item.path && (current === normalize(item.path) || current.startsWith(`${normalize(item.path)}/`)))
    .sort((a, b) => normalize(b.item.path).length - normalize(a.item.path).length)[0]
})

const activeIndex = computed(() => activeMenu.value?.item.path ?? '')

/** 面包屑：分组名 + 当前页名 */
const breadcrumbs = computed<MenuItem[]>(() => {
  const active = activeMenu.value
  if (!active) {
    return []
  }
  return [...active.parents, active.item]
})

const pageTitle = computed(() => {
  const meta = route.meta as RouteMeta | undefined
  return (meta?.name as string) || activeMenu.value?.item.name || '控制台'
})

function persistCollapsed() {
  // 折叠态是便利数据，存不下（隐私模式/配额）只是下次恢复默认，不该炸掉点击
  safeSetItem('kubo:shell:collapsed', String(collapsed.value))
}

function toggleCollapse() {
  collapsed.value = !collapsed.value
  persistCollapsed()
}

function onMenuSelect(path: string) {
  if (!path || normalize(path) === normalize(route.path)) {
    drawerVisible.value = false
    return
  }
  router.push(path)
  drawerVisible.value = false
}

function syncViewport() {
  isNarrow.value = window.innerWidth < 1024
  if (!isNarrow.value) {
    drawerVisible.value = false
  }
}

const isOffline = ref(typeof navigator !== 'undefined' ? !navigator.onLine : false)

function updateOnlineStatus() {
  const current = typeof navigator !== 'undefined' ? !navigator.onLine : false
  if (isOffline.value && !current) {
    FeedbackUtil.toastSuccess('网络连接已恢复')
  }
  isOffline.value = current
}

watch(() => route.fullPath, () => {
  drawerVisible.value = false
})

onMounted(() => {
  syncViewport()
  window.addEventListener('resize', syncViewport)
  window.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('online', updateOnlineStatus)
  window.addEventListener('offline', updateOnlineStatus)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', syncViewport)
  window.removeEventListener('keydown', onGlobalKeydown)
  window.removeEventListener('online', updateOnlineStatus)
  window.removeEventListener('offline', updateOnlineStatus)
})
</script>

<template>
  <div
    class="k-shell"
    :class="{ 'k-shell--collapsed': collapsed && !isNarrow, 'k-shell--narrow': isNarrow }"
  >
    <!-- 侧栏：宽屏常驻 / 窄屏抽屉 -->
    <aside
      v-show="!isNarrow"
      class="k-shell__sider"
      aria-label="主导航"
    >
      <div class="k-sider__brand">
        <img
          alt="Kubo"
          class="k-sider__logo"
          src="@/assets/img/logo.svg"
        >
        <span
          v-if="!collapsed"
          class="k-sider__title"
        >
          Kubo 智方
        </span>
      </div>

      <el-scrollbar class="k-sider__scroll">
        <el-menu
          class="k-menu"
          :collapse="collapsed"
          :collapse-transition="false"
          :default-active="activeIndex"
          :unique-opened="true"
          @select="onMenuSelect"
        >
          <KAppMenuNode
            v-for="item in menuTree"
            :key="item.id"
            :item="item"
          />
        </el-menu>
      </el-scrollbar>

      <button
        class="k-sider__collapse"
        type="button"
        :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
        @click="toggleCollapse"
      >
        <el-icon>
          <component :is="collapsed ? Expand : Fold" />
        </el-icon>
        <span v-if="!collapsed">收起侧栏</span>
      </button>
    </aside>

    <el-drawer
      v-if="isNarrow"
      v-model="drawerVisible"
      :with-header="false"
      class="k-shell__drawer"
      direction="ltr"
      size="260px"
    >
      <div class="k-sider__brand">
        <img
          alt="Kubo"
          class="k-sider__logo"
          src="@/assets/img/logo.svg"
        >
        <span class="k-sider__title">Kubo 智方</span>
      </div>
      <el-scrollbar class="k-sider__scroll">
        <el-menu
          class="k-menu"
          :default-active="activeIndex"
          :unique-opened="true"
          @select="onMenuSelect"
        >
          <KAppMenuNode
            v-for="item in menuTree"
            :key="item.id"
            :item="item"
          />
        </el-menu>
      </el-scrollbar>
    </el-drawer>

    <div class="k-shell__main">
      <Transition name="fade-slide">
        <div
          v-if="isOffline"
          class="k-shell__offline-banner"
          role="alert"
        >
          <el-icon class="banner-icon">
            <Warning />
          </el-icon>
          <span>当前网络已断开，系统已切换至离线只读模式，请检查网络连接以避免数据保存失败。</span>
        </div>
      </Transition>

      <header
        class="k-shell__topbar"
        :style="{ minHeight: `${headerHeight}px` }"
      >
        <div class="k-topbar__left">
          <button
            v-if="isNarrow"
            class="k-topbar__icon-btn"
            type="button"
            aria-label="打开菜单"
            @click="drawerVisible = true"
          >
            <el-icon><Expand /></el-icon>
          </button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(crumb, index) in breadcrumbs"
              :key="crumb.id"
              :class="{ 'is-current': index === breadcrumbs.length - 1 }"
            >
              <el-icon
                v-if="index === 0"
                class="k-crumb__icon"
              >
                <component :is="resolveMenuIcon(crumb.icon)" />
              </el-icon>
              {{ crumb.name }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="k-topbar__right">
          <button
            class="k-topbar__search-trigger"
            type="button"
            title="全局命令与导航 (Ctrl + K)"
            @click="paletteVisible = true"
          >
            <el-icon><Search /></el-icon>
            <span class="placeholder">快速导航...</span>
            <kbd class="shortcut font-mono">⌘K</kbd>
          </button>
          <el-tag
            v-if="isOffline"
            class="k-offline-tag"
            size="small"
            type="danger"
            effect="plain"
          >
            离线
          </el-tag>
          <el-tag
            v-if="isDemoRuntime()"
            class="k-demo-tag"
            size="small"
            type="warning"
            effect="light"
          >
            演示数据
          </el-tag>
          <TenantSwitcher />
          <ThemeSwitcher />
          <UserCard :user="user" />
        </div>
      </header>

      <main class="k-shell__content">
        <div
          class="k-content__inner"
          :class="{ 'k-content__inner--plain': bordered }"
        >
          <slot />
        </div>
      </main>

      <footer class="k-shell__footer">
        <span>{{ pageTitle }}</span>
        <span class="k-footer__meta">
          Kubo · 智能生产管理系统
        </span>
      </footer>
    </div>

    <KCommandPalette
      v-model="paletteVisible"
      :menus="menuList"
      @toggle-collapse="toggleCollapse"
    />
  </div>
</template>

<style lang="scss" scoped>
/* ── 布局骨架 ── */
.k-shell {
  display: flex;
  height: 100vh;
  /* dvh = 移动端动态视口（浏览器地址栏收起/展开后变化）：写死 100vh 在
   * 手机浏览器上会高估可见高度，页脚被顶到 URL 栏底下看不见也滚不到
   * （测试报告 §9「内容丢失」根因之一）。dvh 不支持时自动落回 100vh。 */
  height: 100dvh;
  overflow: hidden;
  background: var(--kubo-color-bg-page);
  color: var(--kubo-color-text-primary);
}

.k-shell__sider {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  width: var(--kubo-sider-width);
  background: var(--kubo-color-bg-sider);
  border-right: 1px solid var(--kubo-color-border-subtle);
  transition: width var(--kubo-duration-base) var(--kubo-ease-out);
}

.k-shell--collapsed .k-shell__sider {
  width: var(--kubo-sider-width-collapsed);
}

.k-shell__main {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
}

.k-shell__offline-banner {
  background: var(--el-color-warning-light-9);
  color: var(--el-color-warning-dark-2);
  border-bottom: 1px solid var(--el-color-warning-light-5);
  padding: 8px 20px;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 500;
  z-index: 100;

  .banner-icon {
    font-size: 16px;
    color: var(--el-color-warning);
    flex-shrink: 0;
  }
}

/* ── 品牌区 ── */
.k-sider__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--kubo-header-height);
  padding: 0 16px;
  border-bottom: 1px solid var(--kubo-sider-border);
  flex: 0 0 auto;
}

.k-sider__logo {
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
}

.k-sider__title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2px;
  color: var(--kubo-sider-text-active);
  white-space: nowrap;
}

.k-sider__scroll {
  flex: 1 1 auto;
  padding: 8px 8px 0;
}

.k-sider__collapse {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 40px;
  margin: 8px;
  border: 1px solid var(--kubo-sider-border);
  border-radius: var(--kubo-radius-sm);
  background: transparent;
  color: var(--kubo-sider-text);
  font-size: 12px;
  cursor: pointer;
  transition:
    color var(--kubo-duration-fast) var(--kubo-ease-out),
    border-color var(--kubo-duration-fast) var(--kubo-ease-out),
    background-color var(--kubo-duration-fast) var(--kubo-ease-out);

  &:hover {
    color: var(--kubo-sider-text-active);
    border-color: transparent;
    background: var(--kubo-sider-bg-hover);
  }
}

/* ── 侧栏菜单（方向 B：深色底 + 实心主色块标记当前页）──
 *
 * 配色优先走 EP 自己的菜单变量（`--el-menu-*`），而不是层层 `:deep()` 穿透：
 * EP 的菜单项/子菜单标题都从这些变量取值，改一处即可覆盖整棵树的常态/悬停/激活三态。
 */
.k-menu {
  border-right: 0;
  background: transparent;

  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--kubo-sider-text);
  --el-menu-hover-bg-color: var(--kubo-sider-bg-hover);
  /* 深色底上 EP 默认的「悬停变主色」几乎看不清，改为提亮文字 */
  --el-menu-hover-text-color: var(--kubo-sider-text-active);
  --el-menu-active-color: var(--kubo-sider-text-active);
  --el-menu-item-height: 40px;
  --el-menu-sub-item-height: 36px;
  --el-menu-base-level-padding: 14px;
}

.k-menu :deep(.el-menu-item),
.k-menu :deep(.el-sub-menu__title) {
  margin: 2px 8px;
  border-radius: var(--kubo-radius-sm);
  transition:
    background-color var(--kubo-duration-fast) var(--kubo-ease-out),
    color var(--kubo-duration-fast) var(--kubo-ease-out);
}

/* 当前页：实心主色块。数据密集型界面里用色块定位「我在哪」，比文字加粗更快扫到 */
.k-menu :deep(.el-menu-item.is-active) {
  background: var(--kubo-sider-bg-active);
  color: var(--kubo-sider-text-active);
  font-weight: 600;
}

/* 展开中的父级：只提亮文字、不给色块，避免与叶子项抢注意力 */
.k-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: var(--kubo-sider-text-active);
}

/* 图标与文字间距：收起态下 EP 靠图标居中，此时不能再加右边距 */
.k-menu:not(.el-menu--collapse) :deep(.el-menu-item .el-icon),
.k-menu:not(.el-menu--collapse) :deep(.el-sub-menu__title .el-icon) {
  margin-right: 10px;
}

/* ── 顶栏 ── */
.k-shell__topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 0 20px;
  background: var(--kubo-color-bg-elevated);
  border-bottom: 1px solid var(--kubo-color-border-subtle);
  flex: 0 0 auto;
}

.k-topbar__left,
.k-topbar__right {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

/* 窄屏挤压时牺牲面包屑（左），绝不裁功能簇（右）：375 屏实测右簇曾被裁出
 * 30px 不可达（测试报告 §9）。左簇允许收缩并裁剪，右簇不参与收缩。 */
.k-topbar__left {
  flex: 0 1 auto;
  overflow: hidden;
}

.k-topbar__right {
  flex: 0 0 auto;
}

.k-topbar__search-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 10px;
  border-radius: var(--kubo-radius-sm, 6px);
  background: var(--el-fill-color-light, #f1f5f9);
  border: 1px solid var(--el-border-color-lighter, #e2e8f0);
  color: var(--el-text-color-secondary, #64748b);
  cursor: pointer;
  transition: all var(--kubo-duration-fast, 120ms) ease;

  &:hover {
    background: var(--el-fill-color, #e2e8f0);
    border-color: var(--el-border-color, #cbd5e1);
    color: var(--el-text-color-primary, #1e293b);
  }

  .placeholder {
    font-size: 12px;
  }

  .shortcut {
    font-size: 11px;
    padding: 1px 4px;
    border-radius: 3px;
    background: var(--el-bg-color, #ffffff);
    border: 1px solid var(--el-border-color-lighter, #cbd5e1);
  }
}

.k-topbar__icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--kubo-color-border-subtle);
  border-radius: var(--kubo-radius-sm);
  background: transparent;
  color: var(--kubo-color-text-secondary);
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
    border-color: var(--el-color-primary-light-5);
  }
}

.k-crumb__icon {
  margin-right: 4px;
  vertical-align: -2px;
}

:deep(.el-breadcrumb__inner) {
  color: var(--kubo-color-text-secondary);
  font-weight: 400;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--kubo-color-text-primary);
  font-weight: 600;
}

.k-demo-tag {
  border-radius: 999px;
}

/* ── 内容区 ── */
.k-shell__content {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
  padding: 20px;
}

.k-content__inner {
  /* 宽度单一数据源 tokens.scss 的 --kubo-content-max-width；
   * 写死 1600 时 2560 宽屏右侧实测空 377px（见测试报告 §8） */
  max-width: var(--kubo-content-max-width, 2200px);
  margin: 0 auto;
}

.k-content__inner--plain {
  max-width: none;
}

/* ── 页脚 ── */
.k-shell__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 34px;
  padding: 0 20px;
  border-top: 1px solid var(--kubo-color-border-subtle);
  background: var(--kubo-color-bg-elevated);
  color: var(--kubo-color-text-secondary);
  font-size: 12px;
  flex: 0 0 auto;
}

.k-footer__meta {
  opacity: 0.7;
}

/* ── 窄屏 ── */
@media (max-width: 1023px) {
  .k-shell__content {
    padding: 12px;
  }

  .k-shell__topbar {
    padding: 0 12px;
  }
}

/* 手机档：搜索框退成纯图标（点击行为不变，仍开命令面板），
 * 右簇间距收紧，375 视口实测不再横向溢出 */
@media (max-width: 640px) {
  .k-topbar__search-trigger {
    .placeholder,
    .shortcut {
      display: none;
    }
  }

  .k-topbar__right {
    gap: 6px;
  }
}
</style>

<script lang="ts" setup>
/**
 * # 侧栏菜单节点（递归组件）
 *
 * 把后端下发的菜单树渲染成 `el-menu` 结构：
 * - 有 `children` 的节点 → `el-sub-menu`（只展开收起，自身不导航）；
 * - 叶子节点 → `el-menu-item`，点击后跳转 `path`。
 *
 * 递归：`<script setup>` 中同名组件可直接自引用（文件名即组件名）。
 */
import type { MenuItem } from '@/router/appRouter'
import { resolveMenuIcon } from './icons'

defineProps<{
  item: MenuItem
}>()
</script>

<template>
  <el-sub-menu
    v-if="item.children && item.children.length > 0"
    :index="`group-${item.id}`"
  >
    <template #title>
      <el-icon class="k-menu__icon">
        <component :is="resolveMenuIcon(item.icon)" />
      </el-icon>
      <span>{{ item.name }}</span>
    </template>
    <KAppMenuNode
      v-for="child in item.children"
      :key="child.id"
      :item="child"
    />
  </el-sub-menu>

  <el-menu-item
    v-else
    :index="item.path || `empty-${item.id}`"
  >
    <el-icon class="k-menu__icon">
      <component :is="resolveMenuIcon(item.icon)" />
    </el-icon>
    <template #title>
      {{ item.name }}
    </template>
  </el-menu-item>
</template>

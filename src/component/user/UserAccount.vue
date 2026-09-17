<script lang="ts" setup>
import { ElTabPane, ElTabs } from 'element-plus'
import { ref } from 'vue'
import ModifyPassword from './ModifyPassword.vue'
import PersonalToken from './PersonalToken.vue'

/**
 * # 账号安全中心（F4 批 6 · 新底座）
 *
 * 两个 tab 各自是独立表单：修改密码（`user/updateMyPassword`）与私人令牌
 * （`personalToken/*`）。弹窗外壳由调用方（用户卡片）提供 —— 这个组件只负责 tab 切换，
 * 因此不再是「自己会弹自己的 ADialog」，可以放进任何容器里。
 */
enum Tabs {
  modifyPassword = 'modifyPassword',
  personalToken = 'personalToken',
}

const active = ref(Tabs.modifyPassword)
</script>

<template>
  <ElTabs
    v-model="active"
    type="card"
  >
    <ElTabPane
      :name="Tabs.modifyPassword"
      label="修改密码"
    >
      <ModifyPassword v-if="active === Tabs.modifyPassword" />
    </ElTabPane>
    <ElTabPane
      :name="Tabs.personalToken"
      label="私人令牌"
    >
      <PersonalToken v-if="active === Tabs.personalToken" />
    </ElTabPane>
  </ElTabs>
</template>

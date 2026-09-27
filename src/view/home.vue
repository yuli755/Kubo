<script lang="ts" setup>
import { onMounted } from 'vue'
import { getRouter } from '@/router/appRouter'

/**
 * 首页中间跳板页重定向收口：
 * 登录后或访问 /home 时直达工作台（/console/index），彻底废除冗余的「进入控制台」门楼过渡页。
 */
onMounted(() => {
  getRouter().replace('/console/index')
})
</script>

<template>
  <div class="home-redirect" />
</template>

<style scoped>
.home-redirect {
  display: none;
}
</style>

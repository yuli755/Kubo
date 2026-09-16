<script lang="ts" setup>
import type { IJson } from '@airpower/web'
import { DateTimeUtil, FeedbackUtil } from '@airpower/web'
import { ref } from 'vue'
import { UserService } from '@/hook/personnel/useUser'
import { useAppRoute } from '@/router/appRouter'

const isLoading = ref(true)
const route = useAppRoute()

async function init() {
  const platform = route.params.platform.toString()
  const code = route.query.code?.toString() || ''
  try {
    await UserService.create(isLoading).thirdBind(platform, code)
  }
  catch (e) {
    await FeedbackUtil.alertError((e as IJson).message)
    return
  }
  FeedbackUtil.toastSuccess('绑定第三方账号成功')
  await DateTimeUtil.sleep(1000)
  if (window.opener) {
    window.close()
  }
  else {
    window.location.replace('/console')
  }
}

init()
</script>

<template>
  <div
    v-loading="isLoading"
    class="callback big-loading"
  />
</template>

<style lang="scss" scoped>
.callback {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  background-color: white;
}
</style>

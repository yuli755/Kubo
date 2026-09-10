<script lang="ts" setup>
import type { IJson } from '@airpower/web'
import { DateTimeUtil, FeedbackUtil, WebConfig } from '@airpower/web'
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { UserService } from '@/hook/personnel/useUser'

const isLoading = ref(true)
const route = useRoute()

async function init() {
  const platform = route.params.platform.toString()
  const code = route.query.code?.toString() || ''
  try {
    const accessToken = await UserService.create(isLoading).callbackCode(platform, code)
    WebConfig.saveAccessToken(accessToken)
  }
  catch (e) {
    await FeedbackUtil.alertError((e as IJson).message)
    return
  }

  await DateTimeUtil.sleep(1000)

  // 判断当前窗口是否由 window.open 打开
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

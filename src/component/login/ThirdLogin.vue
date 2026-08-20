<script lang="ts" setup>
import { FeedbackUtil } from '@airpower/web'
import { ThirdLoginPlatform } from '@/model/open/thirdlogin/ThirdLoginPlatform'

function getIcon(item: ThirdLoginPlatform) {
  return `/img/thirdlogin/${item.flag}.png`
}

function getBrandColor(item: ThirdLoginPlatform): string {
  switch (item.flag) {
    case 'wecom':
      return '#07c160'
    case 'wechat':
      return '#07c160'
    case 'qq':
      return '#12b7f5'
    case 'weibo':
      return '#e6162d'
    case 'dingtalk':
      return '#0089ff'
    case 'feishu':
      return '#3370ff'
    case 'google':
      return '#4285f4'
    case 'github':
      return '#24292e'
    case 'gitee':
      return '#c71d23'
    case 'apple':
      return '#000000'
    default:
      return '#909399'
  }
}

function onLogin(item: ThirdLoginPlatform) {
  if (item.disabled || !item.oauthUrl) {
    FeedbackUtil.toastError(`暂不支持${item.label}登录`)
    return
  }
  const redirectUri = `${window.location.origin}/callback/${item.flag}`
  const url = item.oauthUrl.replace('APP_KEY', item.appKey).replace('REDIRECT_URI', redirectUri)
  const features = `menubar=no,toolbar=no,status=no,width=${item.width},height=${item.height},left=${(window.screen.width - item.width) / 2},top=${(window.screen.height - item.height) / 2}`
  window.open(url, '_blank', features)
}
</script>

<template>
  <div class="third-login">
    <div
      v-for="item in ThirdLoginPlatform.toArray().filter((item) => !item.disabled)"
      :key="item.key"
      v-tip="item.label"
      class="item"
      @click="onLogin(item)"
    >
      <div
        class="icon"
        :style="{ backgroundColor: getBrandColor(item) }"
      >
        <img
          :src="getIcon(item)"
          alt=""
        >
      </div>
      <span class="label">{{ item.label }}</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.third-login {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 16px;

  .item {
    cursor: pointer;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;

    .icon {
      border-radius: 50%;
      width: 40px;
      height: 40px;
      display: flex;
      justify-content: center;
      align-items: center;
      transition: all 0.3s ease;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

      img {
        width: 24px;
        height: 24px;
        object-fit: contain;
      }
    }

    .item:hover .icon {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
    }

    .label {
      font-size: 11px;
      color: #999;
    }
  }
}
</style>

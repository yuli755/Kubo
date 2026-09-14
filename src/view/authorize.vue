<script lang="ts" setup>
import type { OauthScope } from '@/model/open/oauth/OauthScope'
import { FeedbackUtil } from '@airpower/web'
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import Copyright from '@/component/login/Copyright.vue'
import Logo from '@/component/login/Logo.vue'
import { OpenAppService } from '@/hook/open/useApp'
import { UserService } from '@/hook/personnel/useUser'
import { OpenAppEntity } from '@/model/open/app/OpenAppEntity'
import { UserEntity } from '@/model/personnel/user/UserEntity'

const user = ref(new UserEntity())

const route = useRoute()
const appKey = (route.query.appKey || '').toString()
const scope = (route.query.scope || '').toString()
const redirectUri = (route.query.redirectUri || '/console').toString()

const appInfo = ref(new OpenAppEntity())

const scopeList = ref([] as OauthScope[])
const scopeNeed = ref([] as string[])

// 一些Loading状态
const isLoading = ref(false)

/**
 * 阶段 E-C2：`?redirectUri=` 由访问者直接控制（/authorize 是 guest 路由，
 * 可以绕过 /open/authorize 的入口校验直接打开本页）。携带授权 code 跳转到
 * 任意值等于把第三方应用的 code 送给攻击者域。规则与后端
 * OauthController.isValidRedirectUri 对齐：只认该 App 登记的回调地址
 * （OpenAppEntity.url）完全一致；对不上就留在本站 /console。
 */
async function validatedRedirectUri(): Promise<string> {
  if (!appInfo.value.url) {
    await getAppInfo()
  }
  return appInfo.value.url && redirectUri === appInfo.value.url ? redirectUri : '/console'
}

/**
 * ### 获取当前应用信息
 */
async function getAppInfo() {
  appInfo.value = await OpenAppService.create(isLoading).getAppByKey(appKey)
}

async function getScopeList() {
  const list = await UserService.create(isLoading).getScopeList()
  scopeList.value = list.filter(item => scopeNeed.value.includes(item.name) || item.isDefault)
}

async function onConfirm() {
  const result = await UserService.create(isLoading).authorize(appKey, scope)
  const target = await validatedRedirectUri()
  window.location.href = `${target}?code=${result}`
}

async function onCancel() {
  const target = await validatedRedirectUri()
  window.location.href = `${target}?error=cancel`
}

async function init() {
  if (!appKey || !scope || !redirectUri) {
    window.location.replace('/')
    return
  }
  scopeNeed.value = scope.split(',')
  user.value = await UserService.create(isLoading).getMyInfo()
  await getAppInfo()
  await getScopeList()
}

async function onSwitchAccount() {
  await FeedbackUtil.confirmWarning('是否确认切换登录的账号', '切换账号')
  window.location.replace(`/login${window.location.search}`)
}

init()
</script>

<template>
  <div class="authorize">
    <Logo />
    <div
      v-loading="isLoading"
      class="card"
    >
      <div class="title">
        Hi, {{ user.nickname }}
        <el-link @click="onSwitchAccount()">
          切换账号
        </el-link>
      </div>
      <div class="account">
        是否授权 {{ appInfo.appName || '...' }} 访问你的：
      </div>
      <div class="scopes">
        <div
          v-for="item in scopeList"
          :key="item.name"
          class="item"
        >
          {{ item.description }}
        </div>
      </div>
      <div class="footer">
        <el-button
          size="large"
          type="primary"
          @click="onConfirm()"
        >
          确认授权
        </el-button>
        <el-button
          size="large"
          @click="onCancel()"
        >
          取消授权
        </el-button>
      </div>
    </div>
    <Copyright />
  </div>
</template>

<style lang="scss" scoped>
.authorize {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  background: #f5f5f5;
  align-items: center;
  justify-content: center;

  .logo {
    height: 60px;
    padding: 10px;
    background-color: rgba($color: #fff, $alpha: 0.1);
    border-radius: 20px;
    position: absolute;
    left: 30px;
    top: 30px;

    img {
      height: 100%;
    }
  }

  .card {
    overflow: hidden;
    background-color: rgba($color: #f5f5f5, $alpha: 0.5);
    box-shadow: 0 0 60px rgba($color: #000, $alpha: 0.1);
    backdrop-filter: blur(20px);
    border-radius: 20px;
    padding: 40px 60px;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 400px;
    min-height: 200px;
    max-height: 500px;

    .title {
      font-size: 24px;
      font-weight: bold;
    }

    .account {
      font-size: 16px;
      margin-top: 20px;
    }

    .scopes {
      background: #eee;
      margin: 20px 0;
      flex: 1;
      height: 0;
      overflow: hidden;
      overflow-y: auto;
      width: 90%;
      padding: 15px 20px;
      border-radius: 15px;

      .item {
        position: relative;
        color: #999;
        font-size: 14px;
        margin: 5px 0;
      }

      .item::before {
        content: '';
        background: #999;
        width: 8px;
        height: 8px;
        display: inline-block;
        margin-right: 5px;
        border-radius: 100%;
      }
    }

    .footer {
      display: flex;
      flex-direction: row;
      align-items: center;
      width: 100%;

      .el-button {
        flex: 1;
        margin: 0 5px;
      }
    }
  }
}

@media screen and ((orientation: portrait) and (max-width: 600px)) {
  .authorize {
    .logo {
      left: auto !important;
    }

    .card {
      width: 90% !important;
      background: transparent !important;
      backdrop-filter: blur(0px) !important;
      box-shadow: none !important;
    }

    .item-3 {
      display: none;
    }
  }
}
</style>

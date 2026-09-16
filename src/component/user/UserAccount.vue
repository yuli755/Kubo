<script lang="ts" setup>
import { ADialog, DialogProps } from '@airpower/web'
import { ref } from 'vue'

import PersonalToken from '@/component/user/PersonalToken.vue'
import ModifyPassword from './ModifyPassword.vue'

defineProps(DialogProps.create())

enum TABS {

  modifyPassword = 'modifyPassword',
  personalToken = 'personalToken',
}

const active = ref(TABS.modifyPassword)
</script>

<template>
  <ADialog
    hide-cancel
    hide-confirm
    hide-fullscreen
    min-height="100px"
    title="账号安全中心"
    @cancel="onCancel"
    @confirm="onConfirm"
  >
    <el-tabs
      v-model="active"
      type="card"
    >
      <el-tab-pane
        :name="TABS.modifyPassword"
        label="修改密码"
      >
        <ModifyPassword v-if="active === TABS.modifyPassword" />
      </el-tab-pane>
      <el-tab-pane
        :name="TABS.personalToken"
        label="私人令牌"
      >
        <PersonalToken v-if="active === TABS.personalToken" />
      </el-tab-pane>
    </el-tabs>
  </ADialog>
</template>

<style lang="scss" scoped>
.account-list {
  overflow: hidden;
  overflow-y: auto;

  .account {
    margin: 5px 10px;
    background-color: var(--kubo-color-muted-bg);
    border-radius: 10px;
    padding: 20px;
    font-size: 14px;
    display: flex;
    flex-direction: row;
    align-items: center;

    img {
      width: 24px;
      height: 24px;
      background-color: var(--el-text-color-placeholder);
      padding: 5px;
      border-radius: 10px;
    }

    .left {
      display: flex;
      flex-direction: row;
      align-items: center;
      color: var(--el-text-color-secondary);
      font-weight: bold;
      flex: 1;

      .logo {
        background-color: var(--el-text-color-placeholder);
        margin-right: 10px;
      }
    }

    .right {
      display: flex;
      flex-direction: row;
      align-items: center;
      color: var(--el-text-color-secondary);
      font-weight: bold;

      .avatar {
        margin-left: 10px;
      }

      .unbind {
        margin-left: 10px;
      }
    }
  }

  .active {
    .left {
      color: var(--primary-color);

      .logo {
        background-color: var(--primary-color);
      }
    }
  }
}
</style>

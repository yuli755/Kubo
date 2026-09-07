<script lang="ts" setup>
import { ElMessageBox } from 'element-plus'
import { ref } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const lastConfirmedTenantId = ref(userStore.tenantId)
const isSwitching = ref(false)

/**
 * 租户切换只接受后端返回的选项。确认后刷新整个控制台，清掉旧租户页面、AirPower
 * 选择器和 TanStack 查询缓存，避免旧租户数据在新租户请求完成前短暂显示。
 */
async function onTenantChange(value: number | undefined) {
  if (value === undefined || value === lastConfirmedTenantId.value) {
    return
  }

  try {
    await ElMessageBox.confirm(
      '切换租户将刷新当前页面，未保存的内容不会保留。是否继续？',
      '切换租户',
      {
        type: 'warning',
        confirmButtonText: '继续',
        cancelButtonText: '取消',
      },
    )
  }
  catch {
    userStore.setTenantId(lastConfirmedTenantId.value)
    return
  }

  isSwitching.value = true
  lastConfirmedTenantId.value = value
  userStore.setTenantId(value)
  window.location.reload()
}
</script>

<template>
  <el-select
    v-if="userStore.tenantList.length > 0"
    v-model="userStore.tenantId"
    aria-label="当前租户"
    class="tenant-switcher"
    :disabled="isSwitching || userStore.tenantList.length < 2"
    :teleported="false"
    @change="onTenantChange"
  >
    <el-option
      v-for="tenant in userStore.tenantList"
      :key="tenant.id"
      :label="`${tenant.name}（${tenant.code}）`"
      :value="tenant.id"
    />
  </el-select>
</template>

<style lang="scss" scoped>
.tenant-switcher {
  width: 190px;

  :deep(.el-input__wrapper) {
    background: transparent;
    box-shadow: 0 0 0 1px rgb(255 255 255 / 35%) inset;
  }

  :deep(.el-input__inner) {
    color: white;
  }

  :deep(.el-select__caret) {
    color: rgb(255 255 255 / 85%);
  }
}
</style>

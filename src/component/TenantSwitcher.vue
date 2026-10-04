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
// 本 EP 版本 el-select 已是新结构（.el-select__wrapper/.el-select__selected-item），
// 旧 :deep(.el-input__wrapper/.el-input__inner) 从未命中过——实测（R41 ②）描边吃的是
// EP 默认灰、选中项文字是灰字，深色顶栏上品牌环与白字全部失效。选择器随结构更新。
.tenant-switcher {
  width: 190px;

  /* 手机档顶栏寸土寸金：375 实测右簇 403px 溢出裁掉用户卡。
   * 收窄后 EP 自带的 ellipsis 会把长租户名省略成「…」，
   * 点开下拉仍能看到全名并切换。 */
  @media (max-width: 640px) {
    width: 96px;
  }

  :deep(.el-select__wrapper) {
    background: transparent;
    box-shadow: var(--kubo-ring-inset-on-brand);
  }

  // 选中项经 .el-select__placeholder span 呈现（EP 新结构），disabled 态同色才可读
  :deep(.el-select__selected-item),
  :deep(.el-select__selected-item span) {
    color: var(--kubo-on-brand-text);
  }

  :deep(.el-select__caret) {
    color: var(--kubo-on-brand-text-muted);
  }
}
</style>

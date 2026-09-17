<script lang="ts" setup>
import type { PersonalTokenEntity } from '@/model/personnel/user/token/PersonalTokenEntity'
import { ElButton, ElEmpty, ElInput, ElMessageBox, ElSwitch } from 'element-plus'
import { onMounted, ref } from 'vue'
import { ClipboardUtil, DateTimeFormatter, FeedbackUtil } from '@/framework'
import { PersonalTokenService } from '@/model/personnel/user/token/PersonalTokenService'

/**
 * # 私人令牌（F4 批 6 · 新底座）
 *
 * 令牌只在**创建的那一刻**返回明文，之后列表里给的是脱敏串 —— 所以创建流程是
 * 「确认 → 创建 → 弹窗展示 → 点「点击复制」才关窗」，这个顺序不能简化成一句 toast：
 * 关早了用户就再也拿不到令牌了。
 *
 * 启停用 `enableMy` / `disableMy` 两个端点，开关的 `active-value` 与 `isDisabled` 相反
 * （旧实现同款），点开关先二次确认再发请求 —— 令牌是凭证，误触即等于把手里的钥匙停掉。
 */
const list = ref<PersonalTokenEntity[]>([])
const isLoading = ref(false)
const name = ref('')

async function init() {
  isLoading.value = true
  try {
    list.value = await new PersonalTokenService().getMyPersonalTokenList()
  }
  finally {
    isLoading.value = false
  }
}

async function createMyPersonalToken() {
  await FeedbackUtil.confirmWarning('是否确认创建新的私人令牌？', '创建确认')
  const token = await new PersonalTokenService().createMyPersonalToken(name.value)

  await ElMessageBox.alert('创建私人令牌成功，请点击复制，后续将不再显示令牌！', '创建成功', {
    confirmButtonText: '点击复制',
    closeOnPressEscape: false,
    type: 'success',
    showClose: false,
  })

  await ClipboardUtil.copy(token)
  FeedbackUtil.toastSuccess('已经成功复制到你的剪切板')
  name.value = ''
  await init()
}

async function onDisableOrEnable(item: PersonalTokenEntity) {
  const action = item.isDisabled ? '启用' : '禁用'
  await FeedbackUtil.confirmWarning(`是否确认${action}这个私人令牌？`, action)
  const service = new PersonalTokenService()
  await (item.isDisabled ? service.enableMyPersonalToken(item) : service.disableMyPersonalToken(item))
  await init()
}

function formatTime(value?: number) {
  return value ? DateTimeFormatter.FULL_DATE_TIME.formatMilliSecond(value) : '-'
}

onMounted(init)
</script>

<template>
  <div class="personal-token">
    <div class="personal-token__new">
      <ElInput
        v-model="name"
        placeholder="请输入令牌名称..."
      />
      <ElButton
        :disabled="!name"
        type="primary"
        @click="createMyPersonalToken()"
      >
        创建令牌
      </ElButton>
    </div>

    <div
      v-loading="isLoading"
      class="personal-token__main"
    >
      <div
        v-for="item in list"
        :key="item.id"
        class="personal-token__item"
      >
        <div class="personal-token__title">
          {{ item.name }}
        </div>
        <div class="personal-token__token">
          {{ item.token }}
        </div>
        <ElSwitch
          :active-value="false"
          :inactive-value="true"
          :model-value="item.isDisabled"
          @click="onDisableOrEnable(item)"
        />
        <div class="personal-token__time">
          创建于：{{ formatTime(item.createTime) }}
        </div>
      </div>

      <ElEmpty
        v-if="list.length === 0"
        description="没有创建任何身份令牌"
      />
    </div>
  </div>
</template>

<style scoped>
.personal-token__new {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px 0;
}

.personal-token__main {
  max-height: 320px;
  overflow-y: auto;
}

.personal-token__item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.personal-token__title {
  min-width: 120px;
  font-weight: 600;
}

.personal-token__token {
  flex: 1;
  overflow: hidden;
  color: var(--el-text-color-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.personal-token__time {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>

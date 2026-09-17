<script lang="ts" setup>
import type { UserEntity } from '@/model/personnel/user/UserEntity'
import { ElAvatar, ElDialog, ElDropdown, ElDropdownItem, ElDropdownMenu, ElLink } from 'element-plus'
import { computed, ref } from 'vue'
import UserAccount from '@/component/user/UserAccount.vue'
import { DialogUtil, FeedbackUtil } from '@/framework'
import { UserService } from '@/model/personnel/user/UserService'
import { FileCategory } from '@/model/system/file/FileCategory'
import { useUserStore } from '@/stores/user'

/**
 * # 用户卡片（顶栏头像下拉 · F4 批 6 · 新底座）
 *
 * 旧实现是 airpower 的 `AUser` + `AImage`：外壳自己写了一个「退出登录」的额外入口，
 * 因为 `AUser` 的内置退出只删 `Authorization` 键就跳登录页 —— 不调 `/user/logout`、
 * 服务端会话与 Cookie 都不更新，本地快照还会把会话复活（阶段 E-C1 的记录）。
 * 换底座时**保留这个修正**：登出只走 `userStore.logout()`（吊销服务端会话 → 清本地态 → 整页跳转）。
 *
 * 头像上传改走框架层的 `DialogUtil.showUpload`（批 0B 补的上传组件），
 * 上传成功后回写 `avatar` 并重新拉一次 `getMyInfo` —— 与旧实现同一顺序。
 */
const props = defineProps<{
  user: UserEntity
}>()

const userStore = useUserStore()

const accountVisible = ref(false)
const isLoggingOut = ref(false)
const isUploading = ref(false)

/** 顶栏只显示昵称首字：头像为空时不做"空图占位"，那比文字更难看 */
const avatarText = computed(() => (props.user.nickname || '?').slice(0, 1))

const isRealNameBound = computed(() => !!props.user.idCard)

const realNameText = computed(() => (props.user.realName ? `${props.user.realName[0]}**` : ''))

function onUserAccount() {
  accountVisible.value = true
}

/** 登出：吊销服务端会话 → 清本地态 → 整页跳转（`replace` 让后退键回不到已登出的页面） */
async function onLogout() {
  if (isLoggingOut.value) {
    return
  }
  isLoggingOut.value = true
  try {
    await userStore.logout()
  }
  finally {
    window.location.replace('/login')
  }
}

/**
 * 头像上传。
 *
 * 用框架层 `DialogUtil.showUpload`（内部走 `KUploadDialog`），而不是在外壳里再塞一个上传控件：
 * 上传的校验、multipart 组装、预览地址都在框架层，外壳只需要"选完文件后干什么"。
 */
async function onUploadAvatar() {
  const file = await DialogUtil.showUpload<{ url?: string }>({
    category: FileCategory.AVATAR.key,
    title: '上传头像',
  })
  if (!file?.url) {
    // 用户取消/关窗：不算错误，也不弹提示（旧实现同样静默）
    return
  }
  isUploading.value = true
  try {
    const service = new UserService()
    const user = await service.getMyInfo()
    user.avatar = file.url
    await service.updateMyInfo(user)
    // 回写的是重新拉取的实体：只改内存里的 avatar 而不重拉，租户/权限等字段可能已被服务端改过
    userStore.setUserInfo(await service.getMyInfo())
  }
  catch {
    FeedbackUtil.toastError('头像已上传，但保存到个人资料失败，请重试')
  }
  finally {
    isUploading.value = false
  }
}
</script>

<template>
  <ElDropdown trigger="click">
    <span class="user-card__trigger">
      <ElAvatar
        :src="user.avatar"
        :size="32"
      >
        {{ avatarText }}
      </ElAvatar>
      <span class="user-card__name">{{ user.nickname }}</span>
    </span>

    <template #dropdown>
      <ElDropdownMenu>
        <div class="user-card__panel">
          <div
            v-loading="isUploading"
            class="user-card__avatar"
            title="点击更换头像"
            @click="onUploadAvatar"
          >
            <ElAvatar
              :src="user.avatar"
              :size="64"
            >
              {{ avatarText }}
            </ElAvatar>
          </div>

          <div class="user-card__info">
            <div class="user-card__item">
              <span class="user-card__key">实名</span>
              <span class="user-card__value">
                <template v-if="isRealNameBound">{{ realNameText }}</template>
                <ElLink
                  v-else
                  type="primary"
                  @click="onUserAccount"
                >
                  去实名
                </ElLink>
              </span>
            </div>
            <div class="user-card__item">
              <span class="user-card__key">邮箱</span>
              <span class="user-card__value">
                <template v-if="user.email">{{ user.email }}</template>
                <ElLink
                  v-else
                  type="primary"
                  @click="onUserAccount"
                >
                  绑定邮箱
                </ElLink>
              </span>
            </div>
            <div class="user-card__item">
              <span class="user-card__key">手机</span>
              <span class="user-card__value">
                <template v-if="user.phone">{{ user.phone }}</template>
                <ElLink
                  v-else
                  type="primary"
                  @click="onUserAccount"
                >
                  绑定手机
                </ElLink>
              </span>
            </div>
          </div>

          <ElDropdownItem
            divided
            @click="onUserAccount"
          >
            账号安全
          </ElDropdownItem>
          <ElDropdownItem
            :disabled="isLoggingOut"
            @click="onLogout"
          >
            {{ isLoggingOut ? '正在退出…' : '退出登录' }}
          </ElDropdownItem>
        </div>
      </ElDropdownMenu>
    </template>
  </ElDropdown>

  <ElDialog
    v-model="accountVisible"
    :close-on-click-modal="false"
    append-to-body
    destroy-on-close
    title="账号安全中心"
    width="640px"
  >
    <UserAccount />
  </ElDialog>
</template>

<style scoped>
.user-card__trigger {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.user-card__name {
  font-size: 14px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-card__panel {
  padding: 12px 16px;
  min-width: 260px;
}

.user-card__avatar {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
  cursor: pointer;
}

.user-card__info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
}

.user-card__item {
  display: flex;
  align-items: center;
  font-size: 13px;
}

.user-card__key {
  width: 40px;
  color: var(--el-text-color-secondary);
}

.user-card__value {
  color: var(--el-text-color-primary);
}
</style>

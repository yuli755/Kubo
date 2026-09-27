<script lang="ts" setup>
import { AImage, AUser, DesensitizeType, DialogUtil } from '@airpower/web'
import { ref } from 'vue'
import { AppConfig } from '@/config/AppConfig'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { FileCategory } from '@/model/system/file/FileCategory'
import { FileEntity } from '@/model/system/file/FileEntity'
import { useUserStore } from '@/stores/user'
import UserAccount from './user/UserAccount.vue'

defineProps({
  user: {
    type: UserEntity,
    required: true,
  },
})

function onUserAccount() {
  DialogUtil.show(UserAccount)
}

const isLoggingOut = ref(false)
const isLoading = ref(false)

/**
 * 阶段 E-C1：AUser 内置退出按钮只删 Authorization 键就跳登录页，
 * 不调 /user/logout（服务端 Cookie/吊销名单不更新），本地快照还会复活会话。
 * 这里提供走 userStore.logout() 的完整登出：吊销服务端会话 → 清本地态 → 整页跳转。
 */
async function onLogout() {
  if (isLoggingOut.value) {
    return
  }
  isLoggingOut.value = true
  try {
    await useUserStore().logout()
  }
  finally {
    window.location.replace('/login')
  }
}

async function onUploadAvatar(file: FileEntity) {
  // L-1 载荷最小化：updateMyInfo 在后端是「非 null 即覆盖」的合并语义（K-1 已把
  // 授权类字段强制清空作为兜底），前端只提交要改的 avatar——整对象上送会把
  // roleList/departmentList 等授权字段带上线，纯属多余攻击面与回归风险。
  const payload = new UserEntity()
  payload.avatar = file.url
  await UserService.create(isLoading).updateMyInfo(payload)
  AppConfig.currentUser.value = await UserService.create(isLoading).getMyInfo()
}
</script>

<template>
  <AUser
    :height="200"
    :user="user"
  >
    <div class="user">
      <div class="user-left">
        <AImage
          v-loading="isLoading"
          :data="{
            category: FileCategory.AVATAR.key,
          }"
          :entity="FileEntity"
          :height="80"
          :width="80"
          upload
          @upload="onUploadAvatar"
        />
        <div
          :class="user.gender === 1 ? 'male' : 'female'"
          class="gender"
        >
          {{ user.gender === 1 ? '男' : '女' }}
        </div>
      </div>
      <div class="user-right">
        <div class="info">
          <div class="item">
            <div class="key">
              实名
            </div>
            <div class="value">
              <template v-if="user.idCard">
                {{ DesensitizeType.CHINESE_NAME.desensitize(user.realName) }}
              </template>
              <el-link
                v-else
                type="primary"
                @click="onUserAccount()"
              >
                去实名
              </el-link>
            </div>
          </div>
          <div class="item">
            <div class="key">
              邮箱
            </div>
            <div class="value">
              <template v-if="user.email">
                {{ DesensitizeType.EMAIL.desensitize(user.email) }}
                <el-link
                  type="primary"
                  @click="onUserAccount()"
                >
                  更换
                </el-link>
              </template>
              <el-link
                v-else
                type="primary"
                @click="onUserAccount()"
              >
                绑定邮箱
              </el-link>
            </div>
          </div>
          <div class="item">
            <div class="key">
              手机
            </div>
            <div class="value">
              <template v-if="user.phone">
                {{ DesensitizeType.MOBILE.desensitize(user.phone) }}
                <el-link
                  type="primary"
                  @click="onUserAccount()"
                >
                  更换
                </el-link>
              </template>
              <el-link
                v-else
                type="primary"
                @click="onUserAccount()"
              >
                绑定手机
              </el-link>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="setting">
      <div
        class="item"
        @click="onUserAccount()"
      >
        账号安全
      </div>
      <div
        class="item"
        :class="{ disabled: isLoggingOut }"
        @click="onLogout"
      >
        {{ isLoggingOut ? '正在退出…' : '退出登录' }}
      </div>
    </div>
  </AUser>
</template>

<style lang="scss" scoped>
.user {
  display: flex;
  flex-direction: row;
  align-items: flex-start;

  &-left {
    margin-right: 20px;
    position: relative;

    ::v-deep(.el-image) {
      width: 80px;
      height: 80px;
      background: var(--kubo-color-muted-bg);
      border-radius: 10px;

      img {
        width: 100%;
        height: 100%;
      }
    }

    .gender {
      position: absolute;
      right: -7px;
      bottom: 0;
      color: white;
      border-radius: 5px;
      font-size: 12px;
      width: 15px;
      height: 15px;
      text-align: center;
      line-height: 15px;
    }

    .male {
      background-color: #409eff;
    }

    .female {
      background-color: #f56c6c;
    }
  }

  &-right {
    flex: 1;

    .info {
      .item {
        display: flex;
        align-items: center;
        margin-bottom: 8px;

        .key {
          width: 40px;
          font-size: 13px;
          color: var(--el-text-color-secondary);
        }

        .value {
          font-size: 13px;
          color: var(--el-text-color-primary);

          .el-link {
            margin-left: 8px;
            font-size: 12px;
          }
        }
      }
    }
  }
}

.setting {
  display: flex;
  flex-direction: column;
  border-top: 1px solid var(--el-border-color);
  padding-top: 10px;
  margin-top: 10px;

  .item {
    padding: 8px 12px;
    cursor: pointer;
    border-radius: 6px;
    font-size: 14px;
    color: var(--el-text-color-primary);
    transition: all 0.2s;

    &.disabled {
      cursor: not-allowed;
      color: var(--el-text-color-disabled);
    }

    &:hover {
      background: var(--kubo-color-muted-bg);
      color: var(--primary-color, #409eff);
    }
  }
}
</style>

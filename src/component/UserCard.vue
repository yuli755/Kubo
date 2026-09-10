<script lang="ts" setup>
import { AImage, AUser, DesensitizeType, DialogUtil } from '@airpower/web'
import { ref } from 'vue'
import { AppConfig } from '@/config/AppConfig'
import { UserEntity } from '@/model/personnel/user/UserEntity'
import { UserService } from '@/model/personnel/user/UserService'
import { FileCategory } from '@/model/system/file/FileCategory'
import { FileEntity } from '@/model/system/file/FileEntity'
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

const isLoading = ref(false)

async function onUploadAvatar(file: FileEntity) {
  AppConfig.currentUser.value = await UserService.create(isLoading).getMyInfo()
  AppConfig.currentUser.value.avatar = file.url
  await UserService.create(isLoading).updateMyInfo(AppConfig.currentUser.value)
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

    &:hover {
      background: var(--kubo-color-muted-bg);
      color: var(--primary-color, #409eff);
    }
  }
}
</style>

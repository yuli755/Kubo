<script lang="ts" setup>
import { RouterUtil } from '@airpower/web'
import { Avatar, Collection, OfficeBuilding, User } from '@element-plus/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { authApi } from '@/api/system'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

/**
 * ### 加载状态（骨架屏）
 */
const isLoading = ref(true)

/**
 * ### 当前登录用户显示名
 */
const displayName = computed(() => {
  const info = userStore.userInfo
  const name = (info && (info.nickname || info.username || info.email)) || ''
  return name || '欢迎使用 Kubo 平台'
})

/**
 * ### 快捷入口
 */
const entries = [
  {
    title: '用户管理',
    desc: '账号、角色、权限配置',
    icon: User,
    path: '/console/personnel/user',
  },
  {
    title: '角色管理',
    desc: '角色与权限分配',
    icon: Avatar,
    path: '/console/personnel/role',
  },
  {
    title: '部门管理',
    desc: '组织架构维护',
    icon: OfficeBuilding,
    path: '/console/personnel/department',
  },
  {
    title: '系统配置',
    desc: '系统参数与规则配置',
    icon: Collection,
    path: '/console/system/config',
  },
]

/**
 * ### 初始化
 * 若 userStore 尚无用户信息，尝试从后端拉取补齐。
 * 失败时静默，不阻塞首页渲染。
 */
async function init() {
  try {
    if (!userStore.userInfo && userStore.token) {
      const user = await authApi.getCurrentUser()
      userStore.setUserInfo(user)
    }
  }
  catch {
    // 静默忽略 — 首页应始终可渲染
  }
  finally {
    isLoading.value = false
  }
}

/**
 * ### 跳转到控制台
 */
function goConsole() {
  RouterUtil.router.push('/console')
}

onMounted(init)
</script>

<template>
  <div class="home">
    <!-- 骨架屏 -->
    <div
      v-if="isLoading"
      class="skeleton"
    >
      <div class="skeleton-banner" />
      <div class="skeleton-cards">
        <div
          v-for="i in 4"
          :key="i"
          class="skeleton-card"
        />
      </div>
    </div>

    <template v-else>
      <div class="banner">
        <div class="greeting">
          <div class="title">
            👋 {{ displayName }}
          </div>
          <div class="subtitle">
            欢迎来到 Kubo 平台，开启高效的管理体验
          </div>
        </div>
        <el-button
          type="primary"
          size="large"
          round
          @click="goConsole"
        >
          进入控制台
        </el-button>
      </div>

      <div class="entries">
        <div
          v-for="item in entries"
          :key="item.title"
          class="card"
          @click="RouterUtil.router.push(item.path)"
        >
          <el-icon :size="32">
            <component :is="item.icon" />
          </el-icon>
          <div class="card-title">
            {{ item.title }}
          </div>
          <div class="card-desc">
            {{ item.desc }}
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.home {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  overflow-y: auto;
  box-sizing: border-box;

  .banner {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    max-width: 900px;
    padding: 40px 50px;
    border-radius: 16px;
    background: linear-gradient(120deg, var(--primary-color), #66b1ff);
    color: #fff;

    .greeting {
      .title {
        font-size: 24px;
        font-weight: 600;
        margin-bottom: 8px;
      }

      .subtitle {
        font-size: 14px;
        opacity: 0.85;
      }
    }
  }

  .entries {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 16px;
    width: 100%;
    max-width: 900px;
    margin-top: 24px;

    .card {
      padding: 28px 20px;
      border-radius: 12px;
      background: #fff;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
      border: 1px solid #ebeef5;
      cursor: pointer;
      transition: all 0.3s;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;

      ::v-deep(.el-icon) {
        color: var(--primary-color);
      }

      .card-title {
        font-size: 16px;
        font-weight: 600;
        color: #303133;
        margin-top: 14px;
      }

      .card-desc {
        font-size: 13px;
        color: #909399;
        margin-top: 6px;
      }
    }

    .card:hover {
      transform: translateY(-4px);
      box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
    }
  }

  .skeleton {
    width: 100%;
    max-width: 900px;

    .skeleton-banner {
      width: 100%;
      height: 200px;
      border-radius: 16px;
      background: linear-gradient(90deg, #f2f2f2 25%, #e6e6e6 37%, #f2f2f2 63%);
      background-size: 400% 100%;
      animation: shimmer 1.4s ease infinite;
    }

    .skeleton-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin-top: 24px;

      .skeleton-card {
        height: 160px;
        border-radius: 12px;
        background: linear-gradient(90deg, #f2f2f2 25%, #e6e6e6 37%, #f2f2f2 63%);
        background-size: 400% 100%;
        animation: shimmer 1.4s ease infinite;
      }
    }
  }
}

@keyframes shimmer {
  0% {
    background-position: 100% 0;
  }

  100% {
    background-position: -100% 0;
  }
}

@media screen and ((orientation: portrait) and (max-width: 600px)) {
  .home {
    justify-content: flex-start;
    padding: 20px;

    .banner {
      flex-direction: column;
      align-items: flex-start;
      padding: 30px 24px;

      .el-button {
        margin-top: 16px;
      }
    }
  }
}
</style>

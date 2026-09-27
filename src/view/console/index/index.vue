<script lang="ts" setup>
import type { Component } from 'vue'
import {
  ArrowRight,
  Box,
  Coordinate,
  Goods,
  Grid,
  Management,
  OfficeBuilding,
  Right,
  Sell,
  ShoppingCart,
  Tickets,
  User,
  Van,
} from '@element-plus/icons-vue'
import { computed, markRaw } from 'vue'
import { Permissions } from '@/config/permissions'
import { getRouter } from '@/router/appRouter'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'
import BusinessOverview from './BusinessOverview.vue'
import SecurityDashboard from './SecurityDashboard.vue'

const userStore = useUserStore()
const permissionStore = usePermissionStore()

/** 安全态势看板鉴权：只在持有数据权限管理身份时展示 */
const canViewSecurity = computed(() => permissionStore.hasAny(
  Permissions.System.DataResource.getDetail,
  Permissions.System.DataPolicy.getDetail,
  Permissions.System.DataFieldPolicy.getDetail,
))

/** 当前用户问候语 */
const greetingText = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) {
    return '夜深了'
  }
  if (hour < 12) {
    return '上午好'
  }
  if (hour < 14) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})

const displayName = computed(() => {
  return userStore.userInfo?.nickname || userStore.userInfo?.username || '管理员'
})

const currentTenantName = computed(() => {
  const activeTenant = userStore.tenantList.find(t => t.id === userStore.tenantId)
  return activeTenant?.name || '默认租户'
})

const currentDateStr = computed(() => {
  const now = new Date()
  const days = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${now.getFullYear()}年${now.getMonth() + 1}月${now.getDate()}日 ${days[now.getDay()]}`
})

/** 核心业务流水线快速穿透 */
const workflowSteps = [
  { num: '01', name: '采购下单', path: '/console/channel/purchase/list', icon: markRaw(ShoppingCart) },
  { num: '02', name: '验收入库', path: '/console/wms/input/list', icon: markRaw(Van) },
  { num: '03', name: '实时库存', path: '/console/wms/inventory/list', icon: markRaw(Goods) },
  { num: '04', name: '库位热力', path: '/console/wms/inventory/list?view=heatmap', icon: markRaw(Grid) },
  { num: '05', name: '库内移位', path: '/console/wms/move/list', icon: markRaw(Coordinate) },
  { num: '06', name: '出库核销', path: '/console/wms/output/list', icon: markRaw(Sell) },
]

interface IDashboardAction {
  name: string
  desc: string
  path: string
  icon: Component
  color: string
  bg: string
}

interface IDashboardModule {
  title: string
  subtitle: string
  tag: string
  actions: IDashboardAction[]
}

const modules: IDashboardModule[] = [
  {
    title: 'ERP 供应链与资产中心',
    subtitle: '采购收发、销售订单、合作伙伴档案与物料主数据',
    tag: 'ERP 资产',
    actions: [
      {
        name: '采购单管理',
        desc: '采购需求录入、单据审核与供应商采购明细跟踪',
        path: '/console/channel/purchase/list',
        icon: markRaw(ShoppingCart),
        color: '#3b82f6',
        bg: '#eff6ff',
      },
      {
        name: '销售单管理',
        desc: '客户销售合同、销售订单闭环及发货结算管理',
        path: '/console/channel/sale/list',
        icon: markRaw(Sell),
        color: '#10b981',
        bg: '#ecfdf5',
      },
      {
        name: '往来合作伙伴',
        desc: '供应商名录、企业客户资料与渠道结算账户',
        path: '/console/channel/customer/list',
        icon: markRaw(OfficeBuilding),
        color: '#8b5cf6',
        bg: '#f5f3ff',
      },
      {
        name: '物料与资产主数据',
        desc: '企业物料分类、规格型号、计量单位与资产档案',
        path: '/console/asset/material/list',
        icon: markRaw(Goods),
        color: '#f97316',
        bg: '#fff7ed',
      },
    ],
  },
  {
    title: 'WMS 智能仓储物流中心',
    subtitle: '多维库存查询、批次流转、库位整理与出入库作业',
    tag: 'WMS 仓储',
    actions: [
      {
        name: '实时库存看板',
        desc: '全库区实时库存、批次号追踪与安全库存预警',
        path: '/console/wms/inventory/list',
        icon: markRaw(Goods),
        color: '#06b6d4',
        bg: '#ecfeff',
      },
      {
        name: '库位热力态势',
        desc: '2.5D立体货架立面、ECharts色阶矩阵与物料定位',
        path: '/console/wms/inventory/list?view=heatmap',
        icon: markRaw(Grid),
        color: '#8b5cf6',
        bg: '#f5f3ff',
      },
      {
        name: '验收入库作业',
        desc: '采购到货检验、生产完工入库与智能上架货位',
        path: '/console/wms/input/list',
        icon: markRaw(Van),
        color: '#2563eb',
        bg: '#dbeafe',
      },
      {
        name: '出库核销作业',
        desc: '销售提货复核、生产领料出库与实时扣减校验',
        path: '/console/wms/output/list',
        icon: markRaw(Box),
        color: '#ea580c',
        bg: '#ffedd5',
      },
      {
        name: '库位移动调拨',
        desc: '跨库区调拨、库内货位整理与动线移库作业',
        path: '/console/wms/move/list',
        icon: markRaw(Coordinate),
        color: '#059669',
        bg: '#d1fae5',
      },
    ],
  },
  {
    title: '系统架构与安全治理',
    subtitle: '人员组织架构、RBAC权限矩阵、编码体系与系统参数',
    tag: '系统治理',
    actions: [
      {
        name: '人员与用户账号',
        desc: '内部员工档案、企业账号、所属部门与岗位关联',
        path: '/console/personnel/user/list',
        icon: markRaw(User),
        color: '#4f46e5',
        bg: '#e0e7ff',
      },
      {
        name: '角色与权限矩阵',
        desc: '功能权限分配、数据权限策略与角色身份授权',
        path: '/console/personnel/role/list',
        icon: markRaw(Management),
        color: '#7c3aed',
        bg: '#ede9fe',
      },
      {
        name: '仓库与货位模型',
        desc: '厂区结构、立体仓库、库区及具体储位拓扑',
        path: '/console/factory/storage/list',
        icon: markRaw(OfficeBuilding),
        color: '#0d9488',
        bg: '#ccfbf1',
      },
      {
        name: '单据编码与规则',
        desc: '单据流水号、业务前缀、日期格式与批次码规则',
        path: '/console/system/coderule/list',
        icon: markRaw(Tickets),
        color: '#d97706',
        bg: '#fef3c7',
      },
    ],
  },
]

function goPage(path: string) {
  getRouter().push(path)
}
</script>

<template>
  <div class="workbench">
    <!-- 顶部欢迎与快速引导横幅 -->
    <div class="workbench-hero">
      <div class="hero-top">
        <div class="hero-greeting">
          <div class="hero-title-row">
            <span class="greeting-emoji">👋</span>
            <h2 class="hero-title">
              {{ greetingText }}，{{ displayName }}
            </h2>
          </div>
          <p class="hero-subtitle">
            今天是 {{ currentDateStr }} · 欢迎使用 Kubo 数字化制造与供应链仓储协同平台
          </p>
        </div>
        <div class="hero-meta">
          <div class="tenant-badge">
            <span class="tenant-label">当前租户</span>
            <span class="tenant-name">{{ currentTenantName }}</span>
          </div>
          <div class="system-status-badge">
            <span class="status-pulse" />
            <span class="status-text">系统运行就绪</span>
          </div>
        </div>
      </div>

      <!-- 核心业务流向链路 -->
      <div class="hero-workflow">
        <span class="workflow-label">快捷流向：</span>
        <div class="workflow-steps">
          <div
            v-for="(step, index) in workflowSteps"
            :key="step.path"
            class="workflow-step-item"
            @click="goPage(step.path)"
          >
            <span class="step-num">{{ step.num }}</span>
            <el-icon class="step-icon">
              <component :is="step.icon" />
            </el-icon>
            <span class="step-name">{{ step.name }}</span>
            <el-icon
              v-if="index < workflowSteps.length - 1"
              class="step-divider"
            >
              <Right />
            </el-icon>
          </div>
        </div>
      </div>
    </div>

    <!-- 业务核心指标看板 -->
    <div class="workbench-section">
      <BusinessOverview class="business-metrics" />
    </div>

    <!-- 安全与权限态势看板（管理员可见） -->
    <div
      v-if="canViewSecurity"
      class="workbench-section"
    >
      <SecurityDashboard class="security-metrics" />
    </div>

    <!-- 业务作业与功能导航矩阵 -->
    <div class="workbench-section modules-section">
      <div
        v-for="module in modules"
        :key="module.title"
        class="module-group"
      >
        <div class="module-header">
          <div class="header-left">
            <span class="module-tag">{{ module.tag }}</span>
            <h3 class="module-title">
              {{ module.title }}
            </h3>
          </div>
          <span class="module-subtitle">{{ module.subtitle }}</span>
        </div>

        <div class="actions-grid">
          <div
            v-for="action in module.actions"
            :key="action.name"
            class="action-card"
            :style="{
              '--badge-color': action.color,
              '--badge-bg': action.bg,
            }"
            @click="goPage(action.path)"
          >
            <div class="action-icon-box">
              <el-icon :size="20">
                <component :is="action.icon" />
              </el-icon>
            </div>
            <div class="action-info">
              <div class="action-title">
                {{ action.name }}
              </div>
              <div class="action-desc">
                {{ action.desc }}
              </div>
            </div>
            <div class="action-arrow">
              <el-icon :size="14">
                <ArrowRight />
              </el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.workbench {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 4px;
}

.workbench-hero {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 22px 26px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  display: flex;
  flex-direction: column;
  gap: 16px;

  .hero-top {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }

  .hero-greeting {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .hero-title-row {
      display: flex;
      align-items: center;
      gap: 10px;

      .greeting-emoji {
        font-size: 24px;
        line-height: 1;
      }

      .hero-title {
        margin: 0;
        font-size: 20px;
        font-weight: 700;
        color: var(--el-text-color-primary);
        letter-spacing: -0.2px;
      }
    }

    .hero-subtitle {
      margin: 0;
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }

  .hero-meta {
    display: flex;
    align-items: center;
    gap: 12px;

    .tenant-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--el-fill-color-light);
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 13px;
      border: 1px solid var(--el-border-color-extra-light);

      .tenant-label {
        color: var(--el-text-color-secondary);
      }

      .tenant-name {
        font-weight: 600;
        color: var(--el-color-primary);
      }
    }

    .system-status-badge {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: #ecfdf5;
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 13px;
      color: #059669;
      font-weight: 500;
      border: 1px solid #a7f3d0;

      .status-pulse {
        width: 7px;
        height: 7px;
        border-radius: 50%;
        background: #10b981;
        box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.25);
        animation: kubo-pulse 2s infinite ease-in-out;
      }
    }
  }

  .hero-workflow {
    display: flex;
    align-items: center;
    gap: 10px;
    padding-top: 14px;
    border-top: 1px dashed var(--el-border-color-lighter);
    flex-wrap: wrap;

    .workflow-label {
      font-size: 12px;
      font-weight: 600;
      color: var(--el-text-color-placeholder);
      text-transform: uppercase;
      letter-spacing: 0.5px;
      flex-shrink: 0;
    }

    .workflow-steps {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      .workflow-step-item {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 5px 10px;
        border-radius: 6px;
        background: var(--el-fill-color-lighter);
        cursor: pointer;
        font-size: 13px;
        color: var(--el-text-color-regular);
        transition: all 0.2s ease;

        &:hover {
          background: var(--el-color-primary-light-9);
          color: var(--el-color-primary);
          transform: translateY(-1px);
        }

        .step-num {
          font-size: 10px;
          font-weight: 700;
          color: var(--el-text-color-secondary);
          background: var(--el-fill-color-dark);
          padding: 1px 4px;
          border-radius: 3px;
        }

        .step-icon {
          font-size: 14px;
        }

        .step-name {
          font-weight: 500;
        }

        .step-divider {
          margin-left: 4px;
          font-size: 12px;
          color: var(--el-text-color-placeholder);
        }
      }
    }
  }
}

.workbench-section {
  display: flex;
  flex-direction: column;
}

.modules-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.module-group {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  gap: 16px;

  .module-header {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 10px;

    .header-left {
      display: flex;
      align-items: center;
      gap: 10px;

      .module-tag {
        font-size: 11px;
        font-weight: 600;
        padding: 2px 8px;
        border-radius: 4px;
        background: var(--el-color-primary-light-9);
        color: var(--el-color-primary);
        letter-spacing: 0.3px;
      }

      .module-title {
        margin: 0;
        font-size: 16px;
        font-weight: 700;
        color: var(--el-text-color-primary);
      }
    }

    .module-subtitle {
      font-size: 13px;
      color: var(--el-text-color-secondary);
    }
  }

  .actions-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
    gap: 14px;
  }

  .action-card {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 16px;
    background: var(--el-fill-color-lighter);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.22s cubic-bezier(0.16, 1, 0.3, 1);
    position: relative;

    &:hover {
      background: var(--el-bg-color);
      border-color: var(--badge-color);
      box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
      transform: translateY(-2px);

      .action-arrow {
        color: var(--badge-color);
        transform: translateX(3px);
      }
    }

    .action-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: var(--badge-bg);
      color: var(--badge-color);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .action-info {
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex: 1;
      min-width: 0;

      .action-title {
        font-size: 15px;
        font-weight: 600;
        color: var(--el-text-color-primary);
        line-height: 1.3;
      }

      .action-desc {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
    }

    .action-arrow {
      color: var(--el-text-color-placeholder);
      display: flex;
      align-items: center;
      transition: all 0.2s ease;
      flex-shrink: 0;
    }
  }
}

@media (max-width: 768px) {
  .workbench-hero {
    padding: 16px;

    .hero-workflow {
      display: none;
    }
  }

  .module-group {
    padding: 16px;

    .module-subtitle {
      display: none;
    }
  }
}
</style>

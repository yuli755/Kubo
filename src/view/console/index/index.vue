<script lang="ts" setup>
import type { IJson } from '@airpower/web'
import { APanel } from '@airpower/web'
import { computed, ref } from 'vue'
import { Permissions } from '@/config/permissions'
import { getRouter } from '@/router/appRouter'
import { usePermissionStore } from '@/stores/permission'
import BusinessOverview from './BusinessOverview.vue'
import SecurityDashboard from './SecurityDashboard.vue'

/**
 * 安全态势看板只在持有数据权限管理身份时渲染：其数据来自跨租户的
 * `dataResource/*` 管理端点（后端鉴权），对普通业务用户既是噪声也是越权面。
 */
const canViewSecurity = computed(() => usePermissionStore().hasAny(
  Permissions.System.DataResource.getDetail,
  Permissions.System.DataPolicy.getDetail,
  Permissions.System.DataFieldPolicy.getDetail,
))

const modules = ref<IJson[]>([
  {
    title: 'ERP资产管理',
    actions: [
      { name: '人员和权限', desc: '统一管理人员、角色、权限等信息', path: '/console/personnel/user/list' },
      { name: '采购和销售渠道', desc: '通过供应商、客户、单价、单据了解企业采购和销售的支出和收入', path: '/console/channel/customer/list' },
      { name: '物料和设备', desc: '管理企业内部的物料、设备等资产', path: '/console/asset/material/list' },
      { name: '企业和工厂模型', desc: '管理企业的组织架构、工厂的生产单元、仓库的存储结构', path: '/console/factory/storage/list' },
    ],
  },
  {
    title: 'WMS仓储管理',
    actions: [
      { name: '库存信息概览', desc: '实时查看仓库各个单位的存储情况', path: '/console/wms/inventory/list' },
      { name: '多种入库方式', desc: '关联生产、采购以及其他方式的入库，来源一目了然', path: '/console/wms/input/list' },
      { name: '库存盘点和移动', desc: '随时盘点库存、移动存储的物料', path: '/console/wms/move/list' },
      { name: '多种出库方式', desc: '关联销售、生产领料等其他方式的出库', path: '/console/wms/output/list' },
    ],
  },
  {
    title: 'MES生产管理',
    actions: [
    ],
  },
  {
    title: '系统管理',
    actions: [
      { name: '菜单管理', desc: '管理系统菜单结构与路由配置', path: '/console/system/menu/list' },
      { name: '权限管理', desc: '管理系统权限标识与分配', path: '/console/system/permission/list' },
      { name: '系统配置', desc: '管理系统参数与业务规则', path: '/console/system/config/list' },
      { name: '编码规则', desc: '管理各业务模块的单据编码规则', path: '/console/system/coderule/list' },
    ],
  },
])

function goPage(path: string) {
  getRouter().push(path)
}
</script>

<template>
  <APanel>
    <div class="home">
      <BusinessOverview class="business" />
      <SecurityDashboard
        v-if="canViewSecurity"
        class="security"
      />
      <div class="index">
        <div
          v-for="(module, index) in modules"
          :key="index"
          class="module"
        >
          <div class="title">
            {{ module.title }}
          </div>
          <div class="actions">
            <div
              v-for="action in module.actions"
              :key="action.name"
              class="action"
              @click="goPage(action.path)"
            >
              <div class="name">
                {{ action.name }}
              </div>
              <div class="desc">
                {{ action.desc }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </APanel>
</template>

<style lang="scss" scoped>
.home {
  position: absolute;
  inset: 10px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.business,
.security {
  flex-shrink: 0;
}

.index {
  display: flex;
  flex-direction: column;
  flex: 1;

  .module {
    display: flex;
    flex-direction: column;
    margin: 5px;
    flex: 1;
    border-bottom: 3px solid var(--el-color-primary-light-9);
    padding: 10px 0;
    background: var(--el-color-primary-light-9);
    border-radius: 10px;

    .title {
      font-size: 18px;
      font-weight: bold;
      color: var(--el-text-color-primary);
      padding: 10px 20px;
    }

    .actions {
      border-radius: 20px;
      display: flex;
      flex-direction: row;
      flex: 1;
      overflow: hidden;
      overflow-x: auto;
      white-space: nowrap;
      padding: 0 10px;

      .action {
        background: var(--el-bg-color);
        padding: 10px;
        margin: 0 5px;
        width: 25%;
        min-width: 300px;
        border-radius: 10px;
        display: flex;
        justify-content: center;
        align-items: center;
        cursor: pointer;
        transition: all 0.3s;
        flex-direction: column;

        .name {
          color: var(--el-text-color-primary);
          margin-bottom: 5px;
          font-size: 18px;
          font-weight: bold;
          transition: all 0.3s;
        }

        .desc {
          color: var(--el-text-color-secondary);
          font-size: 14px;
          height: 0;
          word-break: break-word;
          white-space: break-spaces;
          opacity: 0;
          transition: all 0.3s;
          padding: 0 20px;
        }
      }

      .action:hover {
        transform: scale(0.95);

        .name {
          margin-top: -40px;
          font-size: 14px;
        }

        .desc {
          display: block;
          transform: scale(1);
          opacity: 1;
        }
      }
    }
  }
}
</style>

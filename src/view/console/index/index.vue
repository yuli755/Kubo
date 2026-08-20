<script lang="ts" setup>
import type { IJson } from '@airpower/web'
import { RouterUtil } from '@airpower/web'
import { APanel } from '@airpower/web'
import { ref } from 'vue'

const modules = ref<IJson[]>([
  {
    title: 'ERP资产管理',
    actions: [
      { name: '人员和权限', desc: '统一管理人员、角色、权限等信息', path: '/console/personnel/user' },
      { name: '采购和销售渠道', desc: '通过供应商、客户、单价、单据了解企业采购和销售的支出和收入', path: '/console/channel/customer' },
      { name: '物料和设备', desc: '管理企业内部的物料、设备等资产', path: '/console/asset/material' },
      { name: '企业和工厂模型', desc: '管理企业的组织架构、工厂的生产单元、仓库的存储结构', path: '/console/factory/storage' },
    ],
  },
  {
    title: 'WMS仓储管理',
    actions: [
      { name: '库存信息概览', desc: '实时查看仓库各个单位的存储情况', path: '/console/wms/inventory' },
      { name: '多种入库方式', desc: '关联生产、采购以及其他方式的入库，来源一目了然', path: '/console/wms/input' },
      { name: '库存盘点和移动', desc: '随时盘点库存、移动存储的物料', path: '/console/wms/move' },
      { name: '多种出库方式', desc: '关联销售、生产领料等其他方式的出库', path: '/console/wms/output' },
    ],
  },
  {
    title: 'MES生产管理',
    actions: [
      { name: '生产准备', desc: '管理工艺、工序、配料单等生产信息准备', path: '/console/mes/routing' },
      { name: '生产计划', desc: '可关联客户的销售单，提前指定生产计划', path: '/console/mes/plan' },
      { name: '生产订单', desc: '可关联生产计划，可直接下单生产', path: '/console/mes/order' },
      { name: '生产管控', desc: '控制适当的领料、投料，规范生产过程', path: '/console/mes/picking' },
    ],
  },
  {
    title: '系统管理',
    actions: [
      { name: '菜单管理', desc: '管理系统菜单结构与路由配置', path: '/console/system/menu' },
      { name: '权限管理', desc: '管理系统权限标识与分配', path: '/console/system/permission' },
      { name: '系统配置', desc: '管理系统参数与业务规则', path: '/console/system/config' },
      { name: '编码规则', desc: '管理各业务模块的单据编码规则', path: '/console/system/coderule' },
    ],
  },
])

function goPage(path: string) {
  RouterUtil.router.push(path)
}
</script>

<template>
  <APanel>
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
  </APanel>
</template>

<style lang="scss" scoped>
.index {
  position: absolute;
  inset: 10px;
  display: flex;
  flex-direction: column;
  overflow-y: auto;

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
      color: #333333;
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
        background: white;
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
          color: #333;
          margin-bottom: 5px;
          font-size: 18px;
          font-weight: bold;
          transition: all 0.3s;
        }

        .desc {
          color: #aaa;
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

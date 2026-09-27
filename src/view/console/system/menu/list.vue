<script lang="ts" setup>
import type { MenuEntity } from '@/model/system/menu/MenuEntity'
import {
  Box,
  Coin,
  Compass,
  Document,
  Folder,
  Goods,
  HelpFilled,
  Key,
  List,
  Lock,
  Management,
  Monitor,
  Operation,
  Platform,
  Setting,
  ShoppingCart,
  Tickets,
  TrendCharts,
  User,
  Van,
} from '@element-plus/icons-vue'
import { ElIcon, ElLink, ElOption, ElSelect } from 'element-plus'
import { markRaw, ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KMenuService } from '@/hook/system/useMenu'

/**
 * # 菜单管理（F4 批 6 · 新底座）
 *
 * 树表（`useTable({ tree: true })` → 后端 `getTreeList`），KTable 自己渲染层级与
 * 「添加子级」按钮 —— 旧页面的 `useTableTree` + `ATable is-tree show-add-row` 是同一件事。
 *
 * 三个行为口径照抄旧页面（它们都是**业务规则**，不是样式）：
 * - **有子菜单的行不许删**（`disable-delete`），否则一次删掉一棵子树；
 * - **新增子菜单时把父级放进 `preset`**（旧页面用 `beforeAddRow` 改 `param.parent`）——
 *   父级关系是提交体的一部分，用户在表单里看不到也不该手填；
 * - 路径列渲染成链接（点开就是该菜单对应的路由）。
 *
 * ## 字段选择器优化（能选择绝不让用户手动输入）
 * - **组件地址**：提供全量已注册控制台视图下拉选项，支持模糊检索与自由创建，选中时自动建议并预填路由路径与菜单名称；
 * - **菜单图标**：提供系统常用语义化图标下拉集合，带实时图标图形预览与语义说明，消除盲敲拼写错误。
 */
const table = useTable(KMenuService, { queryKey: ['system:menu'], tree: true })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const preset = ref<Record<string, unknown>>({})

const COMPONENT_OPTIONS = [
  { shortName: '工作台', label: '[概览] 工作台 (index)', value: '/src/view/console/index/index.vue', defaultPath: '/console/index' },
  // 渠道供应链
  { shortName: '采购单', label: '[渠道] 采购单 (purchase)', value: '/src/view/console/channel/purchase/list.vue', defaultPath: '/console/channel/purchase/list' },
  { shortName: '采购价格', label: '[渠道] 采购价格 (purchasePrice)', value: '/src/view/console/channel/purchasePrice/list.vue', defaultPath: '/console/channel/purchasePrice/list' },
  { shortName: '销售单', label: '[渠道] 销售单 (sale)', value: '/src/view/console/channel/sale/list.vue', defaultPath: '/console/channel/sale/list' },
  { shortName: '销售价格', label: '[渠道] 销售价格 (salePrice)', value: '/src/view/console/channel/salePrice/list.vue', defaultPath: '/console/channel/salePrice/list' },
  { shortName: '客户管理', label: '[渠道] 客户管理 (customer)', value: '/src/view/console/channel/customer/list.vue', defaultPath: '/console/channel/customer/list' },
  { shortName: '供应商管理', label: '[渠道] 供应商管理 (supplier)', value: '/src/view/console/channel/supplier/list.vue', defaultPath: '/console/channel/supplier/list' },
  // 仓储物流
  { shortName: '入库单', label: '[仓储] 入库单 (input)', value: '/src/view/console/wms/input/list.vue', defaultPath: '/console/wms/input/list' },
  { shortName: '出库单', label: '[仓储] 出库单 (output)', value: '/src/view/console/wms/output/list.vue', defaultPath: '/console/wms/output/list' },
  { shortName: '移库单', label: '[仓储] 移库单 (move)', value: '/src/view/console/wms/move/list.vue', defaultPath: '/console/wms/move/list' },
  { shortName: '实时库存', label: '[仓储] 实时库存态势 (inventory)', value: '/src/view/console/wms/inventory/list.vue', defaultPath: '/console/wms/inventory/list' },
  // 资产管理
  { shortName: '物料管理', label: '[资产] 物料档案 (material)', value: '/src/view/console/asset/material/list.vue', defaultPath: '/console/asset/material/list' },
  { shortName: '合同管理', label: '[资产] 合同档案 (contract)', value: '/src/view/console/asset/contract/list.vue', defaultPath: '/console/asset/contract/list' },
  // 制造工厂
  { shortName: '仓库管理', label: '[工厂] 仓库库区 (storage)', value: '/src/view/console/factory/storage/list.vue', defaultPath: '/console/factory/storage/list' },
  { shortName: '生产单元', label: '[工厂] 生产单元 (structure)', value: '/src/view/console/factory/structure/list.vue', defaultPath: '/console/factory/structure/list' },
  // 组织人事
  { shortName: '用户管理', label: '[组织] 用户账号 (user)', value: '/src/view/console/personnel/user/list.vue', defaultPath: '/console/personnel/user/list' },
  { shortName: '部门组织', label: '[组织] 部门组织 (department)', value: '/src/view/console/personnel/department/list.vue', defaultPath: '/console/personnel/department/list' },
  { shortName: '角色权限', label: '[组织] 角色管理 (role)', value: '/src/view/console/personnel/role/list.vue', defaultPath: '/console/personnel/role/list' },
  // 系统设置
  { shortName: '菜单管理', label: '[系统] 菜单管理 (menu)', value: '/src/view/console/system/menu/list.vue', defaultPath: '/console/system/menu/list' },
  { shortName: '权限清单', label: '[系统] 权限清单 (permission)', value: '/src/view/console/system/permission/list.vue', defaultPath: '/console/system/permission/list' },
  { shortName: '编码规则', label: '[系统] 编码规则 (coderule)', value: '/src/view/console/system/coderule/list.vue', defaultPath: '/console/system/coderule/list' },
  { shortName: '系统参数', label: '[系统] 系统参数 (config)', value: '/src/view/console/system/config/list.vue', defaultPath: '/console/system/config/list' },
  { shortName: '计量单位', label: '[系统] 计量单位 (unit)', value: '/src/view/console/system/unit/list.vue', defaultPath: '/console/system/unit/list' },
  { shortName: '操作日志', label: '[系统] 操作日志 (log)', value: '/src/view/console/system/log/list.vue', defaultPath: '/console/system/log/list' },
  // 数据权限
  { shortName: '数据资源', label: '[权限] 数据资源 (resource)', value: '/src/view/console/system/datapermission/resource/list.vue', defaultPath: '/console/system/datapermission/resource/list' },
  { shortName: '行级数据策略', label: '[权限] 行级数据策略 (policy)', value: '/src/view/console/system/datapermission/policy/list.vue', defaultPath: '/console/system/datapermission/policy/list' },
  { shortName: '列级脱敏策略', label: '[权限] 列级脱敏策略 (fieldPolicy)', value: '/src/view/console/system/datapermission/fieldPolicy/list.vue', defaultPath: '/console/system/datapermission/fieldPolicy/list' },
]

const ICON_OPTIONS = [
  { label: '工作台/平台', value: 'Platform', icon: markRaw(Platform) },
  { label: '采购/购物车', value: 'ShoppingCart', icon: markRaw(ShoppingCart) },
  { label: '销售/物流车', value: 'Van', icon: markRaw(Van) },
  { label: '仓储/包装箱', value: 'Box', icon: markRaw(Box) },
  { label: '物料/商品货', value: 'Goods', icon: markRaw(Goods) },
  { label: '单据/凭证', value: 'Tickets', icon: markRaw(Tickets) },
  { label: '资金/价格', value: 'Coin', icon: markRaw(Coin) },
  { label: '用户/人员', value: 'User', icon: markRaw(User) },
  { label: '管理/组织', value: 'Management', icon: markRaw(Management) },
  { label: '系统/设置', value: 'Setting', icon: markRaw(Setting) },
  { label: '权限/钥匙', value: 'Key', icon: markRaw(Key) },
  { label: '安全/锁定', value: 'Lock', icon: markRaw(Lock) },
  { label: '报表/趋势', value: 'TrendCharts', icon: markRaw(TrendCharts) },
  { label: '监控/屏幕', value: 'Monitor', icon: markRaw(Monitor) },
  { label: '文档/合同', value: 'Document', icon: markRaw(Document) },
  { label: '清单/明细', value: 'List', icon: markRaw(List) },
  { label: '工序/操作', value: 'Operation', icon: markRaw(Operation) },
  { label: '向导/罗盘', value: 'Compass', icon: markRaw(Compass) },
  { label: '目录/分类', value: 'Folder', icon: markRaw(Folder) },
  { label: '关于/帮助', value: 'HelpFilled', icon: markRaw(HelpFilled) },
]

function onComponentChanged(val: string, model: MenuEntity) {
  const hit = COMPONENT_OPTIONS.find(item => item.value === val)
  if (hit) {
    if (!model.path) {
      model.path = hit.defaultPath
    }
    if (!model.name) {
      model.name = hit.shortName
    }
  }
}

function openCreate() {
  editingId.value = null
  preset.value = {}
  editorVisible.value = true
}

function openEdit(row: MenuEntity) {
  editingId.value = row.id
  preset.value = {}
  editorVisible.value = true
}

/** 新增子菜单：把父级塞进提交体（`parentId` 由后端读取，`parent` 让表单显示"父菜单"） */
function openAddChild(row: MenuEntity) {
  editingId.value = null
  preset.value = { parentId: row.id, parent: row }
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="菜单管理"
      description="菜单 / 路由 / 组件地址的层级维护：改完刷新页面即可看到导航变化"
    >
      <KTable
        :table="table"
        :actions="[]"
        :disable-delete="(row: MenuEntity) => (row.children?.length ?? 0) > 0"
        :permissions="{
          add: Permissions.System.Menu.add,
          addChild: Permissions.System.Menu.addRow,
          edit: Permissions.System.Menu.update,
          delete: Permissions.System.Menu.delete,
        }"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
        @add-child="openAddChild"
      >
        <template #cell-path="{ row }">
          <ElLink
            v-if="row.path"
            type="primary"
            :href="row.path"
            class="font-mono text-xs"
          >
            {{ row.path }}
          </ElLink>
          <span
            v-else
            class="text-slate-400"
          >-</span>
        </template>
        <template #cell-component="{ row }">
          <code
            v-if="row.component"
            class="font-mono text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700"
          >
            {{ row.component }}
          </code>
          <span
            v-else
            class="text-slate-400"
          >-</span>
        </template>
      </KTable>
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KMenuService"
      :preset="preset"
      width="640px"
      @saved="onSaved"
    >
      <!-- 组件地址选择器：下拉速选已有视图，支持自动衍生推荐路径与名称 -->
      <template #field-component="{ form: formData }">
        <ElSelect
          v-model="(formData as MenuEntity).component"
          filterable
          allow-create
          clearable
          placeholder="请选择或输入组件路径..."
          @change="(val: string) => onComponentChanged(val, formData as MenuEntity)"
        >
          <ElOption
            v-for="item in COMPONENT_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <div class="menu-component-option">
              <span class="menu-component-label">{{ item.label }}</span>
              <span class="menu-component-path">{{ item.value }}</span>
            </div>
          </ElOption>
        </ElSelect>
      </template>

      <!-- 菜单图标选择器：下拉图形化预览 -->
      <template #field-icon="{ form: formData }">
        <ElSelect
          v-model="(formData as MenuEntity).icon"
          filterable
          allow-create
          clearable
          placeholder="请选择或输入菜单图标..."
        >
          <ElOption
            v-for="item in ICON_OPTIONS"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          >
            <div class="menu-icon-option">
              <ElIcon><component :is="item.icon" /></ElIcon>
              <span>{{ item.label }} ({{ item.value }})</span>
            </div>
          </ElOption>
        </ElSelect>
      </template>

      <template #extra="{ form: formData }">
        <el-form-item
          v-if="(formData as MenuEntity).parent"
          label="父菜单"
        >
          {{ (formData as MenuEntity).parent?.name }}
        </el-form-item>
      </template>
    </KFormDialog>
  </div>
</template>

<style scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}

.menu-component-option {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  width: 100%;
}

.menu-component-label {
  font-weight: 500;
}

.menu-component-path {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-family: monospace;
}

.menu-icon-option {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>

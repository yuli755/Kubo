<script lang="ts" setup>
import type { CustomerEntity } from '@/model/channel/customer/CustomerEntity'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { KCustomerService } from '@/hook/channel/useCustomer'
import { useTable } from '@/hook/kubo/useTable'

/**
 * # 客户列表（F4 批 5 · 新底座）
 *
 * 与旧页面的三条对齐：
 *
 * | 点 | 旧写法 | 这里 |
 * | --- | --- | --- |
 * | 列表 | `useTanStackTable(CustomerService, { editView })` | `useTable(KCustomerService)` |
 * | 编辑 | `editor.vue` + `useEditor` + `AFormField` | `KFormDialog` 按 `@Form` 元数据渲染（编辑器文件已删） |
 * | 删除 | `delete-permission` | `permissions.delete`（客户有删除端点，保留） |
 *
 * **不显示「启用/禁用」**：`CustomerController` 的 `@Extends` 只加回 Export/QueryExport，
 * 没有 Enable/Disable 端点；而 `v-permission` 遇到未传的权限码是**不拦截**的
 * （见 `directives/permission.ts`），所以必须显式 `hide-enable`，否则按钮点下去必然报错。
 * 同理不显示导出：`Permissions.Channel.Customer` 里没有 export 码。
 */
const table = useTable(KCustomerService, {
  queryKey: ['channel:customer'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: CustomerEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="客户"
      description="销售单与销售价的客户档案"
    >
      <KTable
        :permissions="{
          add: Permissions.Channel.Customer.add,
          edit: Permissions.Channel.Customer.update,
          delete: Permissions.Channel.Customer.delete,
        }"
        :table="table"
        hide-enable
        @add="openCreate"
        @edit="openEdit"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :columns="2"
      :service="KCustomerService"
      title="客户"
      width="600px"
      @saved="onSaved"
    />
  </div>
</template>

<style lang="scss" scoped>
.k-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
}
</style>

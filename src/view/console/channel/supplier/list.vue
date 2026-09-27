<script lang="ts" setup>
import type { SupplierEntity } from '@/model/channel/supplier/SupplierEntity'
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KPanel from '@/component/framework/KPanel.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { KSupplierService } from '@/hook/channel/useSupplier'
import { useTable } from '@/hook/kubo/useTable'
import SupplierDetail from './component/detail.vue'

/**
 * # 供应商列表（F4 批 5 · 新底座）
 *
 * 与旧页面：
 * - 只有「新增 / 编辑」两个权限码（`Permissions.Channel.Supplier` 没有 delete / export），
 *   `SupplierController` 也 `exclude = Curd.Delete` —— 所以 `hide-delete` + `hide-enable` 都要显式给；
 * - 「查看」按钮是本批补的：采购价的供应商列点得开详情（旧页面是 `APayload :view` 内联打开），
 *   列表里给同一份详情弹窗一个入口，两条路径共用 `component/detail.vue`。
 */
const table = useTable(KSupplierService, {
  queryKey: ['channel:supplier'],
})

const editorVisible = ref(false)
const editingId = ref<number | null>(null)
const detailVisible = ref(false)
const detailId = ref<number | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: SupplierEntity) {
  editingId.value = row.id
  editorVisible.value = true
}

function openDetail(row: SupplierEntity) {
  detailId.value = row.id
  detailVisible.value = true
}

function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KPanel
      title="供应商"
      description="采购单与采购价的供应商档案"
    >
      <KTable
        :actions="[{ label: '查看', handler: openDetail }]"
        :permissions="{
          add: Permissions.Channel.Supplier.add,
          edit: Permissions.Channel.Supplier.update,
        }"
        :table="table"
        hide-delete
        hide-enable
        @add="openCreate"
        @edit="openEdit"
        @row-dblclick="openDetail"
      />
    </KPanel>

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :columns="2"
      :service="KSupplierService"
      title="供应商"
      width="600px"
      @saved="onSaved"
    />

    <SupplierDetail
      :id="detailId"
      v-model="detailVisible"
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

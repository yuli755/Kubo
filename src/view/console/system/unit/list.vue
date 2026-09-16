<script lang="ts" setup>
import { ref } from 'vue'
import KFormDialog from '@/component/framework/KFormDialog.vue'
import KTable from '@/component/framework/KTable.vue'
import { Permissions } from '@/config/permissions'
import { useTable } from '@/hook/kubo/useTable'
import { KUnitService } from '@/hook/system/useUnit'

/**
 * # 计量单位（F2 试点页 · 新底座）
 *
 * 这一页是新底座的**样板**：整页零 `@airpower/web` 依赖。
 *
 * ```diff
 * - import { APanel, ATable } from '@airpower/web'      // 旧
 * - import { useTanStackTable } from '@/hook/useTanStackTable'
 * - const hook = useTanStackTable(UnitService, { queryKey, editView })
 * - <APanel><ATable :use-hook="hook" :edit-permission="…"/></APanel>
 * + import { useTable } from '@/hook/kubo/useTable'
 * + import KTable from '@/component/framework/KTable.vue'
 * + const table = useTable(KUnitService, { queryKey: ['system:unit'] })
 * + <KTable :table="table" :permissions="{ edit, delete, add, enable }" @edit="openEditor" />
 * ```
 *
 * 页面的职责只剩三件事：**给权限码、开弹窗、刷新列表** ——
 * 查询、分页、排序、删除确认、启用禁用、空态判定都在 `useTable` + `KTable` 里，
 * 页面不再持有 `request/response` 这类传输层对象。
 */

const table = useTable(KUnitService, { queryKey: ['system:unit'] })

const editorVisible = ref(false)
const editingId = ref<number | null>(null)

function openCreate() {
  editingId.value = null
  editorVisible.value = true
}

function openEdit(row: { id: number }) {
  editingId.value = row.id
  editorVisible.value = true
}

/** 保存后刷新列表（`KFormDialog` 只负责提交，不做"提交完该干什么"的决定） */
function onSaved() {
  table.reload()
}
</script>

<template>
  <div class="k-page">
    <KTable
      :table="table"
      :permissions="{
        add: Permissions.System.Unit.add,
        edit: Permissions.System.Unit.update,
        delete: Permissions.System.Unit.delete,
        enable: Permissions.System.Unit.update,
      }"
      @add="openCreate"
      @edit="openEdit"
    />

    <KFormDialog
      :id="editingId"
      v-model="editorVisible"
      :service="KUnitService"
      @saved="onSaved"
    />
  </div>
</template>

<style scoped>
.k-page {
  padding: 16px;
}
</style>

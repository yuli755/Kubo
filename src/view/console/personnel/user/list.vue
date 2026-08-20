<script lang="ts" setup>
import type { Ref } from 'vue'

import { APanel, ATable, ATreeBox, QueryRequest } from '@airpower/web'
import { ref } from 'vue'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'
import { DepartmentService } from '@/model/personnel/department/DepartmentService'
import { UserService } from '@/model/personnel/user/UserService'
import { Permissions } from '@/config/permissions'
import { UserEditor } from './component'

const hook = useTanStackTable(UserService, {
  queryKey: ['personnel:user'],
  editView: UserEditor,
})

const departmentList: Ref<DepartmentEntity[]> = ref([])

const isLoadingTree = ref(false)

function departmentChanged(department?: DepartmentEntity) {
  hook.request.value.filter.departmentId = department?.id || undefined
  hook.onGetList()
}

async function getDepartmentList() {
  departmentList.value = await DepartmentService.create(isLoadingTree).getList(new QueryRequest(DepartmentEntity))
}

getDepartmentList()
</script>

<template>
  <ATreeBox
    :tree-data="departmentList"
    searchable
    @change="departmentChanged"
  >
    <APanel>
      <ATable
        :use-hook="hook"
        :add-permission="Permissions.Personnel.User.add"
        :delete-permission="Permissions.Personnel.User.delete"
        :disable-permission="Permissions.Personnel.User.disable"
        :edit-permission="Permissions.Personnel.User.update"
        :enable-permission="Permissions.Personnel.User.enable"
        ctrl-width="150"
        show-enable-and-disable
      />
    </APanel>
  </ATreeBox>
</template>

<style lang="scss" scoped></style>

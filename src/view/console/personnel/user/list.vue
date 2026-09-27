<script lang="ts" setup>
import type { Ref } from 'vue'

import { APanel, APhone, ATable, ATreeBox, QueryRequest } from '@airpower/web'
import { onMounted, ref } from 'vue'
import { useMaskPolicy } from '@/composables/useMaskPolicy'
import { Permissions } from '@/config/permissions'
import { DepartmentService } from '@/hook/personnel/useDepartment'
import { UserService } from '@/hook/personnel/useUser'
import { useTanStackTable } from '@/hook/useTanStackTable'
import { DepartmentEntity } from '@/model/personnel/department/DepartmentEntity'
import { UserEditor } from './component'

const hook = useTanStackTable(UserService, {
  queryKey: ['personnel:user'],
  editView: UserEditor,
})

// G-2（审核记录项 2 收尾）：phone/email 是后端 @SensitiveField(personnel.user)
// 字段，此前只有编辑器和价格类列表接了 my-mask 策略，本表还是静态 DesensitizeType
// 掩码——有明文授权的角色（含 root）也被固定规则遮住，策略形同虚设。插槽优先于
// 框架内置渲染（renderSlot 先于 desensitize 分派），改按后端策略逐人决定。
// idCard 保持现状：列默认隐藏 + 列表对所有人遮罩是「防瞄屏」的有意设计，
// 明文可见性由详情表单与后端策略决定（见 UserEntity.ts 注释）。
const piiPolicy = useMaskPolicy('personnel.user', ['phone', 'email'])
onMounted(() => piiPolicy.load())

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
      >
        <template #phone="{ data }">
          <APhone
            v-if="!piiPolicy.restricted('phone')"
            :phone="data.phone"
          />
          <span v-else>{{ piiPolicy.masked('phone', data.phone) }}</span>
        </template>
        <template #email="{ data }">
          <span>{{ piiPolicy.restricted('email') ? piiPolicy.masked('email', data.email) : data.email }}</span>
        </template>
      </ATable>
    </APanel>
  </ATreeBox>
</template>

<style lang="scss" scoped></style>

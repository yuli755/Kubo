<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { RoleService } from '@/hook/personnel/useRole'
import { RoleEntity } from '@/model/personnel/role/RoleEntity'
import { RoleEditor } from '.'

const props = defineProps(DialogProps.withSelector<RoleEntity>())
</script>

<template>
  <ASelector
    :editor="RoleEditor"
    :entity="RoleEntity"
    :props="props"
    :service="RoleService"
  />
</template>

<style lang="scss" scoped></style>

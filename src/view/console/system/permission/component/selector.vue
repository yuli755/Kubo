<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { PermissionService } from '@/hook/system/usePermission'
import { PermissionEntity } from '@/model/system/permission/PermissionEntity'

const props = defineProps(DialogProps.withSelector<PermissionEntity>())
</script>

<template>
  <ASelector
    :entity="PermissionEntity"
    :props="props"
    :service="PermissionService"
  />
</template>

<style lang="scss" scoped></style>

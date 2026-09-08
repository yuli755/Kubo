<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { MenuService } from '@/hook/system/useMenu'
import { MenuEntity } from '@/model/system/menu/MenuEntity'

const props = defineProps(DialogProps.withSelector<MenuEntity>())
</script>

<template>
  <ASelector
    :entity="MenuEntity"
    :props="props"
    :service="MenuService"
  />
</template>

<style lang="scss" scoped></style>

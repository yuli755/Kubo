<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { StructureService } from '@/hook/factory/useStructure'
import { StructureEntity } from '@/model/factory/structure/StructureEntity'

const props = defineProps(DialogProps.withSelector<StructureEntity>())
</script>

<template>
  <ASelector
    :entity="StructureEntity"
    :props="props"
    :service="StructureService"
    :un-paginate="true"
  />
</template>

<style lang="scss" scoped></style>

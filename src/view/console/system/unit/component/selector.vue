<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { UnitService } from '@/hook/system/useUnit'
import { UnitEntity } from '@/model/system/unit/UnitEntity'
import { UnitEditor } from '.'

const props = defineProps(DialogProps.withSelector<UnitEntity>())
</script>

<template>
  <ASelector
    :editor="UnitEditor"
    :entity="UnitEntity"
    :props="props"
    :service="UnitService"
    width="600px"
  />
</template>

<style lang="scss" scoped></style>

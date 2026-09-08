<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { PlanService } from '@/hook/mes/usePlan'
import { PlanEntity } from '@/model/mes/plan/PlanEntity'
import { PlanEditor } from '.'

const props = defineProps(DialogProps.withSelector<PlanEntity>())
</script>

<template>
  <ASelector
    :editor="PlanEditor"
    :entity="PlanEntity"
    :props="props"
    :service="PlanService"
    width="60%"
  />
</template>

<style lang="scss" scoped></style>

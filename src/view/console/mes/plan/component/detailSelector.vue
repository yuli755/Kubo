<script lang="ts" setup>
import { AButton, ADialog, ATable, DialogProps } from '@airpower/web'
import { ref } from 'vue'

import { PlanService } from '@/hook/mes/usePlan'
import { PlanDetailEntity } from '@/model/mes/plan/PlanDetailEntity'
import { PlanEntity } from '@/model/mes/plan/PlanEntity'

const props = defineProps(DialogProps.withSelector<PlanDetailEntity, PlanEntity>())

const isLoading = ref(false)
const list = ref<PlanDetailEntity[]>([])

const selectList = ref(props.selectList)

function onSelected(data: PlanDetailEntity[]) {
  selectList.value = data
}

const plan = ref(new PlanEntity())

async function getPlan() {
  plan.value = await PlanService.create(isLoading).getDetail(props.param.id)
  list.value = plan.value.details
}

getPlan()
</script>

<template>
  <ADialog
    :disable-confirm="isMultiple && selectList.length === 0"
    :hide-footer="!isMultiple"
    :loading="isLoading"
    is-selector
    title="选择计划明细"
    width="70%"
    @cancel="onCancel"
    @confirm="onConfirm(selectList)"
  >
    <ATable
      :data-list="list"
      :entity="PlanDetailEntity"
      :hide-ctrl="isMultiple"
      :select-list="selectList"
      :show-select="isMultiple"
      ctrl-width="80"
      hide-column-selector
      hide-delete
      hide-edit
      @selected="onSelected"
    >
      <template
        v-if="!isMultiple"
        #customRow="{ data }"
      >
        <!-- permission-exempt: 选择器确认只回填表单，不构成数据写入 -->
        <AButton
          :disabled="data.isDisabled"
          link
          @click="onConfirm(data)"
        >
          选择
        </AButton>
      </template>
    </ATable>
  </ADialog>
</template>

<style lang="scss" scoped></style>

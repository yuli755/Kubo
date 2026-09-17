<script lang="ts" setup>
import { ASelector, DialogProps } from '@airpower/web'
import { MaterialService } from '@/hook/asset/useMaterial'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'

/**
 * # 物料选择器（仍是 airpower `ASelector`）
 *
 * ⚠️ 为什么批 2 没顺手换掉它：它被 wms 入库/出库、channel 采购/销售/价格共 6 处引用，
 * 而那些页面要等批 4/5 才迁 —— 现在改成 `KSelector` 就得同时改 6 个未迁移页面。
 *
 * ⚠️ 已知能力缺口（临时）：原来 `:editor="MaterialEditor"` 让用户"在弹窗里新增一个物料"，
 * 物料编辑弹窗在批 2 换成 `KFormDialog`（页面级组件，没有 airpower `DialogProps` 契约）后，
 * 这个入口暂时没了 —— 选择器变成**只能选**。恢复的两种做法（批 4/5 二选一）：
 *   1. 迁移 wms/channel 页时一并换成 `KSelector`，由页面自己给"新增"入口；
 *   2. 若届时仍需要"选择器内新增"，把它做成 `KSelector` 的 `#toolbar-left` 插槽能力。
 * 这不是"忘了迁"，是**迁移顺序的必然过渡态**，写在这里防止后人对着一处静默缺失猜原因。
 */
const props = defineProps(DialogProps.withSelector<MaterialEntity>())
</script>

<template>
  <ASelector
    :entity="MaterialEntity"
    :props="props"
    :service="MaterialService"
  />
</template>

<style lang="scss" scoped></style>

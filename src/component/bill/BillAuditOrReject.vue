<script generic="B extends AbstractBaseBillEntity<D>, D extends AbstractBaseBillDetailEntity" lang="ts" setup>
import type { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
import type { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
import { ElButton } from 'element-plus'
import { vPermission } from '@/directives'

const { bill } = defineProps<{
  /**
   * ### 单据
   */
  bill: B
  /**
   * ### 审核权限码
   *
   * 传入则按权限控制「审核」按钮显隐；不传则始终展示。
   * 权限码应与后端 `PermissionUtil.get(EntityClass, 'audit')` 一致，
   * 形如 `Purchase_audit` / `Order_audit`。
   */
  auditPermission?: string
  /**
   * ### 驳回权限码
   *
   * 传入则按权限控制「驳回」按钮显隐；不传则始终展示。
   * 形如 `Purchase_reject` / `Order_reject`。
   */
  rejectPermission?: string
}>()

const emits = defineEmits<{
  onReject: [bill: B]
  onAudit: [bill: B]
}>()
</script>

<template>
  <!--
    批 0 迁移：`AButton link` → `ElButton link`（Element Plus 2.x 的 link 属性即文字按钮样式）。
    按钮的可用性仍来自单据实体自己的 `canAudit()/canReject()`（状态机判据在后端，
    前端只是回显），权限码仍走 `v-permission`。
  -->
  <ElButton
    v-permission="auditPermission"
    :disabled="!bill.canAudit()"
    link
    type="primary"
    @click="emits('onAudit', bill)"
  >
    审核
  </ElButton>
  <ElButton
    v-permission="rejectPermission"
    :disabled="!bill.canReject()"
    link
    type="danger"
    @click="emits('onReject', bill)"
  >
    驳回
  </ElButton>
</template>

<style lang="scss" scoped></style>

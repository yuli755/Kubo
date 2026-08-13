<script generic="B extends AbstractBaseBillEntity<D>, D extends AbstractBaseBillDetailEntity" lang="ts" setup>
import type { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
import type { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
import { AButton } from '@airpower/web'
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
  <AButton
    v-permission="auditPermission"
    :disabled="!bill.canAudit()"
    link
    @click="emits('onAudit', bill)"
  >
    审核
  </AButton>
  <AButton
    v-permission="rejectPermission"
    :disabled="!bill.canReject()"
    link
    @click="emits('onReject', bill)"
  >
    驳回
  </AButton>
</template>

<style lang="scss" scoped></style>

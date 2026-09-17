import type { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { DialogUtil } from '@airpower/web'
import SaleDetail from './detail.vue'

/**
 * # 打开销售单详情（跨域跳转的接缝）
 *
 * 同 `channel/purchase/component/openDetail.ts`：WMS 出库单详情要能从「销售出库」的单据点开来源销售单，
 * 而销售单详情属 channel 域、批 5 才迁。把打开方式留在 channel 这边，
 * wms 出库域就不必 import 任何 airpower 组件；批 5 只改本文件。
 */
export function openSaleDetail(bill?: SaleEntity): void {
  if (!bill?.id) {
    return
  }
  void DialogUtil.show(SaleDetail, bill)
}

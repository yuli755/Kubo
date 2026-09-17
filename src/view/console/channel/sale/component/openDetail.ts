import type { SaleEntity } from '@/model/channel/sale/SaleEntity'
import { DialogUtil } from '@/framework'
import SaleDetail from './detail.vue'

/**
 * # 打开销售单详情（跨域跳转的**接缝**）
 *
 * 调用方在 `src/view/console/wms/output/component/detail.vue`：出库单详情里
 * 「销售出库」的来源销售单要能点开。批 4 迁移 wms 时销售单详情还留在 airpower，
 * 所以这个函数当时用 airpower 的 `DialogUtil.show` + `DialogProps` 版本；
 * 批 5 换成 `@/framework` 的 `DialogUtil` + 新底座只读弹窗。
 *
 * **调用方一行未动** —— 接缝的意义就在这里。
 */
export function openSaleDetail(bill?: SaleEntity): void {
  if (!bill?.id) {
    return
  }
  void DialogUtil.show(SaleDetail, bill)
}

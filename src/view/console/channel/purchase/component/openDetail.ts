import type { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { DialogUtil } from '@/framework'
import PurchaseDetail from './detail.vue'

/**
 * # 打开采购单详情（跨域跳转的**接缝**）
 *
 * WMS 入库单详情里，采购入库的单据要能点开来源采购单。调用方在
 * `src/view/console/wms/input/component/detail.vue`，它只认这一个函数签名 ——
 * 批 4 迁移 wms 时，采购单详情还留在 airpower（批 5 才迁），所以把「怎么打开」
 * 留在 channel 这边，入库域因此清掉了 airpower 依赖。
 *
 * 批 5 已把内部实现换成新底座：
 * - `DialogUtil` 从 `@/framework` 来（契约与 airpower 版一致：`param` / `onConfirm` / `onCancel`）；
 * - `PurchaseDetail` 是 `KDialog + useDetail + KDetailTable` 的只读弹窗，
 *   仍按 `DialogUtil.show(view, bill)` 的命令式方式打开。
 *
 * **调用方一行未动** —— 这正是接缝该有的样子。
 */
export function openPurchaseDetail(bill?: PurchaseEntity): void {
  if (!bill?.id) {
    return
  }
  void DialogUtil.show(PurchaseDetail, bill)
}

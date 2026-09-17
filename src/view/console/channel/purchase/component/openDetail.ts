import type { PurchaseEntity } from '@/model/channel/purchase/PurchaseEntity'
import { DialogUtil } from '@airpower/web'
import PurchaseDetail from './detail.vue'

/**
 * # 打开采购单详情（跨域跳转的**接缝**）
 *
 * 为什么要单独一个文件：WMS 入库单详情里，采购入库的单据要能点开来源采购单。
 * 采购单详情属 **channel 域**（批 5 才迁到新底座），现在还是 airpower 组件
 * （`DialogProps.withParam` + `DialogUtil.show`）。如果让 wms 页面直接 import 采购组件，
 * 出库/入库域就永远清不掉 airpower 依赖 —— 所以把「怎么打开采购单详情」这件事留在 channel 这边，
 * wms 只调用这个函数。
 *
 * 批 5 迁 channel 时**只需改本文件的两行**（换成新底座的 `DialogUtil.select`/声明式弹窗），
 * 调用方（wms 入库详情）不用动 —— 这正是"接缝"该有的样子。
 */
export function openPurchaseDetail(bill?: PurchaseEntity): void {
  if (!bill?.id) {
    return
  }
  void DialogUtil.show(PurchaseDetail, bill)
}

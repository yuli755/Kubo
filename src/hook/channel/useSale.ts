// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// F4 批 5：销售单切到新底座；审核 / 驳回走 `AbstractBillService`
// （旧 `useSaleAudit` / `useSaleReject` 两个 mutation 已删，口径与采购单一致）。
export { KSaleService } from '@/model/channel/sale/KSaleService'

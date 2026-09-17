// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// F4 批 5：采购单整体切到新底座 —— 审核 / 驳回由 `AbstractBillService` 提供，
// 旧版 `usePurchaseAudit` / `usePurchaseReject` 两个 TanStack mutation 随之删除
// （它们只是把同样的请求包了一层缓存失效，页面自己 `table.reload()` 更直接）。
export { KPurchaseService } from '@/model/channel/purchase/KPurchaseService'

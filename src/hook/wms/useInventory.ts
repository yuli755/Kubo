// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`，服务统一从这里取。
//
// 批 4 把库存页与库存选择器迁到新底座后，旧的 `InventoryService`（airpower 基类）
// 已无消费者、随之删除 —— 库存域现在只有一份服务实现。
export { KInventoryService } from '@/model/wms/inventory/KInventoryService'

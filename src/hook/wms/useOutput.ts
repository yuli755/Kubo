// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`，服务统一从这里取。
//
// 批 4 起出库页走新底座：旧的 `OutputService`/`OutputDetailService` 与 `api/wms` 的
// typed client mutation 已删除，审核/驳回/确认出库全部由 `KOutputService` 承担。
export { KOutputService } from '@/model/wms/output/KOutputService'

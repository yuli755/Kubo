// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`，服务统一从这里取。
//
// 批 4 起入库页走新底座（`useTable` + `KFormDialog` + `AbstractBillService`）：
// 旧的 `InputService`/`InputDetailService`（airpower 基类）与 `api/wms` 的 typed client
// （`useInputAudit`/`useInputReject`/`useInputAddFinish` 那套 mutation）已无消费者、随之删除 ——
// 审核/驳回/确认入库全部由 `KInputService` 承担，同一件事留两条链是漂移的温床。
export { KInputService } from '@/model/wms/input/KInputService'

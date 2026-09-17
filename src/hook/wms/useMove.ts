// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 批 4 起移库页走新底座（`useTable` + `KFormDialog` + `AbstractBillService`），
// 整域迁完后旧 `MoveService`/`MoveDetailService` 已无消费者、随之删除；
// 同样删掉的还有 `useMoveAudit`/`useMoveReject`（走 `api/wms` typed client）——
// 它们的职责已由 `KMoveService.audit/reject` 承担，同一件事留两条链是漂移的温床。
export { KMoveService } from '@/model/wms/move/KMoveService'

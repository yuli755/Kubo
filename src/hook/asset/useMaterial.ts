// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// F4 批 2：物料列表/编辑已切到新底座（`KMaterialService`）。
// 旧的 `MaterialService` 保留给 `MaterialSelector`（wms/channel 共 6 处引用，批 4/5 才迁）。
export { KMaterialService } from '@/model/asset/material/KMaterialService'
export { MaterialService } from '@/model/asset/material/MaterialService'

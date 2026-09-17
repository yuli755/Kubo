// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// F4 批 2：物料列表/编辑切到新底座；批 5：旧 `MaterialService`（只服务 `MaterialSelector`）
// 随选择器一起删除 —— 本仓所有物料选择都走 `KSelector + KMaterialService`。
export { KMaterialService } from '@/model/asset/material/KMaterialService'

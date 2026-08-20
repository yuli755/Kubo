import type { InventoryEntity, InventoryQuery } from './types'
import { request } from '@/api/axios'

/**
 * # WMS 库存 API
 *
 * 与后端 airpower `@Api("inventory")` 自动生成的 CRUD 端点对齐。
 * 仅暴露 GetPage 和 GetDetail（见后端 `@Extends({GetDetail, GetPage})`）。
 */
export const inventoryApi = {
  /**
   * 分页查询库存
   */
  getPage(params: InventoryQuery): Promise<{ list: InventoryEntity[], total: number }> {
    return request.post('/inventory/getPage', params)
  },

  /**
   * 获取库存详情
   */
  getDetail(id: number): Promise<InventoryEntity> {
    return request.post(`/inventory/getDetail`, { id })
  },
}

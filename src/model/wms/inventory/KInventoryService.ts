import { AbstractCurdService } from '@/framework'
import { InventoryEntity } from './InventoryEntity'

/**
 * # 库存服务（新底座）
 *
 * 库存是**只读**资源（没有新增/修改入口，数量由出入库/移库驱动），
 * 所以这个服务只会被 `getPage` / `getList` 用到 —— 但依然走 `AbstractCurdService`：
 * 分页、筛选、导出（后端支持库存导出）都由基类给，页面不需要自己拼请求。
 *
 * 与旧 `InventoryService`（airpower 基类）指向同一个后端接口（`baseUrl = 'inventory'`），
 * 两份实现并存于批 4：迁移中的 wms 页面用这份，还没迁的页面用那份。
 */
export class KInventoryService extends AbstractCurdService<InventoryEntity> {
  entityClass = InventoryEntity
  baseUrl = 'inventory'
}

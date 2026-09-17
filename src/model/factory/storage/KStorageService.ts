import { AbstractCurdService } from '@/framework'
import { StorageEntity } from './StorageEntity'

/**
 * # 仓库服务（新底座）
 *
 * 指向后端 `/storage` 接口；页面用 `useTable(KStorageService, …)` + `KTable` 消费它
 * （旧 `StorageService` 与 `StorageSelector` 已随 wms 域迁移删除）。
 *
 * 租户头（`X-Tenant-Id`）与鉴权不在这里加：`src/api/axios.ts` 的请求拦截器对所有请求统一注入，
 * 所以换底座不影响多租户。
 */
export class KStorageService extends AbstractCurdService<StorageEntity> {
  // 与 airpower 写法一致：`entityClass` 必须是**实例字段**，声明成 static 会在运行期拿到 undefined
  entityClass = StorageEntity
  baseUrl = 'storage'
}

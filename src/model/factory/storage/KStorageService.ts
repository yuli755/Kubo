import { AbstractCurdService } from '@/framework'
import { StorageEntity } from './StorageEntity'

/**
 * # 仓库服务（新底座）
 *
 * 与 `StorageService`（继承 airpower `AbstractBaseService`）指向**同一个后端接口**
 * （`baseUrl = 'storage'`），区别只在依赖的基类：存量 `StorageSelector` 与 airpower 页面
 * 用旧的，新底座页面（`useTable` + `KTable`）用这个。F4 迁完 wms 域后旧的会随选择器一起删除。
 *
 * 租户头（`X-Tenant-Id`）与鉴权不在这里加：`src/api/axios.ts` 的请求拦截器对所有请求统一注入，
 * 所以换底座不影响多租户。
 */
export class KStorageService extends AbstractCurdService<StorageEntity> {
  // 与 airpower 写法一致：`entityClass` 必须是**实例字段**，声明成 static 会在运行期拿到 undefined
  entityClass = StorageEntity
  baseUrl = 'storage'
}

import { AbstractBaseService } from '@/base/AbstractBaseService'
import { DataResourceEntity } from './DataResourceEntity'

/**
 * # 数据资源服务
 *
 * 走后端 `@Api("dataResource")` 暴露的 BaseController 标准动作
 * （权限码即 `{DataResource_add}` 这类，`infra/scripts/check-permission-codes.mjs` 会校验）。
 */
export class DataResourceService extends AbstractBaseService<DataResourceEntity> {
  baseUrl = 'dataResource'

  entityClass = DataResourceEntity
}

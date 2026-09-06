import { AbstractBaseService } from '@/base/AbstractBaseService'
import { DataPolicyEntity } from './DataPolicyEntity'

/**
 * # 行级数据策略服务
 *
 * 走后端 `@Api("dataPolicy")` 暴露的 BaseController 标准动作
 * （权限码即 `{DataPolicy_add}` 这类，`infra/scripts/check-permission-codes.mjs` 会校验）。
 */
export class DataPolicyService extends AbstractBaseService<DataPolicyEntity> {
  baseUrl = 'dataPolicy'

  entityClass = DataPolicyEntity
}

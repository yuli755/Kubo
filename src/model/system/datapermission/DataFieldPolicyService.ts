import { AbstractBaseService } from '@/base/AbstractBaseService'
import { DataFieldPolicyEntity } from './DataFieldPolicyEntity'

/**
 * # 列级脱敏策略服务
 *
 * 走后端 `@Api("dataFieldPolicy")` 暴露的 BaseController 标准动作
 * （权限码即 `{DataFieldPolicy_add}` 这类，`infra/scripts/check-permission-codes.mjs` 会校验）。
 */
export class DataFieldPolicyService extends AbstractBaseService<DataFieldPolicyEntity> {
  baseUrl = 'dataFieldPolicy'

  entityClass = DataFieldPolicyEntity
}

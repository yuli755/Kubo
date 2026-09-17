import { AbstractCurdService } from '@/framework'
import { CustomerEntity } from './CustomerEntity'

/**
 * # 客户服务（新底座）
 *
 * 与旧 `CustomerService`（airpower `AbstractBaseService`）同 `baseUrl`、同端点，
 * 区别只在基类。销售单/销售价/客户选择器都走这一份。
 */
export class KCustomerService extends AbstractCurdService<CustomerEntity> {
  entityClass = CustomerEntity
  baseUrl = 'customer'
}

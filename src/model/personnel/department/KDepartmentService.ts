import { AbstractCurdService } from '@/framework'
import { DepartmentEntity } from './DepartmentEntity'

/**
 * # 部门服务（新底座）
 *
 * 批 3 把部门页与部门选择器都换成新底座后，旧 `DepartmentService`（airpower 基类）
 * 已无消费者、随之删除 —— 部门域现在是**只有一份实现**的状态，
 * 不存在"两份服务指向不同接口"的漂移可能。
 */
export class KDepartmentService extends AbstractCurdService<DepartmentEntity> {
  entityClass = DepartmentEntity
  baseUrl = 'department'
}

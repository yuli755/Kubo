import { AbstractCurdService } from '@/framework'
import { MaterialEntity } from './MaterialEntity'

/**
 * # 物料服务（新底座）
 *
 * 与 `MaterialService`（继承 airpower `AbstractBaseService`）指向**同一个后端接口**
 * （`baseUrl = 'material'`），区别只在依赖的基类。
 *
 * ⚠️ 为什么过渡期要两份：`MaterialSelector`（wms 入库/出库、channel 采购/销售的价格页
 * 共 6 处引用）是 airpower `ASelector`，它要求服务继承 airpower 基类；而那 6 处页面要等
 * 批 4/5 才迁。两份服务只有三行配置、没有任何业务逻辑，所以不存在"两套实现漂移"的风险。
 * 租户头与鉴权由 `src/api/axios.ts` 的拦截器统一注入，换底座不影响多租户。
 */
export class KMaterialService extends AbstractCurdService<MaterialEntity> {
  entityClass = MaterialEntity
  baseUrl = 'material'
}

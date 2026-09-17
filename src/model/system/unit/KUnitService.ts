import { AbstractCurdService } from '@/framework'
import { UnitEntity } from './UnitEntity'

/**
 * # 计量单位服务（新底座）
 *
 * 单位是**系统域**实体，但它的消费者横跨域：物料（asset）的编辑/筛选要用单位选择器。
 * 因此这里提供一个 `@/framework` 基类的服务，让非系统域页面也能用 —— 而不必等系统域整体迁移。
 *
 * ## 历史（别照着旧注释改回去）
 *
 * 这个类曾经是「双服务并行」的过渡态：与之并列的 `UnitService`（airpower
 * `AbstractBaseService`）只为存量 `ASelector` 而活。批 2 删掉
 * `view/console/system/unit/component/{editor,selector}.vue` 之后旧服务没了消费者，
 * `UnitService` 与 `view/console/system/unit/component/**` 已一并删除 —— 现在单位只有这一套服务，
 * 旧页（若还有）也应改用它。
 */
export class KUnitService extends AbstractCurdService<UnitEntity> {
  // 与 airpower 的写法一致：`entityClass` 是**实例字段**（不是 static），
  // `useTable` 先 new 实例再取它 —— 声明成 static 会在运行期拿到 undefined。
  entityClass = UnitEntity
  baseUrl = 'unit'
}

import { AbstractCurdService } from '@/framework'
import { UnitEntity } from './UnitEntity'

/**
 * # 计量单位服务（新底座）
 *
 * 与 `UnitService`（继承 airpower 的 `AbstractBaseService`）**指向同一个后端接口**
 * （`baseUrl = 'unit'`），区别只在依赖的基类：
 *
 * | | `UnitService` | `KUnitService` |
 * | --- | --- | --- |
 * | 基类 | airpower `AbstractBaseService` | `@/framework` `AbstractCurdService` |
 * | 谁在用 | 存量 `UnitSelector`（airpower `ASelector`）与 `useEditor` | 新底座页面（`useTable` + `KTable`） |
 *
 * 两个并存是**过渡态**：`UnitSelector` 仍被物料编辑页使用，而 airpower 的 `ASelector`
 * 要求它自己的服务类型；等到 F4 按域迁移把物料域也换过来，`UnitService` 与
 * `view/console/system/unit/component/**` 会一起删除。这里不复制任何业务逻辑（只有两行配置），
 * 所以过渡期不存在"两套实现漂移"的风险。
 */
export class KUnitService extends AbstractCurdService<UnitEntity> {
  // 与 airpower 的写法一致：`entityClass` 是**实例字段**（不是 static），
  // `useTable` 先 new 实例再取它 —— 声明成 static 会在运行期拿到 undefined。
  entityClass = UnitEntity
  baseUrl = 'unit'
}

/**
 * # 框架层·查询模型
 *
 * 与后端 airpower 的契约完全一致（分页/排序/过滤的字段名不能变）：
 *
 * ```json
 * POST /material/getPage
 * { "filter": { "name": "铝板" }, "page": { "pageNum": 1, "pageSize": 20 }, "sort": { "field": "id", "direction": "DESC" } }
 * ```
 */

import type { RootEntity } from './entity'
import type { ITransformerConstructor } from './types'
import { RootModel } from './entity'

/** 排序方向（后端枚举值） */
export type SortType = 'ASC' | 'DESC'

/** 分页信息 */
export class QueryPage extends RootModel {
  pageNum = 1
  pageSize = 20
}

/** 排序信息 */
export class QuerySort extends RootModel {
  field = 'id'
  direction: SortType = 'DESC'

  setField(field: string): this {
    this.field = field
    return this
  }

  setDirection(direction: SortType): this {
    this.direction = direction
    return this
  }
}

/**
 * 查询请求（分页）。
 *
 * ⚠️ 构造参数必须是**实体类构造器**：`filter` 要 `new FilterClass()` 初始化成一份空表单，
 * 表格的搜索区会直接读写 `request.filter`。传实例会被静默忽略（filter 停在 undefined）。
 */
export class QueryRequest<E extends RootEntity = RootEntity> extends RootModel {
  filter!: E
  sort?: QuerySort

  constructor(FilterClass: ITransformerConstructor<E>) {
    super()
    try {
      this.filter = new FilterClass()
    }
    catch {
      // 与原库一致：构造失败不抛，交由使用方保证传入的是可实例化的类
    }
  }

  setSort(sort: QuerySort): this {
    this.sort = sort
    return this
  }
}

/** 分页查询请求 */
export class QueryRequestPage<E extends RootEntity = RootEntity> extends QueryRequest<E> {
  page: QueryPage = new QueryPage()
}

/** 分页响应 */
export class QueryResponsePage<E extends RootEntity = RootEntity> extends RootModel {
  list: E[] = []
  page: QueryPage = new QueryPage()
  sort?: QuerySort
  total = 0
  pageCount = 0
}

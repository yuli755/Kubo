import type { ComputedRef, Ref } from 'vue'
import { computed } from 'vue'

/**
 * # 「包含已停用」开关 —— 让停用不再是单向不可逆的操作
 *
 * ## 为什么需要它
 *
 * 后端 `BaseEntity.isDisabled` 带成员初始值 `false`（`SPMS-Server/src/main/java/cn/hamm/spms/base/BaseEntity.java:79`），
 * 而分页请求的 `QueryListRequest<T>.filter` **就是实体本身**，所以"请求体里没写 isDisabled"
 * 会被反序列化成 `isDisabled = false`，`BaseService.buildPredicates` 据此加上一条隐形 WHERE
 * `is_disabled = 0`。
 *
 * 这是**刻意**的语义（同文件 L105-106 注释：「默认只看启用行是产品要的语义，各列表页的
 * 『显示已禁用』开关显式传 isDisabled=true 即可」），后端没问题。问题在前端：那个开关
 * 一直没做出来。后果是 2026-09 真机测试实测到的现象 —— 管理员把 `张三` 停用后，
 * 用户列表从 5 行变 4 行，**整行消失**，页面上没有任何入口能再看到他，只能靠直接调
 * `POST /user/enable` 恢复。一个不可逆的权限操作本身就是缺陷。
 *
 * ## 三态约定（真机对拍过，不是推测）
 *
 * | filter.isDisabled | 后端行为 | 界面含义 |
 * | --- | --- | --- |
 * | `false` | `WHERE is_disabled = 0` | 只看启用（默认） |
 * | `true` | `WHERE is_disabled = 1` | 只看已停用 |
 * | `null` | 谓词被跳过（`buildPredicates` 忽略 null） | 全部 |
 *
 * 本开关用 `null`（全部）而不是 `true`（只看停用）：管理员要找的是"刚才那个人去哪了"，
 * 把启用行一起留着才能对比，也免得误以为其它账号也被停用了。
 *
 * ## 为什么状态存在 filter 上、而不是自己开一个 ref
 *
 * 两份状态一定会在某条路径上打架。最典型的是 `KTable` 内置搜索栏的「重置」：它调
 * `table.reset()` 把 filter 打回 `defaultFilter`，此时若开关还是勾选态，界面就显示
 * "含已停用"而列表只有启用行 —— 一个用户看得见、却和实际请求不一致的开关。
 * 派生成 `computed` 后，重置会自动把开关弹回去，无需页面去监听重置事件。
 *
 * **已知取舍**：内置搜索栏提交时只带表单字段，会把 `isDisabled` 冲掉 ⇒ 搜索后回到"只看启用"，
 * 派生的开关同步弹回未勾选。状态仍然自洽；这里选择"不静默保留一个界面上看不见的筛选条件"，
 * 而不是用 `beforeSearch` 把它偷偷续上 —— 后者会让搜索结果的行数和界面上的开关对不上。
 *
 * ## 用法
 *
 * ```ts
 * const table = useTable(KUserService, { queryKey: [...], defaultFilter: DISABLED_FILTER_DEFAULT })
 * const { includeDisabled, onIncludeDisabledChange } = useDisabledVisibility(table)
 * ```
 * ```vue
 * <KTable :table="table">
 *   <template #toolbar-left>
 *     <ElCheckbox :model-value="includeDisabled" @change="onIncludeDisabledChange">包含已停用</ElCheckbox>
 *   </template>
 * </KTable>
 * ```
 *
 * 参数刻意只要求 `{ filter, search }` 这个最小结构，而不是整个 `UseTableReturn<E, S>`：
 * 后者带两个泛型参数，页面传进来时 TS 要先把 `E`/`S` 推出来，而这里一个字段都不读，
 * 结构化约束既够用又不把泛型传染给调用方。
 */

/** `useTable` 的 `defaultFilter`：显式写进请求体，不依赖后端那个成员初始值的隐形 WHERE */
export const DISABLED_FILTER_DEFAULT = { isDisabled: false }

interface IFilterTable {
  filter: Ref<Record<string, unknown>>
  search: (nextFilter?: Record<string, unknown>) => void
}

export interface IDisabledVisibility {
  /** 开关当前状态（派生自 `filter.isDisabled === null`，不是独立状态） */
  includeDisabled: ComputedRef<boolean>
  /** `ElCheckbox` 的 `@change` 处理器（EP 传的是 `boolean | string | number`） */
  onIncludeDisabledChange: (next: boolean | string | number) => void
}

export function useDisabledVisibility(table: IFilterTable): IDisabledVisibility {
  const includeDisabled = computed(() => table.filter.value.isDisabled === null)

  function onIncludeDisabledChange(next: boolean | string | number) {
    table.search({ ...table.filter.value, isDisabled: next ? null : false })
  }

  return { includeDisabled, onIncludeDisabledChange }
}

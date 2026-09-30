import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import { DISABLED_FILTER_DEFAULT, useDisabledVisibility } from '../useDisabledVisibility'

/**
 * # 「包含已停用」开关的契约
 *
 * 为什么这条契约值得单独钉住：它防的不是"代码写错了"，而是**一个看不见的数据丢失**。
 * 后端 `BaseEntity.isDisabled = false` 的成员初始值 + `QueryListRequest.filter` 就是实体本身
 * ⇒ 请求体里不写 `isDisabled` 就等于加了 `WHERE is_disabled = 0`。少了这个开关，被停用的
 * 用户/角色会直接从列表里消失且无处恢复（2026-09 真机实测：用户列表 5 行变 4 行，
 * 只能靠直接调 `POST /user/enable` 找回）。
 *
 * 三态是真机对拍过的（`POST /api/user/getPage`）：
 * `filter:{}` → 4 行（不含已停用）；`filter:{isDisabled:null}` → 5 行（含 `d=true` 的张三）；
 * `filter:{isDisabled:true}` → 1 行（只有张三）。
 */

const ROOT = process.cwd()

function read(rel: string): string {
  return readFileSync(join(ROOT, rel), 'utf8')
}

/** 最小替身：只实现 composable 真正读到的两个成员（结构化约束，不是 UseTableReturn） */
function fakeTable(initial: Record<string, unknown> = { ...DISABLED_FILTER_DEFAULT }) {
  const filter = ref<Record<string, unknown>>({ ...initial })
  const calls: Record<string, unknown>[] = []
  const table = {
    filter,
    search(next?: Record<string, unknown>) {
      const shaped = { ...(next ?? {}) }
      calls.push(shaped)
      filter.value = shaped
    },
  }
  return { table, calls, filter }
}

describe('useDisabledVisibility · 三态筛选', () => {
  it('默认只看启用：DISABLED_FILTER_DEFAULT 必须是显式的 false，不能靠后端隐形 WHERE', () => {
    // 显式 false 与"不传"在后端结果相同，但请求体自己说明意图；
    // 一旦后端哪天给 isDisabled 补上 @FilterIgnore，"不传"会变成"全部"，显式 false 不会。
    expect(DISABLED_FILTER_DEFAULT).toEqual({ isDisabled: false })
    const { table } = fakeTable()
    const { includeDisabled } = useDisabledVisibility(table)
    expect(includeDisabled.value).toBe(false)
  })

  it('勾选 → filter.isDisabled = null（全部）；取消 → false（只看启用）', async () => {
    const { table, calls } = fakeTable()
    const { includeDisabled, onIncludeDisabledChange } = useDisabledVisibility(table)

    onIncludeDisabledChange(true)
    await nextTick()
    expect(calls.at(-1)).toEqual({ isDisabled: null })
    expect(includeDisabled.value, '勾选后开关状态没跟着 filter 变（说明状态被复制成了两份）').toBe(true)

    onIncludeDisabledChange(false)
    await nextTick()
    expect(calls.at(-1)).toEqual({ isDisabled: false })
    expect(includeDisabled.value).toBe(false)
  })

  it('切换时保留其它筛选条件（部门树筛选不能被这个开关清掉）', async () => {
    const { table } = fakeTable({ ...DISABLED_FILTER_DEFAULT, departmentId: 5 })
    const { onIncludeDisabledChange } = useDisabledVisibility(table)

    onIncludeDisabledChange(true)
    await nextTick()
    expect(table.filter.value).toEqual({ departmentId: 5, isDisabled: null })
  })

  it('change 事件的值可能不是 boolean（EP 传 string/number），truthy 判定不能只认 true', async () => {
    const { table, calls } = fakeTable()
    const { onIncludeDisabledChange } = useDisabledVisibility(table)

    onIncludeDisabledChange('true' as unknown as boolean)
    await nextTick()
    expect(calls.at(-1)).toEqual({ isDisabled: null })

    onIncludeDisabledChange(0)
    await nextTick()
    expect(calls.at(-1)).toEqual({ isDisabled: false })
  })

  it('状态派生自 filter：把 filter 打回默认值（KTable 的「重置」）后开关必须自己弹回去', async () => {
    const { table, filter } = fakeTable()
    const { includeDisabled, onIncludeDisabledChange } = useDisabledVisibility(table)

    onIncludeDisabledChange(true)
    await nextTick()
    expect(includeDisabled.value).toBe(true)

    // 模拟 table.reset()：filter 回到 defaultFilter，而页面上那个独立 ref（如果有的话）不会跟着回
    filter.value = { ...DISABLED_FILTER_DEFAULT }
    await nextTick()
    expect(includeDisabled.value, '重置后开关仍显示勾选，而列表只有启用行 ⇒ 界面与请求不一致').toBe(false)
  })
})

describe('useDisabledVisibility · 开了 Curd.Enable/Disable 的页面必须挂上开关', () => {
  /**
   * 后端只有三个控制器重新开启了 `Curd.Enable/Disable`（其余模块的 disable 恒为 501）：
   * `UserController.java:55`、`RoleController.java:21`、`PersonalTokenController.java:31`。
   * 有"停用"按钮却没有"看到已停用"的入口，停用就是单向门 ⇒ 这几个页面必须挂开关。
   * 个人令牌页当前没有列表页（令牌在个人中心内管理），所以这里只钉用户页与角色页。
   */
  const pages = [
    'src/view/console/personnel/user/list.vue',
    'src/view/console/personnel/role/list.vue',
  ]

  it.each(pages)('%s 用了 useDisabledVisibility + 默认筛选 + 可见开关', (rel) => {
    const source = read(rel)
    expect(source).toContain('useDisabledVisibility')
    expect(source, '默认筛选丢了 ⇒ 首屏请求不带 isDisabled，退回依赖后端隐形 WHERE').toContain('DISABLED_FILTER_DEFAULT')
    expect(source, '页面上找不到「包含已停用」这个可见入口').toContain('包含已停用')
    // 开关必须放进 KTable 的工具条插槽，而不是页面自己另画一行（否则 26 个列表页会长出两种工具条）
    expect(source).toContain('#toolbar-left')
    // 单向绑定：状态源是 filter，不能用 v-model 造出第二份状态
    expect(source).toContain(':model-value="includeDisabled"')
    expect(source.includes('v-model="includeDisabled"'), 'v-model 会造出第二份状态，与 filter 打架').toBe(false)
  })
})

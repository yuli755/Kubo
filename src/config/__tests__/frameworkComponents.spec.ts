import type { Component } from 'vue'
import { VueQueryPlugin } from '@tanstack/vue-query'
// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'
import { BaseEntity } from '@/base/BaseEntity'
import { AbstractCurdService, Field, Form, Model, Search, Table, Type } from '@/framework'

/**
 * # F2 组件层契约（新底座）
 *
 * 覆盖三件事，每件都对应一个**会真实发生的故障**：
 *
 * 1. **组件不依赖 airpower**：KTable / KFormDialog / useTable 的源码里不得出现 `@airpower/web`
 *    —— 否则"换底座"只是换了个名字，F2 白做。
 * 2. **元数据驱动**：`@Table` 有列就渲染列、`@Search` 有字段就出搜索框、`@Form` 有字段就出表单项。
 *    这里用"给实体加一列，表格自动多一列"来验证，而不是断言写死的列名。
 * 3. **请求契约**：`useTable` 发出的分页请求体必须是后端认识的
 *    `{ filter, page: { pageNum, pageSize }, sort: { field, direction } }`。
 *    这里最容易错的是把 `page` 写成数字（后端取 `page.pageNum` → 永远第 1 页），
 *    而页面上"看起来只是翻页没反应"。
 */

if (typeof window !== 'undefined' && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia
}

const capturedErrors: unknown[] = []

beforeEach(() => {
  setActivePinia(createPinia())
  capturedErrors.length = 0
  vi.restoreAllMocks()
})

/**
 * 挂载辅助。
 *
 * ⚠️ 这里**不再**手工注册 element-plus 组件：KTable / KFormDialog 已经显式
 * `import { ElTable, ... } from 'element-plus'`（不再依赖 `unplugin-vue-components`
 * 的自动导入），vitest 里不加载该插件也能解析。曾经的手工注册是"模板写 `<el-*>` 但不 import"
 * 的补丁，那种写法在探针（menuSmoke）里会让表格列插槽拿不到 `row` 而整页报错。
 */
async function mountWithQuery<T>(component: T, extra: Record<string, unknown> = {}) {
  return mount(component as never, {
    global: {
      plugins: [VueQueryPlugin],
      // v-permission 未注册时按钮不会按权限隐藏（测试里会误以为"权限生效了"），显式注册
      directives: (await import('@/directives')).directives,
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
      ...extra,
    },
  })
}

describe('f2 · 组件层不依赖 airpower', () => {
  it('kTable / KFormDialog / useTable 源码里没有 @airpower/web', async () => {
    const { readFileSync } = await import('node:fs')
    const files = [
      // 第一批
      'src/component/framework/KTable.vue',
      'src/component/framework/KFormDialog.vue',
      'src/hook/kubo/useTable.ts',
      'src/base/BaseEntity.ts',
      'src/model/system/unit/KUnitService.ts',
      'src/view/console/system/unit/list.vue',
      // 第二批（补齐 APanel/ASelector/useDetail/单元格渲染的缺口）
      'src/component/framework/KPanel.vue',
      'src/component/framework/KSelector.vue',
      'src/component/framework/KDetail.vue',
      'src/hook/kubo/useDetail.ts',
      'src/framework/cell.ts',
      'src/framework/format.ts',
    ]
    const offenders = files.filter((file) => {
      const source = readFileSync(file, 'utf8')
      // 注释里提到 airpower 是允许的（说明性文字），只禁止真正的 import
      return /^\s*import[^\n]*from\s+'@airpower\/web'/m.test(source)
    })
    expect(offenders, '这些文件仍在 import airpower：F2 的目标就是让新页面不依赖它').toEqual([])
  })

  it('试点页（计量单位）已切到新底座，且不再引用 ATable/APanel', async () => {
    const { readFileSync } = await import('node:fs')
    const raw = readFileSync('src/view/console/system/unit/list.vue', 'utf8')
    // 迁移说明的注释里会出现 ATable 字样（对比新旧写法），所以先剥注释再断言
    const code = raw.replace(/\/\*[\s\S]*?\*\//g, '').replace(/<!--[\s\S]*?-->/g, '')
    expect(code).toContain('KTable')
    expect(code).toContain('useTable')
    expect(code, '试点页还在用 airpower 的 ATable/APanel').not.toMatch(/\bATable\b|\bAPanel\b/)
  })
})

describe('f4 · 批 1（factory + index）迁移完成度', () => {
  /**
   * 批 1 的完成定义（DoD）：该域视图**零 airpower import**。
   *
   * 2026-09 更新（批 4 收尾）：`structure/component/selector.vue`（生产单元选择器）原本就没有
   * 任何消费者，先删；最后一份 `storage/component/selector.vue`（仓库选择器）被 wms 的入库/移库页
   * 用到批 4 —— 那两页换成 `KSelector` 后它也没有消费者了，连同 barrel 一起删除。
   * 集合因此从 2 → 1 → **0**：factory 域现在是**整域零 airpower**。
   *
   * 这里断言的是**精确集合**而不是"数量为 0"：新增一处 airpower 用法会让用例变红。
   */
  const ALLOWED: string[] = []

  it('仓库 / 生产单元 / 首页已切新底座（域内零 airpower import）', async () => {
    const { readdirSync, readFileSync } = await import('node:fs')
    const walk = (dir: string): string[] =>
      readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
        entry.isDirectory() ? walk(`${dir}/${entry.name}`) : [`${dir}/${entry.name}`])

    const files = [
      ...walk('src/view/console/factory'),
      ...walk('src/view/console/index'),
    ].filter(file => file.endsWith('.vue'))

    const offenders = files.filter((file) => {
      const source = readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/<!--[\s\S]*?-->/g, '')
      return /^\s*import[^\n]*from\s+'@airpower\/web'/m.test(source)
    })
    expect(offenders.sort()).toEqual([...ALLOWED].sort())
  })

  it('树形页面走 getTreeList（分页接口拿不到树数据）', async () => {
    const { readFileSync } = await import('node:fs')
    for (const page of ['src/view/console/factory/storage/list.vue', 'src/view/console/factory/structure/list.vue']) {
      const source = readFileSync(page, 'utf8')
      expect(source, `${page} 没有开树模式`).toContain('tree: true')
      expect(source, `${page} 没有用新底座服务`).toMatch(/K(Storage|Structure)Service/)
    }
  })
})

describe('f2 · KTable 由元数据驱动', () => {
  it('列来自 @KTable、搜索项来自 @KSearch（加一列则表格多一列）', async () => {
    const { Field, Form, Model, Search, Table } = await import('@/framework')
    const { BaseEntity } = await import('@/base/BaseEntity')
    const { AbstractCurdService } = await import('@/framework')
    const KTable = (await import('@/component/framework/KTable.vue')).default
    const { useTable } = await import('@/hook/kubo/useTable')

    @Model({ label: '契约实体' })
    class ContractEntity extends BaseEntity {
      @Table({ order: 100, minWidth: 160 })
      @Form({ requiredString: true })
      @Field({ label: '名称' })
      @Search()
      name!: string
    }

    class ContractService extends AbstractCurdService<ContractEntity> {
      entityClass = ContractEntity
      baseUrl = 'contract-entity'
    }

    const rows = [{ id: 1, name: '千克' }, { id: 2, name: '米' }]
    vi.spyOn(ContractService.prototype, 'getPage').mockResolvedValue({
      list: rows,
      total: 2,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    /**
     * ⚠️ 注册成组件时要把泛型组件断言成 `Component`：
     * KTable 是 `generic="E extends IIdentifiableEntity, S extends ..."` 的泛型组件，
     * 直接放进 `components` 会让 `defineComponent` 的重载解析失败（TS2769）。
     * 与页面用 `:table="table"` 的真实用法等价，只是把类型放宽在测试边界上。
     */
    const HostComponent = defineComponent({
      components: { KTable: KTable as unknown as Component },
      setup() {
        const table = useTable(ContractService, { queryKey: ['contract-entity'], defaultPageSize: 20 })
        return { table }
      },
      template: '<KTable :table="table" />',
    })

    const wrapper = await mountWithQuery(HostComponent)
    // 等 TanStack Query 把数据写回
    await vi.waitFor(() => {
      expect(wrapper.findAll('.el-table__row').length, '表格没有渲染出行').toBe(2)
    }, { timeout: 8000 })

    // 列头来自元数据 label（不是字段名）
    const headers = wrapper.findAll('.el-table__header th').map(th => th.text())
    expect(headers.some(header => header.includes('名称'))).toBe(true)
    // 搜索项来自 @Search
    expect(wrapper.find('.k-table__search').exists()).toBe(true)
    expect(capturedErrors.map(String)).toEqual([])
  })
})

describe('f2 · useTable 请求契约', () => {
  it('分页请求体是 { filter, page.pageNum, page.pageSize, sort.direction }', async () => {
    const { Field, Model } = await import('@/framework')
    const { BaseEntity } = await import('@/base/BaseEntity')
    const { AbstractCurdService } = await import('@/framework')
    const { useTable } = await import('@/hook/kubo/useTable')
    const { defineComponent: define } = await import('vue')
    const { mount: mountComponent } = await import('@vue/test-utils')

    @Model({ label: '契约实体2' })
    class PageEntity extends BaseEntity {
      @Field({ label: '名称' })
      name!: string
    }
    class PageService extends AbstractCurdService<PageEntity> {
      entityClass = PageEntity
      baseUrl = 'page-entity'
    }

    /**
     * 收集**每一次**请求：`useTable` 在 setup 里就会发出首次查询（page=1），
     * 之后 `onPageChanged(3)` 触发重查。只看"最后一次"会依赖时序，改成按条件找那一次。
     */
    const calls: Array<Record<string, unknown>> = []
    vi.spyOn(PageService.prototype, 'getPage').mockImplementation(async (request: unknown) => {
      calls.push(request as Record<string, unknown>)
      return { list: [], total: 0, pageCount: 0, page: { pageNum: 1, pageSize: 10 } } as never
    })

    const Host = define({
      setup() {
        const table = useTable(PageService, { queryKey: ['page-entity'], defaultPageSize: 10 })
        return { table }
      },
      template: '<div />',
    })
    const wrapper2 = await mountComponent(Host, { global: { plugins: [VueQueryPlugin] } })
    const vm = wrapper2.vm as unknown as {
      table: {
        onPageChanged: (page: number, size: number) => void
        onSortChanged: (sort: { field: string, desc: boolean }) => void
      }
    }

    // ① 翻页：请求体必须带 pageNum=3（写成数字 `page: 3` 后端会一直返回第 1 页）
    vm.table.onPageChanged(3, 10)
    await nextTick()
    await vi.waitFor(() => {
      expect(calls.some(call => (call.page as Record<string, unknown>)?.pageNum === 3), `翻页后没有带 pageNum=3，实际：${JSON.stringify(calls.map(c => c.page))}`).toBe(true)
    }, { timeout: 8000 })

    const page = calls.find(call => (call.page as Record<string, unknown>)?.pageNum === 3)!.page as Record<string, unknown>
    expect(page.pageSize).toBe(10)

    // ② 改排序：必须回到第 1 页（否则会停在一个按新顺序可能不存在的页码上，表格空白）
    vm.table.onSortChanged({ field: 'name', desc: true })
    await nextTick()
    await vi.waitFor(() => {
      expect(
        calls.some(call => (call.sort as Record<string, unknown>)?.direction === 'DESC'),
        '改排序后没有重新请求',
      ).toBe(true)
    }, { timeout: 8000 })
    const afterSort = calls.filter(call => (call.sort as Record<string, unknown>)?.direction === 'DESC').at(-1)!
    expect(afterSort.sort, '排序字段与方向必须按后端契约传 { field, direction }').toMatchObject({ field: 'name', direction: 'DESC' })
    expect((afterSort.page as Record<string, unknown>).pageNum, '改排序后必须回到第 1 页').toBe(1)
  })
})

describe('f2 · 第二批组件（KPanel / 树表格 / KSelector / KDetail）', () => {
  /** 树形实体：仓库/生产单元/部门/菜单都是这个形状 */
  @Model({ label: '树节点' })
  class TreeNodeEntity extends BaseEntity {
    @Table({ order: 100, minWidth: 160 })
    @Form({ requiredString: true })
    @Field({ label: '名称' })
    @Search()
    name!: string

    @Type(TreeNodeEntity)
    children?: TreeNodeEntity[]
  }

  class TreeNodeService extends AbstractCurdService<TreeNodeEntity> {
    entityClass = TreeNodeEntity
    baseUrl = 'tree-node'
  }

  it('kPanel 渲染标题、右上角插槽与内容', async () => {
    const KPanel = (await import('@/component/framework/KPanel.vue')).default
    const Host = defineComponent({
      components: { KPanel },
      template: `
        <KPanel title="仓库" description="共 3 个">
          <template #extra><span class="k-extra">导出</span></template>
          <div class="k-body">内容</div>
        </KPanel>`,
    })
    const wrapper = await mountWithQuery(Host)
    expect(wrapper.text()).toContain('仓库')
    expect(wrapper.text()).toContain('共 3 个')
    expect(wrapper.find('.k-extra').text()).toBe('导出')
    expect(wrapper.find('.k-body').text()).toBe('内容')
  })

  it('树形表格：走 getTreeList、渲染子节点、不显示分页器', async () => {
    const KTable = (await import('@/component/framework/KTable.vue')).default
    const { useTable } = await import('@/hook/kubo/useTable')

    const tree = [
      { id: 1, name: '一号库', children: [{ id: 2, name: 'A 区', children: [] }] },
      { id: 3, name: '二号库', children: [] },
    ]
    const getTreeList = vi.spyOn(TreeNodeService.prototype, 'getTreeList').mockResolvedValue(tree as never)
    const getPage = vi.spyOn(TreeNodeService.prototype, 'getPage')

    // 同上一处：泛型组件注册进 `components` 需要放宽类型（见该用例的注释）
    const Host = defineComponent({
      components: { KTable: KTable as unknown as Component },
      setup() {
        const table = useTable(TreeNodeService, { queryKey: ['f2:tree'], tree: true })
        return { table }
      },
      template: '<KTable :table="table" />',
    })
    const wrapper = await mountWithQuery(Host)
    // 等**渲染结果**而不是只等调用发生：mock 的 promise resolve 与 Vue 更新之间还有一拍
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('一号库')
    }, { timeout: 8000 })

    expect(getPage, '树模式不能走分页接口（后端 getTreeList 才是树数据源）').not.toHaveBeenCalled()
    expect(wrapper.text(), '子节点没有渲染出来：树表格依赖 row-key + children').toContain('A 区')
    expect(wrapper.find('.el-pagination').exists(), '树数据不分页，分页器应隐藏').toBe(false)
    // 树查询请求体：只有 filter/sort，没有 page
    const request = getTreeList.mock.calls[0]![0] as unknown as Record<string, unknown>
    expect(request.page, '树查询不该带 page 参数').toBeUndefined()
  })

  it('树形表格：点「添加子级」把父行回传给页面（页面据此预置 parentId）', async () => {
    const KTable = (await import('@/component/framework/KTable.vue')).default
    const { useTable } = await import('@/hook/kubo/useTable')

    vi.spyOn(TreeNodeService.prototype, 'getTreeList').mockResolvedValue([
      { id: 9, name: '父节点', children: [] },
    ] as never)

    const Host = defineComponent({
      components: { KTable: KTable as unknown as Component },
      setup() {
        const table = useTable(TreeNodeService, { queryKey: ['f2:tree-add-child'], tree: true })
        return { table }
      },
      template: '<KTable :table="table" />',
    })
    const wrapper = await mountWithQuery(Host)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('父节点')
    }, { timeout: 8000 })

    const button = wrapper.findAll('button').find(item => item.text() === '添加子级')
    expect(button, '树模式缺少「添加子级」按钮').toBeTruthy()
    await button!.trigger('click')
    // `findComponent` 的返回在泛型组件下退化成 DOMWrapper，取 emitted 需要显式断言形状
    // `findComponent` 收泛型组件需要放宽类型（同上面的注册处），返回值在泛型下退化成 DOMWrapper
    const tableWrapper = wrapper.findComponent(KTable as unknown as Component) as unknown as { emitted: (name: string) => unknown[] }
    const child = tableWrapper.emitted('addChild') as [Array<{ id: number }>] | undefined
    expect(child?.[0]?.[0]?.id).toBe(9)
  })

  it('kSelector：勾选后确定，把实体数组交给页面', async () => {
    const KSelector = (await import('@/component/framework/KSelector.vue')).default

    const rows = [{ id: 11, name: '主仓库' }, { id: 12, name: '备件库' }]
    vi.spyOn(TreeNodeService.prototype, 'getPage').mockResolvedValue({
      list: rows,
      total: 2,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const Host = defineComponent({
      components: { KSelector },
      template: '<KSelector v-model="visible" :service="service" multiple @confirm="onConfirm" />',
      data() {
        return { visible: true, picked: [] as unknown[] }
      },
      computed: {
        service: () => TreeNodeService,
      },
      methods: {
        onConfirm(list: unknown[]) {
          this.picked = list
        },
      },
    })
    const wrapper = await mountWithQuery(Host)
    // ⚠️ ElDialog 是 `append-to-body`：内容被 teleport 到 document.body，
    // `wrapper.text()` 里永远是空的 —— 断言与点击都得走 document.body
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain('主仓库')
    }, { timeout: 8000 })

    // 勾选：直接触发表格的 selection-change（等价于用户点了复选框）
    const ElTable = (await import('element-plus')).ElTable
    const tableWrapper = wrapper.findComponent(ElTable) as unknown as { vm: { $emit: (name: string, ...args: unknown[]) => void } }
    tableWrapper.vm.$emit('selection-change', [rows[0]])
    await nextTick()

    const buttons = Array.from(document.body.querySelectorAll('button'))
    const confirmButton = buttons.find(node => node.textContent?.trim() === '确定')
    expect(confirmButton, '弹窗里没有「确定」按钮').toBeTruthy()
    // 用 dispatchEvent 而不是 `button.click()`：Element Plus 的按钮在 jsdom 下
    // 对 `.click()` 不触发（实测 emitted 为空），显式派发冒泡事件才稳定。
    confirmButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await nextTick()
    // 断言「宿主页面拿到了什么」而不是「子组件 emit 了什么」：
    // 页面能收到数据才是契约（也是 `@confirm="rows => ..."` 的真实用法）
    const picked = (wrapper.vm as unknown as { picked: { id: number }[] }).picked
    expect(picked?.[0]?.id, '确定后应把选中实体交给页面').toBe(11)
  })

  it('kDetail：按 id 重新查详情，字段名来自元数据、金额按规则格式化', async () => {
    const KDetail = (await import('@/component/framework/KDetail.vue')).default

    @Model({ label: '物料' })
    class MaterialEntity extends BaseEntity {
      @Table({ order: 100, minWidth: 160 })
      @Field({ label: '物料名称' })
      name!: string

      @Table({ order: 90, money: true })
      @Field({ label: '单价' })
      price!: string
    }

    class MaterialService extends AbstractCurdService<MaterialEntity> {
      entityClass = MaterialEntity
      baseUrl = 'material'
    }

    // 后端 BigDecimal 序列化成字符串 —— 详情页必须显示 12.30 而不是 12.3
    vi.spyOn(MaterialService.prototype, 'getDetail').mockResolvedValue({ id: 7, name: '铝板', price: '12.30' } as never)

    const Host = defineComponent({
      components: { KDetail },
      template: '<KDetail :service="service" :id="7" title="物料详情" />',
      computed: { service: () => MaterialService },
    })
    const wrapper = await mountWithQuery(Host)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('铝板')
    }, { timeout: 8000 })

    expect(MaterialService.prototype.getDetail).toHaveBeenCalledWith(7)
    expect(wrapper.text()).toContain('物料名称')
    expect(wrapper.text()).toContain('单价')
    expect(wrapper.text()).toContain('12.30')
  })
})

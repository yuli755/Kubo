import { VueQueryPlugin } from '@tanstack/vue-query'
// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, nextTick } from 'vue'

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
async function mountWithQuery<T>(component: T) {
  return mount(component as never, {
    global: {
      plugins: [VueQueryPlugin],
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
    },
  })
}

describe('f2 · 组件层不依赖 airpower', () => {
  it('kTable / KFormDialog / useTable 源码里没有 @airpower/web', async () => {
    const { readFileSync } = await import('node:fs')
    const files = [
      'src/component/framework/KTable.vue',
      'src/component/framework/KFormDialog.vue',
      'src/hook/kubo/useTable.ts',
      'src/base/KEntity.ts',
      'src/model/system/unit/KUnitService.ts',
      'src/view/console/system/unit/list.vue',
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

describe('f2 · KTable 由元数据驱动', () => {
  it('列来自 @KTable、搜索项来自 @KSearch（加一列则表格多一列）', async () => {
    const { Field, Form, Model, Search, Table } = await import('@/framework')
    const { KEntity } = await import('@/base/KEntity')
    const { AbstractCurdService } = await import('@/framework')
    const KTable = (await import('@/component/framework/KTable.vue')).default
    const { useTable } = await import('@/hook/kubo/useTable')

    @Model({ label: '契约实体' })
    class ContractEntity extends KEntity {
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

    const HostComponent = defineComponent({
      components: { KTable },
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
    const { KEntity } = await import('@/base/KEntity')
    const { AbstractCurdService } = await import('@/framework')
    const { useTable } = await import('@/hook/kubo/useTable')
    const { defineComponent: define } = await import('vue')
    const { mount: mountComponent } = await import('@vue/test-utils')

    @Model({ label: '契约实体2' })
    class PageEntity extends KEntity {
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

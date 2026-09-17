// @vitest-environment jsdom
import { readdirSync, readFileSync } from 'node:fs'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * # F4 批 2：asset 域（物料 / 合同）迁移门禁
 *
 * 三道都守着**同类事故**，不是"跑一遍看看"：
 *
 * 1. **迁移完成度**：域内还 import `@airpower/web` 的文件必须是精确集合。
 *    当前剩下的都是"卡在缺件或跨域引用"的（合同编辑器要上传、选择器被未迁移域引用），
 *    每一项都在用例里写明原因 —— 数量断言做不到这件事。
 * 2. **数据层唯一入口**：页面必须走 `K*Service`（框架层基类）而不是旧 airpower 服务，
 *    否则会出现"新页面读 airpower 注册表、实体装饰器却在框架层"的静默错位。
 * 3. **业务判据不许在换底座时漂移**：合同的生效/终止是**领域动作 + 前端提示**，
 *    这里钉住「参与方不足两方时不发请求」「终止要二次确认」——
 *    这两条是真实业务规则，迁移时最容易"顺手简化"掉。
 */

const capturedErrors: unknown[] = []

beforeEach(() => {
  setActivePinia(createPinia())
  capturedErrors.length = 0
  vi.restoreAllMocks()
})

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

/**
 * 挂载页面。
 *
 * 每个用例一份独立的 `QueryClient`（不传的话用例之间会共用缓存，
 * "上一次断言过的列表"会漏进下一次，表现为偶发失败）；`retry: false` 让请求失败立即显形。
 */
async function mountPage(component: unknown) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(component as never, {
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      directives: (await import('@/directives')).directives,
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
    },
  } as never)
}

/**
 * 剥掉注释后再找依赖。
 *
 * ⚠️ 不写成 `^\s*import[^\n]*from '...'`：本仓的具名 import 常被 eslint 格式化成**多行**
 * （`import {\n  ADialog,\n} from '@airpower/web'`），那种写法会漏判 ——
 * 漏判的后果是"域内还有 airpower 依赖却断言通过"，正好是这条门禁要防的事。
 * 裸的 `from '...'` 只会出现在 import/export 语句里，所以直接匹配它。
 */
/**
 * 取**行操作**按钮。
 *
 * ⚠️ 不能直接 `wrapper.findAll('button')` 里按文案找：Element Plus 的 `ElTable` 会先在
 * 一个隐藏容器（`.el-table__hidden-columns`）里渲染一遍列插槽来收集内容，
 * 那一份里 `row` 是 undefined —— 按文案取到的第一个"生效"就是它（禁用态、点了没反应）。
 * 这里只在真正的 `.el-table__body` 里找。
 */
/** 行操作按钮的最小形状（只需要"读文案 + 能点"；泛型组件的 wrapper 类型在这里会退化） */
interface IRowActionButton {
  text: () => string
  trigger: (event: string) => Promise<void>
}

function rowActionButtons(wrapper: { findAll: (selector: string) => unknown[] }, label: string): IRowActionButton[] {
  return wrapper
    .findAll('.el-table__body button')
    .filter(button => (button as IRowActionButton).text().trim() === label) as IRowActionButton[]
}

function importsAirpower(source: string): boolean {
  const code = source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\/[^\n]*/g, '')
  return /from\s+'@airpower\/web'|import\s*\(\s*'@airpower\/web'/.test(code)
}

function listVueFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? listVueFiles(`${dir}/${entry.name}`) : [`${dir}/${entry.name}`])
}

describe('f4 · 批 2（asset）迁移完成度', () => {
  /**
   * 待迁清单（每条都写清卡在哪）：
   * - `material/component/selector.vue`：airpower `ASelector`，物料选择器，被 **wms 入库/出库**
   *   与 **channel 采购/销售** 的明细编辑器引用（那两域属于批 4/5）→ 随域迁移换成 `KSelector`。
   *
   * 2026-09 更新：合同那三件（`contract/component/{editor,editParticipant,selector}.vue`）与它们
   * 的 barrel **从未被任何页面引用**（批 2 把合同列表改成 `KFormDialog` 后就成了孤岛，barrel 只被
   * 自己人互相 import）→ 已按"零消费者"删除，不再需要"迁移"。
   * ⚠️ 它们承载的**参与者/附件编辑**能力目前没有替代实现，重建时用新底座
   * （`KFormDialog` + 上传件）在批 2B 或 system 批里补 —— 别把"删了死件"误读成"这功能不需要"。
   */
  const ALLOWED = [
    'src/view/console/asset/material/component/selector.vue',
  ]

  it('域内仍依赖 airpower 的文件 = 明确的待迁集合', () => {
    const offenders = listVueFiles('src/view/console/asset')
      .filter(file => importsAirpower(readFileSync(file, 'utf8')))
      .sort()
    expect(
      offenders,
      offenders.length === 0
        ? '整域迁完，可以把这条断言收紧成 toEqual([])'
        : '域内还有 airpower 依赖，若已迁完请同步收紧本用例的 ALLOWED',
    ).toEqual([...ALLOWED].sort())
  })

  it('列表/详情页走新底座（KPanel/KTable/KFormDialog/KDetail + K*Service）', () => {
    const cases: [string, string[]][] = [
      ['src/view/console/asset/material/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KSelector', 'KMaterialService', 'useTable']],
      ['src/view/console/asset/contract/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KContractService', 'useTable']],
      ['src/view/console/asset/contract/component/detail.vue', ['KDialog', 'KDetail', 'KContractService', 'useDetail']],
    ]
    for (const [file, needles] of cases) {
      const source = readFileSync(file, 'utf8')
      for (const needle of needles) {
        expect(source.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
      // 旧适配器 / 视图直连 model 都不许再出现（批 2 的全部意义就在这）。
      // ⚠️ 必须**剥注释后再判**：这些文件的迁移说明里会成段引用旧写法
      // （如注释里的 `useTanStackTable(MaterialService, ...)` 对照表），
      // 直接 `includes` 会把"文档写得好"判成"代码没改"。
      const code = source
        .replace(/\/\*[\s\S]*?\*\//g, '')
        .replace(/<!--[\s\S]*?-->/g, '')
        .replace(/\/\/[^\n]*/g, '')
      expect(code.includes('useTanStackTable'), `${file} 仍在用旧适配器`).toBe(false)
      // 口径与 ESLint 的 `no-restricted-imports` 一致：禁的是**直连 Service**（实体/枚举做类型可以用）
      expect(/from '@\/model\/[^']*Service'/.test(code), `${file} 视图直连了 model 的 Service（应经 hook 转发）`).toBe(false)
    }
  })

  it('两个域的实体都做了双注册（框架层装饰器齐全，K 服务指向同一 baseUrl）', async () => {
    const { getFormConfigList, getTableConfigList } = await import('@/framework')
    const { MaterialEntity } = await import('@/model/asset/material/MaterialEntity')
    const { ContractEntity } = await import('@/model/asset/contract/ContractEntity')
    const { KMaterialService } = await import('@/model/asset/material/KMaterialService')
    const { KContractService } = await import('@/model/asset/contract/KContractService')
    const { MaterialService } = await import('@/model/asset/material/MaterialService')
    const { ContractService } = await import('@/model/asset/contract/ContractService')

    // 表格列 / 表单字段都非空 —— 这是"元数据驱动渲染"能成立的前提
    expect(getTableConfigList(MaterialEntity).length).toBeGreaterThan(4)
    expect(getFormConfigList(MaterialEntity).length).toBeGreaterThan(4)
    expect(getTableConfigList(ContractEntity).length).toBeGreaterThan(4)
    expect(getFormConfigList(ContractEntity).length).toBeGreaterThan(3)

    // 新旧服务同 baseUrl：过渡期两份实现必须指向同一个后端接口，否则数据会"漂移"
    expect(new KMaterialService().baseUrl).toBe(new MaterialService().baseUrl)
    expect(new KContractService().baseUrl).toBe(new ContractService().baseUrl)
  })

  it('物料价格列：@KTable 里是金额列且默认隐藏（脱敏口径与后端一致）', async () => {
    const { getTableConfig } = await import('@/framework')
    const { MaterialEntity } = await import('@/model/asset/material/MaterialEntity')
    for (const key of ['purchasePrice', 'salePrice']) {
      const column = getTableConfig(MaterialEntity, key)
      expect(column?.money, `${key} 不是金额列`).toBe(true)
      expect(column?.hide, `${key} 默认隐藏`).toBe(true)
    }
  })
})

describe('f4 · 批 2 页面行为（换底座不许改业务判据）', () => {
  it('合同列表：参与方不足两方时给出提示且**不发请求**', async () => {
    const { KContractService } = await import('@/model/asset/contract/KContractService')
    const { FeedbackUtil } = await import('@/framework')
    const toastError = vi.spyOn(FeedbackUtil, 'toastError').mockImplementation(() => {})
    const enforce = vi.spyOn(KContractService.prototype, 'enforce').mockResolvedValue()
    const getPage = vi.spyOn(KContractService.prototype, 'getPage').mockResolvedValue({
      list: [{
        id: 1,
        name: '年度框架采购合同',
        code: 'HT001',
        status: 1,
        participantList: [{ role: 1 }],
        documentList: [],
      }],
      total: 1,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const Page = (await import('@/view/console/asset/contract/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('年度框架采购合同')
    }, { timeout: 8000 })

    const enforceButton = rowActionButtons(wrapper, '生效')[0]
    expect(enforceButton, '没有渲染出「生效」行操作').toBeTruthy()
    await enforceButton!.trigger('click')
    await flushPromises()

    expect(toastError).toHaveBeenCalledWith('参与方至少有两方，请先确认')
    expect(enforce).not.toHaveBeenCalled()
    expect(getPage).toHaveBeenCalled()
    expect(capturedErrors).toEqual([])
  })

  it('合同列表：终止必须二次确认，确认后才调服务', async () => {
    const { KContractService } = await import('@/model/asset/contract/KContractService')
    const { FeedbackUtil } = await import('@/framework')
    const confirmWarning = vi.spyOn(FeedbackUtil, 'confirmWarning').mockResolvedValue()
    const stop = vi.spyOn(KContractService.prototype, 'stop').mockResolvedValue()
    vi.spyOn(KContractService.prototype, 'getPage').mockResolvedValue({
      list: [{
        id: 2,
        name: '设备维保服务合同',
        code: 'HT002',
        status: 2,
        participantList: [],
        documentList: [],
      }],
      total: 1,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const Page = (await import('@/view/console/asset/contract/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('设备维保服务合同')
    }, { timeout: 8000 })

    const stopButton = rowActionButtons(wrapper, '终止')[0]
    await stopButton!.trigger('click')
    await flushPromises()

    expect(confirmWarning).toHaveBeenCalledWith('是否确认终止这个合同？', '终止合同')
    expect(stop).toHaveBeenCalledTimes(1)
    expect(capturedErrors).toEqual([])
  })
})

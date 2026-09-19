// @vitest-environment jsdom
import { readdirSync, readFileSync } from 'node:fs'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * # F4 批 4：wms 域（入库 / 出库 / 移库 / 库存）迁移门禁
 *
 * 三条主线，每条都守着一类**真实事故**：
 *
 * 1. **迁移完成度**：域内视图与 hook 必须零 `@airpower/web` 依赖。
 *    wms 是业务主线（出入库/移库/库存），这里漏一个文件就意味着「主线上还有一页读 airpower 注册表」。
 * 2. **单据闭环不许漂移**：审核 / 驳回 / 作业（添加完成数量）三个动作的**端点与载荷形状**
 *    是后端契约（`BaseBillController`），迁移时最容易「顺手改顺手写」。这里直接钉住服务层行为。
 * 3. **演示数据/元数据可用**：K 页面渲染全靠 `@KTable` / `@KForm` 元数据 +
 *    `@KType`（嵌套明细要能实例化成实体，否则 `copyOnlyId()` 之类的实体方法在提交时炸）。
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

/** 剥注释后判断是否依赖 airpower（注释里引用旧写法是**文档**，不是依赖） */
function importsAirpower(source: string): boolean {
  const code = source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\/[^\n]*/g, '')
  return /from\s+'@airpower\/web'|import\s*\(\s*'@airpower\/web'/.test(code)
}

function listFiles(dir: string, extension: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap(entry =>
    entry.isDirectory() ? listFiles(`${dir}/${entry.name}`, extension) : [`${dir}/${entry.name}`])
}

/** 剥注释后的源码（页面断言用） */
function codeOf(file: string): string {
  return readFileSync(file, 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\/[^\n]*/g, '')
}

interface IRowActionButton {
  text: () => string
  trigger: (event: string) => Promise<void>
}

function rowActionButtons(wrapper: { findAll: (selector: string) => unknown[] }, label: string): IRowActionButton[] {
  return wrapper
    .findAll('.el-table__body button')
    .filter(button => (button as IRowActionButton).text().trim() === label) as IRowActionButton[]
}

async function mountPage(component: unknown, props?: Record<string, unknown>, attachToBody = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(component as never, {
    props,
    // ⚠️ 走 demo 适配器的「端到端」用例必须 attachTo document.body：
    // 不 attach 时组件树是**游离**的，`document.body.querySelectorAll('.el-table__body ...')`
    // 找不到任何列表行（只有 teleport 出去的弹窗/分页），会得出「列表没数据」的假结论。
    attachTo: attachToBody ? document.body : undefined,
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      directives: (await import('@/directives')).directives,
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
    },
  } as never)
}

/** 单据列表页用的一行（形态与后端 `getPage` 返回值一致） */
function billRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 7,
    billCode: 'RK2026090001',
    type: 1,
    status: 1,
    createTime: 1757000000000,
    updateTime: 1757000000000,
    canAudit: () => true,
    canReject: () => true,
    ...overrides,
  }
}

function pageResponse(list: unknown[]) {
  return { list, total: list.length, pageCount: 1, page: { pageNum: 1, pageSize: 20 } } as never
}

describe('f4 · 批 4（wms）迁移完成度', () => {
  it('域内视图与 hook 零 airpower 依赖（域内没有例外文件）', () => {
    const files = [
      ...listFiles('src/view/console/wms', '.vue'),
      ...listFiles('src/hook/wms', '.ts'),
    ]
    const offenders = files.filter(file => importsAirpower(readFileSync(file, 'utf8'))).sort()
    expect(
      offenders,
      'wms 域还有 airpower 依赖；若确实是跨域选择器，请把它的 airpower import 留在那个域（接缝函数），不要在 wms 里直接 import',
    ).toEqual([])
  })

  it('四个列表页走新底座（KPanel/KTable/useTable），编辑器走 KFormDialog + KDetailTable', () => {
    const lists = [
      'src/view/console/wms/input/list.vue',
      'src/view/console/wms/output/list.vue',
      'src/view/console/wms/move/list.vue',
      'src/view/console/wms/inventory/list.vue',
    ]
    for (const file of lists) {
      const code = codeOf(file)
      for (const needle of ['KPanel', 'KTable', 'useTable']) {
        expect(code.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
      expect(code.includes('useTanStackTable'), `${file} 仍在用旧适配器`).toBe(false)
      expect(/\bATable\b|\bAPanel\b|\bATreeBox\b/.test(code), `${file} 还有 airpower 组件`).toBe(false)
    }

    const editors = [
      ['src/view/console/wms/input/component/editor.vue', 'KInputService'],
      ['src/view/console/wms/output/component/editor.vue', 'KOutputService'],
      ['src/view/console/wms/move/component/editor.vue', 'KMoveService'],
    ] as const
    for (const [file, service] of editors) {
      const code = codeOf(file)
      for (const needle of ['KFormDialog', 'KDetailTable', service]) {
        expect(code.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
    }

    const details = [
      ['src/view/console/wms/input/component/detail.vue', 'KInputService'],
      ['src/view/console/wms/output/component/detail.vue', 'KOutputService'],
      ['src/view/console/wms/move/component/detail.vue', 'KMoveService'],
    ] as const
    for (const [file, service] of details) {
      const code = codeOf(file)
      for (const needle of ['KDialog', 'KDetailTable', 'useDetail', service]) {
        expect(code.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
    }
  })

  it('四个列表页都接了后端 `getCount`（统计条），且与表格共享同一份 queryKey 前缀', () => {
    const pages: Array<[string, string, string]> = [
      ['src/view/console/wms/input/list.vue', 'wms:input', 'KInputService'],
      ['src/view/console/wms/output/list.vue', 'wms:output', 'KOutputService'],
      ['src/view/console/wms/move/list.vue', 'wms:move', 'KMoveService'],
      ['src/view/console/wms/inventory/list.vue', 'wms:inventory', 'KInventoryService'],
    ]
    for (const [file, queryKey, service] of pages) {
      const code = codeOf(file)
      expect(code.includes('useResourceCount'), `${file} 没接统计条（后端 getCount 无人消费）`).toBe(true)
      expect(code.includes('KStat'), `${file} 没渲染 KStat`).toBe(true)
      expect(code.includes(`queryKey: ['${queryKey}']`), `${file} 统计条与表格的 queryKey 前缀不一致（刷新时会对不上）`).toBe(true)
      expect(code.includes(`new ${service}()`), `${file} 统计条没有用本域服务`).toBe(true)
    }
    // 库存页的"总记录"忽略当前筛选，否则它与「当前筛选」会是同一个数
    const inventory = codeOf('src/view/console/wms/inventory/list.vue')
    expect(inventory.includes('ignoreFilter: true'), '库存页缺少忽略筛选的总记录项').toBe(true)
  })

  it('统计条：点状态计数 → 该状态进表格筛选；再点一次取消', async () => {
    const { KInputService } = await import('@/model/wms/input/KInputService')
    const getPage = vi.spyOn(KInputService.prototype, 'getPage').mockResolvedValue(pageResponse([billRow()]))
    vi.spyOn(KInputService.prototype, 'getCount').mockResolvedValue(3)
    const Page = (await import('@/view/console/wms/input/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('审核中')
    }, { timeout: 8000 })

    // 计数来自后端 getCount（不是把当前页的行数数一遍）；等渲染结果，别只等调用发生
    expect(KInputService.prototype.getCount).toHaveBeenCalled()
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('3')
    }, { timeout: 8000 })

    const chip = wrapper.findAll('.k-stat__item').find(item => item.text().includes('已完成'))
    expect(chip, '统计条没有渲染「已完成」项').toBeTruthy()
    await chip!.trigger('click')
    await vi.waitFor(() => {
      const last = getPage.mock.calls.at(-1)?.[0] as { filter?: { status?: number } } | undefined
      expect(last?.filter?.status, '点统计条没有把状态筛进表格').toBe(4)
    }, { timeout: 8000 })

    await chip!.trigger('click')
    await vi.waitFor(() => {
      const last = getPage.mock.calls.at(-1)?.[0] as { filter?: { status?: number } } | undefined
      expect(last?.filter?.status, '再点一次没有取消筛选').toBeUndefined()
    }, { timeout: 8000 })
  })

  it('统计条降级：单项 getCount 失败（如无权限 403）显示 `-`，不影响表格与整页', async () => {
    const { KInputService } = await import('@/model/wms/input/KInputService')
    vi.spyOn(KInputService.prototype, 'getPage').mockResolvedValue(pageResponse([billRow()]))
    vi.spyOn(KInputService.prototype, 'getCount').mockRejectedValue(new Error('FORBIDDEN'))
    const Page = (await import('@/view/console/wms/input/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.find('.k-stat').text()).toContain('-')
    }, { timeout: 8000 })
    await vi.waitFor(() => {
      expect(wrapper.text(), '统计条失败不该把表格拖成空').toContain('RK2026090001')
    }, { timeout: 8000 })
    expect(capturedErrors).toEqual([])
  })

  it('库存只读页：没有增删改入口，但保留导出', () => {
    const code = codeOf('src/view/console/wms/inventory/list.vue')
    expect(code.includes('hide-actions')).toBe(true)
    expect(code.includes('hide-add')).toBe(true)
    expect(code.includes('Permissions.Wms.Inventory.export')).toBe(true)
  })
})

describe('f4 · 批 4 单据元数据与实体双注册', () => {
  it('四个实体在框架注册表里有列/表单配置，且 K 服务指向后端真实 baseUrl', async () => {
    const { getFormConfigList, getTableConfigList } = await import('@/framework')
    const { InventoryEntity } = await import('@/model/wms/inventory/InventoryEntity')
    const { MoveEntity } = await import('@/model/wms/move/MoveEntity')
    const { InputEntity } = await import('@/model/wms/input/InputEntity')
    const { OutputEntity } = await import('@/model/wms/output/OutputEntity')

    // 库存是只读资源：**只有列，没有表单**（页面也没有新增/编辑入口）
    expect(getTableConfigList(InventoryEntity).length, '库存没有表格列').toBeGreaterThan(3)
    expect(getFormConfigList(InventoryEntity).length, '库存不该有表单字段').toBe(0)

    for (const entity of [MoveEntity, InputEntity, OutputEntity]) {
      expect(getTableConfigList(entity).length, `${entity.name} 没有表格列`).toBeGreaterThan(3)
      expect(getFormConfigList(entity).length, `${entity.name} 没有表单字段`).toBeGreaterThan(0)
    }

    const { KInventoryService } = await import('@/model/wms/inventory/KInventoryService')
    const { KMoveService } = await import('@/model/wms/move/KMoveService')
    const { KInputService } = await import('@/model/wms/input/KInputService')
    const { KOutputService } = await import('@/model/wms/output/KOutputService')
    // baseUrl 就是后端 `@Api("...")` 的路由段，写错等于整页 404
    expect(new KInventoryService().baseUrl).toBe('inventory')
    expect(new KMoveService().baseUrl).toBe('move')
    expect(new KInputService().baseUrl).toBe('input')
    expect(new KOutputService().baseUrl).toBe('output')
  })

  it('单据状态/类型不在 K 表单里（它们由状态机与来源单据决定，用户不能直接改）', async () => {
    const { getFormConfig } = await import('@/framework')
    const { MoveEntity } = await import('@/model/wms/move/MoveEntity')
    const { MoveDetailEntity } = await import('@/model/wms/move/MoveDetailEntity')
    const { InputEntity } = await import('@/model/wms/input/InputEntity')
    const { OutputEntity } = await import('@/model/wms/output/OutputEntity')

    expect(getFormConfig(MoveEntity, 'status'), '移库状态不该出现在表单里').toBeUndefined()
    expect(getFormConfig(MoveEntity, 'rejectReason'), '驳回原因由驳回动作写入，不该可编辑').toBeUndefined()
    expect(getFormConfig(InputEntity, 'type'), '入库类型由来源单据决定').toBeUndefined()
    expect(getFormConfig(OutputEntity, 'type'), '出库类型由来源单据决定').toBeUndefined()
    // 明细数组由页面用 KDetailTable 渲染，不是 KFormDialog 的字段
    expect(getFormConfig(MoveEntity, 'details')).toBeUndefined()
    // 但数量必须是表单字段（明细子弹窗的校验规则由它生成）
    expect(getFormConfig(MoveDetailEntity, 'quantity')?.requiredNumber).toBe(true)
  })

  it('明细数组能递归实例化成实体（`@KType` 守住提交时的 `copyOnlyId()`）', async () => {
    const { fill } = await import('@/framework')
    const { MoveEntity } = await import('@/model/wms/move/MoveEntity')
    const { InputEntity } = await import('@/model/wms/input/InputEntity')
    const { InventoryEntity } = await import('@/model/wms/inventory/InventoryEntity')

    const move = fill(new MoveEntity(), {
      id: 1,
      details: [{ id: 11, quantity: 5, inventory: { id: 3, material: { name: '6061 铝板' } } }],
    })
    expect(move.details[0]!.constructor.name, '移库明细没有实例化成实体（缺 @KType）').toBe('MoveDetailEntity')
    expect(move.details[0]!.inventory!.constructor.name, '嵌套库存没有实例化').toBe('InventoryEntity')
    expect(move.details[0]!.inventory!.material!.name).toBe('6061 铝板')
    expect(typeof move.details[0]!.copyOnlyId, '明细实体缺 copyOnlyId：提交时会 TypeError').toBe('function')

    const input = fill(new InputEntity(), { id: 2, details: [{ id: 21, quantity: 3 }] })
    expect(input.details[0]!.constructor.name).toBe('InputDetailEntity')

    const inventory = fill(new InventoryEntity(), { id: 5, material: { name: '304 不锈钢管' }, storage: { name: '原料一库' } })
    expect(inventory.material!.constructor.name).toBe('MaterialEntity')
    expect(inventory.storage!.constructor.name).toBe('StorageEntity')
  })
})

describe('f4 · 批 4 单据闭环（审核 / 驳回 / 作业）', () => {
  /** 拦住服务里最底层的请求，断言端点名与载荷形状（这是后端契约） */
  async function spyRequest(ServiceClass: { prototype: object }) {
    const request = vi.fn().mockResolvedValue(undefined)
    // `api(url)` 是服务基类的受保护方法：这里只关心它被哪个端点调用、载荷是什么，
    // 所以直接替换成「返回一个记录了 request 的壳」（类型用最小结构断言，避免把基类内部类型拖进来）
    const target = ServiceClass.prototype as unknown as { api: (url: string) => unknown }
    const api = vi.spyOn(target, 'api')
    ;(api as unknown as { mockReturnValue: (value: unknown) => void }).mockReturnValue({ request })
    return { request, api }
  }

  it('审核 / 驳回 / 作业 / 一键完成的端点与载荷与后端一致', async () => {
    const { KMoveService } = await import('@/model/wms/move/KMoveService')
    const { request, api } = await spyRequest(KMoveService)
    const service = new KMoveService()

    await service.audit({ id: 9 } as never)
    expect(api).toHaveBeenLastCalledWith('audit')
    expect(request).toHaveBeenLastCalledWith({ id: 9 })

    await service.reject({ id: 9 } as never, '库存对不上')
    expect(api).toHaveBeenLastCalledWith('reject')
    // 后端 `reject` 用 `WhenReject` 校验：必须同时带 id 与 rejectReason
    expect(request).toHaveBeenLastCalledWith({ id: 9, rejectReason: '库存对不上' })

    // 作业的载荷是**明细**，只带 id 与本次数量（来源库存由后端按明细 id 自己查）
    await service.addDetailFinishQuantity({ id: 31, quantity: 4 } as never)
    expect(api).toHaveBeenLastCalledWith('addFinish')
    expect(request).toHaveBeenLastCalledWith({ id: 31, quantity: 4 })

    // 单据基类只有三个动作：后端没有「一键完成」端点（`setBillDetailsAllFinished`
    // 是后端把单据推进终态的**内部回调**，明细必须逐条作业完成）—— 见 framework/bill.ts 头注释
    expect((service as unknown as Record<string, unknown>).setBillDetailsAllFinished, '幻影端点方法又回来了').toBeUndefined()
  })

  it('移库列表：审核要二次确认，驳回要填原因再确认（两步都不能省）', async () => {
    const { KMoveService } = await import('@/model/wms/move/KMoveService')
    const { FeedbackUtil } = await import('@/framework')
    const confirmWarning = vi.spyOn(FeedbackUtil, 'confirmWarning').mockResolvedValue()
    const toastSuccess = vi.spyOn(FeedbackUtil, 'toastSuccess').mockImplementation(() => {})
    const audit = vi.spyOn(KMoveService.prototype, 'audit').mockResolvedValue()
    const reject = vi.spyOn(KMoveService.prototype, 'reject').mockResolvedValue()
    vi.spyOn(KMoveService.prototype, 'getPage').mockResolvedValue(pageResponse([billRow()]))

    const Page = (await import('@/view/console/wms/move/list.vue')).default
    const wrapper = await mountPage(Page)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('RK2026090001')
    }, { timeout: 8000 })

    rowActionButtons(wrapper, '审核')[0]!.trigger('click')
    await flushPromises()
    expect(confirmWarning).toHaveBeenCalledWith('是否确认审核选择的移库单？', '审核移库单')
    expect(audit).toHaveBeenCalledTimes(1)
    expect(toastSuccess).toHaveBeenCalledWith('审核成功')

    // 驳回：先弹原因框；原因框确认后还要再确认一次，才真正发请求
    rowActionButtons(wrapper, '驳回')[0]!.trigger('click')
    await flushPromises()
    const reasonInput = document.body.querySelector('.el-dialog textarea') as HTMLTextAreaElement | null
    expect(reasonInput, '驳回原因弹窗没有渲染出来').toBeTruthy()
    reasonInput!.value = '目标仓库填错了'
    reasonInput!.dispatchEvent(new Event('input'))
    await flushPromises()
    // 驳回原因弹窗的确认按钮：按主按钮类取，别按文案（文案会随组件默认值变）
    const confirmButton = document.body.querySelector('.el-dialog__footer .el-button--primary') as HTMLButtonElement | null
    expect(confirmButton, '驳回原因弹窗没有确认按钮').toBeTruthy()
    confirmButton!.click()
    await flushPromises()

    expect(confirmWarning).toHaveBeenLastCalledWith('是否确认驳回选择的移库单？', '驳回移库单')
    expect(reject).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), '目标仓库填错了')
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('移库详情：只有「移动中」的单据才能点「作业」，作业提交的是本次数量', async () => {
    const { KMoveService } = await import('@/model/wms/move/KMoveService')
    const addFinish = vi.spyOn(KMoveService.prototype, 'addDetailFinishQuantity').mockResolvedValue()
    vi.spyOn(KMoveService.prototype, 'getDetail').mockResolvedValue({
      id: 7,
      billCode: 'YK2026090007',
      status: 4,
      details: [{ id: 31, quantity: 10, finishQuantity: 4, inventory: { id: 3, material: { name: '6061 铝板' } } }],
    } as never)

    const MoveDetail = (await import('@/view/console/wms/move/component/detail.vue')).default
    await mountPage(MoveDetail, { modelValue: true, id: 7 })
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain('6061 铝板')
    }, { timeout: 8000 })

    // 单据已完成（状态 4 = DONE），作业按钮必须禁用
    const finishButton = Array.from(document.body.querySelectorAll('.el-table__body button'))
      .find(button => button.textContent?.trim() === '作业') as HTMLButtonElement | undefined
    expect(finishButton, '详情里没有渲染「作业」按钮').toBeTruthy()
    expect(finishButton!.disabled).toBe(true)

    finishButton!.click()
    await flushPromises()
    expect(addFinish, '明细禁用状态仍发出作业请求').not.toHaveBeenCalled()
    expect(capturedErrors).toEqual([])
    document.body.innerHTML = ''
  })
})

describe('f4 · 批 4 演示数据（详情弹窗别空着）', () => {
  it('入库/出库/移库的行带 billCode 与明细，明细里是嵌套对象而不是扁平字段', async () => {
    const { buildRow } = await import('@/demo/mock/specs')

    for (const resource of ['input', 'output', 'move'] as const) {
      const row = buildRow(resource, 3)
      // 单据列表第一列读的是 billCode（不是 code）：缺了就是空列
      expect(row.billCode, `${resource} 演示行缺 billCode`).toBe(row.code)

      const details = row.details as Array<Record<string, unknown>>
      expect(Array.isArray(details), `${resource} 演示行缺 details 数组`).toBe(true)
      expect(details.length).toBeGreaterThan(0)

      for (const detail of details) {
        // K 侧明细列渲染器读的是 `row.inventory.material.code` 这种嵌套路径
        const material = resource === 'input'
          ? detail.material as Record<string, unknown>
          : ((detail.inventory as Record<string, unknown>).material) as Record<string, unknown>
        expect(material?.code, `${resource} 明细缺物料嵌套对象`).toBeTruthy()
        expect((material.unit as Record<string, unknown>)?.name, `${resource} 明细缺物料单位`).toBeTruthy()
        expect(typeof detail.quantity).toBe('number')
        expect(typeof detail.finishQuantity).toBe('number')
        // 入库要目标仓库、出库/移库要来源库存 —— 作业弹窗靠它带 storage/inventory
        const holder = resource === 'input' ? detail.storage : detail.inventory
        expect(holder, `${resource} 明细缺 storage/inventory`).toBeTruthy()
      }
    }
  })

  it('明细的完成度覆盖 0 / 部分 / 全部三种，作业按钮的可用性才有可演示的样本', async () => {
    const { buildRow } = await import('@/demo/mock/specs')
    const finishes: number[] = []
    for (let index = 1; index <= 12; index++) {
      for (const detail of buildRow('move', index).details as Array<Record<string, number>>) {
        finishes.push(detail.finishQuantity === 0
          ? 0
          : detail.finishQuantity === detail.quantity ? 2 : 1)
      }
    }
    expect(new Set(finishes)).toEqual(new Set([0, 1, 2]))
  })

  it('库存是只读资源：演示行不该虚构出明细数组（页面也没有明细表）', async () => {
    const { buildRow } = await import('@/demo/mock/specs')
    expect(buildRow('inventory', 5).details).toBeUndefined()
  })
})

describe('f4 · 批 4 演示模式端到端（真实 mock 数据 → 点开详情能看见明细）', () => {
  /**
   * ⚠️ 两个 adapter 都要设：`axios.defaults` 是全局默认值（demo 入口用这个），
   * 而本仓的 `request` 实例创建时就把 `axios.defaults` 深拷进自己的 defaults ——
   * 只设一个的话请求会真的发出去，表现为「弹窗开了但表是空的」，不是报错。
   * 断言查 `document.body`：`ElDialog` 默认 teleport 出组件树。
   */
  beforeEach(async () => {
    const { createDemoAdapter } = await import('@/demo/mock/adapter')
    axios.defaults.adapter = createDemoAdapter([0, 0])
    const { request } = await import('@/api/axios')
    request.defaults.adapter = axios.defaults.adapter
  })

  it('入库单列表：点「查看」后详情弹窗里是真实明细行（物料 / 数量 / 目标仓库）', async () => {
    const { buildRow } = await import('@/demo/mock/specs')
    const expected = buildRow('input', 1)
    const expectedDetails = expected.details as Array<Record<string, unknown>>

    const InputList = (await import('@/view/console/wms/input/list.vue')).default
    await mountPage(InputList, undefined, true)
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(String(expected.billCode))
    }, { timeout: 8000 })

    const viewButton = Array.from(document.body.querySelectorAll('.el-table__body button'))
      .find(button => button.textContent?.trim() === '查看') as HTMLButtonElement | undefined
    expect(viewButton, '列表里没有渲染「查看」按钮').toBeTruthy()
    viewButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    const firstMaterial = expectedDetails[0].material as Record<string, unknown>
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(String(firstMaterial.name))
    }, { timeout: 8000 })

    // 明细行数 = 演示造数给的条数（缺 details 时这里就是 0 行，即「点开空空如也」）
    const detailRows = document.body.querySelectorAll('.el-dialog .el-table__body tr')
    expect(detailRows.length).toBeGreaterThanOrEqual(expectedDetails.length)
    expect(capturedErrors).toEqual([])
    document.body.innerHTML = ''
  })

  it('出库单列表：点「查看」后能看到来源库存（inventory.material 嵌套渲染）', async () => {
    const { buildRow } = await import('@/demo/mock/specs')
    // ⚠️ 必须是列表里**第一行**（点的是第一个「查看」按钮）：演示列表按 id 升序，
    // 拿第 2 行来断言会去点第 1 行的按钮，得到「点开了别的单据」的假失败。
    const expected = buildRow('output', 1)
    const firstDetail = (expected.details as Array<Record<string, unknown>>)[0]
    const inventory = firstDetail.inventory as Record<string, unknown>

    const OutputList = (await import('@/view/console/wms/output/list.vue')).default
    await mountPage(OutputList, undefined, true)
    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(String(expected.billCode))
    }, { timeout: 8000 })

    const viewButton = Array.from(document.body.querySelectorAll('.el-table__body button'))
      .find(button => button.textContent?.trim() === '查看') as HTMLButtonElement | undefined
    viewButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(String((inventory.material as Record<string, unknown>).name))
    }, { timeout: 8000 })
    expect(capturedErrors).toEqual([])
    document.body.innerHTML = ''
  })
})

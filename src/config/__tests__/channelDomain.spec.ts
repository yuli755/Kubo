// @vitest-environment jsdom
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * # F4 批 5：channel 域（客户 / 供应商 / 采购 / 销售 / 采购价 / 销售价）迁移门禁
 *
 * 三条主线：
 *
 * 1. **迁移完成度**：域内视图与 hook 零 `@airpower/web`；`api/channel/*`（typed client 链）
 *    与 8 个旧服务类全部退场 —— 这一域的旧链是"两套数据访问并存"最严重的地方。
 * 2. **后端契约不漂移**：报价查询（`getByMaterialAndSupplier` / `getByMaterialAndCustomer`）
 *    的载荷形状、单据四动作（审核 / 驳回 / 作业）走的是 `AbstractBillService`；
 *    价格是**数值型敏感字段**，页面的 `blockedSubmit` 拦截必须还在。
 * 3. **元数据可用**：K 页面靠 `@KTable` / `@KForm` + `@KType` 渲染；演示模式下
 *    采购/销售也是"单据"（`billCode` + `details`），点开详情不能是空弹窗。
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

async function mountPage(component: unknown, props?: Record<string, unknown>, attachToBody = false) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return mount(component as never, {
    props,
    attachTo: attachToBody ? document.body : undefined,
    global: {
      plugins: [[VueQueryPlugin, { queryClient }]],
      directives: (await import('@/directives')).directives,
      config: { errorHandler: (error: unknown) => capturedErrors.push(error) },
    },
  } as never)
}

function pageResponse(list: unknown[]) {
  return { list, total: list.length, pageCount: 1, page: { pageNum: 1, pageSize: 20 } } as never
}

describe('f4 · 批 5（channel）迁移完成度', () => {
  it('域内视图与 hook 零 airpower 依赖', () => {
    const files = [
      ...listFiles('src/view/console/channel', '.vue'),
      ...listFiles('src/hook/channel', '.ts'),
    ]
    const offenders = files.filter(file => importsAirpower(readFileSync(file, 'utf8'))).sort()
    expect(
      offenders,
      'channel 域还有 airpower 依赖；跨域入口请留在接缝函数（openDetail.ts）里',
    ).toEqual([])
  })

  it('typed client 链与旧服务类已全部退场', () => {
    expect(existsSync('src/api/channel'), 'api/channel 还在：typed client 链应当整目录删除').toBe(false)

    const oldServices = [
      'src/model/channel/customer/CustomerService.ts',
      'src/model/channel/supplier/SupplierService.ts',
      'src/model/channel/purchase/PurchaseService.ts',
      'src/model/channel/purchase/PurchaseDetailService.ts',
      'src/model/channel/sale/SaleService.ts',
      'src/model/channel/sale/SaleDetailService.ts',
      'src/model/channel/purchasePrice/PurchasePriceService.ts',
      'src/model/channel/salePrice/SalePriceService.ts',
    ]
    for (const file of oldServices) {
      expect(existsSync(file), `${file} 是 airpower 服务类，批 5 应当删除`).toBe(false)
    }

    // 视图禁止直连 model 服务（口径与 ESLint no-restricted-imports 一致）
    for (const file of listFiles('src/view/console/channel', '.vue')) {
      const code = codeOf(file)
      expect(code.includes('useTanStackTable'), `${file} 仍在用旧适配器`).toBe(false)
      expect(/from '@\/model\/[^']*Service'/.test(code), `${file} 直连了 model 的 Service`).toBe(false)
    }
  })

  it('两个列表页走新底座，两张价格页带脱敏守卫', () => {
    const cases: [string, string[]][] = [
      ['src/view/console/channel/customer/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KCustomerService', 'useTable', 'hide-enable']],
      ['src/view/console/channel/supplier/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'hide-delete', 'hide-enable']],
      ['src/view/console/channel/purchase/list.vue', ['KPanel', 'KTable', 'KPurchaseService', 'hide-delete', 'hide-enable', 'canAudit', 'canReject']],
      ['src/view/console/channel/sale/list.vue', ['KPanel', 'KTable', 'KSaleService', 'hide-delete', 'hide-enable', 'canAudit', 'canReject']],
      ['src/view/console/channel/purchasePrice/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KSelector', 'useMaskPolicy', 'blockedSubmit', 'formatMoney']],
      ['src/view/console/channel/salePrice/list.vue', ['KPanel', 'KTable', 'KFormDialog', 'KSelector', 'useMaskPolicy', 'blockedSubmit', 'formatMoney']],
      ['src/view/console/channel/purchase/component/editor.vue', ['KFormDialog', 'KDetailTable', 'DialogUtil', 'PurchaseDetailEditor']],
      ['src/view/console/channel/sale/component/editor.vue', ['KFormDialog', 'KDetailTable', 'DialogUtil', 'SaleDetailEditor']],
      ['src/view/console/channel/purchase/component/detail.vue', ['KDialog', 'KDetailTable', 'useDetail', 'addFinishEditor']],
      ['src/view/console/channel/sale/component/detail.vue', ['KDialog', 'KDetailTable', 'useDetail']],
    ]
    for (const [file, needles] of cases) {
      const source = readFileSync(file, 'utf8')
      for (const needle of needles) {
        expect(source.includes(needle), `${file} 缺少 ${needle}`).toBe(true)
      }
    }
  })

  it('旧文案已修正（销售单不再是「采购单」）', () => {
    const editor = readFileSync('src/view/console/channel/sale/component/editor.vue', 'utf8')
    expect(editor.includes('是否删除选中行的销售明细？'), '销售明细删除确认仍是采购文案').toBe(true)
    expect(editor.includes('销售明细'), '销售编辑器缺少「销售明细」分组标题').toBe(true)

    const salePrice = readFileSync('src/model/channel/salePrice/SalePriceEntity.ts', 'utf8')
    // 字段名与标题/取值必须一致：customerCode 取 code、customerName 取 name
    expect(/@KField\(\{ label: '客户编码' \}\)\s*customerCode!/.test(salePrice.replace(/\s+\n/g, '\n')), 'customerCode 的列标题/取值仍错位').toBe(true)
    expect(/@KField\(\{ label: '客户名称' \}\)\s*customerName!/.test(salePrice.replace(/\s+\n/g, '\n')), 'customerName 的列标题/取值仍错位').toBe(true)
  })
})

describe('f4 · 批 5 元数据与后端契约', () => {
  it('六个实体都有 K 元数据，单据的状态不进表单（由状态机驱动）', async () => {
    const { getFormConfig, getFormConfigList, getTableConfigList } = await import('@/framework')
    const { CustomerEntity } = await import('@/model/channel/customer/CustomerEntity')
    const { SupplierEntity } = await import('@/model/channel/supplier/SupplierEntity')
    const { PurchaseEntity } = await import('@/model/channel/purchase/PurchaseEntity')
    const { SaleEntity } = await import('@/model/channel/sale/SaleEntity')
    const { PurchasePriceEntity } = await import('@/model/channel/purchasePrice/PurchasePriceEntity')
    const { SalePriceEntity } = await import('@/model/channel/salePrice/SalePriceEntity')

    for (const Entity of [CustomerEntity, SupplierEntity, PurchaseEntity, SaleEntity, PurchasePriceEntity, SalePriceEntity]) {
      expect(getTableConfigList(Entity).length, `${Entity.name} 没有 K 表格元数据`).toBeGreaterThan(1)
      expect(getFormConfigList(Entity).length, `${Entity.name} 没有 K 表单元数据`).toBeGreaterThan(1)
    }

    // 状态由状态机写（审核 / 驳回 / 作业），表单里出现就是「用户能改状态」
    expect(getFormConfig(PurchaseEntity, 'status')).toBeUndefined()
    expect(getFormConfig(SaleEntity, 'status')).toBeUndefined()
    // 驳回原因同理：只在详情里展示
    expect(getFormConfig(PurchaseEntity, 'rejectReason')).toBeUndefined()
    expect(getFormConfig(SaleEntity, 'rejectReason')).toBeUndefined()
    // 明细数组不是表单字段（页面用 KDetailTable 渲染）
    expect(getFormConfig(PurchaseEntity, 'details')).toBeUndefined()
  })

  it('明细与关联对象能递归实例化（`@KType` 守住提交时的 copyOnlyId）', async () => {
    const { fill } = await import('@/framework')
    const { PurchaseEntity } = await import('@/model/channel/purchase/PurchaseEntity')
    const { SaleEntity } = await import('@/model/channel/sale/SaleEntity')
    const { SalePriceEntity } = await import('@/model/channel/salePrice/SalePriceEntity')

    const purchase = fill(new PurchaseEntity(), {
      id: 1,
      details: [{ id: 11, quantity: 5, material: { id: 3, name: '6061 铝板' }, supplier: { id: 4, name: '宝钢' } }],
    })
    expect(purchase.details[0]!.constructor.name, '采购明细没有实例化成实体（缺 @KType）').toBe('PurchaseDetailEntity')
    expect(purchase.details[0]!.material!.name).toBe('6061 铝板')
    expect(purchase.details[0]!.supplier!.name).toBe('宝钢')
    expect(typeof purchase.details[0]!.copyOnlyId, '明细实体缺 copyOnlyId：提交时会 TypeError').toBe('function')

    const sale = fill(new SaleEntity(), { id: 2, customer: { id: 9, name: '华东精密' }, details: [{ id: 21, quantity: 2 }] })
    expect(sale.customer!.constructor.name).toBe('CustomerEntity')
    expect(sale.details[0]!.constructor.name).toBe('SaleDetailEntity')

    const price = fill(new SalePriceEntity(), { id: 3, material: { id: 3 }, customer: { id: 9 } })
    expect(price.material!.constructor.name).toBe('MaterialEntity')
    expect(price.customer!.constructor.name).toBe('CustomerEntity')
  })

  it('k 服务指向同一批端点，报价查询发送的是「实体入参」载荷', async () => {
    const { KCustomerService } = await import('@/model/channel/customer/KCustomerService')
    const { KSupplierService } = await import('@/model/channel/supplier/KSupplierService')
    const { KPurchaseService } = await import('@/model/channel/purchase/KPurchaseService')
    const { KSaleService } = await import('@/model/channel/sale/KSaleService')
    const { KPurchasePriceService } = await import('@/model/channel/purchasePrice/KPurchasePriceService')
    const { KSalePriceService } = await import('@/model/channel/salePrice/KSalePriceService')

    const baseUrls: [string, string][] = [
      ['customer', new KCustomerService().baseUrl],
      ['supplier', new KSupplierService().baseUrl],
      ['purchase', new KPurchaseService().baseUrl],
      ['sale', new KSaleService().baseUrl],
      ['purchasePrice', new KPurchasePriceService().baseUrl],
      ['salePrice', new KSalePriceService().baseUrl],
    ]
    expect(baseUrls).toEqual([
      ['customer', 'customer'],
      ['supplier', 'supplier'],
      ['purchase', 'purchase'],
      ['sale', 'sale'],
      ['purchasePrice', 'purchasePrice'],
      ['salePrice', 'salePrice'],
    ])

    // 后端 `getByMaterialAndSupplier` 校验的是 `material` / `supplier` 两个关联对象，不是两个 id
    const seen: Array<{ url: string, payload: unknown }> = []
    function spyService(ServiceClass: { prototype: object }) {
      const requestRaw = vi.fn().mockResolvedValue({ id: 1, price: 88 })
      const target = ServiceClass.prototype as unknown as { api: (url: string) => unknown }
      const api = vi.spyOn(target, 'api')
      ;(api as unknown as { mockImplementation: (fn: (url: string) => unknown) => void }).mockImplementation((url: string) => {
        return {
          requestRaw: (payload: unknown) => {
            seen.push({ url, payload })
            return requestRaw(payload)
          },
        }
      })
    }

    spyService(KPurchasePriceService)
    const purchasePrice = await new KPurchasePriceService().getByMaterialAndSupplier(11, 22)
    expect(seen[0]!.url).toBe('getByMaterialAndSupplier')
    expect(seen[0]!.payload).toEqual({ material: { id: 11 }, supplier: { id: 22 } })
    expect(purchasePrice?.price).toBe(88)

    spyService(KSalePriceService)
    const salePrice = await new KSalePriceService().getByMaterialAndCustomer(33, 44)
    expect(seen[1]!.url).toBe('getByMaterialAndCustomer')
    expect(seen[1]!.payload).toEqual({ material: { id: 33 }, customer: { id: 44 } })
    expect(salePrice?.price).toBe(88)
  })

  it('单据四动作走 AbstractBillService（端点名是后端契约）', async () => {
    const { KPurchaseService } = await import('@/model/channel/purchase/KPurchaseService')
    const calls: Array<{ url: string, payload: unknown }> = []
    const request = vi.fn().mockResolvedValue(undefined)
    const target = KPurchaseService.prototype as unknown as { api: (url: string) => unknown }
    const api = vi.spyOn(target, 'api')
    ;(api as unknown as { mockImplementation: (fn: (url: string) => unknown) => void }).mockImplementation((url: string) => ({
      request: (payload: unknown) => {
        calls.push({ url, payload })
        return request(payload)
      },
    }))

    const service = new KPurchaseService()
    await service.audit({ id: 5 } as never)
    await service.reject({ id: 5 } as never, '数量与需求不符')
    await service.addDetailFinishQuantity({ id: 9, quantity: 3 } as never)

    expect(calls.map(call => call.url)).toEqual(['audit', 'reject', 'addFinish'])
    expect(calls[0]!.payload).toEqual({ id: 5 })
    expect(calls[1]!.payload).toEqual({ id: 5, rejectReason: '数量与需求不符' })
    // 作业载荷是**明细**（本次数量），不是整张单据
    expect(calls[2]!.payload).toEqual({ id: 9, quantity: 3 })
  })
})

/** 列表页用的一行采购单（形态与后端 `getPage` 返回一致；`canAudit/canReject` 由实体状态机给） */
function billRow(overrides: Record<string, unknown> = {}) {
  return {
    id: 7,
    billCode: 'CG2026090007',
    code: 'CG2026090007',
    status: 1,
    createTime: 1757000000000,
    updateTime: 1757000000000,
    details: [],
    canAudit: () => true,
    canReject: () => true,
    ...overrides,
  }
}

function rowActionButtons(wrapper: { findAll: (selector: string) => unknown[] }, label: string) {
  return (wrapper.findAll('.el-table__body button') as Array<{ text: () => string, trigger: (event: string) => Promise<void> }>)
    .filter(button => button.text().trim() === label)
}

describe('f4 · 批 5 单据动作：审核 / 驳回（走单据服务，不再有两套调用链）', () => {
  it('采购单列表：审核要二次确认，驳回要填原因再确认', async () => {
    const { FeedbackUtil } = await import('@/framework')
    const { KPurchaseService } = await import('@/model/channel/purchase/KPurchaseService')
    const confirmWarning = vi.spyOn(FeedbackUtil, 'confirmWarning').mockResolvedValue()
    const toastSuccess = vi.spyOn(FeedbackUtil, 'toastSuccess').mockImplementation(() => {})
    const audit = vi.spyOn(KPurchaseService.prototype, 'audit').mockResolvedValue()
    const reject = vi.spyOn(KPurchaseService.prototype, 'reject').mockResolvedValue()
    vi.spyOn(KPurchaseService.prototype, 'getPage').mockResolvedValue(pageResponse([billRow()]))

    const PurchaseList = (await import('@/view/console/channel/purchase/list.vue')).default
    const wrapper = await mountPage(PurchaseList)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('CG2026090007')
    }, { timeout: 8000 })

    await rowActionButtons(wrapper, '审核')[0]!.trigger('click')
    await flushPromises()
    expect(confirmWarning).toHaveBeenCalledWith('是否确认审核选择的采购单？', '审核采购单')
    expect(audit).toHaveBeenCalledTimes(1)
    expect(toastSuccess).toHaveBeenCalledWith('审核成功')

    // 驳回是两步：先填原因，再二次确认（两步都不能省）
    await rowActionButtons(wrapper, '驳回')[0]!.trigger('click')
    await flushPromises()
    const reasonInput = document.body.querySelector('.el-dialog textarea') as HTMLTextAreaElement | null
    expect(reasonInput, '驳回原因弹窗没有渲染出来').toBeTruthy()
    reasonInput!.value = '数量与需求不符'
    reasonInput!.dispatchEvent(new Event('input'))
    await flushPromises()
    const confirmButton = document.body.querySelector('.el-dialog__footer .el-button--primary') as HTMLButtonElement | null
    expect(confirmButton, '驳回原因弹窗没有确认按钮').toBeTruthy()
    confirmButton!.click()
    await flushPromises()

    expect(confirmWarning).toHaveBeenLastCalledWith('是否确认驳回选择的采购单？', '驳回采购单')
    expect(reject).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), '数量与需求不符')
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })

  it('销售单列表：驳回确认文案是「销售单」，不是采购单', async () => {
    const { FeedbackUtil } = await import('@/framework')
    const { KSaleService } = await import('@/model/channel/sale/KSaleService')
    const confirmWarning = vi.spyOn(FeedbackUtil, 'confirmWarning').mockResolvedValue()
    vi.spyOn(KSaleService.prototype, 'getPage').mockResolvedValue(pageResponse([{ ...billRow(), billCode: 'XS2026090007' }]))
    const reject = vi.spyOn(KSaleService.prototype, 'reject').mockResolvedValue()

    const SaleList = (await import('@/view/console/channel/sale/list.vue')).default
    const wrapper = await mountPage(SaleList)
    await vi.waitFor(() => {
      expect(wrapper.text()).toContain('XS2026090007')
    }, { timeout: 8000 })

    await rowActionButtons(wrapper, '驳回')[0]!.trigger('click')
    await flushPromises()
    const reasonInput = document.body.querySelector('.el-dialog textarea') as HTMLTextAreaElement | null
    reasonInput!.value = '客户改单了'
    reasonInput!.dispatchEvent(new Event('input'))
    await flushPromises()
    ;(document.body.querySelector('.el-dialog__footer .el-button--primary') as HTMLButtonElement).click()
    await flushPromises()

    expect(confirmWarning).toHaveBeenLastCalledWith('是否确认驳回选择的销售单？', '驳回销售单')
    expect(reject).toHaveBeenCalledWith(expect.objectContaining({ id: 7 }), '客户改单了')
    expect(capturedErrors).toEqual([])

    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

describe('f4 · 批 5 演示数据与端到端（采购单详情别是空弹窗）', () => {
  it('采购/销售在演示里也是「单据」：有 billCode、明细、金额与原因', async () => {
    const { buildRow } = await import('@/demo/mock/specs')

    for (const resource of ['purchase', 'sale'] as const) {
      const row = buildRow(resource, 3)
      expect(row.billCode, `${resource} 演示行缺 billCode`).toBe(row.code)
      expect(typeof row.totalPrice, `${resource} 缺总金额`).toBe('number')
      expect(String(row.reason ?? '').length, `${resource} 缺事由/说明`).toBeGreaterThan(0)

      const details = row.details as Array<Record<string, unknown>>
      expect(details.length, `${resource} 演示行缺明细`).toBeGreaterThan(0)
      for (const detail of details) {
        const material = detail.material as Record<string, unknown>
        expect(material?.code, `${resource} 明细缺物料嵌套对象`).toBeTruthy()
        expect(typeof detail.price, `${resource} 明细缺单价（金额列会显示 -）`).toBe('number')
        expect(typeof detail.quantity).toBe('number')
      }
    }

    // 采购明细还要供应商（列读 `row.supplier.code|name`）
    const purchaseDetail = (buildRow('purchase', 3).details as Array<Record<string, unknown>>)[0]!
    expect((purchaseDetail.supplier as Record<string, unknown>)?.code).toBeTruthy()
  })

  it('价格页的演示行有 `price`（否则脱敏/金额列看不到东西）', async () => {
    const { buildRow } = await import('@/demo/mock/specs')
    for (const resource of ['purchasePrice', 'salePrice'] as const) {
      const row = buildRow(resource, 4)
      expect(typeof row.price, `${resource} 演示行缺 price`).toBe('number')
      expect((row.material as Record<string, unknown>)?.name, `${resource} 缺物料关联对象`).toBeTruthy()
    }
  })

  it('采购单列表：「查看」能打开详情弹窗，明细表里有真实行（走通 demo 适配器的 detail 链路）', async () => {
    // ⚠️ 这条链路整体走 demo 适配器（列表 + 详情都从 HTTP 层过），所以两个 axios 实例都要换适配器：
    // `@/api/axios` 是自研 client 自己的实例，它的 `defaults.adapter` 不跟着全局 axios 变。
    const { createDemoAdapter } = await import('@/demo/mock/adapter')
    const { request } = await import('@/api/axios')
    axios.defaults.adapter = createDemoAdapter([0, 0])
    request.defaults.adapter = axios.defaults.adapter

    const { buildRow } = await import('@/demo/mock/specs')
    // 点的是列表里**第一行**的「查看」（演示列表按 id 升序），断言就用 buildRow(..., 1)
    const expected = buildRow('purchase', 1)
    const firstDetail = (expected.details as Array<Record<string, unknown>>)[0]!
    const material = firstDetail.material as Record<string, unknown>

    const PurchaseList = (await import('@/view/console/channel/purchase/list.vue')).default
    const wrapper = await mountPage(PurchaseList, undefined, true)

    // 命令式弹窗挂在**另一棵组件树**里，得先把应用上下文接过去（`main.ts` 里同样一行）。
    // 漏接时路径上的每一环都不报错：点「查看」没反应、弹窗永远不出现。
    const { setDialogAppContext } = await import('@/framework')
    const instance = (wrapper as unknown as { vm: { $: { appContext: import('vue').AppContext } } }).vm.$
    setDialogAppContext(instance.appContext)

    await vi.waitFor(() => {
      expect(document.body.textContent).toContain(String(expected.billCode))
    }, { timeout: 8000 })

    const viewButton = Array.from(document.body.querySelectorAll('.el-table__body button'))
      .find(button => button.textContent?.trim() === '查看') as HTMLButtonElement | undefined
    expect(viewButton, '列表里没有渲染「查看」按钮').toBeTruthy()
    viewButton!.dispatchEvent(new MouseEvent('click', { bubbles: true }))

    await vi.waitFor(() => {
      expect(document.body.textContent, '「查看」没有打开采购详情弹窗').toContain('采购详情')
    }, { timeout: 8000 })
    await vi.waitFor(() => {
      expect(document.body.textContent, '详情里没有出现明细的物料名').toContain(String(material.name))
    }, { timeout: 8000 })

    const dialog = document.body.querySelector('.el-dialog')!
    expect(dialog.querySelectorAll('.el-table__body tr').length, '详情弹窗的明细表是空的').toBeGreaterThan(0)
    expect(capturedErrors).toEqual([])

    setDialogAppContext(null)
    wrapper.unmount()
    document.body.innerHTML = ''
  })
})

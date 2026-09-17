// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'

/**
 * # 批 2 给框架层加的三样东西（都因为 asset 域真的需要，不是为了"顺手补全"）
 *
 * 1. **异步导出**（`ExportUtil`）：建任务 → 轮询 → 下载地址。后端是异步导出任务，
 *    前端把流程抽成纯函数才好测；轮询必须**有上限**（否则就是死循环）。
 * 2. **KTable 的 `#cell-*` 槽位**：列的显示不只看值时（金额要先问脱敏策略、链接要拼前缀）用它覆盖。
 * 3. **KFormDialog 的日期控件 + `beforeSubmit`**：合同有起止日期（`Long` 毫秒时间戳），
 *    物料的提交前守卫（价格被遮蔽时不许保存）。
 */

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('framework · ExportUtil（异步导出任务）', () => {
  it('建任务时去掉 page（导出的是整个筛选结果，不是当前页）', async () => {
    const { ExportUtil } = await import('@/framework')
    const createExportTask = vi.fn().mockResolvedValue('file-code-1')
    const queryExport = vi.fn().mockResolvedValue('export/2026-09-17.xlsx')
    const service = { createExportTask, queryExport }

    const url = await ExportUtil.run(service, {
      filter: { name: '板' },
      page: { pageNum: 3, pageSize: 20 },
      sort: { field: 'id', direction: 'DESC' },
    }, { intervalMs: 0 })

    expect(createExportTask).toHaveBeenCalledTimes(1)
    const sent = createExportTask.mock.calls[0]![0] as Record<string, unknown>
    expect(sent.page, '导出请求不该带分页').toBeUndefined()
    expect(sent.filter).toEqual({ name: '板' })
    expect(url).toBe('/static/export/2026-09-17.xlsx')
  })

  it('任务没就绪时按间隔重试，就绪后返回地址', async () => {
    const { ExportUtil } = await import('@/framework')
    const queryExport = vi.fn()
      .mockRejectedValueOnce(new Error('任务进行中'))
      .mockRejectedValueOnce(new Error('任务进行中'))
      .mockResolvedValue('export/ok.csv')
    const onPolling = vi.fn()

    const url = await ExportUtil.run(
      { createExportTask: vi.fn().mockResolvedValue('code'), queryExport },
      {},
      { intervalMs: 0, onPolling },
    )

    expect(queryExport).toHaveBeenCalledTimes(3)
    expect(onPolling).toHaveBeenCalledTimes(3)
    expect(url).toBe('/static/export/ok.csv')
  })

  it('一直没好就抛"导出超时"——不许无限轮询', async () => {
    const { ExportUtil } = await import('@/framework')
    const queryExport = vi.fn().mockRejectedValue(new Error('任务进行中'))
    await expect(
      ExportUtil.run({ createExportTask: vi.fn().mockResolvedValue('code'), queryExport }, {}, { intervalMs: 0, maxAttempts: 3 }),
    ).rejects.toThrow('导出超时')
    expect(queryExport).toHaveBeenCalledTimes(3)
  })

  it('后端没给任务编码时立即失败（否则会拿空 code 去轮询）', async () => {
    const { ExportUtil } = await import('@/framework')
    await expect(
      ExportUtil.run({ createExportTask: vi.fn().mockResolvedValue(''), queryExport: vi.fn() }, {}),
    ).rejects.toThrow('未返回任务编码')
  })
})

describe('framework · KFormDialog 日期控件与提交守卫', () => {
  it('日期字段渲染成日期选择器，提交的是毫秒时间戳（后端字段是 Long）', async () => {
    const { ElDatePicker } = await import('element-plus')
    const { KEntity } = await import('@/base/KEntity')
    const { AbstractCurdService, DateTimeFormatter, DateTimeType, Field, Form, Model } = await import('@/framework')
    const KFormDialog = (await import('@/component/framework/KFormDialog.vue')).default

    @Model({ label: '合同' })
    class ContractEntity extends KEntity {
      @Form({ dateType: DateTimeType.DATE, dateFormatter: DateTimeFormatter.TIMESTAMP, dateShowFormatter: DateTimeFormatter.FULL_DATE })
      @Field({ label: '开始时间' })
      startTime!: number
    }
    class ContractService extends AbstractCurdService<ContractEntity> {
      entityClass = ContractEntity
      baseUrl = 'contract-date'
    }
    const add = vi.spyOn(ContractService.prototype, 'add').mockResolvedValue(1 as never)

    const wrapper = mount(KFormDialog as never, {
      props: { modelValue: true, service: ContractService },
    } as never)
    await nextTick()
    await flushPromises()

    // 渲染成 ElDatePicker，且**取值格式是 `x`（毫秒时间戳）** —— 这条决定了提交给后端的是
    // `Long` 而不是 ISO 字符串（后端字段类型不匹配时表现为"日期永远存不进去"）
    expect(document.body.querySelector('.el-date-editor'), '日期选择器没渲染出来').toBeTruthy()
    // `findComponent` 收泛型/第三方组件时返回值类型会退化成 WrapperLike，这里显式收敛形状
    const picker = wrapper.findComponent(ElDatePicker as never) as unknown as {
      exists: () => boolean
      props: (name: string) => unknown
      vm: { $emit: (event: string, value: unknown) => void }
    }
    expect(picker.exists()).toBe(true)
    expect(picker.props('type')).toBe('date')
    expect(picker.props('valueFormat'), '取值格式必须是毫秒时间戳').toBe('x')
    expect(picker.props('format'), '展示格式').toBe('YYYY-MM-DD')

    // 模拟用户选完日期 → 表单里是数字（不是字符串），保存时原样提交
    await picker.vm.$emit('update:modelValue', 1758038400000)
    await nextTick()
    const confirm = Array.from(document.body.querySelectorAll('button'))
      .find(button => button.textContent?.trim() === '保存')!
    confirm.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()
    expect(add).toHaveBeenCalledTimes(1)
    const payload = add.mock.calls[0]![0] as unknown as { startTime: unknown }
    expect(typeof payload.startTime, '提交的必须是数字时间戳').toBe('number')
    expect(payload.startTime).toBe(1758038400000)
    document.body.innerHTML = ''
  })

  it('beforeSubmit 返回 null 时中止提交（页面自己给提示）', async () => {
    const { KEntity } = await import('@/base/KEntity')
    const { AbstractCurdService, Field, Form, Model } = await import('@/framework')
    const KFormDialog = (await import('@/component/framework/KFormDialog.vue')).default

    @Model({ label: '物料' })
    class MaterialEntity extends KEntity {
      @Form({ requiredString: true })
      @Field({ label: '名称' })
      name!: string
    }
    class MaterialService extends AbstractCurdService<MaterialEntity> {
      entityClass = MaterialEntity
      baseUrl = 'material-guard'
    }
    const add = vi.spyOn(MaterialService.prototype, 'add').mockResolvedValue(1 as never)
    const beforeSubmit = vi.fn().mockReturnValue(null)

    mount(KFormDialog as never, {
      props: { modelValue: true, service: MaterialService, beforeSubmit },
    } as never)
    await nextTick()
    await flushPromises()

    const confirm = Array.from(document.body.querySelectorAll('button'))
      // ⚠️ KFormDialog 的确认按钮文案是「保存」（不是 KDialog 的「确定」）
      .find(button => button.textContent?.trim() === '保存')!
    confirm.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    await flushPromises()

    expect(beforeSubmit).toHaveBeenCalledTimes(1)
    expect(add, '守卫拦下时不该发出写请求').not.toHaveBeenCalled()
    document.body.innerHTML = ''
  })
})

describe('framework · KTable 扩展点', () => {
  it('#cell-<字段> 槽位覆盖某个单元格的渲染（列名来自 @KTable）', async () => {
    const { VueQueryPlugin, QueryClient } = await import('@tanstack/vue-query')
    const { KEntity } = await import('@/base/KEntity')
    const { AbstractCurdService, Field, Model, Table } = await import('@/framework')
    const KTable = (await import('@/component/framework/KTable.vue')).default
    const { useTable } = await import('@/hook/kubo/useTable')

    @Model({ label: '物料' })
    class MaterialEntity extends KEntity {
      @Table({ order: 100, minWidth: 160 })
      @Field({ label: '名称' })
      name!: string

      @Table({ order: 90, money: true })
      @Field({ label: '采购单价' })
      purchasePrice!: number
    }
    class MaterialService extends AbstractCurdService<MaterialEntity> {
      entityClass = MaterialEntity
      baseUrl = 'material-cell'
    }
    vi.spyOn(MaterialService.prototype, 'getPage').mockResolvedValue({
      list: [{ id: 1, name: '铝板', purchasePrice: 12.345 }],
      total: 1,
      pageCount: 1,
      page: { pageNum: 1, pageSize: 20 },
    } as never)

    const { defineComponent, h } = await import('vue')
    const Host = defineComponent({
      setup() {
        const table = useTable<MaterialEntity, MaterialService>(MaterialService, { queryKey: ['cell-slot'] })
        return () => h(KTable as never, { table } as never, {
          'cell-purchasePrice': (slotProps: { value: number }) => h('span', { class: 'masked' }, `已遮蔽(${slotProps.value})`),
        } as never)
      },
    })

    const wrapper = mount(Host as never, {
      global: { plugins: [[VueQueryPlugin, { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) }]] },
    } as never)

    await vi.waitFor(() => {
      expect(wrapper.find('.masked').exists(), '槽位没生效').toBe(true)
    }, { timeout: 8000 })
    expect(wrapper.find('.masked').text()).toBe('已遮蔽(12.345)')
    // 没有覆盖槽位的列仍按元数据渲染（金额列走 formatMoney）
    expect(wrapper.text()).toContain('铝板')
  })
})

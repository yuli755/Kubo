// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import BillStatusFilter from '@/component/bill/BillStatusFilter.vue'
import { PurchaseStatusEnum } from '@/model/channel/purchase/PurchaseStatusEnum'
import { SaleStatusEnum } from '@/model/channel/sale/SaleStatusEnum'
import { InputStatusEnum } from '@/model/wms/input/InputStatusEnum'
import { MoveStatusEnum } from '@/model/wms/move/MoveStatusEnum'
import { OutputStatusEnum } from '@/model/wms/output/OutputStatusEnum'

describe('billStatusFilter 单据状态快捷分段筛选组件', () => {
  it('正确渲染标题与基于 enumClass 生成的选项（含「全部」与「待审核」映射）', () => {
    const wrapper = mount(BillStatusFilter, {
      props: {
        enumClass: PurchaseStatusEnum,
      },
    })

    expect(wrapper.find('.bill-status-filter__title').text()).toBe('单据状态')
    const buttons = wrapper.findAll('.el-radio-button')
    // PurchaseStatusEnum 有 5 项 + 全部 = 6 项
    expect(buttons.length).toBe(6)
    expect(buttons[0].text()).toContain('全部')
    expect(buttons[1].text()).toContain('待审核')
    expect(buttons[2].text()).toContain('已驳回')
    expect(buttons[3].text()).toContain('采购中')
    expect(buttons[4].text()).toContain('入库中')
    expect(buttons[5].text()).toContain('已完成')
  })

  it('支持五大业务单据的枚举字典绑定', () => {
    const enums = [
      { cls: PurchaseStatusEnum, expectedLength: 6 },
      { cls: SaleStatusEnum, expectedLength: 5 },
      { cls: InputStatusEnum, expectedLength: 5 },
      { cls: OutputStatusEnum, expectedLength: 5 },
      { cls: MoveStatusEnum, expectedLength: 5 },
    ]

    for (const { cls, expectedLength } of enums) {
      const wrapper = mount(BillStatusFilter, {
        props: {
          enumClass: cls,
        },
      })
      const buttons = wrapper.findAll('.el-radio-button')
      expect(buttons.length).toBe(expectedLength)
    }
  })

  it('优先使用自定义 options 并在点击时派发事件', async () => {
    const customOptions = [
      { label: '全部单据', value: null },
      { label: '处理中', value: 10, color: '#409eff' },
      { label: '已完结', value: 20, color: '#67c23a' },
    ]

    const wrapper = mount(BillStatusFilter, {
      props: {
        options: customOptions,
        title: '流转状态',
      },
    })

    expect(wrapper.find('.bill-status-filter__title').text()).toBe('流转状态')
    const buttons = wrapper.findAll('.el-radio-button')
    expect(buttons.length).toBe(3)

    // 检查颜色指示点
    const dots = wrapper.findAll('.status-indicator-dot')
    expect(dots.length).toBe(2)

    // 点击第二个选项（value: 10）
    const input = buttons[1].find('input')
    await input.setValue(true)
    await buttons[1].trigger('click')
    await nextTick()

    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([10])
    expect(wrapper.emitted('change')).toBeTruthy()
    expect(wrapper.emitted('change')?.[0]).toEqual([10])
  })

  it('与 mock table 联动：切换选项调用 table.search，reset 时自动同步回全部', async () => {
    const searchFn = vi.fn()
    const mockFilter = ref<Record<string, unknown>>({ keyword: 'test' })

    const mockTable = {
      filter: mockFilter,
      search: searchFn,
    } as any

    const wrapper = mount(BillStatusFilter, {
      props: {
        enumClass: SaleStatusEnum,
        table: mockTable,
      },
    })

    const buttons = wrapper.findAll('.el-radio-button')
    // 点击「待审核」（SaleStatusEnum.AUDITING.key === 1）
    await buttons[1].find('input').setValue(true)
    await buttons[1].trigger('click')
    await nextTick()

    expect(searchFn).toHaveBeenCalledWith({
      keyword: 'test',
      status: 1,
    })

    // 点击「全部」（value: null）
    await buttons[0].find('input').setValue(true)
    await buttons[0].trigger('click')
    await nextTick()

    expect(searchFn).toHaveBeenCalledWith({
      keyword: 'test',
    })

    // 模拟外部 table.reset() 清空 filter
    mockFilter.value = {}
    await nextTick()
    expect(wrapper.emitted('update:modelValue')?.slice(-1)[0]).toEqual([null])
  })
})

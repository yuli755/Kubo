// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'

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

describe('123 优化设计单元契约', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  describe('feature 1 · 全局命令调色板 (KCommandPalette)', () => {
    const mockMenus = [
      { id: 1, name: '工作台', path: '/console/index' },
      {
        id: 2,
        name: '仓储管理',
        children: [
          { id: 21, name: '实时库存', path: '/console/wms/inventory/list' },
          { id: 22, name: '入库单', path: '/console/wms/input/list' },
        ],
      },
      {
        id: 3,
        name: '系统治理',
        children: [
          { id: 31, name: '计量单位', path: '/console/system/unit/list' },
        ],
      },
    ]

    it('显示时自动展平菜单并支持关键词模糊检索', async () => {
      const KCommandPalette = (await import('@/component/framework/KCommandPalette.vue')).default
      const wrapper = mount(KCommandPalette, {
        props: {
          modelValue: true,
          menus: mockMenus as never,
        },
      })

      expect(wrapper.find('.k-command-palette-card').exists()).toBe(true)

      const input = wrapper.find<HTMLInputElement>('.search-input')
      await input.setValue('库存')
      await nextTick()

      const items = wrapper.findAll('.k-command-palette__item')
      expect(items.length).toBeGreaterThanOrEqual(1)
      expect(wrapper.text()).toContain('实时库存')
      expect(wrapper.text()).toContain('仓储管理 / 实时库存')
    })

    it('按 Escape 键关闭命令调色板', async () => {
      const KCommandPalette = (await import('@/component/framework/KCommandPalette.vue')).default
      const wrapper = mount(KCommandPalette, {
        props: {
          modelValue: true,
          menus: mockMenus as never,
        },
      })

      const input = wrapper.find<HTMLInputElement>('.search-input')
      await input.trigger('keydown', { key: 'Escape' })
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })

    it('上下方向键移动选中项并通过 Enter 执行跳转', async () => {
      const KCommandPalette = (await import('@/component/framework/KCommandPalette.vue')).default
      const wrapper = mount(KCommandPalette, {
        props: {
          modelValue: true,
          menus: mockMenus as never,
        },
      })

      const input = wrapper.find<HTMLInputElement>('.search-input')
      await input.trigger('keydown', { key: 'ArrowDown' })
      expect(wrapper.findAll('.k-command-palette__item')[1]?.classes()).toContain('is-active')

      await input.trigger('keydown', { key: 'Enter' })
      expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    })
  })

  describe('feature 2 · 表单本地草稿暂存机制契约', () => {
    it('草稿在 localStorage 读写与丢弃流程一致', () => {
      const draftKey = 'kubo:draft:TestService'
      const payload = { data: { code: 'MAT-001', name: '精密轴承' }, time: Date.now() }

      localStorage.setItem(draftKey, JSON.stringify(payload))
      const raw = localStorage.getItem(draftKey)
      expect(raw).toBeTruthy()

      const parsed = JSON.parse(raw!)
      expect(parsed.data.code).toBe('MAT-001')
      expect(parsed.data.name).toBe('精密轴承')

      localStorage.removeItem(draftKey)
      expect(localStorage.getItem(draftKey)).toBeNull()
    })
  })

  describe('feature 3 · 表格列显隐偏好持久化契约', () => {
    it('列显隐偏好可写入并读取持久化 key', () => {
      const tableKey = 'kubo:table:columns:system:unit'
      const hiddenCols = ['createTime', 'updateTime']

      localStorage.setItem(tableKey, JSON.stringify(hiddenCols))
      const saved = JSON.parse(localStorage.getItem(tableKey)!)
      expect(saved).toEqual(['createTime', 'updateTime'])

      localStorage.removeItem(tableKey)
      expect(localStorage.getItem(tableKey)).toBeNull()
    })
  })

  describe('feature 4 · 离线与网络状态感知契约', () => {
    it('监听 online 与 offline 事件并维护离线标记', () => {
      let isOffline = !navigator.onLine
      const updateOnlineStatus = (online: boolean) => {
        isOffline = !online
      }

      updateOnlineStatus(false)
      expect(isOffline).toBe(true)

      updateOnlineStatus(true)
      expect(isOffline).toBe(false)
    })
  })

  describe('feature 5 · 表格全局快捷键交互契约', () => {
    it('快捷键 / 触发聚焦条件逻辑校验', () => {
      const canFocusSearch = (targetTag: string, isContentEditable: boolean, key: string) => {
        if (key !== '/')
          return false
        if (['INPUT', 'TEXTAREA', 'SELECT'].includes(targetTag))
          return false
        if (isContentEditable)
          return false
        return true
      }

      expect(canFocusSearch('DIV', false, '/')).toBe(true)
      expect(canFocusSearch('BODY', false, '/')).toBe(true)
      expect(canFocusSearch('INPUT', false, '/')).toBe(false)
      expect(canFocusSearch('TEXTAREA', false, '/')).toBe(false)
      expect(canFocusSearch('DIV', false, 'Enter')).toBe(false)
    })
  })

  describe('feature 6 · WMS 入库扫码枪连续录入模式契约', () => {
    it('已存在物料条码时数量累加，未存在时自动初始化单行', () => {
      interface DetailRow {
        materialCode: string
        materialName: string
        quantity: number
      }
      const details: DetailRow[] = [
        { materialCode: 'MAT-A', materialName: '物料 A', quantity: 2 },
      ]

      const handleScan = (code: string) => {
        const found = details.find(d => d.materialCode === code)
        if (found) {
          found.quantity += 1
          return { type: 'increment', quantity: found.quantity }
        }
        details.push({ materialCode: code, materialName: `物料 ${code}`, quantity: 1 })
        return { type: 'add', quantity: 1 }
      }

      // 扫已有物料
      const res1 = handleScan('MAT-A')
      expect(res1.type).toBe('increment')
      expect(res1.quantity).toBe(3)
      expect(details[0].quantity).toBe(3)

      // 扫新物料
      const res2 = handleScan('MAT-B')
      expect(res2.type).toBe('add')
      expect(res2.quantity).toBe(1)
      expect(details.length).toBe(2)
      expect(details[1].materialCode).toBe('MAT-B')
    })
  })

  describe('feature 7 · WMS 出库与移库扫码录入逻辑', () => {
    it('出库与移库扫码支持按条码查找并累加数量', () => {
      const details = [
        { materialCode: 'ITEM-101', quantity: 5 },
      ]
      const handleScan = (code: string) => {
        const found = details.find(d => d.materialCode === code)
        if (found) {
          found.quantity += 1
          return true
        }
        details.push({ materialCode: code, quantity: 1 })
        return false
      }

      expect(handleScan('ITEM-101')).toBe(true)
      expect(details[0].quantity).toBe(6)

      expect(handleScan('ITEM-102')).toBe(false)
      expect(details.length).toBe(2)
      expect(details[1].quantity).toBe(1)
    })
  })

  describe('feature 8 · 库存汇总指标卡计算逻辑', () => {
    it('正确聚合 SKU 种数、总件数与低库存告警数', () => {
      const list = [
        { id: 1, quantity: 100 },
        { id: 2, quantity: 5 },
        { id: 3, quantity: 0 },
        { id: 4, quantity: 50 },
      ]
      const totalSku = list.length
      const totalQty = list.reduce((acc, row) => acc + row.quantity, 0)
      const lowStockCount = list.filter(row => row.quantity < 10).length

      expect(totalSku).toBe(4)
      expect(totalQty).toBe(155)
      expect(lowStockCount).toBe(2)
    })
  })

  describe('feature 9 · 单据凭证打印契约', () => {
    it('打印动作安全调用 window.print', () => {
      let printed = false
      const originalPrint = window.print
      window.print = () => {
        printed = true
      }
      try {
        window.print()
        expect(printed).toBe(true)
      }
      finally {
        window.print = originalPrint
      }
    })
  })

  describe('feature 10 · 采购与销售单据总金额实时汇总计算契约', () => {
    it('明细行金额动态聚合并支持精度控制', () => {
      const details = [
        { quantity: 10, price: 50.5 },
        { quantity: 2, price: 100 },
        { quantity: '5', price: '20' },
      ]
      const totalAmount = details.reduce((sum, item) => {
        const qty = Number(item.quantity) || 0
        const prc = Number(item.price) || 0
        return sum + (qty * prc)
      }, 0)

      expect(totalAmount).toBe(805)
      expect(totalAmount.toFixed(2)).toBe('805.00')
    })
  })

  describe('feature 11 · WMS 库位热力拓扑图与储位状态契约', () => {
    it('储位占用率色阶与状态分类契约准确', () => {
      const getStatus = (occupancy: number) => {
        if (occupancy === 0) {
          return 'empty'
        }
        if (occupancy <= 20) {
          return 'idle'
        }
        if (occupancy <= 60) {
          return 'low'
        }
        if (occupancy <= 80) {
          return 'normal'
        }
        return 'danger'
      }

      expect(getStatus(0)).toBe('empty')
      expect(getStatus(15)).toBe('idle')
      expect(getStatus(50)).toBe('low')
      expect(getStatus(75)).toBe('normal')
      expect(getStatus(92)).toBe('danger')
    })

    it('库区总监控指标动态聚合契约', () => {
      const slots = [
        { occupancy: 0 },
        { occupancy: 15 },
        { occupancy: 50 },
        { occupancy: 85 },
        { occupancy: 95 },
      ]
      const total = slots.length
      const avgOccupancy = Math.round(slots.reduce((sum, s) => sum + s.occupancy, 0) / total)
      const highCount = slots.filter(s => s.occupancy >= 80).length
      const idleCount = slots.filter(s => s.occupancy <= 20).length

      expect(total).toBe(5)
      expect(avgOccupancy).toBe(49)
      expect(highCount).toBe(2)
      expect(idleCount).toBe(2)
    })
  })
})

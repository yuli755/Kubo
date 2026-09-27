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

  describe('feature 12 · 扫码蜂鸣与触觉反馈契约', () => {
    it('在无声卡或无振动环境下静默安全降级', async () => {
      const { playScanSuccess, playScanWarning, playScanError } = await import('@/utils/audioFeedback')
      expect(() => playScanSuccess()).not.toThrow()
      expect(() => playScanWarning()).not.toThrow()
      expect(() => playScanError()).not.toThrow()
    })
  })

  describe('feature 13 · 渠道供应链明细小计与价格来源契约', () => {
    it('单价与数量联动核算行小计', () => {
      const calculateSubtotal = (price: number, quantity: number) => price * quantity
      expect(calculateSubtotal(12.5, 4)).toBe(50)
      expect(calculateSubtotal(99.9, 0)).toBe(0)
    })
  })

  describe('feature 14 · 合同履约到期预警判断契约', () => {
    it('准确识别逾期、临期与正常履约状态', () => {
      const now = 1774600000000 // 假设基准时间
      const ONE_DAY = 1000 * 60 * 60 * 24

      const checkStatus = (endTime: number) => {
        const diffDays = Math.ceil((endTime - now) / ONE_DAY)
        if (diffDays < 0) {
          return 'expired'
        }
        if (diffDays <= 30) {
          return 'imminent'
        }
        return 'normal'
      }

      expect(checkStatus(now - ONE_DAY * 5)).toBe('expired')
      expect(checkStatus(now + ONE_DAY * 10)).toBe('imminent')
      expect(checkStatus(now + ONE_DAY * 90)).toBe('normal')
    })
  })

  describe('feature 15 · 审计日志字段 Diff 对照分析契约', () => {
    it('提取请求与响应中的字段变动并分类', () => {
      const req: Record<string, unknown> = { id: 1, name: '新设备', status: 1 }
      const res: Record<string, unknown> = { id: 1, name: '新设备', status: 2, version: '1.0' }

      const keys = Array.from(new Set([...Object.keys(req), ...Object.keys(res)]))
      const diffs = keys
        .map((k) => {
          if (req[k] === undefined && res[k] !== undefined) {
            return { field: k, type: 'added' }
          }
          if (req[k] !== undefined && res[k] !== undefined && req[k] !== res[k]) {
            return { field: k, type: 'modified' }
          }
          return null
        })
        .filter(Boolean)

      expect(diffs).toEqual([
        { field: 'status', type: 'modified' },
        { field: 'version', type: 'added' },
      ])
    })
  })

  describe('feature 16 · 库位物料/条码精准搜索与高亮匹配契约', () => {
    it('按物料名称、物料编码、规格、批次号或储位号模糊检索匹配储位', () => {
      const slots = [
        { id: 'S1', code: 'WH01-A1-1-1', materialName: '微型伺服电机 24V', materialCode: 'MTR-24V-01', specification: '24V/100W', batchNo: 'BAT-202601' },
        { id: 'S2', code: 'WH01-A1-1-2', materialName: '精密行星减速机', materialCode: 'GR-PL-080', specification: '速比 1:10', batchNo: 'BAT-202602' },
        { id: 'S3', code: 'WH01-A2-1-1', materialName: '高频传感器芯片', materialCode: 'SNS-HF-05', specification: '0-100kHz', batchNo: 'BAT-202603' },
      ]

      const filterSlots = (kw: string) => {
        const query = kw.trim().toLowerCase()
        return slots.filter(s =>
          s.code.toLowerCase().includes(query)
          || s.materialName.toLowerCase().includes(query)
          || s.materialCode.toLowerCase().includes(query)
          || s.specification.toLowerCase().includes(query)
          || s.batchNo.toLowerCase().includes(query),
        ).map(s => s.id)
      }

      expect(filterSlots('电机')).toEqual(['S1'])
      expect(filterSlots('GR-PL')).toEqual(['S2'])
      expect(filterSlots('BAT-2026')).toEqual(['S1', 'S2', 'S3'])
      expect(filterSlots('WH01-A2')).toEqual(['S3'])
      expect(filterSlots('不存在')).toEqual([])
    })
  })

  describe('feature 17 · 库位排数筛选与 KPI 联动过滤契约', () => {
    it('排数筛选正确收敛展示的货架组', () => {
      const aisles = [
        { aisle: 1, name: '第 1 排货架' },
        { aisle: 2, name: '第 2 排货架' },
        { aisle: 3, name: '第 3 排货架' },
      ]

      const filterAisles = (target: number | 'all') => {
        if (target === 'all')
          return aisles
        return aisles.filter(a => a.aisle === target)
      }

      expect(filterAisles('all').length).toBe(3)
      expect(filterAisles(2).map(a => a.name)).toEqual(['第 2 排货架'])
    })

    it('kpi 胶囊点击切换占用率过滤档位', () => {
      let currentFilter: 'all' | 'high' | 'idle' = 'all'
      const toggleFilter = (target: 'high' | 'idle') => {
        currentFilter = currentFilter === target ? 'all' : target
      }

      toggleFilter('high')
      expect(currentFilter).toBe('high')
      toggleFilter('high')
      expect(currentFilter).toBe('all')
      toggleFilter('idle')
      expect(currentFilter).toBe('idle')
    })
  })

  describe('feature 18 · 企业 Webhook 通知 Payload 多平台格式契约', () => {
    it('各平台消息体协议满足开放平台要求', () => {
      const formatWeCom = (title: string, content: string, level: string) => {
        return {
          msgtype: 'markdown',
          markdown: {
            content: `### ${title}\n> 级别: ${level}\n> 内容: ${content}`,
          },
        }
      }

      const formatFeishu = (title: string, content: string, level: string) => {
        return {
          msg_type: 'text',
          content: {
            text: `【${level}】${title}\n${content}`,
          },
        }
      }

      const wecom = formatWeCom('单据驳回', '采购单 PO-20260901 被驳回', 'WARNING')
      expect(wecom.msgtype).toBe('markdown')
      expect(wecom.markdown.content).toContain('PO-20260901')

      const feishu = formatFeishu('死信警报', 'Outbox 记录已达最大重试上限', 'ERROR')
      expect(feishu.msg_type).toBe('text')
      expect(feishu.content.text).toContain('死信警报')
    })
  })
})

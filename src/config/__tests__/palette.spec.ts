// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  accentOf,
  chartTextColor,
  MODULE_ACCENTS,
  NEUTRAL_ACCENT,
  OCCUPANCY_LEVELS,
  occupancyColor,
  occupancyLevel,
  occupancyRamp,
  resolveCssToken,
  statusColor,
} from '@/config/palette'

/**
 * # 调色板纯逻辑（`config/palette.ts`）
 *
 * `styleContract.spec.ts` 守的是「色值只有一处定义、业务不许写死十六进制」这条**静态**约束；
 * 这里守的是那张表被**怎么用**：查表的两级回退、CSS 变量解析的兜底、占用率分档的边界。
 * 这些分支错了不会编译失败，只会让「同一个模块在两个页面颜色不同」「暗色模式下图表没颜色」
 * 「占用率 80% 显示成闲置」—— 全是肉眼很难归因的视觉 bug，所以逐个分支钉住。
 *
 * jsdom 下 `getComputedStyle` 对未定义的 CSS 变量返回空串，正好用来验证 fallback 分支；
 * 需要验证「变量存在时优先取变量」时用 spy 造一个假返回值。
 */

/** 造一个只认识指定变量的 getComputedStyle */
function stubCssVars(vars: Record<string, string>) {
  return vi.spyOn(window, 'getComputedStyle').mockImplementation(() => ({
    getPropertyValue: (name: string) => (name in vars ? vars[name] : ''),
  }) as unknown as CSSStyleDeclaration)
}

describe('palette · accentOf 两级回退', () => {
  it('没有 path 时用中性兜底色（而不是 undefined 让调用方炸）', () => {
    expect(accentOf()).toEqual(NEUTRAL_ACCENT)
    expect(accentOf(null)).toEqual(NEUTRAL_ACCENT)
    expect(accentOf('')).toEqual(NEUTRAL_ACCENT)
  })

  it('已注册的 path 精确命中自己的强调色', () => {
    expect(accentOf('/console/channel/purchase/list')).toEqual(MODULE_ACCENTS['/console/channel/purchase/list'])
    expect(accentOf('/console/personnel/user/list').color).toBe('#ec4899')
  })

  it('带查询串的入口能拿到自己的颜色（热力图与库存列表不同色）', () => {
    const heatmap = accentOf('/console/wms/inventory/list?view=heatmap')
    const inventory = accentOf('/console/wms/inventory/list')
    expect(heatmap.color).toBe('#14b8a6')
    expect(inventory.color).toBe('#10b981')
    expect(heatmap.color, '热力图若与库存列表同色，就失去了独立识别的意义').not.toBe(inventory.color)
  })

  it('未注册的查询串退化为「去掉参数」再匹配，而不是直接掉到中性灰', () => {
    expect(accentOf('/console/wms/inventory/list?from=dashboard'))
      .toEqual(MODULE_ACCENTS['/console/wms/inventory/list'])
  })

  it('两级都未命中才用中性色', () => {
    expect(accentOf('/console/not/registered/list')).toEqual(NEUTRAL_ACCENT)
    expect(accentOf('/console/not/registered/list?a=1')).toEqual(NEUTRAL_ACCENT)
  })
})

describe('palette · resolveCssToken 变量优先、空值兜底', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('变量存在时取变量值并 trim 掉两侧空白（CSS 自定义属性优先于兜底）', () => {
    const spy = stubCssVars({ '--kubo-probe': '  #123456  ' })
    expect(resolveCssToken('--kubo-probe', '#fallback')).toBe('#123456')
    expect(spy).toHaveBeenCalled()
  })

  it('变量取不到（jsdom / SSR / 样式表未加载）时用兜底值', () => {
    stubCssVars({})
    expect(resolveCssToken('--kubo-missing', '#fallback')).toBe('#fallback')
  })
})

describe('palette · statusColor / chartTextColor', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('四种状态色在变量可用时跟随主题（暗色模式靠这条生效）', () => {
    stubCssVars({
      '--el-color-success': 'rgb(1, 1, 1)',
      '--el-color-warning': 'rgb(2, 2, 2)',
      '--el-color-danger': 'rgb(3, 3, 3)',
      '--el-color-info': 'rgb(4, 4, 4)',
    })
    expect(statusColor('success')).toBe('rgb(1, 1, 1)')
    expect(statusColor('warning')).toBe('rgb(2, 2, 2)')
    expect(statusColor('danger')).toBe('rgb(3, 3, 3)')
    expect(statusColor('info')).toBe('rgb(4, 4, 4)')
  })

  it('变量不可用时回落到 EP 默认状态色（ECharts 吃不了空串）', () => {
    stubCssVars({})
    expect(statusColor('success')).toBe('#67c23a')
    expect(statusColor('warning')).toBe('#e6a23c')
    expect(statusColor('danger')).toBe('#f56c6c')
    expect(statusColor('info')).toBe('#909399')
  })

  it('图表文字色走 --el-text-color-secondary，兜底 slate-500', () => {
    stubCssVars({ '--el-text-color-secondary': 'rgb(9, 9, 9)' })
    expect(chartTextColor()).toBe('rgb(9, 9, 9)')
    vi.restoreAllMocks()
    stubCssVars({})
    expect(chartTextColor()).toBe('#64748b')
  })
})

describe('palette · 占用率分档边界', () => {
  it('档位表的阈值上界单调递增，最后一档是 Infinity 兜底', () => {
    const maxes = OCCUPANCY_LEVELS.map(entry => entry.max)
    expect(maxes).toEqual([...maxes].sort((a, b) => a - b))
    expect(maxes.at(-1)).toBe(Number.POSITIVE_INFINITY)
    expect(OCCUPANCY_LEVELS.map(entry => entry.level))
      .toEqual(['empty', 'idle', 'low', 'normal', 'danger'])
  })

  it('边界值归属：0/负数 → empty，20 → idle，21 与 60 → low，61 与 80 → normal，81 → danger', () => {
    expect(occupancyLevel(0)).toBe('empty')
    expect(occupancyLevel(-5)).toBe('empty')
    expect(occupancyLevel(20)).toBe('idle')
    expect(occupancyLevel(20.5)).toBe('low')
    expect(occupancyLevel(21)).toBe('low')
    expect(occupancyLevel(60)).toBe('low')
    expect(occupancyLevel(61)).toBe('normal')
    expect(occupancyLevel(80)).toBe('normal')
    expect(occupancyLevel(80.1)).toBe('danger')
    expect(occupancyLevel(100)).toBe('danger')
  })

  it('非有限值按 0 处理（NaN 不该掉进 danger 把空储位画成红色）', () => {
    expect(occupancyLevel(Number.NaN)).toBe('empty')
    expect(occupancyLevel(Number.POSITIVE_INFINITY)).toBe('empty')
  })

  it('取色按档位落到对应兜底色（变量不可用时）', () => {
    stubCssVars({})
    expect(occupancyColor(0)).toBe('#c0c4cc')
    expect(occupancyColor(10)).toBe('#67c23a')
    expect(occupancyColor(50)).toBe('#409eff')
    expect(occupancyColor(70)).toBe('#e6a23c')
    expect(occupancyColor(95)).toBe('#f56c6c')
    vi.restoreAllMocks()
  })

  it('取色优先用 --kubo-occupancy-* 变量（暗色模式跟随 tokens）', () => {
    stubCssVars({ '--kubo-occupancy-danger': 'rgb(7, 7, 7)' })
    expect(occupancyColor(95)).toBe('rgb(7, 7, 7)')
    vi.restoreAllMocks()
  })
})

describe('palette · occupancyRamp 连续色带', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('排除 empty 档，剩下四档按由低到高排列', () => {
    stubCssVars({})
    const ramp = occupancyRamp()
    // empty 的浅灰若进色带，0-20% 会被灰→绿插值占据，低占用格子发灰、可读性下降
    expect(ramp).toEqual(['#67c23a', '#409eff', '#e6a23c', '#f56c6c'])
    expect(ramp).toHaveLength(OCCUPANCY_LEVELS.length - 1)
  })

  it('色带同样优先取 CSS 变量', () => {
    stubCssVars({
      '--kubo-occupancy-idle': 'rgb(1, 1, 1)',
      '--kubo-occupancy-low': 'rgb(2, 2, 2)',
      '--kubo-occupancy-normal': 'rgb(3, 3, 3)',
      '--kubo-occupancy-danger': 'rgb(4, 4, 4)',
    })
    expect(occupancyRamp()).toEqual(['rgb(1, 1, 1)', 'rgb(2, 2, 2)', 'rgb(3, 3, 3)', 'rgb(4, 4, 4)'])
  })
})

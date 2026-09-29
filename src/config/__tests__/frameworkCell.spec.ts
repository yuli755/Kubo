import { describe, expect, it } from 'vitest'
import { DateTimeFormatter, DesensitizeType, formatMoney, formatQuantity, formatThousands, getCellColor, getCellText, getColumnAlign, getEnumItems, getEnumLabel, WebColor, WebEnum } from '@/framework'

/**
 * # 框架层·单元格与格式化契约
 *
 * 这些纯函数决定「一个字段在表格/详情页里长什么样」。它们出错的后果不报错、只是**显示不对**：
 * 金额少个千分位、时间戳直接糊在列里、手机号明文暴露、关联对象显示 `[object Object]`。
 * 所以这里逐条钉住行为，不靠肉眼回归。
 */

enum DemoStatus {
  WAIT = 1,
  DONE = 2,
}

class DemoStatusEnum extends WebEnum<DemoStatus> {
  static WAIT = new DemoStatusEnum(DemoStatus.WAIT, '待处理').setColor(WebColor.WARNING)
  static DONE = new DemoStatusEnum(DemoStatus.DONE, '已完成').setColor(WebColor.SUCCESS)
}

describe('f2 · 数值格式化', () => {
  it('千分位与小数位', () => {
    expect(formatThousands(1234567.891)).toBe('1,234,567.891')
    expect(formatThousands(1000)).toBe('1,000')
    expect(formatThousands(-1234.5, 2)).toBe('-1,234.50')
    expect(formatThousands('not-a-number')).toBe('-')
  })

  it('金额：字符串形式的后端 BigDecimal 也要保留两位小数', () => {
    // 后端 BigDecimal 序列化常见 `"12.30"` —— 字符串截取会显示成 12.3，金额列不能这样
    expect(formatMoney('12.30')).toBe('12.30')
    expect(formatMoney(12.3)).toBe('12.30')
    expect(formatMoney(1234567.891)).toBe('1,234,567.89')
    expect(formatMoney(1234567.891, 4)).toBe('1,234,567.8910')
    expect(formatMoney('12.30', false)).toBe('12.3')
    expect(formatMoney(null)).toBe('-')
    expect(formatMoney(undefined)).toBe('-')
    expect(formatMoney('')).toBe('-')
  })

  it('数量：整数不补小数位', () => {
    expect(formatQuantity(1000)).toBe('1,000')
    expect(formatQuantity(12.5)).toBe('12.5')
    expect(formatQuantity('')).toBe('-')
  })
})

describe('f2 · 单元格文本', () => {
  it('空值统一 -', () => {
    expect(getCellText({ name: null }, { key: 'name' })).toBe('-')
    expect(getCellText({ name: '' }, { key: 'name' })).toBe('-')
    expect(getCellText({ name: undefined }, { key: 'name' })).toBe('-')
  })

  it('字典按 key 显示 label，没有匹配就退回原值', () => {
    expect(getCellText({ status: 2 }, { key: 'status' }, DemoStatusEnum)).toBe('已完成')
    expect(getCellText({ status: 99 }, { key: 'status' }, DemoStatusEnum)).toBe('99')
  })

  it('布尔显示 是/否（表格里 true/false 读起来最费劲）', () => {
    expect(getCellText({ isDisabled: true }, { key: 'isDisabled' })).toBe('是')
    expect(getCellText({ isDisabled: false }, { key: 'isDisabled' })).toBe('否')
  })

  it('时间列按格式化器渲染（后端给的是毫秒时间戳）', () => {
    const timestamp = new Date(2026, 8, 17, 10, 30, 0).getTime()
    expect(getCellText({ createTime: timestamp }, { key: 'createTime', datetime: true })).toBe('2026-09-17 10:30:00')
    expect(getCellText({ day: timestamp }, { key: 'day', datetime: DateTimeFormatter.FULL_DATE })).toBe('2026-09-17')
  })

  it('脱敏：姓名/手机号/邮箱/身份证', () => {
    expect(getCellText({ name: '张三丰' }, { key: 'name', desensitize: DesensitizeType.CHINESE_NAME })).toBe('张**')
    expect(getCellText({ phone: '13800008888' }, { key: 'phone', desensitize: DesensitizeType.MOBILE })).toBe('138****8888')
    expect(getCellText({ phone: '13800008888' }, { key: 'phone', phone: true })).toBe('138****8888')
    expect(getCellText({ email: 'zhangsan@example.com' }, { key: 'email', desensitize: DesensitizeType.EMAIL })).toBe('zh***@example.com')
  })

  it('关联对象列渲染 payload 标签，不出现 [object Object]', () => {
    const storage = { getPayloadLabel: () => '主仓库(A01)' }
    expect(getCellText({ storage }, { key: 'storage', payload: true })).toBe('主仓库(A01)')
    expect(getCellText({ storage: { name: '主仓库', code: 'A01' } }, { key: 'storage', payload: true })).toBe('主仓库(A01)')
    expect(getCellText({ storage: [storage, { name: '二号库' }] }, { key: 'storage', payload: true })).toBe('主仓库(A01)、二号库')
    expect(getCellText({ storage: [] }, { key: 'storage', payload: true })).toBe('-')
  })

  /**
   * 漏标 `payload: true` 是常态，不是意外：真机实测「数据权限 · 列级脱敏」页的 `resource`
   * 关联列就渲染成了 `[object Object]`（Playwright 抓到的首行文本里原样出现）。
   * 正解是给列标 `payload`，但框架层不能让「漏标」退化成不可读的字符串 ——
   * `getCellText` 被表格 / 详情 / 导出三处共用，漏一处就是三处一起脏。
   */
  it('未标 payload 的关联列也不得吐 [object Object]（漏标注解时的兜底）', () => {
    // 业务标识键优先：resource 列显示 resourceKey，而不是 #15
    expect(getCellText({ resource: { id: 15, resourceKey: 'wms.inventory' } }, { key: 'resource' }))
      .toBe('wms.inventory')
    // name/code 仍是首选（与 payload 分支同一套规则）
    expect(getCellText({ storage: { name: '主仓库', code: 'A01' } }, { key: 'storage' })).toBe('主仓库(A01)')
    // 什么标签都没有时退到 id，至少能对上后端那一行
    expect(getCellText({ owner: { id: 7 } }, { key: 'owner' })).toBe('#7')
    // 连 id 都没有：宁可显示「没有值」，也不要显示 [object Object]
    expect(getCellText({ meta: {} }, { key: 'meta' })).toBe('-')
    expect(getCellText({ list: [{ id: 1 }, { name: '甲' }] }, { key: 'list' })).toBe('#1、甲')
    // Date 保持旧行为（可读的字符串），不被对象兜底吃成 '-'
    expect(getCellText({ at: new Date(2026, 8, 17, 10, 30, 0) }, { key: 'at' }))
      .toContain('2026')
  })

  it('金额列走金额格式化', () => {
    expect(getCellText({ price: '12.30' }, { key: 'price', money: true })).toBe('12.30')
    expect(getCellText({ price: 1234567.8 }, { key: 'price', money: true })).toBe('1,234,567.80')
  })

  it('formatter 优先于其它规则（页面自定义取值时不被字典/金额覆盖）', () => {
    expect(getCellText({ status: 1 }, { key: 'status', formatter: (_row, value) => `#${value}` }, DemoStatusEnum)).toBe('#1')
  })
})

describe('f2 · 字典兼容 airpower 枚举（双底座过渡的硬约束）', () => {
  /**
   * 存量枚举类继承的是 **airpower** 的 `WebEnum`，它只有 `toArray()/getLabel()/get()`，
   * 没有本仓的 `getList()`。双注册的实体（StructureEntity.type 就是 StructureTypeEnum）
   * 在新表格里必须照样显示中文，否则「列头有、值是数字」。
   */
  const airpowerLikeEnum = {
    toArray: () => [
      { key: 1, label: '单人工位', color: WebColor.SUCCESS },
      { key: 2, label: '公共工区', color: WebColor.WARNING },
    ],
    getLabel: (key: number) => (key === 1 ? '单人工位' : '-'),
  }

  it('getEnumItems 能吃 toArray 形状', () => {
    expect(getEnumItems(airpowerLikeEnum).map(item => item.label)).toEqual(['单人工位', '公共工区'])
    expect(getEnumItems(DemoStatusEnum).map(item => item.label)).toEqual(['待处理', '已完成'])
    expect(getEnumItems(undefined)).toEqual([])
  })

  it('getEnumLabel：静态 getLabel 返回占位符时退回列表查找', () => {
    expect(getEnumLabel(airpowerLikeEnum, 1)).toBe('单人工位')
    // 静态 getLabel 返回 '-'（airpower 的默认值）不能当成真标签，否则列里会出现一堆 '-'
    expect(getEnumLabel(airpowerLikeEnum, 2)).toBe('公共工区')
  })

  it('表格单元格用 airpower 枚举也能显示文案与颜色', () => {
    expect(getCellText({ type: 2 }, { key: 'type' }, airpowerLikeEnum)).toBe('公共工区')
    expect(getCellColor(airpowerLikeEnum, 2)).toBe(WebColor.WARNING)
  })
})

describe('f2 · 列样式', () => {
  it('金额列右对齐（数字左对齐时位数对不齐，扫读困难）', () => {
    expect(getColumnAlign({ key: 'price', money: true })).toBe('right')
    expect(getColumnAlign({ key: 'name' })).toBe('left')
    expect(getColumnAlign({ key: 'status', align: 'center' })).toBe('center')
  })

  it('字典色用于单元格文字色', () => {
    expect(getCellColor(DemoStatusEnum, DemoStatus.DONE)).toBe(WebColor.SUCCESS)
    expect(getCellColor(undefined, DemoStatus.DONE)).toBeUndefined()
  })
})

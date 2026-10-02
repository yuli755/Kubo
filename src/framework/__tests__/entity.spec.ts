import type { IJson } from '@/framework/types'
import { describe, expect, it } from 'vitest'
import { DateTimeFormatter, DateTimeUtil } from '@/framework/datetime'
import { fill, RootEntity, RootModel, toJson, Transformer } from '@/framework/entity'
import { DesensitizeType } from '@/framework/enums'
import { Field, Model, Type } from '@/framework/metadata'
import { QueryRequestPage, QueryResponsePage, QuerySort } from '@/framework/query'

@Model({ label: '明细' })
class DetailEntity extends RootEntity {
  @Field({ label: '名称' })
  name!: string
}

@Model({ label: '单据' })
class BillEntity extends RootEntity {
  @Field({ label: '单号' })
  code!: string

  @Type(DetailEntity)
  @Field({ label: '明细' })
  details!: DetailEntity[]

  @Type(DetailEntity)
  @Field({ label: '主明细' })
  mainDetail!: DetailEntity
}

describe('framework · 实体与序列化', () => {
  it('copy() 深拷贝：改副本不影响原对象（嵌套实体与数组同样复制）', () => {
    const bill = new BillEntity()
    bill.code = 'RK001'
    bill.details = [Object.assign(new DetailEntity(), { name: '铝板' })]
    bill.mainDetail = Object.assign(new DetailEntity(), { name: '主' })

    const copy = bill.copy()
    copy.code = 'RK002'
    copy.details[0].name = '钢材'
    copy.mainDetail.name = '改过的主'

    expect(bill.code).toBe('RK001')
    expect(bill.details[0].name).toBe('铝板')
    expect(bill.mainDetail.name).toBe('主')
    // 副本仍是实体实例（保留原型，方法可用）
    expect(copy).toBeInstanceOf(BillEntity)
    expect(copy.details[0]).toBeInstanceOf(DetailEntity)
  })

  it('parse/@Type：JSON 递归解析成嵌套实体', () => {
    const bill = RootModel.parse({
      id: 7,
      code: 'RK007',
      details: [{ id: 1, name: '铝板' }, { id: 2, name: '钢管' }],
      mainDetail: { id: 3, name: '主明细' },
    }, BillEntity)

    expect(bill.id).toBe(7)
    expect(bill.details).toHaveLength(2)
    expect(bill.details[0]).toBeInstanceOf(DetailEntity)
    expect(bill.details[1].name).toBe('钢管')
    expect(bill.mainDetail).toBeInstanceOf(DetailEntity)
  })

  it('exclude() 剔除字段（分页请求不带 filter 的场景）', () => {
    const request = new QueryRequestPage(BillEntity)
    request.filter.code = 'RK'
    request.exclude('filter')
    expect('filter' in request).toBe(false)
    expect(request.page.pageNum).toBe(1)
  })

  it('toJson() 去掉方法、保留数据', () => {
    const bill = Object.assign(new BillEntity(), { id: 1, code: 'RK001' })
    const json = toJson(bill)
    expect(json).toMatchObject({ id: 1, code: 'RK001' })
    expect(typeof (json as { copy?: unknown }).copy).toBe('undefined')
    expect(Transformer.toJson(bill)).toEqual(json)
  })

  it('toJson() 剔除嵌套层级里的脱敏占位值，顶层掩码保留', () => {
    // R-34 场景：受限角色选中的物料带着 h: 摘要价，随明细整对象回传会被后端
    // SensitiveFieldDeserializer 当场拒收；嵌套 @ManyToOne 引用只按 id 绑定，剔除零损失
    const bill = Object.assign(new BillEntity(), {
      code: 'RK001',
      note: 'h:顶层掩码照常保留', // 顶层掩码的拒收/恢复是后端刻意策略，前端不代办
      details: [Object.assign(new DetailEntity(), {
        name: '铝板',
        material: { id: 11, code: 'MA01', purchasePrice: 'h:41d06a0e9908', salePrice: '**', name: '铝板' },
      })],
      mainDetail: Object.assign(new DetailEntity(), {
        name: '主',
        storage: { id: 3, code: 'SRG****001' },
      }),
    })
    const json = toJson(bill)
    const detail = (json.details as IJson[])[0]
    expect((detail.material as IJson)).toEqual({ id: 11, code: 'MA01', name: '铝板' })
    expect((json.mainDetail as IJson).storage).toEqual({ id: 3 })
    expect(json.note).toBe('h:顶层掩码照常保留')
    // Date 实例必须是例外：松散的 isPlainObject 会把 Date 也当对象，递归进去就被掏空
    const stamped = Object.assign(new BillEntity(), { bizDate: new Date(2026, 8, 17) })
    expect((toJson(stamped).bizDate as Date).getFullYear()).toBe(2026)
  })

  it('fill() 容忍 null/undefined 与缺失字段', () => {
    const detail = fill(new DetailEntity(), { id: undefined, name: null })
    expect(detail.name).toBeUndefined()
    expect(fill(new DetailEntity(), '不是对象').id).toBeUndefined()
  })

  it('fill() 跳过原型污染键（__proto__ 换不掉实例原型，全局原型不被污染）', () => {
    // 响应 JSON 是不可信输入：target['__proto__'] = {...} 不是普通赋值，
    // 会触发 setter 把该实例的原型整个换掉（后续属性读取行为诡异且极难排查）
    const evil = JSON.parse('{"name":"ok","__proto__":{"polluted":true},"constructor":{"bad":true}}')
    const detail = fill(new DetailEntity(), evil)
    expect(detail.name).toBe('ok')
    expect(Object.getPrototypeOf(detail)).toBe(DetailEntity.prototype)
    expect((detail as unknown as Record<string, unknown>).polluted).toBeUndefined()
    expect((Object.prototype as unknown as Record<string, unknown>).polluted).toBeUndefined()
  })

  it('copy()/deepCopy 同样跳过原型污染键（草稿等本地数据同源不可信）', () => {
    const seeded = new DetailEntity()
    seeded.name = 'ok'
    // defineProperty 造出真正的**自有** __proto__ 数据属性（赋值触发 setter，造不出来）
    Object.defineProperty(seeded, '__proto__', { value: { polluted: true }, enumerable: true, configurable: true, writable: true })
    const cloned = seeded.copy()
    expect(cloned.name).toBe('ok')
    expect((cloned as unknown as Record<string, unknown>).polluted).toBeUndefined()
    expect(({} as Record<string, unknown>).polluted).toBeUndefined()
  })

  it('分页响应形状与后端一致', () => {
    const response = new QueryResponsePage<BillEntity>()
    response.total = 42
    response.pageCount = 3
    response.list = [Object.assign(new BillEntity(), { id: 1 })]
    expect(response.list).toHaveLength(1)
    expect(response.page.pageSize).toBe(20)
    expect(new QuerySort().setField('code').setDirection('ASC').direction).toBe('ASC')
  })
})

describe('framework · 时间与脱敏', () => {
  it('dateTimeFormatter 按格式串输出，兼容毫秒/秒/ISO 字符串', () => {
    const date = new Date(2026, 8, 17, 14, 5, 9)
    expect(DateTimeFormatter.FULL_DATE.formatDate(date)).toBe('2026-09-17')
    expect(DateTimeFormatter.FULL_DATE_TIME.formatMilliSecond(date.getTime())).toBe('2026-09-17 14:05:09')
    expect(DateTimeFormatter.FULL_DATE.formatSecond(Math.floor(date.getTime() / 1000))).toBe('2026-09-17')
    expect(DateTimeFormatter.FULL_DATE.format('2026-09-17T06:05:09.000Z')).toMatch(/^2026-09-1[78]$/)
    expect(DateTimeFormatter.FULL_DATE.formatMilliSecond(Number.NaN)).toBe('-')
    expect(DateTimeFormatter.TIMESTAMP.formatDate(date)).toBe(String(date.getTime()))
  })

  it('dateTimeUtil 提供时间常量与友好时间', () => {
    expect(DateTimeUtil.SECOND_PER_DAY).toBe(86400)
    expect(DateTimeUtil.getMilliTimeStamps()).toBeGreaterThan(0)
    expect(DateTimeUtil.getFriendlyDateTime(Date.now() - 60_000)).toBe('1 分钟前')
  })

  it('脱敏规则与后端列级脱敏同名', () => {
    expect(DesensitizeType.CHINESE_NAME.desensitize('张三丰')).toBe('张**')
    expect(DesensitizeType.MOBILE.desensitize('13812348888')).toBe('138****8888')
    expect(DesensitizeType.EMAIL.desensitize('linyiming@kubo.example.com')).toBe('li***@kubo.example.com')
    expect(DesensitizeType.ID_CARD.desensitize('320500199001011234')).toBe('320500********1234')
    expect(DesensitizeType.EMAIL.desensitize('')).toBe('')
  })
})

import { describe, expect, it } from 'vitest'
import { RootEntity } from '@/framework/entity'
import { WebColor, WebEnum } from '@/framework/enums'
import {
  Field,
  Form,
  getFieldLabel,
  getFormConfigList,
  getModelName,
  getSearchConfigList,
  getTableConfigList,
  Model,
  Search,
  Table,
} from '@/framework/metadata'

class StatusEnum extends WebEnum {
  static AUDITING = new StatusEnum(1, '审核中').setColor(WebColor.WARNING)
  static DONE = new StatusEnum(4, '已完成').setColor(WebColor.SUCCESS)
}

@Model({ label: '测试模型' })
class Base extends RootEntity {
  @Table({ width: 80, order: -99, force: true, removed: false })
  @Field({ label: '发布状态', dictionary: StatusEnum })
  isPublished!: boolean

  @Table({ order: -99, hide: true })
  @Field({ label: '更新时间' })
  updateTime!: number
}

@Model({ label: '子模型' })
class Child extends Base {
  @Table({ force: true, width: 200 })
  @Form({ requiredString: true })
  @Field({ label: '名称' })
  @Search({ order: 1 })
  name!: string

  @Table({ removed: true })
  @Search({ hide: true })
  @Field({ label: '内部字段' })
  secret!: string

  @Form({ number: true, min: 1, max: 10, order: 5 })
  @Field({ label: '数量' })
  count!: number
}

describe('framework · 模型元数据', () => {
  it('模型名缺失时回落到类名', () => {
    expect(getModelName(Child)).toBe('子模型')
    expect(getModelName(class Foo {})).toBe('Foo')
  })

  it('表格列：继承父类字段、剔除 removed、按 order 降序、回填标题', () => {
    const columns = getTableConfigList(Child)
    expect(columns.map(item => item.key)).toEqual(['name', 'isPublished', 'updateTime'])
    expect(columns[0].label).toBe('名称')
    expect(columns[1].label).toBe('发布状态')
    // removed 的字段不进列表；hide 只是默认隐藏，仍在列里（列选择器可恢复）
    expect(columns.map(item => item.key)).not.toContain('secret')
    expect(columns.map(item => item.key)).toContain('updateTime')
  })

  it('表单字段：继承 + 按 order 降序 + 自动生成校验规则', () => {
    const fields = getFormConfigList(Child)
    expect(fields.map(item => item.key)).toEqual(['count', 'name'])
    expect(fields.find(item => item.key === 'name')?.requiredString).toBe(true)
  })

  it('搜索字段：hide 的字段不参与搜索', () => {
    const searches = getSearchConfigList(Child)
    expect(searches.map(item => item.key)).toEqual(['name'])
  })

  it('字段标题缺省回落到字段名', () => {
    expect(getFieldLabel(Child, 'name')).toBe('名称')
    expect(getFieldLabel(Child, 'unknownField')).toBe('unknownField')
  })

  it('枚举字典：列表/按 key 取文案与颜色', () => {
    expect(StatusEnum.getList().map(item => item.key)).toEqual([1, 4])
    expect(StatusEnum.getLabel(1)).toBe('审核中')
    expect(StatusEnum.getColor(4)).toBe(WebColor.SUCCESS)
    expect(StatusEnum.getLabel(999)).toBe('-')
    expect(StatusEnum.AUDITING.equalsKey(1)).toBe(true)
  })
})

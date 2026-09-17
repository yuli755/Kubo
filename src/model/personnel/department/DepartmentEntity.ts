import type { IPayload, ITree } from '@airpower/web'
import { Field, Form, Model, Search, Table, Type } from '@airpower/web'
// ⚠️ 过渡态双注册：见 `MaterialEntity` 的说明。批 3 之后 personnel 域**自己**已经全走 K 装饰器
// （部门页、用户页的部门选择器都读 `@K*`）；airpower 那套还留着，是因为
// `UserEntity` / `StructureEntity` 的 airpower `@Type(DepartmentEntity, true)` 仍在指向它，
// 而那两个实体的 airpower 消费方（`component/UserCard.vue`、wms 库存页）属于 F5 ——
// 提前删掉会让这些还没迁的页面在运行时拿不到类型元数据。
import { BaseEntity } from '@/base/BaseEntity'
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'

/**
 * # 部门
 * @author Hamm.cn
 */

@Model({
  label: '部门',
  addChildPermission: 'add',
})
@KModel({
  label: '部门',
  addChildPermission: 'add',
})
export class DepartmentEntity extends BaseEntity implements ITree, IPayload {
  @Table({
    force: true,
    order: 99,
    copy: true,
  })
  @Search()
  @Form({
    requiredString: true,
  })
  @Field({
    label: '部门名称',
  })
  @KTable({ order: 100, minWidth: 160, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '部门名称' })
  @KSearch()
  name!: string

  @Table({
    force: true,
    order: 88,
    width: 300,
    copy: true,
  })
  @Search()
  @Form({
    placeholder: '如不填写,则后端自动生成...',
  })
  @Field({
    label: '部门编码',
  })
  @KTable({ order: 90, minWidth: 220, copy: true })
  @KForm({ placeholder: '如不填写,则后端自动生成...', maxLength: 32 })
  @KField({ label: '部门编码' })
  @KSearch()
  code!: string

  @Form({
    max: 200,
    min: 0,
    number: true,
    filterable: true,
    requiredNumber: true,
    defaultValue: 1,
  })
  @Table({
    width: 100,
  })
  @Field({
    label: '排序编号',
  })
  @KTable({ order: 80, width: 100 })
  @KForm({ number: true, min: 0, max: 200, requiredNumber: true, defaultValue: 1 })
  @KField({ label: '排序编号' })
  orderNo!: number

  @Field({
    label: '父级ID',
  })
  parentId!: number

  @Field({})
  @Type(DepartmentEntity, true)
  @KField({})
  @KType(DepartmentEntity)
  children!: this[]

  @Type(DepartmentEntity)
  @KType(DepartmentEntity)
  parent!: this

  @Table({
    removed: true,
  })
  declare createTime: number

  @Table({
    removed: true,
  })
  declare isDisabled: boolean

  getPayloadLabel(): string {
    return this.name
  }
}

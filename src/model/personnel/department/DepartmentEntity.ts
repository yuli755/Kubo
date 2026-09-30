import type { IPayload, ITree } from '@/framework'
// ⚠️ 过渡态双注册：见 `MaterialEntity` 的说明。批 3 之后 personnel 域**自己**已经全走 K 装饰器
// （部门页、用户页的部门选择器都读 `@K*`）；airpower 那套还留着，是因为
// `UserEntity` / `StructureEntity` 的 airpower `@Type(DepartmentEntity, true)` 仍在指向它，
// 而那两个实体的 airpower 消费方（`component/UserCard.vue`、wms 库存页）属于 F5 ——
// 提前删掉会让这些还没迁的页面在运行时拿不到类型元数据。
import { BaseEntity } from '@/base/BaseEntity'
import { Field as KField, Form as KForm, Model as KModel, Search as KSearch, Table as KTable, Type as KType } from '@/framework'

/**
 * # 部门
 * @author Hamm.cn
 */

@KModel({
  label: '部门',
  addChildPermission: 'add',
})
export class DepartmentEntity extends BaseEntity implements ITree, IPayload {
  @KTable({ order: 100, minWidth: 180, force: true, copy: true })
  @KForm({ requiredString: true })
  @KField({ label: '部门名称' })
  @KSearch()
  name!: string

  /**
   * ### 部门编码
   *
   * 定宽 180（实测 30px）。原 `minWidth: 220` 让三列瓜分剩余宽度，
   * 实测名称 319 / 编码 437，内容只有 42 / 30px。列宽口径见 `src/framework/types.ts`。
   */
  @KTable({ order: 90, width: 180, copy: true })
  @KForm({ placeholder: '如不填写,则后端自动生成...', maxLength: 32 })
  @KField({ label: '部门编码' })
  @KSearch()
  code!: string

  @KTable({ order: 80, width: 100 })
  @KForm({ number: true, min: 0, max: 200, requiredNumber: true, defaultValue: 1 })
  @KField({ label: '排序编号' })
  orderNo!: number

  parentId!: number

  @KField({})
  @KType(DepartmentEntity)
  children!: this[]

  @KType(DepartmentEntity)
  parent!: this

  /** 基础数据表统一补「创建时间」列，理由同 `StorageEntity.createTime`。 */
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  declare isDisabled: boolean

  getPayloadLabel(): string {
    return this.name
  }
}

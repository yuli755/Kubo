import type { WebEnum } from '@airpower/web'
import { Field, Form, Model, Table, Type } from '@airpower/web'
import { AbstractBaseBillEntity } from '@/base/bill/AbstractBaseBillEntity'
// 过渡态双注册：`@K*` 服务于批 4 起的移库页（KTable/KFormDialog/KDetail），
// 与本仓 airpower 页共用的选择器等仍读上面那套。基类暂时还是 airpower 的
// `AbstractBaseBillEntity`（`canAudit`/`isRejected` 等判据仍由它提供），
// 切 `KEntity` 与这里的双注册收尾一起做。
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Search as KSearch,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'
import { MoveDetailEntity } from './MoveDetailEntity'
import { MoveStatusEnum } from './MoveStatusEnum'

@Model({
  label: '移库单',
})
@KModel({
  label: '移库单',
  addChildPermission: 'add',
})
export class MoveEntity extends AbstractBaseBillEntity<MoveDetailEntity> {
  @Field({
    label: '移库单号',
  })
  @KTable({ order: 99, force: true, minWidth: 180, copy: true })
  @KForm({ placeholder: '不填写按编码规则自动生成' })
  @KField({ label: '移库单号' })
  @KSearch()
  declare billCode: string

  @Table({
    formatter: row => `${row.storage.name}(${row.storage.code})`,
  })
  @KTable({
    minWidth: 200,
    order: 90,
    formatter: (row: never) => {
      const storage = (row as { storage?: { name?: string, code?: string } }).storage
      return storage ? `${storage.name}(${storage.code})` : '-'
    },
  })
  @KField({ label: '目标仓库' })
  @Field({
    label: '目标仓库',
  })
  storageName!: string

  @Form({
    requiredPayload: true,
  })
  @KForm({ requiredPayload: true })
  @KField({ label: '目标仓库' })
  @KType(StorageEntity)
  @Field({
    label: '目标仓库',
  })
  @Type(StorageEntity)
  storage!: StorageEntity

  @Table({
    width: 100,
    color: true,
    order: -80,
    force: true,
  })
  // 不给 `@KForm`：单据状态由状态机（审核/驳回/作业）驱动，表单里出现就成了"用户能直接改状态"
  @KTable({ width: 100, order: -80, force: true, color: true })
  @KField({ label: '移库状态', dictionary: MoveStatusEnum })
  @KSearch({ width: 120 })
  @Field({
    label: '移库状态',
    dictionary: MoveStatusEnum,
  })
  status!: number

  @Field({
    label: '移库明细',
  })
  @KField({ label: '移库明细' })
  @KType(MoveDetailEntity)
  @Type(MoveDetailEntity, true)
  details: MoveDetailEntity[] = []

  // ── 下面三个字段声明在 airpower 的 `AbstractBaseBillEntity` 上（那套装饰器写不进框架注册表），
  //    这里 `declare` 重声明一次只为挂 `@K*`：K 表单/K 详情要能看到「创建时间/更新时间/驳回原因」。
  @KTable({ order: -90, width: 170, datetime: true })
  @KField({ label: '创建时间' })
  declare createTime: number

  @KTable({ order: -91, width: 170, datetime: true, hide: true })
  @KField({ label: '更新时间' })
  declare updateTime: number

  // 同样不进 K 表单：驳回原因由驳回动作写入，详情页只读展示
  @KField({ label: '驳回原因' })
  declare rejectReason: string

  getAuditingStatus(): WebEnum {
    return MoveStatusEnum.AUDITING
  }

  getAuditedStatus(): WebEnum {
    return MoveStatusEnum.MOVING
  }

  getRejectedStatus(): WebEnum {
    return MoveStatusEnum.REJECTED
  }
}

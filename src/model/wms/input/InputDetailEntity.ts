import { AbstractBaseBillDetailEntity } from '@/base/bill/detail/AbstractBaseBillDetailEntity'
// 双注册：明细的 K 表在单据编辑器/详情里渲染（`KDetailTable`），基类暂时仍是 airpower 的。
import {
  Field as KField,
  Form as KForm,
  Model as KModel,
  Table as KTable,
  Type as KType,
} from '@/framework'
import { MaterialEntity } from '@/model/asset/material/MaterialEntity'
import { StorageEntity } from '@/model/factory/storage/StorageEntity'

@KModel({
  label: '入库明细',
})
export class InputDetailEntity extends AbstractBaseBillDetailEntity {
  @KType(StorageEntity)
  @KField({ label: '目标仓库' })
  storage!: StorageEntity

  @KForm({ requiredPayload: true })
  @KType(MaterialEntity)
  @KField({ label: '物料' })
  material!: MaterialEntity

  @KTable({ force: true, minWidth: 140, order: 90, copy: true, formatter: (row: never) => materialPart(row, 'code') })
  @KField({ label: '物料编码' })
  materialCode!: string

  @KTable({ force: true, minWidth: 180, order: 80, copy: true, formatter: (row: never) => materialPart(row, 'name') })
  @KField({ label: '物料名称' })
  materialName!: string

  @KTable({ minWidth: 180, order: 70, formatter: (row: never) => {
    const storage = (row as { storage?: { name?: string, code?: string } }).storage
    return storage ? `${storage.name}(${storage.code})` : '-'
  } })
  @KField({ label: '目标仓库' })
  storageName!: string

  @KForm({ requiredNumber: true })
  @KField({ label: '物料' })
  materialId!: number

  @KTable({ align: 'right', width: 150, order: -2, force: true })
  @KForm({ number: true, requiredNumber: true })
  @KField({ label: '入库数量' })
  quantity!: number

  finishQuantity!: number

  @KForm({ requiredNumber: true })
  @KField({ label: '目标仓库' })
  storageId!: number
}

/** 从明细的嵌套物料里取字段（airpower 列与 K 列共用同一取值口径） */
function materialPart(row: unknown, part: 'name' | 'code'): string {
  const material = (row as { material?: Record<string, string> }).material
  return material?.[part] ?? ''
}

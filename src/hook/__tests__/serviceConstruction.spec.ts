/**
 * 锁测：`useTanStackTable` 三步构造的真实语义（GUI-3 根因回归锁）。
 *
 * 真机 GUI 发现 24/34 列表页整页崩溃：
 *   1) `serviceClass.entityClass` 静态读取恒为 undefined（class field 是实例属性）
 *      → "serviceClass.entityClass is not a constructor"；
 *   2) 修复第 1 步后部分实体在 `new QueryRequestPage(entity)` 处仍有问题。
 *
 * 本文件不渲染组件（无需 pinia / vue-query 上下文），只钉死三步构造本身：
 *   new 服务类 → 从实例取 entityClass → new 实体 → new QueryRequestPage(实体)。
 * 每个真实 Service 都必须通过。
 */
import { QueryRequestPage } from '@airpower/web'
import { describe, expect, it } from 'vitest'
import { ContractService } from '@/model/asset/contract/ContractService'
import { DeviceService } from '@/model/asset/device/DeviceService'
import { MaterialService } from '@/model/asset/material/MaterialService'
import { CustomerService } from '@/model/channel/customer/CustomerService'
import { PurchaseService } from '@/model/channel/purchase/PurchaseService'
import { SaleService } from '@/model/channel/sale/SaleService'
import { SalePriceService } from '@/model/channel/salePrice/SalePriceService'
import { DepartmentService } from '@/model/personnel/department/DepartmentService'
import { RoleService } from '@/model/personnel/role/RoleService'
import { UserService } from '@/model/personnel/user/UserService'
import { InputService } from '@/model/wms/input/InputService'
import { InventoryService } from '@/model/wms/inventory/InventoryService'
import { MoveService } from '@/model/wms/move/MoveService'
import { OutputService } from '@/model/wms/output/OutputService'
import { OrderService } from '@/model/mes/order/OrderService'
import { BomService } from '@/model/mes/bom/BomService'
import { UnitService } from '@/model/system/unit/UnitService'

const SERVICES = {
  ContractService,
  DeviceService,
  MaterialService,
  CustomerService,
  PurchaseService,
  SaleService,
  SalePriceService,
  DepartmentService,
  RoleService,
  UserService,
  InputService,
  InventoryService,
  MoveService,
  OutputService,
  OrderService,
  BomService,
  UnitService,
} as const

describe.each(Object.entries(SERVICES))('Service 三步构造: %s', (_name, ServiceClass) => {
  it('实例上有可构造的 entityClass（class field 不是静态属性）', () => {
    // 复刻 useTanStackTable 的读取方式：必须先实例化，再从实例取 entityClass
    const service = new ServiceClass()
    const { entityClass } = service as unknown as { entityClass?: unknown }
    expect(typeof entityClass).toBe('function')
  })

  it('new QueryRequestPage(实体) 构造成功', () => {
    const service = new ServiceClass()
    const { entityClass } = service as unknown as { entityClass: new () => object }
    const entity = new entityClass()
    const request = new (QueryRequestPage as unknown as new (e: object) => unknown)(entity)
    expect(request).toBeTruthy()
  })
})

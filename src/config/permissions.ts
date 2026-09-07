/**
 * # 权限码常量
 *
 * 集中管理所有模块的权限标识，格式为 `{EntityName}_{action}`，
 * 与后端 `PermissionUtil.scanPermission()` 自动扫描的格式一致。
 *
 * ## 使用方式
 * ```vue
 * <ATable
 *   :use-hook="hook"
 *   :add-permission="Permissions.Order.add"
 *   :edit-permission="Permissions.Order.update"
 * />
 * <BillAuditOrReject
 *   :audit-permission="Permissions.Order.audit"
 *   :reject-permission="Permissions.Order.reject"
 * />
 * ```
 *
 * @author Hamm.cn
 */

/** 通用 CRUD 动作 */
const Crud = {
  add: 'add',
  update: 'update',
  delete: 'delete',
  getDetail: 'getDetail',
  disable: 'disable',
  enable: 'enable',
  addRow: 'add',
  export: 'export',
  import: 'import',
  audit: 'audit',
  reject: 'reject',
  publish: 'publish',
} as const

/** 生产执行 (MES) 模块 */
const Mes = {
  Order: {
    add: `Order_${Crud.add}`,
    update: `Order_${Crud.update}`,
    getDetail: `Order_${Crud.getDetail}`,
    audit: `Order_${Crud.audit}`,
    reject: `Order_${Crud.reject}`,
    start: 'Order_start',
    pause: 'Order_pause',
    addOrderDetail: 'Order_addOrderDetail',
    finish: 'Order_setBillDetailsAllFinished',
  },
  Plan: {
    add: `Plan_${Crud.add}`,
    update: `Plan_${Crud.update}`,
    getDetail: `Plan_${Crud.getDetail}`,
    audit: `Plan_${Crud.audit}`,
    reject: `Plan_${Crud.reject}`,
  },
  Picking: {
    add: `Picking_${Crud.add}`,
    update: `Picking_${Crud.update}`,
    getDetail: `Picking_${Crud.getDetail}`,
    audit: `Picking_${Crud.audit}`,
    reject: `Picking_${Crud.reject}`,
  },
  Bom: {
    add: `Bom_${Crud.add}`,
    update: `Bom_${Crud.update}`,
    delete: `Bom_${Crud.delete}`,
    publish: `Bom_${Crud.publish}`,
  },
  Operation: {
    add: `Operation_${Crud.add}`,
    update: `Operation_${Crud.update}`,
    delete: `Operation_${Crud.delete}`,
  },
  Routing: {
    add: `Routing_${Crud.add}`,
    update: `Routing_${Crud.update}`,
    delete: `Routing_${Crud.delete}`,
    publish: `Routing_${Crud.publish}`,
  },
} as const

/** 仓储 (WMS) 模块 */
const Wms = {
  Input: {
    add: `Input_${Crud.add}`,
    update: `Input_${Crud.update}`,
    getDetail: `Input_${Crud.getDetail}`,
    audit: `Input_${Crud.audit}`,
    reject: `Input_${Crud.reject}`,
    addFinish: 'Input_addFinish',
  },
  Output: {
    add: `Output_${Crud.add}`,
    update: `Output_${Crud.update}`,
    getDetail: `Output_${Crud.getDetail}`,
    audit: `Output_${Crud.audit}`,
    reject: `Output_${Crud.reject}`,
    addFinish: 'Output_addFinish',
  },
  Move: {
    add: `Move_${Crud.add}`,
    update: `Move_${Crud.update}`,
    getDetail: `Move_${Crud.getDetail}`,
    audit: `Move_${Crud.audit}`,
    reject: `Move_${Crud.reject}`,
    addFinish: 'Move_addFinish',
  },
  Inventory: {
    /** 库存只读，无 CRUD 按钮 */
  },
} as const

/** 渠道 (Channel) 模块 */
const Channel = {
  Sale: {
    add: `Sale_${Crud.add}`,
    update: `Sale_${Crud.update}`,
    getDetail: `Sale_${Crud.getDetail}`,
    audit: `Sale_${Crud.audit}`,
    reject: `Sale_${Crud.reject}`,
    addFinish: 'Sale_addFinish',
  },
  Purchase: {
    add: `Purchase_${Crud.add}`,
    update: `Purchase_${Crud.update}`,
    getDetail: `Purchase_${Crud.getDetail}`,
    audit: `Purchase_${Crud.audit}`,
    reject: `Purchase_${Crud.reject}`,
    addFinish: 'Purchase_addFinish',
  },
  Supplier: {
    add: `Supplier_${Crud.add}`,
    update: `Supplier_${Crud.update}`,
  },
  Customer: {
    add: `Customer_${Crud.add}`,
    update: `Customer_${Crud.update}`,
    delete: `Customer_${Crud.delete}`,
  },
  PurchasePrice: {
    add: `PurchasePrice_${Crud.add}`,
    update: `PurchasePrice_${Crud.update}`,
    delete: `PurchasePrice_${Crud.delete}`,
  },
  SalePrice: {
    add: `SalePrice_${Crud.add}`,
    update: `SalePrice_${Crud.update}`,
    delete: `SalePrice_${Crud.delete}`,
  },
} as const

/** 资产 (Asset) 模块 */
const Asset = {
  Device: {
    getDetail: 'Device_getDetail',
    add: `Device_${Crud.add}`,
    update: `Device_${Crud.update}`,
    delete: `Device_${Crud.delete}`,
  },
  Material: {
    getPage: 'Material_getPage',
    getDetail: 'Material_getDetail',
    add: `Material_${Crud.add}`,
    update: `Material_${Crud.update}`,
    delete: `Material_${Crud.delete}`,
    export: `Material_${Crud.export}`,
    getCount: 'Material_getCount',
    getStat: 'Material_getStat',
  },
  Contract: {
    getDetail: 'Contract_getDetail',
    add: `Contract_${Crud.add}`,
    update: `Contract_${Crud.update}`,
    delete: `Contract_${Crud.delete}`,
    enforce: 'Contract_enforce',
    stop: 'Contract_stop',
  },
} as const

/** 人员 (Personnel) 模块 */
const Personnel = {
  User: {
    add: `User_${Crud.add}`,
    update: `User_${Crud.update}`,
    delete: `User_${Crud.delete}`,
    disable: `User_${Crud.disable}`,
    enable: `User_${Crud.enable}`,
  },
  Role: {
    add: `Role_${Crud.add}`,
    update: `Role_${Crud.update}`,
    delete: `Role_${Crud.delete}`,
    disable: `Role_${Crud.disable}`,
    enable: `Role_${Crud.enable}`,
    authorizeMenu: 'Role_authorizeMenu',
    authorizePermission: 'Role_authorizePermission',
  },
  Department: {
    add: `Department_${Crud.add}`,
    update: `Department_${Crud.update}`,
    delete: `Department_${Crud.delete}`,
    addRow: `Department_${Crud.addRow}`,
  },
} as const

/** 工厂 (Factory) 模块 */
const Factory = {
  Storage: {
    add: `Storage_${Crud.add}`,
    update: `Storage_${Crud.update}`,
    delete: `Storage_${Crud.delete}`,
    addRow: `Storage_${Crud.addRow}`,
  },
  Structure: {
    add: `Structure_${Crud.add}`,
    update: `Structure_${Crud.update}`,
    delete: `Structure_${Crud.delete}`,
    addRow: `Structure_${Crud.addRow}`,
  },
} as const

/** 系统 (System) 模块 */
const System = {
  Menu: {
    add: `Menu_${Crud.add}`,
    update: `Menu_${Crud.update}`,
    delete: `Menu_${Crud.delete}`,
    addRow: `Menu_${Crud.addRow}`,
  },
  Permission: {
    add: `Permission_${Crud.add}`,
    update: `Permission_${Crud.update}`,
    delete: `Permission_${Crud.delete}`,
    addRow: `Permission_${Crud.addRow}`,
  },
  CodeRule: {
    update: `CodeRule_${Crud.update}`,
  },
  Config: {
    update: `Config_${Crud.update}`,
  },
  Log: {
    getDetail: `Log_${Crud.getDetail}`,
  },
  Unit: {
    add: `Unit_${Crud.add}`,
    update: `Unit_${Crud.update}`,
    delete: `Unit_${Crud.delete}`,
  },
  /**
   * 数据权限（资源 / 行级策略 / 列级脱敏策略）
   *
   * 只有 add/update/delete/getDetail 是真实存在的动作：`BaseController` 全局
   * `@Extends(exclude = {Curd.Export, Curd.QueryExport, Curd.Disable, Curd.Enable})`
   * 把导出与启停排掉了，所以这里**不能**写 `DataPolicy_disable` 之类的码
   * —— 用不存在的码门控等于把按钮对所有人永久隐藏（`perms:check` 也会直接失败）。
   * 启停请走 `enabled` 字段。
   */
  DataResource: {
    add: `DataResource_${Crud.add}`,
    update: `DataResource_${Crud.update}`,
    delete: `DataResource_${Crud.delete}`,
    getDetail: `DataResource_${Crud.getDetail}`,
  },
  DataPolicy: {
    add: `DataPolicy_${Crud.add}`,
    update: `DataPolicy_${Crud.update}`,
    delete: `DataPolicy_${Crud.delete}`,
    getDetail: `DataPolicy_${Crud.getDetail}`,
  },
  DataFieldPolicy: {
    add: `DataFieldPolicy_${Crud.add}`,
    update: `DataFieldPolicy_${Crud.update}`,
    delete: `DataFieldPolicy_${Crud.delete}`,
    getDetail: `DataFieldPolicy_${Crud.getDetail}`,
  },
} as const

/** 开放平台 (Open) 模块 */
const Open = {
  App: {
    getDetail: 'OpenApp_getDetail',
    add: `OpenApp_${Crud.add}`,
    update: `OpenApp_${Crud.update}`,
    disable: `OpenApp_${Crud.disable}`,
    enable: `OpenApp_${Crud.enable}`,
    resetSecret: 'OpenApp_resetSecret',
    resetKeyPair: 'OpenApp_resetKeyPair',
  },
  Notify: {
    add: `Notify_${Crud.add}`,
    update: `Notify_${Crud.update}`,
    delete: `Notify_${Crud.delete}`,
    disable: `Notify_${Crud.disable}`,
    enable: `Notify_${Crud.enable}`,
  },
} as const

/** 物联网 (IoT) 模块 */
const IoT = {
  Parameter: {
    add: `Parameter_${Crud.add}`,
    update: `Parameter_${Crud.update}`,
    delete: `Parameter_${Crud.delete}`,
  },
} as const

/**
 * # 全局权限码常量
 *
 * 按业务模块分组，使用时直接引用：
 * ```ts
 * import { Permissions } from '@/config/permissions'
 * Permissions.Mes.Order.audit // → "Order_audit"
 * ```
 */
export const Permissions = {
  Mes,
  Wms,
  Channel,
  Asset,
  Personnel,
  Factory,
  System,
  Open,
  IoT,
} as const

/** 权限码类型（用于需要提取所有权限码的场景） */
export type PermissionCode
  = | (typeof Permissions.Mes)[keyof typeof Permissions.Mes][keyof (typeof Permissions.Mes)[keyof typeof Permissions.Mes]]
    | (typeof Permissions.Wms)[keyof typeof Permissions.Wms][keyof (typeof Permissions.Wms)[keyof typeof Permissions.Wms]]
    | (typeof Permissions.Channel)[keyof typeof Permissions.Channel][keyof (typeof Permissions.Channel)[keyof typeof Permissions.Channel]]
    | (typeof Permissions.Asset)[keyof typeof Permissions.Asset][keyof (typeof Permissions.Asset)[keyof typeof Permissions.Asset]]
    | (typeof Permissions.Personnel)[keyof typeof Permissions.Personnel][keyof (typeof Permissions.Personnel)[keyof typeof Permissions.Personnel]]
    | (typeof Permissions.Factory)[keyof typeof Permissions.Factory][keyof (typeof Permissions.Factory)[keyof typeof Permissions.Factory]]
    | (typeof Permissions.System)[keyof typeof Permissions.System][keyof (typeof Permissions.System)[keyof typeof Permissions.System]]
    | (typeof Permissions.Open)[keyof typeof Permissions.Open][keyof (typeof Permissions.Open)[keyof typeof Permissions.Open]]
    | (typeof Permissions.IoT)[keyof typeof Permissions.IoT][keyof (typeof Permissions.IoT)[keyof typeof Permissions.IoT]]

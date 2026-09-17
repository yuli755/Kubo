/**
 * # 框架层·公共类型
 *
 * 这些类型原先是 `@airpower/web` 的对外契约。业务代码（实体/服务/页面）按这套形状书写，
 * 这里逐一对齐，使业务代码在换底座时**零改动**（只换 import 来源）。
 */

/** 任意 JSON 对象 */
export type IJson = Record<string, unknown>

/** 可实例化的构造器（实体类、服务类） */
export interface ITransformerConstructor<T = unknown> {
  new (): T
  name: string
}

/** 可实例化的 CRUD 服务构造器（`E` 用于约束实例上的 `entityClass`） */
export interface ICurdServiceConstructor<E, S> {
  new (): S & { entityClass: ITransformerConstructor<E> }
  name: string
}

/** 枚举键 */
export type EnumKey = number | string

/** 下拉/标签用的枚举实力类型（WebEnum 的实例形状） */
export interface IWebEnumItem<K extends EnumKey = EnumKey> {
  key: K
  label?: string
  color?: string
  disabled?: boolean
}

/**
 * 字典构造器（枚举类本身）。
 *
 * ⚠️ 这里把静态方法声明为**可选**：迁移期实体同时挂 airpower 与 `@/framework` 两套装饰器，
 * 而两边枚举基类的静态 API 不同 —— 本仓 `WebEnum` 是 `getList()`，airpower 的 `WebEnum`
 * 只有 `toArray()` / `getLabel()` / `get()`。若要求必须实现 `getList()`，
 * `StructureTypeEnum`（airpower 枚举）就传不进 `@KField({ dictionary })`。
 * 取值统一走本文件的 `getEnumItems/getEnumLabel/getEnumColor`，两种形状都能吃。
 */
export interface IWebEnumConstructor<K extends EnumKey = EnumKey> {
  getList?: () => IWebEnumItem<K>[]
  toArray?: () => IWebEnumItem<K>[]
  getLabel?: (key: never, defaultLabel?: string) => string
  getColor?: (key: never) => string | undefined
}

/** 字段基础配置：字段名（装饰器内部回填，业务不传） */
export interface IBaseField {
  key: string
}

/** `@Field` 配置 */
export interface IFieldConfig<K extends EnumKey = EnumKey> extends IBaseField {
  /** 字段标题 */
  label?: string
  /** 枚举字典（按 key 渲染标签/选项） */
  dictionary?: IWebEnumConstructor<K>
}

/** `@Table` 配置 */
export interface ITableColumn extends IBaseField {
  /** 列标题（缺省取 `@Field` 的 label） */
  label?: string
  width?: number | string
  /** 最小宽度（Element Plus `min-width`；列多时靠它撑开而不是挤成省略号） */
  minWidth?: number | string
  /** 排序权重，越大越靠前 */
  order?: number
  /** 强制显示（列选择器中不可取消） */
  force?: boolean
  /** 默认隐藏 */
  hide?: boolean
  /** 该字段不作为表格列（如已废弃字段） */
  removed?: boolean
  /** 该字段参与「复制」操作 */
  copy?: boolean
  /** 按枚举色渲染标签 */
  color?: boolean
  /** 按时间格式化（true 或格式化器） */
  datetime?: boolean | unknown
  /** 金额列（右对齐 + 千分位） */
  money?: boolean
  /** 电话列（脱敏展示） */
  phone?: boolean
  /** 脱敏规则 */
  desensitize?: unknown
  /** 关联对象列（渲染 name/code） */
  payload?: boolean
  align?: 'left' | 'center' | 'right'
  fixed?: boolean | 'left' | 'right'
  sortable?: boolean | 'custom'
  /** 自定义取值：`(row) => value` */
  formatter?: (row: never, value?: unknown) => unknown
}

/** `@Form` 配置 */
export interface IFormField extends IBaseField {
  label?: string
  placeholder?: string
  order?: number
  /** 不参与表单 */
  hide?: boolean
  /** 数字输入 */
  number?: boolean
  requiredString?: boolean
  requiredNumber?: boolean
  requiredPayload?: boolean
  min?: number
  max?: number
  minLength?: number
  maxLength?: number
  precision?: number
  defaultValue?: string | number | boolean
  dictionary?: IWebEnumConstructor
  /** 多行文本 */
  textarea?: boolean
  /** 开关 */
  switch?: boolean
  /** 密码 */
  password?: boolean
  /** 只读 */
  readonly?: boolean
  /** 可清空 */
  clearable?: boolean
  /** 可搜索（下拉） */
  filterable?: boolean
  /** 手机号输入 */
  mobilePhone?: boolean
  /** 邮箱输入 */
  email?: boolean
  /** 电话输入 */
  phone?: boolean
  /** 单选组 */
  radio?: boolean
  /** 颜色选择 */
  color?: boolean
  /** 数字后缀文本（如「元」「件」） */
  suffixText?: string
  /** 时间控件类型 / 提交格式 / 展示格式 */
  dateType?: unknown
  dateFormatter?: unknown
  dateShowFormatter?: unknown
  /** 该字段是一个「实体选择器」：传入实体类，渲染 ASelect 并回填 `xxxId` */
  selector?: ITransformerConstructor
  /** 选择器回填的字段名（默认 `字段Id`） */
  selectorField?: string
}

/** `@Search` 配置 */
export interface ISearchField extends IBaseField {
  label?: string
  order?: number
  hide?: boolean
  width?: number | string
  placeholder?: string
  /** 时间区间查询（对应后端 xxxStart / xxxEnd） */
  range?: boolean
  dictionary?: IWebEnumConstructor
}

/**
 * 实体最小结构：只要求 `id`。
 *
 * ⚠️ 为什么不用 `RootEntity` 做约束：迁移期存在**两套实体基类** ——
 * 存量 `@/base/BaseEntity`（继承 airpower 的 `RootEntity`）与新底座 `@/base/KEntity`
 * （继承本仓 `RootEntity`）。两者的 `copy()` 泛型签名不同，用任一方做约束都会把另一方挡在门外。
 * 新底座的组件只需要「有 id、能被 `fill` 填充」，所以按这个最小结构约束。
 */
export interface IIdentifiableEntity {
  id: number
}

/** `@Model` 配置 */
export interface IModelConfig {
  /** 模型中文名 */
  label?: string
  /** 「新增子级」权限动作名 */
  addChildPermission?: string
}

/** 字段/表单配置允许省略 `key`（装饰器会回填） */
export type ConfigOptionalKey<T extends IBaseField> = Omit<T, 'key'> & { key?: string }

/** 带名称的实体（选择器/下拉通用） */
export interface IPayload {
  getPayloadLabel: () => string
}

/** 树节点形状（后端返回的 tree 列表） */
export interface ITree {
  id: number
  name: string
  code?: string
  children?: ITree[]
  isDisabled?: boolean
}

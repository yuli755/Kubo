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
export type EnumKey = number | string | boolean

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

/**
 * `@Table` 配置
 *
 * ## 列宽口径（改任何实体列宽前先读这段）
 *
 * KTable 只把这里的 `width` / `minWidth` 透传给 Element Plus，**不做任何按内容自适应**：
 * 没声明 `width` 的列一律吃 `min-width: 120` 默认值，然后和其它弹性列**平分**剩余宽度。
 * 2026-09 全量量测过一遍（viewport 1440×900，可用表宽 1131~1142px），后果是两类症状：
 *
 * - **空壳列**：字典表只有 2~3 个业务列时，每列被拉到 300~500px，而内容只有 30~90px
 *   （`unit` 单位名称 448/84、`storage` 仓库编码 495/52、`department` 部门编码 437/30）。
 * - **截断列**：列一多就挤不下，长文本被 `show-overflow-tooltip` 砍掉
 *   （`menu` 菜单路径 261/352、`permission` 权限名称 293/374、`policy` 策略编码 200/292）。
 *
 * 因此约定：
 *
 * | 列类型 | 声明 | 依据 |
 * | --- | --- | --- |
 * | 序号 `#` | 56（KTable 内置，不用管） | — |
 * | 名称（**唯一**弹性列） | `minWidth: 180` | 一个表只留一列吃剩余宽度，其余定宽 |
 * | 编码 / 单据号 | `width: 180` | 实测 75~105px，14 位单号 + 复制按钮 |
 * | 电话（已脱敏） | `width: 150` | 实测 82px |
 * | 枚举 / 状态 / 类型 | `width: 110~130` | 实测 48~126px（标签色块有内边距） |
 * | 排序号等小整数 | `width: 100` | 实测 8~15px，留够表头 |
 * | 金额 | `width: 140`，`align: 'right'` | 千分位 + 两位小数 |
 * | 布尔「启用」 | `width: 90~100` | 「已禁用」三字 |
 * | 时间 | `width: 170`，`datetime: true` | `YYYY-MM-DD HH:mm:ss` |
 * | 长文本（路径/JSON/动作列表） | `minWidth: 200~300` 弹性 | 宁可它撑开，别截断 |
 * | 裸 id / 外键 id / JSON blob | `hide: true` | 默认不显示，列选择器里仍可勾回 |
 *
 * **预算**：Σ 可见数据列声明宽度 ≤ **900px**（+ 序号 56 + 操作列 ≤190 ≈ 1146，
 * 正好是 1440 视口下的可用宽度）。超了就优先 `hide` 低价值列，而不是让表格横向滚动。
 * 守卫见 `src/config/__tests__/tableLayout.spec.ts`（读的是装饰器注册表本身，不是正则扒源码）。
 *
 * **表头标签 ≤ 8 个汉字**；括号里的解释属于表单/详情，用 `label` 覆盖列标题即可
 * （`metadata.ts` 的 `label: item.label || getFieldLabel(...)`：`@Table({ label })` 优先，
 * 缺省才回落 `@Field` 的 label），例如 `@KField({ label: '归属列（可按维度登记）' })`
 * 配 `@KTable({ label: '归属列' })`。
 *
 * **字典表**（≤4 个业务列）至少要凑齐「名称 / 编码 / 创建时间」三列，
 * 否则两列会被拉成 400px+ 的空壳。基类字段挂列按 `BaseEntity` 头注的「列表列不进基类」
 * 约定，在实体上重声明（先例：`RoleEntity.isDisabled`、`InputEntity.createTime`）。
 */
export interface ITableColumn extends IBaseField {
  /** 列标题（缺省取 `@Field` 的 label） */
  label?: string
  /** 定宽（px 或 CSS 长度）。除名称列外一律用定宽，见上方「列宽口径」 */
  width?: number | string
  /**
   * 最小宽度（Element Plus `min-width`；列多时靠它撑开而不是挤成省略号）。
   * 只有「唯一弹性列」该用它 —— 多列同时用 `minWidth` 会平分剩余宽度，
   * 于是每列都变宽一点、每列又都不够宽（这正是 2026-09 量测出的两类症状）。
   */
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
  requiredString?: boolean | string
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
 * 存量 `@/base/BaseEntity`（继承 airpower 的 `RootEntity`）与新底座 `@/base/BaseEntity`
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
  /**
   * 权限码前缀（如 `CodeRule` → 页面对应的 `System.CodeRule.*`）。
   *
   * 由后端/配置声明，前端只透传展示（`getModelConfig()` 读得到）。
   * 保留它是为了不让"模型与权限码的对应关系"只在后端存在。
   */
  permissionPrefix?: string
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

// ────────────────────────────── 存量造型接口 ──────────────────────────────
// 三个「业务实体长什么样」的接口：原先由 airpower 提供，实体用 `implements IUser`
// 之类的写法表达"我满足某个通用造型"（上传组件收 `IFile`、用户卡片收 `IUser`、
// 路由构建读 `IMenu`）。定义很薄，但删掉它们会让消费方退化成 `any`。

/** 上传/附件实体的通用造型（`KUpload`、合同附件都用它） */
export interface IFile {
  /** 文件名 */ name: string
  /** 访问地址 */ url: string
  /** 字节数 */ size?: number
  /** 扩展名 */ extension?: string
  /** 分类（`FileCategory`） */ category?: number
  /** 内容摘要 */ hashMd5?: string
}

/** 用户实体的通用造型（用户卡片、当前登录人展示用） */
export interface IUser {
  /** 昵称 */ nickname: string
  /** 头像地址 */ avatar?: string
  /** 手机号 */ phone?: string
  /** 邮箱 */ email?: string
  /** 真实姓名 */ realName?: string
}

/** 菜单实体的通用造型（路由构建、菜单树用） */
export interface IMenu {
  /** 菜单名称 */ name: string
  /** 菜单路径 */ path: string
  /** 图标 */ icon?: string
  /** 组件地址 */ component?: string
  /** 排序 */ orderNo?: number
  /** 父级 id */ parentId?: number
  /** 子菜单 */ children?: IMenu[] | unknown[]
  /** 绑定权限标识 */ permission?: string
}

/**
 * 动态表单对象的类型。
 *
 * `KFormDialog` 的表单模型是**按元数据生成的**：编译期不知道有哪些键，运行时键由
 * `@KForm` 决定。用 `Record<string, any>` 而不是 `unknown`，是因为页面需要
 * 「按实体读字段」（`form.quantity`、`form as MaterialEntity`）这类操作 ——
 * 用 `unknown` 会把每一次访问都逼成 `as unknown as`，那不是更安全，只是更长。
 */
export type IFormModel = Record<string, any>

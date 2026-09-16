/**
 * # 框架层·模型元数据（装饰器）
 *
 * 业务实体用 `@Model/@Field/@Table/@Form/@Search/@Type` 描述「显示什么、怎么显示」，
 * 组件层（表格/表单/搜索）只读这份元数据渲染 —— 这正是原 `@airpower/web` 的核心机制。
 * 这里用一份**独立的元数据注册表**重新实现，语义与原库对齐：
 *
 * - 装饰器写在属性上，配置里回填 `key`（业务不用手写字段名）；
 * - 子类继承父类的字段配置，子类可覆盖；
 * - 列表类接口（table/form/search）按 `order` **降序**排序，同 order 保持「父类字段在前」的声明序。
 *
 * ⚠️ 元数据挂在**类的原型链**上按类存储，与实例无关；因此 `useDefineForClassFields` 下
 * 声明字段是否被初始化都不影响元数据读取。
 */

import type {
  ConfigOptionalKey,
  IFieldConfig,
  IFormField,
  IModelConfig,
  ISearchField,
  ITableColumn,
  ITransformerConstructor,
} from './types'

interface FieldConfigStore {
  field: Map<string, IFieldConfig>
  table: Map<string, ITableColumn>
  form: Map<string, IFormField>
  search: Map<string, ISearchField>
  type: Map<string, ITransformerConstructor>
  order: string[]
}

const STORE = new WeakMap<object, FieldConfigStore>()

function ensureStore(Class: object): FieldConfigStore {
  let store = STORE.get(Class)
  if (!store) {
    store = {
      field: new Map(),
      table: new Map(),
      form: new Map(),
      search: new Map(),
      type: new Map(),
      order: [],
    }
    STORE.set(Class, store)
  }
  return store
}

function sortByOrder<T extends { order?: number }>(list: T[]): T[] {
  return [...list].sort((a, b) => (b.order || 0) - (a.order || 0))
}

/**
 * 取「该类 + 全部祖先类」的字段元数据。
 *
 * 沿原型链自顶向下合并，保证父类字段先出现（与原库 `Object.keys(new Class())` 的顺序一致），
 * 子类同名字段覆盖父类配置。
 */
function collectChain(Class: object, pick: (store: FieldConfigStore) => Map<string, unknown>): Map<string, unknown> {
  const chain: object[] = []
  let current: object | null = Class
  while (current && current !== Object.prototype) {
    chain.unshift(current)
    current = Object.getPrototypeOf(current) as object | null
  }
  const merged = new Map<string, unknown>()
  for (const node of chain) {
    const store = STORE.get(node)
    if (!store) {
      continue
    }
    for (const [key, value] of pick(store)) {
      merged.set(key, value)
    }
  }
  return merged
}

// ────────────────────────────── 装饰器 ──────────────────────────────

/** 标记类为「模型」，可提供中文名与权限动作 */
export function Model(config: IModelConfig = {}) {
  return (Class: object) => {
    const store = ensureStore(Class)
    store.field.set('__model__', { key: '__model__', label: config.label } as IFieldConfig)
    store.type.set('__model_config__', config as unknown as ITransformerConstructor)
  }
}

/** 标记字段的标题/字典 */
export function Field<K extends number | string>(config: ConfigOptionalKey<IFieldConfig<K>> = {}) {
  return (instance: object, field: string) => {
    const store = ensureStore(instance.constructor)
    store.field.set(field, { ...config, key: field } as IFieldConfig)
    if (!store.order.includes(field)) {
      store.order.push(field)
    }
  }
}

/** 标记该字段可作为表格列 */
export function Table(config: ConfigOptionalKey<ITableColumn> = {}) {
  return (instance: object, field: string) => {
    ensureStore(instance.constructor).table.set(field, { ...config, key: field } as ITableColumn)
  }
}

/** 标记该字段可参与表单 */
export function Form(config: ConfigOptionalKey<IFormField> = {}) {
  return (instance: object, field: string) => {
    ensureStore(instance.constructor).form.set(field, { ...config, key: field } as IFormField)
  }
}

/** 标记该字段可作为搜索条件 */
export function Search(config: ConfigOptionalKey<ISearchField> = {}) {
  return (instance: object, field: string) => {
    ensureStore(instance.constructor).search.set(field, { ...config, key: field } as ISearchField)
  }
}

/** 标记字段的嵌套类型（解析响应时递归实例化） */
export function Type<T>(Class: ITransformerConstructor<T>) {
  return (instance: object, field: string) => {
    ensureStore(instance.constructor).type.set(field, Class as unknown as ITransformerConstructor)
  }
}

// ────────────────────────────── 读取 ──────────────────────────────

/** 模型的配置（`@Model`） */
export function getModelConfig(Class: object): IModelConfig {
  const config = STORE.get(Class)?.type.get('__model_config__')
  return (config as unknown as IModelConfig) || {}
}

/** 模型中文名（缺省用类名） */
export function getModelName(Class: object): string {
  return getModelConfig(Class).label || (Class as { name?: string }).name || '未知模型'
}

/** 指定字段的配置（`@Field`），无则返回 undefined */
export function getFieldConfig(Class: object, field: string): IFieldConfig | undefined {
  return collectChain(Class, store => store.field).get(field) as IFieldConfig | undefined
}

/** 指定字段的标题（缺省用字段名） */
export function getFieldLabel(Class: object, field: string): string {
  return getFieldConfig(Class, field)?.label || field
}

/** 指定字段的枚举字典 */
export function getDictionary(Class: object, field: string): IFieldConfig['dictionary'] | undefined {
  return getFieldConfig(Class, field)?.dictionary
}

/** 指定字段的表格列配置 */
export function getTableConfig(Class: object, field: string): ITableColumn | undefined {
  return collectChain(Class, store => store.table).get(field) as ITableColumn | undefined
}

/** 表格列配置列表（已按 order 降序、剔除 removed，并回填字段标题） */
export function getTableConfigList(Class: object): ITableColumn[] {
  const columns = [...collectChain(Class, store => store.table).values()] as ITableColumn[]
  return sortByOrder(
    columns
      .filter(item => !!item.key && !item.removed)
      .map(item => ({ ...item, label: item.label || getFieldLabel(Class, item.key) })),
  )
}

/** 指定字段的表单配置 */
export function getFormConfig(Class: object, field: string): IFormField | undefined {
  return collectChain(Class, store => store.form).get(field) as IFormField | undefined
}

/** 表单字段配置列表（已按 order 降序、剔除 hide） */
export function getFormConfigList(Class: object): IFormField[] {
  const fields = [...collectChain(Class, store => store.form).values()] as IFormField[]
  return sortByOrder(
    fields
      .filter(item => !!item.key && !item.hide)
      .map(item => ({
        ...item,
        label: item.label || getFieldLabel(Class, item.key),
        dictionary: item.dictionary || getDictionary(Class, item.key),
      })),
  )
}

/** 指定字段的搜索配置 */
export function getSearchConfig(Class: object, field: string): ISearchField | undefined {
  return collectChain(Class, store => store.search).get(field) as ISearchField | undefined
}

/** 搜索字段配置列表（已按 order 降序、剔除 hide） */
export function getSearchConfigList(Class: object): ISearchField[] {
  const fields = [...collectChain(Class, store => store.search).values()] as ISearchField[]
  return sortByOrder(
    fields
      .filter(item => !!item.key && !item.hide)
      .map(item => ({
        ...item,
        label: item.label || getFieldLabel(Class, item.key),
        dictionary: item.dictionary || getDictionary(Class, item.key),
      })),
  )
}

/** 嵌套字段的类型（`@Type`） */
export function getTypeConfig(Class: object, field: string): ITransformerConstructor | undefined {
  return collectChain(Class, store => store.type).get(field) as ITransformerConstructor | undefined
}

/** 类上所有被装饰过的字段名（父类在前、声明序） */
export function getDecoratedFieldList(Class: object): string[] {
  const chain: object[] = []
  let current: object | null = Class
  while (current && current !== Object.prototype) {
    chain.unshift(current)
    current = Object.getPrototypeOf(current) as object | null
  }
  const keys: string[] = []
  for (const node of chain) {
    for (const key of STORE.get(node)?.order || []) {
      if (key !== '__model__' && !keys.includes(key)) {
        keys.push(key)
      }
    }
  }
  return keys
}

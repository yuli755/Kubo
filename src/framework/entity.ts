/**
 * # 框架层·实体基类与（反）序列化
 *
 * 对齐原 `@airpower/web` 的 `RootModel` / `RootEntity` / `Transformer` 三件套：
 *
 * - `copy()` —— 深拷贝一份（表单编辑的就是副本，取消不影响列表行对象）；
 * - `exclude('filter')` —— 请求体里剔除某些字段（QueryRequestPage 用）；
 * - `RootModel.parse(json, Class)` —— 按 `@Type` 元数据递归实例化嵌套实体；
 * - `RootModel.newInstance(Class)` —— 按类创建一个实例（服务内部用）。
 */

import type { IJson, ITransformerConstructor } from './types'
import { looksMasked } from './mask'
import { getTypeConfig } from './metadata'

/** 判断是否「可继续下钻」的纯对象 */
function isPlainObject(value: unknown): value is IJson {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

/** 深拷贝：实体数组/嵌套对象/普通值都能安全复制 */
function deepCopy<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map(item => deepCopy(item)) as unknown as T
  }
  if (isPlainObject(value)) {
    const source = value as IJson
    const copyFn = (source as { copy?: () => unknown }).copy
    if (typeof copyFn === 'function') {
      return copyFn.call(source) as T
    }
    const result: IJson = {}
    for (const [key, item] of Object.entries(source)) {
      result[key] = deepCopy(item)
    }
    return result as unknown as T
  }
  return value
}

/**
 * # 模型基类
 *
 * 提供拷贝与（反）序列化能力，是所有实体/服务的父类。
 */
export class RootModel {
  /** 深拷贝当前实例（数组与嵌套实体同样复制） */
  copy<T extends this>(): T {
    const result = Object.create(Object.getPrototypeOf(this)) as Record<string, unknown>
    for (const [key, value] of Object.entries(this as unknown as Record<string, unknown>)) {
      result[key] = deepCopy(value)
    }
    return result as unknown as T
  }

  /**
   * 剔除字段（返回自身，便于链式调用）。
   * 用于「请求体里不想带某个字段」的场景，例如分页请求不带 filter。
   */
  exclude(...fields: string[]): this {
    for (const field of fields) {
      delete (this as unknown as Record<string, unknown>)[field]
    }
    return this
  }

  /** 按类创建实例（服务的 `entityClass` 由此获得默认值） */
  static newInstance<T>(this: ITransformerConstructor<T> | void, Class: ITransformerConstructor<T>): T {
    return new Class()
  }

  /** 按 `@Type` 元数据把 JSON 填充为实体实例 */
  static parse<T extends object>(json: unknown, Class: ITransformerConstructor<T>): T {
    return fill(new Class(), json)
  }

  /**
   * 从 JSON 建实例（原库写法：`Entity.fromJson(json)`）。
   *
   * 与 `parse(json, Class)` 是同一件事，两种写法都在用 —— 保留是因为存量代码里
   * `XxxEntity.fromJson(...)` 更常见，删掉它会把调用点改成传一长串参数，收益为零。
   */
  static fromJson<T extends object>(this: ITransformerConstructor<T> | void, json: unknown, Class?: ITransformerConstructor<T>): T {
    const Target = (Class ?? (this as unknown as ITransformerConstructor<T>))
    return fill(new Target(), json)
  }

  /** 从 JSON 数组建实例数组（原库写法） */
  static fromJsonArray<T extends object>(this: ITransformerConstructor<T> | void, json: unknown, Class?: ITransformerConstructor<T>): T[] {
    const Target = (Class ?? (this as unknown as ITransformerConstructor<T>))
    return Array.isArray(json) ? json.map(item => fill(new Target(), item)) : []
  }
}

/** 把 JSON 填充进已有实例（递归处理 `@Type` 标注的嵌套实体，数组也会逐项实例化） */
export function fill<T extends object>(instance: T, json: unknown): T {
  if (!isPlainObject(json)) {
    return instance
  }
  const target = instance as unknown as Record<string, unknown>
  for (const [key, value] of Object.entries(json)) {
    const Child = getTypeConfig(instance.constructor, key)
    if (value === null || value === undefined) {
      target[key] = value ?? undefined
      continue
    }
    if (Child) {
      if (Array.isArray(value)) {
        target[key] = value.map(item => fill(new Child() as object, item as object))
      }
      else {
        target[key] = fill(new Child() as object, value as object)
      }
      continue
    }
    target[key] = value
  }
  return instance
}

/**
 * 反向：去掉方法，只保留可序列化字段。
 *
 * 嵌套层级（depth ≥ 1）里的<b>脱敏占位值</b>（`h:xxxx` / `1****0`）一律剔除：
 * 明细里嵌套的物料/仓库等 `@ManyToOne` 引用，后端只按 id 绑定、从不回写其余字段；
 * 若把掩码原样带回，后端 `SensitiveFieldDeserializer` 遇到数值列会当场拒收
 * 整条单据（新增/更新都过不了反序列化），字符串列噪声则交给
 * `SensitiveWriteProtection` 恢复链路。掩码携零信息，剔除零损失。
 * 顶层字段（depth 0，本次要写的本体）不动 —— 顶层掩码数值的拒收是后端
 * 刻意保留的安全姿态，且各页面对自有受限字段已有 `blockedSubmit`/提示条口径。
 */
export function toJson(instance: unknown): IJson {
  return toJsonAt(instance, 0)
}

function toJsonAt(instance: unknown, depth: number): IJson {
  const result: IJson = {}
  if (!isPlainObject(instance)) {
    return result
  }
  for (const [key, value] of Object.entries(instance)) {
    if (typeof value === 'function') {
      continue
    }
    if (depth > 0 && looksMasked(value)) {
      continue
    }
    if (Array.isArray(value)) {
      result[key] = value.map(item => (isPlainObject(item) ? toJsonAt(item, depth + 1) : item))
    }
    else if (value instanceof Date) {
      result[key] = value
    }
    else if (isPlainObject(value)) {
      result[key] = toJsonAt(value, depth + 1)
    }
    else {
      result[key] = value
    }
  }
  return result
}

/** 与原库同名的静态工具（业务里有 `Transformer.parse` 这类写法） */
export class Transformer {
  static parse<T extends object>(json: unknown, Class: ITransformerConstructor<T>): T {
    return RootModel.parse(json, Class)
  }

  static fill<T extends object>(instance: T, json: unknown): T {
    return fill(instance, json)
  }

  static toJson(instance: unknown): IJson {
    return toJson(instance)
  }
}

/**
 * # 数据库实体基类
 *
 * 所有业务实体的父类：`id` 是唯一主键，`isDisabled` 用于启禁用（服务端软删/停用语义）。
 */
export class RootEntity extends RootModel {
  /** 主键 */
  id!: number

  /** 是否已禁用 */
  isDisabled!: boolean

  constructor(id?: number) {
    super()
    if (id !== undefined) {
      this.id = id
    }
  }

  /** 设置禁用状态（链式） */
  setDisable(isDisabled?: boolean): this {
    this.isDisabled = !!isDisabled
    return this
  }

  /** 只保留主键与名称（选择器回填时避免携带整行数据） */
  copyOnlyId(): this {
    const copy = new (this.constructor as ITransformerConstructor<this>)()
    copy.id = this.id
    return copy
  }
}

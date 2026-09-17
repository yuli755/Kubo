/**
 * # 框架层·表单校验
 *
 * - `WebValidator.show('请输入名称').ifEmpty()` —— 链式构造一条 Element Plus 规则；
 * - `WebValidator.create({ name: [WebValidator.show('').ifEmpty()] })` —— 手写规则集；
 * - `WebValidator.createRules(Entity, extra)` —— 由 `@Form` 元数据**自动**生成规则
 *   （`requiredString` / `requiredNumber` / `requiredPayload` / `minLength` / `min` / `max` …），
 *   这是编辑弹窗里「表单字段与校验不脱节」的关键。
 */

import type { ITransformerConstructor } from './types'
import { getFormConfigList } from './metadata'

export type WebValidatorTrigger = 'blur' | 'change'

/** Element Plus 规则形状（只用到需要的字段） */
export interface WebValidateRule {
  required?: boolean
  message?: string
  trigger?: WebValidatorTrigger | WebValidatorTrigger[]
  type?: 'string' | 'number' | 'array' | 'date' | 'email'
  min?: number
  max?: number
  len?: number
  validator?: (rule: unknown, value: unknown, callback: (error?: Error) => void) => void
}

/** 规则集合 */
export type WebValidateRuleSet<E extends object = object> = { [K in keyof E | string]?: WebValidateRule[] }

/** 常用格式（与原 `ValidateUtil` 语义一致，登录页也与这里同源） */
export const ValidateUtil = {
  isEmail(value: string): boolean {
    return /^[\w.%+-]+@[\w.-]+\.[a-z]{2,}$/i.test(value)
  },
  isMobilePhone(value: string): boolean {
    return /^(?:\+\d{1,3})?1[3-9]\d{9}$/.test(value)
  },
  isTelephone(value: string): boolean {
    return /^0\d{2,3}-?\d{7,8}$/.test(value)
  },
  isNaturalInteger(value: unknown): boolean {
    return /^\d+$/.test(String(value))
  },
  isNaturalNumber(value: unknown): boolean {
    return /^\d+(?:\.\d+)?$/.test(String(value))
  },
  isNumber(value: unknown): boolean {
    return /^-?\d+(?:\.\d+)?$/.test(String(value))
  },
  isInteger(value: unknown): boolean {
    return /^-?\d+$/.test(String(value))
  },
}

export class WebValidator {
  /** 提示文案 */
  message = ''

  /** 展示类型 */
  type: WebValidateRule['type']

  /** 是否必填 */
  required = false

  /** 触发时机 */
  trigger: WebValidatorTrigger = 'blur'

  /** 自定义校验函数 */
  validator?: WebValidateRule['validator']

  /** 用提示文案创建一条规则 */
  static show(message = ''): WebValidator {
    return new WebValidator().show(message)
  }

  /** 手写规则集的快捷入口（泛型参数与原库一致，仅为写法兼容，不做运行时约束） */
  static create<E extends object = object>(customRules: WebValidateRuleSet = {}): WebValidateRuleSet<E> {
    return customRules
  }

  /** 电话/座机格式 */
  ifNotPhone(): this {
    this.validator = (_rule, value, callback) => {
      if (!value || ValidateUtil.isMobilePhone(String(value)) || ValidateUtil.isTelephone(String(value))) {
        callback()
        return
      }
      callback(new Error(this.message || '联系电话格式不正确'))
    }
    return this
  }

  static getValidator(configValue: unknown): WebValidator {
    return new WebValidator().show(typeof configValue === 'string' ? configValue : '')
  }

  /**
   * 由 `@Form` 元数据生成整表单的校验规则。
   *
   * @param entityClass 实体类
   * @param customRules 额外/覆盖的规则（同名字段以传入的为准）
   */
  static createRules<E extends object>(
    entityClass: ITransformerConstructor<E>,
    customRules: WebValidateRuleSet<E> = {},
  ): WebValidateRuleSet<E> {
    const rules = { ...customRules } as Record<string, WebValidateRule[]>
    for (const config of getFormConfigList(entityClass)) {
      const message = config.label || config.key
      const list = rules[config.key] ? [...rules[config.key]] : []
      if (config.requiredString) {
        list.push(WebValidator.show(`请输入${message}`).ifEmpty())
      }
      if (config.requiredNumber) {
        list.push(WebValidator.show(`请输入${message}`).toNumber().ifEmpty())
      }
      if (config.requiredPayload) {
        list.push(WebValidator.show(`请选择${message}`).ifPayloadEmpty())
      }
      if (config.minLength) {
        list.push(WebValidator.show(`至少输入 ${config.minLength} 个字符`).ifLengthLessThan(config.minLength))
      }
      if (config.maxLength) {
        list.push(WebValidator.show(`最多输入 ${config.maxLength} 个字符`).ifLengthGreaterThan(config.maxLength))
      }
      if (config.number) {
        if (config.min !== undefined) {
          list.push(WebValidator.show(`不能小于 ${config.min}`).toNumber().ifLessThan(config.min))
        }
        if (config.max !== undefined) {
          list.push(WebValidator.show(`不能大于 ${config.max}`).toNumber().ifGreaterThan(config.max))
        }
      }
      if (config.email) {
        list.push(WebValidator.show('邮箱格式不正确').ifNotEmail())
      }
      if (config.mobilePhone) {
        list.push(WebValidator.show('手机号格式不正确').ifNotMobilePhone())
      }
      if (list.length) {
        rules[config.key] = list
      }
    }
    return rules
  }

  // ────────────────────────── 链式构造 ──────────────────────────

  show(message: string): this {
    this.message = message
    return this
  }

  toString(): this {
    this.type = 'string'
    return this
  }

  toNumber(): this {
    this.type = 'number'
    return this
  }

  toArray(): this {
    this.type = 'array'
    return this
  }

  toDate(): this {
    this.type = 'date'
    return this
  }

  ifEmpty(): this {
    this.required = true
    return this
  }

  ifPayloadEmpty(): this {
    this.required = true
    this.trigger = 'change'
    this.validator = (_rule, value, callback) => {
      if (value === null || value === undefined || value === '' || (Array.isArray(value) && !value.length)) {
        callback(new Error(this.message || '不能为空'))
        return
      }
      callback()
    }
    return this
  }

  /**
   * 内容包含某段文本时判错。
   *
   * 来自真实业务规则：角色名不允许带「管理员」三个字（避免建出一个名字像超管的普通角色，
   * 出事时无法从名字上分辨）。原库同名方法，迁移时补进框架层 ——
   * 页面通过 `KFormDialog` 的 `extraRules` 传它，规则仍属于页面。
   */
  ifContain(text: string): this {
    this.validator = (_rule, value, callback) => {
      if (value && String(value).includes(text)) {
        callback(new Error(this.message || `不能包含 ${text}`))
        return
      }
      callback()
    }
    return this
  }

  ifLengthLessThan(min: number): this {
    this.validator = (_rule, value, callback) => {
      if (value && String(value).length < min) {
        callback(new Error(this.message || `最少输入 ${min} 个字符`))
        return
      }
      callback()
    }
    return this
  }

  ifLengthGreaterThan(max: number): this {
    this.validator = (_rule, value, callback) => {
      if (value && String(value).length > max) {
        callback(new Error(this.message || `最多输入 ${max} 个字符`))
        return
      }
      callback()
    }
    return this
  }

  ifLessThan(min: number): this {
    this.toNumber()
    this.validator = (_rule, value, callback) => {
      if (value !== undefined && value !== null && value !== '' && Number(value) < min) {
        callback(new Error(this.message || `不能小于 ${min}`))
        return
      }
      callback()
    }
    return this
  }

  ifGreaterThan(max: number): this {
    this.toNumber()
    this.validator = (_rule, value, callback) => {
      if (value !== undefined && value !== null && value !== '' && Number(value) > max) {
        callback(new Error(this.message || `不能大于 ${max}`))
        return
      }
      callback()
    }
    return this
  }

  ifNotEmail(): this {
    this.validator = (_rule, value, callback) => {
      if (!value || ValidateUtil.isEmail(String(value))) {
        callback()
        return
      }
      callback(new Error(this.message || '邮箱格式不正确'))
    }
    return this
  }

  ifNotMobilePhone(): this {
    this.validator = (_rule, value, callback) => {
      if (!value || ValidateUtil.isMobilePhone(String(value))) {
        callback()
        return
      }
      callback(new Error(this.message || '手机号格式不正确'))
    }
    return this
  }

  ifNotNumber(): this {
    this.validator = (_rule, value, callback) => {
      if (!value || ValidateUtil.isNumber(value)) {
        callback()
        return
      }
      callback(new Error(this.message || '请输入数字'))
    }
    return this
  }

  ifNotInteger(): this {
    this.validator = (_rule, value, callback) => {
      if (!value || ValidateUtil.isInteger(value)) {
        callback()
        return
      }
      callback(new Error(this.message || '请输入整数'))
    }
    return this
  }

  ifTest(regx: RegExp): this {
    this.validator = (_rule, value, callback) => {
      if (value && regx.test(String(value))) {
        callback(new Error(this.message || '格式不正确'))
        return
      }
      callback()
    }
    return this
  }

  ifNotTest(regx: RegExp): this {
    this.validator = (_rule, value, callback) => {
      if (value && !regx.test(String(value))) {
        callback(new Error(this.message || '格式不正确'))
        return
      }
      callback()
    }
    return this
  }

  setCustomValidator(validator: WebValidateRule['validator']): this {
    this.validator = validator
    return this
  }

  /** 转换成 Element Plus 认得的规则对象 */
  build(): WebValidateRule {
    return {
      required: this.required,
      message: this.message,
      trigger: this.trigger,
      type: this.type,
      validator: this.validator,
    }
  }
}

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
import { isMobilePhone as isMobilePhoneCanonical } from '@/utils/validators'
import { FeedbackUtil } from './feedback'
import { getFieldLabel, getFormConfigList } from './metadata'

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
    // 委托 utils/validators 的规范实现（国家码 1~4 位）。此处曾经自带一份
    // \d{1,3} 的克隆正则 —— 同一语义两处定义已经漂移过一次，留一份就好。
    return isMobilePhoneCanonical(value)
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

  /**
   * 只允许字母与数字（编码规则的前缀/模板用）。
   *
   * 原库同名方法：`WebValidator.show('只允许字母/数字').ifNotOnlyNumberAndLetter()`。
   * 命名确实别扭（否定式 + 内容描述），但它是**存量业务规则的名字**，
   * 改名会让 code review 时对不上旧代码，所以照抄。
   */
  ifNotOnlyNumberAndLetter(): this {
    return this.ifNotTest(/^[A-Z0-9]*$/i)
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

/** ElForm 实例上本工具用到的最小形状（避免为了一个类型把 element-plus 拖进框架层） */
export interface WebFormRefLike {
  validate: () => Promise<boolean>
  scrollToField?: (prop: string) => void
}

/**
 * 提交前的统一校验反馈（R52 · 人性化提示补齐）。
 *
 * 存量的手写弹窗（各明细编辑器）都从 `@KForm` 元数据生成了 `rules`，却从来没人在
 * 「确定」时调 `validate()` —— 空值/漏选被静默收进上一层，用户完全不知道哪里没填。
 * 这里把「校 → 不过就把第一个出错字段滚进视野 + toast 点名」收敛成一个函数，
 * 各编辑器一行接入，文案也保证一致（「请先完善必填项：物料」）。
 *
 * @param form ElForm 实例（未挂载时视为通过，由调用方的 form 空值守卫兜底）
 * @param entityClass 出错的 prop 用它反查 `@Field` 中文标题，报「物料」而不是「material」
 * @returns 是否通过校验
 */
export async function validateFormWithFeedback(
  form: WebFormRefLike | undefined,
  entityClass: object,
): Promise<boolean> {
  if (!form) {
    return true
  }
  let invalid: Record<string, unknown> | null = null
  try {
    await form.validate()
  }
  catch (error) {
    // element-plus 把「不通过的字段表」整体 reject 出来（键 = prop）；
    // 不是对象的情况（少数异步校验器抛 Error）也要拦住，宁可多提示一次
    invalid = (error && typeof error === 'object' ? error : {}) as Record<string, unknown>
  }
  if (!invalid) {
    return true
  }
  const keys = Object.keys(invalid)
  FeedbackUtil.toastWarning(keys.length ? `请先完善必填项：${getFieldLabel(entityClass, keys[0])}` : '请先完善必填项')
  if (keys.length) {
    form.scrollToField?.(keys[0])
  }
  return false
}

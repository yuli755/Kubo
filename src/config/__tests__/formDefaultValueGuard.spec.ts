import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # `@Form({ defaultValue })` 生效守卫（N-14）
 *
 * ## 为什么需要这条守卫（真机栽过一次：N-14）
 * `KFormDialog.resetForm()` 曾在新增时只读「类实例属性」：
 *
 * ```ts
 * next[field.key] = instance[field.key] ?? ''
 * ```
 *
 * 而本工程实体的字段几乎都用 `field!: number` 声明（无初始化器），
 * `new Ctor()[field.key]` 恒为 `undefined` —— 于是 `@Form({ defaultValue })`
 * 元数据里声明的默认值（合同类型=其他、开始/终止时间=now/+1年、物料类型=自产品、
 * 使用类型=消耗品、价格=0 …）在「新增」时全部落空。真机实测（合同新增）：
 * 合同类型/开始时间/终止时间为空，保存被 requiredNumber 校验拦截
 * （请输入合同类型 / 请输入开始时间 / 请输入终止时间），必须手工逐项重选。
 *
 * 教训（同 defaultCheckedKeysGuard 的复读）：**实体元数据是事实来源，表单初始化
 * 必须落到它上面**；只读类属性会把"声明过的默认值"悄悄丢掉，用户看到的就是
 * 表单空了一截、必填校验莫名拦截。
 */

const FILE = path.resolve(
  process.cwd(),
  'src/component/framework/KFormDialog.vue',
)
const text = readFileSync(FILE, 'utf-8')

/** 从 `<script setup>` 里抠出 `resetForm` 函数体。 */
function resetFormBody(): string {
  const m = /function resetForm\(\)\s*\{(?<body>[\s\S]*?)\n\}/.exec(text)
  if (!m?.groups?.body)
    throw new Error(`KFormDialog.vue 里找不到 resetForm() 的函数体，守卫失去锚点`)
  return m.groups.body
}

describe('新增默认值守卫（KFormDialog）', () => {
  it('resetForm() 必须回落到元数据默认值 field.defaultValue', () => {
    const body = resetFormBody()
    expect(body).toContain('field.defaultValue')
  })

  it('优先级必须是 实例值 > 元数据默认值 > 空串（不允许只读实例属性）', () => {
    const body = resetFormBody()
    // 实例值在前、defaultValue 中继、空串兜底；禁止退回旧的 `instance[field.key] ?? ''` 写法
    expect(body).toMatch(/instance\[field\.key\]\s*\?\?\s*field\.defaultValue\s*\?\?\s*'/)
  })

  it('锚点仍在：resetForm 通过 fields 遍历生成新增表单（防止函数被改名后守卫静默失效）', () => {
    const body = resetFormBody()
    expect(body).toContain('fields.value')
    expect(text).toMatch(/function resetForm\(\)/)
  })
})

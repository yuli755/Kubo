import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { FeedbackUtil } from '@/framework'
import { validateFormWithFeedback } from '@/framework/validate'
import { InputDetailEntity } from '@/model/wms/input/InputDetailEntity'

/**
 * # R52 人性化校验反馈守卫
 *
 * 用户原话（goal rev-6）：「一旦用户字段校验或密码输入错误，系统不会提示用户，
 * 哪里操作失误」。审计坐实了三类静默：
 *
 * 1. **五个手写明细编辑器**（wms input/output/move + channel purchase/sale）
 *    都从 `@KForm` 元数据生成了 `rules`，但 `onConfirm()` 从来没人调 `validate()`
 *    —— KDialog 默认 `closeOnConfirm=true`，点「确定」先关窗再 emit，
 *    空值明细行被静默收进上一层单据，零反馈。
 * 2. **KFormDialog.onSubmit()** 拿到 `valid === false` 直接 `return`，
 *    行内红字虽然渲染了，但视口外的出错项用户根本看不到（表单可滚动）。
 * 3. **ModifyPassword.vue** 表单只有 `:model`，没有 `:rules` / `:prop`，
 *    且按钮 `:disabled="!canSubmit()"` —— 用户看到的是「按钮点不动，不知道为什么」。
 *
 * 修复后行为收敛在框架层 `validateFormWithFeedback()`（toast 点名 + scrollToField），
 * 本文件把「每个接入点确实接上了」钉死：源码级守卫（改动即红）+ helper 行为级锁。
 */

const ROOT = process.cwd()

function source(relative: string): string {
  return readFileSync(join(ROOT, relative), 'utf-8')
}

const DETAIL_EDITORS: Array<[string, string]> = [
  ['入库明细', 'src/view/console/wms/input/component/detailEditor.vue'],
  ['出库明细', 'src/view/console/wms/output/component/detailEditor.vue'],
  ['移库明细', 'src/view/console/wms/move/component/detailEditor.vue'],
  ['采购明细', 'src/view/console/channel/purchase/component/detailEditor.vue'],
  ['销售明细', 'src/view/console/channel/sale/component/detailEditor.vue'],
]

describe('明细编辑器确定前必须校验并反馈（R52）', () => {
  it.each(DETAIL_EDITORS)('%s：onConfirm 走 validateFormWithFeedback 且不提前关窗', (_name, file) => {
    const text = source(file)
    // 校验入口：框架统一 helper，禁止各页自拼 toast（复发即红）
    expect(text).toContain('validateFormWithFeedback(')
    // ElForm 必须真的挂上 ref，否则 helper 拿到 undefined 会直接放行
    expect(text).toContain('ref="formRef"')
    // KDialog 默认 confirm 时先关窗，异步校验必须在关窗前拦住
    expect(text).toContain(':close-on-confirm="false"')
  })

  it('销售物料项带 prop（缺了它 EP 无法把错误归属到该字段）', () => {
    const text = source('src/view/console/channel/sale/component/detailEditor.vue')
    expect(text).toContain('prop="material"')
  })

  it('框架表单弹窗（KFormDialog）校验不过时点名第一个出错字段并滚过去', () => {
    const text = source('src/component/framework/KFormDialog.vue')
    expect(text).toContain('请先完善必填项')
    expect(text).toContain('formRef.value?.scrollToField(key)')
  })

  it('改密面板（ModifyPassword）有 rules + 三个 prop，且不再用 disabled 把按钮变哑', () => {
    const text = source('src/component/user/ModifyPassword.vue')
    expect(text).toContain(':rules="rules"')
    expect(text).toContain('prop="oldPassword"')
    expect(text).toContain('prop="password"')
    expect(text).toContain('prop="confirmPassword"')
    expect(text).not.toContain(':disabled="!canSubmit()"')
    expect(text).toContain('两次密码不一致')
  })
})

describe('validateFormWithFeedback 行为契约（R52）', () => {
  const warn = vi.spyOn(FeedbackUtil, 'toastWarning')

  beforeEach(() => {
    warn.mockReset()
  })

  it('校验通过：不 toast、不滚动', async () => {
    const scrollToField = vi.fn()
    const ok = await validateFormWithFeedback(
      { validate: () => Promise.resolve(true), scrollToField },
      InputDetailEntity,
    )
    expect(ok).toBe(true)
    expect(warn).not.toHaveBeenCalled()
    expect(scrollToField).not.toHaveBeenCalled()
  })

  it('reject 的不是字段表（如 Error）：兜底通用文案，但仍必须出声', async () => {
    const scrollToField = vi.fn()
    const ok = await validateFormWithFeedback(
      {
        validate: () => Promise.reject(new Error('fail')),
        scrollToField,
      },
      InputDetailEntity,
    )
    expect(ok).toBe(false)
    // Error 上没有可点名的字段键，兜底为通用文案，但绝不允许静默
    expect(warn).toHaveBeenCalledWith('请先完善必填项')
    expect(scrollToField).not.toHaveBeenCalled()
  })

  it('reject { material } 字段表：报「请先完善必填项：物料」（@Field 中文标题）', async () => {
    const scrollToField = vi.fn()
    const ok = await validateFormWithFeedback(
      {
        // element-plus 的真实 reject 就是一张普通对象表（键 = prop），这里刻意复刻它，
        // 而不是造一个 Error —— 被测代码正是按这张表取第一个出错字段的
        // eslint-disable-next-line prefer-promise-reject-errors
        validate: () => Promise.reject({ material: [{ message: '请选择物料' }] }),
        scrollToField,
      },
      InputDetailEntity,
    )
    expect(ok).toBe(false)
    expect(warn).toHaveBeenCalledWith('请先完善必填项：物料')
    expect(scrollToField).toHaveBeenCalledWith('material')
  })

  it('form 未挂载时放行（由调用方的 form 空值守卫兜底，不制造假报错）', async () => {
    expect(await validateFormWithFeedback(undefined, InputDetailEntity)).toBe(true)
    expect(warn).not.toHaveBeenCalled()
  })
})

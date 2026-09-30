import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # `:default-checked-keys` 副作用守卫
 *
 * ## 为什么需要这条守卫（真机栽过一次：N-13）
 * `src/view/console/personnel/role/component/menuEditor.vue` 曾把计数副作用写进
 * `checkedIds()`：
 *
 * ```ts
 * function checkedIds(): number[] {
 *   const ids = (props.role?.menuList ?? []).map(item => item.id)
 *   checkedCount.value = ids.length   // ← 副作用
 *   return ids
 * }
 * ```
 *
 * 而 `checkedIds()` 在模板里被 `:default-checked-keys="checkedIds()"` 绑定 —— **每次渲染
 * 都会执行**。于是「清空 / 全选 / 手工勾选」之后 `onCheckChange()` 更新的计数器，
 * 会在下一次渲染被钉死回 `props.role.menuList.length`。真机实测：树勾选状态正常
 * 变化（34↔0↔34），但「已选 N 项」tag 纹丝不动，且保存的授权结果不受影响（EP 只在
 * 挂载时应用 `default-checked-keys`，保存走 `getCheckedNodes()`）—— 一个典型的
 * 「树正常、计数器不同步」的静默前端缺陷。
 *
 * 教训（同 dialogWiring.spec.ts 的复读）：**凡是绑定进模板的 getter 函数，必须无副作用**；
 * 模板渲染频率不由你控制，副作用会被无限放大。
 */

const FILE = path.resolve(
  process.cwd(),
  'src/view/console/personnel/role/component/menuEditor.vue',
)
const text = readFileSync(FILE, 'utf-8')

/** 从 `<script setup>` 里抠出 `checkedIds` 函数体（到下一个同缩进 `function` 或 `}` 收束）。 */
function checkedIdsBody(): string {
  const m = /function checkedIds\(\)\s*:\s*(?:number\[\]|Array<number>)\s*\{(?<body>[\s\S]*?)\n\}/.exec(text)
  if (!m?.groups?.body)
    throw new Error(`menuEditor.vue 里找不到 checkedIds() 的函数体，守卫失去锚点`)
  return m.groups.body
}

describe('default-checked-keys 副作用守卫', () => {
  it('checkedIds() 的函数体里不能给 checkedCount 赋值', () => {
    const body = checkedIdsBody()
    expect(body).not.toMatch(/checkedCount\.value\s*=/)
  })

  it('checkedIds() 必须是无副作用、直接返回 role.menuList 的 id 映射', () => {
    const body = checkedIdsBody()
    // 应是一条 return 语句（哪怕折行），不允许再有别的语句
    const nonBlank = body
      .split('\n')
      .map(s => s.trim())
      .filter(Boolean)
    expect(nonBlank).toHaveLength(1)
    expect(nonBlank[0]).toMatch(/^return\b/)
    expect(nonBlank[0]).toContain('props.role?.menuList')
    expect(nonBlank[0]).toContain('.map')
  })

  it('模板里 checkedIds 必须仍被 :default-checked-keys 引用（锚点仍有效）', () => {
    expect(text).toMatch(/:default-checked-keys="checkedIds\(\)"/)
  })
})

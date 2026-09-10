import { describe, expect, it } from 'vitest'
import { directives, vMask, vPermission } from '@/directives'

/**
 * 自定义指令统一入口契约测试。
 *
 * main.ts 通过 `Object.entries(directives)` 循环注册，因此这里锁死
 * 「入口 map 的键名与指令实现一一对应」，防止新增/改名指令后注册表失配。
 */
describe('directives · 统一入口', () => {
  it('暴露 mask / permission 两个指令', () => {
    expect(Object.keys(directives).sort()).toEqual(['mask', 'permission'])
    expect(directives.mask).toBe(vMask)
    expect(directives.permission).toBe(vPermission)
  })
})

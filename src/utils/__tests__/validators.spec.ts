import { describe, expect, it } from 'vitest'
import { isEmail, isMobilePhone, isNaturalNumber } from '@/utils/validators'

/**
 * `validators` 复刻 airpower `ValidateUtil` 三方法的契约测试。
 *
 * 锁的是登录页依赖的判定语义。其中 `isNaturalNumber` 有一处**有意的笔误修正**：
 * airpower 原实现 `/^\d+((.)\d+)?$/` 的 `(.)` 是未转义的点号（匹配任意字符，疑笔误），
 * 复刻时修正为 `\.` 小数点，符合函数名「自然数/小数」语义。此行为变化需在本机登录页
 * 回归时确认小数输入场景。
 */
describe('validators · airpower ValidateUtil 复刻', () => {
  describe('isEmail', () => {
    it('接受常规邮箱', () => {
      expect(isEmail('a@b.com')).toBe(true)
      expect(isEmail('user@example.com')).toBe(true)
    })

    it('接受大小写混合（i 标志）', () => {
      expect(isEmail('User@Example.COM')).toBe(true)
    })

    it('接受多段域名与点分隔的本地名', () => {
      expect(isEmail('user.name@sub.example.com')).toBe(true)
    })

    it('拒绝无点域名、空本地名、含空格', () => {
      expect(isEmail('a@b')).toBe(false)
      expect(isEmail('@b.com')).toBe(false)
      expect(isEmail('a b@c.com')).toBe(false)
      expect(isEmail('a@.com')).toBe(false)
    })
  })

  describe('isMobilePhone', () => {
    it('接受 +国家码 手机号', () => {
      expect(isMobilePhone('+8613800138000')).toBe(true)
    })

    it('接受无国家码（+ 前缀必填）', () => {
      expect(isMobilePhone('+13800138000')).toBe(true)
    })

    it('拒绝缺 + 前缀', () => {
      expect(isMobilePhone('13800138000')).toBe(false)
    })

    it('拒绝第二段非 3-9', () => {
      expect(isMobilePhone('+8612800138000')).toBe(false)
    })
  })

  describe('isNaturalNumber', () => {
    it('接受整数与小数', () => {
      expect(isNaturalNumber('123')).toBe(true)
      expect(isNaturalNumber('0')).toBe(true)
      expect(isNaturalNumber('123.45')).toBe(true)
    })

    it('拒绝非法输入', () => {
      expect(isNaturalNumber('abc')).toBe(false)
      expect(isNaturalNumber('1.2.3')).toBe(false)
      expect(isNaturalNumber('.5')).toBe(false)
      expect(isNaturalNumber('1.')).toBe(false)
      expect(isNaturalNumber('-1')).toBe(false)
    })
  })
})

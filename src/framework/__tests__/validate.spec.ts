import { describe, expect, it } from 'vitest'
import { ValidateUtil } from '@/framework/validate'

/**
 * `ValidateUtil.isMobilePhone` 契约测试（第 35 轮审计 R-39）。
 *
 * 这个判断原本在 framework/validate.ts 里有一份**克隆**，且已漂移：
 * 规范版 `src/utils/validators.ts` 是 `^(?:\+\d{1,4})?1[3-9]\d{9}$`，
 * 克隆版的国家码是 `\d{1,3}` —— 于是 `+6513…`（4 位国家码，新加坡）在
 * 表单校验里合法、在 ifMobilePhone 里非法，同一份数据两个答案。
 * 现在 validate.ts 委托给唯一权威实现，这里锁委托关系不再漂移。
 */
describe('校验工具 ValidateUtil · isMobilePhone 委托到 validators 唯一权威实现', () => {
  it('裸号 11 位：1[3-9] 开头才算手机号', () => {
    expect(ValidateUtil.isMobilePhone('13812348888')).toBe(true)
    expect(ValidateUtil.isMobilePhone('12812348888')).toBe(false)
    expect(ValidateUtil.isMobilePhone('1381234888')).toBe(false)
  })

  it('带国家码时按规范版接受 1~4 位（克隆版只到 3 位，正是漂移点）', () => {
    expect(ValidateUtil.isMobilePhone('+6513812348888')).toBe(true)
    expect(ValidateUtil.isMobilePhone('+8613812348888')).toBe(true)
    expect(ValidateUtil.isMobilePhone('+1234513812348888')).toBe(false)
  })

  it('不接受纯座机号与空值', () => {
    expect(ValidateUtil.isMobilePhone('01012345678')).toBe(false)
    expect(ValidateUtil.isMobilePhone('')).toBe(false)
  })

  it('座机号仍由 isTelephone 判定（两者分工，不要拿手机号正则去兜座机）', () => {
    expect(ValidateUtil.isTelephone('010-12345678')).toBe(true)
    expect(ValidateUtil.isTelephone('13812348888')).toBe(false)
  })
})

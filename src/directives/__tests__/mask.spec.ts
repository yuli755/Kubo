// @vitest-environment jsdom
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, withDirectives } from 'vue'

/**
 * `v-mask` / `useMaskPolicy` 契约测试。
 *
 * 这里锁的是"前端不再自己决定遮多少"这条 F2 主线：
 * 遮蔽口径必须来自后端 `my-mask`，而**策略未知时宁可全遮也不能闪明文**。
 * 同时把 token 变更后缓存必须失效钉住 —— 缓存键里不含身份，一旦忘记清空
 * 就是"A 用户的明文策略给 B 用户用"，与后端行权限缓存当年串号是同一类事故。
 */
const getMyMask = vi.hoisted(() => vi.fn())
const getMyMaskMap = vi.hoisted(() => vi.fn())
const currentUser = vi.hoisted(() => ({ token: 'token-a' }))

vi.mock('@/api/system', () => ({
  dataFieldPolicyApi: { getMyMask, getMyMaskMap },
}))

// user store 只用到 token；真实 store 依赖 pinia 与 WebConfig，测试里给最小替身
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ token: currentUser.token }),
}))

const { vMask } = await import('../mask')
const {
  clearMaskPolicyCache,
  ensureMaskPolicy,
  ensureResourceMaskPolicies,
  maskPolicyEntry,
  useMaskPolicy,
} = await import('@/composables/useMaskPolicy')

function mountMasked(binding: Record<string, unknown>) {
  const Comp = defineComponent({
    setup: () => () => withDirectives(h('span', null, 'raw'), [[vMask, binding]]),
  })
  return mount(Comp)
}

describe('v-mask · 后端策略优先', () => {
  beforeEach(() => {
    clearMaskPolicyCache()
    getMyMask.mockReset()
    getMyMaskMap.mockReset()
    currentUser.token = 'token-a'
  })

  it('策略为 FULL 时渲染表达式，不用本地规则', async () => {
    getMyMask.mockResolvedValue({ maskType: 'FULL', maskExpression: '受限' })
    const wrapper = mountMasked({ value: 1999.5, type: 'phone', resourceKey: 'channel.purchasePrice', field: 'price' })
    await flushPromises()
    expect(wrapper.text()).toBe('受限')
  })

  it('策略为 PARTIAL 时按后端口径（首尾各 1 位、星号数封顶 6）', async () => {
    getMyMask.mockResolvedValue({ maskType: 'PARTIAL' })
    const wrapper = mountMasked({ value: '13800138000', resourceKey: 'personnel.user', field: 'phone' })
    await flushPromises()
    expect(wrapper.text()).toBe('1******0')
  })

  it('后端返回 null（无需脱敏）时显示原值，不做二次遮蔽', async () => {
    getMyMask.mockResolvedValue(null)
    const wrapper = mountMasked({ value: '13800138000', type: 'phone', resourceKey: 'personnel.user', field: 'phone' })
    await flushPromises()
    expect(wrapper.text()).toBe('13800138000')
  })

  it('策略到达之前保守全遮，绝不闪出明文', async () => {
    let resolvePolicy: ((policy: Record<string, unknown>) => void) | undefined
    getMyMask.mockImplementation(() => new Promise((resolve) => {
      resolvePolicy = resolve
    }))
    const wrapper = mountMasked({ value: '13800138000', resourceKey: 'personnel.user', field: 'phone' })
    expect(wrapper.text()).toBe('****')
    resolvePolicy?.({ maskType: 'LAST4' })
    await flushPromises()
    expect(wrapper.text()).toBe('*******8000')
  })

  it('请求失败按最严格处理（失败 ≠ 无需脱敏）', async () => {
    getMyMask.mockRejectedValue(new Error('502'))
    const wrapper = mountMasked({ value: '13800138000', resourceKey: 'personnel.user', field: 'phone' })
    await flushPromises()
    expect(wrapper.text()).toBe('****')
    const policy = useMaskPolicy('personnel.user', ['phone'])
    expect(policy.restricted('phone')).toBe(true)
    expect(policy.masked('phone', '13800138000')).toBe('****')
  })

  it('未接策略时退回本地规则（type 仍是给无 data_resource 的展示位用的）', () => {
    const wrapper = mountMasked({ value: '13800138000', type: 'phone' })
    expect(wrapper.text()).toBe('138****8000')
    expect(getMyMask).not.toHaveBeenCalled()
  })

  it('后端已写好掩码的值不被二次遮蔽', async () => {
    getMyMask.mockResolvedValue({ maskType: 'PARTIAL' })
    const wrapper = mountMasked({ value: '039****88', resourceKey: 'personnel.user', field: 'phone' })
    await flushPromises()
    expect(wrapper.text()).toBe('039****88')
  })
})

describe('useMaskPolicy · 缓存与身份失效', () => {
  beforeEach(() => {
    clearMaskPolicyCache()
    getMyMask.mockReset()
    getMyMaskMap.mockReset()
    currentUser.token = 'token-a'
  })

  it('同一字段的并发调用共享一次请求，结果进缓存', async () => {
    getMyMask.mockResolvedValue({ maskType: 'FULL' })
    await Promise.all([
      ensureMaskPolicy('channel.purchasePrice', 'price'),
      ensureMaskPolicy('channel.purchasePrice', 'price'),
      ensureMaskPolicy('channel.purchasePrice', 'price'),
    ])
    expect(getMyMask).toHaveBeenCalledTimes(1)
    await ensureMaskPolicy('channel.purchasePrice', 'price')
    expect(getMyMask).toHaveBeenCalledTimes(1)
  })

  it('token 变化后缓存必须失效（防止上一位用户的明文策略被复用）', async () => {
    getMyMask.mockResolvedValue({ maskType: 'FULL' })
    await ensureMaskPolicy('channel.purchasePrice', 'price')
    expect(getMyMask).toHaveBeenCalledTimes(1)

    currentUser.token = 'token-b'
    await ensureMaskPolicy('channel.purchasePrice', 'price')
    expect(getMyMask).toHaveBeenCalledTimes(2)
  })

  it('策略未就绪时一律按"应遮蔽"处理，不能拿"还不知道"当"可以看"', () => {
    getMyMask.mockImplementation(() => new Promise(() => {}))
    const policy = useMaskPolicy('channel.purchasePrice', ['price'])
    expect(policy.restricted('price')).toBe(true)
    expect(policy.masked('price', 12.5)).toBe('****')
  })
})

describe('批量策略（my-mask-map）与掩码识别', () => {
  beforeEach(() => {
    clearMaskPolicyCache()
    getMyMask.mockReset()
    getMyMaskMap.mockReset()
    currentUser.token = 'token-a'
  })

  it('按资源一次拉取即可覆盖多个字段，未出现在返回里的字段=后端确认无需脱敏', async () => {
    getMyMaskMap.mockResolvedValue({ price: { maskType: 'FULL' } })
    await ensureResourceMaskPolicies('channel.purchasePrice', ['price', 'qty'])

    expect(getMyMaskMap).toHaveBeenCalledTimes(1)
    // 关键：退回逐字段一次都不发，N+1 消失
    expect(getMyMask).not.toHaveBeenCalled()
    expect(maskPolicyEntry('channel.purchasePrice', 'price')?.policy).toMatchObject({ maskType: 'FULL' })
    const qty = maskPolicyEntry('channel.purchasePrice', 'qty')
    expect(qty).toMatchObject({ loaded: true, failed: false, policy: null })
  })

  it('同一资源的第二次加载不再发请求（跨视图共享）', async () => {
    getMyMaskMap.mockResolvedValue({})
    await ensureResourceMaskPolicies('channel.salePrice', ['price'])
    await ensureResourceMaskPolicies('channel.salePrice', ['price', 'qty'])
    expect(getMyMaskMap).toHaveBeenCalledTimes(1)
  })

  it('老后端没有该端点时自动退回逐字段，前端不必等后端发版', async () => {
    getMyMaskMap.mockRejectedValue(new Error('404'))
    getMyMask.mockResolvedValue({ maskType: 'PARTIAL' })
    await ensureResourceMaskPolicies('channel.purchasePrice', ['price'])

    expect(getMyMask).toHaveBeenCalledWith('channel.purchasePrice', 'price')
    expect(maskPolicyEntry('channel.purchasePrice', 'price')?.loaded).toBe(true)
    const policy = useMaskPolicy('channel.purchasePrice', ['price'])
    expect(policy.restricted('price')).toBe(true)
    // '28000.0' 长度 7 → 首 1 + min(7-2,6)=5 个星 + 尾 1
    expect(policy.masked('price', '28000.0')).toBe('2*****0')
  })

  it('blockedSubmit：掩码留在提交体里就给出中止文案，真值与主动清空一律放行', () => {
    const policy = useMaskPolicy('channel.purchasePrice', ['price'])

    // 拦截与否只看"提交值是不是掩码"，与后端 MaskedWriteGuard 同一口径
    expect(policy.blockedSubmit({ price: '****', qty: 3 })).toContain('price')
    expect(policy.blockedSubmit({ price: '1******0' })).toContain('不可见')
    expect(policy.blockedSubmit({ price: 1999.5, qty: 3 })).toBeNull()
    // 用户主动清空 ≠ 提交掩码：这条区分是整套写侧保护的分界点，写反了就会把清空当成"恢复原值"
    expect(policy.blockedSubmit({ price: null })).toBeNull()
    expect(policy.blockedSubmit({ price: 'a*b' })).toBeNull()
    expect(policy.blockedSubmit(undefined)).toBeNull()
  })

  it('掩码字段只用于提示，不改提交体（删字段等于把该列写空）', () => {
    getMyMaskMap.mockResolvedValue({ phone: { maskType: 'FULL' } })
    const policy = useMaskPolicy('personnel.user', ['phone', 'idCard'])
    expect(policy.maskedFieldsIn({ phone: '138****000', idCard: '110101199001011234' }))
      .toEqual(['phone'])
    expect(policy.maskedFieldsIn(null)).toEqual([])
  })
})

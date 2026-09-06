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
const currentUser = vi.hoisted(() => ({ token: 'token-a' }))

vi.mock('@/api/system', () => ({
  dataFieldPolicyApi: { getMyMask },
}))

// user store 只用到 token；真实 store 依赖 pinia 与 WebConfig，测试里给最小替身
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ token: currentUser.token }),
}))

const { vMask } = await import('../mask')
const { clearMaskPolicyCache, ensureMaskPolicy, useMaskPolicy } = await import('@/composables/useMaskPolicy')

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

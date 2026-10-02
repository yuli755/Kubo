// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ModifyPassword from '@/component/user/ModifyPassword.vue'

/**
 * 修改密码面板的生命周期回归（R43 缺陷 2）。
 *
 * 原缺陷：成功后 `setTimeout(3000 → location.replace('/login'))` 没留句柄，
 * 3 秒内关掉抽屉/切走页面仍会被强制甩到登录页。修复后句柄留存、卸载即 clear。
 * 三枚用例把「修复前后各是什么样」钉死：正常等待仍跳（行为不回归）、
 * 卸载即撤销（缺陷不复现）。
 *
 * window.location 用 defineProperty 替换（同 axios.spec.ts 口径）——jsdom 的
 * location 不可直接赋值。定时器替身让断言确定化：不依赖「没被调用」的负向时序。
 */
const mocks = vi.hoisted(() => ({
  updateMyPassword: vi.fn(),
  toastSuccess: vi.fn(),
  toastError: vi.fn(),
}))

vi.mock('@/model/personnel/user/UserService', () => ({
  UserService: class {
    updateMyPassword = mocks.updateMyPassword
  },
}))

vi.mock('@/framework', () => ({
  FeedbackUtil: {
    toastSuccess: mocks.toastSuccess,
    toastError: mocks.toastError,
  },
}))

const locationReplace = vi.fn()

beforeEach(() => {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { replace: locationReplace, href: 'http://localhost/', pathname: '/', search: '' },
  })
  locationReplace.mockReset()
  mocks.updateMyPassword.mockReset().mockResolvedValue(undefined)
  mocks.toastSuccess.mockReset().mockResolvedValue(undefined)
  mocks.toastError.mockReset()
})

function mountPanel() {
  return mount(ModifyPassword)
}

function fill(wrapper: ReturnType<typeof mountPanel>, old: string, next: string, confirmVal: string) {
  const inputs = wrapper.findAll('input[type="password"]')
  return Promise.all([
    inputs[0].setValue(old),
    inputs[1].setValue(next),
    inputs[2].setValue(confirmVal),
  ])
}

describe('user/ModifyPassword · 成功跳转的定时器生命周期（R43）', () => {
  it('两次密码不一致：本地报错，不发请求', async () => {
    const wrapper = mountPanel()
    await fill(wrapper, 'a12345', 'b12345', 'c12345')
    await wrapper.find('button').trigger('click')
    expect(mocks.updateMyPassword).not.toHaveBeenCalled()
    expect(mocks.toastError).toHaveBeenCalledWith('两次密码不一致')
  })

  it('修改成功后 3 秒整：跳转 /login（原行为不回归）', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mountPanel()
      await fill(wrapper, 'a12345', 'b12345', 'b12345')
      await wrapper.find('button').trigger('click')
      await vi.advanceTimersByTimeAsync(0)
      expect(mocks.updateMyPassword).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(2999)
      expect(locationReplace).not.toHaveBeenCalled()
      await vi.advanceTimersByTimeAsync(1)
      expect(locationReplace).toHaveBeenCalledWith('/login')
      wrapper.unmount()
    }
    finally {
      vi.useRealTimers()
    }
  })

  it('成功后的 3 秒窗口内卸载组件：定时器被撤销，不再强制跳登录（缺陷回归锁）', async () => {
    const clearSpy = vi.spyOn(globalThis, 'clearTimeout')
    try {
      const wrapper = mountPanel()
      await fill(wrapper, 'a12345', 'b12345', 'b12345')
      await wrapper.find('button').trigger('click')
      await vi.waitFor(() => {
        expect(mocks.toastSuccess).toHaveBeenCalled()
      })
      // 真实计时器下 3s 未到，先卸载（等价于用户立刻关抽屉/翻页）
      wrapper.unmount()
      expect(clearSpy).toHaveBeenCalled()
      // 兜底负向锁：即便有残留句柄，100ms 内也不该跳（远小于 3000ms）
      await new Promise(resolve => setTimeout(resolve, 100))
      expect(locationReplace).not.toHaveBeenCalled()
    }
    finally {
      clearSpy.mockRestore()
    }
  })
})

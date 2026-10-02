// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ResetPassword from '@/component/login/ResetPassword.vue'

/**
 * 忘记密码面板组件测试（R41 ①a）。
 *
 * 网络层与 ElMessage 都 mock 掉：本组件要锁的是**校验门控、载荷形状、冷却、事件**；
 * HTTP 语义由 axios/errorPolicy 层的测试负责。vi.hoisted 是必须的 —— mock 工厂在
 * import 阶段执行，普通模块级 const spy 那时还没初始化（TDZ）。
 *
 * 选择器用 placeholder 而不是 id：实测（DOM dump）本版本 el-input 不把 id 绑定到
 * 内层 <input>，组件弃用了 id、由 placeholder 承担可访问名 —— 测试随之同构。
 */
const mocks = vi.hoisted(() => ({
  sendEmailCode: vi.fn(),
  sendSmsCode: vi.fn(),
  resetMyPassword: vi.fn(),
  success: vi.fn(),
}))

vi.mock('@/api/system', () => ({
  authApi: {
    sendEmailCode: (email: string) => mocks.sendEmailCode(email),
    sendSmsCode: (phone: string) => mocks.sendSmsCode(phone),
    resetMyPassword: (payload: unknown) => mocks.resetMyPassword(payload),
  },
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal<typeof import('element-plus')>()
  return {
    ...actual,
    ElMessage: Object.assign(actual.ElMessage, {
      success: (...args: unknown[]) => mocks.success(...args),
    }),
  }
})

function mountReset(initialEmail = '') {
  return mount(ResetPassword, {
    props: { initialEmail },
  })
}

const P = {
  email: '请输入注册邮箱',
  phone: '请输入注册手机号',
  code: '6 位验证码',
  password: '至少 6 位',
  confirm: '再次输入新密码',
} as const

function field(wrapper: ReturnType<typeof mountReset>, placeholder: string) {
  return wrapper.find(`input[placeholder="${placeholder}"]`)
}

describe('login/ResetPassword · 忘记密码面板（R41 ①a）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.sendEmailCode.mockResolvedValue(undefined)
    mocks.sendSmsCode.mockResolvedValue(undefined)
    mocks.resetMyPassword.mockResolvedValue(undefined)
  })

  it('默认邮箱渠道：initialEmail 回填，手机号表单不渲染', () => {
    const wrapper = mountReset('a@b.cn')
    expect((field(wrapper, P.email).element as HTMLInputElement).value).toBe('a@b.cn')
    expect(wrapper.find(`input[placeholder="${P.phone}"]`).exists()).toBe(false)
    expect(wrapper.text()).toContain('重置密码')
  })

  it('邮箱非法时发送按钮禁用；合法邮箱后恢复', async () => {
    const wrapper = mountReset()
    const sendBtn = wrapper.find('.send-code-btn')
    await field(wrapper, P.email).setValue('not-an-email')
    expect(sendBtn.attributes('disabled')).toBeDefined()
    await field(wrapper, P.email).setValue('user@hamm.cn')
    expect(sendBtn.attributes('disabled')).toBeUndefined()
  })

  it('邮箱渠道发送验证码：走 sendEmailCode 并进入倒计时冷却', async () => {
    vi.useFakeTimers()
    try {
      const wrapper = mountReset()
      await field(wrapper, P.email).setValue('user@hamm.cn')
      await wrapper.find('.send-code-btn').trigger('click')
      await vi.advanceTimersByTimeAsync(0)
      expect(mocks.sendEmailCode).toHaveBeenCalledWith('user@hamm.cn')
      expect(mocks.success).toHaveBeenCalled()
      // 冷却期内按钮禁用且文案带倒计时（后端同账号有 EMAIL_SEND_BUSY 防刷，前端同构体验）
      expect(wrapper.find('.send-code-btn').attributes('disabled')).toBeDefined()
      expect(wrapper.find('.send-code-btn').text()).toContain('s 后重发')
    }
    finally {
      vi.useRealTimers()
    }
  })

  it('切到手机号渠道：清空凭证类输入，发送走 sendSmsCode', async () => {
    const wrapper = mountReset()
    await field(wrapper, P.code).setValue('123456')
    await wrapper.findAll('.channel-tab')[1].trigger('click')
    // 渠道切换清凭证：带着旧验证码提交只会必然失败
    expect((field(wrapper, P.code).element as HTMLInputElement).value).toBe('')
    expect(wrapper.find(`input[placeholder="${P.email}"]`).exists()).toBe(false)
    await field(wrapper, P.phone).setValue('13800138000')
    await wrapper.find('.send-code-btn').trigger('click')
    expect(mocks.sendSmsCode).toHaveBeenCalledWith('13800138000')
    expect(mocks.sendEmailCode).not.toHaveBeenCalled()
  })

  it('两次密码不一致时提交被禁用（前端同构预检，不消耗后端往返）', async () => {
    const wrapper = mountReset()
    await field(wrapper, P.email).setValue('user@hamm.cn')
    await field(wrapper, P.code).setValue('123456')
    await field(wrapper, P.password).setValue('secret6')
    await field(wrapper, P.confirm).setValue('secret7')
    expect(wrapper.find('.submit-btn').attributes('disabled')).toBeDefined()
    await field(wrapper, P.confirm).setValue('secret6')
    expect(wrapper.find('.submit-btn').attributes('disabled')).toBeUndefined()
  })

  it('提交成功：载荷 {email,code,password} 不含确认框，成功事件带账号', async () => {
    const wrapper = mountReset()
    await field(wrapper, P.email).setValue('user@hamm.cn')
    await field(wrapper, P.code).setValue('123456')
    await field(wrapper, P.password).setValue('secret6')
    await field(wrapper, P.confirm).setValue('secret6')
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => {
      expect(mocks.resetMyPassword).toHaveBeenCalledWith({
        email: 'user@hamm.cn',
        code: '123456',
        password: 'secret6',
      })
    })
    expect(wrapper.emitted('success')?.[0]).toEqual(['user@hamm.cn'])
  })

  it('返回登录按钮发出 back 事件', async () => {
    const wrapper = mountReset()
    await wrapper.find('.back-link').trigger('click')
    expect(wrapper.emitted('back')).toHaveLength(1)
  })
})

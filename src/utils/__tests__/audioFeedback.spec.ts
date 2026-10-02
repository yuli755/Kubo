// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  __resetAudioContextForTests,
  playScanError,
  playScanSuccess,
  playScanWarning,
} from '@/utils/audioFeedback'

/**
 * `audioFeedback` 契约测试（第 35 轮审计）。
 *
 * 锁的是「连续扫码仍然听得见」：浏览器对同时存在的 AudioContext 数量有硬上限
 * （Safari/iOS 约 4~6 个），此前每个提示音都 `new AudioContext()` 且从不 close，
 * 手持机扫几十次之后成功音与失败音一起消失 —— 这套反馈机制静默失效，
 * 而且是在仓库现场才会发现的那种失效。
 */

interface FakeContext {
  state: string
  currentTime: number
  resume: ReturnType<typeof vi.fn>
  destination: unknown
  createOscillator: ReturnType<typeof vi.fn>
  createGain: ReturnType<typeof vi.fn>
  /** 共享的假节点：断言波形/频率写到了哪里 */
  oscType: string
  frequency: { setValueAtTime: ReturnType<typeof vi.fn> }
  gain: { setValueAtTime: ReturnType<typeof vi.fn>, exponentialRampToValueAtTime: ReturnType<typeof vi.fn> }
  started: ReturnType<typeof vi.fn>
  stopped: ReturnType<typeof vi.fn>
}

function makeFakeContext(): FakeContext {
  const noop = () => undefined
  const ctx: FakeContext = {
    state: 'suspended',
    currentTime: 0,
    resume: vi.fn(() => Promise.resolve()),
    destination: { connect: vi.fn() },
    oscType: '',
    frequency: { setValueAtTime: vi.fn() },
    gain: { setValueAtTime: vi.fn(), exponentialRampToValueAtTime: vi.fn() },
    started: vi.fn(),
    stopped: vi.fn(),
    createOscillator: vi.fn(),
    createGain: vi.fn(() => ({ connect: noop, gain: ctx.gain })),
  }
  ctx.createOscillator.mockImplementation(() => ({
    connect: noop,
    start: ctx.started,
    stop: ctx.stopped,
    frequency: ctx.frequency,
    set type(v: string) { ctx.oscType = v },
    get type() { return ctx.oscType },
  }))
  return ctx
}

let ctorSpy: ReturnType<typeof vi.fn>
let contexts: FakeContext[]

beforeEach(() => {
  __resetAudioContextForTests()
  contexts = []
  // 必须用 function（不是箭头）：实现里是 `new AudioCtx()`，箭头函数不能当构造器
  // eslint-disable-next-line prefer-arrow-callback -- 构造器语义是这条用例的全部
  ctorSpy = vi.fn(function () {
    const ctx = makeFakeContext()
    contexts.push(ctx)
    return ctx
  })
  Object.defineProperty(window, 'AudioContext', { value: ctorSpy, configurable: true, writable: true })
  // jsdom 不实现 navigator.vibrate，这里补一个可断言的桩（实现侧有 'vibrate' in navigator 守卫）
  Object.defineProperty(navigator, 'vibrate', { value: vi.fn(() => true), configurable: true, writable: true })
})

afterEach(() => {
  vi.restoreAllMocks()
  __resetAudioContextForTests()
})

describe('audioFeedback · 上下文单例', () => {
  it('连续三种提示音只创建一个 AudioContext（旧实现每次都新建 → 现场静音）', () => {
    playScanSuccess()
    playScanWarning()
    playScanError()
    expect(ctorSpy).toHaveBeenCalledTimes(1)
    expect(contexts).toHaveLength(1)
  })

  it('挂起的上下文（iOS 切后台回来）会先 resume 再复用，而不是再开一个', () => {
    playScanSuccess()
    expect(contexts[0].state).toBe('suspended')
    playScanSuccess()
    expect(contexts[0].resume).toHaveBeenCalled()
    expect(ctorSpy).toHaveBeenCalledTimes(1)
  })

  it('环境不支持 Web Audio 时静默返回，不抛错打断扫码', () => {
    Object.defineProperty(window, 'AudioContext', { value: undefined, configurable: true, writable: true })
    Object.defineProperty(window, 'webkitAudioContext', { value: undefined, configurable: true, writable: true })
    expect(() => playScanSuccess()).not.toThrow()
  })

  it('构造抛错（配额耗尽）时降级为静默，并清空半截状态', () => {
    let calls = 0
    Object.defineProperty(window, 'AudioContext', {
      // eslint-disable-next-line prefer-arrow-callback -- 同上：new 上去要能抛（配额耗尽场景）
      value: vi.fn(function () {
        calls += 1
        throw new Error('too many AudioContexts')
      }),
      configurable: true,
      writable: true,
    })
    expect(() => playScanError()).not.toThrow()
    expect(() => playScanError()).not.toThrow()
    expect(calls).toBeGreaterThan(0)
  })

  it('三种提示音各有自己的频率与波形（声音要能区分，不然等于没反馈）', () => {
    playScanSuccess()
    expect(contexts[0].oscType).toBe('sine')
    expect(contexts[0].frequency.setValueAtTime).toHaveBeenCalledWith(880, 0)
    playScanWarning()
    expect(contexts[0].oscType).toBe('triangle')
    playScanError()
    expect(contexts[0].oscType).toBe('sawtooth')
    expect(contexts[0].frequency.setValueAtTime).toHaveBeenCalledWith(260, 0)
  })
})

// @vitest-environment jsdom
import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import ErrorBoundary from '@/component/ErrorBoundary.vue'

/**
 * `ErrorBoundary` 契约测试。
 *
 * 锁住三条底线：
 * 1. 页面渲染错误不再白屏——被兜成「出错提示 + 重试」，其余布局不受影响；
 * 2. 错误被上报一次（`reportError` + `context=error-boundary`），且返回 false 阻断传播
 *    （不会二次冒泡到全局兜底）；
 * 3. 「重试」复位后子内容重新渲染。
 *
 * 注：`onErrorCaptured` 里置 `error` 触发的重渲染是异步调度的，断言前需 `await nextTick()`。
 */

const reportError = vi.hoisted(() => vi.fn())

vi.mock('@/utils/errorHandler', () => ({
  reportError,
}))

// 组件已改用 `useAppRoute()`（见 appRouter 的说明：vue-router 的 useRoute 在
// airpower 内联副本存在时注入不到，会返回 undefined）
vi.mock('@/router/appRouter', () => ({
  useAppRoute: () => ({ fullPath: '/home' }),
}))

/** 抛出渲染错误的子组件 */
const Boom = defineComponent({
  name: 'Boom',
  setup: () => () => {
    throw new Error('render boom')
  },
})

describe('errorBoundary · 渲染错误兜底', () => {
  beforeEach(() => {
    reportError.mockReset()
  })

  it('无错误时正常渲染插槽内容', () => {
    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h('div', '正常内容') },
    })
    expect(wrapper.text()).toContain('正常内容')
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
  })

  it('子组件渲染抛错：显示兜底 UI，不白屏', async () => {
    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(Boom) },
    })
    await nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(true)
    expect(wrapper.text()).toContain('页面渲染出错')
    expect(wrapper.text()).toContain('render boom')
  })

  it('错误被上报一次且带 error-boundary 上下文', () => {
    mount(ErrorBoundary, {
      slots: { default: () => h(Boom) },
    })
    expect(reportError).toHaveBeenCalledTimes(1)
    const [err, context] = reportError.mock.calls[0]
    expect(err).toBeInstanceOf(Error)
    expect(context).toBe('error-boundary')
  })

  it('点击重试复位错误，重新渲染子内容', async () => {
    let shouldThrow = true
    const Flaky = defineComponent({
      name: 'Flaky',
      setup: () => () => {
        if (shouldThrow) {
          shouldThrow = false
          throw new Error('boom once')
        }
        return h('div', '恢复内容')
      },
    })

    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(Flaky) },
    })
    await nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(true)

    await wrapper.find('.error-boundary__retry').trigger('click')
    await nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
    expect(wrapper.text()).toContain('恢复内容')
  })

  it('非 Error 异常也被兜住并转成字符串展示', async () => {
    const ThrowString = defineComponent({
      name: 'ThrowString',
      setup: () => () => {
        // eslint-disable-next-line no-throw-literal
        throw 'plain string error'
      },
    })
    const wrapper = mount(ErrorBoundary, {
      slots: { default: () => h(ThrowString) },
    })
    await nextTick()
    expect(wrapper.text()).toContain('plain string error')
  })
})

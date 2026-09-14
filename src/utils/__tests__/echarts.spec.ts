import { describe, expect, it } from 'vitest'

/**
 * ECharts 按需加载封装契约测试。
 *
 * 这个封装存在的意义就是「不引入全量 echarts」（省 2.77MB），所以这里只锁
 * 它对外暴露的极窄接口（init + 类型），保证消费者 history.vue / SecurityDashboard.vue
 * 的 `import { init } from '@/utils/echarts'` 不会因重构而断裂。
 */
describe('echarts · 按需封装导出', () => {
  it('导出 init 工厂函数', async () => {
    const mod = await import('@/utils/echarts')
    expect(typeof mod.init).toBe('function')
  })
})

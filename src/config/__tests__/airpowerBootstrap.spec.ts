import { describe, expect, it, vi } from 'vitest'

/**
 * airpower 运行时初始化的契约测试：锁 `WebConfig` 的全局值。
 *
 * 这些值是首屏之后、业务页首次进入时一次性写入的全局配置。它们一旦被误改，
 * 影响是静默的（上传地址错 = 上传 404；appKey 错 = 鉴权头错），不会在编译期暴露。
 * 因此这里锁死：appKey / product / uploadUrl / staticUrl / 中文语言包。
 */

const WebConfig = vi.hoisted(() => ({}) as Record<string, unknown>)

vi.mock('@airpower/web', () => ({ WebConfig }))

describe('airpower-bootstrap · WebConfig 全局配置契约', () => {
  it('写入 appKey/product/上传地址/静态地址/语言包，值固定', async () => {
    await import('@/config/airpower-bootstrap')

    expect(WebConfig.appKey).toBe('kubo')
    expect(WebConfig.product).toBe('Kubo')
    expect(WebConfig.uploadUrl).toBe('/api/file/upload')
    expect(WebConfig.staticUrl).toBe('/static/')
    expect(WebConfig.elementPlusLocale).toBeDefined()
  })
})

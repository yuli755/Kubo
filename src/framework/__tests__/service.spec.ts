import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RootEntity } from '@/framework/entity'
import { AbstractCurdService } from '@/framework/http'
import { Field, Model, Type } from '@/framework/metadata'
import { QueryRequestPage } from '@/framework/query'

/**
 * 服务层的核心契约是 **URL 与请求体形状**：后端是 airpower 控制器，
 * 换掉前端库不能把接口打歪，因此这里逐条锁死。
 */

const requestMock = vi.fn()
vi.mock('@/api/axios', () => ({
  request: (...args: unknown[]) => requestMock(...args),
}))
vi.mock('element-plus', () => ({
  ElMessage: vi.fn(),
  ElMessageBox: { alert: vi.fn() },
}))

@Model({ label: '明细' })
class SimpleDetail extends RootEntity {
  @Field({ label: '名称' })
  name!: string
}

@Model({ label: '物料' })
class DemoEntity extends RootEntity {
  @Field({ label: '名称' })
  name!: string

  @Type(SimpleDetail)
  @Field({ label: '明细' })
  detail!: SimpleDetail
}

class DemoService extends AbstractCurdService<DemoEntity> {
  entityClass = DemoEntity
  baseUrl = 'demo'
}

describe('framework · 服务层 URL 与解析契约', () => {
  const service = new DemoService()

  beforeEach(() => {
    requestMock.mockReset()
  })

  it('分页查询：POST /demo/getPage，请求体带 filter/page', async () => {
    requestMock.mockResolvedValue({
      list: [{ id: 1, name: '铝板', detail: { id: 2, name: '明细' } }],
      total: 42,
      pageCount: 3,
      page: { pageNum: 1, pageSize: 20 },
    })

    const req = new QueryRequestPage(DemoEntity)
    req.filter.name = '铝板'
    const response = await service.getPage(req)

    expect(requestMock).toHaveBeenCalledTimes(1)
    const [config] = requestMock.mock.calls[0] as [{ url: string, method: string, data: Record<string, unknown> }]
    expect(config.url).toBe('/demo/getPage')
    expect(config.method).toBe('post')
    expect(config.data).toMatchObject({ filter: { name: '铝板' }, page: { pageNum: 1, pageSize: 20 } })

    // 响应解析成实体（含 @Type 嵌套）
    expect(response.total).toBe(42)
    expect(response.pageCount).toBe(3)
    expect(response.list[0]).toBeInstanceOf(DemoEntity)
    expect(response.list[0].detail).toBeInstanceOf(SimpleDetail)
    expect(response.list[0].detail.name).toBe('明细')
  })

  it('详情：POST /demo/getDetail，id 在请求体里（后端无 getDetail/{id} 路由）', async () => {
    requestMock.mockResolvedValue({ id: 9, name: '钢管' })
    const detail = await service.getDetail(9)
    const [config] = requestMock.mock.calls[0] as [{ url: string, data: unknown }]
    expect(config.url).toBe('/demo/getDetail')
    // id 必须在体内：后端是 `@RequestBody E source`，路径式 `getDetail/9` 真机是 404
    expect(config.data).toEqual({ id: 9 })
    expect(detail).toBeInstanceOf(DemoEntity)
    expect(detail.name).toBe('钢管')
  })

  it('新增/修改/删除/启停：URL 与后端控制器一致，且带成功提示', async () => {
    const { ElMessage } = await import('element-plus')
    requestMock.mockResolvedValue(5)

    const entity = Object.assign(new DemoEntity(), { id: 5, name: '铝板' })
    expect(await service.add(entity, '新增成功')).toBe(5)
    expect((requestMock.mock.calls[0] as [{ url: string }])[0].url).toBe('/demo/add')
    expect(ElMessage).toHaveBeenCalledWith(expect.objectContaining({ message: '新增成功' }))

    requestMock.mockClear()
    await service.update(entity)
    expect((requestMock.mock.calls[0] as [{ url: string }])[0].url).toBe('/demo/update')

    requestMock.mockClear()
    await service.delete(5)
    expect((requestMock.mock.calls[0] as [{ url: string }])[0].url).toBe('/demo/delete')
    expect((requestMock.mock.calls[0] as [{ data: unknown }])[0].data).toEqual({ id: 5 })

    requestMock.mockClear()
    await service.enable(5)
    expect((requestMock.mock.calls[0] as [{ url: string }])[0].url).toBe('/demo/enable')

    requestMock.mockClear()
    await service.disable(5)
    expect((requestMock.mock.calls[0] as [{ url: string }])[0].url).toBe('/demo/disable')
  })

  it('自定义接口：链式 api().request() 走同一实例，可加头/超时', async () => {
    requestMock.mockResolvedValue(undefined)
    interface Chainable {
      addHttpHeader: (k: string, v: string) => Chainable
      setTimeout: (t: number) => Chainable
      request: (body?: unknown) => Promise<void>
    }
    const exposed = service as unknown as { api: (url: string) => Chainable }
    await exposed.api('publish').addHttpHeader('X-Demo', '1').setTimeout(1000).request({ id: 1 })

    const [config] = requestMock.mock.calls[0] as [{ url: string, headers: Record<string, string>, timeout: number }]
    expect(config.url).toBe('/demo/publish')
    expect(config.headers).toEqual({ 'X-Demo': '1' })
    expect(config.timeout).toBe(1000)
  })

  it('列表响应不是数组时返回空数组（不炸页面）', async () => {
    requestMock.mockResolvedValue(null)
    const list: DemoEntity[] = await service.getList(new QueryRequestPage(DemoEntity))
    expect(list).toEqual([])
  })
})

/**
 * # 框架层·HTTP 与服务基类
 *
 * ## 为什么自己写
 *
 * 原来这套在 `@airpower/web` 里：`AbstractService.api(url)` 返回一个可链式的 `Http`，
 * CRUD 方法（`getPage/getDetail/add/update/delete/enable/disable`）拼出后端约定的 URL。
 * 换底座后由本文件承担，**URL 拼法与后端 airpower 控制器保持一致**（后端不动）：
 *
 * | 方法 | URL |
 * | --- | --- |
 * | `getPage` | `POST /{baseUrl}/getPage` |
 * | `getList` | `POST /{baseUrl}/getList` |
 * | `getTreeList` | `POST /{baseUrl}/getTreeList` |
 * | `getDetail` | `GET /{baseUrl}/getDetail/{id}` |
 * | `add` | `POST /{baseUrl}/add` |
 * | `update` | `POST /{baseUrl}/update` |
 * | `delete` | `POST /{baseUrl}/delete` |
 * | `enable` / `disable` | `POST /{baseUrl}/enable` / `disable` |
 *
 * 请求实际发往 `src/api/axios.ts` 的统一实例（拦截器/租户头/401 刷新/演示适配器都在这儿生效），
 * 因此换底座不影响鉴权、多租户与演示模式。
 */

import type { Ref } from 'vue'
import type { QueryRequest } from './query'
import type { IIdentifiableEntity, IJson, ITransformerConstructor } from './types'
import { request } from '@/api/axios'
import { fill, RootModel, toJson } from './entity'
import { FeedbackUtil } from './feedback'
import { QueryPage, QueryRequestPage, QueryResponsePage, QuerySort } from './query'

/** 单个请求的可选项 */
export interface IHttpOption {
  /** 是否弹出后端的失败消息（默认弹） */
  showError?: boolean
  /** 超时（毫秒） */
  timeout?: number
}

/** 链式 HTTP 构造器 */
export class Http {
  private readonly url: string
  private readonly headers: Record<string, string> = {}
  private option: IHttpOption = { showError: true }
  private method: 'get' | 'post' = 'post'

  constructor(url: string, option: IHttpOption = {}) {
    this.url = url
    this.option = { ...this.option, ...option }
  }

  /** 追加请求头 */
  addHttpHeader(key: string, value: string): this {
    this.headers[key] = value
    return this
  }

  /** 设置超时 */
  setTimeout(timeout: number): this {
    this.option.timeout = timeout
    return this
  }

  /** 失败时抛出错误（默认行为，显式声明用于可读性） */
  throwError(): this {
    this.option.showError = false
    return this
  }

  /** 不弹错误提示 */
  hideError(): this {
    this.option.showError = false
    return this
  }

  private async send(body?: unknown, method: 'get' | 'post' = this.method): Promise<unknown> {
    return request({
      url: this.url,
      method,
      data: body === undefined ? undefined : toJson(body as object),
      headers: this.headers,
      timeout: this.option.timeout,
    } as never)
  }

  /** 发起请求，返回响应体 `data` */
  async request(): Promise<void>
  async request<T>(body?: unknown): Promise<T>
  async request<T>(body?: unknown): Promise<T | void> {
    return this.send(body) as Promise<T>
  }

  /** 发起请求，原样返回（不解析） */
  async requestRaw<T>(body?: unknown): Promise<T> {
    return this.send(body) as Promise<T>
  }

  /** 请求并把响应解析成实体 */
  async requestModel<E extends object>(body: unknown, EntityClass: ITransformerConstructor<E>): Promise<E> {
    const data = await this.send(body)
    return fill(new EntityClass(), data)
  }

  /** 请求并把响应解析成实体数组 */
  async requestModelList<E extends object>(body: unknown, EntityClass: ITransformerConstructor<E>): Promise<E[]> {
    const data = await this.send(body)
    return Array.isArray(data) ? data.map(item => fill(new EntityClass(), item)) : []
  }
}

/**
 * # 服务基类
 *
 * 业务 Service 只要声明 `baseUrl` 与 `entityClass` 即获得全部 CRUD 能力。
 */
export abstract class AbstractService extends RootModel {
  /** 接口目录（对应后端控制器路径，如 `material`） */
  abstract baseUrl: string

  /** 加载态（可绑定到页面 `v-loading`） */
  loading: Ref<boolean> = { value: false } as Ref<boolean>

  /** 静态创建实例（与原库写法兼容） */
  static create<S extends AbstractService>(this: new () => S, loading?: Ref<boolean>): S {
    const service = new this()
    if (loading) {
      service.loading = loading
    }
    return service
  }

  /** 创建一个链式 HTTP 请求 */
  protected api(url: string, baseUrl?: string): Http {
    const prefix = baseUrl ?? this.baseUrl
    return new Http(`/${prefix}/${url}`.replace(/\/+/g, '/'))
  }
}

/**
 * # CRUD 服务基类
 *
 * @param E 实体类型
 */
export abstract class AbstractCurdService<E extends IIdentifiableEntity> extends AbstractService {
  /** 该服务操作的实体类（请求/响应都经它转换） */
  abstract entityClass: ITransformerConstructor<E>

  /** 分页查询 */
  protected urlGetPage = 'getPage'

  /** 不分页查询 */
  protected urlGetList = 'getList'

  /** 树查询 */
  protected urlGetTreeList = 'getTreeList'

  /** 详情查询 */
  protected urlGetDetail = 'getDetail'

  /** 新增 */
  protected urlAdd = 'add'

  /** 修改 */
  protected urlUpdate = 'update'

  /** 删除 */
  protected urlDelete = 'delete'

  /** 启用 */
  protected urlEnable = 'enable'

  /** 禁用 */
  protected urlDisable = 'disable'

  /** 创建导出任务（异步导出：返回任务编码，由 `queryExport` 轮询下载地址） */
  protected urlExport = 'export'

  /** 查询导出结果 */
  protected urlQueryExport = 'queryExport'

  /** 实体类（`new this.entityClass()` 的写法会触发 new-cap，统一在这里取一次） */
  protected get EntityClass(): ITransformerConstructor<E> {
    return this.entityClass
  }

  /**
   * 创建导出任务。
   *
   * 返回 `fileCode`（任务编码），配合 `queryExport` 轮询下载地址 ——
   * 完整流程见 `framework/export.ts` 的 `ExportUtil.run()`，页面一般不需要直接调它。
   */
  async createExportTask(request: unknown, apiUrl = this.urlExport): Promise<string> {
    return this.api(apiUrl).requestRaw<string>(request)
  }

  /** 查询导出结果：任务未完成时后端会返回业务错误，调用方负责重试（见 `ExportUtil`） */
  async queryExport(fileCode: string, apiUrl = this.urlQueryExport): Promise<string> {
    return this.api(apiUrl).requestRaw<string>({ fileCode })
  }

  /** 分页查询 */
  async getPage(request: QueryRequest<E>, apiUrl = this.urlGetPage): Promise<QueryResponsePage<E>> {
    const data = await this.api(apiUrl).requestRaw<IJson>(request)
    const EntityClass = this.EntityClass
    const response = new QueryResponsePage<E>()
    if (data && typeof data === 'object') {
      const raw = data as IJson
      response.total = Number(raw.total ?? 0)
      response.pageCount = Number(raw.pageCount ?? 0)
      const page = raw.page as IJson | undefined
      if (page) {
        response.page = fill(new QueryPage(), page)
      }
      const sort = raw.sort as IJson | undefined
      if (sort) {
        response.sort = fill(new QuerySort(), sort)
      }
      const list = Array.isArray(raw.list) ? raw.list : []
      response.list = list.map(item => fill(new EntityClass(), item))
    }
    return response
  }

  /** 不分页查询 */
  async getList(request: QueryRequest<E>, apiUrl = this.urlGetList): Promise<E[]> {
    const data = await this.api(apiUrl).requestRaw<unknown>(request)
    const EntityClass = this.EntityClass
    return Array.isArray(data) ? data.map(item => fill(new EntityClass(), item)) : []
  }

  /** 树结构查询 */
  async getTreeList(request: QueryRequest<E>, apiUrl = this.urlGetTreeList): Promise<E[]> {
    const data = await this.api(apiUrl).requestRaw<unknown>(request)
    const EntityClass = this.EntityClass
    return Array.isArray(data) ? data.map(item => fill(new EntityClass(), item)) : []
  }

  /** 详情查询 */
  async getDetail(id: number, apiUrl?: string): Promise<E> {
    const url = apiUrl ?? `${this.urlGetDetail}/${id}`
    const data = await this.api(url).requestRaw<unknown>({})
    return fill(new this.EntityClass(), data)
  }

  /** 新增 */
  async add(entity: E, successMessage?: string, apiUrl = this.urlAdd): Promise<number> {
    const id = await this.api(apiUrl).request<number>(entity)
    if (successMessage) {
      FeedbackUtil.toastSuccess(successMessage)
    }
    return Number(id)
  }

  /** 修改 */
  async update(entity: E, successMessage?: string, apiUrl = this.urlUpdate): Promise<number> {
    const id = await this.api(apiUrl).request<number>(entity)
    if (successMessage) {
      FeedbackUtil.toastSuccess(successMessage)
    }
    return Number(id)
  }

  /** 删除 */
  async delete(id: number, successMessage = '删除成功'): Promise<void> {
    await this.api(this.urlDelete).request({ id })
    FeedbackUtil.toastSuccess(successMessage)
  }

  /** 启用 */
  async enable(id: number, successMessage = '启用成功'): Promise<void> {
    await this.api(this.urlEnable).request({ id })
    FeedbackUtil.toastSuccess(successMessage)
  }

  /** 禁用 */
  async disable(id: number, successMessage = '禁用成功'): Promise<void> {
    await this.api(this.urlDisable).request({ id })
    FeedbackUtil.toastSuccess(successMessage)
  }
}

/** 兼容旧写法：`QueryRequestPage` 需要显式实体类 */
export function createRequest<E extends IIdentifiableEntity>(EntityClass: ITransformerConstructor<E>): QueryRequestPage<E> {
  return new QueryRequestPage<E>(EntityClass)
}

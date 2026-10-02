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
 * | `getTreeList` | `POST /{baseUrl}/getList`（后端无 `getTreeList` 端点，树形实体由 `getList` 直接返回嵌套树；详见 `urlGetTreeList` 的注释） |
 * | `getDetail` | `POST /{baseUrl}/getDetail`（body `{ id }`；后端**没有** `getDetail/{id}` 路由，详见方法注释） |
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

  /**
   * 失败时抛出错误，且**不弹全局提示**（调用方自己 catch 后决定怎么呈现）。
   *
   * 与 {@link hideError} 等价；保留两个名字是为了让调用点的意图更可读
   * （「这个异常我自己处理」vs「这次失败不必打扰用户」）。
   */
  throwError(): this {
    this.option.showError = false
    return this
  }

  /** 不弹全局错误提示（仍然 reject，调用方可以 catch） */
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
      // showError 必须真的传下去：此前 `hideError()` / `throwError()` 只改了 Http 内部字段，
      // 拦截器根本读不到 —— 两个方法是**空操作**，调用方自己 catch 之后仍会多弹一条全局提示。
      showError: this.option.showError,
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

  /**
   * 树查询。
   *
   * 值刻意是 `getList` 而不是 `getTreeList`：**后端从来没有 `getTreeList` 这个端点**。
   * 本文件头部写的「URL 拼法与后端 airpower 控制器保持一致」正是来源 —— `getTreeList`
   * 是 airpower `CurdController` 的契约，B0 退役 airpower 底座时后端没有实现它，
   * 前端却继续按旧名字发请求。真机实测 6 个树形服务全部如此：
   *
   *     /permission/getTreeList  → HTTP 404      /permission/getList → 200，29/35 个节点带 children
   *     /menu/getTreeList        → HTTP 404      /menu/getList       → 200，4/5
   *     /department/getTreeList  → HTTP 404      /department/getList → 200，1/1
   *     /storage/getTreeList     → HTTP 404      /storage/getList    → 200，1/4
   *     /structure/getTreeList   → HTTP 404      /structure/getList  → 200
   *     /inventory/getTreeList   → HTTP 404      /inventory/getList  → 200
   *
   * 影响面：`useTable({ tree: true })` 的 7 处页面（factory/storage、factory/structure、
   * personnel/department、personnel/user 的部门筛选树、system/menu、system/permission、
   * wms/inventory 的仓库树+库位树）以及角色授权的两个弹窗
   * （`personnel/role/component/permissionEditor.vue`、`menuEditor.vue`）——
   * 全部请求 404，表现为树形表格与授权弹窗**空白**，且 404 响应体没有 code/message，
   * 前端只能显示泛化的网络错误，看不出是端点不存在。
   *
   * 为什么修前端而不是给后端补一个 `getTreeList`：后端 `getList` 对树形实体**本来就返回树**
   * （`Trees.buildTreeList` 在三个不同层次被调用：PermissionController 的 controller 级
   * `afterGetList`、Menu/DepartmentController 直接覆写端点、Storage/StructureService 的
   * service 级 `afterGetList`）。而新增端点会新增权限身份 `X_getTreeList`，
   * `RequestInterceptor.checkUserPermission` 是**从数据库查权限并且 fail-closed**
   * （`FORBIDDEN.whenNull(needPermission, "权限不存在")`，且该判空在 SYSTEM_ROOT 的
   * bypass 判断之前），而把扫描结果写库的 `PermissionService.loadPermission()` 只被
   * `DevDataInitRunner` 调用、受 `init.lock` 与 dev profile 双重限制 ——
   * 结果是新端点在权限行落库前**对所有角色都是 403**，包括超管。
   * 为一个「后端早已用 getList 满足」的契约去手写权限播种脚本，得不偿失。
   *
   * 保留 `getTreeList()` 方法本身（而不是让调用方直接改调 `getList()`）：调用点
   * `useTable({ tree: true })` 表达的是「不分页、要嵌套 children」的语义，
   * 这个名字在调用侧仍然有价值，只是不再对应一个独立的后端 URL。
   */
  protected urlGetTreeList = 'getList'

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

  /**
   * 查询导出结果：任务未完成时后端会返回业务错误，调用方负责重试（见 `ExportUtil`）。
   *
   * 轮询期间的「任务还在跑」是**预期状态**不是失败（`waitForFile` 的 catch 注释），
   * 所以这里必须 `hideError()`：否则每 500ms 一轮轮询 × 3s 去重窗口 = 一次导出
   * 叠三四条红色报错，任务完成后又变绿字「导出完成」（第 35 轮审计 P1）。
   * 真正的失败由 `ExportUtil` 超时抛错 + 调用点（useTable）统一 toast。
   */
  async queryExport(fileCode: string, apiUrl = this.urlQueryExport): Promise<string> {
    return this.api(apiUrl).hideError().requestRaw<string>({ fileCode })
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

  /**
   * 详情查询。
   *
   * ⚠️ 契约是 `POST {baseUrl}/getDetail` + body `{ id }`，**不是** `getDetail/{id}`：
   * 后端 `BaseController` 只声明了 `@PostMapping("getDetail")`，入参是
   * `@RequestBody @Validated(WhenIdRequired.class) E source` —— id 在**请求体**里。
   * 真机实测（2026-09，走 dev 代理 `127.0.0.1:3000/api` → `127.0.0.1:8080`）：
   *
   *     GET  /input/getDetail/1  → HTTP 404（Spring 默认错误体，没有 code/message）
   *     POST /input/getDetail/1  → HTTP 404
   *     POST /input/getDetail    → HTTP 200 {"code":200,"message":"获取成功",...}
   *     GET  /material/getDetail/1 → HTTP 404     POST /material/getDetail → 200
   *
   * 影响面（症状都是"点开就空白"，列表页却正常，很难联想到 URL）：
   * `KFormDialog` 的编辑回填、`useDetail` 的详情查询、销售/采购列表的「查看详情」。
   *
   * 演示模式为什么没暴露：`demo/mock/adapter.ts` 专门兼容了「路径末段是数字」的写法
   * （它把 `getDetail/3` 拆成 action=`getDetail` + `payload.id=3`）—— **mock 通过 ≠ 真机通过**。
   * 这与 `urlGetTreeList` 记录的 `getTreeList` 404 是同一类偏差的第二个实例，
   * 所以本轮补了 `pnpm contract:check`：前端拼出的每个端点都必须存在于后端路由表。
   */
  async getDetail(id: number, apiUrl = this.urlGetDetail): Promise<E> {
    const data = await this.api(apiUrl).requestRaw<unknown>({ id })
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

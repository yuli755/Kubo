/**
 * # 传输层接缝：框架声明契约，应用负责装配
 *
 * ## 为什么会有这个文件
 *
 * 前端的层次是 `utils/types → base/model → framework → view`（见 `docs/架构分层设计.md` §八）。
 * 但框架的 HTTP 与上传**必须**走应用那个统一实例 —— 鉴权头、租户头、401 刷新、
 * 链路追踪、幂等键、演示模式适配器**全部**挂在 `src/api/axios.ts` 上。
 * 于是 `framework/http.ts` 曾经直接 `import { request } from '@/api/axios'`：
 * 框架层依赖应用装配面，门禁里只能登记成"例外"。
 *
 * 现在把依赖**反过来**：
 *
 * ```
 *   src/api/axios.ts ──注册──▶ framework/transport.ts ◀──取用── framework/http.ts
 *   （应用装配面）                （框架的契约）                 （框架的请求出口）
 * ```
 *
 * 依赖方向变成 `api → framework`（应用依赖框架，天经地义），框架里没有任何一句提到应用。
 *
 * ## 为什么不直接拆成"注入 + 忘了装就算了"
 *
 * 当初不拆的理由是实的：**接线漏了会变成运行期静默无请求**（页面点了没反应，控制台干净），
 * 而本仓没有端到端冒烟兜底。所以这里不是"可选注入"，而是**未装配就抛错**，
 * 错误信息里带文件名。装配点在组合根（`src/main.ts` 显式 import 统一实例 + 启动自检），
 * 也就是说：漏装配会**在启动时就炸**，而不是等用户点了按钮才静默失败。
 *
 * ## 用法
 *
 * ```ts
 * // 应用侧（src/api/axios.ts）——注册
 * configureTransport({ request: config => ..., upload: (url, file, option) => ... })
 *
 * // 框架侧 ——取用（测试里可以装一个假的）
 * const data = await getTransport().request({ url: '/demo/getPage', method: 'post', data })
 * ```
 */

/** 一次请求的配置（框架只说这些；axios 那些参数由应用侧的适配器消化） */
export interface TransportRequest {
  /** 相对 `baseURL` 的路径，如 `/demo/getPage` */
  url: string
  /** HTTP 方法，默认 `post`（与后端 airpower 控制器一致） */
  method?: string
  /** 请求体（已序列化的 JSON 或 FormData） */
  data?: unknown
  /** 附加请求头 */
  headers?: Record<string, string>
  /** 超时（毫秒） */
  timeout?: number
  /** `blob` / `arraybuffer` 等特殊响应类型 */
  responseType?: string
}

/** 上传的可选项 */
export interface TransportUploadOption {
  /** 上传进度（0-100） */
  onProgress?: (percent: number) => void
  /** 随文件一起提交的表单字段（如后端 `FileController.upload` 要的 `category`） */
  fields?: Record<string, string | number>
}

/**
 * 框架需要的全部传输能力。
 *
 * 两个方法都返回**已解包**的 `data`（统一响应格式 `{code,message,data}` 的解包在应用侧完成），
 * 因此返回值是 `unknown`，由调用方按契约收窄。
 */
export interface HttpTransport {
  /** 发起一次请求 */
  request: (config: TransportRequest) => Promise<unknown>
  /** 上传一个文件（multipart） */
  upload: (url: string, file: File, option?: TransportUploadOption) => Promise<unknown>
}

/** 当前装配的实现（未装配为 `null`） */
let transport: HttpTransport | null = null

/**
 * 注册传输实现（应用侧调用；传 `null` 可注销，测试里用来验"未装配就抛错"）。
 *
 * 后注册者生效：测试可以先让统一实例注册，再换成假实现。
 */
export function configureTransport(next: HttpTransport | null): void {
  transport = next
}

/** 是否已装配（组合根自检与测试断言用） */
export function hasTransport(): boolean {
  return transport !== null
}

/**
 * 取传输实现。
 *
 * 未装配时**抛错而不是静默返回空**：静默会让"没发请求"伪装成"请求成功但没数据"。
 */
export function getTransport(): HttpTransport {
  if (!transport) {
    throw new Error(
      'HTTP 传输层未装配：请在应用启动处引入 src/api/axios.ts（它会调用 configureTransport）。'
      + '见 src/framework/transport.ts 的说明。',
    )
  }
  return transport
}

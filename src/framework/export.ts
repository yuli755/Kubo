import { WebFileUtil } from './feedback'

/**
 * # 导出（异步导出任务）
 *
 * ## 后端契约（两端都已实现，前端只是补齐）
 * | 步骤 | 请求 | 响应 |
 * | --- | --- | --- |
 * | 1. 建任务 | `POST /{baseUrl}/export`（体 = 查询请求，**不带 page**） | `fileCode`（字符串） |
 * | 2. 轮询 | `POST /{baseUrl}/queryExport`，体 `{ fileCode }` | 可下载的相对路径 |
 * | 3. 下载 | 打开 `WebConfig.staticUrl + 路径` | 文件流 |
 *
 * ## 为什么是"异步任务"而不是直接下载
 * 导出要做行级/列级权限过滤、脱敏与 CSV 公式注入转义（后端 `ExportColumnMasker` /
 * `CsvFormulaSanitizer`），数据量大时不能占着请求线程 —— 所以后端返回的是**任务编码**，
 * 前端轮询到就绪再下载。原库把这套藏在 `DialogUtil.createExportTask` 的弹窗里，
 * 换底座时要么整段复刻（含弹窗），要么把流程抽成可测的纯函数。这里选后者：
 * `ExportUtil.run()` 负责"建任务 → 轮询 → 返回地址"，要不要显示进度归调用方。
 *
 * ## 轮询的边界（不设边界就是死循环）
 * 默认最多 60 次、每次间隔 1s。超时抛错而不是无限等 —— 用户宁可看到"导出超时，请重试"，
 * 也不愿看着一个永远转的圈。
 */

export interface IExportOption {
  /** 轮询间隔（毫秒），默认 1000 */
  intervalMs?: number
  /** 最多轮询次数，默认 60（≈1 分钟） */
  maxAttempts?: number
  /** 每次轮询回调（用于进度提示） */
  onPolling?: (attempt: number) => void
}

/** 导出请求的最小形状（`ExportUtil` 需要去掉分页：导出的是整个筛选结果，不是当前页） */
type ExportRequest = Record<string, unknown>

/** 一个能建导出任务的服务（`AbstractCurdService` 满足；此处只声明用到的那部分） */
export interface IExportableService {
  createExportTask: (request: unknown) => Promise<string>
  queryExport: (fileCode: string) => Promise<string>
}

const sleep = (ms: number): Promise<void> => new Promise(resolve => setTimeout(resolve, ms))

export class ExportUtil {
  /**
   * 建导出任务，返回任务编码。
   *
   * ⚠️ 请求体里的 `page` 必须去掉：带上分页只会导出当前那一页，
   * 而用户点"导出"想要的是"符合当前筛选的全部数据"。
   */
  static async createTask(service: IExportableService, request: ExportRequest): Promise<string> {
    const payload: ExportRequest = { ...request }
    delete payload.page
    const fileCode = await service.createExportTask(payload)
    if (!fileCode) {
      throw new Error('导出任务创建失败：后端未返回任务编码')
    }
    return fileCode
  }

  /** 轮询任务，直到拿到可下载路径（超时抛错） */
  static async waitForFile(
    service: IExportableService,
    fileCode: string,
    option: IExportOption = {},
  ): Promise<string> {
    const intervalMs = option.intervalMs ?? 1000
    const maxAttempts = option.maxAttempts ?? 60
    for (let attempt = 1; attempt <= maxAttempts; attempt++) {
      option.onPolling?.(attempt)
      try {
        const path = await service.queryExport(fileCode)
        if (path) {
          return path
        }
      }
      catch {
        // 任务还在跑：后端此时通常返回业务错误码而不是空值，继续等下一轮
      }
      await sleep(intervalMs)
    }
    throw new Error('导出超时：文件还没生成完，请稍后重试')
  }

  /** 完整流程：建任务 → 轮询 → 返回**可直接打开**的地址 */
  static async run(
    service: IExportableService,
    request: ExportRequest,
    option: IExportOption = {},
  ): Promise<string> {
    const fileCode = await ExportUtil.createTask(service, request)
    const path = await ExportUtil.waitForFile(service, fileCode, option)
    return WebFileUtil.getStaticFileUrl(path)
  }

  /** 打开下载地址（新标签页；`noopener` 防止导出页反向拿到本页 window） */
  static download(url: string): void {
    window.open(url, '_blank', 'noopener')
  }
}

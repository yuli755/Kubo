/**
 * # Demo Mock 适配器（仅演示模式）
 *
 * ## 为什么挂在 axios 适配器上
 * Kubo 前端有**两条**请求路径：
 * - 自研 client：`src/api/axios.ts` 的 `axios.create(...)` 实例；
 * - airpower：`@airpower/web` 的 `Http` 类是直接用**全局默认 axios 实例**发请求的
 *   （`axios.request(axiosConfig)`，已对照 dist 源码核验）。
 *
 * 因此把适配器挂到 `axios.defaults.adapter` 上，**两条路径一起被接管** —— 不需要改
 * 任何 Service / hook / 视图代码，也不会在业务代码里留下 mock 分支。
 *
 * ## 行为
 * - 按 `./engine` 的规则解析 URL 与请求体，返回统一响应信封 `{code:200, message, data}`；
 * - 刻意加 120–320ms 随机延迟：让加载态、骨架屏、按钮 loading 在演示时**看得见**；
 * - 导出/下载类请求（`responseType: blob|arraybuffer`）直接回一个空文件，避免报错；
 * - **文件上传**（multipart，`file/upload`）不落库：把浏览器里的 `File` 变成一个 objectURL 返回，
 *   于是演示模式下上传头像/附件能立刻看到**用户自己选的那张图**（而不是占位图）。
 */

import type { AxiosAdapter, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { resolveMock } from './engine'

const BLOB_PATH_HINTS = ['export', 'download', 'importTemplate', 'template']

interface ParsedUrl {
  resourcePath: string
  action: string
}

/** 去掉协议/域名/查询串与 `/api` 前缀，拆出「资源路径 + 动作」 */
export function parseRequestUrl(rawUrl: string): ParsedUrl {
  let url = rawUrl.trim()
  url = url.replace(/^[a-z]+:\/\/[^/]+/i, '')
  url = url.split('?')[0] ?? ''
  url = url.replace(/^\/api\//, '/')
  const segments = url.split('/').filter(Boolean)
  const action = segments.pop() ?? ''
  return { resourcePath: segments.join('/'), action }
}

/** 合并 body / params / 路径里的 id，供引擎取用 */
function collectPayload(config: InternalAxiosRequestConfig): Record<string, unknown> {
  const payload: Record<string, unknown> = {}

  const params = config.params as Record<string, unknown> | undefined
  if (params && typeof params === 'object') {
    Object.assign(payload, params)
  }

  const rawBody = config.data
  if (typeof rawBody === 'string' && rawBody) {
    try {
      Object.assign(payload, JSON.parse(rawBody) as Record<string, unknown>)
    }
    catch {
      // 非 JSON body（如 multipart）忽略
    }
  }
  else if (rawBody && typeof rawBody === 'object') {
    Object.assign(payload, rawBody as Record<string, unknown>)
  }

  // 路径末段是数字时视为 id（如 `/{res}/getDetail/{id}`）——此时**动作段是它的前一段**。
  // 必须在这里把动作也还原出来：否则引擎拿到的是 `resourcePath='wms/input/getDetail'`、
  // `action='3'`，任何端点都命不中，最后落到「未覆盖的读取端点」返回 `true`，
  // 表现为**详情弹窗字段全是 '-'、明细永远为空**（列表页却完全正常，很难联想到 URL 解析）。
  const { resourcePath, action } = parseRequestUrl(config.url ?? '')
  if (/^\d+$/.test(action)) {
    const segments = resourcePath.split('/').filter(Boolean)
    payload.id = Number(action)
    payload.__resourcePath = segments.slice(0, -1).join('/')
    payload.__action = segments[segments.length - 1] ?? ''
  }
  return payload
}

/**
 * 演示模式的上传：不发给任何人，把 `File` 变成 `objectURL` 回一个「仿佛上传成功」的记录。
 *
 * 这样预览里上传头像/合同附件时，列表里显示的就是刚选的那张图 —— 直接回静态占位图
 * 会让人以为「上传成功但内容不对」。`URL.createObjectURL` 在 jsdom 里不保证存在，故做了兜底。
 */
function resolveDemoUpload(config: InternalAxiosRequestConfig): Record<string, unknown> | null {
  const body = config.data
  if (typeof FormData === 'undefined' || !(body instanceof FormData)) {
    return null
  }
  const file = body.get('file')
  if (!(file instanceof File)) {
    return null
  }
  const extension = file.name.includes('.') ? file.name.split('.').pop()?.toLowerCase() ?? '' : ''
  const category = body.get('category')
  return {
    id: Math.floor(Math.random() * 100000) + 1,
    name: file.name,
    extension,
    url: typeof URL?.createObjectURL === 'function' ? URL.createObjectURL(file) : `/static/demo/${file.name}`,
    size: file.size,
    category: category === null ? 1 : Number(category),
    hashMd5: 'demo',
    creatorId: 1,
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

function wantsBlob(config: InternalAxiosRequestConfig, action: string): boolean {
  if (config.responseType === 'blob' || config.responseType === 'arraybuffer') {
    return true
  }
  // 用**已还原的**动作段判断（`/x/export/12` 这种带路径 id 的导出也要认得出来）
  return BLOB_PATH_HINTS.some(hint => action.toLowerCase().includes(hint.toLowerCase()))
}

/**
 * 创建演示模式适配器。
 *
 * @param delayMs 模拟网络延迟区间，默认 `[120, 320]`
 */
export function createDemoAdapter(delayMs: [number, number] = [120, 320]): AxiosAdapter {
  return async (config: InternalAxiosRequestConfig): Promise<AxiosResponse> => {
    const url = `${config.baseURL ?? ''}${config.url ?? ''}`
    const { resourcePath: parsedPath, action: parsedAction } = parseRequestUrl(url)
    const payload = collectPayload(config)
    const resourcePath = (payload.__resourcePath as string | undefined) ?? parsedPath
    const action = (payload.__action as string | undefined) ?? parsedAction
    delete payload.__resourcePath
    delete payload.__action

    await sleep(delayMs[0] + Math.random() * (delayMs[1] - delayMs[0]))

    const uploaded = action === 'upload' ? resolveDemoUpload(config) : null
    const data = wantsBlob(config, action)
      ? new Blob(['Kubo 演示模式：导出未接入真实文件'], { type: 'text/plain' })
      : (uploaded ?? resolveMock(resourcePath, action, payload))

    return {
      data: wantsBlob(config, action)
        ? data
        : {
            code: 200,
            message: 'success',
            data,
            timestamp: new Date().toISOString(),
            path: url,
          },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    } as AxiosResponse
  }
}

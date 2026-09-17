/**
 * # 框架层·文件上传
 *
 * 对应原 `@airpower/web` 的「上传模型」：页面/弹窗只描述**要什么文件**（类别、类型、大小），
 * 剩下的校验、multipart 提交、响应整形、预览地址在这里一次做完。
 *
 * ## 后端契约（已核对 `FileController.upload`）
 *
 * | 项 | 值 |
 * | --- | --- |
 * | 端点 | `POST /api/file/upload`（multipart） |
 * | 字段 | `file`（文件本身）+ `category`（Integer，缺省按后端默认类别） |
 * | 响应 | `Json.data(FileEntity)` → `{ id, name, extension, url, category, hashMd5, size }` |
 * | 鉴权 | 后端标了 `authorize = false`（允许匿名上传），但仍会带登录态 |
 *
 * ## 两个坑（都在这里消化掉了）
 *
 * 1. **上传地址不能直接拼**：`WebConfig.uploadUrl = '/api/file/upload'` 是给 airpower 用的
 *    （它的 axios 没有 baseURL），而本仓 `src/api/axios.ts` 的实例 `baseURL = '/api'` ——
 *    原样传进去会变成 `/api/api/file/upload`。这里统一剥掉前缀后交给**传输层**
 *    （`framework/transport.ts`，由 `src/api/axios.ts` 装配），于是鉴权头 / 租户头 /
 *    演示适配器照常生效，而框架侧不认识应用实例。
 * 2. **响应里没有 `size` 时要补**：后端返回的 `size` 是 `Long`（可能为 0/缺失），
 *    而列表要显示文件大小；这里在缺失时回落到浏览器已知的 `file.size`。
 */

import type { IJson } from './types'
import { WebConfig } from './config'
import { WebFileUtil } from './feedback'
import { getTransport } from './transport'

/** 上传成功后拿到的文件记录（与后端 `FileEntity` 同形，去掉框架不关心的字段） */
export interface IUploadedFile {
  id?: number
  /** 文件名（含扩展名） */
  name: string
  /** 相对路径，展示时用 `UploadUtil.resolveUrl()` 补前缀 */
  url: string
  /** 字节数 */
  size?: number
  /** 扩展名（不带点） */
  extension?: string
  /** 文件类别（后端字典 `FileCategory`） */
  category?: number
  hashMd5?: string
}

/** 上传配置：校验规则 + 落库类别 */
export interface IUploadOption {
  /** 文件类别（`FileCategory` 的 key；后端缺省为普通文件） */
  category?: number
  /** 接受的类型（同 `<input accept>`，如 `image/*`、`.pdf,.docx`） */
  accept?: string
  /** 单文件大小上限（MB），默认 10 */
  maxSizeMB?: number
}

/**
 * 上传弹窗（`DialogUtil.showUpload`）的配置。
 *
 * `data.category` 是 airpower 旧契约（`IUploadProps`）里的写法：
 * 存量调用点写的是 `showUpload({ data: { category: …, }, entity: FileEntity })`，
 * 过渡期两种写法都要能打开，所以这里把旧字段也声明进来（`entity` 只是目标实体，上传用不到）。
 */
export interface IUploadConfig extends IUploadOption {
  /** 是否多选（多选时回到的是数组） */
  multiple?: boolean
  /** 弹窗标题 */
  title?: string
  /** 提示文案 */
  tip?: string
  /** airpower 旧契约：`{ data: { category } }` */
  data?: { category?: number }
}

/** 上传进度回调 */
export interface IUploadProgress {
  onProgress?: (percent: number, file: File) => void
}

/** 常见图片扩展名（决定列表里是缩略图还是文件名） */
const IMAGE_EXTENSIONS = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico']

export class UploadUtil {
  /** 默认大小上限（MB）——超过时给出提示而不是静默失败 */
  static defaultMaxSizeMB = 10

  /** 取扩展名（小写、不带点） */
  static getExtension(nameOrUrl?: string): string {
    if (!nameOrUrl) {
      return ''
    }
    const clean = nameOrUrl.split('?')[0] ?? ''
    const dot = clean.lastIndexOf('.')
    return dot < 0 ? '' : clean.slice(dot + 1).toLowerCase()
  }

  /** 是否图片（按扩展名判断；预览与 `accept` 提示都用它） */
  static isImage(file: { name?: string, url?: string, extension?: string }): boolean {
    const extension = file.extension || this.getExtension(file.name || file.url)
    return IMAGE_EXTENSIONS.includes(extension)
  }

  /**
   * 校验单个文件；通过返回 `null`，不通过返回**给用户看的**中文原因。
   *
   * 单独抽成纯函数的原因：`ElUpload` 的原生校验在演示模式/单元测试里不好触发，
   * 而「类型不对、文件太大」是业务上真正会出问题的两种输入，必须能单独断言。
   */
  static validate(file: Pick<File, 'name' | 'size' | 'type'>, option: IUploadOption = {}): string | null {
    const { accept, maxSizeMB = this.defaultMaxSizeMB } = option
    const name = file.name || ''
    const extension = this.getExtension(name)

    if (accept) {
      const rules = accept.split(',').map(rule => rule.trim().toLowerCase()).filter(Boolean)
      const matched = rules.some((rule) => {
        if (rule.startsWith('.')) {
          return `.${extension}` === rule
        }
        if (rule.endsWith('/*')) {
          return (file.type || '').toLowerCase().startsWith(rule.slice(0, -1))
        }
        return (file.type || '').toLowerCase() === rule
      })
      if (!matched) {
        return `只允许上传 ${accept} 类型的文件`
      }
    }

    if (maxSizeMB > 0 && file.size > maxSizeMB * 1024 * 1024) {
      return `文件不能超过 ${maxSizeMB} MB`
    }
    return null
  }

  /**
   * 上传一个文件。
   *
   * 走传输层（`src/api/axios.ts` 装配的统一实例）→ 鉴权、租户、演示适配器都自动生效
   * （演示模式下不会真的发请求）。
   */
  static async upload(file: File, option: IUploadOption & IUploadProgress = {}): Promise<IUploadedFile> {
    const error = this.validate(file, option)
    if (error) {
      throw new Error(error)
    }

    const data = await getTransport().upload(this.getRequestUrl(), file, {
      fields: option.category === undefined ? {} : { category: option.category },
      onProgress: percent => option.onProgress?.(percent, file),
    })
    return this.normalize(data as IJson, file)
  }

  /** 一次性上传多个文件（用于多选上传） */
  static async uploadList(files: File[], option: IUploadOption & IUploadProgress = {}): Promise<IUploadedFile[]> {
    const result: IUploadedFile[] = []
    for (const file of files) {
      result.push(await this.upload(file, option))
    }
    return result
  }

  /** 上传地址：`WebConfig.uploadUrl` 剥掉统一实例已经带上的 `/api` 前缀（见文件头注释） */
  static getRequestUrl(): string {
    return WebConfig.uploadUrl.replace(/^\/?api\//, '').replace(/^\//, '')
  }

  /** 展示用地址（后端存的是相对路径） */
  static resolveUrl(url?: string): string {
    return WebFileUtil.getStaticFileUrl(url)
  }

  /** 人类可读的文件大小（列表右侧显示） */
  static sizeText(size?: number): string {
    if (!size || size <= 0) {
      return '-'
    }
    if (size < 1024) {
      return `${size} B`
    }
    if (size < 1024 * 1024) {
      return `${(size / 1024).toFixed(1)} KB`
    }
    return `${(size / 1024 / 1024).toFixed(2)} MB`
  }

  /**
   * 后端返回 → 框架记录。
   *
   * ⚠️ 一定要**补 `name` / `size`**：后端在极少数情况下（老数据、匿名上传）可能给空 `name`，
   * 而列表/详情用它做展示与判重；空名字会表现成"上传成功但列表里一行空白"。
   */
  static normalize(data: IJson, file?: Pick<File, 'name' | 'size'>): IUploadedFile {
    const record = (data ?? {}) as Partial<IUploadedFile>
    const name = record.name || file?.name || ''
    return {
      id: record.id,
      name,
      url: record.url ?? '',
      size: record.size || file?.size || 0,
      extension: record.extension || this.getExtension(name),
      category: record.category,
      hashMd5: record.hashMd5,
    }
  }
}

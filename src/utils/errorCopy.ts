import { classifyBusinessError } from '@/api/errorPolicy'
import { isChunkLoadError } from '@/utils/routerErrorHandler'

/**
 * # 面向用户的错误文案分档（D5 · 错卡别再甩英文原文）
 *
 * ## 为什么需要这一层
 * `ErrorBoundary.vue` 与 `Console.vue` 的错卡此前直接把 `error.message` 打给用户
 * （见 §13.4 / §14.5 记录的 D5 候选）。实测后果：懒加载 chunk 失败时用户看到的是
 * `Failed to fetch dynamically imported module: http://localhost:3000/src/view/...vue`
 * —— 一句英文、含内部路径、且**没告诉用户能做什么**。而 §12.5 已立过判据：
 * **「兜底是否有效」的判据不是有没有 catch，而是失败之后用户有没有一条能走通的路**。
 * 一张只有英文原文 + 「重试」按钮的错卡，在版本已更新（旧 chunk 永久 404）时就是死路。
 *
 * ## 分档原则（每条都必须给「下一步」）
 * 1. **chunk 加载失败** → 说「资源加载失败」并给 Ctrl+F5（这类错误的真相是
 *    「你手上这份 index.html 引用的文件已经不存在了」，普通重试按钮不会重新拉
 *    index.html，因此必须明说硬刷新，否则用户点一百次重试也没用）；
 * 2. **网络/超时** → 说清是网络，不是系统坏了（与 axios 无响应分支同一套口径）；
 * 3. **业务错误**（带数字 code 的 ApiError）→ 直接复用 `errorPolicy` 的分类文案，
 *    保证「同一条错在任何界面说同一句话」，不再另写一套；
 * 4. **其它** → 保留原始信息（排查必需）但**降级为次要信息**，主位给中文可行动指引。
 *
 * ## 不做什么
 * 不做「全部翻译成中文」而后丢掉原始信息：运维/开发需要 `detail` 里的原文定位问题，
 * 所以原始 message 始终保留在 detail 槽位，只是不再独占标题。
 */

/** 分档后的错卡文案（不外发：调用方用 `ReturnType<typeof describeUserError>`，见 exports:check） */
interface UserErrorCopy {
  /** 主标题：一句话说清「发生了什么」，中文 */
  title: string
  /** 次要详情：可行动说明；原始错误文本放这里（排查用，不与标题重复） */
  detail: string
  /** 下一步指引：必填 —— 没有可行动建议就不该出这一档 */
  hint: string
  /** 原始错误文本（供折叠区/上报对照；与 detail 可能相同） */
  raw: string
}

/** axios 无响应分支产出的网络错误文案（见 src/api/axios.ts 的 `!error.response` 分支） */
const NETWORK_WORDING = /网络连接失败|请求超时|Network Error|Failed to fetch|net::ERR_/i

/** 业务错误形态：带数字 code（ApiError 是普通对象，HTTP 路径才是 Error 实例） */
function businessCodeOf(error: unknown): number | undefined {
  const code = (error as { code?: unknown } | null | undefined)?.code
  return typeof code === 'number' ? code : undefined
}

/** 尽量取出可展示的原始文本 */
function rawTextOf(error: unknown): string {
  if (error instanceof Error) {
    return error.message
  }
  if (error && typeof error === 'object') {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message) {
      return message
    }
    const text = String(error)
    // `String({})` 会得到 `[object Object]`，对用户和排查都是噪声
    return text === '[object Object]' ? '' : text
  }
  return String(error ?? '')
}

/**
 * 把任意异常分档成错卡文案。
 *
 * 顺序即优先级：chunk → 网络 → 业务码 → 兜底。chunk 必须在网络之前判：
 * 两者的文本都含 "fetch"，而下一步完全不同（chunk 要硬刷新拉新 index.html，
 * 网络只需等网络恢复后重试）。
 *
 * @param error 任意异常（Error / ApiError 普通对象 / 字符串 / null 都不抛）
 * @param fallbackTitle 归不了档时的标题 —— 由调用方按**出错位置**给（渲染错卡说
 *   「页面渲染出错」、初始化错卡说「控制台初始化失败」），比统一一句更准确，
 *   也让既有契约测试的标题不断裂。
 */
export function describeUserError(error: unknown, fallbackTitle = '操作未能完成'): UserErrorCopy {
  const raw = rawTextOf(error)

  if (isChunkLoadError(error)) {
    return {
      title: '页面资源加载失败',
      detail: raw || '页面模块未能加载完成。',
      hint: '多为系统刚发布新版本或网络不稳所致：请强制刷新（Ctrl + F5）重新获取最新页面；若仍失败请稍后再试',
      raw,
    }
  }

  if (NETWORK_WORDING.test(raw)) {
    return {
      title: '网络连接失败',
      detail: raw || '请求未能送达服务器。',
      hint: '请检查本机网络后点击重试；数据不会因本次失败而丢失',
      raw,
    }
  }

  const code = businessCodeOf(error)
  if (code !== undefined) {
    const message = (error as { message?: unknown }).message
    const verdict = classifyBusinessError({
      code,
      message: typeof message === 'string' ? message : undefined,
    })
    return {
      // 业务分类已有中文标题/指引，直接用，不再自造一句（同一句话只说一遍）
      title: verdict.title,
      detail: verdict.detail,
      hint: verdict.hint ?? '请点击重试；若持续失败，请把本次操作时间告知管理员',
      raw,
    }
  }

  return {
    title: fallbackTitle,
    // detail 永不为空：错卡的副信息位要么有原文、要么有一句人话，不留空洞
    detail: raw || '发生未预期的错误。',
    hint: '请点击重试；若反复出现，请把本页地址与操作时间告知管理员以便定位',
    raw,
  }
}

/**
 * 一行式文案（`标题：详情（指引）`），与 `notificationText` 同构，
 * 便于错卡副标题或 toast 复用同一句话。
 */
export function userErrorLine(copy: UserErrorCopy): string {
  const body = copy.detail && copy.detail !== copy.title ? `${copy.title}：${copy.detail}` : copy.title
  return copy.hint ? `${body}（${copy.hint}）` : body
}

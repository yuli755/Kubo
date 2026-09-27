import type { ErrorReport } from './errorHandler'

/**
 * # 前端错误上报客户端（生产可观测性闭环）
 *
 * 把 `errorHandler.ts` 预留的上报钩子真正接到后端：错误经 `POST /api/error-report` 落后端
 * 结构化日志（Loki 采集），不再停留在「只落浏览器 console」。与后端
 * `cn.hamm.spms.common.errorreport.ErrorReportController` 配套。
 *
 * ## 设计要点（防「错误风暴」把后端打爆 + 防自我递归）
 * - **去重**：同一 `source:message` 在窗口内（1 分钟）只报一次，风暴场景下重复错误被折叠；
 * - **失败静默**：上报失败仅忽略（不 console 刷屏、不递归上报，避免死循环），错误上报
 *   永远不得反过来影响业务；
 * - **keepalive**：页面跳转/卸载时尽力把最后一批错误发出去；
 * - **内存保护**：去重 Map 超阈值时清理过期项，防止长会话内存无限增长；
 * - **脱敏**：上报前抹掉 message/stack/context 里的凭证类键值（token / password /
 *   apiKey / Bearer …），错误堆栈里常夹带请求 URL (?accessToken=) 与 axios 配置对象，
 *   后端日志（Loki）不应成为第二个明文凭证存储点。
 */

const REPORT_URL = '/api/error-report'
const DEDUP_WINDOW_MS = 60_000
const MAX_KEY_LENGTH = 200
const MAX_DEDUP_SIZE = 1000
/** 与后端 ErrorReportController 对齐：message/stack 上限与 source/context 短字段上限 */
const MAX_MESSAGE_LENGTH = 2000
const MAX_SHORT_FIELD_LENGTH = 200

/**
 * 凭证类键值脱敏规则。覆盖三种常见泄漏形态：
 * - `accessToken=eyJhbGci...` / `"password": "..."`（query / JSON / k=v）
 * - `Authorization: Bearer eyJhbGci...`
 * 替换后保留键名便于排查（知道「这里曾经有 token」但拿不到值）。
 */
const REDACT_PATTERNS: RegExp[] = [
  /\b(accessToken|access_token|refreshToken|refresh_token|token|apiKey|api_key|password|passwd|secret|authorization)("\s*[:=]\s*|\s*[=:]\s*)(?:"[^"\r\n]*"|'[^'\r\n]*'|Bearer\s+[\w.~+/=-]+|[^\s&;,}'"]+)/gi,
  /\bBearer\s+[\w.~+/=-]+/gi,
]

/** 对自由文本做凭证脱敏（导出仅为便于测试）。 */
export function redactSecrets(text: string): string {
  let out = text.replace(REDACT_PATTERNS[0], '$1$2***')
  out = out.replace(REDACT_PATTERNS[1], 'Bearer ***')
  return out
}

/**
 * 压平换行 + 截断（导出仅为便于测试）。
 *
 * K-4 对齐：后端 error-report 对四字段做 CRLF 替换与截断，但那已是日志落盘前的
 * 最后一道；上报体在「出浏览器前」就应自净——中间还有 Loki/promtail 采集链路，
 * 且匿名端点的截断口径是服务端资源保护，前端先按同口径收敛可减小滥用载荷。
 */
export function inlineTruncate(text: string, max: number): string {
  const flat = text.replace(/[\r\n]+/g, ' ')
  return flat.length > max ? `${flat.slice(0, max)}…(truncated)` : flat
}

const lastReported = new Map<string, number>()

function dedupKey(report: ErrorReport): string {
  return `${report.source}:${report.message.slice(0, MAX_KEY_LENGTH)}`
}

/** 清理过期的去重记录（Map 超阈值时触发，避免长会话内存增长） */
function prune(now: number): void {
  if (lastReported.size <= MAX_DEDUP_SIZE) {
    return
  }
  for (const [key, time] of lastReported) {
    if (now - time >= DEDUP_WINDOW_MS) {
      lastReported.delete(key)
    }
  }
}

/** 上报单条错误到后端。脱敏 + 去重 + 失败静默。 */
export function reportErrorToServer(report: ErrorReport): void {
  const key = dedupKey(report)
  const now = Date.now()
  const last = lastReported.get(key)
  if (last !== undefined && now - last < DEDUP_WINDOW_MS) {
    return
  }
  lastReported.set(key, now)
  prune(now)

  // 脱敏在去重之后：去重键用原文（不同 token 的同类错误应折叠），
  // 但发出的 body 只含脱敏后的文本。不改动调用方传入的对象。
  // 自由文本统一「脱敏 → 压平换行 → 截断」，与后端 sanitize/sanitizeShort 同口径；
  // source 是 errorHandler 里的常量联合类型（非自由文本），无需前端处理。
  const sanitized: ErrorReport = {
    ...report,
    message: inlineTruncate(redactSecrets(report.message), MAX_MESSAGE_LENGTH),
  }
  if (report.stack) {
    sanitized.stack = inlineTruncate(redactSecrets(report.stack), MAX_MESSAGE_LENGTH)
  }
  if (report.context) {
    sanitized.context = inlineTruncate(redactSecrets(report.context), MAX_SHORT_FIELD_LENGTH)
  }

  fetch(REPORT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(sanitized),
    keepalive: true,
  }).catch(() => {
    // 上报失败静默：不递归上报、不阻塞业务
  })
}

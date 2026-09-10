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
 * - **内存保护**：去重 Map 超阈值时清理过期项，防止长会话内存无限增长。
 */

const REPORT_URL = '/api/error-report'
const DEDUP_WINDOW_MS = 60_000
const MAX_KEY_LENGTH = 200
const MAX_DEDUP_SIZE = 1000

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

/** 上报单条错误到后端。去重 + 失败静默。 */
export function reportErrorToServer(report: ErrorReport): void {
  const key = dedupKey(report)
  const now = Date.now()
  const last = lastReported.get(key)
  if (last !== undefined && now - last < DEDUP_WINDOW_MS) {
    return
  }
  lastReported.set(key, now)
  prune(now)

  fetch(REPORT_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(report),
    keepalive: true,
  }).catch(() => {
    // 上报失败静默：不递归上报、不阻塞业务
  })
}

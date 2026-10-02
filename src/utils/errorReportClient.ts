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
 * - **身份与追踪头（§15.8 → R40）**：裸 fetch 此前不带任何身份头，后端日志里上报是
 *   「孤儿」——无法回溯是谁、哪个租户、哪条链路。现补 `Authorization`/`X-Tenant-Id`
 *   （与 axios 拦截器同源）+ 每次上报新生成的 `X-Trace-Id`。这些头是**尽力而为**：
 *   登录页（无 store）或 pinia 未就绪的引导期报错，也必须匿名发得出——头的缺失永远
 *   不能成为不报的理由。
 */

import { useUserStore } from '@/stores/user'
import { authToken } from './authStorage'

const REPORT_URL = '/api/error-report'
const DEDUP_WINDOW_MS = 60_000
const MAX_KEY_LENGTH = 200
const MAX_DEDUP_SIZE = 1000
/** 与 `api/axios.ts` 的 TRACE_ID_HEADER 同名同义（此处不 import，保持上报器依赖面最小） */
const TRACE_ID_HEADER = 'X-Trace-Id'

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

/**
 * 尽力而为地拼身份/追踪头。
 *
 * 这里的任何失败（最常见：pinia 尚未就绪——引导期或登录页就崩了）都**绝不能**变成
 * 「不报了」：先读 store，失败退回 localStorage 的 `authToken`，再没有就匿名发。
 * 孤儿上报（无身份）只是少一半线索；不发上报是全部线索都没有。
 */
function identityHeaders(): Record<string, string> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    // 每次上报新生成：后端 MDC 关联认的是 id 本身，不是复用
    [TRACE_ID_HEADER]: crypto.randomUUID(),
  }
  let token = ''
  let tenantId: unknown
  try {
    const userStore = useUserStore()
    token = userStore.token || authToken.get()
    tenantId = userStore.tenantId
  }
  catch {
    // getActivePinia() 抛错：store 不可用，匿名上报优于沉默
    token = authToken.get()
  }
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  if (tenantId !== undefined && tenantId !== null) {
    headers['X-Tenant-Id'] = String(tenantId)
  }
  return headers
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
    headers: identityHeaders(),
    body: JSON.stringify(report),
    keepalive: true,
  }).catch(() => {
    // 上报失败静默：不递归上报、不阻塞业务
  })
}

import type { MaskPolicy } from '@kubo/shared'
import { applyMaskPolicy, normalizeMaskPolicy } from '@kubo/shared'
import { computed, shallowReactive } from 'vue'
import { dataFieldPolicyApi } from '@/api/system'
import { useUserStore } from '@/stores/user'

/**
 * # 列级脱敏策略的前端消费入口
 *
 * ## 为什么需要它
 *
 * 后端 `GET /dataFieldPolicy/my-mask` 才是"当前这个人能不能看这个字段"的**唯一权威**：
 * 角色取自服务端权限上下文，`null` 表示无需脱敏。在此之前，`v-mask` 指令自带一套
 * 本地规则（phone / idCard / …），后端策略对渲染**毫无作用** —— 等价于"前端自己决定
 * 遮多少"，正是改造方案里要消灭的第二套实现。这个 composable 把两者接起来，
 * 并处理三件端点本身不负责的事：缓存、并发去重、身份切换失效。
 *
 * ## 缓存必须绑定身份（前端的"串号"风险）
 *
 * `my-mask` 的返回值是**按用户解析**的结果，所以缓存键不能只有 `resourceKey#field`。
 * 这里用"当前会话 token 变了就整表清空"来兜住：token 在登录 / 登出 / refresh 时都会变，
 * 因此不会出现"A 用户的明文策略被 B 用户命中"。（历史上后端行权限缓存就因为这个串过号，
 * 见 `SPMS-Server` 里 `JpaPermissionRuleProvider` 的注释。）
 *
 * ## 失败取向
 *
 * 请求失败 ≠ 无需脱敏。失败时条目记为 `failed`，调用方（`v-mask`）退回本地规则遮蔽，
 * 只有后端明确返回 `null`（真没策略）或 `allowPlaintext` 才显示明文。
 */

interface MaskPolicyEntry {
  /** 后端返回的原始策略；`null` = 该字段对当前用户无需脱敏 */
  policy: MaskPolicy | null
  /** 是否已从后端取回（成功或"确认无策略"） */
  loaded: boolean
  /** 取回失败：调用方应按"敏感"处理，不能当成无需脱敏 */
  failed: boolean
}

const store = shallowReactive<Record<string, MaskPolicyEntry>>({})
const inflight = new Map<string, Promise<void>>()

/** 缓存归属的会话指纹（不导出，只用于"换人就清空"） */
let scopeToken: string | null = null

function keyOf(resourceKey: string, field: string): string {
  return `${resourceKey}#${field}`
}

/**
 * 取当前会话指纹
 *
 * `useUserStore()` 需要 pinia 已激活；单元测试 / 极早期调用时可能还没有，
 * 这种情况按"无 token"处理（缓存照常工作，只是不做身份切换失效）。
 */
function currentToken(): string | null {
  try {
    return useUserStore().token || null
  }
  catch {
    return null
  }
}

function resetIfIdentityChanged(): void {
  const token = currentToken()
  if (token === scopeToken) {
    return
  }
  scopeToken = token
  Object.keys(store).forEach((key) => {
    delete store[key]
  })
  inflight.clear()
}

/**
 * 清空策略缓存（登出、切换租户后调用；单元测试也用得上）
 */
export function clearMaskPolicyCache(): void {
  scopeToken = null
  Object.keys(store).forEach((key) => {
    delete store[key]
  })
  inflight.clear()
}

/**
 * 取回并缓存某资源字段对当前用户生效的策略
 *
 * 同一 (resourceKey, field) 的并发调用共享一个请求；已加载的直接命中缓存不再请求。
 *
 * @param resourceKey 数据资源标识，如 `channel.purchasePrice`（必须是后端 `data_resource` 注册过的键）
 * @param field 字段名，如 `price`
 */
export async function ensureMaskPolicy(resourceKey: string, field: string): Promise<MaskPolicyEntry> {
  resetIfIdentityChanged()
  const key = keyOf(resourceKey, field)
  const hit = store[key]
  if (hit?.loaded || hit?.failed) {
    return hit
  }
  const running = inflight.get(key)
  if (running) {
    await running
    return store[key] ?? { policy: null, loaded: false, failed: true }
  }
  const task = (async () => {
    try {
      const policy = await dataFieldPolicyApi.getMyMask(resourceKey, field)
      store[key] = { policy: policy ?? null, loaded: true, failed: false }
    }
    catch {
      store[key] = { policy: null, loaded: false, failed: true }
    }
    finally {
      inflight.delete(key)
    }
  })()
  inflight.set(key, task)
  await task
  return store[key] ?? { policy: null, loaded: false, failed: true }
}

/**
 * 同步读取已缓存的策略条目（`undefined` 表示尚未取回）
 */
export function maskPolicyEntry(resourceKey: string, field: string): MaskPolicyEntry | undefined {
  resetIfIdentityChanged()
  return store[keyOf(resourceKey, field)]
}

/**
 * 组合式入口：在组件里按 `resourceKey` 批量声明敏感字段
 *
 * ```ts
 * const { masked, hasRestrictedField } = useMaskPolicy('channel.purchasePrice', ['price'])
 * // 模板：{{ masked('price', row.price, 'phone') }}
 * ```
 *
 * @param resourceKey 数据资源标识
 * @param fields 该视图会用到的字段名（挂载时一次性发起策略查询）
 */
export function useMaskPolicy(resourceKey: string, fields: string[] = []) {
  function load(): void {
    fields.forEach(field => ensureMaskPolicy(resourceKey, field))
  }

  /**
   * 渲染值：以 `store` 里的后端策略为准，策略到达后调用方会随之重渲染。
   *
   * 策略还在路上时**先全遮**（`****`）而不是显示明文——宁可晚一点出现真值，
   * 也不能在弱网下闪出价格 / 身份证。
   */
  function masked(field: string, value: unknown): unknown {
    const entry = maskPolicyEntry(resourceKey, field)
    if (!entry || (!entry.loaded && !entry.failed)) {
      load()
      // 未就绪：先按"敏感"处理，避免策略到达前闪出明文
      return value === null || value === undefined ? value : '****'
    }
    if (entry.failed) {
      return '****'
    }
    return applyMaskPolicy(value, entry.policy)
  }

  /**
   * 该字段当前是否应遮蔽显示
   *
   * 与 `masked()` 的区别是只回答"要不要遮"，用于决定渲染分支
   * （例如：不遮时走框架的 `AMoney` 金额格式化，遮时输出纯文本）。
   * 策略未取回 / 取回失败都算"应遮蔽"——不能拿"还不知道"当"可以看"。
   */
  function restricted(field: string): boolean {
    const entry = maskPolicyEntry(resourceKey, field)
    if (!entry || (!entry.loaded && !entry.failed)) {
      load()
      return true
    }
    return entry.failed || normalizeMaskPolicy(entry.policy).masked
  }

  /** 当前视图是否存在被脱敏的字段（用于提示条 / 图标） */
  const hasRestrictedField = computed(() =>
    fields.some(field => normalizeMaskPolicy(maskPolicyEntry(resourceKey, field)?.policy).masked),
  )

  return {
    load,
    masked,
    restricted,
    hasRestrictedField,
  }
}

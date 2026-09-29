/**
 * # 业务错误码 → 用户可操作的提示（纯逻辑层，不依赖 Vue / element-plus）
 *
 * ## 为什么要有这一层
 *
 * 后端把所有业务失败都渲染成 **HTTP 200 + `{ code, message }`**
 * （`BusinessExceptionAdvice` / `RequestBindingExceptionAdvice` / `DataIntegrityViolationAdvice`
 * 三个 advice 全部标了 `@ResponseStatus(HttpStatus.OK)`，注释里写明"沿用 HTTP 200 + 业务码，
 * 不擅自改成真实 401/403"）。这带来一个直接后果：
 *
 * - axios 拦截器里按 **HTTP status** 分流的 `case 403` / `case 422` 分支，对后端业务错误是**死分支**
 *   （只有网关/Spring Security 直出的 403 才会走到）；
 * - 真正承接业务错误的是「HTTP 200 但 code≠200」那一支，而它此前**只 reject、不提示** ——
 *   用户点「保存」后看到的是"什么都没发生"，只能去开控制台。
 *
 * 所以要把「码 → 说什么」收敛成一处可测的纯函数：**同一类错误在任何页面都得到同一句话**，
 * 且这句话必须**可操作**（告诉用户下一步做什么），而不是把后端异常文本原样甩给用户。
 *
 * ## 边界
 *
 * - 本文件**不弹提示**（不 import element-plus），只产出「该说什么、要不要说、说给谁」。
 *   弹窗与去重时机由 `src/api/axios.ts` 决定，表单定位由 `src/stores/fieldError.ts` 承接 ——
 *   这样分类逻辑能在 node 环境下被穷举测试，不需要挂 jsdom。
 * - 码表与后端 `Errors` / `CustomError` 一一对应；新增码请**同时**改这里与后端，
 *   `errorPolicy.spec.ts` 会钉住已知码的归类。
 */

/**
 * 后端业务码表（与 `cn.hamm.spms.common.exception.Errors` / `CustomError` 对齐）。
 *
 * 常量化而不是在代码里散写 `4002`：读代码的人不必去猜数字含义，
 * 改口径时也只需改一处。
 */
export const BusinessCode = {
  /** 缺少必要参数（"没传"） */
  PARAM_MISSING: 4001,
  /** 参数取值不合法（"传了但自相矛盾"） */
  PARAM_INVALID: 4002,
  /** 无权限（授权边界，不是输入错误） */
  FORBIDDEN: 403,
  /** 数据不存在 */
  DATA_NOT_FOUND: 404,
  /** 服务端内部错误 */
  SERVICE_ERROR: 500,
  /** 登录账号或密码不正确（由登录页自己呈现，不走全局提示） */
  USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID: 200001,
} as const

/** 细粒度权限码区间：`FORBIDDEN_*`(4031..4039)，见后端 `Errors` */
export const FORBIDDEN_CODE_MIN = 4031
export const FORBIDDEN_CODE_MAX = 4039

/**
 * 同一「码 + 路径」的重复提示抑制窗口（毫秒）。
 *
 * 为什么需要：一个列表页常常并发 3~5 个请求（列表 + 统计 + 字典 + 权限），
 * 权限没开通时它们会**同时**失败 —— 逐个弹窗就是 5 条一模一样的红条糊满屏幕，
 * 用户既看不清也关不掉。3 秒窗口内同一来源只提示一次。
 */
export const NOTIFY_DEDUPE_MS = 3000

/** 错误类别：决定标题、提示语气与是否交给调用方自己处理 */
type BusinessErrorKind = 'forbidden' | 'param' | 'notFound' | 'server' | 'credential' | 'unknown'

/** 分类结果 */
interface ClassifiedBusinessError {
  /** 归类 */
  kind: BusinessErrorKind
  /** 原始业务码（回传用） */
  code: number
  /** 一句话标题（"无权限" / "提交内容有误" …） */
  title: string
  /** 具体原因，通常来自后端 message */
  detail: string
  /** 可操作的下一步指引；没有可行建议时不给（不写废话） */
  hint?: string
  /** 从后端文案里解析出的字段名（用于把焦点定位到出错控件） */
  field?: string
  /** 交给调用方自己呈现（如登录失败），全局不弹 */
  silent: boolean
}

/** 分类入参 */
interface BusinessErrorInput {
  code: number
  message?: string
  data?: unknown
}

/** 这些码由具体页面自己呈现，全局不弹（弹了就是双份提示） */
const SILENT_CODES = new Set<number>([
  BusinessCode.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID,
])

/** 403 与 4031..4039 都算授权失败 */
export function isForbiddenCode(code: number): boolean {
  return code === BusinessCode.FORBIDDEN
    || (code >= FORBIDDEN_CODE_MIN && code <= FORBIDDEN_CODE_MAX)
}

/** 4001 / 4002 都算「调用方给的东西不对」 */
export function isParamCode(code: number): boolean {
  return code === BusinessCode.PARAM_MISSING || code === BusinessCode.PARAM_INVALID
}

/**
 * 从后端文案里解析出**第一个**字段名。
 *
 * 后端有两种写法（都在 `RequestBindingExceptionAdvice` 里，属于稳定契约）：
 * 1. `字段名: 消息`（`BindException` / 约束违规，最多 3 条用 `；` 连接，如 `email: 邮箱格式不正确`）；
 * 2. `缺少必要的请求参数「name」` / `参数「name」取值不合法，应为...` / `字段「name」取值与期望类型不符...`。
 *
 * 只取第一个：焦点只能落在一个控件上，剩下的字段仍完整显示在 detail 里。
 * 解析不出来就返回 undefined —— 宁可不做定位，也不要猜错字段把用户带到无关的输入框。
 */
export function extractFieldName(message?: string): string | undefined {
  if (!message) {
    return undefined
  }
  const bracketed = message.match(/[「『]([^」』\n]{1,64})[」』]/)
  if (bracketed?.[1]) {
    return bracketed[1].trim()
  }
  // 形如 `email: 邮箱格式不正确`；字段名限定为标识符，避免把普通中文句子误判成字段
  const leading = message.match(/^\s*([a-z_$][\w$]{0,63})\s*[:：]\s*\S/i)
  if (leading?.[1]) {
    return leading[1]
  }
  // 多条用 `；` 连接时，第一条已在上面匹配过；这里兜住「前面有前缀文字」的情况
  const inline = message.match(/(?:^|[；;，,]\s*)([a-z_$][\w$]{0,63})\s*[:：]\s*\S/i)
  return inline?.[1]
}

/**
 * 从 403 文案里提取**权限码**。
 *
 * 后端授权失败的真实文案（2026-09 真机抓取，`POST /permission/getList`，受限账号）：
 * `你无权访问 权限-不分页查询 (Permission_getList)` —— 括号里就是权限码。
 * 把它单拎出来放进指引，用户截图给管理员时管理员能直接搜到那一条授权，
 * 不必再猜"到底是哪个权限"。抓不到就退回通用指引，不硬编。
 */
export function extractPermissionCode(message?: string): string | undefined {
  if (!message) {
    return undefined
  }
  // 只认「句尾括号里的标识符」：`[a-z]\w*` 已含下划线（Permission_getList），
  // 中文括号内容（如 `(权限-不分页查询)`）匹配不上，正好避免把说明文字当成权限码
  const matched = message.match(/[（(]\s*([a-z]\w*)\s*[)）]\s*$/i)
  return matched?.[1]
}

/**
 * 把业务码分类成「该说什么」。
 *
 * @param input 业务码与后端消息
 * @return 分类结果（标题 / 详情 / 指引 / 字段 / 是否静默）
 */
export function classifyBusinessError(input: BusinessErrorInput): ClassifiedBusinessError {
  const code = Number(input.code)
  const detail = (input.message ?? '').trim() || '请求未成功'

  if (SILENT_CODES.has(code)) {
    return { kind: 'credential', code, title: '登录未通过', detail, silent: true }
  }
  if (isForbiddenCode(code)) {
    const permissionCode = extractPermissionCode(detail)
    return {
      kind: 'forbidden',
      code,
      title: '无权限',
      detail,
      // 可操作：说清"找谁、开通哪一条"，而不是只说"不行"
      hint: permissionCode
        ? `请联系管理员为当前角色开通权限「${permissionCode}」`
        : '当前角色未被授予该操作，请联系管理员在角色授权里开通后重试',
      silent: false,
    }
  }
  if (isParamCode(code)) {
    const field = extractFieldName(detail)
    return {
      kind: 'param',
      code,
      title: code === BusinessCode.PARAM_MISSING ? '缺少必要信息' : '提交内容有误',
      detail,
      hint: field ? `请检查「${field}」这一项` : '请检查提交内容后重试',
      field,
      silent: false,
    }
  }
  if (code === BusinessCode.DATA_NOT_FOUND) {
    return {
      kind: 'notFound',
      code,
      title: '数据不存在',
      detail,
      hint: '可能已被删除或页面数据已过期，请刷新后重试',
      silent: false,
    }
  }
  if (code === BusinessCode.SERVICE_ERROR) {
    return {
      kind: 'server',
      code,
      title: '服务器处理失败',
      detail,
      hint: '请稍后重试；若持续失败，请把本次操作时间与 traceId 告知管理员',
      silent: false,
    }
  }
  return { kind: 'unknown', code, title: '请求失败', detail, silent: false }
}

/**
 * 拼出最终要显示的一句话：`标题：详情（指引）`。
 *
 * 详情与指引都缺失时只留标题，避免出现 `无权限：（）` 这种空壳文案。
 */
export function notificationText(verdict: ClassifiedBusinessError): string {
  const parts = [verdict.title]
  if (verdict.detail && verdict.detail !== verdict.title) {
    parts.push(verdict.detail)
  }
  const body = parts.join('：')
  return verdict.hint ? `${body}（${verdict.hint}）` : body
}

/** 去重键：同一「码 + 接口」算同一件事 */
export function notifyKey(code: number, path?: string): string {
  return `${code}@${path ?? ''}`
}

const lastNotifiedAt = new Map<string, number>()

/**
 * 该不该弹这条提示（同一 `notifyKey` 在 {@link NOTIFY_DEDUPE_MS} 内只弹一次）。
 *
 * @param key `notifyKey()` 的结果
 * @param now 当前时间（可注入，便于测试）
 * @return true 表示应当提示
 */
export function shouldNotify(key: string, now: number = Date.now()): boolean {
  const last = lastNotifiedAt.get(key)
  if (last !== undefined && now - last < NOTIFY_DEDUPE_MS) {
    return false
  }
  lastNotifiedAt.set(key, now)
  return true
}

/** 清空去重记录（测试与"手动重试"场景用） */
export function resetNotifyDedupe(): void {
  lastNotifiedAt.clear()
}

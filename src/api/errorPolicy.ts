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
  /**
   * 需要登录（`Errors.UNAUTHORIZED`，"获取你的身份信息失败，请重新登录后再试"）。
   *
   * 注意：它以**业务码**形式出现在 HTTP 200 里时，axios 的 HTTP-401 刷新/登出流程**不会**触发，
   * 所以必须在这里给出"请重新登录"的指引，否则会退化成一句无信息的"请求失败"。
   */
  UNAUTHORIZED: 401,
  /** 数据不存在 */
  DATA_NOT_FOUND: 404,
  /**
   * 接口未开放（`Errors.API_SERVICE_UNSUPPORTED`，枚举原文"请求的接口暂未实现"）。
   *
   * ## 它不是 404，也不是 403，更不是 500
   * 后端每个资源的端点集合由 `@Extends(value = {...}, exclude = {...})` 显式开关
   * （基路径来自自定义 `@Api("input")`，全后端零路径变量，一律 `POST /{base}/{action}`）。
   * 被 `exclude` 掉的端点**仍然注册着**，只是 `BaseController.checkApiAvailable` 在运行时抛这个码。
   * 真机核实（2026-09）：`input` / `purchase` / `move` / `output` / `sale` / `supplier` 的 `delete`
   * 都返回 HTTP 200 + `{ code: 501, message: "当前接口不支持此操作" }`。
   *
   * 所以三件事都不成立：路由存在（不是 404）、跟角色授权无关（不是 403，找管理员开权限也没用）、
   * 服务端没坏（不是 500，重试不会成功）。它是**产品决定**：这个模块刻意不提供该能力
   * （如入库单不允许物理删除，要留审计痕迹）。用户能做的下一步是"别再试"，不是"再试一次"。
   */
  API_UNSUPPORTED: 501,
  /** 服务端内部错误 */
  SERVICE_ERROR: 500,
  /** 登录账号或密码不正确（由登录页自己呈现，不走全局提示） */
  USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID: 200001,
} as const

/**
 * 后端「403 家族」的数字区间：`FORBIDDEN_*` / `DATABASE_CONCURRENT_ERROR`(4031..4039)。
 *
 * ## 为什么区间**不等于**授权失败（读 `Errors` 枚举才发现的坑）
 * 后端把 4031..4039 归在注释「403 无权限代码」下面，但它们的**语义各不相同**：
 *
 * | 码 | 后端常量 | 真实含义 |
 * | --- | --- | --- |
 * | 4031 | `FORBIDDEN_EXIST` | 数据已存在（冲突） |
 * | 4032/4033 | `FORBIDDEN_EDIT`/`_DELETE` | 修改/删除被业务规则拒绝（**也可能**是所有权校验） |
 * | 4034 | `FORBIDDEN_DELETE_USED` | 数据正在使用中（前置条件） |
 * | 4035 | `FORBIDDEN_UPLOAD_MAX_SIZE` | 上传文件过大（请求载荷） |
 * | 4036/4037/4038 | `FORBIDDEN_DISABLED*` | 记录/应用已禁用、当前状态不允许禁用 |
 * | 4039 | `DATABASE_CONCURRENT_ERROR` | 系统繁忙（可重试） |
 *
 * 若按数字区间一律归成「无权限，请联系管理员开通权限」，用户在"文件太大"或"数据已存在"时
 * 会被告知去找管理员开权限 —— 那是**假指引**，比不指引更坏（用户会照着做，且白跑一趟）。
 * 所以：**只有 403 本身**默认按授权失败处理，4031..4039 逐码给语义；
 * 唯一的例外是后端文案本身就在说权限（如 `你没有权限修改此令牌`，来自所有权校验），那时才归为无权限。
 */
export const LEGACY_FORBIDDEN_FAMILY_MIN = 4031
export const LEGACY_FORBIDDEN_FAMILY_MAX = 4039

/**
 * 同一「码 + 路径」的重复提示抑制窗口（毫秒）。
 *
 * 为什么需要：一个列表页常常并发 3~5 个请求（列表 + 统计 + 字典 + 权限），
 * 权限没开通时它们会**同时**失败 —— 逐个弹窗就是 5 条一模一样的红条糊满屏幕，
 * 用户既看不清也关不掉。3 秒窗口内同一来源只提示一次。
 */
export const NOTIFY_DEDUPE_MS = 3000

/** 错误类别：决定标题、提示语气与是否交给调用方自己处理 */
type BusinessErrorKind
  = 'forbidden'
    | 'denied'
    | 'conflict'
    | 'param'
    | 'notFound'
    | 'unsupported'
    | 'server'
    | 'credential'
    | 'unknown'

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
  /**
   * 调用方已确定这是**授权失败**（而非业务规则拒绝），文案不参与判断。
   *
   * 用途：HTTP 层直出的 403（网关 / Spring Security）根本没有业务文案，只有 `Forbidden` 这类
   * 状态短语 —— 按措辞判断会把它降级成"操作未被允许"，丢掉"找管理员开通"的正确指引。
   * 业务码路径不要传这个标志，让文案说话。
   */
  authorizationKnown?: boolean
}

/** 这些码由具体页面自己呈现，全局不弹（弹了就是双份提示） */
const SILENT_CODES = new Set<number>([
  BusinessCode.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID,
])

/**
 * 登录页要自己呈现的那一类失败见文件底 `loginInlineError`（R-37 契约的落地处）。
 */

/**
 * 4031..4039 的**逐码语义**（键与后端 `Errors` 常量一一对应，见上方区间常量的注释表）。
 *
 * 每码给「标题 + 下一步」，而不是共用一句"无权限"：
 * 冲突要说"别重复添加"，文件过大要说"压缩后重传"，系统繁忙要说"稍后重试"。
 */
const LEGACY_FORBIDDEN_SEMANTICS: Record<number, { kind: BusinessErrorKind, title: string, hint: string }> = {
  4031: { kind: 'conflict', title: '数据已存在', hint: '请改用查询或编辑现有记录，不要重复添加' },
  4032: { kind: 'denied', title: '修改未被允许', hint: '按提示调整后重试；若认为不该受限，请联系管理员确认' },
  4033: { kind: 'denied', title: '删除未被允许', hint: '按提示调整后重试；若认为不该受限，请联系管理员确认' },
  4034: { kind: 'conflict', title: '数据正在使用中', hint: '先解除引用它的单据或档案，再执行删除' },
  4035: { kind: 'param', title: '文件超过大小限制', hint: '请压缩或拆分文件后重新上传' },
  4036: { kind: 'denied', title: '数据已被禁用', hint: '请先启用该记录，或联系管理员确认是否应保留' },
  4037: { kind: 'denied', title: '应用已被禁用', hint: '请联系管理员启用该应用后重试' },
  4038: { kind: 'denied', title: '当前状态不允许禁用', hint: '请先处理依赖它的数据，再执行禁用' },
  4039: { kind: 'server', title: '系统繁忙', hint: '请稍后重试；反复出现请把 traceId 告知管理员' },
}

/**
 * 后端文案里是否在讲**权限**。
 *
 * 403 家族同时承载两类失败，而它们的下一步完全不同：
 * - 授权失败（`你无权访问 … (Permission_getList)`、`数据权限不足…`、`你没有权限修改此令牌`）→ 找管理员开通；
 * - 状态机/业务规则拒绝（`该单据状态无法审核`、`令牌数量已达上限`）→ 用户自己调整数据或流程。
 * 判据只能取文案：状态机类文案里不会出现"权限"二字。
 */
const AUTHORIZATION_WORDING = /权限|无权|未授权|不允许访问/

/** 文案里明确在说权限 → 按授权失败处理（哪怕码是 403x） */
export function mentionsAuthorization(message: string): boolean {
  return AUTHORIZATION_WORDING.test(message)
}

/**
 * 是否为**真正的授权失败码**。
 *
 * 只认 403：4031..4039 是语义混杂的遗留区间（见 {@link LEGACY_FORBIDDEN_FAMILY_MIN} 的注释表），
 * 把它们算进来就会给"文件过大"配上"去找管理员开权限"的假指引。
 */
export function isForbiddenCode(code: number): boolean {
  return code === BusinessCode.FORBIDDEN
}

/** 是否落在遗留「403 家族」区间（4031..4039） */
export function isLegacyForbiddenFamily(code: number): boolean {
  return code >= LEGACY_FORBIDDEN_FAMILY_MIN && code <= LEGACY_FORBIDDEN_FAMILY_MAX
}

/** 4001 / 4002 都算「调用方给的东西不对」 */
export function isParamCode(code: number): boolean {
  return code === BusinessCode.PARAM_MISSING || code === BusinessCode.PARAM_INVALID
}

/**
 * 是否为「该模块未开放此接口」（`@Extends` 排除 → 运行时 501）。
 *
 * 与 403 的区别是**下一步完全不同**：403 让用户找管理员开权限，501 让用户别再试。
 * 若把它并进 403 家族，用户会照着假指引白跑一趟（管理员那边根本没有对应的授权项可开）。
 */
export function isUnsupportedCode(code: number): boolean {
  return code === BusinessCode.API_UNSUPPORTED
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
  // 403 与 403x 家族：后端把「授权失败」与「业务规则/状态机拒绝」**都**渲染成 403 家族
  // （前者如 `你无权访问 权限-不分页查询 (Permission_getList)`、`数据权限不足…`；
  //   后者如 `该单据状态无法审核`、`令牌数量已达上限`、`该明细已标记完成…`）。
  // 只有**文案**能区分二者：状态机类文案里不会出现"权限"二字。
  // 分不清就按"操作未被允许"处理 —— 宁可少给一句指引，也不要让用户白跑一趟去找管理员开权限。
  if (isForbiddenCode(code) || isLegacyForbiddenFamily(code)) {
    if (input.authorizationKnown === true || mentionsAuthorization(detail)) {
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
    const legacy = LEGACY_FORBIDDEN_SEMANTICS[code]
    if (legacy) {
      return {
        kind: legacy.kind,
        code,
        title: legacy.title,
        detail,
        hint: legacy.hint,
        // 文案里若带了字段名，顺手给表单定位
        field: extractFieldName(detail),
        silent: false,
      }
    }
    return {
      kind: 'denied',
      code,
      title: '操作未被允许',
      detail,
      hint: '按提示调整后重试；若认为不该受限，请联系管理员确认',
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
  if (code === BusinessCode.UNAUTHORIZED) {
    return {
      kind: 'credential',
      code,
      title: '登录状态已失效',
      detail,
      // 后端原文里通常已经写了"请重新登录"，再挂一条同样的指引就是把同一句话说两遍
      hint: /登录/.test(detail) ? undefined : '请重新登录后再试',
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
  if (isUnsupportedCode(code)) {
    return {
      kind: 'unsupported',
      code,
      title: '此操作在当前模块不可用',
      detail,
      // 后端原文"当前接口不支持此操作"没有说清"为什么"与"该怎么办"，这里补上：
      // 不是权限问题（找管理员开通也没用）、不是故障（重试不会成功），是模块刻意没开这个能力
      hint: '该模块未开放此接口，重试不会成功；如确需此功能请联系管理员确认',
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

/**
 * R-37 契约：登录失败的**页面内联**呈现（`login.vue` 三个登录入口的 catch 调用）。
 *
 * ## 为什么需要一个可执行的契约
 * `SILENT_CODES` 的注释写着「由登录页自己呈现」，但 R-37 真机复现（2026-10-01）：
 * `{ code: 200001, message: 用户账号或密码错误 }`（HTTP 200）→ 全局按约定静默 →
 * 登录页 catch 里只有 `reportError`（只落 console）→ 用户点「立即登录」后**界面毫无变化**，
 * 不知道密码错了——正是本文件头注释批判的「点了没反应」在登录页的复刻。
 * 「silent 的前提是有人呈现」必须落成函数，而不是注释里的君子协定。
 *
 * ## 分工（同一条失败只说一遍）
 * - classify 结果为 `credential`（200001；登录页场景下撞到的 401 也算）→ 返回要显示的一句话，
 *   页面把它渲染在提交按钮上方；
 * - 其余业务/HTTP 码 → 返回 undefined：响应拦截器的全局 toast 已经说了同一句话，
 *   页面再显示就是双份；
 * - 网络错误/超时（reject 的是没有数字 `code` 的 `Error`）→ 同样 undefined（拦截器直接弹
 *   「网络连接失败 / 请求超时」）。
 *
 * 入参是 `unknown`：业务码路径 reject 的是 `ApiError` **普通对象**（非 Error 实例），
 * HTTP 路径 reject 的才是带 `code` 的 `Error` —— 两种形态都要能读数。
 */
export function loginInlineError(error: unknown): string | undefined {
  const code = (error as { code?: unknown } | null | undefined)?.code
  if (typeof code !== 'number') {
    return undefined
  }
  const message = (error as { message?: unknown } | null | undefined)?.message
  const verdict = classifyBusinessError({ code, message: typeof message === 'string' ? message : undefined })
  return verdict.kind === 'credential' ? notificationText(verdict) : undefined
}

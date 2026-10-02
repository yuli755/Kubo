import { describe, expect, it } from 'vitest'
import {
  BusinessCode,
  classifyBusinessError,
  extractFieldName,
  extractPermissionCode,
  isForbiddenCode,
  isLegacyForbiddenFamily,
  isParamCode,
  isUnsupportedCode,
  LEGACY_FORBIDDEN_FAMILY_MAX,
  LEGACY_FORBIDDEN_FAMILY_MIN,
  loginInlineError,
  mentionsAuthorization,
  NETWORK_ERROR_CODE,
  notificationText,
  NOTIFY_DEDUPE_MS,
  notifyKey,
  resetNotifyDedupe,
  shouldNotify,
} from '@/api/errorPolicy'

/**
 * # 业务码 → 用户提示的分类契约
 *
 * 这一层是「同一类错误在任何页面都得到同一句话」的唯一保障，所以按码表穷举，
 * 而不是只挑几个顺手的测。后端所有业务失败都是 **HTTP 200 + 业务码**
 * （三个 advice 全标了 `@ResponseStatus(HttpStatus.OK)`），也就是说：
 * 这里的归类错了，用户在界面上看到的就是错的 —— 编译器和 HTTP 状态码都拦不住。
 */

describe('errorPolicy · 码表归类', () => {
  it('403 是授权失败，给出「找谁开通哪条权限」的可操作指引', () => {
    const verdict = classifyBusinessError({ code: 403, message: '权限不足' })
    expect(verdict.kind).toBe('forbidden')
    expect(verdict.title).toBe('无权限')
    expect(verdict.detail).toBe('权限不足')
    expect(verdict.silent).toBe(false)
    expect(verdict.hint, '只说「不行」而不说「怎么办」等于没说').toContain('管理员')
  })

  it('4031..4039 是语义混杂的遗留区间，逐码说各自的下一步', () => {
    // 键与后端 Errors 常量一一对应：FORBIDDEN_EXIST / _DELETE_USED / _UPLOAD_MAX_SIZE / _DISABLED / DATABASE_CONCURRENT_ERROR
    const cases: [number, string, string][] = [
      [4031, '数据已存在', '不要重复添加'],
      [4034, '数据正在使用中', '先解除'],
      [4035, '文件超过大小限制', '压缩'],
      [4036, '数据已被禁用', '启用'],
      [4037, '应用已被禁用', '管理员'],
      [4039, '系统繁忙', '稍后重试'],
    ]
    for (const [code, title, hintPart] of cases) {
      const verdict = classifyBusinessError({ code, message: '后端原文' })
      expect(verdict.title, `code=${code}`).toBe(title)
      expect(verdict.hint, `code=${code}`).toContain(hintPart)
      expect(verdict.silent, `code=${code}`).toBe(false)
    }
  })

  it('回归锁：文件过大（4035）绝不能让用户去找管理员开权限', () => {
    // 曾经的实现按数字区间把 4031..4039 一律归成「无权限」，于是"文件太大"会配上
    // "请联系管理员开通权限"的假指引 —— 用户照着做也解决不了问题，还白跑一趟
    const text = notificationText(classifyBusinessError({ code: 4035, message: '上传的文件大小超过最大限制' }))
    expect(text).not.toContain('开通权限')
    expect(text).not.toContain('角色授权')
    expect(text).toContain('压缩')
  })

  it('4032 的文案在说权限时（所有权校验）仍归为无权限，不被"修改未被允许"盖掉', () => {
    // 真机同源：PersonalTokenController 用 FORBIDDEN_EDIT.whenNotEquals(owner, currentUser, "你没有权限修改此令牌")
    const verdict = classifyBusinessError({ code: 4032, message: '你没有权限修改此令牌' })
    expect(verdict.kind).toBe('forbidden')
    expect(verdict.title).toBe('无权限')
    expect(verdict.hint).toContain('管理员')
    expect(mentionsAuthorization('你没有权限修改此令牌')).toBe(true)
    expect(mentionsAuthorization('添加失败，数据已存在')).toBe(false)
  })

  it('4032 没有权限措辞时按码给"修改未被允许"（业务规则拒绝）', () => {
    const verdict = classifyBusinessError({ code: 4032, message: '当前状态不允许修改' })
    expect(verdict.kind).toBe('denied')
    expect(verdict.title).toBe('修改未被允许')
  })

  it('403 也可能只是状态机拒绝：文案没提权限就不能让用户去找管理员开权限', () => {
    // 真机同源：BaseBillController「该单据状态下无法编辑」、AbstractBaseBillService「该单据状态无法审核」、
    // PersonalTokenService「令牌数量已达上限」都用 FORBIDDEN(403) 表达**前置条件失败**，不是授权边界
    for (const message of ['该单据状态无法审核', '该单据状态下无法编辑', '私人令牌数量已达上限']) {
      const verdict = classifyBusinessError({ code: 403, message })
      expect(verdict.kind, message).toBe('denied')
      expect(verdict.title, message).toBe('操作未被允许')
      expect(verdict.hint, message).not.toContain('开通权限')
      expect(notificationText(verdict), message).not.toContain('角色授权')
    }
  })

  it('403 的文案提到权限时（含数据权限拒绝）仍按授权失败给指引', () => {
    const authorized = [
      '你无权访问 权限-不分页查询 (Permission_getList)',
      '数据权限不足，请缩小查询范围后重试',
      '你没有权限修改此令牌',
    ]
    for (const message of authorized) {
      expect(classifyBusinessError({ code: 403, message }).kind, message).toBe('forbidden')
    }
  })

  it('authorizationKnown 让 HTTP 层直出的 403 不被措辞判断降级（网关只回一个 Forbidden）', () => {
    const verdict = classifyBusinessError({ code: 403, message: 'Forbidden', authorizationKnown: true })
    expect(verdict.kind).toBe('forbidden')
    expect(verdict.title).toBe('无权限')
    expect(classifyBusinessError({ code: 403, message: 'Forbidden' }).kind, '没有该标志时按措辞判断').toBe('denied')
  })

  it('家族区间边界：4030 / 4040 落在区间外，且区间内也不算真·授权码', () => {
    expect(isLegacyForbiddenFamily(LEGACY_FORBIDDEN_FAMILY_MIN)).toBe(true)
    expect(isLegacyForbiddenFamily(LEGACY_FORBIDDEN_FAMILY_MAX)).toBe(true)
    expect(isLegacyForbiddenFamily(LEGACY_FORBIDDEN_FAMILY_MIN - 1)).toBe(false)
    expect(isLegacyForbiddenFamily(LEGACY_FORBIDDEN_FAMILY_MAX + 1)).toBe(false)
    expect(isForbiddenCode(403)).toBe(true)
    expect(isForbiddenCode(LEGACY_FORBIDDEN_FAMILY_MIN), '区间内不是授权码').toBe(false)
    expect(classifyBusinessError({ code: 4030 }).kind).toBe('unknown')
  })

  it('4001「没传」与 4002「传了但不合法」分开表述，别让用户猜该补还是该改', () => {
    const missing = classifyBusinessError({ code: BusinessCode.PARAM_MISSING, message: '缺少必要的请求参数「email」' })
    expect(missing.kind).toBe('param')
    expect(missing.title).toBe('缺少必要信息')
    expect(missing.field).toBe('email')

    const invalid = classifyBusinessError({ code: BusinessCode.PARAM_INVALID, message: 'email: 邮箱格式不正确' })
    expect(invalid.kind).toBe('param')
    expect(invalid.title).toBe('提交内容有误')
    expect(invalid.field).toBe('email')
    expect(isParamCode(4001)).toBe(true)
    expect(isParamCode(4002)).toBe(true)
    expect(isParamCode(403)).toBe(false)
  })

  it('业务码 401 说「登录状态已失效」而不是"请求失败"（HTTP-401 的刷新流程不会触发）', () => {
    const verdict = classifyBusinessError({ code: 401, message: '获取你的身份信息失败，请重新登录后再试' })
    expect(verdict.kind).toBe('credential')
    expect(verdict.title).toBe('登录状态已失效')
    expect(verdict.silent, '要提示，但不像登录失败那样交给页面自己呈现').toBe(false)
    expect(verdict.hint, '后端原文已含"重新登录"，不再重复一遍').toBeUndefined()
    expect(classifyBusinessError({ code: 401 }).hint, '后端没给文案时才补指引').toBe('请重新登录后再试')
  })

  it('404 与 500 各给各的下一步（刷新重试 / 带 traceId 找管理员）', () => {
    const notFound = classifyBusinessError({ code: BusinessCode.DATA_NOT_FOUND, message: '数据不存在' })
    expect(notFound.kind).toBe('notFound')
    expect(notFound.hint).toContain('刷新')

    const server = classifyBusinessError({ code: BusinessCode.SERVICE_ERROR, message: '空指针' })
    expect(server.kind).toBe('server')
    expect(server.hint).toContain('traceId')
  })

  it('登录失败（200001）静默：登录页自己呈现，全局再弹一条就是双份提示', () => {
    const verdict = classifyBusinessError({
      code: BusinessCode.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID,
      message: '账号或密码不正确',
    })
    expect(verdict.kind).toBe('credential')
    expect(verdict.silent).toBe(true)
  })

  it('未登记的码落到 unknown，仍要给出后端原文而不是空提示', () => {
    const verdict = classifyBusinessError({ code: 40001, message: '业务失败' })
    expect(verdict.kind).toBe('unknown')
    expect(verdict.title).toBe('请求失败')
    expect(verdict.detail).toBe('业务失败')
    expect(verdict.silent).toBe(false)
  })

  it('后端没给 message 时用兜底文案，不显示 "undefined"', () => {
    expect(classifyBusinessError({ code: 500 }).detail).toBe('请求未成功')
    expect(classifyBusinessError({ code: 500, message: '   ' }).detail).toBe('请求未成功')
  })
})

describe('errorPolicy · 权限码提取（真机抓取的文案格式）', () => {
  // 2026-09 真机：受限账号 POST /permission/getList → HTTP 200 + code=403
  const LIVE_FORBIDDEN = '你无权访问 权限-不分页查询 (Permission_getList)'

  it('从后端 403 文案里取出权限码，并写进指引（管理员能直接搜到那一条授权）', () => {
    expect(extractPermissionCode(LIVE_FORBIDDEN)).toBe('Permission_getList')
    const verdict = classifyBusinessError({ code: 403, message: LIVE_FORBIDDEN })
    expect(verdict.hint).toBe('请联系管理员为当前角色开通权限「Permission_getList」')
    expect(notificationText(verdict)).toContain('Permission_getList')
  })

  it('中文括号同样能取到', () => {
    expect(extractPermissionCode('你无权访问 用户-不分页查询（User_getList）')).toBe('User_getList')
  })

  it('文案里没有权限码就返回 undefined，指引退回通用话术（不硬编一个假码）', () => {
    expect(extractPermissionCode('权限不足')).toBeUndefined()
    expect(extractPermissionCode(undefined)).toBeUndefined()
    const verdict = classifyBusinessError({ code: 403, message: '权限不足' })
    expect(verdict.hint).toContain('管理员')
    expect(verdict.hint).not.toContain('「')
  })

  it('括号不在句尾、或内容不是标识符时不误判', () => {
    expect(extractPermissionCode('（备注）你无权访问 Permission_getList')).toBeUndefined()
    expect(extractPermissionCode('你无权访问 (权限-不分页查询)')).toBeUndefined()
  })
})

describe('errorPolicy · 字段名解析（对齐后端两种文案格式）', () => {
  it('「xxx」格式：缺参 / 取值不合法 / 类型不符三种句式都能取到', () => {
    expect(extractFieldName('缺少必要的请求参数「email」')).toBe('email')
    expect(extractFieldName('参数「name」取值不合法，应为字符串')).toBe('name')
    expect(extractFieldName('字段「code」取值与期望类型不符，请检查提交内容')).toBe('code')
  })

  it('`field: message` 格式：取第一条（焦点只能落在一个控件上）', () => {
    expect(extractFieldName('email: 邮箱格式不正确')).toBe('email')
    expect(extractFieldName('email: 不能为空；name: 长度超限')).toBe('email')
    expect(extractFieldName('materialId: 物料不存在')).toBe('materialId')
  })

  it('解析不出来就返回 undefined —— 宁可不做定位，也不要跳到无关的输入框', () => {
    expect(extractFieldName('服务器内部错误')).toBeUndefined()
    expect(extractFieldName('中文冒号：不是字段')).toBeUndefined()
    expect(extractFieldName('')).toBeUndefined()
    expect(extractFieldName(undefined)).toBeUndefined()
  })
})

describe('errorPolicy · 文案拼装', () => {
  it('有指引时是「标题：详情（指引）」', () => {
    const verdict = classifyBusinessError({ code: 403, message: '权限不足' })
    expect(notificationText(verdict)).toBe(`无权限：权限不足（${verdict.hint}）`)
  })

  it('详情与标题重复时不重复输出，没有指引时不留空括号', () => {
    expect(notificationText({ kind: 'forbidden', code: 403, title: '无权限', detail: '无权限', silent: false }))
      .toBe('无权限')
    expect(notificationText({ kind: 'unknown', code: 1, title: '请求失败', detail: '业务失败', silent: false }))
      .toBe('请求失败：业务失败')
  })
})

describe('errorPolicy · 真机抓取的响应（2026-09，本机 8080，admin 与受限账号张三）', () => {
  /**
   * 这四条是**真载荷进 → 用户看到的那句话出**的端到端断言，不是构造数据：
   * 后端所有业务失败都是 HTTP 200 + 业务码，所以"用户读到什么"完全由这一层决定。
   * 抓法见 docs/DESIGN_THINKING.md 案例 I / J 的实测表。
   */
  it('403 越权：给出可搜到的权限码（张三调 /permission/getList）', () => {
    const text = notificationText(classifyBusinessError({
      code: 403,
      message: '你无权访问 权限-不分页查询 (Permission_getList)',
    }))
    expect(text).toBe('无权限：你无权访问 权限-不分页查询 (Permission_getList)（请联系管理员为当前角色开通权限「Permission_getList」）')
  })

  it('4031 重复邮箱：说「数据已存在」并给出下一步，绝不说成无权限（/user/add）', () => {
    const verdict = classifyBusinessError({ code: 4031, message: '邮箱已经存在，请勿重复添加用户' })
    expect(verdict.kind).toBe('conflict')
    expect(verdict.title).toBe('数据已存在')
    const text = notificationText(verdict)
    expect(text).toContain('不要重复添加')
    expect(text, '曾经的假指引：让用户去找管理员开权限').not.toContain('开通权限')
    expect(text).not.toContain('无权限')
  })

  it('4002 字段校验：解析出 nickname 并给出定位指引（/user/add 少传昵称）', () => {
    const verdict = classifyBusinessError({ code: 4002, message: 'nickname: 昵称不能为空' })
    expect(verdict.field).toBe('nickname')
    expect(notificationText(verdict)).toBe('提交内容有误：nickname: 昵称不能为空（请检查「nickname」这一项）')
  })

  it('404 查不到行：提示可能是页面数据过期（/material/getDetail 传了不存在的 id）', () => {
    const verdict = classifyBusinessError({ code: 404, message: '没有查询到ID为99999999的物料' })
    expect(verdict.kind).toBe('notFound')
    expect(notificationText(verdict)).toContain('刷新后重试')
  })

  /**
   * 真机抓取：`POST /input/delete {"id":1}` → HTTP **200** + `{ code: 501, message: '当前接口不支持此操作' }`。
   * `InputController` 标了 `@Extends(exclude = {Curd.Delete})`，端点仍注册着，只是运行时被
   * `BaseController.checkApiAvailable` 拒绝。同类：purchase / move / output / sale / supplier 的 delete。
   */
  it('501 接口未开放：说清「不是权限、不是故障、重试没用」（/input/delete，@Extends 排除了删除）', () => {
    const verdict = classifyBusinessError({ code: 501, message: '当前接口不支持此操作' })
    expect(verdict.kind).toBe('unsupported')
    expect(verdict.silent, '必须弹：点了按钮却什么都不说，用户会一直点下去').toBe(false)
    const text = notificationText(verdict)
    expect(text).toBe('此操作在当前模块不可用：当前接口不支持此操作（该模块未开放此接口，重试不会成功；如确需此功能请联系管理员确认）')
    // 曾经的兜底路径：落到 unknown，只剩一句"请求失败：当前接口不支持此操作"，
    // 用户既不知道是权限问题还是故障，也不知道该不该重试
    expect(text, '不得退化成无信息的泛化失败').not.toBe('请求失败：当前接口不支持此操作')
    expect(text, '不是授权失败，不能给"开权限"的假指引').not.toContain('开通权限')
    expect(text, '不是服务端故障，不能给"稍后重试"的假指引').not.toContain('稍后重试')
  })

  it('501 与 403 家族不混：isUnsupportedCode 只认 501，4031..4039 仍走各自语义', () => {
    expect(isUnsupportedCode(501)).toBe(true)
    expect(isUnsupportedCode(403)).toBe(false)
    expect(isUnsupportedCode(500)).toBe(false)
    expect(isUnsupportedCode(4034)).toBe(false)
    // 反向：501 不得被 403 家族或"服务端错误"吃掉
    expect(isLegacyForbiddenFamily(501)).toBe(false)
    expect(classifyBusinessError({ code: 501 }).kind).toBe('unsupported')
  })
})

describe('errorPolicy · 重复提示抑制', () => {
  it('同一「码 + 接口」在窗口内只提示一次，超过窗口后可以再提示', () => {
    resetNotifyDedupe()
    const key = notifyKey(403, '/permission/list')
    const t0 = 1_000_000
    expect(shouldNotify(key, t0)).toBe(true)
    expect(shouldNotify(key, t0 + 10)).toBe(false)
    expect(shouldNotify(key, t0 + NOTIFY_DEDUPE_MS - 1)).toBe(false)
    expect(shouldNotify(key, t0 + NOTIFY_DEDUPE_MS)).toBe(true)
  })

  it('不同接口 / 不同码互不影响（一个页面并发失败时不会互相吞掉）', () => {
    resetNotifyDedupe()
    const t0 = 2_000_000
    expect(shouldNotify(notifyKey(403, '/a'), t0)).toBe(true)
    expect(shouldNotify(notifyKey(403, '/b'), t0)).toBe(true)
    expect(shouldNotify(notifyKey(500, '/a'), t0)).toBe(true)
    expect(shouldNotify(notifyKey(403, '/a'), t0)).toBe(false)
  })

  it('path 缺省时也能稳定成键', () => {
    resetNotifyDedupe()
    expect(notifyKey(403)).toBe('403@')
    expect(shouldNotify(notifyKey(403), 3_000_000)).toBe(true)
  })
})

describe('errorPolicy · 登录页内联契约 loginInlineError（R-37）', () => {
  // 真机复现（2026-10-01）：错误密码 → HTTP 200 + { code: 200001, message: 用户账号或密码错误 }
  // → 全局按 SILENT_CODES 静默 → 登录页此前只有 reportError（落 console）→ 界面毫无变化。
  it('200001 必须给出内联文案：静默的前提是有人呈现', () => {
    expect(loginInlineError({
      code: BusinessCode.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID,
      message: '用户账号或密码错误',
    })).toBe('登录未通过：用户账号或密码错误')
  })

  it('登录页撞上业务码 401（登录状态失效）也内联呈现，不让用户对着假表单发呆', () => {
    const text = loginInlineError({ code: 401, message: '获取你的身份信息失败，请重新登录后再试' })
    expect(text).toContain('登录状态已失效')
  })

  it('非 credential 码返回 undefined：全局 toast 已说过同一句话，页面不得双份提示', () => {
    expect(loginInlineError({ code: 500, message: '服务器内部错误' })).toBeUndefined()
    expect(loginInlineError({ code: 4002, message: 'email: 邮箱格式不正确' })).toBeUndefined()
    expect(loginInlineError({ code: 403, message: '权限不足' })).toBeUndefined()
  })

  it('网络错误（无数字 code 的 Error）与非法入参一律 undefined，绝不显示 undefined', () => {
    expect(loginInlineError(new Error('网络连接失败'))).toBeUndefined()
    expect(loginInlineError(undefined)).toBeUndefined()
    expect(loginInlineError(null)).toBeUndefined()
    expect(loginInlineError('boom')).toBeUndefined()
    expect(loginInlineError({ code: '200001', message: '字符串码不算数' })).toBeUndefined()
  })

  it('后端没给 message 时用兜底文案，内联不出现空壳', () => {
    expect(loginInlineError({ code: BusinessCode.USER_LOGIN_ACCOUNT_OR_PASSWORD_INVALID })).toBe('登录未通过：请求未成功')
  })
})

/**
 * 第 35 轮审计新增的两类码：网络层失败的**前端合成码**与 429。
 * 它们没有后端文案可分类（请求根本没到后端 / 网关直出），标题与指引必须由本层给全，
 * 否则用户看到的就是一句裸的「请求超时」。
 */
describe('errorPolicy · 网络合成码与 429', () => {
  it('超时（-1）说「请求超时」并给可执行的下一步', () => {
    const verdict = classifyBusinessError({ code: NETWORK_ERROR_CODE.timeout, message: '请求超时' })
    expect(verdict.kind).toBe('network')
    expect(verdict.title).toBe('请求超时')
    expect(verdict.silent).toBe(false)
    expect(notificationText(verdict)).toContain('请检查网络后重试')
  })

  it('断线（-2）说「网络连接失败」，并提示可能是服务端不可达', () => {
    const verdict = classifyBusinessError({ code: NETWORK_ERROR_CODE.offline, message: '网络连接失败' })
    expect(verdict.kind).toBe('network')
    expect(verdict.title).toBe('网络连接失败')
    expect(notificationText(verdict)).toContain('服务暂时不可达')
  })

  it('429 归为 conflict 并给「稍等几秒」；不被 403 家族或 unknown 吞掉', () => {
    const verdict = classifyBusinessError({ code: BusinessCode.RATE_LIMITED, message: '请求过于频繁，请稍后再试' })
    expect(verdict.kind).toBe('conflict')
    expect(verdict.title).toBe('请求过于频繁')
    expect(verdict.hint).toContain('稍等几秒')
    expect(verdict.silent).toBe(false)
  })

  it('合成码是负数：与后端业务码空间（正整数）天然不冲突', () => {
    expect(NETWORK_ERROR_CODE.timeout).toBeLessThan(0)
    expect(NETWORK_ERROR_CODE.offline).toBeLessThan(0)
  })
})

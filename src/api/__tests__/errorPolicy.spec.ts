import { describe, expect, it } from 'vitest'
import {
  BusinessCode,
  classifyBusinessError,
  extractFieldName,
  extractPermissionCode,
  FORBIDDEN_CODE_MAX,
  FORBIDDEN_CODE_MIN,
  isForbiddenCode,
  isParamCode,
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
  it('403 与 4031..4039 都是授权失败，给出「找谁开通」的可操作指引', () => {
    for (const code of [403, FORBIDDEN_CODE_MIN, 4035, FORBIDDEN_CODE_MAX]) {
      const verdict = classifyBusinessError({ code, message: '权限不足' })
      expect(verdict.kind, `code=${code}`).toBe('forbidden')
      expect(verdict.title).toBe('无权限')
      expect(verdict.detail).toBe('权限不足')
      expect(verdict.silent).toBe(false)
      expect(verdict.hint, '只说「不行」而不说「怎么办」等于没说').toContain('管理员')
    }
  })

  it('授权码区间的边界外不算无权限（4030 / 4040 落到 unknown）', () => {
    expect(isForbiddenCode(403)).toBe(true)
    expect(isForbiddenCode(FORBIDDEN_CODE_MIN)).toBe(true)
    expect(isForbiddenCode(FORBIDDEN_CODE_MAX)).toBe(true)
    expect(isForbiddenCode(FORBIDDEN_CODE_MIN - 1)).toBe(false)
    expect(isForbiddenCode(FORBIDDEN_CODE_MAX + 1)).toBe(false)
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

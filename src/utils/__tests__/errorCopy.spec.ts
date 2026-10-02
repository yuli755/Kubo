import { describe, expect, it } from 'vitest'
import { describeUserError, userErrorLine } from '@/utils/errorCopy'

/**
 * D5 契约：错卡文案必须**分档**且**每档都给下一步**。
 *
 * 钉住的历史缺陷（§13.4 / §14.5 记录的候选，本轮落地）：`ErrorBoundary` / `Console`
 * 把 `error.message` 原样打到主位，chunk 失效时用户看到
 * `Failed to fetch dynamically imported module: http://…/view/….vue` ——
 * 英文、含内部路径、而且「重试」按钮对它完全无效（必须重新拉 index.html）。
 */

/** 各浏览器对懒加载失败的原文措辞（isChunkLoadError 认的四种） */
const CHUNK_MESSAGES = [
  'Failed to fetch dynamically imported module: http://localhost:3000/src/view/wms/input/list.vue',
  'error loading dynamically imported module: http://localhost:3000/src/view/x.vue',
  'Importing a module script failed.',
  'ChunkLoadError: Loading chunk 42 failed.',
]

describe('errorCopy · D5 错卡文案分档', () => {
  it.each(CHUNK_MESSAGES)('chunk 加载失败（%s）→ 中文标题 + 明确要硬刷新', (message) => {
    const copy = describeUserError(new Error(message))
    expect(copy.title).toBe('页面资源加载失败')
    // 「重试」按钮救不了 chunk 失效，指引必须点出强制刷新，否则用户点一百次也没用
    expect(copy.hint).toContain('Ctrl + F5')
    // 原始文本仍在（排查需要），但降级到 detail 槽位
    expect(copy.detail).toContain(message)
    expect(copy.raw).toBe(message)
  })

  it('网络/超时口径与 axios 无响应分支一致，且不误判为 chunk', () => {
    for (const message of ['网络连接失败', '请求超时', 'Network Error', 'net::ERR_INTERNET_DISCONNECTED']) {
      const copy = describeUserError(new Error(message))
      expect(copy.title, message).toBe('网络连接失败')
      expect(copy.hint, message).toContain('网络')
      expect(copy.hint, message).not.toContain('Ctrl')
    }
  })

  it('业务错误复用 errorPolicy 的文案（同一句话只说一遍）', () => {
    // ApiError 是普通对象（不是 Error 实例）—— 两种形态都要认
    const copy = describeUserError({ code: 403, message: '你无权访问 权限-不分页查询 (Permission_getList)' })
    expect(copy.title).toBe('无权限')
    expect(copy.hint).toContain('Permission_getList')
    expect(copy.hint).toContain('管理员')
  })

  it('带数字 code 的 Error 实例（HTTP 路径）也走业务分档', () => {
    const error = Object.assign(new Error('服务器内部错误'), { code: 500 })
    const copy = describeUserError(error)
    expect(copy.title).toBe('服务器处理失败')
    expect(copy.hint).toContain('traceId')
  })

  it('未知错误 → 中文兜底标题 + 指引，原文保留在 detail，绝不出现 [object Object]', () => {
    const unknown = describeUserError(new Error('cannot read properties of undefined'))
    expect(unknown.title).toBe('操作未能完成')
    expect(unknown.hint).toContain('重试')
    expect(unknown.detail).toContain('cannot read properties')

    const bare = describeUserError({ weird: true })
    expect(bare.detail).toBe('发生未预期的错误。')
    expect(bare.raw).toBe('')
    expect(bare.title).toBe('操作未能完成')
    expect(bare.hint).toBeTruthy()
  })

  it('调用方可按出错位置指定兜底标题（渲染错卡 ≠ 初始化错卡）', () => {
    const err = new Error('cannot read x')
    expect(describeUserError(err, '页面渲染出错').title).toBe('页面渲染出错')
    expect(describeUserError(err, '控制台初始化失败').title).toBe('控制台初始化失败')
    // 能归档的错误不受 fallbackTitle 影响
    expect(describeUserError(new Error('Network Error'), '页面渲染出错').title).toBe('网络连接失败')
  })

  it('null / undefined / 字符串异常都不能抛错（错卡自己崩了是最坏情形）', () => {
    for (const value of [null, undefined, '', 'boom', 0, Number.NaN]) {
      const copy = describeUserError(value)
      expect(copy.title, String(value)).toBeTruthy()
      expect(copy.hint, String(value)).toBeTruthy()
    }
    expect(describeUserError('boom').detail).toBe('boom')
  })

  it('userErrorLine：标题：详情（指引），详情与标题同名时不重复', () => {
    expect(userErrorLine({ title: '无权限', detail: '你没有权限', hint: '找管理员', raw: '' }))
      .toBe('无权限：你没有权限（找管理员）')
    expect(userErrorLine({ title: '操作未能完成', detail: '操作未能完成', hint: '', raw: '' }))
      .toBe('操作未能完成')
  })
})

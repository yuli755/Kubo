import { describe, expect, it } from 'vitest'
import { Permissions } from '../permissions'

/**
 * # 权限码常量不变量
 *
 * 权限码字符串必须与后端 `PermissionUtil.scanPermission()` 生成的
 * `{Entity}_{action}` 格式严格一致 —— 这是「前端常量」与「后端扫描结果」之间
 * 唯一的**隐式契约**：写错不会编译报错、不会运行报错，只会静默变成
 * "这个按钮永远无权限"或"永远有权限"。所以把它钉成测试。
 *
 * 注意：前端按业务模块分组（`Open.App`），后端实体名是 `OpenAppEntity`，
 * 因此**不能**要求「组名 == 码前缀」，只校验动作后缀（见第 3 条）。
 */

/** 后端 CRUD 扫描出的标准动作 */
const STANDARD_ACTIONS = new Set([
  'add', 'update', 'delete', 'getDetail', 'enable', 'disable', 'export', 'import', 'audit', 'reject', 'publish',
])

/**
 * 已知的别名键：`addRow` 是「主子表新增行」，与主表 `add` 共用同一权限标识，
 * 由后端 `OpenAppController` 等实现决定，属预期重复。
 */
const KNOWN_ALIASES = new Set(['addRow'])

interface Node {
  [key: string]: string | Node
}

function flatten(node: Node, prefix: string[] = []): Array<{ path: string, entity: string, action: string, code: string }> {
  const out: Array<{ path: string, entity: string, action: string, code: string }> = []
  for (const [key, value] of Object.entries(node)) {
    if (typeof value === 'string') {
      out.push({ path: [...prefix, key].join('.'), entity: prefix.at(-1) ?? '', action: key, code: value })
    }
    else {
      out.push(...flatten(value as Node, [...prefix, key]))
    }
  }
  return out
}

const all = flatten(Permissions as unknown as Node)

describe('Permissions 常量', () => {
  it('非空（防止误删导致前端权限判定整体失效）', () => {
    expect(all.length).toBeGreaterThan(100)
  })

  it('全部权限码符合 `{Entity}_{action}` 格式', () => {
    const bad = all.filter(({ code }) => !/^[A-Z][A-Za-z0-9]*_[a-z][A-Za-z0-9]*$/.test(code))
    expect(bad.map(b => `${b.path} = ${b.code}`)).toEqual([])
  })

  it('标准动作的键名与码后缀一致（复制粘贴防护）', () => {
    const bad = all
      .filter(({ action }) => STANDARD_ACTIONS.has(action))
      .filter(({ action, code }) => !code.endsWith(`_${action}`))
    expect(bad.map(b => `${b.path} = ${b.code}`)).toEqual([])
  })

  it('单据类实体（有 audit 的）必须同时有 reject', () => {
    const actions = new Map<string, Set<string>>()
    for (const { entity, action } of all) {
      actions.set(entity, new Set([...(actions.get(entity) ?? []), action]))
    }
    const broken = [...actions.entries()]
      .filter(([, acts]) => acts.has('audit') && !acts.has('reject'))
      .map(([entity]) => entity)
    expect(broken).toEqual([])
  })

  it('同一实体下无重复权限码（addRow 别名除外）', () => {
    const seen = new Map<string, string[]>()
    for (const { entity, action, code } of all) {
      const k = `${entity}::${code}`
      seen.set(k, [...(seen.get(k) ?? []), action])
    }
    const bad = [...seen.entries()]
      // 允许「别名键 → 同一码」（如 addRow → add）；除此之外不允许重复
      .filter(([, actions]) => actions.length > 1 && actions.slice(1).some(a => !KNOWN_ALIASES.has(a)))
      .map(([k, actions]) => `${k} ← ${actions.join(', ')}`)
    expect(bad).toEqual([])
  })
})

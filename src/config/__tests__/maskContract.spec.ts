import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 脱敏契约门禁（前端 F2 的卡点）
 *
 * 四条规则都来自本轮实际踩到的坑，不是"看起来该有"：
 *
 * 1. **前端 `MaskType` 必须覆盖后端 `ColumnMaskingService#applyMask` 的全部分支**。
 *    后端早已支持 `LAST4` / `HASH`，前端 union 里只有 3 个值 —— 后果不是编译失败，
 *    而是前端落进"未知类型"分支被按 `****` 处理（过度遮蔽），排查时两头都"没错"。
 * 2. **前端写的 `resourceKey` 必须是后端注册过的数据资源**。写错一个字母，
 *    `my-mask` 永远返回 `null` = "该字段无需脱敏"，脱敏静默失效（fail-open）。
 * 3. **只有后端真的提供 export 端点的实体才允许开 `showExport`**。`BaseController`
 *    全局 `@Extends(exclude = {Curd.Export, Curd.QueryExport})`，目前只有 `Material`
 *    加回来；导出走文件流不经过 Jackson，是脱敏的已知旁路，前端别随手开。
 * 4. **模型里的敏感字段必须显式表态**：`@Table({ desensitize })` 或 `mask-exempt: 理由`。
 *    框架在列表渲染层认这个元数据，一处声明全视图生效；漏了就是"某个页面的手机号裸奔"。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java', 'cn', 'hamm', 'spms')

function collect(dir: string, ext: string, out: string[] = []): string[] {
  if (statSync(dir, { throwIfNoEntry: false })?.isDirectory() !== true) {
    return out
  }
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      collect(full, ext, out)
    }
    else if (name.endsWith(ext)) {
      out.push(full)
    }
  }
  return out
}

const read = (path: string): string => readFileSync(path, 'utf-8')

describe('mask contract · 前后端脱敏口径', () => {
  it('前端 MaskType 覆盖后端 applyMask 的全部分支', () => {
    const java = read(join(SERVER, 'module', 'system', 'datapermission', 'service', 'ColumnMaskingService.java'))
    const body = java.slice(java.indexOf('private Object applyMask('))
    const backend = [...body.slice(0, 1500).matchAll(/case "([A-Z0-9_]+)"/g)].map(m => m[1]).sort()
    expect(backend.length).toBeGreaterThanOrEqual(5)

    const types = read(join(ROOT, 'packages', 'shared', 'src', 'types', 'mask.ts'))
    const union = (types.match(/export type MaskType =([^\n]+)/)?.[1] ?? '')
      .split('|')
      .map(part => part.trim().replace(/'/g, ''))
      .filter(Boolean)

    // 后端有、前端没有 = 前端会把它当"未知类型"过度遮蔽
    const uncovered = backend.filter(value => !union.includes(value))
    expect(uncovered).toEqual([])
  })

  it('前端引用的 resourceKey 都必须在后端注册（写错会静默 fail-open）', () => {
    const registered = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = read(file)
      for (const m of source.matchAll(/setResourceKey\("([^"]+)"\)/g)) {
        registered.add(m[1])
      }
      for (const m of source.matchAll(/@SensitiveField\(resourceKey = "([^"]+)"\)/g)) {
        registered.add(m[1])
      }
    }
    expect(registered.size).toBeGreaterThan(10)

    const offenders: string[] = []
    for (const file of [...collect(join(ROOT, 'src'), '.vue'), ...collect(join(ROOT, 'src'), '.ts')]) {
      const source = read(file)
      for (const m of source.matchAll(/useMaskPolicy\(\s*'([^']+)'|resourceKey:\s*'([^']+)'/g)) {
        const key = m[1] ?? m[2]
        if (!registered.has(key)) {
          offenders.push(`${relative(ROOT, file)} → ${key}`)
        }
      }
    }
    expect(offenders).toEqual([])
  })

  it('showExport 只允许出现在后端提供 export 端点的实体视图上', () => {
    const exportCapable = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = read(file)
      const extendsLine = source.match(/@Extends\(\{([^}]*)\}\)/)?.[1] ?? ''
      if (!extendsLine.includes('Export') || extendsLine.includes('exclude')) {
        continue
      }
      // 用 controller 所在目录推断模块路径，例如 .../module/asset/material → asset/material
      const rel = relative(SERVER, file).split(/[\\/]/)
      const modulePath = rel.slice(1, 3).join('/')
      exportCapable.add(modulePath)
    }
    expect([...exportCapable]).toEqual(expect.arrayContaining(['asset/material']))

    const offenders: string[] = []
    const viewRoot = join(ROOT, 'src', 'view', 'console')
    for (const file of collect(viewRoot, '.vue')) {
      if (!/showExport|:show-export/.test(read(file))) {
        continue
      }
      const rel = relative(viewRoot, file).split(/[\\/]/).slice(0, 2).join('/')
      if (!exportCapable.has(rel)) {
        offenders.push(`${relative(ROOT, file)}（后端 ${rel} 没有 export 端点）`)
      }
    }
    expect(offenders).toEqual([])
  })
})

describe('mask contract · 模型元数据', () => {
  // 只用 .test()，因此分组一律非捕获（捕获组会被 lint 判为无用）
  const SENSITIVE_FIELD = /^(?:phone|mobile|tel|telephone|mobilePhone|email|mail|idCard|idNo|bankCard|bankAccount|cardNo|contactPhone)$/

  it('实体里的敏感字段必须声明 desensitize 或写明豁免理由', () => {
    const missing: string[] = []
    for (const file of collect(join(ROOT, 'src', 'model'), '.ts')) {
      const lines = read(file).split('\n')
      // pending = 当前字段上方的装饰器与注释（生成代码里装饰器总是紧贴字段）
      let pending: string[] = []
      lines.forEach((line, index) => {
        const field = line.match(/^\s*(\w+)\s*[!?]:/)
        if (!field) {
          pending.push(line)
          return
        }
        if (SENSITIVE_FIELD.test(field[1])) {
          const block = pending.join('\n')
          const declared = /@Table\([\s\S]*?desensitize/.test(block)
          const exemptMark = block.match(/mask-exempt:([^\n]*)/)
          if (!declared && !exemptMark) {
            missing.push(`${relative(ROOT, file)}:${index + 1} ${field[1]}（补 @Table({ desensitize }) 或写 mask-exempt: 理由）`)
          }
          if (!declared && exemptMark && exemptMark[1].replace(/[^0-9A-Z\u4E00-\u9FA5]/gi, '').length < 4) {
            missing.push(`${relative(ROOT, file)}:${index + 1} ${field[1]} 的 mask-exempt 必须写理由`)
          }
        }
        pending = []
      })
    }
    expect(missing).toEqual([])
  })
})

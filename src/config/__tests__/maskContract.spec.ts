import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 脱敏契约门禁（前端 F2 的卡点）
 *
 * 五条规则都来自本轮实际踩到的坑，不是"看起来该有"：
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
 * 5. **`useMaskPolicy(resourceKey, fields)` 里的字段名必须真的是后端那个实体上被
 *    `@SensitiveField` 标记的字段**。这是比资源键更容易犯的错（复制粘贴改一个词），
 *    后果同第 2 条：策略查不到 = 前端认为"无需脱敏"，静默 fail-open，
 *    而界面上一切看起来都正常 —— 所以必须在提交前被机器拦住。
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

/**
 * Java 源码里只看代码、不看注释
 *
 * 门禁靠正则读后端源码，而注释里的示例（`@SensitiveField(resourceKey = "wms.inventory")`）
 * 与真代码长得一模一样：不过滤的话，"文档里举了个例子"就会被当成"后端注册过这个资源"，
 * 于是资源键写错也能通过门禁 —— 这是自我安慰式的检查，比没有检查更糟。
 */
function readJavaCode(path: string): string {
  return read(path)
    .split('\n')
    .filter(line => !/^\s*(?:\*|\/\/)/.test(line))
    .join('\n')
}

describe('mask contract · 前后端脱敏口径', () => {
  it('前端 MaskType 覆盖后端 applyMask 的全部分支', () => {
    const java = readJavaCode(join(SERVER, 'module', 'system', 'datapermission', 'service', 'ColumnMaskingService.java'))
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
      const source = readJavaCode(file)
      for (const m of source.matchAll(/setResourceKey\("([^"]+)"\)/g)) {
        registered.add(m[1])
      }
      for (const m of source.matchAll(/@SensitiveField\(resourceKey = "([^"]+)"\)/g)) {
        registered.add(m[1])
      }
    }
    expect(registered.size).toBeGreaterThan(10)

    // 自检：注释过滤真的在起作用。`SensitiveField` 的 javadoc 里放着一个可直接抄的示例键
    // `wms.inventory`，它只应存在于注释中 —— 若哪天示例被删，请连同这两行一起删掉，
    // 不要为了让自检通过而把示例加回代码。
    const annotationFile = join(SERVER, 'module', 'system', 'datapermission', 'annotation', 'SensitiveField.java')
    expect(/@SensitiveField\(resourceKey = "wms\.inventory"\)/.test(read(annotationFile))).toBe(true)
    expect(/@SensitiveField\(resourceKey = "wms\.inventory"\)/.test(readJavaCode(annotationFile))).toBe(false)

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

  it('useMaskPolicy 声明的字段必须是后端该资源真实标记过的敏感字段', () => {
    // 后端事实：resourceKey → 被 @SensitiveField 标记的字段名（装饰器紧贴字段，故按"字段上方的块"归属）
    const fieldsByResource = new Map<string, Set<string>>()
    for (const file of collect(SERVER, '.java')) {
      const code = readJavaCode(file)
      if (!code.includes('@SensitiveField')) {
        continue
      }
      let pending: string[] = []
      for (const line of code.split('\n')) {
        // 类型段刻意不容空白（\s 与类型字符集重叠会造出多项回溯），泛型内部允许任意非 > 字符；
        // 因此 `private Map<String, Object> x;` 这类写法会被跳过 —— 本门禁只关心 @SensitiveField
        // 标在简单类型上的字段，扫不到时的后果是"少校验一个字段"，不是误报，可接受。
        const field = line.match(/^\s*private\s+[\w.]+(?:<[^>]*>)?\s+(\w+)\s*[=;]/)
        if (!field) {
          pending.push(line)
          continue
        }
        const key = pending.join('\n').match(/@SensitiveField\(resourceKey = "([^"]+)"\)/)?.[1]
        if (key) {
          if (!fieldsByResource.has(key)) {
            fieldsByResource.set(key, new Set<string>())
          }
          fieldsByResource.get(key)!.add(field[1])
        }
        pending = []
      }
    }
    // 前提自检：后端样本必须非空，否则这条门禁会因为"什么都没解析到"而恒真通过
    expect(fieldsByResource.size).toBeGreaterThanOrEqual(4)
    expect([...fieldsByResource.get('personnel.user') ?? []]).toEqual(
      expect.arrayContaining(['phone', 'email', 'idCard']),
    )

    const offenders: string[] = []
    let checked = 0
    for (const file of [...collect(join(ROOT, 'src', 'view'), '.vue'), ...collect(join(ROOT, 'src', 'view'), '.ts')]) {
      for (const m of read(file).matchAll(/useMaskPolicy\(\s*'([^']+)'\s*,\s*\[([^\]]*)\]/g)) {
        const key = m[1]
        const declared = [...m[2].matchAll(/'([^']+)'/g)].map(x => x[1])
        checked += declared.length
        const known = fieldsByResource.get(key)
        if (!known) {
          offenders.push(`${relative(ROOT, file)} → ${key}（后端没有任何 @SensitiveField 字段，声明字段无意义）`)
          continue
        }
        for (const name of declared) {
          if (!known.has(name)) {
            offenders.push(`${relative(ROOT, file)} → ${key}.${name}（后端该资源只标记了 ${[...known].join('/')}）`)
          }
        }
      }
    }
    expect(checked).toBeGreaterThan(0)
    expect(offenders).toEqual([])
  })

  it('showExport 只允许出现在后端提供 export 端点的实体视图上', () => {
    const exportCapable = new Set<string>()
    for (const file of collect(SERVER, '.java')) {
      const source = readJavaCode(file)
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

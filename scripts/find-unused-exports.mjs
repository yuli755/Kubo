#!/usr/bin/env node
/**
 * 找出「文件还在用、但导出没人要」的代码。
 *
 * 与 `find-unused-frontend.mjs` 的分工：
 *   - 那个脚本管**整文件级**死件（没有任何 import / 路径字符串指向它）；
 *   - 本脚本管**导出级**死件：文件确实被引用了，但里面某些 `export` 出来的东西
 *     全仓没有任何地方 import —— 典型来源是重构后只删了调用方、忘了删定义，
 *     或者一个内部用的辅助函数被顺手写成了 export。
 *
 * 判定用的是 TypeScript 自己的解析器（不是正则），所以多行 import、
 * `import { a as b }`、`export { x } from './y'`、`export * from`、`import type`、
 * `import('@/x').Foo`、`const { a } = await import('@/x')` 都能正确处理。
 *
 * 每个候选还会区分三种动作：
 *   - 「删掉定义」：文件内也不需要；
 *   - 「去掉 export 关键字」：文件内自己还在用，只是不该 export；
 *   - 「删掉该 re-export 条目」：`export { X } from './y'` 里的多余项。
 *
 * 用法：
 *   node scripts/find-unused-exports.mjs            # 只报告
 *   node scripts/find-unused-exports.mjs --check    # CI 模式：有候选则退出码 1
 *   node scripts/find-unused-exports.mjs --json     # 机器可读（含可删除的位置区间）
 *   node scripts/prune-unused-exports.mjs --write   # 按报告自动清理（先 dry-run）
 *
 * 两类"看起来没人用、其实在用"的文件被排除在报告外：
 *   - `src/framework/**`：换底座后的对外契约面，允许有暂时没被调用的导出；
 *   - `*.d.ts`：环境与自动生成的声明（`src/types/components.d.ts` 就是
 *     unplugin-vue-components 生成的模板组件清单），它是**消费语料**不是被删对象。
 */
import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import { createRequire } from 'node:module'
import { join, relative, resolve } from 'node:path'
import process from 'node:process'
import { pathToFileURL } from 'node:url'

const require = createRequire(import.meta.url)
const ts = require('typescript')

const ROOT = resolve(import.meta.dirname, '..')
const SRC = join(ROOT, 'src')
const SHARED = join(ROOT, 'packages', 'shared', 'src')

/** 参与扫描的源码目录 */
const SCAN_DIRS = [SRC, SHARED].filter(existsSync)
/** 报告里排除的路径前缀 */
const SKIP_REPORT = [/^src\/framework\//]
const EXT = ['.ts', '.vue', '.mts']

function walk(dir, out = []) {
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name.startsWith('.'))
      continue
    const full = join(dir, entry.name)
    if (entry.isDirectory())
      walk(full, out)
    else if (EXT.some(e => entry.name.endsWith(e)))
      out.push(full)
  }
  return out
}

/** SFC 里把 `<script>` / `<script setup>` 抽出来当 TS 解析 */
export function extractScript(file) {
  const raw = readFileSync(file, 'utf8')
  if (!file.endsWith('.vue'))
    return { source: raw, offset: 0 }
  const blocks = [...raw.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)]
  if (!blocks.length)
    return { source: '', offset: 0 }
  // 取第一个 script 块，记录它在原文里的偏移（供 codemod 映射回原文）
  const first = blocks[0]
  return { source: first[1], offset: first.index + first[0].indexOf(first[1]) }
}

/** 模块标识符 → 源文件绝对路径 */
export function resolveModule(fromFile, specifier) {
  let base
  if (specifier.startsWith('@/'))
    base = join(SRC, specifier.slice(2))
  else if (specifier === '@kubo/shared')
    base = join(SHARED, 'index.ts')
  // vite.config.mts 把裸 `element-plus` 与 `element-plus/es` 都指向聚合入口
  else if (specifier === 'element-plus' || specifier === 'element-plus/es')
    base = join(SRC, 'types', 'element-plus-aggregate.ts')
  else if (specifier.startsWith('.'))
    base = resolve(fromFile, '..', specifier)
  else
    return null // 第三方包

  for (const candidate of [base, `${base}.ts`, `${base}.vue`, `${base}.mts`, join(base, 'index.ts'), join(base, 'index.vue')]) {
    if (existsSync(candidate) && statSync(candidate).isFile())
      return candidate
  }
  return null
}

/** 解析单个文件：导出了什么、引用了别人的什么 */
function analyze(file) {
  const { source, offset } = extractScript(file)
  if (!source.trim())
    return null
  const sf = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, undefined)

  /** 本文件导出的符号 */
  const exportsLocal = new Map()
  /** 本文件对外部模块的引用：target -> Set<名字> | 'ALL' */
  const uses = new Map()
  /** 本文件里所有标识符出现次数（判断"文件内自己还用不用"） */
  const identifiers = new Map()

  const addUse = (target, name) => {
    if (!target)
      return
    const cur = uses.get(target)
    if (cur === 'ALL')
      return
    if (name === 'ALL') {
      uses.set(target, 'ALL')
      return
    }
    const set = cur || new Set()
    set.add(name)
    uses.set(target, set)
  }

  const hasExportModifier = node =>
    !!node.modifiers?.some(m => m.kind === ts.SyntaxKind.ExportKeyword)

  const register = (name, node, kind) => {
    exportsLocal.set(name, {
      kind,
      line: sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1,
      start: node.getStart(sf) + offset,
      end: node.getEnd() + offset,
      fileStart: node.getStart(sf),
      fileEnd: node.getEnd() + offset,
      exportModifier: node.modifiers?.find(m => m.kind === ts.SyntaxKind.ExportKeyword)
        ? {
            start: node.modifiers.find(m => m.kind === ts.SyntaxKind.ExportKeyword).getStart(sf) + offset,
            end: node.modifiers.find(m => m.kind === ts.SyntaxKind.ExportKeyword).getEnd() + offset,
          }
        : null,
    })
  }

  /**
   * 环境声明（`declare module 'pinia' { ... }` / `declare global { ... }`）里的成员
   * **不是给别人 import 的导出**，而是给编译器看的类型增强。
   * 曾经因为不认这一点，把 `defineStore` 的 `persist` 选项增强删了，
   * 直接让所有 store 的类型报错 —— 这类"没人 import 但绝对在用"的东西必须跳过。
   */
  let ambientDepth = 0
  const isAmbientScope = (node) => {
    if (!ts.isModuleDeclaration(node))
      return false
    const isGlobal = node.name && ts.isIdentifier(node.name) && node.name.text === 'global'
    const isDeclare = !!node.modifiers?.some(m => m.kind === ts.SyntaxKind.DeclareKeyword)
    return isGlobal || isDeclare
  }

  const handleStatement = (node) => {
    if (ambientDepth > 0)
      return
    if (ts.isImportDeclaration(node)) {
      const target = resolveModule(file, node.moduleSpecifier.text)
      const clause = node.importClause
      if (clause) {
        if (clause.name)
          addUse(target, 'default')
        const bindings = clause.namedBindings
        if (bindings) {
          if (ts.isNamespaceImport(bindings)) {
            addUse(target, 'ALL')
          }
          else if (ts.isNamedImports(bindings)) {
            for (const el of bindings.elements)
              addUse(target, (el.propertyName ?? el.name).text)
          }
        }
      }
      return
    }
    if (ts.isExportDeclaration(node)) {
      const target = node.moduleSpecifier ? resolveModule(file, node.moduleSpecifier.text) : null
      const clause = node.exportClause
      if (!clause) {
        addUse(target, 'ALL') // export * from './y'
        return
      }
      if (ts.isNamespaceExport(clause)) {
        register(clause.name.text, clause, 'NamespaceExport')
        addUse(target, 'ALL')
        return
      }
      for (const el of clause.elements) {
        const exported = el.name.text
        register(exported, el, 'NamedExport')
        const meta = exportsLocal.get(exported)
        meta.from = target
        meta.parentStart = clause.getStart(sf) + offset
        meta.parentEnd = clause.getEnd() + offset
        meta.element = { start: el.getStart(sf) + offset, end: el.getEnd() + offset }
        // 有 `from` 是转出别人的东西；没有 `from` 则是「本文件 import 进来再列出去」的
        // barrel 条目 —— 两者对使用者完全一样，动作都是"删掉这一项"。
        meta.barrelElement = !target
        if (target)
          addUse(target, (el.propertyName ?? el.name).text)
      }
      return
    }
    if (ts.isExportAssignment(node)) {
      register('default', node, 'DefaultExport')
      return
    }
    if (hasExportModifier(node)) {
      const kind = ts.SyntaxKind[node.kind]
      if (node.name && ts.isIdentifier(node.name))
        register(node.name.text, node, kind)
      if (ts.isVariableStatement(node)) {
        for (const d of node.declarationList.declarations) {
          if (ts.isIdentifier(d.name))
            register(d.name.text, node, 'VariableStatement')
        }
      }
    }
  }

  const walkNode = (node) => {
    // `import('@/x').Foo` / `typeof import('@/x')`
    if (ts.isImportTypeNode(node) && ts.isLiteralTypeNode(node.argument)
      && ts.isStringLiteral(node.argument.literal)) {
      const target = resolveModule(file, node.argument.literal.text)
      if (node.qualifier && ts.isIdentifier(node.qualifier)) {
        addUse(target, node.qualifier.text)
      }
      else if (node.qualifier && ts.isLiteralTypeNode(node.qualifier)
        && ts.isStringLiteral(node.qualifier.literal)) {
        addUse(target, node.qualifier.literal.text)
      }
      else {
        addUse(target, 'ALL')
      }
    }
    // `const { a } = await import('@/x')` / `(await import('@/x')).a` / 裸 `import('@/x')`
    if (ts.isCallExpression(node) && node.expression.kind === ts.SyntaxKind.ImportKeyword
      && node.arguments.length === 1 && ts.isStringLiteral(node.arguments[0])) {
      const target = resolveModule(file, node.arguments[0].text)
      const parent = node.parent
      let handled = false
      if (ts.isVariableDeclaration(parent) && ts.isObjectBindingPattern(parent.name)) {
        for (const el of parent.name.elements) {
          addUse(target, (el.propertyName && ts.isIdentifier(el.propertyName) ? el.propertyName : el.name).text)
        }
        handled = true
      }
      else if (ts.isPropertyAccessExpression(parent)) {
        addUse(target, parent.name.text)
        handled = true
      }
      // 其余形态（把整个模块交出去）静态判不了 → 保守：整模块算被用
      if (!handled)
        addUse(target, 'ALL')
    }
    if (ts.isIdentifier(node)) {
      const name = node.text
      identifiers.set(name, (identifiers.get(name) || 0) + 1)
    }
    if (ts.isStatement(node) || ts.isSourceFile(node))
      handleStatement(node)
    const ambient = isAmbientScope(node)
    if (ambient)
      ambientDepth++
    ts.forEachChild(node, walkNode)
    if (ambient)
      ambientDepth--
  }
  walkNode(sf)

  return { file, source, sf, exportsLocal, uses, identifiers }
}

/** 全项目分析：返回 { files, visible } */
export function analyzeProject() {
  const files = SCAN_DIRS.flatMap(dir => walk(dir))
  const analyses = new Map()
  for (const file of files) {
    const a = analyze(file)
    if (a)
      analyses.set(file, a)
  }

  const consumers = new Map()
  for (const [, a] of analyses) {
    for (const [target, names] of a.uses) {
      if (!target || !analyses.has(target))
        continue
      const cur = consumers.get(target)
      if (cur === 'ALL' || names === 'ALL') {
        consumers.set(target, 'ALL')
        continue
      }
      const set = cur || new Set()
      for (const n of names) set.add(n)
      consumers.set(target, set)
    }
  }

  /**
   * 测试文件里的**文本**引用也算"在用"。
   *
   * 本仓有一类契约测试是把源码当字符串读进来断言的（`maskContract.spec.ts` 读
   * `src/api/system/types.ts` 检查 `export interface EnforceReadinessCheck {` 这一格不许空着）。
   * 这类引用静态 import 图看不见，删了就红 —— 所以先做成保护名单，报告里单列一栏。
   */
  const specText = new Set()
  for (const file of files) {
    if (!/__tests__|\.spec\.(?:ts|mts)$/.test(file))
      continue
    const text = readFileSync(file, 'utf8')
    for (const m of text.matchAll(/[A-Z_$][\w$]*/gi))
      specText.add(m[0])
  }

  const visible = []
  for (const [file, a] of analyses) {
    const consumed = consumers.get(file)
    if (consumed === 'ALL')
      continue
    const unused = []
    for (const [name, meta] of a.exportsLocal) {
      if (name === 'default')
        continue
      if (consumed?.has(name))
        continue
      const occurrences = a.identifiers.get(name) || 0
      unused.push({
        // 名字出现在测试文本里 → 不参与清理
        textReferenced: specText.has(name),
        name,
        kind: meta.kind,
        line: meta.line,
        start: meta.start,
        end: meta.end,
        exportModifier: meta.exportModifier,
        isReExport: !!meta.from || !!meta.barrelElement,
        element: meta.element,
        parentStart: meta.parentStart,
        parentEnd: meta.parentEnd,
        usedInsideFile: occurrences > 1,
      })
    }
    if (unused.length)
      visible.push({ file: relative(ROOT, file), abs: file, source: a.source, unused })
  }

  const reportable = visible
    .filter(r => !r.file.endsWith('.d.ts'))
    .filter(r => !SKIP_REPORT.some(re => re.test(r.file)))

  return { files, all: visible, visible: reportable }
}

function main() {
  const { files, visible } = analyzeProject()

  if (process.argv.includes('--json')) {
    // 注意：不能在这里 process.exit() —— stdout 是异步写，大 JSON 会被截断成非法 JSON
    console.log(JSON.stringify(visible, null, 2))
    process.exitCode = visible.length ? 1 : 0
    return
  }

  const all = visible.flatMap(r => r.unused)
  const actionable = all.filter(u => !u.textReferenced)
  const total = actionable.length
  const dead = actionable.filter(u => !u.isReExport && !u.usedInsideFile).length
  const reExports = actionable.filter(u => u.isReExport).length
  const unexportOnly = total - dead - reExports
  const protectedNames = all.length - total

  console.log(`扫描 ${files.length} 个文件，发现 ${total} 个「没人 import 的导出」`)
  console.log(`  · 文件内也不需要（可直接删定义）：${dead}`)
  console.log(`  · 文件内自己还在用（去掉 export 关键字）：${unexportOnly}`)
  console.log(`  · 多余的 re-export 条目（删掉那一项）：${reExports}`)
  if (protectedNames)
    console.log(`  · 被测试文本引用、不清理：${protectedNames}`)
  console.log(`  注：src/framework/** 与 *.d.ts 已排除在报告外\n`)

  for (const r of visible) {
    const rows = r.unused.filter(u => !u.textReferenced)
    if (!rows.length)
      continue
    console.log(`  ${r.file}`)
    for (const u of rows) {
      const tag = u.isReExport
        ? '删掉该 re-export 条目'
        : (u.usedInsideFile ? '去掉 export 关键字' : '删掉定义')
      console.log(`      ${u.name}  (${u.kind}, L${u.line})  → ${tag}`)
    }
  }

  if (process.argv.includes('--check')) {
    if (total) {
      console.log(`\n❌ 存在没人使用的导出：定义要么删掉，要么把 export 收回去。`)
      console.log('   自动化清理：`node scripts/prune-unused-exports.mjs`（先 dry-run 后 --write）。')
    }
    else {
      console.log('\n✅ 没有没人使用的导出')
    }
    process.exitCode = total ? 1 : 0
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href)
  main()

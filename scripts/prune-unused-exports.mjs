#!/usr/bin/env node
/**
 * 按 `find-unused-exports.mjs` 的判定自动清理「没人要的导出」。
 *
 * 三种动作：
 *   1. `删掉定义`           —— 把整段声明（含上方 JSDoc）删掉；
 *   2. `去掉 export 关键字` —— 文件内还在用，只是不该对外暴露；
 *   3. `删掉该 re-export 条目` —— `export { X } from './y'` 里删掉 `X,`。
 *
 * 删除后会再扫一遍同文件的 import，把因此变成「只有它自己出现」的导入名一并清掉
 * （否则 tsc/vitest 会因为 unused import 报错 —— 这是本脚本存在的理由）。
 *
 * 安全边界：
 *   - 只改 `.ts` / `.mts`：`.vue` 的定位偏移要映射回 SFC 原文，收益不值这个风险，
 *     报告里会提示哪些 `.vue` 需要人工看一眼；
 *   - 默认 dry-run，`--write` 才落盘；
 *   - 跑到不动点（删掉 A 可能让同文件的 B 也没人用了；最多 5 轮）。
 *
 * 用法：
 *   node scripts/prune-unused-exports.mjs           # 预览
 *   node scripts/prune-unused-exports.mjs --write   # 落盘
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import process from 'node:process'
import { analyzeProject } from './find-unused-exports.mjs'

const require = createRequire(import.meta.url)
const ts = require('typescript')

const WRITE = process.argv.includes('--write')

/** 删除声明时，连同上方紧邻的 JSDoc 一起删 */
function cutStatement(source, nodeStart, nodeEnd) {
  let start = nodeStart
  const leading = ts.getLeadingCommentRanges(source, nodeStart) || []
  if (leading.length)
    start = leading[0].start
  // 吃掉声明后方到行尾
  let end = nodeEnd
  while (source[end] === ' ' || source[end] === '\t')
    end++
  if (source[end] === '\n')
    end++
  // 吃掉注释前方的缩进空行（避免留下空洞）
  while (start > 0 && (source[start - 1] === ' ' || source[start - 1] === '\t'))
    start--
  return source.slice(0, start) + source.slice(end)
}

/** 删掉「本文件里只有 import 自己提到」的导入名 */
function pruneUnusedImports(file, source) {
  const sf = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  const ids = new Map()
  const collect = (node) => {
    if (ts.isIdentifier(node))
      ids.set(node.text, (ids.get(node.text) || 0) + 1)
    ts.forEachChild(node, collect)
  }
  collect(sf)

  const wholeStatements = []
  const elementEdits = []
  for (const stmt of sf.statements) {
    if (!ts.isImportDeclaration(stmt) || !stmt.importClause)
      continue
    const clause = stmt.importClause
    const bindings = clause.namedBindings
    const used = name => (ids.get(name) || 0) > 1

    const dropElements = bindings && ts.isNamedImports(bindings)
      ? bindings.elements.filter(el => !used(el.name.text))
      : []
    const dropDefault = !!clause.name && !used(clause.name.text)
    const dropNamespace = !!bindings && ts.isNamespaceImport(bindings) && !used(bindings.name.text)

    const keepsDefault = !!clause.name && !dropDefault
    const keepsNamespace = !!bindings && ts.isNamespaceImport(bindings) && !dropNamespace
    const keepsNamed = !!bindings && ts.isNamedImports(bindings)
      && bindings.elements.length - dropElements.length > 0

    if (!keepsDefault && !keepsNamespace && !keepsNamed) {
      wholeStatements.push(stmt)
      continue
    }
    if (dropDefault) {
      // `import X, { A } from 'x'` → 删掉 `X,`
      let end = clause.name.getEnd(sf)
      let cursor = end
      while (source[cursor] === ' ')
        cursor++
      if (source[cursor] === ',')
        end = cursor + 1
      elementEdits.push({ start: clause.name.getStart(sf), end })
    }
    for (const el of dropElements) {
      // 关键：只有在**没有**后继逗号（即它是最后一个元素）时才去吃掉前面的逗号，
      // 否则 `{ A, B, C }` 删 B 会同时吃掉两个逗号 → `{ A C }`（曾经的语法错误）
      let end = el.getEnd(sf)
      let cursor = end
      while (source[cursor] === ' ' || source[cursor] === '\n')
        cursor++
      const hasTrailingComma = source[cursor] === ','
      if (hasTrailingComma)
        end = cursor + 1
      let start = el.getStart(sf)
      if (!hasTrailingComma) {
        let back = start - 1
        while (back >= 0 && (source[back] === ' ' || source[back] === '\n'))
          back--
        if (source[back] === ',')
          start = back
      }
      elementEdits.push({ start, end, trimBefore: false })
    }
  }

  if (!wholeStatements.length && !elementEdits.length)
    return { source, removed: 0 }

  const edits = [
    ...wholeStatements.map(s => ({ start: s.getStart(sf), end: s.getEnd(sf), whole: true })),
    ...elementEdits,
  ].sort((a, b) => b.start - a.start)

  let out = source
  for (const e of edits) {
    if (e.whole) {
      out = cutStatement(out, e.start, e.end)
    }
    else {
      let start = e.start
      if (e.trimBefore) {
        let back = start - 1
        while (back >= 0 && (out[back] === ' ' || out[back] === '\n'))
          back--
        if (out[back] === ',')
          start = back
        else
          start = back + 1
      }
      out = out.slice(0, start) + out.slice(e.end)
    }
  }
  out = out.replace(/import\s*\{\s*\}\s*from\s*['"][^'"]+['"]\n?/g, '')
  out = out.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n')
  return { source: out, removed: edits.length }
}

function processOnce() {
  const { visible } = analyzeProject()
  const touched = []
  const broken = []
  const emptied = []
  const skippedVue = []

  for (const file of visible) {
    if (file.file.endsWith('.vue')) {
      skippedVue.push({ file: file.file, count: file.unused.length })
      continue
    }
    const original = readFileSync(file.abs, 'utf8')
    let source = original

    // 被测试文本引用的（契约测试把源码当字符串读）一律跳过
    const candidates = file.unused.filter(u => !u.textReferenced)
    const removals = candidates.filter(u => !u.isReExport && !u.usedInsideFile)
    const unexports = candidates.filter(u => !u.isReExport && u.usedInsideFile && u.exportModifier)
    const reExports = candidates.filter(u => u.isReExport)

    // 所有区间偏移都基于**本轮开头**的文本，所以本轮只做一种编辑，
    // 其余留给下一轮重扫后再改（否则前一步的增删会让后面的偏移全部错位）。
    let edited = null
    if (removals.length)
      edited = 'removal'
    else if (unexports.length)
      edited = 'unexport'
    else if (reExports.length)
      edited = 're-export'
    if (!edited)
      continue

    // 1) 整段删除（从后往前）
    if (edited === 'removal') {
      for (const u of [...removals].sort((a, b) => b.start - a.start))
        source = cutStatement(source, u.start, u.end)
    }

    // 2) 去掉 export 关键字
    if (edited === 'unexport') {
      for (const u of [...unexports].sort((a, b) => b.start - a.start)) {
        const m = u.exportModifier
        let end = m.end
        if (source.startsWith('  ', end))
          end += 2
        else if (source[end] === ' ')
          end += 1
        source = source.slice(0, m.start) + source.slice(end)
      }
    }

    // 3) 删 re-export 条目
    if (edited === 're-export') {
      const cuts = reExports.map((u) => {
        let end = u.element.end
        let cursor = end
        while (source[cursor] === ' ' || source[cursor] === '\n')
          cursor++
        const hasTrailingComma = source[cursor] === ','
        if (hasTrailingComma)
          end = cursor + 1
        let start = u.element.start
        if (!hasTrailingComma) {
          let back = start - 1
          while (back >= 0 && (source[back] === ' ' || source[back] === '\n'))
            back--
          if (source[back] === ',')
            start = back
        }
        return { start, end }
      }).sort((a, b) => b.start - a.start)
      for (const c of cuts)
        source = source.slice(0, c.start) + source.slice(c.end)
      source = source.replace(/export\s*\{\s*\}\s*(?:from\s*['"][^'"]+['"])?\n?/g, '')
    }

    // 4) 清理因此变成"只被 import 自己提到"的导入
    const pruned = pruneUnusedImports(file.abs, source)
    source = pruned.source.replace(/[ \t]+\n/g, '\n').replace(/\n{3,}/g, '\n\n')

    if (source !== original && !/[\w'"]/.test(source)) {
      emptied.push(file.file)
      if (WRITE)
        writeFileSync(file.abs, source.replace(/^\n+/, ''))
      continue
    }

    if (source !== original) {
      const check = ts.createSourceFile(file.abs, source, ts.ScriptTarget.Latest, true)
      if (check.parseDiagnostics?.length) {
        // 自检失败：宁可不改，也不要把语法坏掉的代码写进仓库
        broken.push({
          file: file.file,
          detail: check.parseDiagnostics
            .slice(0, 3)
            .map(d => `L${check.getLineAndCharacterOfPosition(d.start).line + 1}: ${ts.flattenDiagnosticMessageText(d.messageText, ' ')}`)
            .join('; '),
        })
        continue
      }
      touched.push({
        file: file.file,
        removed: removals.length,
        unexported: unexports.length,
        reExports: reExports.length,
        importsRemoved: pruned.removed,
      })
      if (WRITE)
        writeFileSync(file.abs, source)
    }
  }
  return { touched, broken, emptied, skippedVue }
}

let round = 0
const all = []
const emptiedNotes = []
let vueNotes = []
while (round < 8) {
  const { touched, broken, emptied, skippedVue } = processOnce()
  vueNotes = skippedVue
  if (emptied.length)
    emptiedNotes.push(...emptied)
  if (broken.length) {
    console.log(`⚠️  ${broken.length} 个文件自检失败（已跳过，未写入）：`)
    for (const b of broken) console.log(`     ${b.file}  ${b.detail}`)
  }
  if (!touched.length)
    break
  all.push(...touched)
  console.log(`第 ${round + 1} 轮：${touched.length} 个文件`)
  for (const t of touched)
    console.log(`  ${t.file}  删定义 ${t.removed} · 去 export ${t.unexported} · 删 re-export ${t.reExports} · 清 import ${t.importsRemoved}`)
  round++
  if (!WRITE)
    break
}

if (emptiedNotes.length) {
  console.log('\n🗑  以下文件被清空，整文件已无人引用，交给 `find-unused-frontend.mjs --delete` 收尾：')
  for (const f of emptiedNotes)
    console.log(`     ${f}`)
}

if (vueNotes.length) {
  console.log('\nℹ️  需要人工看一眼的 .vue（脚本不动 SFC 的偏移）：')
  for (const v of vueNotes)
    console.log(`     ${v.file}  ${v.count} 处`)
}

const totalRemoved = all.reduce((s, t) => s + t.removed + t.reExports, 0)
console.log(`\n${WRITE ? '✅ 已落盘' : '（dry-run，加 --write 落盘）'}：共清理 ${all.length} 个文件、${totalRemoved} 处死导出`)

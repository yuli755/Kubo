#!/usr/bin/env node
/**
 * # airpower 退场进度扫描（F5 的验收口径）
 *
 * 只回答一个问题：**还有多少文件真的 import 了 `@airpower/web`**。
 *
 * ## 为什么要剥注释
 * 迁移过程中大量文件会写"这里原来是 `import { ATable } from '@airpower/web'`，现在换成…"这类
 * 说明性注释（还有 `assetDomain.spec.ts` 里那条用来断言"没在 import airpower"的正则）。
 * 直接 `grep -rl '@airpower/web'` 会把这些算成"仍在用"，数字只涨不跌，失去指导意义。
 *
 * ## 口径
 * - 统计 **import / export from / 动态 import()** 三种真实引用；
 * - 注释剥离保留字符串（否则 `'@airpower/web'` 这个字面量本身会被吃掉）；
 * - 测试文件单独报一个数（`*.spec.ts`），它们可能是 `vi.mock('@airpower/web')`，不影响运行时退场。
 *
 * 用法：
 * ```
 * node scripts/find-airpower.mjs          # 汇总
 * node scripts/find-airpower.mjs --list   # 列出文件（按目录分组）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..')
const SRC = path.join(ROOT, 'src')
const TARGETS = ['@airpower/web', '@airpower/core']

/** 剥掉注释、保留字符串与换行（行号不保证，但 import 语句是否成对出现不受影响） */
function stripComments(code) {
  let out = ''
  let state = 'code' // code | line | block | single | double | template
  for (let i = 0; i < code.length; i += 1) {
    const c = code[i]
    const next = code[i + 1]
    if (state === 'code') {
      if (c === '/' && next === '/') {
        state = 'line'
        i += 1
        continue
      }
      if (c === '/' && next === '*') {
        state = 'block'
        i += 1
        continue
      }
      if (c === '\'') {
        state = 'single'
      }
      else if (c === '"') {
        state = 'double'
      }
      else if (c === '`') {
        state = 'template'
      }
    }
    else if (state === 'line') {
      if (c === '\n') {
        state = 'code'
        out += c
      }
      continue
    }
    else if (state === 'block') {
      if (c === '*' && next === '/') {
        state = 'code'
        i += 1
      }
      else if (c === '\n') {
        out += c
      }
      continue
    }
    else if (state === 'single') {
      if (c === '\\') {
        out += c + (next ?? '')
        i += 1
        continue
      }
      if (c === '\'') {
        state = 'code'
      }
    }
    else if (state === 'double') {
      if (c === '\\') {
        out += c + (next ?? '')
        i += 1
        continue
      }
      if (c === '"') {
        state = 'code'
      }
    }
    else if (state === 'template') {
      if (c === '\\') {
        out += c + (next ?? '')
        i += 1
        continue
      }
      if (c === '`') {
        state = 'code'
      }
    }
    out += c
  }
  return out
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory())
      walk(full, files)
    else if (/\.(?:ts|vue|mts)$/.test(entry.name))
      files.push(full)
  }
  return files
}

const files = walk(SRC)
const importing = []
const mentioning = []

for (const file of files) {
  const raw = fs.readFileSync(file, 'utf8')
  const code = stripComments(raw)
  // import/export … from '…'（可跨行）+ 动态 import('…')
  const staticRe = /(?:^|\n)\s*(?:import|export)[\s\S]{0,400}?from\s*['"]([^'"]+)['"]/g
  const dynamicRe = /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g
  const hit = [...code.matchAll(staticRe), ...code.matchAll(dynamicRe)]
    .some(match => TARGETS.some(t => match[1] === t || match[1].startsWith(`${t}/`)))
  if (hit)
    importing.push(path.relative(ROOT, file))
  else if (TARGETS.some(t => raw.includes(t)))
    mentioning.push(path.relative(ROOT, file))
}

const isSpec = f => f.includes('__tests__') || f.endsWith('.spec.ts')
const production = importing.filter(f => !isSpec(f))

if (process.argv.includes('--list')) {
  const byDir = {}
  for (const file of production) {
    const dir = path.dirname(file)
    ;(byDir[dir] ??= []).push(path.basename(file))
  }
  for (const dir of Object.keys(byDir).sort()) {
    console.log(`\n${dir}/`)
    for (const name of byDir[dir].sort()) console.log(`  · ${name}`)
  }
  console.log('')
}

console.log(`airpower 仍在 import 的文件：${production.length} 个（生产代码）`)
console.log(`  · 测试文件另计：${importing.length - production.length} 个`)
console.log(`  · 只在注释里提到它：${mentioning.length} 个`)

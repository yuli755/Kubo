#!/usr/bin/env node
/**
 * # airpower 退场门禁（防止 `@airpower/web` 回来）
 *
 * 2026-09-18 前端已把 `@airpower/web` 从代码与依赖里删净（连带它的两个构建插件）。
 * 这个脚本从那一天的"进度扫描"转成**硬门禁**：仓库里只要再出现一条真实引用就退出码 1，
 * 并接进 CI（`pnpm airpower:check`）。
 *
 * ## 为什么要剥注释
 * 迁移留下了大量说明性注释（"这里原来是 `import { ATable } from '@airpower/web'`，现在换成…"），
 * 它们是档案不是依赖。直接 `grep` 会把档案算成违规 —— 于是人人学会在门禁前删注释，
 * 最后档案没了、违规也照样能过。
 *
 * ## 口径
 * - 扫描：`src/`、`infra/`、`scripts/`、根配置（vite/vitest/tsconfig）、`package.json` 依赖表；
 * - 命中：`import/export … from '…'`、动态 `import('…')`、package.json 里的依赖名；
 * - 注释（行注释 / 块注释 / HTML 注释）里的提及不算违规。
 *
 * 用法：
 * ```
 * pnpm airpower:check          # 有残留 → 非零退出
 * pnpm airpower:check --list   # 顺带列出"只在注释里提到"的文件（了解来龙去脉用）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..')
// 拼接而非字面量：本文件自身也在扫描范围内，字面量会让自己永远"命中"
const PREFIX = `@airpower${'/'}`
const SCAN_DIRS = ['src', 'infra', 'scripts']
const SCAN_FILES = ['vite.config.mts', 'vitest.config.ts', 'tsconfig.json', 'tsconfig.app.json', 'package.json']

/** 剥掉注释、保留字符串（`'@airpower/web'` 这个字面量本身不能当成注释吃掉） */
function stripComments(code) {
  let out = ''
  let state = 'code'
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
    if (entry.name === 'node_modules' || entry.name === 'dist' || entry.name === 'coverage') {
      continue
    }
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, files)
    }
    else if (/\.(?:ts|vue|mts|mjs|js|json)$/.test(entry.name)) {
      files.push(full)
    }
  }
  return files
}

const files = [
  ...SCAN_DIRS.filter(dir => fs.existsSync(path.join(ROOT, dir))).flatMap(dir => walk(path.join(ROOT, dir))),
  ...SCAN_FILES.map(f => path.join(ROOT, f)).filter(f => fs.existsSync(f)),
]

const STATIC_RE = /(?:^|\n)\s*(?:import|export)[\s\S]{0,400}?from\s*['"]([^'"]+)['"]/g
const DYNAMIC_RE = /\bimport\s*\(\s*['"]([^'"]+)['"]\s*\)/g

const violations = []
const mentioning = []

for (const file of files) {
  const relative = path.relative(ROOT, file).replaceAll('\\', '/')
  const raw = fs.readFileSync(file, 'utf8')
  const code = stripComments(raw)

  if (file.endsWith('package.json')) {
    // 包名只会写在依赖表里，直接找 `"@airpower/` 字面量即可（不必解析 JSON 结构）
    const dependencyLine = code.split('\n').find(line => line.includes(`"${PREFIX}`))
    if (dependencyLine) {
      violations.push(`${relative} 依赖表里仍有 ${dependencyLine.trim().split(':')[0]}`)
    }
    continue
  }

  const specs = [...code.matchAll(STATIC_RE), ...code.matchAll(DYNAMIC_RE)].map(m => m[1])
  const hit = specs.filter(spec => spec === PREFIX.slice(0, -1) || spec.startsWith(PREFIX))
  if (hit.length) {
    violations.push(`${relative} 仍在 import：${[...new Set(hit)].join(', ')}`)
  }
  else if (raw.includes(PREFIX)) {
    mentioning.push(relative)
  }
}

if (process.argv.includes('--list') && mentioning.length) {
  console.log('\n只在注释/文档里提到 airpower（不算违规）：')
  for (const file of mentioning.sort()) {
    console.log(`  · ${file}`)
  }
}

if (violations.length) {
  console.error(`\n✗ airpower 退场门禁：${violations.length} 处残留\n`)
  for (const item of violations) {
    console.error(`  · ${item}`)
  }
  console.error('\nairpower 已于 2026-09-18 整体退场，底座是 @/framework（KTable/KFormDialog/useTable…）。')
  console.error('迁移留下的历史说明请写在注释里（注释不计入本门禁），代码里不要再引用它。\n')
  process.exit(1)
}

console.log(`✓ airpower 退场门禁通过：0 处引用（扫描 ${files.length} 个文件；注释里的历史提及 ${mentioning.length} 处不计）`)

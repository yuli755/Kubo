#!/usr/bin/env node
/**
 * Java 源文件结构检查（无 JDK 环境下的兜底）
 *
 * 沙箱里没有 javac、也没有 maven 依赖，无法真编译；这里做**结构性**检查，
 * 用于替代「改完 Java 直接提交」的裸奔：
 *   - 字符串/字符字面量、文本块是否正确闭合（解析器本身是字符串感知的）
 *   - 花括号 / 圆括号 / 方括号配对
 *   - 单行完整语句（`add(...)` / `return ...`）漏写分号
 *   - 统计类/方法数量，便于与预期核对
 *
 * ⚠️ 它是**兜底**，不是编译器：能抓住括号/引号/分号这类低级错误，
 * 抓不住类型错误。真正的编译仍以 `mvn -q compile`（本机或 CI）为准。
 *
 * 用法：node scripts/java-syntax-check.mjs <文件或目录> [...]
 */

import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import process from 'node:process'

/**
 * 字符串感知的词法扫描。
 *
 * @returns {{ stripped: string, code: string }} stripped = 去除注释与字面量内容（用于括号配对）；
 *          code = 去除注释但保留字面量（用于逐行引号检查）
 */
function scan(source) {
  let stripped = ''
  let code = ''
  let i = 0
  const n = source.length
  while (i < n) {
    const c = source[i]
    const next = source[i + 1]

    // 行注释：整行丢弃（保留换行以维持行号）
    if (c === '/' && next === '/') {
      while (i < n && source[i] !== '\n') i++
      continue
    }
    // 块注释
    if (c === '/' && next === '*') {
      i += 2
      while (i < n && !(source[i] === '*' && source[i + 1] === '/')) {
        if (source[i] === '\n') {
          stripped += '\n'
          code += '\n'
        }
        i++
      }
      i += 2
      continue
    }
    // 文本块 """ ... """
    if (c === '"' && source.startsWith('"""', i)) {
      i += 3
      let closed = false
      while (i < n) {
        if (source.startsWith('"""', i)) {
          i += 3
          closed = true
          break
        }
        i++
      }
      stripped += closed ? '""' : '"""'
      code += closed ? '""' : '"""'
      continue
    }
    // 字符串
    if (c === '"') {
      i++
      let value = '"'
      while (i < n && source[i] !== '"') {
        if (source[i] === '\\') {
          value += source[i] + (source[i + 1] ?? '')
          i += 2
          continue
        }
        value += source[i]
        i++
      }
      i++
      stripped += '""'
      code += `${value}"`
      continue
    }
    // 字符字面量
    if (c === '\'') {
      i++
      while (i < n && source[i] !== '\'') {
        if (source[i] === '\\')
          i++
        i++
      }
      i++
      stripped += '\'\''
      code += '\'\''
      continue
    }
    stripped += c
    code += c
    i++
  }
  return { stripped, code }
}

function balanceOf(text, open, close) {
  let depth = 0
  let min = 0
  for (const ch of text) {
    if (ch === open)
      depth++
    if (ch === close) {
      depth--
      min = Math.min(min, depth)
    }
  }
  return { depth, min }
}

function checkFile(file) {
  const raw = readFileSync(file, 'utf8')
  const problems = []
  const { stripped, code } = scan(raw)

  for (const [open, close, name] of [['{', '}', '花括号'], ['(', ')', '圆括号'], ['[', ']', '方括号']]) {
    const { depth, min } = balanceOf(stripped, open, close)
    if (depth !== 0)
      problems.push(`${name}不配对（差 ${depth}）`)
    if (min < 0)
      problems.push(`${name}出现提前闭合`)
  }

  // 逐行引号计数（注释已剔除、字符串保留，故每行引号数必须为偶数）
  for (const [index, line] of code.split('\n').entries()) {
    const quotes = (line.match(/(?<!\\)"/g) || []).length
    if (quotes % 2 !== 0) {
      problems.push(`第 ${index + 1} 行字符串可能未闭合：${line.trim().slice(0, 80)}`)
    }
  }

  // 语句级：单行内括号已配平且无续行时，检查是否漏了分号
  const rawLines = code.split('\n')
  const CONTINUATION = /^[.?:),]|^(?:&&|\|\||[+\-*/]|==|!=|>=|<=)/
  for (const [index, line] of rawLines.entries()) {
    const trimmed = line.trim()
    if (!trimmed || trimmed.startsWith('*') || trimmed.startsWith('@'))
      continue
    const nextLine = (rawLines[index + 1] || '').trim()
    if (CONTINUATION.test(nextLine))
      continue
    if (balanceOf(trimmed, '(', ')').depth !== 0)
      continue
    if (/^(?:add|addAndGet|return|throw)\b/.test(trimmed) && !/[;{},]$/.test(trimmed)) {
      problems.push(`第 ${index + 1} 行语句可能缺少分号：${trimmed.slice(0, 80)}`)
    }
  }

  const classes = (stripped.match(/\bclass\s+\w+/g) || []).length
  const methods = (stripped.match(/\b(?:public|private|protected)\s[\w<>[\],\s]+\s\w+\s*\(/g) || []).length
  return { problems, classes, methods }
}

function collect(target) {
  if (statSync(target).isFile())
    return [target]
  const files = []
  for (const entry of readdirSync(target)) files.push(...collect(join(target, entry)))
  return files.filter(f => f.endsWith('.java'))
}

const targets = process.argv.slice(2)
if (!targets.length) {
  console.error('用法：node scripts/java-syntax-check.mjs <文件或目录> [...]')
  process.exit(2)
}

let checked = 0
let failed = 0
for (const target of targets) {
  for (const file of collect(target)) {
    checked++
    const { problems, classes, methods } = checkFile(file)
    if (problems.length) {
      failed++
      console.error(`❌ ${file}`)
      for (const problem of problems.slice(0, 8)) console.error(`   - ${problem}`)
    }
    else {
      console.log(`✅ ${file}（类 ${classes} / 方法 ${methods}）`)
    }
  }
}
console.log(`\n共检查 ${checked} 个文件，${failed} 个有问题`)
process.exit(failed ? 1 : 0)

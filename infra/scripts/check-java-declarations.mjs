/**
 * # 门禁：Java 声明级错误（`pnpm java:decl`）
 *
 * ## 为什么要有它
 *
 * 这个仓的 Java 改动有很大一部分是**脚本批量改**的（搬类、加参数、改 import），而门禁链里
 * 一直缺"javac 会当场拒绝的那类错误"的检查 —— 因为沙箱里没有 JDK，编译留在使用者的机器上。
 * 代价已经出现过一次：L2b 的批量脚本给 `SystemServices.initService` 插参数时插重了一个
 * （同一方法签名里 `DataFieldPolicyProvider dataFieldPolicyService` 出现两次），
 * **提交进了仓库、过了 21 道门禁、没人发现** —— `mvn compile` 一跑就是红的。
 *
 * 所以这里只做 javac 一定会拒绝、而脚本最容易撞出来、且**不需要类型信息**的两类错误：
 *
 * | 规则 | 内容 | javac 的报错 |
 * | --- | --- | --- |
 * | D1 | 方法/构造器参数表里**形参名重复** | `variable x is already defined` |
 * | D2 | 类体里**字段名重复**（只算类体第一层，见下） | `variable x is already defined` |
 *
 * 第一版还写了 D3「同名 + 同参型的方法重复」。**砍掉了**：它需要按类体作用域判定，
 * 而文件级文本扫描会把 `if (...)` / `switch (...)` / 另一个类里的 `of()` 都算成"方法" ——
 * 实测在本仓报了 4 条假命中（`DataPermissionSpecification.if(...)` 这种）。
 * 逐类体切分要做真正的花括号配对 + 作用域跟踪，收益不抵复杂度：**宁可少一条规则，不要一条会误报的规则**
 * （判据误报的代价是所有人开始忽略它）。
 *
 * ## 判据口径
 *
 * - 先剥注释（复用 `lib/java-source.mjs`），避免注释里的示例被当成声明；
 * - 形参按 `<>` / `()` 深度切分，形参名取每一段里**最后一个标识符**（跳过 `@NotNull` 之类注解）；
 * - **D2 只认类体第一层**（行首花括号深度 == 1）：方法里的局部变量也在第一层的语义之外，
 *   这样就不会把 `final String finalSource = ...;` 这种局部变量当成字段（第一版就是这么误报的）；
 * - 只看 `src/main/java`（测试代码由 `mvn test` 覆盖）。
 *
 * ## 用法
 * ```bash
 * pnpm java:decl              # 人读
 * pnpm java:decl -- --check   # 门禁（有命中即 1）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SRC = path.join(ROOT, 'SPMS-Server', 'src', 'main', 'java')

/** 按分隔符切分，忽略 `<>` 与 `()` 内部的同一分隔符 */
function splitTopLevel(text, separator) {
  const parts = []
  let depth = 0
  let current = ''
  for (const ch of text) {
    if (ch === '<' || ch === '(' || ch === '[') depth += 1
    if (ch === '>' || ch === ')' || ch === ']') depth -= 1
    if (ch === separator && depth === 0) {
      parts.push(current)
      current = ''
      continue
    }
    current += ch
  }
  parts.push(current)
  return parts
}

const IDENTIFIER = /[A-Za-z_$][\w$]*/g

/** 形参段 → 形参名（最后一个标识符）；取不到返回 null（如 `void` 空参、`...` 残缺） */
function parameterName(segment) {
  const stripped = segment
    .replace(/@\w+(\([^)]*\))?/g, ' ') // 参数级注解
    .replace(/\b(final|@NotNull|@Nullable)\b/g, ' ')
  const names = stripped.match(IDENTIFIER)
  return names ? names[names.length - 1] : null
}

/** 形参段 → 参型（去掉泛型参数、注解、空白、`final`、变长点） */
/** 抽出一个文件里的方法/构造器签名：`{ name, params, line }` */
function collectSignatures(code) {
  const signatures = []
  const pattern = /([A-Za-z_$][\w$<>,.[\]\s?]*?)\s+([A-Za-z_$][\w$]*)\s*\(([^;{}]*)\)\s*(\{|throws)/g
  let match = pattern.exec(code)
  while (match !== null) {
    const [, , name, rawParams] = match
    const line = code.slice(0, match.index).split('\n').length
    const params = splitTopLevel(rawParams, ',')
      .map(s => s.trim())
      .filter(s => s.length > 0)
    signatures.push({
      name,
      line,
      params: params.map(s => ({ name: parameterName(s) })),
    })
    match = pattern.exec(code)
  }
  return signatures
}

/**
 * 抽出一个文件里**类体第一层**的字段声明（`private Type name;` / `private Type name = ...;`）。
 *
 * 为什么限定第一层：字段与局部变量的区别就是"在不在类体里"。第一版按行扫，
 * 把 `final String finalSource = ...;`（局部变量）也当字段，报了一堆假命中。
 * 内层类（嵌套类）的字段因此会被漏掉 —— 这是**故意**的取舍：宁可漏，不要误报。
 */
function collectFields(code) {
  const fields = []
  const pattern = /^\s*(?:(?:private|protected|public|static|final|transient|volatile)\s+)+[A-Za-z_$][\w$<>,.[\]\s?]*?\s+([A-Za-z_$][\w$]*)\s*(?:=[^;]*)?;\s*$/gm
  let depth = 0
  let line = 1
  let match = pattern.exec(code)
  let cursor = 0
  const atLineStartDepth = new Map()
  while (cursor < code.length) {
    const ch = code[cursor]
    if (ch === '\n') {
      line += 1
      atLineStartDepth.set(line, depth)
    }
    if (ch === '{') depth += 1
    if (ch === '}') depth -= 1
    cursor += 1
  }
  while (match !== null) {
    const line = code.slice(0, match.index).split('\n').length
    // 行首所在的花括号深度（类体 = 1；方法体里 = 2+）
    if (atLineStartDepth.get(line) === 1 && !match[0].includes('(')) {
      fields.push({ name: match[1], line })
    }
    match = pattern.exec(code)
  }
  return fields
}

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, acc)
    else if (entry.name.endsWith('.java')) acc.push(full)
  }
  return acc
}

const RULE_DESC = {
  D1: '形参名重复：javac 会报 variable ... is already defined',
  D2: '字段名重复（类体第一层）：javac 会报 variable ... is already defined',
}

const files = walk(SRC)
const violations = []

for (const abs of files) {
  const rel = path.relative(ROOT, abs).split(path.sep).join('/')
  const code = stripJavaComments(fs.readFileSync(abs, 'utf8'))

  for (const signature of collectSignatures(code)) {
    const seen = new Map()
    for (const param of signature.params) {
      if (!param.name) continue
      if (seen.has(param.name)) {
        violations.push({
          rule: 'D1',
          file: rel,
          line: signature.line,
          detail: `${signature.name}(...) 的形参 ${param.name} 出现两次`,
        })
      }
      seen.set(param.name, true)
    }
  }

  const fieldNames = new Map()
  for (const field of collectFields(code)) {
    if (fieldNames.has(field.name)) {
      violations.push({
        rule: 'D2',
        file: rel,
        line: field.line,
        detail: `字段 ${field.name} 重复声明（上一次在第 ${fieldNames.get(field.name)} 行）`,
      })
    }
    fieldNames.set(field.name, field.line)
  }
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify({ files: files.length, violations }, null, 2))
}
else {
  console.log('Java 声明级门禁：形参重名 / 字段重名（javac 一定会拒绝、且不会误报的两类）')
  console.log(`  扫描：${files.length} 个 .java（src/main/java，剥注释）`)
  if (violations.length === 0) {
    console.log('✓ 没有声明级错误')
  }
  else {
    console.log(`✗ ${violations.length} 处命中：`)
    for (const v of violations) {
      console.log(`  [${v.rule}] ${v.file}:${v.line}  ${v.detail}   —— ${RULE_DESC[v.rule]}`)
    }
  }
}

if (process.argv.includes('--check') && violations.length > 0) process.exit(1)

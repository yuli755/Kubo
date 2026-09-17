#!/usr/bin/env node
/**
 * # 门禁：Java 文件里不许写 SQL（SQL 只许住在专属文件里）
 *
 * ## 规则（用户 2026-09-18 定，属于"代码分层"）
 * > **禁止 `.java` 文件编写 SQL 语句，只能使用专属文件编写。**
 *
 * 「专属文件」= `SPMS-Server/src/main/resources/mapper/**\/*.xml`（MyBatis 的 mapper XML，
 * 由 `mybatis-plus.mapper-locations: classpath*:/mapper/**\/*.xml` 加载）。
 *
 * ## 为什么这条规则值得写成门禁
 * 1. **SQL 可以被 review、被 DBA 读到**：散在 Java 字符串里时，`grep -r "select"` 出来的是一堆
 *    拼接与转义；放进 XML 后，一条语句一眼能看全，索引/字段名可以直接拿去 `EXPLAIN`；
 * 2. **换 ORM 的成本差一个数量级**：JPA 的 `@Query` 与 MyBatis 的 `@Select` 都让"查询"躲在注解里，
 *    本仓刚花了两批把 JPA 请出去 —— 如果换成 `@Select` 继续写 Java，等于把同一道题重做一遍；
 * 3. **XML 是 MyBatis 的官方分层**：接口只声明"要做什么"，SQL 在 XML 里写"怎么做"。
 *
 * ## 判据（剥注释后）
 * - A 类：**字符串字面量**里出现 SQL 语句特征 —— `select … from` / `insert into` / `delete from` /
 *   `update … set` / `truncate table` / `alter table` / `create table` / `drop table` /
 *   `for update` / `order by` / `group by` / `limit <数字>`
 * - B 类：**SQL 载体注解** —— `@Select` / `@Insert` / `@Update` / `@Delete` / `@SelectKey` /
 *   `@*Provider` / `@Query`（JPQL 与原生 SQL 都算）/ `nativeQuery = true` / `createNativeQuery(`
 *
 * 只扫字符串字面量与注解，**不扫标识符**：`sortBy`、`orderNo`、`updateTime` 这些名字里带
 * SQL 单词的字段不会被误报（这是上一版 JPA 棘轮踩过的坑：子串匹配把 `@TableName` 算成了 `@Table`）。
 *
 * ## 唯一的豁免：`// SQL-IGNORE: 理由`
 * 只给**非可执行 SQL** 用，且必须写在违规行的前一行（或同一行）：
 * - 安全测试里的**恶意输入样例**（`"1; DROP TABLE x"` 是"值"，测试断言它不会被拼进 SQL）；
 * - 被测对象的输入（如给 JSqlParser 解析用的探针语句）。
 * 豁免会**被统计并打印**：`pnpm sql:check` 每次都列出所有豁免与理由，所以"悄悄加一个豁免"在
 * review 里是可见的。生产代码（`src/main/java`）不留任何豁免 —— 可执行 SQL 一律进 mapper XML。
 *
 * ## 用法
 * ```bash
 * pnpm sql:check              # 人读：列出所有违规（文件:行 + 片段）
 * pnpm sql:check -- --json    # 给脚本/CI 读
 * pnpm sql:check -- --check   # 门禁：有违规即 exit 1（没有基线，规则是"零"）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')
const JAVA_DIRS = [path.join(SERVER, 'src', 'main', 'java'), path.join(SERVER, 'src', 'test', 'java')]
const MAPPER_XML_DIR = path.join(SERVER, 'src', 'main', 'resources', 'mapper')

/**
 * A 类：字符串字面量里的 SQL 特征。
 * 刻意全部用词边界 + 大小写不敏感；`select` 必须跟到 `from` 才算（避免 "select the user" 这类提示语误报）。
 */
const LITERAL_RULES = [
  { name: 'select…from', re: /\bselect\b[\s\S]{0,400}?\bfrom\b/i },
  { name: 'insert into', re: /\binsert\s+into\b/i },
  { name: 'delete from', re: /\bdelete\s+from\b/i },
  { name: 'update…set', re: /\bupdate\s+[\w`".]+\s+set\b/i },
  { name: 'truncate table', re: /\btruncate\s+table\b/i },
  { name: 'alter table', re: /\balter\s+table\b/i },
  { name: 'create table', re: /\bcreate\s+table\b/i },
  { name: 'drop table', re: /\bdrop\s+table\b/i },
  { name: 'for update', re: /\bfor\s+update\b/i },
  { name: 'order by', re: /\border\s+by\b/i },
  { name: 'group by', re: /\bgroup\s+by\b/i },
  { name: 'limit N', re: /\blimit\s+\d/i },
  { name: 'where (裸片段)', re: /["'\s]\bwhere\s+[\w`".]+\s*(=|>|<|in\b|like\b)/i },
]

/** B 类：SQL 载体注解 / API（注解名本身不是 SQL，但它出现即意味着"SQL 写在 Java 里"）。 */
const ANNOTATION_RULES = [
  { name: '@Select', re: /@Select\s*[({]/ },
  { name: '@Insert', re: /@Insert\s*[({]/ },
  { name: '@Update', re: /@Update\s*[({]/ },
  { name: '@Delete', re: /@Delete\s*[({]/ },
  { name: '@SelectKey', re: /@SelectKey\s*[({]/ },
  { name: '@SelectProvider', re: /@SelectProvider\s*[({]/ },
  { name: '@InsertProvider', re: /@InsertProvider\s*[({]/ },
  { name: '@UpdateProvider', re: /@UpdateProvider\s*[({]/ },
  { name: '@DeleteProvider', re: /@DeleteProvider\s*[({]/ },
  { name: '@Query', re: /@Query\s*[({]/ },
  { name: 'nativeQuery', re: /\bnativeQuery\s*=\s*true\b/ },
  { name: 'createNativeQuery', re: /\.createNativeQuery\s*\(/ },
]

/** 从剥完注释的源码里抽出字符串字面量（含起始偏移，便于换算行号）。 */
function stringLiterals(code) {
  const out = []
  let i = 0
  while (i < code.length) {
    const ch = code[i]
    if (ch === '"' || ch === "'") {
      const quote = ch
      let j = i + 1
      let text = ''
      while (j < code.length) {
        if (code[j] === '\\') {
          text += code[j] + (code[j + 1] ?? '')
          j += 2
          continue
        }
        if (code[j] === quote) break
        text += code[j]
        j += 1
      }
      out.push({ text, start: i })
      i = j + 1
      continue
    }
    i += 1
  }
  return out
}

/** 偏移 → 行号（1 起） */
function lineOf(code, offset) {
  let line = 1
  for (let i = 0; i < offset && i < code.length; i += 1) {
    if (code[i] === '\n') line += 1
  }
  return line
}

function walkJava(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walkJava(full, acc)
    else if (entry.name.endsWith('.java')) acc.push(full)
  }
  return acc
}

const IGNORE_MARK = /\/\/\s*SQL-IGNORE\s*:?\s*(\S.*)?$/

function checkFile(file) {
  const raw = fs.readFileSync(file, 'utf8')
  const code = stripJavaComments(raw)
  const findings = []
  // 豁免标记按"原始行"找（剥注释后标记本身也没了）：标记所在行 + 下一行算豁免
  const ignoredLines = new Set()
  const exemptions = []
  raw.split('\n').forEach((line, idx) => {
    if (IGNORE_MARK.test(line)) {
      ignoredLines.add(idx + 1)
      ignoredLines.add(idx + 2)
      exemptions.push({ line: idx + 1, reason: (line.match(IGNORE_MARK)[1] ?? '').trim() })
    }
  })

  for (const lit of stringLiterals(code)) {
    // 转义还原，便于规则命中 `\"` 之类的写法
    const value = lit.text.replace(/\\(.)/g, '$1')
    for (const rule of LITERAL_RULES) {
      if (rule.re.test(value)) {
        findings.push({
          kind: 'literal',
          rule: rule.name,
          line: lineOf(code, lit.start),
          snippet: value.length > 90 ? `${value.slice(0, 87)}…` : value,
        })
      }
    }
  }

  code.split('\n').forEach((line, idx) => {
    for (const rule of ANNOTATION_RULES) {
      if (rule.re.test(line)) {
        findings.push({ kind: 'annotation', rule: rule.name, line: idx + 1, snippet: line.trim().slice(0, 90) })
      }
    }
  })

  return { findings: findings.filter(f => !ignoredLines.has(f.line)), exemptions }
}

/** 门禁自检：规则本身要能分清"SQL"与"看着像 SQL 的标识符"。 */
function selfTest() {
  const cases = [
    { text: 'const x = "delete from t where id = 1"', expect: true, rule: 'delete from' },
    { text: 'int deleteById(Long id)', expect: false },
    { text: 'private long orderNo;', expect: false },
    { text: 'wrapper.orderByAsc(Entity::getId)', expect: false },
    { text: 'public static final String ORDER_BY = "orderNo"', expect: false },
    { text: 'String sql = "select id from t for update"', expect: true, rule: 'for update' },
    { text: 'long updateTime = System.currentTimeMillis()', expect: false },
    { text: 'mp.updateById(entity)', expect: false },
    { text: 'int increaseQuantityById(long id, double delta)', expect: false },
    { text: '"update wms_inventory set quantity = quantity + 1"', expect: true, rule: 'update…set' },
  ]
  let failed = 0
  for (const c of cases) {
    const code = c.text
    const literals = stringLiterals(code)
    const hitLiteral = literals.some(lit => {
      const value = lit.text.replace(/\\(.)/g, '$1')
      return LITERAL_RULES.some(r => r.re.test(value))
    })
    const hitAnnotation = code.split('\n').some(line => ANNOTATION_RULES.some(r => r.re.test(line)))
    const hit = hitLiteral || hitAnnotation
    if (hit !== c.expect) {
      failed += 1
      console.error(`  ✗ 自检失败：${JSON.stringify(c.text)} 期望 ${c.expect ? '命中' : '不命中'}`)
    }
  }
  return failed
}

// ── 主流程
if (process.argv.includes('--self-test')) {
  const failed = selfTest()
  console.log(failed === 0 ? '✓ 自检 10 例通过' : `✗ 自检 ${failed} 例失败`)
  process.exit(failed === 0 ? 0 : 1)
}

const files = JAVA_DIRS.flatMap(dir => walkJava(dir))
const violations = []
const exemptions = []
for (const file of files) {
  const result = checkFile(file)
  for (const finding of result.findings) {
    violations.push({ file: path.relative(ROOT, file), ...finding })
  }
  for (const exemption of result.exemptions) {
    exemptions.push({ file: path.relative(ROOT, file), ...exemption })
  }
}

const json = process.argv.includes('--json')
if (json) {
  console.log(JSON.stringify({ scanned: files.length, violations, exemptions }, null, 2))
} else {
  console.log('SQL 分层门禁：Java 文件里不许写 SQL（只许住在 mapper XML）')
  console.log(`  扫描：${files.length} 个 .java（剥注释后只查字符串字面量与 SQL 注解）`)
  console.log(`  专属文件目录：${path.relative(ROOT, MAPPER_XML_DIR)}/**/*.xml`)
  if (violations.length === 0) {
    console.log(`✓ 0 处 SQL 写在 Java 里${exemptions.length > 0 ? `（有 ${exemptions.length} 处标记豁免，见下）` : ''}`)
  } else {
    console.log(`✗ ${violations.length} 处违规：`)
    for (const v of violations) {
      console.log(`  ${v.file}:${v.line}  [${v.rule}]  ${v.snippet}`)
    }
    console.log('\n  改法：把语句搬进 mapper XML（接口只留方法声明），见 SPMS-Server/docs/05-Development/CODE_STYLE.md')
  }
  if (exemptions.length > 0) {
    console.log('\n  豁免清单（只允许"非可执行 SQL"：恶意输入样例 / 被测对象的输入）：')
    for (const e of exemptions) {
      console.log(`  ${e.file}:${e.line}  ${e.reason || '（没写理由！）'}`)
    }
  }
}

if (process.argv.includes('--check') && violations.length > 0) process.exit(1)

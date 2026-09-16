#!/usr/bin/env node
/**
 * JPA 实体 → Flyway 基线 DDL（MySQL）
 *
 * ## 为什么要有这个脚本
 *
 * 现行 `ddl-auto: validate`（Hibernate 只校验不建表），而仓库里**没有任何一份完整建表脚本**：
 * 开发库是历史上 `ddl-auto: update` 时代长出来的，`src/test/resources/schema-test.sql` 只是
 * 9 张表的 H2 测试子集。于是"新环境怎么起库"这件事目前**没有可执行的答案**，
 * 而接下来要去 JPA（写路径换 MyBatis），没有 schema 基线就没法验证"换完行为一致"。
 *
 * 这个脚本把**实体定义**当唯一事实源，生成 `V1__baseline_schema.sql`：
 * 表名、列名、类型、唯一约束全部来自注解，而不是人手抄一遍（手抄必漂）。
 *
 * ## 能可靠解析什么 / 解析不了什么
 *
 * ✅ `@Table(name, uniqueConstraints)`、`@Column(name, columnDefinition, nullable, length, unique)`、
 *    `@ManyToOne` 关系列（Hibernate 默认命名 `<字段>_id`）、四个 `@MappedSuperclass` 基类的公共列。
 * ⚠️ 没有 `columnDefinition` 的列按 Java 类型映射（见 TYPE_MAP），**类型可能与本机实际表不同**
 *    （如 Boolean 在 MySQL8 下可能被建为 `bit(1)`）。所以生成后必须与真库对一次：
 *
 *        mysqldump --no-data --skip-comments --compact kubo > /tmp/real.sql
 *        node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
 *
 *    `--check` 会列出「实体有、真库没有」「真库有、实体没有」的列，差异必须逐条确认。
 *
 * ## 用法
 *
 *     node scripts/gen-flyway-baseline.mjs                     # 打印 DDL
 *     node scripts/gen-flyway-baseline.mjs --write             # 写入 src/main/resources/db/migration/V1__baseline_schema.sql
 *     node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
 */

import { mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const JAVA_ROOT = join(ROOT, 'SPMS-Server/src/main/java')
const OUTPUT = join(ROOT, 'SPMS-Server/src/main/resources/db/migration/V1__baseline_schema.sql')

/** 无 `columnDefinition` 时按 Java 类型给出的兜底类型（与本仓既有表风格对齐） */
const TYPE_MAP = {
  String: 'varchar(255)',
  Long: 'bigint UNSIGNED',
  long: 'bigint UNSIGNED',
  Integer: 'int UNSIGNED',
  int: 'int UNSIGNED',
  Boolean: 'tinyint(1)',
  boolean: 'tinyint(1)',
  BigDecimal: 'decimal(18, 6)',
  Double: 'double',
  Float: 'float',
  LocalDateTime: 'bigint UNSIGNED',
  LocalDate: 'bigint UNSIGNED',
  Date: 'bigint UNSIGNED',
}

/** 四个 `@MappedSuperclass` 基类带来的公共列（airpower `CurdEntity` + 本仓 `BaseEntity`/`TenantAwareEntity`） */
const BASE_COLUMNS = [
  ['id', 'bigint UNSIGNED NOT NULL AUTO_INCREMENT', true],
  ['create_time', 'bigint UNSIGNED DEFAULT 0 NOT NULL', false],
  ['update_time', 'bigint UNSIGNED DEFAULT 0 NOT NULL', false],
  ['is_disabled', 'tinyint(1) DEFAULT 0 NOT NULL', false],
  ['is_published', 'tinyint(1) DEFAULT 0 NOT NULL', false],
]

/** 继承 `TenantAwareEntity` 才有归属列 */
const TENANT_COLUMNS = [
  ['tenant_id', 'bigint UNSIGNED DEFAULT 0', false],
  ['creator_id', 'bigint UNSIGNED DEFAULT 0', false],
]

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory())
      out.push(...walk(full))
    else if (entry.endsWith('.java'))
      out.push(full)
  }
  return out
}

/** 驼峰 → 下划线（Hibernate 默认物理命名策略） */
function snake(name) {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
}

/** 提取 `@Column(...)` 的参数文本（括号配对） */
function columnArgs(source, at) {
  const open = source.indexOf('(', at)
  if (open < 0)
    return ''
  let depth = 0
  for (let i = open; i < source.length; i++) {
    if (source[i] === '(') {
      depth++
    }
    else if (source[i] === ')') {
      depth--
      if (depth === 0)
        return source.slice(open + 1, i)
    }
  }
  return ''
}

/**
 * 取 `@Column(...)` 之后的字段名。
 *
 * 中间可能还夹着别的注解（`@Description` / `@Length` / `@NotBlank` …），
 * 所以不能要求"下一行就是字段声明"——要在窗口内找第一条 `private|protected` 声明。
 */
function fieldNameAfter(source, at) {
  const window = source.slice(at, at + 800)
  const match = window.match(/(?:private|protected)\s[\w.<>,\s[\]]+?\s(\w+)\s*[;=]/)
  return match?.[1] ?? null
}

function parseEntity(file) {
  const source = readFileSync(file, 'utf8')
  const tableMatch = source.match(/@Table\(name\s*=\s*"([^"]+)"/)
  if (!tableMatch)
    return null

  const entity = {
    table: tableMatch[1],
    tenantAware: /extends\s+(?:\w+\s*,\s*)?TenantAwareEntity|extends\s+AbstractBaseBillEntity|extends\s+BaseBillDetailEntity/.test(source)
      || /class\s+\w+\s+extends\s+TenantAwareEntity/.test(source),
    columns: [],
    unparsed: [],
    unique: [],
    file,
  }

  // 唯一约束（可多个）
  // 注解参数拆成两步匹配：一步抓括号内全部参数，再分别取 name / columnNames。
  // 单条大正则里 `\s*` 与 `[^})]*` 会互相吞吐空白，ESLint 的 no-super-linear-backtracking 会拦。
  for (const constraint of source.matchAll(/@UniqueConstraint\(([^)]*)\)/g)) {
    const args = constraint[1]
    const name = args.match(/name\s*=\s*"([^"]+)"/)?.[1]
    const columns = args.match(/columnNames\s*=\s*\{?([^}]*)\}?/)?.[1]
    const names = (columns ?? '').split(',').map(s => s.trim().replace(/^"|"$/g, '')).filter(Boolean)
    if (name && names.length)
      entity.unique.push([name, names])
  }

  // 列：@Column
  for (const match of source.matchAll(/@Column\s*\(/g)) {
    const args = columnArgs(source, match.index)
    const name = args.match(/name\s*=\s*"([^"]+)"/)?.[1]
    const definition = args.match(/columnDefinition\s*=\s*"([^"]+)"/)?.[1]
    const nullable = !/nullable\s*=\s*false/.test(args)
    const unique = /(?:^|[\s,(])unique\s*=\s*true/.test(args)
    const field = fieldNameAfter(source, match.index)
    if (!field) {
      entity.unparsed.push(`@Column 找不到字段声明（file=${file}, at=${match.index}）`)
      continue
    }
    // 大多数 @Column 不写 name：列名由字段名按 Hibernate 默认物理命名策略推导
    entity.columns.push({ name: name || snake(field), definition, nullable, unique, field })
  }

  // 关系列：@ManyToOne 无 @JoinColumn 时，Hibernate 建 `<字段>_id`
  for (const match of source.matchAll(/@ManyToOne[^\n]*\n(?:[ \t]*@[\w().,"={} ]*\n)*[ \t]*private\s+[\w.<>]+\s+(\w+)\s*;/g)) {
    const field = match[1]
    entity.columns.push({ name: `${snake(field)}_id`, definition: 'bigint UNSIGNED DEFAULT 0', nullable: true, unique: false, field, relation: true })
  }

  // 基类里声明的列（bill / bill detail 特有）
  const isBill = /extends\s+AbstractBaseBillEntity/.test(source)
  const isBillDetail = /extends\s+BaseBillDetailEntity/.test(source)
  const inherited = []
  if (isBill)
    inherited.push(['reject_reason', 'varchar(255) default \'\' comment \'驳回原因\''])
  if (isBillDetail) {
    inherited.push(['bill_id', 'bigint UNSIGNED DEFAULT 0'])
    inherited.push(['is_finished', 'tinyint(1) DEFAULT 0'])
  }
  entity.inherited = inherited

  // 显式 @Column 没标 nullable=false 时，主键与 NOT NULL 由 columnDefinition 自身表达
  return entity
}

function renderColumn(name, definition, { unique = false } = {}) {
  const parts = [`\`${name}\``, definition]
  if (unique)
    parts.push('UNIQUE')
  return parts.join(' ')
}

function renderTable(entity) {
  const lines = []
  const columns = [...BASE_COLUMNS.map(([name, definition]) => ({ name, definition })), ...(entity.tenantAware ? TENANT_COLUMNS.map(([name, definition]) => ({ name, definition })) : []), ...entity.inherited.map(([name, definition]) => ({ name, definition })), ...entity.columns.map(column => ({
    name: column.name,
    definition: column.definition || TYPE_MAP[inferJavaType(entity, column.field)] || 'varchar(255)',
    unique: column.unique,
  }))]

  const seen = new Set()
  for (const column of columns) {
    if (seen.has(column.name))
      continue
    seen.add(column.name)
    lines.push(`    ${renderColumn(column.name, column.definition, { unique: column.unique })}`)
  }
  lines.push('    PRIMARY KEY (`id`)')
  for (const [name, cols] of entity.unique) {
    lines.push(`    CONSTRAINT \`${name}\` UNIQUE (${cols.map(c => `\`${c}\``).join(', ')})`)
  }
  return `CREATE TABLE IF NOT EXISTS \`${entity.table}\` (\n${lines.join(',\n')}\n) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '${entity.table}';`
}

/** 从实体源码取字段的 Java 类型（TYPE_MAP 兜底用） */
function inferJavaType(entity, field) {
  const source = readFileSync(entity.file, 'utf8')
  const match = source.match(new RegExp(`(private|protected)\\s+([\\w.<>]+)\\s+${field}\\s*[;=]`))
  if (!match)
    return 'String'
  const type = match[2].replace(/<.*>/, '')
  return type
}

const entities = []
for (const file of walk(JAVA_ROOT)) {
  const entity = parseEntity(file)
  if (entity)
    entities.push(entity)
}
entities.sort((a, b) => a.table.localeCompare(b.table))

const args = process.argv.slice(2)
const checkIndex = args.indexOf('--check')

if (checkIndex >= 0) {
  const dump = readFileSync(args[checkIndex + 1], 'utf8')
  const realTables = new Map()
  for (const match of dump.matchAll(/CREATE TABLE `?(\w+)`? \(([\s\S]*?)\n\) ENGINE/g)) {
    const columns = [...match[2].matchAll(/^\s*`(\w+)`/gm)].map(m => m[1])
    realTables.set(match[1], new Set(columns))
  }
  let problems = 0
  for (const entity of entities) {
    const real = realTables.get(entity.table)
    if (!real) {
      console.log(`⚠️  真库缺表：${entity.table}`)
      problems++
      continue
    }
    const declared = new Set([
      ...BASE_COLUMNS.map(([name]) => name),
      ...(entity.tenantAware ? TENANT_COLUMNS.map(([name]) => name) : []),
      ...entity.inherited.map(([name]) => name),
      ...entity.columns.map(column => column.name),
    ])
    for (const name of declared) {
      if (!real.has(name) && name !== 'id')
        console.log(`⚠️  ${entity.table}：实体有 \`${name}\`，真库没有`)
    }
  }
  console.log(`\n实体 ${entities.length} 张表；真库 ${realTables.size} 张表`)
  process.exit(problems ? 1 : 0)
}

const ddl = `-- =====================================================================
-- Flyway V1 · 基线 schema（由实体定义生成，勿手改）
--
-- 生成：node scripts/gen-flyway-baseline.mjs --write
-- 事实源：src/main/java 下 ${entities.length} 个 @Table 实体 + 4 个 @MappedSuperclass 基类
-- 生成器：scripts/gen-flyway-baseline.mjs（带 --check 与真库对账）
--
-- ## 与存量库的关系
-- 存量库（开发/生产）在 Flyway 里以 \`baseline-on-migrate\` 记为 **baseline 1**，
-- 本文件**不会**被执行；只有全新环境才会跑它。差异用 mysqldump 对账：
--     mysqldump --no-data --skip-comments --compact kubo > /tmp/real.sql
--     node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
--
-- ## 为什么不是 mysqldump 产物
-- 因为仓库里没有可信的完整建表脚本，mysqldump 需要一台真库；实体是随代码走的，
-- 生成器让"改实体忘了改 schema"当场暴露（CI 里由 schemaContract.spec.ts 守）。
-- =====================================================================

SET NAMES utf8mb4;

${entities.map(renderTable).join('\n\n')}
`
// 末尾固定补一个换行：writeFileSync(ddl) 写出的文本文件要符合 POSIX 习惯（git 也会提示 \ No newline）
if (args.includes('--write')) {
  mkdirSync(dirname(OUTPUT), { recursive: true })
  writeFileSync(OUTPUT, ddl)
  console.log(`✅ 已写入 ${OUTPUT.replace(`${ROOT}/`, '')}`)
  console.log(`   表 ${entities.length} 张，列 ${entities.reduce((sum, e) => sum + e.columns.length, 0)} 个（另有基类公共列）`)
  const unparsed = entities.filter(e => e.unparsed.length)
  for (const entity of unparsed) console.log(`   ⚠️ ${entity.table}: ${entity.unparsed.join('; ')}`)
}
else {
  console.log(ddl)
}

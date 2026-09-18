import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # schema 契约：实体 ↔ `infra/init-sql/init.sql`
 *
 * ## 为什么需要这条门禁
 *
 * 项目跑的是 `ddl-auto: validate`（只校验、不建表），结构脚本是整个环境的唯一建库路径：
 * 新同事/新环境把 `infra/init-sql/init.sql` + `init_data.sql` 一跑就该有个能用的库。
 * 因此两边必须严丝合缝：
 *
 * - 改实体忘了改脚本 → 启动期 `Schema-validation: missing column`（信息难懂）；
 * - 改脚本忘了改实体 → 库里多出没人映射的列（同样是漂移）。
 *
 * ## 覆盖范围
 * - 每个 `@TableName(...)` 实体都有对应建表语句；
 * - 每个实体声明的列都出现在脚本里（J3 起列名按 MyBatis-Plus 口径推导：
 *   `@TableField("x")` 优先，否则字段名驼峰转下划线；关系对象字段不是列，
 *   它由标量外键字段 `<字段>Id` 承载 `<字段>_id`）；
 * - 标了 `@Unique` 的列必须在脚本里有唯一约束（应用层查重与库面唯一键成对）；
 * - 基类的公共列（id / create_time / update_time / is_disabled / is_published，
 *   归属实体再加 tenant_id / creator_id）每张表都有；
 * - `infra/init-sql/` 下**恰好两个** `.sql`（init.sql 结构 + init_data.sql 数据），
 *   且数据脚本里每条 INSERT 都自带幂等守卫。
 *
 * 历史：这两条判据以前钉的是 Flyway 的 `V1__baseline_schema.sql`（版本号连续、V1..V10 顺序），
 * 2026-09-18 用户定下"全仓只保留两个 SQL 文件、以后都改这两个"之后，Flyway 与其版本化命名一并退场。
 */

const ROOT = process.cwd()
const JAVA_ROOT = join(ROOT, 'SPMS-Server/src/main/java')
const INIT_SQL_DIR = join(ROOT, 'infra/init-sql')
const BASELINE = join(INIT_SQL_DIR, 'init.sql')
const DATA_SQL = join(INIT_SQL_DIR, 'init_data.sql')

function walk(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory())
      out.push(...walk(full))
    else if (entry.endsWith('.java'))
      out.push(full)
  }
  return out
}

function snake(name: string): string {
  return name.replace(/([a-z0-9])([A-Z])/g, '$1_$2').toLowerCase()
}

interface EntityShape {
  table: string
  file: string
  columns: Set<string>
  uniqueColumns: Set<string>
}

function parseEntities(): EntityShape[] {
  const out: EntityShape[] = []
  for (const file of walk(JAVA_ROOT)) {
    const source = readFileSync(file, 'utf8')
    const table = source.match(/@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"/)?.[1]
    if (!table)
      continue

    const columns = new Set<string>(['id', 'create_time', 'update_time', 'is_disabled', 'is_published'])
    const uniqueColumns = new Set<string>()
    const tenantAware = /extends\s+(?:\w+\s*,\s*)?(?:TenantAwareEntity|AbstractBaseBillEntity|BaseBillDetailEntity)/.test(source)
    if (tenantAware) {
      columns.add('tenant_id')
      columns.add('creator_id')
    }
    if (/extends\s+AbstractBaseBillEntity/.test(source))
      columns.add('reject_reason')
    if (/extends\s+BaseBillDetailEntity/.test(source)) {
      columns.add('bill_id')
      columns.add('is_finished')
    }

    // 字段 → 列（与后端 EntityMetadata 同口径：显式 @TableField 优先，否则驼峰转下划线）
    const lines = source.split('\n')
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i].trim()
      // 线性扫描而不是一个带惰性量词的大正则：`\s+` 与 `[...\s]+?` 会互相交换字符，
      // eslint 的 regexp/no-super-linear-backtracking 会判红（实测踩到）
      // 不用"前缀 + 惰性量词"的正则：`\s+`/`.+?`/`\s*` 互相可交换，eslint 的
      // regexp/no-super-linear-backtracking 会判红。这里全部走字符串位置，复杂度是线性的。
      if (!/^(?:private|protected)\s/.test(line))
        continue
      const rest = line.slice(line.indexOf(' ') + 1).trim()
      const end = rest.search(/[;=]/)
      if (end < 0)
        continue
      const declaration = rest.slice(0, end).trim()
      const splitAt = declaration.lastIndexOf(' ')
      if (splitAt < 0)
        continue
      const name = declaration.slice(splitAt + 1)
      const type = declaration.slice(0, splitAt).trim()
      const annotations: string[] = []
      for (let j = i - 1; j >= 0 && j > i - 14; j--) {
        const above = lines[j].trim()
        if (above === '' || above.startsWith('*') || above.startsWith('/*'))
          continue
        if (above.startsWith('@')) {
          annotations.unshift(above)
          continue
        }
        break
      }
      const block = annotations.join(' ')
      if (/@TableField\s*\(\s*exist\s*=\s*false/.test(block))
        continue
      if (/^(?:Set|List|Collection|Map)</.test(type) || type.endsWith(']') || type.endsWith('Entity'))
        continue
      const column = /@TableField\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/.exec(block)?.[1] ?? snake(name)
      columns.add(column)
      if (block.includes('@Unique'))
        uniqueColumns.add(column)
    }

    out.push({ table, file, columns, uniqueColumns })
  }
  return out.sort((a, b) => a.table.localeCompare(b.table))
}

/** 解析 init.sql 的唯一约束：表名 → 出现在唯一键里的列（行内 UNIQUE 与 UNIQUE KEY(...) 都认） */
function parseUniqueColumns(): Map<string, Set<string>> {
  const sql = readFileSync(BASELINE, 'utf8')
  const keys = new Map<string, Set<string>>()
  for (const match of sql.matchAll(/CREATE TABLE IF NOT EXISTS `(\w+)` \(([\s\S]*?)\n\) ENGINE/g)) {
    const columns = new Set<string>()
    for (const line of match[2].split('\n')) {
      const inline = /^\s*`(\w+)`\s[^,]*\bUNIQUE\b/.exec(line)
      if (inline) {
        columns.add(inline[1])
      }
      for (const key of line.matchAll(/UNIQUE\s+KEY\s+`\w+`\s*\(([^)]*)\)/gi)) {
        for (const column of key[1].split(','))
          columns.add(column.trim().replace(/`/g, ''))
      }
    }
    keys.set(match[1], columns)
  }
  return keys
}
/** 解析基线 DDL：表名 → 列名集合 */
function parseBaseline(): Map<string, Set<string>> {
  const sql = readFileSync(BASELINE, 'utf8')
  const tables = new Map<string, Set<string>>()
  for (const match of sql.matchAll(/CREATE TABLE IF NOT EXISTS `(\w+)` \(([\s\S]*?)\n\) ENGINE/g)) {
    const columns = new Set(
      [...match[2].matchAll(/^\s*`(\w+)`/gm)].map(m => m[1]),
    )
    tables.set(match[1], columns)
  }
  return tables
}

const entities = parseEntities()
const baseline = parseBaseline()

describe('schema 契约 · 实体 ↔ init.sql', () => {
  it('结构脚本存在且有表（否则所有断言都会"空过"）', () => {
    expect(entities.length, '没解析出任何 @TableName 实体，解析器失效').toBeGreaterThan(30)
    expect(baseline.size, 'init.sql 里没有建表语句').toBeGreaterThan(30)
  })

  it('每个实体表都在基线里', () => {
    const missing = entities.filter(entity => !baseline.has(entity.table)).map(entity => entity.table)
    expect(missing, '这些实体没有建表语句：新环境起库会缺表（ddl-auto: validate 才炸，且信息难懂）').toEqual([])
  })

  it('每个实体列都在基线里（含关系字段的桥接标量外键列）', () => {
    const problems: string[] = []
    for (const entity of entities) {
      const table = baseline.get(entity.table)
      if (!table)
        continue
      const missing = [...entity.columns].filter(column => !table.has(column))
      if (missing.length)
        problems.push(`${entity.table}: ${missing.join(', ')}`)
    }
    expect(problems, '实体有、基线没有的列（生成器漏解析，或实体新增列未重新生成基线）').toEqual([])
  })

  it('基线列不超出实体声明（防止基线里留下已删字段）', () => {
    const problems: string[] = []
    for (const entity of entities) {
      const table = baseline.get(entity.table)
      if (!table)
        continue
      const extra = [...table].filter(column => !entity.columns.has(column))
      if (extra.length)
        problems.push(`${entity.table}: ${extra.join(', ')}`)
    }
    expect(problems, '基线有、实体没有的列（实体删了字段但没重新生成基线）').toEqual([])
  })

  it('每个实体表都有基类公共列（关联表除外：它们没有实体，也没有审计列）', () => {
    const problems: string[] = []
    const entityTables = new Set(entities.map(entity => entity.table))
    for (const [table, columns] of baseline) {
      if (!entityTables.has(table))
        continue
      for (const base of ['id', 'create_time', 'update_time', 'is_disabled', 'is_published']) {
        if (!columns.has(base))
          problems.push(`${table} 缺 ${base}`)
      }
    }
    expect(problems).toEqual([])
  })

  it('标了 @Unique 的列必须在 init.sql 里有唯一约束（应用层查重与库面唯一键成对）', () => {
    const uniqueKeys = parseUniqueColumns()
    const problems: string[] = []
    let declared = 0
    for (const entity of entities) {
      const keys = uniqueKeys.get(entity.table)
      for (const column of entity.uniqueColumns) {
        declared += 1
        if (!keys?.has(column))
          problems.push(`${entity.table}.${column}`)
      }
    }
    // 空转守卫：一个 @Unique 都没解析到 ⇒ 判据坏了，而不是"全过"
    expect(declared, '没解析出任何 @Unique 字段：J3 起唯一列由这个注解声明，解析失效等于这条门禁不存在')
      .toBeGreaterThan(10)
    expect(problems, '这些列标了 @Unique（应用层查重），但 init.sql 里没有唯一约束 —— '
    + '并发下同一值会写进两行，应用层查重挡不住；给 init.sql 补 UNIQUE KEY 或去掉注解')
      .toEqual([])
  })

  it('目录里恰好两个 .sql：init.sql（结构）+ init_data.sql（数据）', () => {
    const files = readdirSync(INIT_SQL_DIR).filter(file => file.endsWith('.sql')).sort()
    expect(files, '全仓只有两个数据库脚本 —— 新增迁移文件会在这里红，新表/新列改 init.sql，新数据改 init_data.sql')
      .toEqual(['init.sql', 'init_data.sql'])
  })

  it('数据脚本里每条 INSERT 都自带幂等守卫（可重复执行）', () => {
    const sql = readFileSync(DATA_SQL, 'utf8')
    const inserts = [...sql.matchAll(/^\s*INSERT\s+(?:IGNORE\s+)?INTO\b[\s\S]*?;/gim)]
    expect(inserts.length, 'init_data.sql 里没有 INSERT —— 解析失效或数据被搬走了').toBeGreaterThan(0)
    const unguarded = inserts
      .filter(m => !/NOT\s+EXISTS|INSERT\s+IGNORE|ON\s+DUPLICATE\s+KEY\s+UPDATE/i.test(m[0]))
      .map(m => m[0].split('\n')[0].slice(0, 60))
    expect(unguarded, '这些 INSERT 重复执行会报重复键：加 WHERE NOT EXISTS / INSERT IGNORE').toEqual([])
  })

  it('结构脚本里不许出现建库脚本的危险写法', () => {
    const sql = readFileSync(BASELINE, 'utf8')
    const banned = [/^\s*DROP\b/im, /^\s*TRUNCATE\b/im, /^\s*DELETE\b/im, /^\s*DELIMITER\b/im]
    const hit = banned.filter(re => re.test(sql)).map(re => re.source)
    expect(hit, 'init.sql 只建对象，不删对象/数据，也不用 Flyway 时代的过程式写法').toEqual([])
  })
})

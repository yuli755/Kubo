import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # schema 契约：JPA 实体 ↔ Flyway 基线 DDL
 *
 * ## 为什么需要这条门禁
 *
 * 项目跑的是 `ddl-auto: validate`（Hibernate 只校验、不建表），而**建表脚本一直不存在**——
 * 开发库是历史 `ddl-auto: update` 时代长出来的。这意味着：
 *
 * - 新同事/新环境**起不了库**（没有可执行的建表路径）；
 * - 接下来要把写路径从 JPA 换到 MyBatis，没有 schema 基线就无从验证"换完表结构一致"；
 * - 改实体忘了改 schema 这类错误，在 `validate` 下要到**启动时**才炸，而且报的是
 *   "missing column" 这种不含前因后果的信息。
 *
 * 所以：实体定义 = 事实源，`V1__baseline_schema.sql` 由 `scripts/gen-flyway-baseline.mjs`
 * 生成，本用例把两边钉在一起。任何一边单独改动，CI 立刻红。
 *
 * ## 覆盖范围
 * - 每个 `@Table(name=...)` 实体都有对应建表语句；
 * - 每个实体声明的列都出现在基线里（含 `@ManyToOne` 推导出的 `<字段>_id`）；
 * - 基类的公共列（id / create_time / update_time / is_disabled / is_published，
 *   归属实体再加 tenant_id / creator_id）每张表都有；
 * - 迁移文件版本号连续、命名符合 Flyway 约定（`V<n>__<描述>.sql`）。
 */

const ROOT = process.cwd()
const JAVA_ROOT = join(ROOT, 'SPMS-Server/src/main/java')
const MIGRATION_DIR = join(ROOT, 'SPMS-Server/src/main/resources/db/migration')
const BASELINE = join(MIGRATION_DIR, 'V1__baseline_schema.sql')

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
  notNullColumns: Set<string>
}

function parseEntities(): EntityShape[] {
  const entities: EntityShape[] = []
  for (const file of walk(JAVA_ROOT)) {
    const source = readFileSync(file, 'utf8')
    const table = source.match(/@Table\(name\s*=\s*"([^"]+)"/)?.[1]
    if (!table)
      continue

    const columns = new Set<string>(['id', 'create_time', 'update_time', 'is_disabled', 'is_published'])
    const notNull = new Set<string>(['id'])
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

    // @Column(...) 后的字段：列名取 name 属性，缺省用字段名 snake_case
    for (const match of source.matchAll(/@Column\s*\(/g)) {
      const open = source.indexOf('(', match.index)
      let depth = 0
      let args = ''
      for (let i = open; i < source.length; i++) {
        if (source[i] === '(') {
          depth++
        }
        else if (source[i] === ')') {
          depth--
          if (depth === 0) {
            args = source.slice(open + 1, i)
            break
          }
        }
      }
      const field = source.slice(match.index, match.index + 800)
        .match(/(?:private|protected)\s[\w.<>,\s[\]]+?\s(\w+)\s*[;=]/)?.[1]
      if (!field)
        continue
      const name = args.match(/name\s*=\s*"([^"]+)"/)?.[1] ?? snake(field)
      columns.add(name)
      if (/nullable\s*=\s*false/.test(args))
        notNull.add(name)
    }

    // @ManyToOne 关系列（Hibernate 默认命名 `<字段>_id`）
    for (const match of source.matchAll(/@ManyToOne[^\n]*\n(?:[ \t]*@[\w().,"={} ]*\n)*[ \t]*private\s+[\w.<>]+\s+(\w+)\s*;/g)) {
      columns.add(`${snake(match[1])}_id`)
    }

    entities.push({ table, file, columns, notNullColumns: notNull })
  }
  return entities.sort((a, b) => a.table.localeCompare(b.table))
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

describe('schema 契约 · 实体 ↔ Flyway 基线', () => {
  it('基线文件存在且有表（否则所有断言都会"空过"）', () => {
    expect(entities.length, '没解析出任何 @Table 实体，解析器失效').toBeGreaterThan(30)
    expect(baseline.size, '基线里没有建表语句').toBeGreaterThan(30)
  })

  it('每个实体表都在基线里', () => {
    const missing = entities.filter(entity => !baseline.has(entity.table)).map(entity => entity.table)
    expect(missing, '这些实体没有建表语句：新环境起库会缺表（ddl-auto: validate 才炸，且信息难懂）').toEqual([])
  })

  it('每个实体列都在基线里（含 @ManyToOne 推导的关系列）', () => {
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

  it('基线里每张表都有基类公共列', () => {
    const problems: string[] = []
    for (const [table, columns] of baseline) {
      for (const base of ['id', 'create_time', 'update_time', 'is_disabled', 'is_published']) {
        if (!columns.has(base))
          problems.push(`${table} 缺 ${base}`)
      }
    }
    expect(problems).toEqual([])
  })

  it('迁移文件命名与版本号符合 Flyway 约定且连续', () => {
    const files = readdirSync(MIGRATION_DIR).filter(file => file.endsWith('.sql'))
    const versions = files
      .map(file => Number(file.match(/^V(\d+)__/)?.[1]))
      .filter(version => !Number.isNaN(version))
      .sort((a, b) => a - b)
    expect(versions.length, '有迁移文件不符合 V<n>__<描述>.sql 命名').toBe(files.length)
    expect(versions, 'Flyway 版本号必须从 1 连续递增（跳号会被 out-of-order 策略拒绝）').toEqual(
      Array.from({ length: versions.length }, (_, index) => index + 1),
    )
  })

  it('存量脚本按原日期顺序收编（V1 基线 + V2..V13，不能颠倒）', () => {
    // 按**版本号**排序（不能按文件名字典序：V10 < V2）
    const ordered = readdirSync(MIGRATION_DIR)
      .filter(file => file.endsWith('.sql'))
      .map(file => file)
      .sort((a, b) => Number(a.match(/^V(\d+)/)![1]) - Number(b.match(/^V(\d+)/)![1]))
    expect(ordered[0]).toBe('V1__baseline_schema.sql')
    expect(ordered.at(-1)).toBe('V13__retired_resource_policy_convergence.sql')
    // 历史脚本必须保持原日期序：V2..V10 的描述里带日期或语义顺序。
    // V11/V12 是 2026-09-28 真机联调后新增的修复脚本，V13 是其后的退场资源收敛，
    // 一律追加在尾部，不得插入历史序列中间
    // （Flyway 已应用的历史版本号一旦重排，存量库会报 checksum mismatch 或跳过迁移）。
    expect(ordered.slice(1)).toEqual([
      'V2__data_permission_migrations.sql',
      'V3__operation_log.sql',
      'V4__role_tenant_backfill.sql',
      'V5__file_creator_id.sql',
      'V6__menu_permission.sql',
      'V7__system_permission_identities.sql',
      'V8__user_email_phone_unique_null_default.sql',
      'V9__inventory_dimension_unique.sql',
      'V10__menu_canonical_paths.sql',
      'V11__is_published_column_default.sql',
      'V12__retired_module_catalog_convergence.sql',
      'V13__retired_resource_policy_convergence.sql',
    ])
  })
})

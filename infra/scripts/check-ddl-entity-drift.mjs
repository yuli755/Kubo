#!/usr/bin/env node
/**
 * 生产迁移 DDL ↔ JPA 实体列 漂移门禁
 * ============================================================================
 * 为什么要有这个脚本：`data_*` 这批表的真实结构只存在于 `SPMS-Server/docs/database/migration/*.sql`
 * （MySQL 8 方言：存储过程 + information_schema 判断 + DELIMITER），而 H2 那边是一份**手抄**的
 * `schema-test.sql`。后端 150+ 用例跑在 H2 上，所以"实体加了列、迁移脚本没加"这类漂移
 * 在 CI 里**完全不会红**（H2 用的是手抄那份），到了生产就变成启动期 `Schema-validation: missing column`。
 * 反过来，"迁移脚本加了列、实体没有"也没人拦。这个脚本把两边对齐成一件事：读文件、比对、非零退出。
 *
 * 判定方向（都是"读文件"，不连库，所以本地与 CI 结果一致）：
 *   ① 实体声明了、迁移 DDL 里没有  → **失败**（生产会缺列）
 *   ② 迁移 DDL 里有、实体没声明     → **失败**（列写了却没人用，或实体漏映射；框架基类列除外）
 *   ③ H2 测试 schema 与迁移 DDL 不一致 → **只告警**（红线：`schema-test.sql` 不是生产 schema；
 *      把它做成硬门禁会诱使人"改测试 schema 让门禁变绿"，那正是我们反复在修的反模式）
 *
 * 框架基类列（`CurdEntity` 来自 `airpower-curd` jar，源码不在本仓，扫不到）走显式白名单：
 * FRAMEWORK_COLUMNS。白名单本身也做校验 —— 出现任何表里都不存在的列名即失败，防止它变成"藏问题的抽屉"。
 *
 * @author Arena agent
 */
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { writeFileSync, mkdirSync } from 'node:fs'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const argv = process.argv.slice(2)
const opt = (name, dft) => {
  const i = argv.indexOf(`--${name}`)
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith('--') ? argv[i + 1] : dft
}

const ENTITY_DIR = opt('entities', 'SPMS-Server/src/main/java/cn/hamm/spms/module/system/datapermission/entity')
const APP_BASE = opt('app-base', 'SPMS-Server/src/main/java/cn/hamm/spms/base/BaseEntity.java')
const DDL_DIR = opt('ddl', 'SPMS-Server/docs/database/migration')
const TEST_SCHEMA = opt('test-schema', 'SPMS-Server/src/test/resources/schema-test.sql')
const JSON_OUT = opt('json', '')
const LIVE = opt('live-columns', '')

/** 来自 `airpower-curd` 的 `CurdEntity`/`Entity`：本仓扫不到，但 DDL 里逐表重复出现 */
const FRAMEWORK_COLUMNS = new Set(['id', 'create_time', 'update_time', 'is_disabled'])

const walk = (dir, out = []) => {
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, e.name)
    if (e.isDirectory()) {
      walk(p, out)
    } else if (e.name.endsWith('.java')) {
      out.push(p)
    }
  }
  return out
}

/**
 * 解析 Java 实体：表名 + `@Column(name = "...")` 声明的列。
 * 逐行扫（不引 AST）：`@JoinColumn`、`@UniqueConstraint(columnNames = {...})` 也含 name/columnNames，
 * 必须排除 —— 否则会把关联列当成新列，或把唯一约束里的列名重复计入。
 */
function parseEntity(file) {
  const src = readFileSync(file, 'utf8')
  const table = /@Table\s*\(\s*name\s*=\s*"([^"]+)"/.exec(src)?.[1]
  if (!table) {
    return null
  }
  const columns = new Set()
  const skip = new Set()
  src.split('\n').forEach((line) => {
    const t = line.trim()
    // 只在"这一行就是 @Column 声明"时取 name，避免 @JoinColumn(name=...) 混进来
    if (t.startsWith('@Column') || t.startsWith('@Column(')) {
      const n = /name\s*=\s*"([^"]+)"/.exec(t)?.[1]
      if (n) {
        columns.add(n)
      }
    } else if (t.startsWith('@JoinColumn') || t.startsWith('@JoinTable') || t.startsWith('@UniqueConstraint')) {
      // 关联列由 @JoinColumn 定义：计入，但只是"允许存在"，不是新增要求 —— 记下来避免方向②误报
      for (const m of t.matchAll(/name\s*=\s*"([^"]+)"/g)) {
        skip.add(m[1])
      }
    }
  })
  return { table, columns, joinColumns: skip, file: relative(REPO, file) }
}

/**
 * 解析 MySQL 迁移脚本：`CREATE TABLE IF NOT EXISTS \`t\` (...)` 的列 +
 * `CALL <proc>('t', 'col', ...)` 追加的列（这套脚本用存储过程做幂等，直接扫 ALTER 是扫不到的）。
 */
function parseDdl(files) {
  const tables = new Map()
  const touch = (t, col) => {
    if (!tables.has(t)) {
      tables.set(t, new Set())
    }
    if (col) {
      tables.get(t).add(col)
    }
  }
  for (const f of files) {
    const src = readFileSync(f, 'utf8')
    const lines = src.split('\n')
    let current = null
    for (const raw of lines) {
      const line = raw.trim()
      if (line.startsWith('--') || !line) {
        continue
      }
      const ct = /^CREATE TABLE (?:IF NOT EXISTS )?`([a-zA-Z0-9_]+)`\s*\(/i.exec(line)
      if (ct) {
        current = ct[1]
        touch(current, null)
        continue
      }
      if (current) {
        if (/^\)\s*ENGINE/i.test(line)) {
          current = null
          continue
        }
        // 只认 `col` 开头的列定义；主键/索引/约束/生成列的整型名不是列
        const col = /^`([a-zA-Z0-9_]+)`\s+[A-Za-z]/.exec(line)
        if (col) {
          touch(current, col[1])
        }
        continue
      }
      // 幂等加列：CALL kubo_*_add_column('table', 'column', '`column` TYPE ...')
      const call = /^CALL\s+`?([a-zA-Z0-9_]+)`?\s*\(\s*'([a-zA-Z0-9_]+)'\s*,\s*'([a-zA-Z0-9_]+)'/i.exec(line)
      if (call && /add_column/i.test(call[1])) {
        touch(call[2], call[3])
      }
    }
  }
  return tables
}

/** H2 测试 schema：`CREATE TABLE IF NOT EXISTS x (...)`，列名可能不带反引号 */
function parseTestSchema(file) {
  if (!existsSync(file)) {
    return null
  }
  const tables = new Map()
  let current = null
  for (const raw of readFileSync(file, 'utf8').split('\n')) {
    const line = raw.trim()
    if (!line || line.startsWith('--')) {
      continue
    }
    const ct = /^CREATE TABLE (?:IF NOT EXISTS )?[`"]?([a-zA-Z0-9_]+)[`"]?\s*\(/i.exec(line)
    if (ct) {
      current = ct[1]
      if (!tables.has(current)) {
        tables.set(current, new Set())
      }
      continue
    }
    if (current) {
      if (/^\)/.test(line)) {
        current = null
        continue
      }
      const col = /^[`"]?([a-zA-Z0-9_]+)[`"]?\s+[A-Za-z]/.exec(line)
      if (col && !/^(PRIMARY|UNIQUE|KEY|CONSTRAINT|INDEX|FOREIGN)$/.test(col[1].toUpperCase())) {
        tables.get(current).add(col[1])
      }
    }
  }
  return tables
}

const entityDir = resolve(REPO, ENTITY_DIR)
if (!existsSync(entityDir)) {
  console.error(`✗ 实体目录不存在：${ENTITY_DIR}`)
  process.exit(1)
}
const entities = walk(entityDir).map(parseEntity).filter(Boolean)
if (!entities.length) {
  console.error(`✗ 在 ${ENTITY_DIR} 下没解析到任何带 @Table 的实体 —— 解析逻辑失效，别让门禁空转`)
  process.exit(1)
}
// 应用基类列（本仓可扫）：所有实体都继承它，因此方向①要把它并进去，方向②要把它从"额外列"里摘掉
const appBase = parseEntity(resolve(REPO, APP_BASE))
const baseColumns = new Set([...(appBase ? appBase.columns : []), 'is_published'])

const ddlFiles = readdirSync(resolve(REPO, DDL_DIR))
  .filter((f) => f.endsWith('.sql'))
  .sort()
  .map((f) => resolve(REPO, DDL_DIR, f))
if (!ddlFiles.length) {
  console.error(`✗ ${DDL_DIR} 下没有 .sql —— 门禁空转`)
  process.exit(1)
}
const ddl = parseDdl(ddlFiles)
if (ddl.size === 0) {
  console.error('✗ 迁移脚本里没解析到任何 CREATE TABLE —— 解析逻辑失效，别让门禁空转')
  process.exit(1)
}
// 白名单自检：里面的列名若在所有表里都不存在，说明白名单本身过期了
for (const c of FRAMEWORK_COLUMNS) {
  const hit = [...ddl.values()].some((cols) => cols.has(c))
  if (!hit) {
    console.error(`✗ FRAMEWORK_COLUMNS 里的 "${c}" 在任何迁移表里都不存在：白名单已过期，请核对 airpower 基类`)
    process.exit(1)
  }
}

const errors = []
const warnings = []
const rows = []
const entityTables = new Set(entities.map((e) => e.table))

// --live-columns：把"脚本读起来对"升级为"库里真的建出来了"（文件里 <file>:<line> 形如 data_resource.id，
// 由 CI 的 MySQL 步骤从 information_schema 导出）。没有这个参数时该段整体跳过（本地离线跑只用文件比对）。
let liveTables = null
if (LIVE) {
  const file = resolve(REPO, LIVE)
  if (!existsSync(file)) {
    console.error(`✗ --live-columns 指向的文件不存在：${LIVE}`)
    process.exit(1)
  }
  const live = new Set(readFileSync(file, 'utf8').split('\n').map((l) => l.trim()).filter((l) => l && l !== 'NULL'))
  if (live.size === 0) {
    // 空文件必须失败：否则"库里啥都没建出来"会显示成绿色
    console.error(`✗ ${LIVE} 是空的 —— information_schema 查询没返回列，别把空转读成通过`)
    process.exit(1)
  }
  liveTables = new Set([...live].map((k) => k.split('.')[0]))
  let skipped = 0
  for (const [table, cols] of ddl) {
    if (!liveTables.has(table)) {
      skipped += 1
      if (entityTables.has(table)) {
        errors.push(`${table}: 迁移脚本里有 CREATE TABLE，但 MySQL 里不存在这张表（脚本没真的执行成功）`)
      }
      continue
    }
    for (const c of cols) {
      if (!live.has(`${table}.${c}`)) {
        errors.push(`${table}.${c}: 迁移脚本里写了，但 MySQL 库里没有（幂等过程被跳过 / DELIMITER 没生效 / 方言被拒）`)
      }
    }
  }
  for (const key of live) {
    const [t, c] = key.split('.')
    if (ddl.has(t) && liveTables.has(t) && !ddl.get(t).has(c)) {
      errors.push(`${key}: 存在于 MySQL 但不在迁移脚本里（有人直接改库，脚本已与真实结构脱节）`)
    }
  }
  console.log(`  （库内比对：${liveTables.size} 张表有列信息；${skipped} 张迁移里的表本次未建，属预期 —— 它们的前置业务表不存在）`)
}
const byTable = new Map()
for (const e of entities) {
  byTable.set(e.table, [...(byTable.get(e.table) || []), e])
}
for (const [table, list] of byTable) {
  const want = new Set(list.flatMap((e) => [...e.columns]))
  const joined = new Set(list.flatMap((e) => [...e.joinColumns]))
  baseColumns.forEach((c) => want.add(c))
  const have = ddl.get(table)
  if (!have) {
    errors.push(`表 ${table}（${list.map((e) => e.file.split('/').pop()).join(', ')}）在迁移脚本里没有 CREATE TABLE —— 新表必须进 docs/database/migration`)
    continue
  }
  const missing = [...want].filter((c) => !have.has(c))
  // 框架基类列（CurdEntity）与关联列（@JoinColumn）在 DDL 里必然存在、但不在本实体的 @Column 里，
  // 不是漂移；把它们从"额外列"里摘掉，剩下的才是"DDL 写了却没人映射"
  const extra = [...have].filter((c) => !want.has(c) && !joined.has(c) && !FRAMEWORK_COLUMNS.has(c))
  rows.push({ table, ddlColumns: have.size, entityColumns: want.size, missing, extra })
  if (missing.length) {
    errors.push(`${table}: 实体要求但迁移 DDL 缺列 → ${missing.join(', ')}`)
  }
  if (extra.length) {
    errors.push(`${table}: 迁移 DDL 有列但实体未映射 → ${extra.join(', ')}`)
  }
}

const testSchema = parseTestSchema(resolve(REPO, TEST_SCHEMA))
if (testSchema) {
  for (const [table, cols] of ddl) {
    if (!testSchema.has(table)) {
      continue
    }
    const t = testSchema.get(table)
    const onlyProd = [...cols].filter((c) => !t.has(c))
    if (onlyProd.length) {
      warnings.push(`H2 测试 schema 的 ${table} 缺生产列（用例覆盖不到）：${onlyProd.join(', ')}`)
    }
  }
}

for (const r of rows) {
  console.log(`  ${r.table.padEnd(28)} DDL ${String(r.ddlColumns).padStart(2)} 列 / 实体 ${String(r.entityColumns).padStart(2)} 列`
    + `${r.missing.length ? `  缺:${r.missing.join(',')}` : ''}${r.extra.length ? `  多:${r.extra.join(',')}` : ''}`)
}
if (JSON_OUT) {
  mkdirSync(resolve(JSON_OUT, '..'), { recursive: true })
  writeFileSync(resolve(REPO, JSON_OUT), JSON.stringify({ generatedAt: new Date().toISOString(), rows, errors, warnings }, null, 2))
}
if (warnings.length) {
  console.warn(`\n⚠ DDL↔测试 schema 有 ${warnings.length} 处差异（不判失败，见脚本头注释）：`)
  warnings.forEach((w) => console.warn(`  · ${w}`))
}
if (errors.length) {
  console.error(`\n✗ DDL↔实体漂移 ${errors.length} 处（${ddlFiles.length} 个迁移脚本 / ${entities.length} 个实体）：`)
  errors.forEach((e) => console.error(`  · ${e}`))
  process.exit(1)
}
console.log(`✓ DDL↔实体一致：${rows.length} 张表、${entities.length} 个实体、${ddlFiles.length} 个迁移脚本`)

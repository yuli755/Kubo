#!/usr/bin/env node
/**
 * # JPA 足迹盘点 + 棘轮门禁（没有 JDK/Maven 也能跑的那一半验证）
 *
 * ## 为什么需要它
 * 「去 JPA」和「去 airpower」是同一类工程：**删除面很大、单批收益不可见**。
 * airpower 退场时靠 `pnpm airpower:backend` 把"还剩多少"变成数字，才有了"只许降"的棘轮感；
 * JPA 这边更麻烦 —— 它是**运行时的核心**（写链路），不能像工具类那样整块搬走，
 * 只能按域/按层一批批换，所以更需要一个"每批都能看到数字下降"的尺子。
 *
 * 这个脚本扫 Java / pom / yml，回答三个问题：
 *   1. 现在还有多少 JPA？（文件数 / 引用次数 / import 数 / 涉及哪些注解与 API）
 *   2. 分布在哪些模块、哪些层（实体 / 仓储 / 服务 / 配置）？
 *   3. 还剩哪些"只有 JPA 才需要"的东西（`spring-boot-starter-data-jpa`、`ddl-auto`、
 *      `Specification`、`EntityManager`、`@Query`）—— 这些是删除清单的尾部。
 *
 * ## 棘轮（`--check`）
 * 与 `BASELINE` 比对：**只许降**。做完一批就把基线改成新数字（改动可见、可 review）。
 * 数字变大 = 有人又往 JPA 里写了东西，CI 直接红。
 *
 * ## 口径
 * 剥掉注释与 JavaDoc 后再数（沿用 `infra/scripts/lib/java-source.mjs`，
 * 与 `check-airpower-backend.mjs` / `check-java-imports.mjs` 同一实现）——
 * 否则"这里原来是 JPA，已换成 MyBatis-Plus"这类**迁移档案注释**会把数字顶上去。
 *
 * ## 用法
 * ```bash
 * pnpm jpa:check              # 人读盘点
 * pnpm jpa:check -- --json    # 给脚本/CI 读
 * pnpm jpa:check -- --check   # 棘轮：超过基线即 exit 1
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')

/**
 * 基线（**只许降**）。每完成一批 JPA 换轨，把这里改成实测值。
 *
 * 演进：
 * - 2026-09-18 起点（airpower 退场完成、去 JPA 开工前）：**110 文件 / 599 引用 / 478 import**。
 *   这一版数字里的大头很能说明"为什么不能一刀切"：`@Column` 222（41 个实体的列映射）、
 *   `CriteriaBuilder` 70 + `Specification` 54 + `criteria.Predicate` 42（数据权限的行级条件编译
 *   目前是**用 JPA Criteria 手搓**的，与 MyBatis-Plus 那条 SQL 渲染链路是一套逻辑两份实现）、
 *   `@Entity` 42、`@ManyToOne` 29（对象图关系，换 MP 要改成显式 join）。
 * - 2026-09-18 SQL 分层（把 main 里最后的 Java SQL 搬进 mapper XML）：`InventoryRepository` 的两条
 *   原子 UPDATE 与 `DataFieldPolicyRepository` 的 JPQL join 搬走后，**110 → 108 文件 / 599 → 594 引用 /
 *   478 → 473 import**。这一批的教训值得记：`@Select`/`@Query`/`last("LIMIT 50")` 都能让 JPA 数字"看着降了"
 *   却只是把 SQL 藏得更深 —— 所以 SQL 分层单独立了一条门禁（`pnpm sql:check`），不靠这条棘轮兼职。
 * - 2026-09-18 J1（数据权限证据/审计日志族 → MyBatis-Plus）：4 个仓储 + 4 个实体 + 调用点。
 *   这一批**没有让上面的三行大数字下降**，因为换的是最容易换的那一小块；
 *   真正的下降会来自"实体注解批量替换"与"写链路引擎换轨"两批（见 `docs/去JPA决策评估.md` 的执行记录）。
 */
const BASELINE = { files: 108, refs: 594, imports: 473 }

/**
 * JPA / Hibernate / Spring Data 的 import 根（命中即计入 imports）。
 * 注意 `org.springframework.data.domain` 只是 Sort/Page/Pageable —— 它们也是 JPA 路径上的，
 * 换成 MyBatis-Plus 后应该变成 `com.baomidou.mybatisplus.extension.plugins.pagination.Page`。
 */
const JPA_IMPORT_ROOTS = [
  'jakarta.persistence.',
  'org.springframework.data.jpa.',
  'org.springframework.data.domain.',
  'org.springframework.data.repository.',
  'org.hibernate.',
]

/**
 * JPA 专属的注解与 API 关键字（剥注释后在代码里出现即计入 refs）。
 * 这些是"换轨时必须逐处处理"的东西：注解要删/换，API 要改写成 MyBatis-Plus 等价物。
 */
const JPA_KEYWORDS = [
  // 实体映射注解（注意：全部用词边界匹配 —— `@Table` 不能把 `@TableName`（MyBatis-Plus）算进来，
  // `@Access` 不能把 Lombok 的 `@Accessors` 算进来，`Specification` 不能把本仓的
  // `DataPermissionSpecification` 算进来。这些坑本轮实测都踩到了。）
  [/@Entity\b/g, '@Entity'],
  [/@Table\b/g, '@Table'],
  [/@Column\b/g, '@Column'],
  [/@Id\b/g, '@Id'],
  [/@GeneratedValue\b/g, '@GeneratedValue'],
  [/@MappedSuperclass\b/g, '@MappedSuperclass'],
  [/@Transient\b/g, '@Transient'],
  [/@ManyToOne\b/g, '@ManyToOne'],
  [/@OneToMany\b/g, '@OneToMany'],
  [/@ManyToMany\b/g, '@ManyToMany'],
  [/@OneToOne\b/g, '@OneToOne'],
  [/@JoinColumn\b/g, '@JoinColumn'],
  [/@Enumerated\b/g, '@Enumerated'],
  [/@PrePersist\b/g, '@PrePersist'],
  [/@PreUpdate\b/g, '@PreUpdate'],
  [/@Version\b/g, '@Version'],
  [/@EntityListeners\b/g, '@EntityListeners'],
  [/@Embedded\b/g, '@Embedded'],
  [/@Basic\b/g, '@Basic'],
  // Spring Data JPA / Spring Data domain
  [/\bJpaRepository\b/g, 'JpaRepository'],
  [/\bJpaSpecificationExecutor\b/g, 'JpaSpecificationExecutor'],
  [/\bSpecification\b/g, 'Specification'],
  [/\bPageable\b/g, 'Pageable'],
  [/\bPageRequest\b/g, 'PageRequest'],
  [/\bExample\.of\b/g, 'Example.of'],
  [/@Query\b/g, '@Query'],
  [/@Modifying\b/g, '@Modifying'],
  [/@Lock\b/g, '@Lock'],
  [/\bLockModeType\b/g, 'LockModeType'],
  [/@NoRepositoryBean\b/g, '@NoRepositoryBean'],
  // JPA 运行时 API
  [/\bEntityManager\b/g, 'EntityManager'],
  [/\bCriteriaBuilder\b/g, 'CriteriaBuilder'],
  [/\bCriteriaQuery\b/g, 'CriteriaQuery'],
  [/\bjakarta\.persistence\.criteria\.Predicate\b/g, 'criteria.Predicate'],
  [/\bTypedQuery\b/g, 'TypedQuery'],
  [/@PersistenceContext\b/g, '@PersistenceContext'],
  [/\bHibernate\b/g, 'Hibernate'],
]

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules' || entry.name === '.git') {
      continue
    }
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, out)
    }
    else {
      out.push(full)
    }
  }
  return out
}

const srcDir = path.join(SERVER, 'src')
const javaFiles = fs.existsSync(srcDir) ? walk(srcDir).filter(f => f.endsWith('.java')) : []

const fileHits = []
const keywordHits = new Map()
const importHits = new Map()
const moduleHits = new Map()

/** 模块名：`module/<域>`、`base`、`common`、`framework`、`config` …（用于按域看进度） */
function moduleOf(rel) {
  const parts = rel.split('/')
  const i = parts.indexOf('module')
  if (i >= 0 && parts[i + 1]) {
    return `module/${parts[i + 1]}`
  }
  return parts[0] ?? '(root)'
}

for (const file of javaFiles) {
  const rel = path.relative(ROOT, file).replaceAll('\\', '/')
  const source = stripJavaComments(fs.readFileSync(file, 'utf8'))
  const hits = []
  for (const [pattern, label] of JPA_KEYWORDS) {
    const count = (source.match(pattern) ?? []).length
    if (count > 0) {
      hits.push(`${label}×${count}`)
      keywordHits.set(label, (keywordHits.get(label) ?? 0) + count)
    }
  }
  const imports = source.match(/^import\s+(?:static\s+)?([\w.]+)\s*;/gm) ?? []
  const jpaImports = imports.filter(line => JPA_IMPORT_ROOTS.some(root => line.includes(root)))
  if (hits.length === 0 && jpaImports.length === 0) {
    continue
  }
  const refCount = hits.reduce((sum, h) => sum + Number(h.split('×')[1]), 0)
  fileHits.push({ file: rel, module: moduleOf(rel), refs: refCount, imports: jpaImports.length })
  for (const line of jpaImports) {
    const root = JPA_IMPORT_ROOTS.find(r => line.includes(r))
    importHits.set(root, (importHits.get(root) ?? 0) + 1)
  }
  moduleHits.set(moduleOf(rel), (moduleHits.get(moduleOf(rel)) ?? 0) + refCount)
}

// ── 依赖与配置面 ─────────────────────────────────────────────────────────────
const pomPath = path.join(SERVER, 'pom.xml')
const pom = fs.existsSync(pomPath) ? fs.readFileSync(pomPath, 'utf8') : ''
const jpaStarterDeclared = /<artifactId>spring-boot-starter-data-jpa<\/artifactId>/.test(pom)

const ddlAuto = []
for (const yml of walk(path.join(SERVER, 'src', 'main', 'resources')).filter(f => /\.ya?ml$/.test(f))) {
  const lines = fs.readFileSync(yml, 'utf8').split('\n')
  lines.forEach((line, index) => {
    const m = line.match(/^\s*ddl-auto:\s*(\S+)/)
    if (m) {
      ddlAuto.push(`${path.relative(ROOT, yml).replaceAll('\\', '/')}:${index + 1}=${m[1]}`)
    }
  })
}

/**
 * 跨 ORM 判据：实体的表名必须只有一份真相
 *
 * 为什么需要它（2026-09-18 实测）：JPA 读 `@Table(name)`，MyBatis-Plus 读 `@TableName`，
 * **两个都不写时 MP 会按类名推表名**（`ContractEntity` → `contract_entity`）——
 * 而真表叫 `contract`。那不是一个报错，是**指向另一张表**：查不到数据、或者查到别人的数据，
 * 且编译/启动都不报。所以换轨期内钉死：每个 `@Entity` 必须同时有
 * `@Table(name = "x")` 与 `@TableName("x")`，且 **x 相同**。
 */
const tableNameDrift = []
for (const file of javaFiles) {
  const rel = path.relative(ROOT, file).replaceAll('\\', '/')
  // 只查生产实体：测试里的 @Entity 是给 JPA 用的局部夹具（它们不需要被 MP 认识），
  // 要求测试夹具也写 @TableName 属于把判据推到它管不着的地方
  if (!rel.includes('/src/main/java/')) {
    continue
  }
  const source = stripJavaComments(fs.readFileSync(file, 'utf8'))
  if (!/@Entity\b/.test(source)) {
    continue
  }
  const jpaTable = /@Table\(\s*name\s*=\s*"([^"]+)"/.exec(source)
  const mpTable = /@TableName\(\s*"([^"]+)"/.exec(source)
  if (!jpaTable || !mpTable) {
    tableNameDrift.push({ file: rel, reason: !jpaTable ? '缺 @Table(name)' : '缺 @TableName' })
    continue
  }
  if (jpaTable[1] !== mpTable[1]) {
    tableNameDrift.push({ file: rel, reason: `表名不一致：@Table=${jpaTable[1]} / @TableName=${mpTable[1]}` })
  }
}

const totals = {
  javaFiles: javaFiles.length,
  jpaFiles: fileHits.length,
  refs: fileHits.reduce((sum, f) => sum + f.refs, 0),
  imports: fileHits.reduce((sum, f) => sum + f.imports, 0),
}

const report = {
  generatedAt: new Date().toISOString(),
  totals,
  baseline: BASELINE,
  jpaStarterDeclared,
  ddlAuto,
  tableNameDrift,
  byModule: [...moduleHits.entries()]
    .map(([module, refs]) => ({ module, refs }))
    .sort((a, b) => b.refs - a.refs),
  byKeyword: [...keywordHits.entries()]
    .map(([keyword, count]) => ({ keyword, count }))
    .sort((a, b) => b.count - a.count),
  byImport: [...importHits.entries()]
    .map(([root, count]) => ({ root, count }))
    .sort((a, b) => b.count - a.count),
  /** 最重的 20 个文件 = 下一批优先看的对象 */
  heaviest: [...fileHits].sort((a, b) => b.refs - a.refs).slice(0, 20),
}

// ── 棘轮 ──
if (process.argv.includes('--check')) {
  const grows = []
  if (totals.jpaFiles > BASELINE.files) {
    grows.push(`JPA 文件 ${totals.jpaFiles} > 基线 ${BASELINE.files}`)
  }
  if (totals.refs > BASELINE.refs) {
    grows.push(`JPA 引用 ${totals.refs} > 基线 ${BASELINE.refs}`)
  }
  if (totals.imports > BASELINE.imports) {
    grows.push(`JPA import ${totals.imports} > 基线 ${BASELINE.imports}`)
  }
  if (tableNameDrift.length) {
    console.error('\n✗ 实体表名不一致（跨 ORM 判据）：\n')
    for (const entry of tableNameDrift) {
      console.error(`  · ${entry.file}：${entry.reason}`)
    }
    console.error('\nMyBatis-Plus 按 @TableName 解析表名；只有 @Table(name) 时它会按类名推断'
      + '（ContractEntity → contract_entity），那是指向另一张表，编译与启动都不会报。\n')
    process.exit(1)
  }
  if (grows.length) {
    console.error('\n✗ JPA 足迹比基线更大：\n')
    for (const line of grows) {
      console.error(`  · ${line}`)
    }
    console.error('\n去 JPA 是长跑，数字只许降。新写的代码请直接用 MyBatis-Plus'
      + '（`KuboBaseMapper` / `KuboInfraMapper` + `LambdaQueryWrapper`）。\n')
    process.exit(1)
  }
  console.log(
    `✓ JPA 棘轮通过：文件 ${totals.jpaFiles}/${BASELINE.files} · `
    + `引用 ${totals.refs}/${BASELINE.refs} · import ${totals.imports}/${BASELINE.imports}（只许降）`,
  )
  console.log('✓ 实体表名一致：每个 @Entity 的 @Table(name) 与 @TableName 同值（MP 不会指到别的表）')
  process.exit(0)
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

console.log(`\n🧱 JPA 足迹（${totals.jpaFiles} / ${totals.javaFiles} 个 Java 文件，${totals.refs} 处引用，${totals.imports} 条 JPA import）`)
console.log(`  依赖：spring-boot-starter-data-jpa ${jpaStarterDeclared ? '**仍在 pom 里**（删它的条件是写链路与实体都换完）' : '已删除'}`)
console.log(`  ddl-auto：${ddlAuto.join(' · ') || '无'}\n`)

console.log('── 按模块 ──────────────────────────────────────────────')
for (const { module, refs } of report.byModule.slice(0, 15)) {
  console.log(`  ${module.padEnd(20)} ${String(refs).padStart(4)} 处`)
}

console.log('\n── 按关键字（前 20，= 换轨时要逐处处理的东西）────────────')
for (const { keyword, count } of report.byKeyword.slice(0, 20)) {
  console.log(`  ${String(count).padStart(4)}×  ${keyword}`)
}

console.log('\n── 最重的 20 个文件（下一批优先看）──────────────────────')
for (const f of report.heaviest) {
  console.log(`  ${String(f.refs).padStart(4)} 处/${String(f.imports).padStart(2)} import  ${f.file}`)
}

console.log('\n  用法：`pnpm jpa:check -- --json` 给脚本读；`--check` 是棘轮（只许降）。\n')

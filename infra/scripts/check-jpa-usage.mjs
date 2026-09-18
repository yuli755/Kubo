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
 * - 2026-09-18 J2（写链路引擎干净切除）：**108 → 84 文件 / 594 → 503 引用 / 473 → 385 import**。
 *   引擎（`CurdService`/`QueryHelper`/`PageData`/`QueryPageResponse`/`ICurdRepository`）、
 *   15 个仓储的派生查询、17 个查询钩子、数据权限的读侧注入全部离开 Spring Data / Criteria；
 *   剩下的 84 个文件是**实体注解 + 影子比对线**（`ShadowDiffService` / `JpaRowVisibilityProbe` /
 *   `*ReadShadowService` / `JpaPermissionRuleCompiler`）—— 它们按 §10.8.4 的口径自持到 J3/J4：
 *   实体注解批量替换 + 影子线收尾 + 删 starter，届时这三行归零。
 *   注意本批的教训同样值得记：换轨**没有**碰 `org.springframework.data.redis`（那不是 JPA），
 *   数字掉下来的那部分是 `org.springframework.data.jpa.*` 与 `org.springframework.data.domain.*`。
 * - 2026-09-18 J4（去 starter + ddl-auto + 影子线收尾）：**30 → 0 文件 / 122 → 0 引用 / 77 → 0 import**。
 *   影子比对那 30 个文件随 `spring-boot-starter-data-jpa` 一起退场：JPA 侧已被整个删掉，
 *   "两套 ORM 行数对不一致"这个问题从此不存在（§10.5 的口径：换完只剩一条路径）；
 *   `ShadowDiffService` 留下来但换成 MP 单路径取证（同一份渲染产物挂到 wrapper 上数两遍），
 *   `SHADOW_EVIDENCE` 继续是放开 enforce 的阻塞项 —— 证据链没有因此变松。
 *   **棘轮到 0 之后这个脚本不删**：它现在唯一的职责是"谁把 JPA 写回来立刻红"。
 *   判据从"数字只许降"换成三条硬事实：pom 里没有 starter、任何 yml 里没有 `spring.jpa`/`ddl-auto`、
 *   实体上没有 JPA 注解。留着一条只会打印 0 的棘轮是装饰，换成硬判据才是门禁。
 * - 2026-09-18 J3（实体注解换 MP + 关系字段对象图）：**84 → 30 文件 / 503 → 122 引用 / 385 → 77 import**。
 *   这一批把 41 个实体 + 4 个基类上的 JPA 注解（`@Entity`/`@Table`/`@Column`/`@Id`/`@GeneratedValue`/
 *   `@ManyToOne`/`@ManyToMany`/`@MappedSuperclass`/`@PrePersist`/`@DynamicInsert`）整体换成
 *   `@TableName`/`@TableField`/`@TableId`，并把"关系字段不再是列"这件事从注解层搬到代码层
 *   （对象字段标 `@TableField(exist = false)`，值落在标量外键字段上；读回填走 `RelationHydrator`，
 *   写同步走 `RelationMapping`，中间表读写走属主仓储 XML 的三条语句）。
 *   剩下的 30 个文件就是**影子比对线**（`ShadowDiffService` / `JpaRowVisibilityProbe` /
 *   `*ReadShadowService` / `JpaPermissionRuleCompiler` / `DataPermissionSpecification`）——
 *   它们按 §10.5 的口径自持到 J4：与 `spring-boot-starter-data-jpa`、`ddl-auto` 一起退场。
 *   本批的教训：**注解删干净不等于语义保住了**。`@Column(unique = true)` 这类"顺带声明业务规则"
 *   的注解要自己接住（→ 框架自持 `@Unique` + `schemaContract` 的交叉判据），
 *   `@PrePersist` 这种生命周期回调要换宿主（→ `KuboMetaObjectHandler`），
 *   否则删的当天没有任何东西会红，直到线上出现重复数据或归属为空。
 */
const BASELINE = { files: 0, refs: 0, imports: 0 }

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

/**
 * J4 判据：配置面里不许再有 JPA 的东西
 *
 * 口径从"数 `ddl-auto`"扩成"数 `spring.jpa` 这一段" —— 删掉 starter 之后，
 * 任何一个 yml 里剩下的 `spring.jpa.*` 都是**没人读的配置**：它不会报错、也不会生效，
 * 只会让下一个读配置的人以为 schema 校验还开着（`ddl-auto: validate` 的注释尤其容易被这么读）。
 * 两条都判：整段（`spring:\n  jpa:`）与孤立的 `ddl-auto`（有人可能把它挪到别处）。
 */
const jpaConfigHits = []
for (const yml of walk(path.join(SERVER, 'src', 'main', 'resources')).filter(f => /\.ya?ml$/.test(f))) {
  const lines = fs.readFileSync(yml, 'utf8').split('\n')
  let inSpringBlock = false
  lines.forEach((line, index) => {
    const rel = `${path.relative(ROOT, yml).replaceAll('\\', '/')}:${index + 1}`
    if (/^\S/.test(line)) {
      // 顶层键（注释行以 # 开头，不算）：离开 spring 段
      inSpringBlock = /^spring:\s*(?:#.*)?$/.test(line)
    }
    if (inSpringBlock && /^\s{2}jpa:\s*(?:#.*)?$/.test(line)) {
      jpaConfigHits.push(`${rel} spring.jpa 段（J4 之后没有东西会读它）`)
    }
    const m = line.match(/^\s*ddl-auto:\s*(\S+)/)
    if (m) {
      jpaConfigHits.push(`${rel} ddl-auto=${m[1]}`)
    }
  })
}

/**
 * J3 判据：实体类不许再长回 JPA 注解
 *
 * 为什么需要它（J3 之后的现实）：41 个实体的 JPA 注解换成了 MyBatis-Plus 的
 * `@TableName`/`@TableField`/`@TableId`。如果谁在某个实体上顺手写回一个 `@Column`，
 * 编译不报、启动不报、`ddl:check` 也不报（它只认 MP 口径）—— 那个注解就成了一段
 * **看起来在声明什么、实际上没人读**的注释。而 `@Id`/`@GeneratedValue` 回来更糟：
 * MP 不认识它们，主键回填会静默失效（插入后 `getId()` 是 null）。
 *
 * 口径：以 `@TableName` 认实体（J3 之后实体只有这一个标记），逐个扫它有没有
 * **实体映射类**的 JPA 注解（词边界匹配，所以 `@TableName` 不会被算成 `@Table`）。
 * 空转守卫：`@TableName` 文件少于 30 个就红 —— 说明扫描口径坏了，而不是"实体都干净"。
 */
const ENTITY_JPA_ANNOTATIONS = [
  // 全限定写法（`@jakarta.persistence.Column`）也要抓：它有 import，注解本身带 `.` 前缀，
  // 只按短名匹配会漏掉这一类 —— 而"漏掉"正是这条门禁最怕的失效方式。
  [/jakarta\.persistence\./g, 'jakarta.persistence.*'],
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
  [/@JoinTable\b/g, '@JoinTable'],
  [/@Enumerated\b/g, '@Enumerated'],
  [/@PrePersist\b/g, '@PrePersist'],
  [/@PreUpdate\b/g, '@PreUpdate'],
  [/@DynamicInsert\b/g, '@DynamicInsert'],
  [/@DynamicUpdate\b/g, '@DynamicUpdate'],
]

const entityFiles = []
const annotationLeaks = []
for (const file of javaFiles) {
  const rel = path.relative(ROOT, file).replaceAll('\\', '/')
  if (!rel.includes('/src/main/java/')) {
    continue
  }
  const source = stripJavaComments(fs.readFileSync(file, 'utf8'))
  if (!/@TableName\s*\(/.test(source)) {
    continue
  }
  entityFiles.push(rel)
  for (const [pattern, label] of ENTITY_JPA_ANNOTATIONS) {
    const hits = source.match(pattern)?.length ?? 0
    if (hits > 0) {
      annotationLeaks.push({ file: rel, label, hits })
    }
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
  jpaConfigHits,
  entityFiles: entityFiles.length,
  annotationLeaks,
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
  // J4 硬判据：棘轮已经到 0，再往后"只许降"没有下降空间 —— 判的是"不许回来"
  const facts = []
  if (jpaStarterDeclared) {
    facts.push('pom.xml 里又声明了 spring-boot-starter-data-jpa：J4 之后写链路引擎与实体注解都是 MP 口径，'
      + '把它加回来会同时引入两套一级缓存/flush 语义（实体上也没有 JPA 注解，启动即报"没有映射的实体"）')
  }
  if (jpaConfigHits.length) {
    facts.push(`配置面还有 JPA 的残留：${jpaConfigHits.join(' · ')}`
      + '（没有 starter 时这些键没人读，留着只会让人以为 schema 校验还开着）')
  }
  if (entityFiles.length < 30) {
    console.error(`\n✗ 只扫到 ${entityFiles.length} 个 @TableName 实体 —— 解析口径失效，别让本条门禁空转\n`)
    process.exit(1)
  }
  if (annotationLeaks.length) {
    console.error('\n✗ 实体上出现了 JPA 注解（J3 之后它们没有任何东西在读）：\n')
    for (const entry of annotationLeaks) {
      console.error(`  · ${entry.file}：${entry.label} × ${entry.hits}`)
    }
    console.error('\n实体映射只认 MyBatis-Plus：表名 @TableName、列 @TableField、主键 @TableId(type = IdType.AUTO)、'
      + '唯一列 @Unique。写回 @Column/@Id 这类注解不会编译失败、也不会启动失败 ——'
      + '它们会变成"看起来在声明什么、其实没人读"的死注解（@Id 还会让主键回填静默失效）。\n')
    process.exit(1)
  }
  if (facts.length) {
    console.error('\n✗ JPA 又回来了：\n')
    for (const line of facts) {
      console.error(`  · ${line}`)
    }
    console.error('\n本仓只保留 MyBatis-Plus：读侧 `KuboBaseMapper` / 写侧 `KuboInfraMapper` +'
      + ' `LambdaQueryWrapper`，表结构由 `infra/init-sql/init.sql` 单一维护、'
      + '由 `pnpm ddl:check` 与 schemaContract 在 CI 里核对。\n')
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
    `✓ JPA 归零：文件 ${totals.jpaFiles}/${BASELINE.files} · `
    + `引用 ${totals.refs}/${BASELINE.refs} · import ${totals.imports}/${BASELINE.imports}`,
  )
  console.log('✓ 依赖与配置面干净：pom 无 spring-boot-starter-data-jpa · 无 spring.jpa / ddl-auto')
  console.log(`✓ 实体注解干净：${entityFiles.length} 个 @TableName 实体里 0 处 JPA 映射注解`)
  process.exit(0)
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

console.log(`\n🧱 JPA 足迹（${totals.jpaFiles} / ${totals.javaFiles} 个 Java 文件，${totals.refs} 处引用，${totals.imports} 条 JPA import）`)
console.log(`  依赖：spring-boot-starter-data-jpa ${jpaStarterDeclared ? '**仍在 pom 里**（删它的条件是写链路与实体都换完）' : '已删除'}`)
console.log(`  spring.jpa 配置面：${jpaConfigHits.join(' · ') || '无'}\n`)

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

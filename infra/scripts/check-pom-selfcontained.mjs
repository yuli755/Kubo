#!/usr/bin/env node
/**
 * # POM 自持校验（没有 JDK/Maven 也能跑的那一半）
 *
 * ## 为什么需要它
 * 2026-09-18 起 `SPMS-Server/pom.xml` **不再继承 cn.hamm:airpower 父 POM**（退场 B0）：
 * 原来由父 POM 提供的构建基线（Java 17 / UTF-8）与依赖版本表（Spring Boot BOM）
 * 全部搬进了本 POM。父 POM 消失后，**Maven 不再替我们兜底**：
 *
 *   - 少写一个 `<version>` 的依赖，在旧结构下可能被父 POM 的 dependencyManagement 救活，
 *     现在会变成 "dependencies.dependency.version is missing" —— 构建期报错；
 *   - 少写一个 `<properties>`，`${xxx}` 会原样当成版本号去仓库要 `xxx`，同样是构建期报错；
 *   - 多余的构建插件（source / javadoc / gpg / central-publishing）不再被继承进来。
 *
 * 这三类问题**不需要编译就能查**：读 pom.xml 就能判定。本脚本就是这道离线闸门。
 *
 * ## 它查什么
 * 1. 依赖没有写 `<version>` 时，其 groupId 必须在 **Spring Boot BOM 家族白名单**内
 *    （BOM 里管着这些族的版本，所以不写是对的）；
 * 2. pom 里出现的每一个 `${...}` 属性（`project.*` / `env.*` / `settings.*` 除外）
 *    必须在本 POM 的 `<properties>` 里有定义；
 * 3. 不允许残留 airpower 发版用的构建插件（`central-publishing-maven-plugin` /
 *    `maven-gpg-plugin` / `maven-source-plugin` / `maven-javadoc-plugin`）；
 * 4. `cn.hamm:` 依赖**一个都不许有**（2026-09-18 起 airpower-curd 已删除，白名单为空集）。
 *
 * ## 用法
 * ```
 * pnpm pom:check                 # 闸门：任一问题即 exit 1
 * pnpm pom:check -- --json       # 给脚本读
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const POM = path.join(ROOT, 'SPMS-Server', 'pom.xml')

/**
 * Spring Boot BOM（`spring-boot-dependencies`）管版本的 groupId 家族。
 * 白名单外的依赖**必须**自带 `<version>`，否则 Maven 解析不出该用什么版本。
 *
 * 新增一个 groupId 到这里 = 声明"我知道它由 Spring Boot BOM 管版本"，
 * 所以只在确认过 BOM 里确实有它之后才加。
 */
const BOOT_BOM_GROUPS = new Set([
  'org.springframework.boot',
  'org.springframework',
  'io.micrometer',
  'com.h2database',
  'org.flywaydb',
  'jakarta.validation',
])

/**
 * 退场期间允许残留的 airpower 依赖 —— **现在是空集**。
 *
 * 2026-09-18：最后一条 `cn.hamm:airpower-curd` 已删除（curd/api/core/redis/cookie/exception 六模块
 * 全部搬进本仓自持）。这条白名单保留成空集合而不是删掉，是为了让"再塞一条 airpower 依赖回来"
 * 变成一次**显式的代码改动**（要在 review 里被看见），而不是悄悄通过。
 */
const AIRPOWER_DEPENDENCIES_STILL_ALLOWED = new Set([])

/** airpower 父 POM 带来的发版插件：应用工程不需要，也不该被继承回来。 */
const FORBIDDEN_BUILD_PLUGINS = [
  'central-publishing-maven-plugin',
  'maven-gpg-plugin',
  'maven-source-plugin',
  'maven-javadoc-plugin',
]

/** Maven 内置/隐式定义的属性前缀，不在本 POM 里出现是正常的。 */
const IMPLICIT_PROPERTY_PREFIXES = ['project.', 'env.', 'settings.', 'pom.', 'java.', 'maven.', 'basedir', 'version']

const rawPom = fs.readFileSync(POM, 'utf8')
/**
 * 注释先剥掉再查：pom 的注释里会写"${...} 属性必须有定义""maven-gpg-plugin 是父 POM 遗产"这类
 * **举例说明**，不剥的话闸门会被自己写的文档触发（本轮就这么错过一次）。
 */
const pom = rawPom.replace(/<!--[\s\S]*?-->/g, '')
const problems = []
const notes = []

// ────────────────────────────── 1. 依赖版本 ──────────────────────────────
const dependencyBlocks = [...pom.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)].map(m => m[1])
const versionlessDeps = []
for (const block of dependencyBlocks) {
  const groupId = block.match(/<groupId>([^<]+)<\/groupId>/)?.[1]?.trim()
  const artifactId = block.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim()
  const hasVersion = /<version>/.test(block)
  if (!groupId || !artifactId || hasVersion)
    continue
  versionlessDeps.push({ groupId, artifactId })
}

const offendingDeps = versionlessDeps.filter(d => !BOOT_BOM_GROUPS.has(d.groupId))
for (const d of offendingDeps)
  problems.push(`依赖 ${d.groupId}:${d.artifactId} 没有写 <version>，且不在 Spring Boot BOM 白名单里 —— 父 POM 没了，没人替它定版本`)

// ────────────────────────────── 2. 属性引用 ──────────────────────────────
const declaredProps = new Set(
  [...pom.matchAll(/<([\w.-]+)>[^<]*<\/\1>/g)]
    .map(m => m[1])
    .filter(name => !['groupId', 'artifactId', 'version', 'name', 'description', 'modelVersion', 'packaging', 'url', 'type', 'scope', 'goal', 'id', 'enabled', 'url'].includes(name)),
)
// 更稳的口径：直接看 <properties> 段里的键
const propertiesSection = pom.match(/<properties>([\s\S]*?)<\/properties>/)?.[1] ?? ''
const propertyKeys = new Set([...propertiesSection.matchAll(/<([\w.-]+)>/g)].map(m => m[1]))

const referenced = new Set([...pom.matchAll(/\$\{([^}]+)\}/g)].map(m => m[1].trim()))
const dangling = [...referenced].filter((name) => {
  if (propertyKeys.has(name))
    return false
  return !IMPLICIT_PROPERTY_PREFIXES.some(prefix => name.startsWith(prefix))
})
for (const name of dangling)
  problems.push(`\${${name}} 在本 POM 里没有定义 —— 父 POM 没了，它会被原样当成版本号去仓库要 "${name}"`)

// ────────────────────────────── 3. 发版插件 ──────────────────────────────
for (const plugin of FORBIDDEN_BUILD_PLUGINS) {
  if (pom.includes(`<artifactId>${plugin}</artifactId>`))
    problems.push(`构建插件 ${plugin} 又出现了 —— 那是 airpower 发版用的（父 POM 遗产），应用工程不需要`)
}

// ────────────────────────────── 4. airpower 依赖 ──────────────────────────────
const hammDeps = dependencyBlocks
  .map(b => b.match(/<groupId>([^<]+)<\/groupId>/)?.[1]?.trim())
  .filter(g => g === 'cn.hamm')
const hammArtifacts = dependencyBlocks
  .filter(b => b.includes('<groupId>cn.hamm</groupId>'))
  .map(b => b.match(/<artifactId>([^<]+)<\/artifactId>/)?.[1]?.trim())
for (const artifact of hammArtifacts) {
  if (!AIRPOWER_DEPENDENCIES_STILL_ALLOWED.has(artifact))
    problems.push(`残留 airpower 依赖 ${artifact}：不在退场白名单内（要么删掉它，要么在这个脚本里显式承认）`)
}

// ────────────────────────────── 报告 ──────────────────────────────
const hasParent = /<parent>/.test(pom)
notes.push(`父 POM：${hasParent ? '有（应为无 —— B0 已摘除）' : '无（自持构建基线，B0 已生效）'}`)
notes.push(`白名单内不写版本的依赖：${versionlessDeps.length} 个（${[...new Set(versionlessDeps.map(d => d.groupId))].join(', ')}）`)
notes.push(`本 POM 定义属性：${[...propertyKeys].join(', ')}`)
notes.push(`残留 cn.hamm 依赖：${hammArtifacts.join(', ') || '无'}`)

if (hasParent)
  problems.push('pom.xml 里还有 <parent> —— B0 之后应当完全自持')

if (process.argv.includes('--json')) {
  console.log(JSON.stringify({ pom: 'SPMS-Server/pom.xml', versionlessDeps, propertyKeys: [...propertyKeys], hammArtifacts, problems, notes }, null, 2))
  process.exit(problems.length ? 1 : 0)
}

console.log('\n🔩 POM 自持校验（SPMS-Server/pom.xml · 无 JDK/Maven 也能跑）\n')
for (const n of notes)
  console.log(`  · ${n}`)
if (problems.length) {
  console.log('\n✗ 发现问题：')
  for (const p of problems)
    console.log(`  · ${p}`)
  process.exit(1)
}
console.log('\n✓ 通过：无版本依赖都在 BOM 家族内、${...} 属性都有定义、无发版插件残留、cn.hamm 依赖为零\n')

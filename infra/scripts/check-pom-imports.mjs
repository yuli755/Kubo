#!/usr/bin/env node
/**
 * # pom 依赖 ↔ Java import 对账门禁（没有 JDK / Maven 也能跑的那一半）
 *
 * ## 为什么需要它
 * 2026-09-18 去掉 `cn.hamm:airpower` **父 POM** 与 `airpower-curd` 依赖之后，pom 变成完全自持 ——
 * 代价是：**原来由框架传递过来的 starter 不再自动到位**，而代码一直在 import 它们。
 * 那次重写只补了能立刻想到的几条，漏掉了 `spring-boot-starter-web` / `-data-redis` /
 * `-validation` / `-aop` / `lombok` / `org.jetbrains:annotations` / `mysql-connector-j`，
 * 于是**整个后端编译不过**（183 个文件 import lombok、97 处 import spring-web…），
 * 而三件事同时把这个问题藏住了：
 *   1. 沙箱里没有 JDK，`pnpm check` 全是静态检查，碰不到编译；
 *   2. `pnpm pom:check` 只管"**不该有的**依赖"（无版本 / 悬空属性 / cn.hamm 残留），
 *      从不检查"**该有的**依赖在不在"；
 *   3. 那段时间 CI 因计费问题全部「未启动」，真编译一步都没跑。
 *
 * 判据本身很朴素：**代码 import 了什么，pom 就必须声明谁能提供它**。这条规律不需要编译器，
 * 只需要两张表（import 家族 → 需要的 artifact），所以它能进 `pnpm check`，在提交前就红。
 *
 * ## 它查什么
 * ① 每个 import 家族必须归口到一条规则，规则里列出的 artifact 必须**至少有一个**被 pom 声明；
 * ② 规则表没覆盖的第三方 import 家族 → **失败**（逼人来归口，而不是让它悄悄溜过）；
 * ③ yml 里出现的 JDBC 驱动（`com.mysql.` / `org.h2.`）必须在 pom 里有对应驱动依赖
 *    —— 驱动是**运行期**依赖，代码里不 import，只靠 ① 是抓不到的；
 * ④ 反向提示：pom 里声明了、但规则表与运行期白名单都不认识的依赖 → 只提示不失败（可能是纯运维件）。
 *
 * ## 口径（写清楚，免得被"顺手放宽"）
 * - 规则里的 artifact 列表 = **验收面**，不是建议：`spring-boot-starter-web` 那一格少了，
 *   这道门禁就该红 —— 哪怕 jackson 侥幸由 springdoc 带进来了。显式声明 > 依赖传递。
 * - 归口到"任意 Boot starter"（`anyBootStarter`）的只有 Spring 自身的核心包
 *   （context / beans / core / stereotype / util / cache / scheduling / aop / boot 等）：
 *   它们由任一 starter 提供，逐个钉死 starter 只会制造假红。
 *
 * ## 用法
 * ```
 * pnpm deps:check              # 闸门：任一问题即 exit 1
 * pnpm deps:check -- --json    # 给脚本读
 * ```
 *
 * @author Arena agent
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')
const POM = path.join(SERVER, 'pom.xml')
const JAVA_DIRS = [path.join(SERVER, 'src', 'main', 'java'), path.join(SERVER, 'src', 'test', 'java')]
const YML_DIRS = [path.join(SERVER, 'src', 'main', 'resources'), path.join(SERVER, 'src', 'test', 'resources')]

const argv = process.argv.slice(2)
const asJson = argv.includes('--json')

function walk(dir, out = []) {
  if (!fs.existsSync(dir)) {
    return out
  }
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
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

const rel = file => path.relative(ROOT, file).replaceAll('\\', '/')

// ── pom：只取 build 依赖（dependencyManagement 里的 BOM import 不算"提供了什么"）──
const pomRaw = fs.readFileSync(POM, 'utf8')
const dependencies = [...pomRaw.matchAll(/<dependency>([\s\S]*?)<\/dependency>/g)]
  .map(m => m[1])
  .map((block) => {
    const groupId = block.match(/<groupId>([^<]+)</)?.[1]
    const artifactId = block.match(/<artifactId>([^<]+)</)?.[1]
    const scope = block.match(/<scope>([^<]+)</)?.[1] ?? 'compile'
    return groupId && artifactId ? { g: groupId, a: artifactId, scope } : null
  })
  .filter(Boolean)
const declared = new Set(dependencies.map(d => `${d.g}:${d.a}`))
const anyBootStarter = dependencies.some(d => d.g === 'org.springframework.boot' && d.a.startsWith('spring-boot-starter'))

// ── Java import ──
// 按**全限定名**统计（不是前 N 段）：规则里的前缀本来就是精确到包的，
// 截断成"前两段/前三段"会把 org.springframework.data.redis.* 与
// org.springframework.data.mapping.* 混成一个家族，规则匹配随之出错（踩过）。
const IMPORT_RE = /^\s*import\s+(?:static\s+)?([\w.]+)\s*;/gm
const importCount = new Map() // 全限定名 → 出现次数

for (const dir of JAVA_DIRS) {
  for (const file of walk(dir).filter(f => f.endsWith('.java'))) {
    const source = fs.readFileSync(file, 'utf8')
    for (const m of source.matchAll(IMPORT_RE)) {
      const fqn = m[1]
      if (fqn.startsWith('java.') || fqn.startsWith('javax.') || fqn.startsWith('cn.hamm.')) {
        continue
      }
      importCount.set(fqn, (importCount.get(fqn) ?? 0) + 1)
    }
  }
}

/**
 * 归口规则：`match` 命中 import 家族即归到这条，
 * `artifacts` 里任一被 pom 声明即通过；`anyBootStarter` 表示"任一 Boot starter 提供即可"。
 */
const RULES = [
  { id: 'web', label: 'Web 层（spring-web/webmvc + servlet + Jackson）', match: /^org\.springframework\.(web|http|validation|boot\.web)\.|^jakarta\.servlet\./, artifacts: ['org.springframework.boot:spring-boot-starter-web'] },
  { id: 'jackson', label: 'Jackson（JSON 序列化；随 web starter 进来）', match: /^com\.fasterxml\.jackson\./, artifacts: ['org.springframework.boot:spring-boot-starter-web'] },
  { id: 'redis', label: 'Redis（spring-data-redis / lettuce）', match: /^org\.springframework\.data\.redis\.|^redis\.clients\./, artifacts: ['org.springframework.boot:spring-boot-starter-data-redis'] },
  { id: 'validation', label: '参数校验（jakarta.validation + hibernate-validator 实现）', match: /^jakarta\.validation\.|^org\.hibernate\.validator\./, artifacts: ['org.springframework.boot:spring-boot-starter-validation'] },
  { id: 'aop', label: 'AOP（aspectjweaver）', match: /^org\.aspectj\./, artifacts: ['org.springframework.boot:spring-boot-starter-aop'] },
  { id: 'lombok', label: 'Lombok（编译期注解处理器）', match: /^lombok\./, artifacts: ['org.projectlombok:lombok'] },
  { id: 'jetbrains', label: 'JetBrains 注解（@NotNull/@Nullable/@Contract）', match: /^org\.jetbrains\.annotations\./, artifacts: ['org.jetbrains:annotations'] },
  { id: 'mybatis', label: 'MyBatis-Plus + Spring JDBC/事务/DAO 异常族', match: /^com\.baomidou\.mybatisplus\.|^org\.apache\.ibatis\.|^org\.mybatis\.spring\.|^org\.springframework\.(jdbc|transaction|dao)\./, artifacts: ['com.baomidou:mybatis-plus-spring-boot3-starter'] },
  { id: 'jsqlparser', label: 'JSqlParser（MyBatis-Plus 拦截器解析 SQL 用）', match: /^net\.sf\.jsqlparser\./, artifacts: ['com.baomidou:mybatis-plus-jsqlparser'] },
  { id: 'openapi', label: 'SpringDoc / Swagger 注解与模型', match: /^io\.swagger\./, artifacts: ['org.springdoc:springdoc-openapi-starter-webmvc-api'] },
  { id: 'logging', label: '日志门面（slf4j，实现是 logback）', match: /^org\.slf4j\.|^ch\.qos\.logback\./, artifacts: ['org.springframework.boot:spring-boot-starter-logging'] },
  { id: 'metrics', label: '指标（Micrometer → /actuator/prometheus）', match: /^io\.micrometer\./, artifacts: ['io.micrometer:micrometer-registry-prometheus', 'org.springframework.boot:spring-boot-starter-actuator'] },
  { id: 'test', label: '测试（JUnit 5 / Mockito / spring-test）', match: /^org\.junit\.|^org\.mockito\.|^org\.springframework\.(test|mock)\.|^org\.assertj\.|^org\.hamcrest\./, artifacts: ['org.springframework.boot:spring-boot-starter-test'] },
  { id: 'mail', label: '邮件（Jakarta Mail + Spring Mail）', match: /^jakarta\.mail\.|^org\.springframework\.mail\./, artifacts: ['org.springframework.boot:spring-boot-starter-mail'] },
  { id: 'h2', label: 'H2（集成测试库）', match: /^org\.h2\./, artifacts: ['com.h2database:h2'] },
  { id: 'spring-core', label: 'Spring 核心（context/beans/core/stereotype/…）', match: /^org\.springframework\.(stereotype|beans|context|core|util|cache|scheduling|aop|lang|task|annotation|boot)\.|^jakarta\.annotation\./, anyBootStarter: true },
]

const problems = []
const ruleReport = []

const matchedFqns = new Set()
for (const rule of RULES) {
  const hits = [...importCount.entries()].filter(([fqn]) => rule.match.test(fqn))
  if (hits.length === 0) {
    continue
  }
  for (const [fqn] of hits) {
    matchedFqns.add(fqn)
  }
  const count = hits.reduce((sum, [, n]) => sum + n, 0)
  const satisfied = rule.anyBootStarter ? anyBootStarter : (rule.artifacts ?? []).some(a => declared.has(a))
  ruleReport.push({ id: rule.id, label: rule.label, imports: count, example: hits[0][0], satisfied })
  if (!satisfied) {
    problems.push(`缺少依赖：${rule.artifacts?.join(' 或 ') ?? '任一 spring-boot-starter-*'}（${count} 处 import 需要它，例如 ${hits[0][0]}）`)
  }
}

// ② 未归口的第三方 import：逼人显式归口（按前三段分组展示，示例给全限定名）
const unmatchedByFamily = new Map()
for (const fqn of importCount.keys()) {
  if (matchedFqns.has(fqn)) {
    continue
  }
  const family = fqn.split('.').slice(0, 3).join('.')
  if (!unmatchedByFamily.has(family)) {
    unmatchedByFamily.set(family, fqn)
  }
}
const unmatched = [...unmatchedByFamily.keys()]
for (const [family, example] of unmatchedByFamily) {
  problems.push(`未归口的 import：${family}.*（示例 ${example}）—— 请在 RULES 里加一条规则并写明哪个 artifact 提供它`)
}

// ③ yml 里的 JDBC 驱动（运行期依赖，代码不 import）
const ymlText = YML_DIRS.flatMap(dir => walk(dir).filter(f => /\.ya?ml$/.test(f))).map(f => fs.readFileSync(f, 'utf8')).join('\n')
const driverRules = [
  { needle: 'com.mysql.', artifact: 'com.mysql:mysql-connector-j', label: 'MySQL 驱动' },
  { needle: 'org.h2.', artifact: 'com.h2database:h2', label: 'H2 驱动' },
]
const driverReport = []
for (const rule of driverRules) {
  if (!ymlText.includes(rule.needle)) {
    continue
  }
  const ok = declared.has(rule.artifact)
  driverReport.push({ ...rule, satisfied: ok })
  if (!ok) {
    problems.push(`${rule.label}：yml 里配了 ${rule.needle}*，但 pom 没有声明 ${rule.artifact}（运行期 ClassNotFoundException）`)
  }
}

// ④ 反向提示（不失败）
const coveredArtifacts = new Set(RULES.flatMap(r => r.artifacts ?? []))
const runtimeAllow = new Set([
  'org.springframework.boot:spring-boot-starter-actuator', // 运维端点，代码不 import
  'io.micrometer:micrometer-registry-prometheus', // 由 actuator 装配
  'com.mysql:mysql-connector-j', // 驱动，运行期
  'com.h2database:h2', // 驱动，测试期
  'org.springframework.boot:spring-boot-dependencies', // BOM import（在 dependencyManagement 里）
])
const unusedDeclared = dependencies
  .filter(d => !coveredArtifacts.has(`${d.g}:${d.a}`) && !runtimeAllow.has(`${d.g}:${d.a}`) && !d.a.startsWith('spring-boot-starter'))
  .map(d => `${d.g}:${d.a}`)

const report = {
  declared: [...declared].sort(),
  rules: ruleReport,
  drivers: driverReport,
  unmatchedFamilies: unmatched,
  unmatchedFqns: [...importCount.keys()].filter(fqn => !matchedFqns.has(fqn)),
  unusedDeclared,
  problems,
}

if (asJson) {
  console.log(JSON.stringify(report, null, 2))
}
else {
  console.log('🔎 pom 依赖 ↔ Java import 对账')
  console.log(`  pom 声明：${declared.size} 个依赖（含 BOM import）`)
  console.log(`  业务 import：${importCount.size} 个全限定名（已排除 java.* / javax.* / cn.hamm.*）\n`)
  for (const entry of ruleReport.sort((a, b) => b.imports - a.imports)) {
    console.log(`  ${entry.satisfied ? '✅' : '❌'} ${String(entry.imports).padStart(4)} 处  ${entry.label}`)
  }
  for (const d of driverReport) {
    console.log(`  ${d.satisfied ? '✅' : '❌'} 驱动     ${d.label}（${d.needle}* ← ${d.artifact}）`)
  }
  if (unusedDeclared.length > 0) {
    console.log(`\n  ℹ️ 反向提示（声明了但规则表与运行期白名单都不认识，通常是纯运维件）：${unusedDeclared.join(', ')}`)
  }
  if (problems.length === 0) {
    console.log('\n✅ 通过：代码用到的每个 import 家族都有 pom 依赖兜底；yml 里的驱动也在')
  }
  else {
    console.log(`\n❌ ${problems.length} 处问题：`)
    for (const item of problems) {
      console.log(`  · ${item}`)
    }
  }
}

process.exit(problems.length === 0 ? 0 : 1)

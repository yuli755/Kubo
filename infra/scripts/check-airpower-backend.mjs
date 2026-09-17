#!/usr/bin/env node
/**
 * # 后端 airpower 足迹盘点 + 棘轮门禁
 *
 * ## 为什么需要它
 * 前端 `@airpower/web` 退场之所以能"一次删净 → 成批修复"，靠的是 `vue-tsc` 能在十几秒内
 * 把**所有缺口**列成一张表。后端没有这个闭环（本仓的沙箱环境既没有 JDK 也没有 Maven 仓库），
 * 于是"缺什么"只能靠人记 —— 这正是当初边删边补最慢的原因。
 *
 * 这个脚本就是后端版的"缺口清单生成器"：扫 Java / pom / yml，把 airpower 的用法按
 * **符号**（要自建什么）与**模块**（core / curd / api / redis / email / exception …）分类汇总。
 * 它不做任何修改，只回答两个问题：
 *   1. 现在还有多少 airpower？（文件数 / 符号数 / 引用次数 / 依赖 / 配置键）
 *   2. 要脱离它，必须自建或替换哪些东西？（逐符号用量表 = 优先级）
 *
 * ## 棘轮（`--check`）
 * 后端退场是长跑，途中不能"越删越多"。`--check` 与 BASELINE 比对：**只许降**。
 * 某一批真正删掉了若干符号后，把 BASELINE 改成新数字（这是有意的代码改动，会被 review）。
 *
 * ## 用法
 * ```
 * pnpm airpower:backend              # 人读盘点
 * pnpm airpower:backend -- --json    # 给脚本/CI 读
 * pnpm airpower:backend -- --check   # 棘轮：超过基线即 exit 1（CI 用）
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SERVER = path.join(ROOT, 'SPMS-Server')

/**
 * 基线（2026-09-18 实测）。**只许降**：每完成一批后端自建，把这里改成新数字。
 * 数字膨胀时 `--check` 会红，并告诉你该回去看哪一批把 airpower 用回来了。
 */
const BASELINE = { files: 180, symbols: 73, references: 657 }

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === 'target' || entry.name === 'node_modules' || entry.name === '.git') {
      continue
    }
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walk(full, files)
    }
    else {
      files.push(full)
    }
  }
  return files
}

/** airpower 的顶层模块（`cn.hamm.airpower.<module>`）→ 人话说明，用于分组展示 */
const MODULES = {
  api: 'HTTP 入口层：@Api 注解、ApiController 基类、ApiConfig',
  core: '内核工具与元数据注解：Json / TreeUtil / CollectionUtil / @Description / @Meta / IDictionary …',
  curd: 'CRUD 引擎：Curd 端点、@Search / @Extends 元数据、分页请求响应、Permission、ExportHelper',
  redis: 'Redis 帮助类',
  cookie: 'Cookie 配置与帮助类',
  email: '邮件帮助类',
  exception: '错误码（Errors）',
}

const javaFiles = fs.existsSync(path.join(SERVER, 'src')) ? walk(path.join(SERVER, 'src')).filter(f => f.endsWith('.java')) : []

const SYMBOL_RE = /cn\.hamm\.airpower\.[a-zA-Z0-9_.]*/g

/**
 * 符号级的足迹。
 *
 * 注意两处口径（都踩过）：
 * - 结尾的点要剥掉：`OperationLogSnapshot` 里有一句 `startsWith("cn.hamm.airpower.")` 的
 *   **字符串**（判断"这个类是框架类还是业务类"），它不是 import，但确实是耦合点，照样计数；
 * - 同一个符号在一行里出现多次只记多次（用量 = 改动面大小）。
 */
const symbolHits = new Map() // symbol → 次数
const moduleHits = new Map() // module → 次数
const fileHits = []

for (const file of javaFiles) {
  const source = fs.readFileSync(file, 'utf8')
  if (!source.includes('cn.hamm.airpower')) {
    continue
  }
  const hits = source.match(SYMBOL_RE) ?? []
  if (hits.length === 0) {
    continue
  }
  fileHits.push({ file: path.relative(ROOT, file).replaceAll('\\', '/'), count: hits.length })
  for (const hit of hits) {
    const symbol = hit.replace(/\.$/, '')
    symbolHits.set(symbol, (symbolHits.get(symbol) ?? 0) + 1)
    const module = symbol.split('.')[3] ?? 'core'
    moduleHits.set(module, (moduleHits.get(module) ?? 0) + 1)
  }
}

// ── 依赖与配置面（同样是耦合，不能只数 import）──
const pomPath = path.join(SERVER, 'pom.xml')
const pom = fs.existsSync(pomPath) ? fs.readFileSync(pomPath, 'utf8') : ''
const pomDependencies = [...pom.matchAll(/<artifactId>(airpower[\w-]*)<\/artifactId>/g)].map(m => m[1])
const parentAirpower = /<parent>[\s\S]*?<artifactId>airpower<\/artifactId>/.test(pom)

const ymlHits = []
for (const yml of walk(path.join(SERVER, 'src', 'main', 'resources')).filter(f => /\.ya?ml$/.test(f))) {
  const lines = fs.readFileSync(yml, 'utf8').split('\n')
  lines.forEach((line, index) => {
    if (/^\s*airpower\s*:/.test(line)) {
      ymlHits.push(`${path.relative(ROOT, yml).replaceAll('\\', '/')}:${index + 1}`)
    }
  })
}

const symbols = [...symbolHits.entries()]
  .map(([symbol, count]) => ({ symbol, count, module: symbol.split('.')[3] ?? 'core' }))
  .sort((a, b) => b.count - a.count || a.symbol.localeCompare(b.symbol))

const modules = [...moduleHits.entries()]
  .map(([module, count]) => ({ module, count, description: MODULES[module] ?? '（未归类）' }))
  .sort((a, b) => b.count - a.count)

const report = {
  generatedAt: new Date().toISOString(),
  totals: {
    javaFiles: javaFiles.length,
    airpowerFiles: fileHits.length,
    symbols: symbols.length,
    references: symbols.reduce((sum, s) => sum + s.count, 0),
  },
  baseline: BASELINE,
  parentPom: parentAirpower,
  dependencies: [...new Set(pomDependencies)],
  configKeys: ymlHits,
  modules,
  symbols,
  // 「要自建什么」的清单：按用量倒序，前几名就是最该先做的通用件
  buildList: symbols.filter(s => s.count >= 3).map(s => s.symbol),
}

// ── 棘轮 ──
if (process.argv.includes('--check')) {
  const grows = []
  if (report.totals.airpowerFiles > BASELINE.files) {
    grows.push(`触及 airpower 的文件 ${report.totals.airpowerFiles} > 基线 ${BASELINE.files}`)
  }
  if (report.totals.symbols > BASELINE.symbols) {
    grows.push(`airpower 符号 ${report.totals.symbols} > 基线 ${BASELINE.symbols}`)
  }
  if (report.totals.references > BASELINE.references) {
    grows.push(`引用次数 ${report.totals.references} > 基线 ${BASELINE.references}`)
  }
  if (grows.length) {
    console.error('\n✗ 后端 airpower 足迹比基线更大：\n')
    for (const line of grows) {
      console.error(`  · ${line}`)
    }
    console.error('\n退场是长跑，数字只许降。若这轮确实新增了用法，说明该自建的通用件还没建完 ——')
    console.error('先看 `pnpm airpower:backend` 的「要自建什么」清单，把缺口补上再往下走。\n')
    process.exit(1)
  }
  console.log(
    `✓ 后端 airpower 棘轮通过：文件 ${report.totals.airpowerFiles}/${BASELINE.files} · `
    + `符号 ${report.totals.symbols}/${BASELINE.symbols} · 引用 ${report.totals.references}/${BASELINE.references}（只许降）`,
  )
  process.exit(0)
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

console.log(`\n🔍 后端 airpower 足迹（${report.totals.airpowerFiles} / ${report.totals.javaFiles} 个 Java 文件，${report.totals.references} 处引用）\n`)
console.log(`  父 POM：${report.parentPom ? 'cn.hamm:airpower（决定 Spring Boot 版本 —— 最硬的耦合）' : '无'}`)
console.log(`  依赖：${report.dependencies.join(', ') || '无'}`)
console.log(`  配置键：${report.configKeys.join(', ') || '无'}`)

console.log('\n── 按模块 ──────────────────────────────────────────────')
for (const { module, count, description } of modules) {
  console.log(`  ${module.padEnd(10)} ${String(count).padStart(4)} 处  ${description}`)
}

console.log('\n── 要自建 / 替换什么（按用量倒序，前 20）────────────────')
for (const { symbol, count, module } of symbols.slice(0, 20)) {
  console.log(`  ${String(count).padStart(4)}×  ${symbol.replace('cn.hamm.airpower.', '').padEnd(42)} [${module}]`)
}

console.log('\n── 用量 ≥3 的符号（= 通用件，先做这些收益最大）──────────')
console.log(`  ${report.buildList.length} 个：${report.buildList.map(s => s.replace('cn.hamm.airpower.', '')).join(', ')}`)
console.log('\n  用法：`pnpm airpower:backend -- --json` 给脚本读；`--check` 是 CI 棘轮（只许降）。\n')

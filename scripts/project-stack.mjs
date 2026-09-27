#!/usr/bin/env node
/**
 * # 项目整体技术栈一览（前端 + 后端 + 数据 + 部署 + 工具链）
 *
 * `pnpm stack` 只看前端（package.json + node_modules + 前端构建产物）。
 * 这个脚本把**整个仓库**扫一遍，回答"这个项目到底跑在什么上"：
 *
 * | 维度 | 证据来源（都是真文件，不靠人写） |
 * | --- | --- |
 * | 前端运行期/构建期依赖 | `package.json` + `node_modules/<包>/package.json`（实际安装版本） |
 * | 后端依赖与版本 | `SPMS-Server/pom.xml`（自持：import Spring Boot BOM，无第三方父 POM） |
 * | JVM / Spring Boot 基线 | pom 的属性与 parent 声明 |
 * | 数据层 | Flyway 迁移脚本和/或 `db/` 目录、MyBatis-Plus、Hikari、JPA |
 * | 中间件 | `application*.yml`（MySQL / Redis / RabbitMQ / mail / actuator） |
 * | 部署 | `docker-compose.yml`、`Dockerfile`、`nginx.conf`、`deploy.sh` |
 * | CI | `.github/workflows/*.yml`（job 名 + 触发分支 + 关键命令） |
 * | 门禁 | `package.json` 的 scripts（哪些是硬门禁） |
 * | 退场进度 | 前端 `pnpm airpower:check` 口径 + 后端 `airpower:backend` 口径 |
 *
 * 用法：
 * ```
 * pnpm stack:project            # 人读
 * pnpm stack:project -- --json  # 机器可读
 * ```
 */
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { pomDependencyDeclarations } from '../infra/scripts/backend-pom.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..')
const SERVER = path.join(ROOT, 'SPMS-Server')

function read(p, fallback = '') {
  try {
    return fs.readFileSync(p, 'utf8')
  }
  catch {
    return fallback
  }
}
const exists = p => fs.existsSync(p)
const rel = p => path.relative(ROOT, p).replaceAll('\\', '/')

// ────────────────────────────── 前端 ──────────────────────────────
const pkg = JSON.parse(read(path.join(ROOT, 'package.json'), '{}'))
function installedVersion(name) {
  try {
    const p = path.join(ROOT, 'node_modules', name, 'package.json')
    if (fs.existsSync(p)) {
      return JSON.parse(fs.readFileSync(p, 'utf8')).version
    }
  }
  catch {}
  // pnpm 的隔离布局：从 .pnpm 目录里找该包的落点
  try {
    const store = path.join(ROOT, 'node_modules/.pnpm')
    for (const dir of fs.readdirSync(store)) {
      const marker = `${name.replace('/', '+').replace('@', '@')}@`
      if (dir.startsWith(marker)) {
        const v = dir.slice(marker.length).split('_')[0]
        const meta = path.join(store, dir, 'node_modules', name, 'package.json')
        if (exists(meta)) {
          return JSON.parse(fs.readFileSync(meta, 'utf8')).version
        }
        return v
      }
    }
  }
  catch {}
  return '（未安装）'
}

const frontend = {
  runtime: Object.entries(pkg.dependencies ?? {}).map(([name, declared]) => ({ name, declared, installed: installedVersion(name) })),
  dev: Object.entries(pkg.devDependencies ?? {}).map(([name, declared]) => ({ name, declared, installed: installedVersion(name) })),
  node: process.version,
  packageManager: pkg.packageManager ?? '（未声明）',
}

// ────────────────────────────── 后端 ──────────────────────────────
const pom = read(path.join(SERVER, 'pom.xml'))
function pomProp(name) {
  const m = pom.match(new RegExp(`<${name}>([^<]+)</${name}>`))
  return m ? m[1] : null
}
// `${xxx}` 形式的版本写的是属性名 —— 报告里要显示**解析后的值**，不然"技术栈"等于没说
function resolveVersion(raw) {
  const m = raw?.match(/^\$\{([^}]+)\}$/)
  return m ? (pomProp(m[1]) ?? raw) : raw
}
const parentArtifact = (() => {
  const m = pom.match(/<parent>[\s\S]*?<groupId>([^<]+)<\/groupId>\s*<artifactId>([^<]+)<\/artifactId>\s*<version>([^<]+)<\/version>/)
  return m ? `${m[1]}:${m[2]}:${m[3]}` : null
})()
const pomDeps = pomDependencyDeclarations(pom).map(dependency => ({
  ...dependency,
  version: dependency.version === null ? '（由 BOM 管理）' : resolveVersion(dependency.version),
  isBom: dependency.type === 'pom' && dependency.scope === 'import',
})).filter(dependency => dependency.artifactId)
const pomBoms = pomDeps.filter(dependency => dependency.isBom)
const pomRealDeps = pomDeps.filter(dependency => !dependency.managed)

const javaFiles = (() => {
  const out = []
  const walk = (dir) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      if (e.name === 'target')
        continue
      const full = path.join(dir, e.name)
      e.isDirectory() ? walk(full) : (e.name.endsWith('.java') && out.push(full))
    }
  }
  if (exists(path.join(SERVER, 'src')))
    walk(path.join(SERVER, 'src'))
  return out
})()

const migrations = (() => {
  const dirs = [path.join(SERVER, 'src/main/resources/db/migration')]
  const files = []
  for (const dir of dirs) {
    if (!exists(dir))
      continue
    const walk = d => fs.readdirSync(d, { withFileTypes: true }).forEach((e) => {
      const full = path.join(d, e.name)
      e.isDirectory() ? walk(full) : (/\.sql$/.test(e.name) && files.push(rel(full)))
    })
    walk(dir)
  }
  return files
})()

const ymlFiles = (() => {
  const dir = path.join(SERVER, 'src/main/resources')
  return exists(dir) ? fs.readdirSync(dir).filter(f => /\.ya?ml$/.test(f)) : []
})()

/** 从 yml 里抓"连了什么中间件"：只看键，不看值（值里可能有口令） */
function detectMiddleware() {
  const found = {}
  for (const f of ymlFiles) {
    const text = read(path.join(SERVER, 'src/main/resources', f))
    // JDBC URL 里的数据库品牌（`spring.datasource.url: jdbc:mysql://...`）
    for (const m of text.matchAll(/jdbc:([a-z0-9]+):/g)) {
      const brand = { mysql: 'MySQL', postgresql: 'PostgreSQL', h2: 'H2（测试）', mariadb: 'MariaDB' }[m[1]]
      if (brand) {
        found[brand] = [...(found[brand] ?? []), f]
      }
    }
    for (const [key, label] of [
      ['mysql', 'MySQL'],
      ['redis', 'Redis'],
      ['rabbitmq', 'RabbitMQ'],
      ['amqp', 'AMQP'],
      ['mail', 'SMTP(mail)'],
      ['datasource', 'DataSource'],
      ['flyway', 'Flyway'],
      ['kafka', 'Kafka'],
      ['rabbitmq', 'RabbitMQ'],
      ['influx', 'InfluxDB'],
      ['mqtt', 'MQTT'],
      ['elasticsearch', 'Elasticsearch'],
    ]) {
      if (new RegExp(`^\\s*${key}\\s*:`, 'im').test(text)) {
        found[label] = [...(found[label] ?? []), f]
      }
    }
  }
  return Object.entries(found).map(([name, files]) => ({ name, files: [...new Set(files)] }))
}

const backend = {
  parent: parentArtifact,
  java: pomProp('java.version') ?? pomProp('maven.compiler.release') ?? null,
  springBoot: pomBoms.find(b => b.artifactId === 'spring-boot-dependencies')?.version ?? resolveVersion(pomProp('spring-boot.version')) ?? '（未声明）',
  boms: pomBoms,
  mybatisPlus: pomProp('mybatis-plus.version'),
  dependencies: pomRealDeps,
  javaFiles: javaFiles.length,
  testFiles: javaFiles.filter(f => f.includes(`${path.sep}test${path.sep}`)).length,
  migrations: migrations.length,
  middleware: detectMiddleware(),
}

// ────────────────────────────── 部署 ──────────────────────────────
const compose = read(path.join(ROOT, 'docker-compose.yml'))
const composeServices = (() => {
  // 只取 `services:` 段（`volumes:` / `networks:` 下同样缩进的名字不是服务）
  const start = compose.indexOf('\nservices:')
  if (start < 0)
    return []
  const rest = compose.slice(start + 1)
  // 跳过 `services:` 这一行本身，再找下一个顶层键（volumes: / networks: …）
  const afterHeader = rest.slice(rest.indexOf('\n'))
  const end = afterHeader.search(/^[a-z]+:/m)
  const section = end > 0 ? afterHeader.slice(0, end) : afterHeader
  return [...section.matchAll(/^\s{2}([a-z][\w-]*):\s*$/gm)].map(m => m[1])
})()
const composeImages = [...compose.matchAll(/image:\s*([^\s#]+)/g)].map(m => m[1])

const deploy = {
  composeServices: [...new Set(composeServices)],
  composeImages: [...new Set(composeImages)],
  dockerfiles: ['Dockerfile', 'SPMS-Server/Dockerfile'].filter(f => exists(path.join(ROOT, f))),
  nginx: exists(path.join(ROOT, 'nginx.conf')) ? 'nginx.conf' : null,
  scripts: ['deploy.sh', 'SPMS-Server/deploy.sh'].filter(f => exists(path.join(ROOT, f))),
}

// ────────────────────────────── CI / 门禁 ──────────────────────────────
const ciFile = path.join(ROOT, '.github/workflows/ci.yml')
const ci = read(ciFile)
const ciJobs = (() => {
  // 只在 `jobs:` 段里找 2 空格缩进的任务名（否则会把 on: 的 push/pull_request 当成 job）
  const jobsSection = ci.slice(ci.indexOf('\njobs:'))
  const names = [...jobsSection.matchAll(/^\s{2}([a-z][\w-]*):\s*$/gm)].map(m => m[1])
  // 过滤掉 jobs 段里其它 2 空格缩进的非任务键（如 defaults/permissions）
  return names.filter(n => !['defaults', 'permissions', 'env', 'concurrency'].includes(n))
})()
const ciBranches = (() => {
  const m = ci.match(/branches:\s*\[([^\]]+)\]/)
  return m ? m[1].split(',').map(s => s.trim()) : []
})()

const gates = Object.entries(pkg.scripts ?? {})
  .filter(([name]) => /:check$|^lint:ci$|^typecheck$|^test$|^build$|^size$/.test(name))
  .map(([name, cmd]) => ({ name, cmd }))

// ────────────────────────────── 退场进度 ──────────────────────────────
// 后端 airpower 足迹不在这里重算——直接复用唯一真相源 `pnpm airpower:backend --json`（棘轮脚本），
// 免得两套口径漂移。脚本不可用（如缺 node_modules）时静默降级为 null。
const airpowerBackend = (() => {
  const script = path.join(ROOT, 'infra', 'scripts', 'check-airpower-backend.mjs')
  if (!fs.existsSync(script))
    return null
  try {
    const raw = execFileSync(process.execPath, [script, '--json'], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
    const data = JSON.parse(raw)
    return {
      totals: data.totals,
      baseline: data.baseline,
      dependencies: data.dependencies,
      exclusions: data.exclusions,
      configKeys: data.configKeys,
      parentPom: data.parentPom,
      modules: data.modules,
    }
  }
  catch {
    return null
  }
})()

const report = {
  generatedAt: new Date().toISOString(),
  frontend,
  backend,
  deploy,
  ci: { jobs: [...new Set(ciJobs)], branches: ciBranches },
  gates,
  airpowerBackend,
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(0)
}

const line = () => console.log('─'.repeat(78))

console.log(`\n📋 项目整体技术栈（生成于 ${report.generatedAt.slice(0, 19).replace('T', ' ')}）\n`)
line()
console.log('前端（kubo-web · 浏览器侧）')
line()
console.log(`  Node ${frontend.node} · 包管理器 ${frontend.packageManager}`)
console.log('  运行期依赖：')
for (const d of frontend.runtime)
  console.log(`    · ${d.name.padEnd(28)} ${String(d.installed).padEnd(12)} (声明 ${d.declared})`)
console.log('  构建/测试依赖：')
for (const d of frontend.dev)
  console.log(`    · ${d.name.padEnd(28)} ${String(d.installed).padEnd(12)} (声明 ${d.declared})`)

line()
console.log('后端（SPMS-Server · Spring Boot 应用）')
line()
console.log(`  Java ${backend.java ?? '17（由父 POM 决定，AirPower4J 基线要求 17+）'} · 父 POM ${backend.parent ?? '无（自持：spring-boot-dependencies BOM）'}`)
console.log(`  Spring Boot ${backend.springBoot}${backend.mybatisPlus ? ` · MyBatis-Plus ${backend.mybatisPlus}` : ''}`)
console.log(`  源码规模：${backend.javaFiles} 个 Java 文件（其中测试 ${backend.testFiles} 个）`)
console.log('  Maven 依赖：')
for (const b of backend.boms)
  console.log(`    · ${`${b.groupId}:${b.artifactId}`.padEnd(46)} ${b.version}  ← 依赖版本表（BOM import）`)
for (const d of backend.dependencies)
  console.log(`    · ${`${d.groupId}:${d.artifactId}`.padEnd(46)} ${d.version}`)
console.log(`  数据库迁移：${backend.migrations} 个 SQL`)
console.log('  中间件（由 application*.yml 的键 + jdbc URL 探测）：')
for (const m of backend.middleware)
  console.log(`    · ${m.name.padEnd(14)} ${m.files.join(', ')}`)

line()
console.log('部署 / 运行')
line()
console.log(`  docker-compose 服务：${deploy.composeServices.join(', ') || '（无）'}`)
console.log(`  镜像：${deploy.composeImages.join(', ') || '（无）'}`)
console.log(`  Dockerfile：${deploy.dockerfiles.join(', ') || '（无）'}`)
console.log(`  反向代理：${deploy.nginx ?? '（无）'} · 部署脚本：${deploy.scripts.join(', ') || '（无）'}`)

line()
console.log('CI / 门禁')
line()
console.log(`  GitHub Actions job：${report.ci.jobs.join(', ')}`)
console.log(`  触发分支：${report.ci.branches.join(', ')}`)
console.log(`  package.json 门禁别名：${gates.map(g => g.name).join(', ')}`)

if (airpowerBackend) {
  const t = airpowerBackend.totals
  line()
  console.log('airpower 退场进度（前端 = 0，已删净）')
  line()
  console.log(`  后端足迹：${t.airpowerFiles}/${t.javaFiles} 个 Java 文件 · ${t.symbols} 个符号 · ${t.references} 处引用（剥注释口径）`)
  console.log(`  依赖残留：${airpowerBackend.dependencies.join(' + ') || '（无）'}`)
  console.log(`  传递排除（POM 声明）：${airpowerBackend.exclusions.map(item => `${item.from} → ${item.groupId}:${item.artifactId}`).join(', ') || '无'}`)
  console.log(`  后端状态：${airpowerBackend.totals.references || airpowerBackend.dependencies.length || airpowerBackend.configKeys.length ? '未完成（棘轮通过不等于退场完成）' : '源码/声明归零，仍需完整依赖树与 Java 17 验收'}`)
  console.log(`  模块分布：${airpowerBackend.modules.map(m => `${m.module} ${m.count}`).join(' · ')}`)
  console.log('  下一步：`pnpm airpower:backend`（列出要自建什么）· 分批方案见 docs/后端airpower退场·可行性评估与分批建议.md')
}

console.log('\n  口径：全部来自真文件（package.json / node_modules / pom.xml / application*.yml /')
console.log('  docker-compose.yml / Dockerfile / nginx.conf / .github/workflows），不靠人写；')
console.log('  前端退场进度用 `pnpm airpower:check`，后端用 `pnpm airpower:backend`。\n')

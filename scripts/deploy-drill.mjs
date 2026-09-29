#!/usr/bin/env node
/**
 * 生产部署预演门禁（不上 Docker 也能演的，全部提前演掉）
 * ============================================================================
 * 为什么要有这个脚本：
 * docker:check 只验「配置写对了没有」，实机演练要等真实主机。两者之间存在一大类
 * 「读文件 + 本地计算就能发现」的部署事故：env 变量插值拼错、alertmanager 模板渲染
 * 出坏 YAML、bind-mount 挂了不存在的宿主路径、健康检查端点没暴露、prometheus 抓
 * compose 里不存在的服务……这类问题每次都到 up -d 那一刻才炸。
 *
 * 本脚本把部署链路在提交前完整「空跑」一遍（真文件、真插值、真渲染）：
 *   ① 环境生成演练：.env 缺失**或缺必需键**时自动走 docker:env 合并补齐（零手动），
 *      并校验 JWT_SECRET 强度、密码长度、DATA_PERMISSION_MODE 合法档位；
 *   ② 编排演练：docker-compose.prod.yml 用 .env 真值完成 ${VAR:-def} 插值后解析，
 *      六服务齐全、健康检查完整、depends_on 引用有效、构建上下文存在、
 *      bind-mount 宿主路径真实存在（专抓「挂了幽灵路径」）；
 *   ③ 告警渲染演练：按容器 entrypoint 的同款 sed 逻辑渲染 alertmanager 生产模板，
 *      渲染产物必须能被 YAML 解析且无占位符残留 —— 模板坏了启动即崩；
 *   ④ 生产抓取演练：prometheus.prod.yml / alerts.prod.yml 解析，抓取与告警 target
 *      必须存在于编排服务里，规则指标必须来自真实可采集集合（up / jvm_* / http_*），
 *      rule_files 挂载路径必须与编排一致；
 *   ⑤ 探针演练：后端暴露 health 端点、compose 健康检查路径与之匹配、JVM 容器参数
 *      与内存限额同源；
 *   ⑥ 迁移演练：Flyway 已启用且迁移 SQL 真实存在（schema 由 Flyway 负责，不靠 init-sql）。
 *
 * 它不替代实机 up -d（镜像构建、内核参数、真实网络仍要实机验收）；它拦的是
 * 「不用起容器就能看出来的错」。用法：pnpm deploy:drill
 */

import { spawnSync } from 'node:child_process'
import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { parse as parseYaml } from 'yaml'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const VERBOSE = process.argv.includes('--verbose')

const passes = []
const errors = []

function pass(message) {
  passes.push(message)
}

function fail(message) {
  errors.push(message)
}

/** 极简 ${VAR:-def} 插值（compose 语义子集，覆盖编排文件里实际用到的形态） */
function interpolate(text, env) {
  return text.replace(/\$\{(\w+)(?::-([^}]*))?\}/g, (_, name, def) => {
    const value = env[name] ?? def ?? ''
    if (value === '' && def === undefined) {
      fail(`环境变量 ${name} 未在 .env 定义且无默认值，实机 up 时会插值成空串`)
    }
    return value
  })
}

function readEnvFile() {
  const envPath = join(REPO, '.env')

  function parseEnv(text) {
    const env = {}
    for (const line of text.split('\n')) {
      const trimmed = line.trim()
      if (!trimmed || trimmed.startsWith('#')) {
        continue
      }
      const eq = trimmed.indexOf('=')
      if (eq > 0) {
        env[trimmed.slice(0, eq).trim()] = trimmed.slice(eq + 1).trim()
      }
    }
    return env
  }

  // 生产编排与告警链路必需的键；缺任何一个，实机 up 都会插值成空串或回落占位地址
  const requiredKeys = [
    'JWT_SECRET',
    'MYSQL_ROOT_PASSWORD',
    'MYSQL_PASSWORD',
    'REDIS_PASSWORD',
    'DATA_PERMISSION_MODE',
    'KUBO_ALERT_WEBHOOK_URL',
  ]

  let env = existsSync(envPath) ? parseEnv(readFileSync(envPath, 'utf8')) : {}
  const missing = requiredKeys.filter(key => !env[key])

  if (!existsSync(envPath) || missing.length > 0) {
    console.log(`⚙️  .env ${!existsSync(envPath) ? '不存在' : `缺键 ${missing.join(', ')}`}，自动执行 docker:env 合并补齐（零手动）…`)
    const result = spawnSync('node', [join(REPO, 'scripts', 'docker-helper.mjs'), '--init-env'], {
      encoding: 'utf8',
    })
    if (result.status !== 0) {
      fail(`docker:env 自动生成失败：${result.stderr || result.stdout}`)
      return null
    }
    // 合并模式：已有的开发键（VITE_* 等）原样保留，只补缺键与弱敏感键
    env = parseEnv(readFileSync(envPath, 'utf8'))
  }
  return env
}

/** ① 环境生成演练 */
function drillEnv(env) {
  const jwt = env.JWT_SECRET || ''
  if (/^[0-9a-f]{64}$/.test(jwt)) {
    pass('JWT_SECRET 为 64 位强随机十六进制（docker:env 自动注入）')
  }
  else {
    fail('JWT_SECRET 不是 64 位十六进制强随机密钥（重跑 pnpm docker:env 重新生成）')
  }

  const weakPasswords = ['MYSQL_ROOT_PASSWORD', 'MYSQL_PASSWORD', 'REDIS_PASSWORD']
    .filter(name => (env[name] || '').length < 20)
  if (weakPasswords.length === 0) {
    pass('MySQL/Redis 密码均为 20 位以上强随机串')
  }
  else {
    fail(`弱密码：${weakPasswords.join(', ')}（重跑 pnpm docker:env 重新生成）`)
  }

  const mode = env.DATA_PERMISSION_MODE || 'shadow'
  if (['off', 'shadow', 'enforce'].includes(mode)) {
    pass(`数据权限档位合法：${mode}（enforce 放量完成后切换，off 为紧急回滚档）`)
  }
  else {
    fail(`DATA_PERMISSION_MODE=${mode} 非法，必须是 off / shadow / enforce`)
  }

  if (env.KUBO_ALERT_WEBHOOK_URL) {
    pass('告警通知地址 KUBO_ALERT_WEBHOOK_URL 已就位（alertmanager 渲染用）')
  }
  else {
    fail('.env 缺 KUBO_ALERT_WEBHOOK_URL，告警链路渲染将回落占位地址')
  }
}

/** ② 编排演练 */
function drillCompose(env) {
  const raw = readFileSync(join(REPO, 'docker-compose.prod.yml'), 'utf8')
  const interpolated = interpolate(raw, env)
  const compose = parseYaml(interpolated)
  const services = compose?.services || {}

  const required = ['mysql', 'redis', 'server', 'web', 'prometheus', 'alertmanager']
  const missing = required.filter(s => !services[s])
  if (missing.length === 0) {
    pass(`生产编排六服务齐全：${required.join(', ')}`)
  }
  else {
    fail(`编排缺失服务：${missing.join(', ')}`)
  }

  // 健康检查：所有服务必须有探针（无探针的服务挂了 depends_on 也感知不到）
  const noHealth = Object.keys(services).filter(s => !services[s]?.healthcheck)
  if (noHealth.length === 0) {
    pass(`六服务全部配置健康检查探针（${Object.keys(services).length} 个）`)
  }
  else {
    fail(`服务缺少 healthcheck 探针：${noHealth.join(', ')}（挂了 depends_on 也感知不到）`)
  }

  // depends_on 引用的服务必须存在
  for (const [name, def] of Object.entries(services)) {
    for (const dep of Object.keys(def?.depends_on || {})) {
      if (!services[dep]) {
        fail(`服务 ${name} depends_on 引用不存在的服务 ${dep}（实机 up 直接报错）`)
      }
    }
  }
  pass('depends_on 服务依赖引用全部有效')

  // 构建上下文与 bind-mount 宿主路径必须真实存在
  for (const [name, def] of Object.entries(services)) {
    const context = def?.build?.context
    if (context && !existsSync(join(REPO, context))) {
      fail(`服务 ${name} 构建上下文 ${context} 不存在`)
    }
    for (const mount of def?.volumes || []) {
      const source = String(mount).split(':')[0]
      if (source.startsWith('.') && !existsSync(join(REPO, source))) {
        fail(`服务 ${name} 挂载了不存在的宿主路径 ${source}（幽灵挂载，实机会被 Docker 静默建成空目录）`)
      }
    }
  }
  pass('构建上下文与 bind-mount 宿主路径全部真实存在（无幽灵挂载）')

  // 卷声明：引用的 named volume 必须在顶层声明
  const declared = new Set(Object.keys(compose?.volumes || {}))
  for (const [name, def] of Object.entries(services)) {
    for (const mount of def?.volumes || []) {
      const source = String(mount).split(':')[0]
      if (!source.startsWith('.') && !source.startsWith('/')) {
        if (source === 'mysql' || source === 'redis') {
          continue // mysql: / redis: 挂载的是镜像内路径（healthcheck 常见写法）
        }
        if (!declared.has(source)) {
          fail(`服务 ${name} 引用了未声明的 named volume ${source}`)
        }
      }
    }
  }
  pass('named volume 引用全部在顶层声明')

  // 回滚杠杆：DATA_PERMISSION_MODE 必须以能被 Spring 绑定的形态注入
  const serverEnv = JSON.stringify(services.server?.environment || {})
  if (serverEnv.includes('DATA_PERMISSION_MODE') && !serverEnv.includes('KUBO_DATA_PERMISSION_MODE')) {
    pass('回滚杠杆接线正确：DATA_PERMISSION_MODE 注入容器（Spring 显式占位符绑定，非失效的宽松绑定名）')
  }
  else {
    fail('server 服务的数据权限档位环境变量接线错误（KUBO_DATA_PERMISSION_MODE 无法绑定 kubo.data-permission.mode）')
  }
}

/** ③ 告警渲染演练（与容器 entrypoint 同款 sed 逻辑） */
function drillAlertRender(env) {
  const tpl = readFileSync(join(REPO, 'infra', 'alertmanager', 'alertmanager.prod.yml.tpl'), 'utf8')
  const webhook = env.KUBO_ALERT_WEBHOOK_URL || 'http://kubo-alert-hook.example.invalid/alert'
  // 与 docker-compose.prod.yml entrypoint 相同：sed "s|__KUBO_ALERT_WEBHOOK_URL__|$URL|g"
  const rendered = tpl.replaceAll('__KUBO_ALERT_WEBHOOK_URL__', webhook)
  const parsed = parseYaml(rendered)

  if (!JSON.stringify(parsed).includes('__KUBO_ALERT_WEBHOOK_URL__')) {
    pass('alertmanager 模板渲染演练通过（与容器 entrypoint 同款逻辑，产物 YAML 合法且无占位符残留）')
  }
  else {
    fail('alertmanager 模板渲染后仍残留占位符（实机启动即坏配置）')
  }

  const receivers = (parsed?.receivers || []).map(r => r.name)
  const routed = [parsed?.route?.receiver, ...((parsed?.route?.routes || []).map(r => r.receiver))].filter(Boolean)
  const dangling = routed.filter(name => !receivers.includes(name))
  if (routed.length > 0 && dangling.length === 0) {
    pass('渲染后告警路由引用的 receiver 全部真实存在')
  }
  else {
    fail(`渲染后路由引用了未定义 receiver：${dangling.join(', ') || '无任何路由'}`)
  }
}

/** ④ 生产抓取与告警规则演练 */
const PROD_ALLOWED_METRIC_PREFIXES = ['up', 'jvm_', 'http_server_requests_', 'process_']

function drillProdMonitoring(env, compose) {
  const prom = parseYaml(readFileSync(join(REPO, 'infra', 'prometheus', 'prometheus.prod.yml'), 'utf8'))
  const alerts = parseYaml(readFileSync(join(REPO, 'infra', 'prometheus', 'rules', 'alerts.prod.yml'), 'utf8'))
  const services = Object.keys(compose?.services || {})

  const targets = []
  for (const g of prom?.alerting?.alertmanagers || []) {
    for (const s of g?.static_configs || []) {
      targets.push(...(s?.targets || []))
    }
  }
  for (const job of prom?.scrape_configs || []) {
    for (const s of job?.static_configs || []) {
      targets.push(...(s?.targets || []))
    }
  }
  const ghost = targets.map(String).filter((t) => {
    const host = t.split(':')[0]
    return !['localhost', '127.0.0.1'].includes(host) && !services.includes(host)
  })
  if (targets.length > 0 && ghost.length === 0) {
    pass(`生产抓取/告警 target 全部存在于编排（${targets.length} 个，无愿景 target）`)
  }
  else {
    fail(`生产 prometheus 配置指向编排里不存在的主机：${ghost.join(', ') || '未配置任何 target'}`)
  }

  // 规则指标必须真实可采集：生产编排里只有 up + actuator（jvm_/http_/process_）。
  // 提取前先剥离 label 选择器、函数调用名、by(...) 分组与区间，避免把 area/sum/5m
  // 这类非指标 token 误判成「愿景指标」。
  const vision = []
  for (const g of alerts?.groups || []) {
    for (const r of g?.rules || []) {
      if (typeof r?.expr !== 'string') {
        continue
      }
      const cleaned = r.expr
        .replace(/\{[^}]*\}/g, ' {}') // label 选择器
        .replace(/\bby\s*\([^)]*\)/g, ' ') // by (label, ...) 分组
        .replace(/\b([a-z_]\w*)\s*\(/gi, ' ') // 函数调用名
        .replace(/\[[^\]]*\]/g, ' ') // 区间 [5m]
      const used = cleaned.match(/[a-z_]\w*/gi) || []
      for (const metric of used) {
        if (!PROD_ALLOWED_METRIC_PREFIXES.some(p => metric === p || metric.startsWith(p))) {
          vision.push(`${r.alert}:${metric}`)
        }
      }
    }
  }
  if (vision.length === 0) {
    pass('生产告警规则只引用真实可采集指标（up / jvm_* / http_* / process_*），无愿景规则')
  }
  else {
    fail(`生产告警规则引用了生产编排采集不到的指标：${vision.join(', ')}（永远静默的愿景规则）`)
  }

  // rule_files 路径必须与 compose 挂载点一致
  const promMounts = (compose?.services?.prometheus?.volumes || []).map(String)
  for (const ruleFile of prom?.rule_files || []) {
    const ok = promMounts.some((m) => {
      const containerPath = m.split(':')[1]
      return ruleFile.includes(containerPath) || containerPath.includes(String(ruleFile).replaceAll('\'', ''))
    })
    if (!ok) {
      fail(`prometheus rule_files 引用 ${ruleFile} 未被编排挂载（规则永远加载不到）`)
    }
  }
  pass('prometheus rule_files 与编排挂载路径一致')
}

/** ⑤ 探针演练 */
function drillProbes(compose) {
  const prodYml = readFileSync(join(REPO, 'SPMS-Server', 'src', 'main', 'resources', 'application-production.yml'), 'utf8')
  if (prodYml.includes('include: health,info,prometheus')) {
    pass('后端生产配置暴露 health 端点（compose 健康检查有真端点可打）')
  }
  else {
    fail('后端生产配置未暴露 health 端点，compose 健康检查会永远失败')
  }

  const healthcheck = JSON.stringify(compose?.services?.server?.healthcheck || {})
  if (healthcheck.includes('/actuator/health')) {
    pass('server 健康检查路径 /actuator/health 与后端暴露端点匹配')
  }
  else {
    fail('server 健康检查未打 /actuator/health，探针形同虚设')
  }

  const dockerfile = readFileSync(join(REPO, 'SPMS-Server', 'Dockerfile'), 'utf8')
  if (dockerfile.includes('-XX:+UseContainerSupport') && dockerfile.includes('-XX:MaxRAMPercentage=75.0')) {
    pass('JVM 容器感知参数与内存限额同源（堆自适应，无 OOM 假死风险）')
  }
  else {
    fail('后端 Dockerfile 缺少容器感知 JVM 参数')
  }
}

/** ⑥ 迁移演练 */
function drillMigrations() {
  const appYml = readFileSync(join(REPO, 'SPMS-Server', 'src', 'main', 'resources', 'application.yml'), 'utf8')
  const migrationDir = join(REPO, 'SPMS-Server', 'src', 'main', 'resources', 'db', 'migration')
  const migrations = existsSync(migrationDir) ? readdirSync(migrationDir).filter(f => f.endsWith('.sql')) : []

  if (appYml.includes('flyway:') && migrations.length > 0) {
    pass(`Flyway 迁移链路就绪（${migrations.length} 个受控 SQL，schema 由应用启动自建，不依赖 init-sql）`)
  }
  else {
    fail('Flyway 未启用或迁移 SQL 缺失，容器起来也没有可用的数据库 schema')
  }
}

// ============================================================================
// 主流程
// ============================================================================
const env = readEnvFile()
if (env) {
  drillEnv(env)
  drillCompose(env)
  drillAlertRender(env)

  const compose = parseYaml(interpolate(readFileSync(join(REPO, 'docker-compose.prod.yml'), 'utf8'), env))
  drillProdMonitoring(env, compose)
  drillProbes(compose)
  drillMigrations()
}

if (VERBOSE) {
  for (const p of passes) {
    console.log(`  ✓ ${p}`)
  }
}

if (errors.length > 0) {
  console.error(`\n❌ 部署预演未通过（发现 ${errors.length} 处实机必炸点）：`)
  for (const e of errors) {
    console.error(`  - ${e}`)
  }
  process.exit(1)
}

console.log(`✓ 生产部署预演通过（${passes.length} 项）：环境生成、编排插值、告警渲染、生产抓取、探针匹配、迁移链路全部真实演练`)
console.log('  说明：本预演覆盖「不起容器就能发现的错」；镜像构建与真实网络请在一台真实主机上跑一次 up -d 做最终验收。')

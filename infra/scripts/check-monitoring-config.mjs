#!/usr/bin/env node
/**
 * 监控/告警配置静态完整性门禁
 * ============================================================================
 * 为什么要有这个脚本：infra/prometheus + infra/alertmanager + docker-compose 的监控栈
 * 曾长期处于「愿景态半成品」——prometheus.yml 用 K8s 服务发现（实际是 docker-compose）、
 * alertmanagers.targets 为空（告警触发也无处投递）、alerts.yml 里大量规则引用代码从未
 * 暴露的指标（rabbitmq_*、ws_*、inventory_*、order_*、auth_login_failed_total、主从延迟）。
 * 这类问题「读文件就能看出来」，却因为没有静态门禁而一直没被拦住，直到生产第一次
 * 告警静默/误报才发现。
 *
 * 本脚本把「不依赖真实 Prometheus/Alertmanager 就能判定的确定性错误」提前到提交前：
 *   ① 四个配置文件（prometheus / alerts / alertmanager / docker-compose）YAML 语法合法；
 *   ② prometheus.yml 的 alertmanagers.targets 非空（告警必须有处可送）；
 *   ③ 所有 static target 的主机名必须真实存在于 docker-compose 的 services（否则永远 down）；
 *   ④ alerts.yml 的 expr 不得引用「代码从未暴露」的愿景指标（组件没接就永远静默/误报）。
 *
 * 它**不替代**真实环境的运行时验证（Prometheus 是否真抓到指标、告警通道是否真送达
 * 仍要靠 docker compose up 后实测）；它拦的是「不用起服务、读文件就能看出来的错」。
 *
 * 防空转：脚本内嵌自检（见 selfCheck），坏样本逐条判据反证，门禁失灵时 CI 立刻红。
 *
 * 用法：node infra/scripts/check-monitoring-config.mjs [--verbose]
 */
import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse as parseYaml } from 'yaml'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const VERBOSE = process.argv.includes('--verbose')

// 允许脱离 compose services 单独解析的 target 主机（本机回环 / Docker Desktop 宿主别名）
const SPECIAL_HOSTS = new Set(['localhost', '127.0.0.1', 'host.docker.internal'])

// 愿景指标黑名单：代码从未埋点、Prometheus 也抓不到的指标。
// 若 alerts.yml 的 expr 引用它们，说明「愿景态规则」又被加回来了 —— 组件没接就永远
// 静默（指标不存在）或误报，比没有告警更糟。新增真实埋点指标时，从这里移除对应项。
const VISION_METRICS = [
  'rabbitmq_', // RabbitMQ 组件代码未接、无 exporter
  'ws_reconnect_total', // WebSocket 重连指标未埋
  'ws_message_latency_seconds', // WebSocket 消息延迟指标未埋
  'inventory_stock_quantity', // 库存指标未埋
  'inventory_stock_warning_threshold', // 库存预警阈值指标未埋
  'order_processing_duration_seconds', // 工单处理时长指标未埋
  'auth_login_failed_total', // 登录失败指标未埋
  'mysql_slave_status_seconds_behind_master', // 单节点无主从
]

/**
 * 对一份监控配置做静态完整性检查，返回错误描述数组（空 = 通过）。
 * cfg: { prometheus: '<yml>', alerts: '<yml>', alertmanager: '<yml>', compose: '<yml>' }
 * 主流程与自检共用此入口，避免两边行为漂移。
 */
function checkMonitoringConfig(cfg) {
  const errors = []
  const fail = (msg) => errors.push(msg)

  // ① YAML 语法合法性
  const parsed = {}
  for (const [k, text] of Object.entries(cfg)) {
    try {
      parsed[k] = parseYaml(text) ?? {}
    } catch (e) {
      fail(`${k}: YAML 语法错误（${e.message}）`)
      parsed[k] = null
    }
  }

  const prom = parsed.prometheus
  const compose = parsed.compose
  const services = compose && compose.services ? Object.keys(compose.services) : []

  // ② alertmanagers.targets 非空（告警必须有处可送）
  const amTargets = (prom && prom.alerting && prom.alerting.alertmanagers
    ? prom.alerting.alertmanagers
    : []
  ).flatMap((g) => (g && g.static_configs ? g.static_configs.flatMap((s) => (s && s.targets) || []) : []))
  if (prom && amTargets.length === 0) {
    fail('prometheus.yml: alertmanagers.targets 为空，告警触发也无处投递（应指向 compose 里的 alertmanager:9093）')
  }

  // ③ 所有 static target 的主机名必须在 compose services（或特殊主机）里
  const targets = []
  for (const t of amTargets) targets.push({ where: 'alertmanager', t })
  for (const job of (prom && prom.scrape_configs) || []) {
    for (const s of (job && job.static_configs) || []) {
      for (const t of (s && s.targets) || []) {
        targets.push({ where: `scrape job ${job.job_name}`, t })
      }
    }
  }
  for (const { where, t } of targets) {
    const host = String(t).split(':')[0]
    if (SPECIAL_HOSTS.has(host)) continue
    if (!services.includes(host)) {
      fail(`prometheus.yml: ${where} 的 target ${t} 指向 docker-compose 里不存在的主机 ${host}（target 会永远 down）`)
    }
  }

  // ④ alerts.yml 的 expr 不引用愿景指标
  for (const g of (parsed.alerts && parsed.alerts.groups) || []) {
    for (const r of (g && g.rules) || []) {
      if (typeof (r && r.expr) !== 'string') continue
      for (const v of VISION_METRICS) {
        if (r.expr.includes(v)) {
          fail(`alerts.yml: 规则 ${r.alert} 的 expr 引用未暴露指标 \`${v}\`（组件未接/指标未埋，告警永远静默或误报）`)
        }
      }
    }
  }

  // ⑤ alertmanager 的 route/sub-route 引用的 receiver 必须真实存在（引用未定义的 receiver 启动即炸）
  const am = parsed.alertmanager
  if (am && am.route) {
    const receiverNames = new Set(((am.receivers || [])).map(r => r.name).filter(Boolean))
    const referenced = [am.route.receiver, ...((am.route.routes || []).map(r => r.receiver))].filter(Boolean)
    for (const name of referenced) {
      if (!receiverNames.has(name)) {
        fail(`alertmanager.yml: route 引用未定义的 receiver \`${name}\`（Alertmanager 启动即失败，告警全链路瘫痪）`)
      }
    }

    // ⑥ 每个 receiver 必须至少配置一种投递通道（webhook_configs / email_configs），否则告警送进去就丢
    for (const r of (am.receivers || [])) {
      const hasChannel = ((r.webhook_configs || []).length > 0) || ((r.email_configs || []).length > 0)
      if (!hasChannel) {
        fail(`alertmanager.yml: receiver \`${r.name}\` 没有配置任何投递通道（webhook_configs / email_configs），告警送进去就丢`)
      }
    }
  }

  return errors
}

// ============================================================================
// 门禁自检：证明每条判据真的能咬人，且不误伤合法配置
// ============================================================================
function selfCheck() {
  const problems = []
  const good = () => ({
    prometheus: [
      'alerting:',
      '  alertmanagers:',
      '    - static_configs:',
      '        - targets: ["alertmanager:9093"]',
      'scrape_configs:',
      '  - job_name: redis',
      '    static_configs:',
      '      - targets: ["redis-exporter:9121"]',
    ].join('\n'),
    alerts: [
      'groups:',
      '  - name: g',
      '    rules:',
      '      - alert: TargetDown',
      '        expr: up == 0',
      '        labels: { severity: critical }',
      '        annotations: { summary: x }',
    ].join('\n'),
    alertmanager: [
      'route:',
      '  receiver: default',
      '  routes:',
      '    - matchers: [severity="critical"]',
      '      receiver: default',
      'receivers:',
      '  - name: default',
      '    webhook_configs:',
      '      - url: "http://hook.example.invalid/alert"',
    ].join('\n'),
    compose: 'services:\n  redis-exporter:\n    image: x\n  alertmanager:\n    image: y\n',
  })
  const cases = [
    {
      name: 'alertmanagers.targets 为空（告警无处可送）',
      cfg: (() => {
        const c = good()
        c.prometheus = c.prometheus.replace('        - targets: ["alertmanager:9093"]', '        - targets: []')
        return c
      })(),
      expect: /alertmanagers\.targets 为空/,
    },
    {
      name: 'scrape target 指向 compose 不存在的主机',
      cfg: (() => {
        const c = good()
        c.prometheus = c.prometheus.replace('redis-exporter:9121', 'ghost-service:9090')
        return c
      })(),
      expect: /不存在的主机 ghost-service/,
    },
    {
      name: 'alerts 规则引用未暴露的 rabbitmq_ 指标',
      cfg: (() => {
        const c = good()
        c.alerts = 'groups:\n  - name: g\n    rules:\n      - alert: MQBacklog\n        expr: rabbitmq_queue_messages_ready > 10000\n        labels: { severity: warning }\n        annotations: { summary: x }\n'
        return c
      })(),
      expect: /未暴露指标 `rabbitmq_`/,
    },
    {
      name: '配置文件 YAML 语法错误',
      cfg: (() => {
        const c = good()
        c.alertmanager = 'route: [unclosed\n  receiver: default\n'
        return c
      })(),
      expect: /YAML 语法错误/,
    },
    {
      name: 'route 引用未定义的 receiver（alertmanager 启动即炸）',
      cfg: (() => {
        const c = good()
        c.alertmanager = c.alertmanager.replace('      receiver: default\nreceivers:', '      receiver: ghost\nreceivers:')
        return c
      })(),
      expect: /引用未定义的 receiver `ghost`/,
    },
    {
      name: 'receiver 没有任何投递通道（告警送进去就丢）',
      cfg: (() => {
        const c = good()
        c.alertmanager = c.alertmanager.replace('    webhook_configs:\n      - url: "http://hook.example.invalid/alert"', '')
        return c
      })(),
      expect: /没有配置任何投递通道/,
    },
    {
      name: '合法：targets 非空 + 主机存在 + 无愿景指标',
      cfg: good(),
      expect: null,
    },
  ]
  let checked = 0
  for (const c of cases) {
    checked++
    const errs = checkMonitoringConfig(c.cfg)
    if (c.expect === null) {
      if (errs.length > 0) {
        problems.push(`自检：「${c.name}」被误判为错误（${errs.join(' / ')}）`)
      }
      continue
    }
    if (!errs.some((e) => c.expect.test(e))) {
      problems.push(`自检：「${c.name}」没有被检出（期望匹配 ${c.expect}）`)
    }
  }
  return { problems, checked }
}

// ============================================================================
// 主流程
// ============================================================================
const cfg = {
  prometheus: readFileSync(join(REPO, 'infra', 'prometheus', 'prometheus.yml'), 'utf-8'),
  alerts: readFileSync(join(REPO, 'infra', 'prometheus', 'rules', 'alerts.yml'), 'utf-8'),
  alertmanager: readFileSync(join(REPO, 'infra', 'alertmanager', 'alertmanager.yml'), 'utf-8'),
  compose: readFileSync(join(REPO, 'docker-compose.yml'), 'utf-8'),
}

const allErrors = checkMonitoringConfig(cfg)
if (VERBOSE) {
  for (const [k, text] of Object.entries(cfg)) {
    const parsed = (() => { try { return parseYaml(text) } catch { return null } })()
    if (k === 'prometheus' && parsed) {
      const jobs = (parsed.scrape_configs || []).map((j) => j.job_name)
      console.log(`  prometheus.yml: ${jobs.length} 个 scrape job（${jobs.join(', ')}）`)
    }
    if (k === 'alerts' && parsed) {
      const n = (parsed.groups || []).reduce((s, g) => s + (g.rules || []).length, 0)
      console.log(`  alerts.yml: ${n} 条启用的告警规则`)
    }
  }
}

const { problems, checked } = selfCheck()
if (problems.length > 0) {
  console.error('\n✗ check-monitoring-config 自检失败：门禁本身不可信，先修门禁再谈监控配置')
  problems.forEach((p) => console.error(`  ${p}`))
  process.exit(1)
}

if (allErrors.length > 0) {
  console.error(`✗ 监控/告警配置静态完整性 ${allErrors.length} 处错误：`)
  allErrors.forEach((e) => console.error(`  · ${e}`))
  process.exit(1)
}

console.log(`✓ 监控/告警配置静态检查通过：4 个文件 YAML 合法、alertmanager 有处可送、路由 receiver 真实存在且有投递通道、所有 target 真实存在、无愿景指标；门禁自检 ${checked} 例通过`)

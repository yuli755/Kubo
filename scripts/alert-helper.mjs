#!/usr/bin/env node
/**
 * 告警通知链路一键实测助手（真实触达验收）
 * ============================================================================
 * 为什么要有这个脚本：
 * monitoring:check 只能证明「配置读起来是对的」，证明不了「群里真收到了」。
 * 通知链路的最后一公里只能实测：一条命令把测试告警推进链路，值班人员在
 * 钉钉/企微/飞书群里亲眼看到，才算验收通过 —— 不需要手搓 curl JSON。
 *
 * 用法：
 *   pnpm alert:test                      # 直发 webhook（地址取 --url > 环境变量 > .env）
 *   pnpm alert:test -- --url <webhook>   # 显式指定 webhook 地址
 *   pnpm alert:test -- --am http://localhost:9093
 *                                        # 推进本地 alertmanager（/api/v2/alerts），
 *                                        # 由它按路由 fan-out 到真实 webhook —— 全链路实测
 *
 * 退出码：0 = HTTP 投递成功（收到方如何展示由对方决定）；1 = 投递失败。
 */

import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '..')

function readArgValue(flag) {
  const idx = process.argv.indexOf(flag)
  return idx >= 0 ? process.argv[idx + 1] : undefined
}

function readEnvFileWebhook() {
  const envPath = join(REPO, '.env')
  if (!existsSync(envPath)) {
    return undefined
  }
  const line = readFileSync(envPath, 'utf8')
    .split('\n')
    .find(l => l.trim().startsWith('KUBO_ALERT_WEBHOOK_URL='))
  const value = line?.split('=').slice(1).join('=').trim()
  return value || undefined
}

/** Alertmanager v2 API 的 alerts 载荷：一条 firing 的链路测试告警 */
function buildTestAlert() {
  const startsAt = new Date().toISOString()
  return [{
    labels: {
      alertname: 'KuboAlertChainTest',
      severity: 'info',
      team: 'platform',
    },
    annotations: {
      summary: 'Kubo 告警通知链路测试',
      description: '这是一条由 pnpm alert:test 发出的链路验证告警：看到它说明 webhook 通知链路已通。收到后无需处理。',
    },
    startsAt,
    endsAt: new Date(Date.now() + 5 * 60 * 1000).toISOString(),
    generatorURL: 'https://kubo.local/alert-test',
  }]
}

async function postJson(url, payload) {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(payload),
    signal: AbortSignal.timeout(10_000),
  })
  return { status: response.status, ok: response.ok }
}

async function main() {
  const amBase = readArgValue('--am')
  const webhook = readArgValue('--url') || process.env.KUBO_ALERT_WEBHOOK_URL || readEnvFileWebhook()

  let target
  let mode
  if (amBase) {
    target = `${amBase.replace(/\/$/, '')}/api/v2/alerts`
    mode = `经 alertmanager (${amBase}) fan-out 到其配置的真实 webhook`
  }
  else if (webhook && !webhook.includes('example.invalid')) {
    target = webhook
    mode = '直发 webhook'
  }
  else {
    console.error('❌ 未找到可用的告警 webhook 地址。请按以下任一方式提供：')
    console.error('   1. 在 .env 配置 KUBO_ALERT_WEBHOOK_URL=（推荐，docker:env 生成的 .env 即可补这一行）')
    console.error('   2. 或执行 pnpm alert:test -- --url <你的钉钉/企微/自研网关 webhook>')
    console.error('   3. 或本地起了 alertmanager 后执行 pnpm alert:test -- --am http://localhost:9093 全链路实测')
    process.exit(1)
  }

  const payload = buildTestAlert()
  console.log(`📤 投递模式：${mode}`)
  console.log(`📤 目标地址：${target}`)
  console.log('📤 测试告警：KuboAlertChainTest (severity=info, firing, 5 分钟后自动 resolve)')
  try {
    const { status, ok } = await postJson(target, payload)
    if (!ok) {
      console.error(`\n❌ 投递失败：HTTP ${status}。检查 webhook 地址是否正确、网关是否可达。`)
      process.exit(1)
    }
    console.log(`\n✅ HTTP 投递成功（状态码 ${status}）。`)
    console.log('   注意：HTTP 200 只代表对方收到了请求 —— 请到钉钉/企微/飞书群里确认真实看到这条测试告警；')
    console.log('   群里没收到时，检查机器人适配器（如 prometheus-webhook-dingtalk）与关键词过滤规则。')
  }
  catch (error) {
    console.error(`\n❌ 投递异常：${error.cause?.code || error.name || 'unknown'} ${error.cause?.message || error.message || ''}`)
    console.error('   webhook 地址不可达：确认网关已启动、地址拼对、防火墙放行。')
    process.exit(1)
  }
}

main()

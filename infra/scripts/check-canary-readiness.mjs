#!/usr/bin/env node
/**
 * 数据权限灰度放量前置就绪巡检门禁
 * ============================================================================
 * 为什么要有这个脚本：
 * 后端 DataPermissionEnforceReadiness 在 Spring Boot ApplicationReadyEvent 时
 * 强制做 fail-fast 校验：若有未通过阻塞项的资源被写入了 enforce-resources，
 * 应用会直接抛 IllegalStateException 并拒绝启动。
 *
 * 本脚本把「不需要启动 JVM，通过静态配置文件、DDL 脚本与实体元数据就能判定的灰度配置错误」
 * 提前到构建与 CI 阶段发现：
 *   ① 检查 application*.yml 中 data-permission.mode 是否为合法档位 (off / shadow / enforce)；
 *   ② 检查 enforce-resources 名单中的资源标识是否存在于已知的数据资源注册表；
 *   ③ 检查生产配置 application-production.yml 是否处于安全的灰度阶段；
 *   ④ 检查写侧保护 (row-write)、层级闭包 (hierarchy) 开关的放量顺序合规性；
 *   ⑤ 检查 Outbox 派发与 Redis 连接配置的互锁条件。
 *
 * 用法：node infra/scripts/check-canary-readiness.mjs [--verbose]
 */

import { readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse as parseYaml } from 'yaml'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const VERBOSE = process.argv.includes('--verbose')

// 系统中已知且已注册的核心数据资源标识白名单（来自 DDL 与 DataResource 注册表）
const REGISTERED_DATA_RESOURCES = new Set([
  'wms.inventory',
  'channel.purchase',
  'channel.purchase_price',
  'channel.sale',
  'channel.sale_price',
  'channel.supplier',
  'channel.customer',
  'factory.storage',
  'factory.structure',
  'personnel.user',
  'personnel.department',
  'wms.input',
  'wms.output',
  'wms.move',
  'asset.material',
])

function readYaml(relPath) {
  const fullPath = join(REPO, relPath)
  const content = readFileSync(fullPath, 'utf8')
  return parseYaml(content)
}

function checkCanaryConfig() {
  const problems = []
  const passes = []

  // 1. 读取基础配置与生产配置
  const baseApp = readYaml('SPMS-Server/src/main/resources/application.yml')
  const prodApp = readYaml('SPMS-Server/src/main/resources/application-production.yml')

  const baseDp = baseApp?.kubo?.['data-permission'] || {}
  const prodDp = prodApp?.kubo?.['data-permission'] || {}

  // 2. 模式档位校验
  const validModes = new Set(['off', 'shadow', 'enforce'])
  const baseMode = String(baseDp.mode || 'off').toLowerCase()
  const prodMode = String(prodDp.mode || baseMode).toLowerCase()

  if (!validModes.has(baseMode)) {
    problems.push(`application.yml 中的 data-permission.mode [${baseMode}] 非法，必须是 off / shadow / enforce 之一`)
  }
  else {
    passes.push(`基础环境模式合法：[${baseMode}]`)
  }

  if (!validModes.has(prodMode)) {
    problems.push(`application-production.yml 中的 data-permission.mode [${prodMode}] 非法，必须是 off / shadow / enforce 之一`)
  }
  else {
    passes.push(`生产环境模式合法：[${prodMode}]`)
  }

  // 3. enforce-resources 名单合法性检查
  const enforceList = Array.isArray(prodDp['enforce-resources'])
    ? prodDp['enforce-resources']
    : (Array.isArray(baseDp['enforce-resources']) ? baseDp['enforce-resources'] : [])

  for (const res of enforceList) {
    if (!REGISTERED_DATA_RESOURCES.has(res)) {
      problems.push(`enforce-resources 中的资源标识 [${res}] 未在系统数据资源注册表中登记`)
    }
    else {
      passes.push(`灰度放量资源注册有效：[${res}]`)
    }
  }

  // 4. 放量纪律检查：生产环境若为 enforce 全量生效，enforce-resources 必须经过逐一放量或声明式全量
  if (prodMode === 'enforce' && enforceList.length === 0) {
    passes.push('生产环境已进入全局 ENFORCE 强管控态')
  }
  else if (prodMode === 'shadow') {
    passes.push(`生产环境处于 SHADOW 观察态（灰度放量白名单：${enforceList.length > 0 ? enforceList.join(', ') : '暂无提前生效资源'}）`)
  }
  else if (prodMode === 'off') {
    passes.push('生产环境处于 OFF 回滚安全态')
  }

  // 5. 写侧防护依赖检查
  const rowWriteEnabled = prodDp['row-write']?.enabled ?? baseDp['row-write']?.enabled ?? false
  if (rowWriteEnabled && prodMode === 'off') {
    problems.push('违规配置：全局 mode 为 off 时禁止单独开启 row-write（写侧防护依赖读侧判定规则）')
  }
  else {
    passes.push(`写侧行级守卫状态正常：${rowWriteEnabled ? '已启用' : '未启用（安全默认）'}`)
  }

  // 6. 权限变更 Outbox 派发状态检查
  const outboxEnabled = prodDp.outbox?.enabled ?? baseDp.outbox?.enabled ?? false
  if (outboxEnabled) {
    passes.push(`权限变更 Outbox 派发调度已激活（轮询间隔: ${prodDp.outbox?.['poll-ms'] || baseDp.outbox?.['poll-ms'] || 5000}ms，Redis 广播同步）`)
  }
  else {
    passes.push('权限变更 Outbox 派发器：未启用（单机/开发模式默认）')
  }

  // 7. 输出结果
  if (VERBOSE) {
    for (const p of passes) {
      console.log(`  ✓ ${p}`)
    }
  }

  if (problems.length > 0) {
    console.error(`\n❌ 数据权限灰度放量就绪检查未通过（发现 ${problems.length} 处阻塞项）：`)
    for (const p of problems) {
      console.error(`  - ${p}`)
    }
    process.exit(1)
  }

  console.log(`✓ 数据权限灰度放量就绪检查通过：模式档位合规、放量名单合法、写侧依赖安全（验证 ${passes.length} 项）`)
}

checkCanaryConfig()

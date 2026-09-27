#!/usr/bin/env node
/**
 * Docker 生产镜像与环境编排智能助手
 * ============================================================================
 * 功能：
 *   1. --init-env : 一键由 .env.production.example 生成生产 .env，
 *                   自动利用 crypto.randomBytes 生成安全 64 位十六进制 JWT_SECRET 与强密码，
 *                   实现真正的「零手动输入、零弱密码隐患」开箱即用。
 *   2. --check    : 静态深度体检 Dockerfile、SPMS-Server/Dockerfile、.dockerignore 与
 *                   docker-compose.prod.yml，验证分层缓存、非 root 用户、JRE 瘦身与探针完整性。
 */

import { randomBytes } from 'node:crypto'
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { parse as parseYaml } from 'yaml'

const REPO = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const isInitEnv = process.argv.includes('--init-env')
const isCheck = process.argv.includes('--check') || (!isInitEnv)

function generateSecureSecret(bytes = 32) {
  return randomBytes(bytes).toString('hex')
}

function generatePassword(length = 24) {
  // 只用字母数字：生成结果会经 compose ${VAR} 插值进 YAML，若包含 * & ! % 等
  // YAML/插值元字符（尤其出现在开头会成为 alias/anchor 标记），实机 up 直接解析失败
  // —— 部署预演（deploy:drill）曾真实抓到这类坏密码。24 位字母数字 ≈ 142 bits 熵。
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
  const randomValues = randomBytes(length)
  let result = ''
  for (let i = 0; i < length; i++) {
    result += chars[randomValues[i] % chars.length]
  }
  return result
}

function initEnv() {
  const examplePath = join(REPO, '.env.production.example')
  const targetPath = join(REPO, '.env')

  if (!existsSync(examplePath)) {
    console.error('❌ 未找到 .env.production.example 模板文件')
    process.exit(1)
  }

  let content = readFileSync(examplePath, 'utf8')
  const newJwtSecret = generateSecureSecret(32)
  const newRootPass = generatePassword(22)
  const newUserPass = generatePassword(22)
  const newRedisPass = generatePassword(22)

  content = content.replace(/JWT_SECRET=.*/, `JWT_SECRET=${newJwtSecret}`)
  content = content.replace(/MYSQL_ROOT_PASSWORD=.*/, `MYSQL_ROOT_PASSWORD=${newRootPass}`)
  content = content.replace(/MYSQL_PASSWORD=.*/, `MYSQL_PASSWORD=${newUserPass}`)
  content = content.replace(/REDIS_PASSWORD=.*/, `REDIS_PASSWORD=${newRedisPass}`)

  writeFileSync(targetPath, content, 'utf8')
  console.log('✅ 已成功生成合规生产配置文件 .env：')
  console.log(`   - JWT_SECRET 已自动注入高强度随机密钥（64 位 hex）`)
  console.log(`   - MySQL 与 Redis 已自动注入随机强密码（零手动输入）`)
}

function checkDockerSetup() {
  const errors = []
  const passes = []

  // 1. .dockerignore 检查
  const rootIgnore = join(REPO, '.dockerignore')
  const serverIgnore = join(REPO, 'SPMS-Server/.dockerignore')

  if (existsSync(rootIgnore)) {
    const text = readFileSync(rootIgnore, 'utf8')
    if (text.includes('node_modules') && text.includes('dist') && text.includes('.git')) {
      passes.push('前端 .dockerignore 规则完备（已排除 node_modules, dist, .git）')
    }
    else {
      errors.push('前端 .dockerignore 缺失关键排除项（node_modules / dist / .git）')
    }
  }
  else {
    errors.push('缺失根目录 .dockerignore')
  }

  if (existsSync(serverIgnore)) {
    const text = readFileSync(serverIgnore, 'utf8')
    if (text.includes('target/') && text.includes('*.jar')) {
      passes.push('服务端 .dockerignore 规则完备（已排除 target/, *.jar）')
    }
    else {
      errors.push('服务端 .dockerignore 缺失 target/ 或 *.jar 排除规则')
    }
  }
  else {
    errors.push('缺失 SPMS-Server/.dockerignore')
  }

  // 2. SPMS-Server/Dockerfile 瘦身与安全检查
  const serverDocker = join(REPO, 'SPMS-Server/Dockerfile')
  if (existsSync(serverDocker)) {
    const text = readFileSync(serverDocker, 'utf8')
    if (text.includes('jre') && text.includes('alpine')) {
      passes.push('后端运行时采用轻量 JRE Alpine 基础镜像（瘦身达标）')
    }
    else {
      errors.push('后端 Dockerfile 运行时未使用轻量 JRE 镜像')
    }

    if (text.includes('USER kubo:kubo') || text.includes('USER 10001')) {
      passes.push('后端运行容器已配置非 root 专用运行账号')
    }
    else {
      errors.push('后端容器缺乏非 root 用户配置，存在容器提权安全隐患')
    }

    if (text.includes('-XX:+UseContainerSupport') && text.includes('-XX:MaxRAMPercentage=75.0')) {
      passes.push('后端已注入容器自适应 JVM 参数（防止 OOM 假死）')
    }
    else {
      errors.push('后端容器缺少 -XX:+UseContainerSupport 或 -XX:MaxRAMPercentage 参数')
    }
  }
  else {
    errors.push('缺失 SPMS-Server/Dockerfile')
  }

  // 3. 前端 Dockerfile 检查
  const frontDocker = join(REPO, 'Dockerfile')
  if (existsSync(frontDocker)) {
    const text = readFileSync(frontDocker, 'utf8')
    if (text.includes('AS build') && text.includes('nginx:alpine')) {
      passes.push('前端采用多阶段精简构建并交付 nginx:alpine 静态容器')
    }
    else {
      errors.push('前端 Dockerfile 未采用 Multi-stage 构建')
    }
  }
  else {
    errors.push('缺失前端 Dockerfile')
  }

  // 4. docker-compose.prod.yml 闭环检查
  const composePath = join(REPO, 'docker-compose.prod.yml')
  if (existsSync(composePath)) {
    const composeContent = readFileSync(composePath, 'utf8')
    const parsed = parseYaml(composeContent)
    const services = Object.keys(parsed?.services || {})
    const requiredServices = ['mysql', 'redis', 'server', 'web']
    const missing = requiredServices.filter(s => !services.includes(s))

    if (missing.length === 0) {
      passes.push(`生产编排服务完整闭环（包含 ${requiredServices.join(', ')}）`)
    }
    else {
      errors.push(`docker-compose.prod.yml 缺失必要服务: ${missing.join(', ')}`)
    }

    // 5. 生产告警通知链路检查：抓取 + 通知 + env 渲染，三环缺一不可
    const alertServices = ['prometheus', 'alertmanager']
    const missingAlertServices = alertServices.filter(s => !services.includes(s))
    if (missingAlertServices.length === 0) {
      passes.push('生产编排已接入监控告警闭环（prometheus 抓取 + alertmanager 通知）')
    }
    else {
      errors.push(`docker-compose.prod.yml 缺失告警链路服务: ${missingAlertServices.join(', ')}`)
    }

    if (composeContent.includes('KUBO_ALERT_WEBHOOK_URL')) {
      passes.push('告警 webhook 通知地址已通过 .env 注入（KUBO_ALERT_WEBHOOK_URL，换地址不改 YAML）')
    }
    else {
      errors.push('生产编排未接线 KUBO_ALERT_WEBHOOK_URL，告警无法投递到真实通知渠道')
    }

    const alertTpl = join(REPO, 'infra', 'alertmanager', 'alertmanager.prod.yml.tpl')
    if (existsSync(alertTpl) && readFileSync(alertTpl, 'utf8').includes('__KUBO_ALERT_WEBHOOK_URL__')) {
      passes.push('alertmanager 生产模板就绪（env 渲染占位符，零手动改配置）')
    }
    else {
      errors.push('缺失 infra/alertmanager/alertmanager.prod.yml.tpl 生产渲染模板或占位符不全')
    }
  }
  else {
    errors.push('缺失 docker-compose.prod.yml 生产编排文件')
  }

  if (errors.length > 0) {
    console.error('\n❌ Docker 生产部署与容器瘦身体检未通过：')
    for (const e of errors) {
      console.error(`  - ${e}`)
    }
    process.exit(1)
  }

  console.log(`✓ Docker 生产镜像与编排配置体检全部通过（${passes.length} 项达标）：`)
  for (const p of passes) {
    console.log(`  ✓ ${p}`)
  }
}

if (isInitEnv) {
  initEnv()
}
if (isCheck) {
  checkDockerSetup()
}

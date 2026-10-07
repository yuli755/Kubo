import { execSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'

const ROOT = process.cwd()

describe('top 1 (数据权限灰度放量) & Top 2 (Docker 镜像瘦身编排) 交付契约', () => {
  describe('top 2: Docker 容器镜像瘦身与生产级编排', () => {
    const rootDockerignore = join(ROOT, '.dockerignore')
    const serverDockerignore = join(ROOT, 'SPMS-Server/.dockerignore')
    const frontendDockerfile = join(ROOT, 'Dockerfile')
    const serverDockerfile = join(ROOT, 'SPMS-Server/Dockerfile')
    const composeProdFile = join(ROOT, 'docker-compose.prod.yml')
    const envExampleFile = join(ROOT, '.env.production.example')

    it('根目录与服务端均具备严格的 .dockerignore，阻断无用文件上传', () => {
      expect(existsSync(rootDockerignore)).toBe(true)
      expect(existsSync(serverDockerignore)).toBe(true)

      const rootContent = readFileSync(rootDockerignore, 'utf8')
      expect(rootContent).toContain('node_modules')
      expect(rootContent).toContain('.git')
      expect(rootContent).toContain('dist')

      const serverContent = readFileSync(serverDockerignore, 'utf8')
      expect(serverContent).toContain('target/')
      expect(serverContent).toContain('*.jar')
    })

    it('前端 Dockerfile 采用多阶段构建并挂载探针健康检查', () => {
      const content = readFileSync(frontendDockerfile, 'utf8')
      expect(content).toContain('FROM node:20-alpine AS build')
      expect(content).toContain('FROM nginx:alpine')
      expect(content).toContain('COPY --from=build /app/dist /usr/share/nginx/html')
      expect(content).toContain('HEALTHCHECK')
    })

    it('后端 Dockerfile 采用 JRE 瘦身镜像、非 root 用户与容器感知参数', () => {
      const content = readFileSync(serverDockerfile, 'utf8')
      // 采用 JRE 镜像而非庞大 JDK
      expect(content).toContain('eclipse-temurin:17-jre-alpine')
      // 非 root 用户运行
      expect(content).toContain('USER kubo:kubo')
      // 容器堆内存自适应与 OOM 立即退出防假死
      expect(content).toContain('-XX:+UseContainerSupport')
      expect(content).toContain('-XX:MaxRAMPercentage=75.0')
      expect(content).toContain('-XX:+ExitOnOutOfMemoryError')
      // 探针健康检查
      expect(content).toContain('HEALTHCHECK')
    })

    it('docker-compose.prod.yml 形成四位一体完整闭环', () => {
      expect(existsSync(composeProdFile)).toBe(true)
      const composeContent = readFileSync(join(ROOT, 'docker-compose.prod.yml'), 'utf8')

      expect(composeContent).toContain('mysql:')
      expect(composeContent).toContain('redis:')
      expect(composeContent).toContain('server:')
      expect(composeContent).toContain('web:')
      // 强依赖健康就绪
      expect(composeContent).toContain('condition: service_healthy')
      expect(composeContent).toContain('kubo-prod-net')
    })

    it('生产环境变量模板清晰引导密码与安全密钥设置', () => {
      expect(existsSync(envExampleFile)).toBe(true)
      const envContent = readFileSync(envExampleFile, 'utf8')
      expect(envContent).toContain('JWT_SECRET=')
      expect(envContent).toContain('MYSQL_ROOT_PASSWORD=')
      expect(envContent).toContain('REDIS_PASSWORD=')
      expect(envContent).toContain('DATA_PERMISSION_MODE=')
    })

    it('pnpm docker:check 与 docker:env 辅助脚本完备且能正常校验', () => {
      const output = execSync('node scripts/docker-helper.mjs --check', {
        cwd: ROOT,
        encoding: 'utf8',
      })
      expect(output).toContain('Docker 生产镜像与编排配置体检全部通过')
    })
  })

  describe('production SMS PII fail-closed contract', () => {
    it('production config requires an explicit SMS provider and excludes log fallback', () => {
      const prodYml = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/resources/application-production.yml'),
        'utf8',
      )
      const helper = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/sms/SmsHelper.java'),
        'utf8',
      )
      const loggingProvider = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/sms/LoggingSmsProvider.java'),
        'utf8',
      )

      expect(prodYml).toContain('provider: \'${SPMS_SMS_PROVIDER:}\'')
      expect(helper).toContain('@Value("${spms.sms.provider:}")')
      expect(helper).toContain('生产环境禁止回退到日志短信提供商')
      expect(loggingProvider).toContain('@Profile("!production")')
      expect(loggingProvider).not.toContain('发送验证码：{}')
      expect(loggingProvider).not.toContain('发送验证码：", phone, code')
      expect(loggingProvider).toContain('SmsPhoneMasker.mask(phone)')
    })

    it('all SMS phone log sites use masking and OTP is absent from logging provider', () => {
      const helper = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/sms/SmsHelper.java'),
        'utf8',
      )
      const aliyun = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/sms/AliyunSmsProvider.java'),
        'utf8',
      )

      expect(helper).toContain('mask(phone)')
      expect(aliyun).toContain('SmsPhoneMasker.mask(phone)')
      expect(aliyun).not.toContain('phone={}, template=", phone')
      expect(aliyun).not.toContain('phone={}, error=", phone')
    })
  })

  describe('export task retention contract', () => {
    it('production export task state has a bounded Redis TTL', () => {
      const prodYml = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/resources/application-production.yml'),
        'utf8',
      )
      const exportConfig = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/export/ExportConfig.java'),
        'utf8',
      )
      const exportHelper = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/java/cn/hamm/spms/common/export/ExportHelper.java'),
        'utf8',
      )

      expect(prodYml).toContain('task-ttl-seconds: \'${SPMS_EXPORT_TASK_TTL_SECONDS:3600}\'')
      expect(exportConfig).toContain('private long taskTtlSeconds = 3600L')
      expect(exportConfig).toContain('private int fileRetentionDays = 1')
      expect(prodYml).toContain('file-retention-days: \'${SPMS_EXPORT_FILE_RETENTION_DAYS:1}\'')
      expect(prodYml).toContain('file-retention-cleanup-cron: \'${SPMS_EXPORT_FILE_RETENTION_CLEANUP_CRON:0 30 3 * * *}\'')
      expect(exportHelper).toContain('@Scheduled(cron = "${app.export.file-retention-cleanup-cron:0 30 3 * * *}")')
      expect(exportHelper).toContain('Path.of(FilePaths.formatDirectory(configuredRoot)).resolve(EXPORT_DIR).normalize()')
      expect(exportHelper).toContain('Files.deleteIfExists(path)')
      expect(exportHelper).toContain('private long taskTtlSeconds()')
      expect(exportHelper).toContain('redisHelper.set(fileCacheKey, "", taskTtlSeconds)')
      expect(exportHelper).toContain('redisHelper.set(fileCacheKey, result != null ? result : "", taskTtlSeconds)')
      expect(exportHelper).toContain('redisHelper.set(fileCacheKey, "ERROR: " + e.getMessage(), taskTtlSeconds)')
      expect(exportHelper).toContain('private long taskTtlSeconds()')
    })
  })

  describe('top 1: 数据权限真实灰度放量准入与合规验证', () => {
    it('dev 分支会触发完整 CI 与生产 Compose smoke，而不是只有本地静态检查', () => {
      const workflow = readFileSync(join(ROOT, '.github/workflows/ci.yml'), 'utf8')
      expect(workflow).toContain('branches: [main, master, dev,')
      expect(workflow).toContain("github.ref == 'refs/heads/dev'")
      expect(workflow).toContain('docker compose -f docker-compose.prod.yml build --pull')
      expect(workflow).toContain("grep -qi '^x-content-type-options: nosniff'")
      expect(workflow).toContain("grep -qi '^x-frame-options: DENY'")
      expect(workflow).toContain("grep -qi '^content-security-policy:'")
      expect(workflow).toContain('docker compose -f docker-compose.prod.yml exec -T server wget -qO- http://127.0.0.1:8080/actuator/health')
      expect(workflow).toContain('docker compose -f docker-compose.prod.yml exec -T prometheus wget -qO- http://127.0.0.1:9090/-/ready')
      expect(workflow).toContain('docker compose -f docker-compose.prod.yml exec -T alertmanager wget -qO- http://127.0.0.1:9093/-/ready')
      expect(workflow).toContain('http://127.0.0.1:9090/api/v1/targets')
      expect(workflow).toContain('http://127.0.0.1:9090/api/v1/alertmanagers')
      expect(workflow).toContain('http://127.0.0.1:9090/api/v1/rules')
      expect(workflow).toContain('kubo-prod-critical')
      expect(workflow).toContain('http://127.0.0.1:9093/api/v2/status')
      expect(workflow).toContain('kubo-webhook')
      expect(workflow).toContain('"health":"up"')
      expect(workflow).toContain('Scan built images for HIGH/CRITICAL CVEs')
    })

    it('pnpm canary:check 静态门禁执行通过且无报错', () => {
      const output = execSync('node infra/scripts/check-canary-readiness.mjs', {
        cwd: ROOT,
        encoding: 'utf8',
      })
      expect(output).toContain('数据权限灰度放量就绪检查通过')
    })

    it('生产配置文件中具备明确的灰度运行模式且符合安全闭环', () => {
      const prodYml = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/resources/application-production.yml'),
        'utf8',
      )
      expect(prodYml).toContain('mode: "${DATA_PERMISSION_MODE' + ':shadow}"')
      expect(prodYml).toContain('allow-weak-default-secret: false')
    })

    it('ENFORCE 灰度门禁会阻止租户归属缺失时回落默认租户', () => {
      const gate = readFileSync(
        join(ROOT, 'infra/scripts/check-canary-readiness.mjs'),
        'utf8',
      )
      expect(gate).toContain('hasEffectiveEnforce')
      expect(gate).toContain('tenant-required=true')
      expect(gate).toContain('回落默认租户')
    })

    it('数据资源编辑器提供核心业务预设速选，免去手动输入列名与表名', () => {
      const editorSource = readFileSync(
        join(ROOT, 'src/view/console/system/datapermission/resource/component/editor.vue'),
        'utf8',
      )
      expect(editorSource).toContain('RESOURCE_PRESETS')
      expect(editorSource).toContain('wms.inventory')
      expect(editorSource).toContain('channel.purchase')
      expect(editorSource).toContain('onPresetSelect')
      expect(editorSource).toContain('#field-resourceKey')
    })

    it('数据资源管理页面具备灰度放量向导卡片与一键配置复制', () => {
      const listSource = readFileSync(
        join(ROOT, 'src/view/console/system/datapermission/resource/list.vue'),
        'utf8',
      )
      expect(listSource).toContain('canary-assistant-card')
      expect(listSource).toContain('copyCanaryConfig')
      expect(listSource).toContain('checkInventoryReadiness')
      expect(listSource).toContain('wms.inventory')
    })

    it('生产配置真实落地三批次灰度放量白名单与 Outbox 调度且通过门禁', () => {
      const prodYml = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/resources/application-production.yml'),
        'utf8',
      )
      expect(prodYml).toContain('enforce-resources:')
      expect(prodYml).toContain('- wms.inventory')
      expect(prodYml).toContain('- channel.purchase')
      expect(prodYml).toContain('- channel.sale')
      expect(prodYml).toContain('- channel.customer')
      expect(prodYml).toContain('- channel.supplier')
      expect(prodYml).toContain('- asset.material')
      expect(prodYml).toContain('outbox:')
      expect(prodYml).toContain('enabled: true')

      const inventorySource = readFileSync(
        join(ROOT, 'src/view/console/wms/inventory/list.vue'),
        'utf8',
      )
      expect(inventorySource).toContain('scanKeyword')
      expect(inventorySource).toContain('inventory-scan-input')
      expect(inventorySource).toContain('onScanLocate')
    })

    it('数据资源编辑器预置第三批次档案类资源速选，向导卡片支持三批次一键切换与复制', () => {
      const editorSource = readFileSync(
        join(ROOT, 'src/view/console/system/datapermission/resource/component/editor.vue'),
        'utf8',
      )
      expect(editorSource).toContain('\'channel.customer\'')
      expect(editorSource).toContain('\'channel.supplier\'')

      const listSource = readFileSync(
        join(ROOT, 'src/view/console/system/datapermission/resource/list.vue'),
        'utf8',
      )
      expect(listSource).toContain('CANARY_BATCHES')
      expect(listSource).toContain('batch3')
      expect(listSource).toContain('channel.customer')
      expect(listSource).toContain('channel.supplier')
      expect(listSource).toContain('asset.material')
    })

    it('生产告警通知链路完整闭环：抓取、分级路由、env 渲染与一键实测触达', () => {
      const alertmanagerYml = readFileSync(
        join(ROOT, 'infra/alertmanager/alertmanager.yml'),
        'utf8',
      )
      expect(alertmanagerYml).toContain('receiver: \'kubo-webhook\'')
      expect(alertmanagerYml).toContain('severity = "critical"')
      expect(alertmanagerYml).toContain('webhook_configs:')

      const alertTpl = readFileSync(
        join(ROOT, 'infra/alertmanager/alertmanager.prod.yml.tpl'),
        'utf8',
      )
      expect(alertTpl).toContain('__KUBO_ALERT_WEBHOOK_URL__')

      const prodPrometheus = readFileSync(
        join(ROOT, 'infra/prometheus/prometheus.prod.yml'),
        'utf8',
      )
      expect(prodPrometheus).toContain('targets: [\'server:8080\']')
      expect(prodPrometheus).toContain('targets: [\'alertmanager:9093\']')

      const prodAlerts = readFileSync(
        join(ROOT, 'infra/prometheus/rules/alerts.prod.yml'),
        'utf8',
      )
      expect(prodAlerts).toContain('TargetDown')
      expect(prodAlerts).toContain('jvm_memory_used_bytes')

      const composeContent = readFileSync(join(ROOT, 'docker-compose.prod.yml'), 'utf8')
      expect(composeContent).toContain('prometheus:')
      expect(composeContent).toContain('alertmanager:')
      expect(composeContent).toContain('KUBO_ALERT_WEBHOOK_URL')
      expect(composeContent).toContain('alertmanager.prod.yml.tpl')

      const envExample = readFileSync(join(ROOT, '.env.production.example'), 'utf8')
      expect(envExample).toContain('KUBO_ALERT_WEBHOOK_URL=')

      const packageJson = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'))
      expect(packageJson.scripts['alert:test']).toBe('node scripts/alert-helper.mjs')
      expect(existsSync(join(ROOT, 'scripts/alert-helper.mjs'))).toBe(true)
    })

    it('pnpm deploy:drill 在缺少真实 webhook 时 fail-closed（不可静默放行）', () => {
      let output = ''
      let failed = false
      try {
        output = execSync('node scripts/deploy-drill.mjs', {
          cwd: ROOT,
          encoding: 'utf8',
          env: { ...process.env, KUBO_ALERT_WEBHOOK_URL: '' },
          stdio: ['ignore', 'pipe', 'pipe'],
        })
      }
      catch (error) {
        failed = true
        const commandError = error as { stdout?: unknown, stderr?: unknown }
        const stdout = String(commandError.stdout ?? '')
        const stderr = String(commandError.stderr ?? '')
        output = `${stdout}\n${stderr}`
      }
      expect(failed).toBe(true)
      expect(output).toContain('缺少真实 KUBO_ALERT_WEBHOOK_URL')
      expect(output).toContain('alertmanager 渲染缺少真实 webhook 地址')
      expect(output).not.toContain('生产部署预演通过')
    })

    it('紧急回滚杠杆真实可用：.env 一键切档，Spring 显式占位符绑定而非失效的宽松绑定名', () => {
      const prodYml = readFileSync(
        join(ROOT, 'SPMS-Server/src/main/resources/application-production.yml'),
        'utf8',
      )
      expect(prodYml).toContain('mode: "${DATA_PERMISSION_MODE' + ':shadow}"')

      const composeContent = readFileSync(join(ROOT, 'docker-compose.prod.yml'), 'utf8')
      expect(composeContent).toContain('DATA_PERMISSION_MODE: ${DATA_PERMISSION_MODE' + ':-shadow}')
      expect(composeContent).not.toMatch(/KUBO_DATA_PERMISSION_MODE\s*:/)
      // 幽灵挂载已被部署预演揪掉：schema 由 Flyway 负责
      expect(composeContent).not.toContain('init-sql')

      const envExample = readFileSync(join(ROOT, '.env.production.example'), 'utf8')
      expect(envExample).toContain('DATA_PERMISSION_MODE=shadow')

      const dockerHelper = readFileSync(join(ROOT, 'scripts/docker-helper.mjs'), 'utf8')
      // 生成密码不得包含 YAML/插值元字符（预演曾真实抓到 * 开头密码炸掉编排解析）
      expect(dockerHelper).toContain('\'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789\'')
    })

    it('灰度向导具备一键切换 ENFORCE 能力且以后端真实就绪结果为闸门', () => {
      const listSource = readFileSync(
        join(ROOT, 'src/view/console/system/datapermission/resource/list.vue'),
        'utf8',
      )
      expect(listSource).toContain('switchToEnforce')
      expect(listSource).toContain('一键切换 ENFORCE')
      expect(listSource).toContain('mode: enforce')
      expect(listSource).toContain('切换被拒绝')
    })

    it('购销核心单据具备一键「再来一单」与「一键全部到货」，大幅精简全流程操作', () => {
      const purchaseListSource = readFileSync(
        join(ROOT, 'src/view/console/channel/purchase/list.vue'),
        'utf8',
      )
      expect(purchaseListSource).toContain('再来一单')
      expect(purchaseListSource).toContain('onCloneBill')
      expect(purchaseListSource).toContain('initial-bill')

      const purchaseDetailSource = readFileSync(
        join(ROOT, 'src/view/console/channel/purchase/component/detail.vue'),
        'utf8',
      )
      expect(purchaseDetailSource).toContain('finishAllDetails')
      expect(purchaseDetailSource).toContain('一键全部到货')

      const saleListSource = readFileSync(
        join(ROOT, 'src/view/console/channel/sale/list.vue'),
        'utf8',
      )
      expect(saleListSource).toContain('再来一单')
      expect(saleListSource).toContain('onCloneBill')
      expect(saleListSource).toContain('initial-bill')
    })
  })
})

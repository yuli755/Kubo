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
      const composeContent = readFileSync(composeProdFile, 'utf8')

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
  })

  describe('top 1: 数据权限真实灰度放量准入与合规验证', () => {
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
      expect(prodYml).toContain('mode: shadow')
      expect(prodYml).toContain('allow-weak-default-secret: false')
    })
  })
})

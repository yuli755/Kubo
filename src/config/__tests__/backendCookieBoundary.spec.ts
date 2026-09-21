import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import process from 'node:process'
import { afterEach, describe, expect, it } from 'vitest'

const fixtures: string[] = []
const scripts = ['infra/scripts/check-pom-selfcontained.mjs', 'infra/scripts/check-airpower-backend.mjs'] as const
function fixture(pom: string) {
  const root = mkdtempSync(join(tmpdir(), 'kubo-cookie-gate-'))
  fixtures.push(root)
  mkdirSync(join(root, 'SPMS-Server/src/main/resources'), { recursive: true })
  mkdirSync(join(root, 'SPMS-Server/src/main/java/example'), { recursive: true })
  writeFileSync(join(root, 'SPMS-Server/pom.xml'), pom)
  writeFileSync(join(root, 'SPMS-Server/src/main/java/example/Example.java'), 'class Example {}')
  return root
}
function run(root: string, script: string, args: string[] = []) {
  const result = spawnSync(process.execPath, [resolve(script), ...args], {
    encoding: 'utf8',
    env: { ...process.env, KUBO_ROOT: root },
    timeout: 30_000,
  })
  if (result.error)
    throw result.error
  return { status: result.status, output: result.stdout + result.stderr }
}
const exclusion = '<exclusions><exclusion><groupId>cn.hamm</groupId><artifactId>airpower-cookie</artifactId></exclusion></exclusions>'
function pom(exclusions: string, extra = '') {
  return `<project><dependencies><dependency><groupId>cn.hamm</groupId><artifactId>airpower-curd</artifactId><version>6.4.3</version>${exclusions}</dependency>${extra}</dependencies></project>`
}
afterEach(() => {
  for (const root of fixtures.splice(0))
    rmSync(root, { recursive: true, force: true })
})

describe('b7a · 旧 Cookie 模块不能再靠排除 Auto 留在依赖图里', () => {
  it('正确排除允许保留 curd；exclusion 必须另列，不能冒充仍有 cookie 依赖', () => {
    const root = fixture(pom(exclusion))
    expect(run(root, scripts[0]).status).toBe(0)
    expect(run(root, scripts[1], ['--check']).status).toBe(0)
    const report = JSON.parse(run(root, scripts[1], ['--json']).output)
    expect(report.dependencies).toEqual(['airpower-curd'])
    expect(report.exclusions).toEqual([{ from: 'cn.hamm:airpower-curd', groupId: 'cn.hamm', artifactId: 'airpower-cookie' }])
  })

  it.each([
    '',
    `<!-- ${exclusion} -->`,
    exclusion.replace('cn.hamm', 'example'),
    exclusion.replace('airpower-cookie', 'airpower-email'),
  ])('缺少真正的 Cookie 排除即失败：%s', (value) => {
    const root = fixture(pom(value))
    expect(run(root, scripts[0]).status).toBe(1)
    expect(run(root, scripts[1], ['--check']).status).toBe(1)
  })

  it('排除 curd 的传递路径不能抵消另一个直接 Cookie 依赖', () => {
    const root = fixture(pom(exclusion, '<dependency><groupId>cn.hamm</groupId><artifactId>airpower-cookie</artifactId><version>6.4.3</version></dependency>'))
    expect(run(root, scripts[0]).status).toBe(1)
    expect(run(root, scripts[1], ['--check']).status).toBe(1)
  })

  it('另一个依赖的 exclusion 不能掩盖 curd 路径遗漏', () => {
    const root = fixture(pom('', `<dependency><groupId>example</groupId><artifactId>other</artifactId><version>1</version>${exclusion}</dependency>`))
    expect(run(root, scripts[1], ['--check']).status).toBe(1)
  })

  it('子节点顺序不能把 exclusion 坐标误读成主 dependency', () => {
    const root = fixture(`<project><dependencies><dependency>${exclusion}<version>6.4.3</version><artifactId>airpower-curd</artifactId><groupId>cn.hamm</groupId></dependency></dependencies></project>`)
    const report = JSON.parse(run(root, scripts[1], ['--json']).output)
    expect(report.dependencies).toEqual(['airpower-curd'])
    expect(run(root, scripts[0]).status).toBe(0)
  })

  it('dependencyManagement 不是直接依赖，注释中的依赖不计入', () => {
    const root = fixture(`<project><dependencyManagement><dependencies><dependency><groupId>cn.hamm</groupId><artifactId>airpower-curd</artifactId><version>6.4.3</version></dependency></dependencies></dependencyManagement><!-- ${pom('')} --></project>`)
    const report = JSON.parse(run(root, scripts[1], ['--json']).output)
    expect(report.dependencies).toEqual([])
  })

  it('启动类不再引用旧 Auto，登录/退出仍注入项目自持 CookieHelper', () => {
    const main = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
    const app = readFileSync(join(main, 'Application.java'), 'utf8')
    expect(app).not.toContain('cn.hamm.airpower.cookie')
    for (const file of ['UserController.java', 'UserService.java']) {
      const source = readFileSync(join(main, 'module/personnel/user', file), 'utf8')
      expect(source).toContain('import cn.hamm.spms.common.cookie.CookieHelper;')
      expect(source).toContain('cookieHelper.getAuthorizeCookie(')
    }
  })

  it('stack:project 实际执行并读取当前 Java/POM/迁移，不把 exclusion 统计成依赖', () => {
    const result = run(resolve('.'), 'scripts/project-stack.mjs', ['--json'])
    expect(result.status).toBe(0)
    const report = JSON.parse(result.output)
    expect(report.backend.java).toBe('17')
    expect(report.backend.springBoot).toBe('3.4.4')
    const migrations = readdirSync(resolve('SPMS-Server/src/main/resources/db/migration')).filter(file => file.endsWith('.sql'))
    expect(report.backend.migrations).toBe(migrations.length)
    expect(report.backend.dependencies.some((item: { artifactId: string }) => item.artifactId === 'airpower-cookie')).toBe(false)
    expect(report.airpowerBackend.dependencies).not.toContain('airpower-cookie')
    expect(report.airpowerBackend.exclusions).toContainEqual({ from: 'cn.hamm:airpower-curd', groupId: 'cn.hamm', artifactId: 'airpower-cookie' })
  })

  it('cI 必须继续用非空真实依赖树拦截旧 Cookie，不能用声明代替解析', () => {
    const ci = readFileSync(resolve('.github/workflows/ci.yml'), 'utf8')
    expect(ci).toContain('dependency:tree -DoutputFile=')
    expect(ci).toContain('if [ ! -s "$tree_file" ]')
    expect(ci).toMatch(/if grep -Fq 'cn\.hamm:airpower-cookie:' "\$tree_file"; then/)
    expect(ci).toContain('node ../infra/scripts/test-backend-cookie.mjs')
  })

  it.each([false, true])('cookie 切片拒绝缺少或损坏的依赖，tampered=%s', (tampered) => {
    const root = fixture('<project/>')
    if (tampered)
      writeFileSync(join(root, 'spring-context-6.2.5.jar'), 'not an official jar')
    const result = spawnSync(process.execPath, [resolve('infra/scripts/test-backend-cookie.mjs')], {
      encoding: 'utf8',
      env: { ...process.env, KUBO_COOKIE_JARS: root, JAVA_HOME: join(root, 'no-jdk') },
      timeout: 30_000,
    })
    expect(result.status).toBe(1)
    expect(result.stderr).toContain(tampered ? '校验和不符，拒绝执行' : '缺少正式 Cookie 依赖')
  })
})

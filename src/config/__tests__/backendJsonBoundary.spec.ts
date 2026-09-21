import { spawnSync } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import process from 'node:process'
import { afterEach, describe, expect, it } from 'vitest'

const root = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
function code(file: string) {
  return readFileSync(join(root, file), 'utf8')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}
const fixtures: string[] = []
function fixture() {
  const dir = mkdtempSync(join(tmpdir(), 'kubo-json-boundary-'))
  fixtures.push(dir)
  return dir
}
function run(script: string, env: Record<string, string>) {
  const result = spawnSync(process.execPath, [resolve(script)], { encoding: 'utf8', env: { ...process.env, ...env }, timeout: 30_000 })
  if (result.error)
    throw result.error
  return { status: result.status, output: result.stdout + result.stderr }
}

afterEach(() => {
  for (const dir of fixtures.splice(0))
    rmSync(dir, { recursive: true, force: true })
})

describe('b2d · 内部 JSON 文本退场边界', () => {
  it('先持久化 outbox，再进入可降级的 Redis 广播，不因换 codec 交换异常边界', () => {
    const source = code('module/system/datapermission/event/DataPermissionEventPublisher.java')
    const body = source.slice(source.indexOf('private void publish('))
    expect(body.indexOf('outboxRecorder.record(event)')).toBeGreaterThanOrEqual(0)
    expect(body.indexOf('outboxRecorder.record(event)')).toBeLessThan(body.indexOf('try {'))
    expect(body).toContain('JsonCodec.write(event)')
    expect(body).toContain('redisHelper.publish(CHANNEL, json)')
    expect(body).toContain('catch (Exception e)')
  })

  it('outbox 序列化失败不能写入空 payload 或被幂等捕获吞掉', () => {
    const source = code('module/system/datapermission/service/PermissionChangeOutboxRecorder.java')
    expect(source.indexOf('.setPayload(encodePayload(event))')).toBeLessThan(source.indexOf('repository.save(row)'))
    expect(source).toContain('catch (JsonCodecException error)')
    expect(source).toContain('SERVICE_ERROR.show(error.getMessage())')
    expect(source).toContain('throw new IllegalStateException(')
    expect(source).toContain('catch (DataIntegrityViolationException ex)')
  })

  it('redis 字节按显式 UTF-8 解码，审计失败占位仍由原边界负责', () => {
    expect(code('module/system/datapermission/event/DataPermissionEventListener.java'))
      .toContain('JsonCodec.readUtf8(message.getBody(), DataPermissionChangedEvent.class)')
    const codec = code('framework/json/JsonCodec.java')
    expect(codec).toContain('new String(bytes, StandardCharsets.UTF_8)')
    expect(code('module/system/datapermission/service/OperationLogSnapshot.java')).toContain('catch (RuntimeException ex)')
  })

  it('不把兼容型事件 codec 接入权限条件 DSL 的严格解析器', () => {
    const parser = code('module/system/datapermission/rule/PermissionRuleParser.java')
    expect(parser).toContain('.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)')
    expect(parser).toContain('KNOWN_KEYS')
    expect(parser).not.toContain('JsonCodec')
  })

  it('hTTP 封装与用户菜单缓存已全量切换至自持 ApiResponse 与 JsonCodec', () => {
    const controller = code('module/personnel/user/UserController.java')
    expect(controller).not.toContain('cn.hamm.airpower')
    expect(controller).toContain('ApiResponse<List<MenuEntity>> getMyMenuList()')
    expect(controller).toContain('JsonCodec.read(object.toString(), MenuEntity[].class)')
  })

  it('缺少正式依赖时不运行 Java、不空转通过', () => {
    const dir = fixture()
    const result = run('infra/scripts/test-backend-json.mjs', { KUBO_JSON_JARS: dir, JAVA_HOME: join(dir, 'no-jdk') })
    expect(result.status).toBe(1)
    expect(result.output).toContain('缺少正式依赖 jackson-annotations-2.18.3.jar')
  })

  it('同名伪造 JAR 在执行 Java 之前被校验和拒绝', () => {
    const dir = fixture()
    writeFileSync(join(dir, 'jackson-annotations-2.18.3.jar'), 'not the official artifact')
    const result = run('infra/scripts/test-backend-json.mjs', { KUBO_JSON_JARS: dir, JAVA_HOME: join(dir, 'no-jdk') })
    expect(result.status).toBe(1)
    expect(result.output).toContain('校验和不符，拒绝作为测试依赖执行')
  })

  it.each([
    ['jackson-databind', 0],
    ['jackson-not-managed', 1],
  ])('pOM 无版本白名单只新增确认受 BOM 管理的坐标：%s', (artifact, status) => {
    const dir = fixture()
    mkdirSync(join(dir, 'SPMS-Server'))
    writeFileSync(join(dir, 'SPMS-Server/pom.xml'), `<project><dependencies><dependency>
      <groupId>com.fasterxml.jackson.core</groupId><artifactId>${artifact}</artifactId>
    </dependency></dependencies></project>`)
    expect(run('infra/scripts/check-pom-selfcontained.mjs', { KUBO_ROOT: dir }).status).toBe(status)
  })
})

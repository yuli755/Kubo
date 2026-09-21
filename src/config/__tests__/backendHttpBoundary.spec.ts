import { spawnSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import process from 'node:process'
import { afterEach, describe, expect, it } from 'vitest'

const java = resolve('SPMS-Server/src/main/java/cn/hamm/spms')
const code = (file: string) => readFileSync(join(java, file), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '')
const fixtures: string[] = []
afterEach(() => {
  for (const dir of fixtures.splice(0))
    rmSync(dir, { recursive: true, force: true })
})

describe('p1 · 业务异常与旧 HTTP 链的切换边界', () => {
  it.each(['Errors', 'CustomError'])('%s 真正脱离旧异常接口，而不是换命名空间继承', (name) => {
    const source = code(`common/exception/${name}.java`)
    expect(source).toContain('implements ErrorCode')
    expect(source).not.toMatch(/cn\.hamm\.airpower|IException|import lombok/)
    expect(source).toContain('private final String message;')
    expect(source).not.toContain('setMessage(')
  })

  it('项目异常必须是独立 RuntimeException，不能继承旧异常伪装自持', () => {
    const source = code('framework/error/BusinessException.java')
    expect(source).toContain('extends RuntimeException')
    expect(source).not.toContain('cn.hamm.airpower')
    expect(source).toContain('private final int code;')
    expect(source).toContain('private final Object data;')
  })

  it('新异常 advice 仅处理自持异常，保持 HTTP 200 并先于旧通用异常兜底', () => {
    const source = code('common/exception/BusinessExceptionAdvice.java')
    expect(source).toContain('@RestControllerAdvice')
    expect(source).toContain('@Order(Ordered.HIGHEST_PRECEDENCE)')
    expect(source).toContain('@ResponseStatus(HttpStatus.OK)')
    expect(source).toContain('@ExceptionHandler(BusinessException.class)')
    expect(source).not.toContain('@ExceptionHandler(Exception.class)')
    expect(source).toContain('ApiResponse.failure(failure)')
  })

  it('过渡桥必须实际注册且先执行，保留旧 Json 的字段过滤/trace 接点', () => {
    const source = code('common/compat/LegacyApiResponseBridge.java')
    expect(source).toContain('@ControllerAdvice')
    expect(source).toContain('@Order(Ordered.HIGHEST_PRECEDENCE)')
    expect(source).toContain('ApiResponse.class.isAssignableFrom(returnType.getParameterType())')
    expect(source).toContain('AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)')
    expect(source).toContain('Json.show(result.getCode(), result.getMessage(), result.getData()).setTraceId(result.getTraceId())')
  })

  it('防爆破同时统计新旧两类业务失败，并重抛原异常', () => {
    const source = code('module/personnel/user/UserController.java')
    expect(source).toMatch(/catch\s*\(BusinessException\s*\|\s*ServiceException exception\)\s*\{\s*loginBruteForceGuard\.recordFailure\(account, clientIp\);\s*throw exception;/)
    expect(source).toContain('loginBruteForceGuard.clear(account, clientIp)')
  })

  it('数据库错误返回项目协议，不再直接依赖旧 Json', () => {
    const source = code('common/exception/DataIntegrityViolationAdvice.java')
    expect(source).toContain('ApiResponse<Void> handle(Exception ex)')
    expect(source).not.toContain('cn.hamm.airpower')
    expect(source).toContain('seen.add(current)')
  })

  it('p2 核心继承链自持替换：BaseController 与 RequestInterceptor 彻底脱离旧框架基类', () => {
    const baseController = code('base/BaseController.java')
    expect(baseController).not.toContain('extends CurdController')
    expect(baseController).toContain('extends ApiController implements CurdAction')
    expect(baseController).toContain('ApiResponse<Void> publish(')
    expect(baseController).toContain('ApiResponse<E> add(')
    expect(baseController).toContain('ApiResponse<QueryPageResponse<E>> getPage(')
    const interceptor = code('common/interceptor/RequestInterceptor.java')
    expect(interceptor).not.toContain('extends CurdRequestInterceptor')
    expect(interceptor).toContain('implements HandlerInterceptor')
  })

  it.each([false, true])('hTTP runner 缺包/同名坏包拒绝执行，tampered=%s', (tampered) => {
    const dir = mkdtempSync(join(tmpdir(), 'kubo-http-gate-'))
    fixtures.push(dir)
    if (tampered)
      writeFileSync(join(dir, 'spring-context-6.2.5.jar'), 'not the verified artifact')
    const result = spawnSync(process.execPath, [resolve('infra/scripts/test-backend-http.mjs')], {
      encoding: 'utf8',
      timeout: 30_000,
      env: { ...process.env, KUBO_HTTP_JARS: dir, JAVA_HOME: join(dir, 'no-java') },
    })
    expect(result.status).toBe(1)
    expect(result.stderr).toContain(tampered ? '校验和不符，拒绝执行' : '缺少正式 HTTP 依赖')
  })

  it('cI 使用正式 JAR 模式，不能把本地源码参考模式当成完整 Maven 验收', () => {
    const workflow = readFileSync(resolve('.github/workflows/ci.yml'), 'utf8')
    expect(workflow).toContain('node ../infra/scripts/test-backend-http.mjs')
    expect(workflow).not.toContain('KUBO_HTTP_LEGACY_CORE_SOURCES')
    expect(workflow).toContain('./mvnw -B -s settings.xml -DskipTests compile')
    expect(workflow).toContain('./mvnw -B -s settings.xml test')
    const manifest = JSON.parse(readFileSync(resolve('infra/scripts/backend-http-legacy-source.json'), 'utf8'))
    expect(manifest.ref).toBe('fe1691b2c978')
    expect(manifest.files).toHaveLength(41)
    expect(manifest.files.every((file: { path: string, sha256: string }) => file.path.startsWith('cn/hamm/airpower/core/') && /^[a-f0-9]{64}$/.test(file.sha256))).toBe(true)
  })
})

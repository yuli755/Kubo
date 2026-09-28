import { spawnSync } from 'node:child_process'
import { mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
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

  it('全局响应增强实际注册且先执行，为自持 ApiResponse 注入全链路追踪标识', () => {
    const source = code('framework/http/ApiResponseAdvice.java')
    expect(source).toContain('@ControllerAdvice')
    expect(source).toContain('@Order(Ordered.HIGHEST_PRECEDENCE)')
    expect(source).toContain('ApiResponse.class.isAssignableFrom(returnType.getParameterType())')
    expect(source).toContain('AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType)')
    expect(source).toContain('result.withTraceId(Tracer.getTraceId())')
  })

  it('防爆破统计业务失败，并重抛自持 BusinessException', () => {
    const source = code('module/personnel/user/UserController.java')
    expect(source).toMatch(/catch\s*\(BusinessException exception\)\s*\{\s*loginBruteForceGuard\.recordFailure\(account, clientIp\);\s*throw exception;/)
    expect(source).toContain('loginBruteForceGuard.clear(account, clientIp)')
  })

  it('数据库错误返回项目协议，不再直接依赖旧 Json', () => {
    const source = code('common/exception/DataIntegrityViolationAdvice.java')
    expect(source).toContain('ApiResponse<Void> handle(Exception ex)')
    expect(source).not.toContain('cn.hamm.airpower')
    expect(source).toContain('seen.add(current)')
  })

  it('请求绑定/协议异常收敛进项目协议，且不做 Exception 兜底', () => {
    const source = code('common/exception/RequestBindingExceptionAdvice.java')
    // 与其余 advice 同一响应形态：HTTP 200 + 业务码，不真返回 4xx/5xx
    expect(source).toContain('@RestControllerAdvice')
    expect(source).toContain('@ResponseStatus(HttpStatus.OK)')
    // 必须让位于两个 HIGHEST_PRECEDENCE 的 advice，否则会把 BusinessException 一并吞掉
    expect(source).toContain('@Order(Ordered.LOWEST_PRECEDENCE)')
    // 保持 backendHttpBoundary 既有纪律：不写 Exception 兜底，意外异常仍出真实 5xx
    expect(source).not.toContain('@ExceptionHandler(Exception.class)')
    // 覆盖此前会逃逸成 Spring 默认错误体的每一类
    for (const handled of [
      'BindException.class',
      'MissingServletRequestParameterException.class',
      'MethodArgumentTypeMismatchException.class',
      'HttpMessageNotReadableException.class',
      'HttpRequestMethodNotSupportedException.class',
      'HttpMediaTypeNotSupportedException.class',
    ])
      expect(source).toContain(handled)
    // 错误码语义要分得开：没传≠传错≠body 不是 JSON≠方法/类型不支持
    for (const err of [
      'Errors.PARAM_MISSING',
      'Errors.PARAM_INVALID',
      'Errors.JSON_DECODE_FAIL',
      'Errors.REQUEST_METHOD_UNSUPPORTED',
      'Errors.REQUEST_CONTENT_TYPE_UNSUPPORTED',
    ])
      expect(source).toContain(err)
    // HttpMessageNotReadableException 混装两类错误，必须按 cause 分流，
    // 否则「值填错」会被误报成「你发的不是 JSON」
    expect(source).toContain('MismatchedInputException')
  })

  it('编译保留参数名元数据，@RequestParam 隐式命名不会在运行期炸成 500', () => {
    // Spring Framework 6.1 删除了 LocalVariableTableParameterNameDiscoverer，
    // 未写显式 name 的 @RequestParam/@PathVariable 完全依赖 -parameters。
    // 缺它时：编译通过、单测通过、启动正常，请求一进来才 IllegalArgumentException → 500，
    // 且在 Filter 链内抛出会绕过 @RestControllerAdvice（GUI-2 原始形态）。
    // 该配置在 airpower 退役时被静默丢弃过，故在此钉死。
    const pom = readFileSync(resolve('SPMS-Server/pom.xml'), 'utf8')
    expect(pom).toContain('<parameters>true</parameters>')
    expect(pom).toContain('maven-compiler-plugin')
    // -parameters 没有任何标准 Maven 属性，只能落在插件 configuration 里；
    // 只搬 maven.compiler.* 属性是不够的（正是当初漏掉它的原因）。
    expect(pom).toMatch(/maven-compiler-plugin[\s\S]{0,2000}<parameters>true<\/parameters>/)
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

/**
 * # 树形列表端点契约
 *
 * 真机联调实测：前端 7 个 `useTable({ tree: true })` 页面与角色授权的两个弹窗
 * 都在 POST `/{baseUrl}/getTreeList`，而后端**没有这个端点**，6 个树形服务全部 HTTP 404，
 * 页面表现为树形表格与授权弹窗空白。`getTreeList` 是 airpower `CurdController` 的旧契约，
 * B0 退役 airpower 底座时后端未实现，前端却继续按旧名字发请求。
 *
 * 修法是把 `urlGetTreeList` 指回 `getList`（后端树形实体的 `getList` 本就返回嵌套树），
 * 而不是给后端补端点 —— 补端点会新增权限身份 `X_getTreeList`，而
 * `RequestInterceptor.checkUserPermission` 从**数据库**查权限且 fail-closed
 * （判空还在 SYSTEM_ROOT 的 bypass 之前），写库的 `loadPermission()` 又只被
 * 受 `init.lock` 与 dev profile 双重限制的 `DevDataInitRunner` 调用，
 * 于是新端点在权限行落库前对所有角色都是 403「权限不存在」。
 *
 * 这组用例把上述结论钉成可执行契约：任何一侧单独改动都会在这里失败。
 */
describe('p6 · 树形列表端点契约（前端不得请求后端不存在的 getTreeList）', () => {
  /** 递归列出目录下匹配扩展名的文件全文 */
  const sources = (dir: string, ext: RegExp): string[] =>
    (readdirSync(resolve(dir), { recursive: true }) as string[])
      .filter(name => ext.test(name))
      .map(name => readFileSync(join(resolve(dir), name), 'utf8'))

  it('urlGetTreeList 指向 getList，不再发往后端不存在的 getTreeList', () => {
    const source = readFileSync(resolve('src/framework/http.ts'), 'utf8')
    expect(source).toMatch(/urlGetTreeList\s*=\s*'getList'/)
    expect(source).not.toMatch(/urlGetTreeList\s*=\s*'getTreeList'/)
    // 方法本身要保留：调用点 useTable({ tree: true }) 表达的是「不分页 + 嵌套 children」的语义
    expect(source).toContain('async getTreeList(')
    // 头部 URL 对照表不能继续宣称存在 /getTreeList
    expect(source).not.toContain('`POST /{baseUrl}/getTreeList`')
  })

  it('后端确实没有 getTreeList 映射，且树由 Trees.buildTreeList 在 getList 链路内组装', () => {
    const javaSources = sources('SPMS-Server/src/main/java', /\.java$/)
    // 若将来后端真的补上了这个端点，这条会失败 —— 那时应重新评估：
    // 需要同时播种 X_getTreeList 权限行并授予已持有 X_getList 的角色，否则一律 403。
    expect(javaSources.filter(s => s.includes('"getTreeList"'))).toHaveLength(0)
    expect(javaSources.filter(s => s.includes('Trees.buildTreeList('))).not.toHaveLength(0)
  })

  it('树模式确实在被使用（契约不是死代码），且这些服务都有后端 getList', () => {
    const vueSources = sources('src/view', /\.vue$/)
    const treePages = vueSources.filter(s => /tree:\s*true/.test(s))
    // factory/storage、factory/structure、personnel/department、personnel/user 的部门筛选树、
    // system/menu、system/permission、wms/inventory（仓库树 + 库位树）
    expect(treePages.length, '树形页面数量异常，请核对 useTable({ tree: true }) 的调用点').toBeGreaterThanOrEqual(7)

    const javaSources = sources('SPMS-Server/src/main/java', /\.java$/)
    // 这些页面用到的树形服务，后端都必须暴露 getList（BaseController 提供）
    for (const api of ['storage', 'structure', 'department', 'menu', 'permission']) {
      expect(
        javaSources.some(s => s.includes(`@Api("${api}")`)),
        `后端缺少 @Api("${api}") 控制器，树形页面将无数据源`,
      ).toBe(true)
    }
  })
})

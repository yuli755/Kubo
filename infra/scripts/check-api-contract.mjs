#!/usr/bin/env node
/**
 * 前后端 API 契约门禁（前端拼出的端点必须真实存在于后端路由表）
 * ============================================================================
 * 为什么要有这个脚本：**同一类 BUG 已经出现两次，两次都只能靠真机发现。**
 *
 *   ① `getTreeList`：前端 6 个树形服务全部请求 `/{res}/getTreeList` → HTTP 404，
 *      树形表格与角色授权弹窗整片空白（见 `src/framework/http.ts` 里 `urlGetTreeList` 的长注释）。
 *   ② `getDetail/{id}`：`AbstractCurdService.getDetail()` 拼 `getDetail/9` 发过去 → HTTP 404。
 *      后端只有 `@PostMapping("getDetail")` + `@RequestBody E source`（id 在**体内**）。
 *      影响 `KFormDialog` 编辑回填、`useDetail` 详情、销售/采购「查看详情」—— 点开就空白。
 *
 * 两次都躲过了所有既有门禁，原因很具体：
 *   - 单元测试断言的是**前端自己拼出的字符串**（`expect(config.url).toBe('/demo/getDetail/9')`），
 *     它锁住的是实现，不是契约 —— 实现错了，测试跟着一起错；
 *   - 演示模式的 mock（`src/demo/mock/adapter.ts`）为兼容路径式 id 专门写了分支，
 *     于是 `pnpm demo:check` 与 demoPreview 用例全绿。**mock 通过 ≠ 真机通过**；
 *   - `perms:check` / `auth:check` 都是「后端 → 前端」方向（后端权限码是否被前端用到），
 *     没有一个门禁做「前端 → 后端」方向（前端要打的地址后端有没有）。
 *
 * ## 后端契约的两条硬事实（本脚本自检里也钉住了）
 *   - 全部映射都是 `POST /{base}/{action}`，**零路径变量**（`@*Mapping("...{id}")` 全仓 0 处）；
 *   - `BaseController` 提供 11 个通用端点，`BaseBillController` 再加 4 个单据端点，
 *     基路径来自每个具体控制器的类级 `@RequestMapping`。
 *   ⇒ 所以"前端在端点后拼路径段"这件事**永远**是错的，可以直接当规则拦（Rule A）。
 *
 * ## 三条规则
 *   A 前端不得给 CRUD/单据端点拼路径后缀（`getDetail/${id}`、`'getDetail/' + id` 之类）；
 *   B 前端每个 `baseUrl` 都必须是后端某个控制器的类级 `@RequestMapping` 基路径；
 *   C 每个前端服务按其基类链继承到的端点名，都必须能在后端路由表里找到 `{baseUrl}/{endpoint}`。
 *
 * 用法：node infra/scripts/check-api-contract.mjs [--verbose]
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java')
const WEB = join(ROOT, 'src')
const VERBOSE = process.argv.includes('--verbose')

/** 后端类级基路径 */
const REQUEST_MAPPING = /@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"/
/** 后端方法级端点（含 value= 写法） */
const METHOD_MAPPING = /@(?:Post|Get|Put|Delete|Patch|Request)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"/g
/** 类声明与继承 */
const CLASS_DECL = /\bclass\s+(\w+)(?:\s*<[^{]*?>)?(?:\s+extends\s+(\w+))?/
/** 前端服务的 baseUrl 字段 */
const BASE_URL = /\bbaseUrl\s*(?::\s*string)?\s*=\s*['"]([^'"]+)['"]/g
/** 前端框架层声明的端点名字段：`protected urlGetPage = 'getPage'` */
const URL_FIELD = /\burl([A-Z]\w*)\s*(?::\s*string)?\s*=\s*['"]([^'"]+)['"]/g
/** TS 类声明（用于把端点名归到声明它的那个基类） */
const TS_CLASS = /\b(?:export\s+)?(?:abstract\s+)?class\s+(\w+)(?:\s*<[^{]*?>)?(?:\s+extends\s+(\w+))?/g
/** Rule A：给端点拼路径后缀 */
const PATH_SUFFIX = [
  /['"`](\w+)\/\$\{/g,
  /['"`](\w+)\/['"`]\s*\+/g,
  /\$\{\s*this\.url\w+\s*\}\s*\/\s*\$\{/g,
]

function walk(dir, predicate, out = []) {
  for (const name of readdirSync(dir)) {
    if (name === 'node_modules' || name === 'dist' || name === '.git') {
      continue
    }
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) {
      walk(p, predicate, out)
    }
    else if (predicate(p)) {
      out.push(p)
    }
  }
  return out
}

/** 去注释但保留行数（报错行号才对得上源文件） */
function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, matched => matched.replace(/[^\n]/g, ' '))
    .replace(/^[ \t]*\/\/[^\n]*$/gm, '')
}

function lineOf(source, index) {
  return source.slice(0, index).split('\n').length
}

const rel = p => relative(ROOT, p).replaceAll('\\', '/')

// ============================================================================
// 后端：路由表（@Api 基路径 + Curd 枚举 + @Extends 白/黑名单 + 显式 @PostMapping）
// ============================================================================

/** 后端类级基路径：本项目用自研 `@Api("input")`，`@RequestMapping` 只是兜底 */
const API_BASE = /@Api\(\s*(?:value\s*=\s*)?"([^"]*)"/
/** `@Extends(value = {Curd.Export, ...}, exclude = Curd.Delete)` */
const EXTENDS_ANNOTATION = /@Extends\s*\(([^)]*)\)/
/** Curd 枚举项：`GetDetail(5, "查询详情", "getDetail"),` */
const CURD_ENTRY = /^\s*(\w+)\s*\(\s*\d+\s*,\s*"[^"]*"\s*,\s*"(\w+)"\s*\)/gm

/**
 * 解析 `Curd` 枚举 → 受 `checkApiAvailable` 门控的端点名集合。
 *
 * 为什么要从源码解析而不是写死：这 10 个名字就是后端 CRUD 契约的全部，
 * 写死一份常量等于把契约抄两遍 —— 后端加一个 `Curd.Publish`，抄的那份不会跟着变，
 * 门禁就会对着一个真实存在的端点报 404。
 */
function parseCurdEnum() {
  const file = join(SERVER, 'cn', 'hamm', 'spms', 'base', 'Curd.java')
  const src = readFileSync(file, 'utf8')
  const gated = new Map()
  for (const m of src.matchAll(CURD_ENTRY)) {
    gated.set(m[1], m[2]) // 枚举名 -> 端点名
  }
  return gated
}

function buildBackend() {
  const files = walk(SERVER, p => p.endsWith('Controller.java'))
  const gatedCurd = parseCurdEnum()
  /** @type {Map<string, {base: string, own: Set<string>, white: string[], black: string[], parent: string|null, file: string}>} */
  const classes = new Map()

  for (const file of files) {
    const src = stripComments(readFileSync(file, 'utf8'))
    const decl = CLASS_DECL.exec(src)
    if (!decl) {
      continue
    }
    const [, name, parent] = decl
    const head = src.slice(0, decl.index)
    const base = (API_BASE.exec(head)?.[1] ?? REQUEST_MAPPING.exec(head)?.[1] ?? '').replace(/^\/|\/$/g, '')

    // @Extends 的白/黑名单（只取本类自己声明的那份，继承链在解析时合并）
    const white = []
    const black = []
    const ext = EXTENDS_ANNOTATION.exec(head)
    if (ext) {
      const text = ext[1]
      const valuePart = /(?:^|[,(\s])value\s*=\s*([\s\S]*?)(?:,\s*exclude\s*=|$)/.exec(text)?.[1] ?? ''
      const excludePart = /exclude\s*=\s*([\s\S]*)$/.exec(text)?.[1] ?? ''
      const names = part => [...part.matchAll(/(?:Curd\s*\.\s*)?(\w+)/g)].map(m => m[1]).filter(n => gatedCurd.has(n))
      white.push(...names(valuePart))
      black.push(...names(excludePart))
    }

    // 本类显式声明的端点（class 声明之后才是方法）
    const own = new Set()
    for (const m of src.slice(decl.index).matchAll(METHOD_MAPPING)) {
      own.add(m[1].replace(/^\//, ''))
    }
    classes.set(name, { base, own, white, black, parent: parent ?? null, file: rel(file) })
  }

  /** 继承链上显式声明的全部端点 */
  const declaredOf = (name, seen = new Set()) => {
    if (!name || seen.has(name) || !classes.has(name)) {
      return new Set()
    }
    seen.add(name)
    const entry = classes.get(name)
    return new Set([...declaredOf(entry.parent, seen), ...entry.own])
  }

  /**
   * 复刻 `Curd.getCurdList(clazz)` 的向上遍历：
   * 子类先跑，`value` 进白名单（已在黑名单则不加）、`exclude` 进黑名单，
   * 走到 Object 时把「既不在白也不在黑」的 Curd 项全部补进白名单。
   * 结论：`exclude` 只挡**之后**的加入与最终兜底，已被子类显式 value 的项仍然可用。
   */
  const availableCurdOf = (name) => {
    const white = []
    const black = []
    let cursor = name
    const seen = new Set()
    while (cursor && classes.has(cursor) && !seen.has(cursor)) {
      seen.add(cursor)
      const entry = classes.get(cursor)
      for (const c of entry.white) {
        if (!black.includes(c) && !white.includes(c)) {
          white.push(c)
        }
      }
      for (const c of entry.black) {
        if (!black.includes(c)) {
          black.push(c)
        }
      }
      cursor = entry.parent
    }
    for (const [enumName] of gatedCurd) {
      if (!black.includes(enumName) && !white.includes(enumName)) {
        white.push(enumName)
      }
    }
    return new Set(white.map(enumName => gatedCurd.get(enumName)))
  }

  /** @type {Set<string>} 已注册路由（打过去不会是 404） */
  const routes = new Set()
  /** @type {Set<string>} 已注册但被 @Extends 排除（打过去是 501 当前接口不支持此操作） */
  const refused = new Set()
  /** @type {Set<string>} 基路径 */
  const bases = new Set()
  const perController = new Map()

  for (const [name, entry] of classes) {
    if (!entry.base) {
      continue // 抽象基类（BaseController / BaseBillController）没有自己的基路径
    }
    bases.add(entry.base)
    const declared = declaredOf(name)
    const availableCurd = availableCurdOf(name)
    // Curd 门控的端点：BaseController 全部注册了，可用性由 @Extends 决定
    for (const endpoint of gatedCurd.values()) {
      routes.add(`${entry.base}/${endpoint}`)
      if (!availableCurd.has(endpoint)) {
        refused.add(`${entry.base}/${endpoint}`)
      }
    }
    // 非 Curd 端点（audit / reject / addFinish / publish / getCount …）不受门控，声明即可用
    for (const endpoint of declared) {
      if (!new Set(gatedCurd.values()).has(endpoint)) {
        routes.add(`${entry.base}/${endpoint}`)
      }
    }
    perController.set(entry.base, { availableCurd, declared, file: entry.file })
  }

  return { routes, refused, bases, perController, controllerCount: files.length, gatedCurd }
}

// ============================================================================
// 前端：服务清单（baseUrl + 基类链）与框架层端点名
// ============================================================================
function buildFrontend() {
  const tsFiles = walk(WEB, p => /\.(ts|vue)$/.test(p) && !p.includes('__tests__'))
  /** @type {Map<string, {endpoints: Set<string>, parent: string|null, file: string}>} */
  const tsClasses = new Map()
  /** @type {{baseUrl: string, extendsClass: string|null, file: string, line: number}[]} */
  const services = []
  /** @type {string[]} */
  const violationsA = []

  for (const file of tsFiles) {
    const src = stripComments(readFileSync(file, 'utf8'))
    const decls = [...src.matchAll(TS_CLASS)].map(m => ({ name: m[1], parent: m[2] ?? null, index: m.index }))
    for (const d of decls) {
      const prev = tsClasses.get(d.name) ?? { endpoints: new Set(), parent: null, file: rel(file) }
      prev.parent = prev.parent ?? d.parent
      tsClasses.set(d.name, prev)
    }
    /**
     * 一个字段属于哪个类 = **位置在它之前、最近的那个类声明**。
     *
     * 不能按"文件里第一个类"归属：`src/framework/http.ts` 里同时有 `Http`（链式请求器）、
     * `AbstractService`、`AbstractCurdService` 三个类，`urlGetPage` 等字段声明在最后一个里 ——
     * 按第一个类归属会把全部端点算到 `Http` 头上，`AbstractCurdService` 继承链拿到 0 个端点，
     * Rule C 就此静默失效（自检会红，但红得莫名其妙）。
     */
    const ownerOf = (index) => {
      let owner = null
      for (const d of decls) {
        if (d.index <= index) {
          owner = d.name
        }
        else {
          break
        }
      }
      return owner
    }

    for (const m of src.matchAll(URL_FIELD)) {
      const owner = ownerOf(m.index)
      if (!owner) {
        continue
      }
      const entry = tsClasses.get(owner) ?? { endpoints: new Set(), parent: null, file: rel(file) }
      entry.endpoints.add(m[2])
      tsClasses.set(owner, entry)
    }

    for (const m of src.matchAll(BASE_URL)) {
      const owner = ownerOf(m.index)
      services.push({
        baseUrl: m[1],
        extendsClass: owner ? (tsClasses.get(owner)?.parent ?? null) : null,
        file: rel(file),
        line: lineOf(src, m.index),
      })
    }

    // Rule A：给端点拼路径后缀
    for (const pattern of PATH_SUFFIX) {
      for (const m of src.matchAll(pattern)) {
        violationsA.push(`${rel(file)}:${lineOf(src, m.index)}  端点后拼了路径段：${m[0]}`)
      }
    }
  }

  /** 某前端基类链上声明的全部端点名 */
  const endpointsOf = (name, seen = new Set()) => {
    if (!name || seen.has(name)) {
      return new Set()
    }
    seen.add(name)
    const entry = tsClasses.get(name)
    if (!entry) {
      return new Set()
    }
    const inherited = endpointsOf(entry.parent, seen)
    return new Set([...inherited, ...entry.endpoints])
  }

  return { services, endpointsOf, tsClasses, violationsA, fileCount: tsFiles.length }
}

// ============================================================================
// 规则实现（纯函数，便于自检复用）
// ============================================================================
function checkContract(backend, frontend) {
  const violations = []

  // Rule A
  for (const v of frontend.violationsA) {
    violations.push(
      `A ${v}\n`
      + `     后端全部端点都是 POST /{base}/{action} + 请求体，**零路径变量**；\n`
      + `     拼路径后缀的请求真机一定是 404（历史两例：getTreeList、getDetail/{id}）。\n`
      + `     改法：把参数放进请求体，例如 getDetail(id) → POST getDetail { id }。`,
    )
  }

  // Rule B
  const seenBase = new Set()
  for (const svc of frontend.services) {
    if (seenBase.has(svc.baseUrl)) {
      continue
    }
    seenBase.add(svc.baseUrl)
    if (!backend.bases.has(svc.baseUrl)) {
      violations.push(
        `B ${svc.file}:${svc.line}  baseUrl '${svc.baseUrl}' 在后端找不到对应的 @RequestMapping 基路径\n`
        + `     该服务的所有请求都会 404。改法：核对后端控制器的类级 @RequestMapping 值。`,
      )
    }
  }

  // Rule C
  for (const svc of frontend.services) {
    if (!backend.bases.has(svc.baseUrl)) {
      continue // Rule B 已经报过，不重复刷屏
    }
    for (const ep of frontend.endpointsOf(svc.extendsClass)) {
      if (!backend.routes.has(`${svc.baseUrl}/${ep}`)) {
        violations.push(
          `C ${svc.file}:${svc.line}  '${svc.baseUrl}' 的基类链声明了端点 '${ep}'，但后端没有 ${svc.baseUrl}/${ep}\n`
          + `     调用它会 404（且 404 响应体没有 code/message，前端只能显示泛化网络错误）。`,
        )
      }
    }
  }

  return violations
}

// ============================================================================
// 门禁自检：三条规则都要能咬人，且不能误伤正确写法
// ============================================================================
function selfCheck(backend, frontend) {
  const problems = []
  const cases = []
  const expect = (label, fn) => {
    cases.push(label)
    try {
      fn()
    }
    catch (error) {
      problems.push(`自检「${label}」失败：${error.message}`)
    }
  }
  const assert = (condition, message) => {
    if (!condition) {
      throw new Error(message)
    }
  }

  // 后端解析必须真的解析出东西（否则三条规则会安静地永远通过）
  expect('后端路由表非空', () => {
    assert(backend.routes.size > 100, `只解析出 ${backend.routes.size} 条路由，解析器可能失效`)
    assert(backend.bases.has('input'), '没有解析出 input 基路径（@Api("input")）')
    assert(backend.routes.has('input/getDetail'), '没有解析出继承来的 input/getDetail')
    assert(backend.routes.has('purchase/audit'), '没有解析出单据端点 purchase/audit')
    assert(backend.routes.has('material/queryExport'), '没有解析出 material/queryExport')
  })

  // @Extends 的排除集必须与真机一致（2026-09 实测：排除项返回 HTTP 200 + code=501「当前接口不支持此操作」）
  expect('被 @Extends 排除的端点识别正确', () => {
    assert(backend.refused.has('input/delete'), 'input/delete 真机是 501，应识别为已排除')
    assert(backend.refused.has('purchase/delete'), 'purchase/delete 真机是 501，应识别为已排除')
    assert(backend.refused.has('supplier/delete'), 'supplier/delete 真机是 501，应识别为已排除')
    assert(!backend.refused.has('material/delete'), 'material/delete 真机可用（返回 404 只是查不到那一行）')
    assert(!backend.refused.has('personalToken/delete'), 'personalToken/delete 真机可用')
    // 关键区分：被排除 != 路由不存在。delete 是注册了的，打过去是 501 而不是 404
    assert(backend.routes.has('input/delete'), 'input/delete 的路由存在（运行时才拒绝），不能按 404 报')
  })

  // 后端零路径变量是 Rule A 的前提，前提变了规则就得重写
  expect('后端零路径变量（Rule A 的前提）', () => {
    for (const route of backend.routes) {
      assert(!route.includes('{'), `后端出现了路径变量路由 ${route}，Rule A 的前提不再成立`)
    }
  })

  // 前端服务清单必须扫到（含新底座与 airpower 存量两套）
  expect('前端服务清单非空', () => {
    assert(frontend.services.length >= 20, `只扫到 ${frontend.services.length} 个 baseUrl`)
    const bases = new Set(frontend.services.map(s => s.baseUrl))
    for (const wanted of ['input', 'output', 'move', 'purchase', 'sale', 'material', 'user', 'permission']) {
      assert(bases.has(wanted), `没扫到 baseUrl '${wanted}'`)
    }
  })

  // Rule A 必须咬人：合成一段"给端点拼路径"的代码
  expect('Rule A 咬人', () => {
    const fake = { ...frontend, violationsA: ["synthetic.ts:1  端点后拼了路径段：`getDetail/${"] }
    const got = checkContract(backend, fake).filter(v => v.startsWith('A '))
    assert(got.length === 1, `期望 1 处 A 违规，实际 ${got.length}`)
  })

  // Rule B 必须咬人：不存在的 baseUrl
  expect('Rule B 咬人', () => {
    const fake = {
      ...frontend,
      violationsA: [],
      services: [{ baseUrl: 'noSuchResource', extendsClass: null, file: 'synthetic.ts', line: 1 }],
    }
    const got = checkContract(backend, fake).filter(v => v.startsWith('B '))
    assert(got.length === 1, `期望 1 处 B 违规，实际 ${got.length}`)
  })

  // Rule C 必须咬人：真实 baseUrl + 后端没有的端点名（getTreeList 就是这一类）
  expect('Rule C 咬人', () => {
    const fakeBackend = { ...backend, routes: new Set([...backend.routes].filter(r => r !== 'input/getList')) }
    const fake = {
      ...frontend,
      violationsA: [],
      services: [{ baseUrl: 'input', extendsClass: 'AbstractCurdService', file: 'synthetic.ts', line: 1 }],
    }
    const got = checkContract(fakeBackend, fake).filter(v => v.startsWith('C '))
    assert(got.length >= 1, '基类端点缺失时应当报 C 违规')
  })

  // 不该误伤：真实服务清单跑一遍，A/B 必须为 0（C 若有则是真问题，由主流程报）
  expect('不误伤真实 baseUrl', () => {
    const got = checkContract(backend, { ...frontend, violationsA: [] }).filter(v => v.startsWith('B '))
    assert(got.length === 0, `真实 baseUrl 被判为不存在：${got[0] ?? ''}`)
  })

  return { problems, cases }
}

// ============================================================================

if (!statSync(SERVER).isDirectory() || !statSync(WEB).isDirectory()) {
  console.error(`✗ 找不到源码目录：${SERVER} 或 ${WEB}`)
  process.exit(1)
}

const backend = buildBackend()
const frontend = buildFrontend()

if (VERBOSE) {
  // 诊断放在自检之前：自检失败时也能看到解析结果，否则只能靠猜
  const bases = [...frontend.services].map(s => s.baseUrl).filter((v, i, a) => a.indexOf(v) === i).sort()
  console.log(`[诊断] 前端 baseUrl（${bases.length}）：${bases.join(', ')}`)
  console.log(`[诊断] 后端基路径（${backend.bases.size}）：${[...backend.bases].sort().join(', ')}`)
  const parents = [...new Set(frontend.services.map(s => s.extendsClass).filter(Boolean))].sort()
  for (const parent of parents) {
    const eps = [...frontend.endpointsOf(parent)].sort()
    console.log(`[诊断] ${parent} → ${eps.length} 个端点：${eps.join(', ') || '(空！解析失败)'}`)
  }
}

const { problems, cases } = selfCheck(backend, frontend)
if (problems.length) {
  for (const p of problems) {
    console.error(`✗ ${p}`)
  }
  process.exit(1)
}

const violations = checkContract(backend, frontend)

/**
 * Rule D（只披露、不失败）：前端基类链里有、但后端用 `@Extends(exclude=…)` 排除的端点。
 * 打过去不是 404 而是 HTTP 200 + `code=501 当前接口不支持此操作`（真机实测）。
 * 不当失败处理的原因：基类提供了 `delete()` 方法，不等于页面真的放了删除按钮 ——
 * 静态判不出"会不会被调用"，硬报会变成没人看的噪音。所以这里给出清单，
 * 让人在改页面动作时能对一眼（前端 errorPolicy 已把 501 渲染成「该模块未开放此操作」）。
 */
const refusedUsed = []
for (const svc of frontend.services) {
  for (const ep of frontend.endpointsOf(svc.extendsClass)) {
    if (backend.refused.has(`${svc.baseUrl}/${ep}`)) {
      refusedUsed.push(`${svc.baseUrl}/${ep}`)
    }
  }
}
const refusedUnique = [...new Set(refusedUsed)].sort()

console.log(`后端：${backend.controllerCount} 个 *Controller.java → ${backend.bases.size} 个基路径、${backend.routes.size} 条已注册路由、${backend.refused.size} 条被 @Extends 排除（运行时 501）`)
console.log(`前端：${frontend.fileCount} 个 ts/vue 文件 → ${frontend.services.length} 处 baseUrl、${frontend.tsClasses.size} 个类`)
console.log(`自检 ${cases.length} 项全部通过（三条规则都能咬人；后端零路径变量与 @Extends 排除集都与真机一致）`)
if (refusedUnique.length) {
  console.log(`披露：${refusedUnique.length} 个「前端基类有、后端已排除」的端点（调用得到 501，不是 404）`)
  if (VERBOSE) {
    console.log(`  · ${refusedUnique.join('  ')}`)
  }
}
if (violations.length) {
  const byRule = { A: 0, B: 0, C: 0 }
  for (const v of violations) {
    byRule[v[0]] += 1
  }
  console.error(`\n✗ ${violations.length} 处前后端契约偏差（A 路径后缀 ${byRule.A} / B baseUrl 不存在 ${byRule.B} / C 端点缺失 ${byRule.C}）：`)
  for (const v of violations) {
    console.error(`  - ${v}`)
  }
  console.error('\n  为什么要管：这类偏差单元测试看不见（它断言的是前端自己拼的字符串），')
  console.error('  演示模式也看不见（mock 会兼容），只有真机会 404，且 404 响应体没有 code/message，')
  console.error('  页面上表现为"点开就空白"。历史上已因此废掉过 getTreeList 与 getDetail/{id} 两轮。')
  process.exit(1)
}

console.log('✓ 前端拼出的每个端点都存在于后端路由表（无路径后缀、无悬空 baseUrl、无缺失端点）')

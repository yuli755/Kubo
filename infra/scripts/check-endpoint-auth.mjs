#!/usr/bin/env node
/**
 * HTTP 端点显式鉴权门禁
 * ============================================================================
 * 为什么要有这个脚本：airpower 的 `ApiController`（根类）**没有** `@Permission`，而
 * `PermissionUtil.getWhatNeedAccess` 的语义是「方法无注解、类也无注解 → 默认无需登录、
 * 无需授权」。于是「继承 `ApiController` 却漏写 `@Permission`」的控制器会静默退化成
 * 公开端点 —— 历史上一度真实发生：`IndexController`/`WecomController`（第十五批收口）、
 * `OauthController` 的 `unBindThird`（第二十一批收口，匿名 `{id:N}` 即可删除任意用户
 * 第三方登录绑定，是可直接利用的破坏性操作）。
 *
 * 这个脚本把「每个 HTTP 端点都必须有显式 `@Permission`」变成提交前硬门禁：
 *   - 只核对带 `@*Mapping` 注解的**真 HTTP 端点**（`beforeAdd`/`afterGetList` 等生命周期
 *     钩子与工具方法不是端点，不查）；
 *   - 类级 `@Permission` 覆盖该控制器所有方法；方法级 `@Permission` 覆盖单个方法；
 *   - 两者都没有的端点 → **失败**（依赖 airpower 隐式默认 = 无需登录）。
 * 注解在 `@*Mapping` 之上还是之下都认（本仓两种写法都有）；继承链在本仓内解析到
 * `BaseController`/`BaseBillController`（均有类级 `@Permission`）为止。
 *
 * 用法：node infra/scripts/check-endpoint-auth.mjs [--verbose]
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const SERVER = join(ROOT, 'SPMS-Server', 'src', 'main', 'java')
const VERBOSE = process.argv.includes('--verbose')

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) {
      walk(p, out)
    }
    else if (name.endsWith('Controller.java')) {
      out.push(p)
    }
  }
  return out
}

function stripComments(source) {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^[ \t]*\/\/.*$/gm, '')
}

const METHOD_SIG = /^\s*(public|private|protected)\s+(?!class\b)[\w<>?,.\[\]\s]+\s+\w+\s*\(/
const CLASS_DECL = /class\s+\w+Controller\b/

/**
 * 解析一个控制器文件：类名 / 父类 / 类级是否有 @Permission / 每个 @*Mapping 端点是否有
 * 方法级 @Permission。返回 null 表示不是控制器。
 */
function parseController(src, relFile) {
  const lines = stripComments(src).split('\n')

  // 类声明可能多行（泛型换行）：先找 `class XController`，再向下找 `extends Y`
  const classIdx = lines.findIndex(l => CLASS_DECL.test(l))
  if (classIdx < 0) {
    return null
  }
  const name = /class\s+(\w+Controller)\b/.exec(lines[classIdx])[1]
  // 父类：类体的开括号 `{` 所在行（单行声明=类名行，泛型多行= `> extends X {` 行）。
  // 不能从类名行直接找 `extends` —— 泛型上界 `E extends BaseEntity<E>` 会先匹配到。
  let openBraceIdx = -1
  for (let i = classIdx; i < Math.min(classIdx + 12, lines.length); i++) {
    if (/\{/.test(lines[i])) {
      openBraceIdx = i
      break
    }
  }
  if (openBraceIdx < 0) {
    return null
  }
  let parent = null
  for (let i = openBraceIdx; i >= classIdx; i--) {
    const m = /extends\s+(\w+)/.exec(lines[i])
    if (m) {
      parent = m[1]
      break
    }
  }
  if (!parent) {
    return null
  }

  // 从 endIdx 向上扫「注解块」到最近边界（上一个方法签名 / 类声明 / 顶层 } / ; / package / import）
  const blockHasPermission = (endIdx) => {
    for (let i = endIdx - 1; i >= 0; i--) {
      const t = lines[i].trim()
      if (t.includes('@Permission')) {
        return true
      }
      if (/^[};]/.test(t) || /^(package|import)\s/.test(t)) {
        return false
      }
      if (METHOD_SIG.test(lines[i]) || CLASS_DECL.test(lines[i])) {
        return false
      }
    }
    return false
  }

  const classPermission = blockHasPermission(classIdx)

  // 只收集带 @*Mapping 注解的真端点：从 @*Mapping 行向下找到它标注的方法签名
  const methods = []
  lines.forEach((line, mi) => {
    if (mi <= classIdx) {
      return
    }
    if (!/@(?:Get|Post|Put|Delete|Patch|Request)Mapping\b/.test(line)) {
      return
    }
    for (let j = mi + 1; j < lines.length; j++) {
      if (CLASS_DECL.test(lines[j]) || /^}/.test(lines[j].trim())) {
        return
      }
      if (METHOD_SIG.test(lines[j])) {
        const methodName = /^\s*(?:public|private|protected)\s+[\w<>?,.\[\]\s]+\s+(\w+)\s*\(/.exec(lines[j])[1]
        methods.push({ name: methodName, hasPermission: blockHasPermission(j), line: j + 1 })
        return
      }
    }
  })

  return { name, parent, classPermission, methods, file: relFile }
}

/** 本仓内沿继承链查找类级 @Permission（jar 里的父类视为「无类级注解」，交给方法级兜底） */
function resolveClassPermission(className, map) {
  let cur = map.get(className)
  const seen = new Set()
  while (cur && !seen.has(cur.name)) {
    seen.add(cur.name)
    if (cur.classPermission) {
      return true
    }
    cur = map.get(cur.parent)
  }
  return false
}

// ============================================================================
// 门禁自检：证明判据真的能咬人（未标注端点必须标红、已标注的不误伤、泛型换行不误判）
// ============================================================================
function selfCheck() {
  const problems = []
  const analyze = (src) => {
    const c = parseController(src, 'selfcheck.java')
    if (!c) {
      return { c: null, violations: [] }
    }
    const covered = c.classPermission || resolveClassPermission(c.name, new Map([[c.name, c]]))
    const violations = c.methods.filter(m => !m.hasPermission && !covered)
    return { c, covered, violations }
  }

  // 1：继承 ApiController、无类级、一个无注解端点 → 必须被标出
  const r1 = analyze(`
class BadController extends ApiController {
    @PostMapping("leak")
    public Json leak() { return null; }
}
`)
  if (!r1.c || r1.violations.length !== 1 || r1.violations[0].name !== 'leak') {
    problems.push('自检 1 失败：未标注端点未被识别')
  }

  // 2：显式 login=false 的方法 → 不算未标注
  const r2 = analyze(`
class OkController extends ApiController {
    @Permission(login = false)
    @PostMapping("pub")
    public Json pub() { return null; }
}
`)
  if (!r2.c || r2.violations.length !== 0) {
    problems.push('自检 2 失败：显式 login=false 被误判为未标注')
  }

  // 3：无类级、方法级 @Permission → 不算未标注
  const r3 = analyze(`
class Ok3Controller extends ApiController {
    @PostMapping("x")
    @Permission(authorize = false)
    public Json x() { return null; }
}
`)
  if (!r3.c || r3.violations.length !== 0) {
    problems.push('自检 3 失败：方法级注解未被识别')
  }

  // 4：多行泛型类声明（BaseController 形态）+ 类级 @Permission → 类级必须被识别、不误报
  const r4 = analyze(`
@Permission
public class GenericController<
        E extends BaseEntity<E>
        > extends CurdController<E> {
    @PostMapping("y")
    public Json y() { return null; }
}
`)
  if (!r4.c || !r4.covered || r4.violations.length !== 0) {
    problems.push('自检 4 失败：多行泛型类声明的类级 @Permission 未被识别')
  }

  // 5：生命周期钩子（无 @*Mapping）不该被当端点查
  const r5 = analyze(`
class HookController extends ApiController {
    protected MaterialEntity beforeAdd(MaterialEntity e) { return e; }
}
`)
  if (!r5.c || r5.violations.length !== 0) {
    problems.push('自检 5 失败：生命周期钩子被误当端点')
  }

  return problems
}

const files = walk(SERVER)
if (!files.length) {
  console.error('✗ 没扫到任何 *Controller.java —— 门禁空转')
  process.exit(1)
}

const controllers = []
for (const f of files) {
  const c = parseController(readFileSync(f, 'utf8'), relative(ROOT, f))
  if (c) {
    controllers.push(c)
  }
}
if (!controllers.length) {
  console.error('✗ 没解析到任何控制器 —— 解析逻辑失效')
  process.exit(1)
}

const map = new Map(controllers.map(c => [c.name, c]))
const violations = []
for (const c of controllers) {
  const classCovered = resolveClassPermission(c.name, map)
  for (const m of c.methods) {
    if (!m.hasPermission && !classCovered) {
      violations.push(`${c.file}:${m.line}  ${c.name}.${m.name} 缺显式 @Permission（继承 ${c.parent}，类级亦无）`)
    }
  }
}

const selfProblems = selfCheck()
if (selfProblems.length) {
  for (const p of selfProblems) {
    console.error(`✗ ${p}`)
  }
  process.exit(1)
}

const endpointCount = controllers.reduce((n, c) => n + c.methods.length, 0)
console.log(`已核对 ${controllers.length} 个控制器、${endpointCount} 个 HTTP 端点方法`)
if (VERBOSE) {
  for (const c of controllers) {
    if (c.classPermission || resolveClassPermission(c.name, map)) {
      console.log(`  ✓ 类级鉴权覆盖：${c.name}`)
    }
    else {
      console.log(`  · 逐方法鉴权：${c.name}（${c.methods.length} 端点）`)
    }
  }
}

if (violations.length) {
  console.error(`\n✗ ${violations.length} 个 HTTP 端点缺显式 @Permission（airpower 语义下 = 无需登录，公开暴露）：`)
  for (const v of violations) {
    console.error(`  - ${v}`)
  }
  console.error('\n  修复：给方法补 @Permission(login=false|authorize=false|默认) 或给类补类级 @Permission。')
  process.exit(1)
}

console.log('✓ 所有 HTTP 端点均有显式 @Permission（类级或方法级），无依赖 airpower 隐式默认的公开面')

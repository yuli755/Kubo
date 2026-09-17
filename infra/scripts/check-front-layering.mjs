/**
 * # 前端分层门禁（`pnpm front:layer:check`）
 *
 * 后端有 `check-layering.mjs`（L0–L7），前端此前只有"目录看着挺整齐"。
 * 这份脚本把前端的层次也变成机器判据 —— 起因是实测发现：
 * `framework/http.ts` 会去 import `api/axios`（框架层依赖应用装配面），而这类依赖
 * **编译期一切正常**，只是把"framework 是库还是胶水"这个问题变得没有答案。
 *
 * ## 层（自下而上）
 *
 * | 层 | 目录 | 允许依赖 |
 * | --- | --- | --- |
 * | 纯工具 | `utils` / `types` | 只允许 `utils` / `types`（零出向） |
 * | 数据层 | `base` / `model` | 纯工具 + `framework`（模型基类）；**不知道 UI** |
 * | 框架层 | `framework` | 框架内部 + `component` / `base` / 纯工具 / `model`；**不认识应用面**（view/api/stores/config/router/composables/hook/guard/demo/pwa） |
 * | 视图层 | `view` | 任意层（叶子） |
 * | 组合根 | `main.ts` / `App.vue` / `router/**` / `config/routes*` | 任意层（路由表与挂载点必然认识视图） |
 *
 * ## 规则
 *
 * - **LF1**：非组合根文件不得 import `view/**`（视图是叶子）
 * - **LF2**：`framework/**` 不得 import 应用面（view/api/stores/config/router/composables/hook/guard/demo/pwa）
 * - **LF3**：数据层（`base/**` / `model/**`）不得 import `view` / `component` / `composables` / `hook`；**模型不知道 UI**
 * - **LF4**：`utils/**` / `types/**` 零出向（只许互相依赖）
 *
 * ## 判据口径（两个坑，都是实测踩过的）
 *
 * 1. **注释不算依赖**：`composables/useRoutePreload.ts` 的 JSDoc 示例里写着
 *    `preload(() => import('@/view/home.vue'))`，按文本扫会报出一条**假违规** ——
 *    文档里举例子正是希望读者这么用，它不该让门禁变红。所以扫描前先剥注释。
 * 2. **`.vue` 只看 `<script>`**：`<style>` 里的 `@import '@/styles/x.css'` 也长得像 import
 *    （正则同样命中），把它当依赖会得到一堆假边。
 *
 * ## 白名单纪律
 *
 * 与后端同一套：条目必须**仍然存在**（代码改了却忘删 = 红）、条目数**只许降**（`MAX_ALLOWLIST`）。
 * 现在的 2 条是 `framework/**` 与 `api/axios` 之间那处**刻意**的耦合（见各条理由）。
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SRC = path.join(ROOT, 'src')

/**
 * 组合根：挂载点与路由表必然认识视图（它们的工作就是把路径接到组件上）
 */
const COMPOSITION_ROOTS = ['main.ts', 'App.vue']
function isCompositionRoot(rel) {
  return COMPOSITION_ROOTS.includes(rel)
    || rel.startsWith('router/')
    || rel.startsWith('config/routes')
}

/** `framework/**` 不许认识的应用面 */
const APP_FACE = ['view', 'api', 'stores', 'config', 'router', 'composables', 'hook', 'guard', 'demo', 'pwa']

/** 数据层：实体基类与单据基类（`base`）+ 业务模型（`model`）—— 都不知道 UI */
const DATA_LAYERS = ['base', 'model']

/** 数据层不许认识的 UI 层 */
const UI_LAYERS = ['view', 'component', 'composables', 'hook']

/** `utils` / `types` 只许互相依赖 */
const PURE_LAYERS = ['utils', 'types']

/**
 * 白名单：`framework/**` 与 `api/axios` 之间那处刻意耦合。
 *
 * 为什么留着而不是拆：框架的请求出口**必须**是应用那个统一实例 —— 拦截器、租户头、
 * 401 刷新、演示模式适配器都挂在它上面（`framework/http.ts` 头注释里写明了）。
 * 拆成注入（`configureHttp(request)`）会让"接线漏了"变成运行期静默无请求，
 * 而本仓当前没有浏览器级回归测试兜底 —— 先登记、只许降。
 */
const ALLOWLIST = [
  ['framework/http.ts', 'api/axios', '框架的请求出口必须是应用实例（鉴权/租户/401/演示适配器都挂在它上面）'],
  ['framework/upload.ts', 'api/axios', '同上：上传走同一实例，演示模式下不会真的发请求'],
].map(([file, target, reason]) => ({ file: `src/${file}`, target, reason }))

/** 条目数上限（**只许降**） */
const MAX_ALLOWLIST = 2

/**
 * 剥掉注释与字符串：TS/JS 的注释与字符串都可能长得像依赖（见文件头口径①）
 *
 * 不用正则一把梭：`//` 出现在字符串里、`'` 出现在注释里都会让文本扫描错位。
 */
export function stripTsComments(code) {
  let out = ''
  let i = 0
  const n = code.length
  while (i < n) {
    const c = code[i]
    if (c === '/' && code[i + 1] === '/') {
      const j = code.indexOf('\n', i)
      i = j < 0 ? n : j
    }
    else if (c === '/' && code[i + 1] === '*') {
      const j = code.indexOf('*/', i + 2)
      i = j < 0 ? n : j + 2
    }
    else if (c === '\'' || c === '"' || c === '`') {
      const quote = c
      out += c
      i += 1
      while (i < n && code[i] !== quote) {
        if (code[i] === '\\') {
          out += code[i]
          i += 1
        }
        if (i < n) {
          out += code[i]
          i += 1
        }
      }
      if (i < n) {
        out += code[i]
        i += 1
      }
    }
    else {
      out += c
      i += 1
    }
  }
  return out
}

/** `.vue` 只取 `<script>` 块（口径②：`<style>` 里的 @import 不是依赖） */
function scriptOf(rel, code) {
  if (!rel.endsWith('.vue')) return code
  const blocks = [...code.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)].map(m => m[1])
  return blocks.length > 0 ? blocks.join('\n') : ''
}

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name === '__tests__' || entry.name === 'node_modules') continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) walk(full, acc)
    else if (entry.name.endsWith('.ts') || entry.name.endsWith('.vue')) acc.push(full)
  }
  return acc
}

/** 依赖说明符 → `src/` 下的相对路径；返回 null 表示不是本仓模块 */
function resolveSpecifier(fromFile, spec) {
  let target = null
  if (spec.startsWith('@/')) target = path.join(SRC, spec.slice(2))
  else if (spec.startsWith('.')) target = path.resolve(path.dirname(fromFile), spec)
  else return null
  const rel = path.relative(SRC, target)
  if (rel.startsWith('..')) return null
  return rel.split(path.sep).join('/')
}

const files = walk(SRC)
const violations = []
const allowlistSeen = new Set()

for (const abs of files) {
  const rel = path.relative(SRC, abs).split(path.sep).join('/')
  const home = rel.includes('/') ? rel.split('/')[0] : '(root)'
  const script = scriptOf(rel, fs.readFileSync(abs, 'utf8'))
  const code = stripTsComments(script)
  const specs = [
    ...code.matchAll(/(?:from|import)\s*\(?\s*['"]([^'"]+)['"]/g),
  ].map(m => m[1])

  for (const spec of specs) {
    const target = resolveSpecifier(abs, spec)
    if (!target) continue
    const targetTop = target.includes('/') ? target.split('/')[0] : '(root)'
    if (targetTop === home) continue

    const hit = (rule) => {
      const entry = ALLOWLIST.find(a => a.file === `src/${rel}` && target.startsWith(a.target))
      if (entry) {
        allowlistSeen.add(`${entry.file}|${entry.target}`)
        return
      }
      violations.push({
        rule,
        file: `src/${rel}`,
        type: 'import',
        detail: spec,
      })
    }

    if (home !== 'view' && targetTop === 'view' && !isCompositionRoot(rel)) {
      hit('LF1')
    }
    if (home === 'framework' && APP_FACE.includes(targetTop)) {
      hit('LF2')
    }
    if (DATA_LAYERS.includes(home) && UI_LAYERS.includes(targetTop)) {
      hit('LF3')
    }
    if (PURE_LAYERS.includes(home) && !PURE_LAYERS.includes(targetTop)) {
      hit('LF4')
    }
  }
}

const RULE_DESC = {
  LF1: '视图是叶子：非组合根文件不得 import view/**',
  LF2: '框架层是库：framework/** 不得 import 应用面（view/api/stores/config/router/…）',
  LF3: '模型不知道 UI：base/** 与 model/** 不得 import view/component/composables/hook',
  LF4: '纯工具零出向：utils/** 与 types/** 只许互相依赖',
}

const stale = ALLOWLIST.filter(a => !allowlistSeen.has(`${a.file}|${a.target}`))
const tooMany = ALLOWLIST.length > MAX_ALLOWLIST
const failed = violations.length > 0 || stale.length > 0 || tooMany

if (process.argv.includes('--json')) {
  console.log(JSON.stringify({ files: files.length, violations, allowlist: ALLOWLIST.length, stale }, null, 2))
}
else {
  console.log('前端分层门禁：工具/模型/框架/视图 + 组合根（LF1–LF4）')
  console.log(`  扫描：${files.length} 个 .ts/.vue（src 下，剥注释、.vue 只取 <script>）`)
  console.log(`  白名单：${ALLOWLIST.length}/${MAX_ALLOWLIST} 条（framework → api/axios 的刻意耦合，只许降）`)
  if (violations.length === 0) {
    console.log('✓ 前端的层次没有越界')
  }
  else {
    console.log(`✗ ${violations.length} 处违规：`)
    for (const v of violations) {
      console.log(`  [${v.rule}] ${v.file}  ${v.detail}   —— ${RULE_DESC[v.rule]}`)
    }
  }
  if (stale.length > 0) {
    console.log(`✗ ${stale.length} 条白名单已过期（代码已经不长这样了，请删掉白名单条目）：`)
    for (const s of stale) console.log(`  ${s.file} → ${s.target}`)
  }
  if (tooMany) console.log(`✗ 白名单条目数 ${ALLOWLIST.length} > 上限 ${MAX_ALLOWLIST}：只许降。`)
  if (!failed) {
    console.log('  层次：utils/types → base/model → framework → view（组合根：main.ts / App.vue / router / config/routes）')
  }
}

if (process.argv.includes('--check') && failed) process.exit(1)

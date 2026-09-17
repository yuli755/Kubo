#!/usr/bin/env node
/**
 * # 当前前端技术栈一览（一条命令看清"装了什么、用在哪、哪些是待退场的"）
 *
 * 为什么需要它：技术栈分散在 `package.json`、`vite.config.mts`、`vitest.config.ts`、`eslint.config.js`
 * 和几十个 `import` 里，"现在到底跑的是什么"每次都要重新翻。这份报表把它固定下来：
 *
 * 1. **运行时/构建依赖**：声明版本、实际安装版本、在仓库里的引用点、归属（目标栈 / 过渡期 / 开发工具）
 * 2. **死重量**：全仓没有任何引用的依赖、`public/` 里没人用的资源（这些是"纯删"的对象）
 * 3. **airpower 退场进度**：与 `pnpm airpower` 同口径
 * 4. **体积**：`dist/size-report.json`（先跑 `pnpm build && pnpm size`）
 *
 * 用法：
 * ```
 * pnpm stack            # 人类可读
 * pnpm stack --json     # 给 CI/脚本用
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const ROOT = path.resolve(import.meta.dirname, '..')
const REL = p => path.relative(ROOT, p)

/** 扫哪些目录/文件里的 import 算"用了" */
const CODE_EXT = /\.(?:ts|mts|cts|js|mjs|cjs|vue|scss|css|html|json|ya?ml)$/
const SKIP_DIRS = new Set(['node_modules', 'dist', '.git', '.vite', 'coverage', '.arena', 'build', 'out'])
/** 文档也扫，但只作为弱证据（不计入"没用"的判定） */
const DOC_EXT = /\.md$/

function walk(dir, out = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (SKIP_DIRS.has(entry.name))
      continue
    const full = path.join(dir, entry.name)
    if (entry.isDirectory())
      walk(full, out)
    else out.push(full)
  }
  return out
}

const allFiles = walk(ROOT)
// package.json 只是"声明"，不算"使用"的证据；pnpm-lock 同理
// 本脚本自己也不能算证据：ROLE 表里写着所有包名，会把"用在哪"全指向自己（第一次跑就中招了）
const SELF = REL(path.join(ROOT, 'scripts/frontend-stack.mjs'))
const codeFiles = allFiles.filter(f => CODE_EXT.test(f) && !f.endsWith('pnpm-lock.yaml') && !f.endsWith('package.json') && REL(f) !== SELF)
const docFiles = allFiles.filter(f => DOC_EXT.test(f))

/**
 * 归属表：把"这个依赖是什么身份"写死，比自动猜可靠。
 * `core` = 目标栈；`transition` = 过渡期（F5 删）；`dev` = 构建/测试工具。
 */
const ROLE = {
  'vue': ['core', '视图层'],
  'vue-router': ['core', '路由'],
  'pinia': ['core', '状态（persist 用 flatted 序列化）'],
  'element-plus': ['core', 'UI 库（经 types/element-plus-aggregate 按需聚合）'],
  '@element-plus/icons-vue': ['core', '图标'],
  '@tanstack/vue-query': ['core', '数据层（useTable / useDetail）'],
  'axios': ['core', 'HTTP（framework/http.ts 与 airpower Http 共用实例）'],
  'echarts': ['core', '看板图表'],
  'flatted': ['core', 'pinia 持久化序列化'],
  '@airpower/web': ['transition', '旧栈，F4 逐域迁完即删（F5）'],
  '@vitejs/plugin-vue': ['dev', 'Vue SFC 编译'],
  'vite': ['dev', '构建/开发服务器'],
  'vite-plugin-pwa': ['dev', 'PWA（离线缓存）'],
  'unplugin-vue-components': ['dev', 'Element Plus 组件按需自动注册'],
  'typescript': ['dev', '类型检查'],
  'vue-tsc': ['dev', '模板+脚本类型检查'],
  'vitest': ['dev', '单元测试'],
  '@vue/test-utils': ['dev', '组件挂载'],
  '@vitest/coverage-v8': ['dev', '覆盖率（隐式引用，勿删）'],
  'jsdom': ['dev', '测试 DOM 环境（隐式引用，勿删）'],
  'sass': ['dev', 'SCSS 编译'],
  'eslint': ['dev', 'Lint（CLI）'],
  '@antfu/eslint-config': ['dev', 'Lint 规则集'],
  '@types/node': ['dev', '构建脚本类型（tsconfig types）'],
  'openapi-typescript-codegen': ['dev', '按后端 /v3/api-docs 生成 src/api（generate-api.mjs 里以 npx 调用）'],
}

/** 这些包由 CLI / 工具按名字隐式使用，字符串扫不到，不算死重量 */
const IMPLICIT = new Set(['@vitest/coverage-v8', 'jsdom', 'eslint', 'vue-tsc', 'typescript', 'vite', '@types/node'])

const pkgJson = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8'))
const sections = [
  ['dependencies', pkgJson.dependencies ?? {}],
  ['devDependencies', pkgJson.devDependencies ?? {}],
]

const codeText = codeFiles.map(f => ({ file: REL(f), text: fs.readFileSync(f, 'utf8') }))
const docText = docFiles.map(f => ({ file: REL(f), text: fs.readFileSync(f, 'utf8') }))

function countHits(name) {
  const escaped = name.replace(/[/\\^$*+?.()|[\]{}]/g, '\\$&')
  // `'pkg'` / `"pkg"` / `"pkg/sub"` / npx pkg / pnpm add pkg
  const re = new RegExp(`(['"\`]${escaped}(?:/[^'"\`]*)?['"\`]|\\bnpx ${escaped}\\b|\\b${escaped}\\b\\s*[:=]\\s*['"\`]~?\\^?\\d)`)
  const code = codeText.filter(x => re.test(x.text))
  const docs = docText.filter(x => re.test(x.text))
  return { code, docs }
}

/**
 * 引用点排序：源码 > 构建/测试配置 > 脚本与其他。
 * 目的只是让"用在哪"这一列可读 —— 计数不受影响。
 */
function rank(hits) {
  const weight = (file) => {
    if (file.startsWith('src/'))
      return 0
    if (/^(?:vite|vitest|eslint)\.config/.test(file) || file === 'index.html')
      return 1
    if (file.startsWith('infra/'))
      return 2
    return 3
  }
  return [...hits].sort((a, b) => weight(a.file) - weight(b.file) || a.file.localeCompare(b.file))
}

function installedVersion(name) {
  try {
    const manifest = JSON.parse(fs.readFileSync(path.join(ROOT, 'node_modules', name, 'package.json'), 'utf8'))
    return manifest.version
  }
  catch {
    return null
  }
}

const rows = []
const dead = []
for (const [kind, deps] of sections) {
  for (const [name, declared] of Object.entries(deps)) {
    const { code, docs } = countHits(name)
    const [role, why] = ROLE[name] ?? ['core', '（未登记，建议补 ROLE 说明）']
    const row = {
      name,
      kind,
      declared,
      installed: installedVersion(name),
      role,
      why,
      refs: code.length,
      // 展示顺序：先看 src/** 的真实用法，再看构建/配置文件，免得被测试注释带偏
      sample: rank(code).slice(0, 2).map(x => x.file),
      docRefs: docs.length,
    }
    rows.push(row)
    if (code.length === 0 && !IMPLICIT.has(name) && docs.length === 0)
      dead.push(row)
  }
}

// public/ 里没人引用的静态资源
const publicDir = path.join(ROOT, 'public')
const publicFiles = fs.existsSync(publicDir) ? walk(publicDir) : []
const referenced = new Set(codeText.map(x => x.text).join('\n').match(/[\w./-]+\.(?:png|jpg|jpeg|svg|ico|webp|gif)/g) ?? [])
const deadPublic = publicFiles
  .map(f => REL(f))
  .filter(f => !referenced.has(path.basename(f)) && !f.endsWith('robots.txt'))

// airpower 退场进度（与 scripts/find-airpower.mjs 同口径：剥注释后的真实 import）
function stripComments(code) {
  let out = ''
  let state = 'code'
  for (let i = 0; i < code.length; i += 1) {
    const c = code[i]
    const next = code[i + 1]
    if (state === 'code') {
      if (c === '/' && next === '/') {
        state = 'line'
        i += 1
        continue
      }
      if (c === '/' && next === '*') {
        state = 'block'
        i += 1
        continue
      }
      if (c === '\'' || c === '"' || c === '`')
        state = c === '\'' ? 'single' : (c === '"' ? 'double' : 'template')
    }
    else if (state === 'line') {
      if (c === '\n') {
        state = 'code'
        out += c
      }
      continue
    }
    else if (state === 'block') {
      if (c === '*' && next === '/') {
        state = 'code'
        i += 1
      }
      continue
    }
    else if (c === '\\') {
      out += c + (next ?? '')
      i += 1
      continue
    }
    else if ((state === 'single' && c === '\'') || (state === 'double' && c === '"') || (state === 'template' && c === '`')) {
      state = 'code'
    }
    out += c
  }
  return out
}

const srcFiles = codeFiles.filter(f => REL(f).startsWith('src/'))
let airpowerFiles = 0
let airpowerSpecFiles = 0
for (const file of srcFiles) {
  const code = stripComments(fs.readFileSync(file, 'utf8'))
  const hit = /(?:^|\n)\s*(?:import|export)[\s\S]{0,400}?from\s*['"]@airpower\/(?:web|core)['"/]/.test(code)
    || /\bimport\s*\(\s*['"]@airpower\/(?:web|core)['"/]/.test(code)
  if (!hit)
    continue
  if (REL(file).includes('__tests__') || file.endsWith('.spec.ts'))
    airpowerSpecFiles += 1
  else airpowerFiles += 1
}

// 体积（可选）
let sizeReport = null
try {
  sizeReport = JSON.parse(fs.readFileSync(path.join(ROOT, 'dist/size-report.json'), 'utf8'))
}
catch {
  sizeReport = null
}

const result = {
  generatedAt: new Date().toISOString(),
  node: process.version,
  packageManager: pkgJson.packageManager,
  rows,
  deadDependencies: dead.map(d => d.name),
  deadPublicAssets: deadPublic,
  airpower: { productionFiles: airpowerFiles, testFiles: airpowerSpecFiles },
  size: sizeReport,
}

if (process.argv.includes('--json')) {
  console.log(JSON.stringify(result, null, 2))
  process.exit(0)
}

const roleLabel = { core: '目标栈', transition: '过渡期', dev: '开发工具' }

console.log(`\n🧱 当前前端技术栈（node ${process.version} · ${pkgJson.packageManager}）\n`)

for (const [kind, title] of [['dependencies', '运行时依赖'], ['devDependencies', '构建 / 测试依赖']]) {
  console.log(`── ${title} ${'─'.repeat(Math.max(0, 60 - title.length))}`)
  console.log('  包                          声明        实际       身份      引用  用在哪')
  for (const row of rows.filter(r => r.kind === kind)) {
    const name = row.name.padEnd(28)
    const declared = String(row.declared).padEnd(11)
    const installed = String(row.installed ?? '—').padEnd(10)
    const role = (roleLabel[row.role] ?? row.role).padEnd(8)
    const refs = String(row.refs).padStart(3)
    console.log(`  ${name} ${declared} ${installed} ${role} ${refs}   ${row.sample[0] ?? row.why}`)
  }
  console.log('')
}

console.log('── 退场与死重量 ────────────────────────────────────────────')
console.log(`  airpower 仍在 import：${airpowerFiles} 个生产文件${airpowerSpecFiles ? `（另 ${airpowerSpecFiles} 个测试文件）` : ''}`)
console.log(`  没有任何引用的依赖：${dead.length === 0 ? '无 ✅' : dead.map(d => d.name).join(', ')}`)
console.log(`  public/ 里没人用的资源：${deadPublic.length === 0 ? '无 ✅' : deadPublic.join(', ')}`)

if (sizeReport) {
  const entry = sizeReport.entryGzipKB
  const total = sizeReport.totalGzipKB
  console.log(`  体积：首屏 ${entry}/${sizeReport.entryBudgetKB} KB · 总体 ${total}/${sizeReport.totalBudgetKB} KB（dist/size-report.json，${sizeReport.pass ? '达标' : '超预算'}）`)
}
else {
  console.log('  体积：无 dist/size-report.json（先跑 `pnpm build && pnpm size:report`）')
}
console.log('\n  口径：引用数 = 仓库内（源码/配置/文档除外）字符串命中；隐式引用（CLI/环境）见脚本 IMPLICIT。')
console.log('  每批迁移后跑一次，数字写进 docs/前端技术栈现状.md。\n')

#!/usr/bin/env node
/**
 * # 构建产物体积门禁（M0 硬门槛）
 *
 * 规划文档写了「gzip < 800KB」但从未测量过 —— 没测量就没门禁。
 * 而且那 800KB 本身有歧义：首屏闭包和全部分包是两个量级完全不同的东西。
 * 本脚本把两者分开测：
 *
 * | 指标            | 含义                                   | 默认预算 |
 * |-----------------|----------------------------------------|----------|
 * | entry（首屏闭包）| index.html 静态引用 + 其递归静态 import | 450 KB   |
 * | total（全产物） | dist 下全部 js/css/html                 | 850 KB   |
 *
 * 动态 `import()` 视为懒加载，不计入首屏闭包。
 *
 * 用法：
 *   pnpm size
 *   node infra/scripts/check-bundle-size.mjs --entry-budget=450 --total-budget=850 --max-chunk-gzip=300
 *   pnpm size -- --json          # 供 CI 上传 / 写 PR comment
 *
 * 退出码：0 达标；1 超预算；2 dist 不存在（先 build）。
 */
import { gzipSync } from 'node:zlib'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, extname, join, resolve } from 'node:path'

const argv = process.argv.slice(2)
const num = (name, dflt) => {
  const hit = argv.find(a => a.startsWith(`--${name}=`))
  return hit ? Number(hit.split('=')[1]) : dflt
}
const str = (name, dflt) => {
  const hit = argv.find(a => a.startsWith(`--${name}=`))
  return hit ? hit.split('=').slice(1).join('=') : dflt
}
const asJson = argv.includes('--json')

const DIST = resolve(process.cwd(), str('dist', 'dist'))
const ENTRY_BUDGET_KB = num('entry-budget', 450)
const TOTAL_BUDGET_KB = num('total-budget', 850)
const MAX_CHUNK_KB = num('max-chunk-gzip', 300)
const COUNTED = new Set(['.js', '.css', '.html'])

const kb = n => Number((n / 1024).toFixed(1))
const gzipSize = file => gzipSync(readFileSync(file), { level: 9 }).length

function walk(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const full = join(dir, entry)
    return statSync(full).isDirectory() ? walk(full) : [full]
  })
}

/**
 * 从 index.html 出发求首屏静态闭包。
 * 只跟随静态 `import ... from "./x"` / `export ... from "./x"`，跳过动态 `import()`。
 */
function entryClosure(distDir) {
  const html = readFileSync(join(distDir, 'index.html'), 'utf8')
  const seeds = [...html.matchAll(/(?:src|href)="([^"]+\.(?:js|css))"/g)]
    .map(m => resolve(distDir, m[1].replace(/^\/+/, '')))
  const IMPORT_RE = /(?:^|\n)\s*(?:import|export)\s[^;\n]*?from\s*["'](\.[^"']+)["']/g
  const seen = new Set()
  const queue = [...seeds]
  while (queue.length > 0) {
    const file = queue.pop()
    if (seen.has(file) || !existsSync(file) || !statSync(file).isFile()) {
      continue
    }
    seen.add(file)
    if (!file.endsWith('.js')) {
      continue
    }
    for (const m of readFileSync(file, 'utf8').matchAll(IMPORT_RE)) {
      queue.push(resolve(dirname(file), m[1]))
    }
  }
  return [...seen]
}

if (!existsSync(DIST)) {
  console.error(`❌ 未找到构建产物目录：${DIST}（先执行 pnpm build）`)
  process.exit(2)
}

const files = walk(DIST)
  .filter(f => COUNTED.has(extname(f)))
  .map(f => ({ file: f.slice(DIST.length + 1), gzip: gzipSize(f) }))
  .sort((a, b) => b.gzip - a.gzip)

const closure = entryClosure(DIST).map(f => ({ file: f.slice(DIST.length + 1), gzip: gzipSize(f) }))
  .sort((a, b) => b.gzip - a.gzip)

const totalGzip = files.reduce((s, f) => s + f.gzip, 0)
const entryGzip = closure.reduce((s, f) => s + f.gzip, 0)
const oversize = files.filter(f => f.gzip / 1024 > MAX_CHUNK_KB)
const overEntry = entryGzip / 1024 > ENTRY_BUDGET_KB
const overTotal = totalGzip / 1024 > TOTAL_BUDGET_KB
const failed = overEntry || overTotal

if (asJson) {
  console.log(JSON.stringify({
    generatedAt: new Date().toISOString(),
    fileCount: files.length,
    entryGzipKB: kb(entryGzip),
    entryBudgetKB: ENTRY_BUDGET_KB,
    totalGzipKB: kb(totalGzip),
    totalBudgetKB: TOTAL_BUDGET_KB,
    maxChunkGzipKB: MAX_CHUNK_KB,
    pass: !failed,
    oversizeChunks: oversize.map(f => ({ file: f.file, gzipKB: kb(f.gzip) })),
    entryFiles: closure.map(f => ({ file: f.file, gzipKB: kb(f.gzip) })),
  }, null, 2))
}
else {
  console.log(`\n📦 产物体积（gzip）  首屏预算 ${ENTRY_BUDGET_KB} KB · 总体预算 ${TOTAL_BUDGET_KB} KB · 单文件上限 ${MAX_CHUNK_KB} KB\n`)
  console.log(`  首屏闭包 ${kb(entryGzip)} KB（${closure.length} 个文件，静态 import 递归）`)
  for (const f of closure) {
    console.log(`      ${String(kb(f.gzip)).padStart(7)} KB  ${f.file}`)
  }
  console.log(`\n  全部产物 ${kb(totalGzip)} KB（${files.length} 个文件，含懒加载分包）`)
  for (const f of files.filter(x => !closure.some(c => c.file === x.file)).slice(0, 8)) {
    console.log(`      ${String(kb(f.gzip)).padStart(7)} KB  ${f.file}  (懒加载)`)
  }
  if (oversize.length > 0) {
    console.log(`\n⚠️  超过单文件上限（建议 manualChunks 细分）：`)
    for (const f of oversize) {
      console.log(`      ${String(kb(f.gzip)).padStart(7)} KB  ${f.file}`)
    }
  }
  const verdict = failed ? '❌ 超预算' : '✅ 达标'
  console.log(`\n${verdict}  首屏 ${kb(entryGzip)}/${ENTRY_BUDGET_KB} KB · 总体 ${kb(totalGzip)}/${TOTAL_BUDGET_KB} KB\n`)
}

process.exit(failed ? 1 : 0)

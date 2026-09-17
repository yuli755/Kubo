#!/usr/bin/env node
/**
 * 前端「没人引用」的文件扫描器
 *
 * ## 为什么需要它
 * 换底座/砍功能之后，仓库里会留下一批**没有任何 import 指向**的文件：删掉的域的残留、
 * 被新组件替代的旧适配器、只在某个历史页里用过的工具。它们不影响构建（不 import 就不进 bundle），
 * 所以 CI 与体积门禁都发现不了 —— 只能靠静态引用图找出来。
 *
 * ## 判定口径（保守）
 * 一个文件被判定为「未被引用」，必须同时满足：
 * 1. 没有任何文件 `import` / `require` 它（相对路径、`@/` 别名、`@kubo/shared` 都解析）；
 * 2. 它的**路径片段**没有以字符串形式出现在任何源码里（视图页是靠路由/菜单里的
 *    `'system/unit/list'` 这种字符串被懒加载的，字符串匹配是防漏判的关键）；
 * 3. 不是入口（`src/main.ts`、`index.html` 引用）、不是配置、不是 `.d.ts`、不是测试。
 *
 * 用法：
 *   node scripts/find-unused-frontend.mjs            # 只报告
 *   node scripts/find-unused-frontend.mjs --check    # CI 模式：有死件则退出码 1
 *   node scripts/find-unused-frontend.mjs --delete   # 报告并删除（谨慎：先看报告）
 */

import { existsSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const SRC = join(ROOT, 'src')
const SHARED = join(ROOT, 'packages/shared/src')

/** 扫描范围（只看会进产物的源码，脚本与文档不在其列） */
const SCAN_ROOTS = [SRC, SHARED].filter(existsSync)
const EXTENSIONS = ['.ts', '.vue', '.mts', '.js']
/**
 * 入口：从这些文件开始走引用图。
 *
 * ⚠️ 构建配置必须算入口：`vite.config.mts` 用**字符串**引用了
 * `src/types/element-plus-aggregate.ts`（`resolve.alias` 指向它），
 * 不算进来的话那个文件会被判成死件 —— 删掉就是打包直接崩。
 */
const ENTRIES = [
  join(SRC, 'main.ts'),
  join(ROOT, 'index.html'),
  join(ROOT, 'vite.config.mts'),
  join(ROOT, 'vitest.config.ts'),
  join(ROOT, 'package.json'),
]
/** 额外参与「字符串引用」判定但不参与删除的目录（构建脚本、CI 脚本） */
const CORPUS_DIRS = [join(ROOT, 'infra'), join(ROOT, 'scripts')]

function walk(dir) {
  const out = []
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (['node_modules', 'dist', '__snapshots__'].includes(entry.name))
        continue
      out.push(...walk(full))
    }
    else if (EXTENSIONS.includes(entry.name.slice(entry.name.lastIndexOf('.')))) {
      out.push(full)
    }
  }
  return out
}

const files = SCAN_ROOTS.flatMap(walk)
const fileSet = new Set(files)

/** 路径 → 文件内容（每个文件读一次） */
const sources = new Map(files.map(file => [file, readFileSync(file, 'utf8')]))

/**
 * 「路径字符串」判定语料：源码 + 根配置 + infra/scripts。
 * 任何一处以字符串写下某个路径，都算引用（构建别名、CI 检查清单、文档脚本都可能引用源码）。
 *
 * ⚠️ **测试文件不进这份语料**（但它们仍参与 import 图 —— 测试 import 的模块当然算在用）。
 * 原因：`maskContract.spec.ts` 里有一串**脱敏资源键**（`'asset/material'`、`'factory/storage'`…），
 * 与 `src/api/asset/material.ts` 的路径后缀逐字相同 —— 把它们算作引用，会让
 * 「已经被清空的 API 文件」永远不被报告（本仓真实踩到：四个 0 字节文件因它长期不被发现）。
 * 页面/模型这类按路径懒加载的东西，字面量在 `src/demo/fixtures/menu.ts`（非测试）里，
 * 不依赖测试文件提供引用。
 */
function readTreeQuietly(dir) {
  if (!existsSync(dir))
    return []
  try {
    return walk(dir).map(file => readFileSync(file, 'utf8'))
  }
  catch {
    return []
  }
}

const isTestFile = file => /[\\/]__tests__[\\/]|\.spec\.(?:ts|mts)$/.test(file)

const allText = [
  ...[...sources.entries()].filter(([file]) => !isTestFile(file)).map(([, text]) => text),
  ...ENTRIES.filter(existsSync).map(file => readFileSync(file, 'utf8')),
  ...CORPUS_DIRS.flatMap(readTreeQuietly),
].join('\n')

/**
 * 解析一个 import 说明符到实际文件。
 * 依次尝试：原样、加各扩展名、`/index.*`、`.vue`。
 */
function resolveSpecifier(specifier, fromFile) {
  let base
  if (specifier.startsWith('@/'))
    base = join(SRC, specifier.slice(2))
  else if (specifier === '@kubo/shared')
    base = join(SHARED, 'index.ts')
  else if (specifier.startsWith('@kubo/shared/'))
    base = join(SHARED, specifier.slice('@kubo/shared/'.length))
  else if (specifier.startsWith('.'))
    base = resolve(dirname(fromFile), specifier)
  else
    return null

  const candidates = [
    base,
    ...EXTENSIONS.map(ext => `${base}${ext}`),
    ...EXTENSIONS.map(ext => join(base, `index${ext}`)),
  ]
  for (const candidate of candidates) {
    if (fileSet.has(candidate))
      return candidate
  }
  return null
}

/**
 * 引用提取。
 *
 * ⚠️ 不要写成「import ... from」的单条正则：本仓的具名 import 常被 eslint 格式化成多行
 * （`import {\n  a,\n  b,\n} from '@/x'`），`[^'"\n]*` 会漏掉它们 —— 漏掉的后果是
 * **把在用的文件判成死件**。这里拆成三条互不依赖的规则：
 *   1. 任何 `from '...'`（含多行 import / export ... from）；
 *   2. 动态 `import('...')`（路由懒加载、按需引入）；
 *   3. 副作用 `import '...'`（样式、polyfill）。
 */
const FROM_RE = /\bfrom\s*['"]([^'"]+)['"]/g
const DYNAMIC_RE = /import\s*\(\s*['"]([^'"]+)['"]\s*\)/g
const SIDE_EFFECT_RE = /^\s*import\s+['"]([^'"]+)['"]/gm

/** 提取一个文件里所有 import 说明符 */
function extractSpecifiers(source) {
  const specifiers = []
  for (const regex of [FROM_RE, DYNAMIC_RE, SIDE_EFFECT_RE]) {
    for (const match of source.matchAll(regex)) {
      specifiers.push(match[1])
    }
  }
  return specifiers
}

/**
 * 访问根（roots）：
 * 1. 入口文件；
 * 2. **所有测试**（测试会 import 被测模块，这些模块不能算死件）；
 * 3. **所有视图页**（`src/view/**`）：页面是按路径约定懒加载的，静态 import 图看不到，
 *    所以它们自己是根 —— 否则「页面 import 的东西」全会被误判成没人用；
 * 4. **演示层**（`src/demo/**`）：预览/演示模式是当前交付形态的一部分。
 */
const ROOTS = [
  ...ENTRIES.filter(existsSync),
  ...walk(SRC).filter(file => file.endsWith('.spec.ts')),
  ...(existsSync(join(SRC, 'view')) ? walk(join(SRC, 'view')) : []),
  ...(existsSync(join(SRC, 'demo')) ? walk(join(SRC, 'demo')) : []),
]

/** 引用图：从访问根出发 BFS */
const used = new Set()
const queue = [...ROOTS]
while (queue.length) {
  const file = queue.pop()
  if (used.has(file) || !fileSet.has(file))
    continue
  used.add(file)
  for (const specifier of extractSpecifiers(sources.get(file) ?? '')) {
    const target = resolveSpecifier(specifier, file)
    if (target && !used.has(target)) {
      queue.push(target)
    }
  }
}

/**
 * 字符串引用：路由/菜单里的 `'system/unit/list'`、`'/src/view/...'`、
 * `import.meta.glob('/src/view/**')` 这类**不经过 import 语句**的引用。
 */
function referencedByString(file) {
  const rel = relative(ROOT, file).replaceAll('\\', '/')
  const withoutSrc = rel.replace(/^src\//, '')
  const withoutExt = rel.replace(/\.[^.]+$/, '')
  const withoutSrcExt = withoutSrc.replace(/\.[^.]+$/, '')
  const segments = withoutExt.split('/')
  // 视图/模型这类「按路径懒加载」的，用两段以上后缀匹配（如 `unit/list`、`system/unit/list`）
  const suffixes = []
  for (let i = 1; i < segments.length; i++) {
    suffixes.push(segments.slice(i).join('/'))
  }
  return [rel, withoutSrc, withoutExt, withoutSrcExt, ...suffixes]
    .filter(item => item.includes('/'))
    .some(item => allText.includes(`'${item}'`) || allText.includes(`\`${item}\``) || allText.includes(`"${item}"`))
}

const IGNORE = [
  /\.d\.ts$/,
  /\.spec\.ts$/,
  /\.vue\.d\.ts$/,
  /components\.d\.ts$/,
  /[\\/]main\.ts$/,
  /[\\/]env\.d\.ts$/,
  /[\\/]__tests__[\\/]/,
]

/**
 * ⚠️ `src/view/**` 一律视为「在用」，本脚本不报告它们。
 *
 * 原因：视图页不是 import 进来的，而是**按路径约定懒加载**（`/console/wms/input/list`
 * ↔ `src/view/console/wms/input/list.vue`，菜单字符串由 `listPage(area, page)` 拼出来），
 * 静态引用图看不到这层关系 —— 报告出来会诱导误删线上页面。
 * 页面级的"孤儿检测"交给 `src/config/__tests__/viewContract.spec.ts`（用真实的菜单树对账）。
 */
const isView = file => relative(ROOT, file).startsWith('src/view/')

const checkOnly = process.argv.includes('--check')

const unused = files
  .filter(file => !used.has(file))
  .filter(file => !isView(file))
  .filter(file => !IGNORE.some(pattern => pattern.test(file)))
  .filter(file => !referencedByString(file))
  .map(file => relative(ROOT, file))
  .sort()

console.log(`扫描 ${files.length} 个文件，引用图覆盖 ${used.size} 个`)
if (!unused.length) {
  console.log('✅ 没有未被引用的文件')
  process.exit(0)
}

console.log(`\n⚠️  ${unused.length} 个文件没有被任何 import 或路径字符串引用：\n`)
for (const file of unused) {
  const lines = (sources.get(join(ROOT, file)) ?? '').split('\n').length
  console.log(`  ${file}  (${lines} 行)`)
}

if (checkOnly) {
  console.log('\n❌ 存在未被引用的文件：CI 门禁不允许死代码继续堆积。')
  console.log('   确认后二选一：`node scripts/find-unused-frontend.mjs --delete` 删除，')
  console.log('   或在该文件里补上真实引用（若它是被动态字符串引用的，请同时完善本脚本的判定）。')
  process.exit(1)
}

if (process.argv.includes('--delete')) {
  for (const file of unused) {
    rmSync(join(ROOT, file))
  }
  console.log(`\n🗑  已删除 ${unused.length} 个文件`)
}
else {
  console.log('\n（加 --delete 删除；建议先逐个人工确认，尤其是可能被动态字符串引用的页面）')
}

// 顺带统计：有没有「文件存在但没人 import 的第三方依赖」
const pkg = JSON.parse(readFileSync(join(ROOT, 'package.json'), 'utf8'))
const runtimeDeps = Object.keys(pkg.dependencies ?? {})
const unusedDeps = runtimeDeps.filter((name) => {
  const patterns = [`'${name}'`, `"${name}"`, `'${name}/`, `"${name}/`]
  return !patterns.some(pattern => allText.includes(pattern))
})
if (unusedDeps.length) {
  console.log(`\n⚠️  运行期依赖里没有 import 记录的（需人工判断是构建期插件还是死依赖）：\n${unusedDeps.map(name => `  ${name}`).join('\n')}`)
}
else {
  console.log('\n✅ 运行期依赖都有 import 记录')
}

// 统计体积相关的目录规模，便于对比清理效果
const countLines = list => list.reduce((sum, file) => sum + (sources.get(file)?.split('\n').length ?? 0), 0)
console.log(`\n源码规模：${files.length} 个文件 / ${countLines(files)} 行（含空行与注释）`)
void statSync

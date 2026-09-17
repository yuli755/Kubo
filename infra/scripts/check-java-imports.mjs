#!/usr/bin/env node
/**
 * # Java 导入可达性校验（没有 JDK 也能跑的那一半编译器）
 *
 * ## 为什么需要它
 * 后端退场的每一批都是"搬类 → 换 import → 删旧依赖"。沙箱里没有 JDK，`javac` 给不出
 * "找不到符号"的清单，于是搬错/删早/漏搬这类问题只能靠人眼看 —— 这个脚本把那部分变成机器答案：
 *
 *   1. **本仓自己的 import 必须能落到真实文件上**（`cn.hamm.spms.*` 的每个 import 都要有对应 .java），
 *      这条能抓住"搬进来了但被删早了"（本轮就差点留下 7 个孤儿副本）、包名写错、类名拼错；
 *   2. **搬进来的层（`cn.hamm.spms.core.*`）不得引用 airpower 的**（否则"自持"是假象：
 *      上游一升级/一删除，这里就断）；
 *   3. **同一简单类名不得同时存在本仓副本与 airpower 副本** —— 这是 airpower 退场最阴的坑：
 *      两边类名一样，谁用谁不看包名，出了问题编译期不报、运行期静默（典型：注解身份不一致 →
 *      反射读不到 → 导出/字典/脱敏行为悄悄退化）。
 *
 * ## 用法
 * ```
 * pnpm java:imports          # 闸门：发现问题即 exit 1
 * pnpm java:imports -- --json
 * ```
 */
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { stripJavaComments } from './lib/java-source.mjs'

const ROOT = process.env.KUBO_ROOT ?? path.resolve(import.meta.dirname, '..', '..')
const SRC = path.join(ROOT, 'SPMS-Server', 'src')
/**
 * 自持层里**已承认**的 airpower import（默认必须是空的：搬进来的层不该再反向依赖上游）。
 * 用法：真的必须依赖时写进来的同时写清原因与消掉它的批次。
 */
const ALLOWED_SELF_OWNED_AIRPOWER_IMPORTS = new Set([])

/**
 * 已承认的"同名副本"：本仓有一份副本，代码里同时还在用 airpower 的那一份。
 * 这是退场过程里最需要盯的状态（注解/接口的**身份**决定反射能不能读到），
 * 所以每一条都要写清「为什么暂时必须共存 + 什么时候消掉」。
 */
const DETAIL_COLLISIONS = new Map([
  ['Description', 'B3 未做：实体/响应类上的 @Description 必须继续是 airpower 的那一份（它的 ExportHelper/CurdService 按类身份反射读取），本仓副本只服务自持层内部（Json 自己）'],
  ['ReflectUtil', 'B3 未做：airpower 的 ReflectUtil.getDescription(entity) 读的是它自己的 @Description；本仓副本目前只被自持层的 javadoc 引用，尚未启用'],
])

/** 已由本仓自持的层（搬进来的副本与 airpower 同名共存，必须有额外约束）。 */
const SELF_OWNED_PREFIX = 'cn.hamm.spms.core'

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name)
    if (e.isDirectory())
      walk(p, out)
    else if (e.name.endsWith('.java'))
      out.push(p)
  }
  return out
}

const javaFiles = walk(SRC).sort()
const problems = []
const notes = []

// ── 索引：本仓所有类的 FQN（含嵌套类型按外层类算）──────────────
const ownClasses = new Map() // fqn -> file
for (const file of javaFiles) {
  const text = fs.readFileSync(file, 'utf8')
  const pkg = text.match(/^package\s+([\w.]+);/m)?.[1]
  if (!pkg)
    continue
  const cls = path.basename(file, '.java')
  ownClasses.set(`${pkg}.${cls}`, file)
}

/** 把 import 的 FQN 收敛到"外层类"（处理 `A.B` 这种静态成员/嵌套类写法）。 */
function resolveOwn(fqn) {
  if (ownClasses.has(fqn))
    return ownClasses.get(fqn)
  const parts = fqn.split('.')
  for (let i = parts.length - 1; i > 0; i -= 1) {
    const candidate = parts.slice(0, i).join('.')
    if (ownClasses.has(candidate))
      return ownClasses.get(candidate)
  }
  return null
}

// ── 检查 1：本仓 import 可达 ─────────────────────────────────
let ownImportCount = 0
for (const file of javaFiles) {
  const text = stripJavaComments(fs.readFileSync(file, 'utf8'))
  const rel = path.relative(ROOT, file)
  for (const m of text.matchAll(/^import\s+(?:static\s+)?(cn\.hamm\.spms\.[\w.]+)\s*;/gm)) {
    ownImportCount += 1
    const fqn = m[1]
    if (!resolveOwn(fqn))
      problems.push(`${rel}: import ${fqn} 在本仓找不到对应 .java（搬丢/被删早/名字写错）`)
  }
}

// ── 检查 2：自持层不得反向依赖 airpower ──────────────────────
const selfOwnedFiles = javaFiles.filter(f => fs.readFileSync(f, 'utf8').includes(`package ${SELF_OWNED_PREFIX}`))
for (const file of selfOwnedFiles) {
  const text = stripJavaComments(fs.readFileSync(file, 'utf8'))
  const rel = path.relative(ROOT, file)
  for (const m of text.matchAll(/^import\s+(?:static\s+)?(cn\.hamm\.airpower\.[\w.]+)\s*;/gm)) {
    // 自持层内部允许 import airpower 的场景：**已登记**的少数（例如仍需沿用上游注解身份）。
    if (ALLOWED_SELF_OWNED_AIRPOWER_IMPORTS.has(m[1]))
      continue
    problems.push(`${rel}: 自持层反向 import 了 ${m[1]} —— "自持"是假象，记到 ALLOWED_SELF_OWNED_AIRPOWER_IMPORTS 里才算承认`)
  }
}
notes.push(`自持层文件：${selfOwnedFiles.length} 个（包 ${SELF_OWNED_PREFIX}.*）`)

// ── 检查 3：同名副本（本仓副本 vs airpower 副本）──────────────
const ownSimpleNames = new Set([...ownClasses.keys()].map(k => k.split('.').pop()))
const airpowerRefs = new Set()
for (const file of javaFiles) {
  const text = stripJavaComments(fs.readFileSync(file, 'utf8'))
  for (const m of text.matchAll(/(cn\.hamm\.airpower\.[\w.]+)/g))
    airpowerRefs.add(m[1].replace(/\.$/, ''))
}
const collisionAll = new Map()
for (const ref of airpowerRefs) {
  const simple = ref.split('.').pop()
  if (ownSimpleNames.has(simple))
    collisionAll.set(simple, ref)
}
const shadowed = new Map([...collisionAll].filter(([simple]) => !DETAIL_COLLISIONS.has(simple)))
const acknowledged = new Map([...collisionAll].filter(([simple]) => DETAIL_COLLISIONS.has(simple)))
for (const [simple, ref] of shadowed)
  problems.push(`同名副本：本仓有 ${simple}，同时又在用 airpower 的 ${ref} —— 同名不同身份（注解/接口尤其危险），必须显式承认或统一`)
for (const [simple] of acknowledged)
  notes.push(`同名副本已承认：${simple} —— ${DETAIL_COLLISIONS.get(simple)}`)

// ── 报告 ────────────────────────────────────────────────────
const report = { javaFiles: javaFiles.length, ownImports: ownImportCount, selfOwned: selfOwnedFiles.length, shadowed: Object.fromEntries(shadowed), problems }
if (process.argv.includes('--json')) {
  console.log(JSON.stringify(report, null, 2))
  process.exit(problems.length ? 1 : 0)
}
console.log('\n🧭 Java 导入可达性（无 JDK 也能跑的那一半编译器）\n')
for (const n of notes)
  console.log(`  · ${n}`)
console.log(`  · 本仓 import 检查：${ownImportCount} 条`)
if (shadowed.size > 0) {
  console.log('\n  ⚠ 同名副本（本仓 / airpower）：')
  for (const [simple, ref] of shadowed)
    console.log(`    · ${simple} ← airpower 的 ${ref}`)
}
if (problems.length) {
  console.log('\n✗ 发现问题：')
  for (const p of problems)
    console.log(`  · ${p}`)
  process.exit(1)
}
console.log('\n✓ 通过：本仓 import 全部可达、自持层未反向依赖 airpower、无未承认的同名副本\n')

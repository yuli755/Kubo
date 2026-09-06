#!/usr/bin/env node
/**
 * 框架情报探针：把"看不到 jar"变成"读得到签名"
 *
 * ## 为什么需要它
 *
 * 本仓多次因为"不知道框架里到底有什么"而停在推断层：导出真遮蔽需要 `Curd.Export` 的行组装入口，
 * §14.5 里"复制的是掩码还是原值"无法判定，等等。沙箱里既没有 JDK 也没有出站网络，
 * `javap` 这类工具一次都跑不了 —— 而 CI 有 JDK、有依赖，唯一缺的是**把结果传回来的通道**。
 *
 * Actions 的日志正文在对象存储上，脚本环境经常取不到（本仓踩过，见 docs §19.9），
 * 所以这个探针把结论**写成 annotation**：`gh api .../check-runs/{id}/annotations` 一步就能读。
 *
 * ## 输出预算
 *
 * GitHub 每个 check run 只留 10 条 annotation，单条 message 有长度上限（约 4KB）。
 * 因此：分块打包（每块 ≤ CHUNK 字符），**并按信息量排序** —— 类清单比方法体更该被看到，
 * 宁可在末尾说"还有 N 块未上报"，也不要让最重要的内容被挤掉。
 *
 * ## 只在 workflow_dispatch 时跑
 *
 * 它是一次性侦察，不是回归门禁；挂在普通 push 上等于每次多花时间换一堆没人看的字。
 * 触发：`gh workflow run ci.yml --ref <分支>`。
 *
 * 用法（在 SPMS-Server 目录下，先备好 classpath）：
 *   ./mvnw -B -s settings.xml -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 *   node ../infra/scripts/framework-intel.mjs --cp /tmp/cp.txt
 *   node ../infra/scripts/framework-intel.mjs --cp /tmp/cp.txt --classes a.B,c.D   # 定向看某几个类
 *
 * `--classes` 是给"已经有假设要验证"的第二次探测用的：泛扫只能给出类清单，
 * 而真正决定方案的是某个方法体到底调了谁（例如导出行是走 Jackson 序列化还是反射取字段 ——
 * 前者意味着 `@SensitiveField` 已经天然遮住导出，后者意味着必须在导出链路上另做一层）。
 */
import {execFileSync} from 'node:child_process'
import {existsSync, readFileSync} from 'node:fs'
import {basename} from 'node:path'

const argv = process.argv.slice(2)
const opt = (name, dflt) => {
  const i = argv.indexOf(`--${name}`)
  return i >= 0 ? argv[i + 1] : dflt
}
const cpFile = opt('cp', '/tmp/cp.txt')
const grep = opt('grep', 'export|excel|csv|poi|easyexcel|download|curd')
const maxChunks = Number(opt('chunks', 9))
const targeted = String(opt('classes', '')).split(',').map((x) => x.trim()).filter(Boolean)
const CHUNK = 3200

const notes = []
const push = (title, body) => {
  let text = String(body ?? '').trim()
  // javap 找不到类时返回的是 "Error: class not found"，那不属于情报，只会占掉 annotation 名额
  if (!text || /^Error:/.test(text) || text.startsWith('__(执行失败')) {
    return
  }
  for (let i = 0, part = 0; i < text.length && part < 4; i += CHUNK, part++) {
    notes.push({title: part === 0 ? title : `${title} #${part + 1}`, body: text.slice(i, i + CHUNK)})
  }
}

const run = (cmd, args, opts = {}) => {
  try {
    return execFileSync(cmd, args, {encoding: 'utf-8', maxBuffer: 32 * 1024 * 1024, ...opts})
  } catch (error) {
    return `__(执行失败：${(error.message ?? String(error)).split('\n')[0]})__`
  }
}

if (!existsSync(cpFile)) {
  console.log(`::notice title=intel::找不到 classpath 文件 ${cpFile}；先跑 dependency:build-classpath`)
  process.exit(0)
}

const cp = readFileSync(cpFile, 'utf-8').trim()
const cpEntries = cp.split(':').filter((p) => p.endsWith('.jar'))
const airpower = cpEntries.filter((p) => /airpower|hamm/.test(p))
const thirdParty = cpEntries.filter((p) => /easyexcel|poi|apache.*poi/i.test(p))

const javapFor = (cls) => run('javap', ['-p', '-classpath', cp, cls])
// （probeClass 复用它；定义顺序在这里是为了让"泛扫"和"定向"共用一套取数逻辑）
/** 一个类的签名 + 方法体里"取值/写出"相关的调用，压成一块 */
const probeClass = (cls) => {
  const sig = javapFor(cls)
  if (sig.startsWith('__') || /^Error:/.test(sig.trim())) {
    push(`缺席 ${cls.split('.').pop()}`, `javap 拿不到 ${cls}（${sig.split('\n')[0]}）`)
    return
  }
  const lines = run('javap', ['-p', '-c', '-classpath', cp, cls]).split('\n')
  const calls = lines.filter((l) => /^\s*\d+:\s+invoke/.test(l))
  const interesting = calls.filter((l) => /ObjectMapper|writeValue|Jackson|toJson|toMap|Csv|Excel|Bean|Field|getter|Getter|serialize| reflective/i.test(l))
  const sigLines = sig.split('\n')
  const exportish = sigLines.filter((l) => /export|csv|queryList|findPage|beforeGetList/i.test(l))
  push(`类 ${cls.split('.').pop()}`, [
    `— 与导出/查询相关的签名 ${exportish.length} 条（全类 ${sigLines.length} 行）：`,
    ...exportish.slice(0, 24),
    ...(exportish.length === 0 ? sigLines.slice(0, 40) : []),
    `— 方法体共 ${calls.length} 处 invoke，其中与"序列化/取值/写出"相关的 ${interesting.length} 处：`,
    ...interesting.slice(0, 26).map((l) => l.trim()),
    interesting.length === 0 && calls.length > 0
      ? `（没有命中的关键调用，退化为前 14 处 invoke：\n${calls.slice(0, 14).map((l) => l.trim()).join('\n')}）`
      : '',
  ].filter(Boolean).join('\n'))
}

if (targeted.length > 0) {
  for (const cls of targeted.slice(0, 4)) {
    probeClass(cls)
  }
}

// 1) 有哪些 jar 可查（定向模式下也留着，用来判断"类名猜对了没有"）
push('jar', [
  `classpath 共 ${cpEntries.length} 个 jar`,
  // 注意别写成 .map(basename)：Array 会把 (value, index, array) 都传进去，
  // basename 的第二个参数是 suffix —— 于是拿到 index 0 直接抛 ERR_INVALID_ARG_TYPE。
  `airpower: ${airpower.map((jar) => basename(jar)).join(', ') || '(无)'}`,
  `excel/csv 相关三方: ${thirdParty.map((jar) => basename(jar)).join(', ') || '(无)'}`,
].join('\n'))

/** 从 jar 的类清单里挑出与导出/CRUD 有关的类名 */
const listClasses = (jar) => {
  const out = run('unzip', ['-Z', '1', jar])
  if (out.startsWith('__')) {
    // 有些环境没有 unzip：退回 jar tf（JDK 自带）
    return run('jar', ['tf', jar]).split('\n')
  }
  return out.split('\n')
}
const pattern = new RegExp(grep, 'i')
const candidates = []
for (const jar of [...airpower, ...thirdParty]) {
  for (const line of listClasses(jar)) {
    const m = /([A-Za-z0-9_./$]+)\.class/.exec(line.trim())
    if (!m) {
      continue
    }
    const cls = m[1].replace(/\//g, '.').replace(/\$/g, '.')
    const simple = cls.split('.').pop()
    if (pattern.test(simple) || pattern.test(cls)) {
      candidates.push({jar, cls, simple})
    }
  }
}

// 2) 候选类清单 —— 定向模式下跳过：名额比信息更稀缺
const rank = (c) => (/Export/i.test(c.simple) ? 0 : /Curd/i.test(c.simple) ? 1 : /Excel|Csv|Poi|Write/i.test(c.simple) ? 2 : 3)
candidates.sort((a, b) => rank(a) - rank(b) || a.cls.localeCompare(b.cls))
const uniq = [...new Map(candidates.map((c) => [c.cls, c])).values()]
if (targeted.length === 0) {
  push('候选类', uniq.slice(0, 90).map((c) => c.cls).join('\n'))
}

// 3) 关键类的成员签名（同样只在泛扫模式跑）
if (targeted.length === 0) {
  for (const c of uniq.slice(0, 3)) {
    push(`签名 ${c.simple}`, javapFor(c.cls).split('\n').slice(0, 70).join('\n'))
  }
}

// 4) 反汇编：找**声明了 export 方法**的类，看它方法体里到底调了谁
//    —— 这决定"能不能在这一层把列遮住"：如果它是「查列表 → 逐实体反射取字段 → 写 sheet」，
//    那么遮蔽点在实体取值那一步；如果它直接把 ResultSet/JDBC 流写出去，就没有可挂钩的地方。
const javapDis = (cls) => run('javap', ['-p', '-c', '-classpath', cp, cls])
for (const c of targeted.length === 0 ? uniq.slice(0, 8) : []) {
  const sig = javapFor(c.cls)
  if (sig.startsWith('__') || !/\bexport/i.test(sig)) {
    continue
  }
  const lines = javapDis(c.cls).split('\n')
  // `-c` 会把整个类的方法体都打出来（动辄上千行），所以只截 export 相关方法的窗口
  const start = lines.findIndex((l) => /export|Export/.test(l) && /\)\s*;?\s*$/.test(l.trim()) === false)
  const window = lines.slice(start >= 0 ? start : 0, (start >= 0 ? start : 0) + 420)
  const hits = window.filter((l) => /^\s*\d+:\s+invoke/.test(l) && /get|write|row|list|map|field|value|convert|util|Bean/i.test(l))
  push(`反汇编 ${c.simple}`, [
    `签名：${sig.split('\n').filter((l) => /export|Export/.test(l)).join(' | ').slice(0, 700)}`,
    `方法体调用（窗口 ${window.length} 行里命中 ${hits.length} 行，只送前 34 行）：`,
    ...hits.slice(0, 34),
  ].join('\n'))
  break
}

if (notes.length === 0) {
  console.log('::notice title=intel::没有匹配到相关类（可 --grep 换个关键词）')
  process.exit(0)
}

const emitted = notes.slice(0, maxChunks)
console.log(`框架情报探针：${notes.length} 块内容，上报 ${emitted.length} 块（上限 ${maxChunks}）`)
for (const note of notes) {
  console.log(`\n===== ${note.title} =====\n${note.body}`)
}
for (const note of emitted) {
  // title 里带上块序号，读的人知道是不是被截断了
  console.log(`::notice title=${note.title}（${emitted.indexOf(note) + 1}/${emitted.length}）::${note.body.replace(/\n/g, '%0A')}`)
}
if (notes.length > emitted.length) {
  console.log(`::notice title=intel 截断::还有 ${notes.length - emitted.length} 块未上报（完整内容在本步 stdout）`)
}

#!/usr/bin/env node
/**
 * 把 surefire 报告读成 GitHub Actions annotation（并打印一份人能扫一眼的汇总）
 *
 * ## 为什么不是 grep 日志
 *
 * `mvn test` 失败时日志里同时有：Maven 的 [ERROR] 汇总、每个失败类的 `<<< FAILURE!` 行、
 * 完整堆栈、`Please refer to dump files` 之类噪音。annotation 只有 10 条名额，
 * grep 出来的尾部基本是"知道有类失败、不知道哪个用例为什么失败"。
 *
 * surefire 自己写了结构化结果（`target/surefire-reports/TEST-*.xml`），里面有：
 *   `<testsuite name tests failures errors time>` + `<testcase name classname>` + `<failure message>` + 堆栈。
 * 直接读它，就能给出"用例名 + 首个属于本项目的堆栈帧（文件:行）+ 断言消息"，
 * 也就是可以钉到源码行的那种 annotation。
 *
 * ## 约定
 *
 * - 任何异常都吞掉并 `exit 0`：诊断工具不能把流水线带崩，否则下一次没人愿意看它。
 * - 只上报 `cn.hamm.*` 的堆栈帧为 `file=`，其它（Spring/Jackson）留在消息文本里当上下文。
 * - `--max` 控制 annotation 条数，其余只打日志（数量如实说明）。
 *
 * 用法：node infra/scripts/surefire-summary.mjs [报告目录] [--max 8]
 */
import{existsSync, readFileSync, readdirSync} from 'node:fs'
import {join} from 'node:path'

const argv = process.argv.slice(2)
let dir = null
let max = 8
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--max') {
    max = Number(argv[++i]) || max
  } else if (!dir) {
    dir = argv[i]
  }
}
dir = dir || 'target/surefire-reports'

const attr = (tag, name) => {
  const m = new RegExp(`\\b${name}="([^"]*)"`).exec(tag)
  return m ? m[1] : ''
}
const unescape = (text) => text
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')

/** 从堆栈里挑第一个属于本项目主源码的帧：测试类自己的行没意义，要看的是它调用的那一行 */
const pickFrame = (stack) => {
  for (const raw of stack.split('\n')) {
    const line = raw.trim()
    if (!line.startsWith('at ')) {
      continue
    }
    const m = /^at\s+(cn\.hamm\.[\w$.]+)\.([\w$<>]+)\(([\w$]+)\.java:(\d+)\)/.exec(line)
    if (!m) {
      continue
    }
    if (line.includes('$Proxy') || line.includes('CGLIB') || line.includes('Reflective') || line.includes('MethodInvoker')) {
      continue
    }
    const [, pkgCls, , simpleFile, ln] = m
    const pkg = pkgCls.split('.').slice(0, -1).join('/')
    // annotation 的 file= 必须相对仓库根；CI 里本步的 cwd 是 SPMS-Server，所以要补上这一层
    return {file: `SPMS-Server/src/main/java/${pkg}/${simpleFile}.java`, line: Number(ln), target: `${pkgCls}.${m[2]}()`}
  }
  return null
}

/** surefire 常把异常类名同时放在 type 和 message 开头，去掉重复前缀，让一行 annotation 说得清 */
const format = ({type, message}) => {
  const t = type ?? ''
  const head = `${t}: `
  const body = (t && message.startsWith(head)) ? message.slice(head.length) : message
  return t ? `${t}: ${body}` : body
}

const collect = (xml, fallbackName) => {
  const suiteTag = /<testsuite\b[^>]*>/.exec(xml)?.[0] ?? ''
  const suite = {
    name: attr(suiteTag, 'name') || fallbackName,
    tests: Number(attr(suiteTag, 'tests') || 0),
    failures: Number(attr(suiteTag, 'failures') || 0),
    errors: Number(attr(suiteTag, 'errors') || 0),
    skipped: Number(attr(suiteTag, 'skipped') || 0),
    time: Number(attr(suiteTag, 'time') || 0),
  }
  const bad = []
  const caseRe = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g
  let m
  while ((m = caseRe.exec(xml)) !== null) {
    const head = m[1]
    const body = m[3] ?? ''
    const issue = /<(failure|error)\b([^>]*)>([\s\S]*?)<\/\1>|<(failure|error)\b([^>]*)\/>/.exec(body)
    if (!issue) {
      continue
    }
    const attrs = issue[2] ?? issue[5] ?? ''
    const stack = unescape(issue[3] ?? '')
    bad.push({
      suite: suite.name,
      name: attr(head, 'name'),
      classname: attr(head, 'classname') || suite.name,
      type: attr(attrs, 'type'),
      message: unescape(attr(attrs, 'message') || stack.split('\n')[0] || '(无消息)'),
      stack,
    })
  }
  return {suite, bad}
}

const main = async () => {
  if (!existsSync(dir)) {
    console.log(`（没有 surefire 报告目录 ${dir}：测试没跑，或被跳过 —— 这本身就是要报的信息）`)
    console.log('::warning::surefire 报告目录不存在，无法判断测试是否真的执行过')
    return
  }
  const files = readdirSync(dir).filter((f) => f.startsWith('TEST-') && f.endsWith('.xml'))
  if (files.length === 0) {
    console.log('（报告目录里没有 TEST-*.xml：测试没被执行为）')
    console.log('::warning::surefire 目录存在但没有用例结果，"测试通过"这个结论不成立')
    return
  }
  let totals = {tests: 0, failures: 0, errors: 0, skipped: 0, time: 0}
  const allBad = []
  for (const f of files) {
    const {suite, bad} = collect(readFileSync(join(dir, f), 'utf-8'), f.replace(/^TEST-|\.xml$/g, ''))
    totals = {
      tests: totals.tests + suite.tests,
      failures: totals.failures + suite.failures,
      errors: totals.errors + suite.errors,
      skipped: totals.skipped + suite.skipped,
      time: totals.time + (Number.isFinite(suite.time) ? suite.time : 0),
    }
    allBad.push(...bad)
  }
  const verdict = totals.failures + totals.errors === 0 ? '通过' : '有失败'
  console.log(`用例 ${totals.tests} 个 · 断言失败 ${totals.failures} · 错误 ${totals.errors} · 跳过 ${totals.skipped} · ${totals.time.toFixed(1)}s → ${verdict}`)
  if (allBad.length === 0) {
    return
  }
  console.log(`\n失败/错误清单（共 ${allBad.length} 个，annotation 送前 ${max} 个）：`)
  for (const item of allBad) {
    const frame = pickFrame(item.stack)
    const where = frame ? `${frame.file}:${frame.line} (${frame.target})` : '(堆栈里没有本项目主源码帧)'
    console.log(`  - ${item.classname}#${item.name}\n      ${format(item)}\n      ${where}`)
  }
  for (const item of allBad.slice(0, max)) {
    const frame = pickFrame(item.stack)
    const msg = `${item.classname}#${item.name} ${format(item)}`.replace(/\n/g, ' ').slice(0, 900)
    console.log(frame
        ? `::error file=${frame.file},line=${frame.line},title=测试失败::${msg}`
        : `::error title=测试失败::${msg}`)
  }
  if (allBad.length > max) {
    console.log(`::error::另有 ${allBad.length - max} 个失败用例未逐条上报，完整清单见本步日志`)
  }
}

main().catch((error) => {
  console.log(`（读取 surefire 报告失败：${error.message}）`)
})

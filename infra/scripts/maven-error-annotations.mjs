#!/usr/bin/env node
/**
 * 把 Maven 日志里的 javac 错误转成 GitHub Actions annotation
 *
 * ## 为什么需要它
 *
 * 一个 Maven 失败日志里，真正有价值的那几行会被淹没：
 * - `javac` 每个错误一行 `路径:[行,列] 说明`；
 * - Maven 会把同一批错误**再打印一次**（一次在 "COMPILATION ERROR" 块，一次在结尾汇总）；
 * - 中间还夹着 `first type: int` / `second type: String` 这类续行；
 * - 再往后是 `[ERROR] -> [Help 1]`、堆栈提示等噪音。
 *
 * 而 GitHub 每个 check run **只保留 10 条 annotation**。直接把 `[ERROR]` 前 40 行贴上去，
 * 结果就是续行和汇总行把真正的错误位置挤掉 —— 本仓踩过（第一次读 CI 时只看到
 * "Failed to execute goal ... Compilation failure" 而看不到是哪个文件哪一行）。
 *
 * 本脚本做三件事：**只取 `*.java:[行,列]` 形式的错误行** → **去重** → 输出
 * `::error file=...,line=...,col=...::说明`，让 annotation 直接钉在源文件那一行上。
 * 同时把完整清单原样打到 stdout，annotation 装不下的部分至少在本步日志里能看到。
 *
 * ## file= 必须是相对仓库根的路径
 *
 * `actions/checkout` 把工作区放在 `$GITHUB_WORKSPACE`，annotation 的 `file=` 要相对它，
 * 否则点不进 PR diff 里的代码行（路径解析失败时 GitHub 会静默退回 `.github:1`）。
 * 所以这里剥掉 `$GITHUB_WORKSPACE/` 而**保留 `SPMS-Server/` 前缀**。
 *
 * ## 本地可验证
 *
 * 不依赖 CI 就能测：`node infra/scripts/maven-error-annotations.mjs <log 文件> [最多几条]`。
 * CI 里那条流水线的正确性只有跑一次才知道，所以逻辑放在这里、用真实样例先验过。
 *
 * 用法：
 *   node infra/scripts/maven-error-annotations.mjs /tmp/mvn-compile.log
 *   MAVEN_LOG=/tmp/mvn-test.log node infra/scripts/maven-error-annotations.mjs --max 9
 */
import {readFileSync} from 'node:fs'

const argv = process.argv.slice(2)
let logPath = null
let max = 9
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === '--max') {
    max = Number(argv[++i]) || max
  } else if (argv[i] === '--') {
    /* 忽略 */
  } else if (!logPath) {
    logPath = argv[i]
  }
}
logPath = logPath || process.env.MAVEN_LOG

if (!logPath) {
  console.error('用法：maven-error-annotations.mjs <maven 日志路径> [--max N]')
  // 没有日志路径不报错退出：CI 里这一步只是"把原因说清楚"，不能因为工具自身把流水线带崩
  process.exit(0)
}

const workspace = process.env.GITHUB_WORKSPACE ? process.env.GITHUB_WORKSPACE.replace(/\/$/, '') + '/' : ''

/**
 * `src/main/java/x/Y.java:[150,34] bad operand types for binary operator '&&'`
 *
 * 只认这种形状：`::error file=...,line=...` 需要行号，续行/汇总行拿不到就干脆不要。
 */
const ERROR_AT = /^\[ERROR\]\s*(.*?\.java):\[(\d+),(\d+)\]\s*(.*)$/

const DETAIL = /^(symbol|location|required|reason|found|actual|incompatible types)\b/i

const parse = (text) => {
  const found = []
  const lines = text.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trimEnd()
    const m = ERROR_AT.exec(line.startsWith('[ERROR]') ? line : '')
    if (!m) {
      continue
    }
    // javac 把"缺哪个符号、在哪个类里"放在紧跟着的续行上（`symbol: method runWithLock()`）。
    // 只报 `cannot find symbol` 等于报了一半：知道行号还得自己回去猜。这里把续行折进同一条消息。
    const details = []
    for (let j = i + 1; j < lines.length && details.length < 3; j++) {
      const next = lines[j].trim()
      if (!next.startsWith('[ERROR]')) {
        break
      }
      const stripped = next.replace(/^\[ERROR\]\s*/, '')
      if (ERROR_AT.test(stripped) || !DETAIL.test(stripped)) {
        break
      }
      details.push(stripped)
    }
    let file = m[1]
    if (workspace && file.startsWith(workspace)) {
      file = file.slice(workspace.length)
    } else {
      file = file.replace(/^.*\/(?=[^/]+\.java$)/, '')
    }
    const base = m[4].trim()
    found.push({
      file,
      line: Number(m[2]),
      col: Number(m[3]),
      message: details.length > 0 ? `${base} (${details.join(' / ')})` : base,
    })
  }
  // 去重：Maven 同一批错误会打两遍；顺带按 文件->行 排序，让相邻错误聚在一起
  const seen = new Set()
  return found
      .filter((item) => {
        const key = `${item.file}:${item.line}:${item.col}:${item.message}`
        if (seen.has(key)) {
          return false
        }
        seen.add(key)
        return true
      })
      .sort((a, b) => (a.file === b.file ? a.line - b.line : a.file < b.file ? -1 : 1))
}

const totalErrorLines = (text) => text.split('\n').filter((l) => l.startsWith('[ERROR]')).length

let text = ''
try {
  text = readFileSync(logPath, 'utf-8')
} catch (error) {
  console.log(`::error::无法读取 Maven 日志（${error.message}）—— 失败原因只在本步日志里`)
  process.exit(0)
}

const errors = parse(text)
const noise = totalErrorLines(text)

if (errors.length === 0) {
  if (noise === 0) {
    // 这正是 `mvnw` 丢执行位那次的形状：exit 126、一行 [ERROR] 都没有、日志里只有一句 shell 报错。
    // 这时候"没有编译错误"这句话毫无用处，必须把尾部原样递到 annotation 里。
    console.log('日志里没有 [ERROR] 行（不是编译错误；可能是命令没跑起来 / 被中断）')
    const tail = text.split('\n').map((l) => l.trimEnd()).filter((l) => l.length > 0).slice(-6)
    for (const l of tail) {
      console.log(`::error::${l.slice(0, 400)}`)
    }
  } else {
    // 非 javac 失败（依赖解析、settings.xml、exit 126 的"命令不可执行"…）：
    // 没有位置可钉，就把尾部原样贴出来 —— 宁可粗糙，也别留"失败但无声"。
    console.log(`非编译定位类失败，共 ${noise} 行 [ERROR]，尾部如下：`)
    const tail = text.split('\n').filter((l) => l.startsWith('[ERROR]') || l.startsWith('[WARNING]')).slice(-8)
    for (const l of tail) {
      console.log(`::error::${l.replace(/^\[(ERROR|WARNING)\]\s*/, '').slice(0, 400)}`)
    }
  }
  process.exit(0)
}

console.log(`定位到 ${errors.length} 处源码问题（日志里 [ERROR] 共 ${noise} 行；annotation 上限 10 条，下面最多送 ${max} 条）：`)
for (const item of errors) {
  console.log(`  ${item.file}:${item.line}:${item.col}  ${item.message}`)
}
for (const item of errors.slice(0, max)) {
  console.log(`::error file=${item.file},line=${item.line},col=${item.col}::${item.message}`)
}
if (errors.length > max) {
  console.log(`::error::还有 ${errors.length - max} 处未逐条上报，完整清单见本步日志`)
}

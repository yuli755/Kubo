#!/usr/bin/env node
/**
 * 源码编码完整性校验（防止 `U+FFFD` 污染再次发生）
 *
 * ## 为什么需要它
 * 本仓曾有 5 个 Java 文件里 **105 行**中文被替换成 `U+FFFD`（``），且是提交进仓库的状态：
 * `git show` 分支起点与后续每个提交的计数完全一致，说明不是某一轮改动引入的，
 * 而是文件在某个环节被"解码失败 → 用替换符填空"写回了磁盘。
 *
 * 后果不只是注释难看：`UserController` 里 `Json.data(..., "查询成功")` 这类**返回给前端的提示**
 * 直接显示成乱码；`DevDataInitRunner` 会把坏名字（如房间 `"测试"`）写进 `menu`/业务表 ——
 * 一旦初始化过库，脏数据就长期留在里面，灰度验收时很容易被误判成"这次改造改坏了"。
 *
 * ## 为什么只卡 U+FFFD / 非字符，不做"mojibake 猜测"
 * `U+FFFD` 与 `U+FFFE`/`U+FFFF` 是**确定性事实**（合法源码里不该出现，出现即字节已丢），
 * 而"看起来像 `æ¸` 的编码错乱"很容易误伤正则表达式、多语言文案与测试夹具。
 * 门禁咬人一次，之后就没人信它了 —— 所以宁可窄一点。
 *
 * ## 为什么必须让机器来管这件事
 * 这类损坏不会导致编译失败、不会导致测试变红（测试断言的是行为，不是文案），
 * 只有人眼在特定页面才能看到 —— 正好是"人眼不该负责发现"的那一类问题。
 *
 * 用法：node infra/scripts/check-source-encoding.mjs [--verbose]
 */
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import process from 'node:process'

const HERE = dirname(fileURLToPath(import.meta.url))
const ROOT = resolve(HERE, '..', '..')

/** 扫描范围：源码与文档，跳过依赖与产物（这些目录本身也不该被提交） */
const SCAN_ROOTS = ['src', 'packages', 'infra', 'SPMS-Server/src', 'SPMS-Server/docs', '.github']
/** 只检查文本文件；二进制（图片/字体/jar）里 U+FFFD 没有语义 */
const TEXT_EXT = new Set([
  '.ts', '.tsx', '.js', '.mjs', '.cjs', '.vue', '.java', '.md', '.json',
  '.yml', '.yaml', '.sql', '.xml', '.css', '.scss', '.html',
])
const SKIP_DIR = new Set(['node_modules', 'dist', 'target', '.git', 'coverage', '.venv'])

const BAD = {
  '\uFFFD': 'U+FFFD 替换符（原始字节已丢失，无法自动还原）',
  '\uFFFE': 'U+FFFE 非字符',
  '\uFFFF': 'U+FFFF 非字符',
}

function walk(dir, out = []) {
  if (statSync(dir, { throwIfNoEntry: false })?.isDirectory() !== true) {
    return out
  }
  for (const name of readdirSync(dir)) {
    if (SKIP_DIR.has(name)) {
      continue
    }
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      walk(full, out)
    }
    else if (TEXT_EXT.has(name.slice(name.lastIndexOf('.')))) {
      out.push(full)
    }
  }
  return out
}

const verbose = process.argv.includes('--verbose')
const findings = []
let scanned = 0

for (const root of SCAN_ROOTS) {
  for (const file of walk(resolve(ROOT, root))) {
    scanned++
    const text = readFileSync(file, 'utf-8')
    if (!/[\uFFFD\uFFFE\uFFFF]/.test(text)) {
      continue
    }
    text.split('\n').forEach((line, index) => {
      for (const [char, why] of Object.entries(BAD)) {
        if (!line.includes(char)) {
          continue
        }
        findings.push({
          file: relative(ROOT, file),
          line: index + 1,
          why,
          preview: line.replace(/[\uFFFD\uFFFE\uFFFF]/g, '□').trim().slice(0, 90),
        })
        break
      }
    })
  }
}

if (findings.length === 0) {
  console.log(`✓ 编码完整性：${scanned} 个文本文件无 U+FFFD / 非字符`)
  process.exit(0)
}

const byFile = new Map()
for (const item of findings) {
  if (!byFile.has(item.file)) {
    byFile.set(item.file, [])
  }
  byFile.get(item.file).push(item)
}

console.error(`\n✗ 发现 ${findings.length} 处不可逆编码损坏，分布在 ${byFile.size} 个文件：`)
for (const [file, items] of byFile) {
  console.error(`\n  ${file}（${items.length} 行）`)
  const shown = verbose ? items : items.slice(0, 5)
  for (const item of shown) {
    console.error(`    ${String(item.line).padStart(5)}: ${item.preview}`)
  }
  if (!verbose && items.length > shown.length) {
    console.error(`    … 还有 ${items.length - shown.length} 行，加 --verbose 查看全部`)
  }
}
console.error(`\n  ${findings[0].why}。`)
console.error('  处理办法：按上下文重写这些中文（原字节已丢，机器无法还原），')
console.error('  并检查产生损坏的环节（脚本读写未指定 UTF-8、Windows 记事本另存、CI 制品回传等）。')
process.exit(1)

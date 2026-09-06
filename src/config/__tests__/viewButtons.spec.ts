import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * # 视图按钮权限门控回归测试（前端 F1 的卡点）
 *
 * ## 为什么用"扫源码"而不是挂载组件测
 * 逐个视图挂载需要为 33 个页面搭 mock（query key、service、路由、airpower 组件树），
 * 成本比收益高得多，而且一旦 mock 漂移就测不到真东西。真正会发生的事故是
 * **新写一个动作按钮忘了加权限**，那是纯语法层面的事，用静态检查卡最稳。
 *
 * ## 规则
 * 视图里每个 `<AButton>` 必须满足其一：
 * 1. 带 `v-permission`（或 `*-permission` 之类的权限属性）；
 * 2. 上方 6 行内写了 `<!-- permission-exempt: 原因 -->` 显式豁免。
 *
 * 豁免必须给理由：`v-permission` 语义是"无权限即隐藏"，
 * 漏加是"所有人可见"，滥用豁免是"所有人可见但看起来审过了"——后者更糟。
 */

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')
const VIEW_DIR = join(ROOT, 'src', 'view')

const EXEMPT_MARK = 'permission-exempt:'

function collectVue(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory())
      collectVue(full, out)
    else if (name.endsWith('.vue'))
      out.push(full)
  }
  return out
}

/** 取 `<AButton` 开标签文本（遇到引号内的 > 不截断） */
function openingTag(source: string, start: number): string {
  let i = source.indexOf('<AButton', start)
  i = source.indexOf('<', i)
  let inQuote: string | null = null
  const from = i
  for (; i < source.length; i++) {
    const ch = source[i]
    if (inQuote) {
      if (ch === inQuote)
        inQuote = null
      continue
    }
    if (ch === '"' || ch === '\'') {
      inQuote = ch
      continue
    }
    if (ch === '>')
      break
  }
  return source.slice(from, i + 1)
}

interface Finding {
  file: string
  line: number
  tag: string
}

function scan(): Finding[] {
  const findings: Finding[] = []
  for (const file of collectVue(VIEW_DIR)) {
    const source = readFileSync(file, 'utf8')
    const lines = source.split('\n')
    let cursor = source.indexOf('<AButton')
    while (cursor >= 0) {
      const tag = openingTag(source, cursor)
      const lineNo = source.slice(0, cursor).split('\n').length
      // v-bind 形式的权限属性是 :add-permission / :delete-permission（带冒号前缀）；
      // 裸 permission= 不是 ATable/AButton 的真实 prop，不能当门控（否则测不到东西）。
      const guarded = /v-permission|:[\w-]*permission=/.test(tag)
      const window = lines.slice(Math.max(0, lineNo - 7), lineNo).join('\n')
      const exempted = window.includes(EXEMPT_MARK)
      if (!guarded && !exempted) {
        findings.push({ file: relative(ROOT, file), line: lineNo, tag: tag.replace(/\s+/g, ' ').slice(0, 140) })
      }
      cursor = source.indexOf('<AButton', cursor + tag.length)
    }
  }
  return findings
}

describe('视图按钮权限门控', () => {
  it('每个 AButton 都有 v-permission 或显式豁免理由', () => {
    const findings = scan()
    if (findings.length > 0) {
      const detail = findings.map(f => `${f.file}:${f.line}  ${f.tag}`).join('\n  ')
      expect.fail(`以下按钮既没有权限门控也没有豁免说明：\n  ${detail}\n\n加 v-permission="[Permissions.模块.动作.add, Permissions.模块.动作.update]"，或写明豁免理由：<!-- ${EXEMPT_MARK} 原因 -->`)
    }
    expect(findings).toEqual([])
  })

  it('豁免注释必须带原因（防止只写标记不写理由）', () => {
    const missingReason: string[] = []
    for (const file of collectVue(VIEW_DIR)) {
      const source = readFileSync(file, 'utf8')
      source.split('\n').forEach((line, index) => {
        if (!line.includes(EXEMPT_MARK)) {
          return
        }
        const tail = line.slice(line.indexOf(EXEMPT_MARK) + EXEMPT_MARK.length)
        // 只数"字母/数字"字符（\p 属性转义，避免正则里的字面 CJK 区间）
        const letters = tail.replace(/[^\p{L}\p{N}]/gu, '').length
        if (letters < 4) {
          missingReason.push(`${relative(ROOT, file)}:${index + 1}`)
        }
      })
    }
    expect(missingReason, '这些豁免注释没有写原因').toEqual([])
  })

  it('单据编辑器里的明细行按钮必须跟随主单据的写权限', () => {
    // 明细行没有独立端点（后端随主单保存），所以门控必须用主单据的 add/update；
    // 用 {Entity}_addRow 这类"看起来存在"的码会把按钮永久藏起来。
    const offenders: string[] = []
    for (const file of collectVue(VIEW_DIR)) {
      const source = readFileSync(file, 'utf8')
      if (!/addDetail\(|deleteDetail\(/.test(source))
        continue
      if (!/v-permission="\[Permissions\.[\w.]+\.add, Permissions\.[\w.]+\.update\]"/.test(source)) {
        offenders.push(relative(ROOT, file))
      }
    }
    expect(offenders, '这些明细行编辑器没有按主单据写权限门控').toEqual([])
  })
})

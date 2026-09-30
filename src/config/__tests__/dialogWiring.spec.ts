import { readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # 弹窗接线守卫：`:on-confirm` 与 `@confirm` 不能混用
 *
 * ## 为什么需要这条守卫（真机栽过两次）
 * 项目里的弹窗组件有**两种并存**的 confirm 契约，写法互不通用：
 *
 * 1. **emit-only 型** —— 组件内部只 `emit('confirm', data)`，从不碰 `props.onConfirm`。
 *    例：`src/component/framework/KDialog.vue`、`src/component/framework/KSelector.vue`、
 *    `src/view/console/wms/inventory/component/selector.vue`。
 *    外层必须写 `@confirm="h"`：它编译成驼峰键 `onConfirm`，`emit('confirm')` 按
 *    `toHandlerKey('confirm')` 去 `vnode.props` 找的就是这个键。
 *
 * 2. **props-call 型** —— 组件内部自己写 `@confirm="() => props.onConfirm?.(data)"`，
 *    把 KDialog 的 emit 接住再转调 props。例：`src/component/bill/BillRejectDialog.vue:29`、
 *    `src/component/bill/BillAddFinishDialog.vue:32`、
 *    `src/component/framework/KUploadDialog.vue:47`。
 *    外层写 `:on-confirm="h"` 是对的：Vue 解析 props 时对原始键做 camelize，
 *    `on-confirm` → `onConfirm` → 落进 `props.onConfirm`。
 *
 * **混用的后果是静默失效**：把第 2 型的 `:on-confirm` 写到第 1 型组件上，处理器被当成
 * 普通 attr 收下，`emit` 永远找不到它 —— 不报错、不告警，只表现为「点了确定没反应」。
 * 出库「确认出库」挑库存（`wms/output/component/addFinishEditor.vue`）与移库挑库存
 * （`wms/move/component/editor.vue`）各栽过一次，两处都是把 `InventorySelector`
 * 当成 props-call 型来挂。
 *
 * ## 这条守卫量什么
 * 扫全部 `.vue`，对每一处 confirm/cancel 接线做**双向**检查：
 * - 写 `:on-confirm` / `:on-cancel` 的，目标组件必须是 props-call 型；
 * - 写 `@confirm` / `@cancel` 的，目标组件必须是 emit-only 型。
 * 并自检「两类都必须真的扫到」，防止规则退化成永真（守卫量错对象 = 绿测试掩盖线上缺陷，
 * 这是本项目已经付过三次的学费）。
 */

const SRC_DIR = path.resolve(process.cwd(), 'src')

/** 目标组件里出现这些形态即判定为 props-call 型（自己转调 props）。 */
const PROPS_CALL_PATTERNS = [
  /props\.onConfirm\s*\?\.\s*\(/,
  /props\.onCancel\s*\?\.\s*\(/,
] as const

/** 目标组件里出现这些形态即判定为 emit-only 型（只派发事件）。 */
const EMIT_ONLY_PATTERNS = [
  /emit\(\s*'confirm'/,
  /emits\(\s*'confirm'/,
  /emit\(\s*'cancel'\s*\)/,
] as const

interface VueFile {
  /** 绝对路径 */
  file: string
  /** 相对 src 的路径，用于报错信息 */
  rel: string
  text: string
}

interface Wiring {
  /** 发起接线的宿主文件（相对 src） */
  host: string
  /** 被挂的组件标签名 */
  tag: string
  /** 解析到的组件源文件（相对 src），解析失败为 null */
  target: string | null
  /** 接线写法 */
  kind: 'prop' | 'event'
  /** 事件名（confirm / cancel） */
  event: string
  /** 命中处的原文片段，便于定位 */
  snippet: string
}

function walkVue(dir: string, out: VueFile[] = []): VueFile[] {
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      walkVue(full, out)
    }
    else if (name.endsWith('.vue')) {
      out.push({ file: full, rel: path.relative(SRC_DIR, full).replaceAll('\\', '/'), text: readFileSync(full, 'utf8') })
    }
  }
  return out
}

const FILES = walkVue(SRC_DIR)

/** 文件名（不含扩展名）→ 相对 src 路径。用于解析没有 import 语句的自动注册组件。 */
const BY_BASENAME = new Map<string, string>()
for (const f of FILES) {
  const base = path.basename(f.file, '.vue')
  // 同名组件以先扫到的为准；本守卫只关心契约分类，同名同契约不影响判定。
  if (!BY_BASENAME.has(base))
    BY_BASENAME.set(base, f.rel)
}

const TEXT_BY_REL = new Map<string, string>(FILES.map(f => [f.rel, f.text]))

/** 把一个模块说明符解析成相对 src 的 `.vue` 路径；解析不出来返回 null。 */
function resolveModule(spec: string, fromRel: string): string | null {
  let base: string
  if (spec.startsWith('@/')) {
    base = spec.slice(2)
  }
  else if (spec.startsWith('.')) {
    base = path.relative(SRC_DIR, path.resolve(path.dirname(path.join(SRC_DIR, fromRel)), spec)).replaceAll('\\', '/')
  }
  else {
    return null // 第三方包
  }

  const candidates = [
    `${base}.vue`,
    base.endsWith('.vue') ? base : null,
    `${base}/index.ts`,
    `${base}.ts`,
  ].filter((c): c is string => c !== null)

  for (const c of candidates) {
    if (c.endsWith('.vue')) {
      if (TEXT_BY_REL.has(c))
        return c
      continue
    }
    // .ts 桶文件（如 inventory/component/index.ts）：跟一层 `export { default as X } from './x.vue'`
    const text = TEXT_BY_REL.get(c) ?? readTs(c)
    if (text === undefined)
      continue
    const re = /export\s*\{[^}]*\}\s*from\s*'([^']+\.vue)'/
    const m = text.match(re)
    if (m) {
      const nested = path.relative(SRC_DIR, path.resolve(path.dirname(path.join(SRC_DIR, c)), m[1])).replaceAll('\\', '/')
      if (TEXT_BY_REL.has(nested))
        return nested
    }
  }
  return null
}

function readTs(rel: string): string | undefined {
  try {
    return readFileSync(path.join(SRC_DIR, rel), 'utf8')
  }
  catch {
    return undefined
  }
}

/** 从宿主文件的 import 里找标签名对应的模块说明符。 */
function findImportSpec(hostText: string, tag: string): string | null {
  const esc = tag.replaceAll(/[$()*+.?[\\\]^{|}]/g, '\\$&')
  const patterns = [
    new RegExp(`import\\s+${esc}\\s+from\\s*['"]([^'"]+)['"]`),
    new RegExp(`import\\s*\\{[^}]*\\b${esc}\\b[^}]*\\}\\s*from\\s*['"]([^'"]+)['"]`),
  ]
  for (const re of patterns) {
    const m = hostText.match(re)
    if (m)
      return m[1]
  }
  return null
}

function resolveTag(host: VueFile, tag: string): string | null {
  const spec = findImportSpec(host.text, tag)
  if (spec) {
    const resolved = resolveModule(spec, host.rel)
    if (resolved)
      return resolved
  }
  return BY_BASENAME.get(tag) ?? null
}

/**
 * 分类前必须剥掉注释：`KDialog.vue` 的头注释里就写着示例
 * `<KDialog @confirm="props.onConfirm?.(rejectReason)" />`，不剥掉会把一个纯 emit-only
 * 组件误判成 props-call 型（守卫量错对象 = 又一次绿测试掩盖线上缺陷）。
 */
function stripComments(text: string): string {
  return text
    .replaceAll(/\/\*[\s\S]*?\*\//g, '')
    .replaceAll(/<!--[\s\S]*?-->/g, '')
}

function classify(rel: string): 'props-call' | 'emit-only' | 'unknown' {
  const raw = TEXT_BY_REL.get(rel) ?? readTs(rel)
  if (raw === undefined)
    return 'unknown'
  const text = stripComments(raw)
  const isPropsCall = PROPS_CALL_PATTERNS.some(re => re.test(text))
  const isEmitOnly = EMIT_ONLY_PATTERNS.some(re => re.test(text))
  // 两类形态都有 = 它既转调 props 又对外 emit；按 props-call 归类，
  // 因为对外暴露的契约取决于它自己有没有 emit('confirm')。
  if (isPropsCall)
    return 'props-call'
  if (isEmitOnly)
    return 'emit-only'
  return 'unknown'
}

/**
 * 切出每个大写开头组件的开标签属性串。
 *
 * 手写扫描而不是用一条大正则：`/<([A-Z][\w-]*)((?:[^<>"']|"[^"]*")*)>/` 里
 * `[\w-]*` 与后面的属性组都能吃字母，触发 `regexp/no-super-linear-backtracking`
 * （多项式回溯）。改成「先定位 `<Tag`，再逐字符走到本标签的 `>`，途中跳过引号内的
 * 内容」是线性的，而且能正确处理属性值里带 `>` 的情况（如 `:max="a > b ? x : y"`）。
 */
function openTagAttrs(text: string): Array<{ tag: string, attrs: string }> {
  const out: Array<{ tag: string, attrs: string }> = []
  const startRe = /<([A-Z][\w-]*)/g
  let m = startRe.exec(text)
  while (m !== null) {
    const tag = m[1]
    const attrsFrom = m.index + m[0].length
    let j = attrsFrom
    let quote = ''
    while (j < text.length) {
      const ch = text[j]
      if (quote !== '') {
        if (ch === quote)
          quote = ''
      }
      else if (ch === '"' || ch === '\'') {
        quote = ch
      }
      else if (ch === '>') {
        break
      }
      j++
    }
    if (j >= text.length)
      break
    out.push({ tag, attrs: text.slice(attrsFrom, j) })
    startRe.lastIndex = j + 1
    m = startRe.exec(text)
  }
  return out
}

/** 收集全部 confirm/cancel 接线。标签可能跨行，所以按「标签起点 + 到 `>` 为止」切片。 */
function collectWirings(): Wiring[] {
  const out: Wiring[] = []
  for (const host of FILES) {
    for (const { tag, attrs } of openTagAttrs(host.text)) {
      if (!/:on-(?:confirm|cancel)|@(?:confirm|cancel)\b/.test(attrs))
        continue

      const propHits = [...attrs.matchAll(/:on-(confirm|cancel)\s*=/g)]
      const eventHits = [...attrs.matchAll(/@(confirm|cancel)(?:\.[\w-]+)*\s*=/g)]

      for (const h of propHits) {
        out.push({
          host: host.rel,
          tag,
          target: resolveTag(host, tag),
          kind: 'prop',
          event: h[1],
          snippet: h[0].trim(),
        })
      }
      for (const h of eventHits) {
        out.push({
          host: host.rel,
          tag,
          target: resolveTag(host, tag),
          kind: 'event',
          event: h[1],
          snippet: h[0].trim(),
        })
      }
    }
  }
  return out
}

const WIRINGS = collectWirings()

/** 只保留能解析到 `.vue` 源文件的接线；解析不到的单独在一条测试里盯住。 */
const RESOLVED = WIRINGS.filter(w => w.target !== null)

function label(w: Wiring): string {
  return `${w.host} → <${w.tag}> ${w.snippet}`
}

describe('dialogWiring · 弹窗 confirm/cancel 接线契约', () => {
  it('自检：必须真的扫到接线，且两种写法都在场（防止规则退化成永真）', () => {
    expect(RESOLVED.length).toBeGreaterThan(0)
    const props = RESOLVED.filter(w => w.kind === 'prop')
    const events = RESOLVED.filter(w => w.kind === 'event')
    // 两种写法都是项目里的正当用法，任一种消失都说明扫描器坏了而不是代码变干净了。
    expect(props.length, `:on-confirm/:on-cancel 一处都没扫到，扫描器可能失效`).toBeGreaterThan(0)
    expect(events.length, `@confirm/@cancel 一处都没扫到，扫描器可能失效`).toBeGreaterThan(0)
    expect(new Set(RESOLVED.map(w => w.tag)).size).toBeGreaterThan(1)
  })

  it('自检：契约分类器必须能把两个已知样本分到正确的两侧', () => {
    // 已知 props-call 型（内部 `props.onConfirm?.(...)`）
    expect(classify('component/bill/BillRejectDialog.vue')).toBe('props-call')
    // 已知 emit-only 型（内部只 `emit('confirm', ...)`）
    expect(classify('component/framework/KSelector.vue')).toBe('emit-only')
    expect(classify('view/console/wms/inventory/component/selector.vue')).toBe('emit-only')
  })

  it('`:on-confirm` / `:on-cancel` 只能挂在 props-call 型组件上（否则 emit 静默丢失）', () => {
    const bad = RESOLVED
      .filter(w => w.kind === 'prop')
      .filter(w => classify(w.target as string) !== 'props-call')
      .map(label)
    expect(
      bad,
      `以下接线把 :on-${'{confirm|cancel}'} 写在了 emit-only 型组件上：\n`
      + `${bad.join('\n')}\n`
      + '组件内部只 emit，连字符键 on-confirm 永远命中不了 emit 的驼峰查找 ⇒ 点了确定没反应且不报错。\n'
      + '改成 @confirm / @cancel。',
    ).toEqual([])
  })

  it('@confirm / @cancel 只能挂在 emit-only 型组件上（否则 props 契约收不到监听）', () => {
    const bad = RESOLVED
      .filter(w => w.kind === 'event')
      .filter(w => classify(w.target as string) !== 'emit-only')
      .map(label)
    expect(
      bad,
      `以下接线把 @${'{confirm|cancel}'} 写在了 props-call 型组件上：\n`
      + `${bad.join('\n')}\n`
      + '该组件对外暴露的是 IDialogActionProps 的 props 契约，自己不 emit ⇒ 监听不会被调用。\n'
      + '改成 :on-confirm / :on-cancel。',
    ).toEqual([])
  })

  it('每一处接线的组件都必须能解析到源文件（解析不到就等于守卫有盲区）', () => {
    const unresolved = WIRINGS
      .filter(w => w.target === null)
      .map(label)
    expect(
      unresolved,
      `以下组件无法解析到 .vue 源文件，守卫对它们是盲的：\n${unresolved.join('\n')}\n`
      + '补一条 import 解析规则，或把组件加进 BY_BASENAME 能覆盖的命名。',
    ).toEqual([])
  })

  it('已修的两处库存选择器接线必须保持 @confirm（回归钉）', () => {
    const hosts = [
      'view/console/wms/output/component/addFinishEditor.vue',
      'view/console/wms/move/component/editor.vue',
    ]
    for (const host of hosts) {
      const text = TEXT_BY_REL.get(host)
      expect(text, `${host} 不存在`).toBeDefined()
      const wiring = WIRINGS.filter(w => w.host === host && w.tag === 'InventorySelector')
      expect(wiring.length, `${host} 里没扫到 <InventorySelector> 的 confirm 接线`).toBeGreaterThan(0)
      for (const w of wiring) {
        expect(w.kind, `${label(w)} 又退回连字符写法了`).toBe('event')
      }
      // 连字符写法不得再出现（`as never` 那版就是靠它绕过类型检查的）
      expect(text).not.toContain(':on-confirm="onInventoryPicked')
    }
  })
})

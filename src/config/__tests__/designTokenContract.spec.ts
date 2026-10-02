import { readdirSync, readFileSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # 设计 token 契约（Track 3：常量化 / 规范化）
 *
 * 视觉一致性的敌人不是「某个页面丑」，而是**同一个语义在十几个文件里各写一遍字面值**：
 * 阴影写死 `rgba(0,0,0,.02)` 之后，暗色模式无从适配（同一份黑色落影在深色背景上等于没有），
 * 改一次视觉要全仓搜替，漏一处就是不一致。
 *
 * 所以这里把「视觉字面值必须走 token」做成 CI 守卫：
 * - `src/**\/*.vue` 里不允许出现写死的 `box-shadow` 颜色值（一律取 `--kubo-shadow-*`）；
 * - `src/styles/tokens.scss` 必须定义明暗两套同名 token（缺暗色映射 = 暗色下直接继承浅色值）。
 *
 * 判据刻意收窄到 box-shadow：颜色字面值的存量还很大，一次性拉闸只会让守卫被绕过；
 * 先把「已经有完整 token 层级」的阴影钉死，后续按同一模式扩展（见文件尾的扩展说明）。
 */

// 本文件在 src/config/__tests__/ 下 → 仓库根要上三级（写成两级会得到 src/src）
const ROOT = join(__dirname, '..', '..', '..')
const SRC = join(ROOT, 'src')

function walk(dir: string, out: string[] = []): string[] {
  // 用 dirent 判定类型，不做 statSync：src 下存在符号链接/特殊文件时 stat 会抛错，
  // 而守卫只关心普通文件（链接目标若也在 src 下，会被它自己的目录遍历覆盖）。
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name)
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules' || entry.name === '__tests__') {
        continue
      }
      walk(full, out)
    }
    else if (entry.isFile()) {
      out.push(full)
    }
  }
  return out
}

/**
 * 写死的阴影：box-shadow 的值里直接出现 rgb/rgba/#hex，而不是 var(--kubo-shadow-*)
 *
 * 冒号后不再写 `\s*`：`[^;]*` 已经能吃掉空白，两个量词并存会产生可交换的匹配路径
 * （eslint 的 regexp/no-super-linear-backtracking 会判为多项式回溯风险）。
 */
const HARDCODED_SHADOW = /box-shadow\s*:[^;]*(rgba?\(|#[0-9a-fA-F]{3,8})/g

describe('设计 token · 阴影不允许写死字面值', () => {
  it('src/**/*.vue 里的 box-shadow 全部取 --kubo-shadow-* token', () => {
    const offenders: string[] = []
    for (const file of walk(SRC).filter(f => f.endsWith('.vue'))) {
      const text = readFileSync(file, 'utf8')
      const lines = text.split(/\r?\n/)
      lines.forEach((line, index) => {
        if (HARDCODED_SHADOW.test(line)) {
          offenders.push(`${relative(ROOT, file).split(sep).join('/')}:${index + 1}  ${line.trim()}`)
        }
        HARDCODED_SHADOW.lastIndex = 0
      })
    }
    expect(offenders, '写死的阴影无法适配暗色模式，请改用 var(--kubo-shadow-sm|base|lg|overlay)')
      .toEqual([])
  })

  it('tokens.scss 为四档阴影都给了明暗两套值（暗色缺失 = 暗色下继承浅色）', () => {
    const tokens = readFileSync(join(SRC, 'styles', 'tokens.scss'), 'utf8')
    const tiers = ['sm', 'base', 'lg', 'overlay']

    // 以 html.dark **规则**为界切成「浅色段 / 暗色段」，两段都要出现全部四档。
    // 必须匹配带大括号的规则而不是裸字符串：文件头注释里也提到了 `html.dark`，
    // 用 indexOf 会把切分点落在注释里，浅色段就只剩文件头。
    const darkAt = tokens.search(/^\s*html\.dark\s*\{/m)
    expect(darkAt, 'tokens.scss 里找不到 html.dark 暗色规则').toBeGreaterThan(0)
    const lightPart = tokens.slice(0, darkAt)
    const darkPart = tokens.slice(darkAt)

    for (const tier of tiers) {
      const token = `--kubo-shadow-${tier}:`
      expect(lightPart, `浅色段缺 ${token}`).toContain(token)
      expect(darkPart, `暗色段缺 ${token}（暗色下会继承浅色值，落影在深色背景上等于消失）`).toContain(token)
    }
  })

  it('扩展说明：新增视觉字面值类别时，照 box-shadow 的模式加一条断言', () => {
    // 这条不是空测试：它把「守卫怎么长」写在代码里，避免下一个人重新发明。
    // 类别进度：
    //   3. 颜色字面值 → 已落地（见下方两条：清零存量后直接 fail 级）；
    //      兜底口径与 styleContract.spec.ts（view 层 hex 守卫）一致，扩到 rgb 族与组件层。
    // 后续候选（按存量从少到多）：
    //   1. transition/animation 的时长与缓动 → var(--kubo-duration-*) / var(--kubo-ease-*)
    //   2. border-radius 字面值 → var(--kubo-radius-*)
    // 每加一类都先在 CI 里跑出存量清单，清零后再把断言从「告警」升级为「失败」。
    expect(typeof HARDCODED_SHADOW).toBe('object')
  })

  /**
   * 颜色字面值的三类「合法出现」，先剔除再判违规（口径继承 styleContract.spec.ts）：
   * 注释；`var(--x, 兜底)` 的第二参数（兜底可以是 hex / rgb / rgba）；
   * `resolveCssToken('--x', '兜底')` 的第二参数（canvas 侧必须有字面量兜底）。
   */
  const LEGAL_COLOR_SPOTS = [
    /<!--[\s\S]*?-->/g,
    /\/\*[\s\S]*?\*\//g,
    /^\s*\/\/.*$/gm,
    /^\s*\*.*$/gm,
    // 到第一个 `)` 为止是兜底表达式的结尾（rgba 嵌套时），可选再吃掉 var 自己的闭括号
    /var\(\s*--[\w-]+\s*,[^;{}]*?\)\)?/g,
    /resolveCssToken\(\s*'[^']*'\s*,\s*'[^']*'\s*\)/g,
  ]
  const COLOR_LITERAL = /#[0-9a-fA-F]{3,8}\b|rgba?\(/

  it('颜色字面值必须走 token：src/**/*.vue 清零（Track 3 扩展类 3 拉闸）', () => {
    const offenders: string[] = []
    for (const file of walk(SRC).filter(f => f.endsWith('.vue'))) {
      let text = readFileSync(file, 'utf8')
      for (const pattern of LEGAL_COLOR_SPOTS)
        text = text.replace(pattern, ' ')
      text.split(/\r?\n/).forEach((line, index) => {
        if (COLOR_LITERAL.test(line))
          offenders.push(`${relative(ROOT, file).split(sep).join('/')}:${index + 1}  ${line.trim()}`)
      })
    }
    expect(
      offenders,
      `写死的颜色在暗色模式下无从适配、改色必漏（--kubo-scrim / --kubo-on-brand-* / --kubo-print-* / var(--el-*) 已覆盖这些语义）：\n${offenders.join('\n')}`,
    ).toEqual([])
  })

  it('被引用的 --kubo-* 必须都在 tokens.scss 有定义（防 --primary-color 式死引用静默走兜底）', () => {
    const tokens = readFileSync(join(SRC, 'styles', 'tokens.scss'), 'utf8')
    const defined = new Set(
      [...tokens.matchAll(/^\s*(--kubo-[\w-]+)\s*:/gm)].map(m => m[1]),
    )

    // 引用 = var(--kubo-x) / var(--kubo-x, …) / resolveCssToken('--kubo-x', …)。
    // 模板字符串拼出的动态名（`--kubo-occupancy-${level}`）刻意不匹配：
    // 名字不完整，校验它只会误报，档位一致性由 styleContract 钉住。
    const referenced = new Map<string, string>()
    for (const file of walk(SRC).filter(f => f.endsWith('.vue') || f.endsWith('.scss'))) {
      const text = readFileSync(file, 'utf8')
      for (const m of text.matchAll(/var\(\s*(--kubo-[a-z0-9-]+)\s*[,)]/g))
        referenced.set(m[1], relative(ROOT, file).split(sep).join('/'))
      for (const m of text.matchAll(/resolveCssToken\(\s*'(--kubo-[a-z0-9-]+)'/g))
        referenced.set(m[1], relative(ROOT, file).split(sep).join('/'))
    }

    const dead = [...referenced].filter(([name]) => !defined.has(name))
    expect(
      dead,
      `引用了未定义的 token，会永远静默走 fallback（与 --primary-color 历史缺陷同族）：${dead.map(([name, where]) => `${name} ← ${where}`).join(', ')}`,
    ).toEqual([])
    // 哨兵：引用解析必须真的工作过，防止哪天 walk 路径写错把守卫变成空转
    expect(referenced.size, '应至少解析出一批 --kubo-* 引用').toBeGreaterThan(10)
  })
})

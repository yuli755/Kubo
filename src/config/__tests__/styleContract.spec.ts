import { readdirSync, readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { MODULE_ACCENTS, NEUTRAL_ACCENT, OCCUPANCY_LEVELS } from '@/config/palette'

/**
 * # p7 · 视觉一致性契约（设计 token 单一数据源）
 *
 * 2026-09 的风格审计发现，"统一风格"这件事在本仓失败的**不是缺 token 层**，
 * 而是 token 层建好之后没人守着：`styles/tokens.scss` 已经定义了 `--kubo-*` 并映射到
 * `--el-color-*`，但业务页面里仍有 **97 处硬编码十六进制**，集中在 5 个文件，
 * 且由此产生三类真实可见的缺陷：
 *
 * 1. **同一模块在两个页面不同色**：`console/index/index.vue` 与 `index/BusinessOverview.vue`
 *    各维护一份「模块 → 强调色」映射，7 个重叠模块**全部**不一致
 *    （采购在首页是 `#3b82f6`、在业务概览是 `#6366f1`）；`index.vue` 内部还把
 *    `#8b5cf6` 同时分给「往来合作伙伴」与「库位热力态势」两个不同模块。
 * 2. **同一储位同屏两种颜色**：`WarehouseHeatmap.vue` 里占用色阶写了三份、档位还不一样
 *    （图例 5 档 / 进度条 3 档三元 / ECharts 色带 4 色），占用率 50% 的格子
 *    在热力图里是蓝「偏低」、在进度条上是绿「闲置」。
 * 3. **登录页完全不支持暗色模式**：`login.vue` 39 处写死 `#333`/`#999`/`#fff`/`#f0f2f5`，
 *    而项目里有 `ThemeSwitcher`；另有 5 处 `var(--primary-color, #409eff)` ——
 *    `--primary-color` **在全仓没有任何定义**，故永远走 fallback，改主色时登录页不跟随。
 *
 * 修复方式是把色值收成单一数据源（`config/palette.ts` + `styles/tokens.scss`），
 * 本文件负责让它**不再退化**。因此这里的断言刻意分两类：
 * - 「不得再出现裸硬编码色」—— 守住清理成果；
 * - 「跨文件必须逐档一致」—— 这是重点。色值天然要在 CSS（给 SCSS 用）与 TS
 *   （给 ECharts canvas 用，canvas 吃不了 `var()`）两侧各存一份，
 *   **两份就一定会漂移**，所以必须有测试把它们钉在一起，而不是靠注释提醒。
 */

const viewDir = resolve('src/view')
const vueFiles = (readdirSync(viewDir, { recursive: true }) as string[])
  .filter(name => name.endsWith('.vue'))
  .map(name => ({ name, text: readFileSync(join(viewDir, name), 'utf8') }))

/**
 * 去掉「合法出现十六进制」的三类位置，剩下的才算违规：
 * 1. 注释（`/* *\/`、行首 `//`、行首 `*`、`<!-- -->`）—— 文档里记录旧值是必要的；
 * 2. `var(--x, #hex)` 兜底 —— 这是 token 优先的**正确**写法，`error.vue` 与
 *    `KAppShell.vue` 一直这么写，不该被误判；
 * 3. `resolveCssToken('--x', '#hex')` 兜底 —— 同理，canvas 侧必须有字面量兜底，
 *    否则 jsdom / SSR 下取不到变量就会画出无色图形。
 */
function stripLegalHex(text: string): string {
  return text
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')
    .replace(/^\s*\*.*$/gm, '')
    .replace(/var\(\s*--[\w-]+\s*,\s*#[0-9a-fA-F]{3,8}\s*\)/g, 'var()')
    .replace(/resolveCssToken\(\s*'[^']*'\s*,\s*'#[0-9a-fA-F]{3,8}'\s*\)/g, 'resolveCssToken()')
}

const HEX = /#[0-9a-f]{3,8}\b/i
const OCCUPANCY = ['danger', 'empty', 'idle', 'low', 'normal'] as const

describe('p7 · 视觉一致性契约（设计 token 单一数据源）', () => {
  it('src/view 下不得再有裸硬编码色（var/resolveCssToken 兜底与注释除外）', () => {
    const offenders: string[] = []
    for (const file of vueFiles) {
      const lines = stripLegalHex(file.text).split('\n')
      lines.forEach((line, index) => {
        if (HEX.test(line))
          offenders.push(`${file.name}:${index + 1}  ${line.trim()}`)
      })
    }
    expect(offenders, `发现裸硬编码色，请改用 var(--el-*) / var(--kubo-*)：\n${offenders.join('\n')}`).toEqual([])
  })

  it('已废弃的 --primary-color 死变量不得再被引用（全仓无定义，引用即永远走 fallback）', () => {
    const all = [
      ...vueFiles.map(f => [f.name, f.text] as const),
      ...(['src/styles/tokens.scss', 'src/styles/index.scss', 'src/styles/motion.scss'] as const)
        .map(p => [p, readFileSync(resolve(p), 'utf8')] as const),
    ]
    const offenders = all
      .filter(([, text]) => text.includes('var(--primary-color'))
      .map(([name]) => name)
    expect(offenders, `这些文件仍在引用未定义的 --primary-color：${offenders.join(', ')}`).toEqual([])
  })

  it('模块强调色只有一张表：两个页面都查 palette，自身不再声明 color/bg 字面量', () => {
    const index = readFileSync(resolve('src/view/console/index/index.vue'), 'utf8')
    const overview = readFileSync(resolve('src/view/console/index/BusinessOverview.vue'), 'utf8')

    for (const [name, text] of [['index.vue', index], ['BusinessOverview.vue', overview]] as const) {
      expect(text, `${name} 必须从 @/config/palette 取色`).toContain('from \'@/config/palette\'')
      expect(text, `${name} 必须调用 accentOf()`).toContain('accentOf(')
      // 数据里不得再出现 color/bg 十六进制字面量（否则就是又养了一份映射）
      expect(text, `${name} 不得自带强调色字面量`).not.toMatch(/(?:color|bg):\s*'#[0-9a-fA-F]{3,8}'/)
    }

    // 首页卡片的类型里不该再有 color/bg 字段 —— 字段存在就总有一天会被重新填上
    expect(index).not.toMatch(/interface IDashboardAction \{[^}]*\bcolor:/)
  })

  it('首页与业务概览用到的每个 path 都已在 MODULE_ACCENTS 登记（否则静默退化为中性灰）', () => {
    const paths = new Set<string>()
    for (const file of ['src/view/console/index/index.vue', 'src/view/console/index/BusinessOverview.vue']) {
      const text = readFileSync(resolve(file), 'utf8')
      for (const match of text.matchAll(/path:\s*'(\/console\/[^']*)'/g))
        paths.add(match[1])
    }
    // 业务概览的兜底项刻意不登记：它就该是中性灰，用来承接未注册资源
    const unregistered = [...paths].filter(p => p !== '/console/index' && !(p in MODULE_ACCENTS))
    expect(paths.size, '应当从两个页面解析出 path').toBeGreaterThan(8)
    expect(unregistered, `这些 path 未在 MODULE_ACCENTS 登记，会静默显示为中性灰：${unregistered.join(', ')}`).toEqual([])
    expect(NEUTRAL_ACCENT).toEqual({ color: '#6b7280', bg: '#f3f4f6' })
  })

  it('占用色阶在 tokens.scss 与 palette.ts 两侧逐档相等（CSS 与 canvas 双份存储必须钉在一起）', () => {
    const scss = readFileSync(resolve('src/styles/tokens.scss'), 'utf8')
    const ts = readFileSync(resolve('src/config/palette.ts'), 'utf8')

    const fromScss = new Map<string, string>()
    for (const m of scss.matchAll(/--kubo-occupancy-(\w+):\s*(#[0-9a-fA-F]{6})/g))
      fromScss.set(m[1], m[2].toLowerCase())

    const fallbackBlock = ts.slice(ts.indexOf('OCCUPANCY_FALLBACK'))
    const fromTs = new Map<string, string>()
    for (const m of fallbackBlock.matchAll(/(\w+):\s*'(#[0-9a-f]{6})'/gi))
      fromTs.set(m[1], m[2].toLowerCase())

    for (const level of OCCUPANCY) {
      expect(fromScss.get(level), `tokens.scss 缺少 --kubo-occupancy-${level}`).toBeTruthy()
      // empty 的描边 token 也会匹配到 fromTs，故只比对五个档位本身
      expect(fromTs.get(level), `palette.ts 的 OCCUPANCY_FALLBACK 缺少 ${level}`).toBeTruthy()
      expect(
        fromTs.get(level),
        `占用档 ${level} 两侧色值不一致：SCSS=${fromScss.get(level)} TS=${fromTs.get(level)}。`
        + 'canvas 侧必须有字面量兜底，但两份值一旦漂移，暗色/降级环境下热力图就会与页面不同色',
      ).toBe(fromScss.get(level))
    }
  })

  it('占用档位定义唯一：阈值只在 OCCUPANCY_LEVELS，图例类与之一一对应（防 5/4/3 档并存回归）', () => {
    expect(OCCUPANCY_LEVELS.map(l => l.level).sort()).toEqual([...OCCUPANCY].sort())
    // 档位必须由低到高、阈值严格递增，否则 occupancyLevel() 的短路返回会错档
    const maxes = OCCUPANCY_LEVELS.map(l => l.max)
    expect(maxes, '阈值必须递增').toEqual([...maxes].sort((a, b) => a - b))
    expect(maxes.at(-1)).toBe(Number.POSITIVE_INFINITY)

    const heatmap = readFileSync(resolve('src/view/console/wms/inventory/component/WarehouseHeatmap.vue'), 'utf8')
    for (const level of OCCUPANCY) {
      expect(heatmap, `图例缺少 .dot-${level}`).toContain(`&.dot-${level}`)
      expect(heatmap, `.dot-${level} 必须走 token`).toContain(`var(--kubo-occupancy-${level})`)
    }
    // 进度条必须用统一函数，而不是再写一个三元阈值（这正是原来 3 档 vs 5 档的来源）
    expect(heatmap).toContain('occupancyColor(selectedSlot.occupancy)')
    expect(heatmap).not.toMatch(/occupancy\s*>=\s*\d+\s*\?\s*'#/)
    // ECharts 色带同源
    expect(heatmap).toContain('color: occupancyRamp()')
  })

  it('登录页卡片必须自带盒模型与滚动兜底（F54-1/F54-2：内容被裁剪且无处可滚的本体）', () => {
    const login = readFileSync(resolve('src/view/login.vue'), 'utf8')
    // 截取 `.card { … }` 块（.login 的直接子块，内部嵌套以两级缩进计，取到下一个同级 `}` 前的声明区即可）
    const start = login.indexOf('\n  .card {')
    expect(start, 'login.vue 必须存在 `.login > .card` 样式块').toBeGreaterThan(-1)
    // 注释里记录旧值（「原来 overflow:hidden」）是必要的文档，负向断言前先去注释
    const block = login.slice(start, start + 1200).replace(/\/\*[\s\S]*?\*\//g, '')

    // F54-1：全仓没有全局 border-box 重置，竖屏断点 width:100% + 24px 内边距
    // 在 content-box 下算出「视口 + 8px」外宽，卡片左右各裁 4px —— 必须就地 border-box。
    expect(block, 'F54-1 复发：.card 缺 box-sizing: border-box，竖屏下卡片会被裁边')
      .toContain('box-sizing: border-box;')
    // F54-2：页缩放 150% / 矮屏时卡片比视口高，原 overflow:hidden 的 fixed 容器
    // 里上下各裁一截且**无处可滚**（用户「内容被裁剪、F12 才恢复」症状的机械本体）。
    expect(block, 'F54-2 复发：.card 必须允许内部滚动，矮视口下内容要能滚出来')
      .toContain('overflow: auto;')
    expect(block, 'F54-2 复发：.card 缺 max-height，overflow:auto 永远不会触发')
      .toContain('max-height: calc(100vh - 32px);')
    const backToHidden = /overflow:\s*hidden/.test(block)
    expect(backToHidden, '.card 不得回到 overflow: hidden —— 那是 F54-2 的缺陷本体').toBe(false)
  })
})

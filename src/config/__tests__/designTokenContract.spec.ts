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

/** 写死的阴影：box-shadow 的值里直接出现 rgb/rgba/#hex，而不是 var(--kubo-shadow-*) */
const HARDCODED_SHADOW = /box-shadow\s*:\s*[^;]*(rgba?\(|#[0-9a-fA-F]{3,8})/g

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
    // 下一步候选（按存量从少到多）：
    //   1. transition/animation 的时长与缓动 → var(--kubo-duration-*) / var(--kubo-ease-*)
    //   2. border-radius 字面值 → var(--kubo-radius-*)
    //   3. 颜色字面值（#hex / rgb）→ var(--kubo-color-*) 或 EP 的 var(--el-*)
    // 每加一类都先在 CI 里跑出存量清单，清零后再把断言从「告警」升级为「失败」。
    expect(typeof HARDCODED_SHADOW).toBe('object')
  })
})

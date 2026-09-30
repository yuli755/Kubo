/**
 * `element-plus` 具名导入 ↔ 聚合入口 契约测试。
 *
 * ## 为什么需要它
 * `vite.config.mts` 把裸 `element-plus` / `element-plus/es` **别名重定向**到
 * `src/types/element-plus-aggregate.ts`（为了绕开 `es/index.mjs` 的顶层副作用、保住树摇）。
 * 后果是：**源码里 `import { ElXxx } from 'element-plus'` 必须能在聚合入口里找到同名导出**，
 * 否则 `vue-tsc` 与单测全绿、`pnpm build` 才炸（"ElXxx is not exported by …"）。
 *
 * 这个坑真实踩过两次（`UserCard.vue` 的 `ElAvatar` / `ElDropdown*`），每次都要重跑一遍
 * 20 秒的构建才能发现下一个。所以把"导入集合 ⊆ 聚合导出集合"钉成静态用例：
 * 新增组件忘了补聚合入口 → 这里立刻红，并直接告诉你缺哪几个名字。
 */
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'
import process from 'node:process'
import { describe, expect, it } from 'vitest'

const ROOT = process.cwd()
const AGGREGATE = join(ROOT, 'src/types/element-plus-aggregate.ts')

/**
 * 剥注释再扫。
 *
 * 不剥会**自指**：本文件的说明文字里就写着 `import { ElXxx } from 'element-plus'`，
 * 别的 spec 里也有"旧写法长这样"的示例 —— 那是文档，不是真实依赖，
 * 但 grep 式扫描分不清（第一次跑这条用例就是这么红的）。
 */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/^\s*\/\/.*$/gm, '')
}

function walk(dir: string, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      walk(full, out)
    }
    else if (/\.(?:ts|vue)$/.test(entry)) {
      out.push(full)
    }
  }
  return out
}

/** 聚合入口提供了哪些导出名（含 `export type {}`） */
function aggregateExports(): Set<string> {
  const source = readFileSync(AGGREGATE, 'utf8')
  const names = new Set<string>()
  for (const match of source.matchAll(/export\s+(?:type\s+)?\{([^}]+)\}\s*from/g)) {
    for (const piece of match[1].split(',')) {
      const name = piece.trim().split(/\s+as\s+/).pop()?.trim()
      if (name) {
        names.add(name)
      }
    }
  }
  // 默认导出与独立 `export const/class` 都算（当前没有，留个口子避免将来漏判）
  for (const match of source.matchAll(/export\s+(?:default\s+)?(?:const|class|function)\s+(\w+)/g)) {
    names.add(match[1])
  }
  return names
}

describe('element-plus 聚合入口 · 导入必须能解析', () => {
  it('源码里 `from \'element-plus\'` 的每个具名导入，聚合入口都有同名导出', () => {
    const exported = aggregateExports()
    const files = [
      ...walk(join(ROOT, 'src')),
      join(ROOT, 'vite.config.mts'),
    ].filter(file => !file.endsWith('element-plus-aggregate.ts') && !file.endsWith('components.d.ts'))

    const missing: string[] = []
    for (const file of files) {
      const source = stripComments(readFileSync(file, 'utf8'))
      // 只认裸 `element-plus`：子路径（`element-plus/es/locale/...`）走真实包、不经别名
      for (const match of source.matchAll(/import\s+(?:type\s+)?\{([^}]+)\}\s*from\s*'element-plus'/g)) {
        for (const piece of match[1].split(',')) {
          const name = piece.trim().split(/\s+as\s+/)[0]?.trim()
          if (name && !exported.has(name)) {
            missing.push(`${relative(ROOT, file)} 需要 ${name}`)
          }
        }
      }
    }

    expect(
      missing,
      '聚合入口缺导出 → `pnpm build` 会失败（typecheck 查不出来）。请到 src/types/element-plus-aggregate.ts 补对应子路径的 re-export',
    ).toEqual([])
  })

  it('聚合入口的导出名不重复（重复会让 rollup 报 "duplicate export"）', () => {
    const source = readFileSync(AGGREGATE, 'utf8')
    const seen = new Map<string, number>()
    for (const match of source.matchAll(/export\s+(?:type\s+)?\{([^}]+)\}\s*from/g)) {
      for (const piece of match[1].split(',')) {
        const name = piece.trim().split(/\s+as\s+/).pop()?.trim()
        if (name) {
          seen.set(name, (seen.get(name) ?? 0) + 1)
        }
      }
    }
    const duplicated = [...seen.entries()].filter(([, count]) => count > 1).map(([name]) => name)
    expect(duplicated).toEqual([])
  })
})

/**
 * 组件目录名：`element-plus/es/components/<dir>/…`（**不含** `…/style/css`）
 *
 * 单独抽出来是因为下面两条用例都要用同一份解析，而正则写两遍一定会写歪一遍。
 *
 * 不写死 `/index.mjs` 是刻意的：`provideGlobalConfig` 走的是
 * `element-plus/es/components/config-provider/src/hooks/use-global-config.mjs`（深子路径），
 * 写死 index.mjs 就会漏掉它 —— 而它的 `config-provider.css` 正是 `--el-*` 变量的落点，
 * 少一行整个主题变量层都塌。第一次跑这条用例就是这么红的（orphanStyle 报 config-provider）。
 */
function aggregateComponentDirs(source: string): Set<string> {
  const dirs = new Set<string>()
  for (const match of source.matchAll(/element-plus\/es\/components\/([a-z0-9-]+)\/(?!style\/)/g)) {
    dirs.add(match[1])
  }
  return dirs
}

function aggregateStyleDirs(source: string): Set<string> {
  const dirs = new Set<string>()
  for (const match of source.matchAll(/import\s*'element-plus\/es\/components\/([a-z0-9-]+)\/style\/css'/g)) {
    dirs.add(match[1])
  }
  return dirs
}

describe('element-plus 聚合入口 · re-export 的组件必须连样式一起钉住', () => {
  /**
   * ## 这条用例守的是什么
   *
   * `ElementPlusResolver` 只给**它自己在模板里自动解析出来的**组件注入 `style/css`。
   * 项目里 60+ 处都是手写 `import { ElTable, ... } from 'element-plus'`，
   * 标识符已在作用域内 ⇒ resolver 跳过 ⇒ **样式一行都不注入**。
   * 于是"这个组件有没有样式"取决于一件与本页无关的偶然事实：
   * 别的某个文件恰好在模板里裸用过同名组件。
   *
   * 实测后果（2026-09，`/console/asset/material/list` 冷加载，修复前）：
   * EP theme-chalk 只加载了 22 个 css，缺 table / pagination / form / checkbox / popover /
   * card / link / avatar / dropdown ⇒ `.el-pagination` 拿到 `display: block`，
   * 整条分页器被压成 `151x216` 的竖排块，外层 `.k-table__pagination` 被撑到 **222px** 高
   * （正常约 40px）；`.el-table__cell` 的 CSS 规则数为 **0**（表格边框/斑马纹/固定列全靠
   * KTable 的 scoped 样式硬撑）。修复后 51 个 css 全到位，分页器回到 `flex / 448x32`。
   *
   * 所以把不变量钉死：**聚合入口里 re-export 了某个组件目录，就必须同时 import 它的 style/css**。
   * 聚合入口是两个 alias（`/^element-plus$/`、`/^element-plus\/es$/`）的共同落点，
   * 所有裸 `element-plus` 导入都经过它 ⇒ 手写与自动解析两条路径都拿到样式，且只此一份。
   */
  it('每个 re-export 的组件目录都有对应的 style/css 导入（少一行就是裸渲染）', () => {
    const source = readFileSync(AGGREGATE, 'utf8')
    const components = aggregateComponentDirs(source)
    const styled = aggregateStyleDirs(source)

    // 自检：扫出来的目录数不能是个位数，否则说明正则写歪了（守卫自己先要有反向自检）
    expect(components.size, '解析到的组件目录数异常，正则大概写歪了').toBeGreaterThanOrEqual(30)
    expect(components, '聚合入口理应包含这两个目录').toContain('table')
    expect(components, '聚合入口理应包含这两个目录').toContain('pagination')
    expect(styled.size, '样式区一条都没解析到，正则大概写歪了').toBeGreaterThanOrEqual(30)

    const missingStyle = [...components].filter(dir => !styled.has(dir)).sort()
    expect(
      missingStyle,
      '这些组件被 re-export 了但没钉样式：手写 import 它们的页面会「有 DOM 无样式」裸渲染'
      + '（分页器会变竖排、表格没有边框）。请到 src/types/element-plus-aggregate.ts 的样式区按字母序补上 '
      + 'import \'element-plus/es/components/<dir>/style/css\'',
    ).toEqual([])

    const orphanStyle = [...styled].filter(dir => !components.has(dir)).sort()
    expect(
      orphanStyle,
      '样式区钉了却没 re-export 的组件：要么是把组件删了忘删样式，要么是拼错了目录名（拼错会静默加载不到 CSS）',
    ).toEqual([])
  })

  it('样式入口只此一份：源码别处不得再零散 import EP 组件样式', () => {
    const files = walk(join(ROOT, 'src')).filter(file => !file.endsWith('element-plus-aggregate.ts'))
    const scattered: string[] = []
    for (const file of files) {
      const source = stripComments(readFileSync(file, 'utf8'))
      for (const match of source.matchAll(/import\s*'element-plus\/es\/components\/[a-z0-9-]+\/style\/css'/g)) {
        scattered.push(`${relative(ROOT, file)} → ${match[0]}`)
      }
    }
    expect(
      scattered,
      'EP 组件样式必须统一钉在 src/types/element-plus-aggregate.ts 的样式区。'
      + '两处都补 = 没有单一事实来源，正是当年 main.ts 只补了 message/message-box/loading 三个、'
      + '而 table/pagination/form 等九个组件裸渲染却没人发现的原因',
    ).toEqual([])
  })
})

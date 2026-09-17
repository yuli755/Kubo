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

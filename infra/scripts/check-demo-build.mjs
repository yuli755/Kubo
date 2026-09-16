#!/usr/bin/env node
/**
 * # 演示模式产物门禁（CI 第三道闸）
 *
 * 演示模式会替换 axios 适配器、预置登录态（token/租户）—— 它**绝不能出现在生产产物里**。
 * 三道闸见 `infra/plugins/demo-gate.ts`；本脚本是可被 CI 执行的最后一道：
 *
 * ```
 * node infra/scripts/check-demo-build.mjs [distDir]
 * ```
 *
 * 判定规则：扫描 `dist/` 下所有 `.js`，出现任一标记即失败：
 * - `KUBO_DEMO_MODE_v1`   —— 演示模式常量（`src/demo/index.ts` 的 `DEMO_MARKER`）
 * - `__KUBO_DEMO__`       —— 演示模式运行时标记
 * - `installDemoMode`     —— 演示模式入口函数名
 *
 * 说明：这里刻意用**字符串标记**而不是"文件是否存在"——标记只有在演示代码真被打进产物时
 * 才会出现，因此同时也覆盖了"演示代码被意外 import 进业务模块"的情况。
 */

import { readFileSync, readdirSync, statSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..')
const DIST = resolve(ROOT, process.argv[2] ?? 'dist')

const MARKERS = ['KUBO_DEMO_MODE_v1', '__KUBO_DEMO__', 'installDemoMode']

/** 递归收集目录下的 .js 文件 */
function collectJs(dir) {
  const found = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    const stat = statSync(full)
    if (stat.isDirectory()) {
      found.push(...collectJs(full))
    }
    else if (entry.endsWith('.js')) {
      found.push(full)
    }
  }
  return found
}

if (!statSync(DIST, { throwIfNoEntry: false })?.isDirectory()) {
  console.error(`❌ 未找到构建产物目录：${DIST}（先执行 pnpm build）`)
  process.exit(1)
}

const hits = []
for (const file of collectJs(DIST)) {
  const content = readFileSync(file, 'utf-8')
  for (const marker of MARKERS) {
    if (content.includes(marker)) {
      hits.push({ file: file.replace(`${ROOT}/`, ''), marker })
    }
  }
}

if (hits.length > 0) {
  console.error('❌ 生产产物中发现演示模式代码（这是"绕过登录的后门"，禁止上线）：\n')
  for (const { file, marker } of hits) {
    console.error(`   · ${file}  ← 命中标记 ${marker}`)
  }
  console.error('\n排查方向：')
  console.error('  1. 是否用 `--mode demo` 做了构建？（demo-gate 插件本应拦截，请检查 vite.config.mts）')
  console.error('  2. 是否有业务模块静态 import 了 `@/demo/**`？（应改为仅 main.ts 的动态 import）')
  process.exit(1)
}

console.log(`✅ 产物干净：${collectJs(DIST).length} 个 JS 文件均不含演示模式标记（${DIST.replace(`${ROOT}/`, '')}）`)

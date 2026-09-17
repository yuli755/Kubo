// @vitest-environment node
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

/**
 * PWA manifest ↔ `public/` 资源契约
 *
 * ## 为什么要有这条用例
 *
 * PWA manifest 里的 `icons[].src` 是**纯字符串约定**：文件不存在时构建**不会报错**
 * （Vite 只把它当 manifest 的一个字段），直到用户真去装桌面版才 404 —— 一个静默失效的引用。
 *
 * 本仓真实踩过：manifest 声明了 `pwa-192x192.png` / `pwa-512x512.png`，而 `public/` 里
 * 从来没有过这两个文件（2026-09 残留审计发现）。现在图标由
 * `scripts/gen-pwa-icons.mjs` 从 `src/assets/img/logo.svg` 的立方体图元本地生成，
 * 这条用例负责保证"声明了就必须真实存在，且尺寸与声明一致"。
 */
const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..')

/** 从 `vite.config.mts` 的 manifest 块里取 `icons: [...]` 的 `src` 与 `sizes` */
function readManifestIcons(): Array<{ src: string, sizes: string }> {
  const config = readFileSync(join(ROOT, 'vite.config.mts'), 'utf8')
  const icons = config.slice(config.indexOf('icons: ['), config.indexOf('workbox: {'))
  const entries: Array<{ src: string, sizes: string }> = []
  for (const block of icons.split('{').slice(1)) {
    const src = /src:\s*'([^']+)'/.exec(block)?.[1]
    const sizes = /sizes:\s*'([^']+)'/.exec(block)?.[1]
    if (src && sizes) {
      entries.push({ src, sizes })
    }
  }
  return entries
}

/** 读 PNG 的 IHDR：宽高各 4 字节，位于固定偏移 */
function pngSize(file: string): { width: number, height: number } {
  const buf = readFileSync(file)
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}

describe('pwa · manifest 图标与 public/ 资源一致', () => {
  it('声明的每个图标都存在，且 PNG 实际尺寸与 sizes 一致', () => {
    const icons = readManifestIcons()
    expect(icons.length, 'manifest 里一个图标都没有，安装到桌面会是默认灰块').toBeGreaterThan(0)

    for (const icon of icons) {
      const file = join(ROOT, 'public', icon.src)
      expect(existsSync(file), `manifest 声明了 ${icon.src}，但 public/ 下没有（跑 node scripts/gen-pwa-icons.mjs 生成）`).toBe(true)
      const { width, height } = pngSize(file)
      expect(`${width}x${height}`).toBe(icon.sizes)
    }
  })

  it('至少覆盖 192 与 512 两个尺寸（Chromium 安装提示的门槛）', () => {
    const sizes = readManifestIcons().map(icon => icon.sizes)
    expect(sizes).toContain('192x192')
    expect(sizes).toContain('512x512')
  })
})

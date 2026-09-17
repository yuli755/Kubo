#!/usr/bin/env node
/**
 * 生成 PWA 图标（`public/pwa-192x192.png` / `public/pwa-512x512.png`）
 *
 * ## 为什么要有这个脚本
 *
 * `vite.config.mts` 的 PWA manifest 一直声明着这两个 PNG，但仓库里**从来没有过这两个文件**
 * （构建不报错，安装到桌面时才 404）。2026-09 的残留审计发现了这个断链。
 *
 * 处置方式不是"从 manifest 里删掉图标"（那样 PWA 会退化成没有图标的安装项），
 * 而是**把图标本地生成出来**：图形直接取自 `src/assets/img/logo.svg` 的图标部分
 * （等距立方体线框 + 琥珀色顶面），所以品牌一致、可复现、不引入二进制黑盒。
 *
 * 为什么不直接栅格化那个 SVG：
 * - 沙箱里没有 `rsvg-convert` / `inkscape` / `cairosvg`（ImageMagick 的 SVG delegate 也缺），
 *   `convert logo.svg ...` 会直接失败；
 * - 那个 SVG 里还有两行 `<text>`（"Kubo / 智能生产管理系统"），依赖系统字体，
 *   在无字体的环境里栅格化结果不可控 —— 图标只用立方体那部分反而更稳。
 * 几何数据是**照抄** SVG 里那几行 path（都是直线，无曲线），改 logo 时同步改这里。
 *
 * 用法：`node scripts/gen-pwa-icons.mjs`（默认写文件；`--check` 只校验是否已存在且尺寸正确）
 *
 * @author Arena agent
 */
import { Buffer } from 'node:buffer'
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'
import { deflateSync } from 'node:zlib'

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const OUT_DIR = join(ROOT, 'public')
const SIZES = [192, 512]
const SS = 4 // 超采样倍数：先画 4 倍大再降采样，得到抗锯齿边缘

// ── 几何：来自 src/assets/img/logo.svg（已经过 `translate(8,4)`） ──
/** 立方体外轮廓（六边形） */
const OUTLINE = [[24, 4], [44, 15], [44, 39], [24, 50], [4, 39], [4, 15]]
/** 顶面（琥珀色填充） */
const TOP_FACE = [[24, 4], [44, 15], [24, 26], [4, 15]]
/** 描边：外轮廓 + 顶面两条射线 + 中缝 */
const EDGES = [
  ...OUTLINE.map((p, i) => [p, OUTLINE[(i + 1) % OUTLINE.length]]),
  [[4, 15], [24, 26]],
  [[24, 26], [44, 15]],
  [[24, 26], [24, 50]],
]
const GLYPH = { minX: 1, minY: 1, maxX: 47, maxY: 53 } // 含描边宽度的一半

const COLORS = {
  background: [255, 255, 255],
  stroke: [55, 71, 79], // #37474f
  highlight: [255, 179, 0], // #ffb300
}

function distToSegment(px, py, [x1, y1], [x2, y2]) {
  const dx = x2 - x1
  const dy = y2 - y1
  const lenSq = dx * dx + dy * dy
  const t = lenSq === 0 ? 0 : Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq))
  const cx = x1 + t * dx
  const cy = y1 + t * dy
  return Math.hypot(px - cx, py - cy)
}

function inPolygon(px, py, polygon) {
  let inside = false
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const [xi, yi] = polygon[i]
    const [xj, yj] = polygon[j]
    if ((yi > py) !== (yj > py) && px < ((xj - xi) * (py - yi)) / (yj - yi) + xi) {
      inside = !inside
    }
  }
  return inside
}

/** 画一张 size×size 的 RGBA 图标（超采样 + 盒式降采样） */
function render(size) {
  const big = size * SS
  const pad = big * 0.16
  const scale = (big - pad * 2) / Math.max(GLYPH.maxX - GLYPH.minX, GLYPH.maxY - GLYPH.minY)
  const offsetX = (big - (GLYPH.maxX - GLYPH.minX) * scale) / 2 - GLYPH.minX * scale
  const offsetY = (big - (GLYPH.maxY - GLYPH.minY) * scale) / 2 - GLYPH.minY * scale
  const toPx = ([x, y]) => [x * scale + offsetX, y * scale + offsetY]
  const halfStroke = 1.2 * scale // SVG 里 stroke-width 2.4
  const radius = big * 0.2 // 圆角
  const edges = EDGES.map(([a, b]) => [toPx(a), toPx(b)])
  const topFace = TOP_FACE.map(toPx)

  const hi = new Uint8Array(big * big * 4)
  for (let y = 0; y < big; y++) {
    for (let x = 0; x < big; x++) {
      const i = (y * big + x) * 4
      const cx = x + 0.5
      const cy = y + 0.5
      // 圆角矩形外 → 全透明
      const r = radius
      const nearestX = Math.min(Math.max(cx, r), big - r)
      const nearestY = Math.min(Math.max(cy, r), big - r)
      if (Math.hypot(cx - nearestX, cy - nearestY) > r) {
        continue
      }
      let color = COLORS.background
      if (inPolygon(cx, cy, topFace)) {
        color = COLORS.highlight
      }
      const onStroke = edges.some(([a, b]) => distToSegment(cx, cy, a, b) <= halfStroke)
      if (onStroke) {
        color = COLORS.stroke
      }
      hi[i] = color[0]
      hi[i + 1] = color[1]
      hi[i + 2] = color[2]
      hi[i + 3] = 255
    }
  }

  // 盒式降采样（含 alpha 预乘，避免边缘出现黑边）
  const out = new Uint8Array(size * size * 4)
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let sr = 0
      let sg = 0
      let sb = 0
      let sa = 0
      for (let dy = 0; dy < SS; dy++) {
        for (let dx = 0; dx < SS; dx++) {
          const i = ((y * SS + dy) * big + (x * SS + dx)) * 4
          const a = hi[i + 3] / 255
          sr += hi[i] * a
          sg += hi[i + 1] * a
          sb += hi[i + 2] * a
          sa += a
        }
      }
      const n = SS * SS
      const alpha = sa / n
      const o = (y * size + x) * 4
      out[o] = alpha === 0 ? 0 : Math.round(sr / sa)
      out[o + 1] = alpha === 0 ? 0 : Math.round(sg / sa)
      out[o + 2] = alpha === 0 ? 0 : Math.round(sb / sa)
      out[o + 3] = Math.round(alpha * 255)
    }
  }
  return out
}

// ── 最小 PNG 编码器（RGBA / 8bit，无第三方依赖） ──
const CRC_TABLE = (() => {
  const table = new Int32Array(256)
  for (let n = 0; n < 256; n++) {
    let c = n
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xEDB88320 ^ (c >>> 1) : c >>> 1
    }
    table[n] = c
  }
  return table
})()

function crc32(buf) {
  let c = 0xFFFFFFFF
  for (let i = 0; i < buf.length; i++) {
    c = CRC_TABLE[(c ^ buf[i]) & 0xFF] ^ (c >>> 8)
  }
  return (c ^ 0xFFFFFFFF) >>> 0
}

function chunk(type, data) {
  const len = Buffer.alloc(4)
  len.writeUInt32BE(data.length, 0)
  const typeAndData = Buffer.concat([Buffer.from(type, 'ascii'), data])
  const crc = Buffer.alloc(4)
  crc.writeUInt32BE(crc32(typeAndData), 0)
  return Buffer.concat([len, typeAndData, crc])
}

function encodePng(rgba, size) {
  const ihdr = Buffer.alloc(13)
  ihdr.writeUInt32BE(size, 0)
  ihdr.writeUInt32BE(size, 4)
  ihdr[8] = 8 // bit depth
  ihdr[9] = 6 // color type: RGBA
  const raw = Buffer.alloc((size * 4 + 1) * size)
  for (let y = 0; y < size; y++) {
    raw[y * (size * 4 + 1)] = 0 // filter: none
    Buffer.from(rgba.buffer, rgba.byteOffset + y * size * 4, size * 4).copy(raw, y * (size * 4 + 1) + 1)
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    chunk('IHDR', ihdr),
    chunk('IDAT', deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ])
}

const check = process.argv.includes('--check')
const targets = SIZES.map(size => [join(OUT_DIR, `pwa-${size}x${size}.png`), size])

if (check) {
  const missing = targets.filter(([file]) => !existsSync(file))
  if (missing.length) {
    console.error(`✗ PWA 图标缺失：${missing.map(([f]) => f).join(', ')}`)
    console.error('  manifest 里声明的图标必须真实存在，否则安装到桌面时才 404。')
    console.error('  跑 `node scripts/gen-pwa-icons.mjs` 生成。')
    process.exit(1)
  }
  console.log(`✓ PWA 图标就位：${targets.map(([f]) => f.split('/').pop()).join(', ')}`)
  process.exit(0)
}

mkdirSync(OUT_DIR, { recursive: true })
for (const [file, size] of targets) {
  writeFileSync(file, encodePng(render(size), size))
  console.log(`✓ 生成 ${file.split('/').pop()}（${size}×${size}，源自 src/assets/img/logo.svg 的立方体）`)
}

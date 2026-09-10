/**
 * # airpower 整包 CSS 精简插件（删除内嵌的 element-plus 冗余组件样式）
 *
 * ## 背景
 * `src/main.ts` 整包引入 `@airpower/web/dist/web.css`（378 KB 未压缩 / 52 KB gzip）。
 * 实测该文件 92% 是 element-plus 的**全量**样式（341 KB），airpower 自己的
 * `.a-*`/`.web-*` 样式仅约 26 KB。这些 element 样式与项目按需引入的
 * vendor-element.css（base.css + 项目实际使用的组件）大面积重复。
 *
 * element 样式按「谁在用」分三类：
 *
 *   A. airpower 组件内部依赖、但项目 .vue **未直接书写**对应 `<el-xxx>` 标签的组件
 *      （ATable→el-table、AMenu→el-menu、AImage→el-image、AUpload→el-upload、
 *      APage→el-pagination、APopover→el-popover、el-check-tag、AInput 的
 *      cascader 模式→el-cascader）。unplugin-vue-components 只扫描项目 .vue，
 *      不扫描 airpower dist，故这类样式**只存在于 web.css，必须保留**。
 *
 *   B. 项目 .vue 直接使用的组件（el-button/el-input/el-form/el-select/
 *      el-date-picker/el-switch/el-result/el-checkbox/el-tooltip/...）。
 *      这些已由 unplugin-vue-components + ElementPlusResolver 按需引入，
 *      与 web.css 重复，**可删**（vendor-element.css 提供）。
 *
 *   C. 项目与 airpower 都不用的组件（el-carousel/el-slider/el-rate/el-color-picker/
 *      el-transfer/el-tour/el-splitter/el-cascader 之外的栅格 el-row/el-col 等）。
 *      **纯冗余，可删**。
 *
 * ## 方案
 * 保留「A 类（airpower 独用组件）+ airpower 自身样式（.a-* / .web-* / data-v / 过渡 /
 * keyframes/:root 变量）」，删除「B/C 类（其余 .el-* 组件样式）」。收益约 35 KB（gzip）。
 *
 * 关键正确性依赖（已逐一核验）：
 *   - A 类组件（el-table 等）的**内部依赖**（el-scrollbar 滚动、el-checkbox 多选、
 *     el-tooltip 列头提示、el-popper/el-overlay 弹层、el-input/el-tag 编辑与筛选）
 *     均为项目直接使用的组件，样式由 vendor-element.css 提供；
 *   - airpower 样式引用的 `--el-*` 变量（含 --el-menu-hover-bg-color 等 A 类组件变量）
 *     仅定义于 web.css 的 :root，故 **:root 整体保留**，不删除。
 *
 * 白名单为「airpower dist 具名 import + resolveComponent 动态解析、且 vendor-element.css
 * 未覆盖」的组件（前缀匹配覆盖其子组件，如 el-table 覆盖 el-table-column/el-table-filter）。
 */
import type { Plugin } from 'vite'

/**
 * airpower 独用 el 组件白名单（vendor-element.css 未覆盖，须保留）。
 * 前缀匹配：`el-table` 同时覆盖 `el-table-column`/`el-table-filter`/`el-table-v`，
 * `el-cascader` 覆盖 `el-cascader-panel`/`el-cascader-menu`/`el-cascader-node` 等。
 * 注：`el-sub-menu`、`el-pager` 前缀不连续，需单独列出。
 */
const AIRPOWER_ONLY_EL = [
  'el-table',
  'el-menu',
  'el-sub-menu',
  'el-image',
  'el-upload',
  'el-pagination',
  'el-pager',
  'el-popover',
  'el-check-tag',
  'el-cascader',
]

/** airpower 自身样式的选择器特征（.a-* 组件 + 内部 scoped/组件/过渡类） */
const AIRPOWER_SELECTOR_RE
  = /\.a-|data-v-|\.web-|\.selector|\.upload-dialog|\.export-dialog|\.slideInLeft|\.slideInRight|\.body-enter|\.body-leave|\.dialog-enter|\.dialog-leave|\.search-enter|\.search-leave|\.love|\.shake/

interface Block {
  prefix: string
  body: string
}

/** 括号匹配提取顶层块（web.css 为压缩单行；@keyframes 的帧在 body 内，不会拆散） */
function parseBlocks(css: string): Block[] {
  const blocks: Block[] = []
  const n = css.length
  let i = 0
  while (i < n) {
    const brace = css.indexOf('{', i)
    if (brace === -1) {
      break
    }
    const prefix = css.slice(i, brace).trim()
    let depth = 0
    let j = brace
    while (j < n) {
      const ch = css[j]
      if (ch === '{') {
        depth++
      }
      else if (ch === '}') {
        depth--
        if (depth === 0) {
          break
        }
      }
      j++
    }
    blocks.push({ prefix, body: css.slice(brace + 1, j) })
    i = j + 1
  }
  return blocks
}

/** 规则是否保留：airpower 标识 / 非 el 规则 / airpower 独用 el 组件 */
function shouldKeep(selector: string): boolean {
  // airpower 自身样式（含其对 el 的覆盖，如 .a-dialog .el-input__inner）一律保留
  if (AIRPOWER_SELECTOR_RE.test(selector)) {
    return true
  }
  const m = selector.match(/\.(el-[a-z-]+)/)
  if (!m) {
    // 非 el 规则（:root、@charset、过渡类、keyframes 等）保留
    return true
  }
  const comp = m[1]
  return AIRPOWER_ONLY_EL.some(p => comp === p || comp.startsWith(`${p}-`))
}

function stripRedundant(css: string): string {
  const blocks = parseBlocks(css)
  const out: string[] = []
  for (const { prefix, body } of blocks) {
    const p = prefix
    if (p.startsWith('@media')) {
      // web.css 的 @media 均为 el-col 栅格（既非 airpower 也非白名单），删除
      if (!shouldKeep(body)) {
        continue
      }
      out.push(`${p}{${body}}`)
      continue
    }
    if (shouldKeep(p)) {
      out.push(`${p}{${body}}`)
    }
  }
  return out.join('')
}

export function airpowerCssStripPlugin(): Plugin {
  return {
    name: 'airpower-css-strip',
    enforce: 'pre',
    transform(code, id) {
      const normalizedId = id.replace(/\\/g, '/')
      if (!normalizedId.includes('@airpower/web/dist/web.css')) {
        return null
      }

      const stripped = stripRedundant(code)

      // 安全兜底：airpower 样式与关键依赖组件必须一个不少；否则降级返回原文
      const count = (s: string, re: RegExp) => (s.match(re) || []).length
      const aBefore = count(code, /\.a-[a-zA-Z0-9-]+/g)
      const aAfter = count(stripped, /\.a-[a-zA-Z0-9-]+/g)
      const dvBefore = count(code, /data-v-/g)
      const dvAfter = count(stripped, /data-v-/g)
      const critical = AIRPOWER_ONLY_EL.filter(c => !stripped.includes(c))
      if (aAfter !== aBefore || dvAfter !== dvBefore || critical.length > 0) {
        this.warn(
          `airpower-css-strip: 剥离结果异常（.a-* ${aBefore}→${aAfter}，data-v ${dvBefore}→${dvAfter}，`
          + `缺失依赖 ${critical.join(',') || '无'}），降级返回原文`,
        )
        return null
      }

      return { code: stripped, map: null }
    },
  }
}

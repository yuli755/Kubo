/**
 * # 统一调色板 —— 业务模块强调色 / 状态色 / 库位占用色阶的唯一数据源
 *
 * ## 为什么需要这一层
 *
 * 2026-09 的风格审计发现，同一套「模块 → 强调色」映射在两个页面里各写了一份，
 * 且**7 个重叠模块的配色全部不一致**（按路由 path 对比）：
 *
 * | 模块 | `console/index/index.vue` | `index/BusinessOverview.vue` |
 * | --- | --- | --- |
 * | channel/purchase | `#3b82f6` | `#6366f1` |
 * | channel/sale     | `#10b981` | `#06b6d4` |
 * | asset/material   | `#f97316` | `#8b5cf6` |
 * | wms/inventory    | `#06b6d4` | `#10b981` |
 * | wms/input        | `#2563eb` | `#3b82f6` |
 * | wms/output       | `#ea580c` | `#f97316` |
 * | personnel/user   | `#4f46e5` | `#ec4899` |
 *
 * 后果是用户在首页卡片看到的「采购」是蓝色，在业务概览里同一个采购却成了靛色 ——
 * 颜色本来承担着「模块身份识别」的作用，两份定义让它失效。
 * 另外 `index.vue` 内部还把 `#8b5cf6` 同时分给了「往来合作伙伴」与「库位热力态势」两个不同模块。
 *
 * 本文件把这些值收成一处，两个页面都改为查表，**新增模块只需加一行**。
 *
 * ## 取舍：为什么以 `BusinessOverview` 的 7 项为准
 *
 * 它按后端 `data_resource` 的资源码（`wms.input` 等）索引，身份比首页卡片的中文显示名更稳定；
 * 且它已带 `DEFAULT_VISUAL` 兜底结构。故收敛方向是「首页向业务概览对齐」，
 * 这样 `BusinessOverview.vue` 的配色零改动，改动集中在首页一侧，回归面更小。
 *
 * ## 约束
 *
 * - 这里的十六进制是**分类色板**（categorical），与 `styles/tokens.scss` 的**语义 token** 分工不同：
 *   tokens 管「主色 / 状态色 / 圆角 / 阴影 / 间距」这类全局语义，本文件管「第 N 个模块用哪个色相」。
 *   分类色板无法用语义 token 表达，硬塞进 `--kubo-*` 只会污染 token 层。
 * - 但**状态色与占用色阶**属于语义，故 `statusColor()` / `occupancyColor()` 优先从
 *   CSS 变量实时解析，让暗色模式与 tokens 的调整自动生效，十六进制只作兜底。
 * - 业务代码不要再直接写这些十六进制；`styleContract.spec.ts` 有静态守卫。
 */

/** 一个模块的强调色与其浅色底（图标前景 / 图标背景） */
interface ModuleAccent {
  /** 图标与强调文字色 */
  color: string
  /** 图标底衬色（同色相的 50 号浅底） */
  bg: string
}

/**
 * 模块强调色注册表，**以导航 path 为键**。
 *
 * 选 path 而不是资源码或中文名，是因为它是首页卡片与业务概览**唯一都持有**的稳定标识：
 * 首页卡片有 `path` 字段，业务概览的 `RESOURCE_CONFIG` 每项也带 `path`；
 * 而中文显示名会随文案调整漂移，资源码首页侧根本没有。
 *
 * 带查询串的入口（如热力图的 `?view=heatmap`）单独注册，
 * 因为它是与库存列表**不同的功能视图**，理应有自己的识别色。
 */
export const MODULE_ACCENTS: Record<string, ModuleAccent> = {
  // ── 渠道 / ERP ──
  '/console/channel/purchase/list': { color: '#6366f1', bg: '#eef2ff' },
  '/console/channel/sale/list': { color: '#06b6d4', bg: '#ecfeff' },
  '/console/channel/customer/list': { color: '#0ea5e9', bg: '#f0f9ff' },
  '/console/asset/material/list': { color: '#8b5cf6', bg: '#f5f3ff' },

  // ── WMS 仓储 ──
  '/console/wms/inventory/list': { color: '#10b981', bg: '#ecfdf5' },
  '/console/wms/inventory/list?view=heatmap': { color: '#14b8a6', bg: '#f0fdfa' },
  '/console/wms/input/list': { color: '#3b82f6', bg: '#eff6ff' },
  '/console/wms/output/list': { color: '#f97316', bg: '#fff7ed' },
  '/console/wms/move/list': { color: '#059669', bg: '#d1fae5' },

  // ── 人员 / 系统治理 ──
  '/console/personnel/user/list': { color: '#ec4899', bg: '#fdf2f8' },
  '/console/personnel/role/list': { color: '#7c3aed', bg: '#ede9fe' },
  '/console/factory/storage/list': { color: '#0d9488', bg: '#ccfbf1' },
  '/console/system/coderule/list': { color: '#d97706', bg: '#fef3c7' },
}

/** 未注册模块的中性兜底（与 `BusinessOverview` 原 `DEFAULT_VISUAL` 保持一致） */
export const NEUTRAL_ACCENT: ModuleAccent = { color: '#6b7280', bg: '#f3f4f6' }

/**
 * 取模块强调色。
 *
 * 先按完整 path 精确匹配（让 `?view=heatmap` 这类带参入口拿到自己的颜色），
 * 再退化为「去掉查询串」匹配 —— 这样任何指向库存列表的带参链接都至少能拿到库存的颜色，
 * 而不会因为多了个参数就掉到中性灰。两级都未命中才用中性色。
 */
export function accentOf(path?: string | null): ModuleAccent {
  if (!path)
    return NEUTRAL_ACCENT
  const exact = MODULE_ACCENTS[path]
  if (exact)
    return exact
  const base = path.split('?')[0]
  return MODULE_ACCENTS[base] ?? NEUTRAL_ACCENT
}

/**
 * 从 CSS 自定义属性实时取值，取不到则用兜底。
 *
 * 为什么不在 TS 里直接写死状态色：`styles/tokens.scss` 已经在 `html.dark` 下
 * 对色阶做过对比度微调，写死会让暗色模式拿不到那份调整（正是本轮审计里
 * 登录页硬编码 `#333`/`#999` 导致暗色失效的同一类错误）。
 *
 * `getComputedStyle` 在 jsdom 下对未定义的变量返回空串，故必须有 fallback，
 * 否则单测环境里 ECharts 会拿到 `''` 而画不出颜色。
 */
export function resolveCssToken(name: string, fallback: string): string {
  if (typeof window === 'undefined' || typeof getComputedStyle !== 'function')
    return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

/** 状态色种类，与 Element Plus 的 `--el-color-*` 一一对应 */
type StatusKind = 'danger' | 'info' | 'success' | 'warning'

/** EP 默认状态色兜底值（tokens.scss 声明「状态色沿用 EP 默认」，故与 EP 一致） */
const STATUS_FALLBACK: Record<StatusKind, string> = {
  success: '#67c23a',
  warning: '#e6a23c',
  danger: '#f56c6c',
  info: '#909399',
}

/**
 * 取状态色（成功 / 警告 / 危险 / 信息）。
 *
 * ECharts 画在 canvas 上，吃不了 CSS 变量，只能拿到具体色值 ——
 * 所以在**调用时**解析，而不是在模块加载时解析成常量：
 * 模块加载早于主题切换，缓存下来就再也跟不上暗色模式了。
 */
export function statusColor(kind: StatusKind): string {
  return resolveCssToken(`--el-color-${kind}`, STATUS_FALLBACK[kind])
}

/**
 * 图表坐标轴与说明文字色。
 *
 * 原来各图表自己写 `#64748b`（Tailwind slate-500），与 EP 的次级文字色 `#909399` 是两套灰；
 * 统一走 `--el-text-color-secondary` 后，图表文字与页面正文的次级文字同色，且暗色模式自动适配
 * （写死的 slate-500 在暗底上偏亮刺眼，EP 的暗色变量已做过调整）。
 */
export function chartTextColor(): string {
  return resolveCssToken('--el-text-color-secondary', '#64748b')
}

/** 库位占用档位，从空到危险 */
type OccupancyLevel = 'danger' | 'empty' | 'idle' | 'low' | 'normal'

/**
 * 占用档位的**唯一**定义：阈值上界 + 语义色 + 中文标签。
 *
 * 收拢前两处不一致（同一文件里三份定义、档位还不一样）：
 * - SCSS 的图例点有 5 档（empty/idle/low/normal/danger）；
 * - 进度条却用 3 档三元（`>=80` 危险 / `>=60` 警告 / 其余一律绿），
 *   于是占用率 50% 的储位在热力格子里是蓝色「低」、在进度条上却是绿色「闲置」，
 *   **同一储位同屏两种颜色**；
 * - ECharts visualMap 又是 4 色连续渐变，与 5 档图例对不上。
 *
 * 现在阈值只此一处，`max` 为 `Infinity` 的那档是兜底，顺序即档位由低到高。
 */
export const OCCUPANCY_LEVELS: { label: string, level: OccupancyLevel, max: number }[] = [
  { level: 'empty', max: 0, label: '空储位' },
  { level: 'idle', max: 20, label: '闲置 (≤20%)' },
  { level: 'low', max: 60, label: '偏低 (21-60%)' },
  { level: 'normal', max: 80, label: '正常 (61-80%)' },
  { level: 'danger', max: Number.POSITIVE_INFINITY, label: '拥挤 (>80%)' },
]

/**
 * 各档位的兜底色，**必须与 `styles/tokens.scss` 的 `--kubo-occupancy-*` 保持一致**。
 *
 * 只在 `getComputedStyle` 取不到变量时才用（jsdom 单测、SSR、样式表尚未加载）。
 * 值取 Element Plus 状态色，理由见 tokens.scss 里那段注释：
 * 与 EP 同色相后，占用徽章能直接复用 EP 的 light-9 / dark-2 / light-7 三级色阶。
 */
const OCCUPANCY_FALLBACK: Record<OccupancyLevel, string> = {
  empty: '#c0c4cc',
  idle: '#67c23a',
  low: '#409eff',
  normal: '#e6a23c',
  danger: '#f56c6c',
}

/** 按占用率百分比定档（0 → empty，>80 → danger） */
export function occupancyLevel(occupancy: number): OccupancyLevel {
  const value = Number.isFinite(occupancy) ? occupancy : 0
  for (const entry of OCCUPANCY_LEVELS) {
    if (value <= entry.max)
      return entry.level
  }
  return 'danger'
}

/** 按占用率取色；实时解析 CSS 变量，暗色模式下跟随 tokens 调整 */
export function occupancyColor(occupancy: number): string {
  const level = occupancyLevel(occupancy)
  return resolveCssToken(`--kubo-occupancy-${level}`, OCCUPANCY_FALLBACK[level])
}

/** 图例用：档位 + 标签 + 当前主题下的实际颜色 */

/**
 * ECharts `visualMap.inRange.color` 用的连续色带，由低到高。
 *
 * **刻意排除 `empty` 档**：visualMap 是 0-100 的连续映射，若把「空储位」的浅灰放进色带起点，
 * 0-20% 这一段会被灰→绿的插值占据，低占用率的格子会发灰、可读性反而下降。
 * 空储位在图例与格子里仍按 `empty` 的浅灰呈现（见 `occupancyLegend()` / SCSS 的 `.dot-empty`），
 * 只是不参与连续渐变的端点。
 *
 * 颜色仍取自 `OCCUPANCY_LEVELS` 同一张表，故「改色只改一处」这条约束没有被破坏。
 */
export function occupancyRamp(): string[] {
  return OCCUPANCY_LEVELS
    .filter(entry => entry.level !== 'empty')
    .map(entry => resolveCssToken(`--kubo-occupancy-${entry.level}`, OCCUPANCY_FALLBACK[entry.level]))
}

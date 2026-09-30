// @vitest-environment jsdom
import { readdirSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { getModelName, getTableConfigList } from '@/framework'

/**
 * # 表格列宽门禁
 *
 * 这一批规则不是"代码风格"，而是 2026-09 一轮**实测**出来的布局事故的固化：
 * 1440×900 视口下，可用表宽 1131~1142px（`.k-shell__content` 内边距 20 + `.k-content__inner`
 * max-width 1600 + KPanel 内边距），而当时 7 个页面的 Σ 声明列宽超出它 100~350px，
 * 于是表格底部出现一条**横向滚动条**——用户看到的第一屏是"列被切掉一半"。
 * 另一类事故是反方向的：字典表只有 2~3 个业务列时，全部富余宽度被弹性列吸走，
 * `unit` 页的「单位名称」被撑到 448px 装 84px 内容，右侧一片空白。
 *
 * 两类事故都源于同一件事：**列宽写在实体装饰器里，但没有任何一处校验它们的总和**。
 * 所以口径固化在这里，见 `src/framework/types.ts` 的「列宽口径」小节。
 *
 * 为什么用 vitest 而不是 `infra/scripts/check-*.mjs`：
 * 这里要读的是**装饰器注册表**（`getTableConfigList` 已经把 order 降序、剔除 `removed`、
 * 回填 `@KField` 的 label），静态正则扒源码等于把注册表逻辑再抄一遍——
 * 抄出来的那份会和真实渲染慢慢漂移，而 vitest 直接 import 实体走的就是 KTable 读的同一份数据。
 * 附带好处：不需要往 `package.json` 的 `check` 链和 `.github/workflows/ci.yml` 各加一步。
 */

const MODEL_DIR = `${process.cwd()}/src/model`

function listModelFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    if (entry.isDirectory()) {
      // __tests__ 里的桩实体不是页面会渲染的表格，纳进来只会造出一堆假违规
      return entry.name === '__tests__' ? [] : listModelFiles(`${dir}/${entry.name}`)
    }
    return entry.name.endsWith('.ts') ? [`${dir}/${entry.name}`] : []
  })
}

/** 实体类 → 它的可见表格列（`hide: true` 的列仍能在列选择器里勾回来，但不占布局宽度） */
const models = new Map<object, ReturnType<typeof getTableConfigList>>()

for (const file of listModelFiles(MODEL_DIR)) {
  const module = (await import(file)) as Record<string, unknown>
  for (const exported of Object.values(module)) {
    if (typeof exported !== 'function' || models.has(exported as object)) {
      continue
    }
    const columns = getTableConfigList(exported as object)
    if (columns.length) {
      models.set(exported as object, columns)
    }
  }
}

/** 反向自检：注册表要是空的，下面每条断言都会"通过"，等于门禁静默失效 */
function expectRegistryAlive() {
  expect(models.size, '一个带 @KTable 的实体都没扫到，说明 src/model 的路径或导入方式变了').toBeGreaterThan(15)
  const names = [...models.keys()].map(Class => getModelName(Class))
  for (const expected of ['物料', '仓库', '采购单', '权限']) {
    expect(names, `注册表里少了「${expected}」，扫描逻辑可能漏了某个域`).toContain(expected)
  }
}

/** 列的实际占宽：弹性列按 minWidth 计（它至少占这么宽） */
function declaredWidth(column: { width?: number | string, minWidth?: number | string }): number {
  const value = column.width ?? column.minWidth
  return typeof value === 'number' ? value : Number.parseInt(String(value ?? ''), 10) || 0
}

/** KTable.vue 的 `:min-width="column.minWidth ?? 120"` —— 不声明宽度就吃这个默认值 */
const FALLBACK_MIN_WIDTH = 120
/** 序号列 56（KTable 固定）+ 操作列最多 190，1142 可用宽里留给数据列的预算 */
const WIDTH_BUDGET = 900

function visibleColumns(Class: object) {
  return (models.get(Class) ?? []).filter(column => !column.hide)
}

function nameOf(Class: object): string {
  return getModelName(Class)
}

/** 主键/租户/创建人/父节点这类"给人看没有意义"的裸 ID 列名 */
const RAW_ID_KEYS = new Set(['id', 'tenantId', 'creatorId', 'parentId'])

/**
 * 是不是"原始值列"：裸 ID、以 `Id` 结尾的外键、以 `Json`/`Expression` 结尾的序列化片段。
 *
 * 2026-09 实测清过一轮：权限表挂着一个裸 `id` 列（width 100）、数据策略表暴露
 * `subjectId`(110) + `conditionJson`(260)、字段策略表暴露 `resourceId`(110) + `maskExpression`(120)，
 * 四页合计白占约 800px 预算，读者拿到的却是一串数字。全部 hide 之后这条规则应当零命中。
 */
function isRawColumn(key: string): boolean {
  return RAW_ID_KEYS.has(key) || key.endsWith('Id') || key.endsWith('Json') || key.endsWith('Expression')
}

/**
 * Σ 预算的豁免清单：守卫只读实体元数据，看不到页面级的 `exclude-columns`。
 *
 * 豁免是**结构化**的——写明页面在运行时排掉哪一列，算预算时扣掉它的声明宽，
 * 并断言这一列真的存在且默认可见。实体改了列名/列宽而豁免没跟上，这里会红，
 * 不会退化成一张永远有效的免死金牌。
 */
const BUDGET_EXEMPT: Record<string, { excludedKey: string, reason: string }> = {
  库存: {
    excludedKey: 'storageName',
    reason:
      'src/view/console/wms/inventory/list.vue:74 按库存类型二选一：'
      + 'STRUCTURE 时 exclude-columns=[storageName]，否则 =[structureName]（两列都声明 180px）'
      + '⇒ 任一时刻只有 6 列可见。该页还是 .k-page__aside/.k-page__main 双栏布局，'
      + '表格可用宽只有 856px（不是主列表的 1142px），扣掉一列后 Σ=770 ≤ 800（856−56 序号）。',
  },
}

describe('表格列宽口径 · Σ 可见列宽必须在预算内（超了就是横向滚动条）', () => {
  it(`每个实体的可见数据列声明宽合计 ≤ ${WIDTH_BUDGET}px`, () => {
    expectRegistryAlive()
    const over: string[] = []
    for (const [Class] of models) {
      const name = nameOf(Class)
      const columns = visibleColumns(Class)
      const exempt = BUDGET_EXEMPT[name]
      let effective = columns
      if (exempt) {
        const excluded = columns.find(column => String(column.key) === exempt.excludedKey)
        if (!excluded) {
          over.push(`${name}：豁免已失效——页面声明排除的 ${exempt.excludedKey} 不在可见列里，请删掉或改对 BUDGET_EXEMPT`)
          continue
        }
        effective = columns.filter(column => column !== excluded)
      }
      const sum = effective.reduce((total, column) => total + (declaredWidth(column) || FALLBACK_MIN_WIDTH), 0)
      if (sum > WIDTH_BUDGET) {
        const detail = effective
          .map(column => `${column.label || column.key}:${declaredWidth(column) || `默认${FALLBACK_MIN_WIDTH}`}`)
          .join(' + ')
        over.push(`${name}（${sum}px，超出 ${sum - WIDTH_BUDGET}px${exempt ? '，已按豁免扣掉一列' : ''}）\n      ${detail}`)
      }
    }
    expect(
      over,
      '横向溢出：把弹性列收成 minWidth、把定宽列按实测内容宽收紧，或者给低价值列加 hide: true\n'
      + '（hide 是布局取舍不是权限控制，列选择器里仍可勾回）。实测内容宽见各实体注释。',
    ).toEqual([])
  })

  it('每个可见列都显式声明了 width 或 minWidth（不许吃 KTable 的 120 默认值）', () => {
    expectRegistryAlive()
    const missing: string[] = []
    for (const [Class] of models) {
      for (const column of visibleColumns(Class)) {
        if (column.width === undefined && column.minWidth === undefined) {
          missing.push(`${nameOf(Class)}.${String(column.key)}（表头「${column.label}」）`)
        }
      }
    }
    expect(
      missing,
      '不声明宽度的列会一起吃 `min-width: 120` 并平分剩余空间，'
      + '于是"编码所属字段"这种 84px 内容的列被撑到 182px，而旁边真正需要宽度的列被挤成省略号。',
    ).toEqual([])
  })

  /*
   * 这里曾有过一条「每个实体最多一个弹性列（minWidth 无 width）」的规则，已删除，别再加回来。
   *
   * 加它的动机是「多个弹性列会互相抢宽度，摊薄成几列都很宽的空白」，但真机量测证明**因果关系不成立**：
   * - 命中它的 11 个实体里，菜单（菜单路径内容 352px、组件地址 150px）、权限（权限名称 374px、
   *   权限标识 285px）、移库单（移库单号 302px + 目标仓库 335px，Σ 正好 1131 不溢出）
   *   都是**两列都真的需要弹性**，强行定宽只会把它们截成省略号；
   * - 而真正被量测到"448px 的列装着 84px 内容"的单位表，恰恰**只有一个**弹性列 ——
   *   空白来自"3 列表格摊在 1142px 画布上"这个事实，不是弹性列的个数。
   * ⇒ 个数不编码任何缺陷，只产出 11 个假阳性。真正被量测到的缺陷是 Σ 超预算（横向滚动条）、
   *   不声明宽度（一起吃 120 回落值）、重复列、长表头、裸 ID/JSON 列 —— 各自都有独立用例钉住。
   */
  it('原始 ID / 外键 ID / JSON 片段不得作为可见列（对人没有信息量，还白占预算）', () => {
    expectRegistryAlive()
    const raw: string[] = []
    for (const [Class] of models) {
      for (const column of visibleColumns(Class)) {
        if (isRawColumn(String(column.key))) {
          raw.push(`${nameOf(Class)}.${String(column.key)}（表头「${column.label}」）`)
        }
      }
    }
    expect(
      raw,
      '列表读者要的是「哪个仓库/哪个角色/哪个资源」，不是一串数字主键或一段 JSON。'
      + '关系列用名称投影（`formatter: row => row.supplier?.name`），JSON 属于详情抽屉。'
      + '排查时列选择器里仍能勾回来 ⇒ 加 hide: true，不是删字段。',
    ).toEqual([])
  })

  it('同一张表的可见列表头不重复（重复列是纯冗余，白占预算）', () => {
    expectRegistryAlive()
    const dup: string[] = []
    for (const [Class] of models) {
      const seen = new Map<string, string>()
      for (const column of visibleColumns(Class)) {
        const label = String(column.label ?? column.key)
        const previous = seen.get(label)
        if (previous) {
          dup.push(`${nameOf(Class)}：「${label}」同时来自 ${previous} 和 ${String(column.key)}`)
        }
        seen.set(label, String(column.key))
      }
    }
    expect(
      dup,
      '同一个关系的两个投影（例如「供应商」和「供应商名称」）在列表上并排出现，'
      + '内容一样、宽度翻倍。留信息量大的那个，另一个 hide 掉。',
    ).toEqual([])
  })

  it('可见列表头 ≤ 8 个字符（长表头会把整列撑宽，说明文字属于字段注释/tooltip）', () => {
    expectRegistryAlive()
    const long: string[] = []
    for (const [Class] of models) {
      for (const column of visibleColumns(Class)) {
        const label = String(column.label ?? column.key)
        if (label.length > 8) {
          long.push(`${nameOf(Class)}.${String(column.key)}：「${label}」（${label.length} 字）`)
        }
      }
    }
    expect(
      long,
      '表头是列宽的下限：「可过滤列（条件树白名单）」11 个字直接把这列顶到 200px，'
      + '而它的内容只有 5px 宽。用 @KTable({ label: \'短标题\' }) 覆盖，'
      + '完整说明留在 @KField 的注释或列 tooltip 里。',
    ).toEqual([])
  })
})

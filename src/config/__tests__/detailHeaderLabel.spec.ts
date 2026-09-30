import fs from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * # 详情页头部标签守卫（N-11）
 *
 * ## 它防什么
 * 六个单据/主数据详情页（出库、移库、入库、销售、采购、供应商）头部都有一组
 * `ElDescriptions`，标签由本地的 `fieldLabel(key)` 产出。原先六处都写成：
 *
 * ```ts
 * return getTableConfig(Model, key)?.label ?? key   // ← 错
 * ```
 *
 * `getTableConfig` 读的是 **@KTable 列配置**，而头部字段恰恰大多不是列表列
 * （`status` / `createTime` / `updateTime` / `billCode` 在实体上只有 @KField，
 * 没有 @KTable，或者被 `hide: true` 排除在列表之外）⇒ 查不到配置，
 * `?? key` 就把**原始英文键名**印到了用户界面上。真机实测原文：
 * `出库单详情 billCode OUT202609300005 type 销售出库 status 出库中 createTime 2026-09-30 18:58:11 …`
 * —— 同一页下面的明细表列头却是正常中文（明细表走 `getTableConfigList`，
 * 它内部 `label: item.label || getFieldLabel(...)` 会回填），
 * 于是「同一页一半中文一半英文」，观感割裂。
 *
 * ## 正确 API
 * `getFieldLabel(Class, field)` = `getFieldConfig(Class, field)?.label || field`，
 * 读的是 **@KField**（全字段都有），且**返回值一定是 string**，不需要 `?? key` 兜底。
 * 框架自己的用法就是范本：`src/component/framework/KDetail.vue:48`
 * `return getFieldLabel(EntityClass.value, key)`；
 * `src/framework/metadata.ts:172` 的 `getTableConfigList` 也用 `getFieldLabel` 回填列标题。
 *
 * ## 为什么值得一条守卫
 * 这类退化**不会报错、不会红测试、页面照常渲染**，只有肉眼扫过标签才会发现。
 * 六处同时写错说明它是「照着邻居抄」扩散的 —— 没有守卫就还会再抄第七处。
 * 与 dialogWiring.spec.ts 同一类教训：绿测试掩盖线上缺陷，本项目已付过多次学费。
 */

const SRC_DIR = path.resolve(process.cwd(), 'src')

interface Source {
  file: string
  rel: string
  text: string
}

function walkVue(dir: string, out: Source[] = []): Source[] {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      walkVue(full, out)
    }
    else if (entry.name.endsWith('.vue')) {
      out.push({ file: full, rel: path.relative(SRC_DIR, full).replaceAll('\\', '/'), text: fs.readFileSync(full, 'utf8') })
    }
  }
  return out
}

/** 详情页头部标签助手：`function fieldLabel(key: string): string { … }` 的函数体 */
function fieldLabelBody(text: string): string | null {
  const m = /function\s+fieldLabel\s*\([^)]*\)\s*:\s*string\s*\{/.exec(text)
  if (!m) {
    return null
  }
  const start = m.index + m[0].length
  let depth = 1
  let i = start
  while (i < text.length && depth > 0) {
    const ch = text[i]
    if (ch === '{') {
      depth++
    }
    else if (ch === '}') {
      depth--
    }
    i++
  }
  return text.slice(start, i - 1)
}

const SOURCES = walkVue(SRC_DIR)
const HELPERS = SOURCES
  .map(s => ({ rel: s.rel, body: fieldLabelBody(s.text) }))
  .filter((h): h is { rel: string, body: string } => h.body !== null)

describe('详情页头部标签（N-11）', () => {
  it('自检：必须真的扫到了头部标签助手，且覆盖已知的六个详情页', () => {
    // 规则退化成永真（扫不到任何助手）时必须自己先红
    expect(HELPERS.length).toBeGreaterThanOrEqual(6)
    const rels = HELPERS.map(h => h.rel)
    for (const expected of [
      'view/console/wms/output/component/detail.vue',
      'view/console/wms/move/component/detail.vue',
      'view/console/wms/input/component/detail.vue',
      'view/console/channel/sale/component/detail.vue',
      'view/console/channel/purchase/component/detail.vue',
      'view/console/channel/supplier/component/detail.vue',
    ]) {
      expect(rels, `缺少详情页头部标签助手：${expected}`).toContain(expected)
    }
  })

  it('fieldLabel 必须用 @KField 的 getFieldLabel，不得用 @KTable 的 getTableConfig 取标签', () => {
    const offenders = HELPERS
      .filter(h => !/getFieldLabel\s*\(/.test(h.body) || /getTableConfig\s*\([^)]*\)\s*\?\.\s*label/.test(h.body))
      .map(h => `${h.rel} → ${h.body.trim().replaceAll(/\s+/g, ' ')}`)
    expect(
      offenders,
      `${[
        '详情页头部标签必须来自 getFieldLabel(Model, key)（读 @KField，返回一定是 string）。',
        '用 getTableConfig(Model, key)?.label ?? key 时，非列表列（status / createTime / updateTime / billCode）',
        '查不到配置，会把原始英文键名印到界面上 —— 就是 N-11 那个「同一页一半中文一半英文」的缺陷。',
        '范本见 src/component/framework/KDetail.vue:48。违规清单：',
      ].join('\n')}\n${offenders.join('\n')}`,
    ).toEqual([])
  })
})

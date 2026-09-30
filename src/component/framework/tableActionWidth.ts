/**
 * 操作列宽度估算 —— `KTable` 与 `KDetailTable` 共用。
 *
 * 只此一份：两个组件都渲染「文字 link 按钮 + 固定列宽」的操作列，写两份实现必然漂移
 * （本轮之前 `KTable` 是按按钮个数拍档位、`KDetailTable` 是写死 160px，两边都超配）。
 *
 * 为什么不按按钮个数拍档位：原 `KTable` 实现是
 * `count<=1→90, <=2→140, <=3→190, <=4→230, else Math.min(count*58, 360)`，
 * 对 2 字标签严重超配 —— 实测采购单 5 个按钮（查看/审核/驳回/再来一单/编辑）的文字只需要
 * 174px，操作列却占 290px；合同页 348px；`KDetailTable` 默认的 160px 只装一个「作业」按钮
 * （30px 文字）。1131px 的可用宽度里被操作列吃掉 26%~31%，是采购/销售/合同三页横向滚动的
 * 最大单一成因（数据列本身的内容宽都远小于列宽）。
 *
 * 实测校准（`size="small"` 的 link 按钮，1440×900）：`查看` = 29.6px、`再来一单` = 53.6px
 * ⇒ CJK 每字 12px、底噪 5.6px，与 `actionButtonWidth` 一致。
 * 改完后复测：采购单操作列 290 → 230，`.k-table__actions` 内容宽 205.2（= 230 − 24 内边距），
 * 估宽与真实渲染吻合到 1px 以内。
 */

/** 单个按钮的文字宽：CJK 12px/字，其它字符 7px/字，加按钮自身 6px 内边距。 */
function actionButtonWidth(label: string): number {
  let width = 6
  for (const char of label) {
    width += /[\u2E80-\u9FFF\uFF00-\uFFEF]/.test(char) ? 12 : 7
  }
  return width
}

interface IActionColumnWidthOptions {
  /** 下限，默认 90（一个按钮也不至于窄到把表头「操作」挤成两行） */
  min?: number
  /** 上限，默认 360（再多的按钮就该收进「更多」下拉，而不是把列撑宽） */
  max?: number
  /**
   * 相邻按钮的间距，默认 8。
   *
   * 必须与组件样式里的真实间距一致，否则估宽与渲染对不上：
   * - `KTable` 的 `.k-table__actions` 是 `display:flex; gap:8px` 且把 Element Plus 的
   *   `.el-button + .el-button { margin-left: 12px }` 清零了 ⇒ 传 8；
   * - `KDetailTable` 的按钮直接排在单元格里（没有 flex 容器），间距就是 EP 那 12px ⇒ 传 12。
   * 两套间距叠加时实测每个间隔 23.3px，这正是改前操作列超配的一部分来源。
   */
  gap?: number
}

/**
 * 操作列宽 = Σ 按钮文字宽 + (n−1)×gap + 24px 单元格左右内边距，夹在 [min, max]。
 *
 * 调用方按**自己的渲染口径**拼 labels：`KTable` 是 `actions` + 树表的「添加子级」
 * + 未隐藏的编辑/启停/删除，`KDetailTable` 是 `rowActions`。
 * `v-permission` 可能让某个按钮对当前用户不渲染，这里仍按全部计入（宁宽勿窄）。
 * labels 为空时返回 `min`。
 */
export function estimateActionColumnWidth(labels: string[], options: IActionColumnWidthOptions = {}): number {
  const { min = 90, max = 360, gap = 8 } = options
  if (!labels.length) {
    return min
  }
  const text = labels.reduce((sum, label) => sum + actionButtonWidth(label), 0)
  const gaps = (labels.length - 1) * gap
  return Math.max(min, Math.min(text + gaps + 24, max))
}

/**
 * # element-plus 按需聚合入口（树摇友好）
 *
 * ## 背景
 * `element-plus` 主入口 `es/index.mjs` 在顶层存在副作用：
 *   - `import defaults_default from "./defaults.mjs"`（其中 `defaults_default = makeInstaller([...component_default, ...plugin_default])`）
 *   - `const install = defaults_default.install; var element_plus_default = defaults_default;`
 * 这些顶层语句引用了 `defaults_default`（→ `component.mjs` → 全部 80 个组件），
 * 致使 Rollup 将 `index.mjs` 视为「有副作用」模块，无法对其顶层 80 个组件 import 做树摇。
 *
 * 只要项目源码以 `import { ElButton } from "element-plus"` 解析到 `index.mjs`，
 * 全量 80 组件的「壳定义」就会进入 bundle（即便未被使用）。
 * 经验证：在 `minify: false` 下 `vendor-element` 约 2.0MB / gzip 431KB，其中大量为未用组件壳。
 *
 * ## 方案
 * 本聚合模块仅从 **子路径**（`element-plus/es/components/<dir>/index.mjs`）
 * re-export 实际使用的具名导出，**完全不经过 `index.mjs`**，从而：
 *   1. 不触发 `defaults_default` / `component.mjs` 全量导入链
 *   2. 仅打包被显式 re-export（即被实际引用）的组件，其余可被 Rollup 树摇
 *
 * 通过 `vite.config.mts` 的 `resolve.alias` 将裸 `element-plus`（精确匹配，
 * 不影响 `element-plus/es/...` 子路径）重定向到本模块，即可让所有
 * `import { ... } from "element-plus"` 走聚合入口。
 *
 * ## 曾经的 `default` 导出已删除（2026-09-18）
 * 它当初只为 `@airpower/web/dist/main.js` 顶部的 `import ElementPlus from "element-plus"`
 * 默认导入存在（dev 的依赖预构建不经过插件 transform，缺 default 会直接报
 * "No matching export ... for import 'default'"）。airpower 拔除后**没有任何 default 导入方**，
 * 跟着一起删 —— 顺带删掉了 `airpower-element-plus-shim` 插件（它专为替换那行默认导入而生）。
 *
 * ## 维护
 * 新增 element-plus 组件 / 命令式 API / 类型时，需在此追加对应子路径 re-export，
 * 否则 TS 与运行时会报缺失导出。已逐一核验下列每个子路径的具名导出名（见 PR 注释）。
 *
 * **并且必须同时追加下面样式区里对应的 `style/css` 导入** —— 少一行，那个组件就是
 * 「有 DOM 无样式」的裸渲染（守卫见 `src/config/__tests__/elementPlusAggregate.spec.ts`）。
 */

/**
 * # 组件样式区（本模块是**唯一**的 EP 样式入口，别处不要再零散补）
 *
 * ## 为什么样式必须由本模块负责
 * `vite.config.mts` 用 `unplugin-vue-components` + `ElementPlusResolver` 做按需导入，
 * resolver 的样式注入**只对它自己在模板里自动解析出来的组件生效**。而本项目源码里
 * 几乎所有 `.vue`/`.ts` 都是**手写** `import { ElTable, ... } from 'element-plus'`
 * （60+ 处）—— 手写导入的标识符已在作用域内，resolver 直接跳过，**样式一行都不会注入**。
 *
 * 于是样式能否加载取决于一件与本页无关的偶然事实：*别的*某个文件恰好在模板里
 * 裸用了同名组件。实测（2026-09，`/console/asset/material/list` 冷加载）：
 *
 * | 组件 | 渲染出的类名 | EP 样式 | 现场症状 |
 * |------|--------------|---------|----------|
 * | ElPagination | `.el-pagination` `.el-pager` `.el-pagination__sizes` | **缺失** | `display: block` 而非 flex，整条分页器被压成 `151x216` 竖排块，外层 `.k-table__pagination` 被撑到 **222px** 高（正常约 40px） |
 * | ElTable | `.el-table__cell` `.el-table--border` `.el-table--striped` `.el-table-fixed-column--right` | **缺失** | `.el-table` 只剩 3 条规则（全部来自项目自己的 scss），边框/斑马纹/固定列/表头背景全靠 KTable 的 scoped 样式硬撑 |
 * | ElForm | `.el-form--inline` `.el-form-item__label` `.el-form-item__content` | **缺失** | 搜索区标签与输入框失去 EP 的行内布局 |
 * | ElCheckbox / ElPopover | `.el-checkbox__inner` `.el-popover` | **缺失** | 列选择器弹层与勾选框裸渲染 |
 * | ElCard / ElLink / ElAvatar / ElDropdown | `.el-card__header` `.el-link__inner` `.el-avatar--circle` `.el-dropdown-menu__item` | **缺失** | KPanel 卡片头、正文链接、顶栏头像与下拉菜单裸渲染 |
 * | ElButton / ElInput / ElSelect / ElTag / ElMenu … | — | 已加载 | 恰好有文件在模板里裸用，resolver 替它们注入了 |
 *
 * 「偶然被别的文件带出来」不是可维护的契约。本模块是 `vite.config.mts` 里
 * `/^element-plus$/` 与 `/^element-plus\/es$/` 两个 alias 的共同落点，
 * **所有**裸 `element-plus` 导入都经过它 ⇒ 把样式钉在这里，手写导入与自动解析
 * 两条路径就都拿到样式，且只此一份。
 *
 * ## 层叠顺序（与 `src/styles/index.scss` 的约定一致，别调换）
 * `main.ts` 先 import 本模块（L2）、后 import `@/styles/index.scss`（L22），
 * 所以 EP 组件样式**先**进 bundle，项目 token 层（`--kubo-*` → `--el-color-*`）
 * 与 motion 层随后覆盖它 —— 正是 `index.scss` 头部写明的期望顺序。
 *
 * ## 体积
 * 这 38 个组件的 theme-chalk 合计约 205KB raw / **28.2KB gzip**（实测）。
 * `pnpm size` 按 **gzip** 计量（`infra/scripts/check-bundle-size.mjs:62`），
 * 首屏预算 450KB、总体 900KB ⇒ 全量钉死样式的代价约 3~4% 预算，
 * 换掉的是「样式看运气」的结构性缺陷。若将来要收缩，收缩口径应是
 * 「按路由拆样式」，不是「删掉某一行让它退回偶然加载」。
 *
 * 子路径写 `.../style/css`（Vite 解析到 `style/css.mjs`），
 * 两个 alias 都用 `$` 精确锚定，**不会**把这类子路径重定向回本模块（否则自指死循环）。
 */
import 'element-plus/es/components/alert/style/css'
import 'element-plus/es/components/avatar/style/css'
import 'element-plus/es/components/breadcrumb/style/css'
import 'element-plus/es/components/button/style/css'
import 'element-plus/es/components/card/style/css'
import 'element-plus/es/components/check-tag/style/css'
import 'element-plus/es/components/checkbox/style/css'
import 'element-plus/es/components/config-provider/style/css'
import 'element-plus/es/components/date-picker/style/css'
import 'element-plus/es/components/descriptions/style/css'
import 'element-plus/es/components/dialog/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/dropdown/style/css'
import 'element-plus/es/components/empty/style/css'
import 'element-plus/es/components/form/style/css'
import 'element-plus/es/components/icon/style/css'
import 'element-plus/es/components/image/style/css'
import 'element-plus/es/components/input/style/css'
import 'element-plus/es/components/input-number/style/css'
import 'element-plus/es/components/link/style/css'
import 'element-plus/es/components/loading/style/css'
import 'element-plus/es/components/menu/style/css'
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/pagination/style/css'
import 'element-plus/es/components/popover/style/css'
import 'element-plus/es/components/progress/style/css'
import 'element-plus/es/components/radio/style/css'
import 'element-plus/es/components/result/style/css'
import 'element-plus/es/components/scrollbar/style/css'
import 'element-plus/es/components/select/style/css'
import 'element-plus/es/components/switch/style/css'
import 'element-plus/es/components/table/style/css'
import 'element-plus/es/components/tabs/style/css'
import 'element-plus/es/components/tag/style/css'
import 'element-plus/es/components/tooltip/style/css'
import 'element-plus/es/components/tree/style/css'
import 'element-plus/es/components/upload/style/css'

export { ElAlert } from 'element-plus/es/components/alert/index.mjs'
export { ElAvatar } from 'element-plus/es/components/avatar/index.mjs'
// 以下三者为新壳 `KAppShell.vue`（侧栏滚动 / 顶部面包屑 / 移动端抽屉）新增使用
export { ElBreadcrumb, ElBreadcrumbItem } from 'element-plus/es/components/breadcrumb/index.mjs'
export { ElButton } from 'element-plus/es/components/button/index.mjs'
export { ElCard } from 'element-plus/es/components/card/index.mjs'
export { ElCheckTag } from 'element-plus/es/components/check-tag/index.mjs'
// ── 项目源码使用的组件（components.d.ts 声明 + main.ts 命令式 API）────────
export { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
export { provideGlobalConfig } from 'element-plus/es/components/config-provider/src/hooks/use-global-config.mjs'
export { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
export { ElDescriptions, ElDescriptionsItem } from 'element-plus/es/components/descriptions/index.mjs'
export { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
export { ElDrawer } from 'element-plus/es/components/drawer/index.mjs'
// dropdown 子路径同时导出 ElDropdown / ElDropdownMenu / ElDropdownItem
// （`UserCard.vue` 的顶栏头像菜单用；2026-09 曾因"无人引用"删过，重用时又踩了一次 alias 缺导出的构建错）
export { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
// 注：曾从这里 re-export 的 `ElTimeline*` 全仓无人引用，2026-09 清理时删除；
// 新用到时按本文件顶部的维护说明补回对应子路径。
export { ElEmpty } from 'element-plus/es/components/empty/index.mjs'
// ── 类型导出（FormInstance / TreeInstance）─────────────────────────────
export type { FormInstance } from 'element-plus/es/components/form/index.mjs'
// form 子路径同时导出 ElForm 与 ElFormItem
export { ElFormItem } from 'element-plus/es/components/form/index.mjs'
export { ElForm } from 'element-plus/es/components/form/index.mjs'
export { ElIcon } from 'element-plus/es/components/icon/index.mjs'
export { ElImage } from 'element-plus/es/components/image/index.mjs'
export { ElInputNumber } from 'element-plus/es/components/input-number/index.mjs'
export { ElInput } from 'element-plus/es/components/input/index.mjs'
export { ElLink } from 'element-plus/es/components/link/index.mjs'
// ── 指令与全局配置（项目自用）──────────────────────────────────────────
// vLoading 指令由 unplugin-vue-components 注入为 `import { ElLoadingDirective } from 'element-plus/es'`，
// 必须在此 re-export，否则别名重定向后指令解析失败。
export { ElLoading, ElLoadingDirective } from 'element-plus/es/components/loading/index.mjs'
// menu 子路径同时导出 ElMenu / ElMenuItem / ElSubMenu
export { ElMenu, ElMenuItem, ElSubMenu } from 'element-plus/es/components/menu/index.mjs'
export { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
export { ElMessage } from 'element-plus/es/components/message/index.mjs'
export { ElPagination } from 'element-plus/es/components/pagination/index.mjs'
export { ElPopover } from 'element-plus/es/components/popover/index.mjs'
export { ElProgress } from 'element-plus/es/components/progress/index.mjs'
// radio 子路径同时导出 ElRadioGroup / ElRadioButton
export { ElRadioButton, ElRadioGroup } from 'element-plus/es/components/radio/index.mjs'
export { ElResult } from 'element-plus/es/components/result/index.mjs'

export { ElScrollbar } from 'element-plus/es/components/scrollbar/index.mjs'
// select 子路径同时导出 ElSelect / ElOption
export { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
export { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
// table 子路径同时导出 ElTable / ElTableColumn
export { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
// tabs 子路径同时导出 ElTabs / ElTabPane
export { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
export { ElTag } from 'element-plus/es/components/tag/index.mjs'
// tooltip 子路径导出 ElTooltip（未导出 ElTooltipContent）
export { ElTooltip } from 'element-plus/es/components/tooltip/index.mjs'
export type { TreeInstance } from 'element-plus/es/components/tree/index.mjs'
export { ElTree } from 'element-plus/es/components/tree/index.mjs'
export { ElUpload } from 'element-plus/es/components/upload/index.mjs'

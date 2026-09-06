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
 * 只要 airpower / 项目源码以 `import { ElButton } from "element-plus"` 解析到 `index.mjs`，
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
 * ## default 导出
 * `@airpower/web/dist/main.js` 顶部含 `import ElementPlus, { ... } from "element-plus"`
 * 默认导入，在 **dev 模式** 下 vite 的 esbuild 依赖预构建直接读 node_modules 源文件，
 * 不经过插件 transform（airpower-element-plus-shim 仅在 build/prod 的 transform 阶段生效）。
 * 因此聚合模块必须提供 `default` 导出，否则 dev 预构建报
 * "No matching export ... for import 'default'"。
 *
 * 该 default 导出复用 airpower shim 同款最小 install：仅注册 v-loading 指令 + locale，
 * 不注册任何全局组件（与 build 期 shim 替换体等价），不会重新拉入全量组件。
 *
 * ## 维护
 * 新增 element-plus 组件 / 命令式 API / 类型时，需在此追加对应子路径 re-export，
 * 否则 TS 与运行时会报缺失导出。已逐一核验下列每个子路径的具名导出名（见 PR 注释）。
 */

import type { App } from 'vue'
import { provideGlobalConfig as _provideGlobalConfig } from 'element-plus/es/components/config-provider/src/hooks/use-global-config.mjs'

// ── default 导出（供 airpower `import ElementPlus from "element-plus"` 使用）──
// dev 预构建不经过 transform，shim 不会替换 airpower 的默认导入行，故此 default 必须存在。
// 与 build 期 airpower-element-plus-shim 的 install 体等价：仅 v-loading 指令 + locale，
// 不注册任何全局组件（项目 .vue 的 <el-xxx> 已由 unplugin-vue-components 局部注册），
// 因此不会重新拉入全量组件，保持树摇友好。
import { ElLoading as _ElLoading } from 'element-plus/es/components/loading/index.mjs'

export { ElAlert } from 'element-plus/es/components/alert/index.mjs'
export { ElButton } from 'element-plus/es/components/button/index.mjs'
export { ElCheckTag } from 'element-plus/es/components/check-tag/index.mjs'
// ── 项目源码使用的组件（components.d.ts 声明 + main.ts 命令式 API）────────
export { ElCheckbox } from 'element-plus/es/components/checkbox/index.mjs'
export { provideGlobalConfig } from 'element-plus/es/components/config-provider/src/hooks/use-global-config.mjs'
export { ElDatePicker } from 'element-plus/es/components/date-picker/index.mjs'
// dropdown 子路径同时导出 ElDropdown / ElDropdownItem / ElDropdownMenu
export { ElDropdown, ElDropdownItem, ElDropdownMenu } from 'element-plus/es/components/dropdown/index.mjs'
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
// ── airpower dist/main.js 具名依赖（25 个）──────────────────────────────
export { ElLink } from 'element-plus/es/components/link/index.mjs'
// ── airpower shim 依赖（ElLoading 指令 + provideGlobalConfig locale）─────
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
// select 子路径同时导出 ElSelect / ElOption
export { ElOption, ElSelect } from 'element-plus/es/components/select/index.mjs'
export { ElSwitch } from 'element-plus/es/components/switch/index.mjs'
// table 子路径同时导出 ElTable / ElTableColumn
export { ElTable, ElTableColumn } from 'element-plus/es/components/table/index.mjs'
// tabs 子路径同时导出 ElTabs / ElTabPane
export { ElTabPane, ElTabs } from 'element-plus/es/components/tabs/index.mjs'
export { ElTag } from 'element-plus/es/components/tag/index.mjs'
// timeline 子路径同时导出 ElTimeline / ElTimelineItem
export { ElTimeline, ElTimelineItem } from 'element-plus/es/components/timeline/index.mjs'
export type { TreeInstance } from 'element-plus/es/components/tree/index.mjs'
export { ElTree } from 'element-plus/es/components/tree/index.mjs'
export { ElUpload } from 'element-plus/es/components/upload/index.mjs'

const ElementPlus = {
  install(app: App, options?: Record<string, unknown>) {
    app.use(_ElLoading)
    if (options) {
      _provideGlobalConfig(options, app, true)
    }
  },
}

export default ElementPlus

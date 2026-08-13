/**
 * # airpower ElementPlus 默认导入树摇修复插件
 *
 * ## 背景
 * `@airpower/web/dist/main.js` 顶部存在：
 *   `import ElementPlus, { ElLink, ElButton, ... } from "element-plus"`
 *
 * 其中 `ElementPlus` **默认导入**会触发 element-plus `es/index.mjs` 的默认导出
 * `defaults_default = makeInstaller([...component_default, ...plugin_default])`，
 * 该导出聚合了全部 80 个组件模块（`component.mjs` 顶层 import 了所有组件）。
 *
 * 又因 `@airpower/web` 的 `package.json` 中 `sideEffects` 为 `undefined`（非 `false`），
 * Rollup 无法对该默认导入做树摇，导致全量 element-plus 进入 bundle
 * （vendor-element chunk 约 2.0MB / gzip 423KB）。
 *
 * airpower 内部 `ElementPlus` 仅用于 `DialogUtil.build()` 中的：
 *   `app.use(ElementPlus, { locale: WebConfig.elementPlusLocale })`
 * 即为「弹窗独立 app 实例」注册全局组件 / 指令 / locale。
 *
 * ## 方案
 * 项目已通过 `unplugin-vue-components + ElementPlusResolver` 为所有 .vue
 * （含弹窗组件）的 `<el-xxx>` 标签注入了「具名 import + 局部注册」，
 * 弹窗内的 `<el-` 标签不再依赖全局注册。
 *
 * 因此弹窗 app 实例实际只需：
 *   1. `v-loading` 指令（airpower 7 处 `resolveDirective("loading")`）
 *   2. locale（`provideGlobalConfig`，由原 app.use(ElementPlus, {locale}) 注入）
 *
 * 本插件在构建期将 airpower 的默认导入替换为一个最小 shim：
 *   - 从 element-plus 具名导入 `ElLoading`（树摇友好，不触发 index.mjs 默认导出链）
 *   - 构造 `{ install(app, options) { app.use(ElLoading); provideGlobalConfig(options, app, true) } }`
 *
 * 由此切断 `index.mjs → defaults.mjs → component.mjs（全量 80 组件）` 的导入链，
 * 使 element-plus 仅打包「实际被具名 import 的组件」。
 *
 * 改动仅作用于 airpower dist 文本的 transform，不修改 node_modules 源文件，
 * airpower 版本升级后只要该导入语句结构未变即继续生效；若变更则触发降级正则兜底。
 */
import type { Plugin } from 'vite'

/**
 * 注入的最小 ElementPlus shim。
 *
 * 仅做弹窗 app 实例必需的两件事：
 *  1. 注册 v-loading 指令（app.use(ElLoading) → ElLoading.install 内部 app.directive('loading', vLoading)）
 *  2. 通过 provideGlobalConfig 注入 locale（与原 app.use(ElementPlus, {locale}) 等价）
 *
 * 不注册任何全局组件——项目 .vue 的 <el-xxx> 已由
 * unplugin-vue-components 局部注册；airpower 自身组件用的 El 组件
 * 已通过具名 import 在模块内局部可用。
 *
 * 注意：原 airpower 默认导入行同时具名导入了 25 个组件（ElButton/ElMessage 等），
 * airpower 自身代码依赖这些具名绑定。本 shim 保留这些具名导入（见下方 SHIM_IMPORTS），
 * 仅去掉默认导入名 `ElementPlus`，并补充 ElLoading 具名导入 + shim 定义。
 *
 * 组件 import 统一从 `element-plus` 聚合入口（见
 * `src/types/element-plus-aggregate.ts` + `vite.config.mts` alias）解析，
 * 以绕开 element-plus `es/index.mjs` 的顶层副作用，恢复树摇。
 */
// 原 airpower 默认导入行的具名导入部分（精确，保持 airpower 自身依赖）
const NAMED_IMPORTS_FROM_AIRPOWER
  = 'ElLink, ElButton, ElMessage, ElMessageBox, ElIcon, ElEmpty, ElFormItem, ElSubMenu, ElMenuItem, ElMenu, ElImage, ElUpload, ElPagination, ElPopover, ElRadioGroup, ElRadioButton, ElInput, ElProgress, ElTabPane, ElTabs, ElCheckTag, ElSelect, ElOption, ElTable, ElTableColumn, ElTree'

// 替换后的整段代码：具名导入（含原 25 个 + 新增 ElLoading）+ provideGlobalConfig + ElementPlus shim 定义
// `element-plus` 经 vite alias 重定向到聚合模块，`provideGlobalConfig` 直接走子路径
const SHIM_REPLACEMENT = `
import { ${NAMED_IMPORTS_FROM_AIRPOWER}, ElLoading } from "element-plus";
import { provideGlobalConfig } from "element-plus/es/components/config-provider/src/hooks/use-global-config.mjs";
const ElementPlus = {
  install(app, options) {
    app.use(ElLoading);
    if (options) {
      provideGlobalConfig(options, app, true);
    }
  }
};`

// 精确匹配 airpower dist/main.js 顶部的默认导入语句
const AIRPOWER_DEFAULT_IMPORT_RE
  = /import\s+ElementPlus\s*,\s*\{[^}]*\}\s*from\s*["']element-plus["'];?/

export function airpowerElementPlusShimPlugin(): Plugin {
  return {
    name: 'airpower-element-plus-shim',
    enforce: 'pre',
    transform(code, id) {
      // 仅处理 airpower 预构建产物主入口（兼容 windows 反斜杠路径）
      const normalizedId = id.replace(/\\/g, '/')
      if (!normalizedId.includes('@airpower/web/dist/main.js')) {
        return null
      }
      if (!AIRPOWER_DEFAULT_IMPORT_RE.test(code)) {
        return null
      }

      const replaced = code.replace(
        AIRPOWER_DEFAULT_IMPORT_RE,
        SHIM_REPLACEMENT.trim(),
      )

      if (replaced === code) {
        this.warn(
          'airpower-element-plus-shim: 未匹配到 ElementPlus 默认导入语句，跳过（airpower 版本可能已变更，请检查）',
        )
        return null
      }

      return { code: replaced, map: null }
    },
  }
}

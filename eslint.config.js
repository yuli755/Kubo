/**
 * ESLint 扁平配置（@antfu/eslint-config v6）
 *
 * ## 两个必须知道的坑
 * 1. **v6 的默认导出返回 Promise**：不能写 `export default [...config({...})]`
 *    ——同步展开 Promise 会报 "config is not a function or its return value is not iterable"，
 *    而这条错误在 CI 里表现为"lint 好像跑了其实没跑"（本项目此前就是这样）。
 *    正确姿势是把额外规则对象作为**后续参数**传给 antfu()。
 * 2. **不要拿 lint 去扫文档/配置**：docs 里的伪代码、yml 片段是给人看的，
 *    不是可解析的 JS/YAML。用 ignores 明确排除，而不是把规则调松。
 */
import antfu from '@antfu/eslint-config'

export default antfu({
  stylistic: {
    indent: 2,
    semi: false,
  },
  // 尊重 .gitignore（dist / node_modules / 生成物），不再依赖手写忽略
  gitignore: true,
  ignores: [
    // 文档与设计稿里的代码片段是示意性的，不能按 JS/TS 语法判定
    '**/*.md',
    '**/*.sql',
    'docs/**',
    'SPMS-Server/**',
    // 部署与 CI 配置交给各自的校验器
    '**/*.yml',
    '**/*.yaml',
    '**/*.json',
    'infra/**',
  ],
  vue: {
    overrides: {
      'vue/block-order': ['error', {
        order: ['script', 'template', 'style'],
      }],
      'vue/max-attributes-per-line': ['error', {
        singleline: 1, // 单行最多 1 个属性
        multiline: 1,
      }],
      // airpower ATable 的 #customRow="{ data }" 插槽变量名固定，
      // 与列表视图 useXxxList() 返回的 data 同名属既定约定，允许遮蔽
      'vue/no-template-shadow': 'off',
    },
  },
},

/**
 * 数据层单入口约定（决策 D5）：视图层禁止直接依赖 @/model 下的 Service。
 *
 * F3 已把 133 处视图直连全部迁到 src/hook 下的 use* 系列 hook（走 @/api typed client），
 * maskContract 门禁里「视图直连总数基线 0」也在守着。这里把规则从 warn 升为 error 锁死：
 * 任何视图回头直接 import `@/model/**Service` 都会让 lint:ci 直接失败。
 *
 * 坑：pattern 必须写 `@/model/**\/*Service`。写成 `@/model/**Service` 时，minimatch 的
 * globstar `**` 因后面直接跟非 `/` 字符而退化为单段匹配，匹配不到 `@/model/asset/material/MaterialService`
 * 这类多段路径 —— 规则会变成从不触发的死规则（本项目此前正是这样，warn 阶段一条都没报过）。
 */
{
  files: ['src/view/**/*.{ts,vue}'],
  rules: {
    'no-restricted-imports': [
      'error',
      {
        patterns: [
          {
            group: ['@/model/**/*Service'],
            message: '视图层请改用 src/hook 下的 use* 系列 hook（内部统一走 @/api typed client），不要直接 import @/model 的 Service。',
          },
        ],
      },
    ],
  },
})

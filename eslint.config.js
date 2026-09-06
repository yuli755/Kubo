import config from '@antfu/eslint-config'

export default [
  ...config({
    stylistic: {
      indent: 2,
      semi: false,
    },
    gitignore: false,
    ignores: ['dist', '.idea', '.vscode'],
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
    typescript: {
      overrides: {
        'unicorn/error-message': 'off',
      },
    },
  }),

  /**
   * 数据层单入口约定（决策 D5）：视图层禁止直接依赖 @/model 下的 Service。
   *
   * 现状：133 个视图仍直接 import `@/model/**Service`，而 `src/api` 的 typed client 只有 2 处在用，
   * 属于「双轨数据层」—— 缓存失效与请求治理各跑一半，行为不可预测。
   * 这里先把约定写成 lint 规则（warn，避免一次性打断 CI）；F2 迁完视图后改成 error，
   * 之后 lint:ci 就会守住不回退。
   */
  {
    files: ['src/view/**/*.{ts,vue}'],
    rules: {
      'no-restricted-imports': [
        'warn',
        {
          patterns: [
            {
              group: ['@/model/**Service'],
              message: '视图层请改用 src/hook 下的 use* 系列 hook（内部统一走 @/api typed client），不要直接 import @/model 的 Service。',
            },
          ],
        },
      ],
    },
  },
]

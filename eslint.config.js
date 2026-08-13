import config from '@antfu/eslint-config'

export default config({
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
})

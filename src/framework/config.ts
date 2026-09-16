/**
 * # 框架层·全局配置
 *
 * 对应原 `WebConfig`：应用名、上传地址、静态资源前缀、Element Plus 语言包等。
 * 由 `src/config/airpower-bootstrap.ts`（后续改名 `framework-bootstrap.ts`）在业务页入口处填充。
 */

import type { Language } from 'element-plus/es/locale'

export class WebConfig {
  /** 应用标识 */
  static appKey = 'kubo'

  /** 产品名 */
  static product = 'Kubo'

  /** 文件上传地址 */
  static uploadUrl = '/api/file/upload'

  /** 静态资源前缀 */
  static staticUrl = '/static/'

  /** Element Plus 语言包（组件内部透传，避免各处重复 import） */
  static elementPlusLocale: Language | undefined = undefined

  /** 单行文本最大长度 */
  static maxTextLength = 128

  /** 多行文本最大长度 */
  static maxTextAreaLength = 2048
}

/**
 * # 框架层·全局配置
 *
 * 迁移期这里是 airpower `WebConfig` 的替身（appKey / product / 上传地址 / 静态前缀 /
 * EP 语言包…都要和 airpower 对齐，谁先初始化谁后初始化还得排顺序）。
 * airpower 拔除后只留下**真正有读者**的两项：
 *
 * - `uploadUrl`：`src/framework/upload.ts` 拼上传地址用；
 * - `staticUrl`：`src/framework/feedback.ts` 把后端返回的相对路径拼成可访问 URL 用。
 *
 * 删掉的字段（appKey / product / elementPlusLocale / maxTextLength / maxTextAreaLength）
 * 都是 airpower 契约：语言包改由 `main.ts` 用 `provideGlobalConfig` 直接交给 Element Plus，
 * 不再经框架层中转（中转的结果是"设了没人读"，见下方注释）。
 */
export class WebConfig {
  /** 文件上传地址（`/api` 前缀由 axios 实例统一带上，见 upload.ts） */
  static uploadUrl = '/api/file/upload'

  /** 静态资源前缀 */
  static staticUrl = '/static/'
}

/**
 * # 框架层统一出口
 *
 * 业务代码只从这里取能力（`import { ATable, Field, FeedbackUtil } from '@/framework'`），
 * 与原来的 `@airpower/web` 一一对应 —— 换底座时业务文件只需改 import 来源。
 *
 * 目录职责：
 * - `metadata`  装饰器与元数据（@Model/@Field/@Table/@Form/@Search/@Type）
 * - `entity`    实体基类与序列化（RootEntity/RootModel/Transformer）
 * - `http`      服务基类（AbstractService/AbstractCurdService）与链式请求
 * - `query`     分页/排序/查询模型（与后端契约一致）
 * - `feedback`  吐司/确认框/命令式弹窗/剪贴板
 * - `export`    异步导出任务（建任务 → 轮询 → 下载地址）
 * - `validate`  表单校验
 * - `datetime`  时间工具
 * - `enums`     枚举字典/颜色/脱敏
 * - `format`    金额/数量格式化
 * - `cell`      单元格文本/颜色/对齐（表格与详情页共用一套渲染规则）
 */

export * from './cell'
export * from './config'
export * from './datetime'
export * from './entity'
export * from './enums'
export * from './export'
export * from './feedback'
export * from './format'
export * from './http'
export * from './metadata'
export * from './query'
export * from './types'
export * from './validate'

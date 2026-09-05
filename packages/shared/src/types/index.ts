/**
 * 共享类型定义
 *
 * 已迁移：
 * - api.ts：通用 API 类型（BaseEntity / PageRequest / PageResponse / ApiResponse 等）
 * - mask.ts：列级脱敏策略类型（MaskPolicy / MaskType）
 *
 * 未来迁移目标：
 * - 通用实体基类类型
 */

export * from './api'
export * from './mask'
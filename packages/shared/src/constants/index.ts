/**
 * 通用常量定义
 * 由 @kubo/shared 承载，origin 为 src/config/Constant.ts
 */

// 布尔值字符串表示（后端接口约定 "1" / "0"）
export const BOOLEAN_YES = '1'
export const BOOLEAN_NO = '0'

// 兼容类形式的常量访问（origin: src/config/Constant.ts 的静态成员）
export const Constant = {
  BOOLEAN_YES,
  BOOLEAN_NO,
} as const
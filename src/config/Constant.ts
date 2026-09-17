/**
 * # 通用常量
 *
 * 历史上这些常量被搬到 workspace 包 `@kubo/shared`，这里只留一条转发 shim；
 * 2026-09-17 删除该包（唯一消费者就是本应用）后，常量回归本文件，
 * 调用方的 `@/config/Constant` 路径不变。
 */

/** 布尔值字符串表示（后端接口约定 "1" / "0"） */
export const BOOLEAN_YES = '1'
export const BOOLEAN_NO = '0'

/** 兼容类形式的常量访问（origin: 早期 `Constant.BOOLEAN_YES` 用法） */
export const Constant = {
  BOOLEAN_YES,
  BOOLEAN_NO,
} as const

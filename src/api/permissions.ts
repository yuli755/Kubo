/**
 * @airpower/web 权限注解 OpenAPI 扩展支持
 *
 * 此文件展示如何将 @airpower/web 的权限注解转换为 OpenAPI 扩展元数据
 * 前端可通过 x-visibility、x-mask 等扩展自动实现行级/列级权限控制
 */

import type { OpenAPIV3 } from 'openapi-types'

/**
 * 扩展字段定义
 */
export interface AirpowerPermissionExtensions {
  /** 行级权限：可见角色列表 */
  'x-visibility'?: string[]
  /** 行级权限：拥有角色列表 */
  'x-ownership'?: string[]
  /** 列级权限：脱敏策略 */
  'x-mask'?: 'FULL' | 'PARTIAL' | 'HASH' | 'REDACT' | 'LAST4' | 'FIRST2'
  /** 列级权限：允许查看的角色 */
  'x-mask-roles'?: string[]
  /** 字段是否为敏感字段 */
  'x-sensitive'?: boolean
  /** 字段业务分类 */
  'x-category'?: 'PII' | 'FINANCIAL' | 'MEDICAL' | 'CREDENTIAL' | 'INTERNAL'
}

/**
 * 扩展 OpenAPI Schema
 */
export interface ExtendedSchema extends OpenAPIV3.SchemaObject {
  'x-visibility'?: string[]
  'x-ownership'?: string[]
  'x-mask'?: 'FULL' | 'PARTIAL' | 'HASH' | 'REDACT' | 'LAST4' | 'FIRST2'
  'x-mask-roles'?: string[]
  'x-sensitive'?: boolean
  'x-category'?: 'PII' | 'FINANCIAL' | 'MEDICAL' | 'CREDENTIAL' | 'INTERNAL'
}

/**
 * 权限注解到 OpenAPI 扩展的映射
 *
 * @airpower/web 注解 -> OpenAPI 扩展
 *
 * @Table(mask = true)          -> x-mask: "PARTIAL"
 * @Table(visibility = {...})   -> x-visibility: [...]
 * @Field(sensitive = true)     -> x-sensitive: true
 * @Form(mask = true)           -> x-mask: "PARTIAL"
 *
 * 自定义注解示例:
 * @SensitiveField(mask = MaskStrategy.PARTIAL, roles = {"FINANCE", "ADMIN"})
 * @RowLevelPermission(roles = {"WAREHOUSE_MGR", "ADMIN"})
 */

// 示例：生成的 OpenAPI JSON 片段
const exampleOpenApiSpec = {
  openapi: '3.1.0',
  info: {
    title: 'Kubo API',
    version: '1.0.0',
  },
  components: {
    schemas: {
      InventoryEntity: {
        type: 'object',
        properties: {
          id: {
            type: 'integer',
            format: 'int64',
          },
          name: {
            type: 'string',
            description: '物料名称',
          },
          code: {
            type: 'string',
            description: '物料编码',
          },
          costPrice: {
            'type': 'number',
            'format': 'double',
            'description': '成本价',
            'x-mask': 'PARTIAL',
            'x-mask-roles': ['FINANCE', 'ADMIN'],
            'x-sensitive': true,
            'x-category': 'FINANCIAL',
          },
          salePrice: {
            'type': 'number',
            'format': 'double',
            'description': '销售价',
            'x-mask': 'PARTIAL',
            'x-mask-roles': ['SALES_MGR', 'ADMIN'],
            'x-sensitive': true,
            'x-category': 'FINANCIAL',
          },
          supplierPhone: {
            'type': 'string',
            'description': '供应商电话',
            'x-mask': 'PARTIAL',
            'x-mask-roles': ['PURCHASE_MGR', 'ADMIN'],
            'x-sensitive': true,
            'x-category': 'PII',
          },
          warehouseId: {
            'type': 'integer',
            'format': 'int64',
            'description': '仓库ID',
            'x-visibility': ['WAREHOUSE_MGR', 'ADMIN'],
            'x-ownership': ['WAREHOUSE_MGR'],
          },
        },
      },
    },
  },
}

export { exampleOpenApiSpec }

// 脱敏策略枚举
export enum MaskStrategy {
  /** 完全隐藏: */
  REDACT = 'REDACT',
  /** 部分显示: 138****1234 */
  PARTIAL = 'PARTIAL',
  /** 哈希值: sha256(...) */
  HASH = 'HASH',
  /** 仅显示最后4位: ***1234 */
  LAST4 = 'LAST4',
  /** 仅显示前2位: 13 */
  FIRST2 = 'FIRST2',
  /** 完全显示 (白名单角色) */
  NONE = 'NONE',
}

// 前端脱敏管道类型
export type MaskPipe = (value: unknown, strategy: MaskStrategy, roles?: string[]) => unknown

// 脱敏处理示例
export const maskPipes: Record<MaskStrategy, MaskPipe> = {
  [MaskStrategy.REDACT]: () => '****',
  [MaskStrategy.PARTIAL]: (value: unknown) => {
    const str = String(value)
    if (str.length <= 4)
      return '****'
    return `${str.slice(0, 2)}****${str.slice(-2)}`
  },
  [MaskStrategy.HASH]: (value: unknown) => {
    // 实际项目中使用 crypto.subtle.digest
    return `hash:${btoa(String(value)).slice(0, 8)}...`
  },
  [MaskStrategy.REDACT]: () => '****',
  [MaskStrategy.LAST4]: (value: unknown) => {
    const str = String(value)
    return `***${str.slice(-4)}`
  },
  [MaskStrategy.FIRST2]: (value: unknown) => {
    const str = String(value)
    return str.slice(0, 2) + '*'.repeat(Math.max(0, str.length - 2))
  },
  [MaskStrategy.NONE]: (value: unknown) => value,
}

// 权限检查工具
export function hasPermission(userRoles: string[], requiredRoles: string[]): boolean {
  if (!requiredRoles || requiredRoles.length === 0)
    return true
  return requiredRoles.some(role => userRoles.includes(role))
}

// 脱敏值处理
export function applyMask(
  value: unknown,
  strategy: MaskStrategy,
  userRoles: string[],
  allowedRoles: string[] = [],
): unknown {
  // 有权限查看原值
  if (hasPermission(userRoles, allowedRoles)) {
    return value
  }
  // 无权限，应用脱敏
  return maskPipes[strategy]?.(value, strategy, allowedRoles) ?? '****'
}

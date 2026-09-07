/**
 * API 类型定义
 * 由 @kubo/shared 承载的纯类型模块，origin 为 src/api/types.ts
 */

// 基础类型
export interface BaseEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean
}

// 分页请求
export interface PageRequest {
  page: number
  pageSize: number
  sort?: string[]
  filter?: Record<string, unknown>
}

// 分页响应
export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

// 权限实体
export interface PermissionEntity extends BaseEntity {
  identity: string
  isSystem: boolean
  type: number
  name: string
  parentId: number
  children?: PermissionEntity[]
}

// 用户实体
export interface UserEntity extends BaseEntity {
  username: string
  nickname: string
  email: string
  phone: string
  avatar: string
  status: number
  lastLoginTime: string
  lastLoginIp: string
  roles: RoleEntity[]
}

// 角色实体
export interface RoleEntity extends BaseEntity {
  name: string
  code: string
  description: string
  permissions: PermissionEntity[]
}

// 部门实体
export interface DepartmentEntity extends BaseEntity {
  name: string
  code: string
  parentId: number
  sort: number
  leader: string
  phone: string
  email: string
  children?: DepartmentEntity[]
}

// 字典实体
export interface DictionaryEntity extends BaseEntity {
  name: string
  code: string
  parentId: number
  sort: number
  value: string
  label: string
  type: string
  children?: DictionaryEntity[]
}

// 登录请求
export interface LoginRequest {
  username: string
  password: string
  rememberMe: boolean
  captchaId?: string
  captchaCode?: string
}

// 登录响应
export interface LoginResponse {
  token: string
  refreshToken: string
  expiresIn: number
  tokenType: string
  user: UserEntity
}

// 当前用户可切换租户
export interface TenantOption {
  id: number
  code: string
  name: string
  isDefault: boolean
}

// 刷新 Token 请求
export interface RefreshTokenRequest {
  refreshToken: string
}

// 刷新 Token 响应
export interface RefreshTokenResponse {
  token: string
  refreshToken: string
  expiresIn: number
  tokenType: string
}

// 修改密码请求
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}

// 文件上传响应
export interface FileUploadResponse {
  id: number
  name: string
  url: string
  size: number
  type: string
}

// 导出配置
export interface ExportConfig {
  fields: string[]
  filename: string
  sheetName: string
}

// 通用查询参数
export interface QueryParams {
  page?: number
  pageSize?: number
  sort?: string
  order?: 'asc' | 'desc'
  keyword?: string
  startTime?: string
  endTime?: string
  [key: string]: unknown
}

// API 响应包装
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

// 空响应
export interface VoidResponse {
  code: number
  message: string
  timestamp: string
}

/**
 * # Personnel 模块 API 类型定义
 */

// ──────────────────────────────────────────
// 部门 (Department)
// ──────────────────────────────────────────

export interface DepartmentEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean
  isDisabled: boolean

  name: string
  code: string
  orderNo: number
  parentId: number
  children?: DepartmentEntity[]
  parent?: DepartmentEntity
}

export interface DepartmentQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string }
}

// ──────────────────────────────────────────
// 角色 (Role)
// ──────────────────────────────────────────

export interface RoleEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean
  isDisabled: boolean

  name: string
  code: string
  menuList?: { id: number }[]
  permissionList?: { id: number }[]
}

export interface RoleQuery {
  page?: number
  pageSize?: number
  filter?: { name?: string, code?: string }
}

// ──────────────────────────────────────────
// 用户 (User)
// ──────────────────────────────────────────

export interface UserEntity {
  id: number
  createTime: string
  updateTime: string
  isPublished: boolean
  isDeleted: boolean
  isDisabled: boolean

  nickname: string
  phone: string
  email: string
  realName: string
  idCard: string
  avatar: string
  gender: number
  bio: string
  departmentId?: number
  roleList?: { id: number, name: string, code: string }[]
  departmentList?: { id: number, name: string, code: string }[]
}

export interface UserQuery {
  page?: number
  pageSize?: number
  filter?: { nickname?: string, phone?: string, email?: string }
}
/**
 * # Demo 身份与租户（仅演示模式）
 *
 * 形状对齐真实接口：
 * - `user/getMyInfo` → `UserEntity`
 * - `user/getMyTenantList` → `TenantOption[]`（见 `@kubo/shared`）
 *
 * 用途：跳过登录后，控制台初始化（`Console.vue`）仍能拿到「用户 + 租户 + 权限 + 菜单」，
 * 从而把管理端完整渲染出来。
 */

import type { TenantOption } from '@kubo/shared'

/** 当前登录用户（全权限管理员） */
export function demoUserInfo(): Record<string, unknown> {
  return {
    id: 1,
    code: 'U0001',
    nickname: '演示管理员',
    username: 'demo',
    realName: '演示管理员',
    // 留空可让用户卡片显示「去实名 / 绑定邮箱」等完整交互态；这里填上以展示脱敏后的显示效果
    idCard: '310101199001011234',
    email: 'demo@kubo.example.com',
    phone: '13800138000',
    avatar: '',
    gender: 1,
    bio: '演示模式账号：拥有全部权限，用于界面评审',
    isSuperAdmin: true,
    tenantId: 1,
    departmentId: 1,
    departmentList: [
      { id: 1, name: '生产制造部' },
      { id: 3, name: '质量管理部' },
    ],
    roleList: [
      { id: 1, name: '系统管理员' },
      { id: 2, name: '生产计划员' },
    ],
  }
}

/** 可切换租户列表（演示两个租户 + 默认标记） */
export function demoTenants(): TenantOption[] {
  return [
    { id: 1, name: '库铂智造（苏州）有限公司', code: 'KUBO-SZ', isDefault: true },
    { id: 2, name: '库铂智造（东莞）分公司', code: 'KUBO-DG', isDefault: false },
  ]
}

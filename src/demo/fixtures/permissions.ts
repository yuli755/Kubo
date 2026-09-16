/**
 * # Demo 权限集合（仅演示模式）
 *
 * 直接从 `@/config/permissions` 的常量树**递归收集**，不手抄权限码 ——
 * 新增业务权限码时演示模式自动跟上，不存在「演示和真实权限码不一致」的漂移。
 *
 * 演示身份是「全权限管理员」：所有按钮（新增/修改/审核/导出…）都会渲染出来，
 * 便于一次性评审全部界面的视觉与交互。
 */

import { Permissions } from '@/config/permissions'

function collect(value: unknown, bucket: string[]): void {
  if (typeof value === 'string') {
    if (value) {
      bucket.push(value)
    }
    return
  }
  if (value && typeof value === 'object') {
    for (const nested of Object.values(value)) {
      collect(nested, bucket)
    }
  }
}

let cache: string[] | null = null

/** 全部权限码（去重、稳定排序） */
export function demoPermissions(): string[] {
  if (!cache) {
    const bucket: string[] = []
    collect(Permissions, bucket)
    // 后端把「菜单可读」也算作权限位；补几条通用的，避免个别页面因缺 getPage 而不渲染
    for (const code of ['Menu_getMyMenuList', 'User_getMyInfo', 'User_getMyPermissionList']) {
      if (!bucket.includes(code)) {
        bucket.push(code)
      }
    }
    cache = Array.from(new Set(bucket)).sort()
  }
  return cache
}

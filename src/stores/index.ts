/**
 * # Pinia 状态管理统一导出
 *
 * @module stores
 */

export { useAppStore } from './app'
export { usePermissionStore } from './permission'
export { createPersistPlugin } from './persist'
export type { PersistOptions } from './persist'
export { useTenantStore } from './tenant'
export { useUserStore } from './user'

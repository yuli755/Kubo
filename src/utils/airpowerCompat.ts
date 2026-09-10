/**
 * # airpower 兼容层（「去 airpower 化入口」配套）
 *
 * 首屏（main.ts + 登录页）不再静态引入 `@airpower/web` 单体，以免把整个 airpower
 * 框架（107 KB）及其依赖的 26 个 element-plus 组件（168 KB）拖进首屏闭包。
 *
 * 但业务页（懒加载）里的 airpower 组件仍依赖几个「与 airpower 共享」的 localStorage
 * 键值（如 AImage/AUpload 读取 `Authorization` 拼 URL、权限缓存）。为保证 key 一致、
 * 业务页 airpower 组件能读到首屏写入的数据，这里以**相同 key** 复刻 airpower 的存取语义：
 *
 *   - token    → key `Authorization`（airpower `HttpHeader.AUTHORIZATION`）
 *   - 权限列表 → key `permissions`（airpower `WebConfig.permissionCacheKey`）
 *
 * 仅做 localStorage 读写，不引入 airpower 依赖。
 */

const AUTHORIZATION_KEY = 'Authorization'
const PERMISSION_KEY = 'permissions'

/** token 存取（与 airpower WebConfig 的 accessToken 同 key） */
export const airpowerToken = {
  get: () => localStorage.getItem(AUTHORIZATION_KEY) || '',
  save: (token: string) => localStorage.setItem(AUTHORIZATION_KEY, token),
  remove: () => localStorage.removeItem(AUTHORIZATION_KEY),
}

/** 权限列表存取（与 airpower PermissionUtil 的缓存同 key + 同步其内存态） */
export const airpowerPermission = {
  saveList: (list: string[]) => {
    localStorage.setItem(PERMISSION_KEY, JSON.stringify(list))
    // airpower 的 PermissionUtil.has() 读的是「内存静态 permissionList」，仅写 localStorage
    // 不足以让业务页 ATable/AButton 的自动权限门禁生效（WebConfig.disablePermission=false 时
    // 会按 PermissionUtil.has() 隐藏增删改按钮）。此处懒加载同步其内存态：
    // saveList 只在 Console.vue 权限加载时被调用，彼时业务页已静态 import airpower，
    // 动态 import 命中同一模块实例，不会把 airpower 拖回首屏。
    void import('@airpower/web')
      .then(({ PermissionUtil }) => PermissionUtil.saveList(list))
      .catch(() => {
        // airpower 未加载：仅写 localStorage（理论上不触发——saveList 仅随业务页调用）
      })
  },
}

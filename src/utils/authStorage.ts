/**
 * # 登录态本地存储（token / 权限缓存）
 *
 * 这两个 key 以前叫「airpower 兼容层」：首屏（main.ts + 登录页）不引入 airpower，
 * 业务页里的 `AImage`/`AUpload`/`PermissionUtil` 却要读同一份 localStorage，于是用相同 key
 * 复刻它的存取语义。**airpower 已拔除**，与框架共享 key 的理由不存在了，
 * 本模块降级为一层薄的 localStorage 读写 —— 只负责「key 定在哪、JSON 怎么进出」。
 *
 * ## 为什么 key 仍叫 `Authorization` / `permissions`
 * 它们与后端无关（后端读的是请求头），纯粹是浏览器里的存放位置。改名会让所有在线用户
 * 刷新后被静默登出一次（键不见了 = 没有 token），收益只是好看一点，不值得。
 * 新代码不要依赖这两个字面量，用本模块导出的对象。
 */

/** 令牌键（历史键名，见文件头说明） */
const AUTHORIZATION_KEY = 'Authorization'

/** 权限缓存键（历史键名，见文件头说明） */
const PERMISSION_KEY = 'permissions'

/** token 存取 */
export const authToken = {
  get: () => localStorage.getItem(AUTHORIZATION_KEY) || '',
  save: (token: string) => localStorage.setItem(AUTHORIZATION_KEY, token),
  remove: () => localStorage.removeItem(AUTHORIZATION_KEY),
}

/** 权限列表缓存存取（页面刷新后守卫能在首个请求返回前先按缓存判断） */
export const permissionCache = {
  get: (): string[] => {
    try {
      const raw = localStorage.getItem(PERMISSION_KEY)
      return raw ? (JSON.parse(raw) as string[]) : []
    }
    catch {
      return []
    }
  },
  saveList: (list: string[]) => localStorage.setItem(PERMISSION_KEY, JSON.stringify(list)),
}

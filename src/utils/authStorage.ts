/**
 * # 登录态本地存储（token）
 *
 * 这个 key 以前叫「airpower 兼容层」：首屏（main.ts + 登录页）不引入 airpower，
 * 业务页却要读同一份 localStorage，于是用相同 key 复刻它的存取语义。**airpower 已拔除**，
 * 与框架共享 key 的理由不存在了，本模块降级为一层薄的 localStorage 读写 ——
 * 只负责「key 定在哪」。
 *
 * ## 为什么 key 仍叫 `Authorization`
 * 它与后端无关（后端读的是请求头），纯粹是浏览器里的存放位置。改名会让所有在线用户
 * 刷新后被静默登出一次（键不见了 = 没有 token），收益只是好看一点，不值得。
 * 新代码不要依赖这个字面量，用本模块导出的对象。
 *
 * ## 历史上这里还有过 permissionCache（权限列表镜像）
 * 第 35 轮审计证明它是**只写不读的死镜像**：真正的权限读者是 permission store
 * （sessionStorage `kubo:permission`，路由守卫与 v-permission 都从它取），
 * `permissions` 这个 localStorage 键没有任何生产代码读取，注释里「守卫先按缓存判断」
 * 的说法不成立。写它的代价是：整份权限码清单在登出后仍留在 localStorage 里
 * （同源任何脚本/扩展可读），换来的收益为零 —— 已删除。
/**
 * 存量为零的成本：老浏览器里残留的旧键由 permission store 初始化时顺手清掉。
 */

import { safeGetItem, safeRemoveItem, safeSetItem } from './storage'

/** 令牌键（历史键名，见文件头说明） */
const AUTHORIZATION_KEY = 'Authorization'

/** token 存取（隐私模式/配额下静默降级：登录态当场仍有效，只是不跨刷新） */
export const authToken = {
  get: () => safeGetItem(AUTHORIZATION_KEY) || '',
  save: (token: string) => safeSetItem(AUTHORIZATION_KEY, token),
  remove: () => safeRemoveItem(AUTHORIZATION_KEY),
}

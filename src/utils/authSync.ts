/**
 * # 跨标签页登录态同步（R-38）
 *
 * ## 没有这一层时的真实缺陷（真机复现 2026-10-01，报告 §14/R-38）
 * 登录态有**两处**存放：`Authorization`（localStorage，权威来源）和 `useUserStore().token`
 * （内存，请求拦截器真正读取的那一份）。标签页 A 点「退出登录」后：
 * A 调 `/user/logout`、摘掉 `Authorization`、`replace('/login')` —— 但 B 的内存 token
 * 还在，于是 B 继续带旧凭证发请求，而后端 `/user/logout` 只删服务端映射，
 * 自包含 JWT 在有效期内**照旧鉴权通过**：B 实测仍能拉到 20 行入库单、
 * 还能正常翻页/导航，只是再也回不到「未登录」口径。共享电脑上的「注销」形同虚设。
 * E-C1 的 `onRestore` 只在**重新加载**时兜底（丢弃 `kubo:user` 快照），
 * 对已经打开的标签页无效 —— 这正是本次补的缺口。
 *
 * ## 为什么用 `storage` 事件而不是 BroadcastChannel
 * 1. 触发点就在**唯一的权威来源**上：`authToken.remove()` 一执行，同源其它标签页
 *    立刻收到事件，无需第二个通道，也就不存在两个事实来源互相打脸；
 * 2. 零新增状态、零心跳、零"通道没建立成功"的降级分支；
 * 3. `BroadcastChannel` 需要发送方在所有登出路径上显式广播（漏一处就静默失效），
 *    而 `Authorization` 只有 `authToken.save/remove` 两个写入口，收口成本更低。
 *
 * ## 只处理「别的标签页登出了」这一件事
 * - 别的标签页**登录**（newValue 有值）：不抢占当前页 —— 用户可能正在 B 里填表单，
 *   替他把会话换掉是更糟的行为；他下次冷启动自然会拿到新 token。
 * - 本标签页自己登出：`storage` 事件不在本标签页触发，天然不会自打。
 */
import { useUserStore } from '@/stores/user'

/** `Authorization` 键名与 `authToken` 必须一致（见 `@/utils/authStorage`） */
const AUTHORIZATION_KEY = 'Authorization'

/** 跳转 query 的键值对：与 `signedOutReasonHint` 成对维护，不做对外契约（登录页只认文案映射） */
const SIGNED_OUT_QUERY_KEY = 'reason'
const SIGNED_OUT_QUERY_VALUE = 'signedOut'

/**
 * 登录页提示文案：只做「reason → 文案」的映射，纯函数便于单测。
 * 认不出的 reason 返回 `undefined`，让登录页保持安静，绝不编造原因。
 */
export function signedOutReasonHint(reason: unknown): string | undefined {
  return reason === SIGNED_OUT_QUERY_VALUE
    ? '当前账号已在其他标签页退出登录，请重新登录'
    : undefined
}

/** 当前是否停在登录页（含 `/login?redirect=`）——这里不需要再"跳转"，也不该打提示 */
function isOnLoginPage(): boolean {
  return window.location.pathname === '/login'
}

/**
 * 安装跨标签页登出同步。在 pinia 就绪之后、挂载之前调用（见 `src/main.ts`）。
 *
 * 测试可反复调用：每次都会挂一个新的监听，卸载交给 `window.removeEventListener`
 * （返回值即注销函数）。
 */
export function installAuthSync(): () => void {
  const onStorage = (event: StorageEvent): void => {
    // 只认 token 键被「清掉」这一种变化（登出）。其它键（权限缓存、主题…）与本机制无关。
    if (event.key !== AUTHORIZATION_KEY || event.newValue !== null) {
      return
    }
    const userStore = useUserStore()
    // 本标签页本来就没有会话：无事可做（也覆盖了「登录页上另一标签页登出」）
    if (!userStore.token) {
      return
    }
    // 只清本地：对端已经调过 /user/logout，这里再发一次等于用作废凭证打空炮
    userStore.clearLocalSession()
    if (isOnLoginPage()) {
      return
    }
    // 与 handleSessionExpired 同一收口姿势：replace 不留历史，整页重载把上一身份的
    // 组件态/事件监听一并抹掉；带上来源与 reason，重新登录后回原页、登录页能说明原因。
    const back = encodeURIComponent(`${window.location.pathname}${window.location.search}`)
    window.location.replace(`/login?${SIGNED_OUT_QUERY_KEY}=${SIGNED_OUT_QUERY_VALUE}&redirect=${back}`)
  }

  window.addEventListener('storage', onStorage)
  return () => window.removeEventListener('storage', onStorage)
}

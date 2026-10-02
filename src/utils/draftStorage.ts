/**
 * # 表单草稿的本地存放（第 35 轮审计）
 *
 * `KFormDialog` 把未提交的新建表单存进 localStorage，键按 service 名区分。
 * 键里**没有用户身份** —— 共享电脑 / 同标签页换账号时，上一个账号写的草稿
 * （可能含客户手机号、地址等）会被下一个账号的同号对话框恢复出来。
 * 修法不是给键加身份（登出时 JWT 往往已被清掉，无从取身份），而是
 * **登出/切身份时清空全部草稿**：草稿是便利性数据，丢得起；跨身份泄露丢不起。
 *
 * 键前缀集中在本模块，两处使用者（KFormDialog 写、user store 清）共用一份定义。
 */

/** 草稿键前缀（历史格式，改动会让所有人手里的草稿作废，不值得） */
export const DRAFT_KEY_PREFIX = 'kubo:draft:'

/** 某个 service 的草稿键 */
export function draftKeyOf(serviceName: string): string {
  return `${DRAFT_KEY_PREFIX}${serviceName}`
}

/** 清空全部草稿（登出 / 跨标签页登出同步时调用） */
export function clearAllDrafts(): void {
  try {
    const doomed: string[] = []
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key?.startsWith(DRAFT_KEY_PREFIX)) {
        doomed.push(key)
      }
    }
    for (const key of doomed) {
      localStorage.removeItem(key)
    }
  }
  catch {
    // 隐私模式等存储不可用场景：本来也没有草稿可清
  }
}

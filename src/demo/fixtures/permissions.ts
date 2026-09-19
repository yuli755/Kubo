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

/** 演示用的权限树节点（形状对齐 `PermissionEntity` + `getList` 的裸数组） */
export interface DemoPermissionNode {
  id: number
  name: string
  identity: string
  type: number
  isSystem: boolean
  parentId: number
  children: DemoPermissionNode[]
}

/**
 * 权限树（角色授权弹窗用）。
 *
 * 为什么不能靠 mock 引擎的通用树生成器：那个生成器按 `specs.ts` 的名池造行
 * （「用户新增 / 用户修改 / 入库单审核 / 库存导出」配 `P1/P2…` 编码），
 * 而授权弹窗是**真的要把 identity 提交给后端**的地方 —— 演示数据里的
 * identity 与真实权限码不一致，会让人误以为授权的是别的东西。
 *
 * 这里按真实权限码分组：根节点 = 实体名（`Role`、`Material`…），
 * 子节点 = 具体权限（`Role_authorizeMenu`…），与 `demoPermissions()` 同源，
 * 新增权限码时自动跟上。
 */
export function demoPermissionTree(): DemoPermissionNode[] {
  const groups = new Map<string, string[]>()
  for (const code of demoPermissions()) {
    const entity = code.split('_')[0] ?? code
    const bucket = groups.get(entity) ?? []
    bucket.push(code)
    groups.set(entity, bucket)
  }

  let nextId = 1
  const roots: DemoPermissionNode[] = []
  for (const [entity, codes] of groups) {
    const id = nextId++
    roots.push({
      id,
      name: entity,
      identity: `${entity}_*`,
      type: 0,
      isSystem: false,
      parentId: 0,
      children: codes.map((code) => {
        const childId = nextId++
        return {
          id: childId,
          name: code,
          identity: code,
          type: 0,
          isSystem: false,
          parentId: id,
          children: [],
        }
      }),
    })
  }
  return roots
}

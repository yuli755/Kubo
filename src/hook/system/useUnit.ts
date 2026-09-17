// 新底座服务（F2 试点页用）：同样是"视图只从 hook 取"的约定，避免视图层直接依赖 @/model
export { KUnitService } from '@/model/system/unit/KUnitService'
// 说明：这里曾经 re-export 旧 airpower 版 `UnitService`（供 `useTanStackTable` / `useEditor` /
// `ASelector` 使用）。批 2 删掉 `unit/component/{editor,selector}.vue` 后它已无消费者，
// 连同 `model/system/unit/UnitService.ts` 一起删除 —— 系统域的单位页面只走 `KUnitService`。

/**
 * # System 计量单位列表查询 Hook
 */

/**
 * # System 计量单位详情查询 Hook
 */

/**
 * # System 计量单位删除 Mutation Hook
 */

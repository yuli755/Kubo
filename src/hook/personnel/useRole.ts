// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 角色的 CRUD 走 airpower 的 Service 类（useTanStackTable / useEditor / useSelector 需要类实例），
// 因此由本 hook 模块统一转发 —— 视图从这里取，不再回头依赖 @/model。
export { RoleService } from '@/model/personnel/role/RoleService'

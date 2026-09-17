// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// `KUserService` 是批 3 的新底座服务（用户管理页 CRUD）；
// 认证类动作（login / logout / getMyInfo / updateMyPassword…）仍在旧 `UserService` 里，
// 调用方是 store 与 `component/user/*`（F5 统一迁），视图层不要直接用。
export { KUserService } from '@/model/personnel/user/KUserService'

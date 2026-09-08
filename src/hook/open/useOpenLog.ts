// F3 数据层收敛（D5）：应用日志视图走 useTanStackTable，CRUD 由 airpower 的 Service 类驱动，
// 由本 hook 模块统一转发 —— 视图从这里取，不再直接 import @/model 的 Service。
export { OpenLogService } from '@/model/open/log/OpenLogService'

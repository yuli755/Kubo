// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// 销售价的 CRUD 走 airpower 的 Service 类，由本 hook 模块统一转发。
export { SalePriceService } from '@/model/channel/salePrice/SalePriceService'

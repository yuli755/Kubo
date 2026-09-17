// F3 数据层收敛（D5）：视图层禁止直接 import `@/model/**Service`。
// F4 批 2：合同的列表/详情已切到新底座（`KContractService`：框架层基类 + 领域动作 enforce/stop），
// 仍保留对旧 `ContractService` 的转发 —— 合同编辑器还卡在框架层上传组件（批 2B），
// 它读 airpower 注册表、必须用旧服务。批 2B 完成后删掉这一行。
export { ContractService } from '@/model/asset/contract/ContractService'
export { KContractService } from '@/model/asset/contract/KContractService'

import { AbstractCurdService } from '@/framework'
import { ContractEntity } from './ContractEntity'

/**
 * # 合同服务（新底座）
 *
 * 与 `ContractService`（airpower 基类）同接口（`baseUrl = 'contract'`）。
 * 旧的那份给**还没迁移的合同编辑器**（卡在框架层上传组件，见 F4 文档批 2B）用；
 * 列表/详情已切到这份。批 2B 换完编辑器后删掉旧 Service 与实体上的 airpower 装饰器。
 *
 * 生效/终止这类业务动作不走这里：它们是**领域动作**而非 CRUD，
 * 走 `src/api/asset/` 的 typed client（`contractApi.enforce/stop`），由 hook 转发 ——
 * 与批 1 之后各域的统一做法一致（`src/hook/asset/useContract.ts`）。
 */
export class KContractService extends AbstractCurdService<ContractEntity> {
  entityClass = ContractEntity
  baseUrl = 'contract'

  /**
   * 合同生效（`POST /contract/enforce`）。
   *
   * 为什么放在服务上而不是页面里各写一次 `request.post`：
   * 生效/终止是**这个域的领域动作**，`AbstractCurdService` 不覆盖；放在服务里
   * 页面就只有一个数据入口（`useTable` 的 service），不必再引入第二套 typed client。
   * 判据（参与方 ≥ 2 方、附件是否齐、状态是否允许）**全在后端**，前端只做提示与确认。
   */
  async enforce(contract: ContractEntity): Promise<void> {
    await this.api('enforce').request({ id: contract.id })
  }

  /** 合同终止（`POST /contract/stop`） */
  async stop(contract: ContractEntity): Promise<void> {
    await this.api('stop').request({ id: contract.id })
  }
}

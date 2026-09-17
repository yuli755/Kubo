import type { BaseEntity } from './BaseEntity'
import { AbstractCurdService, FeedbackUtil } from '@/framework'

/**
 * # 抽象服务基类（新底座）
 *
 * 业务服务继承它即可获得全部 CRUD（`@/framework` 的 `AbstractCurdService`）。
 *
 * ## 历史上这里做了什么
 * 原实现继承 airpower 的 `AbstractCurdService`，并覆写 `api()` 给请求补
 * `X-Tenant-Id`（多租户账号不带租户头会回到默认租户）。换底座后**租户头已经统一在
 * `src/api/axios.ts` 的请求拦截器里注入**（`tenantAware !== false` 时自动带），
 * 所以这一层不再需要覆写 `api()` —— 留着会让"租户头从哪来"这件事有两个答案。
 *
 * `publish()` 保留：后端 `CurdController` 有 `publish` 端点，合同/物料等页面在用，
 * 且它要在成功后统一给一次提示（页面各写一遍必然漂移）。
 */
export abstract class AbstractBaseService<E extends BaseEntity> extends AbstractCurdService<E> {
  /**
   * ### 发布实体
   * @param entity 实体
   */
  async publish(entity: E): Promise<void> {
    await this.api('publish').request(entity)
    FeedbackUtil.toastSuccess('发布成功')
  }
}

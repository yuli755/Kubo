import type { BaseEntity } from './BaseEntity'
import { AbstractCurdService, FeedbackUtil } from '@airpower/web'
import { getActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

/**
 * # 抽象服务基类
 * @author Hamm.cn
 */
export abstract class AbstractBaseService<E extends BaseEntity> extends AbstractCurdService<E> {
  /**
   * ### 创建带当前租户意图的 AirPower HTTP 客户端
   *
   * AirPower 的默认客户端只带 access token；后端租户解析器还需要
   * `X-Tenant-Id`，否则多租户账号每次都会回到默认租户。租户 ID 仍由
   * 后端返回的可选列表驱动，服务器会再次校验成员关系，前端 header 不是信任边界。
   */
  protected override api(url: string, baseUrl?: string) {
    const http = super.api(url, baseUrl)
    const pinia = getActivePinia()
    if (pinia) {
      const userStore = useUserStore(pinia)
      if (userStore.tenantId !== undefined) {
        http.addHttpHeader('X-Tenant-Id', userStore.tenantId.toString())
      }
    }
    return http
  }

  /**
   * ### 发布实体
   * @param entity 实体
   */
  async publish(entity: E): Promise<void> {
    await this.api('publish').request(entity)
    FeedbackUtil.toastSuccess('发布成功')
  }
}

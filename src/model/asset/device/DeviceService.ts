import { AbstractBaseService } from '@/base/AbstractBaseService'
import { CollectionEntity } from '@/model/iot/collection/CollectionEntity'
import { DeviceEntity } from './DeviceEntity'

export class DeviceService extends AbstractBaseService<DeviceEntity> {
  entityClass = DeviceEntity

  baseUrl = 'device'

  async getCurrentReport(id: number, apiKey?: string): Promise<CollectionEntity[]> {
    const device = new DeviceEntity(id)
    if (apiKey) {
      device.apiKey = apiKey
    }
    return this.api('getCurrentReport').requestModelList(device, CollectionEntity)
  }

  async getDevicePayloadHistory(item: CollectionEntity): Promise<CollectionEntity[]> {
    return this.api('getDevicePayloadHistory').requestModelList(item, CollectionEntity)
  }

  async getDeviceByUuid(uuid: string, apiKey?: string): Promise<DeviceEntity> {
    const device = new DeviceEntity()
    device.uuid = uuid
    if (apiKey) {
      device.apiKey = apiKey
    }
    return this.api('getDeviceConfig').requestModel(device, DeviceEntity)
  }

  /**
   * ### 查看设备采集端 API-Key（登录态，仅此一次回显，用于向采集端下发）
   */
  async getApiKey(id: number): Promise<string> {
    const result = await this.api('getApiKey').requestRaw(new DeviceEntity(id))
    return result as unknown as string
  }

  /**
   * ### 重置设备采集端 API-Key（登录态，旧密钥立即失效，返回新密钥）
   */
  async resetApiKey(id: number): Promise<string> {
    const result = await this.api('resetApiKey').requestRaw(new DeviceEntity(id))
    return result as unknown as string
  }
}

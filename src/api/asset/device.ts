import type { DeviceEntity, DeviceQuery, CollectionEntity } from './types'
import { request } from '@/api/axios'

export const deviceApi = {
  getPage(params: DeviceQuery): Promise<{ list: DeviceEntity[], total: number }> {
    return request.post('/device/getPage', params)
  },

  getDetail(id: number): Promise<DeviceEntity> {
    return request.post('/device/getDetail', { id })
  },

  add(data: Partial<DeviceEntity>): Promise<DeviceEntity> {
    return request.post('/device/add', data)
  },

  update(data: Partial<DeviceEntity>): Promise<DeviceEntity> {
    return request.post('/device/update', data)
  },

  delete(id: number): Promise<void> {
    return request.post('/device/delete', { id })
  },

  getCurrentReport(id: number): Promise<CollectionEntity[]> {
    return request.post('/device/getCurrentReport', { id })
  },

  getDevicePayloadHistory(data: CollectionEntity): Promise<CollectionEntity[]> {
    return request.post('/device/getDevicePayloadHistory', data)
  },
}
// 统一导出
export * from './axios'
export * from './types'
export * from './system'

// 默认导出
import { api } from './system'
import { request } from './axios'

export default {
  request,
  api,
}
import { request } from '@/api/axios'

/** 白名单命中：框架的请求出口必须是应用实例（夹具里它不该报红） */
export const http = request

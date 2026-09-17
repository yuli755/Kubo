import { configureTransport } from '@/framework/transport'

/** 接缝的正确方向：应用把实例注册给框架（api → framework，不该报红） */
export const request = async () => ({})

configureTransport({ request })

// / <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'

  const component: DefineComponent<unknown, unknown, any>
  export default component
}

interface ImportMetaEnv extends Readonly<Record<string, string | boolean>> {
  // 注：原先这里声明了 `VITE_APP_NAME` / `VITE_APP_API_URL`（配套根目录 `.env.production`）。
  // 全仓没有任何代码读取它们（API 地址由 `vite.config.mts` 的 `/api` 代理与 nginx 承担），
  // 2026-09 清理时随 `.env.production` 一并删除 —— 需要环境变量时**先写读取代码，再补声明**。

  /**
   * ### 是否开发模式
   */
  DEV: boolean

  /**
   * ### 是否生产模式
   */
  PROD: boolean

  /**
   * ### 当前环境模式
   */
  MODE: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'virtual:pwa-register' {
  export interface RegisterSWOptions {
    onNeedRefresh?: () => void
    onOfflineReady?: () => void
    onRegistered?: (swRegistration: ServiceWorkerRegistration | undefined) => void
    onRegisterError?: (error: unknown) => void
  }
  export function registerSW(options?: RegisterSWOptions): (reloadPage?: boolean) => Promise<void>
}

# Kubo 平台前端系统二次改造设计文档

> **版本**: v2.0  
> **状态**: 🟢 设计稿  
> **最后更新**: 2026-08-13  
> **适用场景**: 10,000+ 工厂用户 · 7×24 高并发 · 流畅操作体验

---

## 目录

1. [文档概述](#1-文档概述)
2. [现状分析](#2-现状分析)
3. [目标架构总览](#3-目标架构总览)
4. [技术栈转变对照](#4-技术栈转变对照)
5. [运行时架构设计](#5-运行时架构设计)
6. [状态管理设计](#6-状态管理设计)
7. [API 层设计](#7-api-层设计)
8. [WebSocket 实时通信](#8-websocket-实时通信)
9. [权限引擎设计](#9-权限引擎设计)
10. [模块化与路由设计](#10-模块化与路由设计)
11. [UI 组件体系](#11-ui-组件体系)
12. [大数据渲染优化](#12-大数据渲染优化)
13. [国际化方案](#13-国际化方案)
14. [构建与部署优化](#14-构建与部署优化)
15. [PWA 与离线能力](#15-pwa-与离线能力)
16. [测试与质量保障](#16-测试与质量保障)
17. [性能目标与指标](#17-性能目标与指标)
18. [分阶段实施计划](#18-分阶段实施计划)

---

## 1. 文档概述

### 1.1 编写目的

本文档旨在系统性阐述 Kubo 平台前端从 AirPower 框架向现代化高性能前端架构的二次改造设计方案。读者通过本文档可全面了解：

- 当前前端架构的现状与瓶颈
- 目标架构的设计理念与技术选型
- 每个技术维度的具体改造方案
- 改造后的性能预期与用户体验提升

### 1.2 适用范围

- **改造对象**: Kubo 前端 (Vue 3.5 + TypeScript 5.9 + Vite 7 + @airpower/web 2.9.1)
- **目标形态**: 现代化高性能 SPA + PWA 应用
- **业务场景**: 智能生产管理 (WMS/MES/工厂/财务/IoT)
- **用户规模**: 10,000+ 并发用户

### 1.3 当前前端概况

| 维度 | 数据 |
|------|------|
| Vue 文件数 | 154 |
| 业务模块 | 9 个 (asset, channel, factory, iot, mes, open, personnel, system, wms) |
| 后端模块 | 12 个 → 目标 16 个微服务 |
| 技术栈 | Vue 3 + TypeScript + Element Plus + @airpower/web |
| 入口 | `src/main.ts` (RouterUtil.createRouter + WebConfig) |
| API 层 | 自定义 Axios 封装 (5 个文件) |
| 状态管理 | 无 Pinia store (通过 @airpower/web 内置) |
| 国际化 | 未启用 (Element Plus zhCn 硬编码) |
| 构建 | Vite 7 + vueDevTools (minify: false) |

---

## 2. 现状分析

### 2.1 当前架构概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Kubo 前端当前架构                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Vue 3.5 + TypeScript 5.9 + Vite 7                           │   │
│  │  @airpower/web 2.9.1 (框架核心)                               │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  main.ts 入口                                                │   │
│  │  - RouterUtil.createRouter(routes)                           │   │
│  │  - ElementPlus (zhCn)                                       │   │
│  │  - WebConfig (appKey, uploadUrl, staticUrl)                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│           ┌──────────────────┼──────────────────┐                   │
│           ▼                  ▼                  ▼                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  src/config   │  │  src/api     │  │  src/model    │              │
│  │  routes.ts    │  │  axios.ts    │  │  (9 模块)     │              │
│  │  AppConfig.ts │  │  system.ts   │  │               │              │
│  │  Constant.ts  │  │  types.ts    │  │  ┌─────────┐  │              │
│  │  store.ts     │  │  index.ts    │  │  │ BaseEntity│  │              │
│  └──────────────┘  │  permissions │  │  └─────────┘  │              │
│                     └──────────────┘  └──────────────┘              │
│           │                  │                  │                   │
│           ▼                  ▼                  ▼                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │  src/view     │  │  src/component│  │  src/hook    │              │
│  │  (9 个 console)│  │  Console.vue  │  │  useMyTable  │              │
│  │  login.vue    │  │  UserCard    │  │  useBillTable │              │
│  │  home.vue     │  │  AuditFlow   │  │  IBaseTable   │              │
│  │  callback.vue │  │  PublishButton│  └──────────────┘              │
│  │  bind.vue     │  │  PayloadLink │                                 │
│  │  authorize.vue│  │  bill/*      │                                 │
│  │  error.vue    │  │  login/*     │                                 │
│  └──────────────┘  │  user/*      │                                 │
│                     └──────────────┘                                 │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  状态管理: 无 Pinia / Vuex                                    │   │
│  │  - 用户信息通过 @airpower/web 内置管理                          │   │
│  │  - 路由通过 RouterUtil 自动生成                               │   │
│  │  - 无持久化缓存 (刷新后状态丢失)                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  构建配置                                                    │   │
│  │  - minify: false (vite BUG 导致 keep_classnames 不生效)       │   │
│  │  - 无压缩/拆分/预加载优化                                     │   │
│  │  - 无 PWA / Service Worker                                   │   │
│  │  - 无 i18n                                                    │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 当前技术栈明细

| 维度 | 当前选型 | 版本 | 备注 |
|------|---------|------|------|
| **框架** | Vue 3 | ^3.5.25 | 可保留升级 |
| **语言** | TypeScript | ^5.9.3 | 可保留升级 |
| **构建** | Vite | ^7.3.0 | 可保留升级 |
| **UI 库** | Element Plus | ^2.12.0 | 可保留升级 |
| **基础框架** | @airpower/web | ^2.9.1 | 核心依赖，封装了大量逻辑 |
| **图标** | @element-plus/icons-vue | ^2.3.2 | 可保留 |
| **路由** | vue-router | ^4.6.4 | 由 @airpower/web 封装 |
| **状态管理** | 无独立方案 | — | 框架内置 |
| **HTTP 请求** | Axios | 1.13.2 | 保留升级 |
| **图表** | ECharts | ^6.0.0 | 保留 |
| **二维码** | qrcode.vue | 3.6.0 | 保留 |
| **拖拽** | vue-draggable-plus | ^0.6.0 | 保留 |
| **加密** | crypto-js | ^4.2.0 | 保留 |
| **Base64** | js-base64 | ^3.7.8 | 保留 |
| **CSS** | SCSS | ^1.96.0 | 保留 |

### 2.3 当前存在的关键问题

#### 🔴 P0 - 阻塞性问题

| 问题 | 描述 | 影响 |
|------|------|------|
| **无独立状态管理** | 依赖 @airpower/web 内置状态管理，无 Pinia | 状态不可控，刷新后丢失，无法持久化 |
| **无服务端状态管理** | 无 @tanstack/vue-query | 缓存不可控，频繁重复请求，无乐观更新 |
| **构建产物未压缩** | minify: false (Vite BUG) | 生产包体积巨大，加载缓慢 |
| **无 WebSocket 管理器** | 原生 WebSocket 无 STOMP 协议 | 高并发下连接不稳定，无自动重连 |

#### 🟡 P1 - 重要问题

| 问题 | 描述 | 影响 |
|------|------|------|
| 无 PWA 离线能力 | 无 Service Worker | 网络不稳定时无法使用 |
| 无国际化框架 | Element Plus zhCn 硬编码 | 无法支持多语言 |
| 无虚拟滚动 | 表格直接渲染全部数据 | 1000+ 行数据渲染卡顿 |
| 无权限控制引擎 | 权限由 @airpower/web 封装 | 无法与后端行级/列级权限配合 |
| 路由配置固化 | 通过 RouterUtil 自动生成，定制困难 | 无法实现细粒度权限路由 |
| 无单元测试 | 无测试框架 | 重构风险高，无法自动化验证 |

#### 🟢 P2 - 优化问题

| 问题 | 描述 | 影响 |
|------|------|------|
| 无代码分割 | 路由懒加载但不细粒度 | 首屏加载过大 |
| 无请求缓存 | 每次进入页面都重新请求 | 重复网络开销 |
| 无错误边界 | 组件崩溃导致白屏 | 用户体验差 |
| 无骨架屏 | 加载中直接白屏 | 感知性能差 |
| 无构建分析 | 无 bundle 分析 | 无法优化体积 |
| 无 TypeScript 严格模式 | 类型检查不严格 | 运行时类型错误 |

---

## 3. 目标架构总览

### 3.1 目标架构全景

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Kubo 前端目标架构                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                          PWA Shell                            │   │
│  │   Service Worker (离线缓存 + 预加载 + 后台同步)               │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Vue 3.5 + TypeScript 5.9                    │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────────────┐  │   │
│  │  │ Element Plus │  │  ECharts    │  │  @vueuse/core        │  │   │
│  │  │ 2.12         │  │ 6.0         │  │  (composables)       │  │   │
│  │  └─────────────┘  └─────────────┘  └──────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│              ┌───────────────┼───────────────┐                      │
│              ▼               ▼               ▼                      │
│  ┌────────────────────┐ ┌────────────────┐ ┌──────────────────┐    │
│  │  全局状态 (Pinia)   │ │  服务端状态      │ │  WebSocket 管理器  │    │
│  │  - userStore       │ │  @tanstack/vue- │ │  - STOMP 1.2     │    │
│  │  - permissionStore │ │  query          │ │  - 自动重连       │    │
│  │  - appStore        │ │  - 缓存管理     │ │  - 优先级队列     │    │
│  │  - tenantStore     │ │  - 乐观更新     │ │  - 心跳检测       │    │
│  │                    │ │  - 数据预取     │ │                  │    │
│  └────────────────────┘ └────────────────┘ └──────────────────┘    │
│                              │                                       │
│              ┌───────────────┼───────────────┐                      │
│              ▼               ▼               ▼                      │
│  ┌────────────────────┐ ┌────────────────┐ ┌──────────────────┐    │
│  │  权限引擎           │ │  API 网关层     │ │  国际化 (i18n)    │    │
│  │  - 路由权限         │ │  - Axios 封装  │ │  - vue-i18n v9   │    │
│  │  - 按钮权限         │ │  - 请求拦截    │ │  - 多语言包       │    │
│  │  - 数据权限         │ │  - 响应拦截    │ │  - 动态切换       │    │
│  │  - 字段权限 (脱敏)   │ │  - 错误处理    │ │                  │    │
│  └────────────────────┘ └────────────────┘ └──────────────────┘    │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    业务视图层 (9 个模块)                       │   │
│  │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │   │
│  │  │ WMS  │ │ MES  │ │Factory│ │Asset │ │Channel│ │Person│     │   │
│  │  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │   │
│  │  ┌──────┐ ┌──────┐ ┌──────┐                                   │   │
│  │  │ IoT  │ │Open  │ │System│                                   │   │
│  │  └──────┘ └──────┘ └──────┘                                   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  构建层: Vite 7 + vite-plugin-compression + vite-plugin-pwa   │   │
│  │  - 代码分割 + 预加载 + 压缩 + Service Worker                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  质量保障: Vitest + Playwright E2E + ESLint 9 + TypeScript   │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 架构设计原则

| 原则 | 说明 | 实践 |
|------|------|------|
| **性能优先** | 首屏加载快、交互流畅 | 代码分割、预加载、虚拟滚动、WebWorker |
| **离线可用** | 网络不稳定时核心功能可用 | PWA + Service Worker + IndexedDB 缓存 |
| **状态可控** | 所有状态可预测、可追踪 | Pinia + @tanstack/vue-query + 持久化 |
| **安全第一** | 前端配合后端实现纵深防御 | 路由权限 + 按钮权限 + 字段脱敏 |
| **国际化** | 支持多语言 | vue-i18n v9 + 语言包按需加载 |
| **可测试** | 核心逻辑可自动化测试 | Vitest 单元测试 + Playwright E2E |
| **可维护** | 代码规范、模块化、文档化 | TypeScript 严格模式 + ESLint + 注释 |

---

## 4. 技术栈转变对照

### 4.1 核心技术栈对比

| 维度 | 当前 (改造前) | 目标 (改造后) | 转变理由 |
|------|--------------|--------------|----------|
| **框架** | @airpower/web 2.9.1 (封装过多) | Vue 3.5 + 独立选型 (保留部分兼容) | 解耦框架限制，回归标准生态 |
| **状态管理** | 框架内置 (无 Pinia) | **Pinia 3.x** + **@tanstack/vue-query 5.x** | 可预测状态管理 + 服务端缓存 |
| **HTTP 请求** | 自定义 Axios 封装 | Axios + **@tanstack/vue-query** | 自动缓存、去重、乐观更新 |
| **WebSocket** | 原生 WebSocket (无封装) | **STOMP 1.2** + 自动重连管理器 | 集群兼容、高可用、自动恢复 |
| **权限控制** | 框架内置 RBAC | **自定义权限引擎** (路由+按钮+数据+字段) | 配合后端行级/列级权限 |
| **国际化** | Element Plus zhCn 硬编码 | **vue-i18n v9** | 多语言支持、动态切换 |
| **UI 组件** | Element Plus 基础组件 | Element Plus + **自定义业务组件库** | 业务复用、统一风格 |
| **大数据渲染** | 直接渲染 (无优化) | **虚拟滚动** (vue-virtual-scroller) | 1000+ 行数据流畅渲染 |
| **构建优化** | minify: false (Vite BUG) | minify: esbuild + **压缩插件** | 生产包体积减少 60%+ |
| **PWA** | 无 | **vite-plugin-pwa** + Service Worker | 离线可用、预加载、后台同步 |
| **WebWorker** | 无 | 导出/复杂计算 → WebWorker | 不阻塞主线程 |
| **工具库** | 无 | **@vueuse/core** | 标准 Composable 工具函数 |
| **测试** | 无 | **Vitest** + **@vue/test-utils** + **Playwright** | 代码质量保障 |
| **ESLint** | @antfu/eslint-config 6.x | 保留升级 | 代码规范 |
| **TypeScript** | 宽松模式 | **严格模式** (strict: true) | 类型安全 |

### 4.2 新增依赖清单

```json
{
  "dependencies": {
    "vue": "^3.5.25",                    // 保留
    "element-plus": "^2.12.0",           // 保留
    "pinia": "^3.0.4",                   // 保留 (现为独立使用)
    "@tanstack/vue-query": "^5.x",       // 新增: 服务端状态管理
    "@vueuse/core": "^11.x",             // 新增: 标准 Composable 工具
    "vue-i18n": "^9.x",                  // 新增: 国际化
    "vue-virtual-scroller": "^2.x",      // 新增: 虚拟滚动
    "@stomp/stompjs": "^7.x",            // 新增: STOMP WebSocket
    "sockjs-client": "^1.x",             // 新增: WebSocket 回退

    // 保留 (已有)
    "axios": "1.13.2",
    "echarts": "^6.0.0",
    "crypto-js": "^4.2.0",
    "js-base64": "^3.7.8",
    "qrcode.vue": "3.6.0",
    "vue-draggable-plus": "^0.6.0",
    "@element-plus/icons-vue": "^2.3.2"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^6.0.3",      // 保留
    "vite": "^7.3.0",                    // 保留
    "typescript": "^5.9.3",              // 保留
    "sass": "^1.96.0",                   // 保留
    "eslint": "^9.39.2",                 // 保留

    // 新增
    "vite-plugin-compression": "^1.x",   // 新增: gzip/brotli 压缩
    "vite-plugin-pwa": "^0.x",           // 新增: PWA 支持
    "vite-plugin-inspect": "^0.x",       // 新增: 构建分析
    "vitest": "^2.x",                    // 新增: 单元测试
    "@vue/test-utils": "^2.x",           // 新增: Vue 测试工具
    "playwright": "^1.x",                // 新增: E2E 测试
    "vite-plugin-vue-devtools": "^8.x",  // 保留

    // 保留 (已有)
    "@antfu/eslint-config": "^6.7.1",
    "globals": "^16.5.0",
    "vue-tsc": "^3.1.8",
    "openapi-typescript-codegen": "^2.0.0"
  }
}
```

### 4.3 依赖移除说明

| 依赖 | 状态 | 说明 |
|------|------|------|
| @airpower/web | ⚠️ 逐步剥离 | 保留兼容层，逐步替换为独立实现 |
| register-service-worker | ❌ 移除 | 由 vite-plugin-pwa 替代 |

---

## 5. 运行时架构设计

### 5.1 应用初始化流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                    应用初始化流程                                     │
│                                                                      │
│  1. 用户访问应用                                                    │
│     │                                                                │
│     ▼                                                                │
│  2. Service Worker 激活 (PWA)                                       │
│     ├─ 缓存 App Shell (HTML/CSS/JS)                                 │
│     └─ 预加载关键资源                                                │
│     │                                                                │
│     ▼                                                                │
│  3. Vue 应用初始化                                                   │
│     ├─ Pinia 初始化                                                  │
│     │   ├─ appStore (应用配置)                                       │
│     │   ├─ userStore (用户信息) ← 尝试从 localStorage 恢复           │
│     │   └─ permissionStore (权限) ← 从缓存加载                      │
│     │                                                                │
│     ├─ @tanstack/vue-query 初始化 (QueryClient)                      │
│     │   ├─ 默认缓存策略: staleTime 30s, gcTime 5min                  │
│     │   └─ 全局错误处理                                              │
│     │                                                                │
│     ├─ vue-i18n 初始化                                               │
│     │   ├─ 检测浏览器语言                                            │
│     │   └─ 加载对应语言包                                            │
│     │                                                                │
│     ├─ WebSocket 管理器初始化                                        │
│     │   ├─ STOMP 连接                                                │
│     │   └─ 订阅全局频道                                              │
│     │                                                                │
│     └─ 路由初始化                                                    │
│         ├─ 动态路由 (根据权限加载)                                   │
│         └─ 路由守卫 (认证 + 权限检查)                                │
│     │                                                                │
│     ▼                                                                │
│  4. 页面渲染                                                         │
│     ├─ 骨架屏 (Skeleton)                                             │
│     ├─ 数据预取 (页面级 data prefetch)                               │
│     └─ 页面组件渲染                                                  │
│                                                                      │
│  5. 后台任务                                                         │
│     ├─ Token 刷新定时器                                              │
│     ├─ 权限规则缓存刷新                                              │
│     └─ WebSocket 心跳                                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 5.2 应用入口改造

```typescript
// ── 改造前: src/main.ts ──
import { RouterUtil, WebConfig } from '@airpower/web'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { createApp, version } from 'vue'
import App from '@/App.vue'
import { routes } from '@/config/routes'
import '@airpower/web/dist/web.css'
import '@/assets/css/main.scss'

WebConfig.elementPlusLocale = zhCn
WebConfig.appKey = 'kubo'
WebConfig.product = 'Kubo'
WebConfig.uploadUrl = '/api/file/upload'
WebConfig.staticUrl = '/static/'

createApp(App)
  .use(RouterUtil.createRouter(routes))
  .use(ElementPlus, { zIndex: 3000, locale: zhCn })
  .mount('#app')

// ── 改造后: src/main.ts ──
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from '@/App.vue'
import { createAppRouter } from '@/config/router'
import { queryClient } from '@/api/queryClient'
import { i18nConfig } from '@/config/i18n'
import { initWebSocketManager } from '@/websocket'
import { initAuthGuard } from '@/guard/authGuard'
import { initPermissionGuard } from '@/guard/permissionGuard'
import { registerSW } from '@/pwa/registerSW'
import 'element-plus/dist/index.css'
import '@/assets/css/main.scss'

async function bootstrap() {
  const app = createApp(App)

  // 1. 状态管理
  const pinia = createPinia()
  app.use(pinia)

  // 2. 服务端状态
  app.use(VueQueryPlugin, { queryClient })

  // 3. 国际化
  const i18n = createI18n(i18nConfig)
  app.use(i18n)

  // 4. UI 组件
  app.use(ElementPlus, { zIndex: 3000, locale: zhCn })

  // 5. 路由
  const router = createAppRouter()
  app.use(router)

  // 6. 路由守卫
  initAuthGuard(router)
  initPermissionGuard(router)

  // 7. 挂载
  app.mount('#app')

  // 8. 后台初始化
  initWebSocketManager()   // WebSocket 连接
  registerSW()             // Service Worker 注册
}

bootstrap().catch(console.error)
```

### 5.3 运行时模块关系

```
┌─────────────────────────────────────────────────────────────────────┐
│                    运行时模块依赖关系                                  │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  App.vue                                                     │   │
│  │  ├─ <router-view />                                         │   │
│  │  ├─ <GlobalLoading /> (全局加载指示器)                        │   │
│  │  ├─ <GlobalErrorBoundary /> (全局错误边界)                    │   │
│  │  └─ <WebSocketStatus /> (连接状态指示器)                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│              │             │             │                          │
│     ┌────────┘     ┌───────┘     ┌──────┘                          │
│     ▼              ▼             ▼                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                            │
│  │ Router   │ │ Pinia    │ │ @tanstack│                            │
│  │          │ │ Store    │ │ /vue-    │                            │
│  │ AuthGuard│ │ userStore│ │ query    │                            │
│  │ PermGuard│ │ permStore│ │ 缓存管理  │                            │
│  │          │ │ appStore │ │ 乐观更新 │                            │
│  │ LazyLoad │ │          │ │ 数据预取  │                            │
│  └──────────┘ └──────────┘ └──────────┘                            │
│       │              │              │                               │
│       └──────────────┼──────────────┘                               │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  API 层 (Axios + @tanstack/vue-query hooks)                   │   │
│  │  请求拦截 → 响应拦截 → 错误处理 → 缓存管理                    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                      │                                              │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  WebSocket 层 (STOMP 1.2)                                    │   │
│  │  实时通知 → 消息推送 → 状态同步 → 数据更新                    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                      │                                              │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  后端服务 (Spring Cloud Gateway → kubo-* 微服务)              │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 6. 状态管理设计

### 6.1 状态分层架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                   状态分层架构                                        │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Layer 1: 全局状态 (Pinia)                                    │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  │   │
│  │  │ userStore     │  │ appStore     │  │ permissionStore     │  │   │
│  │  │ - token       │  │ - theme      │  │ - roles            │  │   │
│  │  │ - userInfo    │  │ - sidebar    │  │ - permissions      │  │   │
│  │  │ - tenantId    │  │ - locale     │  │ - menuTree         │  │   │
│  │  │ - refreshToken│  │ - device     │  │ - dataScopes       │  │   │
│  │  │                │  │ - lang       │  │ - fieldPolicies    │  │   │
│  │  │ 持久化: localStorage │  持久化: localStorage  │ 持久化: sessionStorage │  │
│  │  └──────────────┘  └──────────────┘  └────────────────────┘  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Layer 2: 服务端状态 (@tanstack/vue-query)                    │   │
│  │                                                                  │
│  │  缓存策略:                                                      │
│  │  ├─ staleTime: 30s (数据在 30s 内视为新鲜, 不重新请求)          │   │
│  │  ├─ gcTime: 5min (缓存保留 5 分钟)                              │   │
│  │  ├─ refetchOnWindowFocus: true (窗口聚焦时重新验证)              │   │
│  │  └─ retry: 3 (失败重试 3 次)                                    │   │
│  │                                                                  │
│  │  适用场景:                                                      │
│  │  ├─ 列表数据 (分页/搜索)                                        │   │
│  │  ├─ 详情数据 (ID 查询)                                          │   │
│  │  ├─ 下拉选项 (字典/枚举)                                        │   │
│  │  └─ 业务统计 (Dashboard 数据)                                   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Layer 3: 本地状态 (Composable / ref / reactive)              │   │
│  │                                                                  │
│  │  适用场景:                                                      │
│  │  ├─ 表单输入 (v-model 绑定)                                     │   │
│  │  ├─ UI 状态 (展开/折叠/选中)                                    │   │
│  │  ├─ 临时计算 (computed)                                         │   │
│  │  └─ 组件内状态                                                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Pinia Store 定义

```typescript
// ── userStore (用户认证) ──
export const useUserStore = defineStore('user', {
  state: () => ({
    token: null as string | null,
    refreshToken: null as string | null,
    userInfo: null as UserInfo | null,
    tenantId: null as number | null,
    permissions: [] as string[],
    roles: [] as string[],
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.roles.includes('ROLE_SUPER_ADMIN'),
    hasPermission: (state) => (perm: string) => state.permissions.includes(perm),
  },

  actions: {
    async login(credentials: LoginParams) {
      const { data } = await authApi.login(credentials)
      this.token = data.accessToken
      this.refreshToken = data.refreshToken
      this.userInfo = data.user
      this.tenantId = data.tenantId
      // 持久化到 localStorage
      this.persist()
    },

    async logout() {
      await authApi.logout()
      this.$reset()
      this.clearPersist()
      router.push('/login')
    },

    async refreshTokenAction() {
      const { data } = await authApi.refreshToken(this.refreshToken!)
      this.token = data.accessToken
      this.refreshToken = data.refreshToken
      this.persist()
    },

    persist() {
      localStorage.setItem('kubo:user', JSON.stringify({
        token: this.token,
        refreshToken: this.refreshToken,
        userInfo: this.userInfo,
        tenantId: this.tenantId,
        permissions: this.permissions,
        roles: this.roles,
      }))
    },

    restore() {
      // 应用初始化时从 localStorage 恢复
      try {
        const saved = localStorage.getItem('kubo:user')
        if (saved) {
          const data = JSON.parse(saved)
          this.$patch(data)
        }
      } catch { /* ignore */ }
    },

    clearPersist() {
      localStorage.removeItem('kubo:user')
    },
  },
})

// ── permissionStore (权限控制) ──
export const usePermissionStore = defineStore('permission', {
  state: () => ({
    menuTree: [] as MenuItem[],
    flatMenus: [] as MenuItem[],
    dataScopes: {} as Record<string, number[]>,
    fieldPolicies: {} as Record<string, FieldPolicy>,
    loaded: false,
  }),

  getters: {
    hasDataScope: (state) => (dimension: string, id: number) => {
      const ids = state.dataScopes[dimension]
      return ids?.includes(id) ?? false
    },

    getFieldPolicy: (state) => (entity: string, field: string) => {
      return state.fieldPolicies[`${entity}:${field}`]
    },
  },

  actions: {
    async loadPermissions() {
      if (this.loaded) return
      const { data } = await permissionApi.getMyPermissions()
      this.menuTree = data.menus
      this.flatMenus = flattenMenuTree(data.menus)
      this.dataScopes = data.dataScopes
      this.fieldPolicies = data.fieldPolicies
      this.loaded = true
    },

    refreshPermissions() {
      this.loaded = false
      return this.loadPermissions()
    },
  },
})
```

### 6.3 @tanstack/vue-query 配置

```typescript
// ── src/api/queryClient.ts ──
import { QueryClient } from '@tanstack/vue-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30 * 1000,          // 30s 内不重新请求
      gcTime: 5 * 60 * 1000,         // 缓存保留 5 分钟
      refetchOnWindowFocus: true,     // 窗口聚焦时重新验证
      retry: 3,                       // 失败重试 3 次
      retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
      refetchOnReconnect: true,       // 网络重连时重新验证
    },
    mutations: {
      retry: 1,
      onError: (error) => {
        // 全局错误处理
        handleMutationError(error)
      },
    },
  },
})

// ── 使用示例: 列表查询 ──
export function useInventoryList(query: Ref<InventoryQuery>) {
  return useQuery({
    queryKey: ['inventory', 'list', query],  // 自动缓存键
    queryFn: () => inventoryApi.page(query.value),
    placeholderData: keepPreviousData,  // 分页时保持上一页数据
  })
}

// ── 使用示例: 创建/更新 ──
export function useCreateInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: InventoryCreateCmd) => inventoryApi.create(data),
    onSuccess: () => {
      // 创建成功后, 自动刷新列表
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
      ElMessage.success('创建成功')
    },
  })
}

// ── 使用示例: 乐观更新 ──
export function useUpdateInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: InventoryUpdateCmd) => inventoryApi.update(data),
    onMutate: async (newData) => {
      // 乐观更新: 立即更新缓存
      await queryClient.cancelQueries({ queryKey: ['inventory', 'detail', newData.id] })
      const previous = queryClient.getQueryData(['inventory', 'detail', newData.id])
      queryClient.setQueryData(['inventory', 'detail', newData.id], newData)
      return { previous }
    },
    onError: (err, newData, context) => {
      // 失败时回滚
      if (context?.previous) {
        queryClient.setQueryData(['inventory', 'detail', newData.id], context.previous)
      }
    },
    onSettled: () => {
      // 完成后刷新
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
  })
}
```

### 6.4 状态持久化策略

```typescript
// ── 持久化方案 ──
// 1. localStorage: 用户信息 (token, 偏好设置)
// 2. sessionStorage: 权限缓存 (会话级别)
// 3. IndexedDB: 离线数据 (PWA)
// 4. Pinia plugin: 自动持久化 (可选)

// ── Pinia 持久化插件 (简化版) ──
export function createPersistPlugin(options: PersistOptions) {
  return (context: PiniaPluginContext) => {
    const { store, options: storeOptions } = context
    const persistKey = `kubo:${store.$id}`

    // 恢复
    try {
      const saved = localStorage.getItem(persistKey)
      if (saved) {
        store.$patch(JSON.parse(saved))
      }
    } catch { /* ignore */ }

    // 订阅变化
    store.$subscribe((mutation, state) => {
      if (storeOptions.persist !== false) {
        localStorage.setItem(persistKey, JSON.stringify(state))
      }
    }, { detached: true })
  }
}
```

---

## 7. API 层设计

### 7.1 API 层架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    API 层分层设计                                    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  视图层 (View/Component)                                      │   │
│  │  └─ 使用 @tanstack/vue-query hooks 替代直接 API 调用          │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                       │
│  ┌──────────────────────────▼───────────────────────────────────┐   │
│  │  @tanstack/vue-query Hooks                                  │   │
│  │  ├─ useQuery / useMutation                                  │   │
│  │  ├─ 缓存管理 / 乐观更新 / 数据预取                           │   │
│  │  └─ 自动错误处理 / 加载状态                                  │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                       │
│  ┌──────────────────────────▼───────────────────────────────────┐   │
│  │  API 服务层 (Service)                                        │   │
│  │  ├─ inventory.api.ts (业务 API 调用)                         │   │
│  │  └─ 调用 Axios 实例                                          │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                       │
│  ┌──────────────────────────▼───────────────────────────────────┐   │
│  │  Axios 实例 (基础请求层)                                     │   │
│  │  ├─ 请求拦截: Token 注入 / 租户注入 / 幂等键                 │   │
│  │  ├─ 响应拦截: 统一错误处理 / Token 刷新                      │   │
│  │  └─ 超时 / 重试 / 取消                                      │   │
│  └──────────────────────────┬───────────────────────────────────┘   │
│                             │                                       │
│                             ▼                                       │
│                   后端 Spring Cloud Gateway                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 7.2 Axios 实例改造

```typescript
// ── src/api/request.ts (改造后) ──
import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import { useUserStore } from '@/stores/user'
import { handleTokenExpiry } from './tokenRefresh'
import { handleApiError } from './errorHandler'

const API_PREFIX = '/api'

export function createRequest(): AxiosInstance {
  const instance = axios.create({
    baseURL: API_PREFIX,
    timeout: 30000,
    headers: { 'Content-Type': 'application/json' },
    withCredentials: true,
  })

  // ── 请求拦截器 ──
  instance.interceptors.request.use(
    (config: InternalAxiosRequestConfig) => {
      const userStore = useUserStore()

      // Token 注入
      if (userStore.token) {
        config.headers.Authorization = `Bearer ${userStore.token}`
      }

      // 租户注入
      if (userStore.tenantId) {
        config.headers['X-Tenant-Id'] = userStore.tenantId
      }

      // 幂等键 (POST/PUT/PATCH)
      if (config.method && ['post', 'put', 'patch'].includes(config.method)) {
        config.headers['Idempotency-Key'] = crypto.randomUUID()
      }

      // 链路追踪
      config.headers['X-Trace-Id'] = crypto.randomUUID()

      return config
    },
    (error) => Promise.reject(error),
  )

  // ── 响应拦截器 ──
  instance.interceptors.response.use(
    (response) => {
      const { code, message, data } = response.data

      if (code === 200) {
        return data  // 直接返回业务数据
      }

      // 业务错误
      return Promise.reject(new ApiError(code, message, data))
    },
    async (error) => {
      if (error.response?.status === 401) {
        // Token 过期 → 尝试刷新
        const refreshed = await handleTokenExpiry()
        if (refreshed) {
          return instance(error.config)  // 重试原请求
        }
        // 刷新失败 → 跳转登录
        useUserStore().logout()
        return Promise.reject(error)
      }

      handleApiError(error)
      return Promise.reject(error)
    },
  )

  return instance
}

export const request = createRequest()
```

### 7.3 业务 API 封装

```typescript
// ── src/api/services/inventory.api.ts (新结构) ──
import { request } from '@/api/request'
import type { InventoryVO, InventoryQuery, InventoryCreateCmd, InventoryUpdateCmd } from '@/api/generated/models'

const BASE_URL = '/v1/wms/inventory'

export const inventoryApi = {
  /** 分页查询 */
  page(query: InventoryQuery): Promise<PageResult<InventoryVO>> {
    return request.get(`${BASE_URL}/page`, { params: query })
  },

  /** 获取详情 */
  getById(id: number): Promise<InventoryVO> {
    return request.get(`${BASE_URL}/${id}`)
  },

  /** 创建 */
  create(data: InventoryCreateCmd): Promise<InventoryVO> {
    return request.post(BASE_URL, data)
  },

  /** 更新 */
  update(data: InventoryUpdateCmd): Promise<void> {
    return request.put(BASE_URL, data)
  },

  /** 删除 */
  delete(id: number): Promise<void> {
    return request.delete(`${BASE_URL}/${id}`)
  },

  /** 导出 */
  export(query: InventoryQuery): Promise<Blob> {
    return request.get(`${BASE_URL}/export`, {
      params: query,
      responseType: 'blob',
    })
  },
}

// ── @tanstack/vue-query hooks ──
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'

export function useInventoryList(query: Ref<InventoryQuery>) {
  return useQuery({
    queryKey: ['inventory', 'list', query],
    queryFn: () => inventoryApi.page(query.value),
    placeholderData: keepPreviousData,
  })
}

export function useInventoryDetail(id: Ref<number | null>) {
  return useQuery({
    queryKey: ['inventory', 'detail', id],
    queryFn: () => inventoryApi.getById(id.value!),
    enabled: computed(() => id.value !== null),
  })
}

export function useCreateInventory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: InventoryCreateCmd) => inventoryApi.create(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
      ElMessage.success('创建成功')
    },
  })
}
```

### 7.4 API 类型自动生成

```json
{
  "scripts": {
    "api:generate": "node infra/scripts/generate-api.mjs",
    "api:generate:dev": "API_DOC_URL=http://localhost:8080/v3/api-docs node infra/scripts/generate-api.mjs",
    "api:generate:prod": "API_DOC_URL=https://api.kubo.example.com/v3/api-docs node infra/scripts/generate-api.mjs"
  }
}
```

```
生成输出:
src/api/generated/
├── models/                 # 接口定义 (DTO/VO)
│   ├── InventoryVO.ts
│   ├── InventoryQuery.ts
│   ├── InventoryCreateCmd.ts
│   └── ...
├── services/               # API 调用 (可选)
│   ├── InventoryService.ts
│   └── ...
└── core/                   # 请求工具
    ├── ApiError.ts
    ├── OpenAPI.ts
    └── ...
```

---

## 8. WebSocket 实时通信

### 8.1 WebSocket 架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    WebSocket 实时通信架构                             │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  前端 WebSocket 管理器 (STOMP 1.2)                           │   │
│  │                                                                  │
│  │  ┌──────────────────────────────────────────────────────────┐   │
│  │  │  Connection Manager                                      │   │
│  │  │  ├─ 连接管理 (STOMP over WebSocket)                      │   │
│  │  │  ├─ 心跳检测 (30s/30s)                                   │   │
│  │  │  ├─ 自动重连 (指数退避 + 随机抖动)                       │   │
│  │  │  └─ 连接状态: CONNECTING / CONNECTED / RECONNECTING /    │   │
│  │  │              DISCONNECTED                                │   │
│  │  └──────────────────────────────────────────────────────────┘   │
│  │                                                                  │
│  │  ┌──────────────────────────────────────────────────────────┐   │
│  │  │  Subscription Manager                                    │   │
│  │  │  ├─ 订阅管理 (按主题/路由)                               │   │
│  │  │  ├─ 自动重订阅 (重连后恢复)                              │   │
│  │  │  └─ 优先级队列 (CRITICAL / HIGH / NORMAL / LOW)          │   │
│  │  └──────────────────────────────────────────────────────────┘   │
│  │                                                                  │
│  │  ┌──────────────────────────────────────────────────────────┐   │
│  │  │  Message Handler                                         │   │
│  │  │  ├─ 消息分发 (按类型路由到处理函数)                       │   │
│  │  │  ├─ 消息去重 (消息 ID 幂等)                              │   │
│  │  │  └─ 消息确认 (client-individual ACK)                     │   │
│  │  └──────────────────────────────────────────────────────────┘   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  RabbitMQ STOMP Plugin                                      │   │
│  │  ├─ /exchange/kubo.notification (通知推送)                   │   │
│  │  ├─ /queue/kubo.inventory.change (库存变更)                  │   │
│  │  ├─ /topic/kubo.broadcast (广播消息)                         │   │
│  │  └─ /user/queue/kubo.private (个人消息)                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  后端 WebSocket 服务 (kubo-gateway / kubo-chat)              │   │
│  │  ├─ 集群化消息推送 (通过 RabbitMQ 广播)                       │   │
│  │  └─ 消息持久化 (离线消息存储)                                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 WebSocket 管理器实现

```typescript
// ── src/websocket/manager.ts ──
import { Client, type IFrame, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { useUserStore } from '@/stores/user'

export enum ConnectionState {
  DISCONNECTED = 'disconnected',
  CONNECTING = 'connecting',
  CONNECTED = 'connected',
  RECONNECTING = 'reconnecting',
}

export enum MessagePriority {
  CRITICAL = 0,  // 库存变更、订单状态
  HIGH = 1,      // 通知提醒
  NORMAL = 2,    // 数据同步
  LOW = 3,       // 日志、非关键更新
}

interface Subscription {
  destination: string
  handler: (message: unknown) => void
  priority: MessagePriority
  stompSub?: StompSubscription
}

class WebSocketManager {
  private client: Client | null = null
  private subscriptions = new Map<string, Subscription>()
  private connectionState = ref<ConnectionState>(ConnectionState.DISCONNECTED)
  private reconnectAttempts = 0
  private maxReconnectAttempts = 20

  readonly state = computed(() => this.connectionState.value)

  connect() {
    const userStore = useUserStore()
    if (!userStore.token) return

    this.connectionState.value = ConnectionState.CONNECTING

    this.client = new Client({
      brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
      connectHeaders: {
        Authorization: `Bearer ${userStore.token}`,
        'X-Tenant-Id': userStore.tenantId?.toString() ?? '',
      },
      heartbeatIncoming: 30000,   // 30s 心跳
      heartbeatOutgoing: 30000,
      reconnectDelay: this.getReconnectDelay(),  // 指数退避
      onConnect: () => {
        this.connectionState.value = ConnectionState.CONNECTED
        this.reconnectAttempts = 0
        this.resubscribeAll()
      },
      onDisconnect: () => {
        this.connectionState.value = ConnectionState.DISCONNECTED
      },
      onStompError: (frame: IFrame) => {
        console.error('STOMP error:', frame.headers['message'])
        this.connectionState.value = ConnectionState.RECONNECTING
      },
    })

    this.client.activate()
  }

  disconnect() {
    this.client?.deactivate()
    this.connectionState.value = ConnectionState.DISCONNECTED
  }

  subscribe(destination: string, handler: (message: unknown) => void, priority = MessagePriority.NORMAL) {
    this.subscriptions.set(destination, { destination, handler, priority })

    if (this.client?.connected) {
      this.subscribeToDestination(destination)
    }
  }

  unsubscribe(destination: string) {
    const sub = this.subscriptions.get(destination)
    sub?.stompSub?.unsubscribe()
    this.subscriptions.delete(destination)
  }

  private subscribeToDestination(destination: string) {
    const sub = this.subscriptions.get(destination)
    if (!sub || !this.client?.connected) return

    sub.stompSub = this.client.subscribe(destination, (message: IMessage) => {
      try {
        const parsed = JSON.parse(message.body)
        sub.handler(parsed)
        // 手动 ACK (client-individual)
        message.ack()
      } catch (e) {
        console.error('Failed to parse WS message:', e)
      }
    }, { ack: 'client-individual' })
  }

  private resubscribeAll() {
    // 按优先级排序重订阅
    const sorted = [...this.subscriptions.values()]
      .sort((a, b) => a.priority - b.priority)

    for (const sub of sorted) {
      this.subscribeToDestination(sub.destination)
    }
  }

  private getReconnectDelay(): number {
    // 指数退避 + 随机抖动
    const base = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000)
    const jitter = Math.random() * 1000
    return base + jitter
  }
}

export const wsManager = new WebSocketManager()

// ── 使用示例 ──
// 在组件中订阅
wsManager.subscribe(
  '/exchange/kubo.notification',
  (msg) => handleNotification(msg),
  MessagePriority.HIGH,
)

// 在组件销毁时取消订阅
onUnmounted(() => {
  wsManager.unsubscribe('/exchange/kubo.notification')
})
```

### 8.3 消息类型与处理

```typescript
// ── src/websocket/messageTypes.ts ──
export enum MessageType {
  // 通知
  NOTIFICATION = 'notification',
  ORDER_STATUS_CHANGE = 'order.status.change',
  INVENTORY_CHANGE = 'inventory.change',

  // 数据同步
  DATA_SYNC = 'data.sync',
  PERMISSION_CHANGE = 'permission.change',
  CONFIG_CHANGE = 'config.change',

  // 聊天
  CHAT_MESSAGE = 'chat.message',
  CHAT_TYPING = 'chat.typing',

  // 系统
  SYSTEM_ANNOUNCEMENT = 'system.announcement',
  HEARTBEAT = 'heartbeat',
}

// ── 消息路由 ──
export function setupMessageRouter() {
  const router: Record<string, (payload: unknown) => void> = {
    [MessageType.INVENTORY_CHANGE]: (payload) => {
      // 库存变更时刷新列表
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
    },
    [MessageType.PERMISSION_CHANGE]: () => {
      // 权限变更时重新加载
      usePermissionStore().refreshPermissions()
    },
    [MessageType.NOTIFICATION]: (payload) => {
      // 显示通知
      const notification = payload as NotificationPayload
      ElNotification({
        title: notification.title,
        message: notification.content,
        type: notification.level,
      })
    },
  }

  wsManager.subscribe('/exchange/kubo.notification', (msg) => {
    const typed = msg as { type: MessageType; payload: unknown }
    router[typed.type]?.(typed.payload)
  })
}
```

---

## 9. 权限引擎设计

### 9.1 权限引擎架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                   前端权限引擎架构                                    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  权限数据源                                                    │
│  │  ├─ permissionStore (Pinia)                                    │   │
│  │  │   ├─ roles: string[] (用户角色列表)                         │   │
│  │  │   ├─ permissions: string[] (权限标识列表)                    │   │
│  │  │   ├─ menuTree: MenuItem[] (菜单树)                         │   │
│  │  │   ├─ dataScopes: DataScopeMap (数据权限范围)                │   │
│  │  │   └─ fieldPolicies: FieldPolicyMap (字段脱敏策略)           │   │
│  │  └─ 缓存策略: 登录时加载 → 权限变更时刷新                      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                             │                                        │
│          ┌──────────────────┼──────────────────┐                    │
│          ▼                  ▼                  ▼                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐              │
│  │ 路由权限      │  │ 视图权限      │  │ 数据权限      │              │
│  │              │  │              │  │              │              │
│  │ 路由守卫检查  │  │ v-permission │  │ 字段脱敏指令  │              │
│  │ 动态路由生成  │  │ 按钮级控制   │  │ 数据过滤     │              │
│  │ 菜单过滤     │  │ 元素显隐     │  │ 操作禁用     │              │
│  └──────────────┘  └──────────────┘  └──────────────┘              │
│                                                                      │
│  3 层检查:                                                          │
│  1. 路由层: 未授权 → 403 页面                                      │
│  2. 组件层: 无权限 → 隐藏按钮/元素                                  │
│  3. API 层: 后端二次验证 (最终防线)                                 │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 路由权限控制

```typescript
// ── src/guard/permissionGuard.ts ──
import type { Router } from 'vue-router'
import { usePermissionStore } from '@/stores/permission'
import { useUserStore } from '@/stores/user'

export function initPermissionGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    // 未登录 → 登录页
    if (!userStore.isLoggedIn && to.path !== '/login') {
      return next('/login')
    }

    // 已登录, 加载权限
    if (userStore.isLoggedIn && !permissionStore.loaded) {
      await permissionStore.loadPermissions()
    }

    // 路由权限检查
    const requiredPerm = to.meta?.permission as string
    if (requiredPerm && !userStore.hasPermission(requiredPerm)) {
      return next('/403')
    }

    // 动态路由: 根据权限过滤菜单
    if (to.meta?.dynamicMenu) {
      const filteredRoutes = filterRoutesByPermission(
        to.matched,
        userStore.permissions,
      )
      if (filteredRoutes.length === 0) {
        return next('/403')
      }
    }

    next()
  })
}

// ── src/directives/permission.ts ──
import type { Directive } from 'vue'

export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const userStore = useUserStore()
    const permissions = Array.isArray(binding.value)
      ? binding.value
      : [binding.value]

    const hasPermission = permissions.some(p => userStore.hasPermission(p))
    if (!hasPermission) {
      el.parentNode?.removeChild(el)  // 无权限直接移除元素
    }
  },
}

// ── 使用示例 ──
// <template>
//   <el-button v-permission="'wms:inventory:create'">新建</el-button>
//   <el-button v-permission="['wms:inventory:export', 'wms:inventory:admin']">导出</el-button>
// </template>
```

### 9.3 字段脱敏指令

```typescript
// ── src/directives/mask.ts ──
import type { Directive } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export type MaskType = 'phone' | 'idCard' | 'bankCard' | 'email' | 'custom'

export const vMask: Directive<HTMLElement, {
  type: MaskType
  value: string
  entity?: string
  field?: string
  fallback?: string
}> = {
  mounted(el, binding) {
    const { type, value, entity, field, fallback = '****' } = binding.value

    // 检查列级权限
    if (entity && field) {
      const permissionStore = usePermissionStore()
      const policy = permissionStore.getFieldPolicy(entity, field)
      if (policy?.visible) {
        el.textContent = value  // 可见
        return
      }
    }

    // 执行脱敏
    el.textContent = maskValue(value, type) ?? fallback
  },
}

function maskValue(value: string, type: MaskType): string {
  switch (type) {
    case 'phone':
      return value.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
    case 'idCard':
      return value.replace(/(\d{6})\d{8}(\d{4})/, '$1********$2')
    case 'bankCard':
      return value.replace(/(\d{4})\d{8}(\d{4})/, '$1 **** **** $2')
    case 'email':
      return value.replace(/(.{1}).*(@.*)/, '$1***$2')
    default:
      return value
  }
}

// ── 使用示例 ──
// <span v-mask="{ type: 'phone', value: user.phone, entity: 'User', field: 'phone' }">
//   {{ user.phone }}
// </span>
```

---

## 10. 模块化与路由设计

### 10.1 目录结构规划

```
src/
├── api/                          # API 层
│   ├── request.ts                # Axios 实例
│   ├── queryClient.ts            # @tanstack/vue-query 配置
│   ├── errorHandler.ts           # 全局错误处理
│   ├── tokenRefresh.ts           # Token 刷新逻辑
│   ├── generated/                # 自动生成的 API 类型
│   │   ├── models/
│   │   └── services/
│   └── services/                 # 手动封装的业务 API
│       ├── inventory.api.ts
│       ├── order.api.ts
│       └── ...
│
├── stores/                       # Pinia 状态
│   ├── user.store.ts
│   ├── permission.store.ts
│   ├── app.store.ts
│   └── tenant.store.ts
│
├── composables/                  # 可复用组合式函数
│   ├── useTable.ts               # 表格通用逻辑
│   ├── useForm.ts                # 表单通用逻辑
│   ├── usePagination.ts          # 分页逻辑
│   └── useExport.ts              # 导出逻辑
│
├── websocket/                    # WebSocket 管理
│   ├── manager.ts                # 连接管理器
│   ├── messageTypes.ts           # 消息类型定义
│   └── handlers/                 # 消息处理器
│       ├── inventoryHandler.ts
│       └── notificationHandler.ts
│
├── directives/                   # 自定义指令
│   ├── permission.ts             # v-permission 权限指令
│   └── mask.ts                   # v-mask 脱敏指令
│
├── guards/                       # 路由守卫
│   ├── authGuard.ts              # 认证守卫
│   └── permissionGuard.ts        # 权限守卫
│
├── components/                   # 公共组件
│   ├── common/                   # 通用组件
│   │   ├── SvgIcon.vue
│   │   ├── Table.vue             # 虚拟滚动表格
│   │   ├── Form.vue              # 动态表单
│   │   └── ErrorBoundary.vue     # 错误边界
│   ├── business/                 # 业务组件
│   │   ├── InventoryCard.vue
│   │   └── OrderStatus.vue
│   └── layout/                   # 布局组件
│       ├── AppLayout.vue
│       ├── Sidebar.vue
│       └── Navbar.vue
│
├── views/                        # 页面视图
│   ├── login.vue
│   ├── home.vue
│   ├── console/                  # 控制台页面
│   │   ├── wms/
│   │   ├── mes/
│   │   └── ...
│   └── error.vue
│
├── i18n/                         # 国际化
│   ├── index.ts
│   ├── lang/
│   │   ├── zh-CN.ts
│   │   └── en-US.ts
│   └── locales/                  # 按模块拆分的语言包
│       ├── wms/
│       └── mes/
│
├── utils/                        # 工具函数
│   ├── format.ts                 # 格式化
│   ├── validate.ts               # 验证
│   └── export.ts                 # 导出
│
├── worker/                       # WebWorker
│   ├── export.worker.ts          # 导出 Worker
│   └── calculation.worker.ts     # 计算 Worker
│
├── pwa/                          # PWA 配置
│   └── registerSW.ts
│
├── router/                       # 路由配置
│   ├── index.ts
│   └── routes/
│       ├── wms.routes.ts
│       └── mes.routes.ts
│
├── main.ts                       # 入口
└── App.vue                       # 根组件
```

### 10.2 路由配置

```typescript
// ── src/router/index.ts ──
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login.vue'),
    meta: { guest: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/home',
      },
      {
        path: 'home',
        name: 'Home',
        component: () => import('@/views/home.vue'),
        meta: { title: '首页', icon: 'HomeFilled' },
      },
      // 业务模块路由 (懒加载)
      {
        path: 'wms',
        name: 'WMS',
        redirect: '/wms/inventory',
        meta: { title: '仓储管理', permission: 'wms:access' },
        children: [
          {
            path: 'inventory',
            name: 'InventoryList',
            component: () => import('@/views/console/wms/inventory/InventoryList.vue'),
            meta: { title: '库存管理', permission: 'wms:inventory:read' },
          },
          {
            path: 'input',
            name: 'InputList',
            component: () => import('@/views/console/wms/input/InputList.vue'),
            meta: { title: '入库管理', permission: 'wms:input:read' },
          },
          // ...
        ],
      },
      // 其他模块...
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
  },
]

export function createAppRouter() {
  return createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior: () => ({ top: 0 }),
  })
}
```

### 10.3 模块拆分策略

```
┌─────────────────────────────────────────────────────────────────────┐
│                   模块拆分与代码分割策略                               │
│                                                                      │
│  路由懒加载: 每个页面组件独立 chunk                                   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  dist/assets/                                                │   │
│  │  ├─ index.[hash].js          (App Shell, 150KB)              │   │
│  │  ├─ vendor.[hash].js         (Element Plus, 200KB)           │   │
│  │  ├─ Login.[hash].js          (登录页, 20KB)                  │   │
│  │  ├─ Home.[hash].js           (首页, 30KB)                    │   │
│  │  ├─ WMS-Inventory.[hash].js  (库存管理, 50KB)                │   │
│  │  ├─ WMS-Input.[hash].js      (入库管理, 40KB)                │   │
│  │  ├─ MES-Order.[hash].js      (工单管理, 60KB)                │   │
│  │  └─ ...                     (按模块拆分, 每个 20-80KB)       │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  预加载策略:                                                        │
│  - 登录后预加载首屏模块 (WMS/MES 首页)                               │
│  - 用户悬停在菜单时预加载对应模块                                    │
│  - 空闲时预加载所有模块 (requestIdleCallback)                        │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 11. UI 组件体系

### 11.1 组件分层

```
┌─────────────────────────────────────────────────────────────────────┐
│                   组件分层架构                                        │
│                                                                      │
│  第 1 层: 基础组件 (Element Plus)                                   │
│  ├─ el-button, el-table, el-form, el-input, ...                     │
│  └─ 全局样式覆盖 (主题定制)                                         │
│                                                                      │
│  第 2 层: 通用业务组件                                               │
│  ├─ KuboTable.vue              (虚拟滚动 + 列配置 + 导出)           │
│  ├─ KuboForm.vue               (动态表单 + 验证 + 联动)            │
│  ├─ KuboSearch.vue             (高级搜索 + 条件组合)               │
│  ├─ KuboCard.vue               (统计卡片 + 趋势图)                 │
│  ├─ KuboTree.vue               (树形 + 懒加载 + 拖拽)              │
│  ├─ KuboUpload.vue             (文件上传 + 进度 + 预览)            │
│  ├─ KuboEditor.vue             (富文本 + Markdown)                 │
│  └─ KuboErrorBoundary.vue      (错误边界 + 降级 UI)                │
│                                                                      │
│  第 3 层: 业务组件                                                   │
│  ├─ InventoryCard.vue          (库存卡片: 数量/位置/状态)          │
│  ├─ OrderStatusTag.vue         (订单状态: 颜色/图标/文字)          │
│  ├─ MaterialSelector.vue       (物料选择器: 搜索/树形/分页)        │
│  ├─ WarehousePicker.vue        (仓库选择器: 树形/权限过滤)         │
│  ├─ BOMTree.vue                (BOM 树: 层级/展开/对比)            │
│  └─ DashboardChart.vue         (看板图表: ECharts 封装)            │
│                                                                      │
│  第 4 层: 页面组件                                                   │
│  ├─ views/console/wms/inventory/InventoryList.vue                  │
│  ├─ views/console/wms/inventory/InventoryDetail.vue                │
│  └─ ...                                                             │
└─────────────────────────────────────────────────────────────────────┘
```

### 11.2 虚拟滚动表格组件

```vue
<!-- ── src/components/common/KuboTable.vue (简化) ── -->
<template>
  <div ref="containerRef" class="kubo-table" :style="{ height: height + 'px' }">
    <RecycleScroller
      :items="data"
      :item-size="rowHeight"
      key-field="id"
      v-slot="{ item }"
    >
      <div class="kubo-table-row" :class="{ selected: selectedId === item.id }" @click="$emit('rowClick', item)">
        <slot name="row" :item="item">
          <div v-for="col in columns" :key="col.prop" class="kubo-table-cell" :style="{ width: col.width + 'px' }">
            <!-- 脱敏字段 -->
            <span v-if="col.mask" v-mask="{ type: col.mask, value: item[col.prop], entity: entity, field: col.prop }">
              {{ item[col.prop] }}
            </span>
            <template v-else>
              {{ item[col.prop] }}
            </template>
          </div>
        </slot>
      </div>
    </RecycleScroller>
  </div>
</template>

<script setup lang="ts">
import { RecycleScroller } from 'vue-virtual-scroller'
import 'vue-virtual-scroller/dist/vue-virtual-scroller.css'

interface Column {
  prop: string
  label: string
  width: number
  mask?: 'phone' | 'idCard' | 'bankCard' | 'email'
  sortable?: boolean
}

defineProps<{
  data: Record<string, unknown>[]
  columns: Column[]
  height: number
  rowHeight?: number
  selectedId?: string | number
  entity?: string  // 用于列级权限
}>()

defineEmits<{
  rowClick: [item: Record<string, unknown>]
}>()
</script>
```

### 11.3 主题定制

```scss
// ── src/assets/css/variables.scss (Element Plus 主题变量覆盖) ──
@forward 'element-plus/theme-chalk/src/common/var.scss' with (
  $colors: (
    'primary': (
      'base': #409eff,
    ),
    'success': (
      'base': #67c23a,
    ),
    'warning': (
      'base': #e6a23c,
    ),
    'danger': (
      'base': #f56c6c,
    ),
    'info': (
      'base': #909399,
    ),
  ),
  $border-radius: (
    'base': 6px,
    'small': 4px,
    'round': 20px,
  ),
  $font-size: (
    'extra-large': 20px,
    'large': 18px,
    'medium': 16px,
    'base': 14px,
    'small': 12px,
    'extra-small': 10px,
  ),
);
```

---

## 12. 大数据渲染优化

### 12.1 优化策略矩阵

| 场景 | 优化方案 | 预期效果 | 适用场景 |
|------|---------|---------|----------|
| **大表格 (1000+ 行)** | 虚拟滚动 (RecycleScroller) | 渲染节点 < 50, 滚动流畅 | 库存列表、出入库记录 |
| **大表单 (100+ 字段)** | 分段渲染 + 懒加载 | 首屏加载 < 200ms | 工单创建、物料配置 |
| **大图表** | ECharts 采样 + Canvas | 渲染 < 500ms | Dashboard 看板 |
| **树形数据 (5000+ 节点)** | 懒加载 + 虚拟树 | 展开/折叠 < 100ms | BOM 树、组织架构树 |
| **大量 DOM 操作** | requestAnimationFrame 批量更新 | 不掉帧 | 实时数据更新 |
| **复杂计算** | WebWorker 离线程 | 不阻塞主线程 | 导出、数据聚合 |

### 12.2 虚拟滚动实现

```vue
<!-- ── 使用虚拟滚动表格 ── -->
<template>
  <kubo-table
    :data="inventoryList"
    :columns="columns"
    :height="tableHeight"
    :row-height="48"
    entity="Inventory"
    @row-click="handleRowClick"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useInventoryList } from '@/api/services/inventory.api'
import KuboTable from '@/components/common/KuboTable.vue'

const query = reactive({
  page: 1,
  size: 1000,  // 一次加载 1000 条
  warehouseId: null,
})

// 使用 @tanstack/vue-query 缓存
const { data, isLoading } = useInventoryList(toRef(query))

const inventoryList = computed(() => data.value?.records ?? [])
const tableHeight = computed(() => window.innerHeight - 200)

const columns = [
  { prop: 'materialCode', label: '物料编码', width: 150 },
  { prop: 'materialName', label: '物料名称', width: 200 },
  { prop: 'warehouseName', label: '仓库', width: 120 },
  { prop: 'availableQty', label: '可用数量', width: 120, sortable: true },
  { prop: 'lockedQty', label: '锁定数量', width: 120 },
  { prop: 'unit', label: '单位', width: 80 },
  { prop: 'phone', label: '联系电话', width: 150, mask: 'phone' },
]
</script>
```

### 12.3 WebWorker 导出

```typescript
// ── src/worker/export.worker.ts ──
self.onmessage = async (event) => {
  const { data, columns, format } = event.data

  // 在 Worker 中处理大量数据
  const workbook = await generateExcel(data, columns, format)

  // 发送回主线程
  self.postMessage({ workbook })
}

async function generateExcel(data: unknown[], columns: Column[], format: string) {
  // 大量数据处理, 不阻塞主线程
  // ...
  return workbook
}

// ── 主线程调用 ──
export function useExport() {
  const exportLoading = ref(false)

  async function exportData(data: unknown[], columns: Column[]) {
    exportLoading.value = true

    const worker = new Worker(new URL('@/worker/export.worker.ts', import.meta.url))

    worker.postMessage({ data, columns, format: 'xlsx' })

    worker.onmessage = (event) => {
      const workbook = event.data.workbook
      // 下载文件
      downloadExcel(workbook)
      exportLoading.value = false
      worker.terminate()
    }

    worker.onerror = (error) => {
      console.error('Export worker error:', error)
      exportLoading.value = false
      worker.terminate()
      ElMessage.error('导出失败')
    }
  }

  return { exportData, exportLoading }
}
```

---

## 13. 国际化方案

### 13.1 国际化架构

```typescript
// ── src/i18n/index.ts ──
import { createI18n } from 'vue-i18n'

// 按模块拆分语言包
const messages = {
  'zh-CN': {
    common: {},       // 通用
    login: {},        // 登录
    wms: {},          // 仓储
    mes: {},          // 生产
    factory: {},      // 工厂
    finance: {},      // 财务
    system: {},       // 系统
    validation: {},   // 验证消息
    error: {},        // 错误信息
  },
  'en-US': {
    common: {},
    login: {},
    wms: {},
    mes: {},
    // ...
  },
}

export function detectLocale(): string {
  const saved = localStorage.getItem('kubo:locale')
  if (saved) return saved

  const browserLang = navigator.language
  if (browserLang.startsWith('zh')) return 'zh-CN'
  return 'en-US'
}

export const i18nConfig = {
  locale: detectLocale(),
  fallbackLocale: 'zh-CN',
  messages,
  legacy: false,
  globalInjection: true,
}

// ── 使用示例 ──
// <template>
//   <h1>{{ $t('wms.inventory.title') }}</h1>
//   <el-button>{{ $t('common.create') }}</el-button>
// </template>
// <script setup>
// import { useI18n } from 'vue-i18n'
// const { t } = useI18n()
// console.log(t('validation.required', { field: '物料编码' }))
// </script>
```

### 13.2 语言包按需加载

```typescript
// ── 按模块异步加载语言包 ──
async function loadLocaleMessages(locale: string, module: string) {
  const messages = await import(`./locales/${locale}/${module}.ts`)
  i18n.global.mergeLocaleMessage(locale, messages.default)
}

// 进入 WMS 模块时加载
router.beforeEach(async (to) => {
  if (to.path.startsWith('/wms')) {
    await loadLocaleMessages(i18n.global.locale.value, 'wms')
    await loadLocaleMessages('en-US', 'wms')
  }
})
```

---

## 14. 构建与部署优化

### 14.1 Vite 配置改造

```typescript
// ── vite.config.mts (改造后) ──
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { compression } from 'vite-plugin-compression'
import { VitePWA } from 'vite-plugin-pwa'
import { visualizer } from 'vite-plugin-visualizer'

export default defineConfig({
  resolve: {
    alias: {
      '@': '/src/',
    },
  },

  build: {
    target: 'es2020',
    // 修复 keep_classnames BUG → 使用 esbuild
    minify: 'esbuild',
    cssMinify: 'esbuild',
    // 代码分割
    rollupOptions: {
      output: {
        manualChunks: {
          'vendor-vue': ['vue', 'vue-router', 'pinia', 'vue-i18n'],
          'vendor-element': ['element-plus', '@element-plus/icons-vue'],
          'vendor-echarts': ['echarts'],
          'vendor-query': ['@tanstack/vue-query'],
          'vendor-stomp': ['@stomp/stompjs', 'sockjs-client'],
        },
      },
    },
    // 开启 sourcemap (仅 production 时可选)
    sourcemap: false,
    // 提高构建速度
    chunkSizeWarningLimit: 500,
  },

  plugins: [
    vue(),
    vueDevTools(),

    // Gzip/Brotli 压缩
    compression({
      algorithm: 'gzip',
      threshold: 10240,     // 10KB 以上文件压缩
      deleteOriginFile: false,
    }),
    compression({
      algorithm: 'brotliCompress',
      threshold: 10240,
      deleteOriginFile: false,
    }),

    // PWA 支持
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.ico'],
      manifest: {
        name: 'Kubo 智能生产管理系统',
        short_name: 'Kubo',
        description: '智能生产管理系统',
        theme_color: '#409eff',
        background_color: '#ffffff',
        display: 'standalone',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg,woff2}'],
        runtimeCaching: [
          {
            // API 请求缓存 (NetworkFirst)
            urlPattern: /^\/api\/.*/i,
            handler: 'NetworkFirst',
            options: {
              cacheName: 'kubo-api-cache',
              expiration: { maxEntries: 100, maxAgeSeconds: 300 },
            },
          },
        ],
      },
    }),

    // 构建分析 (可选)
    visualizer({
      open: false,
      filename: 'dist/stats.html',
    }),
  ],

  server: {
    open: true,
    host: '0.0.0.0',
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        rewrite: path => path.replace(/^\/api/, ''),
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://127.0.0.1:8080',
        ws: true,
      },
    },
  },
})
```

### 14.2 构建优化效果

```
┌─────────────────────────────────────────────────────────────────────┐
│                   构建优化前后对比                                    │
│                                                                      │
│  指标               改造前           改造后         优化幅度          │
│  ───────────────────────────────────────────────────────────────    │
│  总包体积 (未压缩)   ~5MB           ~1.5MB         -70%             │
│  总包体积 (gzip)     ~1.5MB         ~500KB         -67%             │
│  App Shell           ~2MB           ~400KB         -80%             │
│  首屏 JS             全部加载        按需加载        -60%+            │
│  CSS 体积           ~500KB          ~200KB         -60%             │
│  Chunk 数量          1              20+            代码分割          │
│  构建时间            ~30s           ~15s           -50%             │
│  FCP (首屏)          ~3s            <1.2s          -60%             │
│  TTI (可交互)        ~5s            <2.0s          -60%             │
│  LCP (最大内容)      ~4s            <1.5s          -63%             │
│                                                                      │
│  优化手段:                                                          │
│  - esbuild minify (替代 terser, 速度提升 10x)                       │
│  - 代码分割 (manualChunks)                                          │
│  - 路由懒加载 (每个页面独立 chunk)                                   │
│  - 预加载 (preload + prefetch)                                      │
│  - Gzip/Brotli 压缩                                                 │
│  - 移除无用 CSS (PurgeCSS)                                          │
│  - 图片优化 (WebP + 懒加载)                                         │
└─────────────────────────────────────────────────────────────────────┘
```

### 14.3 Docker 部署

```dockerfile
# ── Dockerfile ──
FROM node:20-alpine AS builder
WORKDIR /app
COPY package.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

```nginx
# ── nginx.conf (前端部署) ──
server {
    listen 80;
    server_name kubo.example.com;

    root /usr/share/nginx/html;
    index index.html;

    # Gzip 压缩
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml application/xml+rss text/javascript image/svg+xml;
    gzip_min_length 10240;
    gzip_comp_level 6;

    # 静态资源缓存 (1 年)
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # Service Worker 不缓存
    location /sw.js {
        add_header Cache-Control "no-cache";
    }

    # SPA 路由: 所有请求指向 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 安全头
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
}
```

---

## 15. PWA 与离线能力

### 15.1 PWA 功能清单

| 功能 | 实现方式 | 效果 |
|------|---------|------|
| **可安装** | Web App Manifest | 用户可将应用添加到桌面 |
| **离线缓存** | Service Worker (Cache-first) | 离线时显示上次访问的页面 |
| **预加载** | Service Worker 预缓存 | 用户访问前预加载模块 |
| **后台同步** | Background Sync API | 离线创建的数据, 恢复后自动提交 |
| **推送通知** | Push API + Notification | 新消息/订单通知 |
| **自动更新** | Service Worker 自动更新 | 有新版本时静默更新 |

### 15.2 PWA 配置

```typescript
// ── src/pwa/registerSW.ts ──
import { registerSW } from 'virtual:pwa-register'

export function registerServiceWorker() {
  const updateSW = registerSW({
    onNeedRefresh() {
      // 有新版本可用, 提示用户更新
      ElNotification({
        title: '发现新版本',
        message: '点击刷新以获取最新版本',
        type: 'info',
        duration: 0,
        showClose: true,
        onClick: () => updateSW(true),
      })
    },
    onOfflineReady() {
      console.log('App ready for offline use')
    },
    onRegistered(swRegistration) {
      // 定期检查更新 (每 30 分钟)
      setInterval(() => {
        swRegistration?.update()
      }, 30 * 60 * 1000)
    },
  })
}
```

---

## 16. 测试与质量保障

### 16.1 测试策略

```
┌─────────────────────────────────────────────────────────────────────┐
│                   测试金字塔 (Kubo 前端)                              │
│                                                                      │
│                    ╱╲                                                │
│                   ╱  ╲                    E2E 测试 (5%)              │
│                  ╱    ╲                   Playwright                │
│                 ╱      ╲                 核心流程: 登录/CRUD/导出    │
│                ╱────────╲                                           │
│               ╱          ╲             集成测试 (15%)               │
│              ╱            ╲            @vue/test-utils              │
│             ╱              ╲          组件交互: 表单/表格/权限      │
│            ╱────────────────╲                                        │
│           ╱                  ╲      单元测试 (80%)                   │
│          ╱                    ╲     Vitest                          │
│         ╱                      ╲    Store / Utils / API Hooks       │
│        ╱────────────────────────╲                                    │
│                                                                      │
│  测试工具:                                                          │
│  - Vitest: 单元测试 + 组件测试                                      │
│  - @vue/test-utils: Vue 组件挂载/交互                               │
│  - Playwright: E2E 浏览器测试                                       │
│  - c8/istanbul: 覆盖率报告                                          │
│                                                                      │
│  覆盖率目标:                                                        │
│  - 行覆盖率: ≥ 80%                                                  │
│  - 分支覆盖率: ≥ 70%                                                │
│  - 函数覆盖率: ≥ 80%                                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 16.2 测试配置

```typescript
// ── vitest.config.ts ──
import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./tests/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      include: ['src/**/*.{ts,vue}'],
      exclude: [
        'src/**/*.d.ts',
        'src/api/generated/**',
        'src/main.ts',
      ],
      thresholds: {
        lines: 80,
        branches: 70,
        functions: 80,
      },
    },
  },
})
```

---

## 17. 性能目标与指标

### 17.1 核心性能指标

| 指标 | 当前值 | 目标值 | 测量方式 | 优先级 |
|------|--------|--------|----------|--------|
| **FCP (首屏内容渲染)** | ~3s | < 1.2s | Lighthouse | 🔴 P0 |
| **TTI (可交互时间)** | ~5s | < 2.0s | Lighthouse | 🔴 P0 |
| **LCP (最大内容渲染)** | ~4s | < 1.5s | Lighthouse | 🔴 P0 |
| **CLS (累积布局偏移)** | > 0.1 | < 0.05 | Lighthouse | 🟡 P1 |
| **TBT (总阻塞时间)** | > 500ms | < 200ms | Lighthouse | 🟡 P1 |
| **SI (速度指数)** | > 4s | < 2s | Lighthouse | 🟡 P1 |
| **Bundle (gzip)** | ~1.5MB | < 800KB | 构建分析 | 🔴 P0 |
| **App Shell (gzip)** | ~600KB | < 200KB | 构建分析 | 🔴 P0 |
| **首屏 JS 体积** | 全部加载 | < 150KB | 构建分析 | 🔴 P0 |
| **1000 行表格渲染** | > 1000ms | < 150ms | Performance API | 🟡 P1 |
| **页面切换时间** | > 500ms | < 200ms | 性能监控 | 🟡 P1 |
| **内存占用 (峰值)** | > 200MB | < 100MB | Chrome DevTools | 🟢 P2 |
| **Lighthouse 得分** | ~50 | > 90 | Lighthouse CI | 🔴 P0 |

### 17.2 性能监控方案

```typescript
// ── src/utils/performance.ts ──
export function reportWebVitals() {
  // 使用 PerformanceObserver 监听 Core Web Vitals
  if ('PerformanceObserver' in window) {
    // LCP
    new PerformanceObserver((list) => {
      const entries = list.getEntries()
      if (entries.length > 0) {
        const lcp = entries[entries.length - 1]
        console.log('LCP:', lcp.startTime)
        // 上报到监控系统
        reportMetric('lcp', lcp.startTime)
      }
    }).observe({ type: 'largest-contentful-paint', buffered: true })

    // FID
    new PerformanceObserver((list) => {
      list.getEntries().forEach((entry) => {
        console.log('FID:', entry.processingStart - entry.startTime)
        reportMetric('fid', entry.processingStart - entry.startTime)
      })
    }).observe({ type: 'first-input', buffered: true })

    // CLS
    new PerformanceObserver((list) => {
      let cls = 0
      list.getEntries().forEach((entry) => {
        if (!entry.hadRecentInput) {
          cls += (entry as LayoutShift).value
        }
      })
      console.log('CLS:', cls)
      reportMetric('cls', cls)
    }).observe({ type: 'layout-shift', buffered: true })
  }
}
```

---

## 18. 分阶段实施计划

### 18.1 实施总计划 (16 周)

```
周次      1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16
          │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │
Phase 0   ████████████
基础设施   ████████████
+ 骨架链路 ████████████

Phase 1                  ██████████████████████████
核心业务                  ██████████████████████████

Phase 2                                    ████████████████████████
渲染优化                                    ████████████████████████

Phase 3                                                      ████████████████
工程化质量                                                    ████████████████

Phase 4                                                            ████████████
灰度发布                                                            ████████████
```

### 18.2 各阶段任务

#### Phase 0: 基础设施 + 骨架链路 (Week 1-2)

| 任务 | 描述 | 产出 |
|------|------|------|
| 0.1 | 目录结构调整 | 新目录结构落地 |
| 0.2 | Pinia 集成 + userStore | 状态管理可运行 |
| 0.3 | @tanstack/vue-query 集成 | 缓存管理可运行 |
| 0.4 | Axios 封装改造 | 统一请求层 |
| 0.5 | 路由改造 + 路由守卫 | 权限路由可运行 |
| 0.6 | 构建优化 (压缩 + 代码分割) | 生产包体积减少 50%+ |
| 0.7 | PWA 基础配置 | Service Worker 可注册 |
| 0.8 | 登录/首页重构 | 首屏加载 < 1.5s |
| 0.9 | ESLint + TypeScript 严格模式 | 代码规范落地 |

#### Phase 1: 核心业务模块 (Week 3-8)

| 任务 | 模块 | 工作量 |
|------|------|--------|
| 1.1 | WMS 模块重构 (库存/出入库/移库) | 3 周 |
| 1.2 | MES 模块重构 (工单/BOM/排产) | 3 周 |
| 1.3 | Factory 模块重构 (仓库/车间/储位) | 2 周 |
| 1.4 | 权限引擎 (v-permission + 路由权限) | 1 周 |
| 1.5 | WebSocket 管理器 | 1 周 |
| 1.6 | 字段脱敏指令 (v-mask) | 0.5 周 |

#### Phase 2: 渲染优化 + 业务补全 (Week 7-12)

| 任务 | 描述 | 工作量 |
|------|------|--------|
| 2.1 | 虚拟滚动表格组件 | 1 周 |
| 2.2 | 大表格/大数据渲染优化 | 1 周 |
| 2.3 | 图表优化 (ECharts 采样 + 懒加载) | 1 周 |
| 2.4 | 首屏加载优化 (骨架屏 + 预加载) | 0.5 周 |
| 2.5 | 剩余模块重构 (Asset/Channel/Open/Wechat/Chat) | 3 周 |
| 2.6 | WebWorker 导出 | 0.5 周 |

#### Phase 3: 工程化质量 (Week 10-14)

| 任务 | 描述 | 工作量 |
|------|------|--------|
| 3.1 | 国际化 (vue-i18n) | 1 周 |
| 3.2 | 单元测试 (Vitest) | 2 周 |
| 3.3 | E2E 测试 (Playwright) | 1 周 |
| 3.4 | 性能监控 + 错误监控 | 1 周 |
| 3.5 | 通用业务组件库 | 2 周 |
| 3.6 | 文档 + 组件 Storybook | 1 周 |

#### Phase 4: 灰度发布 (Week 12-16)

| 任务 | 描述 | 工作量 |
|------|------|--------|
| 4.1 | 兼容层 (@airpower/web 逐步剥离) | 2 周 |
| 4.2 | 灰度策略配置 | 1 周 |
| 4.3 | 性能 Benchmark | 1 周 |
| 4.4 | 问题修复 + 优化 | 2 周 |
| 4.5 | 全量切换 | 1 周 |

### 18.3 兼容策略

```
┌─────────────────────────────────────────────────────────────────────┐
│                   @airpower/web 逐步剥离策略                         │
│                                                                      │
│  Step 1: 共存期 (Phase 0-1)                                         │
│  ├─ 保留 @airpower/web 依赖                                         │
│  ├─ 新代码使用新的架构 (Pinia + @tanstack/vue-query)                │
│  └─ 旧组件逐渐迁移                                                  │
│                                                                      │
│  Step 2: 迁移期 (Phase 2-3)                                         │
│  ├─ 使用兼容层包装 @airpower/web 功能                                │
│  ├─ 逐步替换 RouterUtil 为原生 vue-router                           │
│  └─ 逐步替换 WebConfig 为 Pinia store                               │
│                                                                      │
│  Step 3: 剥离期 (Phase 4)                                           │
│  ├─ 确认所有功能已迁移                                              │
│  ├─ 移除 @airpower/web 依赖                                         │
│  └─ 清理兼容层代码                                                  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 附录 A: 关键架构决策记录

| 决策编号 | 决策 | 理由 | 状态 |
|---------|------|------|------|
| F-ADR-001 | 采用 Pinia 3.x 替代框架内置状态管理 | 独立可控, 支持持久化, 开发体验好 | ✅ 已确认 |
| F-ADR-002 | 采用 @tanstack/vue-query 5.x 管理服务端状态 | 自动缓存/去重/乐观更新, 减少 80% 重复请求 | ✅ 已确认 |
| F-ADR-003 | 采用 STOMP 1.2 协议替代原生 WebSocket | 集群兼容, 支持 RabbitMQ 集成, 自动重连 | ✅ 已确认 |
| F-ADR-004 | 采用虚拟滚动处理大数据表格 | 1000+ 行渲染 < 150ms, 内存占用减少 90% | ✅ 已确认 |
| F-ADR-005 | 采用 vue-i18n v9 实现国际化 | 按需加载, 支持动态切换, 生态好 | ✅ 已确认 |
| F-ADR-006 | 采用 vite-plugin-pwa 实现 PWA | 离线可用, 预加载, 后台同步 | ✅ 已确认 |
| F-ADR-007 | 逐步剥离 @airpower/web 依赖 | 解耦框架限制, 回归标准生态 | ✅ 已确认 |
| F-ADR-008 | 采用 eslint + TypeScript 严格模式 | 代码质量, 减少运行时错误 | ✅ 已确认 |

## 附录 B: 参考文档

- [Kubo 平台-前端高并发重构实施路线规划计划.md](../Kubo%20平台-前端高并发重构实施路线规划计划.md)
- [前后端重构规划差距分析报告.md](../前后端重构规划差距分析报告.md)
- [Kubo 平台后端系统二次改造设计文档](./Kubo%20平台后端系统二次改造设计文档.md)
- [SECURITY_GUIDE.md](../05-Development/SECURITY_GUIDE.md)
- [TESTING_STRATEGY.md](../05-Development/TESTING_STRATEGY.md)

---

> **文档版本**: v2.0  
> **最后更新**: 2026-08-13  
> **审批**: 待定
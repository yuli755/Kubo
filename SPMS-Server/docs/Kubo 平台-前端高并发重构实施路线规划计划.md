# Kubo 平台-前端高并发重构实施路线规划计划

> **文档版本**：v1.0  
> **适用项目**：Kubo 智能生产管理系统 Web 端 (原 SPMS-Web)  
> **目标规模**：10,000+ 并发用户、7×24 小时生产环境、多工厂多租户、复杂交互场景  
> **核心技术栈**：Vue 3.5+ (Composition API) + TypeScript 5.9+ + Vite 7+ + **Pinia 3+** + Element Plus 2.12+ + **@airpower/web 2.9+** + Vue Router 4.6+ + ECharts 6+  
> **配套后端**：`kubo-{domain}` 微服务集群 (Spring Cloud Alibaba + MyBatis-Plus + RabbitMQ + MySQL 8.0 InnoDB Cluster + Redis Cluster)

---

## 1. 现状分析与痛点识别

### 1.1 当前技术栈评估

| 领域 | 现状 | 评级 | 高并发风险 |
|------|------|------|------------|
| **框架核心** | Vue 3.5 + TS 5.9 + Vite 7 | ✅ 优秀 | 版本新、生态活、性能强 |
| **状态管理** | Pinia 3.0 | ✅ 优秀 | 模块化、TS 支持好、DevTools 友好 |
| **UI 组件库** | Element Plus 2.12 + @airpower/web 2.9 | ⚠️ 良好 | 组件重、按需引入未彻底、虚拟滚动未用 |
| **构建工具** | Vite 7 (minify: false) | ❌ 风险 | **生产构建未压缩**，包体积极大、加载慢 |
| **路由** | Vue Router 4.6 | ✅ 优秀 | 懒加载已启用 (`() => import()`) |
| **HTTP 客户端** | Axios 1.13 (airpower 封装) | ⚠️ 良好 | 无请求取消、重试、去抖、缓存策略 |
| **实时通信** | airpower-websocket (Redis 支持) | ⚠️ 良好 | 无心跳重连、消息可靠性、背压控制 |
| **图表** | ECharts 6 | ✅ 优秀 | 大数据渲染需开启 `progressive` 模式 |
| **权限控制** | @airpower/web 装饰器 + 后端行级/列级 | ✅ 良好 | 前端需配合列级脱敏渲染 |

### 1.2 关键性能瓶颈

| 瓶颈 | 影响 | 典型场景 |
|------|------|----------|
| **首屏加载慢** | 用户首次访问白屏 > 3s | 生产环境首次打开、切换工厂/租户 |
| **大表格渲染卡顿** | 1000+ 行数据交互延迟 > 500ms | 库存流水、工单明细、设备遥测历史 |
| **图表大数据卡死** | 10万+ 数据点页面假死 | 生产看板、质量趋势、能耗分析 |
| **WebSocket 消息风暴** | 设备上线/报警并发导致主线程阻塞 | 1000+ 设备同时上报、批量报警推送 |
| **权限指令重复计算** | 每次渲染遍历权限树 | 复杂表单、多按钮权限控制页面 |
| **构建产物过大** | `minify: false` 导致 JS > 10MB | 生产部署、CDN 分发、移动端加载 |

---

## 2. 重构目标与指标 (SLA)

| 指标 | 当前基线 | **目标值** | 测量方式 |
|------|----------|------------|----------|
| **首屏加载 (FCP)** | ~3.5s | **< 1.2s** | Lighthouse / Web Vitals |
| **可交互时间 (TTI)** | ~4.2s | **< 2.0s** | Lighthouse |
| **大表格 1000 行渲染** | ~800ms | **< 150ms** | 虚拟滚动 + 分片渲染 |
| **图表 10万点渲染** | 假死/崩溃 | **< 500ms** | ECharts `progressive` + WebWorker |
| **WebSocket 消息吞吐** | 丢包/卡顿 | **10,000 msg/s 无丢包** | 压测工具 + 监控大盘 |
| **JS 包体积** | > 10MB (未压缩) | **< 800KB (gzip)** | `vite build --report` |
| **API 请求成功率** | 99.5% | **99.99%** | 请求重试 + 熔断降级 |
| **内存泄漏** | 长时间运行内存涨不下 | **24h 无泄漏** | Chrome DevTools Memory Profile |
| **缓存命中率** | 无策略 | **> 85%** | Service Worker + HTTP 缓存 + Pinia 持久化 |

---

## 3. 技术栈升级与选型确认

### 3.1 核心依赖升级策略

```json
{
  "dependencies": {
    "vue": "^3.5.25",           // 保持最新 minor
    "vue-router": "^4.6.4",
    "pinia": "^3.0.4",
    "element-plus": "^2.12.0",
    "@airpower/web": "^2.9.1",  // 锁定主版本，等重构稳定后再升大版本
    "axios": "^1.13.2",
    "echarts": "^6.0.0",
    "@vueuse/core": "^11.0.0",  // **新增**：组合式工具库 (防抖、存储、网络状态等)
    "@tanstack/vue-query": "^5.0.0", // **新增**：服务端状态管理 (缓存、去重、预取)
    "mitt": "^3.0.1",           // **新增**：轻量事件总线 (解耦跨组件通信)
    "workerize-loader": "^2.0.0", // **新增**：WebWorker 化大计算
    "virtual-scroller-vue3": "^1.0.0" // **新增**：大列表虚拟滚动
  },
  "devDependencies": {
    "vite": "^7.3.0",
    "@vitejs/plugin-vue": "^6.0.3",
    "vite-plugin-compression": "^0.5.1", // **新增**：gzip/brotli 压缩
    "vite-plugin-pwa": "^0.20.0", // **新增**：Service Worker 离线缓存
    "rollup-plugin-visualizer": "^5.12.0", // **新增**：包体积分析
    "vitest": "^2.0.0",         // **新增**：单元测试
    "@vue/test-utils": "^2.4.0",
    "playwright": "^1.45.0"     // **新增**：E2E 测试
  }
}
```

### 3.2 架构模式重构

| 模式 | 现状 | **重构后** | 收益 |
|------|------|------------|------|
| **状态管理** | Pinia 分散在各 model/store | **分层状态架构**：`Global Store` (用户/权限/租户) + `Feature Store` (业务域) + `Server State` (@tanstack/vue-query) | 关注点分离、服务端状态自动缓存/去重/预取 |
| **API 请求层** | Axios 直接在 Service 调用 | **统一请求网关**：`useRequest` + 请求取消/重试/去抖/缓存/错误码统一处理 | 重试策略、防抖搜索、请求去重、离线队列 |
| **实时通信** | airpower-websocket 直接用 | **WebSocket Manager**：心跳重连、消息分优先级队列、背压控制、断点续传 | 10k 并发稳定、消息不丢、不阻塞主线程 |
| **权限系统** | 指令/装饰器分散 | **Permission Engine**：编译期裁剪 + 运行时缓存 + 指令/组件/函数三重防护 | 渲染零开销、动态权限秒级生效 |
| **构建产物** | 单包、未压缩 | **代码分割 + 多级缓存 + 压缩 + PWA** | 首包 < 200KB、二次访问离线可用 |

---

## 4. 分阶段详细实施路线图 (总周期 14 周，与后端并行)

### 阶段 0：工程化基建与性能基线 (第 1-2 周) —— **「量化现状、立标杆」**

| 步骤 | 任务 | 产出物 | 验收标准 |
|------|------|--------|----------|
| 0.1 | **Vite 生产构建优化** | `vite.config.prod.ts` | `minify: 'esbuild'` / `cssCodeSplit: true` / `chunkSizeWarningLimit: 500` / `rollupOptions.output.manualChunks` 策略 |
| 0.2 | **启用压缩与分析** | `vite-plugin-compression` (gzip + brotli) + `rollup-plugin-visualizer` | gzip 后总包 < 800KB，单 chunk < 200KB |
| 0.3 | **PWA 离线缓存** | `vite-plugin-pwa` (Workbox) | 核心资源离线可用，`serviceWorker` 注册成功率 100% |
| 0.4 | **性能基线测量** | Lighthouse CI + Web Vitals 上报 | 记录 FCP/TTI/CLS/INP 基线，生成《性能基线报告》 |
| 0.5 | **构建 CI/CD 集成** | GitLab CI / GitHub Actions | 每次 MR 自动跑 Lighthouse、包体积对比、单测覆盖率 |
| 0.6 | **依赖升级与锁版** | `pnpm update -i` + `pnpm audit fix` | 0 high/critical 漏洞，`pnpm-lock.yaml` 提交 |

> **里程碑 M0**：**第 2 周末**，生产构建产物 **gzip < 800KB**，首屏 FCP 基线 < 2.5s，CI/CD 门禁生效。

---

### 阶段 1：架构核心重构 (第 3-6 周) —— **「换心脏、强内功」**

#### 1.1 请求网关层 (第 3 周)
```typescript
// composables/useRequest.ts - 统一请求网关
interface RequestOptions<T> {
  immediate?: boolean
  cache?: 'memory' | 'swr' | false
  retry?: number | RetryStrategy
  debounce?: number
  deduplicate?: boolean
  onSuccess?: (data: T) => void
  onError?: (error: ApiError) => void
}

// 功能：自动重试(指数退避) / 请求去重 / 防抖搜索 / 缓存策略 / 请求取消 / 错误码统一映射 / 离线队列
```

| 交付物 | 关键特性 |
|--------|----------|
| `useRequest` / `useMutation` | 替代所有 Service 直接调用 Axios |
| `RequestInterceptor` | 统一 Token 刷新、租户头、错误码映射 (401/403/500) |
| `OfflineQueue` | 网络离线自动入队，恢复后重放 (IndexedDB 持久化) |
| `RequestCache` | `memory` (组件级) / `swr` (stale-while-revalidate) / `persist` (本地持久化) |

#### 1.2 服务端状态管理 (第 3-4 周)
```typescript
// 替代 Pinia 中的异步数据管理
// stores/queries/useInventoryQuery.ts
export const useInventoryList = (params: InventoryQueryParams) => 
  useQuery({
    queryKey: ['inventory', params],
    queryFn: () => inventoryApi.list(params),
    staleTime: 30_000,      // 30s 内不重复请求
    gcTime: 5 * 60_000,     // 5 分钟未使用才回收
    placeholderData: keepPreviousData, // 翻页不闪烁
  })
```

| 迁移清单 | 状态 |
|----------|------|
| 所有列表页 `useMyTable` → `useQuery` + `useInfiniteQuery` | 🔄 进行中 |
| 所有详情页 `useDetail` → `useQuery` | 🔄 进行中 |
| 所有下拉选项/字典 → `useQuery` + `staleTime: Infinity` | 🔄 进行中 |
| 复杂关联查询 → `useQueries` / `useQueryClient.setQueryData` | 🔄 进行中 |

#### 1.3 WebSocket 管理器重构 (第 4 周)
```typescript
// composables/useWebSocketManager.ts
class WebSocketManager {
  private ws: WebSocket | null = null
  private reconnectTimer: number = 0
  private messageQueue: Message[] = []        // 离线/重连期间缓存
  private priorityQueues: Map<Priority, Message[]> = new Map() // 优先级队列
  private heartbeatInterval: number = 30_000
  private maxReconnectAttempts: number = 10
  private backoffMultiplier: number = 1.5
  
  // 背压控制：每帧处理上限、WebWorker 解析大消息
  // 断点续传：消息 ID 确认机制、服务端补发
}
```

| 关键指标 | 目标 |
|----------|------|
| 重连成功率 | 100% (指数退避 + 抖动) |
| 消息不丢失 | 持久化队列 + 服务端 ACK 确认 |
| 主线程阻塞 | < 16ms/帧 (大消息进 WebWorker) |
| 并发连接 | 单页支持 50+ 并发订阅 |

#### 1.4 权限引擎前端落地 (第 5 周)
```typescript
// composables/usePermission.ts
// 编译期：vite 插件根据权限元数据裁剪代码 (可选)
// 运行时：PermissionEngine 单例缓存 + 订阅式更新

// 指令用法
<button v-permission:btn="['inventory:export', 'inventory:print']">导出</button>

// 组件用法
<PermissionWrapper :permissions="['inventory:cost:view']">
  <CostColumn />
</PermissionWrapper>

// 函数用法
if (permissionEngine.check('inventory:cost:edit')) { ... }
```

| 落地范围 | 实现方式 |
|----------|----------|
| 路由守卫 | `router.beforeEach` + `permissionEngine.canAccess(route.meta.permission)` |
| 菜单渲染 | `PermissionWrapper` 组件包裹，无权限不挂载 (非 `v-show`) |
| 按钮/列/表单项 | `v-permission` 指令 + `permissionEngine.check()` |
| 列级脱敏渲染 | 配合后端 `x-mask` 元数据，自动应用 `MaskPipe` |

#### 1.5 组件库按需引入与虚拟化 (第 5-6 周)
```typescript
// vite.config.ts - Element Plus 自动导入
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

// 大表格虚拟滚动
<VirtualList
  :data-source="largeData"
  :item-height="48"
  :visible-count="20"
  :buffer="5"
>
  <template #item="{ item, index }">
    <InventoryRow :data="item" />
  </template>
</VirtualList>

// ECharts 大数据
<Chart :option="option" :progressive="1000" :progressiveThreshold="3000" />
```

| 优化项 | 预期收益 |
|--------|----------|
| Element Plus 组件按需引入 | 首包减少 150KB+ |
| 表格虚拟滚动 (1000+ 行) | 渲染时间 < 150ms，内存恒定 |
| ECharts `progressive` 模式 | 10 万点渲染 < 500ms，不阻塞主线程 |
| 图标 SVG Sprite | 图标请求数从 50+ 降为 1 |

> **里程碑 M1**：**第 6 周末**，核心架构层上线，**所有列表页迁移至 `@tanstack/vue-query`**，WebSocket 管理器通过 10k 并发压测，权限引擎零渲染开销。

---

### 阶段 2：性能专项攻坚 (第 7-10 周) —— **「极致体验」**

#### 2.1 大表格/大数据渲染专项 (第 7 周)
| 场景 | 方案 | 关键技术 |
|------|------|----------|
| **库存流水/工单明细 (10k+ 行)** | 虚拟滚动 + 分片渲染 + `requestIdleCallback` | `virtual-scroller-vue3` + `requestIdleCallback` 分批挂载 |
| **设备遥测实时表 (高频更新)** | WebWorker 数据预处理 + `Object.freeze` + `shallowRef` | WebWorker 解析/聚合 + `shallowRef` 避免深度响应式开销 |
| **可编辑大表格** | 仅编辑行响应式 + 批量提交 | `shallowRef` + 编辑态切换 + `debouncedBatchSubmit` |
| **复杂表头/合并单元格** | 预计算布局 + CSS Grid | 预计算布局信息，渲染时直接应用 |

#### 2.2 图表/可视化专项 (第 8 周)
| 场景 | 方案 | 关键技术 |
|------|------|----------|
| **生产看板 (10万+ 点)** | `progressive` 渲染 + `large: true` + WebWorker 数据预聚合 | `echarts` `progressive: 2000` + `largeThreshold: 3000` + WebWorker 下采样 |
| **实时趋势图 (高频推送)** | 数据流 + 环形缓冲区 + `appendData` | 环形缓冲区固定内存 + `chart.appendData()` 增量更新 |
| **地图/热力图** | WebGL 渲染 + 数据精简 | `echarts-gl` / `L7` + 服务端 GeoJSON 简化 |

#### 2.3 首屏加载极致优化 (第 9 周)
| 优化手段 | 实施细节 | 预期收益 |
|----------|----------|----------|
| **路由级代码分割** | 已有 `() => import()`，细化到 Tab/Modal 级 | 首包 < 150KB |
| **关键资源预加载** | `<link rel="preload/prefetch">` + `vite-plugin-preload` | 核心路由瞬间打开 |
| **首屏关键 CSS 内联** | `vite-plugin-css-inlined` | 无白屏闪烁 |
| **字体子集化/WOFF2** | `fonttools` 提取常用字 + `woff2` 压缩 | 字体文件 < 50KB |
| **图片 WebP/AVIF + 响应式** | `vite-plugin-imagemin` + `<picture>` | 图片体积 -60% |
| **Service Worker 离线首包** | `vite-plugin-pwa` `precacheManifest` | 二次访问 0 网络请求 |

#### 2.4 运行时性能守护 (第 10 周)
```typescript
// performance.monitor.ts - 生产环境性能监控上报
// 1. Long Task 监控 (> 50ms)
// 2. 内存增长趋势 (每 5 分钟采样)
// 3. 组件渲染耗时 (Vue DevTools Performance API)
// 4. API 耗时/错误率 (useRequest 拦截器自动上报)
// 5. WebSocket 消息积压/处理耗时
```

| 监控指标 | 告警阈值 | 上报频率 |
|----------|----------|----------|
| FCP / LCP / INP / CLS | 超过 P75 基线 20% | 实时 |
| JS Heap 内存增长 | > 50MB/小时 | 5 分钟 |
| Long Task 总时长 | > 300ms/分钟 | 实时 |
| API P99 延迟 | > 2000ms | 1 分钟 |
| WS 消息处理耗时 | > 16ms | 实时 |

> **里程碑 M2**：**第 10 周末**，全链路性能指标达标：**FCP < 1.2s、TTI < 2.0s、大表格 150ms、图表 500ms、包体积 gzip < 800KB、无内存泄漏**。

---

### 阶段 3：工程化与质量体系 (第 10-12 周) —— **「防回滚、可持续」**

| 专题 | 任务 | 交付标准 |
|------|------|----------|
| **3.1 单元测试体系** | Vitest + @vue/test-utils，核心 composables/store/utils 覆盖率 > 80% | CI 强制阈值，MR 必检 |
| **3.2 E2E 核心流程** | Playwright：登录、列表增删改查、工单流转、报表导出 | 核心 10 条流程自动化，每日定时跑 |
| **3.3 视觉回归测试** | Playwright + pixelmatch，关键页面截图对比 | UI 意外变更自动拦截 |
| **3.4 组件文档/设计系统** | Storybook + `@airpower/web` 组件目录 | 新人 1 天上手，组件复用率 > 60% |
| **3.5 性能回归测试** | Lighthouse CI + 基线对比，每次 MR 自动对比 | 性能退化 > 10% 直接阻断合并 |
| **3.6 包体积监控** | `rollup-plugin-visualizer` + CI 门禁 | 总包 > 800KB / 单 chunk > 200KB 阻断 |
| **3.7 依赖安全/许可证** | `pnpm audit` + `license-checker` | 0 high/critical，许可证合规 |

> **里程碑 M3**：**第 12 周末**，质量体系全链路跑通，**零手工发布、零性能回归、零安全漏洞**。

---

### 阶段 4：灰度验证与全量上线 (第 12-14 周) —— **「平稳落地」**

| 周次 | 策略 | 关键动作 | 回滚条件 |
|------|------|----------|----------|
| **第 12 周** | **内网 UAT** | 邀请关键用户(仓管/调度/财务)实操核心流程，收集体验反馈 | P0 体验问题 > 0 不得进入下一阶段 |
| **第 13 周** | **单工厂灰度 (5%)** | Nginx/网关按租户/用户分流 5% 流量至新版，旧版保留 95% | 错误率 > 0.1% / FCP > 2s / 核心投诉 > 3 单 → 秒级回滚 |
| **第 14 周** | **扩大灰度 (20% → 50% → 100%)** | 每日扩容一档，白天观察、夜间扩容，核心指标看板盯盘 | 同回滚条件 |
| **第 14 周末** | **全量切换** | CDN 刷新、Service Worker 版本更新、旧版静态资源下线 | 监控无异常持续 48h |

> **里程碑 M4 (项目交付)**：**第 14 周末**，新前端全量承接生产流量，**连续运行 14 天 无 P0 事故**，全性能指标持续达标。

---

## 5. 关键技术攻关专项 (贯穿全周期)

| 专项 | 负责角色 | 关键交付物 | 完成时间点 |
|------|----------|------------|------------|
| **构建工程化** | 前端架构师 | `vite.config.prod.ts` + CI/CD 流水线 + 性能基线报告 | **第 2 周末** (M0 阻塞项) |
| **请求网关 + 服务端状态** | 资深前端 | `kubo-web-request` 库 (`useRequest`/`useQuery` 封装) | **第 4 周末** |
| **WebSocket 管理器** | 资深前端 | `WebSocketManager` (心跳/重连/优先级/背压/离线队列) | **第 4 周末** |
| **权限引擎前端** | 资深前端 | `PermissionEngine` + `v-permission` + `PermissionWrapper` | **第 5 周末** |
| **大表格虚拟化方案** | 资深前端 | `VirtualTable` 组件库 (选型/封装/文档) | **第 7 周末** |
| **ECharts 大数据方案** | 资深前端 | `ProChart` 组件 + WebWorker 下采样工具 | **第 8 周末** |
| **性能监控上报 SDK** | 前端架构师 | `kubo-web-monitor` (LongTask/内存/API/WS 自动上报) | **第 10 周末** |
| **PWA 离线化** | 前端架构师 | `vite-plugin-pwa` 配置 + 离线页面 + 更新提示 | **第 2 周末** |

---

## 6. 风险预案与应对策略 (ROAM 模型)

| 风险 | 等级 | 应对策略 (Mitigate) | 触发条件 | 应急预案 (Contingency) |
|------|------|---------------------|----------|------------------------|
| **@airpower/web 升级破坏性变更** | 🔴 高 | 1. 锁定版本 `^2.9.1`<br>2. 编写适配器层隔离核心依赖<br>3. 核心组件二次封装 | 升级后核心组件报错 | **回滚版本**，维护 fork 补丁，长期计划自研核心组件 |
| **Pinia → @tanstack/vue-query 迁移数据不一致** | 🔴 高 | 1. 逐页面迁移，新旧并存期用 `useQueryClient.setQueryData` 同步<br>2. 双写期对比数据源 | 列表数据与详情不一致 | **暂停迁移**，回滚该页面，排查同步逻辑 |
| **WebSocket 重连风暴** | 🟠 中 | 1. 指数退避 + 随机抖动<br>2. 连接池限制并发重连数<br>3. 服务端限流保护 | 服务端连接数突增 | **客户端主动降级** 降低心跳频率、暂停非核心订阅 |
| **虚拟滚动在复杂布局下失效** | 🟠 中 | 1. 预研 3 个方案 (virtual-scroller/vue-virtual-scroller/自研)<br>2. 复杂合并单元格场景单独处理 | 合并单元格/多行高度场景报错 | **降级为分页加载**，牺牲体验保功能 |
| **ECharts 大数据内存泄漏** | 🟠 中 | 1. `dispose()` 严格调用<br>2. WebWorker 隔离大数据计算<br>3. 定时 `echarts.dispose()` + 弱引用检测 | 内存增长 > 100MB | **强制销毁实例**，切换 Canvas 渲染模式 |
| **PWA Service Worker 缓存更新失败** | 🟡 中 | 1. `skipWaiting` + `clients.claim`<br>2. 版本号机制 + 用户感知更新提示<br>3. 离线兜底页面 | 用户拿到旧版资源 | **强制刷新** + 清除 Cache Storage |
| **构建产物体积超标** | 🟡 中 | 1. `rollup-plugin-visualizer` 每次 CI 分析<br>2. 动态导入细化到组件级<br>3. 移除未用 locale/图标 | gzip > 1MB | **紧急拆包**，移除大体积依赖 (如 moment → dayjs) |

---

## 7. 资源投入与团队组织建议

| 角色 | 人数 | 职责 | 关键产出 |
|------|------|------|----------|
| **前端架构师** | 1 | 技术选型、架构决策、性能基线、核心库封装、Code Review 终审 | 核心库、架构文档、性能报告 |
| **资深前端工程师** | 3-4 | 核心业务域 (WMS/MES/Finance) 重构、性能攻关、疑难杂症 | 高质量业务代码、组件库 |
| **中高级前端工程师** | 3-4 | 支撑域 (Procurement/Sales/Asset/Personnel/Report) 重构 | 标准化业务代码 |
| **UI/UX 设计师** | 1 | 交互优化、设计系统维护、组件库设计 | 设计规范、组件设计稿 |
| **QA/测试** | 1-2 | E2E 自动化、性能测试脚本、视觉回归、兼容性测试 | 测试报告、缺陷趋势 |
| **DevOps** | 1 (共享) | CI/CD 维护、前端构建优化、CDN/静态资源部署 | 自动化流水线、性能门禁 |

---

## 8. 成功交付标准 (Definition of Done)

| 维度 | 指标 | 验收方式 |
|------|------|----------|
| **功能完整性** | 覆盖旧系统 100% 核心用例 + 新增 PWA/离线/实时协作能力 | UAT 签字、E2E 自动化通过率 100% |
| **首屏性能** | FCP < 1.2s / TTI < 2.0s / LCP < 1.5s / CLS < 0.1 / INP < 200ms | Lighthouse CI / 真实用户监控 (RUM) |
| **运行时性能** | 大表格 1000 行 < 150ms / 图表 10万点 < 500ms / WS 10k msg/s 无丢包 | 专项压测报告 + 生产监控大盘 |
| **包体积** | 总包 gzip < 800KB / 首包 gzip < 200KB / 单 chunk < 200KB | `vite build --report` + CI 门禁 |
| **可靠性** | API 成功率 99.99% / WS 重连 100% / 离线可用核心流程 / 24h 无内存泄漏 | 生产监控大盘 / 压测报告 |
| **工程质量** | 单测覆盖率 > 80% / E2E 核心流程 100% / 0 high/critical 漏洞 / 性能回归 0 | SonarQube / CI 报告 / 依赖审计 |
| **用户体验** | 核心流程操作步数 -20% / 页面切换无白屏 / 离线提示友好 / 加载骨架屏覆盖 100% | UAT 评分 ≥ 4.5/5.0 |

---

## 9. 与后端重构的协同接口契约

| 协同点 | 前端需求 | 后端交付 | 验收方式 |
|--------|----------|----------|----------|
| **OpenAPI 契约** | 自动生成 TS 类型 + `useRequest` 类型安全 | `springdoc-openapi` + `openapi-typescript-codegen` | 类型检查 0 报错 |
| **行级/列级权限元数据** | `x-visibility` / `x-mask` 渲染脱敏 | Controller 返回 `@Schema(extensions = @Extension(name="x-mask", ...))` | 前端自动脱敏渲染 |
| **WebSocket 协议** | 二进制/JSON 帧格式、心跳、ACK、重连 Token | `STOMP over WebSocket` / 自定义协议文档 | 连接/重连/消息收发 0 错误 |
| **SSE/流式响应** | 大文件导出、报表生成进度 | `SseEmitter` / `Flux` | 进度实时更新、可取消 |
| **错误码规范** | 统一错误码枚举、国际化 Key | `ErrorCode` 枚举 + `i18n` key | 前端统一弹窗/Toast/重试逻辑 |
| **幂等 Key 规范** | `Idempotency-Key` Header 自动生成 | 幂等 Key 校验逻辑 | 重复提交 0 脏数据 |
| **分页/排序/过滤** | 统一参数名 `page/pageSize/sort/filter` | `Pageable` + `Specification` | 列表页零适配代码 |

---

## 10. 附录：前端代码仓库结构规划 (重构后)

```text
kubo-web/ (Monorepo 推荐)
├── apps/
│   ├── kubo-web-main/              # 主应用 (生产入口)
│   │   ├── src/
│   │   │   ├── main.ts             # 入口、Provider 注册
│   │   │   ├── App.vue
│   │   │   ├── router/             # 路由配置、守卫、懒加载
│   │   │   ├── layouts/            # 布局组件 (Console/Blank/Auth)
│   │   │   ├── pages/              # 页面级组件 (按路由组织)
│   │   │   │   ├── login/
│   │   │   │   ├── console/
│   │   │   │   │   ├── dashboard/
│   │   │   │   │   ├── wms/
│   │   │   │   │   ├── mes/
│   │   │   │   │   └── ...
│   │   │   └── styles/             # 全局样式、变量、重置
│   │   ├── vite.config.ts
│   │   └── package.json
│   ├── kubo-web-admin/             # 管理后台 (可选独立部署)
│   └── kubo-web-mobile/            # 移动端/H5 (可选)
├── packages/                        # 内部包 (Monorepo 共享)
│   ├── kubo-web-request/           # 请求网关 (核心库)
│   │   ├── src/
│   │   │   ├── useRequest.ts
│   │   │   ├── useMutation.ts
│   │   │   ├── interceptors/
│   │   │   ├── cache/
│   │   │   └── offline/
│   ├── kubo-web-query/             # 服务端状态封装
│   │   ├── src/
│   │   │   ├── queryClient.ts
│   │   │   ├── queryKeys.ts
│   │   │   └── hooks/
│   ├── kubo-web-ws/                # WebSocket 管理器
│   │   ├── src/
│   │   │   ├── WebSocketManager.ts
│   │   │   ├── hooks/
│   │   │   └── types/
│   ├── kubo-web-permission/        # 权限引擎
│   │   ├── src/
│   │   │   ├── PermissionEngine.ts
│   │   │   ├── directives/
│   │   │   ├── components/
│   │   │   └── hooks/
│   ├── kubo-web-components/        # 业务通用组件库
│   │   ├── src/
│   │   │   ├── VirtualTable/
│   │   │   ├── ProChart/
│   │   │   ├── ProForm/
│   │   │   ├── ProSearch/
│   │   │   └── index.ts
│   ├── kubo-web-monitor/           # 性能监控 SDK
│   │   ├── src/
│   │   │   ├── PerformanceMonitor.ts
│   │   │   ├── reporters/
│   │   │   └── hooks/
│   ├── kubo-web-utils/             # 通用工具函数
│   │   ├── src/
│   │   │   ├── storage/
│   │   │   ├── format/
│   │   │   ├── validation/
│   │   │   └── ...
│   └── kubo-web-config/            # 共享配置 (ESLint/TSConfig/Vite/PostCSS)
├── tools/
│   ├── scripts/                    # 构建/部署/代码生成脚本
│   │   ├── generate-api.ts         # OpenAPI → TS 类型生成
│   │   ├── generate-permission.ts  # 权限元数据生成
│   │   └── analyze-bundle.ts       # 包体积分析
│   └── scripts/ci/                 # CI 专用脚本
├── .github/workflows/              # GitHub Actions / GitLab CI
│   ├── ci.yml
│   ├── cd.yml
│   ├── performance.yml
│   └── dependency-review.yml
├── docker/
│   ├── Dockerfile
│   └── nginx.conf
├── pnpm-workspace.yaml
├── package.json
├── tsconfig.base.json
└── README.md
```

---

**文档状态**：**已定稿，可执行**  
**核心决策**：✅ **Vue 3.5 + TS 5.9 + Vite 7 + Pinia 3 + Element Plus 2.12 + @airpower/web 2.9** ✅ **@tanstack/vue-query (服务端状态) + @vueuse/core (工具库) + 虚拟滚动 + WebWorker + PWA** ✅ **分层状态架构 + 请求网关 + WebSocket 管理器 + 权限引擎** ✅ **与后端 `kubo-{domain}` 微服务契约协同**  
**下一步行动**：**第 1 周启动会** → 分配任务 → 搭建 `kubo-web` Monorepo 脚手架 → 发布 `kubo-web-config` + `kubo-web-utils` 快照版 → **第 2 周末完成构建工程化 + 性能基线测量 (M0)** → 同步后端「钢丝」链路联调。

---

*文档生成时间：2026-08-01*  
*文档位置：`D:\JAVA\Code_repository\SPMS-Web\SPMS-Server\docs\Kubo 平台-前端高并发重构实施路线规划计划.md`*
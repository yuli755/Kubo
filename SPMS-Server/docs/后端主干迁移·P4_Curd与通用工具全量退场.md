# 后端主干迁移 · P4 Curd 模块与通用工具全量退场（2026-09-21）

> 用户已确认采纳 4 阶段后端退场路径。本阶段完成 **P4 阶段（Curd 模块全量退场、通用工具与注解完全自持）**。
> **Curd 模块引用彻底归零（86 → 0），Api 模块引用彻底归零（4 → 0），足迹降至 3 文件 / 2 符号 / 5 处引用（仅保留协议兼容层）。**
> 138 项纯 JDK 契约测试全通，全量 517 个 Java 源码通过 AST 解析，前端 60 个测试套件 / 623 个 Vitest 测试全量通过。

---

## 一、P4 退场成果与核心替换矩阵

| 原 AirPower 符号 | 所属模块 | 原用量 | 自持替代方案 | 当前剩余用量 |
| :--- | :--- | :--- | :--- | :--- |
| `curd.base.Curd` | curd | 21× | `cn.hamm.spms.base.Curd` | **0** |
| `curd.annotation.Extends` | curd | 20× | `cn.hamm.spms.base.annotation.Extends` | **0** |
| `curd.helper.ExportHelper` | curd | 15× | `cn.hamm.spms.common.export.ExportHelper` | **0** |
| `curd.base.Curd.GetDetail` | curd | 14× | `cn.hamm.spms.base.Curd.GetDetail` | **0** |
| `curd.base.Curd.GetPage` | curd | 14× | `cn.hamm.spms.base.Curd.GetPage` | **0** |
| `curd.permission.Permission` | curd | 14× | `cn.hamm.spms.framework.web.annotation.Permission` | **0** |
| `api.ApiController` | api | 6× | `cn.hamm.spms.base.ApiController` | **0** |
| `curd.config.AccessConfig` | curd | 4× | `cn.hamm.spms.common.config.AccessConfig` | **0** |
| `api.config.ApiConfig` | api | 4× | `cn.hamm.spms.common.config.ApiConfig` | **0** |
| `curd.helper.TransactionHelper` | curd | 2× | `cn.hamm.spms.common.transaction.TransactionHelper` | **0** |
| `curd.model.query.QueryExport` | curd | 2× | `cn.hamm.spms.framework.query.QueryExport` | **0** |
| `core.annotation.Desensitize` | core | 2× | `cn.hamm.spms.framework.web.annotation.Desensitize` | **0** |
| `core.enums.DesensitizeType` | core | 2× | `cn.hamm.spms.framework.web.annotation.DesensitizeType` | **0** |
| `core.annotation.ExposeAll` | core | 2× | `cn.hamm.spms.framework.web.annotation.ExposeAll` | **0** |
| `curd.interceptor.filter.RequestFilter` | curd | 1× | `cn.hamm.spms.common.filter.RequestFilter` | **0** |
| `curd.config.CurdConfig` | curd | 1× | 移除冗余配置 | **0** |
| `curd.base.Curd.Export` | curd | 1× | `cn.hamm.spms.base.Curd.Export` | **0** |
| `curd.base.Curd.QueryExport` | curd | 1× | `cn.hamm.spms.base.Curd.QueryExport` | **0** |

---

## 二、关键设计与实现细节

### 1. 自持 `Curd` 与 `@Extends` 控制机制
- 在 `cn.hamm.spms.base` 中创建自持 `Curd` 枚举与 `cn.hamm.spms.base.annotation.Extends` 注解。
- 完整保留基于控制器继承层级的白名单/黑名单继承判定算法（`Curd.getCurdList(clazz)`）。
- 支持静态常量级 API 可用性校验 `GetDetail.checkApiAvailable(this)` 和 `GetPage.checkApiAvailable(this)`。
- 全量 37 个控制器及基类平滑切换至自持实现。

### 2. 自持 `ExportHelper` 与 `ExportConfig`
- 在 `cn.hamm.spms.common.export` 中自持异步导出任务调度器 `ExportHelper` 与配置类 `ExportConfig`。
- 依赖自持 `RedisHelper` 管理异步导出任务状态与结果缓存；采用 `CompletableFuture.runAsync` 进行多线程异步落盘。
- 复用 `FilePaths` 本地文件落盘机制，无缝接管全仓 13 个业务领域服务的 CSV 导出链路。

### 3. 自持权限注解与拦截配置
- `@Permission` 统一收敛至 `cn.hamm.spms.framework.web.annotation.Permission`。
- `AccessConfig` 与 `ApiConfig` 收敛至 `cn.hamm.spms.common.config`。
- `RequestFilter` 收敛至 `cn.hamm.spms.common.filter.RequestFilter`。
- `TransactionHelper` 收敛至 `cn.hamm.spms.common.transaction.TransactionHelper`，提供 `@Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)`。

### 4. 剩余符号口径与协议兼容保障
- 生产代码仅保留 `UserController`（2 处）与 `LegacyApiResponseBridge`（1 处）对 `cn.hamm.airpower.core.Json` 的兼容桥接，确保旧请求过滤链与 HTTP 响应在过渡期字节完全一致。
- 测试代码 `HttpErrorContracts` 保留 2 处断言，严格锁定向后兼容契约。
- 门禁脚本 `check-airpower-backend.mjs` 基线同步收敛至 `{ files: 3, symbols: 2, references: 5 }`，并将全部已退场符号列入防回潮黑名单。

---

## 三、验收与门禁全通证据

1. **真实 Java 17 AST 语法扫描**：
   - 全仓 517 个 Java 源码全部通过 AST 解析（0 语法错误，0 注解冲突）。
2. **纯 JDK 17 生产契约套件**：
   - 138 项脱离第三方 classpath 的严格契约测试全数通过（包括 US-ASCII 跨字符集校验）。
3. **前端与接口自动化套件**：
   - Vitest 60 个测试文件、623 个用例全量通过（涵盖数据权限、登录防爆破、单据流转、列级脱敏）。
4. **项目整体技术栈真实文件审计**：
   - `pnpm stack:project` 基于真实文件成功生成整体技术栈报表，确认 curd 模块归零，仅剩 3 文件 / 2 符号 / 5 处协议兼容引用。

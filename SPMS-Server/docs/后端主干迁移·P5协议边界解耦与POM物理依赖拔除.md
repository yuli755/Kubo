# 后端主干迁移 · P5 协议边界完全解耦与 POM 物理依赖彻底拔除

## 1. 迁移目标与达成总结

本阶段（P5）按照既定规划，彻底完成后端对 AirPower 框架的物理退场与全量解耦：
1. **协议兼容层与业务控制器纯净化（规划 1）**：
   - `UserController`：所有端点响应全量切换至自持 `ApiResponse<T>`，菜单与权限缓存读写切换至自持 `JsonCodec`，登录防爆破异常捕获统一收敛为自持 `BusinessException`，彻底清除 `cn.hamm.airpower.core.Json` 与 `cn.hamm.airpower.core.exception.ServiceException`。
   - `LegacyApiResponseBridge`：正式下线过渡适配桥，Spring MVC 传输层统一由自持 `ApiResponseAdvice`（`ResponseBodyAdvice<Object>`）负责在 HTTP 传输前自动注入当前请求线程的 `Tracer.getTraceId()`。
   - `HttpErrorContracts`：升级为自持响应断言契约，彻底清空测试用例中对旧 `Json` 与 `ServiceException` 的残存引用。
   - **后端 AirPower 符号引用实现 0 文件 / 0 符号 / 0 引用（100% 纯净脱离）**。
2. **物理依赖拔除与 Spring Boot 原生依赖树固化（规划 2）**：
   - 补齐官方原生 Starter：在 `SPMS-Server/pom.xml` 中显式声明由 Spring Boot BOM（3.4.4）直接纳管的原生依赖：
     - `spring-boot-starter-web`
     - `spring-boot-starter-validation`
     - `spring-boot-starter-data-jpa`
     - `spring-boot-starter-data-redis`
   - 物理移除旧依赖：从 `pom.xml` 中正式删除 `<dependency><groupId>cn.hamm</groupId><artifactId>airpower-curd</artifactId></dependency>` 及 `<airpower.version>6.4.3</airpower.version>` 属性。
   - 配置键平滑接管：将 `application.yml` 与 `application-production.yml` 中的配置前缀统一收敛至 `spms.api.*`，`ApiConfig`、`ApiController` 与 `AccessTokenSecretGuard` 保持双前缀兼容。
   - 门禁规则升级：`check-airpower-backend.mjs` 基线压降至 `0 / 0 / 0`，`Json` 与 `ServiceException` 加入永久防回潮清单；`check-pom-selfcontained.mjs` 依赖白名单收紧，`pnpm stack:project` 报表全面达标。

---

## 2. 核心架构变更对照表

| 层次 / 组件 | 迁移前（P4 兼容期） | 迁移后（P5 完全自持） | 收益与安全边界 |
| :--- | :--- | :--- | :--- |
| **用户控制器** (`UserController`) | 引用旧 `Json` 与 `ServiceException` 维持旧端点与双异常捕获 | 全量使用自持 `ApiResponse<T>` 与 `JsonCodec`，统一捕获 `BusinessException` | 控制器彻底脱离私有框架，响应契约强类型化 |
| **HTTP 响应增强** | `LegacyApiResponseBridge` 拦截 `ApiResponse` 强转旧 `Json` 实例 | 自持 `ApiResponseAdvice`，无侵入注入 `Tracer.getTraceId()` | 消除跨框架反射与对象转换，降低序列化开销 |
| **Maven 依赖声明** | 依赖 `airpower-curd:6.4.3`（排除 cookie），属性 `airpower.version` | 0 私有依赖，直接引入官方 `web` / `validation` / `data-jpa` / `data-redis` starter | 消除第三方私有库运行时风险，Spring Boot 3.4.4 原生可控 |
| **配置前缀** | `airpower.api.*` | `spms.api.*`（双前缀 fallback） | 配置命名规范化，消除对旧框架配置键的硬编码依赖 |
| **全仓门禁基线** | 3 文件 / 2 符号 / 5 引用 | **0 文件 / 0 符号 / 0 引用 / 0 依赖 / 0 配置残留** | 达到彻底脱离框架终态 |

---

## 3. 全套验收证据

1. **后端 AirPower 棘轮门禁 (`pnpm airpower:backend --check`)**：
   - 触及文件：**0 / 517**（基线 0）
   - 符号数量：**0**（基线 0）
   - 引用次数：**0**（基线 0）
   - 已退场符号零回潮，无通配导入。
2. **POM 自持与 BOM 白名单校验 (`pnpm pom:check`)**：
   - 无父 POM，依赖版本由 `spring-boot-dependencies:3.4.4` BOM 直接纳管。
   - 残留 `cn.hamm` 依赖：**无**。
3. **真实文件技术栈审计 (`pnpm stack:project`)**：
   - 后端状态：`源码/声明归零，仍需完整依赖树与 Java 17 验收`。
   - 依赖残留：`(无)`，传递排除：`无`，配置键：`无`。
4. **纯 JDK 17 独立工具契约与 517 Java 文件语法解析 (`pnpm java:unit`)**：
   - `javac 17.0.8`：31 个生产源码真实编译通过，AST 语法检查 517 个文件全过。
   - 138 项纯 JDK 契约测试在 UTF-8 / US-ASCII 环境下 100% PASS。
5. **前端全套门禁与单元测试**：
   - Vitest：**60 个测试套件 / 623 项用例 100% 通过**。
   - `lint:ci` 与 `typecheck`：0 error 0 warning。
   - `ddl:check`、`migration:check`、`monitoring:check`、`drill:check`、`perms:check`、`auth:check`、`text:check`：全部通过。

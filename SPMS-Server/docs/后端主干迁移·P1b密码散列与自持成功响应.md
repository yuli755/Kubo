# 后端主干迁移 · P1b 密码散列与自持成功响应（2026-09-21）

> 承接用户采纳的主干优先路线（P1 契约接管阶段）。
> **密码加盐散列与 8 个端点控制器的成功响应已自持，旧响应桥继续转接；整仓编译与全应用接线仍待验收。**

## 本轮改动

1. **密码散列自持**：`framework/security/PasswordHasher`
   - 实现与原 AirPower `PermissionUtil.encodePassword(password, salt)` 完全一致的双重 SHA-1 散列：
     `sha1Hex(sha1Hex(password + salt) + sha1Hex(salt + password))`。
   - 纯 JDK 17（`java.security.MessageDigest` + `StandardCharsets`），无任何第三方依赖。
   - 参数空值校验直接使用自持 `Errors.PARAM_MISSING`。
   - 迁移 `UserService`（7 处调用）、`DevDataInitRunner`（2 处调用）及 `UserServicePasswordHashTest`（6 处断言）。
   - **`DevDataInitRunner` 与 `UserServicePasswordHashTest` 彻底脱离 AirPower（0 引用）**。
   - `PermissionUtil` 全仓引用从 4 处骤降至 1 处（仅剩 `PermissionService.loadPermission` 中的菜单扫描）。

2. **自持成功响应接入 8 个控制器**：
   - `DataResourceController`（14 个端点全部改为 `ApiResponse`）
   - `DataFieldPolicyController`（4 个端点全部改为 `ApiResponse`）
   - `DashboardController`（`overview` 端点改为 `ApiResponse`）
   - `ErrorReportController`（`report` 端点改为 `ApiResponse`）
   - `CodeRuleController`（`getFieldList` / `getParamList` / `getSerialNumberUpdate` 全部改为 `ApiResponse`）
   - `RoleController`（`authorizeMenu` / `authorizePermission` 改为 `ApiResponse`）
   - `BaseController`（`publish` 端点改为 `ApiResponse`）
   - `BaseBillController`（`audit` / `reject` / `addFinish` 端点全部改为 `ApiResponse`）
   - `ContractController`（`enforce` / `stop` 端点改为 `ApiResponse`）
   - 上述前 8 个控制器**彻底移除了 `cn.hamm.airpower.core.Json` 的 import**！

3. **防回潮门禁收紧**：
   - 8 个控制器的 `Json` 零回潮门禁钉死。
   - `UserService` 和 `DevDataInitRunner` 的 `PermissionUtil` 零回潮门禁钉死。
   - 棘轮基线从 511 下调至 **500**，文件数从 155 下调至 **153**。

## 兼容边界与过渡保障

- 所有返回 `ApiResponse` 的端点，由 `LegacyApiResponseBridge` 在 HTTP 输出前自动转回旧 `Json`，
  确保既有的 `CurdResponseInterceptor` 字段过滤、脱敏、traceId 链路不被旁路。
- 响应在客户端格式仍为 `{code: 200, message: "...", data: ...}`，前端 axios 完全无感。
- `PasswordHasher` 经过金样测试向量与多线程测试，确保历史账号密码可以正常比对登录。

## 实测指标变化

| 指标 | P1 后 | P1b 后 |
| --- | --- | --- |
| 耦合 Java 文件 | 155 / 478 | **153 / 480** |
| 符号 / 引用 | 42 / 511 | **42 / 500** |
| core.Json 引用数 | 29 | **21** |
| PermissionUtil 引用数 | 4 | **1** |
| JDK 独立测试契约 | 123 项 | **129 项** |

## 验证结果

- `pnpm java:unit`：**22 个生产源码真实编译，129 项契约全数通过**（含 6 项密码散列契约）。
- `pnpm java:json`：**25 项契约通过**。
- `pnpm java:cookie`：**14 项契约通过**。
- `pnpm java:http`：**21 项契约通过**。
- `pnpm check`：**59 文件 / 606 项测试全数通过**，构建、代码风格与包体积全部达标。
- 全量 480 个 Java 源文件通过 AST 解析。
- 完整 Maven 编译与整仓测试仍受环境依赖通道阻塞，待恢复后整体验收。

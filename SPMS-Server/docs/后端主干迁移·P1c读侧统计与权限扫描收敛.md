# 后端主干迁移·P1c 读侧统计与权限扫描收敛

> 本文档记录后端 AirPower 退场第一阶段（Phase 1 契约层接管）的第三个推进节点（P1c）：
> 1. 十四资源读侧统计出口（`getCount` / `getStat`）整体切换至自持 `ApiResponse<Long>` 与 `ApiResponse<ReadStat>`；
> 2. `PersonalTokenController` 与 `FileController` 两个控制器完全脱离 `cn.hamm.airpower.core.Json`（自持零 Json 控制器增至 10 个）；
> 3. 自持 `PermissionScanner` 落地接管权限元数据扫描，`PermissionUtil` 与 `IPermission` 全量归零退场（符号数 42 → 40）；
> 4. 自持查询模型（`Page`、`Sort`、`QueryRequest`、`QueryListRequest`、`QueryPageRequest`、`QueryPageResponse`）落地并通过 136 项纯 JDK 17 可执行契约。

---

## 一、本次推进背景与目标

在 Phase 1 阶段（契约层接管），已完成：
- **P1**：自持错误码（`Errors` / `CustomError`）、自持业务异常体系（`BusinessException` / `ErrorCode`）、自持异常处理器（`BusinessExceptionAdvice`）与通用成功响应包（`ApiResponse<T>` + `LegacyApiResponseBridge`）；
- **P1b**：自持双向加盐 SHA-1 密码散列（`PasswordHasher`，bit-for-bit 等价），首批 8 个控制器与初始化脚本脱离旧 `Json` 与 `PermissionUtil`。

本批（P1c）针对第一阶段剩余的契约层与读侧接口进行收口：
1. **十四资源读侧接口统一响应**：14 个 Mapper 读控制器此前已完成分页与详情换轨，但自定义的 `getCount`（行数）与 `getStat`（`ReadStat` 统计）仍返回旧 `Json.data(...)`。本批将这 28 个端点全部换为类型明确的 `ApiResponse<Long>` 与 `ApiResponse<ReadStat>`；
2. **非 CRUD 自定义控制器脱离旧 Json**：`PersonalTokenController`（私人令牌管理，5 个端点）与 `FileController`（文件上传，1 个端点）均为非继承式自定义接口，将其全部换为 `ApiResponse`，使这两个控制器完全移除 `cn.hamm.airpower.core.Json` 导入；
3. **权限扫描与接口抽象退场**：`PermissionService.loadPermission()` 曾直接调用旧 `PermissionUtil.scanPermission(...)`，且 `PermissionEntity` 实现了旧 `IPermission<E>` 接口。本批编写自持 `PermissionScanner`，移除对 `PermissionUtil` 和 `IPermission` 的全部依赖，实现 2 个旧符号**彻底退场**；
4. **自持查询请求/响应模型构建**：在 `cn.hamm.spms.framework.query` 下构建无第三方依赖的 `Page`、`Sort`、`QueryRequest`、`QueryListRequest`、`QueryPageRequest`、`QueryPageResponse`，并提供与现有 `ReadPage` 计算对接的工厂方法，为 Phase 2 替换 `CurdController` 提供完备的数据传输模型。

---

## 二、代码改动清单

### 1. 自持权限扫描器与实体解耦
- **`SPMS-Server/src/main/java/cn/hamm/spms/common/security/PermissionScanner.java`**（新建）：
  - 扫描指定包名或类集合下的 Controller；
  - 识别 `@Api`、`@Description`、`@Permission`（支持类级默认与方法级覆盖）及 `@Extends`（检查 CRUD 排除列表）；
  - 输出轻量不可变记录 `ScannedPermission` 并提供转换为 `PermissionEntity` 的工厂；
  - 纯 Spring Core + 反射实现，零加密库依赖，不依赖任何已退场符号。
- **`SPMS-Server/src/main/java/cn/hamm/spms/module/system/permission/PermissionService.java`**：
  - 移除 `import cn.hamm.airpower.curd.permission.PermissionUtil;`；
  - `loadPermission()` 改为调用 `PermissionScanner.scanPermission(Application.class.getPackageName())`。
- **`SPMS-Server/src/main/java/cn/hamm/spms/module/system/permission/PermissionEntity.java`**：
  - 移除 `import cn.hamm.airpower.curd.permission.IPermission;`；
  - 移除类声明上的 `implements IPermission<PermissionEntity>`，仅保留 `implements TreeNode<PermissionEntity>`。

### 2. 控制器成功响应迁移与 Json 摘除
- **`PersonalTokenController.java`**：
  - 移除 `import cn.hamm.airpower.core.Json;`；
  - 5 个方法全切 `ApiResponse`：`getMyList`（`ApiResponse<List<PersonalTokenEntity>>`）、`disableMy`（`ApiResponse<Void>`）、`enableMy`（`ApiResponse<Void>`）、`deleteMy`（`ApiResponse<Void>`）、`createMy`（`ApiResponse<String>`）。
- **`FileController.java`**：
  - 移除 `import cn.hamm.airpower.core.Json;`；
  - `upload` 端点返回 `ApiResponse<String>`（文件路径），保持 `"文件上传成功"` 提示语。
- **十四资源读侧 Controller**（共 28 处端点替换）：
  - `ContractController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `MaterialController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `CustomerController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `SaleController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `PurchaseController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `PurchasePriceController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `SalePriceController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `SupplierController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `StorageController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `StructureController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `InputController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `MoveController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `InventoryController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`；
  - `OutputController`：`getCount` → `ApiResponse<Long>`，`getStat` → `ApiResponse<ReadStat>`。

### 3. 自持查询请求与响应模型
在 `cn.hamm.spms.framework.query` 下新增：
- **`Page.java`**：自持分页入参（`pageNum`、`pageSize`，默认 1 / 20，`serialVersionUID = 1L`）；
- **`Sort.java`**：自持排序入参（`field`、`direction`，支持 `isAsc()`、`isDesc()` 判断，大小写不敏感）；
- **`QueryRequest<T>.java`**：通用筛选请求包装；
- **`QueryListRequest<T>.java`**：继承 `QueryRequest`，附加 `Sort`；
- **`QueryPageRequest<T>.java`**：继承 `QueryListRequest`，附加 `Page`（带默认值防空保护）；
- **`QueryPageResponse<T>.java`**：通用分页响应结构（`total`、`pageCount`、`list`、`page`、`sort`），提供 `QueryPageResponse.of(ReadPage, rows, totalElements, requestedSort)` 工厂方法。
- **`QueryModelContracts.java`**（新建）：7 项全覆盖可执行契约，验证默认值、链式调用、空保护、equals/hashCode 及工厂行为。

### 4. 门禁与回归防范
- **`infra/scripts/check-airpower-backend.mjs`**：
  - 基线调整为：**文件 153 · 符号 40（降 2）· 引用 500**；
  - `RETIRED_SYMBOLS` 新增 `cn.hamm.airpower.curd.permission.PermissionUtil` 与 `cn.hamm.airpower.curd.permission.IPermission`；
  - `jsonFreeControllers` 扩充为 10 个控制器（新增 `PersonalTokenController`、`FileController`）。
- **`src/config/__tests__/backendAirpowerGate.spec.ts`**：
  - 测试用例由 69 项增至 73 项；
  - 增加针对 `PermissionUtil` 与 `IPermission` 全局禁用的断言；
  - 增加对 10 个零 Json 控制器的零回潮断言。

---

## 三、足迹与指标变化

| 维度 | 起始状态 | P1b 状态 | P1c 本状态 | 净变化 |
| --- | --- | --- | --- | --- |
| 涉及 Java 文件数 | 180 | 153 | **153** | 0（存量控制器改造） |
| AirPower 符号数 | 73 | 42 | **40** | **-2**（彻底退场 PermissionUtil, IPermission） |
| 符号引用总次数 | 656 | 500 | **500** | 持平（移除 4 处旧引用，适配器新增 4 处注解引用） |
| 完全零 Json 控制器 | 0 | 8 | **10** | **+2**（PersonalToken, File） |
| getCount/getStat 改造端点 | 0 | 0 | **28** | 全部转为 `ApiResponse` |
| 自持 Java 生产文件数 | 14 | 22 | **28** | **+6**（自持查询模型） |
| 独立可执行契约总数 | 32 | 129 | **136** | **+7**（全部通过 UTF-8 / US-ASCII） |

---

## 四、验证结果

1. **`pnpm java:unit`**：
   - 28 个生产源码通过真实 `javac 17.0.8` 编译（无任何第三方 classpath）；
   - 488 个 Java 文件通过语法与注解 AST 解析；
   - 136 项独立工具契约全部通过，且在强制 `US-ASCII` 环境下全部验证通过。
2. **`pnpm java:json`**：
   - 5 个生产源码 + 正式 Jackson 2.18.3 / Lombok 1.18.36 真实编译；
   - 25 项契约全部通过。
3. **`pnpm java:cookie`**：
   - 2 个生产组件 + 正式 Spring/Boot/Servlet 真实编译；
   - 14 项契约全部通过。
4. **`pnpm java:http`**：
   - 9 个生产源码 + Spring MVC + LegacyApiResponseBridge 真实编译；
   - 21 项契约全部通过。
5. **后端边界门禁测试（`vitest run src/config/__tests__/backend*.spec.ts`）**：
   - 8 个测试文件、151 项用例全部通过（AirPower 门禁 73 项、Cookie 14 项、Json 9 项、Http 11 项、Enum 8 项、Paging 15 项、Export 15 项、Phone 6 项）。
6. **全项目静态门禁（`pnpm perms:check && pnpm auth:check && pnpm pom:check && pnpm text:check` 等）**：
   - 105 个前端权限码与后端 35 个控制器 124 个端点严格对账通过；
   - 993 个文件文本完整性校验通过，488 个 Java 文件零非法字符。
7. **前端全量校验（`pnpm lint:ci && pnpm typecheck && pnpm build`）**：
   - ESLint 9 校验通过；
   - `vue-tsc` 类型检查零错误；
   - Vite 生产构建顺利产出，首屏 270.4 KB（预算 450 KB），总包 630.4 KB（预算 900 KB）。

---

## 五、安全边界与下一步

- **关于 `airpower-curd` 依赖**：目前 `BaseController` 仍继承 `CurdController`，`BaseService` 仍继承 `CurdService`，因此 POM 中的 `airpower-curd` **严禁提前删除**；
- **关于 `LegacyApiResponseBridge`**：现存 JPA CRUD 的拦截链仍需识别旧 `Json` 实例以执行字段白名单和列级脱敏，新端点返回的 `ApiResponse` 由该桥接器透明转为 `Json.show(...)`，平滑兼容现有所有过滤器；
- **下一步（Phase 2：核心继承链分批替换）**：
  1. 自建 `BaseController`，将通用 CRUD 端点（`add`、`update`、`delete`、`getDetail`、`getPage`、`export` 等）的返回类型迁移至 `ApiResponse`；
  2. 自建 `BaseService` 与 `BaseEntity`，使业务服务彻底脱离 `CurdService` 与 `CurdEntity`；
  3. 接管 `CurdRequestInterceptor` 与 `CurdResponseInterceptor`，由项目自持统一的拦截链。

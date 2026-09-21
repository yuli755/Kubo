# Kubo Server 开发规范

本文档约定了 **Kubo Server** 项目的开发规范，供 AI 辅助开发（Cursor、GitHub Copilot、Claude Code 等）参考，保证生成的代码与项目现有风格一致。

## 后端现代自持架构基线（2026-09-21 全量自持）

- **全仓 AirPower 彻底清零**：前端 0 处，后端 517 个 Java 源码文件 0 处引用，POM 物理依赖 0 残留，配置键 0 残留。
- **构建完全自持**：SPMS-Server 不继承任何第三方私有父 POM，依赖由官方 `org.springframework.boot:spring-boot-dependencies:3.4.4` BOM import 全量托管。
- **自持框架核心类库（`cn.hamm.spms.framework.*`）**：
  - 工具层：`DecimalMath`（十进制运算）、`RandomValues`（密码盐/验证码/安全随机）、`FilePaths`（文件与路径处理）、`EnumValues`（枚举选项反射）、`Trees` + `TreeNode`（递归安全迭代建树）、`BeanFields`（ClassValue 高性能字段缓存）、`CsvWriter` + `CsvText`（RFC 4180 CSV 编码与公式注入防护）、`PasswordHasher`（SHA-1 双向加盐散列）、`Tracer`（分布式追踪上下文）。
  - Web 与契约：`ApiResponse`（标准响应体 `{code, message, data}`）、`ApiResponseAdvice`（统一响应自动包装与空字段剔除）、`BusinessException` + `ErrorCode`（语义化业务异常运行时）、`Errors` / `CustomError`（全自持错误码与断言）。
  - 数据模型：`Page`、`Sort`、`QueryRequest`、`QueryPageRequest`、`QueryListRequest`、`QueryPageResponse`、`ReadPage`。
  - 元数据注解：`@Api`、`@Description`、`@Dictionary`、`@Export`、`@Extends`、`@Desensitize`。
- **持久化与双轨读写**：
  - 写路径：基于自持 `BaseRepository<E>`（Spring Data JPA `JpaRepository` + `JpaSpecificationExecutor` + 行锁 `getForUpdateById`）。
  - 读路径：通过 MyBatis-Plus 3.5.9 Mapper 读加速（由 `kubo.data-permission.mapper-read.enabled` 控制，带 JPA 平滑回退机制）。
  - 元数据提取：使用 `EntityMetadata` 解析实体类与字段注解，生成导出列配置。
- **安全与权限防护**：
  - `RequestInterceptor`：处理追踪标识注入、认证令牌解析、权限扫描与请求后置清理。
  - `AccessTokenSecretGuard`：强随机 JWT 密钥守卫，防止弱密钥上线。
  - `AccessTokenRevocationService`：登出黑名单，Redis TTL 自动过期。
  - `LoginBruteForceGuard`：防暴力破解守卫（账号 5 次/15m，IP 50 次，带自动锁定与计数清理）。
  - `ExportColumnMasker` + `ExportBypassGuard`：导出列级遮蔽深拷贝副本，防止脏写回库；未授权字段直接拦截。

## 技术栈

| 技术              | 版本              | 说明     |
|-----------------|-----------------|--------|
| Java            | 17              | 开发语言   |
| Spring Boot     | 3.4.4           | 应用框架（BOM 自持管理） |
| MyBatis-Plus    | 3.5.9           | 读加速 ORM 框架 |
| Maven Wrapper   | 3.8.3           | wrapper 固定版本 |
| JPA + Hibernate | Spring Data JPA | 持久化 ORM 框架  |
| MySQL           | 8.x             | 关系型数据库    |
| Redis           | 7.x             | 缓存与会话状态     |
| Lombok          | -               | 代码简化   |

## 项目整体结构

```
SPMS-Server/
├── src/main/java/cn/hamm/spms/
│   ├── base/               # 项目基础类（BaseController, BaseService, BaseEntity, BaseRepository, AbstractBaseBillService）
│   ├── common/             # 公共组件（安全、缓存、异常、拦截器、树与元数据）
│   ├── framework/          # 自持框架核心（http, json, error, dictionary, query, util, web）
│   ├── module/             # 业务功能模块
│   │   ├── asset/          # 资产域（物料、合同）
│   │   ├── channel/        # 渠道域（采购、销售、供应商、客户、价格）
│   │   ├── factory/        # 工厂域（仓库、生产单元）
│   │   ├── personnel/      # 人事域（用户、角色、部门）
│   │   ├── system/         # 系统域（数据权限、菜单、编码规则、文件、参数）
│   │   └── wms/            # 仓储域（入库、出库、移库、库存）
│   ├── Application.java    # 启动类
│   ├── DevDataInitRunner.java # 开发数据初始化
│   └── WebConfig.java      # Web 配置
└── src/main/resources/
    ├── application.yml         # 主配置文件
    ├── application-template.yml # 配置模板
    ├── application-production.yml # 生产配置
    ├── db/migration/           # Flyway 增量迁移脚本（V1~V10）
    └── logback-spring.xml     # 日志配置
```

## 编码风格规范

### 1. 文件头注释

每个文件必须包含类说明和作者：

```java
/**
 * <h1>类描述</h1>
 *
 * @author Hamm.cn
 */
```

### 2. Controller 注解与继承

```java
@Api("api_path")
@Description("功能描述")
@Extends({GetDetail, GetPage, Add, Update, Delete})
public class XxxController extends BaseController<XxxEntity, XxxService, XxxRepository> {
}
```

常用扩展动作：
- `GetDetail` - 根据 ID 查询详情
- `GetPage` - 分页查询
- `Add` - 新增
- `Update` - 更新
- `Delete` - 删除

### 3. Service 与 Repository

```java
@Service
public class XxxService extends BaseService<XxxEntity, XxxRepository> {
}

@Repository
public interface XxxRepository extends BaseRepository<XxxEntity> {
}
```

### 4. 异常处理

通用错误使用 `cn.hamm.spms.common.exception.Errors`；自定义业务码使用 `CustomError`：

```java
Errors.FORBIDDEN_EDIT.when(subtract < 0, "库存数量不足");
```

### 5. 导入包规范

1. `java.*`
2. `jakarta.*`
3. `org.*`
4. `springframework.*`
5. `cn.hamm.spms.*`
6. `lombok.*`

**导入包严禁使用通配符（*），每个类单独显式导入。禁止引入任何已退场的外部私有框架。**

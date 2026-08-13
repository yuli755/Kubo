# Kubo API 文档与类型生成指南

## 概述

本项目使用 **SpringDoc OpenAPI 3** + **Knife4j** 自动生成后端 API 文档，并使用 **openapi-typescript-codegen** 自动生成前端 TypeScript 类型和 API 客户端代码。

## 架构图

```mermaid
graph LR
    A[后端 Controller] -->|@Operation @Schema| B[SpringDoc OpenAPI 3]
    B -->|JSON/YAML| C[v3/api-docs]
    C -->|HTTP GET| D[openapi-typescript-codegen]
    D -->|生成| E[TypeScript 类型]
    D -->|生成| F[Axios API 客户端]
    E --> G[前端业务代码]
    F --> G
    B -->|UI| H[Knife4j / Swagger UI]
```

## 后端配置

### 依赖 (pom.xml)

```xml
<!-- SpringDoc OpenAPI 3 + Swagger UI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
<!-- Knife4j UI 增强 -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-spring-boot-starter</artifactId>
    <version>4.5.0</version>
</dependency>
```

### 配置 (application.yml)

```yaml
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /doc.html
    try-it-out-enabled: true
  packages-to-scan: cn.hamm.spms
  paths-to-match:
    - /api/**
```

### Controller 注解示例

```java
@Tag(name = "权限管理", description = "权限树形结构管理")
@Api("permission")
public class PermissionController extends BaseController<...> {

    @Operation(summary = "发布权限", description = "将权限标记为已发布状态")
    @Override
    public Json publish(@RequestBody @Validated(WhenIdRequired.class) E entity) {
        return super.publish(entity);
    }
}
```

### 实体注解示例

```java
@Schema(name = "PermissionEntity", description = "权限实体")
public class PermissionEntity extends BaseEntity<PermissionEntity> {

    @Schema(description = "权限唯一标识", example = "inventory:create")
    private String identity;

    @Schema(description = "是否为系统内置权限", example = "false")
    private Boolean isSystem;
}
```

## 访问文档

| 环境 | OpenAPI JSON | Swagger UI | Knife4j UI |
|------|-------------|------------|------------|
| 本地开发 | http://localhost:8080/v3/api-docs | http://localhost:8080/doc.html | http://localhost:8080/doc.html |
| 生产环境 | https://api.kubo.example.com/v3/api-docs | 关闭 | 关闭 |

## 前端类型生成

### 安装依赖

```bash
pnpm add -D openapi-typescript-codegen
```

### 生成命令

```bash
# 开发环境
pnpm run api:generate:dev

# 生产环境
pnpm run api:generate:prod

# 自定义地址
API_DOC_URL=http://192.168.1.100:8080/v3/api-docs pnpm run api:generate
```

### 生成产物结构

```
src/api/generated/
├── core/
│   ├── axiosInstance.ts      # Axios 实例配置
│   └── index.ts
├── models/
│   ├── *.ts                  # 所有 Schema 类型定义
│   └── index.ts
├── services/
│   ├── *.ts                  # 每个 Tag 对应一个 Service
│   └── index.ts
├── enums.ts                  # 枚举类型
└── index.ts                  # 统一导出
```

### 使用示例

```typescript
// 2. 使用类型
import type { PermissionEntity } from '@/api/generated'

// 1. 直接使用生成的 Service
import { permissionService } from '@/api/generated'

// 3. 手动封装的业务 API (推荐)
import { permissionApi } from '@/api/system'

// 获取权限树
const tree = await permissionService.getTree()

const permission: PermissionEntity = {
  identity: 'inventory:create',
  name: '新建库存',
  type: 2,
  parentId: 0,
}

const tree = await permissionApi.getTree()
```

## 开发工作流

### 1. 后端开发完成接口
```bash
# 1. 编写 Controller + Entity + 注解
# 2. 启动后端服务
./mvnw spring-boot:run
```

### 2. 前端生成类型
```bash
# 确保后端已启动
pnpm run api:generate:dev
```

### 3. 前端使用类型开发
```typescript
// 直接使用生成的类型，享受完整类型提示
const { data } = await permissionApi.getTree()
```

### 4. 提交代码
```bash
# 生成的类型文件不提交 (gitignore 已配置)
# 只需提交手动编写的业务代码
git add src/api/system.ts src/api/types.ts
git commit -m "feat: 权限管理页面"
```

## CI/CD 集成

### GitHub Actions 示例

```yaml
# .github/workflows/api-docs.yml
name: API Docs & Types

on:
  push:
    paths:
      - 'SPMS-Server/**/*.java'
  pull_request:
    paths:
      - 'SPMS-Server/**/*.java'

jobs:
  generate-docs:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'

      - name: Start Backend
        run: |
          cd SPMS-Server
          ./mvnw spring-boot:run -Dspring-boot.run.profiles=local &
          sleep 30  # 等待启动

      - name: Generate Frontend Types
        run: |
          cd SPMS-Web
          pnpm install
          API_DOC_URL=http://localhost:8080/v3/api-docs pnpm run api:generate

      - name: Check Type Changes
        run: |
          git diff --exit-code src/api/generated/ || (
            echo "API 类型有变更，请前端同步更新"
            exit 1
          )
```

## 常见问题

### Q: 生成的类型报错 "Cannot find namespace 'axios'"
**A**: 确保 `@types/axios` 已安装，或在 `tsconfig.json` 添加 `"types": ["axios"]`

### Q: 枚举类型生成异常
**A**: 检查后端枚举是否使用 `@Schema(enum = [...])` 或 `allowableValues`

### Q: 日期类型生成成 string 而非 Date
**A**: OpenAPI 规范中 `format: date-time` 会生成 `string`，前端需手动转换或使用 `@vueuse/core` 的 `useDateFormat`

### Q: 文件上传/下载类型不正确
**A**: 确保 Controller 方法使用 `@RequestPart` + `MultipartFile`，且 `@Schema(type = "string", format = "binary")`

### Q: 生成的代码不符合项目规范
**A**: 可通过 `--templates` 自定义模板，或生成后通过脚本后处理

## 最佳实践

1. **接口优先开发**: 先定义 OpenAPI 规范，再实现后端/前端
2. **版本管理**: 生产环境 API 文档按版本管理 (`/v3/api-docs/v1`, `/v3/api-docs/v2`)
3. **破坏性变更检测**: CI 中集成 `openapi-diff` 检测 Breaking Changes
4. **类型安全**: 前端严禁使用 `any`，所有 API 调用必须有类型
5. **文档即代码**: 保持文档与代码同步，文档即最新契约

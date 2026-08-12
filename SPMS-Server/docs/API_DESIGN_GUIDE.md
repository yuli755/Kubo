# API 设计规范指南

> **版本**：v1.0  
> **状态**：🟢 已定稿，强制执行  
> **适用范围**：Kubo 平台所有对外 API (REST/gRPC/Dubbo)

---

## 1. 设计原则

| 原则 | 说明 |
|------|------|
| **资源导向** | API 围绕资源建模，而非动作/过程 |
| **标准化** | 遵循 RESTful 规范、HTTP 语义、状态码规范 |
| **一致性** | 命名、参数、响应、错误码全平台统一 |
| **版本化** | 显式版本控制、向后兼容、平滑演进 |
| **安全性** | 认证授权、参数校验、速率限制、审计日志 |
| **可观测** | 请求追踪、指标采集、错误追踪、性能监控 |
| **可演进** | 版本化、向后兼容、废弃策略、平滑迁移 |

---

## 2. RESTful API 设计规范

### 2.1 资源命名规范

| 资源类型 | URL 格式 | 示例 |
|----------|----------|------|
| **集合资源** | `/api/v{version}/{resource-plural}` | `/api/v1/inventories` |
| **单个资源** | `/api/v{version}/{resource-plural}/{id}` | `/api/v1/inventories/123` |
| **子资源集合** | `/api/v{version}/{parent}/{parentId}/{child-plural}` | `/api/v1/inventories/123/flows` |
| **子资源单个** | `/api/v{version}/{parent}/{parentId}/{child-plural}/{childId}` | `/api/v1/inventories/123/flows/456` |
| **动作/操作** | `/api/v{version}/{resource-plural}/{id}/actions/{action}` | `/api/v1/inventories/123/actions/publish` |

**命名约定**：
- 资源名使用 **复数、小写、kebab-case**：`inventories`, `work-orders`, `purchase-orders`
- 避免使用下划线、驼峰、单数形式
- 标识符使用 `id` (数字) 或 `code` (业务编码)

### 2.2 HTTP 方法语义

| 方法 | 语义 | 幂等性 | 安全性 | 典型响应码 |
|------|------|--------|--------|------------|
| `GET` | 查询资源 | ✅ | ✅ | 200, 404 |
| `POST` | 创建资源 / 执行动作 | ❌ | ❌ | 201, 400, 409 |
| `PUT` | 全量更新资源 | ✅ | ❌ | 200, 404, 409 |
| `PATCH` | 部分更新资源 | ✅ | ❌ | 200, 404, 409 |
| `DELETE` | 删除资源 | ✅ | ❌ | 204, 404, 409 |

### 2.3 统一查询参数

| 参数 | 类型 | 说明 | 示例 |
|------|------|------|------|
| `page` | integer | 页码 (从 1 开始) | `page=1` |
| `pageSize` | integer | 每页条数 (最大 500) | `pageSize=20` |
| `sort` | string | 排序字段，前缀 `-` 表示降序 | `sort=-createTime,code` |
| `filter` | object | 过滤条件 (JSON 编码) | `filter={"status":"PUBLISHED"}` |
| `fields` | string | 返回字段白名单 | `fields=id,name,code` |
| `include` | string | 关联资源展开 | `include=warehouse,material` |

**分页响应标准格式**：
```json
{
  "content": [...],
  "totalElements": 1000,
  "totalPages": 50,
  "size": 20,
  "number": 1,
  "first": true,
  "last": false,
  "empty": false
}
```

### 2.4 统一响应格式

#### 成功响应
```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-08-01T12:00:00.000Z",
  "traceId": "a1b2c3d4e5f6"
}
```

#### 列表/分页响应
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "content": [...],
    "totalElements": 1000,
    "totalPages": 50,
    "size": 20,
    "number": 1
  },
  "timestamp": "2026-08-01T12:00:00.000Z",
  "traceId": "a1b2c3d4e5f6"
}
```

#### 空响应 (DELETE 等)
```json
{
  "code": 204,
  "message": "success",
  "timestamp": "2026-08-01T12:00:00.000Z",
  "traceId": "a1b2c3d4e5f6"
}
```

### 2.5 错误响应格式

```json
{
  "code": 4001,
  "message": "参数校验失败: pageSize 必须大于 0",
  "data": {
    "field": "pageSize",
    "rejectedValue": -1,
    "constraint": "必须大于 0"
  },
  "timestamp": "2026-08-01T12:00:00.000Z",
  "traceId": "a1b2c3d4e5f6",
  "path": "/api/v1/inventories/page"
}
```

---

## 3. 状态码规范

| 状态码 | 含义 | 使用场景 |
|--------|------|----------|
| **200** | OK | GET/PUT/PATCH 成功 |
| **201** | Created | POST 创建成功 (返回 Location 头) |
| **204** | No Content | DELETE 成功、无返回体 |
| **400** | Bad Request | 参数校验失败、请求体格式错误 |
| **401** | Unauthorized | 未认证、Token 过期/无效 |
| **403** | Forbidden | 权限不足 |
| **404** | Not Found | 资源不存在 |
| **409** | Conflict | 资源冲突 (并发冲突、唯一约束) |
| **422** | Unprocessable Entity | 语义校验失败 (业务规则校验) |
| **429** | Too Many Requests | 限流 |
| **500** | Internal Server Error | 服务端未知错误 |
| **503** | Service Unavailable | 服务不可用 (熔断、维护) |

---

## 4. 请求/响应规范

### 4.1 请求头规范

| Header | 必填 | 说明 | 示例 |
|--------|------|------|------|
| `Authorization` | ✅ | Bearer Token | `Bearer eyJhbGciOiJIUzI1NiIs...` |
| `X-Tenant-Id` | ✅ | 租户 ID | `1001` |
| `Idempotency-Key` | ⭕ | 幂等键 (POST/PUT/PATCH) | `uuid-v4` |
| `X-Request-Time` | ✅ | 请求时间戳 (ms) | `1722489600000` |
| `X-Trace-Id` | ⭕ | 链路追踪 ID | `a1b2c3d4e5f6` |
| `Accept-Language` | ⭕ | 语言偏好 | `zh-CN,en-US;q=0.9` |
| `Content-Type` | ✅ | 请求体类型 | `application/json` |

### 4.2 响应头规范

| Header | 说明 | 示例 |
|--------|------|------|
| `Content-Type` | 响应体类型 | `application/json;charset=UTF-8` |
| `X-Trace-Id` | 链路追踪 ID | `a1b2c3d4e5f6` |
| `X-Response-Time` | 响应耗时 (ms) | `45` |
| `RateLimit-Limit` | 限流总额度 | `1000` |
| `RateLimit-Remaining` | 剩余额度 | `999` |
| `RateLimit-Reset` | 重置时间戳 | `1722489600` |
| `Deprecation` | API 废弃标记 | `true` |
| `Sunset` | 停用时间 | `Sat, 01 Jan 2027 00:00:00 GMT` |
| `Link` | 后继版本链接 | `<https://api.kubo.example.com/api/v2/>; rel="successor-version"` |

---

## 5. 幂等性设计

### 5.1 幂等键规范

| 特性 | 规范 |
|------|------|
| **Header 名称** | `Idempotency-Key` |
| **格式** | UUID v4 (推荐) 或 业务唯一标识 |
| **长度限制** | 1-128 字符 |
| **作用范围** | 租户级 + 用户级 + 幂等键 |
| **有效期** | 24 小时 (可配置) |

### 5.2 幂等适用场景

| 场景 | 是否必须 | 说明 |
|------|----------|------|
| POST 创建资源 | ✅ 必须 | 防止重复提交 |
| PUT/PATCH 更新 | ✅ 必须 | 防止重复提交 |
| POST 执行动作 (发布/审批) | ✅ 必须 | 业务幂等 |
| GET 查询 | ❌ 不需要 | 安全幂等 |
| DELETE | ✅ 推荐 | 防止重复删除 |

### 5.3 幂等响应

```json
// 首次请求
{
  "code": 201,
  "message": "success",
  "data": { "id": 123 }
}

// 重复请求 (相同幂等键)
{
  "code": 200,
  "message": "success",
  "data": { "id": 123 },
  "x-idempotent-replay": "true"
}
```

---

## 6. 过滤与搜索规范

### 6.1 过滤语法

```json
// 简单过滤
GET /api/v1/inventories?filter={"status":"PUBLISHED","warehouseId":1}

// 复杂过滤 (支持操作符)
GET /api/v1/inventories?filter={"quantity":{"$gte":10,"$lte":100},"status":{"$in":["PUBLISHED","CHECKING"]}}

// 支持操作符
$eq    等于 (默认)
$ne    不等于
$gt    大于
$gte   大于等于
$lt    小于
$lte   小于等于
$in    包含
$nin   不包含
$like  模糊匹配 (仅字符串)
$regex 正则匹配 (仅字符串)
$exists 字段存在
$null  字段为空
```

### 6.2 排序规范

```
# 单字段升序
sort=createTime

# 单字段降序
sort=-createTime

# 多字段排序 (先按状态升序，再按创建时间降序)
sort=status,-createTime
```

---

## 7. 文件上传/下载规范

### 7.1 文件上传

```http
POST /api/v1/files/upload
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="inventory.xlsx"
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

<binary data>
------WebKitFormBoundary--
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 123,
    "name": "inventory.xlsx",
    "url": "https://file.kubo.example.com/abc123",
    "size": 102400,
    "type": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
  }
}
```

### 7.2 文件下载

```http
GET /api/v1/files/123/download
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

**响应头**：
```
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
Content-Disposition: attachment; filename="inventory.xlsx"
Content-Length: 102400
```

---

## 8. 导入导出规范

### 8.1 导出

```http
GET /api/v1/inventories/export?filter={...}&fields=id,name,code,quantity
Accept: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
```

**响应**：Excel 文件流

### 8.2 导入

```http
POST /api/v1/inventories/import
Content-Type: multipart/form-data

# 文件字段名: file
# 可选参数: skipHeader=true, batchSize=500
```

**响应**：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 1000,
    "success": 995,
    "failed": 5,
    "errors": [
      {"row": 10, "field": "quantity", "message": "数量必须大于 0"},
      {"row": 25, "field": "materialCode", "message": "物料编码不存在"}
    ]
  }
}
```

---

## 9. 权限控制规范

### 9.1 权限标识格式

```
{module}:{resource}:{action}
# 示例
inventory:material:create
inventory:material:read
inventory:material:update
inventory:material:delete
inventory:material:export
inventory:stock:adjust
```

### 9.2 权限控制层级

| 层级 | 实现方式 | 说明 |
|------|----------|------|
| **接口级** | `@RequirePermission` 注解 + 网关拦截 | 粗粒度 |
| **行级** | MyBatis-Plus 拦截器注入 WHERE | 数据隔离 |
| **列级** | `@Schema(x-mask)` + 前端脱敏 | 敏感字段保护 |

### 9.3 权限响应头

```http
X-Permissions: inventory:material:read,inventory:material:create
X-Permission-Denied: inventory:material:delete
```

---

## 10. 审计日志规范

### 10.1 自动审计字段

所有写操作自动记录：
- `operatorId` / `operatorName` - 操作人
- `operation` - 操作类型 (CREATE/UPDATE/DELETE/PUBLISH)
- `resourceType` / `resourceId` - 资源类型/ID
- `requestParams` - 请求参数 (脱敏)
- `responseData` - 响应数据 (脱敏)
- `ip` / `userAgent` - 客户端信息
- `costTime` - 耗时 (ms)
- `status` - 成功/失败
- `errorMessage` - 错误信息

### 10.2 敏感字段脱敏规则

| 字段类型 | 脱敏策略 |
|----------|----------|
| 手机号 | `138****1234` |
| 身份证 | `110101********1234` |
| 银行卡 | `6225********1234` |
| 邮箱 | `abc***@example.com` |
| 金额 | `****` (需权限可见) |
| 密码 | `******` |

---

## 11. 版本控制与废弃

### 11.1 版本控制
- URL 路径包含大版本：`/api/v1/...`
- 详见 [API_VERSIONING_STRATEGY.md](./API_VERSIONING_STRATEGY.md)

### 11.2 废弃响应头
```http
Deprecation: true
Sunset: Sat, 01 Jan 2027 00:00:00 GMT
Link: <https://api.kubo.example.com/api/v2/>; rel="successor-version"
Warning: 299 - "API v1 is deprecated, will sunset on 2027-01-01. Please migrate to v2."
```

---

## 12. 安全规范

### 12.1 传输安全
- 生产环境强制 HTTPS (TLS 1.2+)
- HSTS、CSP、X-Frame-Options 安全头

### 12.2 输入校验
- 所有输入参数严格校验 (白名单)
- 防 SQL 注入 (参数化查询/ORM)
- 防 XSS (输出编码、CSP)
- 文件上传类型/大小/内容校验

### 12.3 速率限制
| 维度 | 限制策略 |
|------|----------|
| 用户级 | 1000 req/min |
| IP 级 | 5000 req/min |
| 租户级 | 10000 req/min |
| 接口级 | 核心写接口 100 req/min |

---

## 13. 测试规范

### 13.1 契约测试
- Provider: Spring Cloud Contract
- Consumer: Pact
- 每次发布前必须通过契约测试

### 13.2 测试覆盖要求
| 测试类型 | 覆盖率要求 |
|----------|------------|
| 单元测试 | > 80% (核心域 > 95%) |
| 集成测试 | 核心链路 100% |
| 契约测试 | 100% |
| E2E 测试 | 核心流程 100% |
| 性能测试 | 每版本发布前必跑 |

---

## 14. 相关链接

- [RESTful API Design Guidelines](https://github.com/microsoft/api-guidelines/blob/vNext/Guidelines.md)
- [Google API Design Guide](https://cloud.google.com/apis/design)
- [HTTP 状态码规范 (RFC 7231)](https://tools.ietf.org/html/rfc7231)
- [OpenAPI 3.0 规范](https://spec.openapis.org/oas/v3.1.0)
- [JSON:API 规范](https://jsonapi.org/)
- [ADR-001 到 ADR-007](../adr/) - 架构决策记录

---

*文档版本：v1.0*  
*最后更新：2026-08-01*  
*状态：🟢 已定稿，强制执行*
# Kubo Platform Development Guide

> **Version**: 1.0
> **Status**: 🟢 Active
> **Last Updated**: 2026-08-02
> **Audience**: All Developers (Frontend/Backend/DevOps)

---

## 🚀 Quick Start

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK | 17+ | `sdk install java 17.0.10-tem` |
| Node.js | 20+ | `nvm install 20` |
| pnpm | 9+ | `corepack enable && pnpm install -g pnpm` |
| Docker | 24+ | `docker --version` |
| kubectl | 1.28+ | `curl -LO https://dl.k8s.io/release/v1.28.0/bin/linux/amd64/kubectl` |
| Helm | 3.12+ | `curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \| bash` |
| Maven | 3.9+ | 包含在项目 `mvnw` 中 |
| Git | 2.40+ | `git --version` |

### IDE Recommendations

| IDE | Plugins |
|-----|---------|
| **IntelliJ IDEA Ultimate** | Lombok, MyBatis-Plus, Spring Boot, GitToolBox, Rainbow Brackets |
| **VS Code** | Vue Language Features (Volar), TypeScript Vue Plugin, ESLint, Prettier, GitLens |

---

## 🏗️ Project Structure

```
SPMS-Web/                          # 前端 Monorepo (pnpm workspace)
├── apps/
│   ├── kubo-web-main/             # 主应用
│   └── kubo-web-admin/            # 管理后台
├── packages/
│   ├── kubo-web-request/          # 请求网关库
│   ├── kubo-web-query/            # 服务端状态库
│   ├── kubo-web-ws/               # WebSocket 管理库
│   ├── kubo-web-permission/       # 权限引擎库
│   ├── kubo-web-components/       # 业务组件库
│   ├── kubo-web-monitor/          # 性能监控库
│   ├── kubo-web-utils/            # 工具函数库
│   └── kubo-web-config/           # 共享配置
├── scripts/
│   └── generate-api.mjs           # API 类型生成
└── docs/

SPMS-Server/                       # 后端 Maven 多模块
├── kubo-parent/                   # 父工程、依赖管理
├── kubo-common/                   # 公共基础库
│   ├── kubo-common-core/
│   ├── kubo-common-security/
│   ├── kubo-common-resilience/
│   ├── kubo-common-dao/
│   ├── kubo-common-seata/
│   ├── kubo-common-mq/
│   ├── kubo-common-audit/
│   └── kubo-common-permission/
├── kubo-gateway/                  # API 网关
├── kubo-auth/                     # 认证授权中心
├── kubo-system/                   # 系统管理
├── kubo-wms/                      # 仓储核心
├── kubo-mes/                      # 制造执行
├── kubo-factory/                  # 工厂建模
├── kubo-finance/                  # 财务核算
├── kubo-procurement/              # 采购管理
├── kubo-sales/                    # 销售管理
├── kubo-asset/                    # 资产设备
├── kubo-personnel/                # 人事组织
├── kubo-iot/                      # 物联网关
├── kubo-report/                   # 报表分析
├── kubo-notify/                   # 通知中心
├── kubo-file/                     # 文件存储
└── kubo-infra/                    # 基础设施即代码
```

---

## 🔧 Environment Setup

### 1. Clone Repositories

```bash
# 后端
git clone https://git.kubo.example.com/kubo-platform.git SPMS-Server
cd SPMS-Server

# 前端
git clone https://git.kubo.example.com/kubo-web.git SPMS-Web
cd SPMS-Web
```

### 2. Start Local Infrastructure

```bash
# 从 SPMS-Web 根目录
cd SPMS-Web
docker-compose up -d

# 验证服务
docker-compose ps
# 应看到: mysql, redis, rabbitmq, nacos, minio, prometheus, grafana, loki, promtail, skywalking-oap, skywalking-ui, elasticsearch 均为 healthy
```

### 3. Backend Setup

```bash
cd SPMS-Server

# 编译并运行 (开发模式)
./mvnw spring-boot:run -pl kubo-gateway,kubo-auth,kubo-wms -Dspring-boot.run.profiles=local

# 或在 IDE 中直接运行 Application.java
# 依次启动: kubo-gateway -> kubo-auth -> kubo-wms (按依赖顺序)
```

### 4. Frontend Setup

```bash
cd SPMS-Web

# 安装依赖
pnpm install

# 生成 API 类型 (后端需已启动)
pnpm run api:generate:dev

# 启动开发服务器
pnpm run s
# 访问 http://localhost:3000
```

### 5. Verify End-to-End

```bash
# 登录测试
# 账号: 17666666666 / admin@hamm.cn
# 密码: Aa123456

# API 文档
open http://localhost:8080/doc.html

# 监控面板
open http://localhost:3001  # Grafana (admin/admin123)
open http://localhost:8081  # SkyWalking UI
```

---

## 🔄 Daily Development Workflow

### 1. Start Day

```bash
# 同步最新代码
git checkout develop
git pull origin develop

# 同步依赖 (如有变更)
cd SPMS-Server && ./mvnw clean install -DskipTests -q
cd ../SPMS-Web && pnpm install

# 启动基础设施
docker-compose up -d

# 启动后端服务 (按需)
# 推荐: IDE 中 Run Configuration 启动网关、认证、核心业务

# 启动前端
cd SPMS-Web && pnpm run s
```

### 2. Feature Development

```bash
# 创建特性分支
git checkout -b feat/WMS-123-optimize-stock-deduct

# 开发...

# 代码规范检查 (提交前自动执行)
# 后端: mvn checkstyle:check spotless:check
# 前端: pnpm run lint

# 运行测试
# 后端: mvn test
# 前端: pnpm run test:unit

# 提交 (遵循 Conventional Commits)
git add .
git commit -m "feat(wms): 优化库存扣减 Lua 脚本，支持批量预扣减

- 使用 Redis Pipeline 批量预扣减
- 新增库存预热接口
- 单 SKU 扣减 QPS 提升 40%"

# 推送并创建 MR
git push origin feat/WMS-123-optimize-stock-deduct
# 在 GitLab/GitHub 创建 Merge Request
```

### 3. Code Review Checklist

- [ ] 功能正确性：符合需求、边界情况处理
- [ ] 代码规范：命名、格式、注释、无警告
- [ ] 测试覆盖：单测 > 80%，核心链路有集成测试
- [ ] 性能：无 N+1、无内存泄漏、SQL 经 EXPLAIN 优化
- [ ] 安全：参数校验、权限检查、敏感数据脱敏
- [ ] 文档：API 变更同步更新、ADR 记录架构决策
- [ ] 兼容性：API 向后兼容、数据库迁移脚本可回滚

### 4. End of Day

```bash
# 推送进度
git push origin feat/WMS-123-optimize-stock-deduct

# 清理本地临时文件
docker system prune -f  # 谨慎使用
```

---

## 🧪 Testing

### Backend

```bash
# 单元测试
./mvnw test

# 单元测试 + 覆盖率报告
./mvnw test jacoco:report
# 查看: target/site/jacoco/index.html

# 集成测试 (需 Testcontainers)
./mvnw verify -Pintegration-test

# 契约测试
./mvnw test -Pcontract-test
```

### Frontend

```bash
# 单元测试
pnpm run test:unit

# 单元测试 + 覆盖率
pnpm run test:unit -- --coverage

# E2E 测试 (需前后端运行)
pnpm run test:e2e

# 视觉回归测试
pnpm run test:visual
```

---

## 🐛 Debugging

### Backend Debugging

```bash
# IDE 远程调试配置
# JVM 参数: -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

# 日志查看
tail -f logs/kubo-wms.log | grep -E "ERROR|WARN"

# 数据库 SQL 日志 (开发环境)
# application-local.yml 中已开启 show-sql: true

# SkyWalking 链路追踪
open http://localhost:8081
```

### Frontend Debugging

```bash
# Vue DevTools (浏览器扩展)
# 安装 Vue.js devtools

# Vite 代理问题
# 检查 vite.config.mts proxy 配置

# 网络请求调试
# Chrome DevTools -> Network -> 过滤 /api/

# 状态管理调试
# Pinia DevTools (Chrome 扩展)
```

---

## 📦 Build & Release

### Backend

```bash
# 构建所有模块
./mvnw clean package -DskipTests

# 构建特定模块
./mvnw clean package -pl kubo-wms -am -DskipTests

# 构建 Docker 镜像
./mvnw spring-boot:build-image -pl kubo-wms

# 发布到 Maven 私服
./mvnw deploy -DskipTests
```

### Frontend

```bash
# 生产构建
pnpm build

# 构建产物体积报告（首屏 / 分包明细）
pnpm size:report
# 产出: dist/size-report.json（注意：仓库**没有** rollup-plugin-visualizer，
# 没有 `--mode=analyze` 这个模式，历史文档里的 dist/stats.html 是脚手架残留）

# 预览生产构建
pnpm run preview
```

---

## 🔐 Security Practices

- **永远不要** 提交密钥、密码、Token 到 Git
- 使用 `SealedSecrets` 或 Vault 管理敏感配置
- 依赖漏洞扫描：`./mvnw dependency-check:check` / `pnpm audit`
- 定期轮换：JWT Secret、DB 密码、API Keys
- 代码审查关注：SQL 注入、XSS、路径遍历、权限绕过

---

## 📚 Useful Links

| Resource | URL |
|----------|-----|
| 内部 Wiki | https://wiki.kubo.example.com |
| API 文档 (Dev) | http://localhost:8080/doc.html |
| Grafana | http://localhost:3001 |
| SkyWalking | http://localhost:8081 |
| Nacos 控制台 | http://localhost:8848/nacos |
| RabbitMQ 管理 | http://localhost:15672 |
| MinIO Console | http://localhost:9001 |
| GitLab/GitHub | https://git.kubo.example.com |

---

## ❓ FAQ

### Q: 后端启动报错 "Connection refused" 到 MySQL/Redis/Nacos?
**A**: 确保 `docker-compose up -d` 已完全启动，检查 `docker-compose ps` 所有服务为 `healthy`。查看日志：`docker-compose logs mysql`。

### Q: 前端生成 API 类型失败？
**A**: 确保后端已启动且可访问 `http://localhost:8080/v3/api-docs`。检查 `scripts/generate-api.mjs` 中的 URL 配置。

### Q: 修改代码后前端不热更新？
**A**: 检查 `vite.config.mts` 中 `server.hmr` 配置，确保没被防火墙拦截。尝试重启 `pnpm run s`。

### Q: 如何添加新的微服务模块？
**A**: 1) 在 `kubo-parent` 下创建模块 2) 继承 `kubo-common` 依赖 3) 添加到 `kubo-parent/pom.xml` modules 4) 配置 Nacos 注册 5) 更新 ADR-004。

### Q: 本地开发如何模拟多租户？
**A**: Header 传递 `X-Tenant-Id: <tenantId>`，或在 Nacos 配置中设置默认租户。

---

*Last Updated: 2026-08-02*
*Maintainer: Platform Team*

# Kubo Platform Documentation Index

> **Version**: 2026-08-02
> **Status**: 🟢 Active
> **Last Updated**: 2026-08-02

---

## 📁 Documentation Structure

```
SPMS-Web/
├── docs/                           # 前端文档
│   ├── API_DOCS_GUIDE.md           # API 文档生成与类型同步指南
│   └── FRONTEND_I18N_GUIDE.md      # 前端国际化实施指南
│
├── SPMS-Server/
│   └── docs/                       # 后端/全栈文档
│       ├── README.md               # 本索引文件
│       │
│       ├── 01-Planning/            # 规划类文档
│       │   ├── Kubo 平台高并发重构实施路线规划计划.md
│       │   ├── Kubo 平台-前端高并发重构实施路线规划计划.md
│       │   └── 前后端重构规划差距分析报告.md
│       │
│       ├── 02-Architecture/        # 架构决策 (ADR)
│       │   ├── ADR-TEMPLATE.md
│       │   ├── ADR-001-消息队列选型.md
│       │   ├── ADR-002-数据库选型.md
│       │   ├── ADR-003-分布式事务选型.md
│       │   ├── ADR-004-服务框架选型.md
│       │   ├── ADR-005-前端框架选型.md
│       │   ├── ADR-006-Monorepo选型.md
│       │   ├── ADR-007-监控告警体系.md
│       │   └── ADR-008-部署发布策略.md
│       │
│       ├── 03-API/                 # API 设计与规范
│       │   ├── API_DESIGN_GUIDE.md
│       │   ├── API_VERSIONING_STRATEGY.md
│       │   └── API_DOCS_GUIDE.md   # (软链接到前端目录)
│       │
│       ├── 04-Infrastructure/      # 基础设施与协议
│       │   ├── WEBSOCKET_PROTOCOL.md
│       │   ├── DATABASE_MIGRATION_STRATEGY.md
│       │   └── INFRASTRUCTURE_OVERVIEW.md      # ⬜ 待创建
│       │
│       ├── 05-Development/         # 开发规范与指南
│       │   ├── DEVELOPMENT_GUIDE.md            # ⬜ 待创建
│       │   ├── CODE_STYLE_GUIDE.md             # ⬜ 待创建
│       │   ├── GIT_COMMIT_SPEC.md              # ⬜ 待创建
│       │   ├── TESTING_STRATEGY.md             # ⬜ 待创建
│       │   └── SECURITY_GUIDE.md               # ⬜ 待创建
│       │
│       ├── 06-Operations/          # 运维与部署
│       │   ├── DEPLOYMENT_GUIDE.md             # ⬜ 待创建
│       │   ├── RUNBOOK.md                      # ⬜ 待创建
│       │   ├── BACKUP_RECOVERY.md              # ⬜ 待创建
│       │   └── INCIDENT_RESPONSE.md            # ⬜ 待创建
│       │
│       ├── 06-Reference/           # 参考文档
│       │   ├── ERROR_CODES.md                  # ⬜ 待创建 (从 ErrorCode.java 生成)
│       │   ├── CONFIG_REFERENCE.md             # ⬜ 待创建
│       │   └── TROUBLESHOOTING.md              # ⬜ 待创建
│       │
│       └── adr/                    # ADR 归档 (已整理)
│
├── docker-compose.yml              # 本地开发环境编排
├── scripts/
│   └── generate-api.mjs            # API 类型生成脚本
│
├── src/api/                        # 前端 API 客户端
│   ├── axios.ts                    # 统一请求网关
│   ├── error-codes.ts              # 前端错误码枚举
│   ├── index.ts                    # 统一导出
│   ├── permissions.ts              # 权限元数据
│   ├── system.ts                   # 系统模块 API
│   └── types.ts                    # 核心类型定义
│
├── src/locales/                    # 前端国际化 (待完善)
│   └── (see FRONTEND_I18N_GUIDE.md)
│
├── prometheus/                     # Prometheus 配置
│   ├── prometheus.yml
│   └── rules/alerts.yml
│
├── grafana/                        # Grafana 配置
│   ├── datasources/datasources.yaml
│   └── dashboards/cluster-overview.json
│
├── loki/                           # Loki 配置
│   └── local-config.yaml
│
├── promtail/                       # Promtail 配置
│   └── config.yml
│
├── rabbitmq/                       # RabbitMQ 定义
│   └── definitions.json
│
└── init-sql/                       # 数据库初始化脚本 (待补充)
```

---

## 📋 Document Status Legend

| Icon | Status | Meaning |
|------|--------|---------|
| 🟢 | **Done** | 文档完整、已评审、可直接使用 |
| 🟡 | **Draft** | 文档草稿、需评审补充 |
| ⬜ | **Missing** | 文档缺失、需创建 |
| 🔄 | **Sync Needed** | 需与代码同步更新 |

---

## 📚 Core Documents Quick Access

### 🎯 Planning & Strategy
| Document | Status | Purpose |
|----------|--------|---------|
| [Kubo 平台高并发重构实施路线规划计划](./01-Planning/Kubo%20平台高并发重构实施路线规划计划.md) | 🟢 | 后端 16 周重构总规划 |
| [Kubo 平台-前端高并发重构实施路线规划计划](./01-Planning/Kubo%20平台-前端高并发重构实施路线规划计划.md) | 🟢 | 前端 16 周重构总规划 |
| [前后端重构规划差距分析报告](./01-Planning/前后端重构规划差距分析报告.md) | 🟢 | 双规划对齐分析与 P0 阻塞项识别 |

### 🏗️ Architecture Decisions (ADR)
| ADR | Title | Status |
|-----|-------|--------|
| [ADR-001](./02-Architecture/ADR-001-消息队列选型.md) | 消息队列选型: RabbitMQ | 🟢 |
| [ADR-002](./02-Architecture/ADR-002-数据库选型.md) | 数据库选型: MySQL 8.0 InnoDB Cluster | 🟢 |
| [ADR-003](./02-Architecture/ADR-003-分布式事务选型.md) | 分布式事务: Seata AT + 本地消息表 | 🟢 |
| [ADR-004](./02-Architecture/ADR-004-服务框架选型.md) | 服务框架: Spring Cloud Alibaba + Dubbo 3 | 🟢 |
| [ADR-005](./02-Architecture/ADR-005-前端框架选型.md) | 前端框架: Vue 3 + Vite + Pinia + 增强库 | 🟢 |
| [ADR-006](./02-Architecture/ADR-006-Monorepo选型.md) | 代码仓库策略: 前端 pnpm Monorepo | 🟢 |
| [ADR-007](./02-Architecture/ADR-007-监控告警体系.md) | 监控体系: Prometheus + Grafana + SkyWalking + Loki | 🟢 |
| [ADR-008](./02-Architecture/ADR-008-部署发布策略.md) | 部署发布: GitOps + ArgoCD + 金丝雀 | 🟢 |

### 🔌 API & Protocols
| Document | Status | Purpose |
|----------|--------|---------|
| [API_DESIGN_GUIDE](./03-API/API_DESIGN_GUIDE.md) | 🟢 | RESTful 设计规范、错误码、幂等、权限 |
| [API_VERSIONING_STRATEGY](./03-API/API_VERSIONING_STRATEGY.md) | 🟢 | 语义化版本、废弃策略、兼容性矩阵 |
| [API_DOCS_GUIDE](./03-API/API_DOCS_GUIDE.md) | 🟢 | SpringDoc + openapi-typescript-codegen 流程 |
| [WEBSOCKET_PROTOCOL](./04-Infrastructure/WEBSOCKET_PROTOCOL.md) | 🟢 | STOMP over WebSocket 完整协议规范 |

### 🏭 Infrastructure
| Document | Status | Purpose |
|----------|--------|---------|
| [DATABASE_MIGRATION_STRATEGY](./04-Infrastructure/DATABASE_MIGRATION_STRATEGY.md) | 🟢 | 双写迁移、校验、回滚全流程 |
| [INFRASTRUCTURE_OVERVIEW](./04-Infrastructure/INFRASTRUCTURE_OVERVIEW.md) | ⬜ | 基础设施全景图、端口、网络拓扑 |

### 🛠️ Development (待创建)
| Document | Priority | Purpose |
|----------|----------|---------|
| DEVELOPMENT_GUIDE.md | P0 | 新人上手、环境搭建、开发流程、提交规范 |
| CODE_STYLE_GUIDE.md | P1 | 代码风格、命名规范、最佳实践 |
| GIT_COMMIT_SPEC.md | P1 | Conventional Commits、分支策略、MR 流程 |
| TESTING_STRATEGY.md | P1 | 单测/集成/契约/E2E/性能测试策略 |
| SECURITY_GUIDE.md | P1 | 认证授权、数据加密、合规要求 |

### 🚀 Operations (待创建)
| Document | Priority | Purpose |
|----------|----------|---------|
| DEPLOYMENT_GUIDE.md | P0 | 环境部署、ArgoCD 配置、Helm Chart 使用 |
| RUNBOOK.md | P0 | 常用操作、扩缩容、故障排查清单 |
| BACKUP_RECOVERY.md | P1 | 备份策略、恢复演练、RPO/RTO 验证 |
| INCIDENT_RESPONSE.md | P1 | 事故分级、响应流程、复盘模板 |

---

## 🔧 Quick Start: Local Development

```bash
# 1. Start infrastructure
docker-compose up -d

# 2. Verify services
curl http://localhost:8080/actuator/health    # Backend
curl http://localhost:8080/doc.html           # Swagger UI
curl http://localhost:9090/-/healthy          # Prometheus
curl http://localhost:3001/api/health         # Grafana
curl http://localhost:8081/health             # SkyWalking UI
curl http://localhost:9001                    # MinIO Console

# 3. Generate frontend API types
cd SPMS-Web && pnpm install && pnpm run api:generate:dev

# 4. Start frontend dev server
pnpm run s   # http://localhost:3000
```

---

## 📝 Document Maintenance Rules

1. **每次架构变更必须更新对应 ADR**
2. **API 变更需同步更新**：`API_DESIGN_GUIDE` + `ERROR_CODES` + 前端 `error-codes.ts`
3. **新增文档需更新本索引**
4. **每季度 Review 一次文档有效性**
5. **代码即文档**：优先在代码中写注释，文档引用代码而非复制

---

## 📂 File Ownership

| Area | Owner | Reviewers |
|------|-------|-----------|
| Planning | 架构师 | Tech Lead, PM |
| ADR | 架构师 | 全栈团队 |
| API | 后端 Leader | 前端 Leader, QA |
| Infrastructure | DevOps | 架构师 |
| Development | Tech Lead | 全体开发 |
| Operations | DevOps | SRE, 架构师 |

---

*Last Updated: 2026-08-02*
*Next Review: 2026-11-02*

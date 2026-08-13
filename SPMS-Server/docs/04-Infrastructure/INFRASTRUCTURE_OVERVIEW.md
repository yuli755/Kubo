# Kubo Platform Infrastructure Overview

> **Version**: 1.0
> **Status**: 🟢 Active
> **Last Updated**: 2026-08-02

---

## 🏗️ Infrastructure Topology

```
┌─────────────────────────────────────────────────────────────────────────────┐
                            Kubo Platform Infrastructure
└─────────────────────────────────────────────────────────────────────────────┘

                                    ┌──────────────┐
                                    │   Internet   │
                                    └──────┬───────┘
                                           │
                                    ┌──────▼───────┐
                                    │  CloudFlare  │  (WAF, DDoS, CDN)
                                    │   / Nginx    │
                                    └──────┬───────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
              ┌─────▼─────┐          ┌─────▼─────┐          ┌─────▼─────┐
              │  Prod AZ1 │          │  Prod AZ2 │          │  Prod AZ3 │
              │           │          │           │          │           │
              │ ┌───────┐ │          │ ┌───────┐ │          │ ┌───────┐ │
              │ │ K8s   │ │          │ │ K8s   │ │          │ │ K8s   │ │
              │ │ Master│ │          │ │ Master│ │          │ │ Master│ │
              │ └───────┘ │          │ └───────┘ │          │ └───────┘ │
              │           │          │           │          │           │
              │ ┌─────────────────────────────────────────────────────┐ │
              │ │              Worker Nodes (Auto-scaling)            │ │
              │ │  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │ │
              │ │  │WMS   │ │MES   │ │FIN   │ │AUTH  │ │GW    │ ... │ │
              │ │  │ Pods │ │Pods  │ │Pods  │ │Pods  │ │Pods  │     │ │
              │ │  └──────┘ └──────┘ └──────┘ └──────┘ └──────┘     │ │
              │ └─────────────────────────────────────────────────────┘ │
              └─────────────────────────────────────────────────────────┘
                    │                      │                      │
                    └──────────────────────┼──────────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
              ┌─────▼─────┐          ┌─────▼─────┐          ┌─────▼─────┐
              │  MySQL    │          │  Redis    │          │ RabbitMQ  │
              │  Cluster  │          │  Cluster  │          │  Cluster  │
              │  (3主3从) │          │  (6M6S)   │          │  (3节点)  │
              └───────────┘          └───────────┘          └───────────┘
                    │                      │                      │
                    └──────────────────────┼──────────────────────┘
                                           │
                    ┌──────────────────────┼──────────────────────┐
                    │                      │                      │
              ┌─────▼─────┐          ┌─────▼─────┐          ┌─────▼─────┐
              │ MinIO     │          │ ES/       │          │ SkyWalking│
              │ (S3 API)  │          │ ClickHouse│          │ + Loki    │
              └───────────┘          └───────────┘          └───────────┘
```

---

## 🌐 Network & Ports

### External Access (通过 CloudFlare / Nginx)

| Service | Domain | Port | Protocol | Notes |
|---------|--------|------|----------|-------|
| API Gateway | `api.kubo.example.com` | 443 | HTTPS | 统一入口、鉴权、限流 |
| Web App | `app.kubo.example.com` | 443 | HTTPS | 静态资源、SPA |
| Admin Console | `admin.kubo.example.com` | 443 | HTTPS | 管理后台 |
| WebSocket | `ws.kubo.example.com` | 443 | WSS | 实时通信 |

### Internal Cluster Network (Kubernetes Service Mesh)

| Service | Namespace | Service Name | Port | Protocol | Internal DNS |
|---------|-----------|--------------|------|----------|--------------|
| API Gateway | kubo-system | kubo-gateway | 8080 | HTTP/gRPC | kubo-gateway.kubo-system.svc |
| Auth Service | kubo-auth | kubo-auth | 8080/50051 | HTTP/Dubbo | kubo-auth.kubo-auth.svc |
| WMS Service | kubo-wms | kubo-wms | 8080/50051 | HTTP/Dubbo | kubo-wms.kubo-wms.svc |
| MES Service | kubo-mes | kubo-mes | 8080/50051 | HTTP/Dubbo | kubo-mes.kubo-mes.svc |
| Finance Service | kubo-finance | kubo-finance | 8080/50051 | HTTP/Dubbo | kubo-finance.kubo-finance.svc |
| System Service | kubo-system | kubo-system | 8080/50051 | HTTP/Dubbo | kubo-system.kubo-system.svc |

### Infrastructure Direct Access (仅运维/调试)

| Component | Internal Port | External Port (Dev) | Credentials | Notes |
|-----------|---------------|---------------------|-------------|-------|
| MySQL Primary | 3306 | 3306 | kubo/kubo | 仅内网访问 |
| Redis | 6379 | 6379 | - | 密码可选 |
| RabbitMQ AMQP | 5672 | 5672 | kubo/kubo | 管理端 15672 |
| RabbitMQ Mgmt | 15672 | 15672 | kubo/kubo | Web UI |
| Nacos | 8848 | 8848 | nacos/nacos | 配置中心 |
| MinIO API | 9000 | 9000 | kubo/kubo123456 | S3 兼容 |
| MinIO Console | 9001 | 9001 | kubo/kubo123456 | Web UI |
| Prometheus | 9090 | 9090 | - | 监控指标 |
| Grafana | 3000 | 3001 | admin/admin123 | 可视化 |
| SkyWalking UI | 8080 | 8081 | - | 链路追踪 |
| Elasticsearch | 9200 | 9200 | elastic/kubo123456 | 日志存储 |
| Loki | 3100 | 3100 | - | 日志聚合 |

---

## 🗄️ Data Layer Details

### MySQL 8.0 InnoDB Cluster

```yaml
# Cluster Topology
Topology:
  - Primary (AZ1): Read/Write
  - Secondary (AZ2): Read/Write (自动故障转移候选)
  - Secondary (AZ3): Read/Write (自动故障转移候选)
  - Read Replicas (每 AZ 2个): Read Only

# Key Configuration
Settings:
  - GTID Mode: ON
  - Binlog Format: ROW
  - Transaction Isolation: READ-COMMITTED
  - innodb_buffer_pool_size: 70% 内存
  - max_connections: 2000
  - Group Replication: 单主模式 (single-primary)

# Schemas (按微服务隔离)
Schemas:
  - kubo_auth: 认证授权
  - kubo_system: 系统管理
  - kubo_wms: 仓储管理
  - kubo_mes: 制造执行
  - kubo_finance: 财务核算
  - kubo_procurement: 采购管理
  - kubo_sales: 销售管理
  - kubo_asset: 资产设备
  - kubo_personnel: 人事组织
  - kubo_factory: 工厂建模
  - kubo_iot: 物联网关
  - kubo_report: 报表分析
```

### Redis Cluster (6 Master + 6 Slave)

```yaml
Topology:
  - 6 个 Master 分片，每个 1 个 Slave
  - 跨 3 AZ 部署 (每 AZ 2 Master + 2 Slave)
  - 自动故障转移 (Redis Sentinel 模式)

Key Patterns:
  - session:{userId} -> TTL 30min
  - cache:{entity}:{id} -> TTL 10min
  - lock:{resource} -> TTL 30s (Redisson)
  - rate_limit:{key} -> TTL 1min
  - idempotent:{key} -> TTL 24h
  - stock:deduct:{warehouseId}:{skuId} -> TTL 5min
  - perm:rule:{entity}:v{version} -> TTL 1h (本地缓存 + Redis)

Settings:
  - maxmemory: 8GB/节点
  - maxmemory-policy: allkeys-lru
  - appendonly: yes
  - save: 900 1, 300 10, 60 10000
```

### RabbitMQ Cluster (3 节点 Quorum Queues)

```yaml
Topology:
  - 3 节点集群，镜像队列策略
  - Quorum Queues (Raft 共识)
  - 跨 AZ 部署

Exchanges & Queues (关键):
  - kubo.wms.inventory (Topic) -> 16 个分片队列 (库存扣减顺序消费)
  - kubo.mes.workorder (Topic) -> 工单状态变更
  - kubo.finance (Topic) -> 成本核算、凭证生成
  - kubo.notification (Topic) -> 通知推送 (邮件/短信/推送)
  - kubo.audit (Topic) -> 审计日志
  - kubo.dlx.exchange (Topic) -> 死信交换机
  - kubo.delay.exchange (Topic) -> 延时消息 (TTL + DLX)

Policies:
  - ha-quorum: 所有 kubo.* 队列 -> Quorum, 3副本, delivery-limit=5
  - dlx-policy: 死信队列 -> 7天 TTL
```

---

## 📦 Compute Resources (Kubernetes)

### Node Pool Specification

| Pool Name | Instance Type | vCPU | Memory | Disk | Max Nodes | Workloads |
|-----------|---------------|------|--------|------|-----------|-----------|
| system | ecs.g6.xlarge | 4 | 16 GiB | 100 GiB | 3 | 系统组件 |
| general | ecs.g6.2xlarge | 8 | 32 GiB | 200 GiB | 20 | 业务微服务 |
| memory | ecs.r6.2xlarge | 8 | 64 GiB | 200 GiB | 6 | 大内存服务 (报表/ES) |
| cpu | ecs.c6.2xlarge | 8 | 16 GiB | 100 GiB | 6 | 高 CPU 服务 (排程/计算) |
| spot | 混合 | 混合 | 混合 | 100 GiB | 10 | 批处理/非核心 |

### Resource Quotas (Per Namespace)

```yaml
# 生产环境典型配额
resources:
  limits:
    cpu: '20'
    memory: 64Gi
    pods: '100'
    services: '20'
    persistentvolumeclaims: '20'
  requests:
    cpu: '10'
    memory: 32Gi
```

---

## 🔐 Security & Compliance

### Network Policies

```yaml
# 默认拒绝所有入站
DefaultDeny: true

# 允许规则
IngressRules:
  - From: kubo-gateway namespace
    To: All services
    Ports: 8080, 50051
  - From: Same namespace
    To: Same namespace
    Ports: All
  - From: Monitoring namespace
    To: All services
    Ports: 8080, 9090, 11800
  - From: Namespace with label "allow=kubo-internal"
    To: Services with label "expose-internal=true"
```

### TLS / mTLS

| Layer | Implementation |
|-------|----------------|
| External Edge | CloudFlare TLS 1.3 + HSTS |
| Ingress Controller | cert-manager 自动证书 (Let's Encrypt / 内部 CA) |
| Service Mesh (Sidecar) | Istio / Linkerd mTLS (可选，视性能评估) |
| Database | MySQL SSL (强制) |
| Redis | TLS 1.2 (可选，内网可关闭) |
| RabbitMQ | TLS 1.2 (集群间) |

### Secrets Management

| Secret Type | Storage | Rotation |
|-------------|---------|----------|
| DB Password | SealedSecrets (Git) / Vault | 90 天 |
| JWT Secret | SealedSecrets | 180 天 |
| Redis Password | SealedSecrets | 90 天 |
| RabbitMQ Password | SealedSecrets | 90 天 |
| API Keys (第三方) | Vault | 按供应商策略 |
| TLS Certificates | cert-manager (自动) | 90 天 (自动续期) |

---

## 📊 Observability Stack

### Metrics (Prometheus + Grafana)

| Component | Retention | Scrape Interval | Storage |
|-----------|-----------|-----------------|---------|
| Prometheus (主) | 15d | 15s | 本地 TSDB |
| VictoriaMetrics (长期) | 1y | 30s | 对象存储 |
| Alertmanager | - | - | 配置持久化 |

**关键 Dashboards**:
- Cluster Overview (集群健康、QPS、错误率、延迟)
- JVM Deep Dive (内存、GC、线程、类加载)
- Business API (业务 QPS、错误率、P99、吞吐)
- Database (连接数、慢查询、复制延迟、锁等待)
- Redis (内存、命中率、连接数、命令耗时)
- RabbitMQ (队列堆积、吞吐、消费延迟、连接数)
- SkyWalking Topology (服务拓扑、调用链路、错误热力图)

### Tracing (SkyWalking)

| Setting | Value |
|---------|-------|
| Sampling Rate | 100% (核心链路) / 10% (非核心) |
| Trace Retention | 7 天 (热) + 30 天 (冷) |
| Storage | Elasticsearch |
| Agent | Java Agent (无侵入) + Go/Node SDK |

### Logging (Loki + Promtail + Grafana)

| Setting | Value |
|---------|-------|
| Retention | 30 天 |
| Labels | namespace, pod, container, app, level |
| Query Language | LogQL |
| Integration | Grafana Explore + SkyWalking Trace 关联 |

---

## 🔄 CI/CD Pipeline

```
Git Push (feature/fix)
    │
    ▼
┌─────────────────────────────────────┐
│  Validate Stage                     │
│  - Lint (ESLint/Checkstyle)         │
│  - Type Check (TS/javac)            │
│  - Security Scan (SAST/Dep)         │
│  - Unit Tests                       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  Build Stage                        │
│  - Compile                          │
│  - Integration Tests                │
│  - Contract Tests                   │
│  - Docker Build + Push              │
│  - SBOM 生成                        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  Deploy Dev (Auto)                  │
│  - ArgoCD 自动同步                  │
│  - Smoke Tests                      │
└─────────────────────────────────────┘
    │
    ▼ (Tag Push v1.2.3)
┌─────────────────────────────────────┐
│  Deploy Staging (Manual)            │
│  - ArgoCD 同步                      │
│  - E2E Tests                        │
│  - 性能基线对比                      │
└─────────────────────────────────────┘
    │
    ▼ (Release 分支 + 审批)
┌─────────────────────────────────────┐
│  Deploy Prod (Manual + Sync Window) │
│  - Canary 5% → 20% → 50% → 100%     │
│  - AnalysisTemplate 自动分析        │
│  - 自动回滚阈值                     │
└─────────────────────────────────────┘
```

---

## 📈 Capacity Planning (10k Users Baseline)

| Component | Current Spec | Peak Load | Headroom | Scale Trigger |
|-----------|--------------|-----------|----------|---------------|
| API Gateway | 3×4C16G | 5k RPS | 60% | CPU > 70% |
| Auth Service | 3×2C4G | 2k RPS | 50% | CPU > 70% |
| WMS Service | 5×4C8G | 3k RPS | 40% | CPU > 70% / Queue > 1000 |
| MES Service | 5×4C8G | 2k RPS | 50% | CPU > 70% |
| Finance Service | 3×4C8G | 1k RPS | 60% | CPU > 70% |
| MySQL Primary | 8C64G | 5k QPS | 40% | Connections > 80% |
| Redis | 6×4C16G | 50k ops/s | 50% | Memory > 80% |
| RabbitMQ | 3×4C8G | 20k msg/s | 60% | Queue > 10k |

---

## 🚨 Disaster Recovery

| Scenario | RTO | RPO | Recovery Procedure |
|----------|-----|-----|-------------------|
| 单 Pod 失效 | < 30s | 0 | K8s 自动重调度 |
| 单节点失效 | < 2min | 0 | Pod 漂移 + PV 重新挂载 |
| 单 AZ 失效 | < 5min | 0 | 多 AZ 自动故障转移 |
| MySQL 主库故障 | < 30s | 0 | Group Replication 自动选主 |
| Redis 主节点故障 | < 10s | < 1s | Sentinel 自动故障转移 |
| RabbitMQ 节点故障 | < 30s | 0 | Quorum Queue 自动重选举 |
| 整个集群灭亡 | < 4h | < 1h | 异地备份恢复 + DNS 切换 |
| 数据损坏/误删 | < 1h | < 15min | PITR (Point-in-Time Recovery) |

---

## 📋 Maintenance Windows

| Environment | Window | Frequency | Sync Policy |
|-------------|--------|-----------|-------------|
| Dev | 随时 | 持续 | AutoSync |
| Staging | 每日 02:00-04:00 | 每日 | 手动同步 |
| Prod | 周一-周五 02:00-04:00 | 工作日 | 手动同步 + SyncWindow |

---

## 📞 Emergency Contacts

| Role | Primary | Secondary | Escalation |
|------|---------|-----------|------------|
| Platform On-Call | 张三 (138-xxxx-xxxx) | 李四 | 技术总监 |
| Database On-Call | 王五 (139-xxxx-xxxx) | 赵六 | DBA Lead |
| Security On-Call | 陈七 (137-xxxx-xxxx) | 钱八 | CISO |
| Network On-Call | 孙九 (136-xxxx-xxxx) | 周十 | 运维总监 |

---

*Last Updated: 2026-08-02*
*Next Review: 2026-11-02*

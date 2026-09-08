# Kubo 平台后端系统二次改造设计文档

> **版本**: v2.0
> **状态**: 🟡 已过时（历史设计稿，落地口径见 `../数据权限目标态落地施工图.md`）
> **最后更新**: 2026-08-13
> **适用场景**: 10,000+ 工厂用户 · 7×24 高并发 · 99.99% 可用性

---

## 目录

1. [文档概述](#1-文档概述)
2. [现状分析](#2-现状分析)
3. [目标架构总览](#3-目标架构总览)
4. [技术栈转变对照](#4-技术栈转变对照)
5. [微服务拆分设计](#5-微服务拆分设计)
6. [数据层设计](#6-数据层设计)
7. [缓存架构设计](#7-缓存架构设计)
8. [消息队列设计](#8-消息队列设计)
9. [权限模型设计](#9-权限模型设计)
10. [API 与通信设计](#10-api-与通信设计)
11. [注册中心与配置中心](#11-注册中心与配置中心)
12. [分布式事务设计](#12-分布式事务设计)
13. [可观测性设计](#13-可观测性设计)
14. [部署架构设计](#14-部署架构设计)
15. [安全架构设计](#15-安全架构设计)
16. [性能目标与 SLA](#16-性能目标与-sla)
17. [分阶段迁移策略](#17-分阶段迁移策略)
18. [风险评估与应对](#18-风险评估与应对)

---

## 1. 文档概述

### 1.1 编写目的

本文档旨在系统性地阐述 Kubo 平台后端从单体架构向高并发微服务架构的二次改造设计方案。读者通过本文档可全面了解：

- 当前系统架构的现状与瓶颈
- 目标架构的设计理念与演进方向
- 每个技术维度的具体改造方案
- 改造后的性能预期与效果

### 1.2 适用范围

- **改造对象**: SPMS-Server (AirPower 6.4.3 + Spring Boot 2.x + JPA)
- **目标形态**: Kubo 微服务集群 (Spring Cloud Alibaba + Spring Boot 3.2)
- **业务场景**: 智能生产管理 (WMS/MES/工厂/财务/IoT)
- **用户规模**: 10,000+ 并发用户

### 1.3 阅读指南

| 章节 | 适合读者 | 阅读时长 |
|------|----------|----------|
| 第 1-3 章 | 所有人 | 10 分钟 |
| 第 4-5 章 | 架构师、技术负责人 | 20 分钟 |
| 第 6-12 章 | 后端开发工程师 | 40 分钟 |
| 第 13-16 章 | DevOps、运维 | 20 分钟 |
| 第 17-18 章 | 项目经理、架构师 | 15 分钟 |

---

## 2. 现状分析

### 2.1 当前架构概览

```
┌────────────────────────────────────────────────────────────┐
│                    Kubo 当前单体架构                         │
├────────────────────────────────────────────────────────────┤
│                    Nginx 反向代理                            │
├────────────────────────────────────────────────────────────┤
│              Spring Boot 2.x + AirPower 6.4.3               │
│  ┌──────┬──────┬──────┬──────┬──────┬──────┬──────┬──────┐ │
│  │ WMS  │ MES  │Factory│Asset │Channel│Person│System│ IoT  │ │
│  │module│module│module│module│module│module│module│module│ │
│  └──────┴──────┴──────┴──────┴──────┴──────┴──────┴──────┘ │
│   ┌──────────┐  ┌──────────┐  ┌───────────────────────────┐ │
│   │  JPA/Hibernate │  │  Redis  │  │   WebSocket + MQTT    │ │
│   └──────────┘  └──────────┘  └───────────────────────────┘ │
│   ┌──────────────────────────────────────────────────────┐  │
│   │              MySQL 8.0 单节点                          │  │
│   └──────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────┘
```

### 2.2 当前技术栈明细

| 维度 | 技术选型 | 版本 | 备注 |
|------|----------|------|------|
| **框架** | AirPower 父框架 | 6.4.3 | 封装了 Spring Boot + JPA，灵活性受限 |
| **JDK** | JDK 17 | 17.x | 可保留升级 |
| **ORM** | Spring Data JPA (Hibernate) | 2.x | 复杂查询性能差，N+1 问题 |
| **数据库** | MySQL 8.0 | 8.0.36 | 单节点，无高可用 |
| **缓存** | Redis (Spring Data Redis) | 6.x | 单节点，无集群 |
| **消息** | @EnableScheduling 定时任务 | — | 无消息队列，缺乏异步能力 |
| **WebSocket** | @EnableWebSocket | 原生 | 无 STOMP，无集群支持 |
| **IoT** | MQTT (直接集成) | — | 与业务代码耦合 |
| **注册中心** | 无 | — | 硬编码服务地址 |
| **配置中心** | 无 | — | 配置文件本地管理 |
| **网关** | 无 | — | Nginx 直接代理 |
| **监控** | 无 | — | 无 APM/链路追踪 |
| **日志** | Logback 文件 | — | 无集中式日志 |
| **部署** | 单 JAR 部署 | — | 无容器化 |
| **权限** | 内置 RBAC | — | 无行级/列级 |

### 2.3 当前存在的关键问题

#### 🔴 P0 - 阻塞性问题

| 问题 | 描述 | 影响 |
|------|------|------|
| **单体架构瓶颈** | 所有模块部署在同一个 JVM 进程中 | 无法独立扩缩容，一个模块 OOM 拖垮全部 |
| **数据库单点故障** | MySQL 单节点，无主从/集群 | 数据库宕机 = 全站不可用，RPO > 0 |
| **无消息队列** | 定时任务轮询替代异步消息 | 高并发下数据库连接池耗尽，任务丢失 |
| **缺少缓存分层** | 仅有一层 Redis，无本地缓存 | 热点数据频繁穿透到数据库 |

#### 🟡 P1 - 重要问题

| 问题 | 描述 | 影响 |
|------|------|------|
| JPA 性能瓶颈 | 复杂关联查询生成低效 SQL | 报表/看板页面加载 > 5s |
| 无服务治理 | 无熔断/降级/限流 | 雪崩效应风险 |
| 无分布式事务 | @Transactional 跨数据源失效 | 数据不一致风险 |
| 配置硬编码 | 环境变更需重新打包 | 部署效率低 |
| 无链路追踪 | 调用链无法可视化 | 问题定位困难 |

#### 🟢 P2 - 优化问题

| 问题 | 描述 | 影响 |
|------|------|------|
| 日志分散 | 各服务器本地文件 | 排查问题需登录多台机器 |
| 手动部署 | 无 CI/CD | 发布效率低，回滚困难 |
| 无监控告警 | 被动发现故障 | MTTR 长 |
| 无容器化 | 环境不一致 | 部署差异导致 Bug |

---

## 3. 目标架构总览

### 3.1 目标架构全景

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Kubo 微服务架构全景                               │
├─────────────────────────────────────────────────────────────────────────────┤
│                             客户端层 (PC/移动/第三方)                        │
├─────────────────────────────────────────────────────────────────────────────┤
│                     Spring Cloud Gateway (K8s Ingress 前置)                  │
│                  ┌─── 限流 ─── 鉴权 ─── 路由 ─── 日志 ─── 熔断 ───┐          │
├─────────────────────────────────────────────────────────────────────────────┤
│                              业务微服务层                                    │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │kubo-gateway  │kubo-auth│  │kubo-wms │  │kubo-mes │  │kubo-factory        │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │kubo-finance  │kubo-iot│kubo-wechat│kubo-open │kubo-personnel           │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐          │
│  │kubo-system   │kubo-asset│kubo-channel│kubo-chat  │kubo-mcp  │          │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘          │
│  ┌──────────────────────────────────────────────────────────────────────┐   │
│  │                        kubo-common (公共库)                          │   │
│  └──────────────────────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────────────────────┤
│                         基础设施层                                           │
│  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐         │
│  │  Nacos 2.3         │  │  MySQL 8.0        │  │  Redis Cluster    │         │
│  │  注册中心+配置中心  │  │  InnoDB Cluster   │  │  6M/6S + Redisson │         │
│  └───────────────────┘ └───────────────────┘ └───────────────────┘         │
│  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐         │
│  │  RabbitMQ 3.12     │  │  MinIO            │  │  Seata Server     │         │
│  │  Quorum Queues     │  │  对象存储          │  │  AT 事务模式      │         │
│  └───────────────────┘ └───────────────────┘ └───────────────────┘         │
├─────────────────────────────────────────────────────────────────────────────┤
│                         可观测性层                                           │
│  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐         │
│  │  Prometheus +      │  │  SkyWalking       │  │  Loki +          │         │
│  │  Grafana           │  │  APM 链路追踪     │  │  Promtail 日志   │         │
│  └───────────────────┘ └───────────────────┘ └───────────────────┘         │
├─────────────────────────────────────────────────────────────────────────────┤
│                         部署层 (K8s + Helm + ArgoCD)                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 3.2 架构设计原则

| 原则 | 说明 | 实践 |
|------|------|------|
| **高可用** | 消除单点，故障自动转移 | 多副本部署、MySQL InnoDB Cluster、Redis Cluster |
| **可扩展** | 按需水平扩展 | 微服务独立扩缩容、无状态设计 |
| **可观测** | 全链路可观测 | Metrics + Tracing + Logging 三位一体 |
| **安全** | 纵深防御 | RBAC + ABAC、行级/列级权限、TLS 1.3 |
| **自治** | 服务独立演进 | 每个微服务独立数据库、独立部署 |
| **自动化** | 减少人工干预 | GitOps + CI/CD + 自动化测试 |

---

## 4. 技术栈转变对照

### 4.1 核心技术栈对比

| 维度 | 当前 (改造前) | 目标 (改造后) | 转变理由 |
|------|--------------|--------------|----------|
| **框架体系** | AirPower 6.4.3 (Spring Boot 2.x) | Spring Cloud Alibaba 2023.x | 微服务治理能力(注册/配置/网关) |
| **JDK** | JDK 17 | JDK 17/21 (推荐 21) | 虚拟线程、ZGC、模式匹配 |
| **Spring Boot** | 2.x (AirPower 内置) | 3.2+ | 虚拟线程支持、GraalVM、安全更新 |
| **ORM** | Spring Data JPA (Hibernate) | **MyBatis-Plus 3.5.x** | 复杂 SQL 优化、分页插件、性能可控 |
| **分页方案** | JPA Pageable (自动 count) | **MyBatis-Plus PaginationInnerInterceptor** | 智能 Count 优化、防 SQL 注入 |
| **数据库** | MySQL 8.0 单节点 | **MySQL InnoDB Cluster (3节点)** | 高可用、RPO=0、RTO<30s |
| **缓存** | Redis 单节点 | **Redis Cluster (6M/6S) + Redisson + Caffeine** | 缓存分层、分布式锁、高可用 |
| **消息队列** | @EnableScheduling 轮询 | **RabbitMQ 3.12 Quorum Queues** | 可靠异步、削峰填谷、最终一致性 |
| **服务注册** | 无 (硬编码) | **Nacos 2.3** | 服务发现、健康检查、权重路由 |
| **配置中心** | 本地配置文件 | **Nacos 2.3 (配置中心)** | 动态配置、灰度发布、版本管理 |
| **API 网关** | Nginx 直接代理 | **Spring Cloud Gateway + Nginx** | 统一鉴权、限流、路由、熔断 |
| **远程调用** | HTTP 直连 (RestTemplate) | **Dubbo 3.x Triple** | 高性能 RPC、服务治理、负载均衡 |
| **分布式事务** | @Transactional (单机) | **Seata AT** | 跨服务数据一致性、自动补偿 |
| **分库分表** | 无 | **ShardingSphere-JDBC 5.4+** | 水平扩展、数据分片、读写分离 |
| **WebSocket** | @EnableWebSocket (原生) | **STOMP 1.2 + RabbitMQ STOMP** | 集群化消息推送、订阅管理 |
| **IoT** | MQTT 直接集成 | **MQTT Broker (EMQX) + kubo-iot** | 解耦、规模化设备管理 |
| **监控** | 无 | **Prometheus + Grafana + SkyWalking** | 指标、链路追踪、APM |
| **日志** | Logback 文件 | **Loki + Promtail** | 集中式日志、全文检索 |
| **部署** | 单 JAR 物理机 | **K8s + Helm + ArgoCD** | 容器化、自动化部署、弹性伸缩 |
| **CI/CD** | 手动部署 | **GitHub Actions + ArgoCD GitOps** | 自动化发布、回滚、审批 |
| **API 文档** | 无/手动 | **SpringDoc OpenAPI 3 + Knife4j** | 自动生成、在线调试、代码生成 |

### 4.2 依赖版本矩阵

```xml
<!-- Spring Cloud Alibaba BOM -->
<spring-cloud-alibaba.version>2023.0.1.0</spring-cloud-alibaba.version>
<spring-boot.version>3.2.5</spring-boot.version>
<spring-cloud.version>2023.0.1</spring-cloud.version>

<!-- 核心框架 -->
<mybatis-plus.version>3.5.7</mybatis-plus.version>
<dubbo.version>3.3.2</dubbo.version>
<seata.version>1.8.0</seata.version>
<shardingsphere.version>5.4.1</shardingsphere.version>

<!-- 中间件客户端 -->
<redisson.version>3.33.0</redisson.version>
<rabbitmq-client.version>3.12.14</rabbitmq-client.version>
<nacos-client.version>2.3.2</nacos-client.version>

<!-- 可观测性 -->
<micrometer.version>1.12.5</micrometer.version>
<skywalking.version>9.7.0</skywalking.version>

<!-- 数据库 -->
<mysql-connector.version>8.0.33</mysql-connector.version>
<druid.version>1.2.22</druid.version>

<!-- 其他 -->
<caffeine.version>3.1.8</caffeine.version>
<springdoc.version>2.5.0</springdoc.version>
<knife4j.version>4.5.0</knife4j.version>
```

---

## 5. 微服务拆分设计

### 5.1 拆分原则

```
拆分策略:
  1. 按业务域拆分 (DDD 限界上下文)
  2. 每个微服务拥有独立数据库 (数据隔离)
  3. 高内聚低耦合 (服务间通过 API/MQ 通信)
  4. 拆分粒度适中 (避免过度拆分导致运维复杂)
  5. 考虑团队结构 (康威定律)
```

### 5.2 微服务清单

| 编号 | 服务名 | 来源模块 | 职责 | 独立数据库 | 预估 QPS |
|------|--------|---------|------|-----------|---------|
| 1 | **kubo-gateway** | 新建 | API 网关、统一鉴权、限流 | 否 | 5000+ |
| 2 | **kubo-auth** | personnel + 新建 | 认证授权、OAuth2.1、Token 管理 | `kubo_auth` | 2000+ |
| 3 | **kubo-wms** | wms | 仓储管理 (入库/出库/库存/移库) | `kubo_wms` | 3000+ |
| 4 | **kubo-mes** | mes | 生产管理 (工单/BOM/工艺/排产) | `kubo_mes` | 2000+ |
| 5 | **kubo-factory** | factory | 工厂建模 (仓库/车间/产线/储位) | `kubo_factory` | 1000+ |
| 6 | **kubo-finance** | channel (部分) | 财务管理 (采购价/售价/成本) | `kubo_finance` | 500+ |
| 7 | **kubo-iot** | iot | IoT 数据采集、设备管理、参数上报 | `kubo_iot` | 5000+ |
| 8 | **kubo-asset** | asset | 资产管理 (合同/设备/物料) | `kubo_asset` | 500+ |
| 9 | **kubo-channel** | channel | 渠道管理 (客户/供应商/采购/销售) | `kubo_channel` | 500+ |
| 10 | **kubo-personnel** | personnel | 组织架构 (部门/用户/角色) | `kubo_personnel` | 1000+ |
| 11 | **kubo-system** | system | 系统管理 (配置/编码/文件/菜单/权限) | `kubo_system` | 500+ |
| 12 | **kubo-open** | open | 开放平台 (API 管理/OAuth2/三方登录) | `kubo_open` | 500+ |
| 13 | **kubo-wechat** | wechat | 微信集成 (公众号/小程序/企业微信) | `kubo_wechat` | 200+ |
| 14 | **kubo-chat** | chat | 即时通讯 (消息/房间/成员) | `kubo_chat` | 2000+ |
| 15 | **kubo-mcp** | mcp | MCP 协议适配 (AI 集成) | `kubo_mcp` | 100+ |
| 16 | **kubo-common** | 新建 | 公共模块 (工具类/通用模型/异常定义) | 否 (JAR) | — |

### 5.3 模块 → 微服务映射关系

```
当前模块 (12)              目标微服务 (16)
─────────────────────────────────────────────
wms/          ───────▶    kubo-wms
mes/          ───────▶    kubo-mes
factory/      ───────▶    kubo-factory
iot/          ───────▶    kubo-iot
asset/        ───────▶    kubo-asset
channel/      ───┬───▶    kubo-channel
                 └───▶    kubo-finance (采购价/售价/成本)
personnel/    ───┬───▶    kubo-personnel
                 └───▶    kubo-auth (认证部分)
system/       ───────▶    kubo-system
open/         ───────▶    kubo-open
wechat/       ───────▶    kubo-wechat
chat/         ───────▶    kubo-chat
mcp/          ───────▶    kubo-mcp
(新建)        ───────▶    kubo-gateway
(新建)        ───────▶    kubo-common
```

### 5.4 服务间调用关系

```
                    ┌──────────────────────────────────────┐
                    │            kubo-gateway               │
                    │   (认证/限流/路由/日志/熔断)           │
                    └──┬───────────────────────────────┬───┘
                       │                              │
              ┌────────▼────────┐           ┌─────────▼─────────┐
              │   kubo-auth     │           │  业务微服务集群    │
              │  (Token/MFA/SSO)│           │                   │
              └─────────────────┘           │  ┌─────────────┐  │
                                            │  │ kubo-wms    │  │
       ┌───────────────────────────────────▶│  │ kubo-mes    │  │
       │                                    │  │ kubo-factory│  │
       │  (Dubbo 3.x Triple RPC)            │  │ kubo-finance│  │
       │                                    │  │ kubo-iot    │  │
       │  ┌──────────────────────┐          │  │ kubo-asset  │  │
       │  │   RabbitMQ (异步)    │◀─────────│  │ kubo-asset  │  │
       │  │   - 事件通知         │          │  │   ...       │  │
       │  │   - 数据同步         │          │  └─────────────┘  │
       │  │   - 任务分发         │          └────────────────────┘
       │  └──────────────────────┘
       │
       │  ┌──────────────────────┐
       │  │   Nacos (注册中心)    │
       │  └──────────────────────┘
```

### 5.5 服务间通信协议

| 通信场景 | 协议 | 说明 |
|----------|------|------|
| **同步查询** | Dubbo 3.x Triple (gRPC) | 高性能 RPC，服务间实时数据查询 |
| **异步事件** | RabbitMQ (AMQP 0-9-1) | 业务事件通知、状态变更广播 |
| **文件上传** | MinIO 预签名 URL | 客户端直传，服务端不做中转 |
| **实时推送** | STOMP over WebSocket | 浏览器端实时消息推送 |
| **IoT 数据** | MQTT 5.0 (EMQX) | 设备数据采集，通过 RabbitMQ 桥接 |

---

## 6. 数据层设计

### 6.1 数据库架构

```
┌──────────────────────────────────────────────────────────────┐
│                    MySQL InnoDB Cluster                        │
│                                                               │
│    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐ │
│    │   Primary     │────▶│  Secondary   │────▶│  Secondary   │ │
│    │   (读写)      │     │  (只读)       │     │  (只读)       │ │
│    └──────────────┘     └──────────────┘     └──────────────┘ │
│           │                     │                     │        │
│    ┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐  │
│    │  MySQL 8.0  │     │  MySQL 8.0  │     │  MySQL 8.0  │  │
│    │  Group Repl │     │  Group Repl │     │  Group Repl │  │
│    └─────────────┘     └─────────────┘     └─────────────┘  │
│                                                               │
│  Group Replication: Single-Primary Mode                       │
│  - 自动故障检测: 5s 超时                                   │
│  - 自动选主: 多数派投票                                   │
│  - RPO = 0 (同步复制)                                     │
│  - RTO < 30s (自动切换)                                   │
│  - 读写分离: Primary 写, Secondary 读                      │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 数据库拆分策略

#### 按微服务拆分 (Database per Service)

| 数据库 | 所属服务 | 说明 |
|--------|----------|------|
| `kubo_auth` | kubo-auth | 认证信息、Token、OAuth2 客户端 |
| `kubo_wms` | kubo-wms | 库存、出入库、移库单 |
| `kubo_mes` | kubo-mes | 工单、BOM、工艺、排产计划 |
| `kubo_factory` | kubo-factory | 仓库、车间、产线、储位 |
| `kubo_finance` | kubo-finance | 价格、成本、核算 |
| `kubo_iot` | kubo-iot | 设备、参数、采集数据 |
| `kubo_asset` | kubo-asset | 合同、设备资产、物料 |
| `kubo_channel` | kubo-channel | 客户、供应商、采购/销售订单 |
| `kubo_personnel` | kubo-personnel | 部门、用户、角色 |
| `kubo_system` | kubo-system | 系统配置、编码规则、菜单 |
| `kubo_open` | kubo-open | 开放平台、三方应用 |
| `kubo_wechat` | kubo-wechat | 微信集成 |
| `kubo_chat` | kubo-chat | 即时通讯 |
| `kubo_mcp` | kubo-mcp | MCP 协议 |

#### 大数据量表分片策略

```yaml
# ShardingSphere-JDBC 5.4+ 分片配置示例
# 适用场景: IoT 采集数据、操作日志、消息记录

# 1. IoT 采集数据 (按月分片)
tables:
  iot_parameter_record:
    actualDataNodes: ds$->{0..1}.iot_parameter_record_$->{2026_01..2026_12}
    tableStrategy:
      standard:
        shardingColumn: collect_time
        shardingAlgorithmName: iot_month_sharding
    keyGenerateStrategy:
      column: id
      keyGeneratorName: snowflake

  # 2. 操作日志 (按月分片)
  sys_operation_log:
    actualDataNodes: ds0.sys_operation_log_$->{2026_01..2026_12}
    tableStrategy:
      standard:
        shardingColumn: created_at
        shardingAlgorithmName: log_month_sharding

  # 3. 消息记录 (按用户 ID 取模分片)
  chat_message:
    actualDataNodes: ds$->{0..3}.chat_message
    databaseStrategy:
      standard:
        shardingColumn: sender_id
        shardingAlgorithmName: chat_db_sharding
```

### 6.3 ORM 迁移: JPA → MyBatis-Plus

#### 对比分析

| 能力 | Spring Data JPA | MyBatis-Plus | 优势 |
|------|----------------|--------------|------|
| **单表操作** | 自动 | 自动 (BaseMapper) | 持平 |
| **复杂查询** | JPQL/Criteria (难优化) | XML/注解 (SQL 可控) | ✅ MP |
| **分页** | Pageable (自动 Count) | PaginationInnerInterceptor | ✅ MP |
| **批量操作** | 逐条 (性能差) | 批量插入/更新 | ✅ MP |
| **多表关联** | @ManyToOne (N+1 风险) | 手写 SQL (完全可控) | ✅ MP |
| **动态 SQL** | Specification (复杂) | QueryWrapper/LambdaQueryWrapper | ✅ MP |
| **逻辑删除** | @SQLDelete | @TableLogic | ✅ MP |
| **乐观锁** | @Version | @Version | 持平 |
| **审计字段** | @CreatedDate/@LastModifiedDate | @TableField(fill = FieldFill.INSERT) | 持平 |
| **代码生成** | 逆向工程 | MyBatis-Plus Generator | ✅ MP |

#### 实体类迁移示例

```java
// ── 改造前: JPA 实体 ──
@Entity
@Table(name = "inventory")
@Data
public class InventoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "material_id")
    private Long materialId;

    @Column(name = "warehouse_id")
    private Long warehouseId;

    @Column(name = "available_qty")
    private BigDecimal availableQty;

    @Column(name = "locked_qty")
    private BigDecimal lockedQty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", insertable = false, updatable = false)
    private MaterialEntity material;  // N+1 风险

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

// ── 改造后: MyBatis-Plus 实体 ──
@Data
@TableName("inventory")
@EqualsAndHashCode(callSuper = true)
public class InventoryEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("material_id")
    private Long materialId;

    @TableField("warehouse_id")
    private Long warehouseId;

    @TableField("available_qty")
    private BigDecimal availableQty;

    @TableField("locked_qty")
    private BigDecimal lockedQty;

    // 关联查询手动写 XML，避免 N+1
    @TableField(exist = false)
    private String materialName;  // 手动映射
}

// ── Mapper 接口 ──
@Mapper
public interface InventoryMapper extends BaseMapper<InventoryEntity> {
    // 复杂查询写在 XML 中
    Page<InventoryVO> selectPageWithMaterial(Page<InventoryEntity> page,
                                              @Param("query") InventoryQuery query);
}

// ── Service 层 ──
@Service
public class InventoryService extends ServiceImpl<InventoryMapper, InventoryEntity> {
    // 继承 ServiceImpl 获得基础 CRUD

    public Page<InventoryVO> page(InventoryQuery query) {
        Page<InventoryEntity> page = new Page<>(query.getPage(), query.getSize());
        // 数据权限自动注入 (见第 9 章)
        DataPermissionHelper.startContext(query.getTenantId());
        try {
            return baseMapper.selectPageWithMaterial(page, query);
        } finally {
            DataPermissionHelper.clearContext();
        }
    }
}
```

### 6.4 数据库连接池

```yaml
# Druid 连接池配置 (每个微服务独立配置)
spring:
  datasource:
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      # 核心配置
      initial-size: 10
      min-idle: 20
      max-active: 100
      max-wait: 5000

      # 连接有效性
      validation-query: SELECT 1
      validation-query-timeout: 3000
      test-on-borrow: false
      test-on-return: false
      test-while-idle: true
      time-between-eviction-runs-millis: 60000
      min-evictable-idle-time-millis: 300000

      # 监控
      filters: stat,wall,slf4j
      web-stat-filter:
        enabled: true
        url-pattern: /druid/*
        exclusions: '*.js,*.gif,*.jpg,*.png,*.css,*.ico,/druid/*'
      stat-view-servlet:
        enabled: true
        login-username: admin
        login-password: admin123
        allow: 127.0.0.1,10.0.0.0/8

      # 慢 SQL 记录
      filter:
        stat:
          log-slow-sql: true
          slow-sql-millis: 2000
          merge-sql: true
```

### 6.5 读写分离

```yaml
# ShardingSphere-JDBC 读写分离配置
spring:
  shardingsphere:
    datasource:
      names: primary,secondary-1,secondary-2
      primary:
        type: com.alibaba.druid.pool.DruidDataSource
        url: jdbc:mysql://primary:3306/kubo_wms?useSSL=false&serverTimezone=Asia/Shanghai
        username: kubo
        password: kubo
      secondary-1:
        ...
      secondary-2:
        ...
    rules:
      readwrite-splitting:
        data-sources:
          wms-ds:
            write-data-source-name: primary
            read-data-source-names:
              - secondary-1
              - secondary-2
            load-balancer-name: round_robin
        load-balancers:
          round_robin:
            type: ROUND_ROBIN
```

---

## 7. 缓存架构设计

### 7.1 缓存分层架构

```
┌──────────────────────────────────────────────────────────────┐
│                     缓存分层架构                               │
│                                                               │
│   ┌──────────────────────────────────────────────────────┐    │
│   │  Layer 1: Caffeine (本地缓存)                         │    │
│   │  - 容量: 10000 条                                     │    │
│   │  - 过期: 写入后 5 分钟                                 │    │
│   │  - 适用: 字典数据、配置、用户信息、权限规则              │    │
│   │  - 穿透: 自动回源 Redis                                │    │
│   └──────────────────────┬───────────────────────────────┘    │
│                          │ 缓存未命中                          │
│   ┌──────────────────────▼───────────────────────────────┐    │
│   │  Layer 2: Redis Cluster (分布式缓存)                   │    │
│   │  - 6 主 6 从 (最多可扩展到 12 主 12 从)                 │    │
│   │  - 内存: 每节点 8GB                                    │    │
│   │  - 淘汰策略: allkeys-lru                               │    │
│   │  - 持久化: AOF (everysec) + RDB                       │    │
│   │  - 适用: 业务缓存、分布式锁、计数器、Session            │    │
│   └──────────────────────┬───────────────────────────────┘    │
│                          │ 缓存未命中                          │
│   ┌──────────────────────▼───────────────────────────────┐    │
│   │  Layer 3: MySQL (最终数据源)                          │    │
│   │  - 查询结果 -> 回填 Layer 2 -> 回填 Layer 1           │    │
│   └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 缓存策略

```java
// ── 缓存管理器 (Caffeine + Redis 两级) ──
@Component
public class CacheManager<K, V> {

    // 本地缓存 (Caffeine)
    private final Cache<K, V> localCache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(5, TimeUnit.MINUTES)
        .recordStats()
        .build();

    // 分布式缓存 (Redisson)
    @Autowired
    private RedissonClient redissonClient;

    // 缓存穿透保护 (布隆过滤器)
    @Autowired
    private BloomFilter<String> bloomFilter;

    public V get(K key, String cacheKey, Class<V> type, CacheLoader<K, V> loader) {
        // 1. 布隆过滤器拦截 (不存在直接返回 null)
        if (!bloomFilter.mightContain(cacheKey)) {
            return null;
        }

        // 2. 查本地缓存
        V value = localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }

        // 3. 查 Redis
        RMapCache<String, V> redisCache = redissonClient.getMapCache(cacheKey);
        value = redisCache.get(key);
        if (value != null) {
            localCache.put(key, value);  // 回填本地缓存
            return value;
        }

        // 4. 查数据库 (分布式锁防止缓存击穿)
        String lockKey = "lock:" + cacheKey + ":" + key;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            if (lock.tryLock(100, 3000, TimeUnit.MILLISECONDS)) {
                // 双重检查
                value = redisCache.get(key);
                if (value != null) {
                    localCache.put(key, value);
                    return value;
                }

                // 查询数据库
                value = loader.load(key);
                if (value != null) {
                    redisCache.put(key, value, 30, TimeUnit.MINUTES);
                    localCache.put(key, value);
                    bloomFilter.put(cacheKey);
                }
                return value;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        // 获取锁失败, 查数据库 (兜底)
        return loader.load(key);
    }
}

// ── 缓存穿透解决方案 ──
@Configuration
public class CacheProtectionConfig {

    @Bean
    public BloomFilter<String> bloomFilter(RedissonClient redissonClient) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter("bloom:cache-keys");
        filter.tryInit(5_000_000L, 0.01);  // 500万预期, 1% 误判率
        return filter;
    }
}
```

### 7.3 缓存场景对照

| 缓存场景 | 策略 | 过期时间 | 本地缓存 | 分布式缓存 | 说明 |
|----------|------|---------|---------|-----------|------|
| 用户信息 | 写入后过期 | 30 分钟 | ✅ | ✅ | 用户信息变更率低 |
| 权限规则 | 定时刷新 | 5 分钟 | ✅ | ✅ | 通过 RabbitMQ 广播刷新 |
| 字典数据 | 定时刷新 | 1 小时 | ✅ | ✅ | 改动的极少 |
| 编码规则 | 写入后过期 | 1 小时 | ✅ | ✅ | 业务配置 |
| 库存快照 | 写入后过期 | 1 分钟 | ❌ | ✅ | 实时性要求高 |
| 物料信息 | 写入后过期 | 30 分钟 | ✅ | ✅ | 基础数据 |
| 价格信息 | 写入后过期 | 10 分钟 | ✅ | ✅ | 价格变动不频繁 |
| Session | 写入后过期 | 30 分钟 | ❌ | ✅ | 必须共享 |
| 分布式锁 | 手动释放 | 自定义 | ❌ | ✅ | Redisson 锁 |
| 计数器 | 持久化 | 自定义 | ❌ | ✅ | Redis INCR |

### 7.4 缓存失效方案

```java
// ── 缓存失效事件 (通过 RabbitMQ 广播) ──
@Component
public class CacheInvalidationListener {

    @RabbitListener(queues = "cache.invalidation.queue")
    public void handleCacheInvalidation(CacheInvalidationEvent event) {
        // 1. 清除本地缓存
        localCacheManager.evict(event.getCacheKey());

        // 2. 清除 Redis 缓存
        redissonClient.getMapCache(event.getCacheRegion()).fastRemove(event.getCacheKey());

        // 3. 记录缓存失效日志
        log.info("Cache invalidated: region={}, key={}", event.getCacheRegion(), event.getCacheKey());
    }
}
```

---

## 8. 消息队列设计

### 8.1 RabbitMQ 拓扑

```
┌─────────────────────────────────────────────────────────────────────┐
│                        RabbitMQ 3.12 集群                             │
│                                                                      │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐         │
│   │  Rabbit-1     │────▶│  Rabbit-2    │────▶│  Rabbit-3    │         │
│   │  (Quorum)     │     │  (Quorum)    │     │  (Quorum)    │         │
│   └──────────────┘     └──────────────┘     └──────────────┘         │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │                     Exchange 定义                             │   │
│   │  ┌─────────────┐  ┌─────────────┐  ┌────────────────────┐   │   │
│   │  │ kubo.direct  │  │ kubo.topic  │  │ kubo.dlx           │   │   │
│   │  │ (点对点)     │  │ (发布订阅)   │  │ (死信交换机)       │   │   │
│   │  └─────────────┘  └─────────────┘  └────────────────────┘   │   │
│   └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│   ┌──────────────────────────────────────────────────────────────┐   │
│   │                     Queue 清单                                │   │
│   │                                                                  │
│   │  业务队列:                                                        │
│   │  ├─ kubo.order.create (订单创建, 持久化, 死信 -> kubo.dlx)        │
│   │  ├─ kubo.inventory.change (库存变更, 持久化, 严格顺序)             │
│   │  ├─ kubo.notification (通知推送, 高吞吐)                          │
│   │  ├─ kubo.iot.data (IoT 数据采集, 大量, 短 TTL)                    │
│   │  ├─ kubo.log.audit (审计日志, 持久化, 批量消费)                    │
│   │  └─ kubo.task.delay (延迟任务, DLX+TTL 实现)                      │
│   │                                                                  │
│   │  系统队列:                                                        │
│   │  ├─ cache.invalidation.queue (缓存失效广播)                        │
│   │  ├─ permission.sync.queue (权限规则同步)                           │
│   │  └─ config.change.queue (配置变更通知)                             │
│   └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 消息模式详解

#### 模式 1: 可靠异步 (本地消息表 + 消息队列)

```
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  业务服务  │     │  本地事务  │     │  消息表   │     │  目标服务  │
│          │     │          │     │          │     │          │
│  1. 业务操作────▶ 2. 开启本地事务      │          │          │
│          │     │          │     │          │          │          │
│          │     │ 3. 业务数据写入─────────▶ 4. 消息记录写入 │          │
│          │     │          │     │          │          │          │
│          │     │ 5. 提交事务─────────────▶ 6. 定时任务扫描──▶ 7. 发送 MQ│
│          │     │          │     │          │          │          │
│          │     │          │     │ 8. 删除消息记录◀── 9. ACK 确认◀──│
│          │     │          │     │          │          │          │
│          │     │          │     │ 10. 失败重试 (最多 15 次)        │
│          │     │          │     │          │          │          │
│          │     │          │     │ 11. 超过次数 -> 人工介入队列    │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
```

**适用场景**: 订单创建、库存扣减、财务记账等需要最终一致性的核心操作

#### 模式 2: 严格顺序队列 (Sharded Ordered)

```
┌─────────────────────────────────────────────────────────────────┐
│                    严格顺序消息 (库存变更)                        │
│                                                                  │
│  库存变更事件:                                                    │
│  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐  ┌──────┐              │
│  │ SKU-A │  │ SKU-B │  │ SKU-A │  │ SKU-C │  │ SKU-A │  (消息)  │
│  └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘  └──┬───┘              │
│     │         │         │         │         │                   │
│     ▼         ▼         ▼         ▼         ▼                   │
│  ┌─────────────────────────────────────────────────────────┐     │
│  │  一致性哈希路由器 (按 SKU ID 哈希到固定分区)              │     │
│  └────┬────────────┬────────────┬─────────────────────────┘     │
│       │            │            │                               │
│       ▼            ▼            ▼                               │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐                         │
│  │分区 0    │  │分区 1    │  │分区 2    │  (每个分区单线程消费)    │
│  │ SKU-A  ▶│  │ SKU-B  ▶│  │ SKU-C  ▶│                         │
│  │ SKU-A  ▶│  │         │  │         │                         │
│  │ SKU-A  ▶│  │         │  │         │                         │
│  └─────────┘  └─────────┘  └─────────┘                         │
│                                                                  │
│  效果: 同一个 SKU 的消息严格顺序消费, 不同 SKU 并行处理           │
└─────────────────────────────────────────────────────────────────┘
```

**适用场景**: 库存变更、订单状态机、财务流水

#### 模式 3: 延迟消息 (DLX+TTL)

```
┌─────────────────────────────────────────────────────────────────┐
│                    延迟消息 (订单超时取消)                        │
│                                                                  │
│  1. 订单创建 → 发送消息到 delay.queue (TTL = 30 分钟)            │
│     ┌──────────────────────────────────────────────────────┐     │
│     │  delay.queue                                         │     │
│     │  x-message-ttl: 1800000 (30 分钟)                    │     │
│     │  x-dead-letter-exchange: kubo.dlx                    │     │
│     │  x-dead-letter-routing-key: order.cancel             │     │
│     └──────────────────────┬───────────────────────────────┘     │
│                             │ 30 分钟后                          │
│                             ▼                                    │
│  2. 消息过期 → 自动转入 kubo.dlx 死信交换机                       │
│     ┌──────────────────────────────────────────────────────┐     │
│     │  kubo.dlx (死信交换机)                               │     │
│     └──────────────────────┬───────────────────────────────┘     │
│                             │                                    │
│                             ▼                                    │
│  3. 检查订单状态 → 如果未支付则取消订单                         │
│     ┌──────────────────────────────────────────────────────┐     │
│     │  order.cancel.queue                                  │     │
│     └──────────────────────────────────────────────────────┘     │
│                                                                  │
│  支持多级延迟: 30min / 1h / 2h / 4h / 8h / 24h                 │
└─────────────────────────────────────────────────────────────────┘
```

**适用场景**: 订单超时取消、任务超时处理、定时提醒

### 8.3 消息队列配置

```java
// ── RabbitMQ 统一配置 ──
@Configuration
public class RabbitMQConfig {

    // ============ Exchanges ============
    @Bean
    public DirectExchange kuboDirectExchange() {
        return ExchangeBuilder.directExchange("kubo.direct")
            .durable(true)
            .build();
    }

    @Bean
    public TopicExchange kuboTopicExchange() {
        return ExchangeBuilder.topicExchange("kubo.topic")
            .durable(true)
            .build();
    }

    @Bean
    public DirectExchange kuboDlxExchange() {
        return ExchangeBuilder.directExchange("kubo.dlx")
            .durable(true)
            .build();
    }

    // ============ 业务队列 ============
    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable("kubo.order.create")
            .withArgument("x-queue-type", "quorum")          // Quorum 队列
            .withArgument("x-delivery-limit", 15)             // 最大重试 15 次
            .withArgument("x-dead-letter-exchange", "kubo.dlx")
            .withArgument("x-dead-letter-routing-key", "order.create.failed")
            .build();
    }

    @Bean
    public Queue inventoryChangeQueue() {
        return QueueBuilder.durable("kubo.inventory.change")
            .withArgument("x-queue-type", "quorum")
            .withArgument("x-delivery-limit", 15)
            .build();
    }

    @Bean
    public Queue delayQueue() {
        return QueueBuilder.durable("kubo.task.delay")
            .withArgument("x-queue-type", "quorum")
            .withArgument("x-dead-letter-exchange", "kubo.dlx")
            .withArgument("x-dead-letter-routing-key", "task.delay.execute")
            .build();
    }

    // ============ 绑定 ============
    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
            .to(kuboDirectExchange())
            .with("order.create");
    }

    @Bean
    public Binding inventoryChangeBinding() {
        return BindingBuilder.bind(inventoryChangeQueue())
            .to(kuboDirectExchange())
            .with("inventory.change");
    }
}
```

### 8.4 消息可靠性保证

```yaml
# 生产者可靠性
Producer:
  - 确认机制: publisher-confirm-type=correlated
  - 退回机制: publisher-returns=true
  - 本地消息表: 保证业务操作与消息发送原子性
  - 重试策略: 指数退避 (1s, 3s, 9s, 27s, ...)

# 消费者可靠性
Consumer:
  - 手动 ACK: acknowledge-mode=manual
  - 重试策略: 本地重试 3 次 → 死信队列 → 人工介入
  - 幂等消费: 全局唯一 ID (业务 + 消息 ID)
  - 批量消费: 根据业务场景选择合适的 batchSize

# 消费端幂等实现
@Component
public class IdempotentConsumer {
    @Autowired
    private StringRedisTemplate redisTemplate;

    public boolean tryProcess(String messageId, String businessKey) {
        String idempotentKey = "idempotent:" + messageId;
        // SET NX 实现幂等
        Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(idempotentKey, "1", Duration.ofHours(24));
        if (Boolean.TRUE.equals(success)) {
            return true;  // 首次消费
        }
        // 已消费过, 检查业务状态
        return checkBusinessStatus(businessKey);
    }
}
```

---

## 9. 权限模型设计

### 9.1 权限模型升级: RBAC → RBAC + ABAC

```
┌─────────────────────────────────────────────────────────────────┐
│                    Kubo 权限模型架构                              │
│                                                                  │
│   ┌──────────────────────────────┐                              │
│   │       用户 (User)             │                              │
│   └──────────┬───────────────────┘                              │
│              │ 所属                                               │
│              ▼                                                    │
│   ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│   │       部门 (Department)       │  │  角色 (Role)              │ │
│   └──────────────────────────────┘  └──────────┬───────────────┘ │
│                                                 │ 拥有             │
│                                                 ▼                 │
│   ┌──────────────────────────────┐  ┌──────────────────────────┐ │
│   │       租户 (Tenant)           │  │  权限 (Permission)        │ │
│   │  (数据隔离基本单元)             │  │  - 菜单权限                │ │
│   └──────────────────────────────┘  │  - 操作权限 (按钮级)        │ │
│                                     │  - 数据权限 (行级)          │ │
│   ┌──────────────────────────────┐  │  - 字段权限 (列级)          │ │
│   │   ABAC 规则引擎               │  └──────────────────────────┘ │
│   │  (动态属性评估)                │                               │
│   └──────────────────────────────┘                               │
└─────────────────────────────────────────────────────────────────┘
```

### 9.2 数据权限 (行级)

#### 实现方案

```java
// ── 1. 数据权限注解 ──
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataPermission {
    /**
     * 表别名 (用于 SQL 注入)
     */
    String alias() default "";

    /**
     * 权限维度
     */
    DataDimension[] dimensions() default {
        DataDimension.TENANT,  // 租户隔离 (必选)
        DataDimension.WAREHOUSE,  // 仓库维度
        DataDimension.DEPARTMENT  // 部门维度
    };
}

public enum DataDimension {
    TENANT,        // 租户: tenant_id = ?
    WAREHOUSE,     // 仓库: warehouse_id IN (用户授权的仓库列表)
    DEPARTMENT,    // 部门: dept_id IN (用户所属部门及子部门)
    WORKSHOP,      // 车间: workshop_id IN (用户授权的车间列表)
    PROJECT        // 项目: project_id IN (用户授权的项目列表)
}

// ── 2. MyBatis-Plus 数据权限拦截器 ──
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class DataPermissionInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler handler = (StatementHandler) invocation.getTarget();
        String originalSql = handler.getBoundSql().getSql();

        // 获取当前用户权限上下文
        DataPermissionContext context = DataPermissionContextHolder.get();
        if (context == null || context.isSkipPermission()) {
            return invocation.proceed();
        }

        // 注入行级权限条件
        String newSql = injectPermissionCondition(originalSql, context);
        ReflectUtil.setFieldValue(handler.getBoundSql(), "sql", newSql);

        return invocation.proceed();
    }

    private String injectPermissionCondition(String sql, DataPermissionContext context) {
        // 解析 SQL，找到主表
        // 追加 WHERE 条件:
        //   AND tenant_id = ?
        //   AND warehouse_id IN (?, ?, ?)
        //   AND dept_id IN (SELECT id FROM dept WHERE path LIKE ?)
        StringBuilder condition = new StringBuilder();

        // 1. 租户隔离 (所有表必选)
        if (context.getTenantId() != null) {
            condition.append(" AND tenant_id = ")
                .append(context.getTenantId());
        }

        // 2. 仓库维度
        if (CollectionUtils.isNotEmpty(context.getWarehouseIds())) {
            String ids = context.getWarehouseIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            condition.append(" AND warehouse_id IN (").append(ids).append(")");
        }

        // 3. 部门维度 (含子部门)
        if (CollectionUtils.isNotEmpty(context.getDeptIds())) {
            String ids = context.getDeptIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
            condition.append(" AND dept_id IN (").append(ids).append(")");
        }

        // 注入到 WHERE 前
        if (condition.length() > 0) {
            String upperSql = sql.toUpperCase();
            int whereIndex = upperSql.lastIndexOf("WHERE");
            if (whereIndex > 0) {
                // 已有 WHERE 条件, 追加 AND
                sql = sql.substring(0, whereIndex + 5)
                    + condition.toString()
                    + sql.substring(whereIndex + 5);
            } else {
                // 没有 WHERE, 追加 WHERE
                int orderByIndex = upperSql.lastIndexOf("ORDER BY");
                if (orderByIndex > 0) {
                    sql = sql.substring(0, orderByIndex)
                        + " WHERE 1=1 " + condition.toString()
                        + sql.substring(orderByIndex);
                } else {
                    sql = sql + " WHERE 1=1 " + condition.toString();
                }
            }
        }
        return sql;
    }
}

// ── 3. 使用示例 ──
@Service
public class InventoryService {

    @DataPermission(alias = "inv", dimensions = {DataDimension.TENANT, DataDimension.WAREHOUSE})
    public Page<InventoryVO> page(InventoryQuery query) {
        // 拦截器自动注入:
        //   SELECT inv.* FROM inventory inv
        //   WHERE 1=1
        //     AND inv.tenant_id = 1001
        //     AND inv.warehouse_id IN (1, 2, 3)
        //     AND inv.material_id = ?
        return baseMapper.selectPage(query);
    }
}
```

### 9.3 字段权限 (列级)

```java
// ── 1. 脱敏注解 ──
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveField {
    /**
     * 脱敏策略
     */
    MaskStrategy mask() default MaskStrategy.PARTIAL;

    /**
     * 白名单角色 (可查看明文)
     */
    String[] rolesAllowed() default {};

    /**
     * 敏感分类
     */
    String category() default "PII";
}

public enum MaskStrategy {
    FULL("****"),              // 全隐藏
    PARTIAL("138****1234"),    // 部分显示 (前3后4)
    LAST4("****1234"),         // 仅后4位
    FIRST2("13********"),      // 仅前2位
    HASH("sha256:xxx"),        // 哈希值
    NONE("原值")               // 白名单可见
}

// ── 2. 脱敏切面 (AOP) ──
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ColumnMaskingAspect {

    @Around("execution(* com.kubo..*Service.page*(..)) || execution(* com.kubo..*Service.get*(..))")
    public Object maskResult(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        if (result == null) return null;

        // 获取当前用户角色
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        // 递归脱敏
        maskObject(result, roles);
        return result;
    }

    private void maskObject(Object obj, Set<String> roles) {
        if (obj == null) return;
        if (obj instanceof Collection) {
            ((Collection<?>) obj).forEach(item -> maskObject(item, roles));
            return;
        }
        if (obj instanceof Page) {
            ((Page<?>) obj).getRecords().forEach(record -> maskObject(record, roles));
            return;
        }

        // 反射遍历字段
        for (Field field : getAllFields(obj.getClass())) {
            SensitiveField sensitive = field.getAnnotation(SensitiveField.class);
            if (sensitive == null) continue;

            // 检查白名单
            if (hasAnyRole(roles, sensitive.rolesAllowed())) {
                continue;  // 白名单角色, 不脱敏
            }

            try {
                field.setAccessible(true);
                Object value = field.get(obj);
                if (value instanceof String) {
                    String masked = maskValue((String) value, sensitive.mask());
                    field.set(obj, masked);
                }
            } catch (Exception e) {
                log.warn("Failed to mask field: {}", field.getName(), e);
            }
        }
    }

    private String maskValue(String value, MaskStrategy strategy) {
        return switch (strategy) {
            case FULL -> "****";
            case PARTIAL -> MaskUtils.maskPhone(value);
            case LAST4 -> "****" + value.substring(Math.max(0, value.length() - 4));
            case FIRST2 -> value.substring(0, Math.min(2, value.length())) + "********";
            case HASH -> DigestUtils.sha256Hex(value);
            case NONE -> value;
        };
    }
}
```

### 9.4 权限规则缓存与刷新

```yaml
# 权限规则缓存策略
Permission Cache:
  - 存储: Redis Hash (用户 ID -> 权限规则列表)
  - 本地缓存: Caffeine (用户 ID -> 权限规则列表, 5 分钟过期)
  - 刷新机制:
    1. 用户登录: 加载完整权限到 Redis
    2. 权限变更: 发布 RabbitMQ 事件 → 所有服务实例刷新本地缓存
    3. 定时刷新: 每 5 分钟兜底刷新
    4. 强制刷新: 管理员手动触发

# 刷新流程
Permission Change Flow:
  1. 管理员修改角色权限
  2. kubo-system 更新数据库
  3. 发布 RabbitMQ 事件 (permission.sync.queue)
  4. 所有服务实例消费事件
  5. 清除受影响用户的本地缓存
  6. 用户下次请求时从 Redis 加载最新权限
```

### 9.5 权限评估流程

```
用户请求 ──▶ 1. 认证 (Token 验证)
              │
              ▼
         2. 接口级权限验证 (@RequirePermission)
              │
              ▼
         3. 数据权限 (行级, SQL 拦截器)
              │
              ▼
         4. 字段权限 (列级, AOP 脱敏)
              │
              ▼
        响应返回
```

---

## 10. API 与通信设计

### 10.1 API 规范

```
RESTful API 规范:
  - 基础路径: /api/v1/{service}/{resource}
  - 示例: /api/v1/wms/inventory/page
  - 网关统一前缀: /api

请求格式:
  - Content-Type: application/json
  - 认证: Bearer Token (JWT)
  - 幂等: Idempotency-Key Header (POST/PUT/PATCH)
  - 租户: X-Tenant-Id Header
  - 追踪: X-Trace-Id Header

响应格式:
  {
    "code": 200,          // 业务状态码
    "message": "success",
    "data": { ... },      // 业务数据
    "traceId": "xxx",     // 链路追踪 ID
    "timestamp": "2026-08-13T10:00:00Z"
  }

分页请求:
  GET /api/v1/wms/inventory/page?page=1&size=20&materialId=1001

分页响应:
  {
    "code": 200,
    "data": {
      "records": [...],
      "total": 1000,
      "page": 1,
      "size": 20,
      "pages": 50
    }
  }
```

### 10.2 API 文档生成

```yaml
# 技术选型: SpringDoc OpenAPI 3 + Knife4j 4.5
# 优势:
#   - 符合 OpenAPI 3.0 规范
#   - 支持自动生成接口文档
#   - 提供在线调试 UI
#   - 支持分组聚合 (多微服务)
#   - 可与 openapi-typescript-codegen 配合生成前端类型

# 配置方式:
springdoc:
  api-docs:
    enabled: true
    path: /v3/api-docs
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
  packages-to-scan: com.kubo
  default-produces-media-type: application/json

# Knife4j 增强配置:
knife4j:
  enable: true
  setting:
    language: zh-CN
    enable-swagger-models: true
    enable-document-manage: true
    enable-reload-cache: true
    enable-footer: false
```

### 10.3 前端类型生成

```json
// package.json 脚本 (已配置)
{
  "scripts": {
    "api:generate": "node infra/scripts/generate-api.mjs",
    "api:generate:dev": "API_DOC_URL=http://localhost:8080/v3/api-docs node infra/scripts/generate-api.mjs"
  }
}

// 生成工具: openapi-typescript-codegen
// 生成位置: src/api/generated/
// 输出:
//   - src/api/generated/models/  (接口定义)
//   - src/api/generated/services/ (API 调用)
//   - src/api/generated/core/    (请求工具)
```

### 10.4 错误码规范

```java
// ── 统一错误码定义 ──
public enum ErrorCode {
    // 成功
    SUCCESS(200, "success"),

    // 通用错误 (1000-1999)
    PARAM_VALIDATION_FAILED(1000, "参数校验失败"),
    PARAM_MISSING(1001, "缺少必要参数"),
    RESOURCE_NOT_FOUND(1002, "资源不存在"),
    RESOURCE_ALREADY_EXISTS(1003, "资源已存在"),
    OPERATION_TOO_FREQUENT(1004, "操作太频繁"),
    IDEMPOTENT_CONFLICT(1005, "重复请求"),

    // 认证授权 (2000-2999)
    UNAUTHORIZED(2000, "未登录"),
    TOKEN_EXPIRED(2001, "Token 已过期"),
    TOKEN_INVALID(2002, "Token 无效"),
    FORBIDDEN(2003, "无权限"),
    PERMISSION_DENIED(2004, "权限不足"),
    ACCOUNT_LOCKED(2005, "账号已锁定"),
    ACCOUNT_DISABLED(2006, "账号已禁用"),

    // 业务错误 (3000-3999)
    INVENTORY_SHORTAGE(3000, "库存不足"),
    ORDER_STATUS_INVALID(3001, "订单状态不允许此操作"),
    MATERIAL_NOT_FOUND(3002, "物料不存在"),
    WAREHOUSE_FULL(3003, "仓库已满"),
    PRICE_EXPIRED(3004, "价格已过期"),

    // 系统错误 (5000-5999)
    SYSTEM_ERROR(5000, "系统繁忙, 请稍后重试"),
    SERVICE_UNAVAILABLE(5001, "服务暂不可用"),
    TIMEOUT(5002, "请求超时"),
    RATE_LIMITED(5003, "请求频率限制"),
    DB_ERROR(5004, "数据库操作失败"),
    MQ_ERROR(5005, "消息队列操作失败"),

    // 外部服务错误 (6000-6999)
    THIRD_PARTY_ERROR(6000, "外部服务调用失败"),
    WECHAT_ERROR(6001, "微信接口调用失败"),
    SMS_SEND_FAILED(6002, "短信发送失败"),
    FILE_UPLOAD_FAILED(6003, "文件上传失败");
}
```

---

## 11. 注册中心与配置中心

### 11.1 Nacos 集群部署

```
┌─────────────────────────────────────────────────────────────────┐
│                    Nacos 2.3 集群 (AP 模式)                      │
│                                                                  │
│   ┌──────────────────────┐  ┌──────────────────────┐             │
│   │   Nacos-1             │  │   Nacos-2             │             │
│   │   192.168.1.11:8848   │◀─▶│   192.168.1.12:8848  │             │
│   └──────────────────────┘  └──────────────────────┘             │
│            ◀─▶                                                    │
│   ┌──────────────────────┐                                       │
│   │   Nacos-3             │                                       │
│   │   192.168.1.13:8848   │                                       │
│   └──────────────────────┘                                       │
│                                                                  │
│   存储: MySQL (nacos 数据库)                                      │
│   模式: AP (可用性优先)                                           │
│   端口: 8848 (HTTP), 9848 (gRPC), 9849 (Raft)                   │
└─────────────────────────────────────────────────────────────────┘
```

### 11.2 服务注册配置

```yaml
# 每个微服务的 bootstrap.yml
spring:
  application:
    name: kubo-wms

  cloud:
    nacos:
      discovery:
        server-addr: nacos:8848
        namespace: kubo-prod
        group: KUBO_GROUP
        cluster-name: SH-CLUSTER
        metadata:
          version: 1.0.0
          region: shanghai
          weight: 1

      config:
        server-addr: nacos:8848
        namespace: kubo-prod
        group: KUBO_GROUP
        file-extension: yaml
        refresh-enabled: true
        # 共享配置
        shared-configs:
          - data-id: kubo-common.yaml
            group: KUBO_GROUP
            refresh: true
          - data-id: kubo-datasource.yaml
            group: KUBO_GROUP
            refresh: false
```

### 11.3 配置管理策略

```yaml
# 配置分类
配置分类:
  - 公共配置: kubo-common.yaml (所有服务共享)
  - 数据源配置: kubo-datasource.yaml (数据库连接)
  - 服务级配置: kubo-{service}.yaml (各服务特有)
  - 环境配置: kubo-{env}.yaml (DEV/UAT/PROD)

# 配置变更流程
Config Change Flow:
  1. 开发人员在 Nacos 控制台修改配置
  2. Nacos 推送变更到所有服务实例
  3. 服务实例通过 @RefreshScope 自动刷新
  4. 监控配置变更日志 (配置审计)
  5. 变更回滚: Nacos 支持版本管理

# 敏感配置加密
Sensitive Config:
  - 数据库密码: 使用 Jasypt 加密
  - 密钥: 存储在 Vault 或 KMS
  - 加密方式: 配置中心存储密文, 运行时解密
```

---

## 12. 分布式事务设计

### 12.1 事务选型

| 场景 | 方案 | 说明 |
|------|------|------|
| **跨服务强一致性** | Seata AT (两阶段提交) | 创建订单扣库存、财务记账 |
| **最终一致性** | 本地消息表 + RabbitMQ | 异步通知、状态同步 |
| **最大努力通知** | RabbitMQ + 重试 + 对账 | 支付回调、三方通知 |
| **补偿事务** | Seata TCC (预留-确认-取消) | 库存预留、资金冻结 |

### 12.2 Seata AT 模式

```
┌──────────┐     ┌──────────┐     ┌──────────┐
│  TC       │     │  TM       │     │  RM       │
│  (事务协调器)│     │  (事务管理器)│     │  (资源管理器)│
└──────────┘     └──────────┘     └──────────┘
      │                │                │
      │     1. 开启全局事务              │
      │◀───────────────│                │
      │                │                │
      │                │ 2. 注册分支事务───▶│
      │                │────────────────▶│
      │                │                │
      │                │ 3. 执行业务 SQL──▶│
      │                │                │
      │                │ 4. 回查提交结果───▶│
      │                │◀────────────────│
      │                │                │
      │     5. 提交/回滚全局事务          │
      │◀───────────────│                │
      │                │                │
      │     6. 发送分支提交/回滚指令───────▶│
      │────────────────────────────────▶│
      │                │                │
      │                │                │ 7. 提交/回滚本地事务
      │                │                │
```

### 12.3 事务使用示例

```java
// ── Seata AT 分布式事务 ──
@Service
public class OrderService {

    @GlobalTransactional(name = "create-order", rollbackFor = Exception.class)
    public OrderVO createOrder(OrderCreateCmd cmd) {
        // 1. 创建订单 (kubo-order 本地事务)
        OrderEntity order = orderRepository.save(cmd);

        // 2. 扣减库存 (kubo-wms 远程事务)
        inventoryService.deduct(cmd.getWarehouseId(), cmd.getSkuId(), cmd.getQty());

        // 3. 锁定资金 (kubo-finance 远程事务)
        financeService.lockBalance(cmd.getCustomerId(), cmd.getAmount());

        // 4. 发送消息 (事务消息)
        messageService.sendOrderCreatedEvent(order);

        return OrderVO.fromEntity(order);
    }
}
```

---

## 13. 可观测性设计

### 13.1 三大支柱

```
┌─────────────────────────────────────────────────────────────────┐
│                    可观测性架构                                   │
│                                                                  │
│   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│   │    Metrics    │  │   Tracing    │  │    Logging    │          │
│   │  (指标)       │  │  (链路追踪)   │  │  (日志)       │          │
│   ├──────────────┤  ├──────────────┤  ├──────────────┤          │
│   │ Prometheus    │  │  SkyWalking  │  │  Loki +       │          │
│   │ + Grafana     │  │  APM 9.7     │  │  Promtail     │          │
│   │               │  │              │  │              │          │
│   │ 指标:         │  │ 追踪:       │  │ 日志:        │          │
│   │ - QPS/延迟    │  │ - 调用链     │  │ - 结构化     │          │
│   │ - 错误率      │  │ - 服务拓扑   │  │ - 全文检索   │          │
│   │ - 资源使用    │  │ - 慢调用分析  │  │ - 标签索引   │          │
│   │ - 业务指标    │  │ - 依赖分析   │  │ - 日志聚合   │          │
│   └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│          │                │                │                    │
│          └────────────────┼────────────────┘                    │
│                           │                                      │
│                    ┌──────▼──────┐                               │
│                    │  Grafana    │                               │
│                    │  统一监控面板 │                               │
│                    └─────────────┘                               │
└─────────────────────────────────────────────────────────────────┘
```

### 13.2 关键监控指标

```yaml
# 应用层指标
Application Metrics:
  - QPS: 每秒请求数 (按接口/服务分组)
  - P99/P95/P50 延迟: 接口响应时间
  - 错误率: 5xx/4xx 比例
  - 慢请求: 超过 1s 的请求数
  - 活跃连接数: 数据库/Redis/RabbitMQ

# 业务层指标
Business Metrics:
  - 订单创建 QPS
  - 库存变更 QPS
  - 工单完成率
  - IoT 数据采集速率
  - 消息队列积压量

# 系统层指标
System Metrics:
  - CPU 使用率
  - 内存使用率 (堆/非堆)
  - GC 频率/暂停时间
  - 线程池状态
  - 磁盘 I/O
  - 网络 I/O

# 告警规则
Alert Rules:
  - P99 延迟 > 2s 持续 5 分钟 → 警告
  - 错误率 > 5% 持续 3 分钟 → 警告
  - 错误率 > 10% 持续 1 分钟 → 严重
  - 消息队列积压 > 10000 持续 5 分钟 → 警告
  - 数据库连接池使用率 > 80% → 警告
  - GC 暂停时间 > 1s → 严重
  - 服务不可用 (健康检查失败) → 严重
```

### 13.3 链路追踪

```yaml
# SkyWalking 配置
agent:
  service_name: ${SW_AGENT_NAME:kubo-wms}
  collector:
    backend_service: ${SW_AGENT_COLLECTOR_BACKEND_SERVICES:skywalking-oap:11800}
  sampling:
    # 采样率: 生产环境 10%, 排查问题时 100%
    rate: ${SW_AGENT_SAMPLE_RATE:1000}

# 手动埋点 (关键业务)
@Component
public class BusinessTraceHelper {
    @Autowired
    private Tracer tracer;

    public void traceBusiness(String businessType, String businessId, Runnable action) {
        Span span = tracer.buildSpan("business." + businessType)
            .withTag("businessId", businessId)
            .withTag("tenant", TenantContext.getCurrentTenant())
            .start();
        try (Scope scope = tracer.activateSpan(span)) {
            action.run();
        } catch (Exception e) {
            span.setTag("error", e.getMessage());
            throw e;
        } finally {
            span.finish();
        }
    }
}
```

---

## 14. 部署架构设计

### 14.1 K8s 集群架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                         K8s 集群                                    │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    命名空间: kubo-prod                        │   │
│  │                                                                │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐               │   │
│  │  │ Deployment  │  │  Service   │  │  ConfigMap │               │   │
│  │  │ (无状态服务) │  │  (内部通信) │  │  (配置)    │               │   │
│  │  └────────────┘  └────────────┘  └────────────┘               │   │
│  │                                                                │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐               │   │
│  │  │  HPA        │  │  Ingress   │  │  Secret    │               │   │
│  │  │ (弹性伸缩)  │  │  (外部入口) │  │  (密钥)    │               │   │
│  │  └────────────┘  └────────────┘  └────────────┘               │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐           │
│  │  Node-1       │  │  Node-2       │  │  Node-3       │           │
│  │  kubo-wms x2  │  │  kubo-wms x2  │  │  kubo-mes x2  │           │
│  │  kubo-auth x2 │  │  kubo-mes x2  │  │  kubo-auth x2 │           │
│  │  kubo-gateway │  │  kubo-iot x2  │  │  kubo-gateway │           │
│  └───────────────┘  └───────────────┘  └───────────────┘           │
└─────────────────────────────────────────────────────────────────────┘
```

### 14.2 各微服务资源规格

| 服务名 | 副本数 | CPU 请求/限制 | 内存请求/限制 | JVM 参数 | 存储 |
|--------|--------|-------------|-------------|---------|------|
| kubo-gateway | 2-4 | 1/2 | 512M/1G | -Xms512m -Xmx1g | 无状态 |
| kubo-auth | 2-4 | 1/2 | 512M/1G | -Xms512m -Xmx1g | 无状态 |
| kubo-wms | 4-8 | 2/4 | 2G/4G | -Xms2g -Xmx4g | 无状态 |
| kubo-mes | 2-4 | 2/4 | 2G/4G | -Xms2g -Xmx4g | 无状态 |
| kubo-factory | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-finance | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-iot | 4-8 | 2/4 | 2G/4G | -Xms2g -Xmx4g | 无状态 |
| kubo-asset | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-channel | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-personnel | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-system | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-open | 2-4 | 1/2 | 512M/1G | -Xms512m -Xmx1g | 无状态 |
| kubo-wechat | 1-2 | 0.5/1 | 512M/1G | -Xms512m -Xmx1g | 无状态 |
| kubo-chat | 2-4 | 1/2 | 1G/2G | -Xms1g -Xmx2g | 无状态 |
| kubo-mcp | 1-2 | 0.5/1 | 512M/1G | -Xms512m -Xmx1g | 无状态 |

### 14.3 弹性伸缩策略

```yaml
# HPA 配置 (Horizontal Pod Autoscaler)
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: kubo-wms-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: kubo-wms
  minReplicas: 4
  maxReplicas: 16
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
    # 基于自定义指标 (QPS)
    - type: Pods
      pods:
        metric:
          name: http_requests_per_second
        target:
          type: AverageValue
          averageValue: 500
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
        - type: Percent
          value: 100
          periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
        - type: Percent
          value: 20
          periodSeconds: 120
```

### 14.4 CI/CD 流水线

```
┌─────────────────────────────────────────────────────────────────────┐
│                     GitOps 部署流水线                               │
│                                                                      │
│   开发分支 ──▶ PR ──▶ 代码审查 ──▶ 合并到 main ──▶ GitHub Actions     │
│                                                       │              │
│                                                       ▼              │
│                                                 1. 代码检查 (Lint)    │
│                                                 2. 单元测试          │
│                                                 3. 构建镜像          │
│                                                 4. 推送到镜像仓库    │
│                                                 5. 更新 Helm Chart   │
│                                                       │              │
│                                                       ▼              │
│                                                ArgoCD 同步           │
│                                                       │              │
│                                                       ▼              │
│    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│    │  Dev     │───▶│  Staging │───▶│  Canary  │───▶│  Prod    │     │
│    │  (自动)   │    │  (自动)   │    │  (10%流量)│    │  (手动)   │     │
│    └──────────┘    └──────────┘    └──────────┘    └──────────┘     │
│                                                                      │
│   每个环境:                                                          │
│   - 健康检查 (Readiness + Liveness)                                 │
│   - 冒烟测试 (Smoke Test)                                          │
│   - 回滚策略 (Rollback)                                            │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 15. 安全架构设计

### 15.1 安全体系

```
┌─────────────────────────────────────────────────────────────────────┐
│                        纵深防御体系                                  │
│                                                                      │
│  第 1 层: 边界防护                                                   │
│  ├── CloudFlare WAF (DDoS, SQLi, XSS 防护)                          │
│  ├── Nginx 限流 (IP/用户/UUID 级别)                                  │
│  └── TLS 1.3 强制                                                    │
│                                                                      │
│  第 2 层: 网关防护                                                   │
│  ├── Spring Cloud Gateway 限流 (令牌桶)                              │
│  ├── 统一鉴权 (JWT 验证)                                             │
│  ├── 请求频率限制 (Redis 滑动窗口)                                   │
│  └── 请求大小限制 (10MB)                                             │
│                                                                      │
│  第 3 层: 应用安全                                                   │
│  ├── @RequirePermission 接口级权限                                   │
│  ├── DataPermissionInterceptor 行级权限                              │
│  ├── ColumnMaskingAspect 列级脱敏                                    │
│  ├── 参数校验 (JSR-303 + 自定义校验器)                               │
│  ├── 幂等性校验 (Idempotency-Key)                                    │
│  └── SQL 注入防护 (MyBatis-Plus 参数化查询)                          │
│                                                                      │
│  第 4 层: 数据安全                                                   │
│  ├── 传输加密 (TLS 1.3, mTLS 服务间)                                 │
│  ├── 静态加密 (MySQL TDE, AES-256)                                   │
│  ├── 敏感字段加密存储 (AES-256-GCM)                                  │
│  ├── 日志脱敏 (不记录敏感字段明文)                                    │
│  └── 备份加密                                                       │
│                                                                      │
│  第 5 层: 运维安全                                                   │
│  ├── 最小权限原则 (RBAC + 审批流)                                    │
│  ├── 操作审计 (AuditLog)                                             │
│  ├── 密钥轮换 (90 天周期)                                            │
│  ├── 漏洞扫描 (Trivy + Dependency-Check)                            │
│  └── 应急响应预案                                                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 15.2 安全头配置

```nginx
# Nginx/Gateway 安全头
add_header X-Frame-Options "DENY" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-XSS-Protection "1; mode=block" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Content-Security-Policy "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self' https:; frame-ancestors 'none';" always;
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
add_header Permissions-Policy "geolocation=(), microphone=(), camera=()" always;
```

---

## 16. 性能目标与 SLA

### 16.1 性能指标

| 指标 | 当前值 | 目标值 | 测量方式 |
|------|--------|--------|----------|
| **单接口 P99 延迟** | > 2000ms | < 500ms | Prometheus Histogram |
| **单接口 P50 延迟** | > 500ms | < 100ms | Prometheus Histogram |
| **数据库查询 P99** | > 1000ms | < 200ms | SkyWalking 数据库追踪 |
| **并发用户数** | < 500 | > 10000 | 压力测试 |
| **系统 QPS** | < 500 | > 10000 | 压力测试 |
| **库存扣减 QPS** | < 200 | > 5000 | 压力测试 |
| **IoT 采集速率** | < 1000/s | > 50000/s | 压力测试 |
| **可用性 (SLA)** | < 99% (单点) | > 99.99% | 监控系统 |
| **RPO** | > 1h (无备份) | 0 (零丢失) | 故障演练 |
| **RTO** | > 4h | < 30s | 故障演练 |
| **MTTR** | > 2h | < 15min | 故障演练 |

### 16.2 容量规划

```yaml
# 10,000 并发用户容量估算
User Scale: 10,000 并发用户

# 各服务 QPS 估算
kubo-gateway: 10,000+ (聚合流量)
kubo-auth: 2,000 (登录/Token 刷新)
kubo-wms: 3,000 (库存查询/出入库)
kubo-mes: 2,000 (工单/排产)
kubo-iot: 5,000+ (设备数据采集)
kubo-chat: 2,000 (消息推送)

# 数据库容量
每微服务数据库: 100GB - 500GB
IoT 数据增长: 约 10GB/天
总数据量 (3年): 约 15TB

# 缓存容量
Redis Cluster: 6主6从 × 8GB = 48GB (可用)
Caffeine: 每实例 10,000 条

# 消息队列容量
RabbitMQ: 3 节点 × 16GB
每日消息量: 约 500 万条
峰值: 约 5000 TPS
```

### 16.3 压力测试场景

| 场景 | 目标 | 持续时间 | 通过标准 |
|------|------|---------|---------|
| **基准测试** | 单接口最大 QPS | 30 分钟 | 无错误, P99 < 500ms |
| **负载测试** | 80% 目标负载持续运行 | 4 小时 | 无 OOM, 无慢查询 |
| **压力测试** | 200% 目标负载 | 1 小时 | 系统不崩溃, 降级正常 |
| **稳定性测试** | 100% 目标负载 | 7×24 小时 | 无内存泄漏, 无性能衰减 |
| **尖峰测试** | 突发 500% 负载 | 5 分钟 | 弹性伸缩响应 < 3 分钟 |
| **故障演练** | 单节点/单服务故障 | 30 分钟 | 自动恢复 < 30s |

---

## 17. 分阶段迁移策略

### 17.1 迁移总计划 (16 周)

```
周次      1  2  3  4  5  6  7  8  9  10 11 12 13 14 15 16
          │  │  │  │  │  │  │  │  │  │  │  │  │  │  │  │
Phase 0   ████████████
基础设施   ████████████
+ 骨架链路 ████████████

Phase 1                  ██████████████████████████
核心领域                  ██████████████████████████

Phase 2                                    ████████████████████████
支撑服务                                    ████████████████████████

Phase 3                                                      ████████████████
平台能力                                                      ████████████████

Phase 4                                                            ████████████
灰度发布                                                            ████████████
```

### 17.2 各阶段详细任务

#### Phase 0: 基础设施 + 骨架链路 (Week 1-2)

| 任务 | 描述 | 产出 |
|------|------|------|
| 0.1 | Nacos 集群搭建 | 注册中心 + 配置中心可用 |
| 0.2 | MySQL InnoDB Cluster 搭建 | 3 节点集群, 读写分离 |
| 0.3 | Redis Cluster 搭建 | 6M/6S 集群 |
| 0.4 | RabbitMQ 集群搭建 | 3 节点 Quorum Queues |
| 0.5 | kubo-common 公共模块 | 工具类, 异常定义, 通用模型 |
| 0.6 | kubo-gateway 网关 | 路由, 鉴权, 限流 |
| 0.7 | kubo-auth 认证服务 | 登录, Token, OAuth2 |
| 0.8 | CI/CD 流水线 | GitHub Actions + ArgoCD |
| 0.9 | 基础监控搭建 | Prometheus + Grafana + SkyWalking |
| 0.10 | 端到端测试 (钢线链路) | 登录 → 认证 → 网关 → 业务服务 |

#### Phase 1: 核心业务域 (Week 3-8)

| 任务 | 服务 | 工作量 |
|------|------|--------|
| 1.1 | kubo-wms (仓储) | 3 周 |
| 1.2 | kubo-mes (生产) | 3 周 |
| 1.3 | kubo-factory (工厂) | 2 周 |
| 1.4 | kubo-finance (财务) | 2 周 |
| 1.5 | kubo-personnel (组织) | 1 周 |
| 1.6 | 行级权限接入 | 贯穿 Phase 1 |

#### Phase 2: 支撑服务 (Week 6-11)

| 任务 | 服务 | 工作量 |
|------|------|--------|
| 2.1 | kubo-iot (IoT) | 2 周 |
| 2.2 | kubo-asset (资产) | 2 周 |
| 2.3 | kubo-channel (渠道) | 2 周 |
| 2.4 | kubo-system (系统) | 1 周 |
| 2.5 | kubo-chat (即时通讯) | 2 周 |
| 2.6 | 列级权限 + 脱敏 | 与 Phase 2 并行 |

#### Phase 3: 平台能力 (Week 10-14)

| 任务 | 描述 | 工作量 |
|------|------|--------|
| 3.1 | 开放平台 (kubo-open) | 2 周 |
| 3.2 | 微信集成 (kubo-wechat) | 1 周 |
| 3.3 | MCP 协议适配 (kubo-mcp) | 1 周 |
| 3.4 | 分布式事务 (Seata) | 贯穿 Phase 3 |
| 3.5 | 全链路压测 | 2 周 |
| 3.6 | 安全加固 | 1 周 |

#### Phase 4: 灰度发布 (Week 13-16)

| 任务 | 描述 | 工作量 |
|------|------|--------|
| 4.1 | 双写 + 数据校验 (新老系统并行) | 2 周 |
| 4.2 | 灰度策略配置 (10% → 50% → 100%) | 1 周 |
| 4.3 | 监控告警上线 | 1 周 |
| 4.4 | 回滚预案 | 1 周 |
| 4.5 | 操作手册 + 培训 | 1 周 |
| 4.6 | 全量切换 + 老系统下线 | 1 周 |

### 17.3 迁移策略: 绞杀者模式

```
┌─────────────────────────────────────────────────────────────────────┐
│                   绞杀者模式迁移策略                                  │
│                                                                      │
│  Step 1: 新老系统并存                                                │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  Nginx 路由分流                                                │   │
│  │  /api/v1/wms/*  →  kubo-wms (新)                              │   │
│  │  /api/v1/mes/*  →  kubo-mes (新)                              │   │
│  │  /api/*          →  spms-server (旧, 未迁移的接口)              │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Step 2: 双写 + 数据校验                                             │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  写操作: 同时写入新库 + 旧库                                    │   │
│  │  读操作: 优先读新库, 回退到旧库                                 │   │
│  │  数据校验: 定时任务对比新老库差异                                │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Step 3: 逐步切换                                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  10% 流量 → 新系统 (观察 3 天)                                │   │
│  │  50% 流量 → 新系统 (观察 3 天)                                │   │
│  │  100% 流量 → 新系统                                           │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  Step 4: 老系统下线                                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  确认无问题后, 停止老系统写操作                                │   │
│  │  保留老系统只读一个月                                          │   │
│  │  一个月后, 下线老系统                                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 18. 风险评估与应对

### 18.1 风险矩阵

| 风险 | 等级 | 概率 | 影响 | 应对措施 |
|------|------|------|------|----------|
| **数据迁移丢失** | 🔴 P0 | 低 | 灾难性 | 双写 + 数据校验 + 回滚预案 |
| **服务间调用超时链** | 🔴 P0 | 中 | 高 | 熔断降级 + 超时控制 + 异步化 |
| **分布式事务不一致** | 🔴 P0 | 中 | 高 | 本地消息表 + 对账 + 补偿 |
| **缓存穿透/击穿** | 🟡 P1 | 中 | 中 | 布隆过滤器 + 互斥锁 + 空值缓存 |
| **消息积压** | 🟡 P1 | 低 | 中 | 监控告警 + 自动扩容消费者 |
| **慢 SQL 导致雪崩** | 🟡 P1 | 中 | 高 | 慢 SQL 日志 + 读写分离 + 连接池 |
| **配置中心故障** | 🟡 P1 | 低 | 高 | 本地缓存配置 + 多副本 |
| **注册中心故障** | 🟡 P1 | 低 | 高 | 服务本地缓存 + 多副本 |
| **K8s 节点故障** | 🟡 P1 | 中 | 中 | 反亲和性调度 + HPA + PDB |
| **新系统性能不达标** | 🟡 P1 | 低 | 高 | 前期充分压测 + 逐步灰度 |
| **团队学习曲线** | 🟢 P2 | 高 | 中 | 培训 + 文档 + 代码审查 |
| **前端 API 不兼容** | 🟢 P2 | 中 | 中 | API 变更评审 + 版本管理 |

### 18.2 回滚策略

```yaml
# 回滚策略
Service Rollback:
  1. 单服务回滚: kubectl rollout undo deployment/kubo-wms
  2. 全量回滚: ArgoCD Sync 到上一版本
  3. 数据回滚: 保留老系统只读, 切回 Nginx 路由

# 数据回滚方案
Data Rollback:
  - 保留老系统数据库 (只读) 30 天
  - 双写期间新库数据可丢弃
  - 灰度期间新库数据可回补
```

---

## 附录 A: 架构决策记录 (ADR) 索引

| 编号 | 决策 | 状态 |
|------|------|------|
| ADR-001 | 采用 RabbitMQ 作为消息中间件 | ✅ 已确认 |
| ADR-002 | 采用 MySQL InnoDB Cluster 数据库方案 | ✅ 已确认 |
| ADR-003 | 采用 Seata AT 分布式事务方案 | ✅ 已确认 |
| ADR-004 | 采用 Spring Cloud Alibaba 微服务体系 | ✅ 已确认 |
| ADR-005 | 采用 MyBatis-Plus + 分页插件 | ✅ 已确认 |
| ADR-006 | 微服务命名规范: kubo-{domain} | ✅ 已确认 |
| ADR-007 | 采用 K8s + Helm + ArgoCD 部署方案 | ✅ 已确认 |
| ADR-008 | 采用 Prometheus + Grafana + SkyWalking 监控方案 | ✅ 已确认 |

## 附录 B: 参考文档

- [Kubo 平台高并发重构实施路线规划计划.md](../Kubo%20平台高并发重构实施路线规划计划.md)
- [前后端重构规划差距分析报告.md](../archive/前后端重构规划差距分析报告.md)
- [Kubo 平台前端系统二次改造设计文档](./Kubo%20平台前端系统二次改造设计文档.md)
- [SECURITY_GUIDE.md](../05-Development/SECURITY_GUIDE.md)
- [TESTING_STRATEGY.md](../05-Development/TESTING_STRATEGY.md)

---

> **文档版本**: v2.0
> **最后更新**: 2026-08-13
> **审批**: 待定

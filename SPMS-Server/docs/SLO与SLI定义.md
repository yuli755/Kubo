# SLO / SLI 定义（生产级能力补全 · SLO/SLI）

> 触发：生产级审查报告「SLO/SLI 定义」项。本文把「可靠性目标」从「告警阈值」里抽出来，定义可度量的
> SLI、给业务方可承诺的 SLO 与对应的错误预算（error budget）。**所有 SLI 都基于已落地的真实指标**
> （Prometheus 已 scrape 的 node/redis/mysql/actuator），不引用愿景指标。
>
> 与 `可观测性与告警加固方案.md` §3 的关系：**告警是「症状阈值」（什么时候响），SLO 是「业务目标」
> （允许差多少、还剩多少预算）**。本文补的是后者。

---

## 0. 结论先行

1. 定义 4 条 SLI（可用性 / 延迟 / 错误率 / 饱和度），每条给出**精确 PromQL**，可直接做成
   Grafana 面板或 recording rule。
2. 建议 SLO 目标（默认值，**需业务确认**，同 RPO/RTO 一样是业务决策非技术单方面能定）：
   - 可用性 **99.9% / 月**（错误预算 ≈ 43 分钟/月）；
   - 读接口 P99 延迟 **< 1s**、写接口 P99 **< 2s**（现有告警用 P99>2s，对应写链路）；
   - 5xx 错误率 **< 0.1%**（与 99.9% 可用性一致口径）。
3. 落地顺序：先用现有指标跑 2 周**基线**（不看目标、先看现状），再按基线定 SLO，最后接**燃烧率告警**。
4. **诚实边界**：应用层 `http_server_requests_*` 指标依赖后端 micrometer（pom 已引、生产已暴露
   `/actuator/prometheus`，但后端尚未容器化进 compose，scrape target 用 `host.docker.internal:8080`
   占位）——SLI 表达式真实可用，但**首次出数需本机编译 + docker compose 实测**。

---

## 1. SLI 定义（基于真实指标）

### SLI-1 可用性（Availability）

- **口径**：成功请求占比 = 1 −（5xx 请求数 ÷ 总请求数），按 30 天滚动窗口。
- **数据源**：micrometer `http_server_requests_seconds_count`（Spring MVC 自带，分 status）。
- **PromQL（月窗口）**：

```promql
1 - (
  sum(increase(http_server_requests_seconds_count{status=~"5.."}[30d]))
  /
  sum(increase(http_server_requests_seconds_count[30d]))
)
```

> 实操注：`[30d]` 长窗口对 Prometheus 内存不友好。生产做法是用 **recording rule** 按小时/天聚合
> `error_rate:http_1h` / `error_rate:http_1d`，再对聚合序列算月窗口；或用现成 SLO 工具
> （Sloth / Pyrra）生成。起步阶段可先跑 `[7d]` 试点。

### SLI-2 延迟（Latency）

- **口径**：P99 / P95 响应耗时（histogram 分位）。
- **数据源**：micrometer `http_server_requests_seconds_bucket`。
- **PromQL**：

```promql
# P99（5 分钟窗口）
histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))
# P95
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))
```

### SLI-3 错误率（Error rate）

- **口径**：5xx 占比（可用性的「故障侧」子集，用于快速告警，窗口短）。
- **PromQL（5 分钟）**：同现有 `APIHighErrorRate` 告警：

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)
/
sum(rate(http_server_requests_seconds_count[5m])) by (service)
```

### SLI-4 饱和度（Saturation，容量余量）

| 资源 | 指标 | PromQL（示例） | 目标余量 |
| --- | --- | --- | --- |
| MySQL 连接池（HikariCP） | `hikaricp_connections_active` / `hikaricp_connections_max` | `hikaricp_connections_active / hikaricp_connections_max` | < 0.8 |
| MySQL 连接 | `mysql_global_status_threads_connected` / `mysql_global_variables_max_connections` | 同现有告警 | < 0.8 |
| Redis 内存 | `redis_memory_used_bytes` / `redis_memory_max_bytes` | 同现有告警 | < 0.85 |
| 磁盘 | `node_filesystem_avail_bytes` / `node_filesystem_size_bytes` | 同现有告警 | 可用 > 15% |
| JVM 堆 | `jvm_memory_used_bytes{area="heap"}` / `jvm_memory_max_bytes{area="heap"}` | 同现有告警 | < 0.85 |

---

## 2. SLO 目标与错误预算

| SLO | 目标 | 窗口 | 错误预算 | 备注 |
| --- | --- | --- | --- | --- |
| 可用性 | **99.9%** | 30 天 | **43.2 分钟/月**（0.1%） | MES/WMS 生产可用性底线 |
| 读接口 P99 | **< 1s** | 30 天 | — | 报表/列表页体感 |
| 写接口 P99 | **< 2s** | 30 天 | — | 报工/单据写入 |
| 5xx 错误率 | **< 0.1%** | 30 天 | 与可用性同口径 | 错误率×时间=停机预算 |

**错误预算速查（30 天 = 43,200 分钟）**：

| 可用性 | 允许中断 |
| --- | --- |
| 99% | 432 分钟（7.2 小时） |
| 99.5% | 216 分钟（3.6 小时） |
| 99.9% | 43.2 分钟 |
| 99.95% | 21.6 分钟 |
| 99.99% | 4.3 分钟 |

> 99.9% 是「制造型 MES/WMS」的常见默认；**更高可用性靠更高的运维成本**（多活/热备），不与当前
> 单机 docker-compose 拓扑匹配。定之前先看 §4 的基线数据，别拍脑袋承诺。

---

## 3. 错误预算的使用规则（不浪费也不乱用）

1. **预算耗尽 = 冻结发布**：剩余预算 < 0 时，停止功能发布/灰度放量，转入可靠性工作（这是灰度发布
   `功能开关与灰度发布方案.md` 的「回滚依据」之一）。
2. **预算燃烧告警**（提前预警，而非耗尽才响）——经典「多窗口多燃烧率」简化版：

```promql
# 快燃烧：过去 1 小时错误率 > 14.4 倍预算（99.9% 预算 0.1% → 阈值 1.44%）
(sum(increase(http_server_requests_seconds_count{status=~"5.."}[1h]))
 / sum(increase(http_server_requests_seconds_count[1h]))) > 0.0144

# 慢燃烧：过去 6 小时错误率 > 6 倍预算（阈值 0.6%）
(sum(increase(http_server_requests_seconds_count{status=~"5.."}[6h]))
 / sum(increase(http_server_requests_seconds_count[6h]))) > 0.006
```

   快燃烧 → 立即 page；慢燃烧 → 工单跟进。二者任一命中说明「照此速度预算将提前耗尽」。

---

## 4. 落地步骤（先基线、再目标、后告警）

| 步骤 | 动作 | 产出 |
| --- | --- | --- |
| 1. 出数 | 本机编译后端 + `docker compose up`，确认 `/actuator/prometheus` 出 `http_server_requests_*` | 指标真实可用（本文 SLI 首次出数） |
| 2. 基线 | 跑 2 周，记录可用性/延迟/错误率现状（Grafana 面板按 §1 PromQL 建） | 「现状 vs 目标」差距表 |
| 3. 定标 | 按基线定 SLO（可先 99.5% 起步，稳定后收紧到 99.9%） | 正式 SLO 数值（回写 §2 表） |
| 4. 告警 | 接 §3 燃烧率告警到 Alertmanager/IM | 预算提前预警 |

---

## 5. 与其它能力的关系

- **告警规则**（`alerts.yml` 的 `APIHighErrorRate` / `APIHighLatency`）：是 SLO 的「症状即时响应」，
  SLO 是「月维度承诺」，两者阈值可不同（告警 5% 是「已经坏了」，SLO 0.1% 是「目标」）。
- **灰度发布**：预算燃烧告警是放量→回滚的观测闭环，见 `功能开关与灰度发布方案.md` §2.4。
- **恢复演练**：可用性 SLO 的「中断」包含计划内维护与故障恢复，演练耗时直接计入预算，倒逼缩短 RTO。
- **前端错误上报**：`/api/error-report` 的 `context` 可按功能聚合前端错误，作为「用户侧可用性」的
  补充信号（后端 5xx 看不到的「前端白屏」）。

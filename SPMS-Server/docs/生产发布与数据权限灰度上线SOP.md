# 生产发布与数据权限灰度上线 SOP（标准作业程序）

> **生效日期**：2026-09-21  
> **适用范围**：SPMS-Server 生产部署、数据库增量演进、数据权限多阶段放行与应急降级。  
> **执行原则**：一切变更均需真文件取证；无证据不宣称就绪；生产环境严禁破坏性 DDL；读写路径具备平滑回退能力。

---

## 1. 发布前准入与全量门禁检查（Pre-Release Gate）

在打包并交付生产制品前，必须在会话分支执行全量离线硬门禁，确保 18 道检查无一跳过：

```bash
# 1. 执行全链复合门禁（代码规范、死代码、类型、测试覆盖率、安全契约、DDL与迁移、构建体积）
pnpm check

# 2. 验证纯 JDK 17 生产契约与 AST 解析
JAVA_HOME=<JDK17路径> pnpm java:unit

# 3. 验证真实技术栈文件对账
pnpm stack:project
```

### 1.1 准入硬性指标
- `pnpm airpower:backend -- --check`：文件 0/0 · 符号 0/0 · 引用 0/0（零回潮锁死）；
- `pnpm pom:check`：0 父 POM、0 未管理依赖、0 AirPower 依赖；
- `pnpm ddl:check`：8 张数据权限核心表、8 个 JPA 实体完全无字段漂移；
- `pnpm migration:check`：10 个 Flyway 脚本方言合规，存储过程与定界符配平；
- `pnpm test:coverage`：测试套件 100% 通过，分支覆盖率不低于 85%；
- `pnpm size`：前端首屏闭包 gzip ≤ 450 KB（当前基线 270.4 KB）。

---

## 2. 生产数据库配置与迁移基线（Database Baseline）

### 2.1 Hibernate 校验策略
生产环境配置文件 `application-production.yml` 必须锁定：
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate # 生产环境绝对禁止 update / create-drop
```

### 2.2 Flyway 增量执行机制
- 容器或应用启动时，Flyway 会自动扫描并执行 `SPMS-Server/src/main/resources/db/migration/` 下的 `V1__baseline.sql` 至 `V10__*.sql` 脚本；
- **离线预刷库（冷启动或大规模初始化）**：
  若在发布前需手动初始化或验证结构，可幂等执行合并迁移脚本：
  ```bash
  mysql -u root -p kubo < SPMS-Server/docs/database/migration/20260909_data_permission_migrations_consolidated.sql
  ```

### 2.3 核心数据表巡检清单
上线前核实以下 8 张数据权限与运维核心表已就绪：
1. `data_resource`：受控数据资源登记表（状态为 ENABLED）；
2. `data_policy`：行级数据权限策略表；
3. `data_field_policy`：列级字段脱敏策略表；
4. `permission_change_outbox`：权限变更事务死信队列表；
5. `operation_log`：业务操作审计日志表；
6. `data_permission_deny_log`：越权拦截日志表；
7. `data_permission_diff_log`：影子模式行数差异比对日志表；
8. `data_permission_orm_parity_log`：双轨读写对账审计日志表。

---

## 3. 数据权限影子观测到强制生效标准四步法（Shadow → Enforce）

为避免直接开启行级拦截导致正常业务数据被误杀，必须严格执行灰度推进四部曲：

```
[阶段 1: 影子采样观测] ──> [阶段 2: 差异对账核验] ──> [阶段 3: 核心资源分批放开] ──> [阶段 4: 全量强制生效]
      (mode: shadow)           (diff_rate < 0.1%)         (enforce-resources: [...])       (deny-unregistered: true)
```

### 3.1 阶段一：影子采样观测（Shadow Mode）
- **配置标准**（`application-production.yml`）：
  ```yaml
  kubo:
    data-permission:
      mode: shadow                     # 开启影子计算，不阻断任何 SQL
      tenant-required: false
      shadow-diff-log: true
      shadow:
        sample-rate: 0.05              # 生产采样率 5%
        persist: true
        max-detail-rows: 200
      enforce-resources: []            # 强制名单必须为空
      deny-unregistered: false
  ```
- **执行目标**：每个业务查询在主流程正常执行的同时，在后台异步多打一次带行级条件的 COUNT，记录差异行数至 `data_permission_diff_log`。

### 3.2 阶段二：差异对账与业务核对
- **观察周期**：持续收集连续 3 个生产工作日的真实业务数据；
- **对账 SQL 巡检**：
  ```sql
  -- 查询过去 3 天各受控资源的影子差异率
  SELECT 
      resource, 
      COUNT(1) AS total_samples,
      SUM(CASE WHEN diff_count > 0 THEN 1 ELSE 0 END) AS diff_samples,
      ROUND(SUM(CASE WHEN diff_count > 0 THEN 1 ELSE 0 END) / COUNT(1) * 100, 3) AS diff_percentage
  FROM data_permission_diff_log
  WHERE create_time >= DATE_SUB(NOW(), INTERVAL 3 DAY)
  GROUP BY resource;
  ```
- **放行硬准则**：
  若某资源的 `diff_percentage < 0.1%`，且抽检确认差异均为合法的“跨部门/跨租户越权探测”，则准予进入阶段三。

### 3.3 阶段三：核心资源分批放开（Enforce 灰度放开）
通过配置 `kubo.data-permission.enforce-resources` 逐批开放行级权限阻断：

- **第一批（基础无依赖数据）**：
  ```yaml
  kubo.data-permission.enforce-resources:
    - "asset.material"
    - "factory.storage"
  ```
  *观察 24 小时，核实物料与仓库列表是否正常。*

- **第二批（渠道伙伴主数据）**：
  ```yaml
  kubo.data-permission.enforce-resources:
    - "asset.material"
    - "factory.storage"
    - "channel.customer"
    - "channel.supplier"
  ```
  *观察 24 小时，核实客户、供应商列表与采购销售前置选择器是否正常。*

- **第三批（仓储物流核心交易）**：
  ```yaml
  kubo.data-permission.enforce-resources:
    - "asset.material"
    - "factory.storage"
    - "channel.customer"
    - "channel.supplier"
    - "wms.inventory"
    - "wms.input"
    - "wms.output"
    - "wms.move"
  ```

### 3.4 阶段四：全量强制生效与异常报警
当全部八大核心资源灰度平稳运行满 7 天后：
1. 将 `kubo.data-permission.mode` 调整为 `enforce`；
2. 开启 `deny-unregistered: true`（拒绝未在 `data_resource` 登记的危险资源查询）；
3. 联动 Prometheus + Alertmanager，监控 `data_permission_deny_log` 的剧增告警。

---

## 4. 故障应急预案与一键平滑降级（Rollback & Fallback）

### 4.1 故障场景 A：读侧 MyBatis-Plus 出现 SQL 兼容或字段异常
- **表现**：Mapper 分页查询抛出 SQL 语法错误或找不到列。
- **降级操作**：
  直接在配置中心或启动环境变量调整开关：
  ```yaml
  kubo.data-permission.mapper-read.enabled: false
  ```
- **效果**：系统 14 个业务服务瞬间无缝平滑回退至纯 Spring Data JPA 读路径，零停机且不影响写路径。

### 4.2 故障场景 B：行级数据策略误阻断业务操作（大面积 403 或查不出数据）
- **表现**：合法用户登录后列表为空，`data_permission_deny_log` 激增。
- **降级操作**：
  快速将对应资源从强制名单中移出：
  ```yaml
  kubo.data-permission.enforce-resources: [] # 清空强制名单，回到全量放行/影子状态
  ```
- **效果**：系统立即停止行级拦截，并继续保持影子比对记录，供技术团队排查策略规则。

### 4.3 故障场景 C：Redis 缓存宕机或连接中断
- **表现**：Redis 发生故障或网络分区。
- **系统自愈机制**：
  - 部门树层级索引与数据权限策略具备本地二级缓存（Caffeine / 内存索引）；
  - 广播失效后各工作节点依靠自带的 TTL 机制（默认 300 秒）平滑自愈；
  - 核心持久化交易不受 Redis 宕机阻断。

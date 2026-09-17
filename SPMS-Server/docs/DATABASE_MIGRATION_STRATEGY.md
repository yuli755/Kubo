# 数据库脚本策略（2026-09-18 定稿）

> **一句话**：全仓只有两个数据库脚本，住 `infra/init-sql/`：
>
> | 文件 | 管什么 | 内容 |
> | --- | --- | --- |
> | [`init.sql`](../../infra/init-sql/init.sql) | **结构** | 49 张表（`CREATE TABLE IF NOT EXISTS`，含索引与唯一键） |
> | [`init_data.sql`](../../infra/init-sql/init_data.sql) | **数据** | 默认租户、4 个系统权限身份、`SYSTEM_ROOT` 角色与绑定、归属列回填 |
>
> **以后不管做什么数据库改动，都只改这两个文件**：加表/加列/改索引 → `init.sql`；加种子数据/回填写法 → `init_data.sql`。
> **不再新增迁移文件**（`V11__xxx.sql` 这类一律不许出现），也**不再引入迁移框架**。

---

## 1. 为什么是"两个文件"，而不是版本化迁移

| 口径 | 版本化迁移（Flyway/Liquibase） | 两个文件（本仓现行） |
| --- | --- | --- |
| 结构事实源 | 迁移链（V1 + V2 + … 累加） | `init.sql` 一份最终状态，**打开就能读全表结构** |
| 改结构时要做的 | 新写一个 `V<n>__` 文件（结构散在 N 个文件里，读"现在长什么样"要按顺序脑内执行） | 直接改 `init.sql` 里那张表 |
| 存量库怎么演进 | 迁移框架按记账表逐条应用 | `init.sql` 幂等（`IF NOT EXISTS`），缺什么补什么；大改动走备份 + 重建 |
| 工具依赖 | flyway-core + flyway-mysql + `flyway_schema_history` 记账表 + 校验和规则（改历史脚本会红） | **零依赖**，`mysql < 文件` 就能跑 |
| 适合的场景 | 多环境长期演进、需要审计每次变更 | 单人/小团队的项目库，要的是"一眼看全 + 改了就跑" |

用户 2026-09-18 定为第二种。代价写在明处：**没有逐版本回滚**（回滚 = 用备份还原 + 重放脚本），
**没有"谁在什么时候改的"审计**（`git log infra/init-sql/init.sql` 就是审计），
所以两个脚本必须**始终幂等 + 只增不删数据**（见 §3 的机器判据）。

## 2. 怎么执行

```bash
# ① 全新环境（最省事）：compose 的 mysql 服务把 infra/init-sql 挂进
#    /docker-entrypoint-initdb.d，容器**首次**启动（数据卷为空）时按文件名顺序自动执行
docker compose up -d mysql

# ② 已有库 / 手工建库
mysql -h 127.0.0.1 -uroot -p kubo < infra/init-sql/init.sql
mysql -h 127.0.0.1 -uroot -p kubo < infra/init-sql/init_data.sql
```

> 注意 `docker-entrypoint-initdb.d` 只在**数据目录为空**时生效：库已经在跑就不会重跑脚本，
> 此时用 ② 手工执行（两个脚本都幂等，重复执行没有副作用）。

应用侧行为：`ddl-auto = validate`（local 目前是 `update`，J4 关掉）——**应用不建表、不改表**，
库结构与脚本不一致时启动即报 `Schema-validation`，这正是我们要的"早失败"。

## 3. 谁在守这两个文件（4 道机器判据）

| 判据 | 命令 | 拦什么 |
| --- | --- | --- |
| 目录里**恰好两个** `.sql` | `pnpm migration:check` | 新增迁移文件、名字写错、漏文件；顺带查幂等守卫（`IF NOT EXISTS` / `WHERE NOT EXISTS`）、禁 `DROP/DELETE/TRUNCATE/DELIMITER/CREATE PROCEDURE`、查 `utf8mb4` |
| 结构 ↔ 实体列漂移 | `pnpm ddl:check` | 实体加了列没改 `init.sql`（生产启动会缺列）；脚本有列没人映射 |
| 实体 ↔ 脚本双向一致 | `npx vitest run src/config/__tests__/schemaContract.spec.ts` | 两边列名集合必须相等；`data_*` 表逐个核对 |
| 真实 MySQL 8 空库连跑两遍 | CI `migrations` job | 能不能执行、是否真幂等（第二次执行后 `information_schema` 必须与第一次逐列相同）、**零 ERROR** |

## 4. 改库结构的标准动作（照做）

1. 改实体（`XxxEntity.java`）——列的事实源是实体；
2. 改 `infra/init-sql/init.sql` 里对应的 `CREATE TABLE`：新列/新索引/新唯一键一并写上；
3. 若还有存量库要跟进（补列、回填），在 `init_data.sql` 里加幂等的 `INSERT ... WHERE NOT EXISTS`
   / `UPDATE ... WHERE 列 IS NULL`（**不许** `DELETE` / `DROP`）；
4. 跑 `pnpm check`（含上面 3 道离线判据）；
5. 推送后看 CI 的 `migrations` job（真库那一遍）。

## 5. 与历史的关系（2026-09 的三次收敛）

| 时间 | 状态 | 结果 |
| --- | --- | --- |
| 2026-09 之前 | 9 个手工 SQL，靠文档口口相传，**没人能一键建库** | 新环境起不了库 |
| 2026-09-17 | 收编为 Flyway `V1__baseline_schema.sql` + `V2..V10`（基线 + 存量脚本，含存储过程幂等技巧） | 可执行了，但读"当前结构"要脑内执行 10 个文件 |
| **2026-09-18** | **折叠成 `init.sql` + `init_data.sql` 两个文件，Flyway 整套退场** | 结构一眼看全；改动只在一处；判据从"版本号连续"改成"目录里恰好两个 + 幂等" |

折叠时逐条核对过（脚本可复查）：V2/V4 的 8 列（`contract_document`/`participant`/`personal_token`/`unit`
的 `tenant_id`+`creator_id`）、28 个索引、20 个 `(tenant_id, 列)` 复合唯一键、`user_tenant`/`user_factory`、
7 张 Hibernate 隐式命名关联表（`user_role_list` 等，历史上由 `ddl-auto: update` 建出、脚本里没有），
全部落进 `init.sql`；数据类（租户/权限身份/角色绑定/归属回填）落进 `init_data.sql`。

> 本文档原件是 ShardingSphere 分片 + DataX/Canal 双写 + gh-ost 在线 DDL 的**迁移方案设计**，
> 那些工具与"第 1~8 批（含 MES/IoT/财务域）"从未落地（对应模块早已删除）。
> 2026-09-18 按"过时文档直接删"的口径整篇改写成本文。

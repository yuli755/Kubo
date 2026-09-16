# Flyway S0 · 基线与存量脚本收编

> 日期：2026-09-17
> 目标：把「建库」从**没人能执行的隐性知识**变成**可执行、可验证、可回溯**的代码，
> 为「去 JPA / 写路径换 MyBatis」提供 schema 事实源。
> 状态：基础设施已落地并自测；**存量库接入需在有 MySQL 的环境执行一次**（沙箱无 DB）。

---

## 1. 为什么必须做这一步（问题不是"想加个迁移工具"）

三个事实叠在一起，构成一个**随时会爆的坑**：

| 事实 | 证据 | 后果 |
| --- | --- | --- |
| 应用**不建表** | `application.yml`: `ddl-auto: validate` | Hibernate 只校验，缺列在**启动时报错**，且信息不含前因 |
| 仓库**没有建表脚本** | 全仓 `CREATE TABLE` 只出现在测试子集 `schema-test.sql`（9 张表）与历史文档里 | **新环境无法起库**；换 ORM 时无从验证"表结构一致" |
| schema 变更靠**9 个手工 SQL** | `docs/database/migration/*.sql`（现已收编） | 执行顺序、是否漏跑，全靠人对文档，无账可查 |

换句话说：开发库里那份 schema 是历史 `ddl-auto: update` 时代长出来的，
**它只存在于那台机器上**。任何"删库重来"或"新同事第一次跑起来"都会卡在这里。

## 2. 交付物

| 文件 | 作用 |
| --- | --- |
| `db/migration/V1__baseline_schema.sql` | **40 张表 / 222 列**的完整建表脚本，由实体生成 |
| `db/migration/V2..V10__*.sql` | 原 9 个手工脚本，`git mv` 保留历史，版本化 |
| `scripts/gen-flyway-baseline.mjs` | 实体 → DDL 生成器；带 `--check` 与真库对账 |
| `src/config/__tests__/schemaContract.spec.ts` | **门禁**：实体 ↔ 基线双向对账（7 个用例） |
| `application.yml` | Flyway 配置（见下） |

### 2.1 为什么基线由**实体生成**，而不是 `mysqldump`

`mysqldump` 需要一个真库——而"没有真库能起服务"正是要解决的问题，循环依赖。
实体是随代码走的：把生成器 + 门禁放进仓库，**"改实体忘改 schema"当场红**，
比事后 dump 对账更早、更便宜。

### 2.2 沙箱里做不到的那部分（诚实说明）

生成器按 Java 类型映射无 `columnDefinition` 的列（`String→varchar(255)`、
`Boolean→tinyint(1)`、`BigDecimal→decimal(18,6)`…）。真库里的实际类型可能不同
（Hibernate 在 MySQL8 下可能把 `Boolean` 建为 `bit(1)`，`BigDecimal` 精度也未必是 6 位）。

**所以接入存量库时，第一件事是类型对账**：

```bash
mysqldump --no-data --skip-comments --compact kubo > /tmp/real.sql
node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
# 输出「实体有、真库没有」的列 → 逐条确认后修正生成器的 TYPE_MAP 或补 columnDefinition
```

`--check` 只比对**表与列的存在性**，不比类型（类型差异需要人工判断：
是历史遗留，还是生成器映射不对）。

## 3. 配置与两种库的接入路径

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true     # 存量库：当前状态记为 baseline 1，不跑 V1
    baseline-version: 1
    validate-on-migrate: true     # 校验已应用脚本的校验和，禁止改历史文件
    out-of-order: false
    table: flyway_schema_history
```

| 场景 | 行为 |
| --- | --- |
| **存量库**（开发/生产，已有表） | 首次启动：Flyway 建立 `flyway_schema_history`，插入一条 `version=1, type=BASELINE`，**不执行 V1**；随后只应用 V2+ 中尚未执行的部分 |
| **全新库** | 从 `V1__baseline_schema.sql` 开始执行，再顺序跑 V2..V10 |
| **测试库**（H2） | `application-test.yml` 里 `spring.flyway.enabled: false` —— 迁移是 MySQL 方言（存储过程、`information_schema` 探测），H2 跑不了也不该跑 |

> ⚠️ **V2..V10 在存量库上会重复执行吗？**
> 不会：Flyway 记账（每脚本只跑一次）。这些脚本当初也是**幂等**写的
> （`CREATE TABLE IF NOT EXISTS` / `WHERE NOT EXISTS` / 存储过程探测），
> 所以即使手工跑过多次也不会重复生效，但 Flyway 之后**不允许再改历史脚本**
> （`validate-on-migrate: true` 会校验和失败）——改动一律走新版本号。

## 4. 日常操作

```bash
# 改了实体（加/删列）之后：重新生成基线并跑门禁
node scripts/gen-flyway-baseline.mjs --write
pnpm vitest run src/config/__tests__/schemaContract.spec.ts

# 新增 schema 变更（绝不要改 V1..V10）
#   db/migration/V11__add_xxx_column.sql

# 存量库类型对账（需真库）
node scripts/gen-flyway-baseline.mjs --check /tmp/real.sql
```

## 5. 与「去 JPA」的关系（S0 的下一步）

1. **S0（本文）**：schema 有事实源、有账、有门禁 ✅
2. **S1**：写路径从 JPA 换 MyBatis（`mapper-read` 开关已有对账探针 `RowParityRegistry`，
   先 shadow 比对两套 ORM 的行数/内容，再切）
3. **S2**：切换完成后删 `spring-boot-starter-data-jpa`、`ddl-auto: validate`、
   85 个文件里的 `jakarta.persistence` 注解 —— 到那时**校验职责完全交给 Flyway + 契约测试**

> 顺序不能反：先删 JPA 再补 Flyway，等于在"没有 schema 事实源"的状态下动写路径，
> 出了问题无法判断是 ORM 换错了还是表结构本来就不一致。

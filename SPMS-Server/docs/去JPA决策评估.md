# 去 JPA 决策评估：能不能换、怎么换、代价多少（2026-09-17）

> 触发：使用方提出「不想要 JPA 写」，询问是否建议更换。
>
> 本文基于代码实测（JPA 注解/API 用量、Service/Controller/Mapper 数量、测试依赖），
> 给出**结论 + 隐藏职责清单 + 分期路线 + 成本**。数字可复现（命令见文末）。

---

## 0. 结论先行

| 问题 | 回答 |
| --- | --- |
| 建议换吗？ | ✅ **建议统一到 MyBatis（读写都用）**——它更贴合你「行级+列级数据权限、加新表直接复用」的目标，且有具体代码证据（§3） |
| 只去掉「JPA 写」行不行？ | ❌ **不行，那是最差组合**：仍是双 ORM，且读写两套语义不一致，维护成本反而上升（§2.3） |
| 现在能立刻做的是什么？ | **S1：新物流模块直接用 MyBatis，零 JPA** —— 当天生效，不需要先动老代码（§5） |
| 全量去 JPA 要多久？ | **3–5 人月**；只迁物流核心域（wms/factory/channel）**1–1.5 人月** |
| 最大的隐藏前提 | **必须先让 Flyway 接管 DDL**，否则永远删不掉 JPA（§2.1） |

---

## 1. 实测规模（改动面积）

| 维度 | 现状 | 说明 |
| --- | --- | --- |
| Service | **87 个** | 全部继承 airpower `CurdService`（**JPA 实现**） |
| Controller | 52 个 | 读出口很多已切 Mapper，写出口全在 JPA |
| Repository（JPA） | **61 个** | `JpaRepository` |
| Mapper（MyBatis） | **22 个** | 仅读侧，且仅 21 资源 |
| `Specification` | **61 文件** | 前端筛选条件的翻译层 |
| `CriteriaBuilder` | **28 文件** | 行级权限条件、动态查询 |
| `@DynamicInsert` / `@DynamicUpdate` | **61 / 63** | **几乎每个实体都有** |
| `columnDefinition` | **220 处** | 列注释/默认值写在注解里 |
| `@ManyToMany` / `@ManyToOne` | 20 / 54 | 关系模型（roleList/departmentList/storageList/factoryList…） |
| 测试 | 103 文件 / 660 用例，其中 `@SpringBootTest` **31 个**、引用 Repository **15 个** | 受影响面 |

---

## 2. JPA 在这个仓库承担的 5 件**隐藏职责**

去掉「写」只是其中一件。不认领其余四件就删不掉它。

### 2.1 DDL 所有权（最关键）

- `ddl-auto: validate`（生产/默认）、`update`（本地）；
- **220 处 `columnDefinition`**：列类型、`default`、**`comment`** 全在 JPA 注解里；
- 结论：**DDL 现在是 JPA 的职责**。删 JPA 前必须让 **Flyway/Liquibase** 接管，
  并把 220 处列定义（含中文注释）落到迁移脚本 —— 否则上线即 `validate` 失败或注释丢失。

### 2.2 动态更新语义

`@DynamicInsert` / `@DynamicUpdate` 让 UPDATE 只带变更列（并发编辑下的关键行为）。
MyBatis-Plus 有等价能力（`FieldStrategy` / `updateById` 选择性更新），但**需要逐个资源核对**，
不能默认一致。

### 2.3 查询/筛选翻译层（61 个 Specification）

前端传的是**实体形状的 filter**（含嵌套关联，如 `filter.unit.name = '件'`），
airpower + JPA 自动翻译成 Criteria。MyBatis 侧要自己写动态 SQL
（21 个已迁移资源就是逐个手写的 `InputMapper.xml#inputFilter`）。

### 2.4 关系模型

`@ManyToMany` 20 处：`user.roleList / departmentList / storageList / factoryList`、
`routing.operationList` 等。MyBatis 侧要显式写关联查询与关联写入（中间表）。

### 2.5 写侧行级守卫

`RowWriteGuard` + `JpaRowVisibilityProbe`：改/删前用**读侧同一份条件**判「这行在不在你的范围」。
它是 JPA Criteria 实现 —— **换 ORM 必须重写这一块**（这是本阶段真正的工作量之一）。

---

## 3. 为什么统一到 MyBatis 是对的（针对你的目标）

### 3.1 「加新表直接复用」在 MyBatis 侧才成立

`DataPermissionSpecification` 的类注释白纸黑字写着：

> 「注意：JPA 侧只能取实体已映射的字段。`tenant_id/creator_id` 在数据库加列后，
> **必须同时在实体（基类）上映射**，否则这里会以 binding 指标暴露 —— 这正是期望的信号。」

也就是说：

| 加新维度列（如物流的 `owner_id`） | 需要改什么 |
| --- | --- |
| **JPA 路径** | 数据库加列 **+ 实体加字段映射 + 重启** |
| **MyBatis 路径** | 数据库加列 **+ SQL 里加条件** |

**这正是你「加新表能直接复用」的核心诉求** —— 单 ORM（MyBatis）+ 启动期资源登记就能做到，
JPA 路径天然多一步实体映射。

### 3.2 双 ORM 让权限逻辑编译了**两遍**

```
策略树 PermissionRuleTreeBuilder
        ├─→ JpaPermissionRuleCompiler   → JPA Predicate（读+写守卫）
        └─→ SqlPermissionRuleRenderer   → JSqlParser AST（读侧 Mapper）
```

同一套策略、两套编译器、两套结果。**为了证明两者一致**，仓库被迫养了一整套对账机制：

- `RowParityCounter` / `RowParityRegistry` / `DataPermissionOrmParityEntity` / `DataPermissionOrmParityRepository`
- `MapperShadowReadService` / `ShadowDiffService`（同一次查询多打一次 COUNT 做差异比对）

> 💡 这套对账机制**只因为存在两个 ORM 才需要**。统一到 MyBatis 后可整块删除。

### 3.3 曾经的两次事故都源于 JPA 的对象语义

`ColumnMaskingAspect` 的注释记录了历史实现的两个致命问题：

1. 实体是 **JPA 托管对象**，反射改写脱敏值**有脏写回库的风险**；
2. `@SensitiveField` 多标在 `Double price` 上 → `field.set(obj, "1*.*5")` 直接抛异常 → **脱敏一开就 500**。

脱敏最终只能移到序列化层。**单 ORM（无托管对象）从根上不存在这个坑。**

### 3.4 行业适配性

物流仓储（WMS/TMS）的核心查询是「波次拣选、库存快照与汇总、批次效期、追溯链、报表统计」——
都是复杂 SQL。MyBatis 是国内 WMS/ERP 的事实标准，招人、接手、排查都更顺。

---

## 4. ❌ 为什么「只去掉 JPA 写」是最差组合

若保留 JPA 读、只把写换 MyBatis：

| 后果 | 说明 |
| --- | --- |
| **仍然是双 ORM** | 没省掉任何东西，`JpaPermissionRuleCompiler` 与对账子系统照样要养 |
| **读写语义分叉** | 同一实体，读走 MyBatis（无脏检查、直接 SQL）、写走 MyBatis —— 但**读的缓存/关联加载仍在 JPA**（`user.roleList` 等），会出现「读到的关联对象」与「写回的关联关系」两套规则 |
| **写侧守卫要重写，却仍要维护 JPA 版** | `RowWriteGuard` 仍需读侧条件，读在 JPA → 守卫还得用 Criteria |
| **迁移工作量重复** | 先把写迁完，将来迁读时实体/映射又要改一轮 |

**要么全留，要么全换，不要半换。**

---

## 5. 推荐路线：三阶段（新代码零 JPA 起步）

```
S0 · 让 Flyway 接管 DDL（**先做，否则永远删不掉 JPA**）        0.5–1 人月
     ├─ 从现有实体 + 9 个迁移脚本生成基线 DDL（含 220 处注释）
     ├─ 生成「当前库 vs 实体」差异报告，确认零漂移
     └─ 切 ddl-auto=none，迁移走 Flyway；CI 加 ddl:check（已有门禁可扩展）

S1 · 新物流模块直接用 MyBatis，零 JPA                          **当天生效**
     ├─ 批次/效期、序列号、波次、盘点、货主、承运 —— 全部 MyBatis-Plus + 显式 XML
     ├─ 资源登记 + @DataResource + @SensitiveField（沿用现有权限内核）
     └─ 收益：新模块立刻拥有「加表即用」能力，且不新增 JPA 债

S2 · 按域迁移老模块（每域独立可回滚）                          1–3 人月
     ├─ 域顺序建议：wms → factory → channel → asset → mes → system/personnel
     ├─ 每域产出：Mapper + XML + 筛选动态 SQL + 写侧守卫 MyBatis 版
     └─ 每域迁完，把该域从 JPA 编译器/对账子系统里摘掉

S3 · 收尾删除                                                 1 人月
     ├─ 删：JpaPermissionRuleCompiler、ORM 对账子系统、JpaRowVisibilityProbe
     ├─ 删：airpower-curd 依赖（BaseService 继承的是它的 CurdService）
     └─ 删：61 个 Repository、220 处 columnDefinition、实体注解
```

### 5.1 为什么 S0 必须先做

`ddl-auto: validate` 意味着**"表结构由代码保证"**。删掉 JPA 后没人保证表结构 ——
只有 Flyway 接管了，JPA 才从"职责"降级为"可选"。**顺序反了会导致上线卡在 validate。**

### 5.2 S1 为什么可以立刻做

新模块是**全新文件**，不影响存量：新 Mapper 走 MyBatis、资源登记进 `data_resource`、
策略在数据权限页配置 —— 现在就能跑通，不需要等 S0/S2。

---

## 6. 风险与护栏

| 风险 | 护栏 |
| --- | --- |
| 写侧守卫重写后出现「该拒没拒」 | 用现有 `data_permission_deny_log` + 影子模式先跑 SHADOW，差异率归零再 enforce |
| 动态更新语义不等价（误覆盖并发字段） | 每域迁移后跑并发更新用例；MP `FieldStrategy` 显式声明 |
| DDL 漂移 | 已有 `ddl:check` 门禁 + 新增 Flyway 校验 |
| 660 用例受影响 | 迁移按域推进，每域 CI 全绿才继续；`@SpringBootTest` 31 个优先改造为 Mapper 版 |
| 一次性大爆炸 | **明令禁止**：按域切，每域可回滚 |

---

## 7. 复现命令

```bash
cd SPMS-Server
for a in "@Entity" "@Table" "@Column" "@ManyToMany" "@ManyToOne" "@DynamicInsert" "@DynamicUpdate" columnDefinition; do
  printf "%-18s %s\n" "$a" "$(grep -rh "$a" src/main/java --include=*.java | wc -l)"
done
for a in Specification CriteriaBuilder; do
  printf "%-18s %s\n" "$a" "$(grep -rl "$a" src/main/java --include=*.java | wc -l)"
done
find src/main/java -name '*Service.java' | wc -l      # 87
find src/main/java -name '*Repository.java' | wc -l   # 61
find src/main/java -name '*Mapper.java' | wc -l       # 22
grep -rl "@SpringBootTest" src/test --include=*.java | wc -l   # 31
```

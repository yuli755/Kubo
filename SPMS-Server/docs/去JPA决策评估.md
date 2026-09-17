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

---

## 8. 选型复核：有没有「现成库」可以顶上（2026-09-18，联网查证）

使用方的要求是**「不要自研，能换现成的就换」**。逐条查证如下：

| 候选 | 是什么 | 能不能用 | 结论 |
| --- | --- | --- | --- |
| **`easybest/spring-data-mybatis`**（原 hatunet） | 把 Spring Data 的 Repository 语义架在 MyBatis 上，**实体仍用 JPA 注解**、方法名派生查询、透明审计 | ❌ **不能**：最新 release 是 **2.0.1 / 2020-08-31**（GitHub Releases 显示 11 个 release，2.0.1 为最后）；那是 Spring Data 2.x / `javax.persistence` 时代，**不支持 Jakarta（`jakarta.persistence`）与 Spring Boot 3**，五年无更新。装上去等于把已停维护的库引进来 | **排除** |
| **Spring Data REST** | 直接由 Repository 生成 REST 端点 | ❌ 它只认 Spring Data 的 Repository 抽象；MyBatis 不是 Spring Data 实现（业界共识：MyBatis 不是 JPA 实现，两者不可互换），要它生效就得回到 JPA | **排除**（与「去 JPA」目标相反） |
| **MyBatis-Plus**（**已在栈内，3.5.9**） | `BaseMapper` + `IService/ServiceImpl` 覆盖单表 CRUD；`MybatisPlusInterceptor` 一栈挂齐 **`PaginationInnerInterceptor`（分页）/ `TenantLineInnerInterceptor`（多租户行级）/ `DataPermissionInterceptor`（数据权限，可自定义 `DataPermissionHandler` 用 JSqlParser 拼条件）/ `OptimisticLockerInnerInterceptor`（乐观锁）**；`MetaObjectHandler` 做审计字段填充；`FieldStrategy` 控制动态更新 | ✅ **这就是那个「现成方案」**：本项目**已经装了它**（读链路 + 数据权限拦截器已在跑），写链路切过来即可，**不需要新增任何依赖** | **采用** |

**一句话**：JPA 那一侧（Hibernate 的 `Specification`/`CriteriaBuilder`/`ddl-auto`/脏检查）在现成生态里**没有等价物可以平移**——
要么回到 JPA，要么换 ORM。既然目标栈里写的就是 MyBatis-Plus，且它自带多租户/数据权限/分页/乐观锁/审计填充五个拦截器，
**正确答案就是「把 JPA 的活交给已经在栈里的 MyBatis-Plus」**，而不是再引一个库。

> 与本仓既有一致：`SPMS-Server/docs/数据权限目标态落地施工图.md` §裁决 B 早已写明
> 「拦截器不手写，用 MyBatis-Plus 的 `DataPermissionInterceptor` + `DataPermissionHandler`」——
> 本次查证的结果是**维持该裁决**，并确认没有更省事的外来方案。

### 8.1 实测现状（2026-09-18，较 §1 的变化）

| 维度 | §1（2026-09-17） | 现在 | 说明 |
| --- | --- | --- | --- |
| Service | 87 | **61** | 部分已合并/删除 |
| Repository（JPA） | 61 | **43** | 读侧换轨在推进 |
| Mapper（MyBatis） | 22 | **15**（另有 `@Mapper` 3 处） | 口径按 `*Mapper.java` 文件数 |
| 实体文件 | —— | 45 | `*Entity.java` |
| `@Entity` / `@Table` / `@Column` | —— | 41 / 45 / **223** | 写侧仍全量 JPA |
| `@ManyToMany` / `@ManyToOne` | 20 / 54 | 13 / 37 | 关系映射是迁移中最难的一环（MyBatis 侧无对象图，只有 join） |
| `Specification` / `CriteriaBuilder` | 61 / 28 文件 | **127 / 56 处引用** | 筛选翻译层 + 行级权限条件 |
| `ddl-auto` | `validate`（prod） | prod/main `validate`，**local `update`** | S0 未完成：本地仍由 JPA 建表 |

### 8.2 下一步（按 §5 路线，S0 是唯一的前置）

S0 需要**真实库结构**做基线（`mysqldump --no-data`）才能把 DDL 交给 Flyway——
沙箱里没有 MySQL、也没有 JDK/Maven，所以 S0 的落盘必须由你在有库的机器上配合一步：

```bash
# 在有数据库的机器上（任意一个已有数据的实例即可）
mysqldump -h 127.0.0.1 -u<user> -p --no-data --skip-comments --skip-add-drop-table <db> > schema-baseline.sql
```

拿到这份 schema 后，我可以：① 生成 `V__baseline.sql`（对齐现有 10 个迁移）；② 把 `ddl-auto` 全切 `none`；
③ 扩 `ddl:check` 覆盖面到全部 45 个实体；④ 之后按域（wms → factory → channel → asset → system/personnel）迁写侧。
**在此之前**，JPA 的删除只能停在「盘点 + 门禁」。

> **2026-09-18 更新（见 §9）**：使用方已明确「继续盲删、按域一批批推进、编译报错留到最后统一修」，
> 所以路线从「等 S0」改成「**先建棘轮 → 按域换轨 → 引擎换轨 → 删依赖**」。§8.2 里"S0 是唯一前置"的说法随之调整：S0 仍是 *删掉 `ddl-auto`* 的前置，
> 但不再是把写链路换成 MyBatis-Plus 的前置。

---

## 9. 执行记录（2026-09-18，按域推进）

### 9.1 先把尺子立起来：`pnpm jpa:check`（JPA 足迹棘轮）

去 JPA 和刚做完的 airpower 退场是同一类工程：**删除面大、单批收益不可见**。
airpower 那轮靠 `pnpm airpower:backend` 把"还剩多少"变成数字，才有了"只许降"的节奏；
这一轮照做，新增 `infra/scripts/check-jpa-usage.mjs`：

| 判据 | 起点（2026-09-18） |
| --- | --- |
| 触及 JPA 的 Java 文件 | **110 / 527** |
| 引用次数（剥注释后按关键字数） | **599** |
| JPA import 条数 | **478** |
| 依赖 | `spring-boot-starter-data-jpa` **仍在 pom 里** |
| `ddl-auto` | local `update` · main/prod `validate` |

```bash
pnpm jpa:check              # 人读：按模块 / 按关键字 / 最重的 20 个文件
pnpm jpa:check -- --json    # 机器版
pnpm jpa:check -- --check   # 棘轮（CI 与 pnpm check 都已接入）：超过基线即红
```

**口径踩过的坑**（都写进脚本注释了）：必须**词边界**匹配 ——
`@Table` 不能把 MyBatis-Plus 的 `@TableName/@TableId` 算进来（本轮实测差 48 处）、
`@Access` 不能把 Lombok 的 `@Accessors` 算进来（差 63 处）、
`Specification` 不能把本仓的 `DataPermissionSpecification` 算进来（差 90 处）、
`Predicate` 不能把 `java.util.function.Predicate` 算进来（差 370 处）。
另外一律**剥注释**：不然"这里原来是 JPA"这类迁移档案注释会把数字顶上去。

**起点数字里最值得盯的三行**（它们决定了顺序）：

| 关键字 | 数量 | 含义 |
| --- | --- | --- |
| `@Column` | 222 | 41 个实体的列映射 —— 纯机械，但必须整批替换（半套副本 = 两套 ORM 各认一半字段） |
| `CriteriaBuilder` 70 + `Specification` 54 + `criteria.Predicate` 42 | 166 | **数据权限的行级条件目前是用 JPA Criteria 手搓的**，与 MyBatis-Plus 那条 SQL 渲染链路是"同一套规则、两份实现"（§3.2 说的双份编译），换轨时这条必须整线切 |
| `@ManyToOne` 29 + `@ManyToMany` 8 | 37 | 对象图关系：MyBatis 侧没有对象图，只有显式 join/子查询 |

### 9.2 第一批（J1）：数据权限证据/审计日志族 → MyBatis-Plus

**为什么先动这一块**：这 4 个仓储**不依赖 CRUD 引擎**（它们直接 `extends JpaRepository`，不是
`BaseRepository`），是整块可换、可回滚、边界清楚的切片 —— 拿来验证"换轨的写法"最合适。

| 文件 | 变化 |
| --- | --- |
| `repository/DataPermissionDenyLogRepository.java` | `JpaRepository<_, Long>` → `KuboInfraMapper<_>` |
| `repository/DataPermissionDiffRepository.java` | 同上 |
| `repository/DataPermissionOrmParityRepository.java` | 同上 |
| `repository/OperationLogRepository.java` | 同上 |
| `entity/{DataPermissionDenyLog,DataPermissionDiff,DataPermissionOrmParity,OperationLog}Entity.java` | 加 `@TableName("…")`（JPA 的 `@Table` 保留，并存期各读各的）；删掉**冗余重声明**的 `@Id private Long id;` |
| `framework/curd/base/CurdEntity.java` | `id` 补 `@TableId(type = IdType.AUTO)`（与 JPA `IDENTITY` 同语义：自增 + 回填） |
| `base/mapper/KuboInfraMapper.java` + `base/mapper/InfraMapperConfiguration.java` | **新增**：基础设施 Mapper 的无条件注册（4 个 `MapperFactoryBean` 显式登记，见下） |
| `service/{DataPermissionDenyLogRecorder,OperationLogRecorder,ShadowDiffService}.java` | `repository.save(x)` → `repository.insert(x)`（MP 里 `save` 叫 `insert`，本批唯一需要改调用方的语义） |

**翻译对照**（每一处都在代码注释里写了理由）：

| JPA 写法 | MyBatis-Plus 写法 | 说明 |
| --- | --- | --- |
| `findTop50ByResourceKeyOrderByIdDesc(k)` | `default` 方法 + `LambdaQueryWrapper.eq(...).orderByDesc(...).last("LIMIT 50")` | 列名用方法引用写 → 字段改名编译期就炸，不用等到 SQL 报错 |
| `countByResourceKeyAndVerdict(k, v)` | `selectCount(wrapper)` | 返回 `Long`，空值归一为 `0` |
| `@Query(value="delete … where create_time < :cutoff", nativeQuery=true)` + `@Modifying @Transactional` | `@Delete("delete from <表> where create_time < #{cutoff}")`（**保留显式 SQL**） | 表名与条件是审计对象，写在仓储里可被契约测试 `maskContract.spec.ts` 直接钉住（本批就是被它拦下过一次：wrapper 版会丢掉那句可审计的 SQL）。**刻意不补 `@Transactional`**：MP 的 Mapper 在无事务时按语句自动提交，正好保住"三张证据表各自独立清理、某一张失败不影响其它两张"的既有行为（调用方 `DataPermissionEvidenceRetention` 是逐个 try/catch） |

**新增的地基（这一段是这批的"设计"部分，不是机械替换）**：
MyBatis-Plus 的 Mapper 在本仓有两套注册规则，**刻意用两个 marker 接口分开**：

- `KuboBaseMapper`：业务读链路。由 `MapperReadConfiguration` 在
  `kubo.data-permission.mapper-read.enabled=true` 时注册 —— 那个开关是灰度与回滚位，业务 Mapper 必须能"整体不存在"。
- `KuboInfraMapper`：基础设施表（本批的 4 个）。由新的 `InfraMapperConfiguration` **无条件**注册 ——
  写证据日志是"业务失败也要留痕"的旁路，绝不能跟着灰度开关一起消失（它们原来是 JPA 仓储，一直存在）。

**为什么不是再写一个 `@MapperScan`**：契约测试 `maskContract.spec.ts` 钉着"注册面只能有一处"
（多于一处 = 那个灰度开关就管不住全部 Mapper 了），这条判据是对的，所以本批改成
**4 个 `MapperFactoryBean` 显式逐个登记**：既不受开关影响，又让"哪几个 Mapper 不走开关"
在代码里一眼可见（这几行本身就是理由）。Bean 名字沿用接口驼峰名，`ObjectProvider<...>` 注入一个字都不用改。

> 本批被门禁拦下过两次，都按契约改了实现而不是改判据：① 保留期清理必须留下可审计的显式 SQL
> （wrapper 版会丢掉 `delete from <表> where create_time < …` 这句话）；② 对账实体的
> `@Column` 计数由 12 变 11（冗余重声明的 `id` 字段被删，它在基类 `CurdEntity` 里）。

### 9.3 顺手发现的一处隐患（**没改，等你拍板**）

这 4 张证据表的 `create_time` **一直是 NULL**：

- 写入走的是 `repository.save(entity)`，**不经过** `CurdService` 的审计填充（`CurdService:821` 才有 `setCreateTime(...)`）；
- 而 V2 迁移里 `create_time BIGINT NULL` 没有默认值（`operation_log` 那张更早，是 `DEFAULT 0`）；
- 保留期清理跑的是 `delete … where create_time < cutoff` —— **NULL 不满足任何比较**，
  所以拒绝事件/差异/对账三类证据日志**从来没有被保留期清掉过**（数据只增不减）。

MyBatis-Plus 的 `MetaObjectHandler`（审计字段自动填充）正好是修它的现成位置，可以放进下一批 ——
但那是**行为变化**（清理会第一次真正生效），不是纯重构，所以单独提出来让你确认。

### 9.4 下一批（J2）的设计：写链路引擎换轨（**一次性的核心改动**）

`CurdService`（1052 行）里 JPA 只有 **8 个触点**，这是好消息 —— 换轨不需要重写 CRUD 语义，只需要换底座：

| 触点 | 现在 | 换成 |
| --- | --- | --- |
| 新增 | `repository.saveAndFlush(target)` | `insert(target)` / `updateById(target)`（按 id 判空） |
| 删除 | `repository.deleteById(id)` | `deleteById(id)`（签名兼容） |
| 详情 | `repository.findById(id)` + `entityManager.clear()` | `selectById(id)`（MP 无一级缓存，`clear()` 直接删掉） |
| 列表 | `repository.findAll(spec, sort)` | `selectList(wrapper)` + `orderBy` |
| 分页 | `repository.findAll(spec, pageable)` | `selectPage(Page, wrapper)` |
| 唯一性校验 | `repository.findOne(Example)` | `selectList(wrapper)` 取首条 |
| 悲观锁 | `repository.getForUpdateById(id)` | `selectOne(wrapper.last("FOR UPDATE"))` |
| 条件构造 | `createSpecification(filter, isEqual)`（Criteria） | `createWrapper(filter, isEqual)`（`LambdaQueryWrapper`） |

配套（同批，缺一个都不成立）：

1. `ICurdRepository` 从 `extends JpaRepository, JpaSpecificationExecutor` 改成 `extends BaseMapper`，
   并把 JPA 风格的**兼容垫片**（`findAll()` / `findById()` / `saveAndFlush()` / `findAllById()`）做成
   `default` 方法 —— 这样 21 处域内调用点**零改动**，改动面被压在引擎这一个文件里；
2. `QueryHelper.createSort/createPageable` → MyBatis-Plus 的 `OrderItem` / `Page`；
3. `@MapperScan` 覆盖 `**.repository`（44 个仓储接口从此就是 Mapper，不用逐个改）；
4. 8 个仓储里 41 个**派生查询方法**（`getByPhone`/`getByCode`/…）改成 `LambdaQueryWrapper` 的 `default` 方法。

**为什么这一批必须一次做完、不能按域拆**（§10.1 已用实测数据坐实，并给出照做清单）：实体注解（`@Id`/`@Column`）是**两套 ORM 共读**的；
只要 JPA 还在，实体的 JPA 注解就不能删；而 `ICurdRepository` 一旦不是 JpaRepository，
所有 JPA 读路径（包括数据权限的 `Specification` 与影子比对）就一起断了。
所以顺序只能是：**引擎换轨（J2）→ 实体注解批量替换 + 数据权限 Criteria 线换成 MP 渲染（J3）→ 删 `spring-boot-starter-data-jpa` + `ddl-auto`（J4）**。

### 9.5 验证边界（说清楚哪些能验、哪些不能）

| 这一批 | 能不能离线验 | 怎么验 |
| --- | --- | --- |
| 语法/字面量/编码 | ✅ | `pnpm text:check`（525 个 Java 文件的字面量、转义、JDK import） |
| import 可达性 | ✅ | `pnpm java:imports` |
| JPA 足迹只许降 | ✅ | `pnpm jpa:check -- --check` |
| **编译** | ❌ 沙箱无 JDK、Maven 仓库不可达 | 需要你本机 `mvn -q compile`（或 CI） |
| **运行时语义**（MP 的 insert/update 与 JPA 脏检查是否等价、数据权限拦截器是否覆盖写语句） | ❌ 需要真库 | 需要你本机跑一次：新增/改/删各一条 + 证据日志落库 + 保留期清理 |

## §10 J2 执行清单（**干净切除**：不接受 JPA 与 MyBatis-Plus 并存）

> 决策更新（用户 2026-09-18）：**"只保留 MyBatis-Plus"** —— 本节先前写过的"引擎双路径、JPA 分支先留着"
> 方案作废。`spring-boot-starter-data-jpa` 与 Spring Data 仓储**必须整体退场**，不留并行分支。
> 代价照收：迁移窗口内应用不可启动（两套代理会抢同一个 Bean 名），编译只在你本机验。

### 10.1 为什么必须先动引擎（实测依据）

| 事实 | 出处 | 结论 |
| --- | --- | --- |
| 引擎只碰仓储的 **7 个动作** | `CurdService` L152/328/717/738/800/845/898 | 改造面比想象小 |
| 引擎 31 处 + `CurdController` 18 处 + `QueryHelper` 11 处 `Specification`/`Criteria` | `grep -c` | 126 处 JPA 查询构建里的 60 处集中在 3 个文件 |
| 44 个仓储接口里**只有 6 个**有自定义方法（共 19 个） | `find module -name '*Repository.java'` | 绝大多数仓储是一行接口，换底座是机械操作 |
| 全域 `repository.xxx()` 调用只有 ~30 种方法名 | `grep -rho` | 垫片能覆盖绝大部分调用点 |
| **`addSearchPredicate(Root, CriteriaBuilder, E)` 有 15 个域覆写** | `grep -rn addSearchPredicate` | 换签名必须**同批**换掉这 15 个，否则旧方法变成"能编译但永不被调用"的空转 |
| 这 15 个覆写依赖 `DataPermissionSpecification.buildPredicates`（316 行 Criteria 编译器） | `module/system/datapermission/service/` | **数据权限读侧强制与引擎是同一个编译单元**，不能拆批 |

**结论**：这不是"写链路一批、读链路一批"的关系 —— 仓储底座、引擎、15 个查询钩子、
数据权限条件编译器四者互相咬合，只能是**一批原子切除**（下面 10.2~10.6 是照做清单）。

### 10.2 底座（不留 JPA 族）

```java
// ICurdRepository：不再 extends JpaRepository / JpaSpecificationExecutor
public interface ICurdRepository<E extends CurdEntity<E>> extends BaseMapper<E> {
    // JPA 风格的兼容垫片：default 方法，保域内调用点零改动
    default Optional<E> findById(Long id)   { return Optional.ofNullable(selectById(id)); }
    default <S extends E> S save(S entity)  { /* id 为空 → insert，否则 updateById；回填自增 id */ }
    default <S extends E> S saveAndFlush(S entity) { return save(entity); }
    default void deleteById(Long id)        { deleteById((Serializable) id); }
    default List<E> findAll()               { return selectList(null); }
    default long count()                    { return selectCount(null); }
    default boolean existsById(Long id)     { return selectById(id) != null; }
    default E getForUpdateById(Long id)     { /* selectOne(wrapper.eq(id).last("FOR UPDATE"))；@Transactional(MANDATORY) */ }
    // ⚠️ 上面的 findAll(Specification)/findOne(Example)/flush() 一律**不提供**：
    //    它们没有 MP 语义，留着就是让调用点继续活着。找不到 = 编译期报错 = 我们要的响。
}
```

- `BaseRepository<E>` 去掉 `JpaRepository`/`NoRepositoryBean`，只留 `extends ICurdRepository<E>`；
- 注册：新增 `CurdMapperConfiguration`，`@MapperScan(basePackages = "cn.hamm.spms", markerInterface = ICurdRepository.class)`
  —— **无开关、永远注册**（写路径不能跟着读侧灰度开关消失）；与读侧的 `KuboBaseMapper` 扫描互不相交；
- 新增 `MpCurdMapperConfiguration`/`KuboBaseMapper` 两处扫描的**契约判据升级**（见 10.5）。

### 10.3 引擎：7 个动作只留 MP 实现（照抄表）

| 位置 | 现在（JPA） | 换成 |
| --- | --- | --- |
| L152 `deleteById` | `repository.deleteById(id)` | 同名垫片（语义一致：先查后删） |
| L328 `getForUpdate` 前的 `entityManager.clear()` | 清一级缓存防脏读 | **直接删**（MP 默认 `localCacheScope=STATEMENT`，每条语句后清本地缓存，本来就没有一级缓存陈旧读） |
| L328 `getForUpdateById` | `@Lock(PESSIMISTIC_WRITE)` | 垫片里的 `last("FOR UPDATE")`，仍在 `@Transactional(MANDATORY)` 内 |
| L717 `findAll(spec, sort)` | Criteria + Spring Data `Sort` | `selectList(createWrapper(filter, isEquals).orderBy(...))` |
| L738 `findAll(spec, pageable)` | Criteria + `Pageable` | `selectPage(new Page<>(n, size), createWrapper(...))` → `PageData.newInstance(ipage)` |
| L800 `findById` | `Optional<E>` | 垫片（返回 `Optional`，调用点零改动） |
| L845 `saveAndFlush` | 脏检查 + flush | 垫片（`insert`/`updateById`，**插入必须回填自增主键**） |
| L898 `findOne(Example.of(search))` | `Example` | `selectOne(createWrapper(search, true).last("LIMIT 1"))` |
| L881 `checkUnique` 读 `@Column(unique=true)` | JPA 注解 | 改读 `@TableField`，**过渡期两个都读**（`@Column` 还在时照样能过；J3 删 JPA 注解后自然只读后者） |
| L782 `addSearchPredicate(Root, CriteriaBuilder, E)` | 15 个域覆写 | `addSearchPredicate(@NotNull LambdaQueryWrapper<E> wrapper, @NotNull E search)`；**15 个覆写同批转换**（见 10.4） |

`QueryHelper` 补两个方法：`createWrapper(E filter, boolean isEqual)`（与 `getPredicateList` 同一套
`@Search`/`@SearchEmpty`/`@ManyToOne` 反射遍历，只是往 wrapper 里塞条件）与 `Sort/Page → OrderItem/Page` 转换。
列名解析走中间方法（现在读 `@Column(name=…)`，J3 后优先读 `@TableField`），这样实体注解换轨时这一处不用再动。

### 10.4 15 个查询钩子（同批转换，防静默空转）

| 文件 | 行 | 现在加的什么 |
| --- | --- | --- |
| `asset/contract/ContractService` | 180 | 权限谓词 + 自定义条件 |
| `asset/material/MaterialService` | 182 | 同上 |
| `channel/customer|sale|purchase|purchaseprice|saleprice|supplier/*Service` | 168~196 | 同上（6 个） |
| `factory/storage/StorageService` | 184 | `parent_id` 归属条件 |
| `factory/structure/StructureService` | 178 | 同上 |
| `personnel/department|role|user/*Service` | 41/27/590 | 同上 |
| `wms/input|move|inventory|output/*Service` | 208/188/289/182 | 同上（`InventoryService` 另有 `beforeCreatePredicate`） |

> **为什么必须同批**：`addSearchPredicate` 换签名后，没改的覆写会退化成"普通方法"，
> 编译**不报错**、运行时**永不调用** —— 查询条件与权限条件一起静默消失，比编译错误危险得多。
> （`beforeCreatePredicate` 是另一条线，不动。）
>
> **同批要补的门禁**：`src/config/__tests__/maskContract.spec.ts` 增加一条断言 —— main 里除
> `framework/curd/base/CurdService.java` 外，任何文件都不得再出现 `List<Predicate> addSearchPredicate(`
> 或 `Root<`/`CriteriaBuilder` 参数形状的同名方法。沙箱没有 javac，这条断言的唯一作用就是**替 javac
> 挡住"旧钩子悄悄变成死代码"**：条件与权限一起消失时，`pnpm check` 必须红。"

### 10.5 数据权限读侧：Criteria → wrapper（本批最需要小心的地方）

- `DataPermissionSpecification.buildPredicates(Root, CriteriaBuilder, …)` → `buildWrapper(LambdaQueryWrapper<E>, …)`：
  `OFF/SHADOW/ENFORCE` 三档语义、`deny-unregistered`、binding 指标、空结果集策略**逐条照搬，不许"简化"**；
- `JpaPermissionRuleCompiler` 的规则模型（`PermissionRule` = 字段/比较符/值）本来就是 ORM 中立的
  → 换一个 wrapper 渲染器即可，规则解析、建树、审计三件不动；类名与用法一起改（`Jpa…` 前缀此时必须消失）；
- `ShadowDiffService`(13) / `MapperShadowReadService`(6) / `JpaRowVisibilityProbe` / `DimensionBindingAnalyzer`(3)
  逐个跟着换；影子比对的两条路径（JPA 侧与 Mapper 侧）在换完之后应当只剩一条 —— 这正是"去 JPA"的收益，
  不要在 wrapper 世界里再把两套影子逻辑重建一遍；
- **验收口径不变**：数据权限的既有单测/契约断言全部保留（`src/config/__tests__` 里 94 条与
  `SPMS-Server/src/test/java` 下的单测），换完必须仍然表达同一个语义。

### 10.6 切分与顺序（一批原子切除，内部按这个序做）

1. **底座**：`ICurdRepository`/`BaseRepository` + 垫片 + `CurdMapperConfiguration` + 契约判据升级；
2. **引擎**：`QueryHelper.createWrapper` → `CurdService` 的 7 个动作与查询路径 → `CurdController`；
3. **钩子**：15 个 `addSearchPredicate` 覆写（同批）；
4. **权限线**：`DataPermissionSpecification` → wrapper 版，`JpaPermissionRuleCompiler` 等一并改；
5. **仓储**：44 个接口的 `extends` 换成 MP 族 + 19 个自定义方法改 `default` + wrapper；
6. **收尾**：`pnpm jpa:check` 基线按实测下调并固化、`docs` 对账、`pnpm check` 全绿（前端 459 用例 + 契约）。

### 10.7 窗口内的真相（写下来，免得后来者误判）

- **应用在这个窗口内起不来**：Spring Data 与 MyBatis 会为同一个接口抢同名 Bean（Boot 2.1+ 禁止覆盖），
  只有等 44 个仓储全部脱离 JPA 族才能启动 —— 这是"干净切除"的必然代价，不是 bug；
- **编译仍只在你本机验**：沙箱无 JDK、Maven 仓库不可达。我这边能验的是：
  `pnpm text:check`（字面量/转义/import 可达）、`pnpm java:imports`、`pnpm jpa:check -- --check`（棘轮）、
  94 条后端契约 + 前端用例 —— 这些挡不住"类型不匹配"，但**能挡住静默语义丢失**（10.4 那条）。
- 需要用户本机跑的验收：`mvn -q compile` → `pnpm check` → 真库一次回归（增删改 + 数据权限 ENFORCE 档 + 保留期清理）。

## §11 SQL 分层（2026-09-18，与去 JPA 同源的第二批）

> 规则（用户原话）：**"代码分层：禁止 `.java` 文件编写 SQL 语句，只能使用专属文件编写。"**

这一条比"去 JPA"更硬：去 JPA 说的是"用哪个 ORM"，SQL 分层说的是"语句写在哪儿"。
两者在同一个方向上有交集（`@Query`/`@Select` 都是把语句藏进 Java），但 SQL 分层**现在就能全做完**，
不用等引擎换轨 —— 而且它先把"迁移过程中的中间态"关掉了一条：以后换 ORM 时不会出现
"JPA 的 `:cutoff` 改写成 MyBatis 的 `#{cutoff}`"这种散落各处的方言替换。

### 11.1 门禁（可执行的判据）

`pnpm sql:check`（`infra/scripts/check-sql-in-java.mjs`，已进 `pnpm check` 与 `ci.yml`；门禁总数 20 → **21**）：

- **A 类**：字符串字面量里的 SQL 特征 —— `select … from` / `insert into` / `delete from` / `update … set` /
  `truncate|alter|create|drop table` / `for update` / `order by` / `group by` / `limit N` / 裸 `where` 片段；
- **B 类**：SQL 载体注解 —— `@Select`/`@Insert`/`@Update`/`@Delete`/`@SelectKey`/`@*Provider`/`@Query`/
  `nativeQuery = true`/`createNativeQuery(`；
- **不误报标识符**：`sortBy` / `orderNo` / `updateTime` 这类名字不会被算（子串匹配的坑在 JPA 棘轮上已经踩过一次）；
- **唯一豁免**：`// SQL-IGNORE: 理由`，只给**非可执行 SQL**（安全测试的恶意输入样例、被测对象的输入）。
  豁免会被逐条打印（含理由），所以"悄悄加豁免"在 CI 日志与 review 里都看得见；生产代码不允许豁免。

### 11.2 这一批改了什么（起点 207 处 → 现在 0 处）

| 位置 | 改法 | 结果 |
| --- | --- | --- |
| 证据/审计 4 仓储（J1 刚换的 MyBatis-Plus） | 上一批为保"可审计的显式 SQL"用了 `@Delete("…")` 与 `last("LIMIT 50")` —— **那两样都是把语句写在 Java 里**，这批全部搬进 `<repo>.xml`（含 `LIMIT` 与 `order by`），接口只剩方法声明 + `@Param` | `mapper/system/datapermission/*.xml` ×4 |
| `InventoryRepository` 的两条原子增减（JPA `@Query`，`quantity = quantity ± ?`） | 搬到 `InventoryWriteMapper` + XML。**为什么单独一个接口**：读侧 Mapper 归灰度开关管，写侧增减跟着消失就是"静默不扣库存" —— 它属于无条件注册族 | `mapper/wms/InventoryWriteMapper.xml` |
| `DataFieldPolicyRepository` 的 JPQL join（`JOIN dfp.resource dr`） | 搬到 `DataFieldPolicyQueryMapper` + XML（对象关联 JOIN → 显式外键列 JOIN）；它是权限判定链的一部分，同样无条件注册 | `mapper/system/datapermission/DataFieldPolicyQueryMapper.xml` |
| 14 个 `*MapperRuntimeInterceptorTest` 的 H2 夹具（建表/清表/插种子/策略行） | 抽成 `src/test/resources/sql/mapper-runtime/*.sql`，Java 侧只留 `SqlFixtures.execute(jdbcTemplate, "…")`；`ALTER TABLE … ADD COLUMN IF NOT EXISTS` 这类动态列（原来是 Java 拼字符串）变成脚本里的显式语句，辅助方法整段删除 | 14 个 `.sql` + `support/SqlFixtures` |
| 其余测试夹具与验证查询（`OrmParityEvidenceTest` / `MaterialMapperReadParityTest` / `DashboardOverviewContractTest` / `MybatisJpaCoexistenceTest` / `KuboDataPermissionHandlerTest`） | 同上（含"按表名循环建表"用 `${table}` 占位符传值 —— 语句在文件里，Java 不拼 SQL） | 9 个 `.sql` |
| 6 处安全测试的恶意输入样例 | 加 `// SQL-IGNORE: 理由`（它们是"值"，测试断言的正是"不会被拼进 SQL"） | 8 处豁免（全部带理由） |

### 11.3 契约测试也跟着"改判据而不是放宽判据"

`maskContract.spec.ts` 里那条"证据留存有正数天数、独立定时清理与 create_time 索引"原本断言
**Java 仓储文件里**含 `delete from data_permission_diff_log where create_time < #{cutoff}`。
这批把语句搬进 XML 后，断言改读 `mapper/system/datapermission/DataPermissionDiffRepository.xml`
（并按 XML 的实体转义写成 `&lt;`）—— 判据本身（"表名 + create_time 条件必须可审计地写在某处"）没变，
只是"某处"从 `.java` 换成了专属文件。**同一个文件被这条断言拦下过两次**：第一次是想用
`LambdaQueryWrapper.delete` 省掉显式 SQL（表名消失），第二次是把 `LIMIT` 留在 Java 里（`last("LIMIT 50")`）。

### 11.4 验证边界

| 项 | 结论 |
| --- | --- |
| 门禁自检 10 例（含"标识符不误报"） | ✅ `pnpm sql:check -- --self-test` |
| 当前足迹 | ✅ **0 处违规**（main + test 全扫，530 个 `.java`），8 处带理由的豁免 |
| JPA 棘轮顺带下降 | ✅ 110/599/478 → **108/594/473**（基线已锁定；`@Query` 与两条原子 UPDATE 的 JPA API 引用消失） |
| 契约测试 | ✅ 94/94（含改读 XML 的那条） |
| 编译 | ❌ 沙箱无 JDK —— 新增的 Mapper/XML 需要你本机 `mvn -q compile` + 一次真实启动（MyBatis 在启动时校验 `namespace` 与 statement id，找不到会直接报 `Invalid bound statement`） |
